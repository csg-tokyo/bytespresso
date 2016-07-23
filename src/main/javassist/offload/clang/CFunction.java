// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.offload.ast.Block;
import javassist.offload.ast.Function;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.Jump;

/**
 * A function that will be translated into a normal C function.
 */
public class CFunction extends Function implements TraitCFunction {
    /**
     * Constructs an empty function.
     *
     * @param type      the return type.
     * @param method    the original method or constructor.
     * @param fname     the function name in C.
     * @param params    the formal parameters.
     * @param isStatic  true if the function is a static method.
     */
    protected CFunction(CtClass type, CtBehavior method, String fname,
                     JVariable[] params, boolean isStatic)
    {
        super(type, method, fname, params, isStatic);
    }

    /**
     * Constructs a function.
     * It makes a list of local variables used in the body
     * (the local variables must be TmpVaraible objects only).
     * It also set the jump target of all the Jump statements. 
     *
     * @param type      the return type.
     * @param method    the original method or constructor.
     * @param fname     the function name in C.
     * @param params    the formal parameters.  If the method is
     *                  not static, the first element is the target/receiver object.
     * @param blocks    the function body.
     * @param isStatic  the function is a static method.
     * @see Jump#setTarget(Function)
     */
    protected CFunction(CtClass type, CtBehavior method, String fname, JVariable[] params,
                        Iterable<Block> blocks, boolean isStatic)
    {
        super(type, method, fname, params, blocks, isStatic);
    }

    @Override public FunctionMetaclass metaclass() { return CFunctionMetaclass.instance; }
}
