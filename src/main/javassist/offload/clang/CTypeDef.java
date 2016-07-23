// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.offload.Metaclass;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Callable;
import javassist.offload.ast.Assign;
import javassist.offload.ast.Call;
import javassist.offload.ast.Dispatcher;
import javassist.offload.ast.Function;
import javassist.offload.ast.GetField;
import javassist.offload.ast.TypeDef;
import javassist.offload.ast.New;
import javassist.offload.ast.Null;
import javassist.offload.ast.PutField;
import javassist.offload.ast.AdhocAST;
import javassist.offload.ast.Variable;
import javassist.offload.ast.VisitorException;
import javassist.offload.javatoc.impl.JavaObjectToC;
import javassist.offload.lib.Jvm;
import javassist.offload.reify.Inliner;

/**
 * Type definition.
 *
 * @see ClassDef
 * @see ArrayDef
 */
public abstract class CTypeDef implements TypeDef {
    /*
     * The type ID is a 24bit value.
     * Unless the type is a primitive type or an array type,
     * the upper 8bits of the type ID are 0.
     * If the type is an array type, the lower 16bits are 0.
     */

    /* If the type is an object array type, the upper 8bits
     * are x01 to 0xef.  The lower 16bits are 0.
     */
    public static final int OBJECT_ARRAY = 0x01;

    public static final int BOOL_ARRAY = 0xf0;
    public static final int BYTE_ARRAY = 0xf1;
    public static final int CHAR_ARRAY = 0xf2;
    public static final int SHORT_ARRAY = 0xf3;
    public static final int INT_ARRAY = 0xf4;
    public static final int LONG_ARRAY = 0xf5;
    public static final int FLOAT_ARRAY = 0xf6;
    public static final int DOUBLE_ARRAY = 0xf7;

    public static final int BOOL = 0xf8;
    public static final int BYTE = 0xf9;
    public static final int CHAR = 0xfa;
    public static final int SHORT = 0xfb;
    public static final int INT = 0xfc;
    public static final int LONG = 0xfd;
    public static final int FLOAT = 0xfe;
    public static final int DOUBLE= 0xff;

    /* The upper 8bits is 0 if the type ID represents an object type.
     */
    public static final int CLASS_TYPE_HEAD = 0x00;

    public static final int CLASS_TYPE_NULL = 0;        // null type.  24bit, 0x000000.
    public static final int CLASS_TYPE_CUSTOM = 1;      // used by the deserializer.
    public static final int CLASS_TYPE_STRING = 2;      // java.lang.String

    /* If the type is an object type, the type ID is
     * 3 to 0x007fff.  0x008000..0x00ffff are used for object IDs.
     */
    public static final int CLASS_TYPE = 3;

    /* A bit mask used for object (de)serialization.
     * The bit is 0 if the ID is a type ID, or
     *            1 if the ID is an object ID.
     */
    public static final int OBJECT_ID_BIT = 0x8000;

    /* Every object has a 32bit header.
     */
    public static final String HEADER_TYPE = " int ";
    public static final String HEADER_FIELD = "header_";
    public static final String HEADER_DECL = HEADER_TYPE + HEADER_FIELD + "; ";

    /* The first 32bits of every object compose a header field. 
     * The upper 24bits are for the type ID.
     * The lower 8bits are for flags.
     */
    public static final int FLAG_BITS = 8;
                                            
    private CtClass type;

    /**
     * Constructs a type definition.
     */
    public CTypeDef(CtClass t) {
        type = t;
    }

    /**
     * Returns the type.
     */
    public CtClass type() { return type; }

    public static final Inliner inliner = new Inliner();

    /**
     * Returns an function-inlining engine.
     */
    public Inliner inliner(Callable f) { return inliner; }

    /**
     * Returns the type ID.
     */
    public abstract int typeId();

    /**
     * Returns the type name for the references
     * to this type in C.
     */
    public abstract String referenceType();

    /**
     * Return the type name of this type in C.
     * If the type is an array, it returns the name of the component
     * type.
     *
     * @see ArrayDef#objectType()
     */
    public abstract String objectType();

    /**
     * Returns the metaclass for the subtypes.
     * It may return null if the metaclass is the default one.
     */
    public Metaclass metaclassForSubtypes() { return null; }

    /**
     * A partially ordered set of types. 
     */
    public static interface OrderedTypes {
        /**
         * Returns the TypeDef object corresponding to the given CtClass
         * object.
         *
         * @param cc        the given CtClass object.
         */
        CTypeDef typeDef(CtClass cc);

        /**
         * Records that t1 requires t2.
         */
        void require(CTypeDef t1, CTypeDef t2);
    }

    /**
     * Adds partial orders between this type and other types.
     * The orders are used when type declarations in C are
     * generated.  If a type t1 requires a type t2, then
     * the declaration of t1 is generated after t2.
     *
     * @param set       the partial orders are added to this set.
     */
    public void update(OrderedTypes set) throws NotFoundException {}

    /**
     * Records a subtype of this type.
     *
     * @param t     a subtype.
     */
    public void addSubtype(TypeDef t) {
        throw new RuntimeException(t.type().getName() + " is not a subtype of " + type().getName());
    }

    /**
     * Records this type will be instantiated.
     *
     * @param yes       true if it is instantiated.
     */
    public void hasInstances(boolean yes) {
        throw new RuntimeException("cannot instantiate: " + type().getName());
    }

    /**
     * Returns true if this type is instantiated.
     */
    public boolean hasInstances() { return false; }

    /**
     * Returns all the recorded subtypes.
     *
     * @see #addSubtype(TypeDef)
     */
    public List<TypeDef> getSubtypes() { return new ArrayList<TypeDef>(); }

    /**
     * Is invoked when a new method is included into the set of
     * translated methods.  It may translate the received method and
     * return it.
     *
     * @param f     the AST of the method.
     * @param m     the method (or the constructor).
     * @return      a translated method.
     */
    public Function add(Function f, CtBehavior m) throws NotFoundException { return f; }

    /**
     * Is invoked when a new dispatcher is created.
     * It may record the dispatcher for {@link #add(Dispatcher)}
     * and {@link #findDispatcher(CtMethod)}.
     * A dispatcher is an internal function
     * that invokes the appropriate method implementation according to the
     * actual type of the receiver object.  The static type of the receiver
     * must be the type represented by this TypeDef object.
     */
    public void add(Dispatcher d) {
        throw new RuntimeException(type().getName() + " doesn't have a method: "
                                   + d.method().getLongName());
    }

    /**
     * Returns all the recorded dispatchers.
     *
     * @return  null        if no dispatcher is recorded.
     */
    public List<Dispatcher> allDisptachers() { return null; }

    /**
     * Generates the code for static fields.
     *
     * @param objects   the map from a Java object to the C expression representing the
     *                  reference to that object.
     * @see ClassDef
     */
    public abstract void staticFields(CodeGen gen) throws NotFoundException, VisitorException;

    /**
     * Translates the field name into the 
     * member name. 
     */
    public final String fieldName(CodeGen gen, String name) throws VisitorException {
        return fieldName(gen, name, false);
    }

    String fieldName(CodeGen gen, String name, boolean mustBeWritable) throws VisitorException {
        throw new VisitorException("no such a field: " + name + " in " + type().getName());
    }

    /**
     * Translates a primitive Java type into a C type.
     */
    public static String primitiveTypeName(CtClass cc) {
        if (cc == CtClass.booleanType)
            return "char";
        else if (cc == CtClass.byteType)
            return "signed char";
        else if (cc == CtClass.charType)
            return "unsigned short";
        else if (cc == CtClass.shortType)
            return "signed short";
        else
            return cc.getName();
    }

    /**
     * Translates a primitive Java type into a C type.
     */
    public static String primitiveTypeName(Class<?> klass) {
       if (klass == boolean.class)
           return "char";
       else if (klass == byte.class)
           return "signed char";
       else if (klass == char.class)
           return "unsigned short";
       else if (klass == short.class)
           return "signed short";
       else if (klass == int.class)
           return "int";
       else if (klass == long.class)
           return "long";
       else if (klass == float.class)
           return "float";
       else if (klass == double.class)
           return "double";
       else
           throw new RuntimeException("not primitive type: " + klass.getName());
    }

    /**
     * Translates a primitive Java array type into a C type.
     */
    public static int primitiveArrayTypeId(Class<?> klass) {
       if (klass == boolean[].class)
           return BOOL_ARRAY;
       else if (klass == byte[].class)
           return BYTE_ARRAY;
       else if (klass == char[].class)
           return CHAR_ARRAY;
       else if (klass == short[].class)
           return SHORT_ARRAY;
       else if (klass == int[].class)
           return INT_ARRAY;
       else if (klass == long[].class)
           return LONG_ARRAY;
       else if (klass == float[].class)
           return FLOAT_ARRAY;
       else if (klass == double[].class)
           return DOUBLE_ARRAY;
       else
           throw new RuntimeException("not primitive type: " + klass.getName());
    }

    /**
     * Return the type name in C.
     * 
     * @param compo              the component type.
     * @param compoDef           the component type definition.
     */
    public static String arrayTypeName(CtClass compo, CTypeDef compoDef) {
        if (compo.isPrimitive())
            return CTypeDef.primitiveTypeName(compo) + "*";
        else {
            if (compoDef == null)
                throw new RuntimeException("unknown type: " + compo.getName());
            else
                return compoDef.referenceType() + "*";
        }
    }

    /**
     * Generates the type declaration in C.
     */
    public abstract void code(CodeGen gen) throws NotFoundException;

    /**
     * Generates a variable declaration.
     */
    public static void varDeclaration(CodeGen gen, boolean useVoid, Variable var)
        throws VisitorException
    {
        gen.append(gen.typeName(var.type(), useVoid));
        gen.append(' ');
        gen.visitAsLvalue(var);
    }

    /**
     * Generates the variable (or function header) declaration. 
     */
    public static void varDeclaration(CodeGen gen, boolean useVoid, AdhocAST name)
        throws VisitorException
    {
        gen.append(gen.typeName(name.type(), useVoid));
        gen.append(' ');
        gen.append(name);
    }

    /**
     * Generates a cast operator from this type.
     * If this method is overridden, {@link #castNeeded(CodeGen, CtClass, ASTree)} should
     * be also overridden.
     *
     * @param toType    the destination type.
     * @param value     the operand.
     */
    public void doCast(CodeGen gen, CtClass toType, ASTree value)
        throws NotFoundException, VisitorException
    {
        // call the default implementation
        doCast0(gen, toType, value);
    }

    public static void doCastOnValue(CodeGen gen, CtClass toType, ASTree value)
        throws VisitorException
    {
        CTypeDef def = gen.typeDef(value.type());
        if (def == null)
            doCast0(gen, toType, value);
        else
            try {
                def.doCast(gen, toType, value);
            } catch (NotFoundException e) {
                throw new VisitorException(e);
            }
    }

    static void doCast(CTypeDef self, CodeGen gen, CtClass toType, ASTree value) throws VisitorException {
        if (self == null)
            doCast0(gen, toType, value);
        else
            try {
                self.doCast(gen, toType, value);
            } catch (NotFoundException e) {
                throw new VisitorException(e);
            }
    }

    static void doCast0(CodeGen gen, CtClass toType, ASTree value)
        throws VisitorException
    {
        if (value.type() != toType) {
            CTypeDef toDef = gen.typeDef(toType);
            if (toDef instanceof ImmutableClass && value instanceof Null) 
                throw new VisitorException("how to cast null to ImmutableClass");

            gen.append('(');
            gen.append(gen.typeName(toType));
            gen.append(')');
        }

        value.accept(gen);
    }

    /**
     * Returns true if {@code doCast()} will (probably) insert a cast operator.
     */
    protected boolean castNeeded(CodeGen gen, CtClass toType, ASTree value) throws VisitorException {
        // call the default implementation.
        return castNeeded0(toType, value);
    }

    static boolean castNeeded(CTypeDef self, CodeGen gen, CtClass toType, ASTree value)
        throws VisitorException
    {
        if (self == null)
            return castNeeded0(toType, value);
        else
            return self.castNeeded(gen, toType, value);
    }

    /**
     * Returns true if {@code doCast()} will (probably) insert a cast operator.
     */
    private static boolean castNeeded0(CtClass toType, ASTree value) {
        return value.type() != toType;
    }

    /**
     * Generates the C code representing the given Java object of this type.
     *
     * @param obj       the Java object.
     * @param gen       the generator.
     * @param gvarName  the global variable name representing the object in C.
     */
    public void objectToCode(Object obj, JavaObjectToC jo, CodeGen gen,
                             Class<?> klass, String gvarName)
        throws IllegalAccessException, VisitorException
    {
        throw new RuntimeException("not implemented: " + klass.getName());
    }

    /**
     * Returns object-initialization code when the target language does not allow
     * mutual reference in the initialization code of global variables.
     *
     * @see HeapMemory#portableInitialization()
     */
    public String objectToCodeInitializedLater(CodeGen output, CTypeDef tdef,
                                               Class<?> fieldClass, String gvarName,
                                               Field field, Object value)
        throws VisitorException
    {
        return JavaObjectToC.objectToCodeInitializedLater(output, tdef, this,
                                            fieldClass, gvarName, field, value);
    }

    /**
     * Returns true if this type has custom serializer/deserializer.
     */
    public boolean hasCustomSerializer() { return false; }

    /**
     * Serializes the given object of this type.
     * This method is invoked only if hasCustomSerializer() returns true.
     *
     * @param obj       the object.
     * @see #hasCustomSerializer()
     * @see #serializeRawData(int, byte[], OutputStream)
     */
    public void serializeObject(Object obj, OutputStream output)
        throws IOException
    {
        notImplemented(obj.getClass().getName());
    }

    /**
     * Serializes the given object given as raw data.
     * It is a helper method for implementing serializeObject().
     *
     * @param typeid            the type id.
     * @param body              the memory image of the object except the header word.
     * @see #serializeObject(Object, OutputStream) 
     */
    public static void serializeRawData(int typeid, byte[] body, OutputStream output)
        throws IOException
    {
        // see javassist.offload.lib.Deserializer.readRawData().
        Jvm.writeByte(output, CTypeDef.CLASS_TYPE_HEAD);
        Jvm.writeShort(output, CTypeDef.CLASS_TYPE_CUSTOM);
        Jvm.writeInt(output, typeid << 8);
        Jvm.writeByte(output, body);
    }

    /**
     * Returns true if the method is native and no C code is generated.
     */
    public boolean isNative(CtBehavior method) {
        return false;
    }

    /**
     * Generates the code for object creation.
     *
     * @param expr      a new expression.
     */
    public void instantiate(CodeGen gen, New expr) throws VisitorException {
        notImplemented(expr.type().getName());
    }

    protected void notImplemented(String className) {
        throw new RuntimeException("not implemented: " + className);
    }

    /**
     * Returns true if the code for object creation is a single function call.
     *
     * @return  false if this implementation is invoked.
     */
    public boolean instantiationIsSimple() { return false; }

    /**
     * Generates the code for assignment.
     *
     * @param expr      an assignment expression.
     */
    public void doAssign(CodeGen gen, Assign expr) throws VisitorException {
        doAssign0(gen, expr);
    }

    public static void doAssign0(CodeGen gen, Assign expr) throws VisitorException {
        ASTree left = expr.left();
        if (left instanceof Variable)
            gen.visitAsLvalue((Variable)left);
        else
            left.accept(gen);

        gen.append(" = ");
        CTypeDef.doCastOnValue(gen, expr.left().type(), expr.right());
    }

    /**
     * Returns true if the invoked method is expanded as a macro.
     * If true, the body of the method does not have to be translated into a function in C.
     * If false is returned, a {@link Function} object is constructed
     * for the invoked method.
     */
    public boolean isMacro(Call expr) throws NotFoundException {
        return false;
    }

    /**
     * Generates the code for a method call.
     *
     * @param expr      a method-call expression.
     */
    public void invokeMethod(CodeGen gen, Call expr) throws VisitorException {
        notFound(expr.methodName());
    }

    /**
     * Generates the code for a field read.
     *
     * @param expr      a field-read expression.
     */
    public void getField(CodeGen gen, GetField expr) throws VisitorException {
        notFound(expr.fieldName());
    }

    /**
     * Generates the code for a field write.
     *
     * @param expr      a field-write expression.
     */
    public void putField(CodeGen gen, PutField expr) throws VisitorException {
        notFound(expr.fieldName());
    }

    /**
     * Generates the code for reading the object header.
     */
    public void getHeader(CodeGen gen, ASTree expr) throws VisitorException {
        notFound("the hidden header field");
    }

    protected void notFound(String member) throws VisitorException {
        throw new VisitorException("not found: " + member + " in " + type().getName());
    }
}
