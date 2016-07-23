// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc.impl;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.offload.Final;
import javassist.offload.ast.VisitorException;
import javassist.offload.clang.CodeGen;
import javassist.offload.clang.HeapMemory;
import javassist.offload.clang.CTypeDef;
import javassist.offload.reify.Tracer;

/**
 * This generates C code representing a Java object.
 * It is used to pass Java objects to the C code at translation time.
 */
public class JavaObjectToC {
    
    /**
     * The number of global variables generated in this class.
     */
    private int globalId;

    /**
     * Constructs an object.
     * Since it maintains global-variable identifiers, it should be singleton.
     */
    public JavaObjectToC() { globalId = 0; }

    /**
     * Generates C code, which includes global variable
     * declarations for representing the given Java object.
     * The method records a map from the Java object to the name of
     * the corresponding global variable.
     *
     * @param obj           the Java object.
     */
    public void toCode(Object obj, CodeGen gen)
        throws VisitorException
    {
        if (obj == null)
            ; // objects.put(obj, "0");
        else if (obj instanceof Tracer.PrimitiveNumber) {
            Object v = ((Tracer.PrimitiveNumber)obj).value();
            gen.recordGivenObject(obj, toCvalue(v, v.getClass())); 
        } else
            try {
                objectToCode(obj, gen);
            } catch (IllegalAccessException e) {
                throw new VisitorException(e.getMessage(), e);
            }
    }

    public static String toCvalue(Object value, Class<?> type) {
        if (type == Boolean.class || type == boolean.class)
            return ((Boolean)value).booleanValue() ? "1" : "0";
        else if (type == Character.class || type == char.class)
            return Integer.toString(((Character)value).charValue());
        else if (type == Long.class)
            return value.toString() + "L";
        else if (type == Float.class)
            return value.toString() + "f";
        else
            return value.toString();
    }

    private void objectToCode(Object obj, CodeGen gen)
        throws VisitorException, IllegalAccessException
    {
        if (obj == null)
            return;
        else if (gen.isGivenObject(obj) != null)
            return;

        Class<?> klass = obj.getClass();
        String gvarName = "gvar" + globalId++;
        if (klass.isArray()) {
            gen.recordGivenObject(obj, gvarName);
            if (klass.getComponentType().isPrimitive())
                primArrayToCode(obj, klass, gen, gvarName);
            else {
                CTypeDef tdef = gen.addClass(gen.getCtClass(klass));
                arrayToCode(obj, klass, gen, tdef, gvarName);
            }
        }
        else {
            CTypeDef tdef = gen.addClass(gen.getCtClass(klass));
            gen.classTable().hasInstances(tdef);
            tdef.objectToCode(obj, this, gen, klass, gvarName);
        }
    }

    /**
     * Generates the declaration of the global variable
     * representing the given Java object.
     *
     * @param obj       the object.
     * @param gen       the code generator.
     * @param klass     the class of the object.
     * @param tdef      the type of the object.
     * @param gvarName  the name of the global variable in C.
     *
     * @see javassist.offload.clang.ClassDef#objectToCode(Object, CodeGen, Class, String)
     * @see javassist.offload.clang.ClassDef#staticFields(CodeGen)
     * @see javassist.offload.clang.ImmutableClass#objectToCode(Object, CodeGen, Class, String)
     */
    public void objectToCode(Object obj, CodeGen gen, Class<?> klass, CTypeDef tdef,
                             String gvarName)
        throws IllegalAccessException, VisitorException
    {
        /* If this method is modified, see ImmutableClass#objectToCode() as well.
         */
        gen.recordGivenObject(obj, '&' + gvarName);

        HeapMemory heap = gen.heapMemory();
        if (!heap.portableInitialization())
            heap.prototypeCode(gen, obj, tdef, tdef.objectType(), gvarName);

        Field[] fields = fieldsToCode(obj, gen, klass);

        heap.declarationCode(gen, obj, tdef, tdef.objectType(), gvarName);
        gen.append(" = { ");
        gen.append(tdef.typeId()).append(" << ").append(CTypeDef.FLAG_BITS);
        for (int i = 0; i < fields.length; i++) {
            if (!Modifier.isStatic(fields[i].getModifiers())) {
                Class<?> t = fields[i].getType();
                fields[i].setAccessible(true);
                Object value = fields[i].get(obj);
                gen.append(", ");
                if (t.isPrimitive())
                    gen.append(toCvalue(value, t));
                else if (value == null)
                    gen.append('0');     // NULL
                else if (heap.portableInitialization()) {
                    gen.append('0');     // NULL
                    CTypeDef fieldType = gen.addClass(gen.getCtClass(t));
                    String init = fieldType.objectToCodeInitializedLater(gen,
                                                tdef, t, gvarName, fields[i], value);
                    gen.heapMemory().addInitializer(init);
                }
                else {
                    if (t.isArray()) {
                        String cast = castForArray(t);
                        if (cast != null)
                            gen.append(cast);
                    }
                    else
                        if (t != value.getClass()) {
                            gen.append('(');
                            gen.append(gen.addClass(gen.getCtClass(t)).referenceType());
                            gen.append(')');
                        }

                    gen.append(gen.isGivenObject(value));
                }
            }
        }

        gen.append(" };\n");
    }

    /**
     * Generates code when the target language does not allow mutual reference
     * in the initialization code of global variables.
     *
     * @see HeapMemory#portableInitialization()
     */
    public static String objectToCodeInitializedLater(CodeGen gen, CTypeDef tdef, CTypeDef fieldTypeDef,
                            Class<?> fieldClass, String gvarName, Field field, Object value)
        throws VisitorException
    {
        StringBuilder sb = new StringBuilder();
        String memberName = tdef.fieldName(gen, field.getName());
        sb.append(gvarName).append('.').append(memberName).append(" = ");
        if (fieldClass.isArray()) {
            String cast = castForArray(fieldClass);
            if (cast != null)
                sb.append(cast);
        }
        else
            if (fieldClass != value.getClass()) {
                sb.append('(');
                sb.append(fieldTypeDef.referenceType());
                sb.append(')');
            }

        sb.append(gen.isGivenObject(value)).append(";\n");
        return sb.toString();
    }

    /**
     * Generates the code for field values in the object.
     *
     * @param obj       the object.
     * @return  all the fields of the object.
     */
    public Field[] fieldsToCode(Object obj, CodeGen gen, Class<?> klass)
        throws IllegalAccessException, VisitorException
    {
        Field[] fields = Tracer.getAllFields(klass);
        for (int i = 0; i < fields.length; i++) {
            if (!Modifier.isStatic(fields[i].getModifiers())) {
                Class<?> t = fields[i].getType();
                if (!t.isPrimitive()) {
                    fields[i].setAccessible(true);
                    Object value = fields[i].get(obj);
                    objectToCode(value, gen);
                }
            }
        }
        return fields;
    }

    private static void primArrayToCode(Object obj, Class<?> klass, CodeGen gen,
                                        String gvarName)
    {
        int len = Array.getLength(obj);
        Class<?> compClass = klass.getComponentType();
        int typeId = CTypeDef.primitiveArrayTypeId(klass);
        String compTypeName;
        if (typeId == CTypeDef.FLOAT_ARRAY)
            compTypeName = "int";
        else if (typeId == CTypeDef.DOUBLE_ARRAY)
            compTypeName = "long";
        else
            compTypeName = CTypeDef.primitiveTypeName(compClass);

        gen.heapMemory().declarationCode(gen, obj, null, compTypeName, gvarName);
        gen.append("[] = { ");
        pArrayHeadToCode(gen, typeId, len);
        for (int i = 0; i < len; i++) {
            gen.append(", ");
            Object v = Array.get(obj,  i);
            if (typeId == CTypeDef.FLOAT_ARRAY)
                gen.append(Float.floatToIntBits(((Float)v).floatValue()));
            else if (typeId == CTypeDef.DOUBLE_ARRAY) {
                gen.append("0x");
                long j = Double.doubleToLongBits(((Double)v).doubleValue());
                gen.append(Long.toHexString(j));
                gen.append("L");
            }
            else {
                gen.append(toCvalue(v, v.getClass()));
                if (typeId == CTypeDef.LONG_ARRAY)
                    gen.append("L");
            }
        }

        gen.append(" };\n");
    }

    /**
     * Returns the cast operator for an array expression.
     * It returns null if the cast operator is not needed.
     *
     * @param klass     the class of the array.
     */
    public static String castForArray(Class<?> klass) {
        if (klass == float[].class)
            return "(float*)";
        else if (klass == double[].class)
            return "(double*)";
        else
            return null;
    }

    private void arrayToCode(Object obj, Class<?> klass, CodeGen gen,
                             CTypeDef tdef, String gvarName)
        throws VisitorException, IllegalAccessException
    {
        int len = Array.getLength(obj);
        for (int i = 0; i < len; i++) {
            Object value = Array.get(obj, i);
            objectToCode(value, gen);
        }

        String compType = tdef.objectType();    // objectType() returns the component type.
        gen.heapMemory().declarationCode(gen, obj, tdef, compType, gvarName);
        gen.append("[] = { (");
        gen.append(compType).append(")");
        longArrayHeadToCode(gen, tdef.typeId(), len);
        boolean followsCxxSyntax = gen.heapMemory().portableInitialization();
        for (int i = 0; i < len; i++) {
            gen.append(", ");
            Object value = Array.get(obj, i);
            if (value == null)
                gen.append('0');
            else if (followsCxxSyntax) {
                gen.append('0');
                arrayToCodeInitLater(klass, gvarName, i + 1, value, compType,
                                     gen);
            }
            else {
                if (klass.getComponentType() != value.getClass()) {
                    gen.append('(');
                    gen.append(compType);
                    gen.append(')');
                }

                gen.append(gen.isGivenObject(value));
            }
        }

        gen.append(" };\n");
    }

    private static void arrayToCodeInitLater(Class<?> klass, String gvarName,
                                    int index, Object value, String compType,
                                    CodeGen gen)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(gvarName).append('[').append(index).append("] = ");
        if (klass.getComponentType() != value.getClass()) {
            sb.append('(');
            sb.append(compType);
            sb.append(')');
        }

        sb.append(gen.isGivenObject(value)).append(";\n");
        gen.heapMemory().addInitializer(sb.toString());
    }

    private static void pArrayHeadToCode(CodeGen gen, int typeId, int length) {
        if (typeId == CTypeDef.BOOL_ARRAY || typeId == CTypeDef.BYTE_ARRAY) {
            if (gen.littleEndian) {
                gen.append("0, 0, 0, ");
                gen.append(typeId);
                for (int i = 0; i < 32; i += 8) {
                    gen.append(", ");
                    gen.append((length >> i) & 0xff);
                }
            }
            else {
                gen.append(typeId);
                gen.append(", 0, 0, 0");
                for (int i = 24; i >= 0; i -= 8) {
                    gen.append(", ");
                    gen.append((length >> i) & 0xff);
                }
            }
        }
        else if (typeId == CTypeDef.CHAR_ARRAY || typeId == CTypeDef.SHORT_ARRAY) {
            if (gen.littleEndian) {
                gen.append("0, ").append(typeId << 8).append(", ");
                gen.append(length & 0xffff).append(", ").append(length >> 16);
            }
            else {
                gen.append(typeId << 8).append(", 0, ");
                gen.append(length >> 16).append(", ").append(length & 0xffff);
            }
        }
        else if (typeId == CTypeDef.LONG_ARRAY || typeId == CTypeDef.DOUBLE_ARRAY)
            longArrayHeadToCode(gen, typeId, length);
        else {
            gen.append("0x").append(Integer.toHexString(typeId << 24));
            gen.append(", ").append(length);
        }
    }

    private static void longArrayHeadToCode(CodeGen gen, int typeId, int length) {
        gen.append("0x");
        long value;
        if (gen.littleEndian)
            value = (((long)length) << 32) | ((long)typeId << 24);
        else
            value = (((long)typeId) << 56) | length;

        gen.append(Long.toHexString(value));
        gen.append('L');
    }

    /**
     * Generates the code for a static field.
     *
     * @param fieldName     the normalized name of the field.
     */
    public static void staticFieldToCode(CodeGen gen, String structTag,
                                         int uniqueNo, CtField field, String fieldName)
        throws NotFoundException, VisitorException
    {
        HeapMemory heap = gen.heapMemory();
        String gvarName = structTag + "_" + fieldName + "_" + uniqueNo;
        CtClass type = field.getType();
        String typeName = gen.typeName(type, true);
        CTypeDef tdef = gen.addClass(type);
        Object value = Tracer.getStaticFieldValue(field.getDeclaringClass().getName(),
                                                  field.getName(), type.isPrimitive());
        heap.declarationCode(gen, value, tdef, typeName, gvarName);
        String expr = gen.isGivenObject(value);
        if (expr != null)
            if (heap.portableInitialization()) {
                String code = gvarName + " = (" + typeName + ")" + expr + ";\n";
                heap.addInitializer(code);
            }
            else
                gen.append(" = (").append(typeName).append(')')
                      .append(expr);

        gen.append(";\n");
    }

    public static final boolean ONLY_FINAL = true; 

    /**
     * Returns the value of the specified field if the field is final.
     * Otherwise, this method returns null.
     */
    public static Object getFieldValue(Object obj, CtClass ctKlass, String fieldName, boolean onlyFinal)
        throws NotFoundException
    {
        try {
            Class<?> klass;
            if (obj == null)
                klass = Class.forName(ctKlass.getName());
            else
                klass = obj.getClass();

            return getFieldValue(obj, klass, fieldName, onlyFinal);
        } catch (ClassNotFoundException e) {
            throw new NotFoundException(ctKlass.getName());
        } catch (VisitorException e) {
            throw new NotFoundException(ctKlass.getName() + "." + fieldName, e);
        }
    }

    /**
     * A constant representing null.
     */
    public static final Object NULL = new Object();

    /**
     * Returns the value of the specified field.  It searches super classes.
     * The returned value may be {@link #NULL}.
     *
     * @param onlyFinal         If true, only the value of a final field is obtained.
     *                          If onlyFinal is true and the field is not final, this method returns null.
     */
    public static Object getFieldValue(Object obj, Class<?> klass, String fieldName, boolean onlyFinal)
        throws VisitorException
    {
        try {
            java.lang.reflect.Field f = Tracer.getField(klass, fieldName);
            if (onlyFinal && isNotFinal(f))
                return null;
            else {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v == null)
                    return NULL;
                else
                    return v;
            }
        }
        catch (Exception e) {
            throw new VisitorException("cannot access " + klass.getName() + "." + fieldName, e);
        }
    }

    private static boolean isNotFinal(java.lang.reflect.Field f) {
        return !java.lang.reflect.Modifier.isFinal(f.getModifiers())
               && f.getAnnotation(Final.class) == null;
    }
}
