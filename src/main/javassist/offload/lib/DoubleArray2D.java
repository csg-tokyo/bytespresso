// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.Metaclass;
import javassist.offload.clang.NativeMultiArrayClass;

/**
 * A two-dimensional array of double type.
 * It is translated into a native two-dimensional array in C
 * if it is constructed at the Java side.
 * Note that the array elements are not copied when this array object
 * is passed to the C side.
 */
@Metaclass(type=NativeMultiArrayClass.class, arg="double", args = { "xsize", "ysize" })
public class DoubleArray2D {
    /**
     * The size in the first dimension.
     */
    public final int xsize;

    /**
     * The size in the second dimension. 
     */
    public final int ysize;

    private double[] data;

    /**
     * Constructs an array.
     *
     * @param x     the size in the first dimension.
     * @param y     the size in the second dimension.
     */
    public DoubleArray2D(int x, int y) {
        xsize = x;
        ysize = y;
        initData(false);
    }

    /**
     * Constructs an array.
     *
     * @param x     the size in the first dimension.
     * @param y     the size in the second dimension.
     * @param emptyInJava       if true, the memory is not allocated at the Java side.
     */
    public DoubleArray2D(int x, int y, boolean emptyInJava) {
        xsize = x;
        ysize = y;
        initData(emptyInJava);
    }

    private void initData(boolean emptyInJava) {
        if (emptyInJava)
            data = new double[0];
        else
            data = new double[xsize * ysize];
    }

    /**
     * Gets the value of the element at [i][j].
     * It is available only in C.
     */
    public double get(int i, int j) { return data[i * ysize + j]; }

    /**
     * Sets the value of the element at [i][j] to v.
     * It is available only in C. 
     */
    public void set(int i, int j, double v) { data[i * ysize + j] = v; }
}

