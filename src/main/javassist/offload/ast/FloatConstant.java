// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * Float constant.
 */
public class FloatConstant extends ASTree {
    float value;

    /**
     * Constructs a float constant.
     */
    public FloatConstant(int v) { value = v; }

    /**
     * Constructs a float constant.
     */
    public FloatConstant(float v) { value = v; }

    public CtClass type() { return CtClass.floatType; }

    public ASTree value() { return this; }

    /**
     * Returns the float value.
     */
    public float floatValue() { return value; }

    public String toString() {
        return Float.toString(value) + "f";
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
