// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * Throw statement.
 */
public class Throw extends UnaryTree implements Jump {
	/**
	 * Constructs a throw statement.
	 */
	public Throw(ASTree t) {
        super(t);
    }

    public CtClass type() { return CtClass.voidType; }

    public int outputs() { return 0; }

    public int output(int i) { throw new RuntimeException("no output"); }

    public boolean always() { return true; }

    public Block jumpTo(int i) { throw new RuntimeException("no jump target"); }

    public void setTarget(Function f) {}

    public String toString() { return "throw " + operand(); }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
