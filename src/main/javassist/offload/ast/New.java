// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;

import javassist.CtClass;
import javassist.CtField;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;

/**
 * A new expression.
 */
public class New extends ASTree {
    private CtClass type;
    private Call constructor;
    private TmpVariable tmpVar;
    private boolean returnThis;
    private ASTree isStatement;      // non-null if the call constructs an expression statement.

    /**
     * Constructs a new expression.
     */
    public New(CtClass t) {
        type = t;
        constructor = null;
        tmpVar = null;
        returnThis = true;
        isStatement = null;
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        constructor = copy(constructor, map);
        tmpVar = copy(tmpVar, map);
        isStatement = copy(isStatement, map);
    }

    public CtClass type() { return type; }

    /**
     * Returns the actual value represented by this code.
     * Although the actual value is an object, we use
     * an AST to represent it.
     *   
     */
    public ASTree value() throws NotFoundException { return this; }

    /**
     * Returns a temporary variable declared for this
     * expression.
     */
    public TmpVariable temporaryVariable() { return tmpVar; }

    /**
     * Returns the constructor call.
     */
    public Call constructor() { return constructor; }

    /**
     * Returns the descriptor of the constructor.
     */
    public String descriptor() { return constructor.descriptor(); }

    /**
     * Returns the arguments to the constructor.
     */
    public ASTree[] arguments() { return constructor.arguments(); }

    /**
     * Returns the expected argument types for the constructor.
     */
    public CtClass[] parameterTypes() { return constructor.parameterTypes(); }

    /**
     * Returns true if the {@code new} expression returns a reference
     * to a created object.
     */
    public boolean doesReturnThis() { return returnThis; }

    /**
     * Internal-use only.
     *
     * @param call      a constructor call.
     * @param v         a TmpVariable object or null.
     * @param retThis   true  if the expression returns a reference
     *                  to a created object. 
     */
    public void initialize(Call call, TmpVariable v, boolean retThis) {
        constructor = call;
        tmpVar = v;
        returnThis = retThis;
    }

    public int numChildren() {
        int num = tmpVar == null ? 0 : 1;
        if (constructor == null)
            return num;
        else
            return num + 1;
    }

    public ASTree child(int n) {
        if (n == 0) {
            if (tmpVar != null)
                return tmpVar;
            else if (constructor != null)
                return constructor;
        }
        else if (n == 1 && tmpVar != null && constructor != null)
            return constructor;

        return super.child(n);
    }

    public void setChild(int n, ASTree c) {
        if (n == 0)
            if (tmpVar != null)
                tmpVar = (TmpVariable)c;
            else if (constructor != null)
                constructor = (Call)c;
            else
                super.setChild(n, c);
        else if (n == 1 && tmpVar != null && constructor != null)
            constructor = (Call)c;
        else
            super.setChild(n, c);
    }

    /**
     * Changes the result of {@link #isStatement()}.
     *
     * @param ast       the immediate parent, {@link Block}, {@link Assign},
     *                  or {@link Return}.
     * @see Call#isStatement(ASTree)
     */
    public void isStatement(ASTree ast) {
        isStatement = ast;
        if (constructor != null)
            constructor.isStatement(this);
    }

    /**
     * Returns non-null if this constructs an expression statement, 
     * i.e. if the immediate parent is {@code Block} or if the parent
     * is {@code Assign} or {@code Return} and the grand parent is {@link Block}.
     * Otherwise, null is returned.
     *
     * @see Call#isStatement()
     */
    public ASTree isStatement() { return isStatement; }

    /**
     * Returns the called constructor.
     *
     * @return null if the constructor is unknown.
     */
    public Callable calledConstructor() {
        if (constructor == null)
            return null;
        else
            return constructor.calledFunction();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("new ");
        sb.append(type.getName());
        sb.append('(');
        boolean first = true;
        if (constructor == null)
            sb.append("??");
        else
            for (ASTree arg: arguments()) {
                if (first)
                    first = false;
                else
                    sb.append(", ");
                
                sb.append(arg);
            }

        sb.append(')');
        return sb.toString();
    }

    /**
     * Returns the value of the specified field if the field is a final field
     * and the value is determined by the arguments to the constructor.
     * Otherwise, this method returns null.
     *
     * @return an ObjectConstant, a New, or null.
     */
    ASTree fieldValue(String fieldName, CtClass declaringClass, CtClass fieldType)
        throws NotFoundException
    {
        String desc = Descriptor.of(fieldType);
        CtField f;
        try { 
            f = declaringClass.getField(fieldName, desc);
        }
        catch (NotFoundException e) {
            f = declaringClass.getField(fieldName);
        }

        if (!Modifier.isFinal(f.getModifiers()))
            return null;

        Callable cons = calledConstructor();
        if (cons == null)
            return null;

        /*
         * If the called constructor does not change,
         * fieldValue() must return the same value.
         *
         * See FunctionTable#equivalent(ASTree,ASTree). 
         */
        ASTree value = visitConsElements(cons, fieldName, f.getType());
        if (value == ObjectConstant.UNKNOWN)
            return null;
        else
            return value;
    }

    private static ASTree visitConsElements(ASTree tree, String fieldName, CtClass fieldType)
        throws NotFoundException
    {
        ASTree value = null;
        int n = tree.numChildren();
        for (int i = 0; i < n; i++) {
            ASTree v = visitConsElements(tree.child(i), fieldName, fieldType);
            if (v != null)
                if (v == ObjectConstant.UNKNOWN)
                    return v;
                else if (value == null)
                    value = v;
                else if (value != v)
                    return ObjectConstant.UNKNOWN;
        }

        if (tree instanceof PutField) {
            PutField pf = (PutField)tree;
            ASTree target = pf.target();
            if (target != null && target instanceof JVariable && ((JVariable)target).isThis()
                && pf.fieldType() == fieldType && pf.fieldName().equals(fieldName)) {
                ASTree v = pf.value().value();
                if (v != null)
                    if (value == null
                        && (v instanceof ObjectConstant || v instanceof New || v instanceof Null)
                        && v != ObjectConstant.UNKNOWN)
                        value = v;
                    else if (v == value)
                        ;
                    else
                        return ObjectConstant.UNKNOWN;
            }
        }

        return value;
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
