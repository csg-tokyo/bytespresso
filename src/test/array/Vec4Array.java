package array;

import javassist.offload.Inline;
import javassist.offload.Metaclass;
import javassist.offload.clang.ImmutableClass;
import javassist.offload.clang.LambdaClass;
import javassist.offload.lib.FloatArray2D;
import javassist.offload.lib.OpenMP;

@Metaclass(type=ImmutableClass.class)
final class Vec3 {
    /**
     * True if the target compiler is Intel C.
     */
    public static final boolean isIcc = false;

    public final float x, y, z;

    /* value should be false for Intel C compiler (icc), but
     * it should be true for Fujitsu C compiler (fcc).
     */
    @Inline(value=!isIcc)
    Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    Vec3 add(Vec3 v) {
        return new Vec3(x + v.x, y + v.y, z + v.z);
    }

    Vec3 sub(Vec3 v) {
        return new Vec3(x - v.x, y - v.y, z - v.z);
    }

    float mult(Vec3 v) {
        return x * v.x + y * v.y +  z * v.z;
    }

    Vec3 scale(float s) {
        return new Vec3(x * s, y * s, z * s);
    }
}

@Metaclass(type=ImmutableClass.class)
class Vec4 {
    public final float x, y, z, w;

    public Vec4(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }
}

@Metaclass(type=LambdaClass.class)
interface Vec4Func {
    Vec4 apply(int i);
}

@Metaclass(type=LambdaClass.class)
interface MapFunc {
    Vec3 apply(Vec4Array array, int i, Vec3 v);
}

@Metaclass(type=LambdaClass.class)
interface Vec3Func {
    Vec3 apply(Vec3 v, float w);
}

@Metaclass(type=LambdaClass.class)
interface Func {
    Vec3 apply(Vec4Array array, int i, Vec3 v, float w);
}

public class Vec4Array {
    final VecDSL dsl;
    final FloatArray2D data;
    final int size;

    public Vec4Array(VecDSL dsl, int n) {
        this(dsl, n, new FloatArray2D(n, 4));
    }

    protected Vec4Array(VecDSL dsl, int n, FloatArray2D a) {
        this.dsl = dsl;
        this.data = a;
        this.size = n;
    }

    public Vec3 get(int i) {
        return new Vec3(data.get(i, 0), data.get(i, 1), data.get(i, 2));
    }

    public float getW(int i) { return data.get(i, 3); }

    public void set(int i, Vec3 v) {
        data.set(i, 0, v.x);
        data.set(i, 1, v.y);
        data.set(i, 2, v.z);
    }

    public void set(int i, Vec4 v) {
        data.set(i, 0, v.x);
        data.set(i, 1, v.y);
        data.set(i, 2, v.z);
        data.set(i, 3, v.w);
    }

    public void set(int i, float x, float y, float z, float w) {
        data.set(i, 0, x);
        data.set(i, 1, y);
        data.set(i, 2, z);
        data.set(i, 3, w);
    }

    @Inline public void tabulate(Vec4Func f) {
        if (dsl.withOpenMP) OpenMP.parallelFor();
        for (int i = 0; i < size; i++)
            this.set(i, f.apply(i));
    }

    /**
     * Applies a function to all the elements of {@code src}.
     * The result is stored in the corresponding element of this object.
     * If OpenMP is  used, the function application is executed in
     * parallel.  The function must not cause a race.
     */
    @Inline public void map(MapFunc f, Vec4Array src) {
        if (dsl.withOpenMP) OpenMP.parallelFor();
        for (int i = 0; i < size; i++) {
            Vec3 v = f.apply(src, i, src.get(i));
            this.set(i, v);
        }
    }

    /**
     * Invoked in {@link VecDSL.Code#run()} instead of {@code map}.
     * The implementation in this class is equivalent to {@code map}.
     */
    @Inline public void map2(MapFunc f, Vec4Array src) {
        if (dsl.withOpenMP) OpenMP.parallelFor();
        for (int i = 0; i < size; i++) {
            Vec3 v = f.apply(src, i, src.get(i));
            this.set(i, v);
        }
    }

    /**
     * Applies a function to all the elements of {@code src}.
     * The result is stored in the corresponding element of this object.
     * If OpenMP is  used, the function application is executed in
     * parallel.  The function must not cause a race.
     */
    @Inline public void map(Func f, Vec4Array src) {
        if (dsl.withOpenMP) OpenMP.parallelFor();
        for (int i = 0; i < size; i++) {
            Vec3 vi = src.get(i);
            Vec3 v = f.apply(src, i, vi, src.getW(i));
            this.set(i, v);
        }
    }

    /**
     * Invoked in {@link VecDSL.Code#run()} instead of {@code map}.
     * The implementation in this class is equivalent to {@code map}.
     */
    @Inline public void map2(Func f, Vec4Array src) {
        if (dsl.withOpenMP) OpenMP.parallelFor();
        for (int i = 0; i < size; i++) {
            Vec3 vi = src.get(i);
            Vec3 v = f.apply(src, i, vi, src.getW(i));
            this.set(i, v);
        }
    }

    @Inline public Vec3 sum(Vec3Func f) {
        Vec3 v = new Vec3(0, 0, 0);
        for (int i = 0; i < size; i++) {
            Vec3 v2 = f.apply(get(i), getW(i));
            v = v.add(v2);
        }

        return v;
    }

    @Inline public Vec3 sum(Func f) {
        Vec3 v = new Vec3(0, 0, 0);
        for (int i = 0; i < size; i++) {
            Vec3 vi = get(i);
            Vec3 v2 = f.apply(this, i, vi, getW(i));
            v = v.add(v2);
        }

        return v;
    }
}
