package npbench3lu.arrayXD;
import javassist.offload.Metaclass;
import javassist.offload.clang.ImmutableClass;
import javassist.offload.lib.FloatArray2D;

public final class Array2Dfloat implements Cloneable {
    final protected int beginX;
    final protected int beginY;
    final protected int endX;
    final protected int endY;
    final protected int sizeX;
    final protected int sizeY;
    final FloatArray2D data;

    public Array2Dfloat(int sx, int sy) {
        sizeX = sx;
        sizeY = sy;
        beginX = 1;
        beginY = 1;
        endX = sx;
        endY = sy;
        data = new FloatArray2D(sx, sy);
    }

    public Array2Dfloat(int bx, int ex, int by, int ey) {
        sizeX = ex - bx + 1;
        sizeY = ey - by + 1;
        beginX = bx;
        beginY = by;
        endX = ex;
        endY = ey;
        data = new FloatArray2D(sizeX, sizeY);
    }

    public void set(int x, int y, float value) {
        data.set(x - beginX, y - beginY, value);
    }

    public float get(int x, int y) {
        return data.get(x - beginX, y - beginY);
    }
    
}
