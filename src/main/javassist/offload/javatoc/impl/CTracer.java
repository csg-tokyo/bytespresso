// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc.impl;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.MethodInfo;
import javassist.offload.Intrinsic;
import javassist.offload.Remote;
import javassist.offload.ast.Call;
import javassist.offload.ast.Dispatcher;
import javassist.offload.ast.Function;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.JMethod;
import javassist.offload.ast.TypeDef;
import javassist.offload.ast.VisitorException;
import javassist.offload.clang.CFunctionMetaclass;
import javassist.offload.clang.CodeGen;
import javassist.offload.clang.HeapMemory;
import javassist.offload.clang.IntrinsicCFunction;
import javassist.offload.reify.FunctionTable;
import javassist.offload.reify.MethodTracer;
import javassist.offload.reify.TraceContext;
import javassist.offload.reify.Tracer;

/**
 * A tracer for C code generation.
 */
public class CTracer extends Tracer {
    private HeapMemory heap;    // may be null.
    private CtClass javaUtilObjects = null;
    public static final String requireNonNull = "requireNonNull";
    public static final String javaUtilObjectsName = java.util.Objects.class.getName();

    public CTracer() { heap = null; }

    public void setup(CodeGen gen) throws VisitorException {
        heap = gen.heapMemory();
        javaUtilObjects = gen.getCtClass(java.util.Objects.class);
    }

    public boolean isMacro(Call expr, TraceContext context)
        throws NotFoundException
    {
        CtClass t = expr.actualTargetType();
        if (t == null)
            t = expr.targetType();

        // Java 9 javac uses java.util.Objects.requireNonNull() for null-pointer checking.
        // So we should remove requireNonNull() from the constructed AST.
        //
        // See javassist.offload.clang.ClassDef#invokeMethod().
        // Also see https://bugs.openjdk.java.net/browse/JDK-8074306
        if (t == javaUtilObjects && requireNonNull.equals(expr.methodName()))
            return true;

        TypeDef def = context.classTable().addClass(t);
        return def.isMacro(expr);
    }

    /**
     * Makes a callback handler for {@code Remote}.
     *
     * @param declaring     the class declaring {@code callbacks()} method.
     *                      It will be {@link javassist.offload.lib.Jvm}.
     * @see javassist.offload.lib.Jvm#callbacks()
     */
    public void makeCallbacks(CtClass declaring, CtMethod[] callbacks,
                              TraceContext context) 
        throws BadBytecode, NotFoundException
    {
        CtMethod cm = declaring.getDeclaredMethod("callbacks");
        MethodInfo method = cm.getMethodInfo2();
        FunctionMetaclass fm = context.metaclass();
        Function f;
        if (fm instanceof CFunctionMetaclass)
            f = ((CFunctionMetaclass)fm).makeCallbackFunctions(cm, callbacks);
        else
            throw new NotFoundException("a remote callback function is not available");

        FunctionTable<? extends JMethod, ? extends Dispatcher> funcs = context.functionTable();
        funcs.put(method, f);
        funcs.put(method,  null, null, f);
        visitFuncElements(f, context);
    }

    protected Function readAnnotation(Object anno, int modifier, CtBehavior cm,
                                      TraceContext context, MethodTracer mtracer)
        throws NotFoundException
    {
        if (anno instanceof Intrinsic) {
            mustBePublic(modifier, cm);
            IntrinsicCFunction f = (IntrinsicCFunction)mtracer.makeIntrinsicFunction(context);
            f.setHeap(heap);
            return f;
        }
        else if (anno instanceof Remote) {
            mustBeStatic(modifier, cm);
            context.hasRemoteFunctions(true);
            return makeRemoteFunction(cm, mtracer, context);
        }
        else
            return super.readAnnotation(anno, modifier, cm, context, mtracer); 
    }

    /**
     * Generates a proxy function for @Remote method if this method is @Remote.
     *
     * @see javassist.offload.javatoc.Server
     * @see CFunctionMetaclass#makeCallbackFunctions(CtClass, CtMethod[])
     */
    public Function makeRemoteFunction(CtBehavior method, MethodTracer mtracer, TraceContext context)
        throws NotFoundException
    {
        int fid = mtracer.uniqueID().functionId();
        String name = mtracer.shortClassName() + "_"
                      + MethodTracer.normalizeName(mtracer.originalMethodName()) + "_" + fid;
        FunctionMetaclass fm = context.metaclass();
        if (fm instanceof CFunctionMetaclass)
            return ((CFunctionMetaclass)fm).makeRemoteFunction(method, mtracer.returnType(),
                                                mtracer.originalMethodName(),
                                                mtracer.originalDescriptor(), name,
                                                mtracer.parameters(), mtracer.isStatic());
        else
            throw new NotFoundException("remote functions are not available");
    }
}
