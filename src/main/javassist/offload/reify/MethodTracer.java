// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

import java.util.ArrayList;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtBehavior;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.Descriptor;
import javassist.bytecode.MethodInfo;
import javassist.offload.Metaclass;
import javassist.offload.Options;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Block;
import javassist.offload.ast.Dispatcher;
import javassist.offload.ast.ForeignFunction;
import javassist.offload.ast.Function;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.IntrinsicFunction;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.Jump;
import javassist.offload.ast.UserMetaclass;
import javassist.offload.ast.Variable;

/**
 * A tracer of method body.  It constructs an abstract syntax tree (AST)
 * of the method body.
 *
 * @see BlockTracer  An AST of basic block is constructed by BlockTracer.
 */
public class MethodTracer {
    private final BasicBlock[] basicBlocks;
    private final BlockTracer[] tracers;
    private final CodeAttribute codeAttr;
    private final UniqueID uniqueID;
    private final FlowAnalyzer analyzer;
    private final JVariable[] parameters;
    private final CtBehavior method;
    private final String shortClassName;  // does not contain '.' or '$'
    private final String origMethodName;
    private final String origDescriptor;
    private final boolean isStatic;

    public MethodTracer(ClassPool pool, CtBehavior method,
                        BasicBlock[] bblocks, UniqueID uid)
        throws NotFoundException
    {
        this.method = method;
        CtClass clazz = method.getDeclaringClass();
        MethodInfo minfo = method.getMethodInfo2();
        basicBlocks = bblocks;
        tracers = new BlockTracer[bblocks.length];
        CodeAttribute ca = minfo.getCodeAttribute();
        codeAttr = ca;
        uniqueID = uid;
        analyzer = new FlowAnalyzer(uid);
        ArrayList<JVariable> params = new ArrayList<JVariable>();
        BlockTracer trace = new BlockTracer(pool, minfo.getConstPool(),
                clazz, ca.getMaxStack(), ca.getMaxLocals(),
                Descriptor.getReturnType(minfo.getDescriptor(), pool),
                uid, analyzer, basicBlocks[0], params);
        tracers[0] = trace;
        parameters = params.toArray(new JVariable[params.size()]);
        String simpleClassName = normalizeName(clazz.getSimpleName());
        shortClassName = simpleClassName;
        origDescriptor = minfo.getDescriptor();
        String mname = minfo.getName();
        isStatic = (minfo.getAccessFlags() & AccessFlag.STATIC) != 0;
        if (MethodInfo.nameInit.equals(mname))
            mname = simpleClassName;
        else if (MethodInfo.nameClinit.equals(mname))
            mname = "klass_" + simpleClassName;

        origMethodName = mname;
        // We ignore exception handling.
        // ExceptionTable catcher = ca.getExceptionTable();
    }

    public UniqueID uniqueID() { return uniqueID; }
    public String shortClassName() { return shortClassName; }
    public String originalMethodName() { return origMethodName; }
    public String originalDescriptor() { return origDescriptor; }
    public boolean isStatic() { return isStatic; }
    public CtClass returnType() { return tracers[0].returnType; }
    public JVariable[] parameters() { return parameters; }

    public int findBlock(int pos) {
        for (int i = 0; i < basicBlocks.length; i++)
            if (basicBlocks[i].offset == pos)
                return i;

        throw new RuntimeException("no such a basic block: " + pos);
    }

    public Function trace(Metaclass meta, FunctionMetaclass contextMetaclass)
        throws BadBytecode, NotFoundException
    {
        byte[] code = codeAttr.getCode();
        trace(code, 0);
        return makeFunction(meta, contextMetaclass);
    }

    private void trace(byte[] code, int index) throws BadBytecode, NotFoundException {
        BasicBlock bb = basicBlocks[index];
        BlockTracer bt = tracers[index];
        int pos = bb.offset;
        int end = pos + bb.length;
        boolean goNext = true;
        while (pos < end) {
            int oldSize = bt.statements.size(); 
            pos += bt.doOpcode(pos, code, this);
            if (bt.statements.size() > oldSize) {
                ASTree ast = bt.statements.get(bt.statements.size() - 1);
                if (ast instanceof Jump)
                    goNext = traceJmp((Jump)ast, code, bt, goNext);
            }
        }

        if (goNext) {
            int blk = findBlock(end);
            trace(code, bt, blk);
        }
    }

    private boolean traceJmp(Jump jmp, byte[] code, BlockTracer bt,
                             boolean goNext)
        throws BadBytecode, NotFoundException
    {
        if (jmp.always())
            goNext = false;

        int size = jmp.outputs();
        for (int i = 0; i < size; i++) {
            int out = jmp.output(i);
            trace(code, bt, out);
        }

        return goNext;
    }

    private void trace(byte[] code, BlockTracer bt, int next)
        throws BadBytecode, NotFoundException
    {
        if (tracers[next] == null) {
            tracers[next] = new BlockTracer(basicBlocks[next], bt);
            trace(code, next);
        }
        else
            tracers[next].merge(bt);
    }

    public Function makeNativeFunction(String body, TraceContext contexts)
        throws NotFoundException
    {
        int fid = uniqueID.functionId();
        String methodName = shortClassName + "_" + normalizeName(origMethodName)
                            + "_" + fid;
        FunctionMetaclass fm = contexts.metaclass();
        Function func = fm.makeNative(tracers[0].returnType, method,
                                      methodName, parameters, body, isStatic);
        makeParameters(func);
        return func;
    }

    public ForeignFunction makeForeignFunction(TraceContext context) {
        FunctionMetaclass fm = context.metaclass();
        ForeignFunction func = fm.makeForeignFunction(tracers[0].returnType, method,
                                                      origMethodName, parameters, isStatic);
        makeParameters(func);
        return func;
    }

    public IntrinsicFunction makeIntrinsicFunction(TraceContext context)
        throws NotFoundException
    {
        FunctionMetaclass fm = context.metaclass();
        IntrinsicFunction func = fm.makeIntrinsicFunction(tracers[0].returnType, method,
                                    normalizeName(origMethodName), parameters, isStatic);
        makeParameters(func);
        return func;
    }

    private void makeParameters(Function func) {
        int id = 1;
        for (JVariable p: parameters)
            p.setIdentifier(id++);

        func.setLocalVars(new ArrayList<Variable>());
    }

    private Function makeFunction(Metaclass meta, FunctionMetaclass contextMetaclass)
        throws NotFoundException
    {
        Function func = makeFunctionObject(meta, contextMetaclass);
        for (int i = 0; i < tracers.length; i++) {
            BlockTracer bt = tracers[i];
            Block block;
            if (bt == null)
                block = new Block(i);
            else
                block = new Block(i, bt.statements, bt.resetVariables);

            func.add(block);
        }

        for (JVariable p: parameters)
            analyzer.setVariableIdentity(p, null);

        ArrayList<Variable> vars = new ArrayList<Variable>();
        ASTWalker.functionBody(analyzer, func.body(), vars, func, func);
        func.setLocalVars(vars);
        if (Options.forLoopDetection)
            ASTWalker.findForLoop(func.body());

        return func;
    }

    private Function makeFunctionObject(Metaclass meta, FunctionMetaclass contextMetaclass)
        throws NotFoundException
    {
        int fid = uniqueID.functionId();
        CtClass returnType = tracers[0].returnType;
        String name = shortClassName + "_" + normalizeName(origMethodName) + "_" + fid;

        String arg = null;
        String[] args = null;
        Class<?> companion = null;
        if (meta != null) {
            arg = meta.arg();
            args = meta.args();
            companion = meta.companion();
            contextMetaclass = getMetaclassInstance(meta.type());
        }

        return contextMetaclass.make(returnType, method, name, parameters,
                                     isStatic, arg, args, companion);
    }

    /**
     * Gets the singleton instance of the given function metaclass. 
     */
    public static FunctionMetaclass getMetaclassInstance(Class<? extends UserMetaclass> type)
        throws NotFoundException
    {
        try {
            return (FunctionMetaclass)type.getField(FunctionMetaclass.INSTANCE).get(null);
        } catch (IllegalAccessException e) {
            throw new NotFoundException("cannot access " + type.getName()
                                        + "." + FunctionMetaclass.INSTANCE);
        } catch (NoSuchFieldException e) {
            throw new NotFoundException("cannot find " + type.getName()
                                        + "." + FunctionMetaclass.INSTANCE);
        }
    }

    /**
     * Constructs a dispatcher.
     *
     * @param context       the context used for selecting a function metaclass.
     * @param mth           the method.
     */
    public static Dispatcher makeDispatcher(TraceContext context, CtMethod mth)
        throws NotFoundException
    {
        FunctionMetaclass fm = context.metaclass();
        return fm.makeDispatcher(mth, "_call_" + MethodTracer.normalizeName(mth.getName())
                                      + "_" + context.uniqueID().functionId());
    }

    /**
     * Returns a normalized name.
     * In C, an identifier cannot contain $.  So $ is removed.
     */
    public static String normalizeName(String name) {
        return name.replace('$', '_');
    }
}
