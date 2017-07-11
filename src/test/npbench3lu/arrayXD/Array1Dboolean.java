package npbench3lu.arrayXD;

public class Array1Dboolean {
    final protected int beginX;
    final protected int endX;
    final protected int sizeX;
    final protected boolean data[];

    public Array1Dboolean(int sx) {
        sizeX = sx;
        beginX = 1;
        endX = sx;
        data = new boolean[sx];
    }

    public Array1Dboolean(int bx, int ex) {
        sizeX = ex - bx + 1;
        beginX = bx;
        endX = ex;
        data = new boolean[sizeX];
    }

    public void set(int x, boolean value) {
        data[x - beginX] = value;
    }

    public boolean get(int x) {
        return data[x - beginX];
    }
}
