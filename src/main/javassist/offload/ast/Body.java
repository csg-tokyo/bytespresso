// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

/**
 * Method/function body.
 */
public class Body extends ASTreeList<Block> implements Iterable<Block> {
    /**
     * Constructs a method body.
     */
    public Body() {}

    /**
     * Appends a block to the method body.
     */
    public void add(Block b) { super.add(b); }

    /**
     * Removes a block at the specified position.
     *
     * @param index     the index of the block to be removed.
     */
    public void remove(int index) { super.remove(index); }

    public void setChild(int n, ASTree c) { super.setChild(n, (Block)c); }

    /**
     * Returns the n-th block. 
     */
    public Block block(int n) { return get(n); }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (ASTree e: elements) {
            sb.append(e.toString());
            sb.append("\n");
        }

        return sb.toString();
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
