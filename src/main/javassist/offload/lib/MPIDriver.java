// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.javatoc.DriverException;
import javassist.offload.javatoc.StandaloneDriver;

/**
 * MPI compiler driver.
 */
public class MPIDriver extends StandaloneDriver {
    private int numberOfNodes;
    private boolean offJVMset;  // true if offJVM has been set.
    private boolean offJVM;

    /**
     * Constructs a driver.
     * The driver does not compile or execute the code.
     * {@link #sourceOnly()} is implicitly called.
     * 
     */
    public MPIDriver() { this(0); sourceOnly(); }

    /**
     * Constructs a driver.
     *
     * @param num       the number of MPI nodes.
     */
    public MPIDriver(int num) {
        super();
        numberOfNodes = num;
        offJVMset = false;
        offJVM = true;
    }

    /**
     * If this method is called, MPI will be emulated by Java threads.
     *
     * @return this object.
     */
    public MPIDriver inJava() {
        if (offJVM && offJVMset)
            throw new RuntimeException("inJava() must be called earlier only once.");
        else {
            offJVMset = true;
            offJVM = false;
            return this;
        }
    }

    /**
     * Returns true if MPI will be emulated by Java threads.
     * Once this method is called, it cannot be changed whether
     * MPI is emulated or not.
     */
    public boolean runsInJava() {
        offJVMset = true;
        return !offJVM;
    }

    /**
     * Instructs the driver to compile and execute the code
     * when {@code invoke()} is called.
     *
     * @param num       the number of MPI nodes.
     */
    public void compileAndRun(int num) {
        compileIt = true;
        runIt = true;
        numberOfNodes = num;
    }

    @Override public String compileCommandHead() {
        return "mpicc -O3";
    }

    @Override public String execCommand() {
        if (execCmd == null) {
            String opt = numberOfNodes < 1 ? "" : ("-n " + numberOfNodes);
            return "mpiexec " + opt + " " + binaryName();
        }
        else
            return execCmd;
    }

    @Override public String preamble() {
        return super.preamble() + "#include <mpi.h>\n";
    }

    @Override public String prologue() { return " MPI_Init(&argc, &argv);\n"; }

    @Override public String epilogue() { return " MPI_Finalize();\n"; }

    /**
     * Invokes the {@code run} on the {@code runner} object.
     * If MPI is emulated by Java threads, the {@code runner} object
     * is replicated for every MPI node.  Note that the {@code static} fields
     * are not replicated but they are shared among the MPI nodes.  This
     * semantics is different from the original semantics of MPI.
     *
     * @param runner        this object must be cloneable if MPI is emulated by Java threads.
     */
    public void invoke(Runnable runner) throws DriverException {
        if (offJVM)
            super.invoke(runner);
        else {
            MPIRuntime.start(numberOfNodes, runner);
        }
    }
}
