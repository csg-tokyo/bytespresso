/*
!-------------------------------------------------------------------------!
!                                                                         !
!         N  A  S     P A R A L L E L     B E N C H M A R K S  3.0        !
!                                                                         !
!                        J A V A     V E R S I O N                        !
!                                                                         !
!                                    C G                                  !
!                                                                         !
!-------------------------------------------------------------------------!
!                                                                         !
!    This benchmark is a serial/multithreaded version of the              !
!    NPB3_0_JAV CG code.                                                  !
!                                                                         !
!    Permission to use, copy, distribute and modify this software         !
!    for any purpose with or without fee is hereby granted.  We           !
!    request, however, that all derived work reference the NAS            !
!    Parallel Benchmarks 3.0. This software is provided "as is"           !
!    without express or implied warranty.                                 !
!                                                                         !
!    Information on NPB 3.0, including the Technical Report NAS-02-008    !
!    "Implementation of the NAS Parallel Benchmarks in Java",             !
!    original specifications, source code, results and information        !
!    on how to submit new results, is available at:                       !
!                                                                         !
!	    http://www.nas.nasa.gov/Software/NPB/                         !
!                                                                         !
!    Send comments or suggestions to  npb@nas.nasa.gov                    !
!                                                                         !
!	   NAS Parallel Benchmarks Group                                  !
!	   NASA Ames Research Center                                      !
!	   Mail Stop: T27A-1                                              !
!	   Moffett Field, CA   94035-1000                                 !
!                                                                         !
!	   E-mail:  npb@nas.nasa.gov                                      !
!	   Fax:     (650) 604-3957                                        !
!                                                                         !
!-------------------------------------------------------------------------!
! Authors: M. Yarrow                                                      !
!          C. Kuszmaul                                                    !
! Translation to Java and to MultiThreaded Code                           !
!	   M. Frumkin                                                     !
!	   M. Schultz                                                     !
!-------------------------------------------------------------------------!

  Modified by Shigeru Chiba in 2016
*/

package npbench3cg;

import static npbench3cg.Vector.inner;

/*
 * When this program is run on a big-endian machine such as SPARC,
 * -Dc.endian="big" must be given as an JVM argument.
 */

import javassist.offload.javatoc.DriverException;
import javassist.offload.lib.MPI;
import javassist.offload.lib.Util;

public class CG implements Cloneable {
    public static final int cgitmax = 25;
    public char dataClass = 'S';

    public int na, nonzer, niter;
    public double shift, rcond, zeta_verify_value;
    public int nz;

    public int iv[], arow[], acol[];
    public double v[], aelt[];

    public final Dsl dsl;
    public final Matrix.Sparse mat;
    public final Vector x, z, p, q, r;

    final Random randGen;
    public static final double amult = 1220703125.0;

    public static void main(String[] args) throws DriverException {
        if (args.length == 0) {
            System.out.println("java " + CG.class.getName()
                               + " [-java] class=<size> np=<num_proc_row>");
            System.out.println();
            System.out.println(" When np=0 is given, the serial version of the "
                               + "program is run.");
            return;
        }

        char clazz = classArgument(args);
        int nprocrow = nprocrowArgument(args);
        Dsl.java = javaArgument(args);
        System.out.println("class=" + clazz + " np=" + nprocrow + " java=" + Dsl.java);

        try {
            CG cg = new CG(clazz, nprocrow);
            cg.dsl.compile(new Runner(cg));
        }
        catch (OutOfMemoryError e) {
            System.out.println("The java maximum heap size is "
                               +"to small to run this benchmark class");
            System.out.println("To allocate more memory, use the -Xms, -Xmx options"); 
            System.exit(1);
        }
    }

    static char classArgument(String[] args) {
        for (String a: args)
            if (a.startsWith("class="))
                return Character.toUpperCase(a.charAt(6)); 

        return 'S';
    }

    static int nprocrowArgument(String[] args) {
        for (String a: args)
            if (a.startsWith("np="))
                return Integer.parseInt(a.substring(3));

        return 0;
    }

    static boolean javaArgument(String[] args) {
        for (String a: args)
            if (a.equals("-java"))
                return true;

        return false;
    }

    static class Runner implements Runnable, Cloneable {
        final CG cg;
        Runner(CG c) { cg = c; }
        public void run() {
            cg.runBenchMark();
        }
    }

    /**
     * Constructs a benchmark.
     *
     * @param clazz             the problem size.
     * @param numProcRows       the number of processes allocated for dividing rows.
     */
    public CG(char clazz, int numProcRows) {
        randGen = new Random();
        dataClass = clazz;
        switch (dataClass) {
        case 'S':
            na = 1400;
            nonzer = 7;
            shift = 10;
            niter = 15;
            rcond = .1;
            zeta_verify_value = 8.5971775078648;
            break;
        case 'W':
            na = 7000;
            nonzer = 8;
            shift = 12;
            niter = 15;
            rcond = .1;
            zeta_verify_value = 10.362595087124;
            break;
        case 'A':
            na = 14000;
            nonzer = 11;
            shift = 20;
            niter = 15;
            rcond = .1;
            zeta_verify_value = 17.130235054029;
            break;
        case 'B':
            na = 75000;
            nonzer = 13;
            shift = 60;
            niter = 75;
            rcond = .1;
            zeta_verify_value = 22.712745482631;
            break;
        case 'C':
            na = 150000;
            nonzer = 15;
            shift = 110;
            niter = 75;
            rcond = .1;
            zeta_verify_value = 28.973605592845;
            break;
        case 'D':
            na = 1500000;
            nonzer = 21;
            shift = 500;
            niter = 100;
            rcond = .1;
            zeta_verify_value = 52.514532105794;
            break;
        case 'E':
            na = 9000000;
            nonzer = 26;
            shift = 1500;
            niter = 100;
            rcond = .1;
            zeta_verify_value = 77.522164599383;
            break;
        default:
            throw new RuntimeException("bad class type: " + dataClass);
        }

        nz = (na * (nonzer + 1) * (nonzer + 1) + na * (nonzer + 2));

        dsl = new Dsl(na, numProcRows);
        mat = dsl.sparseMatrix(nz + 1);
        p = dsl.vector();
        q = dsl.vector();
        r = dsl.vector();
        x = dsl.vector();
        z = dsl.vector();
    }

    public int runBenchMark() {
        iv = new int[2 * (na + 1)];
        arow = new int[nz + 1];
        acol = new int[nz + 1];
        v = new double[na + 2];
        aelt = new double[nz + 1];

        if (dsl.rank() == 0) {
            Util.printer.p("NAS Parallel Benchmarks 3.3 -- CG ").p(dataClass).ln();
            Util.printer.p(" Size: ").p(na).p(", Iterations: ").p(niter)
                .p(", Processes ").p(dsl.numProcs).p(" (").p(dsl.numProcRows).p('x')
                .p(dsl.numProcs / dsl.numProcRows).p(')').ln();
        }

        double rnorm = 0;

        // ---------------------------------------------------------------------
        // Initialize random number generator
        // ---------------------------------------------------------------------
        double zeta = randGen.randlc(amult);

        makea(na, nz, mat, nonzer, rcond, arow, acol, aelt, v, iv, shift);

        // ---------------------------------------------------------------------
        // Note: as a result of the above call to makea:
        // values of j used in indexing rowstr go from 1 --> lastrow-firstrow+1
        // values of colidx which are col indexes go from firstcol --> lastcol
        // So:
        // Shift the col index vals from actual (firstcol --> lastcol )
        // to local, i.e., (1 --> lastcol-firstcol+1)
        // ---------------------------------------------------------------------
        mat.shift(dsl.firstcol - 1);

        // ---------------------------------------------------------------------
        // set starting vector to (1, 1, .... 1)
        // ---------------------------------------------------------------------
        x.set(1.0);

        zeta = 0.0;
        // ---------------------------------------------------------------------
        // Do one iteration untimed to init all code and data page tables
        // ----> (then reinit, start timing, to niter its)
        // ---------------------------------------------------------------------
        // input: colidx[nz], rowstr[na], x[na], a[nz]
        // output: z[na], p[na], q[na], r[na]
        // input/output: rnorm
        rnorm = conj_grad(x, z, mat, p, q, r, rnorm);

        // ---------------------------------------------------------------------
        // zeta = shift + 1/(x.z)
        // So, first: (x.z)
        // Also, find norm of z
        // So, first: (z.z)
        // ---------------------------------------------------------------------
        double tnorm1b = inner(x, z);
        double tnorm2b = 1.0 / Util.sqrt(z.norm());

        // ---------------------------------------------------------------------
        // Normalize z to obtain x
        // ---------------------------------------------------------------------
        // x.set(z, e -> tnorm2b * e);      // tnorm1 * e?
        x.set(tnorm2b, z);

        // ---------------------------------------------------------------------
        // set starting vector to (1, 1, .... 1)
        // ---------------------------------------------------------------------
        x.set(1.0);
        zeta = 0.0;

        MPI.barrier();
        long time1 = Util.time();
        // ---------------------------------------------------------------------
        // Main Iteration for inverse power method
        // ---------------------------------------------------------------------
        for (int it = 1; it <= niter; it++) {
            rnorm = conj_grad(x, z, mat, p, q, r, rnorm);

            // ---------------------------------------------------------------------
            // zeta = shift + 1/(x.z)
            // So, first: (x.z)
            // Also, find norm of z
            // So, first: (z.z)
            // ---------------------------------------------------------------------
            double tnorm1 = inner(x, z);
            double tnorm2 = 1.0 / Util.sqrt(z.norm());

            zeta = shift + 1.0 / tnorm1;

            // ---------------------------------------------------------------------
            // Normalize z to obtain x
            // ---------------------------------------------------------------------
            // x.set(z, e -> tnorm2 * e);
            x.set(tnorm2, z);
        }

        long time2 = Util.time();

        // ---------------------------------------------------------------------
        // End of timed section
        // ---------------------------------------------------------------------

        double time = (time2 - time1) / 1000000.0;
        if (dsl.processes() > 1)
            time = MPI.allReduce(time, MPI.max());

        if (dsl.rank() == 0) {
            int verified = verify(zeta, zeta_verify_value, dataClass);
            Util.printer.p(" Time in seconds   = ").p(time).ln();
            Util.printer.p(" Mflops total      = ").p(getMFLOPS(time, niter, na, nonzer)).ln();
            return verified;
        }
        else
            return 1;
    }

    public static double getMFLOPS(double total_time, int niter, int na, int nonzer) {
        double mflops = 0.0;
        if (total_time != 0.0) {
            mflops = (float) (2 * na)
                    * (3. + (float) (nonzer * (nonzer + 1)) + 25. * (5. + (float) (nonzer * (nonzer + 1))) + 3.);
            mflops *= niter / (total_time * 1000000.0);
        }

        return mflops;
    }

    public static int verify(double zeta, double zeta_verify, char dataClazz) {
        int verified = 0;
        double epsilon = 1.0E-10;
        if (dataClazz != 'U') {
            Util.printer.p(" Zeta is   ").p(zeta).ln();
            if (Util.fabs(zeta - zeta_verify) <= epsilon) {
                verified = 1;
                Util.printer.p(" Deviation is   ").p(zeta - zeta_verify).ln();
                Util.printer.p(" Verification Successful").ln();
            }
            else {
                verified = 0;
                Util.printer.p(" The correct zeta is ").p(zeta_verify).ln();
                Util.printer.p(" Verification Failed").ln();
            }
        }
        else {
            verified = -1;
            Util.printer.p("Verification Not Performed").ln();
        }

        return verified;
    }

    public void makea(int n, int nz, Matrix.Sparse a, int nonzer, double rcond, int[] arow,
                      int[] acol, double[] aelt, double[] v, int[] iv, double shift)
    {
        // ---------------------------------------------------------------------
        // generate the test problem for benchmark 6
        // makea generates a sparse matrix with a
        // prescribed sparsity distribution
        //
        // parameter type usage
        //
        // input
        //
        // n i number of cols/rows of matrix
        // nz i nonzeros as declared array size
        // rcond r*8 condition number
        // shift r*8 main diagonal shift
        //
        // output
        //
        // a r*8 array for nonzeros
        // colidx i col indices
        // rowstr i row pointers
        //
        // workspace
        //
        // iv, arow, acol i
        // v, aelt r*8
        // ---------------------------------------------------------------------

        int irow, nzv, jcol;

        // ---------------------------------------------------------------------
        // nonzer is approximately (int(sqrt(nnza /n)));
        // ---------------------------------------------------------------------

        double size = 1.0;
        double ratio = Util.pow(rcond, (1.0 / (float) n));
        int nnza = 0;

        // ---------------------------------------------------------------------
        // Initialize colidx(n+1 .. 2n) to zero.
        // Used by sprnvc to mark nonzero positions
        // ---------------------------------------------------------------------

        for (int i = 1; i <= n; i++)
            a.colidx.set(n + i, 0);

        for (int iouter = 1; iouter <= n; iouter++) {
            nzv = nonzer;
            sprnvc(n, nzv, v, iv, a, 0, n);
            nzv = vecset(n, v, iv, nzv, iouter, .5);

            for (int ivelt = 1; ivelt <= nzv; ivelt++) {
                jcol = iv[ivelt];
                if (jcol >= dsl.firstcol && jcol <= dsl.lastcol) {
                    double scale = size * v[ivelt];
                    for (int ivelt1 = 1; ivelt1 <= nzv; ivelt1++) {
                        irow = iv[ivelt1];
                        if (irow >= dsl.firstrow && irow <= dsl.lastrow) {
                            nnza = nnza + 1;
                            if (nnza > nz) {
                                Util.printer.p("Space for matrix elements exceeded in makea").ln();
                                Util.printer.p("nnza, nzmax = ").p(nnza).p(", ").p(nz).ln();
                                Util.printer.p(" iouter = ").p(iouter).ln();
                                Util.exit(1);
                            }
                            acol[nnza] = jcol;
                            arow[nnza] = irow;
                            aelt[nnza] = v[ivelt1] * scale;
                        }
                    }
                }
            }
            size = size * ratio;
        }

        // ---------------------------------------------------------------------
        // ... add the identity * rcond to the generated matrix to bound
        // the smallest eigenvalue from below by rcond
        // ---------------------------------------------------------------------
        for (int i = dsl.firstrow; i <= dsl.lastrow; i++) {
            if (i >= dsl.firstcol && i <= dsl.lastcol) {
                int iouter = n + i;
                nnza = nnza + 1;
                if (nnza > nz) {
                    Util.printer.p("Space for matrix elements exceeded in makea").ln();
                    Util.printer.p("nnza, nzmax = ").p(nnza).p(", ").p(nz).ln();
                    Util.printer.p(" iouter = ").p(iouter).ln();
                    Util.exit(1);
                }
                acol[nnza] = i;
                arow[nnza] = i;
                aelt[nnza] = rcond - shift;
            }
        }

        // ---------------------------------------------------------------------
        // ... make the sparse matrix from list of elements with duplicates
        // (v and iv are used as workspace)
        // ---------------------------------------------------------------------

        sparse(a, n, arow, acol, aelt, v, iv, 0, iv, n, nnza);
        return;
    }

    //--------------------------------------------------------------------
    //      generate a sparse n-vector (v, iv)
    //       having nzv nonzeros
    //
    //      mark(i) is set to 1 if position i is nonzero.
    //      mark is all zero on entry and is reset to all zero before exit
    //      this corrects a performance bug found by John G. Lewis, caused by
    //      reinitialization of mark on every one of the n calls to sprnvc
    //--------------------------------------------------------------------
    public void sprnvc(int n, int nz, double v[], int iv[], Matrix.Sparse a, int nzloc_offst, int mark_offst) {
        int nzrow = 0, nzv = 0;
        int idx;
        int nn1 = 1;

        do {
            nn1 = 2 * nn1;
        } while (nn1 < n);

        // nn1 is the smallest power of two not less than n

        while (true) {
            if (nzv >= nz) {
                for (int ii = 1; ii <= nzrow; ii++) {
                    idx = a.colidx.get(ii + nzloc_offst);
                    a.colidx.set(idx + mark_offst, 0);
                }
                return;
            }
            double vecelt = randGen.randlc(amult);

            // generate an integer between 1 and n in a portable manner
            double vecloc = randGen.randlc(amult);
            idx = (int) (vecloc * nn1) + 1;
            if (idx > n)
                continue;

            // was this integer generated already?
            if (a.colidx.get(idx + mark_offst) == 0) {
                a.colidx.set(idx + mark_offst, 1);
                nzrow = nzrow + 1;
                a.colidx.set(nzrow + nzloc_offst, idx);
                nzv = nzv + 1;
                v[nzv] = vecelt;
                iv[nzv] = idx;
            }
        }
    }

    // set i-th element of sparse vector (v, iv) with
    // nzv nonzeros to val.
    public int vecset(int n, double v[], int iv[], int nzv, int ival, double val) {
        boolean set = false;
        for (int k = 1; k <= nzv; k++) {
            if (iv[k] == ival) {
                v[k] = val;
                set = true;
            }
        }
        if (!set) {
            nzv = nzv + 1;
            v[nzv] = val;
            iv[nzv] = ival;
        }
        return nzv;
    }

    /*
     * x: double[n]. work space. it refers to #v. mark: boolean[n]. work space.
     * it refers to #iv[..nzloc_offset]. nzloc: int[n]. work space. it refers to
     * #iv[nzloc_offset..]
     */
    public void sparse(Matrix.Sparse a, int n, int arow[], int acol[], double aelt[], double x[],
                       int mark[], int mark_offst, int nzloc[], int nzloc_offst, int nnza) {

        // ---------------------------------------------------
        // generate a sparse matrix from a list of
        // [col, row, element] tri
        // ---------------------------------------------------

        // ---------------------------------------------------------------------
        // rows range from firstrow to lastrow
        // the rowstr pointers are defined for nrows = lastrow-firstrow+1 values
        //
        // how many rows of result
        // ---------------------------------------------------------------------
        int nrows = dsl.lastrow - dsl.firstrow + 1;

        // ---------------------------------------------------------------------
        // ...count the number of triples in each row
        // ---------------------------------------------------------------------
        for (int j = 1; j <= n; j++) {
            a.rowstr.set(j, 0);
            mark[j + mark_offst] = 0;
        }

        a.rowstr.set(n + 1, 0);

        for (int nza = 1; nza <= nnza; nza++) {
            int j = (arow[nza] - dsl.firstrow + 1) + 1;
            a.rowstr.set(j, a.rowstr.get(j) + 1);
        }

        a.rowstr.set(1, 1);
        for (int j = 2; j <= nrows + 1; j++)
            a.rowstr.set(j, a.rowstr.get(j) + a.rowstr.get(j - 1));

        // ---------------------------------------------------------------------
        // ... rowstr(j) now is the location of the first nonzero
        // of row j of a
        // ---------------------------------------------------------------------

        // ---------------------------------------------------------------------
        // ... do a bucket sort of the triples on the row index
        // ---------------------------------------------------------------------
        for (int nza = 1; nza <= nnza; nza++) {
            int j = arow[nza] - dsl.firstrow + 1;
            int k = a.rowstr.get(j);
            a.values.set(k, aelt[nza]);
            a.colidx.set(k, acol[nza]);
            a.rowstr.set(j, a.rowstr.get(j) + 1);
        }

        // ---------------------------------------------------------------------
        // ... rowstr(j) now points to the first element of row j+1
        // ---------------------------------------------------------------------
        for (int j = nrows - 1; j >= 0; j--)
            a.rowstr.set(j + 1, a.rowstr.get(j));

        a.rowstr.set(1, 1);
        // ---------------------------------------------------------------------
        // ... generate the actual output rows by adding elements
        // ---------------------------------------------------------------------
        int nza = 0;
        for (int i = 1; i <= n; i++) {
            x[i] = 0.0;
            mark[i + mark_offst] = 0;
        }

        int jajp1 = a.rowstr.get(1);

        for (int j = 1; j <= nrows; j++) {
            int nzrow = 0;

            // ---------------------------------------------------------------------
            // ...loop over the jth row of a
            // ---------------------------------------------------------------------
            for (int k = jajp1; k <= (a.rowstr.get(j + 1) - 1); k++) {
                int i = a.colidx.get(k);
                x[i] = x[i] + a.values.get(k);
                if ((mark[i + mark_offst] == 0) && (x[i] != 0)) {
                    mark[i + mark_offst] = 1;
                    nzrow = nzrow + 1;
                    nzloc[nzrow + nzloc_offst] = i;
                }
            }

            // ---------------------------------------------------------------------
            // ... extract the nonzeros of this row
            // ---------------------------------------------------------------------
            for (int k = 1; k <= nzrow; k++) {
                int i = nzloc[k + nzloc_offst];
                mark[i + mark_offst] = 0;
                double xi = x[i];
                x[i] = 0;
                if (xi != 0) {
                    nza = nza + 1;
                    a.values.set(nza, xi);
                    a.colidx.set(nza, i);
                }
            }
            jajp1 = a.rowstr.get(j + 1);
            a.rowstr.set(j + 1, nza + a.rowstr.get(1));
        }
        return;
    }

    /*
     * input: x, a output: z, p, q, r input/output: rnorm
     */
    public double conj_grad(Vector x, Vector z, Matrix.Sparse a,
                            Vector p, Vector q, Vector r, double rnorm)
    {
        // ---------------------------------------------------------------------
        // Floating point arrays here are named as in NPB1 spec discussion of
        // CG algorithm
        // ---------------------------------------------------------------------

        // ---------------------------------------------------------------------
        // Initialize the CG algorithm:
        // ---------------------------------------------------------------------
        q.set(0.0);
        z.set(0.0);
        r.set(x);
        p.set(r);

        // ---------------------------------------------------------------------
        // rho = r.r
        // Now, obtain the norm of r: First, sum squares of r elements
        // locally...
        // ---------------------------------------------------------------------
        double rho = r.norm();

        // ---------------------------------------------------------------------
        // The conj grad iteration loop
        // ---------------------------------------------------------------------
        for (int cgit = 1; cgit <= cgitmax; cgit++) {
            // ---------------------------------------------------------------------
            // q = A.p
            // The partition submatrix-vector multiply: use workspace w
            // ---------------------------------------------------------------------
            //
            // NOTE: this version of the multiply is actually (slightly: maybe
            // %5)
            // faster on the sp2 on 16 nodes than is the unrolled-by-2 version
            // below. On the Cray t3d, the reverse is true, i.e., the
            // unrolled-by-two version is some 10% faster.
            // The unrolled-by-8 version below is significantly faster
            // on the Cray t3d - overall speed of code is 1.5 times faster.
            //
            q.setToMult(a, p);

            // ---------------------------------------------------------------------
            // Obtain p.q
            // ---------------------------------------------------------------------
            double d = inner(p, q);

            // ---------------------------------------------------------------------
            // Obtain alpha = rho / (p.q)
            // ---------------------------------------------------------------------
            double alpha = rho / d;
            // ---------------------------------------------------------------------
            // Obtain z = z + alpha*p
            // and r = r - alpha*q
            // ---------------------------------------------------------------------
            z.setToAdd(z, alpha, p);
            r.setToAdd(r, -alpha, q);

            // ---------------------------------------------------------------------
            // rho = r.r
            // Obtain the norm of r: First, sum squares of r elements locally...
            // ---------------------------------------------------------------------
            double rho0 = rho;
            rho = r.norm();
            double beta = rho / rho0;
            // ---------------------------------------------------------------------
            // p = r + beta*p
            // ---------------------------------------------------------------------
            p.setToAdd(r, beta, p);
        }
        // ---------------------------------------------------------------------
        // Compute residual norm explicitly: ||r|| = ||x - A.z||
        // First, form A.z
        // The partition submatrix-vector multiply
        // ---------------------------------------------------------------------
        r.setToMult(a, z);

        // ---------------------------------------------------------------------
        // At this point, r contains A.z
        // ---------------------------------------------------------------------
        // sum = 0.0;
        // for (j = 1; j <= lastcol - firstcol + 1; j++)
        //    sum += (x[j] - r[j]) * (x[j] - r[j]);
        r.setToSub(x, r);
        double sum = r.norm();
        return Util.sqrt(sum);
    }
}
