// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;
import javassist.CtClass;

/**
 * Switch statement.
 */
public class Switch extends UnaryTree implements Jump {
    private int labels[];
    private int jumpToBlock[], defaultToBlock;
    private Block jumpTargets[], defaultTarget;

    /**
     * Constructs a switch statement.
     * This constructor does not complete the initialization.
     * setTarget does.
     *
     * @see #setTarget(Function)
     */
    public Switch(ASTree value, int[] labels,
                  int[] jumpToBlock, int defaultToBlock) {
        super(value);
        this.labels = labels;
        this.jumpToBlock = jumpToBlock;
        this.defaultToBlock = defaultToBlock;
        this.jumpTargets = null;
        this.defaultTarget = null;
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        jumpTargets = copy(jumpTargets, new Block[jumpTargets.length], map);
        defaultTarget = copy(defaultTarget, map);
    }

    public CtClass type() { return CtClass.voidType; }

    /**
     * Returns the number of the case labels.
     */
    public int caseLabels() { return labels.length; }

    /**
     * Returns the value of the i-th case label.
     */
    public int caseLabel(int i) {
        if (i < labels.length)
            return labels[i];
        else
            throw new RuntimeException("no case label: " + i);
    }

    /**
     * Returns the block executed when the i-th case label matches.
     */
    public Block caseTarget(int i) {
        return jumpTargets[i];
    }

    /**
     * Return the block executed by default.
     */
    public Block defaultTarget() { return defaultTarget; }

    public boolean always() { return true; }

    public int outputs() {
        return jumpToBlock.length + 1;
    }

    /**
     * Returns the index of the i-th jump target.
     * The 0th target is the default.
     */
    public int output(int i) {
        if (i == 0)
            return defaultToBlock;
        else if (i < outputs())
            return jumpToBlock[i - 1];
        else
            throw new RuntimeException("no output: " + i);
    }

    /**
     * Returns a jump target.  The 0th target is the default
     * jump target. 
     */
    public Block jumpTo(int i) {
        if (i == 0)
            return defaultTarget;
        else if (i < outputs())
            return jumpTargets[i - 1];
        else
            throw new RuntimeException("no jump target: " + i);
    }

    public void setTarget(Function f) {
        jumpTargets = new Block[jumpToBlock.length]; 
        for (int i = 0; i < jumpTargets.length; i++) {
            jumpTargets[i] = f.block(jumpToBlock[i]);
            jumpTargets[i].isBranchTargetOf(this);
        }

        defaultTarget = f.block(defaultToBlock);
        defaultTarget.isBranchTargetOf(this);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("switch (");
        sb.append(operand());
        sb.append("){");
        for (int i = 0; i < labels.length; i++) {
            sb.append("\ncase ");
            sb.append(labels[i]);
            sb.append(": goto ");
            sb.append(toJumpTarget(jumpTargets[i]));
        }

        sb.append("\ndefault: goto ");
        sb.append(toJumpTarget(defaultTarget));
        sb.append(" }");
        return sb.toString();
    }

    private static String toJumpTarget(Block b) {
        return b == null ? "?" : Integer.toString(b.index());
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
