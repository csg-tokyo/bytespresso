// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.AdhocASTList;
import javassist.offload.ast.New;
import javassist.offload.ast.VisitorException;
import javassist.offload.javatoc.impl.JavaObjectToC;

/**
 * An array of native objects.  The array elements are not initialized.
 *
 * <p>For example,
 * <ul><code>
 * @Metaclass(type=NativeArrayClass.class, arg = "sizeof(MPI_Request)")
 * public class RequestArray {
 *     int length;
 *
 *     @Native("")
 *     public RequestArray(int len) { length = len; }

 *     // returns a pointer to the i-th element of the array.
 *     @Native("return ((char*)v1->body + v2 * sizeof(MPI_Request));")
 *     public byte[] get(int i) { return null; }
 * }
 * </code></ul>
 *
 * This class {@code RequestArray} is translated into the following {@code struct}:
 *
 * <ul><code>
 * struct {
 *     int header;
 *     int flag;
 *     double body[1];    // more elements are allocated when created.
 * };
 * </code></ul>
 *
 * An instance of {@code RequestArray} can be created by {@code new RequestArray(s)},
 * where {@code s} is the size of the array.  The {@code body} member is accessed
 * as an array of {@code MPI_Request} with the size {@code s}. 
 * </p>
 *
 * <p>Note that all the fields declared within a native class are ignored
 * when the class is translated into C.</p>
 */
public class NativeArrayClass extends NativeClass {
    static final String LENGTH_FIELD = "length";

    public NativeArrayClass(CtClass cc, CtField[] f, int uid, String size, String[] args, Class<?> companion) {
        super(cc, f, uid, size, args, companion);
    }

    public void code(CodeGen gen) throws NotFoundException {
        gen.append(objectType());
        gen.append(STRUCT_BODY);
        gen.append(";\n");
    }

    public void instantiate(CodeGen gen, final New expr) throws VisitorException {
        ASTree[] args = expr.arguments();
        if (args.length != 1) {
            String msg = "bad constructor arguments to a NativeArrayClass: "
                         + args.length;
            throw new RuntimeException(msg);
        }

        ASTree allocator = new AdhocASTList(NativeArrayClass.this.type(),
                gen.heapMemory().malloc() + "((" + arg + ")*(", args[0],
                ")+sizeof(" + objectType() + "))");

        instantiationCode(gen, expr, allocator, null);
    }

    public void objectToCode(Object obj, JavaObjectToC jo, CodeGen gen,
                             Class<?> klass, String gvarName)
        throws VisitorException
    {
        Object len = JavaObjectToC.getFieldValue(obj, klass, LENGTH_FIELD, false);
        if (len == null)
            throw new VisitorException("NativeArrayClass " + type().getName()
                                       + " must not be instantiated on the JVM");

        String dataName = gvarName + "_data";
        gen.heapMemory().declarationCode(gen, obj, null, "char", dataName);
        gen.append("[((").append(arg).append(")*(").append((int)len)
              .append("))+sizeof(").append(objectType()).append(")]; __attribute__((aligned(64)))\n");
        gen.recordGivenObject(obj, '&' + gvarName);
        gen.heapMemory().declarationCode(gen, obj, this, referenceType(), gvarName);
        gen.append(" = (").append(referenceType()).append(')').append(dataName).append(";\n");
    }
}
