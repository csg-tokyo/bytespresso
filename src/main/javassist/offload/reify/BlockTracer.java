// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

import java.util.ArrayList;

import javassist.bytecode.ByteArray;
import javassist.bytecode.ClassFile;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Descriptor;
import javassist.bytecode.BadBytecode;
import javassist.offload.ast.*;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.NotFoundException;

/**
 * A tracer of basic block.
 */
public class BlockTracer {
    private ClassPool classPool;
    private ConstPool cpool;
    private ClassFile classFile;
    CtClass returnType;    // used as the type of ARETURN
    private UniqueID uniqueID;
    private FlowAnalyzer analyzer;
    
    private BasicBlock basicBlock;
    ArrayList<ASTree> statements;
    ArrayList<JVariable> resetVariables;

    /* initStackFrame and initLocalVars are the states of the stack frame
     * and local variables at the entry point of the basic block.
     * stackFrame and localVars are working memory.
     */
    private int stackTop;
    private ASTree[] stackFrame, initStackFrame;
    private JVariable[] localVars, initLocalVars;

    private final CtClass OBJECT;
    private final CtClass CLASS;
    private final CtClass STRING;

    private static final boolean WORD = false;
    private static final boolean DWORDS = true;

    private BlockTracer(ClassPool classes, ConstPool cp, ClassFile thisType,
                       CtClass retType, UniqueID uid, FlowAnalyzer fa,
                       BasicBlock block)
    {
    	classPool = classes;
        cpool = cp;
        classFile = thisType;
        returnType = retType;
        uniqueID = uid;
        analyzer = fa;
        basicBlock = block;
        statements = new ArrayList<ASTree>();
        resetVariables = new ArrayList<JVariable>();
        try {
            OBJECT = classes.get("java.lang.Object");
            CLASS = classes.get("java.lang.Class");
            STRING = classes.get("java.lang.String");
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Constructs a tracer for tracing the first block in the method body.
     *
     * @param block     the traced block.
     */
    public BlockTracer(ClassPool classes, ConstPool cp, CtClass thisType, int maxStack,
                       int maxLocals, CtClass retType, UniqueID uid, FlowAnalyzer fa,
                       BasicBlock block, ArrayList<JVariable> params)
    {
    	this(classes, cp, thisType.getClassFile2(), retType, uid, fa, block);
        stackTop = 0;
        stackFrame = new ASTree[maxStack];
        initStackFrame = new ASTree[maxStack];
        localVars = new JVariable[maxLocals];
        initLocalVars = new JVariable[maxLocals];
        CtClass[] paramTypes = block.locals;
        for (int i = 0; i < paramTypes.length; i++) {
            CtClass t = paramTypes[i];
            JVariable var;
            if (t == null || t == BasicBlock.TOP)
                var = null;
            else if (t == BasicBlock.NULL)
                var = new JVariable(OBJECT, i, new Null(OBJECT));
            else {
                var = new JVariable(t, i);
                params.add(var);
            }

            initLocalVars[i] = localVars[i] = var;
        }
    }

    /**
     * Constructs a tracer for tracing the non-first block in the method body.
     *
     * @param block     the traced block.
     * @param src       the source block, which the thread of control comes from. 
     */
    public BlockTracer(BasicBlock block, BlockTracer src) {
    	this(src.classPool, src.cpool, src.classFile, src.returnType, src.uniqueID,
    	     src.analyzer, block);
        stackTop = src.stackTop;
        stackFrame = new ASTree[src.stackFrame.length];
        initStackFrame = new ASTree[src.stackFrame.length];
        copyStackFrame(src);

        localVars = new JVariable[src.localVars.length];
        initLocalVars = new JVariable[src.localVars.length];
        for (int i = 0; i < src.localVars.length; i++) {
            JVariable var;
            if (i >= basicBlock.locals.length)
                var = null;
            else {
                CtClass type = basicBlock.locals[i];
                if (type == BasicBlock.NULL)
                    var = new JVariable(OBJECT, i, new Null(OBJECT));
                else if (type == null || type == BasicBlock.TOP)
                    var = null;
                else {
                    var = src.localVars[i];
                    var.setType(type);
                }
            }

            localVars[i] = initLocalVars[i] = var;
        }
    }

    /**
     * Adding another incoming block to this block.
     */
    public void merge(BlockTracer incoming) throws NotFoundException {
        for (int i = 0; i < incoming.stackTop; i++) {
            ASTree v = incoming.stackFrame[i];
            TmpVariable tmp = (TmpVariable)initStackFrame[i];
            if (tmp != null && v != tmp) {
                incoming.insertAtTail(new Assign(tmp.type(), tmp, v));
                incoming.stackFrame[i] = tmp;
                tmp.beMutable();
            }
        }

        for (int i = 0; i < initLocalVars.length; i++) {
            JVariable v = initLocalVars[i];
            if (v != null && v != incoming.localVars[i]) {
                analyzer.merge(v, incoming.localVars[i]);
                if (!v.isMutable())
                    resetVariables.add(v);

                v.beMutable();
            }
        }
    }

    private void copyStackFrame(BlockTracer src) {
        for (int i = 0; i < stackTop; i++) {
            ASTree v = src.stackFrame[i];
            if (v != null && !(v instanceof TmpVariable)) {
                CtClass type = basicBlock.stack[i];
                TmpVariable tmp = new TmpVariable(type, v, uniqueID.tmpVarId());
                src.insertAtTail(new Assign(tmp.type(), tmp, v));
                src.stackFrame[i] = tmp;
                v = tmp;
            }

            stackFrame[i] = initStackFrame[i] = v;
        }
    }

    private void saveStackFrame() {
        for (int i = 0; i < stackTop; i++) {
            ASTree v = stackFrame[i];
            if (v != null && !(v instanceof TmpVariable) && !(v instanceof New)) {
                TmpVariable tmp = new TmpVariable(v.type(), v, uniqueID.tmpVarId());
                insertAtTail(new Assign(tmp.type(), tmp, v));
                stackFrame[i] = tmp;
            }
        }
    }

    private void insertAtTail(ASTree ast) {
        int s = statements.size() - 1;
        if (s > 0) {
            ASTree last = statements.get(s);
            if (last instanceof Jump) {
                statements.add(s, ast);
                return;
            }
        }

        statements.add(ast);
    }

    /**
     * Does abstract interpretation on the given bytecode instruction.
     *
     * @param pos         the position of the instruction.
     * @return      the size of the instruction at POS
     *              (or the next two instructions).
     *              see doIinc().
     */
    protected int doOpcode(int pos, byte[] code, MethodTracer tracer) throws BadBytecode {
        try {
            int op = code[pos] & 0xff;
            if (op < 96)
                if (op < 54)
                    return doOpcode0_53(pos, code, op);
                else
                    return doOpcode54_95(pos, code, op);
            else
                if (op < 148)
                    return doOpcode96_147(pos, code, op);
                else
                    return doOpcode148_201(pos, code, op, tracer);
        }
        catch (ArrayIndexOutOfBoundsException e) {
            throw new BadBytecode("inconsistent stack height at " + pos, e);
        }
    }

    /**
     * Invoked when the visited instruction is jsr.
     * Java6 or later does not allow using RET.
     */
    protected void visitJSR(int pos, byte[] code) throws BadBytecode {
        throw new BadBytecode("JSR at " + pos);
    }

    /**
     * Invoked when the visited instruction is ret or wide ret.
     * Java6 or later does not allow using RET.
     */
    protected void visitRET(int pos, byte[] code) throws BadBytecode {
        throw new BadBytecode("RET at " + pos);   
    }

    private int doOpcode0_53(int pos, byte[] code, int op) throws BadBytecode {
        switch (op) {
        case Opcode.NOP :
            break;
        case Opcode.ACONST_NULL :
            stackFrame[stackTop++] = new Null(OBJECT);
            break;
        case Opcode.ICONST_M1 :
        case Opcode.ICONST_0 :
        case Opcode.ICONST_1 :
        case Opcode.ICONST_2 :
        case Opcode.ICONST_3 :
        case Opcode.ICONST_4 :
        case Opcode.ICONST_5 :
            stackFrame[stackTop++] = new IntConstant(op - Opcode.ICONST_0);
            break;
        case Opcode.LCONST_0 :
        case Opcode.LCONST_1 :
            stackFrame[stackTop] = new LongConstant(op - Opcode.LCONST_0);
            stackFrame[stackTop + 1] = null;
            stackTop += 2;
            break;
        case Opcode.FCONST_0 :
        case Opcode.FCONST_1 :
        case Opcode.FCONST_2 :
            stackFrame[stackTop++] = new FloatConstant(op - Opcode.FCONST_0);
            break;
        case Opcode.DCONST_0 :
        case Opcode.DCONST_1 :
            stackFrame[stackTop] = new DoubleConstant(op - Opcode.DCONST_0);
            stackFrame[stackTop + 1] = null;
            stackTop += 2;
            break;
        case Opcode.BIPUSH :
            stackFrame[stackTop++] = new IntConstant(code[pos + 1]);
            return 2;
        case Opcode.SIPUSH :
            stackFrame[stackTop++] = new IntConstant(ByteArray.readS16bit(code, pos + 1));
            return 3;
        case Opcode.LDC :
            doLDC(code[pos + 1] & 0xff);
            return 2;
        case Opcode.LDC_W :
        case Opcode.LDC2_W :
            doLDC(ByteArray.readU16bit(code, pos + 1));
            return 3;
        case Opcode.ILOAD :
            return doXLOAD(WORD, code, pos);
        case Opcode.LLOAD :
            return doXLOAD(DWORDS, code, pos);
        case Opcode.FLOAD :
            return doXLOAD(WORD, code, pos);
        case Opcode.DLOAD :
            return doXLOAD(DWORDS, code, pos);
        case Opcode.ALOAD :
            return doXLOAD(WORD, code, pos);
        case Opcode.ILOAD_0 :
        case Opcode.ILOAD_1 :
        case Opcode.ILOAD_2 :
        case Opcode.ILOAD_3 :
            stackFrame[stackTop++] = localVars[op - Opcode.ILOAD_0];
            break;
        case Opcode.LLOAD_0 :
        case Opcode.LLOAD_1 :
        case Opcode.LLOAD_2 :
        case Opcode.LLOAD_3 :
            stackFrame[stackTop] = localVars[op - Opcode.LLOAD_0];
            stackFrame[stackTop + 1] = null;
            stackTop += 2;
            break;
        case Opcode.FLOAD_0 :
        case Opcode.FLOAD_1 :
        case Opcode.FLOAD_2 :
        case Opcode.FLOAD_3 :
            stackFrame[stackTop++] = localVars[op - Opcode.FLOAD_0];
            break;
        case Opcode.DLOAD_0 :
        case Opcode.DLOAD_1 :
        case Opcode.DLOAD_2 :
        case Opcode.DLOAD_3 :
            stackFrame[stackTop] = localVars[op - Opcode.DLOAD_0];
            stackFrame[stackTop + 1] = null;
            stackTop += 2;
            break;
        case Opcode.ALOAD_0 :
        case Opcode.ALOAD_1 :
        case Opcode.ALOAD_2 :
        case Opcode.ALOAD_3 :
            stackFrame[stackTop++] = localVars[op - Opcode.ALOAD_0];
            break;
        case Opcode.IALOAD :
            doXAload(CtClass.intType, WORD);
            break;
        case Opcode.LALOAD :
            doXAload(CtClass.longType, DWORDS);
            break;
        case Opcode.FALOAD :
            doXAload(CtClass.floatType, WORD);
            break;
        case Opcode.DALOAD :
            doXAload(CtClass.doubleType, DWORDS);
            break;
        case Opcode.AALOAD :
            doXAload(OBJECT, WORD);
            break;
        case Opcode.BALOAD :
            doXAload(CtClass.byteType, WORD);
            break;
        case Opcode.CALOAD :
            doXAload(CtClass.charType, WORD);
            break;
        case Opcode.SALOAD :
            doXAload(CtClass.shortType, WORD);
            break;
        default :
            throw new RuntimeException("fatal");
        }

        return 1;
    }

    private void doLDC(int index) {
        int tag = cpool.getTag(index);
        if (tag == ConstPool.CONST_String)
            stackFrame[stackTop++] = new StringConstant(cpool.getStringInfo(index), STRING);
        else if (tag == ConstPool.CONST_Integer)
            stackFrame[stackTop++] = new IntConstant(cpool.getIntegerInfo(index));
        else if (tag == ConstPool.CONST_Float)
            stackFrame[stackTop++] = new FloatConstant(cpool.getFloatInfo(index));
        else if (tag == ConstPool.CONST_Long) {
            stackFrame[stackTop] = new LongConstant(cpool.getLongInfo(index));
            stackFrame[stackTop + 1] = null;
            stackTop += 2;
        }
        else if (tag == ConstPool.CONST_Double) {
            stackFrame[stackTop] = new DoubleConstant(cpool.getDoubleInfo(index));
            stackFrame[stackTop + 1] = null;
            stackTop += 2;
        }
        else if (tag == ConstPool.CONST_Class)
            stackFrame[stackTop++] = new ClassConstant(cpool.getClassInfo(index), CLASS);
        else
            throw new RuntimeException("bad LDC: " + tag);
    }

    private int doXLOAD(boolean is2words, byte[] code, int pos) {
        int localVar = code[pos + 1] & 0xff;
        return doXLOAD(localVar, is2words);
    }

    private int doXLOAD(int var, boolean is2words) {
        stackFrame[stackTop++] = localVars[var];
        if (is2words == DWORDS)
            stackFrame[stackTop++] = null;

        return 2;
    }

    private void doXAload(CtClass type, boolean is2words) throws BadBytecode {
        if (type == OBJECT)
            try {
                type = stackFrame[stackTop - 2].type().getComponentType();
            } catch (NotFoundException e) {
                throw new BadBytecode(e.getMessage(), e);
            }

        stackFrame[stackTop - 2] = new Array(stackFrame[stackTop - 2],
                                             stackFrame[stackTop - 1], type);
        stackFrame[stackTop - 1] = null;
        if (is2words == WORD)
            stackTop--;
    }

    private int doOpcode54_95(int pos, byte[] code, int op) throws BadBytecode {
        switch (op) {
        case Opcode.ISTORE :
            return doXSTORE(pos, code, CtClass.intType, WORD);
        case Opcode.LSTORE :
            return doXSTORE(pos, code, CtClass.longType, DWORDS);
        case Opcode.FSTORE :
            return doXSTORE(pos, code, CtClass.floatType, WORD);
        case Opcode.DSTORE :
            return doXSTORE(pos, code, CtClass.doubleType, DWORDS);
        case Opcode.ASTORE :
            return doXSTORE(pos, code, OBJECT, WORD);
        case Opcode.ISTORE_0 :
        case Opcode.ISTORE_1 :
        case Opcode.ISTORE_2 :
        case Opcode.ISTORE_3 :
          { int var = op - Opcode.ISTORE_0;
            doXSTORE(var, CtClass.intType, WORD);
            break; }
        case Opcode.LSTORE_0 :
        case Opcode.LSTORE_1 :
        case Opcode.LSTORE_2 :
        case Opcode.LSTORE_3 :
          { int var = op - Opcode.LSTORE_0;
            doXSTORE(var, CtClass.longType, DWORDS);
            break; }
        case Opcode.FSTORE_0 :
        case Opcode.FSTORE_1 :
        case Opcode.FSTORE_2 :
        case Opcode.FSTORE_3 :
          { int var = op - Opcode.FSTORE_0;
            doXSTORE(var, CtClass.floatType, WORD);
            break; }
        case Opcode.DSTORE_0 :
        case Opcode.DSTORE_1 :
        case Opcode.DSTORE_2 :
        case Opcode.DSTORE_3 :
          { int var = op - Opcode.DSTORE_0;
            doXSTORE(var, CtClass.doubleType, DWORDS);
            break; }
        case Opcode.ASTORE_0 :
        case Opcode.ASTORE_1 :
        case Opcode.ASTORE_2 :
        case Opcode.ASTORE_3 :
          { int var = op - Opcode.ASTORE_0;
            doXSTORE(var, OBJECT, WORD);
            break; }
        case Opcode.IASTORE :
            doXASTORE(CtClass.intType, WORD);
            break;
        case Opcode.LASTORE :
            doXASTORE(CtClass.longType, DWORDS);
            break;
        case Opcode.FASTORE :
            doXASTORE(CtClass.floatType, WORD);
            break;
        case Opcode.DASTORE :
            doXASTORE(CtClass.doubleType, DWORDS);
            break;
        case Opcode.AASTORE :
            doXASTORE(OBJECT, WORD);
            break;
        case Opcode.BASTORE :
            doXASTORE(CtClass.byteType, WORD);
            break;
        case Opcode.CASTORE :
            doXASTORE(CtClass.charType, WORD);
            break;
        case Opcode.SASTORE :
            doXASTORE(CtClass.shortType, WORD);
            break;
        case Opcode.POP :
            doPop();
            break;
        case Opcode.POP2 :
            doPop();
            doPop();
            break;
        case Opcode.DUP :
            if (stackFrame[stackTop - 1] instanceof New) {
                // heuristics for NEW
                stackFrame[stackTop] = stackFrame[stackTop - 1];
                stackTop++;
                break;
            }
        case Opcode.DUP_X1 :
        case Opcode.DUP_X2 : {
            saveStackFrame();
            int len = op - Opcode.DUP + 1;
            doDUP_XX(1, len);
            stackFrame[stackTop - len] = stackFrame[stackTop];
            stackTop++;
            break; }
        case Opcode.DUP2 :
        case Opcode.DUP2_X1 :
        case Opcode.DUP2_X2 : {
            saveStackFrame();
            int len = op - Opcode.DUP2 + 2;
            doDUP_XX(2, len);
            stackFrame[stackTop - 1] = stackFrame[stackTop + 1];
            stackFrame[stackTop - 2] = stackFrame[stackTop];
            stackTop += 2;
            break; }
        case Opcode.SWAP :
            doSwap();
            break;
        default :
            throw new RuntimeException("fatal");
        }

        return 1;
    }

    private int doXSTORE(int pos, byte[] code, CtClass type, boolean is2words) {
        int index = code[pos + 1] & 0xff;
        return doXSTORE(index, type, is2words);
    }

    private int doXSTORE(int index, CtClass type, boolean is2words) {
        stackTop -= (is2words == DWORDS) ? 2 : 1;
        ASTree value = stackFrame[stackTop];
        if (type == OBJECT)
            type = value.type();

        JVariable var = new JVariable(type, index, value);
        statements.add(new Assign(type, var, value));
        localVars[index] = var;
        stackFrame[stackTop] = null;
        if (is2words == DWORDS)
            stackFrame[stackTop + 1] = null;

        return 2;
    }

    private void doXASTORE(CtClass type, boolean is2words) throws BadBytecode {
        int oldSp = stackTop;
        stackTop -= (is2words == DWORDS) ? 4 : 3;
        if (type == OBJECT)
            try {
                type = stackFrame[stackTop].type().getComponentType();
            } catch (NotFoundException e) {
                throw new BadBytecode(e.getMessage(), e);
            }

        Array array = new Array(stackFrame[stackTop], stackFrame[stackTop + 1], type);
        ASTree value = stackFrame[stackTop + 2];
        statements.add(new Assign(type, array, value));
        for (int sp = stackTop; sp < oldSp; sp++)
            stackFrame[sp] = null;
    }

    private void doDUP_XX(int delta, int len) {
        int sp = stackTop - 1;
        int end = sp - len;
        while (sp > end) {
            stackFrame[sp + delta] = stackFrame[sp];
            sp--;
        }
    }

    private void doPop() {
        ASTree t = stackFrame[--stackTop];
        stackFrame[stackTop] = null;
        if (t != null)
            statements.add(t);        
    }

    private void doSwap() {
        saveStackFrame();
        ASTree a = stackFrame[stackTop - 1];
        stackFrame[stackTop - 1] = stackFrame[stackTop - 2];
        stackFrame[stackTop - 2] = a;
    }

    private int doOpcode96_147(int pos, byte[] code, int op) {
        if (op <= Opcode.LXOR)      // IADD...LXOR
            return doBinOp(op);

        switch (op) {
        case Opcode.IINC :
            return doIinc(pos, code);
        case Opcode.I2L :
            stackFrame[stackTop - 1] = new Coercion(CtClass.intType, CtClass.longType,
                                                     stackFrame[stackTop - 1]);
            stackFrame[stackTop++] = null;
            break;
        case Opcode.I2F :
            stackFrame[stackTop - 1] = new Coercion(CtClass.intType, CtClass.floatType,
                                                    stackFrame[stackTop - 1]);
            break;
        case Opcode.I2D :
            stackFrame[stackTop - 1] = new Coercion(CtClass.intType, CtClass.doubleType,
                                                    stackFrame[stackTop - 1]);
            stackFrame[stackTop++] = null;
            break;
        case Opcode.L2I :
            stackFrame[stackTop - 2] = new Coercion(CtClass.longType, CtClass.intType,
                                                    stackFrame[stackTop - 2]);
            --stackTop;
            break;
        case Opcode.L2F :
            stackFrame[stackTop - 2] = new Coercion(CtClass.longType, CtClass.floatType,
                                                    stackFrame[stackTop - 2]);
            --stackTop;
            break;
        case Opcode.L2D :
            stackFrame[stackTop - 2] = new Coercion(CtClass.longType, CtClass.doubleType,
                                                    stackFrame[stackTop - 2]);
            break;
        case Opcode.F2I :
            stackFrame[stackTop - 1] = new Coercion(CtClass.floatType, CtClass.intType,
                                                    stackFrame[stackTop - 1]);
            break;
        case Opcode.F2L :
            stackFrame[stackTop - 1] = new Coercion(CtClass.floatType, CtClass.longType,
                                                    stackFrame[stackTop - 1]);
            stackFrame[stackTop++] = null;
            break;
        case Opcode.F2D :
            stackFrame[stackTop - 1] = new Coercion(CtClass.floatType, CtClass.doubleType,
                                                    stackFrame[stackTop - 1]);
            stackFrame[stackTop++] = null;
            break;
        case Opcode.D2I :
            stackFrame[stackTop - 2] = new Coercion(CtClass.doubleType, CtClass.intType,
                                                    stackFrame[stackTop - 2]);
            --stackTop;
            break;
        case Opcode.D2L :
            stackFrame[stackTop - 2] = new Coercion(CtClass.doubleType, CtClass.longType,
                                                    stackFrame[stackTop - 2]);
            break;
        case Opcode.D2F :
            stackFrame[stackTop - 2] = new Coercion(CtClass.doubleType, CtClass.floatType,
                                                    stackFrame[stackTop - 2]);
            --stackTop;
            break;
        case Opcode.I2B :
            stackFrame[stackTop - 1] = new Coercion(CtClass.intType, CtClass.byteType,
                                                    stackFrame[stackTop - 1]);
            break;
        case Opcode.I2C :
            stackFrame[stackTop - 1] = new Coercion(CtClass.intType, CtClass.charType,
                                                    stackFrame[stackTop - 1]);
            break;
        case Opcode.I2S :
            stackFrame[stackTop - 1] = new Coercion(CtClass.intType, CtClass.shortType,
                                                    stackFrame[stackTop - 1]);
            break;
        default :
            throw new RuntimeException("fatal");
        }

        return 1;
    }

    // IADD...LXOR
    private int doBinOp(int op) {
        int grow = Opcode.STACK_GROW[op];
        if (grow == -1)
            if (op == Opcode.LSHL || op == Opcode.LSHR || op == Opcode.LUSHR) { 
                stackFrame[stackTop - 3]
                    = new BinaryOp(op, stackFrame[stackTop - 3], stackFrame[stackTop - 1]);
                stackFrame[stackTop - 2] = null;
            }
            else
                stackFrame[stackTop - 2]
                    = new BinaryOp(op, stackFrame[stackTop - 2], stackFrame[stackTop - 1]);
        else if (grow == -2) {
            stackFrame[stackTop - 4]
                = new BinaryOp(op, stackFrame[stackTop - 4], stackFrame[stackTop - 2]);
            stackFrame[stackTop - 3] = null;
        }
        else if (grow == 0)
            if (op == Opcode.LNEG || op == Opcode.DNEG) { 
                stackFrame[stackTop - 2] = new UnaryOp(op, stackFrame[stackTop - 2]);
                stackFrame[stackTop - 1] = null;
            }
            else
                stackFrame[stackTop - 1] = new UnaryOp(op, stackFrame[stackTop - 1]);
        else
            throw new RuntimeException("invalid opcode: " + op);

        int oldSp = stackTop;
        stackTop += grow;
        for (int sp = stackTop; sp < oldSp; sp++)
            stackFrame[sp] = null;

        return 1;
    }

    private int doIinc(int pos, byte[] code) {
        int var = code[pos + 1] & 0xff;
        int value = code[pos + 2];  // signed
        return doIinc(pos, code, var, value, 3);
    }

    private int doIinc(int pos, byte[] code, int var, int value, int size) {
        if (stackTop == 0) {
            statements.add(makeIinc(localVars[var], var, value, Iinc.PRE));
            return size;
        }
        else if (isPostIncrement(stackFrame[stackTop - 1], var, value)) {
            JVariable v = (JVariable)stackFrame[stackTop - 1];
            stackFrame[stackTop - 1] = makeIinc(v, v.index(), value, Iinc.POST);
            return size;
        }
        else {
            int op = code[pos + 3] & 0xff;
            if (Opcode.ILOAD_0 <= op && op <= Opcode.ILOAD_3) {
                int localVar = op - Opcode.ILOAD_0;
                doPreIncrement(pos, var, localVar, value);
                return size + 1;
            }
            else if (op == Opcode.ILOAD) {
                int localVar = code[pos + 4] & 0xff;
                doPreIncrement(pos, var, localVar, value);
                return size + 2;
            }
            else if (op == Opcode.WIDE)
                if ((code[pos + 4] & 0xff) == Opcode.ILOAD) {
                    int localVar = ByteArray.readU16bit(code, pos + 5);
                    doPreIncrement(pos, var, localVar, value);
                    return size + 4;
                }
        }

        throw new RuntimeException("cannot handle this IINC at " + pos);
    }

    private boolean isPostIncrement(ASTree operand, int localVar, int inc) {
        if (operand instanceof JVariable) {
            JVariable var = (JVariable)operand;
            if (var.index() == localVar)
                return true;
        }

        return false;
    }

    private void doPreIncrement(int pos, int var, int localVar, int inc) {
        if (localVar == var) {
            stackFrame[stackTop++] = makeIinc(localVars[var], var, inc, Iinc.PRE);
            return;
        }
        else
            throw new RuntimeException("cannot handle this IINC at " + pos);
    }

    private Iinc makeIinc(JVariable v, int var, int inc, boolean isPost) {
        Iinc ii = new Iinc(v, inc, isPost);
        JVariable v2 = new JVariable(v.type(), v.index(), ii);
        localVars[var] = v2; 
        analyzer.merge(v2, v);
        return ii;
    }

    private int doOpcode148_201(int pos, byte[] code, int op, MethodTracer tracer)
        throws BadBytecode
    {
        switch (op) {
        case Opcode.LCMP :
            stackFrame[stackTop - 4]
                = new BinaryOp(op, stackFrame[stackTop - 4], stackFrame[stackTop - 2]);
            stackTop -= 3;
            break;
        case Opcode.FCMPL :
        case Opcode.FCMPG :
            stackFrame[stackTop - 2]
                = new BinaryOp(op, stackFrame[stackTop - 2], stackFrame[stackTop - 1]);
            stackTop--;
            break;
        case Opcode.DCMPL :
        case Opcode.DCMPG :
            stackFrame[stackTop - 4]
                = new BinaryOp(op, stackFrame[stackTop - 4], stackFrame[stackTop - 2]);
            stackTop -= 3;
            break;
        case Opcode.IFEQ :
        case Opcode.IFNE :
        case Opcode.IFLT :
        case Opcode.IFGE :
        case Opcode.IFGT :
        case Opcode.IFLE : {
            stackTop--;
            int offset = ByteArray.readS16bit(code, pos + 1);
            Branch br = new Branch(op, stackFrame[stackTop],
                                   tracer.findBlock(pos + offset));
            statements.add(br);
            return 3; }
        case Opcode.IF_ICMPEQ :
        case Opcode.IF_ICMPNE :
        case Opcode.IF_ICMPLT :
        case Opcode.IF_ICMPGE :
        case Opcode.IF_ICMPGT :
        case Opcode.IF_ICMPLE :
        case Opcode.IF_ACMPEQ :
        case Opcode.IF_ACMPNE : {
            stackTop -= 2;
            int offset = ByteArray.readS16bit(code, pos + 1);
            Branch br = new Branch(op, stackFrame[stackTop], stackFrame[stackTop + 1],
                                   tracer.findBlock(pos + offset));
            statements.add(br);
            return 3; }
        case Opcode.GOTO : {
            int offset = ByteArray.readS16bit(code, pos + 1);
            Goto g = new Goto(tracer.findBlock(pos + offset));
            statements.add(g);
            return 3; }
        case Opcode.JSR :
            visitJSR(pos, code);
            return 3;       // branch
        case Opcode.RET :
            visitRET(pos, code);
            return 2;
        case Opcode.TABLESWITCH : {
            int pos2 = (pos & ~3) + 8;
            int low = ByteArray.read32bit(code, pos2);
            int high = ByteArray.read32bit(code, pos2 + 4);
            int n = high - low + 1;
            doTableSwitch(pos, code, n, pos2 + 8, ByteArray.read32bit(code, pos2 - 4),
                          low, tracer);
            return n * 4 + 16 - (pos & 3); }
        case Opcode.LOOKUPSWITCH : {
            int pos2 = (pos & ~3) + 8;
            int n = ByteArray.read32bit(code, pos2);
            doLookupSwitch(pos, code, n, pos2 + 4, ByteArray.read32bit(code, pos2 - 4),
                           tracer);
            return n * 8 + 12 - (pos & 3); }
        case Opcode.IRETURN : {
            Return r = new Return(CtClass.intType, stackFrame[--stackTop]);
            statements.add(r);
            break; }
        case Opcode.LRETURN : {
            stackTop -= 2;
            Return r = new Return(CtClass.longType, stackFrame[stackTop]);
            statements.add(r);
            break; }
        case Opcode.FRETURN : {
            Return r = new Return(CtClass.floatType, stackFrame[--stackTop]);
            statements.add(r);
            break; }
        case Opcode.DRETURN : {
            stackTop -= 2;
            Return r = new Return(CtClass.doubleType, stackFrame[stackTop]);
            statements.add(r);
            break; }
        case Opcode.ARETURN : {
            Return r = new Return(returnType, stackFrame[--stackTop]);
            statements.add(r);
            break; }
        case Opcode.RETURN : {
            Return r = new Return();
            statements.add(r);
            break; }
        case Opcode.GETSTATIC :
            return doGetField(pos, code, true);
        case Opcode.PUTSTATIC :
            return doPutField(pos, code, true);
        case Opcode.GETFIELD :
            return doGetField(pos, code, false);
        case Opcode.PUTFIELD :
            return doPutField(pos, code, false);
        case Opcode.INVOKEVIRTUAL :
        case Opcode.INVOKESPECIAL :
            doInvokeMethod(op, pos, code, false);
            return 3;
        case Opcode.INVOKESTATIC :
            doInvokeMethod(op, pos, code, true);
            return 3;
        case Opcode.INVOKEINTERFACE :
            doInvokeMethod(op, pos, code, false);
            return 5;
        case Opcode.INVOKEDYNAMIC :
            doInvokeDynamic(pos, code);
            return 5;
        case Opcode.NEW : {
            int i = ByteArray.readU16bit(code, pos + 1);
            stackFrame[stackTop++] = new New(nameToCtClass(cpool.getClassInfo(i)));
            return 3; }
        case Opcode.NEWARRAY :
            return doNEWARRAY(pos, code);
        case Opcode.ANEWARRAY : {
            int i = ByteArray.readU16bit(code, pos + 1);
            String type = cpool.getClassInfo(i);
            stackFrame[stackTop - 1] = new NewArray(nameToArray(type), nameToCtClass(type),
                                                    stackFrame[stackTop - 1]);
            return 3; }
        case Opcode.ARRAYLENGTH :
            stackFrame[stackTop - 1] = new UnaryOp(op, stackFrame[stackTop - 1]);
            break;
        case Opcode.ATHROW : {
            Throw t = new Throw(stackFrame[--stackTop]);
            statements.add(t);
            break; }
        case Opcode.CHECKCAST :
        case Opcode.INSTANCEOF : {
            int i = ByteArray.readU16bit(code, pos + 1);
            CtClass type = nameToCtClass(cpool.getClassInfo(i));
            ASTree expr = stackFrame[stackTop - 1];
            stackFrame[stackTop - 1] = (op == Opcode.CHECKCAST) ? new Cast(type, expr)
                                                                : new InstanceOf(type, expr);
            return 3; }
        case Opcode.MONITORENTER :
        case Opcode.MONITOREXIT :
            statements.add(new Monitor(stackFrame[--stackTop], op == Opcode.MONITORENTER));
            break;
        case Opcode.WIDE :
            return doWIDE(pos, code);
        case Opcode.MULTIANEWARRAY :
            return doMultiANewArray(pos, code);
        case Opcode.IFNULL :
        case Opcode.IFNONNULL : {
            stackTop--;
            int offset = ByteArray.readS16bit(code, pos + 1);
            Branch br = new Branch(op, stackFrame[stackTop], new Null(OBJECT),
                                   tracer.findBlock(pos + offset));
            statements.add(br);
            return 3; }
        case Opcode.GOTO_W : {
            int offset = ByteArray.read32bit(code, pos + 1);
            Goto g = new Goto(tracer.findBlock(pos + offset));
            statements.add(g);
            return 5; }
        case Opcode.JSR_W :
            visitJSR(pos, code);
            return 5;
        }
        return 1;
    }

    /**
     * @param pos           the position of TABLESWITCH
     * @param code          bytecode
     * @param n             the number of case labels
     * @param offsetPos     the position of the branch-target table.
     * @param defaultOffset     the offset to the default branch target.
     */
    protected void doTableSwitch(int pos, byte[] code, int n,
                int offsetPos, int defaultOffset, int low, MethodTracer tracer)
         throws BadBytecode
    {
        int[] labels = new int[n];
        int[] toBlock = new int[n];
        for (int i = 0; i < n; i++) {
            labels[i] = low + i;
            int toIndex = pos + ByteArray.read32bit(code, offsetPos + i * 4);
            toBlock[i] = tracer.findBlock(toIndex);
        }

        Switch sw = new Switch(stackFrame[--stackTop], labels,
                               toBlock, tracer.findBlock(pos + defaultOffset));
        statements.add(sw);
    }

    /**
     * @param pos           the position of LOOKUPSWITCH
     * @param code          bytecode
     * @param n             the number of case labels
     * @param offsetPos     the position of the table of pairs of a value and a branch target.
     * @param defaultOffset     the offset to the default branch target.
     */
    protected void doLookupSwitch(int pos, byte[] code, int n,
                int pairsPos, int defaultOffset, MethodTracer tracer) throws BadBytecode {
        int[] labels = new int[n];
        int[] toBlock = new int[n];
        for (int i = 0; i < n; i++) {
            labels[i] = ByteArray.read32bit(code, pairsPos + i * 8);
            int toIndex = pos + ByteArray.read32bit(code, pairsPos + i * 8 + 4);
            toBlock[i] = tracer.findBlock(toIndex);
        }

        Switch sw = new Switch(stackFrame[--stackTop], labels,
                               toBlock, tracer.findBlock(pos + defaultOffset));
        statements.add(sw);
    }

    private int doWIDE(int pos, byte[] code) throws BadBytecode {
        int op = code[pos + 1] & 0xff;
        switch (op) {
        case Opcode.ILOAD :
            doWIDE_XLOAD(pos, code, WORD);
            break;
        case Opcode.LLOAD :
            doWIDE_XLOAD(pos, code, DWORDS);
            break;
        case Opcode.FLOAD :
            doWIDE_XLOAD(pos, code, WORD);
            break;
        case Opcode.DLOAD :
            doWIDE_XLOAD(pos, code, DWORDS);
            break;
        case Opcode.ALOAD :
            doWIDE_XLOAD(pos, code, WORD);
            break;
        case Opcode.ISTORE :
            doWIDE_STORE(pos, code, CtClass.intType, WORD);
            break;
        case Opcode.LSTORE :
            doWIDE_STORE(pos, code, CtClass.longType, DWORDS);
            break;
        case Opcode.FSTORE :
            doWIDE_STORE(pos, code, CtClass.floatType, WORD);
            break;
        case Opcode.DSTORE :
            doWIDE_STORE(pos, code, CtClass.doubleType, DWORDS);
            break;
        case Opcode.ASTORE :
            doWIDE_STORE(pos, code, OBJECT, WORD);
            break;
        case Opcode.IINC : {
            int index = ByteArray.readU16bit(code, pos + 2);
            int value = ByteArray.readS16bit(code, pos + 4);
            return doIinc(pos, code, index, value, 6); }
        case Opcode.RET :
            visitRET(pos, code);
            break;
        default :
            throw new RuntimeException("bad WIDE instruction: " + op);
        }

        return 4;
    }

    private void doWIDE_XLOAD(int pos, byte[] code, boolean is2words) {
        int index = ByteArray.readU16bit(code, pos + 2);
        doXLOAD(index, is2words);
    }

    private void doWIDE_STORE(int pos, byte[] code, CtClass type, boolean is2words) {
        int index = ByteArray.readU16bit(code, pos + 2);
        doXSTORE(index, type, is2words);
    }

    private int doPutField(int pos, byte[] code, boolean isStatic) throws BadBytecode {
        int index = ByteArray.readU16bit(code, pos + 1);
        String className = cpool.getFieldrefClassName(index);
        String fieldName = cpool.getFieldrefName(index);
        String desc = cpool.getFieldrefType(index);
        CtClass type = descToCtClass(desc);
        stackTop -= Descriptor.dataSize(desc);
        ASTree value = stackFrame[stackTop];
        ASTree target = isStatic ? null : stackFrame[--stackTop];
        CtClass targetClass = nameToCtClass(className);
        ASTree p = new PutField(targetClass, fieldName, type, isStatic, target, value);
        statements.add(p);
        return 3;
    }

    private CtClass descToCtClass(String desc) throws BadBytecode {
        try {
            return Descriptor.toCtClass(desc, classPool); 
        }
        catch (NotFoundException e) {
            throw new BadBytecode(desc, e);
        }
    }

    private CtClass nameToCtClass(String name) throws BadBytecode {
        try {
            return classPool.get(name);
        }
        catch (NotFoundException e) {
            throw new BadBytecode(name, e);
        }
    }

    private CtClass nameToArray(String componentTypeName) throws BadBytecode {
        String name = componentTypeName + "[]";
        try {
            return classPool.get(name);
        }
        catch (NotFoundException e) {
            throw new BadBytecode(name, e);
        }
    }

    private int doGetField(int pos, byte[] code, boolean isStatic) throws BadBytecode {
        int index = ByteArray.readU16bit(code, pos + 1);
        String className = cpool.getFieldrefClassName(index);
        String fieldName = cpool.getFieldrefName(index);
        String desc = cpool.getFieldrefType(index);
        CtClass type = descToCtClass(desc);
        CtClass targetClass = nameToCtClass(className);
        ASTree target = isStatic ? null : stackFrame[--stackTop]; 
        ASTree p = new GetField(targetClass, fieldName, type, isStatic, target);
        stackFrame[stackTop++] = p;
        if (Descriptor.dataSize(desc) > 1)
            stackFrame[stackTop++] = null;

        return 3;
    }

    private int doNEWARRAY(int pos, byte[] code) throws BadBytecode {
        CtClass type;
        switch (code[pos + 1] & 0xff) {
        case Opcode.T_BOOLEAN :
            type = CtClass.booleanType;
            break;
        case Opcode.T_CHAR :
            type = CtClass.charType;
            break;
        case Opcode.T_FLOAT :
            type = CtClass.floatType;
            break;
        case Opcode.T_DOUBLE :
            type = CtClass.doubleType;
            break;
        case Opcode.T_BYTE :
            type = CtClass.byteType;
            break;
        case Opcode.T_SHORT :
            type = CtClass.shortType;
            break;
        case Opcode.T_INT :
            type = CtClass.intType;
            break;
        case Opcode.T_LONG :
            type = CtClass.longType;
            break;
        default :
            throw new RuntimeException("bad newarray");
        }

        int s = stackTop - 1;
        stackFrame[s] = new NewArray(toArrayType(type), type, stackFrame[s]);
        return 2;
    }

    private int doMultiANewArray(int pos, byte[] code) throws BadBytecode {
        int i = ByteArray.readU16bit(code, pos + 1);
        int dim = code[pos + 3] & 0xff;
        ASTree[] sizes = new ASTree[dim];
        stackTop -= dim - 1;
        for (int d = 0; d < dim; d++)
            sizes[d] = stackFrame[stackTop - 1 + d];

        CtClass type = nameToCtClass(cpool.getClassInfo(i));
        stackFrame[stackTop - 1] = new NewArray(toArrayType(type), type, sizes);
        return 4;
    }

    private CtClass toArrayType(CtClass t) throws BadBytecode {
        try {
            return classPool.get(t.getName() + "[]");
        } catch (NotFoundException e) {
            throw new BadBytecode(e.getMessage(), e);
        }
    }

    private void doInvokeMethod(int op, int pos, byte[] code, boolean isStatic) throws BadBytecode {
        try {
            doInvokeMethod2(op, pos, code, isStatic);
        }
        catch (NotFoundException e) {
            throw new BadBytecode(e.getMessage(), e);
        }
    }

    private void doInvokeMethod2(int op, int pos, byte[] code, boolean isStatic)
        throws BadBytecode, NotFoundException
    {
        int index = ByteArray.readU16bit(code, pos + 1);
        String targetTypeName, methodName, desc;
        if (op == Opcode.INVOKEINTERFACE) {
            targetTypeName = cpool.getInterfaceMethodrefClassName(index);
            methodName = cpool.getInterfaceMethodrefName(index);
            desc = cpool.getInterfaceMethodrefType(index);
        }
        else {
            targetTypeName = cpool.getMethodrefClassName(index);
            methodName = cpool.getMethodrefName(index);
            desc = cpool.getMethodrefType(index);
        }

        CtClass[] paramTypes = Descriptor.getParameterTypes(desc, classPool);
        ASTree[] args = popArguments(paramTypes);

        ASTree target = isStatic ? null : stackFrame[--stackTop];
        CtClass returnType = Descriptor.getReturnType(desc, classPool);
        CtClass targetType = classPool.get(targetTypeName);
        Call call = new Call(target, methodName, desc, args, targetType, returnType, paramTypes,
                             op == Opcode.INVOKESPECIAL);
        if (methodName.equals(MethodInfo.nameInit) && target instanceof New) {
            // heuristics for NEW
            TmpVariable tmp = new TmpVariable(targetType, null, uniqueID.tmpVarId());
            call.setTarget(tmp);
            if (stackTop > 0 && stackFrame[stackTop - 1] instanceof New)
                ((New)stackFrame[stackTop - 1]).initialize(call, tmp, true);
            else {
                ((New)target).initialize(call, tmp, false);
                statements.add(target);
            }
        }
        else if (returnType == CtClass.voidType)
            statements.add(call);
        else {
            stackFrame[stackTop++] = call;
            if (is2wordType(returnType))
                stackFrame[stackTop++] = null;
        }
    }

    protected ASTree[] popArguments(CtClass[] paramTypes) {
        ASTree[] args = new ASTree[paramTypes.length];
        for (int i = args.length - 1; i >= 0; i--) {
            if (is2wordType(paramTypes[i]))
                stackTop--;

            args[i] = stackFrame[--stackTop];
        }
        return args;
    }

    private boolean is2wordType(CtClass c) {
        return c == CtClass.longType || c == CtClass.doubleType;
    }

    private void doInvokeDynamic(int pos, byte[] code) throws BadBytecode {
        try {
            doInvokeDynamic2(pos, code);
        } catch (NotFoundException e) {
            throw new BadBytecode(e.getMessage(), e);
        }
    }
    private void doInvokeDynamic2(int pos, byte[] code) throws BadBytecode, NotFoundException {
        int index = ByteArray.readU16bit(code, pos + 1);
        CtClass clazz = Lambda.makeLambdaClass(index, classPool, classFile);
        New obj = new New(clazz);
        TmpVariable tmp = new TmpVariable(clazz, null, uniqueID.tmpVarId());
        CtConstructor cons = clazz.getConstructors()[0];
        CtClass[] paramTypes = cons.getParameterTypes();
        Call call = new Call(tmp, MethodInfo.nameInit, cons.getMethodInfo().getDescriptor(),
                             popArguments(paramTypes), clazz, CtClass.voidType, paramTypes, true);
        obj.initialize(call, tmp, true);
        stackFrame[stackTop++] = obj;
    }
}
