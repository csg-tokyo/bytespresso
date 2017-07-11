package npbench3lu.arrayXD;

public class Array3Dint {
	final protected int beginX;
	final protected int beginY;
	final protected int beginZ;
	final protected int endX;
	final protected int endY;
	final protected int endZ;
	final protected int sizeX;
	final protected int sizeY;
	final protected int sizeZ;
	final protected int data[];

    public Array3Dint(int sx, int sy, int sz) {
        sizeX = sx;
        sizeY = sy;
        sizeZ = sz;
        beginX = 1;
        beginY = 1;
        beginZ = 1;
        endX = sx;
        endY = sy;
        endZ = sz;
        data = new int[sx * sy * sz];
    }

    public Array3Dint(int bx, int ex, int by, int ey, int bz, int ez) {
        sizeX = ex - bx + 1;
        sizeY = ey - by + 1;
        sizeZ = ez - bz + 1;
        beginX = bx;
        beginY = by;
        beginZ = bz;
        endX = ex;
        endY = ey;
        endZ = ez;
        data = new int[sizeX * sizeY * sizeZ];
    }

    public void set(int x, int y, int z, int value) {
        data[(x - beginX) + (y - beginY) * sizeX + (z - beginZ) * sizeX * sizeY] = value;
    }

    public int get(int x, int y, int z) {
        return data[(x - beginX) + (y - beginY) * sizeX + (z - beginZ) * sizeX * sizeY];
    }
}
