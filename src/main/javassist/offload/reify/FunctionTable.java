// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javassist.NotFoundException;
import javassist.bytecode.MethodInfo;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Dispatcher;
import javassist.offload.ast.Function;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.JMethod;
import javassist.offload.ast.New;
import javassist.offload.ast.Null;
import javassist.offload.ast.ObjectConstant;

public class FunctionTable<T extends JMethod, D extends Dispatcher> implements Iterable<T> {
    static class Entry {    // for a function
        Function function;
        Entry next;

        Entry(Function f) {
            function = f;
        }
    }

    static class SpEntry {  // for a specialized function
        ASTree   target;    // null if the function is static
        ASTree[] arguments; // if non null, at least one element is non null.
        Function function;
        SpEntry next;

        SpEntry(ASTree t, ASTree[] a, Function f) {
            target = t;
            arguments = a;
            function = f;
            next = null;
        }
    }

    private HashMap<MethodInfo,Entry> templates;
    private HashMap<MethodInfo,SpEntry> functions;
    private ArrayList<D> dispatchers;

    public FunctionTable() {
        templates = new HashMap<MethodInfo,Entry>();
        functions = new HashMap<MethodInfo,SpEntry>();
        dispatchers = new ArrayList<D>();
    }

    public Iterator<T> iterator() {
        final Iterator<SpEntry> entries = functions.values().iterator();
        return new Iterator<T>() {
            SpEntry cur = null;
            @Override public boolean hasNext() {
                if (cur == null)
                    return entries.hasNext();
                else
                    return true;
            }

            @SuppressWarnings("unchecked")
            @Override public T next() {
                if (cur == null)
                    cur = entries.next();
                
                Function f = cur.function;
                cur = cur.next;
                return (T)f;
            }

            @Override public void remove() {
                throw new java.lang.UnsupportedOperationException();
            }
        };
    }

    /**
     * Finds a template.
     */
    public Function get(TraceContext context, MethodInfo minfo) {
        FunctionMetaclass fm = context.metaclass();
        Entry e = templates.get(minfo);
        while (e != null) {
            if (fm.accepts(e.function))
                return e.function;

            e = e.next;
        }

        return null;
    }

    /**
     * Records a template.
     * A template is a function that is not specialized for particular
     * arguments.  It can be used as a template when making a
     * specialized function.
     */
    public void put(MethodInfo info, Function f) {
        Entry e = new Entry(f);
        Entry found = templates.get(info);
        if (found == null)
            templates.put(info, e);
        else {
            while (found.next != null)
                found = found.next;

            found.next = e;
        }
    }

    /**
     * Records a dispatcher.
     */
    @SuppressWarnings("unchecked")
    public void add(Dispatcher d) {
        dispatchers.add((D)d);
    }

    /**
     * Returns an iterator over the dispatchers recorded in this object.
     */
    public List<D> dispatchers() { return dispatchers; }

    /**
     * Finds a function body specialized for {@code target} and {@code args}.
     */
    public Function get(TraceContext contexts, MethodInfo minfo, ASTree target, ASTree[] args)
        throws NotFoundException
    {
        FunctionMetaclass fm = contexts.metaclass();
        SpEntry e = functions.get(minfo);
        while (e != null) {
            if (equivalent(e.target, e.arguments, target, args))
                if (fm.accepts(e.function))
                    return e.function;

            e = e.next;
        }

        return null;
    }

    /**
     * Returns true if each type in args1 subsumes
     * the corresponding type in args2.
     */
    private boolean equivalent(ASTree target1, ASTree[] args1, ASTree target2, ASTree[] args2)
        throws NotFoundException
    {
        if (!equivalent(target1, target2))
            return false;

        if (args1 == args2)
            return true;    // e.g. args1 == args2 == null

        if (args1 == null || args2 == null
            || args1.length != args2.length)
            return false;

        for (int i = 0; i < args1.length; i++)
            if (!equivalent(args1[i], args2[i]))
                return false;

        return true;
    }

    /**
     * Returns true if each type in a1 subsumes
     * the corresponding type in a2.
     */
    private boolean equivalent(ASTree a1, ASTree a2) throws NotFoundException {
        if (a1 == null || a2 == null)
            return a1 == a2;
        else if (a1 instanceof ObjectConstant)
            return a2 instanceof ObjectConstant
                   && ((ObjectConstant)a1).theValue() == ((ObjectConstant)a2).theValue();
        else if (a1 instanceof New)
            return a1 == a2
                   || (a2 instanceof New
                       && ((New)a1).calledConstructor() == ((New)a2).calledConstructor());
        else if (a1 instanceof Null)
            return a2 instanceof Null;
        else
            return false;
    }

    /**
     * Records a function body specialized for {@code target} and {@code args}.
     */
    public int put(MethodInfo minfo, ASTree target, ASTree[] args, Function f) {
        SpEntry e = new SpEntry(target, args, f);
        SpEntry found = functions.get(minfo);
        if (found == null) {
            functions.put(minfo, e);
            return 0;
        }
        else {
            int num = 1;
            while (found.next != null) {
                num++;
                found = found.next;
            }

            found.next = e;
            return num;
        }
    }
}
