package npbench3lu.arrayXD;

public class Array2Dint {
	final protected int beginX;
	final protected int beginY;
	final protected int endX;
	final protected int endY;
	final protected int sizeX;
	final protected int sizeY;
	final protected int data[];

    public Array2Dint(int sx, int sy) {
        sizeX = sx;
        sizeY = sy;
        beginX = 1;
        beginY = 1;
        endX = sx;
        endY = sy;
        data = new int[sx * sy];
    }

    public Array2Dint(int bx, int ex, int by, int ey) {
        sizeX = ex - bx + 1;
        sizeY = ey - by + 1;
        beginX = bx;
        beginY = by;
        endX = ex;
        endY = ey;
        data = new int[sizeX * sizeY];
    }

    public void set(int x, int y, int value) {
        data[(x - beginX) + (y - beginY) * sizeX] = value;
    }

    public int get(int x, int y) {
        return data[(x - beginX) + (y - beginY) * sizeX];
    }
}
