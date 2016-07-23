// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;

/**
 * A common super class of jump nodes such as {@code Goto}.
 */
public abstract class JumpNode extends ASTree implements Jump {
    protected Block jumpTarget;
    protected int jumpTo;

    /**
     * Constructs a jump node.
     */
    public JumpNode(int toIndex, Block toBlock) {
        this.jumpTo = toIndex;
        this.jumpTarget = toBlock;
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        jumpTarget = copy(jumpTarget, map);
    }

    /**
     * Return 1, which is the number of the jump targets.
     */
    public int outputs() { return 1; }

    /**
     * Returns the index of the jump target.
     */
    public int output(int i) {
        if (i == 0)
            return jumpTo;
        else
            throw new RuntimeException("no output: " + i);
    }

    /**
     * Returns the index of the jump target.
     */
    public int output() { return jumpTo; }

    /**
     * Returns the jump target.
     */
    public Block jumpTo() { return jumpTarget; }

    /**
     * Returns the i-th jump target.
     */
    public Block jumpTo(int i) {
        if (i == 0)
            return jumpTarget;
        else
            throw new RuntimeException("no jump target");
    }

    /**
     * Sets jump targets.  Internal-use only.
     * Don't call this method.
     */
    public void setTarget(Function f) {
        jumpTarget = f.block(jumpTo);
        jumpTarget.isBranchTargetOf(this);
    }
}
