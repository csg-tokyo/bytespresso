// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Call;
import javassist.offload.ast.GetField;
import javassist.offload.ast.ObjectConstant;
import javassist.offload.ast.UserMetaclass;
import javassist.offload.ast.Variable;
import javassist.offload.ast.VisitorException;
import javassist.offload.javatoc.impl.JavaObjectToC;

/**
 * A metaclass for native array classes.
 * It translates an array class into a multi-dimensional array in C
 * if it is constructed at the Java side.
 *
 * <p>Do not overload the methods {@code get}, {@code set}, or {@code initData}.
 * {@code initData} method must be private.
 *
 * @see javassist.offload.lib.DoubleArray2D
 */
public class NativeMultiArrayClass extends ClassDef implements UserMetaclass {
    public static final String DATA_FIELD = "data";
    public static final String INIT_DATA = "initData";      // must be private
    public static final String GET = "get";
    public static final String SET = "set";

    protected String typeName;
    protected String[] sizes;

    /**
     * Constructs a metaclass.
     * arg and args are passed through @Metaclass.
     *
     * @param arg       the element type.
     * @param args      the names of the fields specifying the array size for each dimension.
     */
    public NativeMultiArrayClass(CtClass cc, CtField[] f, int uid, String arg, String[] args, Class<?> companion) {
        super(cc, f, uid);
        this.typeName = arg;
        this.sizes = args;
    }

    public void objectToCode(Object obj, JavaObjectToC jo, CodeGen gen,
                             Class<?> klass, String gvarName)
        throws IllegalAccessException, VisitorException
    {
        Object array = JavaObjectToC.getFieldValue(obj, klass, DATA_FIELD, false);
        if (array == null)
            throw new VisitorException("not found field: " + klass.getName() + "." + DATA_FIELD);

        String gvarName2 = gvarName + "_" + DATA_FIELD;
        gen.recordGivenObject(array, gvarName2);
        declareArrayHead(obj, gen, klass, gvarName2);
        for (int i = 0; i < sizes.length; i++) {
            String s = JavaObjectToC.getFieldValue(obj, klass, sizes[i], false).toString();
            gen.append('[').append(s).append(']');
        }

        gen.append(" __attribute__((aligned(64)));\n");
        super.objectToCode(obj, jo, gen, klass, gvarName);
    }

    protected void declareArrayHead(Object obj, CodeGen gen, Class<?> klass, String gvarName2)
        throws VisitorException
    {
        gen.heapMemory().declarationCode(gen, obj, this, typeName, gvarName2);
    }

    public boolean isMacro(Call expr) throws NotFoundException {
        String methodName = expr.methodName();
        if (INIT_DATA.equals(methodName))
            return true;
        else if (GET.equals(methodName) || SET.equals(methodName))
            return canGetArrayVarName(expr) || isConstantTarget(expr.target());
        else
            return super.isMacro(expr);
    }

    public void invokeMethod(CodeGen gen, Call expr) throws VisitorException {
        String methodName = expr.methodName();
        if (INIT_DATA.equals(methodName))
            doInitData(gen, expr);
        else if (GET.equals(methodName))
            doGet(gen, expr);
        else if (SET.equals(methodName))
            doSet(gen, expr);
        else
            super.invokeMethod(gen, expr);
    }

    /**
     * Generates the code for allocating an array and set DATA_FIELD
     * to the array.
     */
    private void doInitData(CodeGen gen, Call expr) throws VisitorException {
        targetObject(gen, expr);
        gen.append(fieldName(gen, DATA_FIELD)).append('=');
        gen.append(gen.heapMemory().malloc());
        gen.append("(sizeof(");
        gen.append(typeName);
        gen.append(")");
        for (int i = 0; i < sizes.length; i++) {
            gen.append(" * ");
            targetObject(gen, expr);
            gen.append(fieldName(gen, sizes[i]));
        }

        gen.append(")");
    }

    /**
     * Generates the code like "v13->".
     */
    private void targetObject(CodeGen gen, Call expr) throws VisitorException {
        expr.target().accept(gen);
        gen.append("->");
    }

    /**
     * Generates the code for the get method.
     */
    private void doGet(CodeGen gen, Call expr) throws VisitorException {
        String var = getArrayVarName(gen, expr);
        if (var != null)
            doInlinedGet(gen, expr, var);
        else if (isConstantTarget(expr.target())) {
            gen.append("((").append(typeName).append("*)");
            targetObject(gen, expr);
            gen.append(this.fieldName(gen, DATA_FIELD)).append(")[");
            arrayOffset(gen, expr, expr.arguments(), sizes.length - 1);
            gen.append("]");
        }
        else
            super.invokeMethod(gen, expr);
    }

    /**
     * Returns true if the given expression is side-effect
     * free.
     *
     * @param expr      the expression.
     */
    private boolean isConstantTarget(ASTree expr) {
        if (expr instanceof Variable)
            return true;
        else if (expr instanceof GetField)
            return isConstantTarget(((GetField)expr).target());
        else
            return false;
    }

    private void doInlinedGet(CodeGen gen, Call expr, String target)
        throws VisitorException
    {
        gen.append(target);
        ASTree[] args = expr.arguments();
        for (int i = 0; i < args.length; i++) {
            gen.append('[');
            args[i].accept(gen);
            gen.append(']');
        }
    }

    /**
     * Generates the code for the set method.
     */
    private void doSet(CodeGen gen, Call expr) throws VisitorException {
        String var = getArrayVarName(gen, expr);
        if (var != null) {
            doInlinedSet(gen, expr, var);
            return;
        }
        else if (isConstantTarget(expr.target())) {
            gen.append("((").append(typeName).append("*)");
            targetObject(gen, expr);
            gen.append(fieldName(gen, DATA_FIELD)).append(")[");
            arrayOffset(gen, expr, expr.arguments(), sizes.length - 1);
            gen.append("] = (");
            expr.arguments()[sizes.length].accept(gen);
            gen.append(')');
        }
        else
            super.invokeMethod(gen, expr);
    }

    private void doInlinedSet(CodeGen gen, Call expr, String target)
        throws VisitorException
    {
        gen.append(target);
        ASTree[] args = expr.arguments();
        for (int i = 0; i < args.length - 1; i++) {
            gen.append('[');
            args[i].accept(gen);
            gen.append(']');
        }

        gen.append(" = (");
        expr.arguments()[args.length - 1].accept(gen);
        gen.append(')');
    }

    /**
     * Returns the name of the global variable representing an array body
     * if it is statically allocated. 
     */
    private String getArrayVarName(CodeGen gen, Call expr)
        throws VisitorException
    {
        ASTree target;
        try {
            target = expr.target().value();
        }
        catch (NotFoundException e) {
            throw new VisitorException(e);
        }

        if (target != null && target instanceof ObjectConstant) {
            Object obj = ((ObjectConstant)target).theValue();
            Object array = JavaObjectToC.getFieldValue(obj, obj.getClass(), DATA_FIELD, false);
            return gen.isGivenObject(array);
        }

        return null;
    }
     
    private boolean canGetArrayVarName(Call expr) throws NotFoundException {
        ASTree target = expr.target().value();
        return target != null && target instanceof ObjectConstant;
    }

    private void arrayOffset(CodeGen gen, Call expr, ASTree[] args, int i) throws VisitorException {
        if (i > 0) {
            arrayOffset(gen, expr, args, i - 1);
            for (int j = i; j < sizes.length; j++) {
                gen.append(" * ");
                targetObject(gen, expr);
                gen.append(fieldName(gen, sizes[j]));
            }
            gen.append(" + ");
        }

        gen.append('(');
        args[i].accept(gen);
        gen.append(')');
    }
}
