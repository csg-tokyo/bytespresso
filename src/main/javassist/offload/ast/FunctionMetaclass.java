// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * A metaclass for normal function.  It works as a factory object that instantiates
 * an actual function metaobject according to the call-flow contexts.
 * The class of the actual function metaobject will not be this class
 * {@link FunctionMetaclass}.
 *
 * <p>
 * Every subclass of {@code FunctionMetaclass} has to have a static final field named
 * {@code instance}, which holds a singleton instance of the class.
 * </p>
 */
public class FunctionMetaclass implements UserMetaclass {
    /**
     * The name of the {@code instance} field.
     */
    public static final String INSTANCE = "instance";

    /**
     * The singleton instance.
     */
    public static final FunctionMetaclass instance = new FunctionMetaclass();

    protected FunctionMetaclass() {}

    /**
     * Returns true if the given function is acceptable
     * as an instance of this metaclass.
     *
     * @param f     the given function.
     */
    public boolean accepts(Function f) {
        if (f == null)
            return true;
        else
            return f.metaclass() == this;
    }

    /**
     * Makes a function.
     *
     * @param returnType        the return type.
     * @param method            the original method or constructor.
     * @param name              the name of the function.
     * @param parameters        the parameter variables.
     * @param isStatic          true if the function is static.
     * @param arg               the argument passed to {@code @Metaclass}.
     * @param args              the arguments passed to {@code @Metaclass}.
     * @param companion         the arguments passed to {@code @Metaclass}.
     */
    public Function make(CtClass returnType, CtBehavior method, String name, JVariable[] parameters,
                         boolean isStatic, String arg, String[] args, Class<?> companion)
    {
        return new Function(returnType, method, name, parameters, isStatic);
    }

    /**
     * Makes a method dispatcher.
     *
     * @param cm            the method.
     * @param fname         the name of the dispatcher.
     */
    public Dispatcher makeDispatcher(CtMethod cm, String fname)
        throws NotFoundException
    {
       return new Dispatcher(cm, fname);
    }

    /**
     * Makes a native function.
     *
     * @param returnType        the return type.
     * @param method            the method represented by this function.
     * @param name              the name of the function.
     * @param parameters        the parameter variables.
     * @param body              the function body.
     * @param isStatic          true if the function is static.
     */
    public NativeFunction makeNative(CtClass returnType, CtBehavior method, String name,
                                     JVariable[] parameters, String body, boolean isStatic)
    {
        return new NativeFunction(returnType, method, name, parameters, body, isStatic);
    }

    /**
     * Makes a foreign function.
     *
     * @param returnType        the return type.
     * @param method            the method represented by this function.
     * @param name              the name of the function.
     * @param parameters        the parameter variables.
     * @param body              the function body.
     * @param isStatic          true if the function is static.
     */
    public ForeignFunction makeForeignFunction(CtClass returnType, CtBehavior method, String name,
                                               JVariable[] parameters, boolean isStatic)
    {
        return new ForeignFunction(returnType, method, name, parameters, isStatic);
    }

    /**
     * Makes an intrinsic function.
     *
     * @param type          the return type.
     * @param fname         the name of the function.
     * @param params        the parameter variables.
     * @param isStatic      true if the function is static.
     * @param method        the intrinsic method.
     */
    public IntrinsicFunction makeIntrinsicFunction(CtClass type, CtBehavior method, String fname,
                                                   JVariable[] params, boolean isStatic)
        throws NotFoundException
    {
        return new IntrinsicFunction(type, method, fname, params, isStatic);
    }

    /**
     * Constructs an inlined function with a single-expression body.
     *
     * @param f         the original function.
     * @param body      the code being inlined.
     */
    public InlinedFunction makeInlinedFunction(Function f, ASTree body) {
        return new InlinedFunction(f, body);
    }

    /**
     * Constructs an inlined function that does not have a
     * single-expression body.
     *
     * @param f         the original function.
     * @param body      the code being inlined.
     * @param initBlock the code block for initializing parameter variables.
     *                  It is inserted into body if it is not empty.
     */
    public InlinedFunction makeInlinedFunction(Function f, Body body, Block initBlock) {
        return new InlinedFunction(f, body, initBlock);
    }
}
