// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;
import javassist.CtClass;

/**
 * Return statement.
 */
public class Return extends ASTree implements Jump {
    private CtClass type;
    private ASTree value;

    /**
     * Constructs a return.
     */
    public Return() {
        this(CtClass.voidType, null);
    }

    /**
     * Constructs a return.
     */
    public Return(CtClass type, ASTree ret) {
        value = ret;
        this.type = type;
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        value = copy(value, map);
    }

    /**
     * Returns {@code void} type.
     */
    public CtClass type() { return CtClass.voidType; }

    /**
     * Returns the type of the returned value.
     */
    public CtClass valueType() { return type; }

    /**
     * Returns the return value.
     */
    public ASTree value() { return value; }

    /**
     * Returns the return expression.
     */
    public ASTree expression() { return value; }

    public int numChildren() { 
        if (type == CtClass.voidType)
            return 0;
        else
            return 1;
    }

    public ASTree child(int n) {
        if (numChildren() == 1 && n == 0)
            return value;
        else
            return super.child(n);
    }

    public int outputs() { return 0; }

    public int output(int i) { throw new RuntimeException("no output"); }

    public Block jumpTo(int i) { throw new RuntimeException("no jump target"); }

    public boolean always() { return true; }

    public void setTarget(Function f) {}

    public String toString() {
        if (type == CtClass.voidType)
            return "return";
        else
            return "return " + value(); 
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
