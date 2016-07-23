// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;

import javassist.CtBehavior;
import javassist.CtClass;

/**
 * A super class of various kinds of functions.
 * One of the subclass is {@link InlinedFunction}, which
 * is the code block generated from the function body
 * after inline expansion. 
 */
public abstract class Callable extends ASTree {
    /**
     * A map from a parameter in Java to a parameter in C.
     */
    public static interface ParameterMap {
        static final int TARGET = -1;

        /**
         * Returns the index of the argument corresponding to the n-th
         * parameter of this function.
         * If the parameter corresponds to a target object,
         * then {@link #TARGET} is returned.
         */
        int parameter(int n);
    }

    private final static ParameterMap staticMethodMap
    = new ParameterMap() {
        public int parameter(int n) { return n; }
    };

    private final static ParameterMap dynamicMethodMap
    = new ParameterMap() {
        public int parameter(int n) { return n - 1; }
    };

    private CtClass returnType;
    private CtBehavior method;
    private String name;
    private JVariable[] parameters;
    private ParameterMap parameterMap;

    /**
     * Constructs a function.
     */
    protected Callable(CtClass returnType, CtBehavior method, String name, JVariable[] params, boolean isStatic) {
        this.returnType = returnType;
        this.name = name;
        this.parameters = params;
        parameterMap = isStatic ? staticMethodMap : dynamicMethodMap;
        this.method = method;
    }

    protected Callable(Callable f) {
        returnType = f.returnType;
        name = f.name;
        parameters = JVariable.shallowCopy(f.parameters);
        parameterMap = f.parameterMap;
        method = f.method;
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        if (parameters != null)
            parameters = copy(parameters, new JVariable[parameters.length], map);
    }

    /**
     * Returns the function name.  It will be a mangled name.
     */
    public String name() { return name; }

    /**
     * Renames the function.  The _opt is appended to the name.
     *
     * @param opt
     */
    public void rename(int opt) {
        name = name + "_" + opt;
    }

    /**
     * Returns the return type.
     */
    public CtClass type() { return returnType; }

    /**
     * Sets the return type to the given type.
     */
    public void setType(CtClass t) { returnType = t; }

    /**
     * Returns the called method or constructor.
     */
    public CtBehavior method() { return method; }

    /**
     * Returns the class declaring the method represented by this function.
     */
    public CtClass declaringClass() { return method.getDeclaringClass(); }

    /**
     * Returns the parameters.
     */
    public JVariable[] parameters() { return parameters; }

    /**
     * Changes the parameter map of this function.
     * The original maps  
     */
    public void setParameterMap(ParameterMap map) {
        parameterMap = map;
    }

    /**
     * Returns the index of the argument corresponding
     * to the n-th parameter of this function.
     * If the parameter corresponds to a target object,
     * then {@link ParameterMap#TARGET} is returned.  
     */
    public int parameter(int n) {
        return parameterMap.parameter(n);
    }

    /**
     * Sets the parameter list to the given one.
     */
    public void setParameters(JVariable[] paras) {
        parameters = paras;
    }

    /**
     * Returns true if the function is a static method.
     * @return
     */
    public boolean isStatic() {
        return parameterMap == staticMethodMap;
    }
}
