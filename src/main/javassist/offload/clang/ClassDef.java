// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.offload.Final;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Callable;
import javassist.offload.ast.Block;
import javassist.offload.ast.Call;
import javassist.offload.ast.Dispatcher;
import javassist.offload.ast.GetField;
import javassist.offload.ast.TypeDef;
import javassist.offload.ast.New;
import javassist.offload.ast.Null;
import javassist.offload.ast.ObjectConstant;
import javassist.offload.ast.PutField;
import javassist.offload.ast.AdhocAST;
import javassist.offload.ast.VisitorException;
import javassist.offload.javatoc.impl.JavaObjectToC;
import javassist.offload.reify.Tracer;

/**
 * Class definition.
 *
 * <p>A Java class is translated into a C struct.
 * Every struct includes the 32bit header field for holding
 * the type id.  The upper 24bits of the header represent
 * the type id and the lower 8bits are flags.
 * </p>
 *
 * @see New
 * @see Call
 * @see GetField
 * @see PutField
 */
public class ClassDef extends CTypeDef {
    private String structName;
    private int typeId;
    private CtField[] fields;
    private ArrayList<TypeDef> subclasses;
    private ArrayList<Dispatcher> dispatchers;
    private boolean hasInstances;

    private static final int STRUCT_LEN = "struct ".length();

    /**
     * The type name of the struct in C representing {@code java.lang.Object}.
     */
    public static final String OBJECT_CLASS_TYPE_NAME = "struct java_object "; 

    /**
     * Constructs a ClassDef for {@code java.lang.Object}.
     *
     * @param cc        the {@code CtClass} object representing {@code java.lang.Object}. 
     * @param f         the fields of {@code java.lang.Object}.
     * @param uid       a unique identifier. 
     */
    public static ClassDef makeObjectClassDef(CtClass cc, CtField[] f, int uid) {
        return new ClassDef(cc, f, uid, OBJECT_CLASS_TYPE_NAME);
    }

    /**
     * Constructs a class definition.
     *
     * @param f     all the fields including private ones and the ones
     *              of the super classes.
     */
    public ClassDef(CtClass cc, CtField[] f, int uid) {
        this(cc, f, uid, "struct " + normalizedName(cc.getSimpleName()) + "_" + uid); 
    }

    static String normalizedName(String name) {
        return name.replace('$', '_');
    }

    protected ClassDef(CtClass cc, int uid, String name) {
        this(cc, new CtField[0], uid, name);
    }

    protected ClassDef(CtClass cc, CtField[] f, int uid, String name) {
        super(cc);
        typeId = uid;
        fields = normalizeFields(f);
        structName = name;
        subclasses = null;
        dispatchers = null;
        hasInstances = false;
    }

    private static CtField[] normalizeFields(CtField[] f) {
        CtField[] f2 = new CtField[f.length];
        CtField[] f3 = new CtField[f.length];
        int i2 = 0;
        int i3 = 0;
        for (int i = 0; i < f.length; i++) {
            if (Modifier.isStatic(f[i].getModifiers()))
                f3[i3++] = f[i];
            else
                f2[i2++] = f[i];
        }

        int k = 0;
        while (i2 < f.length)
            f2[i2++] = f3[k++]; 

        return f2;
    }

    public String toString() {
        return "class " + type().getName();
    }

    public int typeId() { return typeId; }

    public void update(OrderedTypes set) throws NotFoundException {
        for (CtField f: fields()) {
            CTypeDef t = set.typeDef(f.getType());
            /* If the field f is not accessed,
             * then t should be null.
             */
            if (t != null && t instanceof ImmutableClass)
                set.require(this, t); 
        }
    }

    public String referenceType() {
        return structName + "*";
    }

    public String objectType() {
        return structName;
    }

    public void addSubtype(TypeDef t) {
        if (subclasses == null)
            subclasses = new ArrayList<TypeDef>();

        subclasses.add(t);
    }

    public List<TypeDef> getSubtypes() {
        if (subclasses == null)
            return new ArrayList<TypeDef>();

        return subclasses;
    }

    public void hasInstances(boolean yes) { hasInstances = yes; }

    public boolean hasInstances() { return hasInstances; }

    public void add(Dispatcher d) {
        if (dispatchers == null)
            dispatchers = new ArrayList<Dispatcher>();

        // only adding to the tail is allowed.
        // see Tracer#revisitDispatchers()
        dispatchers.add(d);
    }

    public List<Dispatcher> allDisptachers() { return dispatchers; }

    /**
     * Returns all the fields including private ones.
     */
    public CtField[] fields() { return fields; }

    /**
     * @see #nonStaticFieldNames()
     * @see #code(CodeGen)
     */
    protected String fieldName(CodeGen gen, String name, boolean mustBeWritable) throws VisitorException {
        String normName = normalizedName(name);
        for (int i = 0; i < fields.length; i++)
            if (fields[i].getName().equals(name)) {
                if (mustBeWritable && fields[i].hasAnnotation(Final.class))
                    throw new RuntimeException("cannot set a @Final field within the translated code: " + name
                                               + " in " + fields[i].getDeclaringClass().getName());

                if (Modifier.isStatic(fields[i].getModifiers()))
                    return structName.substring(STRUCT_LEN) + "_" + normName + "_" + i;
                else
                    return normName + "_" + i;
            }

        try {
            CtClass sup = type().getSuperclass();
            if (sup == null)
                return super.fieldName(gen, normName, mustBeWritable);
            else {
                CTypeDef tdef = gen.typeDef(sup);
                return tdef.fieldName(gen, normName, mustBeWritable);
            }
        }
        catch (NotFoundException e) {
            throw new VisitorException(e);
        }
    }

    /**
     * Returns the C names of non-static fields.
     *
     * @param hidden        if true, the header field is included.
     */
    protected String[] nonStaticFieldNames(boolean hidden) {
        ArrayList<String> names = new ArrayList<String>();
        if (hidden)
            names.add(CTypeDef.HEADER_FIELD);

        for (int i = 0; i < fields.length; i++) {
            if (Modifier.isStatic(fields[i].getModifiers()))
                continue;

            names.add(normalizedName(fields[i].getName()) + '_'
                      + Integer.toString(i));
        }

        return names.toArray(new String[names.size()]);
    }

    /**
     * Generates the preamble for class definitions.
     */
    public static void preamble(CodeGen gen) {}

    /*
     * @see #fieldName(CodeGen, String, boolean)
     * @see #nonStaticFieldNames()
     */
    public void code(CodeGen gen) throws NotFoundException {
        gen.append(structName);
        gen.append(" {");
        gen.append(CTypeDef.HEADER_DECL);
        for (int i = 0; i < fields.length; i++) {
            if (Modifier.isStatic(fields[i].getModifiers()))
                continue;

            gen.append(gen.typeName(fields[i].getType(), true));
            gen.append(' ');
            gen.append(normalizedName(fields[i].getName()));
            gen.append('_');
            gen.append(Integer.toString(i));
            gen.append("; ");
        }

        gen.append("};\n");
    }

    public void staticFields(CodeGen gen) throws NotFoundException, VisitorException {
        String structTag = structName.substring(STRUCT_LEN);
        for (int i = 0; i < fields.length; i++) {
            CtField field = fields[i];
            int mod = field.getModifiers();
            if (Modifier.isStatic(mod))
                JavaObjectToC.staticFieldToCode(gen, structTag, i, field,
                                             normalizedName(field.getName()));
        }
    }

    public void objectToCode(Object obj, JavaObjectToC jo, CodeGen gen,
                             Class<?> klass, String gvarName)
        throws IllegalAccessException, VisitorException
    {
        jo.objectToCode(obj, gen, klass, this, gvarName);
    }

    public void instantiate(CodeGen gen, New expr) throws VisitorException {
        instantiationCode(gen, expr, null, expr.constructor());
    }

    /**
     * Generates the normal code for object creation.
     * This method only calls accept() on the allocator.
     *
     * @param newExpr           the {@code new} expression.
     * @param allocator         null or the expression allocating a memory block.
     * @param cons              the expression calling the constructor.
     */
    public void instantiationCode(CodeGen gen, New newExpr,
                                  ASTree allocator, ASTree cons)
        throws VisitorException
    {
        // If this method is modified, the deserializer must be also
        // updated.  See javassist.offload.lib.Deserializer.

        gen.append('(');
        gen.append(newExpr.temporaryVariable());
        gen.append('=');
        gen.append('(');
        gen.append(gen.typeName(newExpr.type()));
        gen.append(')');
        if (allocator == null) {
            gen.append(gen.heapMemory().calloc() + "(1, sizeof(");
            gen.append(gen.classTable().objectTypeName(newExpr.type()));
            gen.append("))");
        }
        else
            gen.append(allocator);

        gen.append(',');
        gen.append(newExpr.temporaryVariable());
        gen.append("->" + CTypeDef.HEADER_FIELD + "=(");
        gen.append(typeId());
        gen.append("<<").append(CTypeDef.FLAG_BITS).append(')');
        if (cons != null) {
            gen.append(',');
            gen.append(cons);
        }

        if (newExpr.doesReturnThis()) {
            gen.append(',');
            gen.append(newExpr.temporaryVariable());
        }

        gen.append(')');
    }

    public void invokeMethod(CodeGen gen, Call expr) throws VisitorException {
        Callable f = expr.calledFunction();
        if (f == null) {
            if (expr.methodName().equals("getClass") && expr.isStatement() instanceof Block) {
                // Since getClass() is a native method, f will be null.
                // Ignore Object#getClass() if its return value is discarded
            }
            else
                throw new VisitorException("not found the body of " + expr.targetType().getName()
                                            + "." + expr.methodName());
        }
        else
            ((CallableCode)f).callerCode(gen, expr);
    }

    public void getField(CodeGen gen, GetField expr) throws VisitorException {
        if (inlineGetField(gen, expr))
            return;

        if (expr.target() != null) {
            gen.append(expr.target())
               .append("->");
        }

        gen.append(fieldName(gen, expr.fieldName()));
    }

    protected boolean inlineGetField(CodeGen gen, final GetField expr)
        throws VisitorException
    {
        try {
            return inlineGetField2(gen, expr);
        }
        catch (NotFoundException e) {
            throw new VisitorException(e);
        }
    }

    private boolean inlineGetField2(CodeGen gen, final GetField expr)
        throws VisitorException, NotFoundException
    {
        final ASTree v = expr.value();
        if (v instanceof Null) {
            gen.append('(');
            doCast(gen, expr.type(), v);
            gen.append(')');
            return true;
        }
        else if (v instanceof ObjectConstant)
            if (objectConstant(gen, (ObjectConstant)v, expr.type()))
                return true;

        ASTree t = expr.target();
        if (t != null) {
            final ASTree tv = t.value();
            if (tv instanceof ObjectConstant) {
                Object obj = ((ObjectConstant)tv).theValue();
                if (obj != null) {
                    final String var = gen.isGivenObject(obj);
                    if (var != null) {
                        if (castNeeded(gen, expr.type(), tv)) {
                            gen.append('(');
                            doCast(gen, expr.type(),
                                   new AdhocAST(tv.type(),
                                       dereference(var) + "." + fieldName(gen, expr.fieldName())));
                            gen.append(')');
                        }
                        else {
                            gen.append(dereference(var)).append('.');
                            gen.append(fieldName(gen, expr.fieldName()));
                        }

                        return true;
                    }
                }
            }
        }

        return false;
    }

    protected boolean objectConstant(CodeGen gen, ObjectConstant value, CtClass toType)
        throws VisitorException
    {
        Object obj = value.theValue();
        if (obj != null) {
            if (obj instanceof Number) {
                if (obj instanceof Integer) {
                    gen.append(obj.toString());
                    return true;
                }
                else if (obj instanceof Long) {
                    gen.append(obj.toString()).append('L');
                    return true;
                }
                else if (obj instanceof Double) {
                    gen.append(obj.toString());
                    return true;
                }
                else if (obj instanceof Float) {
                    gen.append(obj.toString()).append('F');
                    return true;
                }
            }
            else {
                final String var = gen.isGivenObject(obj);
                if (var != null) { 
                    CTypeDef fromType = gen.typeDef(value.type());
                    // fromType is null if type() is an array type.
                    if (castNeeded(fromType, gen, toType, value)) {
                        gen.append('(');
                        ClassDef.doCast(fromType, gen, toType, new AdhocAST(type(), var));
                        gen.append(')');
                    }
                    else
                        gen.append(var);

                    return true;
                }
            }
        }

        return false;
    }

    protected String dereference(String expr) {
        if (expr.charAt(0) == '&')
            return expr.substring(1);
        else
            return "(*(" + expr + "))";
    }

    protected CtClass typeOfInlinedGetField(CodeGen gen, GetField expr) throws NotFoundException, VisitorException {
        ASTree v = expr.value();
        if (v instanceof Null)
            return CtClass.intType;     // since it is 0.
        else if (v instanceof ObjectConstant) {
            Object obj = ((ObjectConstant)v).theValue();
            if (obj != null) {
                if (obj instanceof Number) {
                    if (obj instanceof Integer)
                        return CtClass.intType;
                    else if (obj instanceof Long)
                        return CtClass.longType;
                    else if (obj instanceof Double)
                        return CtClass.doubleType;
                    else if (obj instanceof Float)
                        return CtClass.floatType;
                }
                else {
                    String var = gen.isGivenObject(obj);
                    if (var != null)
                    	return gen.getCtClass(obj.getClass());
                }
            }
        }

        ASTree t = expr.target();
        if (t != null) {
            ASTree tv = t.value();
            if (tv != null && tv instanceof ObjectConstant) {
                Object obj = ((ObjectConstant)tv).theValue();
                if (obj != null) {
                    String var = gen.isGivenObject(obj);
                    if (var != null)
                    	return fieldType(gen, obj, expr.fieldName());
                }
            }
        }

        return null;
    }

    protected CtClass fieldType(CodeGen gen, Object obj, String fieldName) throws VisitorException {
        Exception e;
        try {
            return gen.getCtClass(Tracer.getField(obj.getClass(), fieldName).getType());
        }
        catch (NoSuchFieldException nfe) { e = nfe; }
        catch (SecurityException se) { e = se; }
        throw new VisitorException(fieldName + " in " + obj.getClass().getName(), e);
    }

    public void putField(CodeGen gen, PutField expr) throws VisitorException {
        if (!inlinePutField(gen, expr)) {
            if (expr.target() != null)
                gen.append(expr.target()).append("->");

            gen.append(fieldName(gen, expr.fieldName(), true));
        }

        gen.append(" = ");
        CTypeDef.doCastOnValue(gen, expr.fieldType(), expr.value());
    }

    protected boolean inlinePutField(CodeGen gen, final PutField expr) throws VisitorException {
        ASTree t = expr.target();
        if (t != null) 
            try {
                final ASTree tv = t.value();
                if (tv != null && tv instanceof ObjectConstant) {
                    Object obj = ((ObjectConstant)tv).theValue();
                    if (obj != null) {
                        final String var = gen.isGivenObject(obj);
                        if (var != null) {
                            gen.append(dereference(var)).append('.');
                            gen.append(fieldName(gen, expr.fieldName(), true));
                            return true;
                        }
                    }
                }
            }
            catch (NotFoundException e) {
                throw new VisitorException(e);
        }

        return false;
    }

    public void getHeader(CodeGen gen, ASTree expr) throws VisitorException {
        gen.append(expr)
           .append("->").append(CTypeDef.HEADER_FIELD);
    }
}
