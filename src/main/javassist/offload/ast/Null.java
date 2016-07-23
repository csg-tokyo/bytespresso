// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * Null constant.
 */
public class Null extends ASTree {
    private CtClass type;

	/**
	 * Constructs a null constant.
	 */
	public Null(CtClass t) { type = t; }

	public CtClass type() { return type; }

	public ASTree value() { return this; }

	public String toString() { return "null"; }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
