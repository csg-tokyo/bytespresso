// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.Final;
import javassist.offload.Metaclass;
import javassist.offload.clang.NativeMultiArrayClass;

/**
 * A three-dimensional array of float type.
 * It is translated into a native three-dimensional array in C
 * if it is constructed at the Java side.
 * Note that the array elements are not copied when this array object
 * is passed to the C side.
 */
@Metaclass(type=NativeMultiArrayClass.class, arg="float", args = { "xsize", "ysize", "zsize" })
public class FloatArray3D {
    /**
     * The size in the first dimension.
     */
    public final int xsize;

    /**
     * The size in the second dimension. 
     */
    public final int ysize;

    /**
     * The size in the third dimension. 
     */
    public final int zsize;

    @Final private float[] data;

    /**
     * Constructs an array.
     *
     * @param x     the size in the first dimension.
     * @param y     the size in the second dimension.
     * @param z     the size in the third dimension.
     */
    public FloatArray3D(int x, int y, int z) {
        xsize = x;
        ysize = y;
        zsize = z;
        initData(false);
    }

    /**
     * Constructs an array.
     *
     * @param x     the size in the first dimension.
     * @param y     the size in the second dimension.
     * @param z     the size in the third dimension.
     * @param emptyInJava       if true, the memory is not allocated at the Java side.
     */
    public FloatArray3D(int x, int y, int z, boolean emptyInJava) {
        xsize = x;
        ysize = y;
        zsize = z;
        initData(emptyInJava);
    }

    private void initData(boolean emptyInJava) {
        if (emptyInJava)
            data = new float[0];
        else
            data = new float[xsize * ysize * zsize];
    }

    public void allocData() {
        initData(false);
    }

    /**
     * Gets the value of the element at [i][j][k].
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public float get(int i, int j, int k) { return data[i * ysize * zsize + j * zsize + k]; }

    /**
     * Sets the value of the element at [i][j][k] to v.
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public void set(int i, int j, int k, float v) { data[i * ysize * zsize + j * zsize + k] = v; }

    /**
     * Sets the value of all the elements to v.
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public void setAll(float v) {
        for(int i=0; i < xsize; i++)
            for(int j=0; j < ysize; j++)
                for(int k=0; k < zsize; k++)
                    set(i, j, k, v);
    }

    /**
     * Returns a C pointer to the float array where the data is stored.
     */
    public float[] toCArray() { return data; }

    /**
     * Returns the offset to the specified element in the float array
     * in C. 
     */
    public int offset(int i, int j, int k) { return i * ysize * zsize + j * zsize + k; }
}
