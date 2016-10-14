// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.Metaclass;
import javassist.offload.clang.NativeMultiArrayClass;

/**
 * A two-dimensional array of int type.
 * It is translated into a native two-dimensional array in C
 * if it is constructed at the Java side.
 * Note that the array elements are not copied when this array object
 * is passed to the C side.
 */
@Metaclass(type=NativeMultiArrayClass.class, arg="int", args = { "size" })
public class IntArray implements Cloneable {
    /**
     * The size.
     */
    public final int size;

    private final int[] data;

    /**
     * Constructs an array.
     *
     * @param s     the size.
     */
    public IntArray(int s) {
        size = s;
        data = new int[s];
    }

    /**
     * Constructs an array.
     *
     * @param s     the size.
     * @param notUsedInJava       if true, the memory is not allocated at the Java side.
     */
    public IntArray(int s, boolean notUsedInJava) {
        size = s;
        if (notUsedInJava)
            data = new int[0];
        else
            data = new int[size];
    }

    /**
     * Gets the value of the element at [i].
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public int get(int i) { return data[i]; }

    /**
     * Sets the value of the element at [i] to v.
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public void set(int i, int v) { data[i] = v; }

    /**
     * Sets the value of all the elements to v.
     * It is available only in C
     * if the memory is not allocated at the Java side. 
     */
    public void setAll(int v) {
        for(int i=0; i < size; i++)
            set(i, v);    
    }

    /**
     * Returns a C pointer to the int array where the data is stored.
     */
    public int[] toCArray() { return data; }

    /**
     * Returns the offset to the specified element in the int array
     * in C. 
     */
    public int offset(int i) { return i; }
}
