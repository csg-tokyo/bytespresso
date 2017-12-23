// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.lang.annotation.Annotation;
import java.util.HashMap;

import javassist.CtClass;
import javassist.CtBehavior;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.MethodInfo;
import javassist.offload.Inline;

/**
 * A method call.
 */
public class Call extends ASTree {
    // these fields should not be changed because they are used as cache keys.
    private boolean isInvokeSpecial;
    private ASTree target;
    private String methodName;
    private String descriptor;
    private ASTree[] args;
    private CtClass targetType, returnType, paramTypes[];
    private ASTree isStatement;      // non-null if the call constructs an expression statement.

    private Callable callee;
    private java.util.function.Consumer<Function> calleeTransformer;

    /**
     * Constructs a call.
     *
     * @param target        the target object or null if the method is static.
     * @param method        the called method.
     * @param args          the arguments.
     */
    public Call(ASTree target, CtMethod method, ASTree[] args) throws NotFoundException {
        this(target, method.getName(), method.getMethodInfo2().getDescriptor(), args,
             method.getDeclaringClass(), method.getReturnType(), method.getParameterTypes(), false); 
    }

    /**
     * Constructs a call.
     */
    public Call(ASTree target, String methodName, String desc, ASTree[] args,
                CtClass targetType, CtClass returnType, CtClass[] paramTypes, boolean isSpecial) {
        this.target = target;
        this.isInvokeSpecial = isSpecial;
        this.methodName = methodName;
        this.descriptor = desc;
        this.args = args;
        this.targetType = targetType;
        this.returnType = returnType;
        this.paramTypes = paramTypes;
        this.isStatement = null;
        this.callee = null;
        this.calleeTransformer = null;

        this.methodCache = null;
        this.methodArgCache = null;
        this.actualTypeCacheValid = false;
        this.actualTypeCache = null;
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        target = copy(target, map);
        args = copy(args, map);
        isStatement = copy(isStatement, map);
        // not copy callee
    }

    public void resetCallee(ASTree target, CtMethod method, ASTree[] args) throws NotFoundException {
        this.target = target;
        this.isInvokeSpecial = false;
        this.methodName = method.getName();
        this.descriptor = method.getMethodInfo2().getDescriptor();
        this.args = args;
        this.targetType = method.getDeclaringClass();
        this.returnType = method.getReturnType();
        this.paramTypes = method.getParameterTypes();
        this.callee = null;

        // this.isStatement = null;
        // this.calleeTransformer = null;

        this.methodCache = null;
        this.methodArgCache = null;
        this.actualTypeCacheValid = false;
        this.actualTypeCache = null;
    }

    /**
     * Returns the target.  It returns null if the method is
     * static.
     */
    public ASTree target() { return target; }

    /**
     * Sets the expression computing the target.
     */
    public void setTarget(ASTree t) {
        actualTypeCacheValid = false;
        target = t;
    }

    /**
     * Returns the expected target type.
     */
    public CtClass targetType() { return targetType; }

    /**
     * Returns the method name.
     */
    public String methodName() { return methodName; }

    /**
     * Changes the method name.
     *
     * @param newName       new name.
     */
    public void methodName(String newName) { methodName = newName; }

    private CtBehavior methodCache;
    private CtClass methodArgCache;

    /**
     * Returns the called method or constructor.
     * This method uses the apparent type for the method lookup.
     */
    public CtBehavior method() throws NotFoundException {
        return method(targetType);
    }

    /**
     * Returns the called method or constructor.
     *
     * @param type              the type of the target object.
     */
    public CtBehavior method(CtClass type) throws NotFoundException {
        if (methodArgCache == type)
            return methodCache;

        methodArgCache = type;
        CtBehavior cb;
        if (MethodInfo.nameInit.equals(methodName))
            cb = type.getConstructor(descriptor);
        else
            cb = type.getMethod(methodName, descriptor);

        methodCache = cb;
        return cb;
    }

    /**
     * Returns the method descriptor.
     */
    public String descriptor() { return descriptor; }

    /**
     * Returns arguments.
     */
    public ASTree[] arguments() { return args; }

    /**
     * Returns argument types.
     */
    public CtClass[] parameterTypes() { return paramTypes; }

    /**
     * Changes an argument.
     *
     * @param i         the i-th argument is changed.
     * @param arg       the new argument.
     */
    public void setArgument(int i, ASTree arg) {
        args[i] = arg;
    }

    /**
     * Return the return type.
     */
    public CtClass returnType() { return returnType; }

    /**
     * Sets the called function.
     */
    public void setCalledFunction(Callable f) {
        callee = f;
    }

    /**
     * Returns the called function.
     */
    public Callable calledFunction() { return callee; }

    /**
     * Returns non-null if the called function is inlined.
     *
     * @return the called function.
     */
    public InlinedFunction isInlined() {
        if (callee instanceof InlinedFunction)
            return (InlinedFunction)callee;
        else
            return null;
    }

    /**
     * Records an ASTree transformer invoked when the callee function is constructed.  
     */
    public void setCalleeTransformer(java.util.function.Consumer<Function> transformer) {
        calleeTransformer = transformer;
    }

    /**
     * Invoked while tracing a method body.
     * It invokes an ASTree transformer.
     *
     * @param f         the argument passed to the transformer.
     */
    public void runCalleeTransformer(Function f) {
        /* This is invoked by Tracer#getAST2().
         * The body of a Java lambda is implemented as a static method
         * in the class declaring the lambda.  So when getAST2() reads
         * the lambda, it calls TypeDef#add() on that declaring class object.
         * It does not call on the class object for the interface type of the lambda.
         * See ImmutableClass#add() and #applyAdvice().
         */
        if (calleeTransformer != null)
            calleeTransformer.accept(f);
    }

    /**
     * Returns non-null if the callee function should be inlined.
     * This implementation returns {@code @Inline} if an ASTree transformer
     * is recorded because inlining is necessary for applying the transformer.
     *
     * @see javassist.offload.clang.ImmutableClass#add(Function, CtBehavior)
     */
    public Inline doInline() {
        if (calleeTransformer == null)
            return null;
        else
            return inlineAttribute;
    }

    private static final Inline inlineAttribute = new Inline() {
        @Override public Class<? extends Annotation> annotationType() {
            return getClass();
        }

        @Override public boolean value() { return true; }
        @Override public boolean object() { return false; }
    };

    /**
     * Changes the result of {@link #isStatement()}.
     *
     * @param ast       the immediate parent, {@link Block}, {@link Assign},
     *                  {@link Return}, or {@link New}.
     * @see New#isStatement(ASTree)
     */
    public void isStatement(ASTree ast) { isStatement = ast; }

    /**
     * Returns non-null if the call constructs an expression statement, 
     * i.e. if the immediate parent is {@link Block} or {@link New} or if the parent
     * is {@code Assign} or {@code Return} and the grand parent is {@link Block}.
     * Otherwise, null is returned.
     *
     * @see New#isStatement()
     */
    public ASTree isStatement() { return isStatement; }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (target != null) {
            sb.append(target.toString());
            sb.append("->");
        }
        if (targetType == null)
            sb.append("?");
        else
            sb.append(targetType.getName());

        sb.append('#');
        sb.append(methodName);
        sb.append('(');
        boolean first = true;
        if (args != null)
            for (ASTree arg: args) {
                if (first)
                    first = false;
                else
                    sb.append(", ");
                
                sb.append(arg);
            }

        sb.append(')');
        return sb.toString();
    }

    /**
     * Returns true if this calls a static method.
     */
    public boolean isStatic() { return target == null; }

    /**
     * Returns true if this call is INVOKESPECIAL.
     */
    public boolean isInvokeSpecial() { return isInvokeSpecial; }

    public int numChildren() {
        if (isInlined() != null)
            return 1;
        else if (isStatic())
            return args.length;
        else
            return args.length + 1;
    }

    public ASTree child(int n) {
        if (isInlined() != null) {
            if (n == 0)
                return callee;
        }
        else if (isStatic()) {
            if (n < args.length)
                return args[n];
        }
        else
            if (n == 0)
                return target;
            else if (n - 1 < args.length)
                return args[n - 1];

        return super.child(n);
    }

    public void setChild(int n, ASTree c) {
        if (isInlined() != null) {
            if (n == 0 && c instanceof Callable) {
                callee = (Callable)c;
                return;
            }
        }
        if (isStatic()) {
            if (n < args.length) {
                args[n] = c;
                return;
            }
        }
        else
            if (n == 0) {
                setTarget(c);
                return;
            }
            else if (n - 1 < args.length) {
                args[n - 1] = c;
                return;
            }

        super.setChild(n, c);
    }

    public CtClass type() { return returnType; }

    public ASTree value() throws NotFoundException {
        if (callee != null)
            return callee.value();
        else
            return super.value();
    }

    private boolean actualTypeCacheValid;
    private CtClass actualTypeCache;

    public void clearActualTypeCache() { actualTypeCacheValid = false; }

    /**
     * Returns the actual type of the target object if it is known.
     * Otherwise, the method returns null.
     * The result will be cached.
     */
    public CtClass actualTargetType() throws NotFoundException {
        if (actualTypeCacheValid)
            return actualTypeCache;

        actualTypeCacheValid = true;
        CtClass t = actualTargetType2();
        actualTypeCache = t;
        return t;
    }

    private CtClass actualTargetType2() throws NotFoundException {
        CtClass clazz = targetType;
        if (isStatic() || isInvokeSpecial() || Modifier.isFinal(clazz.getModifiers()))
            return clazz;
        else {
            ASTree value = target.value();
            if (isStaticallyTyped(value)) {
                // The value is statically given.
                return value.type(); 
            }
            else {
                CtBehavior mth0 = method(clazz);
                if (Modifier.isFinal(mth0.getModifiers()))
                    return clazz;
            }
        }

        return null;
    }

    private boolean isStaticallyTyped(ASTree value) {
        if (value == null)
            return false;
        else
            return value instanceof New || value instanceof ObjectConstant;
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
