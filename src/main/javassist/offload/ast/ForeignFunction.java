// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtBehavior;
import javassist.CtClass;

/**
 * A foreign function.
 *
 * @see javassist.offload.Foreign
 */
public class ForeignFunction extends Function {
    /**
     * Constructs a foreign function.
     *
     * @param type          the return type.
     * @param method        the original method.
     * @param fname         the function name.
     * @param params        the parameters.
     * @param isStatic      true if the function is a static method.
     */
    protected ForeignFunction(CtClass type, CtBehavior method, String fname, JVariable[] params,
                              boolean isStatic)
    {
        super(type, method, fname, params, isStatic);
    }

    @Override public boolean specializable() { return false; }

    public String toString() {
        return "foregin " + super.toString();
    }
}
