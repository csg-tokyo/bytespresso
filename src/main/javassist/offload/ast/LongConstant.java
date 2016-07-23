// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * Long constant.
 */
public class LongConstant extends ASTree {
    private long value;

    /**
     * Constructs a long constant.
     */
    public LongConstant(long v) { value = v; }

    public CtClass type() { return CtClass.longType; }

    public ASTree value() { return this; }

    /**
     * Returns the long value.
     */
    public long longValue() { return value; }

    public String toString() { return Long.toString(value); }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
