// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * Double constant.
 */
public class DoubleConstant extends ASTree {
    private double value;

    /**
     * Constructs a double constant.
     */
    public DoubleConstant(int v) { value = v; }

    /**
     * Constructs a double constant.
     */
    public DoubleConstant(double v) { value = v; }

    public CtClass type() { return CtClass.doubleType; }

    public ASTree value() { return this; }

    /**
     * Returns the double value.
     */
    public double doubleValue() { return value; }

    public String toString() {
        return Double.toString(value);
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
