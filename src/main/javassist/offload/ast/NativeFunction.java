// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtBehavior;
import javassist.CtClass;

/**
 * Native function.  It must be a static Java method.
 *
 * @see javassist.offload.Native
 */
public class NativeFunction extends Function {
    private String body;

    /**
     * Constructs a native function.
     *
     * @param type          the return type.
     * @param method        the original method.
     * @param fname         the function name.
     * @param params        the parameters.
     * @param body          the function body.
     * @param isStatic      true if the function is a static method.
     */
    protected NativeFunction(CtClass type, CtBehavior method, String fname, JVariable[] params,
                             String body, boolean isStatic)
    {
        super(type, method, fname, params, isStatic);
        this.body = body;
    }

    @Override public boolean specializable() { return false; }

    /**
     * Returns the function body.
     */
    public String functionBody() { return body; }

    public String toString() {
        return "native " + super.toString();
    }
}
