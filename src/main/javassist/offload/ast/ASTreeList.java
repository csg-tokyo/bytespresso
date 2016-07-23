// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javassist.CtClass;

/**
 * A list of AStree objects.
 *
 * @param <T>       the type of the elements.
 */
public abstract class ASTreeList<T extends ASTree> extends ASTree implements Iterable<T> {
    protected ArrayList<T> elements;

    /**
     * Constructs a list of ASTree objects.
     */
    public ASTreeList() {
        elements = new ArrayList<T>();
    }

    /**
     * Constructs a list of ASTree objects.
     */
    public ASTreeList(ArrayList<T> objects) {
        elements = new ArrayList<T>(objects);
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        elements = copy(elements, map);
    }

    /**
     * Returns an iterator, which returns an element in this list.
     */
    public Iterator<T> iterator() { return elements.iterator(); }

    /**
     * Appends an element to the list.
     */
    public void add(T b) { elements.add(b); }

    /**
     * Inserts an element at the specified position.
     *
     * @param index     the position.
     * @param b         the element.
     */
    public void add(int index, T b) { elements.add(index, b); }

    /**
     * Removes an element at the specified position.
     *
     * @param index     the index of the element to be removed.
     */
    public void remove(int index) { elements.remove(index); }

    /**
     * Removes the given element.
     */
    public void remove(ASTree e) { elements.remove(e); }

    public int numChildren() { return elements.size(); }
    public ASTree child(int n) { return elements.get(n); }

    @SuppressWarnings("unchecked")
    public void setChild(int n, ASTree c) { elements.set(n, (T)c); }

    /**
     * Returns the number of the elements included in the list.
     */
    public int size() { return elements.size(); }

    /**
     * Returns the n-th element. 
     */
    public T get(int n) { return elements.get(n); }

    /**
     * Replaces the element at the specified position.
     *
     * @param n         the index of the element to replace.
     * @param value     the new value of the element.
     * @return          the old value.
     */
    public T set(int n, T value) { return elements.set(n, value); }

    /**
     * Removes all the elements.
     */
    public void clear() { elements.clear(); }

    /**
     * Returns the void type.
     */
    public CtClass type() { return CtClass.voidType; }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (ASTree e: elements)
            sb.append(e.toString());

        return sb.toString();
    }
}
