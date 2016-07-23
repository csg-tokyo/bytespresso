// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.offload.ast.ForeignFunction;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.VisitorException;

public class ForeignCFunction extends ForeignFunction implements TraitCFunction {
    protected ForeignCFunction(CtClass type, CtBehavior method, String fname,
                            JVariable[] params, boolean isStatic) {
        super(type, method, fname, params, isStatic);
    }

    @Override public FunctionMetaclass metaclass() { return CFunctionMetaclass.instance; }

    /**
     * This method does not generate any code.
     */
    public void code(CodeGen code) {}

    /**
     * Generates only a comment.
     */
    public void prototype(CodeGen gen) throws VisitorException {
        gen.append("/* foregin ");
        gen.append(gen.typeName(type()));
        gen.append(' ');
        gen.append(name());
        gen.append('(');
        parametersCode(gen, true);
        gen.append("); */\n");
    }
}
