// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;
import javassist.bytecode.Opcode;

/**
 * Unary operator.
 */
public class UnaryOp extends UnaryTree {
    private int operator;       // e.g. arraylength
    private CtClass type;

    /**
     * Constructs a unary operator.
     */
    public UnaryOp(int op, ASTree child) {
        super(child);
        operator = op;
        if (op == Opcode.ARRAYLENGTH)
            type = CtClass.intType;
        else
            type = BinaryOp.getType(op);
    }

    public CtClass type() { return type; }

    /**
     * Returns the opcode of this operator such as
     * {@code javassist.bytecode.Opcode.IADD}.
     */
    public int operator() { return operator; }

    /**
     * Returns the operator name.
     */
    public String operatorName() {
        if (operator == Opcode.ARRAYLENGTH)
            return ".length";
        else
            return BinaryOp.names[operator - Opcode.IADD];
    }

    public String toString() {
    	if (operator == Opcode.ARRAYLENGTH)
            return operand() + operatorName();
        else
            return operatorName() + operand();
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
