// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.clang.HeapMemory;
import javassist.offload.javatoc.StdDriver;

/**
 * A compiler driver for CUDA programs.
 *
 * @see MPICudaDriver
 */
public class CudaDriver extends StdDriver {

    public CudaDriver() {
        setHeapMemory(new CUDA.CudaManagedMemory());
    }

    protected String compileCommandHead() {
        return "nvcc -w -arch=sm_35 -Xcompiler \"-O3\" ";
    }

    /**
     * Returns the name of the source file.
     */
    public String sourceFile() { return "bytespresso.cu"; }

    @Override public String preamble() {
        return super.preamble() + ((CUDA.CudaMemory)heapMemory()).preamble();
    }

    public String prologue() {
        return super.prologue() + cudaPrologue();
    }

    /**
     * Returns CUDA-specific prologue.
     */
    public static String cudaPrologue() {
        return "cudaFree(0); /* initialize CUDA */\n";
    }

    /**
     * Changes the {@link HeapMemory} object used during the transformation by this driver.
     * It has to be an instance of {@link CUDA.CudaMemory}.
     * The default {@link HeapMemory} object is {@link CUDA.CudaManagedMemory}.
     *
     * @param h     a new {@code HeapMemory} object.
     * @see CUDA.CudaOptionalManagedMemory
     */
    public void setHeapMemory(HeapMemory h) {
        if (h instanceof CUDA.CudaMemory)
            super.setHeapMemory(h);
        else
            throw new RuntimeException("not a subclass of CUDA.CudaMemory");
    }
}
