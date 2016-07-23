// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

/**
 * Jump statement.
 */
public interface Jump {
    /**
     * Returns true if this jump is an
     * unconditional branch.
     */
    boolean always();

    /**
     * Return the number of the jump targets.
     */
    int outputs();

    /**
     * Returns the index of the i-th jump target.
     * The returned index can be given to <code>block</code>
     * method in <code>Function</code>.
     *
     * @see Function#block(int)
     */
    int output(int i);

    /**
     * Returns the i-th jump target.
     */
    Block jumpTo(int i);

    /**
     * Sets jump targets.  Internal-use only.
     * Don't call this method.
     */
    void setTarget(Function f);
}
