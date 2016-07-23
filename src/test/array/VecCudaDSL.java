package array;

import javassist.offload.lib.CArrayList;
import javassist.offload.lib.CUDA;
import javassist.offload.lib.CudaDriver;

/**
 * Vector language for CUDA.
 */
public class VecCudaDSL extends VecDSL {
    static {
        rsqrtfAvailable = true;
    }

    final CArrayList<Vec4CudaArray> arrays;
    final CArrayList<Vec4CudaArrayOnShared> shared;

    public VecCudaDSL(int size) {
        super(size);
        arrays = new CArrayList<Vec4CudaArray>();
        shared = new CArrayList<Vec4CudaArrayOnShared>();
    }

    public void run(Runnable r) throws Exception {
        CudaDriver drv = new CudaDriver();
        CUDA.CudaMemory cudaMem = new CUDA.CudaOptionalManagedMemory();
        for (int i = 0; i < arrays.size(); i++) {
            Vec4CudaArray a = arrays.get(i);
            cudaMem.onManaged(a);
            cudaMem.onManaged(a.onDevice);
        }

        for (int i = 0; i < shared.size(); i++) {
            cudaMem.onManaged(shared.get(i));
        }

        drv.setHeapMemory(cudaMem);
        drv.invoke(r);
    }

    public Vec4CudaArray array() {
        Vec4CudaArray a = new Vec4CudaArray(this, size);
        arrays.add(a);
        return a;
    }

    /**
     * Make an array on shared memory.
     */
    public Vec4CudaArrayOnShared sharedMemory() {
        Vec4CudaArrayOnShared a = new Vec4CudaArrayOnShared(this, size);
        shared.add(a);
        return a;
    }

    public void copyToDevice() {
        for (int i = 0; i < arrays.size(); i++)
            arrays.get(i).copyToDevice();
    }

    public void copyFromDevice() {
        for (int i = 0; i < arrays.size(); i++)
            arrays.get(i).copyFromDevice();        
    }

    /*
     * Avoids unnecessary synchronization performed
     * by map().  Calls to map() within code are
     * replaced to calls to map2().  
     */
    public void repeat(int n, Code code) {
        copyToDevice();
        for (int i = 0; i < n; i++)
            code.run();

        CUDA.deviceSynchronize();
        copyFromDevice();
    }
}
