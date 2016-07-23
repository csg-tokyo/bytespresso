// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javassist.CtClass;
import javassist.CtBehavior;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.offload.Code;
import javassist.offload.reify.Lambda;
import javassist.offload.reify.TraceContext;

/**
 * An intrinsic function.
 *
 * @see javassist.offload.Intrinsic
 */
public class IntrinsicFunction extends Function {
    /**
     * The name of a static field referring to an instance that
     * a {@code @Intrinsic} method is called on.
     */
    public static final String INSTANCE = "instance"; 

    private Method rtMethod;    // runtime method

    /**
     * Constructs an intrinsic function.
     *
     * @param type          the return type.
     * @param method        the intrinsic method.
     * @param fname         the function name.
     * @param params        the parameters.
     * @param isStatic      true if the function is a static method.
     */
    protected IntrinsicFunction(CtClass type, CtBehavior method, String fname, JVariable[] params,
                                boolean isStatic)
        throws NotFoundException
    {
        super(type, method, fname, params, isStatic);
        this.rtMethod = findMethod(method);

        boolean isFinalClass = Modifier.isFinal(type.getModifiers());
        if (!isStatic && !isFinalClass && !Modifier.isFinal(method.getModifiers()))
            throw new NotFoundException("@Intrinsic methods have to be static or final: "
                            + method.getLongName());
    }

    private Method findMethod(CtBehavior mth) throws NotFoundException {
        try {
            Class<?> c = Class.forName(mth.getDeclaringClass().getName());
            CtClass[] ccParams = mth.getParameterTypes();
            Class<?>[] params = new Class<?>[ccParams.length];
            for (int i = 0; i < params.length; i++)
                params[i] = Class.forName(ccParams[i].getName());

            return c.getDeclaredMethod(mth.getName(), params);
        }
        catch (NotFoundException e1) { throw e1; }
        catch (Exception e2) {
            throw new NotFoundException(mth.getLongName(), e2);
        }
    }

    @Override public boolean specializable() { return false; }

    public String toString() {
        return "intrinsic " + super.toString();
    }

    /**
     * Returns the Java method corresponding to this function.
     */
    public Method javaMethod() { return rtMethod; }

    /**
     * Transforms the call site to an @Intrinsic method.
     *
     * @param expr      the call site.
     */
    public static void transformArguments(Call expr) {
        ASTree[] args = expr.arguments();
        for (int i = 0; i < args.length; i++)
            expr.setArgument(i, stripCoercion(args[i]));
    }

    /**
     * Removes coercion if it is a call expression.
     *
     * @param e     an expression.
     */
    public static ASTree stripCoercion(ASTree e) {
        if (e instanceof Call) {
            Call c = (Call)e;
            if (c.methodName().equals("valueOf")) {
                String className = c.targetType().getName();
                if (className.equals("java.lang.Boolean")
                    || className.equals("java.lang.Byte")
                    || className.equals("java.lang.Character")
                    || className.equals("java.lang.Short")
                    || className.equals("java.lang.Integer")
                    || className.equals("java.lang.Long")
                    || className.equals("java.lang.Float")
                    || className.equals("java.lang.Double")) {
                        ASTree[] args = c.arguments();
                        if (args != null && args.length == 1)
                            return args[0];
                }
            }
        }

        return e;
    }

    private static class CodeHolder {
        static final Code EMPTY = new Code() {
            public ASTree getAST() { return null; }
        };

        Code target = null;
        Code code = null;
        public Object aux = null;
    }

    private static final ThreadLocal<CodeHolder> output = new ThreadLocal<CodeHolder>() {
        @Override protected CodeHolder initialValue() { return new CodeHolder(); }
    };

    public static Code target() { return output.get().target; }

    protected static void setCodeHolderAux(Object aux) { output.get().aux = aux; }

    protected static Object codeHolderAux() { return output.get().aux; }

    /**
     * Sets the caller-side code, which will be passed to the current invocation
     * of {@code transformCallSite} method.
     */
    public static void setCallerCode(Code c) {
        CodeHolder h = output.get();
        if (h != null)
            h.code = c;
    }

    /**
     * Sets the caller-side code, which will be passed to the current invocation
     * of {@code transformCallSite} method.
     *
     * @param function          the lambda function called from the call site.
     * @param args              the arguments passed to the lambda function.
     */
    public static void setCallerCode(Object function, Code[] args) {
        ASTree[] trees = new ASTree[args.length];
        for (int i = 0; i < args.length; i++)
            trees[i] = args[i].getAST();

        setCallerCode(new CallCode(function, trees));
    }

    /**
     * Returns true while the current thread is transforming an intrinsic function.
     */
    public static boolean duringTransformation() {
        return output.get().code == CodeHolder.EMPTY;
    }

    /**
     * Generates the code for the given call site.
     * It invokes the body of the {@code @Intrinsic} method.
     */
    public void transformCallSite(Call call, TraceContext context)
        throws NotFoundException
    {
        CodeHolder holder = output.get();
        holder.code = CodeHolder.EMPTY;
        holder.target = new ASTCode(call.target());
        Throwable exception = null;
        try {
            javaMethod().invoke(javaObject(), toCode(call.arguments()));
            Code c = holder.code;
            holder.code = null;
            holder.target = null;
            if (c == null || c == CodeHolder.EMPTY)
                throw new NotFoundException(javaMethod().getName() + "() in "
                            + javaMethod().getDeclaringClass().getName() + " has an empty body.");
            else {
                translateCallSite(call, c, context);
                return;
            }
        }
        catch (IllegalAccessException e) { exception = e; }
        catch (IllegalArgumentException e) { exception = e; }
        catch (InvocationTargetException e) { exception = e.getCause(); }

        holder.code = null;
        if (exception instanceof RuntimeException)
            throw (RuntimeException)exception;
        else {
            String msg = exception.getMessage();
            if (msg == null)
                msg = exception.getClass().getName();

            throw new RuntimeException(msg, exception);
        }
    }

    private Object javaObject()
        throws IllegalArgumentException, IllegalAccessException, NotFoundException
    {
        if (java.lang.reflect.Modifier.isStatic(javaMethod().getModifiers()))
            return null;

        try {
            return javaMethod().getDeclaringClass().getField(INSTANCE).get(null);
        }
        catch (NoSuchFieldException e) {
            throw new NotFoundException("a static filed '" + INSTANCE
                        + "' is not found in " + javaMethod().getDeclaringClass());
        }
    }

    /**
     * Modifies the call expression if necessary.
     *
     * @param call      the call expression.
     * @param code      the result of the execution of the {@code @Intrinsic} method.
     */
    protected void translateCallSite(Call call, Code code, TraceContext context)
        throws NotFoundException
    {
        if (!(code instanceof CallCode))
            throw new NotFoundException("bad caller code: " + code);

        CallCode newCaller = (CallCode)code;
        Object f = newCaller.function;
        String className = Lambda.getLambdaProxyName(f.getClass().getName());
        CtClass clazz = call.method().getDeclaringClass().getClassPool().get(className);
        for (CtMethod m: clazz.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                call.resetCallee(new ObjectConstant(f, clazz), m, newCaller.arguments);
                context.heapObjects().add(f);
                context.classTable().addClass(clazz);
                return;
            }
        }

        throw new NotFoundException("no public method in " + className + " for " + this);
    }

    static class ASTCode extends Code {
        final ASTree ast;
        ASTCode(ASTree a) { ast = a; }
        public ASTree getAST() { return ast; }
    }

    protected Object[] toCode(ASTree[] args) {
        Object[] result = new Object[args.length];
        for (int i = 0; i < args.length; i++)
            result[i] = new ASTCode(args[i]);

        return result;
    }

    static class CallCode extends Code {
        Object function;
        ASTree[] arguments;

        CallCode(Object f, ASTree[] args) {
            function = f;
            arguments = args;
        }

        public ASTree getAST() { return null; }
    }
}
