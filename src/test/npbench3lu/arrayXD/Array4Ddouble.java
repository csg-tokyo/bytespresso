package npbench3lu.arrayXD;

import javassist.offload.Inline;
import javassist.offload.lib.DoubleArray;
import npbench3lu.LUBase;

public class Array4Ddouble {
	final protected int beginX;
	final protected int beginY;
	final protected int beginZ;
	final protected int beginW;
	final protected int endX;
	final protected int endY;
	final protected int endZ;
	final protected int endW;
	final protected int sizeX;
	final protected int sizeY;
	final protected int sizeZ;
	final protected int sizeW;
	final protected DoubleArray data;

    public Array4Ddouble(int sx, int sy, int sz, int sw) {
        sizeX = sx;
        sizeY = sy;
        sizeZ = sz;
        sizeW = sw;
        beginX = 1;
        beginY = 1;
        beginZ = 1;
        beginW = 1;
        endX = sx;
        endY = sy;
        endZ = sz;
        endW = sw;
        // data = new double[sx * sy * sz * sw];
        data = new DoubleArray(sx * sy * sz * sw, !LUBase.inJava);
    }

    public Array4Ddouble(int bx, int ex, int by, int ey, int bz, int ez, int bw, int ew) {
        sizeX = ex - bx + 1;
        sizeY = ey - by + 1;
        sizeZ = ez - bz + 1;
        sizeW = ew - bw + 1;
        beginX = bx;
        beginY = by;
        beginZ = bz;
        beginW = bw;
        endX = ex;
        endY = ey;
        endZ = ez;
        endW = ew;
        data = new DoubleArray(sizeX * sizeY * sizeZ * sizeW, !LUBase.inJava);
    }

    @Inline public void set(int x, int y, int z, int w, double value) {
        data.set((x - beginX) + (y - beginY) * sizeX + (z - beginZ) * sizeX * sizeY
                + (w - beginW) * sizeX * sizeY * sizeZ,
                value);
    }

    @Inline public double get(int x, int y, int z, int w) {
        return data.get((x - beginX) + (y - beginY) * sizeX + (z - beginZ) * sizeX * sizeY
                        + (w - beginW) * sizeX * sizeY * sizeZ);
    }

    public void copy(int x, int y, int z, int w, double[] values, int size) {
        int copyto;
        copyto = (x - beginX) + (y - beginY) * sizeX + (z - beginZ) * sizeX * sizeY
                + (w - beginW) * sizeX * sizeY * sizeZ;
        for (int i = 0; i < size; i++) {
            data.set(copyto + i, values[i]);
        }
    }

    public int getDataSize() {
        return sizeX * sizeY * sizeZ * sizeW;
    }

}
