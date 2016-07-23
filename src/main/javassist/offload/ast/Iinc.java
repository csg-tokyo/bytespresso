// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;

import javassist.CtClass;

/** Increment/decrement operation.
 */
public class Iinc extends ASTree {
    public static final boolean POST = true;
    public static final boolean PRE = false;
    private JVariable var;
    private int increment;
    private boolean isPost;     // i++ or ++i

    /**
     * Constructs a ++/-- operation.
     */
    public Iinc(JVariable v, int i, boolean isPostInc) {
        var = v;
        increment = i;
        isPost = isPostInc;
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        var = copy(var, map);
    }

    public CtClass type() { return CtClass.intType; }

    /**
     * Returns the operand variable.
     */
    public JVariable variable() { return var; }

    /**
     * Returns true if post increment such as {@code i++}.
     * Otherwise (for example, {@code ++i}), false.
     */
    public boolean isPostIncrement() { return isPost; }

    /**
     * The amount by that a local variable is incremented.
     */
    public int increment() { return increment; }

    public int numChildren() { return 1; }

    public ASTree child(int n) {
        if (n == 0)
            return var;
        else
            return super.child(n);
    }

    public String toString() {
        String op;
        if (increment == 1)
            op = "++";
        else if (increment == -1)
            op = "--";
        else if (!isPost)
            return "(" + var + " += " + increment + ")";
        else
            throw new RuntimeException("post increment " + increment);

        if (isPost)
            return var + op;
        else
            return op + var;
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
