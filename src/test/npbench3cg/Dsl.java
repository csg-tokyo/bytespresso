package npbench3cg;

import javassist.offload.javatoc.DriverException;
import javassist.offload.lib.Util;
import javassist.offload.lib.MPI;
import javassist.offload.lib.MPIDriver;

public class Dsl implements Cloneable {
    /**
     * True when the benchmark runs on the JVM with MPI
     * emulation.
     */
    public static boolean java = false;

    public final int size;
    public int numProcRows;
    private int rank;
    final boolean useMPI;

    int numProcs;
    int firstrow, lastrow, firstcol, lastcol;
    int log2npcols, exchProc, exchRecvLength;
    int sendStart, sendLen;
    int[] reduceExchProc, reduceSendStarts, reduceSendLengths;
    int[] reduceRecvStarts, reduceRecvLengths;
    int matrixWorkSize;

    /**
     * Constructs a DSL.
     * <p>When the benchmark runs on the JVM, ({@code nprows} * 2) MPI processes,
     * which are Java threads, are created by MPI emulator.  If {@code nprows} is
     * one, the benchmark is run by a single Java thread.
     * </p> 
     * 
     * @param s                 the size of vectors.
     * @param nprows            <code>num_proc_rows</code>.
     *                          The number of processes allocated for the same column.
     * @see Dsl#java
     */
    public Dsl(int s, int nprows) {
        size = s;
        if (nprows == 0) {
            numProcRows = numProcs = 1;
            useMPI = false;
        }
        else {
            numProcRows = nprows;
            numProcs = nprows * 2;
            useMPI = true;
        }

        rank = 0;
        firstrow = firstcol = 1;
        lastrow = lastcol = s;
    }

    public Vector vector() {
        return new Vector(size / numProcRows + 3, this);
    }

    public Matrix.Sparse sparseMatrix(int numElements) {
        if (useMPI)
            return new Matrix.SparseMPI(numElements, size + 2,
                                        size / numProcRows + 3, this);
        else
            return new Matrix.Sparse(numElements, size + 2, this);
    }

    public void compile(Runnable r) throws DriverException {
        if (numProcs == 1) {
            if (java)
                r.run();
            else {
                new javassist.offload.javatoc.StdDriver().invoke(() -> {
                    r.run();
                });
            }
        }
        else {
            MPIDriver driver;
            if (java)
                driver = new MPIDriver(numProcs).inJava();
            else {
                /* numProcs will be reset to the number passed
                 * by -n to mpiexec.
                 */
                driver = new MPIDriver();
            }

            driver.invoke(new Runner(this, r));
        }
    }

    static class Runner implements Runnable, Cloneable {
        final Dsl dsl;
        final Runnable run;
        Runner(Dsl d, Runnable r) { dsl = d; run = r; }
        public void run() {
            dsl.setupMPI();
            run.run();
        }
    }

    /**
     * Returns the number of processes.
     */
    public int processes() { return numProcs; }

    /**
     * Returns the rank of this process.
     */
    public int rank() { return rank; }

    private void setupMPI() {
        rank = MPI.commRank();
        numProcs = MPI.commSize();
        setupSubmatrixInfo(rank);
    }

    private void setupSubmatrixInfo(int me) {
        int log2nprocs = log(numProcs);
        if (log2nprocs < 0) {
            Util.printer.p("num_procs is not a power of two: ").p(numProcs).ln();
            Util.exit(1);
        }

        if (log(numProcRows) < 0) { 
            Util.printer.p("num_proc_rows is not a power of two: ").p(numProcRows).ln();
            Util.exit(1);
        }

        if (numProcs % numProcRows != 0) {
            Util.printer.p("num_procs is not divisible by num_proc_rows").ln();
            Util.exit(1);
        }

        int numProcCols = numProcs / numProcRows;
        if (log(numProcCols) < 0) {
            Util.printer.p("num_proc_cols is not a power of two: ").p(numProcCols).ln();
            Util.exit(1);
        }

        int npcols = numProcCols;
        int nprows = numProcRows;

        int procRow = me / npcols;
        int procCol = me - procRow * npcols;

        int colSize, rowSize;
        if (size % npcols == 0) {
            // If size evenly divisible by npcols, then it is evenly divisible 
            // by nprows 
            colSize = size / npcols;
            firstcol = procCol * colSize + 1;
            lastcol = firstcol - 1 + colSize;
            rowSize = size / nprows;
            firstrow = procRow * rowSize + 1;
            lastrow = firstrow - 1 + rowSize;
        }
        else {
            // If naa not evenly divisible by npcols, then first subdivide for nprows
            // and then, if npcols not equal to nprows (i.e., not a sq number of procs), 
            // get col subdivisions by dividing by 2 each row subdivision.
            if (procRow < size - size / nprows * nprows) {
                rowSize = size / nprows + 1;
                firstrow = procRow * rowSize + 1;
                lastrow  = firstrow - 1 + rowSize;
            }
            else {
                rowSize = size / nprows;
                firstrow = (size - size / nprows * nprows) * (rowSize + 1)
                           + (procRow - (size - size / nprows * nprows)) * rowSize + 1;
                lastrow  = firstrow - 1 + rowSize;
            }

            if (npcols == nprows)
                if (procCol < size - size / npcols * npcols) {
                    colSize = size / npcols + 1;
                    firstcol = procCol * colSize + 1;
                    lastcol  = firstcol - 1 + colSize;
                }
                else {
                    colSize = size / npcols;
                    firstcol = (size - size / npcols * npcols) * (colSize + 1)
                               + (procCol - (size - size / npcols * npcols)) * colSize + 1;
                    lastcol  = firstcol - 1 + colSize;
                }
            else {
                if ((procCol / 2) < size - size / (npcols / 2) * (npcols / 2)) {
                    colSize = size / (npcols / 2) + 1;
                    firstcol = (procCol / 2) * colSize + 1;
                    lastcol  = firstcol - 1 + colSize;
                }
                else {
                    colSize = size / (npcols / 2);
                    firstcol = (size - size / (npcols / 2) * (npcols / 2)) * (colSize + 1)
                               + ((procCol / 2) - (size - size / (npcols / 2) * (npcols / 2))) * colSize + 1;
                    lastcol  = firstcol - 1 + colSize;
                }

                if (me % 2 == 0)
                    lastcol  = firstcol - 1 + (colSize - 1) / 2 + 1;
                else {
                    firstcol = firstcol + (colSize - 1) / 2 + 1;
                    lastcol  = firstcol - 1 + colSize / 2;
                }
            }
        }

        if (npcols == nprows) {
            sendStart = 1;
            sendLen = lastrow - firstrow + 1;
        }
        else if (me % 2 == 0) {
            sendStart = 1;
            sendLen = (1 + lastrow - firstrow + 1) / 2;
        }
        else {
            sendStart = (1 + lastrow - firstrow + 1) / 2 + 1;
            sendLen = (lastrow - firstrow + 1) / 2;
        }

        // Transpose exchange processor
        if (numProcCols == numProcRows)
            exchProc = (me % numProcRows) * numProcRows + me / numProcRows;
        else
            exchProc = 2 * (((me / 2) % numProcRows) * numProcRows + me / 2 / numProcRows)
                       + me % 2;

        log2npcols = log(npcols);
        reduceExchProc = new int[log2npcols + 1];
        reduceSendStarts = new int[log2npcols + 1];
        reduceSendLengths = new int[log2npcols + 1];
        reduceRecvStarts = new int[log2npcols + 1];
        reduceRecvLengths = new int[log2npcols + 1];

        // Set up the reduce phase schedules...
        int divFactor = npcols;
        for (int i = 1; i <= log2npcols; i++) {
            int j = (procCol + divFactor / 2) % divFactor
                    + procCol / divFactor * divFactor;
            reduceExchProc[i] = procRow * npcols + j;
            divFactor = divFactor / 2;
        }

        for (int i = log2npcols; i >= 1; i--) {
            if (nprows == npcols) {
                reduceSendStarts[i] = sendStart; 
                reduceSendLengths[i] =sendLen;
                reduceRecvLengths[i] = lastrow - firstrow + 1;
            }
            else {
                reduceRecvLengths[i] = sendLen;
                if (i == log2npcols) {
                    reduceSendLengths[i] = lastrow - firstrow + 1 - sendLen;
                    if (me % 2 == 0)
                        reduceSendStarts[i] = sendStart + sendLen;
                    else
                        reduceSendStarts[i] = 1;
                }
                else {
                    reduceSendLengths[i] = sendLen;
                    reduceSendStarts[i] = sendStart;
                }
            }

            reduceRecvStarts[i] = sendStart;
        }

        exchRecvLength = lastcol - firstcol + 1;
        matrixWorkSize = max(lastrow - firstrow + 1, lastcol - firstcol + 1); 
    }

    /**
     * Returns a larger value.
     */
    public static int max(int a, int b) {
        return a > b ? a : b;
    }

    /**
     * Returns the log base 2 of the input.
     * -1 is returned if the input is not a power of 2. 
     */
    public static int log(int i) {
        int r = 0;
        while (i > 1) {
            if (i % 2 != 0)
                return -1;

            i = i / 2;
            r++;
        }

        return r;
    }
}
