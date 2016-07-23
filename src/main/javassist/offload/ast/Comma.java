// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * A comma operator in C, C++, JavaScript, ...
 * Since it is not available in Java, the decompiler never
 * creates an instance of this class. 
 */
public class Comma extends ASTreeList<ASTree> {
    /**
     * Returns the type of the last expression.
     */
    public CtClass type() {
        if (size() < 1)
            return CtClass.voidType;
        else
            return get(size() - 1).type();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        int s = size() - 1;
        for (int i = 0; i < s; i++) {
            sb.append(get(i).toString());
            sb.append(", ");
        }

        sb.append(get(s).toString());
        sb.append(')');
        return sb.toString();
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
