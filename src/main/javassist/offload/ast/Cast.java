// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;
import javassist.NotFoundException;

/**
 * Explicit type cast.
 */
public class Cast extends UnaryTree {
    private CtClass type;

    /**
     * Constructs a type cast.
	 */
    public Cast(CtClass t, ASTree expr) {
        super(expr);
        type = t;
    }

    public CtClass type() { return type; }

    public ASTree value() throws NotFoundException {
        return operand().value();
    }

    public String toString() {
        return "((" + type.getName() + ")" + operand() + ")";
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
