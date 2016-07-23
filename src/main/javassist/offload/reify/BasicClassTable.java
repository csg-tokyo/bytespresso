// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.offload.Metaclass;
import javassist.offload.ast.Callable;
import javassist.offload.ast.Call;
import javassist.offload.ast.Dispatcher;
import javassist.offload.ast.Function;
import javassist.offload.ast.TypeDef;

/**
 * A class table only for constructing an AST.
 * It implements minimum functionality.
 */
public class BasicClassTable implements ClassTable {

    static class BasicTypeDef implements TypeDef {
        private CtClass type;
        private boolean hasInstances;
        private ArrayList<TypeDef> subclasses;
        private ArrayList<Dispatcher> dispatchers;

        BasicTypeDef(CtClass t) {
            type = t;
            hasInstances = false;
            subclasses = new ArrayList<TypeDef>();
            dispatchers = new ArrayList<Dispatcher>();
        }

        public CtClass type() { return type; }
        public Inliner inliner(Callable f) { return null; }
        public Metaclass metaclassForSubtypes() { return null; }

        public boolean hasInstances() { return hasInstances; }
        public void hasInstances(boolean yes) { hasInstances = yes; }

        public boolean isMacro(Call expr) { return false; }
        public boolean isNative(CtBehavior method) { return false; }

        public Function add(Function f, CtBehavior m) { return f; }

        public void add(Dispatcher d) { dispatchers.add(d); }
        public List<Dispatcher> allDisptachers() { return dispatchers; }

        public List<TypeDef> getSubtypes() { return subclasses; }
        public void addSubtype(TypeDef t) { subclasses.add(t); }

        public boolean instantiationIsSimple() { return false; }
        
        public String toString() {
            return type.getName();
        }
    }

    private HashMap<CtClass,TypeDef> classes;
    private int classId;
    private int numOfInstantiatedClasses;
    private ClassPool cpool;

    public BasicClassTable(ClassPool cp) {
        classes = new HashMap<CtClass,TypeDef>();
        classId = 0;
        numOfInstantiatedClasses = 0;
        cpool = cp;
    }

    public int newClassId() { return classId++; }

    public void hasInstances(TypeDef tdef) {
        numOfInstantiatedClasses++;
        tdef.hasInstances(true);
    }

    public int instantiatedTypes() { return numOfInstantiatedClasses; }

    public Collection<? extends TypeDef> allTypes() {
        return classes.values();
    }

    public TypeDef addClass(Class<?> c) throws NotFoundException {
        String name = c.getName();
        CtClass cc = cpool.get(Lambda.getLambdaProxyName(name));
        return addClass(cc);
    }

    public TypeDef addClass(CtClass cc) throws NotFoundException {
        if (cc.isPrimitive())
            return null;

        TypeDef def = classes.get(cc);
        if (def == null) {
            if (cc.isArray()) {
                CtClass component = cc.getComponentType();
                addClass(component);
            }

            Metaclass meta = selectMetaclass(cc);
            CtField[] fields = getAllFields(cc);
            if (meta == null)
                def = new BasicTypeDef(cc);
            else
                def = makeTypeDef(cc, meta, fields);

            classes.put(cc, def);
            addSuperTypes(cc, def);
        }

        return def;
    }

    public TypeDef addClass(CtClass cc1, CtClass cc2) throws NotFoundException {
        addClass(cc1);
        return addClass(cc2);
    }

    /**
     * Collects all the fields in the given class.
     */
    public static CtField[] getAllFields(CtClass cc) throws NotFoundException {
        ArrayList<CtField> array = new ArrayList<CtField>();
        getAllFields(cc, array, true);
        return array.toArray(new CtField[array.size()]);
    }

    private static void getAllFields(CtClass cc, ArrayList<CtField> fields,
                                     boolean includeStatic)
            throws NotFoundException
    {
        CtClass superClass = cc.getSuperclass();
        if (superClass != null)
            getAllFields(superClass, fields, false);

        CtField[] fs = cc.getDeclaredFields();
        for (CtField f: fs)
            if (!Modifier.isStatic(f.getModifiers()) || includeStatic)
                fields.add(f);
    }

    private void addSuperTypes(CtClass cc, TypeDef tdef) throws NotFoundException {
        CtClass sup = cc.getSuperclass();
        if (sup != null)
            addClass(sup).addSubtype(tdef);

        for (CtClass i: cc.getInterfaces())
            addClass(i).addSubtype(tdef);
    }
}
