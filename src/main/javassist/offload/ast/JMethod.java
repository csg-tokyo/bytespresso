// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtClass;

/**
 * Method-like function. 
 */
public interface JMethod {
    String name();
    JVariable[] parameters();
    CtClass type();
    Body body();
    Variable[] variables();

    /**
     * @see Callable#parameter(int)
     */
    int parameter(int n);
}
