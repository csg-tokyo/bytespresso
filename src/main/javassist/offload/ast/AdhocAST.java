// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * An AST node representing a code fragment given as a string.
 * The decompiler never creates an instance of this class.
 * It will be created during AST transformation and code generation.
 */
public class AdhocAST extends ASTree {
    private CtClass type;
    private String code;

    /**
     * Constructs a simple AST node.
     *
     * @param t     the type of teh code fragment.
     * @param c     the code fragment.
     */
    public AdhocAST(CtClass t, String c) {
        type = t;
        code = c;
    }

    public CtClass type() { return type; }

    /**
     * Returns the code represented by this AST node.
     */
    public String code() { return code; }

    public String toString() { return code; }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
