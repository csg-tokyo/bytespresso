// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;

/**
 * Node that has only one child.
 */
public abstract class UnaryTree extends ASTree {
    protected ASTree child;

    /**
     * Constructs a unary tree.
     */
    public UnaryTree(ASTree t) { child = t; }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        child = copy(child, map);
    }

    /**
     * Returns the child.
     */
    public ASTree operand() { return child; }

    public int numChildren() { return 1; }

    public ASTree child(int n) {
        if (n == 0)
            return child;
        else
            return super.child(n);
    }

    public void setChild(int n, ASTree c) {
        if (n == 0)
            child = c;
        else
            super.setChild(n, c);
    }
}
