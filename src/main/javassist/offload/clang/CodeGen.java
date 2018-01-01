// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import java.util.HashMap;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.Opcode;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Callable;
import javassist.offload.ast.AdhocASTList;
import javassist.offload.ast.Array;
import javassist.offload.ast.Assign;
import javassist.offload.ast.BinaryOp;
import javassist.offload.ast.Block;
import javassist.offload.ast.Body;
import javassist.offload.ast.Branch;
import javassist.offload.ast.Call;
import javassist.offload.ast.Cast;
import javassist.offload.ast.ClassConstant;
import javassist.offload.ast.Coercion;
import javassist.offload.ast.Comma;
import javassist.offload.ast.DoubleConstant;
import javassist.offload.ast.FloatConstant;
import javassist.offload.ast.For;
import javassist.offload.ast.Function;
import javassist.offload.ast.GetField;
import javassist.offload.ast.Goto;
import javassist.offload.ast.Iinc;
import javassist.offload.ast.InlinedFunction;
import javassist.offload.ast.InstanceOf;
import javassist.offload.ast.IntConstant;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.LongConstant;
import javassist.offload.ast.Monitor;
import javassist.offload.ast.New;
import javassist.offload.ast.NewArray;
import javassist.offload.ast.Null;
import javassist.offload.ast.ObjectConstant;
import javassist.offload.ast.PutField;
import javassist.offload.ast.Return;
import javassist.offload.ast.AdhocAST;
import javassist.offload.ast.StringConstant;
import javassist.offload.ast.Switch;
import javassist.offload.ast.Throw;
import javassist.offload.ast.TmpVariable;
import javassist.offload.ast.UnaryOp;
import javassist.offload.ast.Variable;
import javassist.offload.ast.Visitor;
import javassist.offload.ast.VisitorException;
import javassist.offload.javatoc.impl.ClassTableForC;
import javassist.offload.javatoc.impl.OutputCode;
import javassist.offload.reify.Inliner;
import javassist.offload.reify.Lambda;

/**
 * Code generator for C.
 */
public class CodeGen implements Visitor {
    /**
     * The C function for LCMP.
     */
    public static final String LCMP = "jvm_lcmp";

    /**
     * The C function for FCMPL and FCMPG.
     */
    public static final String FCMP = "jvm_fcmp";

    /**
     * The C function for DCMPL and DCMPG.
     */
    public static final String DCMP = "jvm_dcmp";

    private ClassPool cpool;
    private OutputCode out;
    private ClassTableForC classTable;
    private HashMap<Object,String> givenJavaObjects;
    private HeapMemory heap;

    /**
     * True if this is a little endian system.
     */
    public final boolean littleEndian;

    /**
     * Constructs a code generator.
     */
    public CodeGen(ClassPool cp, OutputCode oc, boolean endian, HeapMemory h) throws NotFoundException {
        cpool = cp;
        out = oc;
        classTable = new ClassTableForC(cp);
        givenJavaObjects = new HashMap<Object,String>();
        heap = h;
        littleEndian = endian;
    }

    /**
     * Constructs a copy of the given code generator except the output object. 
     *
     * @param oc        a new output object.
     */
    public CodeGen(CodeGen src, OutputCode oc) {
        cpool = src.cpool;
        out = oc;
        classTable = src.classTable;
        givenJavaObjects = src.givenJavaObjects;
        heap = src.heap;
        littleEndian = src.littleEndian;
    }

    public CodeGen append(char c) { out.append(c); return this; }
    public CodeGen append(int i) { out.append(i); return this; }
    public CodeGen append(String s) { out.append(s); return this; }

    public CodeGen append(ASTree a) throws VisitorException {
        a.accept(this);
        return this;
    }

    public HeapMemory heapMemory() { return heap; }

    /**
     * Returns an expression for obtaining a pointer to the object
     * if the given object is a Java object passed at decompilation time.  
     */
    public String isGivenObject(Object obj) {
        return givenJavaObjects.get(obj);
    }

    /**
     * Records an expression for obtaining a pointer to an object passed
     * from Java.
     *
     * @param obj           the Java object.
     * @param expr          an expression for obtaining a pointer.
     */
    public void recordGivenObject(Object obj, String name) { givenJavaObjects.put(obj, name); }

    public ClassTableForC classTable() { return classTable; }
    public CTypeDef typeDef(CtClass t) { return classTable.typeDef(t); }
    public String typeName(CtClass t) { return classTable.typeName(t, false); }
    public String typeName(CtClass t, boolean useVoid) { return classTable.typeName(t, useVoid); }

    public CTypeDef addClass(CtClass type) throws VisitorException {
        try {
            return classTable.addClass(type);
        } catch (NotFoundException e) {
            throw new VisitorException(e);
        }
    }

    public CtClass getCtClass(Class<?> klass) throws VisitorException {
        try {
            String name = klass.getName();
            return cpool.get(Lambda.getLambdaProxyName(name));
        }
        catch (NotFoundException e) {
            throw new VisitorException(e);
        }
    }

    /*
     * visitor methods(non-Javadoc)
     */

    public void visit(AdhocASTList list) throws VisitorException {
        for (Object code: list.elements())
            if (code instanceof ASTree)
                ((ASTree)code).accept(this);
            else
                out.append(code.toString());
    }

    public void visit(Array a) throws VisitorException {
        ArrayDef.code(this, a);
    }

    public void visit(Assign expr) throws VisitorException {
        boolean done = codeOfInlinedExpr(this, expr.right());
        if (!done) {
            CTypeDef t = typeDef(expr.type());
            if (t == null)
                CTypeDef.doAssign0(this, expr);
            else
                t.doAssign(this, expr);
        }
    }

    public void visit(BinaryOp expr) throws VisitorException {
        int operator = expr.operator(); 
        if (operator == Opcode.LCMP)
            cmpCode(expr, LCMP);
        else if (operator == Opcode.FCMPG || operator == Opcode.FCMPL)
            cmpCode(expr, FCMP);
        else if (operator == Opcode.DCMPG || operator == Opcode.DCMPL)
            cmpCode(expr, DCMP);
        else {
            out.append('(');
            expr.left().accept(this);
            out.append(' ');
            out.append(expr.operatorName());
            out.append(' ');
            expr.right().accept(this);
            out.append(')');
        }
    }

    private void cmpCode(BinaryOp expr, String func) throws VisitorException {
        out.append(func);
        out.append('(');
        expr.left().accept(this);
        out.append(',');
        expr.right().accept(this);
        out.append(')');
    }


    public void visit(Block block) throws VisitorException {
        if (block.incomingJumps() > 0) {
            out.append(' ');
            out.append(block.label());
            out.append(":\n");
        }

        for (ASTree e: block) {
            out.append("  ");
            e.accept(this);
            if (needsSemicolon(e))
                out.append(";\n");
            else
                out.append('\n');
        }
    }

    private static boolean needsSemicolon(ASTree expr) {
        if (expr instanceof Call) {
            Callable f = ((Call)expr).calledFunction();
            if (f instanceof IntrinsicCFunction
                && ((IntrinsicCFunction)f).noSemicolon())
                return false;
        }
        else if (expr instanceof For.Begin || expr instanceof For.End)
            return false;

        return true;
    }

    public void visit(Body body) throws VisitorException {
        for (ASTree e: body) {
            e.accept(this);
            out.append('\n');
        }
    }

    public void visit(Branch branch) throws VisitorException {
        out.append("if (");
        conditionCode(branch);
        out.append(") goto ");
        out.append(branch.jumpTo().label());
    }

    /**
     * Generates the code corresponding to the branch condition.
     */
    void conditionCode(Branch branch) throws VisitorException {
        CtClass leftType = branch.left().type();
        CTypeDef.doCastOnValue(this, leftType, branch.left());

        out.append(' ');
        out.append(branch.operatorName());
        out.append(' ');

        if (leftType.isPrimitive())
            branch.right().accept(this);
        else
            CTypeDef.doCastOnValue(this, leftType, branch.right());
    }

    public void visit(Call expr) throws VisitorException {
        CtClass t;
        try {
            t = expr.actualTargetType();
        } catch (NotFoundException e) {
            throw new VisitorException(e);
        }

        if (t == null)
            t = expr.targetType();

        CTypeDef def = typeDef(t);
        def.invokeMethod(this, expr);
    }

    public void visit(Cast expr) throws VisitorException {
        out.append('(');
        CTypeDef.doCastOnValue(this, expr.type(), expr.operand());
        out.append(')');
    }

    public void visit(ClassConstant expr) throws VisitorException {
        throw new VisitorException("not supported: " + expr.className() + ".class");
    }

    public void visit(Coercion expr) throws VisitorException {
        out.append("((")
           .append(typeName(expr.type()))
           .append(')');
        expr.operand().accept(this);
        out.append(')');
    }

    public void visit(Comma expr) throws VisitorException {
        out.append('(');
        int s = expr.size() - 1;
        for (int i = 0; i < s; i++) {
            expr.get(i).accept(this);
            out.append(", ");
        }

        expr.get(s).accept(this);
        out.append(')');
    }

    public void visit(DoubleConstant expr) throws VisitorException {
        out.append(Double.toString(expr.doubleValue()));
    }

    public void visit(FloatConstant expr) throws VisitorException {
        out.append(Float.toString(expr.floatValue()))
           .append('f');        
    }

    public void visit(For.Begin begin) throws VisitorException {
        out.append("for (");
        if (begin.initializer() != null)
            begin.initializer().accept(this);

        out.append("; ");
        if (begin.condition() != null)
            conditionCode(begin.condition());

        out.append("; ");
        if (begin.step() != null)
            begin.step().accept(this);

        out.append(") {");
    }

    public void visit(For.End end) throws VisitorException {
        append("; } /* for ").append(end.jumpTo().label()).append(" */");        
    }

    public void visit(For.Empty empty) throws VisitorException {}

    public void visit(Function f) throws VisitorException {
        ((TraitCFunction)f).code(this);
    }

    public void visit(GetField expr) throws VisitorException {
        CTypeDef def = typeDef(expr.targetClass());
        def.getField(this, expr);
    }

    public void visit(Goto statement) throws VisitorException {
        out.append("goto ");
        out.append(statement.jumpTo().label());
    }

    public void visit(Iinc expr) throws VisitorException {
        String op;
        int increment = expr.increment();
        JVariable var = expr.variable();
        if (increment == 1)
            op = "++";
        else if (increment == -1)
            op = "--";
        else if (!expr.isPostIncrement()) {
            out.append('(');
            visitAsLvalue(var);
            out.append(" += ");
            out.append(increment);
            out.append(')');
            return;
        }
        else
            throw new RuntimeException("post increment " + increment);

        if (expr.isPostIncrement()) {
            visitAsLvalue(var);
            out.append(op);
        }
        else {
            out.append(op);
            visitAsLvalue(var);
        }
    }

    public void visit(InlinedFunction f) throws VisitorException {
        ((CallableCode)f).code(this);
    }

    public void visit(InstanceOf expr) throws VisitorException {
        throw new VisitorException("instanceof is not supported");
    }

    public void visit(IntConstant expr) throws VisitorException {
        out.append(Integer.toString(expr.intValue()));
    }

    public void visit(JVariable var) throws VisitorException {
        if (!var.isMutable())
            try {
                ASTree value = var.value();
                if (value instanceof ObjectConstant) {
                    visit((ObjectConstant)value, value.type());
                    return;
                }
            }
            catch (NotFoundException e) {
                throw new VisitorException(e);
            }

        visitAsLvalue(var);
    }

    public void visitAsLvalue(Variable var) throws VisitorException {
        if (var instanceof JVariable)
            visitAsLvalue((JVariable)var);
        else if (var instanceof TmpVariable)
            visit((TmpVariable)var);
        else
            throw new VisitorException("invlaid ASTree type: " + var);
    }

    private void visitAsLvalue(JVariable var) {
        out.append('v');
        out.append(var.identifier());
    }

    public void visit(LongConstant expr) throws VisitorException {
        out.append(Long.toString(expr.longValue()))
           .append('L');        
    }

    public void visit(Monitor monitor) throws VisitorException {
        throw new VisitorException("synchronized is not supported");
    }

    public void visit(New expr) throws VisitorException {
        CTypeDef tdef = typeDef(expr.type());
        tdef.instantiate(this, expr);
    }

    public void visit(NewArray expr) throws VisitorException {
        ArrayDef.code(this, expr);
    }

    public void visit(Null expr) throws VisitorException {
        out.append(0);
    }

    public void visit(ObjectConstant expr) {
        out.append("0");        
    }

    /**
     * Generates the code representing the constant.
     *
     * @param toType        the type of the generated expression.
     * @return whether the code is successfully generated. 
     */
    public boolean visit(ObjectConstant expr, CtClass toType) throws VisitorException {
        Object obj = expr.theValue();
        if (obj != null) {
            if (obj instanceof Number) {
                if (obj instanceof Integer) {
                    out.append(obj.toString());
                    return true;
                }
                else if (obj instanceof Long) {
                    out.append(obj.toString()).append('L');
                    return true;
                }
                else if (obj instanceof Double) {
                    out.append(obj.toString());
                    return true;
                }
                else if (obj instanceof Float) {
                    out.append(obj.toString()).append('F');
                    return true;
                }else if (obj instanceof Byte || obj instanceof Short) {
                    out.append(obj.toString());
                    return true;
                }
            }
            else if (obj instanceof Boolean) {
                out.append(((Boolean)obj).booleanValue() ? "(!0)" : "0");
                return true;
            }
            else if (obj instanceof Character) {
                out.append('\'').append(obj.toString()).append('\'');
                return true;
            }
            else {
                final String var = isGivenObject(obj);
                if (var != null) { 
                    CTypeDef fromType = typeDef(expr.type());
                    // fromType is null if type() is an array type.
                    if (ClassDef.castNeeded(fromType, this, toType, expr)) {
                        out.append('(');
                        ClassDef.doCast(fromType, this, toType, new AdhocAST(expr.type(), var));
                        out.append(')');
                    }
                    else
                        out.append(var);

                    return true;
                }
            }
        }

        return false;
    }

    public void visit(PutField expr) throws VisitorException {
        CTypeDef def = typeDef(expr.targetClass());
        def.putField(this, expr);
    }

    private CtClass currentReturnType = null;

    public void visit(Return statement) throws VisitorException {
        CtClass oldReturnType = currentReturnType;
        if (oldReturnType == null)
            currentReturnType = statement.valueType();

        try {
            if (codeOfInlinedExpr(this, statement.expression()))
                return;
        }
        finally {
            currentReturnType = oldReturnType;
        }

        out.append("return ");
        if (statement.valueType() != CtClass.voidType) {
            CtClass retType;
            if (currentReturnType != null)
                retType = currentReturnType;
            else
                retType = statement.valueType();

            CTypeDef.doCastOnValue(this, retType, statement.value());
        }
    }

    public void visit(AdhocAST ast) throws VisitorException {
        out.append(ast.code());        
    }

    public void visit(StringConstant expr) throws VisitorException {
        StringClass.codeLiteral(this, expr.theValue());
    }

    public void visit(Switch statement) throws VisitorException {
        out.append("switch (");
        statement.operand().accept(this);
        out.append("){");
        for (int i = 0; i < statement.caseLabels(); i++) {
            out.append("\ncase ");
            out.append(statement.caseLabel(i));
            out.append(": goto ");
            out.append(statement.caseTarget(i).label());
            out.append(';');
        }

        out.append("\ndefault: goto ");
        out.append(statement.defaultTarget().label());
        out.append("; }");
    }

    public void visit(Throw statement) throws VisitorException {
        throw new RuntimeException("throw is not supported");
    }

    public void visit(TmpVariable var) {
        out.append("tmp");
        out.append(var.identifier());
    }

    public void visit(UnaryOp expr) throws VisitorException {
        if (expr.operator() == Opcode.ARRAYLENGTH)
            ArrayDef.lengthCode(this, expr.operand());
        else {
            out.append(expr.operatorName());
            expr.operand().accept(this);
        }        
    }


    /**
     * Performs code generation if the operand of Return or Assign is inlined.
     *
     * @param operand       the operand of Return or Assign.
     * @return true if the operand is inlined.  Otherwise, false.
     */
    public static boolean codeOfInlinedExpr(CodeGen out, ASTree operand)
        throws VisitorException
    {
        Call call = Inliner.toCall(operand);
        if (call == null)
            return false;

        InlinedFunction f = call.isInlined();
        if (f != null && !f.isExpression()) {
            ((CallableCode)f).callerCode(out, call);
            return true;
        }
        else
            return false;
    }

}
