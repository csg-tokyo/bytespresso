// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;
import javassist.NotFoundException;

/**
 * A variable.  It is normally a SSA (simple single assignment) variable
 * unless two data-flows are merged and so the value of the variable
 * is context-dependent. 
 *
 * @see JVariable
 * @see TmpVariable
 */
public abstract class Variable extends ASTree {
    /**
     * Make a copy of the given array.
     */
    public static Variable[] shallowCopy(Variable[] array) {
        Variable[] array2 = new Variable[array.length];
        for (int i = 0; i < array.length; i++)
            array2[i] = array[i];

        return array2;
    }

    /**
     * Constructs a variable.
     */
    public Variable() {}

    /**
     * Returns the type of this variable.
     */
    public abstract CtClass type();

    /**
     * Sets the type of this variable.
     */
    public abstract void setType(CtClass t);

    /**
     * Makes this variable mutable.  Here, a mutable variable
     * is a variable whose value is control-flow dependent.
     * {@link #value()} on a mutable variable returns null.
     */
    public abstract void beMutable();

    /**
     * Returns whether the variable is mutable or not.
     */
    public abstract boolean isMutable();

    /**
     * Returns the expression to compute the initial
     * value of this variable.  It returns null if the
     * value is not statically determined
     * (i.e. the variable is not SSA).
     */
    public ASTree value() throws NotFoundException {
        ASTree val = getValue();
        int count = 0;
        while (val != null) {
            ASTree old = val;
            val = val.value();
            if (val == old || count++ > 10)
                break;      // too deeply nested 
        }

        return val;
    }

    abstract ASTree getValue();

    /**
     * Sets the value of this variable.
     * Do not call {@link #setValue()} on a mutable variable.
     *
     * @param val	a new value.
     */
    public abstract void setValue(ASTree val);

    /**
     * Returns the identification number of this variable.
     *
     * @return a positive integer.
     */
    public abstract int identifier();

    /**
     * Changes the identification number.
     *
     * @param i     new identification number.
     */
    public abstract void setIdentifier(int i);

    /**
     * Returns an object representing the identity
     * of this variable.  If {@code a.getIdentity()}
     * is equivalent to {@code b.getIdentity()}, then
     * {@code a} and {@code b} represent the same
     * variable.
     */
    public abstract ASTree getIdentity(); 
}
