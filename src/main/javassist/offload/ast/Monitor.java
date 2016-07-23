// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * Monitor entry/exit.
 */
public class Monitor extends UnaryTree {
    private boolean isEnter;

    /**
     * Constructs a monitor enter/exit.
     */
    public Monitor(ASTree expr, boolean isEnter) {
        super(expr);
        this.isEnter = isEnter;
    }

    /**
     * Returns true if this is a monitor enter, or
     * false if this is a monitor exit.
     */
    public boolean isEnter() { return isEnter; }

    public CtClass type() { return CtClass.voidType; }

    public String toString() {
        String inout = isEnter ? "monitor in " : "monitor out ";
        return inout + operand();
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
