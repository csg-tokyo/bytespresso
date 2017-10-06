package npbench3lu.arrayXD;

import javassist.offload.Inline;
import javassist.offload.lib.DoubleArray;
import javassist.offload.lib.MPI;
import npbench3lu.LUBase;

public class Array2Ddouble {
	final protected int beginX;
	final protected int beginY;
	final protected int endX;
	final protected int endY;
	final protected int sizeX;
	final protected int sizeY;
	final protected DoubleArray data;

    public Array2Ddouble(int sx, int sy) {
        sizeX = sx;
        sizeY = sy;
        beginX = 1;
        beginY = 1;
        endX = sx;
        endY = sy;
        // data = new double[sx * sy];
        data = new DoubleArray(sx * sy, !LUBase.inJava);
    }

    public Array2Ddouble(int bx, int ex, int by, int ey) {
        sizeX = ex - bx + 1;
        sizeY = ey - by + 1;
        beginX = bx;
        beginY = by;
        endX = ex;
        endY = ey;
        data = new DoubleArray(sizeX * sizeY, !LUBase.inJava);
    }

    @Inline public void set(int x, int y, double value) {
        data.set((x - beginX) + (y - beginY) * sizeX, value);
    }

    @Inline public double get(int x, int y) {
        return data.get((x - beginX) + (y - beginY) * sizeX);
    }

    public int getDataSize() {
        return sizeX * sizeY;
    }

    public void mpiIRecv(int x, int y, int length, int src, int tag, MPI.Request req) {
        int offset = (x - beginX) + (y - beginY) * sizeX;
        MPI.iRecvC(data.toCArray(), offset, length, src, tag, req);
    }

    public void mpiIRecv(int length, int src, int tag, MPI.Request req) {
        MPI.iRecvC(data.toCArray(), 0, length, src, tag, req);
    }

    public void mpiSend(int offset, int length, int dest, int tag) {
        MPI.sendC(data.toCArray(), offset, length, dest, tag);
    }

    public int getIndex(int x, int y) {
        int idx = (x - beginX) + (y - beginY) * sizeX;
        return idx;
    }

    // public static void main(String[] args) throws Exception {
    //
    // int i, j;
    //
    // Array2Ddouble a = new Array2Ddouble(100, 100);
    // for (i = 0; i < 100; i++) {
    // for (j = 0; j < 100; j++) {
    // a.set(i, j, i * j);
    // }
    // }
    //
    // for (i = 0; i < 100; i++) {
    // for (j = 0; j < 100; j++) {
    // System.out.printf("i = %d, j = %d, data = %f%n", i, j, a.get(i, j));
    // }
    // }
    //
    // }
}
