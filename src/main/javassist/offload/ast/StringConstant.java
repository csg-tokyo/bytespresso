// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * String literal.
 */
public class StringConstant extends ASTree {
	private CtClass type;
    private String value;

    /**
     * Constructs a string literal.
     */
    public StringConstant(String v, CtClass t) { value = v; type = t; }

    public CtClass type() { return type; }

    public ASTree value() { return this; }

    /**
     * Returns the character string represented by this literal.
     */
    public String theValue() { return value; }

    public String toString() { return '"' + value + '"'; }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
