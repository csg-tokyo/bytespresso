// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;

import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.MethodInfo;
import javassist.offload.Foreign;
import javassist.offload.Inline;
import javassist.offload.Intrinsic;
import javassist.offload.Metaclass;
import javassist.offload.Native;
import javassist.offload.Options;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Call;
import javassist.offload.ast.Callable;
import javassist.offload.ast.Cast;
import javassist.offload.ast.ClassConstant;
import javassist.offload.ast.Dispatcher;
import javassist.offload.ast.Function;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.GetField;
import javassist.offload.ast.JMethod;
import javassist.offload.ast.TypeDef;
import javassist.offload.ast.InlinedFunction;
import javassist.offload.ast.InstanceOf;
import javassist.offload.ast.IntrinsicFunction;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.New;
import javassist.offload.ast.NewArray;
import javassist.offload.ast.Null;
import javassist.offload.ast.ObjectConstant;
import javassist.offload.ast.PutField;
import javassist.offload.ast.Variable;

/**
 * A tracer constructs an abstract syntax tree (AST) of
 * a given method and all the methods invoked in the
 * body of that method.  The AST of a method body is
 * constructed by {@code MethodTracer} and the AST of
 * a basic block is constructed by {@code BlockTracer}.
 * 
 * <p>The implementation of dynamic method dispatch
 * and method inlining (the method body is not really inlined
 * in the tree.  A function specialized for particular argument
 * types is generated) is included in this class.
 * If the method body is lexically expanded at the caller site,
 * a {@code InlinedFunction} is constructed and referred to as
 * an invoked function.</p>
 *
 * <p>The {@code Tracer} class is responsible for recursively
 * constructing ASTs of called functions, performing function
 * inlining, handling dynamic method dispatch, and processing
 * annotations such as {@code @Native}.</p>
 *
 * <p>A subclass may override {@link #isMacro(Call,TraceContext)}.</p>
 *
 * @see MethodTracer
 * @see BlockTracer
 * @see javassist.offload.ast.InlinedFunction
 */
public class Tracer {
    /**
     * Visits all functions and returns the AST of the main functions.
     *
     * @param cm            the main function.
     * @param argValues     the actual arguments.
     */
    public Function allFunctions(CtMethod cm, Object[] argValues, TraceContext context)
        throws NotFoundException, BadBytecode
    {
        boolean isStatic = Modifier.isStatic(cm.getModifiers()); 
        int base = isStatic ? 0 : 1;

        // args are ASTs representing the values of the actual arguments.
        ASTree[] args = new ASTree[argValues.length - base];
        for (int i = base; i < argValues.length; i++) {
            if (argValues[i] == null)
                args[i - base] = new Null(context.getCtClass(Object.class));
            else
                args[i - base] = makeObjectConstant(argValues[i], context);
        }

        Call call = new Call(isStatic ? null : makeObjectConstant(argValues[0], context),
                             cm, args);

        if (args.length != cm.getParameterTypes().length)
            throw new NotFoundException("wrong number of arguments to " + cm.getLongName());

        Function main = allFunctions(call, cm, null, context);
        return main;
    }

    private static ObjectConstant makeObjectConstant(Object v, TraceContext context)
        throws NotFoundException
    {
        CtClass t = context.getCtClass(v.getClass());
        return new ObjectConstant(v, t);
    }

    /**
     * Collects the given function and all the functions
     * directly/indirectly called from that function.
     *
     * @param call      the expression calling the function.
     * @param cm        the static method.
     * @param enclosing the function that the call expression belongs to.
     *                  It might be null.
     * @return  the decompiled function.
     *          If {@code enclosing} is non-null, null might be returned. 
     */
    public Function allFunctions(Call call, CtBehavior cm, Function enclosing,
                                 TraceContext contexts)
        throws BadBytecode, NotFoundException
    {
        MethodInfo method = cm.getMethodInfo2();
        ASTree target = normalize(call.target());
        ASTree[] args = normalize(call.arguments());
        FunctionTable<? extends JMethod, ? extends Dispatcher> funcs = contexts.functionTable();
        Function f = funcs.get(contexts, method);
        boolean needVisiting = false;
        if (f == null) {
            f = getAST(call, cm, contexts);
            if (f == null)
                return null;    // a native or abstract method?

            funcs.put(method, f);
            needVisiting = true;
        }

        if (f.specializable()) {
            Function sf = ASTree.copy(f);     // instantiate a template
            contexts = doInline(contexts, enclosing, call, f, target, args, sf);
            if (contexts == null)
                return null;    // inlining has been done.

            Function ff = funcs.get(contexts, method, target, args);
            if (ff != null) {
                // the function is already visited.
                call.setCalledFunction(ff);
                return ff;
            }

            setArguments(sf, target, args);
            int num = funcs.put(method, target, args, sf);
            needVisiting = true;
            if (num < MAX_SPECIALIZATION) {
                if (target != null || args != null)
                    sf.rename(num);      // make a specialized function

                f = sf;
                if (Options.deadcodeElimination) {
                    f.traversalBegins();
                    ASTWalker.eliminateDeadCode(f.body());
                    f.traversalEnds();
                }
            }
            else {
                Function f2 = funcs.get(contexts, method, null, null);
                if (f2 == null)
                    funcs.put(method, null, null, f);
                else {
                    call.setCalledFunction(f2);
                    return f2;
                }
            }
        }
        else
            if (needVisiting)
                funcs.put(method, null, null, f);

        call.setCalledFunction(f);
        if (needVisiting) {
            contexts = contexts.update(f.metaclass());
            f.traversalBegins();
            visitFuncElements(f, contexts);
            f.traversalEnds();
        }

        f.updateLabels();
        return f;
    }

    private static final int MAX_SPECIALIZATION = 50;

    private int inlineDepth = 0;

    private static final int MAX_INLINE = 10;

    /**
     * Attempts to perform function inlining.
     *
     * @param f                     a function body used as a template.
     * @param specialized           a specialized function body.  It may be null.
     * @return null                 if the inlining is done.
     */
    private TraceContext doInline(TraceContext context, Function enclosing, Call call, Function f,
                                   ASTree target, ASTree[] args, Function specialized)
        throws NotFoundException, BadBytecode
    {
        context = context.update(f.inline());

        // inline a function body if it is the body of a Java lambda. 
        TraceContext context2 = context.update(call.doInline());

        if (inlineDepth < MAX_INLINE && context2.doInline()) {
            if (specialized == null)
                specialized = ASTree.copy(f);     // instantiate a template

            specialized.traversalBegins();
            TypeDef declaring = context.classTable().addClass(f.declaringClass());
            Inliner inliner;
            if (declaring == null)
                inliner = new Inliner();
            else
                inliner = declaring.inliner(f);

            InlinedFunction inline;
            if (inliner == null)
                inline = null;
            else
                inline = inliner.inline(context2, enclosing, call, target, args, specialized);

            // now back to contexts from contexts2. 
            context = context.update(inline);
            if (inline != null) {
                try {
                    inlineDepth++;
                    visitElements(inline.body(), enclosing, context);
                } finally {
                    inlineDepth--;
                }

                Inline anno = specialized.inline();
                if (anno != null && anno.object() && inline.isSimpleBlock())
                    Inliner.inlineObjects(enclosing, inline);

                specialized.traversalEnds();
                return null;
            }
            else
                specialized.traversalEnds();
        }

        return context;
    }

    public static ASTree[] normalize(ASTree[] exprs) throws NotFoundException {
        if (exprs != null) {
            ASTree[] exprs2 = new ASTree[exprs.length];
            boolean notEmpty = false;
            for (int i = 0; i < exprs.length; i++) {
                ASTree t2 = normalize(exprs[i]);
                exprs2[i] = t2;
                if (t2 != null)
                    notEmpty = true;
            }

            if (notEmpty)
                return exprs2;
        }

        return null;
    }

    /**
     * Computes a constant value of the given function argument
     * to specialize a function.  If the argument is of a primitive type,
     * it is never considered as a constant (even if it is actually a constant).
     *
     * @param t     the function argument.
     */
    public static ASTree normalize(ASTree t) throws NotFoundException {
        for (int i = 0; i < 10; i++)
            if (t == null)
                return null;
            else if (t instanceof New)
                return t;
            else if (t instanceof ObjectConstant) {
                Object value = ((ObjectConstant)t).theValue();
                if (value instanceof Number)
                    return null;
                else
                    return t;
            }
            else if (t instanceof Null)
                return t;
            else
                t = t.value();

        return null;
    }

    private void setArguments(Function f, ASTree target, ASTree[] args) throws NotFoundException {
        JVariable[] params = f.parameters();
        for (int i = 0; i < params.length; i++) {
            int k = f.parameter(i);
            JVariable v = params[i];
            if (k == Function.ParameterMap.TARGET) {
                if (!v.isMutable())
                    v.setValueAndType(target);
            }
            else if (args != null && !v.isMutable())
                v.setValueAndType(args[k]);
        }
    }

    /**
     * Visits the body elements of the function.
     *
     * @param f         the function.
     */
    protected void visitFuncElements(Function f, TraceContext contexts)
        throws NotFoundException, BadBytecode
    {
        for (Variable v: f.parameters())
            visitElements(v, f, contexts);

        for (Variable v: f.variables())
            visitElements(v, f, contexts);

        visitElements(f.body(), f, contexts);
    }

    /**
     * Visit an AST node.
     *
     * @param tree      the visited node.
     * @param enclosing    the function enclosing this AST node.
     *
     * @see ASTWalker   {@code ASTWalker} also traveres an AST.
     */
    private void visitElements(ASTree tree, Function enclosing, TraceContext context)
        throws NotFoundException, BadBytecode
    {
        if (tree instanceof Call)
            transformIfIntrinsic((Call)tree, enclosing, context);

        int n = tree.numChildren();
        for (int i = 0; i < n; i++)
            visitElements(tree.child(i), enclosing, context);

        if (tree instanceof Call)
            visitCall((Call)tree, enclosing, context);
        else if (tree instanceof ClassConstant
                || tree instanceof Cast
                || tree instanceof InstanceOf
                || tree instanceof NewArray
                || tree instanceof Variable)
            context.classTable().addClass(tree.type());
        else if (tree instanceof New) {
            TypeDef tdef = context.classTable().addClass(tree.type());
            context.classTable().hasInstances(tdef);
        }
        else if (tree instanceof GetField) {
            GetField gf = (GetField)tree;
            context.classTable().addClass(gf.type(), gf.targetClass());
            if (gf.isStatic()) {
                Object value = getStaticFieldValue(gf.targetClass().getName(),
                                                              gf.fieldName(),
                                                              gf.type().isPrimitive());

                /* if heapObjects().contains(value) is not null, the field containing that value
                 * will be read in the traced code.
                 */
                if (value != null)
                    context.heapObjects().add(value);
            }
        }
        else if (tree instanceof PutField) {
            PutField pf = (PutField)tree;
            // gen.addClass(pf.fieldType());    // do not record the type of a write-only field.
            context.classTable().addClass(pf.targetClass());
        }
    }

    /**
     * Transforms method arguments to avoid that Integer class etc. are traced.
     */
    private void transformIfIntrinsic(Call expr, Function enclosing, TraceContext context)
        throws NotFoundException, BadBytecode
    {
        CtClass clazz = expr.actualTargetType();
        if (clazz != null) {
            try {
                Object anno = expr.method(clazz).getAnnotation(Intrinsic.class);
                if (anno != null) {
                    IntrinsicFunction.transformArguments(expr);
                    visitCall(expr, enclosing, context);
                    Callable f = expr.calledFunction();
                    if (f instanceof IntrinsicFunction)
                        ((IntrinsicFunction)f).transformCallSite(expr, context);
                }
            } catch (ClassNotFoundException e) {}
        }
    }

    /**
     * Processing the AST node representing a method call.
     *
     * @param enclosing     the function enclosing the call expression.  It may be null.
     */
    private void visitCall(Call call, Function enclosing, TraceContext context)
        throws NotFoundException, BadBytecode
    {
        if (call.calledFunction() != null)
            return;         // already visited

        call.clearActualTypeCache();
        CtClass clazz = call.actualTargetType();

        if (clazz == null) {
            clazz = call.targetType();
            CtMethod mth0 = (CtMethod)call.method(clazz);
            if (!Modifier.isFinal(clazz.getModifiers()) && !Modifier.isFinal(mth0.getModifiers())) { 
                visitDynaimcCall(call, context, clazz, mth0);  // dynamic method dispatch
                return;
            }
        }

        context.classTable().addClass(clazz);
        if (!isMacro(call, context))
            allFunctions(call, call.method(clazz), enclosing, context);
    }

    /**
     * Returns true if the invoked method is expanded as a macro.
     * If false is returned, a {@link Function} object representing
     * the called method will be constructed and set to through
     * <code>setCalledFunction</code>.
     *
     * <p>If the invoked method is {@link @Intrinsic}, this method
     * should return false.</p>
     *
     * <p>The implementation in this class always returns false.
     * A subclass should override this method.</p>
     *
     * @param expr
     * @param context
     * @see Call#setCalledFunction(Function)
     */
    public boolean isMacro(Call expr, TraceContext context)
        throws NotFoundException
    {
        return false;
    }

    private void visitDynaimcCall(Call call,
                                  TraceContext context, CtClass clazz, CtMethod mth)
        throws NotFoundException, BadBytecode
    {
        TypeDef tdef = context.classTable().addClass(clazz);
        if (tdef.isNative(mth))
            return;

        Dispatcher dis = findDispatcher(tdef, mth, context);
        if (dis == null) {
            dis = MethodTracer.makeDispatcher(context, mth);
            context.functionTable().add(dis);
            tdef.add(dis);
            Call c = new Call(null, mth, null);
            if (tdef.hasInstances())
                dis.add(tdef, allFunctions(c, mth, null,  context));

            overridingMethods(dis, tdef, c, context);
        }

        call.setCalledFunction(dis);
        return;
    }

    private Dispatcher findDispatcher(TypeDef tdef, CtMethod cm, TraceContext context) {
        FunctionMetaclass fm = context.metaclass();
        List<Dispatcher> dispatchers = tdef.allDisptachers();
        if (dispatchers != null)
            for (Dispatcher d: dispatchers)
                if (d.method() == cm) {
                    ArrayList<Function> funcs = d.functions();
                    if (funcs.size() > 0 && fm.accepts(funcs.get(0)))
                        return d;
                }

        return null;
    }

    /**
     * Decompiles all the overriding methods.
     */
    private void overridingMethods(Dispatcher dis, TypeDef tdef, Call call, TraceContext contexts)
        throws NotFoundException, BadBytecode
    {
        try {
            for (TypeDef t: tdef.getSubtypes()) {
                if (t.hasInstances() && !dis.supports(t)) {
                    CtMethod cm = t.type().getMethod(call.methodName(), call.descriptor());
                    dis.add(t, allFunctions(call, cm, null, contexts));
                }

                overridingMethods(dis, t, call, contexts);
            }
        }
        catch (java.util.ConcurrentModificationException e) {
            // ignore.  revistDispatchers() will be called again.
        }
    }

    /**
     * Revisits dynamic calls to decompile the methods that have not been
     * done yet.
     * 
     * @param tdef      the dynamic calls on the objects of this type are revisited.
     */
    public void revisitDispatchers(TypeDef tdef, TraceContext contexts)
        throws NotFoundException, BadBytecode
    {
        List<Dispatcher> dispatchers = tdef.allDisptachers();
        if (dispatchers == null)
            return;

        for (int i = 0; i < dispatchers.size(); i++) {
            Dispatcher dis = dispatchers.get(i);
            CtMethod mth = dis.method();
            Call c = new Call(null, mth, null);
            if (tdef.hasInstances() && !dis.supports(tdef))
                dis.add(tdef, allFunctions(c, mth, null, contexts.update(dis.metaclass())));

            overridingMethods(dis, tdef, c, contexts);
        }
    }

    /**
     * Builds an AST of the given method.
     */
    Function getAST(Call call, CtBehavior cm, TraceContext contexts)
        throws BadBytecode, NotFoundException
    {
        try {
            return getAST2(call, cm, contexts);
        }
        catch (RuntimeException e) {
            throw new RuntimeException(cm.getLongName(), e);
        }
    }

    private Function getAST2(Call call, CtBehavior cm, TraceContext context)
        throws BadBytecode, NotFoundException
    {
        int mod = cm.getModifiers();
        if (Modifier.isAbstract(mod) || Modifier.isNative(mod))
            return null;

        CtClass cc = cm.getDeclaringClass();
        TypeDef tdef = context.classTable().addClass(cc);
        if (tdef != null && tdef.isNative(cm))
            return null;

        ClassPool cp = cc.getClassPool();
        BasicBlock[] blocks = BasicBlock.make(cp, cm);
        MethodTracer mtracer = new MethodTracer(cp, cm, blocks,
                                               context.uniqueID().instance());

        Function func = null;
        Inline inlineAnno = null;
        Metaclass meta = null;
        Object[] annotations = cm.getAvailableAnnotations();
        for (Object a: annotations) {
            func = readAnnotation(a, mod, cm, context, mtracer);
            if (func != null)
                break;
            else if (a instanceof Inline)
                inlineAnno = (Inline)a;
            else if (a instanceof Metaclass)
                meta = (Metaclass)a;
        }

        if (func == null) {
            func = mtracer.trace(meta, context.metaclass());
            func.setInline(inlineAnno);
        }

        if (tdef == null)
            return func;
        else {
            call.runCalleeTransformer(func);
            return tdef.add(func, cm);
        }
    }

    /**
     * Makes a function if an annotation is given to the method.
     * If the annotation is unknown, null is returned.
     * 
     * @param anno          annotation.
     * @param modifier      method modifier.
     * @param cm            method.
     * @param context       trace context.
     * @param mtracer       method tracer.
     * @return a function or null.
     */
    protected Function readAnnotation(Object anno, int modifier, CtBehavior cm,
                                      TraceContext context, MethodTracer mtracer)
        throws NotFoundException
    {
        if (anno instanceof Native) {
            Native ntv = (Native)anno;
            return mtracer.makeNativeFunction(ntv.value(), context);
        }
        else if (anno instanceof Foreign) {
            mustBeStatic(modifier, cm);
            return mtracer.makeForeignFunction(context);
        }
        else if (anno instanceof Intrinsic) {
            mustBePublic(modifier, cm);
            return mtracer.makeIntrinsicFunction(context);
        }
        else
            return null;
    }

    protected void mustBeStatic(int mod, CtBehavior m) {
        if ((mod & AccessFlag.STATIC) == 0)
            throw new RuntimeException(m.getLongName() + " must be a static method");
    }

    protected void mustBePublic(int mod, CtBehavior m) {
        if ((mod & AccessFlag.PUBLIC) == 0)
            throw new RuntimeException(m.getLongName() + " must be a public method");
    }

    /**
     * An object wraps a primitive type object such as
     * Integer and Double.
     * It is used to distinguish primitive type values
     * from their wrapper objects such as Integer objects. 
     */
    public static class PrimitiveNumber {
        final Object value;

        /**
         * Constructs an object.
         *
         * @param v     a primitive-type value.
         */
        public PrimitiveNumber(Object v) { value = v; }

        /**
         * Returns the wrapped object.
         */
        public Object value() { return value; }

        public boolean equals(Object obj) {
            if (obj != null && obj instanceof PrimitiveNumber)
                return value.equals(((PrimitiveNumber)obj).value);
            else
                return false;
        }

        public int hashCode() { return value.hashCode(); }
    }

    /**
     * Returns the value of the specified field.  This method does not search
     * super classes.  If isPrimitive is true, the obtained value is wrapped
     * by a PrimitiveNumber object.
     *
     * @param isPrimitive       true if the field type is a primitive type.
     */
    public static Object getStaticFieldValue(String className, String fieldName,
                                             boolean isPrimitive)
        throws NotFoundException
    {
        Object value = getStaticFieldValue(className, fieldName);
        if (isPrimitive)
            return new PrimitiveNumber(value);
        else
            return value;
    }

    /**
     * Returns the value of the specified field.  This method does not search
     * super classes.
     */
    private static Object getStaticFieldValue(String className, String fieldName)
        throws NotFoundException
    {
        try {
            Class<?> klass = Class.forName(className);
            java.lang.reflect.Field f = getField(klass, fieldName);
            f.setAccessible(true);
            return f.get(null);
        }
        catch (Exception e) {
            throw new NotFoundException("cannot access " + className + "." + fieldName, e);
        }
    }

    static interface Action<T> {
        public T doit() throws Exception;
    }

    /**
     * Gets the field with the given name.
     */
    public static java.lang.reflect.Field getField(Class<?> klass, String fieldName)
            throws NoSuchFieldException
    {
        while (true) {
            try {
                return klass.getDeclaredField(fieldName);
            }
            catch (NoSuchFieldException e) {
                klass = klass.getSuperclass();
                if (klass == null)
                    throw e;
            }
        }
    }

    /**
     * Collects all the fields declared in the given class.
     */
    public static Field[] getAllFields(Class<?> cc) {
        ArrayList<Field> array = new ArrayList<Field>();
        getAllFields(cc, array);
        return array.toArray(new Field[array.size()]);
    }

    private static void getAllFields(Class<?> cc, ArrayList<Field> fields) {
        Class<?> superClass = cc.getSuperclass();
        if (superClass != null)
            getAllFields(superClass, fields);

        Field[] fs = cc.getDeclaredFields();
        for (Field f: fs)
            fields.add(f);
    }
}
