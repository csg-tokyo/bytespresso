// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.NativeFunction;
import javassist.offload.ast.VisitorException;

public class NativeCFunction extends NativeFunction implements TraitCFunction {
    protected NativeCFunction(CtClass type, CtBehavior method, String fname,
                           JVariable[] params, String body, boolean isStatic)
    {
        super(type, method, fname, params, body, isStatic);
    }

    @Override public FunctionMetaclass metaclass() { return CFunctionMetaclass.instance; }

    public void bodyCode(CodeGen code) throws VisitorException {
        code.append("  ").append(functionBody())
            .append('\n');
    }

    public void code(CodeGen gen) throws VisitorException {
        gen.append("/* native */ ");
        TraitCFunction.super.code(gen);
    }

    public void prototype(CodeGen gen) throws VisitorException {
        TraitCFunction.super.prototype(gen);
    }
}
