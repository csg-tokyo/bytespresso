// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * Goto statement.
 */
public class Goto extends JumpNode {
    /**
     * Constructs a goto.
     * This constructor does not complete the initialization.
     * setTarget does.
     *
     * @see #setTarget(Function)
     */
    public Goto(int toIndex) {
        super(toIndex, null);
    }

    /**
     * Constructs a goto.
     */
    public Goto(Block toBlock) {
        super(toBlock.index(), toBlock);
    }

    public CtClass type() { return CtClass.voidType; }

    public boolean always() { return true; }

    public String toString() {
        return "goto " + (jumpTarget == null ? "?" : jumpTarget.index());
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
