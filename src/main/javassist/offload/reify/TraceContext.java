// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

import java.util.HashSet;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.offload.Inline;
import javassist.offload.ast.Dispatcher;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.InlinedFunction;
import javassist.offload.ast.JMethod;

/**
 * A context holder used while traversing functions
 * for building abstract syntax trees.
 *
 * @see #make(ClassPool, ClassTable, Class, FunctionTable, UniqueID)
 */
public abstract class TraceContext {
    public abstract TraceContext next();

    public abstract boolean doInline();
    public abstract boolean doInlineAny();

    public abstract boolean hasRemoteFunctions();
    public abstract void hasRemoteFunctions(boolean yes);

    public abstract FunctionMetaclass metaclass();

    public abstract ClassTable classTable();
    public abstract FunctionTable<? extends JMethod, ? extends Dispatcher> functionTable();
    public abstract UniqueID uniqueID();

    /**
    * Returns a hash table.  It is a set of all the values (i.e. objects)
    * of the static fields found during the translation.
    */
    public abstract HashSet<Object> heapObjects();

    public abstract CtClass getCtClass(Class<?> c) throws NotFoundException;

    /**
     * Constructs a base context.
     *
     * @param type      the type of function metaclass.
     */
    public static TraceContext make(ClassPool cp, ClassTable ct,
                                     Class<? extends FunctionMetaclass> type,
                                     FunctionTable<? extends JMethod, ? extends Dispatcher> funcs, UniqueID uid)
        throws NotFoundException
    {
        return new Base(cp, ct, type, funcs, uid);
    }

    /**
     * Updates the given context according to the {@code Inline} annotation.
     *
     * @return a new context.
     */
    public TraceContext update(Inline inline) {
        // if the context is already InlineExpr,
        // InlineOn is not created as a new context.
        if (inline != null)
            if (inline.value() != this.doInline())
                return new TraceContext.InlineOn(this, inline);    

        return this;
    }

    /**
     * Updates the given context according to the given {@code InlinedFunction}.
     * If the inlined function is expanded as not a statement but an expression,
     * further inlining accepts only the functions whose body is a single expression. 
     */
    public TraceContext update(InlinedFunction f) {
        if (f == null)
            if (doInline() && !doInlineAny())
                return new TraceContext.InlineOn(this, null);
            else
                return this;
        else if (doInlineAny() && f.isExpression())
            return new TraceContext.InlineExpr(this);
        else
            return this;
    }

    /**
     * Updates the given context according to the given function.
     *
     * @return a new context.
     */
    public TraceContext update(FunctionMetaclass fm) {
        if (this.metaclass() == fm)
            return this;
        else
            return new TraceContext.FunctionClass(this, fm);
    }

    static class Base extends TraceContext {
        final ClassPool cpool;
        final ClassTable classTable;
        final UniqueID uniqueID;
        final FunctionTable<? extends JMethod, ? extends Dispatcher> functions;
        final HashSet<Object> heapObjects;
        final FunctionMetaclass meta;
        boolean hasRemoteFunctions;

        Base(ClassPool cp, ClassTable ct,
             Class<? extends FunctionMetaclass> type,
             FunctionTable<? extends JMethod, ? extends Dispatcher> funcs,
             UniqueID uid)
            throws NotFoundException
        {
            cpool = cp;
            uniqueID = uid;
            classTable = ct;
            hasRemoteFunctions = false;
            functions = funcs;
            heapObjects = new HashSet<Object>();
            meta = MethodTracer.getMetaclassInstance(type);
        }

        public TraceContext next() { return null; }
        public boolean doInline() { return false; }
        public boolean doInlineAny() { return false; }

        public boolean hasRemoteFunctions() { return hasRemoteFunctions; }
        public void hasRemoteFunctions(boolean yes) { hasRemoteFunctions = yes; }

        public CtClass getCtClass(Class<?> klass) throws NotFoundException {
            String name = klass.getName();
            return cpool.get(Lambda.getLambdaProxyName(name));
        }

        public FunctionMetaclass metaclass() { return meta; }
        public FunctionTable<? extends JMethod, ? extends Dispatcher> functionTable() { return functions; }
        public HashSet<Object> heapObjects() { return heapObjects; }
        public ClassTable classTable() { return classTable; }
        public UniqueID uniqueID() { return uniqueID; }
    }

    static public class Element extends TraceContext {
        private TraceContext next;
        
        public Element(TraceContext next) {
            this.next = next;
        }

        public TraceContext next() { return next; }
        public boolean hasRemoteFunctions() { return next.hasRemoteFunctions(); }
        public void hasRemoteFunctions(boolean yes) { next.hasRemoteFunctions(yes); }
        public boolean doInline() { return next.doInline(); }
        public boolean doInlineAny() { return next.doInlineAny(); }

        public CtClass getCtClass(Class<?> klass) throws NotFoundException {
            return next.getCtClass(klass);
        }

        public FunctionMetaclass metaclass() { return next.metaclass(); }

        public FunctionTable<? extends JMethod, ? extends Dispatcher> functionTable() {
            return next.functionTable();
        }

        public HashSet<Object> heapObjects() { return next.heapObjects(); }
        public ClassTable classTable() { return next.classTable(); }
        public UniqueID uniqueID() { return next.uniqueID(); }
    }

    public static class InlineOn extends Element {
        private boolean doInline;
        
        public InlineOn(TraceContext next, Inline inline) {
            super(next);
            if (inline == null)
                doInline = true;
            else
                doInline = inline.value();
        }

        @Override public boolean doInline() { return doInline; }
        @Override public boolean doInlineAny() { return true; }
    }

    public static class InlineExpr extends Element {
        public InlineExpr(TraceContext next) { super(next); }
        @Override public boolean doInlineAny() { return false; }  // expression only
    }

    public static class FunctionClass extends Element {
        FunctionMetaclass meta;

        public FunctionClass(TraceContext next, FunctionMetaclass fm) {
            super(next);
            meta = fm;
        }

        @Override public FunctionMetaclass metaclass() { return meta; }
    }
}
