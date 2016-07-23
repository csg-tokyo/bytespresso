package javassist.offload.test;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.offload.clang.CodeGen;
import javassist.offload.clang.NativeClass;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.AdhocASTList;
import javassist.offload.ast.New;
import javassist.offload.ast.VisitorException;
import javassist.offload.clang.CTypeDef;
import javassist.offload.javatoc.impl.JavaObjectToC;

/**
 * An array of native objects.  The array elements are not initialized.
 *
 * <p>For example,
 * <ul><code>
 * @Metaclass(type=NativeArrayClass.class, arg = "sizeof(MPI_Request)")
 * public class RequestArray {
 *     @Native("")
 *     public RequestArray(int s) {}

 *     // returns a pointer to the i-th element of the array.
 *     @Native("return (signed char*)&v1->body[v2];")
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
 */
public class NativeArrayClassMD extends NativeClass {
    protected String[] args;
    protected boolean multiDimensions;

    public NativeArrayClassMD(CtClass cc, CtField[] f, int uid, String size, String[] args, Class<?> companion) {
        super(cc, f, uid, size, args, companion);
        this.args = args;
        multiDimensions = args.length > 0;
    }

    public void code(CodeGen gen) throws NotFoundException {
        gen.append(objectType());
        if (multiDimensions) {
            gen.append(" { ");
            gen.append(CTypeDef.HEADER_DECL);
            gen.append(arg).append(' ').append(BODY_FIELD);
            for (int i = 0; i < args.length; i++) {
                Object a = JavaObjectToC.getFieldValue(null, type(), args[i], !JavaObjectToC.ONLY_FINAL);
                gen.append('[').append(a.toString()).append(']');
            }

            gen.append("; }");
        }
        else
            gen.append(STRUCT_BODY);

        gen.append(";\n");
    }

    public void instantiate(CodeGen gen, final New expr) throws VisitorException {
        if (multiDimensions) {
            super.instantiate(gen, expr);
            return;
        }

        ASTree[] args = expr.arguments();
        if (args.length != 1) {
            String msg = "bad constructor arguments to a NativeArrayClass: "
                         + args.length;
            throw new RuntimeException(msg);
        }

        ASTree allocator = new AdhocASTList(NativeArrayClassMD.this.type(),
                gen.heapMemory().malloc() + "((" + arg + ")*(",
                args[0], ")+sizeof(" + objectType() + "))");

        instantiationCode(gen, expr, allocator, null);
    }
}
