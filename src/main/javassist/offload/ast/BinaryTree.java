// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;

/**
 * A node that has two children.
 */
public abstract class BinaryTree extends ASTree {
    protected ASTree left, right;

    /**
     * Constructs a binary tree.
     */
    public BinaryTree(ASTree t1, ASTree t2) { left = t1; right = t2; }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        left = copy(left, map);
        right = copy(right, map);
    }

    public int numChildren() { return 2; }

    public ASTree child(int n) {
        if (n == 0)
            return left;
        else if (n == 1)
            return right;
        else
            return super.child(n);
    }

    public void setChild(int n, ASTree c) {
        if (n == 0)
            left = c;
        else if (n == 1)
            right = c;
        else
            super.setChild(n, c);
    }

    /**
     * Returns the left child.
     */
    public ASTree left() { return left; }

    /**
     * Returns the right child.
     */
    public ASTree right() { return right; }

    /**
     * Sets the left child to the given node.
     */
    public void setLeft(ASTree t) { left = t; }

    /**
     * Sets the right child to the given node.
     */
    public void setRight(ASTree t) { right = t; }
}
