// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * A class literal.
 */
public class ClassConstant extends ASTree {
    private String className;
    private CtClass type;

    /**
     * Constructs a class literal.
     */
    public ClassConstant(String name, CtClass t) {
    	className = name;
    	type = t;
    }

    /**
     * Returns the type name of the class literal.
     */
    public String className() { return className; }

    public CtClass type() { return type; }

    public String toString() {
        return className + ".class";
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
