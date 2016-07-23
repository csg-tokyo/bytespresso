// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.offload.javatoc.impl.JavaObjectToC;

/**
 * Field reader.
 */
public class GetField extends UnaryTree {
    private String fieldName;
    private CtClass clazz, type;
    private boolean isStatic;

    /**
     * Constructs a field-read expression.
     */
    public GetField(CtClass targetType, String fname, CtClass result,
                    boolean isStaticField, ASTree target) {
        super(isStaticField ? null : target);
        fieldName = fname;
        clazz = targetType;
        type = result;
        isStatic = isStaticField;
    }

    /**
     * Constructs a field-read expression.
     */
    public GetField(GetField gf, CtClass resultType) {
        this(gf.clazz, gf.fieldName, resultType, gf.isStatic, gf.target());
    }

    public int numChildren() { 
        if (isStatic)
            return 0;
        else
            return super.numChildren();
    }

    /**
     * Returns the field name.
     */
    public String fieldName() { return fieldName; }

    /**
     * Return the type of the field.
     */
    public CtClass type() { return type; }

    /**
     * Returns the class declaring the field.
     */
    public CtClass targetClass() { return clazz; }

    /**
     * Returns the target.  null is returned if the field
     * is static.
     */
    public ASTree target() { return operand(); }

    /**
     * Returns true if the field is static.
     */
    public boolean isStatic() { return isStatic; }

    /**
     * Returns the accessed field.
     */
    public CtField field() throws NotFoundException {
        return clazz.getField(fieldName);
    }

    public ASTree value() throws NotFoundException {
        if (isStatic)
            return objectValue(null);
        else {
            ASTree ast = operand().value();
            if (ast != null)
                if (ast instanceof ObjectConstant)
                    return objectValue(((ObjectConstant)ast).theValue());
                else if (ast instanceof New)
                    return ((New)ast).fieldValue(fieldName, clazz, type);

            return null;
        }
    }

    private ASTree objectValue(Object obj) throws NotFoundException {
        Object v = JavaObjectToC.getFieldValue(obj, clazz, fieldName, JavaObjectToC.ONLY_FINAL);
        if (v == null)
            return null;
        else if (v == JavaObjectToC.NULL)
            return new Null(clazz.getClassPool().get(Object.class.getName()));
        else
            return new ObjectConstant(v, clazz.getClassPool());
    }

    public String toString() {
        String s = isStatic ? "" : operand() + "->"; 
        return s + clazz.getName() + "#" + fieldName;
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
