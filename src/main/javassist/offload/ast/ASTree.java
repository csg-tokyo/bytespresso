// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.ArrayList;
import java.util.HashMap;
import javassist.CtClass;
import javassist.NotFoundException;

/**
 * Abstract Syntax Tree node.
 */
public abstract class ASTree implements Cloneable {
	/**
	 * Returns the number of children.
	 */
    public int numChildren() { return 0; }

    /**
     * Returns the n-th child.
     */
    public ASTree child(int n) { throw new RuntimeException("no child: " + n); }

    /**
     * Sets the n-th child to the given new ASTree.
     * The parameter n must be less than this.numChildren().
     */
    public void setChild(int n, ASTree c) { throw new RuntimeException("cannot set: " + n); }

    /**
     * Returns the type of this code.
     */
    public abstract CtClass type();

    /**
     * Returns the actual value represented by this code if it is known.
     * Otherwise, null is returned.
     *
     * The returned value is either {@link ObjectConstant}, {@link New},
     * {@link Null}, or null.
     */
    public ASTree value() throws NotFoundException {
        // see also Tracer.normalize(ASTree[])
        return null;
    }

    /**
     * Makes a copy of this sub tree.
     */
    public ASTree copy() {
        return copy(this, new HashMap<ASTree,ASTree>());
    }

    /**
     * Makes a copy of the given tree.
     */
    public static <T extends ASTree> T copy(T obj) {
        return copy(obj, new HashMap<ASTree,ASTree>());
    }

    /**
     * Makes a copy of the given tree.
     */
    @SuppressWarnings("unchecked")
    protected static <T extends ASTree> T copy(T obj, HashMap<ASTree,ASTree> map) {
        if (obj == null)
            return null;

        T t = (T)map.get(obj);
        if (t == null) {
            t = (T)obj.clone2();
            map.put(obj, t);
            t.deepCopy(map);
    	}

    	return t;
    }

    protected Object clone2() {
        try {
            return clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public static ASTree[] copy(ASTree[] vars, HashMap<ASTree,ASTree> map) {
        if (vars == null)
            return null;
        else
            return copy(vars, new ASTree[vars.length], map);
    }

    /**
     * Makes a copy.
     */
    public static <T extends ASTree> T[] copy(T[] vars, T[] newVars, HashMap<ASTree,ASTree> map) {
        for (int i = 0; i < vars.length; i++)
            newVars[i] = copy(vars[i], map);

        return newVars;
    }

    /**
     * Makes a copy.
     */
    public static <T extends ASTree> ArrayList<T> copy(ArrayList<T> list, HashMap<ASTree,ASTree> map) {
        if (list == null)
            return null;

        ArrayList<T> list2 = new ArrayList<T>();
        int size = list.size();
        for (int i = 0; i < size; i++)
            list2.add(copy(list.get(i), map));

        return list2;
    }

    /**
     * Performs deep copying.  Replaces all the fields with their new copies.
     *
     * @param map		a set of already copied children.
     */
    protected void deepCopy(HashMap<ASTree,ASTree> map) {}

    /**
     * Accepts a visitor.
     */
    public abstract void accept(Visitor v) throws VisitorException;
}
