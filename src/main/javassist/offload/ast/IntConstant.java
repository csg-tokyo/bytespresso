// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * Integer constant.
 */
public class IntConstant extends ASTree {
    /**
     * A zero.
     */
    public static final IntConstant ZERO = new IntConstant(0);

    private int value;

    /**
     * Constructs an integer constant.
     */
    public IntConstant(int v) { value = v; }

    public CtClass type() { return CtClass.intType; }

    public ASTree value() { return this; }

    /**
     * Obtains the integer constant.
     */
    public int intValue() { return value; }

    public String toString() { return Integer.toString(value); }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
