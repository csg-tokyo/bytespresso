// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.ArrayList;
import java.util.HashMap;

import javassist.CtClass;

/**
 * A sequence of code fragments.  The fragments are given
 * as {@code Object} values.
 * The decompiler never creates an instance of this class.
 * It will be created during AST transformation and code generation.
 */
public class AdhocASTList extends ASTree {
    private CtClass type;
    private ArrayList<Object> elements;
    private int children;

    public AdhocASTList(Object... text) { this(CtClass.voidType, text); }

    public AdhocASTList(CtClass t, Object... text) {
        this.type = t;
        this.elements = new ArrayList<Object>(text.length);
        this.children = 0;
        for (Object code: text) {
            this.elements.add(code);
            if (code instanceof ASTree)
                this.children++;
        }
    }

    public CtClass type() { return type; }

    /**
     * Returns the elements of this list.  Do not modify the
     * returned list.
     */
    public ArrayList<Object> elements() { return elements; }

    /**
     * Appends an element to the end of the list.
     */
    public void add(Object e) { elements.add(e); }

    public int numChildren() { return children; }

    public ASTree child(int n) {
        int i = n;
        for (Object code: elements)
            if (code instanceof ASTree)
                if (i-- == 0)
                    return (ASTree)code;

        return super.child(n);
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        ArrayList<Object> old = elements;
        elements = new ArrayList<Object>(old.size());
        for (Object code: old)
            if (code instanceof ASTree)
                elements.add(copy((ASTree)code, map));
            else
                elements.add(code);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Object code: elements)
            sb.append(code.toString());

        return sb.toString();
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
