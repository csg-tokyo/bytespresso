package npbench3lu.arrayXD;

public class Array1Ddouble {
    protected int beginX;
    protected int endX;
    protected int sizeX;
    protected double data[];

    public Array1Ddouble(int sx) {
        sizeX = sx;
        beginX = 1;
        endX = sx;
        data = new double[sx];
    }

    public Array1Ddouble(int bx, int ex) {
        sizeX = ex - bx + 1;
        beginX = bx;
        endX = ex;
        data = new double[sizeX];
    }

    public void set(int x, double value) {
        data[x - beginX] = value;
    }

    public double get(int x) {
        return data[x - beginX];
    }

    public double[] getData() {
        return data;
    }

    public void setData(double[] values) {
        // data = (double[])values.clone();
        data = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            data[i] = values[i];
        }
    }
}
