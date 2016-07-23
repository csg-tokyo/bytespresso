// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.CtClass;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Body;
import javassist.offload.ast.Call;
import javassist.offload.ast.Function;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.AdhocAST;
import javassist.offload.ast.JMethod;
import javassist.offload.ast.Variable;
import javassist.offload.ast.VisitorException;

public interface TraitCFunction extends JMethod, CallableCode {
    String name();
    JVariable[] parameters();
    int parameter(int n);
    CtClass type();
    Body body();
    Variable[] variables();

    /**
     * Generates the code of an expression for calling this
     * function.
     *
     * @param gen       the generator.
     * @param expr      the call expression.
     */
    default void callerCode(CodeGen gen, Call expr) throws VisitorException {
        gen.append(name());
        gen.append('(');
        JVariable[] params = parameters();
        ASTree[] args = expr.arguments();
        for (int i = 0; i < params.length; i++) {
            if (i > 0)
                gen.append(", ");

            int k = parameter(i);
            ASTree value;
            if (k == Function.ParameterMap.TARGET)
                value = expr.target();
            else
                value = args[k];

            CTypeDef.doCastOnValue(gen, params[i].type(), value);
        }

        gen.append(')');
    }

    /**
     * Generates the implementation of the function.
     */
    default void code(CodeGen gen) throws VisitorException {
        try {
            functionDecl(gen, false);
            gen.append(" {\n");
            bodyCode(gen);
            gen.append("}\n");
        }
        catch (RuntimeException e) {
            throw new VisitorException("in " + name(), e);
        }
    }

    /**
     * Generates the return type, the function name, and the parameters.
     *
     * @param isProto       true if the generated code is part of a prototype
     *                      declaration.
     */
    default void functionDecl(CodeGen gen, boolean isProto) throws VisitorException {
        CTypeDef.varDeclaration(gen, false, new AdhocAST(type(), name()));
        gen.append('(');
        parametersCode(gen, isProto);
        gen.append(')');
    }

    /**
     * Generates the prototype declaration.
     */
    default void prototype(CodeGen gen) throws VisitorException {
        gen.append("static ");
        functionDecl(gen, true);
        gen.append(";\n");
    }

    /**
     * Generates the code representing the parameter list.
     *
     * @param isPrototype       if it's true, the parameter name is not
     *                          written. 
     */
    default void parametersCode(CodeGen gen, boolean isPrototype)
        throws VisitorException
    {
        boolean first = true;
        for (JVariable param: parameters()) {
            if (first)
                first = false;
            else
                gen.append(", ");

            if (isPrototype)
                gen.append(gen.typeName(param.type()));
            else
                CTypeDef.varDeclaration(gen, false, param);
        }
    }

    /**
     * Generates the function body.
     * The generated code does not include braces.
     * It calls the <code>localVarsCode</code> method.
     */
    default void bodyCode(CodeGen gen) throws VisitorException {
        localVarsCode(gen);
        gen.append('\n');
        gen.append(body());
    }

    /**
     * Generates the local variable declarations.
     */
    default void localVarsCode(CodeGen gen) throws VisitorException {
        if (variables() != null)
            for (Variable v: variables()) {
                gen.append(' ');
                CTypeDef.varDeclaration(gen, false, v);
                gen.append(";\n");
            }
    }
}
