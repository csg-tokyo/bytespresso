// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.Final;
import javassist.offload.Metaclass;
import javassist.offload.clang.NativeMultiArrayClass;

/**
 * A two-dimensional array of float type.
 * It is translated into a native two-dimensional array in C
 * if it is constructed at the Java side.
 * Note that the array elements are not copied when this array object
 * is passed to the C side.
 */
@Metaclass(type=NativeMultiArrayClass.class, arg="float", args = { "size" })
public class FloatArray implements Cloneable {
    /**
     * The size.
     */
    public final int size;

    @Final private float[] data;

    /**
     * Constructs an array.
     *
     * @param s     the size.
     */
    public FloatArray(int s) {
        size = s;
        initData(false);
    }

    /**
     * Constructs an array.
     *
     * @param s     the size.
     * @param notUsedInJava       if true, the memory is not allocated at the Java side.
     */
    public FloatArray(int s, boolean notUsedInJava) {
        size = s;
        initData(notUsedInJava);
    }

    private void initData(boolean emptyInJava) {
        if (emptyInJava)
            data = new float[0];
        else
            data = new float[size];
    }

    /**
     * Gets the value of the element at [i].
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public float get(int i) { return data[i]; }

    /**
     * Sets the value of the element at [i] to v.
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public void set(int i, float v) { data[i] = v; }

    /**
     * Sets the value of all the elements to v.
     * It is available only in C
     * if the memory is not allocated at the Java side. 
     */
    public void setAll(float v) {
        for(int i=0; i < size; i++)
                set(i, v);    
    }

    /**
     * Returns a C pointer to the float array where the data is stored.
     */
    public float[] toCArray() { return data; }

    /**
     * Returns the offset to the specified element in the float array
     * in C. 
     */
    public int offset(int i) { return i; }
}
