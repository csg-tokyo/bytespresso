// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * Instanceof operation.
 */
public class InstanceOf extends UnaryTree {
    private CtClass type;

    /**
     * Constructs an instanceof operation.
     */
    public InstanceOf(CtClass t, ASTree expr) {
        super(expr);
        type = t;
    }

    public CtClass type() { return CtClass.booleanType; }

    /**
     * Returns the right operand of {@code instanceof}. 
     */
    public CtClass instanceOf() { return type; }

    public String toString() {
        return "(" + operand() + " instanceof " + type.getName() + ")";
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
