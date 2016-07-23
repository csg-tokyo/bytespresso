// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.Code;
import javassist.offload.Intrinsic;
import javassist.offload.Native;
import javassist.offload.clang.ClassDef;
import javassist.offload.clang.NativeClass;
import javassist.offload.clang.StringClass;
import javassist.offload.javatoc.CCode;

public class Unsafe {
    /**
     * The type name of {@code java.lang.Object} in C.
     */
    public static final String OBJECT_CLASS_NAME = ClassDef.OBJECT_CLASS_TYPE_NAME;

    /**
     * Returns the preamble.
     */
    public static String preamble() {
        return NativeClass.ACCESS_TYPE + "\n\n";
    }

    /**
     * Deallocates an object.
     */
    @Intrinsic public static void free(Object obj) {
        if (CCode.inTranslation()) {
            Code ptr = (Code)obj;
            String s = CCode.heapMemory().free();
            CCode.make(s).add("(").add(ptr).add(")").emit();
        }
    }

    /**
     * Reads an element of an array of int.
     *
     * @param obj       a pointer to the array in C.
     * @param index     the index of the element.
     */
    @Native("return *((int*)v1+v2);")
    public static int getInt(@NativePtr int[] a, int index) {
        return a[index];
    }

    /**
     * Reads an element of an array of double.
     *
     * @param obj       a pointer to the array in C.
     * @param index     the index of the element.
     */
    @Native("return *((double*)v1+v2);")
    public static double getDouble(@NativePtr double[] a, int index) {
        return a[index];
    }

    /**
     * Writes a double value into a double array.
     *
     * @param obj       a pointer to the double array in C.
     * @param index     the index of the element.
     * @param value     the stored value.
     */
    @Native("*((double*)v1+v2) = v3;")
    public static void setDouble(@NativePtr double[] a, int index, double value) {
        a[index] = value;
    }

    /**
     * Writes a float value into a float array.
     *
     * @param obj       a pointer to the float array in C.
     * @param index     the index of the element.
     * @param value     the stored value.
     */
    @Native("*((float*)v1+v2) = v3;")
    public static void setFloat(@NativePtr float[] a, int index, float value) {
        a[index] = value;
    }

    /**
     * Stores an int value in the four elements starting at the index
     * in the given byte array.
     *
     * @param obj           the byte array in C.
     * @param index         the value is stored in the elements starting
     *                      at this index in the byte array.
     * @param value         the stored value.
     */
    @Native("*((int*)&v1[v2]) = v3;")
    public static void set(@NativePtr byte[] obj, int index, int value) {
        throw new RuntimeException("bad assignment");
    }

    /**
     * Stores an int value in the element at the index
     * in the given int array.
     *
     * @param obj           the int array in C.
     * @param index         the value is stored in the element
     *                      at this index in the int array.
     * @param value         the stored value.
     */
    @Native("*((int*)&v1[v2]) = v3;")
    public static void set(@NativePtr int[] obj, int index, int value) {
        throw new RuntimeException("bad assignment");
    }

    /**
     * Stores a long value in the two elements starting at the index
     * in the given int array.
     *
     * @param obj           the int array in C.
     * @param index         the value is stored in the element
     *                      at this index in the int array.
     * @param value         the stored value.
     */
    @Native("*((long*)&v1[v2]) = v3;")
    public static void set(@NativePtr int[] obj, int index, long value) {
        throw new RuntimeException("bad assignment");
    }

    /**
     * Stores a float value in the element at the index
     * in the given int array.
     *
     * @param obj           the int array in C.
     * @param index         the value is stored in the element
     *                      at this index in the int array.
     * @param value         the stored value.
     */
    @Native("union { int i; float f; } tmp; tmp.f = v3; *((int*)&v1[v2]) = tmp.i;")
    public static void set(@NativePtr int[] obj, int index, float value) {
        throw new RuntimeException("bad assignment");
    }

    /**
     * Stores a double value in the two element starting at the index
     * in the given int array.
     *
     * @param obj           the int array in C.
     * @param index         the value is stored in the two elements starting
     *                      at this index in the int array.
     * @param value         the stored value.
     */
    @Native("union { long i; double f; } tmp; tmp.f = v3; *((long*)&v1[v2]) = tmp.i;")
    public static void set(@NativePtr int[] obj, int index, double value) {
        throw new RuntimeException("bad assignment");
    }

    /**
     * Stores a 64bit pointer in the elements starting at the index
     * in the given int array.
     *
     * @param obj           the int array in C.
     * @param index         the value is stored in the two elements starting
     *                      at this index in the int array.
     * @param value         the stored pointer value.
     */
    @Native("*((void**)&v1[v2]) = (void*)v3;")
    public static void set(@NativePtr int[] obj, int index, Object value) {
        throw new RuntimeException("bad assignment");
    }

    /**
     * Converts an array object into a pointer to the array in C.
     *
     * @return a pointer in C.
     */
    @Native("return &v1[8];")
    public static byte[] toCArray(byte[] a) { return a; }

    /**
     * Converts an array object into a pointer to the array in C.
     *
     * @return a pointer in C.
     */
    @Native("return &v1[2];")
    public static int[] toCArray(int[] a) { return a; }

    /**
     * Converts an array object into a pointer to the array in C.
     *
     * @return a pointer in C.
     */
    @Native("return &v1[2];")
    public static float[] toCArray(float[] a) { return a; }

    /**
     * Converts an array object into a pointer to the array in C.
     *
     * @return a pointer in C.
     */
    @Native("return &v1[1];")
    public static long[] toCArray(long[] a) { return a; }

    /**
     * Converts an array object into a pointer to the array in C.
     *
     * @return a pointer in C.
     */
    @Native("return &v1[1];")
    public static double[] toCArray(double[] a) { return a; }

    /**
     * Obtains a pointer to the body part of the given object.
     * The meta class for this object must be {@link NativeClass}. 
     *
     * @return a pointer in C.
     */
    @Native(NativeClass.ACCESSOR)
    public static <T> T toNativeBody(T obj) { return obj; }

    /**
     * Converts a String object into a pointer
     * to the character string in C.
     *
     * @return a pointer in C.
     */
    @Native(StringClass.ACCESSOR)
    public static String toCStr(String s) { return s; }
}
