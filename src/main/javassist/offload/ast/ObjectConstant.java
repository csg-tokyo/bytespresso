// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.offload.reify.Lambda;

/**
 * An object value.
 * The value represented by this AST node is directly given by a plain Java object.
 */
public class ObjectConstant extends ASTree {
    private CtClass type;
    private Object value;

    /**
     * Represents a value dynamically determined.
     */
    static final ObjectConstant UNKNOWN = new ObjectConstant();

    private ObjectConstant() {
        type = null;
        value = null;
    }

    public ObjectConstant(Object v, CtClass t) throws NotFoundException {
        value = v;
        type = t;
    }

    public ObjectConstant(Object v, ClassPool cp) throws NotFoundException {
        value = v;
        String typeName = v.getClass().getName();
        type = cp.get(Lambda.getLambdaProxyName(typeName));
    }

    public CtClass type() { return type; }

    public ASTree value() { return this; }

    public Object theValue() { return value; }

    public String toString() {
        return "Object[" + value + "]";
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
