package npbench3lu.arrayXD;

public class Array2Ddouble {
	final protected int beginX;
	final protected int beginY;
	final protected int endX;
	final protected int endY;
	final protected int sizeX;
	final protected int sizeY;
	final protected double data[];

    public Array2Ddouble(int sx, int sy) {
        sizeX = sx;
        sizeY = sy;
        beginX = 1;
        beginY = 1;
        endX = sx;
        endY = sy;
        data = new double[sx * sy];
    }

    public Array2Ddouble(int bx, int ex, int by, int ey) {
        sizeX = ex - bx + 1;
        sizeY = ey - by + 1;
        beginX = bx;
        beginY = by;
        endX = ex;
        endY = ey;
        data = new double[sizeX * sizeY];
    }

    public void set(int x, int y, double value) {
        data[(x - beginX) + (y - beginY) * sizeX] = value;
    }

    public double get(int x, int y) {
        return data[(x - beginX) + (y - beginY) * sizeX];
    }

    public double[] getData() {
        return data;
    }

    public int getDataSize() {
        return sizeX * sizeY;
    }

    public void setData(double[] values) {
        // data = (double[])values.clone();
        //data = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = values[i];
        }
    }

    public void setValues(int x, int y, int length, double[] values) {
        int idx = (x - beginX) + (y - beginY) * sizeX;
        int j = 0;
        for (int i = idx; i < idx + length; i++) {
            data[i] = values[j++];
        }
    }

    public double[] getValues(int x, int y, int length) {
        double[] tmp = new double[length];
        int idx = (x - beginX) + (y - beginY) * sizeX;
        int j = 0;
        for (int i = idx; i < length; i++) {
            tmp[j++] = data[i];
        }
        return tmp;
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
