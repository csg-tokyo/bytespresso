package npbench3cg;

import javassist.offload.lib.DoubleArray;
import javassist.offload.lib.IntArray;
import javassist.offload.lib.MPI;

public abstract class Matrix implements Cloneable {
    public abstract void mult(Vector v, Vector result);

    public static class Sparse extends Matrix {
        public final Dsl dsl;
        public final DoubleArray values;
        public final IntArray colidx;
        public final IntArray rowstr;

        private final DoubleArray work;
        private final MPI.Request request;

        /**
         * 
         * @param size          the number of non-zero elements.
         * @param numOfRows     the number of rows.
         */
        Sparse(int size, int numOfRows, int workSize, Dsl d) {
            dsl = d;
            values = new DoubleArray(size, !Dsl.java);
            colidx = new IntArray(size, !Dsl.java);
            rowstr = new IntArray(numOfRows, !Dsl.java);
            work = new DoubleArray(workSize, !Dsl.java);
            request = new MPI.Request();
        }

        /**
         * Shifts the column index values.
         */
        public void shift(int offset) {
            for (int j = 1; j <= dsl.lastrow - dsl.firstrow + 1; j++) {
                int end = rowstr.get(j + 1) - 1;
                for (int k = rowstr.get(j); k <= end; k++)
                    colidx.set(k, colidx.get(k) - offset);
            }
        }

        public void mult(Vector v, Vector result) {
            for (int j = 1; j <= dsl.lastrow - dsl.firstrow + 1; j++) {
                double sum = 0.0;
                int end = rowstr.get(j + 1) - 1;
                for (int k = rowstr.get(j); k <= end; k++)
                    sum += values.get(k) * v.elements.get(colidx.get(k));

                work.set(j, sum);
            }

            // Sum the partition submatrix-vec's across rows
            // Exchange and sum piece of w with procs identified in reduce_exch_proc
            for (int i = dsl.log2npcols; i >= 1; i--) {
                MPI.iRecvC(result.elements.toCArray(), dsl.reduceRecvStarts[i], dsl.reduceRecvLengths[i],
                          dsl.reduceExchProc[i], i, request);
                MPI.sendC(work.toCArray(), dsl.reduceSendStarts[i], dsl.reduceSendLengths[i],
                          dsl.reduceExchProc[i], i);
                MPI.wait(request);
                int end = dsl.sendStart + dsl.reduceRecvLengths[i] - 1;
                for (int j = dsl.sendStart; j <= end; j++)
                    work.set(j, work.get(j) + result.elements.get(j));
            }

            // Exchange piece of result with transpose processor:
            if (dsl.log2npcols != 0) {
                MPI.iRecvC(result.elements.toCArray(), 1, dsl.exchRecvLength,
                           dsl.exchProc, 1, request);
                MPI.sendC(work.toCArray(), dsl.sendStart, dsl.sendLen,
                          dsl.exchProc, 1);
                MPI.wait(request);
            }
            else
                for (int j = 1; j <= dsl.exchRecvLength; j++)
                    result.elements.set(j, work.get(j));

            // Clear work for reuse
            for (int j = 1; j <= dsl.matrixWorkSize; j++)
                work.set(j, 0.0);
        }
    }
}
