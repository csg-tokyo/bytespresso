// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;
import javassist.bytecode.Opcode;

/**
 * A binary operator.
 */
public class BinaryOp extends BinaryTree {
    static final String[] names = { // IADD... LXOR
        "+", "+", "+", "+", "-", "-", "-", "-", // IADD, ...
        "*", "*", "*", "*", "/", "/", "/", "/",
        "%", "%", "%", "%",
        "-", "-", "-", "-",     // INEG, LNEG, FNEG, DNEG
        "<<", "<<", ">>", ">>", ">>>", ">>>",   // ISHL, ...
        "&", "&", "|", "|", "^", "^"            // ..., LXOR
    };

    private int operator;
    private CtClass type;

    /**
     * Constructs a binary operator.
     */
    public BinaryOp(int op, ASTree left, ASTree right) {
        super(left, right);
        operator = op;
        type = getType(op);
    }

    public String toString() {
        return "(" + left() + " " + operatorName() + " " + right() + ")";
    }

    /**
     * Returns the bytecode of the operator.
     */
    public int operator() { return operator; }

    public CtClass type() { return type; }

    static final CtClass[] types
        = { CtClass.intType, CtClass.longType, CtClass.floatType, CtClass.doubleType };

    static CtClass getType(int op) {
        if (Opcode.IADD <= op && op <= Opcode.LXOR) {
            if (op <= Opcode.DNEG)
                return types[(op - Opcode.IADD) % 4];
            else if ((op - Opcode.ISHL) % 2 == 0)
                return types[0];
            else
                return types[1];
        }
        else if (Opcode.LCMP <= op && op <= Opcode.DCMPG)
            return CtClass.intType;
        else
            throw new RuntimeException("bad operator " + op);
    }

    /**
     * Returns the operator name.
     */
    public String operatorName() {
        if (Opcode.IADD <= operator && operator <= Opcode.LXOR)
            return names[operator - Opcode.IADD];
        else if (Opcode.LCMP <= operator && operator <= Opcode.DCMPG)
            return "<=>";       // see javassist.offload.ast.Branch
        else
            throw new RuntimeException("bad operator " + operator);
    }

     public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
