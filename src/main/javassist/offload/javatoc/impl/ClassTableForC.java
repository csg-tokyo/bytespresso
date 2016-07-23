// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.offload.Metaclass;
import javassist.offload.ast.TypeDef;
import javassist.offload.clang.ArrayDef;
import javassist.offload.clang.ClassDef;
import javassist.offload.clang.ImmutableClass;
import javassist.offload.clang.StringClass;
import javassist.offload.clang.CTypeDef;
import javassist.offload.reify.ClassTable;

/**
 * A class table for generating C code.
 */
public class ClassTableForC implements ClassTable {
    private int classId, arrayTypeId;
    private HashMap<CtClass,CTypeDef> classes;
    private OrderedClasses orderedClasses;
    private int numOfInstantiatedClasses;

    public ClassTableForC(ClassPool cp) throws NotFoundException {
        classId = CTypeDef.CLASS_TYPE;
        arrayTypeId = CTypeDef.OBJECT_ARRAY;
        classes = new HashMap<CtClass,CTypeDef>();
        orderedClasses = new OrderedClasses(this);
        CtClass stringClass = cp.get(String.class.getName());
        CTypeDef stringType = new StringClass(stringClass);
        classes.put(stringClass, stringType);
        stringType.update(orderedClasses);
        numOfInstantiatedClasses = 0;
        addObjectType(cp.get(Object.class.getName()));
    }

    public Collection<? extends TypeDef> allTypes() {
        return classes.values();
    }

    public int newClassId() { return classId++; }

    public int newArrayTypeId() {
        if (arrayTypeId >= CTypeDef.BOOL_ARRAY)
            throw new RuntimeException("too many array types");

        return arrayTypeId++;
    }

    /**
     * Returns all the types in the order considering mutual dependence.
     * If the definition of type T contains type S, then S is positioned
     * before T.
     */
    public ArrayList<CTypeDef> sortedTypes() {
        HashMap<CTypeDef,CTypeDef> set = new HashMap<CTypeDef,CTypeDef>();
        ArrayList<CTypeDef> list = orderedClasses.get(set);
        for (CTypeDef t: classes.values())
            if (set.get(t) == null) {
                set.put(t, t);
                list.add(t);
            }

        return list;
    }

    /**
     * Records a class and makes the type definition of the class.
     * If the class is already recorded, it only returns the type
     * definition.
     *
     * @param cc        the recorded class.
     * @return          the type definition of the recorded class.
     */
    public CTypeDef addClass(CtClass cc) throws NotFoundException {
        // if this method is changed, addObjectType() must be also changed.

        if (cc.isPrimitive())
            return null;

        CTypeDef def = classes.get(cc);
        if (def == null)
            if (cc.isArray()) {
                CtClass component = cc.getComponentType();
                CTypeDef compType = addClass(component);
                def = new ArrayDef(cc, CTypeDef.arrayTypeName(component, compType),
                                   newArrayTypeId());
                classes.put(cc, def);
                def.update(orderedClasses);
            }
            else {
                Metaclass meta = selectMetaclass(cc);
                CtField[] fields = getAllFields(cc);
                if (meta == null)
                    def = new ClassDef(cc, fields, newClassId());
                else
                    def = (CTypeDef)makeTypeDef(cc, meta, fields);

                classes.put(cc, def);
                addSuperTypes(cc, def);
                def.update(orderedClasses);
            }

        return def;
    }

    public CTypeDef addClass(CtClass cc1, CtClass cc2) throws NotFoundException {
        CTypeDef t1 = addClass(cc1);
        CTypeDef t2 = addClass(cc2);
        if (t1 instanceof ImmutableClass)
            t2.update(orderedClasses);

        return t2;
    }

    /**
     * Adds java.lang.Object at initialization.
     *
     * @param cc    the CtClass object for java.lang.Object.
     */
    private void addObjectType(CtClass cc) throws NotFoundException {
        CtField[] fields = getAllFields(cc);
        CTypeDef def = ClassDef.makeObjectClassDef(cc, fields, newClassId());
        classes.put(cc, def);
        addSuperTypes(cc, def);
        def.update(orderedClasses);
    }

    private void addSuperTypes(CtClass cc, CTypeDef tdef) throws NotFoundException {
        CtClass sup = cc.getSuperclass();
        if (sup != null)
            addClass(sup).addSubtype(tdef);

        for (CtClass i: cc.getInterfaces())
            addClass(i).addSubtype(tdef);
    }

    private static CtField[] getAllFields(CtClass cc) throws NotFoundException {
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

    /**
     * Records that the given type will be instantiated.
     *
     * @param tdef      the type.
     */
    public void hasInstances(TypeDef tdef) {
        numOfInstantiatedClasses++;
        tdef.hasInstances(true);
    }

    /**
     * Returns the number of types that will be instantiated.
     */
    public int instantiatedTypes() { return numOfInstantiatedClasses; }

    public CTypeDef typeDef(CtClass cc) { return classes.get(cc); }

    /**
     * Returns the type name in the C language.  The returned type name
     * can be used for type casts and prototype declarations.
     * 
     * @param useVoid    if it is true, void* is returned
     *                   when the unknown type is given.
     */
    public String typeName(CtClass cc, boolean useVoid) {
        if (cc.isPrimitive())
            return CTypeDef.primitiveTypeName(cc);
        else {
            CTypeDef def = classes.get(cc);
            if (def == null)
                if (useVoid)
                    return "void*";
                else
                    throw new RuntimeException("unknown type: " + cc.getName());
            else
                return def.referenceType();
        }
    }

    public String objectTypeName(CtClass cc) {
        if (cc.isPrimitive())
            return CTypeDef.primitiveTypeName(cc);
        else {
            CTypeDef def = classes.get(cc);
            if (def == null)
                throw new RuntimeException("unknown type: " + cc.getName());
            else
                return def.objectType();
        }
    }
}

