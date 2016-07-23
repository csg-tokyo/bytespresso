// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.clang.HeapMemory;

/**
 * MPI + CUDA compiler driver.
 *
 * @see CudaDriver
 */
public class MPICudaDriver extends MPIDriver {
    /**
     * Constructs a driver.
     *
     * @param num       the number of MPI nodes.
     */
    public MPICudaDriver(int num) {
        super(num);
        setHeapMemory(new CUDA.CudaManagedMemory());
    }

    @Override public String compileCommandHead() {
        return "nvcc -w -arch=sm_35 -Xcompiler \"-O3 " + "`mpicc -showme:compile`" + "`mpicc -showme:link`\"";
    }

    /**
     * Returns the name of the source file.
     */
    public String sourceFile() { return "bytespresso.cu"; }

    @Override public String preamble() {
        return super.preamble() + ((CUDA.CudaMemory)heapMemory()).preamble();
    }

    @Override public String prologue() {
        return super.prologue() + CudaDriver.cudaPrologue();
    }

    public void setHeapMemory(HeapMemory h) {
        if (h instanceof CUDA.CudaMemory)
            super.setHeapMemory(h);
        else
            throw new RuntimeException("not a subclass of CUDA.CudaMemory");
    }
}
