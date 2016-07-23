// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc;

import java.util.ArrayList;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.offload.Options;
import javassist.offload.clang.HeapMemory;
import javassist.offload.javatoc.impl.CTranslator;
import javassist.offload.javatoc.impl.MainFunction;
import javassist.offload.javatoc.impl.StandaloneMain;
import javassist.offload.javatoc.impl.Task;

/**
 * A standalone driver.
 *
 * <p>The generated code after the translation is executed without
 * communicating with the JVM where the driver is running.
 * It launches another instance of the JVM for executing @Remote
 * methods.
 * </p>
 *
 * @see StdDriver
 */
public class StandaloneDriver implements Settings {
    protected ClassPool classPool;
    private String binName;
    protected String compiler;
    protected String execCmd;
    private boolean littleEndian;
    private boolean garbageCollection;

    protected boolean compileIt;
    protected boolean runIt;
    protected ArrayList<Object> adjacentObjects;        // hints for variable declarations
    protected CtMethod[] callbackMethods;
    private HeapMemory heapMemory;
    
    /**
     * Constructs a driver.
     *
     * <p>If the system property <code>c.compiler</code> is set,
     * then the value is used as a compiler command.
     * The generated C source file is {@code bytespresso.c}.
     * If <code>c.endian</code> is <code>big</code>, then the endian
     * is set to big endian.  Otherwise, little endian.
     * </p>
     * <p>If the system property <code>c.exec</code> is set,
     * then the value is used as an execution command.
     * If it is not set or null, {@code ./a.out} will be used.
     * </p>
     *
     * <p>To set system properties, start the JVM like:
     * <ul>
     * {@code java -Dc.compiler="gcc -O4 bytespresso.c" -Dc.endian="little"}
     * </ul>
     * Note that these arguments starting with {@code -D} are not program
     * arguments but VM arguments.
     * </p>
     */
    public StandaloneDriver() {
        classPool = new ClassPool(true);
        binName = "./a.out";
        compiler = System.getProperty("c.compiler");     // can be null.
        execCmd = System.getProperty("c.exec");          // can be null.
        String endian = System.getProperty("c.endian");
        if (endian == null)
            littleEndian = true;        // x86?
        else
            littleEndian = !endian.equals("big");

        garbageCollection = false;
        compileIt = true;
        runIt = true;
        adjacentObjects = new ArrayList<Object>();
        callbackMethods = new CtMethod[0];
        heapMemory = null;
    }

    /**
     * Instructs the driver to translate a Java method and generate source code in C
     * when {@code invoke()} is called.
     * The C code is not compiled or run.
     */
    public void sourceOnly() { compileIt = false; }

    /**
     * Instructs the driver to translate a Java method into C code
     * and compile the C code when {@code invoke()} is called.  
     * The compiled C code is not executed.
     */
    public void compileOnly() { compileIt = true; runIt = false; }

    /**
     * Returns true if the target is a little endian system.
     */
    public boolean isLittleEndian() { return littleEndian; }

    /**
     * Turns conservative garbage collection on.
     * {@code libgc.a} has to be available in the current directory.
     * It is linked to the generated C program at compilation time.
     */
    public void doGarbageCollection() { garbageCollection = true; }

    /**
     * Changes the endian.
     *
     * @param isLittle      set the endian to little if true.
     */
    public void setLittleEndian(boolean isLittle) { littleEndian = true; }

    /**
     * Returns the name of the source file.
     */
    public String sourceFile() { return "bytespresso.c"; }

    /**
     * Returns the compile command.
     */
    public String compileCommand() {
        if (compiler == null) {
            String src = sourceFile();
            String execName = binaryName();
            return compileCommandHead() + " -o " + execName + " " + src + compileCommandLinked();
        }
        else
            return compiler;
    }

    protected String compileCommandHead() {
        return "cc -O3";
    }

    protected String compileCommandLinked() {
        if (garbageCollection)
            return " libgc.a";
        else
            return "";
    }

    /**
     * Changes the compiler.  It overrides the compiler command specified
     * by the system property.
     *
     * @param cmd       the compiler command such as {@code "cc -Ofast bytespresso.c"}.
     */
    public void setCompiler(String cmd) {
        compiler = cmd;
    }

    /**
     * Returns the execution command.
     */
    public String execCommand() {
        if (execCmd == null)
            return binaryName();
        else
            return execCmd;
    }

    /**
     * Changes the execution command.  It overrides the compiler command specified
     * by the system property.
     *
     * @param cmd   the execution command such as {@code "./a.out"}.
     */
    public void setExecutionCommand(String cmd) {
        execCmd = cmd;
    }

    /**
     * Returns the name of the executable file.
     */
    protected String binaryName() { return binName; }

    /**
     * Changes the name of the executable file.
     * 
     * @param name      a new name.
     */
    public void setBinaryName(String name) {
        binName = name;
    }

    /**
     * Adds a memory allocation hint.  The objects added by this method
     * will be allocated at successive memory addresses
     * if they are passed into the generated C code.
     * They will be allocated in the order that the objects are added.
     *
     * @param obj       the added object.
     * @return          this driver.
     */
    public StandaloneDriver addAdjacentObjects(Object obj) {
        adjacentObjects.add(obj);
        return this;
    }

    /**
     * Returns objects that should be adjacently allocated.
     * This is a hint for memory allocation. 
     */
    public ArrayList<Object> adjacentObjects() { return adjacentObjects; }

    /**
     * Sets the callback methods.  These methods are translated into C and
     * {@code @Remote} methods can call them
     * by {@link Callback#invoke(int, Class, Object...)}.
     * The index in {@code callbacks} is passed to 
     * {@link Callback#invoke(int, Class, Object...)}
     * to identify the invoked method.
     *
     * @param callbacks     an array of callback methods
     */
    public void setCallbackMethods(CtMethod[] callbacks) {
        callbackMethods = callbacks;
    }

    /**
     * Returns a preamble.
     */
    public String preamble() {
        if (garbageCollection)
            return "#include <stdlib.h>\n"
                 + "extern void* GC_malloc(size_t);\n"
                 + "extern void* GC_malloc_atomic(size_t);\n"
                 + "extern void GC_free(void*);\n"
                 + "static void* GC_calloc_obj(size_t c, size_t s) {"
                 + "  return GC_malloc(c * s); }\n";
        else
            return "";
    }

    /**
     * Returns prologue code of the main function.
     *
     * @return      a non-null character string.
     */
    public String prologue() { return ""; }

    /**
     * Returns epilogue code of the main function.
     *
     * @return      a non-null character string.
     */
    public String epilogue() { return ""; }

    /**
     * Invokes the method on the receiver object.
     *
     * @param method        the method.
     * @param receiver      the receiver object or null.
     * @param args          the arguments.
     * @param callbacks     the callback methods.
     *                      They must be declared on the receiver object.
     * @return the result returned by the method,
     *         or {@code void.class} if the result type is {@code void}.
     * @see Callback
     */
    public Object invoke(java.lang.reflect.Method method, Object receiver, Object[] args,
                         java.lang.reflect.Method[] callbacks)
        throws DriverException
    {
        try {
            Class<?> clazz;
            if (receiver == null)
                clazz = method.getDeclaringClass();
            else
                clazz = receiver.getClass();

            CtClass cc = classPool.get(clazz.getName());
            CtMethod cm = getMethod(cc, method);
            Object[] args2 = makeArguments(cm, receiver, args);
            CtMethod[] callbacks2 = new CtMethod[callbacks.length];
            for (int i = 0; i < callbacks.length; i++)
                callbacks2[i] = getMethod(cc, callbacks[i]);

            return invoke(cm, args2, callbacks2);
        }
        catch (NotFoundException e) {
            throw new DriverException(e);
        }
    }

    public static CtMethod getMethod(CtClass cc, java.lang.reflect.Method method)
        throws NotFoundException
    {
        String descriptor = javassist.util.proxy.RuntimeSupport.makeDescriptor(method);
        return cc.getMethod(method.getName(), descriptor);
    }

    protected static Object[] makeArguments(CtMethod cm, Object receiver, Object[] args)
        throws DriverException
    {
        if (Modifier.isStatic(cm.getModifiers()))
            return args;
        else
            if (receiver == null)
                throw new DriverException("a method call on null: " + cm.getLongName());
            else {
                Object[] args2 = new Object[args.length + 1];
                args2[0] = receiver;
                System.arraycopy(args, 0, args2, 1, args.length);
                return args2;
            }
    }

    /**
     * Invokes the {@code run} method on the given {@code Runnable} object.
     *
     * @param run   the {@code Runnable} object.
     */
    public void invoke(Runnable run) throws DriverException {
        invoke(StandaloneDriver.class, "runVoid", run);
    }

    static int runVoid(Runnable run) {
        run.run();
        return 0;
    }

    protected Object invoke(Class<?> thisClass, String method, Object func)
        throws DriverException
    {
        try {
            CtClass cc = classPool.get(thisClass.getName());
            CtMethod cm = cc.getDeclaredMethod(method);
            return invoke(cm, new Object[] { func }, callbackMethods);
        }
        catch (NotFoundException e) {
            throw new DriverException(e);
        }
    }

    /**
     * The return type of the method must be int or void.
     * If the method returns a non-zero integer, a DriverException is thrown.
     *
     * @return  <code>void.class</code>.  
     */
    protected Object invoke(CtMethod cm, Object[] args, CtMethod[] callbacks)
        throws DriverException, NotFoundException
    {
        return invoke(cm, args, callbacks, null);
    }

    protected Object invoke(CtMethod cm, Object[] args, CtMethod[] callbacks,
                            Task.Communicator com)
            throws DriverException, NotFoundException
    {
        CTranslator drv = makeTranslator();
        drv.translateAndWriteCode(cm, args, callbacks, heapMemory());
        if (compileIt) {
            drv.compileCode();
            if (runIt)
                drv.runCode(com, null, null);
        }

        return void.class;
    }

    /**
     * Returns a {@code CTranslator} used for the translation.
     * Override this method if a custom translator is used.
     */
    protected CTranslator makeTranslator() {
        MainFunction mf = new StandaloneMain();
        return new CTranslator(this, mf);
    }

    /**
     * Returns a {@code HeapMemory} object used during transformation.
     */
    public HeapMemory heapMemory() {
        if (heapMemory == null)
            if (Options.portableInitialization)
                heapMemory = new HeapMemory.PortableHeapMemory(garbageCollection);
            else
                heapMemory = new HeapMemory(garbageCollection);

        return heapMemory;
    }

    /**
     * Changes the {@code HeapMemory} object used during transformation.
     *
     * @param h     a new {@code HeapMemory} object.
     */
    public void setHeapMemory(HeapMemory h) { heapMemory = h; }
}
