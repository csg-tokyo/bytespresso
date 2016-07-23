// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.offload.reify.Inliner;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A code block with only a single entry but multiple
 * exits.  An exit is return, throw, goto, or a conditional branch.
 */
public class Block extends ASTreeList<ASTree> {
    private int index;
    private String label;
    private JVariable[] resetVariables;
    private int incomingJumps;

    /**
     * Constructs an empty block.
     *
     * @param i               this is the i-th block of the function body.
     */
    public Block(int i) {
        index = i;
        label = null;
        resetVariables = new JVariable[0];
        incomingJumps = 0;
    }

    /**
     * Constructs a block.
     *
     * @param i               this is the i-th block of the function body.
     * @param statements    the statements included in this block.
     * @param vars            the variables whose initial values depend on the control flow
     *                      to reach this block. 
     */
    public Block(int i, ArrayList<ASTree> statements, ArrayList<JVariable> vars) {
        super(statements);
        index = i;
        label = null;
        resetVariables = vars.toArray(new JVariable[vars.size()]);
        incomingJumps = 0;
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        if (resetVariables != null)
            resetVariables = copy(resetVariables, new JVariable[resetVariables.length], map);
    }

    /**
     * Appends a statement to the block.
     *
     * @param a		an appended statement.
     */
    public void add(ASTree a) { super.add(a); }

    /**
     * Returns the index of this block in the method body.
     */
    public int index() { return index; }

    /**
     * Sets the label of this block.
     *
     * @param seq       unique sequence number.
     * @return          the sequence number next available.
     */
    public int setLable(int seq) {
        label = "L" + seq++;
        for (ASTree ast: elements)
            seq = Inliner.updateLabels(ast, seq);

        return seq;
    }

    /**
     * Return the label of this block.
     */
    public String label() {
        if (label == null)
            label = "L" + index;

        return label;
    }

    /**
     * Returns the number of incoming jumps.
     * It does not include an incoming control flow from the previous
     * block without a jump instruction such as GOTO.
     */
    public int incomingJumps() { return incomingJumps; }

    /**
     * Records a jump statement whose destination is this block.
     */
    public void isBranchTargetOf(Jump j) {
        incomingJumps++;
    }

    /**
     * Clears the record of the incoming jump statements
     * recorded by {@link #isBranchTargetOf(Jump)}.
     */
    public void clearIncomingJumps() {
        incomingJumps = 0;
    }

    /**
     * Removes all the statements in the block.
     * Call this method if the block is dead code.
     */
    public void empty() {
        resetVariables = new JVariable[0];
        elements.clear();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(index);
        sb.append(":{reset: ");
        for (JVariable v: resetVariables)
            sb.append('v').append(v.identifier()).append(' ');

        sb.append('\n');
        for (ASTree e: elements) {
            sb.append(e.toString());
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
