// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Call;
import javassist.offload.ast.GetField;
import javassist.offload.ast.New;
import javassist.offload.ast.PutField;
import javassist.offload.ast.AdhocAST;
import javassist.offload.ast.UserMetaclass;
import javassist.offload.ast.VisitorException;
import javassist.offload.javatoc.impl.JavaObjectToC;

/**
 * A native class.  Its instances consist of an object header and a body.
 * The body is a chunk of data of the specified size.  It is usually a
 * struct data specified in a C library.  When the instance is created,
 * the body is zero-cleared.
 * The instance also contains an 32bit integer field named <code>flag</code>.
 * It is freely used for maintaining the state of the instance.
 * The initial value of <code>flag</code> is 0.
 * When the instance is copied from Java to C by the serializer,
 * the <code>flag</code> field is reset to 0.
 * 
 * <p>For example,
 * <ul><code>
 * @Metaclass(type=NativeClass.class, arg = "sizeof(MPI_Request)")
 * public class Request {}
 * </code></ul>
 * This class {@code Request} is translated into the following {@code struct}:
 * <ul><code>
 * struct {
 *     int header;
 *     int flag;
 *     char body[sizeof(MPI_Request)];
 * };
 * </code></ul>
 * An instance of {@code Request} can be created by {@code new Request()}.
 * To obtain a pointer to the body, call {@link javassist.offload.lib.Unsafe#toNativeBody}.
 * It returns a pointer to the {@code body} member.
 * </p>
 *
 * <p>Note that all the fields declared within a native class are ignored
 * when the class is translated into C.  When an instance of such a class is
 * copied from the JVM into C code, then the field values in the C code are made
 * unknown.
 * </p>
 *
 * @see javassist.offload.Metaclass
 * @see javassist.offload.lib.Unsafe#toNativeBody(Object)
 */
public class NativeClass extends ClassDef implements UserMetaclass {
    public static final String BODY_FIELD = "body";
    public static final String FLAG_FIELD = "flag";

    public static final String STRUCT_BODY
        = "{" + CTypeDef.HEADER_DECL + " int " + FLAG_FIELD + "; double " + BODY_FIELD + "[1]; }";

    public static final String ACCESS_TYPE_NAME = "_native_class_base";

    /**
     * @see javassist.offload.lib.Unsafe#preamble()
     */
    public static final String ACCESS_TYPE = "struct " + ACCESS_TYPE_NAME + STRUCT_BODY + ";";

    /**
     * @see javassist.offload.lib.Unsafe#toNativeBody(Object)
     */
    public static final String ACCESSOR
        = "return (" + ClassDef.OBJECT_CLASS_TYPE_NAME + "*)((struct " + ACCESS_TYPE_NAME + "*)v1)->" + BODY_FIELD + ";";
    
    protected String arg;

    /**
     * Constructs a native class.
     *
     * @param cc        the class.
     * @param f         the fields contained in the class.
     * @param uid       a unique identifier.
     * @param size      the body size given by arg passed to @Metaclass. e.g. "sizeof(double)".
     * @param args      ignored.
     * @param companion ignored.
     */
    public NativeClass(CtClass cc, CtField[] f, int uid, String size, String[] args, Class<?> companion) {
        super(cc, f, uid);
        this.arg = size;
    }

    public void code(CodeGen gen) throws NotFoundException {
        gen.append(objectType());
        gen.append(" { ");
        gen.append(CTypeDef.HEADER_DECL);
        gen.append(" int ").append(FLAG_FIELD).append(";");
        gen.append(" double ").append(BODY_FIELD).append("[((");
        gen.append(arg);
        gen.append(") + sizeof(double) - 1) / sizeof(double)]; };\n");
    }

    /* The object data is not serialized.
     */
    public void objectToCode(Object obj, JavaObjectToC jo, CodeGen gen,
                             Class<?> klass, String gvarName)
        throws VisitorException
    {
        gen.recordGivenObject(obj, '&' + gvarName);
        gen.heapMemory().declarationCode(gen, obj, this, objectType(), gvarName);
        gen.append(" = { ");
        gen.append(typeId());
        gen.append(", 0 };\n");
    }

    public void instantiate(CodeGen gen, final New expr) throws VisitorException {
        String c = gen.heapMemory().calloc()
                   + "(1, sizeof(" + gen.classTable().objectTypeName(expr.type()) + "))";
        ASTree allocator = new AdhocAST(type(), c);
        instantiationCode(gen, expr, allocator, expr.constructor());
    }

    public void invokeMethod(CodeGen gen, Call expr) throws VisitorException {
        super.invokeMethod(gen, expr);
    }

    public void getField(CodeGen gen, GetField expr) throws VisitorException {
        notFound(expr.fieldName());
    }

    public void putField(CodeGen gen, PutField expr) throws VisitorException {
        notFound(expr.fieldName());
    }
}
