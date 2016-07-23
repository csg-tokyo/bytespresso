// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.offload.ast.Call;
import javassist.offload.ast.Dispatcher;
import javassist.offload.ast.Function;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.VisitorException;
import javassist.offload.lib.Util;

public class CDispatcher extends Dispatcher implements TraitCFunction {
    protected CDispatcher(CtMethod cm, String fname) throws NotFoundException {
        super(cm, fname);
    }

    @Override public FunctionMetaclass metaclass() { return CFunctionMetaclass.instance; }

    public void prototype(CodeGen gen) throws VisitorException {
        if (!hasSingleBody())
            TraitCFunction.super.prototype(gen);
    }

    public void code(CodeGen gen) throws VisitorException {
        /* If this dispatcher refers to only one method,
         * the code of the dispatcher is not generated.
         * A call to this dispatcher is translated into a call
         * to that method.  CodeGen#visit(Call) invokes Dispatcher#name(),
         * which returns the name of that method instead of
         * this dispatcher.
         */
        if (!hasSingleBody())
            TraitCFunction.super.code(gen);
    }

    /**
     * The generated code invokes the most specific method specified by
     * the type of the first parameter (the receiver object).
     */
    public void bodyCode(CodeGen gen) throws VisitorException {
        JVariable[] params = parameters();
        gen.append("  int tid = (");
        CTypeDef target = gen.typeDef(params[0].type());
        target.getHeader(gen, params[0]);
        gen.append(" >> ").append(CTypeDef.FLAG_BITS).append(");\n");
        int size = targetTypes().size();
        for (int i = 0; i < size; i++) {
            CTypeDef tdef = targetType(i);
            gen.append(i == 0 ? "  " : "  else ");
            gen.append("if (tid == ").append(tdef.typeId()).append(") ");
            Function f = functions().get(i);
            CtClass retType = type();
            if (retType != CtClass.voidType)
                gen.append("return (").append(gen.typeName(retType)).append(')');

            JVariable[] args = new JVariable[params.length - 1];
            System.arraycopy(params, 1, args, 0, params.length - 1);
            try {
                Call c = new Call(params[0], method(), args);
                c.setCalledFunction(f);
                tdef.invokeMethod(gen, c);
            }
            catch (NotFoundException e) {
                throw new VisitorException(e);
            }

            gen.append(";\n");
        }

        if (size > 0)
            gen.append("  else");

        errorInBodyCode(gen);
    }

    protected void errorInBodyCode(CodeGen gen) {
        gen.append("  { printf(\"dispatch error tid=%d: line %d\\n\", tid, __LINE__); ");
        gen.append(" exit(").append(Util.ERR_DISPATCH).append("); }\n");
    }
}
