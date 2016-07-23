// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;
import javassist.offload.clang.ArrayDef;

/**
 * An array access.
 *
 * @see ArrayDef
 */
public class Array extends BinaryTree {
    private CtClass type;

    /**
     * Constructs an array access.
     */
    public Array(ASTree array, ASTree index, CtClass t) {
        super(array, index);
        type = t;
    }

    public CtClass type() { return type; }

    /**
     * Returns an array.
     */
    public ASTree array() { return left; }

    /**
     * Returns an array index.
     */
    public ASTree index() { return right; }

    public String toString() {
        return array() + "[" + index() + "]";
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
