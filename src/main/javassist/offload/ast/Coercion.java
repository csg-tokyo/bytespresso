// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * Implicit type coercion.
 */
public class Coercion extends UnaryTree {
    private CtClass fromType, toType;

    public Coercion(CtClass f, CtClass t, ASTree expr) {
        super(expr);
        fromType = f;
        toType = t;
    }

    /**
     * Returns the expected operand type.
     */
    public CtClass fromType() { return fromType; }

    /**
     * Returns the type that the operand is converted into.
     */
    public CtClass type() { return toType; }

    public String toString() {
        return "((" + toType.getName() + ")" + operand() + ")";
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
