// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

public class Assign extends BinaryTree {
    private CtClass type;

    public Assign(CtClass t, ASTree left, ASTree right) {
        super(left, right);
        type = t;
    }

    public CtClass type() { return type; }

    public String toString() {
        return left() + " = " + right();
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
