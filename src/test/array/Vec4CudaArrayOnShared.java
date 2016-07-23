package array;

import javassist.offload.Inline;
import javassist.offload.Metaclass;
import javassist.offload.clang.ImmutableClass;
import javassist.offload.lib.CUDA;

/**
 * An array on shared memory.
 */
public class Vec4CudaArrayOnShared extends Vec4Array {
    static final int BLOCK = Vec4CudaArray.BLOCK;
    final int size;
    final int tiles;

    /**
     * Construct shared memory.
     *
     * @param dmem      its size must be a multiple of BLOCK.
     */
    Vec4CudaArrayOnShared(VecDSL dsl, int n) {
        super(dsl, BLOCK, new CUDA.SharedFloatArray2D(BLOCK, 4));
        size = BLOCK;
        tiles = (n + BLOCK - 1) / BLOCK;
    }

    @Metaclass(type=ImmutableClass.class)
    public static interface Func {
        Vec3 apply(Vec3 acc);
    }

    /**
     * Applies {@code f} to {@code array} through shared memory.
     *
     * @param array      its size must be a multiple of {@code BLOCK}.
     */
    @Inline public Vec3 fold(Vec4Array array, Func f) {
        Vec3 result = new Vec3(0, 0, 0);
        for (int tile = 0; tile < tiles; tile++) {
            int idx0 = tile * CUDA.BlockDim.x() + CUDA.ThreadIdx.x();
            int idx = CUDA.ThreadIdx.x();
            Vec3 v = array.get(idx0);
            data.set(idx, 0, v.x);
            data.set(idx, 1, v.y);
            data.set(idx, 2, v.z);
            data.set(idx, 3, array.getW(idx0));
            CUDA.syncthreads();
            result = f.apply(result);
            CUDA.syncthreads();
        }
        return result;
    }
}
