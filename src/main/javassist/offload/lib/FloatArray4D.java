// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.Metaclass;
import javassist.offload.clang.NativeMultiArrayClass;

/**
 * A four-dimensional array of float type.
 * It is translated into a native four-dimensional array in C
 * if it is constructed at the Java side.
 * Note that the array elements are not copied when this array object
 * is passed to the C side.
 */
@Metaclass(type=NativeMultiArrayClass.class, arg="float", args = { "xsize", "ysize", "zsize", "wsize" })
public class FloatArray4D {
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

    /**
     * The size in the fourth dimension. 
     */
    public final int wsize;

    private float[] data;

    /**
     * Constructs an array.
     *
     * @param x     the size in the first dimension.
     * @param y     the size in the second dimension.
     * @param z     the size in the third dimension.
     * @param w     the size in the forth dimension.
     */
    public FloatArray4D(int x, int y, int z, int w) {
        xsize = x;
        ysize = y;
        zsize = z;
        wsize = w;
        initData(false);
    }

    /**
     * Constructs an array.
     *
     * @param x     the size in the first dimension.
     * @param y     the size in the second dimension.
     * @param z     the size in the third dimension.
     * @param w     the size in the forth dimension.
     * @param emptyInJava       if true, the memory is not allocated at the Java side.
     */
    public FloatArray4D(int x, int y, int z, int w, boolean emptyInJava) {
        xsize = x;
        ysize = y;
        zsize = z;
        wsize = w;
        initData(emptyInJava);
    }

    private void initData(boolean emptyInJava) {
        if (emptyInJava)
            data = new float[0];
        else
            data = new float[xsize * ysize * zsize * wsize];
    }

    /**
     * Gets the value of the element at [i][j][k][l].
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public float get(int i, int j, int k, int l) {
        return data[i * ysize * zsize * wsize + j * zsize * wsize + k * wsize + l];
    }

    /**
     * Sets the value of the element at [i][j][k][l] to v.
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public void set(int i, int j, int k, int l, float v) {
        data[i * ysize * zsize * wsize + j * zsize * wsize + k * wsize + l] = v;
    }

    /**
     * Sets the value of all the elements to v.
     * It is available only in C
     * if the memory is not allocated at the Java side.
     */
    public void setAll(float v) {
        for(int i=0; i < xsize; i++)
            for(int j=0; j < ysize; j++)
                for(int k=0; k < zsize; k++)
                    for(int l=0; l < wsize; l++)
                        set(i, j, k, l, v);
    }

    /**
     * Returns a C pointer to the float array where the data is stored.
     */
    public float[] toCArray() { return data; }

    /**
     * Returns the offset to the specified element in the float array
     * in C. 
     */
    public int offset(int i, int j, int k, int l) {
        return i * ysize * zsize * wsize + j * zsize * wsize + k * wsize + l;
    }
}
