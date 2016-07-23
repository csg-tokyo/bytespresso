// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;

import javassist.CtClass;
import javassist.bytecode.Opcode;
import static javassist.bytecode.Opcode.*;

/**
 * A conditional branch.
 */
public class Branch extends BinaryTree implements Jump {
    private int operator;
    private Goto thenGoto;

    static final String[] names = {
        "==", "!=", "<", ">=", ">", "<=",       // IFEQ, ...
        "==", "!=", "<", ">=", ">", "<=",       // IF_ICMPEQ, ...
        "==", "!="                              // IF_ACMPEQ, IF_ACMPNE
    };

    static final int[] negationOp = {
        IFNE, IFEQ, IFGE, IFLT, IFLE, IFGT,       // IFEQ, ...
        IF_ICMPNE, IF_ICMPEQ, IF_ICMPGE, IF_ICMPLT, IF_ICMPLE, IF_ICMPGT,   // IF_ICMPEQ, ...
        IF_ACMPNE, IF_ACMPEQ                      // IF_ACMPEQ, IF_ACMPNE
    };

    /**
     * Constructs a conditional branch.
     * This constructor does not complete the initialization.
     * setTarget does.
     *
     * @see #setTarget(Function)
     */
    public Branch(int op, ASTree left, ASTree right, int thenBlock) {
        super(left, right);
        this.operator = op;
        this.thenGoto = new Goto(thenBlock);
    }

    /**
     * Constructs a branch if the operator is either IFEQ, IFNE, IFLT,
     * IFGE, IFGT, or IFLE.  This constructor constructs a special
     * tree if the expression is LCMP, etc.
     */
    public Branch(int op, ASTree expr, int thenBlock) {
        this(op, expr, IntConstant.ZERO, thenBlock);
        if (expr instanceof BinaryOp) {
            BinaryOp bop = (BinaryOp)expr;
            int cmp = bop.operator();
            if (Opcode.LCMP <= cmp && cmp <= Opcode.DCMPG) {
                setLeft(bop.left());
                setRight(bop.right());
            }
        }
    }

    Branch(Branch b, Goto g) {
        super(b.left, b.right);
        operator = b.operator;
        thenGoto = g;
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        thenGoto = copy(thenGoto, map);
    }

    /**
     * Returns the comparison operator.
     */
    public int operator() { return operator; }

    /**
     * Returns the name of the comparison operator.
     */
    public String operatorName() {
        if (operator <= Opcode.IF_ACMPNE)
            return names[operator - Opcode.IFEQ];
        else if (operator == Opcode.IFNULL)
            return "==";
        else if (operator == Opcode.IFNONNULL)
            return "!=";
        else
            throw new RuntimeException("bad operator: " + operator);
    }

    /**
     * Makes a branch instruction that jumps if the branch condition
     * of this branch is not satisfied.
     */
    public Branch makeNegation() {
        Branch b = new Branch(this, this.thenGoto);
        b.operator = this.negationOperator();
        return b;
    }

    /**
     * Returns the negation of the operator.
     */
    private int negationOperator() {
        if (operator <= Opcode.IF_ACMPNE)
            return negationOp[operator - Opcode.IFEQ];
        else if (operator == Opcode.IFNULL)
            return Opcode.IFNONNULL;
        else if (operator == Opcode.IFNONNULL)
            return Opcode.IFNULL;
        else
            throw new RuntimeException("bad operator: " + operator);
    }

    public String toString() {
        return "if (" + conditionToString() + ") "
               + thenGoto.toString();
    }

    /**
     * Returns the {@code String} representation of
     * the branch condition.
     */
    public String conditionToString() {
        return left().toString() + ' ' + operatorName() + ' ' + right().toString();
    }

    public CtClass type() { return CtClass.voidType; }

    public boolean always() { return false; }

    public int outputs() {
        return thenGoto.outputs();
    }

    public int output(int i) { return thenGoto.output(i); }

    /**
     * Returns the index of the jump target.
     */
    public int output() { return thenGoto.output(); }

    /**
     * Returns the jump target.
     */
    public Block jumpTo() { return thenGoto.jumpTo(); }

    public Block jumpTo(int i) { return thenGoto.jumpTo(i); }

    public void setTarget(Function f) { thenGoto.setTarget(f); }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
