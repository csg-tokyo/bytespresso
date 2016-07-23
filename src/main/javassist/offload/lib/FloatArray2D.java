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
@Metaclass(type=NativeMultiArrayClass.class, arg="float", args = { "xsize", "ysize" })
public class FloatArray2D implements Cloneable {
    /**
     * The size in the first dimension.
     */
    public final int xsize;

    /**
     * The size in the second dimension. 
     */
    public final int ysize;

    @Final private float[] data;

    /**
     * Constructs an array.
     *
     * @param x     the size in the first dimension.
     * @param y     the size in the second dimension.
     */
    public FloatArray2D(int x, int y) {
        xsize = x;
        ysize = y;
        initData(false);
    }

    /**
     * Constructs an array.
     *
     * @param x     the size in the first dimension.
     * @param y     the size in the second dimension.
     * @param notUsedInJava       if true, the memory is not allocated at the Java side.
     */
    public FloatArray2D(int x, int y, boolean notUsedInJava) {
        xsize = x;
        ysize = y;
        initData(notUsedInJava);
    }

    private void initData(boolean emptyInJava) {
        if (emptyInJava)
            data = new float[0];
        else
            data = new float[xsize * ysize];
    }

    /**
     * Gets the value of the element at [i][j].
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public float get(int i, int j) { return data[i * ysize + j]; }

    /**
     * Sets the value of the element at [i][j] to v.
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public void set(int i, int j, float v) { data[i * ysize + j] = v; }

    /**
     * Sets the value of all the elements to v.
     * It is available only in C
     * if the memory is not allocated at the Java side. 
     */
    public void setAll(float v) {
        for(int i=0; i < xsize; i++)
            for(int j=0; j < ysize; j++)
                    set(i, j, v);    
    }

    /**
     * Returns a C pointer to the float array where the data is stored.
     */
    public float[] toCArray() { return data; }

    /**
     * Returns the offset to the specified element in the float array
     * in C. 
     */
    public int offset(int i, int j) { return i * ysize + j; }
}
