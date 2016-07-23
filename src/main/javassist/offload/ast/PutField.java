// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;

/**
 * Field writer.
 */
public class PutField extends BinaryTree {
    private String fieldName;
    private CtClass clazz, type;
    private boolean isStatic;

    /**
     * Constructs a field-write expression.
     */
    public PutField(CtClass targetClass, String fname, CtClass t, boolean isStaticField, ASTree target, ASTree value) {
        super(isStaticField ? null : target, value);
        clazz = targetClass;
        fieldName = fname;
        type = t;
        isStatic = isStaticField;
    }

    public int numChildren() { 
        if (isStatic)
            return 1;
        else
            return super.numChildren();
    }

    public ASTree child(int n) {
        return super.child(isStatic ? n + 1 : n);
    }

    /**
     * Returns true if the field is static.
     */
    public boolean isStatic() { return isStatic; }

    public CtClass type() { return CtClass.voidType; }

    /**
     * Returns the target.  null is returned if the field
     * is static.
     */
    public ASTree target() { return left(); }

    /**
     * Returns a new value assigned to the field.
     */
    public ASTree value() { return right(); }

    /**
     * Returns the class name in that the field is declared.
     */
    public CtClass targetClass() { return clazz; }

    /**
     * Returns the field name.
     */
    public String fieldName() { return fieldName; }

    /**
     * Returns the field type.
     */
    public CtClass fieldType() { return type; }

    /**
     * Returns the accessed field.
     */
    public CtField field() throws NotFoundException {
        return clazz.getDeclaredField(fieldName);
    }

    public String toString() {
        String s = clazz.getName() + "#" + fieldName + " = " + value();
        if (isStatic)
            return s;
        else
            return target() + "->" + s;
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
