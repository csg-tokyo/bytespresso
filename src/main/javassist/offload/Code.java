// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload;

import javassist.CtBehavior;
import javassist.NotFoundException;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Call;
import javassist.offload.ast.IntrinsicFunction;
import javassist.offload.ast.ObjectConstant;
import javassist.offload.ast.StringConstant;

/**
 * Code fragment.
 *
 * <p>A class declaring an {@code Intrinsic} method has to
 * have a {@code static} field named {@code instance} and
 * its value has to be an instance of that class.
 * This instance is used as a called object when the
 * {@code Intrinsic} method is invoked. 
 *
 * @see Intrinsic
 */
public abstract class Code {
    /**
     * Returns an abstract syntax tree of the code.
     */
    public abstract ASTree getAST();

    /**
     * Returns the method if the code is a call expression.
     * 
     * @return the method that the call expression invokes.
     *      null if the code is not a call expression.
     */
    public CtBehavior getMethodIfCall() throws NotFoundException {
        ASTree tree = getAST();
        if (tree instanceof Call) {
            Call call = (Call)tree;
            return call.method();
        }

        return null;
    }

    /**
     * Returns the called object if the code is a call expression.
     */
    public Code getCalledObjectIfCall() throws NotFoundException {
        ASTree tree = getAST();
        if (tree instanceof Call) {
            Call call = (Call)tree;
            return new ASTCode(call.target()); 
        }

        return null;
    }

    /**
     * Returns the arguments if the code is a call expression.
     */
    public Code[] getArgumentsIfCall() {
        ASTree tree = getAST();
        Code[] result = null;
        if (tree instanceof Call) {
            Call call = (Call)tree;
            ASTree[] args = call.arguments();
            result = new Code[args.length];
            for (int i = 0; i < args.length; i++) {
                ASTree t = IntrinsicFunction.stripCoercion(args[i]);
                result[i] = new ASTCode(t);
            }
        }

        return result;
    }

    static class ASTCode extends Code {
        final ASTree ast;
        ASTCode(ASTree a) { ast = a; }
        public ASTree getAST() { return ast; }
    }

    /**
     * Returns the value of the expression represented by this object
     * if the value can be statically determined.  Otherwise,
     * null is returned.
     */
    public Object value() {
        try {
            int count = 10;
            ASTree v = null;
            ASTree v2 = getAST();
            while (v2 != null && v != v2 && count-- > 0) {
                v = v2;
                v2 = v2.value();
            }

            if (v instanceof ObjectConstant)
                return ((ObjectConstant)v).theValue();
            else if (v instanceof StringConstant)
                return ((StringConstant)v).theValue();
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    /**
     * Returns the object that the {@code Intrinsic} method is
     * called on.  If the method is a {@code static} method,
     * {@code null} is returned.  
     */
    public static Code calledObject() {
        return IntrinsicFunction.target();
    }

    /**
     * Returns true while the current thread is transforming
     * an {@code @Intrinsic} method.  If this method returns false,
     * then the {@code @Intrinsic} method is being executed as a normal
     * Java method; the code added to this object does not make
     * any effects.
     */
    public static boolean inTranslation() {
        return IntrinsicFunction.duringTransformation();
    }

    /**
     * Changes the call expression to invoke the given lambda
     * function.
     *
     * @param f         the lambda function that will be called.
     * @param args      the arguments passed to the lambda function.
     */
    public static void changeCaller(Object f, Code... args) {
        IntrinsicFunction.setCallerCode(f, args);
    }
}
