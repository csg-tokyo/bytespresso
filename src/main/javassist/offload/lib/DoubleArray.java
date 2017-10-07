// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.Metaclass;
import javassist.offload.clang.NativeMultiArrayClass;

/**
 * A one-dimensional array of double type.
 * It is translated into a native two-dimensional array in C
 * if it is constructed at the Java side.
 * Note that the array elements are not copied when this array object
 * is passed to the C side.
 */
@Metaclass(type=NativeMultiArrayClass.class, arg="double", args = { "size" })
public class DoubleArray implements Cloneable {
    /**
     * The size.
     */
    public final int size;

    private final double[] data;

    /**
     * Constructs an array.
     *
     * @param s     the size.
     */
    public DoubleArray(int s) {
        size = s;
        data = new double[s];
    }

    /**
     * Constructs an array.
     *
     * @param s     the size.
     * @param notUsedInJava       if true, the memory is not allocated at the Java side.
     */
    public DoubleArray(int s, boolean notUsedInJava) {
        size = s;
        if (notUsedInJava)
            data = new double[0];
        else
            data = new double[size];
    }

    /**
     * Gets the value of the element at [i].
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public double get(int i) { return data[i]; }

    /**
     * Sets the value of the element at [i] to v.
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public void set(int i, double v) { data[i] = v; }

    /**
     * Sets the value of all the elements to v.
     * It is available only in C
     * if the memory is not allocated at the Java side. 
     */
    public void setAll(double v) {
        for(int i = 0; i < size; i++)
            set(i, v);    
    }

    /**
     * Returns a C pointer to the double array where the data is stored.
     */
    public double[] toCArray() { return data; }

    /**
     * Returns the offset to the specified element in the double array
     * in C. 
     */
    public int offset(int i) { return i; }
}
