// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc.impl;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.offload.Options;
import javassist.offload.ast.*;
import javassist.offload.clang.ArrayDef;
import javassist.offload.clang.CDispatcher;
import javassist.offload.clang.CFunctionMetaclass;
import javassist.offload.clang.ClassDef;
import javassist.offload.clang.CodeGen;
import javassist.offload.clang.HeapMemory;
import javassist.offload.clang.TraitCFunction;
import javassist.offload.clang.CTypeDef;
import javassist.offload.javatoc.DriverException;
import javassist.offload.javatoc.Settings;
import javassist.offload.lib.Jvm;
import javassist.offload.lib.Unsafe;
import javassist.offload.lib.Util;
import javassist.offload.reify.Reifier;
import javassist.offload.reify.FunctionTable;
import javassist.offload.reify.TraceContext;
import javassist.offload.reify.Tracer;

/**
 * The main module of the translator into C.
 *
 * It reads Java bytecode, constructs an abstract syntax tree (AST),
 * generates C source code, and compiles it.
 * It starts the translation from the main function and processes
 * all the functions directly/indirectly called from the main function.
 *
 * @see Tracer
 * @see javassist.offload.reify.Reifier
 */
public class CTranslator {
    private Settings settings;
    private MainFunction mainFunc;
    private CTracer tracer;

    public CTranslator(Settings s) { this(s, new StdMainFunction()); }

    public CTranslator(Settings s, MainFunction mf) {
        this(s, mf, new CTracer());
    }
        
    public CTranslator(Settings s, MainFunction mf, CTracer t) {
        settings = s;
        mainFunc = mf;
        tracer = t;
    }

    public Settings settings() { return settings; }

    /**
     * Translates a Java method into C code, compiles the C code, and run it.
     *
     * @param cm        the invoked method.
     * @param args      the arguments passed to the method.  If the method is not static,
     *                  the first argument is the target object.
     * @param com       the pipe to communicate to the executed C code.
     */
    public void compileAndRun(CtMethod cm, Object[] args, CtMethod[] callbacks,
                              Task.Communicator com, HeapMemory heap)
        throws DriverException
    {
        translateAndWriteCode(cm, args, callbacks, heap);
        compileCode();
        runCode(com, null, null);
    }

    /**
     * Decompiles Java bytecode and generates equivalent (but more efficient) C code.
     */
    public void translateAndWriteCode(CtMethod cm, Object[] args,
                                      CtMethod[] callbacks, HeapMemory heap)
        throws DriverException
    {
        try {
            long t0 = System.nanoTime();
            OutputFile file = new OutputFile(settings.sourceFile());
            CodeGen codegen = new CodeGen(cm.getDeclaringClass().getClassPool(),
                                          file, settings.isLittleEndian(), heap);
            translateIntoC(cm, args, callbacks, codegen);
            file.close();
            if (Options.debug > 0) {
                long msec = (System.nanoTime() - t0) / 1000000;
                System.err.println("Bytespresso: " + msec + " msec. for translation");
            }
        }
        catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new DriverException("translation failed", e); }
    }

    /**
     * Runs the C compiler as an external process.
     */
    public void compileCode() throws DriverException {
        String cmd = settings.compileCommand();
        if (Options.debug > 0)
            System.err.println("Bytespresso: " + cmd);

        long t0 = System.nanoTime();
        int status = new Task(cmd).run();
        if (status != 0)
            throw new DriverException("compilation failed", status);

        if (Options.debug > 0) {
            long msec = (System.nanoTime() - t0) / 1000000;
            System.err.println("Bytespresso: " + msec + " msec. for compilation");
        }
    }

    /**
     * Runs the executable code as an external process.
     */
    public void runCode(Task.Communicator com,
                        Task.Sender sender, Task.Receiver recv)
        throws DriverException
    {
        String cmd = settings.execCommand();
        if (Options.debug > 0)
            System.err.println("Bytespresso: " + cmd);

        Task t = new Task(cmd);
        if (com == null)
            t.in(sender).out(recv);
        else
            t.inout(com);

        int exit = t.run();
        if (exit != 0)
            throw new DriverException("execution failed " + settings.execCommand(), exit);
    }

    /**
     * Reads class files, makes ASTs, generates C code.
     */
    public void translateIntoC(CtMethod cm, Object[] args, CtMethod[] callbacks, CodeGen codegen)
        throws BadBytecode, NotFoundException, IllegalAccessException, IOException, VisitorException
    {
        EnvSnapshot prog = makeASTofProgram(cm, args, callbacks, codegen);

        writePreamble(codegen);
        codegen.append('\n');

        List<CTypeDef> types = prog.classTable().sortedTypes();

        // type declarations.
        for (CTypeDef def: types)
            def.code(codegen);

        // the declarations of the objects passed from Java code.
        codegen.append(prog.initializer()).append('\n');

        generateFunctions(codegen, prog, types);

        mainFunc.generate(codegen, prog, cm, args, settings);
    }

    // a snapshot of execution environment
    public static class EnvSnapshot {
        private Function function;
        private FunctionTable<TraitCFunction,CDispatcher> funcTable;
        private ClassTableForC classTable;
        private Function boolSender;
        private Function sender;
        private String initializer;
        private boolean hasRemote;

        /**
         * Returns the main function.
         */
        public Function function() { return function; }

        /**
         * Returns the function table.
         */
        public FunctionTable<TraitCFunction,CDispatcher> functionTable() { return funcTable; }

        /**
         * Returns the class table.
         */
        public ClassTableForC classTable() { return classTable; }

        /**
         * Returns the function for sending the result status of {@code main()} back to Java.
         */
        public Function boolSender() { return boolSender; }

        /**
         * Returns the function for sending the result value of {@code main()} back to Java.
         */
        public Function sender() { return sender; }

        /**
         * Returns true if the program includes remote methods.
         */
        public boolean hasRemoteMethods() { return hasRemote; }

        /**
         * Returns the code for initializing objects passed from Java code.
         */
        public String initializer() { return initializer; }
    }

    /**
     * Constructs the AST of the translated program.
     * It makes the ASTs of the main function, the functions (in)directly
     * called from the main, and helper functions.  It also collects all
     * the global variables included in the generated C code.
     *
     * @param cm		the main function.  Its name may be different from {@code main}.
     * @param args		the arguments passed to the main function.
     * @param callbacks the callback methods that will be invoked from Java.
     * @param gen       the code generator.
     * @return the constructed AST.
     */
    private EnvSnapshot makeASTofProgram(CtMethod cm, Object[] args,
                                        CtMethod[] callbacks, CodeGen gen)
        throws BadBytecode, NotFoundException, IllegalAccessException,
               IOException, VisitorException
    {
        EnvSnapshot prog = new EnvSnapshot();
        prog.classTable = gen.classTable();
        prog.funcTable = new FunctionTable<TraitCFunction,CDispatcher>();
        tracer.setHeapMemory(gen.heapMemory());
        Reifier driver = new Reifier(cm, args, tracer, prog.classTable,
                                     prog.funcTable, CFunctionMetaclass.class) {
            @Override
            protected void collectTypes(Function main, TraceContext context)
                throws NotFoundException, BadBytecode
            {
                CTranslator.this.collectTypes(main, args, callbacks, context, gen, prog);
            }

            @Override
            protected void doPostProcess(TraceContext context)
                throws NotFoundException, BadBytecode
            {
                prog.hasRemote = context.hasRemoteFunctions();
                CTranslator.this.makeCallbacksIfnotYet(callbacks, context, prog);
            }
        };

        Reifier.Snapshot shot = driver.snap();
        prog.function = shot.function;
        return prog;
    }

    /**
     * Records the classes appearing in the objects passed as arguments
     * and the objects accessed through static fields.
     * After this method finishes, the class table has to contain
     * all the classes that may be a target of dynamic method dispatch.
     *
     * <p>If some post transformation is applied to ASTs, it has to be
     * applied in a subclass method overriding this method.
     * </p>
     * 
     * @param main             the main method.
     * @param args             the arguments passed to the main method.
     */
    protected void collectTypes(Function main, Object[] args, CtMethod[] callbacks,
                                TraceContext context, CodeGen gen, EnvSnapshot prog)
        throws BadBytecode, NotFoundException
    {
        try {
            collectTypes2(main, args, callbacks, context, gen, prog);
        } catch (IllegalAccessException | IOException | VisitorException e) {
            throw new NotFoundException("cannot reify", e);
        }
    }

    private void collectTypes2(Function main, Object[] args, CtMethod[] callbacks,
                     TraceContext context, CodeGen gen, EnvSnapshot prog)
        throws IllegalAccessException, IOException, VisitorException,
               BadBytecode, NotFoundException
    {
        if (callbacks != null && callbacks.length > 0) {
            CtClass jvmClass = context.getCtClass(Jvm.class);
            tracer.makeCallbacks(jvmClass, callbacks, context);
        }

        if (mainFunc.sendResult()) {
            prog.boolSender = senderFunction(CtClass.booleanType, context);
            prog.sender = senderFunction(main.type(), context);
        }
        else {
            prog.boolSender = null;
            prog.sender = null;
        }

        // only the objects that are reachable from args and context.heapObjects()
        // are accessed in the constructed ASTs.
        prog.initializer = objectsToCode(main, args, context.heapObjects(), gen);
    }

    void makeCallbacksIfnotYet(CtMethod[] callbacks,
                               TraceContext context, EnvSnapshot prog)
        throws NotFoundException, BadBytecode
    {
        if (prog.hasRemote)
            if (callbacks == null || callbacks.length == 0) {
                CtClass jvmClass = context.getCtClass(Jvm.class);
                CTranslator.this.tracer.makeCallbacks(jvmClass, callbacks, context);
            }
    }

    public Function senderFunction(CtClass returnType, TraceContext contexts)
        throws NotFoundException, BadBytecode
    {
        CtClass jvm = contexts.getCtClass(Jvm.class);
        CtMethod cm = Serializer.findWriteMethod(returnType, jvm, "return");
        if (cm == null)
            return null;

        Function f = contexts.functionTable().get(contexts, cm.getMethodInfo2(), null, null);
        if (f == null) {
            Call call = new Call(null, cm, null);
            f = tracer.allFunctions(call, cm, null, contexts);
        }

        return f;
    }

    /**
     * Translates objects passed from the JVM into the generated C code.
     * It generates the source code of global variables corresponding to
     * the Java objects.
     */
    private String objectsToCode(Function main, Object[] args,
                                 HashSet<Object> objects,
                                 CodeGen gen)
        throws IllegalAccessException, IOException, VisitorException
    {
        JVariable[] params = main.parameters();
        for (int i = 0; i < params.length; i++)
            if (!params[i].type().isPrimitive() && args[i] != null)
                objects.add(args[i]);  // remove duplications

        StringWriter code = new StringWriter();
        JavaObjectToC jo = new JavaObjectToC();
        OutputCode output2 = new OutputFile(code);
        CodeGen gen2 = new CodeGen(gen, output2);
        ArrayList<Object> adjacents = settings.adjacentObjects();
        HashMap<Object,Object> adjacentsSet = new HashMap<Object,Object>(); 
        for (Object obj: adjacents) {
            adjacentsSet.put(obj, obj);
            jo.toCode(obj, gen2);
        }

        for (Object obj: objects)
            if (adjacentsSet.get(obj) == null)
                jo.toCode(obj, gen2);

        code.close();
        return code.toString();
    }

    /**
     * Writes preamble.
     */
    public void writePreamble(CodeGen gen) {
        String s = settings.preamble();
        if (s != null)
            gen.append(s);

        gen.append(Util.preamble());
        gen.append(Jvm.preamble());
        gen.append(Unsafe.preamble());
        ClassDef.preamble(gen);
        ArrayDef.preamble(gen);
        mainFunc.preamble(gen);
    }

    /**
     * Writes all functions.
     */
    private void generateFunctions(CodeGen gen, EnvSnapshot prog, List<CTypeDef> types)
        throws VisitorException, NotFoundException
    {
        for (CTypeDef def: types)
            def.staticFields(gen);

        gen.append('\n');

        for (TraitCFunction f: prog.functionTable())
            f.prototype(gen);

        gen.append('\n');
        for (CDispatcher f: prog.functionTable().dispatchers())
            f.prototype(gen);

        gen.append('\n');
        for (TraitCFunction f: prog.functionTable()) {
            f.code(gen);
            gen.append('\n');
        }

        for (CDispatcher f: prog.functionTable().dispatchers()) {
            gen.append(f);
            gen.append('\n');
        }
    }
}
