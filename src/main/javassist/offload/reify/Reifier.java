// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.offload.ast.Dispatcher;
import javassist.offload.ast.Function;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.JMethod;
import javassist.offload.ast.TypeDef;

/**
 * Basic reification module for constructing an AST and printing it.
 */
public class Reifier {
    /**
     * Usage example.  This method reifies {@code round} method in
     * {@code java.lang.Math} and prints
     * the AST of the method.
     * {@code ClassPool}, {@code CtClass}, and {@code CtMethod} are
     * provided by Javassist.  They are similar to Java's reflection-API
     * classes but they are for not runtime but load-time reflection.
     *
     * @see www.javassist.org
     */
    public static void main(String[] args)
        throws NotFoundException, BadBytecode    
    {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get(java.lang.Math.class.getName());   // get a class object for java.lang.Math
        CtMethod cm = cc.getDeclaredMethod("round");
        reifyAndPrint(cm, new Object[] { -3.5F });
    }

    /**
     * Usage example.  This method reifies a given method and prints
     * the AST constructed from that method as the root.
     *
     * @param cm        the method.
     * @param args      the actual arguments.
     */
    public static void reifyAndPrint(CtMethod cm, Object[] args)
        throws NotFoundException, BadBytecode
    {
        Reifier reifier = new Reifier(cm, args);
        Snapshot image = reifier.snap();

        System.out.println(" ** types **");
        for (TypeDef t: image.classTable.allTypes())
            System.out.println(t);

        System.out.println(" ** functions **");
        System.out.println(image.function);
        for (JMethod f: image.functionTable)
            if (f != image.function)
                System.out.println(f);
    }

    protected final ClassPool cpool;
    protected final CtMethod method;
    protected final Object[] arguments;
    protected final Tracer tracer;
    protected final ClassTable classTable;
    protected final FunctionTable<? extends JMethod, ? extends Dispatcher> functionTable;
    protected final Class<? extends FunctionMetaclass> defaultMetaclass;

    /**
     * Constructs a driver for reifying a given method and constructing its AST.
     *
     * @param cm        the method.
     * @param args      the arguments to the method.
     */
    public Reifier(CtMethod cm, Object[] args) {
        this(cm, args, new Tracer(),
             new BasicClassTable(cm.getDeclaringClass().getClassPool()),
             new FunctionTable<Function,Dispatcher>(),
             FunctionMetaclass.class);
    }

    /**
     * Constructs a driver for reifying a given method and constructing its AST.
     *
     * @param cm        the method.
     * @param args      the arguments to the method.
     * @param t         the tracer.
     * @param ct        the class table.
     * @param ft        the function table.
     * @param meta      the default metaclass for functions.
     */
    public Reifier(CtMethod cm, Object[] args, Tracer t, ClassTable ct,
                FunctionTable<? extends JMethod, ? extends Dispatcher> ft,
                Class<? extends FunctionMetaclass> meta)
    {
        cpool = cm.getDeclaringClass().getClassPool();
        method = cm;
        arguments = args;
        tracer = t;
        classTable = ct;
        functionTable = ft;
        defaultMetaclass = meta;
    }

    /**
     * An abstract syntax tree.
     *
     * @see #snap()
     */
    public static class Snapshot {
        /**
         * The method as the entry point.
         */
        public final Function function;

        /**
         * A collection of all the classes that appear
         * during the execution of the methods.
         */
        public final ClassTable classTable;

        /**
         * A collection of all the methods directly/indirectly
         * invoked by the entry method.
         */
        public final FunctionTable<? extends JMethod, ? extends Dispatcher> functionTable;

        /**
         * A collection of the objects accessed from the methods.
         */
        public final HashSet<Object> objects;

        /**
         * Constructs an {@code AST} object.
         */
        Snapshot(Function f, ClassTable ct, FunctionTable<? extends JMethod, ? extends Dispatcher> ft,
            HashSet<Object> objs) {
            function = f;
            classTable = ct;
            functionTable = ft;
            objects = objs;
        }
    }

    /**
     * Runs the driver to reify the method.
     * The ASTs of the methods directly/indirectly called from the reified method
     * are recorded in the function table as well as the AST of
     * the reified method.
     * The type names appearing in those ASTs are recorded in the class
     * table.
     *
     * @return      the constructed AST, which includes the function table
     *              and the class table.
     */
    public Snapshot snap()
        throws NotFoundException, BadBytecode
    {
        TraceContext context = TraceContext.make(cpool, classTable, defaultMetaclass,
                                                 functionTable, UniqueID.make());
        Function f = tracer.allFunctions(method, arguments, context);
        collectTypes(f, context);
        makeDispatchers(context);
        doPostProcess(context);
        return new Snapshot(f, classTable, functionTable, context.heapObjects());
    }

    /**
     * Records the classes appearing in the objects passed as arguments
     * and the objects accessed through static fields.
     * After this method finishes, the class table has to contain
     * all the classes that may be a target of dynamic method dispatch.
     *
     * <p>If some post transformation is applied to ASTs, it has to be
     * applied in this method.
     * </p>
     * 
     * @param f             the reified method.
     */
    protected void collectTypes(Function f, TraceContext context)
        throws NotFoundException, BadBytecode
    {
        HashSet<Object> visited = new HashSet<Object>();
        for (Object a: arguments)
            recordTypes(a, visited);

        for (Object obj: context.heapObjects())
            recordTypes(obj, visited);
    }

    /**
     * Makes dispatchers.
     */
    private void makeDispatchers(TraceContext context)
        throws NotFoundException, BadBytecode
    {
        ClassTable ct = classTable;
        int numOfTypes; // the number of classes that will be instantiated. 
        do {
            numOfTypes = ct.instantiatedTypes();
            Collection<? extends TypeDef> col = ct.allTypes();
            for (TypeDef def: col)
                tracer.revisitDispatchers(def, context);
        } while (numOfTypes < ct.instantiatedTypes());
    }

    /**
     * Performs post processing if any.  This method can be overridden.
     * The implementation in this class does not perform anything.
     */
    protected void doPostProcess(TraceContext context)
        throws NotFoundException, BadBytecode
    {
    }

    /**
     * Records the types of the given object and its fields.
     */
    private void recordTypes(Object obj, HashSet<Object> visited)
        throws NotFoundException
    {
        if (obj == null || obj instanceof Tracer.PrimitiveNumber
            || visited.contains(obj))
            return;

        visited.add(obj);
        Class<?> klass = obj.getClass();
        CtClass ctklass = cpool.get(Lambda.getLambdaProxyName(klass.getName()));
        TypeDef tdef = classTable.addClass(ctklass);
        classTable.hasInstances(tdef);
        Field[] fields = Tracer.getAllFields(klass);
        for (int i = 0; i < fields.length; i++) {
            if (!Modifier.isStatic(fields[i].getModifiers())) {
                fields[i].setAccessible(true);
                Object value;
                try {
                    value = fields[i].get(obj);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw new NotFoundException(fields[i].toString(), e);
                }

                recordTypes(value, visited);
            }
        }
    }
}
