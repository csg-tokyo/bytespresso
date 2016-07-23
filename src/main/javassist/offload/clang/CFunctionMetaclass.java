// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import java.util.ArrayList;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.AdhocASTList;
import javassist.offload.ast.Assign;
import javassist.offload.ast.Block;
import javassist.offload.ast.Body;
import javassist.offload.ast.Call;
import javassist.offload.ast.Dispatcher;
import javassist.offload.ast.ForeignFunction;
import javassist.offload.ast.Function;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.InlinedFunction;
import javassist.offload.ast.IntConstant;
import javassist.offload.ast.IntrinsicFunction;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.Jump;
import javassist.offload.ast.NativeFunction;
import javassist.offload.ast.Return;
import javassist.offload.ast.AdhocAST;
import javassist.offload.ast.StringConstant;
import javassist.offload.ast.TmpVariable;
import javassist.offload.javatoc.impl.Serializer;
import javassist.offload.javatoc.impl.StdMainFunction;
import javassist.offload.lib.Jvm;
import javassist.offload.lib.Util;

public class CFunctionMetaclass extends FunctionMetaclass {
    /**
     * The singleton instance.
     */
    public static final FunctionMetaclass instance = new CFunctionMetaclass();

    protected CFunctionMetaclass() {}

    /**
     * Makes a function.
     */
    public Function make(CtClass returnType, CtBehavior method, String name, JVariable[] parameters,
                         boolean isStatic, String arg, String[] args, Class<?> companion)
    {
        return new CFunction(returnType, method, name, parameters, isStatic);
    }

    /**
     * Makes a synthesized function.
     * The constructor makes a list of local variables used in the body
     * (the local variables must be TmpVaraible objects only).
     * It also set the jump target of all the Jump statements. 
     *
     * @param type      the return type.
     * @param method    the Java method.
     * @param fname     the function name in C.
     * @param params    the formal parameters.  If the method is
     *                  not static, the first element is the target/receiver object.
     * @param blocks    the function body.
     * @param isStatic  the function is a static method.
     * @see Jump#setTarget(Function)
     */
    private Function synthesize(CtClass type, CtBehavior method, String fname,
                        JVariable[] params, Iterable<Block> blocks, boolean isStatic)
    {
        return new CFunction(type, method, fname, params, blocks, isStatic);
    }

    /**
     * Makes a method dispatcher.
     */
    public Dispatcher makeDispatcher(CtMethod cm, String fname)
        throws NotFoundException
    {
       return new CDispatcher(cm, fname);
    }

    /**
     * Makes a native function.
     */
    public NativeFunction makeNative(CtClass returnType, CtBehavior method, String name,
                                     JVariable[] parameters, String body, boolean isStatic)
    {
        return new NativeCFunction(returnType, method, name, parameters, body, isStatic);
    }

    /**
     * Makes a foreign function.
     */
    public ForeignFunction makeForeignFunction(CtClass returnType, CtBehavior method, String name,
                                               JVariable[] parameters, boolean isStatic)
    {
        return new ForeignCFunction(returnType, method, name, parameters, isStatic);
    }

    /**
     * Makes an intrinsic function.
     */
    public IntrinsicFunction makeIntrinsicFunction(CtClass type, CtBehavior method, String fname,
                                        JVariable[] params, boolean isStatic)
        throws NotFoundException
    {
        return new IntrinsicCFunction(type, method, fname, params, isStatic);
    }

    /**
     * Constructs an inlined function with a single-expression body.
     */
    public InlinedFunction makeInlinedFunction(Function f, ASTree body) {
        return new InlinedCFunction(f, body);
    }

    /**
     * Constructs an inlined function that does not have a
     * single-expression body.
     */
    public InlinedFunction makeInlinedFunction(Function f, Body body, Block initBlock) {
        return new InlinedCFunction(f, body, initBlock);
    }

    static final String callbackFunctionName = "jvst_offload_callbackDispatcher";

    /**
     * Constructs a @Remote function.
     * It is a proxy for invoking the corresponding method in the JVM.
     *
     * @param method                the @Remote method.
     * @param returnType            the return type.
     * @param origMethodName        the original method name.
     * @param origDescriptor        the descriptor of the method.
     * @param name                  the name of the constructed function.
     * @param parameters            the parameters.
     * @param isStatic              true if the method is static.
     *
     * @see javassist.offload.javatoc.Server
     */
    public Function makeRemoteFunction(CtBehavior method, CtClass returnType,
            String origMethodName, String origDescriptor,
            String methodName, JVariable[] parameters, boolean isStatic)
        throws NotFoundException
    {
        ArrayList<Block> body = new ArrayList<Block>();
        Block block = new Block(0);

        CtClass declaring = method.getDeclaringClass();
        CtClass jvm = declaring.getClassPool().get(Jvm.class.getName());
        remoteCallHeader(declaring, jvm, block, origMethodName, origDescriptor);
        for (JVariable p: parameters) {
            CtClass type = p.type();
            CtMethod cm = Serializer.findWriteMethod(type, jvm, "parameter");
            if (cm == null)
                throw new NotFoundException("not supported parameter type: " + type.getName());

            Call call = new Call(null, cm, new ASTree[] { p });
            block.add(call);
        }

        block.add(new Call(null, jvm.getMethod("javaFlush", "()V"), new ASTree[0]));

        block.add(new AdhocASTList("while (", new Call(null, jvm.getMethod("readBoolean", "()Z"), new ASTree[0]),
                                   " == 0) ", callbackFunctionName, "();")); 

        CtMethod cm = Serializer.findReadMethod(returnType, jvm, "return");
        if (cm != null) {
            Call call = new Call(null, cm, new ASTree[0]);
            block.add(new Return(returnType, call));
        }

        body.add(block);
        return synthesize(returnType, method, methodName,
                          parameters, body, isStatic);
    }

    private static void remoteCallHeader(CtClass declaring, CtClass jvm, Block block,
                                         String origMethodName, String origDescriptor)
        throws NotFoundException
    {
        CtClass stringClass = jvm.getClassPool().get(String.class.getName());
        block.add(new Call(null, jvm.getMethod("writeBoolean", "(Z)V"),
                           new ASTree[] { IntConstant.ZERO }));
        CtMethod writeStr = jvm.getMethod("writeString", "(Ljava/lang/String;)V");
        block.add(makeCallExpr(writeStr, declaring.getName(), stringClass));
        block.add(makeCallExpr(writeStr, origMethodName, stringClass));
        block.add(makeCallExpr(writeStr, origDescriptor, stringClass));
    }

    private static Call makeCallExpr(CtMethod cm, String arg, CtClass stringClass)
        throws NotFoundException
    {
        return new Call(null, cm,
                        new ASTree[] { new StringConstant(arg, stringClass) });
    }

    /**
     * Constructs a function dispatching to callback functions for @Remote methods.
     *
     * @param method           the {@code callbacks()} method.
     * @param callbacks         an array of callback methods.
     */
    public Function makeCallbackFunctions(CtBehavior method, CtMethod[] callbacks)
        throws NotFoundException
    {
        CtClass jvm = method.getDeclaringClass().getClassPool().get(Jvm.class.getName());
        int var = 0;
        Block block = new Block(0);
        CtMethod intReader = Serializer.findReadMethod(CtClass.intType, jvm, "parameter");
        TmpVariable methodNoVar = new TmpVariable(CtClass.intType, null, var++);
        block.add(new Assign(CtClass.intType, methodNoVar, new Call(null, intReader, new ASTree[] {}))); 

        int methodNo = 0;
        for (CtMethod mth: callbacks) {
            block.add(new AdhocASTList(methodNo > 0 ? "  } else if (" : "  if (", methodNoVar, " == ", methodNo++, ") {"));
            CtClass[] params = mth.getParameterTypes();
            ASTree[] paramVars = new TmpVariable[params.length];
            int i = 0;
            for (CtClass p: params) {
                CtMethod reader = Serializer.findReadMethod(p, jvm, "parameter");
                if (reader == null)
                    throw new NotFoundException("not supported parameter type: " + p.getName()
                                                + " in " + mth.getLongName());

                paramVars[i] = new TmpVariable(p, null, var++);
                Assign a = new Assign(p, paramVars[i], new Call(null, reader, new ASTree[] {})); 
                block.add(a);
                i++;
            }

            CtMethod writer = Serializer.findWriteMethod(mth.getReturnType(), jvm, "return");
            block.add(new Call(null, writer, new ASTree[] { new Call(null, mth, paramVars) }));
        }

        block.add(new AdhocAST(CtClass.voidType,
                                methodNo > 0 ? "  } else { exit(" + Util.ERR_CALLBACK + "); }"
                                             : "exit(" + Util.ERR_CALLBACK + ");"));
        block.add(new AdhocAST(CtClass.voidType, "fflush(" + StdMainFunction.OUTPUT + ");"));
        ArrayList<Block> body = new ArrayList<Block>();
        body.add(block);
        return synthesize(CtClass.voidType, method,
                          callbackFunctionName, new JVariable[0], body, true);
    }
}
