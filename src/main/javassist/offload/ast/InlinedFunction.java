// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;

/**
 * The code block generated from a function body
 * by inine expansion.
 */
public class InlinedFunction extends Callable {
    private ASTree body;
    private boolean isExpression;
    private Block initializer;
    private boolean isSimpleBlock;

    /**
     * Constructs an inlined function with a single-expression body.
     *
     * @param f         the original function.
     * @param body      the code being inlined.
     */
    protected InlinedFunction(Function f, ASTree body) {
        super(f);
        this.body = body;
        this.isExpression = true;
        this.initializer = null;
        this.isSimpleBlock = false;
    }

    /**
     * Constructs an inlined function that does not have a
     * single-expression body.
     *
     * @param f         the original function.
     * @param body      the code being inlined.
     * @param initBlock the code block for initializing parameter variables.
     *                  It is inserted into body if it is not empty.
     */
    protected InlinedFunction(Function f, Body body, Block initBlock) {
        super(f);
        this.body = body;
        this.isExpression = false;
        this.isSimpleBlock = false;
        if (initBlock.size() > 0) {
            body.add(0, initBlock);
            this.initializer = initBlock;
        }
        else
            this.initializer = null;
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        body = copy(body, map);
        initializer = copy(initializer, map);
    }

    public int numChildren() { return 1; }

    public ASTree child(int n) {
        if (n == 0)
            return body;
        else
            return super.child(n);
    }

    public void setChild(int n, ASTree c) {
        if (n == 0)
            body = c;
        else
            super.setChild(n, c);
    }

    /**
     * Returns the code being inlined.
     */
    public ASTree body() { return body; }

    /**
     * Returns true if the inlined code is an expression.
     */
    public boolean isExpression() { return isExpression; }

    /**
     * Returns a code block for initializing function
     * parameters.  It may return {@code null}. 
     */
    public Block initializer() { return initializer; }

    /**
     * Changes the value of {@code isSimpleBlock()}.
     *
     * @param b     the new value.
     */
    public void isSimpleBlock(boolean b) { isSimpleBlock = b; }

    /**
     * Returns true if the inlined function body is a simple block
     * where the resulting value is set to a local variable
     * at the end.
     */
    public boolean isSimpleBlock() { return isSimpleBlock; }

    public String toString() {
        return body.toString();
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
