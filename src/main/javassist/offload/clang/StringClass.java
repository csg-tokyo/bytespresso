// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import java.io.IOException;
import java.io.OutputStream;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.offload.ast.Call;
import javassist.offload.ast.GetField;
import javassist.offload.ast.New;
import javassist.offload.ast.PutField;
import javassist.offload.ast.VisitorException;
import javassist.offload.javatoc.impl.JavaObjectToC;

/**
 * A java.lang.String class.  It has a specialized implementation.
 * The implementation is represented by the following data structure:
 *
 * <p><pre>
 * struct {
 *   int header;
 *   int length;
 *   char body[length];  // the last element is \0.
 * }
 * </pre></p>
 *
 * @see javassist.offload.lib.Jvm#readString()
 * @see javassist.offload.lib.Unsafe#toCStr(String)
 */
public class StringClass extends ClassDef {
    /**
     * The type name of this object.
     */
    public static final String STRUCT = "struct java_string";

    /**
     * The body of the native method for obtaining the character string of this object.
     *
     * @see javassist.offload.lib.Unsafe
     */
    public static final String ACCESSOR = "return (" + STRUCT + "*)((" + STRUCT + "*)v1)->body;";

    public StringClass(CtClass clazz) {
        super(clazz, CTypeDef.CLASS_TYPE_STRING, STRUCT);
    }

    public int typeId() {
        return CTypeDef.CLASS_TYPE_STRING;
    }

    public String referenceType() {
        return STRUCT + "*";
    }

    public String objectType() {
        return STRUCT;
    }

    public void staticFields(CodeGen gen) {
        // no filelds.
    }

    /* The body field contains the null character at the end.
     * The length field holds the return value of String#length().
     * It is (the actual size of the body field) - 1.  The null character is excluded.
     *
     * @see javassist.offload.ast.ClassDef#code(javassist.offload.decompiler.Generator)
     */
    public void code(CodeGen gen) throws NotFoundException {
        gen.append(STRUCT + " {" + CTypeDef.HEADER_DECL + "int length; char body[1]; };\n");
    }

    public static void codeLiteral(CodeGen gen, String value) {
        gen.append("((" + STRUCT + "*)");
        encodeString(gen, value);
        gen.append(')');
    }

    public void objectToCode(Object obj, JavaObjectToC jo, CodeGen gen,
                             Class<?> klass, String gvarName)
    {
        gen.recordGivenObject(obj, "((" + STRUCT + "*)" + gvarName + ")");
        gen.heapMemory().declarationCode(gen, obj, this, "char", gvarName);
        gen.append("[] = ");
        encodeString(gen, (String)obj);
        gen.append(";\n");
    }

    private static void encodeString(CodeGen gen, String str) {
        gen.append("\"");
        encodeInt(CTypeDef.CLASS_TYPE_STRING, gen);
        int len = str.length();
        encodeInt(len, gen);
        gen.append("\" \"");
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c == '\n')
                gen.append("\\n");
            else if (c == '\"' || c == '\'' || c == '\\')
                gen.append('\\').append(c);
            else
                gen.append(c);
        }

        gen.append('\"');
    }

    private static void encodeInt(int v, CodeGen gen) {
        if (gen.littleEndian) {
            encodeByte(v, gen);
            encodeByte(v >> 8, gen);
            encodeByte(v >> 16, gen);
            encodeByte(v >> 24, gen);
        }
        else {
            encodeByte(v >> 24, gen);
            encodeByte(v >> 16, gen);
            encodeByte(v >> 8, gen);
            encodeByte(v, gen);
        }
    }

    private static void encodeByte(int v, CodeGen gen) {
        gen.append('\\');
        gen.append(Integer.toOctalString(v & 0xff));
    }

    public boolean hasCustomSerializer() { return true; }

    /**
     * Serializes an object.
     *
     * @see javassist.offload.javatoc.impl.Serializer#findReadMethod(CtClass, CtClass, String)
     * @see javassist.offload.javatoc.impl.Serializer#findWriteMethod(CtClass, CtClass, String)
     * @see javassist.offload.javatoc.impl.Serializer#readValue(Class, java.io.InputStream)
     * @see javassist.offload.javatoc.impl.Serializer#readValue(CtClass, CtClass, java.io.InputStream)
     * @see javassist.offload.javatoc.impl.Serializer#writeValue(Class, Object, OutputStream)
     */
    public void serializeObject(Object obj, OutputStream output)
        throws IOException
    {
        notImplemented(obj.getClass().getName());
    }

    public boolean isNative(CtBehavior method) {
        return true;
    }

    public void instantiate(CodeGen gen, New expr) {
        notImplemented(expr.type().getName());
    }

    public void invokeMethod(CodeGen gen, Call expr) throws VisitorException {
        String methodName = expr.methodName();
        if ("length".equals(methodName)) {
            gen.append("((struct java_string*)")
               .append(expr.target())
               .append(")->length");
        }
        else if ("charAt".equals(methodName)) {
            gen.append("((struct java_string*)")
                .append(expr.target())
                .append(")->body[")
                .append(expr.arguments()[0])
                .append(']');
        }
        else
            super.invokeMethod(gen, expr);
    }

    public void getField(CodeGen gen, GetField expr) throws VisitorException {
        notFound(expr.fieldName());
    }

    public void putField(CodeGen gen, PutField expr) throws VisitorException {
        notFound(expr.fieldName());
    }
}
