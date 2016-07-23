// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc.impl;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.offload.ast.Function;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.AdhocAST;
import javassist.offload.ast.VisitorException;
import javassist.offload.clang.CodeGen;
import javassist.offload.clang.CTypeDef;
import javassist.offload.javatoc.Settings;

/**
 * The main function of the generated C program.
 */
public abstract class MainFunction {
    public void preamble(CodeGen gen) {}

    /**
     * If this returns false, boolSender and sender passed to generate() are null.
     */
    public abstract boolean sendResult();

    /**
     * The exit code is the value returned by the given main function
     * if it is an integer.  Otherwise, the exit code is 0.
     */
    public void generate(CodeGen gen, CTranslator.EnvSnapshot program, CtMethod method,
                         Object[] args, Settings settings)
        throws VisitorException
    {
        gen.append("\nint main(int argc, char** argv) {\n");
        prologue(gen, program);
        gen.append(settings.prologue());
        gen.heapMemory().initializer(gen);
        Function main = program.function();
        if (main.type() != CtClass.voidType) {
            CTypeDef.varDeclaration(gen, false, new AdhocAST(main.type(), "result"));
            gen.append(" = ");
        }

        gen.append(main.name());
        gen.append('(');
        JVariable[] params = main.parameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0)
                gen.append(", ");

            final Object arg = args[i];
            final CtClass argType;
            if (arg == null)
                argType = gen.getCtClass(Object.class);
            else
                argType = gen.getCtClass(arg.getClass());

            final CtClass paramType = params[i].type();
            final CtClass type;
            if (paramType.isPrimitive())
                type = paramType;
            else
                type = argType;

            String value;
            if (arg == null)
                value = "0";
            else if (type.isPrimitive())
                value = JavaObjectToC.toCvalue(arg, arg.getClass());
            else
                value = gen.isGivenObject(arg);

            CTypeDef.doCastOnValue(gen, paramType, new AdhocAST(type, value));
        }

        gen.append(");\n");
        gen.append(settings.epilogue());
        epilogue(gen, program);
    }

    protected void prologue(CodeGen gen, CTranslator.EnvSnapshot prog) {}

    protected void epilogue(CodeGen gen, CTranslator.EnvSnapshot prog) {
        if (prog.function().type() == CtClass.intType)
            gen.append(" return result; }\n");
        else
            gen.append(" return 0; }\n");
    }
}
