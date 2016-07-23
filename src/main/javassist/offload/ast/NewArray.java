// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;
import javassist.CtClass;

/**
 * Constructs a new-array expression.
 */
public class NewArray extends ASTree {
    private CtClass arrayType, componentType;
    private ASTree[] children;

    /**
     * Constructs a new-array expression.
     */
    public NewArray(CtClass array, CtClass compo, ASTree size) {
        children = new ASTree[] { size };   // an array
        arrayType = array;
        componentType = compo;
    }

    /**
     * Constructs a new-array expression.
     */
    public NewArray(CtClass array, CtClass compo, ASTree[] sizes) {
        children = sizes;                   // multi-dimensional array
        arrayType = array;
        componentType = compo;
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        children = copy(children, map);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("new ");
        sb.append(componentType.getName());
        for (ASTree size: children) {
            sb.append('[');
            sb.append(size);
            sb.append(']');
        }

        return sb.toString();
    }

    /**
     * Returns the array type.
     */
    public CtClass type() { return arrayType; }

    /**
     * Returns the type of the component.
     */
    public CtClass componentType() { return componentType; }

    /**
     * Returns the array dimension.
     */
    public int dimension() { return numChildren(); }

    /**
     * Returns the array size.
     */
    public ASTree[] sizes() { return children; }

    public int numChildren() {
        if (children == null)
            return 0;
        else
            return children.length;
    }

    public ASTree child(int n) {
        if (n < 0 || numChildren() <= n)
            return super.child(n);
        else
            return children[n];
    }

    public void setChild(int n, ASTree c) {
        if (n < 0 || numChildren() <= n)
            super.setChild(n, c);
        else
            children[n] = c;
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
