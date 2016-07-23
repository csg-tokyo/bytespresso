package array;

import javassist.offload.Inline;
import javassist.offload.Metaclass;
import javassist.offload.lib.CUDA;
import javassist.offload.lib.CUDA.DeviceFloatArray2D;

/**
 * An array of {@link Vec4} on device memory.
 * This object holds two references; one refers to an array
 * on device memroy (GPU memory) and the other refers to an
 * array on host memory (CPU memory).
 *
 * <p>The values of their elements are not automatically copied
 * between the two arrays.
 */
public class Vec4CudaArray extends Vec4Array {
    static final int BLOCK = 1024 / 4;
    static final CUDA.SharedFloatArray sharedMem = new CUDA.SharedFloatArray(BLOCK * 4);

    final int numBlocks = (size + BLOCK - 1) / BLOCK;
    final DeviceFloatArray2D onDevice;

    /**
     * Constructs an array.
     *
     * @param n     the length of the array.
     */
    Vec4CudaArray(VecCudaDSL dsl, int n) {
        super(dsl, n);
        onDevice = new DeviceFloatArray2D(n, 4);
    }

    public Vec3 get(int i) {
        if (CUDA.withinDevice)
            return new Vec3(onDevice.get(i, 0), onDevice.get(i, 1), onDevice.get(i, 2));
        else
            return super.get(i);
    }

    public float getW(int i) {
        if (CUDA.withinDevice)
            return onDevice.get(i, 3); 
        else
            return super.getW(i);
    }

    public void set(int i, Vec3 v) {
        if (CUDA.withinDevice) {
            onDevice.set(i, 0, v.x);
            onDevice.set(i, 1, v.y);
            onDevice.set(i, 2, v.z);
        }
        else
            super.set(i, v);
    }

    public void set(int i, Vec4 v) {
        if (CUDA.withinDevice) {
            onDevice.set(i, 0, v.x);
            onDevice.set(i, 1, v.y);
            onDevice.set(i, 2, v.z);
            onDevice.set(i, 3, v.w);
        }
        else
            super.set(i, v);
    }

    public void set(int i, float x, float y, float z, float w) {
        if (CUDA.withinDevice) {
            onDevice.set(i, 0, x);
            onDevice.set(i, 1, y);
            onDevice.set(i, 2, z);
            onDevice.set(i, 3, w);
        }
        else
            super.set(i, x, y, z, w);
    }

    public Vec3 sum(Vec3Func f) {
        if (!CUDA.withinDevice)
            return super.sum(f);
        else {
            Vec3 v = new Vec3(0, 0, 0);
            for (int tile = 0; tile < numBlocks; tile++) {
                int idx0 = tile * CUDA.BlockDim.x() + CUDA.ThreadIdx.x();
                int idx = CUDA.ThreadIdx.x() * 4;
                sharedMem.set(idx, onDevice.get(idx0, 0));
                sharedMem.set(idx + 1, onDevice.get(idx0, 1));
                sharedMem.set(idx + 2, onDevice.get(idx0, 2));
                sharedMem.set(idx + 3, onDevice.get(idx0, 3));
                CUDA.syncthreads();

                // javassist.offload.lib.Util.unroll(BLOCK);
                for (int i = 0; i < CUDA.BlockDim.x(); i++) {
                    Vec3 v2 = f.apply(new Vec3(sharedMem.get(i * 4), sharedMem.get(i * 4 + 1),
                                               sharedMem.get(i * 4 + 2)),
                                      sharedMem.get(i * 4 + 3));
                    v = v.add(v2);
                }

                CUDA.syncthreads();
            }

            return v;
        }
    }

    public Vec3 sum(Func f) {
        if (!CUDA.withinDevice)
            return super.sum(f);
        else {
            Vec3 v = new Vec3(0, 0, 0);
            for (int tile = 0; tile < numBlocks; tile++) {
                int idx0 = tile * CUDA.BlockDim.x() + CUDA.ThreadIdx.x();
                int idx = CUDA.ThreadIdx.x() * 4;
                sharedMem.set(idx, onDevice.get(idx0, 0));
                sharedMem.set(idx + 1, onDevice.get(idx0, 1));
                sharedMem.set(idx + 2, onDevice.get(idx0, 2));
                sharedMem.set(idx + 3, onDevice.get(idx0, 3));
                CUDA.syncthreads();

                // javassist.offload.lib.Util.unroll(BLOCK);
                for (int i = 0; i < CUDA.BlockDim.x(); i++) {
                    Vec3 v2 = f.apply(this, i * 4,
                                      new Vec3(sharedMem.get(i * 4), sharedMem.get(i * 4 + 1),
                                               sharedMem.get(i * 4 + 2)),
                                      sharedMem.get(i * 4 + 3));
                    v = v.add(v2);
                }

                CUDA.syncthreads();
            }

            return v;
        }
    }

    @Inline public void map(MapFunc f, Vec4Array src) {
        if (CUDA.withinDevice)
            super.map(f, src);
        else {
            dsl.copyToDevice();
            gpuKernel(numBlocks, BLOCK, f, src, this);
            CUDA.deviceSynchronize();
            dsl.copyFromDevice();
        }
    }

    /**
     * Does not perform memory copying between device and host memory.
     */
    @Inline public void map2(MapFunc f, Vec4Array src) {
        if (CUDA.withinDevice)
            super.map(f,  src);
        else
            gpuKernel(numBlocks, BLOCK, f, src, this);
    }

    @Metaclass(type=CUDA.Global.class)
    static void gpuKernel(int blk, int th, MapFunc f, Vec4Array src, Vec4Array dest) {
        int i = CUDA.BlockIdx.x() * CUDA.BlockDim.x() + CUDA.ThreadIdx.x();
        Vec3 r = f.apply(src, i, src.get(i));
        dest.set(i, r);
    }

    @Inline public void map(Func f, Vec4Array src) {
        if (CUDA.withinDevice)
            super.map(f, src);
        else {
            dsl.copyToDevice();
            gpuKernel(numBlocks, BLOCK, f, src, this);
            CUDA.deviceSynchronize();
            dsl.copyFromDevice();
        }
    }

    /**
     * Does not perform memory copying between device and host memory.
     */
    @Inline public void map2(Func f, Vec4Array src) {
        if (CUDA.withinDevice)
            super.map(f,  src);
        else
            gpuKernel(numBlocks, BLOCK, f, src, this);
    }

    @Metaclass(type=CUDA.Global.class)
    static void gpuKernel(int blk, int th, Func f, Vec4Array src, Vec4Array dest) {
        int i = CUDA.BlockIdx.x() * CUDA.BlockDim.x() + CUDA.ThreadIdx.x();
        Vec3 r = f.apply(src, i, src.get(i), src.getW(i));
        dest.set(i, r);
    }

    /**
     * Copies the array elements from the device memory
     * to the host memory.
     */
    public void copyFromDevice() {
        onDevice.copyTo(data);
    }

    /**
     * Copies the array elements from the host memory
     * to the device memory.
     */
    public void copyToDevice() {
        onDevice.copyFrom(data);
    }
}
