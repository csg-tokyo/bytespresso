/*
!-------------------------------------------------------------------------!
!									  !
!	 N  A  S     P A R A L L E L	 B E N C H M A R K S  3.0	  !
!									  !
!			J A V A 	V E R S I O N			  !
!									  !
!                                  LU                                     !
!                                                                         !
!-------------------------------------------------------------------------!
!                                                                         !
!    This benchmark is a serial/multithreaded version of                  !
!    the NPB3_0_JAV LU code.                                              !
!                                                                         !
!    Permission to use, copy, distribute and modify this software	  !
!    for any purpose with or without fee is hereby granted.  We 	  !
!    request, however, that all derived work reference the NAS  	  !
!    Parallel Benchmarks 3.0. This software is provided "as is" 	  !
!    without express or implied warranty.				  !
!									  !
!    Information on NPB 3.0, including the Technical Report NAS-02-008	  !
!    "Implementation of the NAS Parallel Benchmarks in Java",		  !
!    original specifications, source code, results and information	  !
!    on how to submit new results, is available at:			  !
!									  !
!	    http://www.nas.nasa.gov/Software/NPB/			  !
!									  !
!    Send comments or suggestions to  npb@nas.nasa.gov  		  !
!									  !
!	   NAS Parallel Benchmarks Group				  !
!	   NASA Ames Research Center					  !
!	   Mail Stop: T27A-1						  !
!	   Moffett Field, CA   94035-1000				  !
!									  !
!	   E-mail:  npb@nas.nasa.gov					  !
!	   Fax:     (650) 604-3957					  !
!									  !
!-------------------------------------------------------------------------!
! Authors: R. Van der Wijngaart 					  !
!	   T. Harris							  !
!	   M. Yarrow							  !
! Translation to Java and MultiThreaded Code				  !
!	   M. Frumkin							  !
!	   M. Schultz							  !
!-------------------------------------------------------------------------!
*/

package npbench3lu;

import java.io.File;
import java.text.DecimalFormat;

import javassist.offload.Options;
import javassist.offload.javatoc.DriverException;
import javassist.offload.lib.MPI;
import javassist.offload.lib.MPIDriver;
import javassist.offload.lib.Util;
import npbench3lu.arrayXD.*;

public class LU extends LUBase {
    /**
     * True when the benchmark runs on the JVM with MPI emulation.
     */
    public static boolean java = false;
    public static boolean useStdDriverIfNumProcsIs1 = false;
    public int bid = -1;
    public BMResults results;
    private static char CLASS;
    
    public static void main(String[] args) throws DriverException {
        Options.deadcodeElimination = false;
        if (args.length == 0) {
            System.out.println("java " + LU.class.getName() + " [-java] class=<size> np=<num_proc_row>");
            System.out.println();
            System.out.println(" When np=0 is given, the serial version of the " + "program is run.");
            return;
        }

        char clazz = classArgument(args);
        CLASS = clazz;
        int numProcs = nprocrowArgument(args);
        LU.java = javaArgument(args);
        System.out.println("class=" + clazz + " np=" + numProcs + " java=" + LU.java);

        try {
            LU lu = new LU(clazz, numProcs);
            lu.compile(new Runner(lu), numProcs);
        } catch (RuntimeException e) {
            System.out.println("RuntimeException: " + e.toString());
            e.printStackTrace();
            System.exit(1);
        } catch (OutOfMemoryError e) {
            System.out.println("The java maximum heap size is " + "to small to run this benchmark class");
            System.out.println("To allocate more memory, use the -Xms, -Xmx options");
            System.exit(1);
        }
    }

    static char classArgument(String[] args) {
        for (String a : args)
            if (a.startsWith("class="))
                return Character.toUpperCase(a.charAt(6));

        return 'S';
    }

    static int nprocrowArgument(String[] args) {
        for (String a : args)
            if (a.startsWith("np="))
                return Integer.parseInt(a.substring(3));

        return 1;
    }

    static boolean javaArgument(String[] args) {
        for (String a : args)
            if (a.equals("-java")) {
                System.out.println("Error: -java is not supported.");
                return true;
            }

        return false;
    }

    static class Runner implements Runnable, Cloneable {
        final LU lu;

        Runner(LU c) {
            lu = c;
        }

        public void run() {
            lu.runBenchMark();
        }
    }

    public void compile(Runnable r, int numProcs) throws DriverException {
        if (numProcs == 1 && useStdDriverIfNumProcsIs1) {
            if (java)
                r.run();
            else {
                new javassist.offload.javatoc.StdDriver().invoke(() -> {
                    r.run();
                });
            }
        } else {
            MPIDriver driver;
            if (java)
                driver = new MPIDriver(numProcs).inJava();
            else {
                /*
                 * numProcs will be reset to the number passed by -n to mpiexec.
                 */
                driver = new MPIDriver();
            }

            driver.invoke(() -> {
                r.run();
            });
        }
    }

    // public static void main(String argv[]) throws Exception {
    // LU lu = null;
    //
    // BMArgs.ParseCmdLineArgs(argv,BMName);
    // char CLSS=BMArgs.CLASS;
    // try{
    // lu = new LU(CLSS);
    // }catch(OutOfMemoryError e){
    // BMArgs.outOfMemoryMessage();
    // System.exit(0);
    // }
    //
    // new MPIDriver(4).invoke(() -> {
    // lu.runBenchMark();
    // });
    //
    // }
    //
    // public void run(){runBenchMark();}

    /**
     * Constructs a benchmark.
     *
     * @param clazz
     *            the problem size.
     * @param numProcRows
     *            the number of processes allocated for dividing rows.
     */
    public LU(char clazz, int numProcRows) {
        super(clazz, numProcRows);

        // dsl = new Dsl(na, numProcRows);
        // mat = dsl.sparseMatrix(nz + 1);
        // p = dsl.vector();
        // q = dsl.vector();
        // r = dsl.vector();
        // x = dsl.vector();
        // z = dsl.vector();
    }

    public void runBenchMark() {
        int numTimers = t_last + 1;
        // String t_names[] = new String[numTimers];
        // double trecs[] = new double[numTimers];
        // setTimers(t_names);

        // c---------------------------------------------------------------------
        // c initialize communications
        // c---------------------------------------------------------------------
        init_comm();

        if (id == 0) {
            // BMArgs.Banner(BMName, clazz);
            BMArgs.Banner(BMName, '?');
        }

        //
        // c---------------------------------------------------------------------
        // c read input data
        // c---------------------------------------------------------------------
        read_input();

        // c---------------------------------------------------------------------
        // c set up processor grid
        // c---------------------------------------------------------------------
        proc_grid();

        // c---------------------------------------------------------------------
        // c determine the neighbors
        // c---------------------------------------------------------------------
        neighbors();

        // c---------------------------------------------------------------------
        // c set up sub-domain sizes
        // c---------------------------------------------------------------------
        subdomain();

        // ---------------------------------------------------------------------
        // set up coefficients
        // ---------------------------------------------------------------------
        setcoeff();

        // c---------------------------------------------------------------------
        // c set the masks required for comm
        // c---------------------------------------------------------------------
        sethyper();

        // ---------------------------------------------------------------------
        // set the boundary values for dependent variables
        // ---------------------------------------------------------------------
        setbv();

        // ---------------------------------------------------------------------
        // set the initial values for dependent variables
        // ---------------------------------------------------------------------
        setiv();

        // ---------------------------------------------------------------------
        // compute the forcing term based on prescribed exact solution
        // ---------------------------------------------------------------------
        erhs();

        // ---------------------------------------------------------------------
        // perform the SSOR iterations
        // ---------------------------------------------------------------------
        double tm;
        tm = ssor(); // micro second

        // ---------------------------------------------------------------------
        // compute the solution error
        // ---------------------------------------------------------------------
        error();

        // ---------------------------------------------------------------------
        // compute the surface integral
        // ---------------------------------------------------------------------
        pintgr();

        // ---------------------------------------------------------------------
        // verification test
        // ---------------------------------------------------------------------
        if (id == 0) {
            boolean verified = verify(rsdnm, errnm, frc);

            // results = new BMResults(BMName, 'A', nx0, ny0, nz0, itmax, tm,
            // getMFLOPS(itmax, tm), "floating point",
            // verified ? 1 : 0, bid);
            //results.print();
            Util.printer.p(getMFLOPS(itmax, tm / 1000000)).p(" mflops").ln();
            Util.printer.p(tm/1000000).p(" seconds").ln();        
        }

        // ---------------------------------------------------------------------
        // More timers
        // ---------------------------------------------------------------------
        // if (timeron)
        // printTimers(t_names, trecs, tm);
    }

    public double getMFLOPS(int itmax, double tm) {
        double mflops = 0.0;
        if (tm > 0) {
            mflops = 1984.77 * nx0 * ny0 * nz0 - 10923.3 * Util.pow((nx0 + ny0 + nz0) / 3.0, 2)
                    + 27770.9 * (nx0 + ny0 + nz0) / 3.0 - 144010.0;
            mflops *= itmax / (tm * 1000000.0);
        }
        return mflops;
    }

    public void printTimers(String t_names[], double trecs[], double tm) {
        DecimalFormat fmt = new DecimalFormat("0.000");
        System.out.println("  SECTION     Time (secs)");
        for (int i = 0; i < t_last; i++)
            trecs[i] = timer.readTimer(i);
        if (tm == 0.0)
            tm = 1.0;
        for (int i = 1; i < t_last; i++) {
            System.out.println(
                    "  " + t_names[i] + ":" + fmt.format(trecs[i]) + "  (" + fmt.format(trecs[i] * 100. / tm) + "%)");
            if (i == t_rhs) {
                double t = trecs[t_rhsx] + trecs[t_rhsy] + trecs[t_rhsz];
                System.out.println("     " + "--> total " + "sub-rhs" + ":" + fmt.format(t) + "  ("
                        + fmt.format(t * 100. / tm) + "%)");
                t = trecs[i] - t;
                System.out.println("     " + "--> total " + "rest-rhs" + ":" + fmt.format(t) + "  ("
                        + fmt.format(t * 100. / tm) + "%)");
            }
        }
    }

    public void setTimers(String t_names[]) {
        File f1 = new File("timer.flag");
        timeron = false;
        if (f1.exists()) {
            timeron = true;
            t_names[t_total] = "total";
            t_names[t_rhsx] = "rhsx";
            t_names[t_rhsy] = "rhsy";
            t_names[t_rhsz] = "rhsz";
            t_names[t_rhs] = "rhs";
            t_names[t_jacld] = "jacld";
            t_names[t_blts] = "blts";
            t_names[t_jacu] = "jacu";
            t_names[t_buts] = "buts";
            t_names[t_add] = "add";
            t_names[t_l2norm] = "l2norm";
        }
    }

    // c---------------------------------------------------------------------
    // c
    // c compute the regular-sparse, block lower triangular solution:
    // c
    // c v <-- ( L-inv ) * v
    // c
    // c---------------------------------------------------------------------

    public void blts(int ldmx, int ldmy, int ldmz, int nx, int ny, int nz, int k, double omega, Array4Ddouble v,
            Array4Ddouble ldz, Array4Ddouble ldy, Array4Ddouble ldx, Array4Ddouble d, int ist, int iend, int jst,
            int jend, int nx0, int ny0, int ipt, int jpt) {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int i, j, m;
        int iex;
        double tmp, tmp1;
        Array2Ddouble tmat = new Array2Ddouble(5, 5);

        // c---------------------------------------------------------------------
        // c receive data from north and west
        // c---------------------------------------------------------------------
        iex = 0;

        // before_dump();
        // Util.printer.p("--- ").p(k).p(" ").e(omega).ln();
        // after_dump();
        // dump_rsd();
        // dump_array("blts 1 v", v);
        exchange_1(v, k, iex);
        // dump_array("blts 2 v", v);

        for (j = jst; j <= jend; j++) {
            for (i = ist; i <= iend; i++) {
                for (m = 1; m <= 5; m++) {

                    v.set(m, i, j, k, v.get(m, i, j, k) - omega * (ldz.get(m, 1, i, j) * v.get(1, i, j, k - 1)
                            + ldz.get(m, 2, i, j) * v.get(2, i, j, k - 1) + ldz.get(m, 3, i, j) * v.get(3, i, j, k - 1)
                            + ldz.get(m, 4, i, j) * v.get(4, i, j, k - 1)
                            + ldz.get(m, 5, i, j) * v.get(5, i, j, k - 1)));

                }
            }
        }

        // dump_array("blts v", v);

        for (j = jst; j <= jend; j++) {
            for (i = ist; i <= iend; i++) {

                for (m = 1; m <= 5; m++) {

                    v.set(m, i, j, k, v.get(m, i, j, k) - omega * (ldy.get(m, 1, i, j) * v.get(1, i, j - 1, k)
                            + ldx.get(m, 1, i, j) * v.get(1, i - 1, j, k) + ldy.get(m, 2, i, j) * v.get(2, i, j - 1, k)
                            + ldx.get(m, 2, i, j) * v.get(2, i - 1, j, k) + ldy.get(m, 3, i, j) * v.get(3, i, j - 1, k)
                            + ldx.get(m, 3, i, j) * v.get(3, i - 1, j, k) + ldy.get(m, 4, i, j) * v.get(4, i, j - 1, k)
                            + ldx.get(m, 4, i, j) * v.get(4, i - 1, j, k) + ldy.get(m, 5, i, j) * v.get(5, i, j - 1, k)
                            + ldx.get(m, 5, i, j) * v.get(5, i - 1, j, k)));

                }

                // c---------------------------------------------------------------------
                // c diagonal block inversion
                // c
                // c forward elimination
                // c---------------------------------------------------------------------
                for (m = 1; m <= 5; m++) {
                    tmat.set(m, 1, d.get(m, 1, i, j));
                    tmat.set(m, 2, d.get(m, 2, i, j));
                    tmat.set(m, 3, d.get(m, 3, i, j));
                    tmat.set(m, 4, d.get(m, 4, i, j));
                    tmat.set(m, 5, d.get(m, 5, i, j));
                }

                tmp1 = 1.0e+00 / tmat.get(1, 1);
                tmp = tmp1 * tmat.get(2, 1);
                tmat.set(2, 2, tmat.get(2, 2) - tmp * tmat.get(1, 2));
                tmat.set(2, 3, tmat.get(2, 3) - tmp * tmat.get(1, 3));
                tmat.set(2, 4, tmat.get(2, 4) - tmp * tmat.get(1, 4));
                tmat.set(2, 5, tmat.get(2, 5) - tmp * tmat.get(1, 5));
                v.set(2, i, j, k, v.get(2, i, j, k) - v.get(1, i, j, k) * tmp);

                tmp = tmp1 * tmat.get(3, 1);
                tmat.set(3, 2, tmat.get(3, 2) - tmp * tmat.get(1, 2));
                tmat.set(3, 3, tmat.get(3, 3) - tmp * tmat.get(1, 3));
                tmat.set(3, 4, tmat.get(3, 4) - tmp * tmat.get(1, 4));
                tmat.set(3, 5, tmat.get(3, 5) - tmp * tmat.get(1, 5));
                v.set(3, i, j, k, v.get(3, i, j, k) - v.get(1, i, j, k) * tmp);

                tmp = tmp1 * tmat.get(4, 1);
                tmat.set(4, 2, tmat.get(4, 2) - tmp * tmat.get(1, 2));
                tmat.set(4, 3, tmat.get(4, 3) - tmp * tmat.get(1, 3));
                tmat.set(4, 4, tmat.get(4, 4) - tmp * tmat.get(1, 4));
                tmat.set(4, 5, tmat.get(4, 5) - tmp * tmat.get(1, 5));
                v.set(4, i, j, k, v.get(4, i, j, k) - v.get(1, i, j, k) * tmp);

                tmp = tmp1 * tmat.get(5, 1);
                tmat.set(5, 2, tmat.get(5, 2) - tmp * tmat.get(1, 2));
                tmat.set(5, 3, tmat.get(5, 3) - tmp * tmat.get(1, 3));
                tmat.set(5, 4, tmat.get(5, 4) - tmp * tmat.get(1, 4));
                tmat.set(5, 5, tmat.get(5, 5) - tmp * tmat.get(1, 5));
                v.set(5, i, j, k, v.get(5, i, j, k) - v.get(1, i, j, k) * tmp);

                tmp1 = 1.0e+00 / tmat.get(2, 2);
                tmp = tmp1 * tmat.get(3, 2);
                tmat.set(3, 3, tmat.get(3, 3) - tmp * tmat.get(2, 3));
                tmat.set(3, 4, tmat.get(3, 4) - tmp * tmat.get(2, 4));
                tmat.set(3, 5, tmat.get(3, 5) - tmp * tmat.get(2, 5));
                v.set(3, i, j, k, v.get(3, i, j, k) - v.get(2, i, j, k) * tmp);

                tmp = tmp1 * tmat.get(4, 2);
                tmat.set(4, 3, tmat.get(4, 3) - tmp * tmat.get(2, 3));
                tmat.set(4, 4, tmat.get(4, 4) - tmp * tmat.get(2, 4));
                tmat.set(4, 5, tmat.get(4, 5) - tmp * tmat.get(2, 5));
                v.set(4, i, j, k, v.get(4, i, j, k) - v.get(2, i, j, k) * tmp);

                tmp = tmp1 * tmat.get(5, 2);
                tmat.set(5, 3, tmat.get(5, 3) - tmp * tmat.get(2, 3));
                tmat.set(5, 4, tmat.get(5, 4) - tmp * tmat.get(2, 4));
                tmat.set(5, 5, tmat.get(5, 5) - tmp * tmat.get(2, 5));
                v.set(5, i, j, k, v.get(5, i, j, k) - v.get(2, i, j, k) * tmp);

                tmp1 = 1.0e+00 / tmat.get(3, 3);
                tmp = tmp1 * tmat.get(4, 3);
                tmat.set(4, 4, tmat.get(4, 4) - tmp * tmat.get(3, 4));
                tmat.set(4, 5, tmat.get(4, 5) - tmp * tmat.get(3, 5));
                v.set(4, i, j, k, v.get(4, i, j, k) - v.get(3, i, j, k) * tmp);

                tmp = tmp1 * tmat.get(5, 3);
                tmat.set(5, 4, tmat.get(5, 4) - tmp * tmat.get(3, 4));
                tmat.set(5, 5, tmat.get(5, 5) - tmp * tmat.get(3, 5));
                v.set(5, i, j, k, v.get(5, i, j, k) - v.get(3, i, j, k) * tmp);

                tmp1 = 1.0e+00 / tmat.get(4, 4);
                tmp = tmp1 * tmat.get(5, 4);
                tmat.set(5, 5, tmat.get(5, 5) - tmp * tmat.get(4, 5));
                v.set(5, i, j, k, v.get(5, i, j, k) - v.get(4, i, j, k) * tmp);

                // c---------------------------------------------------------------------
                // c back substitution
                // c---------------------------------------------------------------------
                v.set(5, i, j, k, v.get(5, i, j, k) / tmat.get(5, 5));

                v.set(4, i, j, k, v.get(4, i, j, k) - tmat.get(4, 5) * v.get(5, i, j, k));
                v.set(4, i, j, k, v.get(4, i, j, k) / tmat.get(4, 4));

                v.set(3, i, j, k,
                        v.get(3, i, j, k) - tmat.get(3, 4) * v.get(4, i, j, k) - tmat.get(3, 5) * v.get(5, i, j, k));
                v.set(3, i, j, k, v.get(3, i, j, k) / tmat.get(3, 3));

                v.set(2, i, j, k, v.get(2, i, j, k) - tmat.get(2, 3) * v.get(3, i, j, k)
                        - tmat.get(2, 4) * v.get(4, i, j, k) - tmat.get(2, 5) * v.get(5, i, j, k));
                v.set(2, i, j, k, v.get(2, i, j, k) / tmat.get(2, 2));

                v.set(1, i, j, k,
                        v.get(1, i, j, k) - tmat.get(1, 2) * v.get(2, i, j, k) - tmat.get(1, 3) * v.get(3, i, j, k)
                                - tmat.get(1, 4) * v.get(4, i, j, k) - tmat.get(1, 5) * v.get(5, i, j, k));
                v.set(1, i, j, k, v.get(1, i, j, k) / tmat.get(1, 1));

            }
        }

        // dump_array("blts v", v);
        // dump_array("blts tmat", tmat);

        // c---------------------------------------------------------------------
        // c send data to east and south
        // c---------------------------------------------------------------------
        iex = 2;
        exchange_1(v, k, iex);
        // dump_rsd();

    }

    // c---------------------------------------------------------------------
    // c
    // c compute the regular-sparse, block upper triangular solution:
    // c
    // c v <-- ( U-inv ) * v
    // c
    // c---------------------------------------------------------------------

    public void buts(int ldmx, int ldmy, int ldmz, int nx, int ny, int nz, int k, double omega, Array4Ddouble v,
            Array3Ddouble tv, Array4Ddouble d, Array4Ddouble udx, Array4Ddouble udy, Array4Ddouble udz, int ist,
            int iend, int jst, int jend, int nx0, int ny0, int ipt, int jpt) {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int i, j, m;
        int iex;
        double tmp, tmp1;
        Array2Ddouble tmat = new Array2Ddouble(5, 5);

        // c---------------------------------------------------------------------
        // c receive data from south and east
        // c---------------------------------------------------------------------
        iex = 1;
        exchange_1(v, k, iex);

        for (j = jend; j >= jst; j--) {
            for (i = iend; i >= ist; i--) {
                for (m = 1; m <= 5; m++) {
                    tv.set(m, i, j, omega * (udz.get(m, 1, i, j) * v.get(1, i, j, k + 1)
                            + udz.get(m, 2, i, j) * v.get(2, i, j, k + 1) + udz.get(m, 3, i, j) * v.get(3, i, j, k + 1)
                            + udz.get(m, 4, i, j) * v.get(4, i, j, k + 1)
                            + udz.get(m, 5, i, j) * v.get(5, i, j, k + 1)));
                }
            }
        }

        for (j = jend; j >= jst; j--) {
            for (i = iend; i >= ist; i--) {

                for (m = 1; m <= 5; m++) {
                    tv.set(m, i, j, tv.get(m, i, j) + omega * (udy.get(m, 1, i, j) * v.get(1, i, j + 1, k)
                            + udx.get(m, 1, i, j) * v.get(1, i + 1, j, k) + udy.get(m, 2, i, j) * v.get(2, i, j + 1, k)
                            + udx.get(m, 2, i, j) * v.get(2, i + 1, j, k) + udy.get(m, 3, i, j) * v.get(3, i, j + 1, k)
                            + udx.get(m, 3, i, j) * v.get(3, i + 1, j, k) + udy.get(m, 4, i, j) * v.get(4, i, j + 1, k)
                            + udx.get(m, 4, i, j) * v.get(4, i + 1, j, k) + udy.get(m, 5, i, j) * v.get(5, i, j + 1, k)
                            + udx.get(m, 5, i, j) * v.get(5, i + 1, j, k)));
                }

                // c---------------------------------------------------------------------
                // c diagonal block inversion
                // c---------------------------------------------------------------------
                for (m = 1; m <= 5; m++) {
                    tmat.set(m, 1, d.get(m, 1, i, j));
                    tmat.set(m, 2, d.get(m, 2, i, j));
                    tmat.set(m, 3, d.get(m, 3, i, j));
                    tmat.set(m, 4, d.get(m, 4, i, j));
                    tmat.set(m, 5, d.get(m, 5, i, j));
                }

                tmp1 = 1.0e+00 / tmat.get(1, 1);
                tmp = tmp1 * tmat.get(2, 1);
                tmat.set(2, 2, tmat.get(2, 2) - tmp * tmat.get(1, 2));
                tmat.set(2, 3, tmat.get(2, 3) - tmp * tmat.get(1, 3));
                tmat.set(2, 4, tmat.get(2, 4) - tmp * tmat.get(1, 4));
                tmat.set(2, 5, tmat.get(2, 5) - tmp * tmat.get(1, 5));
                tv.set(2, i, j, tv.get(2, i, j) - tv.get(1, i, j) * tmp);

                tmp = tmp1 * tmat.get(3, 1);
                tmat.set(3, 2, tmat.get(3, 2) - tmp * tmat.get(1, 2));
                tmat.set(3, 3, tmat.get(3, 3) - tmp * tmat.get(1, 3));
                tmat.set(3, 4, tmat.get(3, 4) - tmp * tmat.get(1, 4));
                tmat.set(3, 5, tmat.get(3, 5) - tmp * tmat.get(1, 5));
                tv.set(3, i, j, tv.get(3, i, j) - tv.get(1, i, j) * tmp);

                tmp = tmp1 * tmat.get(4, 1);
                tmat.set(4, 2, tmat.get(4, 2) - tmp * tmat.get(1, 2));
                tmat.set(4, 3, tmat.get(4, 3) - tmp * tmat.get(1, 3));
                tmat.set(4, 4, tmat.get(4, 4) - tmp * tmat.get(1, 4));
                tmat.set(4, 5, tmat.get(4, 5) - tmp * tmat.get(1, 5));
                tv.set(4, i, j, tv.get(4, i, j) - tv.get(1, i, j) * tmp);

                tmp = tmp1 * tmat.get(5, 1);
                tmat.set(5, 2, tmat.get(5, 2) - tmp * tmat.get(1, 2));
                tmat.set(5, 3, tmat.get(5, 3) - tmp * tmat.get(1, 3));
                tmat.set(5, 4, tmat.get(5, 4) - tmp * tmat.get(1, 4));
                tmat.set(5, 5, tmat.get(5, 5) - tmp * tmat.get(1, 5));
                tv.set(5, i, j, tv.get(5, i, j) - tv.get(1, i, j) * tmp);

                tmp1 = 1.0e+00 / tmat.get(2, 2);
                tmp = tmp1 * tmat.get(3, 2);
                tmat.set(3, 3, tmat.get(3, 3) - tmp * tmat.get(2, 3));
                tmat.set(3, 4, tmat.get(3, 4) - tmp * tmat.get(2, 4));
                tmat.set(3, 5, tmat.get(3, 5) - tmp * tmat.get(2, 5));
                tv.set(3, i, j, tv.get(3, i, j) - tv.get(2, i, j) * tmp);

                tmp = tmp1 * tmat.get(4, 2);
                tmat.set(4, 3, tmat.get(4, 3) - tmp * tmat.get(2, 3));
                tmat.set(4, 4, tmat.get(4, 4) - tmp * tmat.get(2, 4));
                tmat.set(4, 5, tmat.get(4, 5) - tmp * tmat.get(2, 5));
                tv.set(4, i, j, tv.get(4, i, j) - tv.get(2, i, j) * tmp);

                tmp = tmp1 * tmat.get(5, 2);
                tmat.set(5, 3, tmat.get(5, 3) - tmp * tmat.get(2, 3));
                tmat.set(5, 4, tmat.get(5, 4) - tmp * tmat.get(2, 4));
                tmat.set(5, 5, tmat.get(5, 5) - tmp * tmat.get(2, 5));
                tv.set(5, i, j, tv.get(5, i, j) - tv.get(2, i, j) * tmp);

                tmp1 = 1.0e+00 / tmat.get(3, 3);
                tmp = tmp1 * tmat.get(4, 3);
                tmat.set(4, 4, tmat.get(4, 4) - tmp * tmat.get(3, 4));
                tmat.set(4, 5, tmat.get(4, 5) - tmp * tmat.get(3, 5));
                tv.set(4, i, j, tv.get(4, i, j) - tv.get(3, i, j) * tmp);

                tmp = tmp1 * tmat.get(5, 3);
                tmat.set(5, 4, tmat.get(5, 4) - tmp * tmat.get(3, 4));
                tmat.set(5, 5, tmat.get(5, 5) - tmp * tmat.get(3, 5));
                tv.set(5, i, j, tv.get(5, i, j) - tv.get(3, i, j) * tmp);

                tmp1 = 1.0e+00 / tmat.get(4, 4);
                tmp = tmp1 * tmat.get(5, 4);
                tmat.set(5, 5, tmat.get(5, 5) - tmp * tmat.get(4, 5));
                tv.set(5, i, j, tv.get(5, i, j) - tv.get(4, i, j) * tmp);

                // c---------------------------------------------------------------------
                // c back substitution
                // c---------------------------------------------------------------------
                tv.set(5, i, j, tv.get(5, i, j) / tmat.get(5, 5));

                tv.set(4, i, j, tv.get(4, i, j) - tmat.get(4, 5) * tv.get(5, i, j));
                tv.set(4, i, j, tv.get(4, i, j) / tmat.get(4, 4));

                tv.set(3, i, j, tv.get(3, i, j) - tmat.get(3, 4) * tv.get(4, i, j) - tmat.get(3, 5) * tv.get(5, i, j));
                tv.set(3, i, j, tv.get(3, i, j) / tmat.get(3, 3));

                tv.set(2, i, j, tv.get(2, i, j) - tmat.get(2, 3) * tv.get(3, i, j) - tmat.get(2, 4) * tv.get(4, i, j)
                        - tmat.get(2, 5) * tv.get(5, i, j));
                tv.set(2, i, j, tv.get(2, i, j) / tmat.get(2, 2));

                tv.set(1, i, j, tv.get(1, i, j) - tmat.get(1, 2) * tv.get(2, i, j) - tmat.get(1, 3) * tv.get(3, i, j)
                        - tmat.get(1, 4) * tv.get(4, i, j) - tmat.get(1, 5) * tv.get(5, i, j));
                tv.set(1, i, j, tv.get(1, i, j) / tmat.get(1, 1));

                v.set(1, i, j, k, v.get(1, i, j, k) - tv.get(1, i, j));
                v.set(2, i, j, k, v.get(2, i, j, k) - tv.get(2, i, j));
                v.set(3, i, j, k, v.get(3, i, j, k) - tv.get(3, i, j));
                v.set(4, i, j, k, v.get(4, i, j, k) - tv.get(4, i, j));
                v.set(5, i, j, k, v.get(5, i, j, k) - tv.get(5, i, j));

            }
        }

        // c---------------------------------------------------------------------
        // c send data to north and west
        // c---------------------------------------------------------------------
        iex = 3;
        exchange_1(v, k, iex);

    }

    // c---------------------------------------------------------------------
    // c
    // c compute the right hand side based on exact solution
    // c
    // c---------------------------------------------------------------------
    public void erhs() {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int i, j, k, m;
        int iglob, jglob;
        int iex;
        int L1, L2;
        int ist1, iend1;
        int jst1, jend1;
        double dsspm;
        double xi, eta, zeta;
        double q;
        double u21, u31, u41;
        double tmp;
        double u21i, u31i, u41i, u51i;
        double u21j, u31j, u41j, u51j;
        double u21k, u31k, u41k, u51k;
        double u21im1, u31im1, u41im1, u51im1;
        double u21jm1, u31jm1, u41jm1, u51jm1;
        double u21km1, u31km1, u41km1, u51km1;

        dsspm = dssp;

        for (k = 1; k <= nz; k++) {
            for (j = 1; j <= ny; j++) {
                for (i = 1; i <= nx; i++) {
                    for (m = 1; m <= 5; m++) {
                        frct.set(m, i, j, k, 0.0e+00);
                    }
                }
            }
        }

        for (k = 1; k <= nz; k++) {
            zeta = ((double) (k - 1)) / (nz - 1);
            for (j = 1; j <= ny; j++) {
                jglob = jpt + j;
                eta = ((double) (jglob - 1)) / (ny0 - 1);
                for (i = 1; i <= nx; i++) {
                    iglob = ipt + i;
                    xi = ((double) (iglob - 1)) / (nx0 - 1);
                    for (m = 1; m <= 5; m++) {
                        rsd.set(m, i, j, k, ce.get(m, 1) + ce.get(m, 2) * xi + ce.get(m, 3) * eta + ce.get(m, 4) * zeta
                                + ce.get(m, 5) * xi * xi + ce.get(m, 6) * eta * eta + ce.get(m, 7) * zeta * zeta
                                + ce.get(m, 8) * xi * xi * xi + ce.get(m, 9) * eta * eta * eta
                                + ce.get(m, 10) * zeta * zeta * zeta + ce.get(m, 11) * xi * xi * xi * xi
                                + ce.get(m, 12) * eta * eta * eta * eta + ce.get(m, 13) * zeta * zeta * zeta * zeta);
                    }
                }
            }
        }

        // dump_rsd();

        // c---------------------------------------------------------------------
        // c xi-direction flux differences
        // c---------------------------------------------------------------------
        // c
        // c iex = flag : iex = 0 north/south communication
        // c : iex = 1 east/west communication
        // c
        // c---------------------------------------------------------------------
        iex = 0;

        // c---------------------------------------------------------------------
        // c communicate and receive/send two rows of data
        // c---------------------------------------------------------------------
        exchange_3(rsd, iex);

        // dump_rsd();

        L1 = 0;
        if (north == -1)
            L1 = 1;
        L2 = nx + 1;
        if (south == -1)
            L2 = nx;

        for (k = 2; k <= nz - 1; k++) {
            // dump_flux();
            for (j = jst; j <= jend; j++) {
                for (i = L1; i <= L2; i++) {
                    flux.set(1, i, j, k, rsd.get(2, i, j, k));
                    u21 = rsd.get(2, i, j, k) / rsd.get(1, i, j, k);
                    q = 0.50e+00 * (rsd.get(2, i, j, k) * rsd.get(2, i, j, k)
                            + rsd.get(3, i, j, k) * rsd.get(3, i, j, k) + rsd.get(4, i, j, k) * rsd.get(4, i, j, k))
                            / rsd.get(1, i, j, k);
                    flux.set(2, i, j, k, rsd.get(2, i, j, k) * u21 + c2 * (rsd.get(5, i, j, k) - q));
                    flux.set(3, i, j, k, rsd.get(3, i, j, k) * u21);
                    flux.set(4, i, j, k, rsd.get(4, i, j, k) * u21);
                    flux.set(5, i, j, k, (c1 * rsd.get(5, i, j, k) - c2 * q) * u21);
                }
            }
        }

        // dump_flux();

        for (k = 2; k <= nz - 1; k++) {
            for (j = jst; j <= jend; j++) {
                for (i = ist; i <= iend; i++) {
                    for (m = 1; m <= 5; m++) {
                        frct.set(m, i, j, k,
                                frct.get(m, i, j, k) - tx2 * (flux.get(m, i + 1, j, k) - flux.get(m, i - 1, j, k)));
                    }
                }
                for (i = ist; i <= L2; i++) {

                    tmp = 1.0e+00 / rsd.get(1, i, j, k);
                    // before_dump();
                    // Util.printer.p(id).p(", ").p(i).p(", ").p(j).p(",
                    // ").p(k).p(", ").e(tmp).ln();
                    // after_dump();

                    u21i = tmp * rsd.get(2, i, j, k);
                    u31i = tmp * rsd.get(3, i, j, k);
                    u41i = tmp * rsd.get(4, i, j, k);
                    u51i = tmp * rsd.get(5, i, j, k);

                    tmp = 1.0e+00 / rsd.get(1, i - 1, j, k);
                    // dump_rsd();
                    // before_dump();
                    // Util.printer.p(id).p(", ").e(rsd.get(1, i-1, j, k)).p(",
                    // ").e(tmp).p(", ").e(tx3).ln();
                    // after_dump();

                    u21im1 = tmp * rsd.get(2, i - 1, j, k);
                    u31im1 = tmp * rsd.get(3, i - 1, j, k);
                    u41im1 = tmp * rsd.get(4, i - 1, j, k);
                    u51im1 = tmp * rsd.get(5, i - 1, j, k);

                    flux.set(2, i, j, k, (4.0e+00 / 3.0e+00) * tx3 * (u21i - u21im1));
                    flux.set(3, i, j, k, tx3 * (u31i - u31im1));
                    flux.set(4, i, j, k, tx3 * (u41i - u41im1));
                    flux.set(5, i, j, k,
                            0.50e+00 * (1.0e+00 - c1 * c5) * tx3
                                    * ((u21i * u21i + u31i * u31i + u41i * u41i)
                                            - (u21im1 * u21im1 + u31im1 * u31im1 + u41im1 * u41im1))
                                    + (1.0e+00 / 6.0e+00) * tx3 * (u21i * u21i - u21im1 * u21im1)
                                    + c1 * c5 * tx3 * (u51i - u51im1));

                    // dump_flux();
                }

                for (i = ist; i <= iend; i++) {
                    frct.set(1, i, j, k, frct.get(1, i, j, k) + dx1 * tx1
                            * (rsd.get(1, i - 1, j, k) - 2.0e+00 * rsd.get(1, i, j, k) + rsd.get(1, i + 1, j, k)));
                    frct.set(2, i, j, k,
                            frct.get(2, i, j, k) + tx3 * c3 * c4 * (flux.get(2, i + 1, j, k) - flux.get(2, i, j, k))
                                    + dx2 * tx1 * (rsd.get(2, i - 1, j, k) - 2.0e+00 * rsd.get(2, i, j, k)
                                            + rsd.get(2, i + 1, j, k)));
                    frct.set(3, i, j, k,
                            frct.get(3, i, j, k) + tx3 * c3 * c4 * (flux.get(3, i + 1, j, k) - flux.get(3, i, j, k))
                                    + dx3 * tx1 * (rsd.get(3, i - 1, j, k) - 2.0e+00 * rsd.get(3, i, j, k)
                                            + rsd.get(3, i + 1, j, k)));
                    frct.set(4, i, j, k,
                            frct.get(4, i, j, k) + tx3 * c3 * c4 * (flux.get(4, i + 1, j, k) - flux.get(4, i, j, k))
                                    + dx4 * tx1 * (rsd.get(4, i - 1, j, k) - 2.0e+00 * rsd.get(4, i, j, k)
                                            + rsd.get(4, i + 1, j, k)));
                    frct.set(5, i, j, k,
                            frct.get(5, i, j, k) + tx3 * c3 * c4 * (flux.get(5, i + 1, j, k) - flux.get(5, i, j, k))
                                    + dx5 * tx1 * (rsd.get(5, i - 1, j, k) - 2.0e+00 * rsd.get(5, i, j, k)
                                            + rsd.get(5, i + 1, j, k)));
                }

                // dump_flux();
                // dump_frct();

                // c---------------------------------------------------------------------
                // c Fourth-order dissipation
                // c---------------------------------------------------------------------
                if (north == -1) {
                    for (m = 1; m <= 5; m++) {
                        frct.set(m, 2, j, k, frct.get(m, 2, j, k) - dsspm * (+5.0e+00 * rsd.get(m, 2, j, k)
                                - 4.0e+00 * rsd.get(m, 3, j, k) + rsd.get(m, 4, j, k)));
                        frct.set(m, 3, j, k, frct.get(m, 3, j, k) - dsspm * (-4.0e+00 * rsd.get(m, 2, j, k)
                                + 6.0e+00 * rsd.get(m, 3, j, k) - 4.0e+00 * rsd.get(m, 4, j, k) + rsd.get(m, 5, j, k)));
                    }
                }

                ist1 = 1;
                iend1 = nx;
                if (north == -1)
                    ist1 = 4;
                if (south == -1)
                    iend1 = nx - 3;
                for (i = ist1; i <= iend1; i++) {
                    for (m = 1; m <= 5; m++) {
                        frct.set(m, i, j, k,
                                frct.get(m, i, j, k) - dsspm * (rsd.get(m, i - 2, j, k)
                                        - 4.0e+00 * rsd.get(m, i - 1, j, k) + 6.0e+00 * rsd.get(m, i, j, k)
                                        - 4.0e+00 * rsd.get(m, i + 1, j, k) + rsd.get(m, i + 2, j, k)));
                    }
                }

                if (south == -1) {
                    for (m = 1; m <= 5; m++) {
                        frct.set(m, nx - 2, j, k,
                                frct.get(m, nx - 2, j, k) - dsspm * (rsd.get(m, nx - 4, j, k)
                                        - 4.0e+00 * rsd.get(m, nx - 3, j, k) + 6.0e+00 * rsd.get(m, nx - 2, j, k)
                                        - 4.0e+00 * rsd.get(m, nx - 1, j, k)));
                        frct.set(m, nx - 1, j, k, frct.get(m, nx - 1, j, k) - dsspm * (rsd.get(m, nx - 3, j, k)
                                - 4.0e+00 * rsd.get(m, nx - 2, j, k) + 5.0e+00 * rsd.get(m, nx - 1, j, k)));
                    }
                }

            }
        }

        // dump_frct();
        // dump_flux();

        // c---------------------------------------------------------------------
        // c eta-direction flux differences
        // c---------------------------------------------------------------------
        // c
        // c iex = flag : iex = 0 north/south communication
        // c : iex = 1 east/west communication
        // c
        // c---------------------------------------------------------------------
        iex = 1;

        // c---------------------------------------------------------------------
        // c communicate and receive/send two rows of data
        // c---------------------------------------------------------------------
        exchange_3(rsd, iex);

        L1 = 0;
        if (west == -1)
            L1 = 1;
        L2 = ny + 1;
        if (east == -1)
            L2 = ny;

        for (k = 2; k <= nz - 1; k++) {
            for (i = ist; i <= iend; i++) {
                for (j = L1; j <= L2; j++) {
                    flux.set(1, i, j, k, rsd.get(3, i, j, k));
                    u31 = rsd.get(3, i, j, k) / rsd.get(1, i, j, k);
                    q = 0.50e+00 * (rsd.get(2, i, j, k) * rsd.get(2, i, j, k)
                            + rsd.get(3, i, j, k) * rsd.get(3, i, j, k) + rsd.get(4, i, j, k) * rsd.get(4, i, j, k))
                            / rsd.get(1, i, j, k);
                    flux.set(2, i, j, k, rsd.get(2, i, j, k) * u31);
                    flux.set(3, i, j, k, rsd.get(3, i, j, k) * u31 + c2 * (rsd.get(5, i, j, k) - q));
                    flux.set(4, i, j, k, rsd.get(4, i, j, k) * u31);
                    flux.set(5, i, j, k, (c1 * rsd.get(5, i, j, k) - c2 * q) * u31);
                }
            }
        }

        for (k = 2; k <= nz - 1; k++) {
            for (i = ist; i <= iend; i++) {
                for (j = jst; j <= jend; j++) {
                    for (m = 1; m <= 5; m++) {
                        frct.set(m, i, j, k,
                                frct.get(m, i, j, k) - ty2 * (flux.get(m, i, j + 1, k) - flux.get(m, i, j - 1, k)));
                    }
                }

                for (j = jst; j <= L2; j++) {
                    tmp = 1.0e+00 / rsd.get(1, i, j, k);

                    u21j = tmp * rsd.get(2, i, j, k);
                    u31j = tmp * rsd.get(3, i, j, k);
                    u41j = tmp * rsd.get(4, i, j, k);
                    u51j = tmp * rsd.get(5, i, j, k);

                    tmp = 1.0e+00 / rsd.get(1, i, j - 1, k);

                    u21jm1 = tmp * rsd.get(2, i, j - 1, k);
                    u31jm1 = tmp * rsd.get(3, i, j - 1, k);
                    u41jm1 = tmp * rsd.get(4, i, j - 1, k);
                    u51jm1 = tmp * rsd.get(5, i, j - 1, k);

                    flux.set(2, i, j, k, ty3 * (u21j - u21jm1));
                    flux.set(3, i, j, k, (4.0e+00 / 3.0e+00) * ty3 * (u31j - u31jm1));
                    flux.set(4, i, j, k, ty3 * (u41j - u41jm1));
                    flux.set(5, i, j, k,
                            0.50e+00 * (1.0e+00 - c1 * c5) * ty3
                                    * ((u21j * u21j + u31j * u31j + u41j * u41j)
                                            - (u21jm1 * u21jm1 + u31jm1 * u31jm1 + u41jm1 * u41jm1))
                                    + (1.0e+00 / 6.0e+00) * ty3 * (u31j * u31j - u31jm1 * u31jm1)
                                    + c1 * c5 * ty3 * (u51j - u51jm1));
                }

                for (j = jst; j <= jend; j++) {
                    frct.set(1, i, j, k, frct.get(1, i, j, k) + dy1 * ty1
                            * (rsd.get(1, i, j - 1, k) - 2.0e+00 * rsd.get(1, i, j, k) + rsd.get(1, i, j + 1, k)));
                    frct.set(2, i, j, k,
                            frct.get(2, i, j, k) + ty3 * c3 * c4 * (flux.get(2, i, j + 1, k) - flux.get(2, i, j, k))
                                    + dy2 * ty1 * (rsd.get(2, i, j - 1, k) - 2.0e+00 * rsd.get(2, i, j, k)
                                            + rsd.get(2, i, j + 1, k)));
                    frct.set(3, i, j, k,
                            frct.get(3, i, j, k) + ty3 * c3 * c4 * (flux.get(3, i, j + 1, k) - flux.get(3, i, j, k))
                                    + dy3 * ty1 * (rsd.get(3, i, j - 1, k) - 2.0e+00 * rsd.get(3, i, j, k)
                                            + rsd.get(3, i, j + 1, k)));
                    frct.set(4, i, j, k,
                            frct.get(4, i, j, k) + ty3 * c3 * c4 * (flux.get(4, i, j + 1, k) - flux.get(4, i, j, k))
                                    + dy4 * ty1 * (rsd.get(4, i, j - 1, k) - 2.0e+00 * rsd.get(4, i, j, k)
                                            + rsd.get(4, i, j + 1, k)));
                    frct.set(5, i, j, k,
                            frct.get(5, i, j, k) + ty3 * c3 * c4 * (flux.get(5, i, j + 1, k) - flux.get(5, i, j, k))
                                    + dy5 * ty1 * (rsd.get(5, i, j - 1, k) - 2.0e+00 * rsd.get(5, i, j, k)
                                            + rsd.get(5, i, j + 1, k)));
                }

                // c---------------------------------------------------------------------
                // c fourth-order dissipation
                // c---------------------------------------------------------------------
                if (west == -1) {
                    for (m = 1; m <= 5; m++) {
                        frct.set(m, i, 2, k, frct.get(m, i, 2, k) - dsspm * (+5.0e+00 * rsd.get(m, i, 2, k)
                                - 4.0e+00 * rsd.get(m, i, 3, k) + rsd.get(m, i, 4, k)));
                        frct.set(m, i, 3, k, frct.get(m, i, 3, k) - dsspm * (-4.0e+00 * rsd.get(m, i, 2, k)
                                + 6.0e+00 * rsd.get(m, i, 3, k) - 4.0e+00 * rsd.get(m, i, 4, k) + rsd.get(m, i, 5, k)));
                    }
                }

                jst1 = 1;
                jend1 = ny;
                if (west == -1)
                    jst1 = 4;
                if (east == -1)
                    jend1 = ny - 3;

                for (j = jst1; j <= jend1; j++) {
                    for (m = 1; m <= 5; m++) {
                        frct.set(m, i, j, k,
                                frct.get(m, i, j, k) - dsspm * (rsd.get(m, i, j - 2, k)
                                        - 4.0e+00 * rsd.get(m, i, j - 1, k) + 6.0e+00 * rsd.get(m, i, j, k)
                                        - 4.0e+00 * rsd.get(m, i, j + 1, k) + rsd.get(m, i, j + 2, k)));
                    }
                }

                if (east == -1) {
                    for (m = 1; m <= 5; m++) {
                        frct.set(m, i, ny - 2, k,
                                frct.get(m, i, ny - 2, k) - dsspm * (rsd.get(m, i, ny - 4, k)
                                        - 4.0e+00 * rsd.get(m, i, ny - 3, k) + 6.0e+00 * rsd.get(m, i, ny - 2, k)
                                        - 4.0e+00 * rsd.get(m, i, ny - 1, k)));
                        frct.set(m, i, ny - 1, k, frct.get(m, i, ny - 1, k) - dsspm * (rsd.get(m, i, ny - 3, k)
                                - 4.0e+00 * rsd.get(m, i, ny - 2, k) + 5.0e+00 * rsd.get(m, i, ny - 1, k)));
                    }
                }

            }
        }

        // c---------------------------------------------------------------------
        // c zeta-direction flux differences
        // c---------------------------------------------------------------------
        for (j = jst; j <= jend; j++) {
            for (i = ist; i <= iend; i++) {
                for (k = 1; k <= nz; k++) {
                    flux.set(1, i, j, k, rsd.get(4, i, j, k));
                    u41 = rsd.get(4, i, j, k) / rsd.get(1, i, j, k);
                    q = 0.50e+00 * (rsd.get(2, i, j, k) * rsd.get(2, i, j, k)
                            + rsd.get(3, i, j, k) * rsd.get(3, i, j, k) + rsd.get(4, i, j, k) * rsd.get(4, i, j, k))
                            / rsd.get(1, i, j, k);
                    flux.set(2, i, j, k, rsd.get(2, i, j, k) * u41);
                    flux.set(3, i, j, k, rsd.get(3, i, j, k) * u41);
                    flux.set(4, i, j, k, rsd.get(4, i, j, k) * u41 + c2 * (rsd.get(5, i, j, k) - q));
                    flux.set(5, i, j, k, (c1 * rsd.get(5, i, j, k) - c2 * q) * u41);
                }

                for (k = 2; k <= nz - 1; k++) {
                    for (m = 1; m <= 5; m++) {
                        frct.set(m, i, j, k,
                                frct.get(m, i, j, k) - tz2 * (flux.get(m, i, j, k + 1) - flux.get(m, i, j, k - 1)));
                    }
                }

                for (k = 2; k <= nz; k++) {
                    tmp = 1.0e+00 / rsd.get(1, i, j, k);

                    u21k = tmp * rsd.get(2, i, j, k);
                    u31k = tmp * rsd.get(3, i, j, k);
                    u41k = tmp * rsd.get(4, i, j, k);
                    u51k = tmp * rsd.get(5, i, j, k);

                    tmp = 1.0e+00 / rsd.get(1, i, j, k - 1);

                    u21km1 = tmp * rsd.get(2, i, j, k - 1);
                    u31km1 = tmp * rsd.get(3, i, j, k - 1);
                    u41km1 = tmp * rsd.get(4, i, j, k - 1);
                    u51km1 = tmp * rsd.get(5, i, j, k - 1);

                    flux.set(2, i, j, k, tz3 * (u21k - u21km1));
                    flux.set(3, i, j, k, tz3 * (u31k - u31km1));
                    flux.set(4, i, j, k, (4.0e+00 / 3.0e+00) * tz3 * (u41k - u41km1));
                    flux.set(5, i, j, k,
                            0.50e+00 * (1.0e+00 - c1 * c5) * tz3
                                    * ((u21k * u21k + u31k * u31k + u41k * u41k)
                                            - (u21km1 * u21km1 + u31km1 * u31km1 + u41km1 * u41km1))
                                    + (1.0e+00 / 6.0e+00) * tz3 * (u41k * u41k - u41km1 * u41km1)
                                    + c1 * c5 * tz3 * (u51k - u51km1));
                }

                for (k = 2; k <= nz - 1; k++) {
                    frct.set(1, i, j, k, frct.get(1, i, j, k) + dz1 * tz1
                            * (rsd.get(1, i, j, k + 1) - 2.0e+00 * rsd.get(1, i, j, k) + rsd.get(1, i, j, k - 1)));
                    frct.set(2, i, j, k,
                            frct.get(2, i, j, k) + tz3 * c3 * c4 * (flux.get(2, i, j, k + 1) - flux.get(2, i, j, k))
                                    + dz2 * tz1 * (rsd.get(2, i, j, k + 1) - 2.0e+00 * rsd.get(2, i, j, k)
                                            + rsd.get(2, i, j, k - 1)));
                    frct.set(3, i, j, k,
                            frct.get(3, i, j, k) + tz3 * c3 * c4 * (flux.get(3, i, j, k + 1) - flux.get(3, i, j, k))
                                    + dz3 * tz1 * (rsd.get(3, i, j, k + 1) - 2.0e+00 * rsd.get(3, i, j, k)
                                            + rsd.get(3, i, j, k - 1)));
                    frct.set(4, i, j, k,
                            frct.get(4, i, j, k) + tz3 * c3 * c4 * (flux.get(4, i, j, k + 1) - flux.get(4, i, j, k))
                                    + dz4 * tz1 * (rsd.get(4, i, j, k + 1) - 2.0e+00 * rsd.get(4, i, j, k)
                                            + rsd.get(4, i, j, k - 1)));
                    frct.set(5, i, j, k,
                            frct.get(5, i, j, k) + tz3 * c3 * c4 * (flux.get(5, i, j, k + 1) - flux.get(5, i, j, k))
                                    + dz5 * tz1 * (rsd.get(5, i, j, k + 1) - 2.0e+00 * rsd.get(5, i, j, k)
                                            + rsd.get(5, i, j, k - 1)));
                }

                // c---------------------------------------------------------------------
                // c fourth-order dissipation
                // c---------------------------------------------------------------------
                for (m = 1; m <= 5; m++) {
                    frct.set(m, i, j, 2, frct.get(m, i, j, 2) - dsspm
                            * (+5.0e+00 * rsd.get(m, i, j, 2) - 4.0e+00 * rsd.get(m, i, j, 3) + rsd.get(m, i, j, 4)));
                    frct.set(m, i, j, 3, frct.get(m, i, j, 3) - dsspm * (-4.0e+00 * rsd.get(m, i, j, 2)
                            + 6.0e+00 * rsd.get(m, i, j, 3) - 4.0e+00 * rsd.get(m, i, j, 4) + rsd.get(m, i, j, 5)));
                }

                for (k = 4; k <= nz - 3; k++) {
                    for (m = 1; m <= 5; m++) {
                        frct.set(m, i, j, k,
                                frct.get(m, i, j, k) - dsspm * (rsd.get(m, i, j, k - 2)
                                        - 4.0e+00 * rsd.get(m, i, j, k - 1) + 6.0e+00 * rsd.get(m, i, j, k)
                                        - 4.0e+00 * rsd.get(m, i, j, k + 1) + rsd.get(m, i, j, k + 2)));
                    }
                }

                for (m = 1; m <= 5; m++) {
                    frct.set(m, i, j, nz - 2,
                            frct.get(m, i, j, nz - 2)
                                    - dsspm * (rsd.get(m, i, j, nz - 4) - 4.0e+00 * rsd.get(m, i, j, nz - 3)
                                            + 6.0e+00 * rsd.get(m, i, j, nz - 2) - 4.0e+00 * rsd.get(m, i, j, nz - 1)));
                    frct.set(m, i, j, nz - 1, frct.get(m, i, j, nz - 1) - dsspm * (rsd.get(m, i, j, nz - 3)
                            - 4.0e+00 * rsd.get(m, i, j, nz - 2) + 5.0e+00 * rsd.get(m, i, j, nz - 1)));
                }
            }
        }

    }

    protected final MPI.Request request_exch = new MPI.Request();

    public void exchange_1(Array4Ddouble g, int k, int iex) {

        int i, j;
        Array2Ddouble dum = new Array2Ddouble(5, isiz1 + isiz2);
        Array2Ddouble dum1 = new Array2Ddouble(5, isiz1 + isiz2);

        if (iex == 0) {

            if (north != -1) {
                double tmp[] = new double[5 * (jend - jst + 1)];
                MPI.iRecv(tmp, 5 * (jend - jst + 1), north, from_n, request_exch);
                MPI.wait(request_exch);
                // trace_recv(tmp, 5*(jend-jst+1), north, from_n);
                dum1.setValues(1, jst, 5 * (jend - jst + 1), tmp);
                // trace_recv(dum1.getData(), dum1.getDataSize(), 999, 999);

                // dump_array("dum1 1 ", dum1);
                // dump_array("g", g);
                for (j = jst; j <= jend; j++) {
                    g.set(1, 0, j, k, dum1.get(1, j));
                    g.set(2, 0, j, k, dum1.get(2, j));
                    g.set(3, 0, j, k, dum1.get(3, j));
                    g.set(4, 0, j, k, dum1.get(4, j));
                    g.set(5, 0, j, k, dum1.get(5, j));
                }
            }

            if (west != -1) {
                double tmp[] = new double[5 * (iend - ist + 1)];
                MPI.iRecv(tmp, 5 * (iend - ist + 1), west, from_w, request_exch);
                MPI.wait(request_exch);
                trace_recv(tmp, 5 * (iend - ist + 1), west, from_w);
                dum1.setValues(1, ist, 5 * (iend - ist + 1), tmp);
                dump_array("dum1 2 ", dum1);
                dump_array("g", g);
                for (i = ist; i <= iend; i++) {
                    g.set(1, i, 0, k, dum1.get(1, i));
                    g.set(2, i, 0, k, dum1.get(2, i));
                    g.set(3, i, 0, k, dum1.get(3, i));
                    g.set(4, i, 0, k, dum1.get(4, i));
                    g.set(5, i, 0, k, dum1.get(5, i));
                }
            }
        } else if (iex == 1) {

            if (south != -1) {
                double tmp[] = new double[5 * (jend - jst + 1)];
                MPI.iRecv(tmp, 5 * (jend - jst + 1), south, from_s, request_exch);
                MPI.wait(request_exch);
                dum1.setValues(1, jst, 5 * (jend - jst + 1), tmp);
                for (j = jst; j <= jend; j++) {
                    g.set(1, nx + 1, j, k, dum1.get(1, j));
                    g.set(2, nx + 1, j, k, dum1.get(2, j));
                    g.set(3, nx + 1, j, k, dum1.get(3, j));
                    g.set(4, nx + 1, j, k, dum1.get(4, j));
                    g.set(5, nx + 1, j, k, dum1.get(5, j));
                }
            }

            if (east != -1) {
                double tmp[] = new double[5 * (iend - ist + 1)];
                MPI.iRecv(tmp, 5 * (iend - ist + 1), east, from_e, request_exch);
                MPI.wait(request_exch);
                dum1.setValues(1, ist, 5 * (iend - ist + 1), tmp);
                for (i = ist; i <= iend; i++) {
                    g.set(1, i, ny + 1, k, dum1.get(1, i));
                    g.set(2, i, ny + 1, k, dum1.get(2, i));
                    g.set(3, i, ny + 1, k, dum1.get(3, i));
                    g.set(4, i, ny + 1, k, dum1.get(4, i));
                    g.set(5, i, ny + 1, k, dum1.get(5, i));
                }
            }

        } else if (iex == 2) {

            if (south != -1) {
                for (j = jst; j <= jend; j++) {
                    dum.set(1, j, g.get(1, nx, j, k));
                    dum.set(2, j, g.get(2, nx, j, k));
                    dum.set(3, j, g.get(3, nx, j, k));
                    dum.set(4, j, g.get(4, nx, j, k));
                    dum.set(5, j, g.get(5, nx, j, k));
                }
                MPI.send(dum.getData(), dum.getIndex(1, jst), 5 * (jend - jst + 1), south, from_n);
                trace_send(dum.getData(), dum.getIndex(1, jst), 5 * (jend - jst + 1), south, from_n);
            }

            if (east != -1) {
                for (i = ist; i <= iend; i++) {
                    dum.set(1, i, g.get(1, i, ny, k));
                    dum.set(2, i, g.get(2, i, ny, k));
                    dum.set(3, i, g.get(3, i, ny, k));
                    dum.set(4, i, g.get(4, i, ny, k));
                    dum.set(5, i, g.get(5, i, ny, k));
                }
                MPI.send(dum.getData(), dum.getIndex(1, ist), 5 * (iend - ist + 1), east, from_w);
                trace_send(dum.getData(), dum.getIndex(1, ist), 5 * (iend - ist + 1), east, from_w);
            }

        } else {

            if (north != -1) {
                for (j = jst; j <= jend; j++) {
                    dum.set(1, j, g.get(1, 1, j, k));
                    dum.set(2, j, g.get(2, 1, j, k));
                    dum.set(3, j, g.get(3, 1, j, k));
                    dum.set(4, j, g.get(4, 1, j, k));
                    dum.set(5, j, g.get(5, 1, j, k));
                }
                MPI.send(dum.getData(), dum.getIndex(1, jst), 5 * (jend - jst + 1), north, from_s);
            }

            if (west != -1) {
                for (i = ist; i <= iend; i++) {
                    dum.set(1, i, g.get(1, i, 1, k));
                    dum.set(2, i, g.get(2, i, 1, k));
                    dum.set(3, i, g.get(3, i, 1, k));
                    dum.set(4, i, g.get(4, i, 1, k));
                    dum.set(5, i, g.get(5, i, 1, k));
                }
                int offset = dum.getIndex(1, ist);
                MPI.send(dum.getData(), offset, 5 * (iend - ist + 1), west, from_e);
            }

        }

        // dump_array("g", g);
    }

    public void exchange_3(Array4Ddouble g, int iex) {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int i, j, k;
        int ipos1, ipos2;

        // MPI.Request request_exch = new MPI.Request();
        // Util.printer.p("[").p(id).p("] nsew
        // ").p(north).p(south).p(east).p(west).ln();

        if (iex == 0) {
            double tmp[] = new double[buf1.getDataSize()];

            // c---------------------------------------------------------------------
            // c communicate in the south and north directions
            // c---------------------------------------------------------------------
            if (north != -1) {
                MPI.iRecv(tmp, 10 * ny * nz, MPI.MPI_ANY_SOURCE(), from_n, request_exch);
            }

            // c---------------------------------------------------------------------
            // c send south
            // c---------------------------------------------------------------------
            if (south != -1) {
                for (k = 1; k <= nz; k++) {
                    for (j = 1; j <= ny; j++) {
                        ipos1 = (k - 1) * ny + j;
                        ipos2 = ipos1 + ny * nz;
                        buf.set(1, ipos1, g.get(1, nx - 1, j, k));
                        buf.set(2, ipos1, g.get(2, nx - 1, j, k));
                        buf.set(3, ipos1, g.get(3, nx - 1, j, k));
                        buf.set(4, ipos1, g.get(4, nx - 1, j, k));
                        buf.set(5, ipos1, g.get(5, nx - 1, j, k));
                        buf.set(1, ipos2, g.get(1, nx, j, k));
                        buf.set(2, ipos2, g.get(2, nx, j, k));
                        buf.set(3, ipos2, g.get(3, nx, j, k));
                        buf.set(4, ipos2, g.get(4, nx, j, k));
                        buf.set(5, ipos2, g.get(5, nx, j, k));
                    }
                }

                /*
                 * double tmp2[] = buf.getData(); for (int ii = 0; ii < 10 * ny
                 * * nz; ii++) {
                 * Util.printer.p("[").p(id).p("] send to south ").e(tmp2[ii]).
                 * ln(); }
                 */

                MPI.send(buf.getData(), 0, 10 * ny * nz, south, from_n);
            }

            // c---------------------------------------------------------------------
            // c receive from north
            // c---------------------------------------------------------------------
            if (north != -1) {
                MPI.wait(request_exch);
                buf1.setData(tmp);

                for (k = 1; k <= nz; k++) {
                    for (j = 1; j <= ny; j++) {
                        ipos1 = (k - 1) * ny + j;
                        ipos2 = ipos1 + ny * nz;
                        g.set(1, -1, j, k, buf1.get(1, ipos1));
                        g.set(2, -1, j, k, buf1.get(2, ipos1));
                        g.set(3, -1, j, k, buf1.get(3, ipos1));
                        g.set(4, -1, j, k, buf1.get(4, ipos1));
                        g.set(5, -1, j, k, buf1.get(5, ipos1));
                        g.set(1, 0, j, k, buf1.get(1, ipos2));
                        g.set(2, 0, j, k, buf1.get(2, ipos2));
                        g.set(3, 0, j, k, buf1.get(3, ipos2));
                        g.set(4, 0, j, k, buf1.get(4, ipos2));
                        g.set(5, 0, j, k, buf1.get(5, ipos2));
                    }
                }

            }

            if (south != -1) {
                MPI.iRecv(tmp, 10 * ny * nz, MPI.MPI_ANY_SOURCE(), from_s, request_exch);
            }

            // c---------------------------------------------------------------------
            // c send north
            // c---------------------------------------------------------------------
            if (north != -1) {
                for (k = 1; k <= nz; k++) {
                    for (j = 1; j <= ny; j++) {
                        ipos1 = (k - 1) * ny + j;
                        ipos2 = ipos1 + ny * nz;
                        buf.set(1, ipos1, g.get(1, 2, j, k));
                        buf.set(2, ipos1, g.get(2, 2, j, k));
                        buf.set(3, ipos1, g.get(3, 2, j, k));
                        buf.set(4, ipos1, g.get(4, 2, j, k));
                        buf.set(5, ipos1, g.get(5, 2, j, k));
                        buf.set(1, ipos2, g.get(1, 1, j, k));
                        buf.set(2, ipos2, g.get(2, 1, j, k));
                        buf.set(3, ipos2, g.get(3, 1, j, k));
                        buf.set(4, ipos2, g.get(4, 1, j, k));
                        buf.set(5, ipos2, g.get(5, 1, j, k));
                    }
                }

                MPI.send(buf.getData(), 0, 10 * ny * nz, north, from_s);
            }

            // c---------------------------------------------------------------------
            // c receive from south
            // c---------------------------------------------------------------------
            if (south != -1) {
                MPI.wait(request_exch);
                buf1.setData(tmp);

                for (k = 1; k <= nz; k++) {
                    for (j = 1; j <= ny; j++) {
                        ipos1 = (k - 1) * ny + j;
                        ipos2 = ipos1 + ny * nz;
                        g.set(1, nx + 2, j, k, buf1.get(1, ipos1));
                        g.set(2, nx + 2, j, k, buf1.get(2, ipos1));
                        g.set(3, nx + 2, j, k, buf1.get(3, ipos1));
                        g.set(4, nx + 2, j, k, buf1.get(4, ipos1));
                        g.set(5, nx + 2, j, k, buf1.get(5, ipos1));
                        g.set(1, nx + 1, j, k, buf1.get(1, ipos2));
                        g.set(2, nx + 1, j, k, buf1.get(2, ipos2));
                        g.set(3, nx + 1, j, k, buf1.get(3, ipos2));
                        g.set(4, nx + 1, j, k, buf1.get(4, ipos2));
                        g.set(5, nx + 1, j, k, buf1.get(5, ipos2));
                    }
                }
            }

        } else {

            double tmp[] = new double[buf1.getDataSize()];

            // c---------------------------------------------------------------------
            // c communicate in the east and west directions
            // c---------------------------------------------------------------------
            if (west != -1) {
                MPI.iRecv(tmp, 10 * nx * nz, MPI.MPI_ANY_SOURCE(), from_w, request_exch);
            }

            // c---------------------------------------------------------------------
            // c send east
            // c---------------------------------------------------------------------
            if (east != -1) {
                for (k = 1; k <= nz; k++) {
                    for (i = 1; i <= nx; i++) {
                        ipos1 = (k - 1) * nx + i;
                        ipos2 = ipos1 + nx * nz;
                        buf.set(1, ipos1, g.get(1, i, ny - 1, k));
                        buf.set(2, ipos1, g.get(2, i, ny - 1, k));
                        buf.set(3, ipos1, g.get(3, i, ny - 1, k));
                        buf.set(4, ipos1, g.get(4, i, ny - 1, k));
                        buf.set(5, ipos1, g.get(5, i, ny - 1, k));
                        buf.set(1, ipos2, g.get(1, i, ny, k));
                        buf.set(2, ipos2, g.get(2, i, ny, k));
                        buf.set(3, ipos2, g.get(3, i, ny, k));
                        buf.set(4, ipos2, g.get(4, i, ny, k));
                        buf.set(5, ipos2, g.get(5, i, ny, k));
                    }
                }

                MPI.send(buf.getData(), 0, 10 * nx * nz, east, from_w);
            }

            // c---------------------------------------------------------------------
            // c receive from west
            // c---------------------------------------------------------------------
            if (west != -1) {
                MPI.wait(request_exch);
                buf1.setData(tmp);

                for (k = 1; k <= nz; k++) {
                    for (i = 1; i <= nx; i++) {
                        ipos1 = (k - 1) * nx + i;
                        ipos2 = ipos1 + nx * nz;
                        g.set(1, i, -1, k, buf1.get(1, ipos1));
                        g.set(2, i, -1, k, buf1.get(2, ipos1));
                        g.set(3, i, -1, k, buf1.get(3, ipos1));
                        g.set(4, i, -1, k, buf1.get(4, ipos1));
                        g.set(5, i, -1, k, buf1.get(5, ipos1));
                        g.set(1, i, 0, k, buf1.get(1, ipos2));
                        g.set(2, i, 0, k, buf1.get(2, ipos2));
                        g.set(3, i, 0, k, buf1.get(3, ipos2));
                        g.set(4, i, 0, k, buf1.get(4, ipos2));
                        g.set(5, i, 0, k, buf1.get(5, ipos2));
                    }
                }

            }

            if (east != -1) {
                MPI.iRecv(tmp, 10 * nx * nz, MPI.MPI_ANY_SOURCE(), from_e, request_exch);
            }

            // c---------------------------------------------------------------------
            // c send west
            // c---------------------------------------------------------------------
            if (west != -1) {
                for (k = 1; k <= nz; k++) {
                    for (i = 1; i <= nx; i++) {
                        ipos1 = (k - 1) * nx + i;
                        ipos2 = ipos1 + nx * nz;
                        buf.set(1, ipos1, g.get(1, i, 2, k));
                        buf.set(2, ipos1, g.get(2, i, 2, k));
                        buf.set(3, ipos1, g.get(3, i, 2, k));
                        buf.set(4, ipos1, g.get(4, i, 2, k));
                        buf.set(5, ipos1, g.get(5, i, 2, k));
                        buf.set(1, ipos2, g.get(1, i, 1, k));
                        buf.set(2, ipos2, g.get(2, i, 1, k));
                        buf.set(3, ipos2, g.get(3, i, 1, k));
                        buf.set(4, ipos2, g.get(4, i, 1, k));
                        buf.set(5, ipos2, g.get(5, i, 1, k));
                    }
                }

                MPI.send(buf.getData(), 0, 10 * nx * nz, west, from_e);
            }

            // c---------------------------------------------------------------------
            // c receive from east
            // c---------------------------------------------------------------------
            if (east != -1) {
                MPI.wait(request_exch);
                buf1.setData(tmp);

                for (k = 1; k <= nz; k++) {
                    for (i = 1; i <= nx; i++) {
                        ipos1 = (k - 1) * nx + i;
                        ipos2 = ipos1 + nx * nz;
                        g.set(1, i, ny + 2, k, buf1.get(1, ipos1));
                        g.set(2, i, ny + 2, k, buf1.get(2, ipos1));
                        g.set(3, i, ny + 2, k, buf1.get(3, ipos1));
                        g.set(4, i, ny + 2, k, buf1.get(4, ipos1));
                        g.set(5, i, ny + 2, k, buf1.get(5, ipos1));
                        g.set(1, i, ny + 1, k, buf1.get(1, ipos2));
                        g.set(2, i, ny + 1, k, buf1.get(2, ipos2));
                        g.set(3, i, ny + 1, k, buf1.get(3, ipos2));
                        g.set(4, i, ny + 1, k, buf1.get(4, ipos2));
                        g.set(5, i, ny + 1, k, buf1.get(5, ipos2));
                    }
                }

            }

        }

    }

    // c---------------------------------------------------------------------
    // c compute the right hand side based on exact solution
    // c---------------------------------------------------------------------

    public void exchange_4(Array2Ddouble g, Array2Ddouble h, int ibeg, int ifin1, int jbeg, int jfin1) {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int i, j;
        int ny2;
        Array1Ddouble dum = new Array1Ddouble(1024);

        int msgid1, msgid3;
        // MPI.Request request_exch = new MPI.Request();

        ny2 = ny + 2;

        // c---------------------------------------------------------------------
        // c communicate in the east and west directions
        // c---------------------------------------------------------------------

        // c---------------------------------------------------------------------
        // c receive from east
        // c---------------------------------------------------------------------
        if (jfin1 == ny) {
            double tmp[] = new double[1024];
            MPI.iRecv(tmp, 2 * nx, MPI.MPI_ANY_SOURCE(), from_e, request_exch);
            MPI.wait(request_exch);
            dum.setData(tmp);

            for (i = 1; i <= nx; i++) {
                g.set(i, ny + 1, dum.get(i));
                h.set(i, ny + 1, dum.get(i + nx));
            }

        }

        // c---------------------------------------------------------------------
        // c send west
        // c---------------------------------------------------------------------
        if (jbeg == 1) {
            for (i = 1; i <= nx; i++) {
                dum.set(i, g.get(i, 1));
                dum.set(i + nx, h.get(i, 1));
            }

            MPI.send(dum.getData(), 0, 2 * nx, west, from_e);
        }

        // c---------------------------------------------------------------------
        // c communicate in the south and north directions
        // c---------------------------------------------------------------------

        // c---------------------------------------------------------------------
        // c receive from south
        // c---------------------------------------------------------------------
        if (ifin1 == nx) {
            double tmp[] = new double[1024];
            MPI.iRecv(tmp, 2 * ny2, MPI.MPI_ANY_SOURCE(), from_s, request_exch);
            MPI.wait(request_exch);
            dum.setData(tmp);

            for (j = 0; j <= ny + 1; j++) {
                g.set(nx + 1, j, dum.get(j + 1));
                h.set(nx + 1, j, dum.get(j + ny2 + 1));
            }

        }

        // c---------------------------------------------------------------------
        // c send north
        // c---------------------------------------------------------------------
        if (ibeg == 1) {
            for (j = 0; j <= ny + 1; j++) {
                dum.set(j + 1, g.get(1, j));
                dum.set(j + ny2 + 1, h.get(1, j));
            }

            MPI.send(dum.getData(), 0, 2 * ny2, north, from_s);
        }

        return;
    }

    // c---------------------------------------------------------------------
    // c compute the right hand side based on exact solution
    // c---------------------------------------------------------------------

    public void exchange_5(Array2Ddouble g, int ibeg, int ifin1) {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int k;
        Array1Ddouble dum = new Array1Ddouble(1024);

        // MPI.Request request_exch = new MPI.Request();

        // c---------------------------------------------------------------------
        // c communicate in the south and north directions
        // c---------------------------------------------------------------------

        // c---------------------------------------------------------------------
        // c receive from south
        // c---------------------------------------------------------------------
        if (ifin1 == nx) {
            double[] tmp = new double[1024];
            MPI.iRecv(tmp, nz, MPI.MPI_ANY_SOURCE(), from_s, request_exch);
            MPI.wait(request_exch);
            dum.setData(tmp);

            for (k = 1; k <= nz; k++) {
                g.set(nx + 1, k, dum.get(k));
            }

        }

        // c---------------------------------------------------------------------
        // c send north
        // c---------------------------------------------------------------------
        if (ibeg == 1) {
            for (k = 1; k <= nz; k++) {
                dum.set(k, g.get(1, k));
            }

            MPI.send(dum.getData(), 0, nz, north, from_s);
        }

        return;

    }

    // c---------------------------------------------------------------------
    // c compute the right hand side based on exact solution
    // c---------------------------------------------------------------------

    public void exchange_6(Array2Ddouble g, int jbeg, int jfin1) {

        // c---------------------------------------------------------------------
        // c local parameters
        // c---------------------------------------------------------------------
        int k;
        Array1Ddouble dum = new Array1Ddouble(1024);

        //MPI.Request request_exch = new MPI.Request();

        // c---------------------------------------------------------------------
        // c communicate in the east and west directions
        // c---------------------------------------------------------------------

        // c---------------------------------------------------------------------
        // c receive from east
        // c---------------------------------------------------------------------
        if (jfin1 == ny) {
            double[] tmp = new double[1024];
            MPI.iRecv(tmp, nz, MPI.MPI_ANY_SOURCE(), from_e, request_exch);
            MPI.wait(request_exch);
            dum.setData(tmp);

            for (k = 1; k <= nz; k++) {
                g.set(ny + 1, k, dum.get(k));
            }

        }

        // c---------------------------------------------------------------------
        // c send west
        // c---------------------------------------------------------------------
        if (jbeg == 1) {
            for (k = 1; k <= nz; k++) {
                dum.set(k, g.get(1, k));
            }

            MPI.send(dum.getData(), 0, nz, west, from_e);
        }

        return;

    }

    // c---------------------------------------------------------------------
    // c
    // c compute the solution error
    // c
    // c---------------------------------------------------------------------

    public void error() {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int i, j, k, m;
        int iglob, jglob;
        double tmp;
        Array1Ddouble u000ijk = new Array1Ddouble(5);
        Array1Ddouble dummy = new Array1Ddouble(5);

        for (m = 1; m <= 5; m++) {
            errnm.set(m, 0.0e+00);
            dummy.set(m, 0.0e+00);
        }

        for (k = 2; k <= nz - 1; k++) {
            for (j = jst; j <= jend; j++) {
                jglob = jpt + j;
                for (i = ist; i <= iend; i++) {
                    iglob = ipt + i;
                    exact(iglob, jglob, k, u000ijk, 1);
                    for (m = 1; m <= 5; m++) {
                        tmp = (u000ijk.get(m) - u.get(m, i, j, k));
                        dummy.set(m, dummy.get(m) + tmp * tmp);
                    }
                }
            }
        }

        // c---------------------------------------------------------------------
        // c compute the global sum of individual contributions to dot product.
        // c---------------------------------------------------------------------

        double[] result = new double[5];

        MPI.allReduce_sum(dummy.getData(), result);
        errnm.setData(result);

        for (m = 1; m <= 5; m++) {
            errnm.set(m, Util.sqrt(errnm.get(m) / ((nx0 - 2) * (ny0 - 2) * (nz0 - 2))));
        }

        // c if (id == 0) {
        // c write (*,1002) ( errnm.get(m), m = 1, 5 )
        // c }

        // 1002 format (1x/1x,'RMS-norm of error in soln. to ',;
        // > 'first pde = ',1pe12.5/,;
        // > 1x,'RMS-norm of error in soln. to ',;
        // > 'second pde = ',1pe12.5/,;
        // > 1x,'RMS-norm of error in soln. to ',;
        // > 'third pde = ',1pe12.5/,;
        // > 1x,'RMS-norm of error in soln. to ',;
        // > 'fourth pde = ',1pe12.5/,;
        // > 1x,'RMS-norm of error in soln. to ',;
        // > 'fifth pde = ',1pe12.5);

        return;
    }

    // c---------------------------------------------------------------------
    // c compute the lower triangular part of the jacobian matrix
    // c---------------------------------------------------------------------
    public void jacld(int k) {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int i, j;
        double r43;
        double c1345;
        double c34;
        double tmp1, tmp2, tmp3;

        r43 = (4.0e+00 / 3.0e+00);
        c1345 = c1 * c3 * c4 * c5;
        c34 = c3 * c4;

        for (j = jst; j <= jend; j++) {
            for (i = ist; i <= iend; i++) {

                // c---------------------------------------------------------------------
                // c form the block daigonal
                // c---------------------------------------------------------------------
                tmp1 = 1.0e+00 / u.get(1, i, j, k);
                tmp2 = tmp1 * tmp1;
                tmp3 = tmp1 * tmp2;

                d.set(1, 1, i, j, 1.0e+00 + dt * 2.0e+00 * (tx1 * dx1 + ty1 * dy1 + tz1 * dz1));
                d.set(1, 2, i, j, 0.0e+00);
                d.set(1, 3, i, j, 0.0e+00);
                d.set(1, 4, i, j, 0.0e+00);
                d.set(1, 5, i, j, 0.0e+00);

                d.set(2, 1, i, j, dt * 2.0e+00 * (tx1 * (-r43 * c34 * tmp2 * u.get(2, i, j, k))
                        + ty1 * (-c34 * tmp2 * u.get(2, i, j, k)) + tz1 * (-c34 * tmp2 * u.get(2, i, j, k))));
                d.set(2, 2, i, j,
                        1.0e+00 + dt * 2.0e+00 * (tx1 * r43 * c34 * tmp1 + ty1 * c34 * tmp1 + tz1 * c34 * tmp1)
                                + dt * 2.0e+00 * (tx1 * dx2 + ty1 * dy2 + tz1 * dz2));
                d.set(2, 3, i, j, 0.0e+00);
                d.set(2, 4, i, j, 0.0e+00);
                d.set(2, 5, i, j, 0.0e+00);

                d.set(3, 1, i, j, dt * 2.0e+00 * (tx1 * (-c34 * tmp2 * u.get(3, i, j, k))
                        + ty1 * (-r43 * c34 * tmp2 * u.get(3, i, j, k)) + tz1 * (-c34 * tmp2 * u.get(3, i, j, k))));
                d.set(3, 2, i, j, 0.0e+00);
                d.set(3, 3, i, j,
                        1.0e+00 + dt * 2.0e+00 * (tx1 * c34 * tmp1 + ty1 * r43 * c34 * tmp1 + tz1 * c34 * tmp1)
                                + dt * 2.0e+00 * (tx1 * dx3 + ty1 * dy3 + tz1 * dz3));
                d.set(3, 4, i, j, 0.0e+00);
                d.set(3, 5, i, j, 0.0e+00);

                d.set(4, 1, i, j, dt * 2.0e+00 * (tx1 * (-c34 * tmp2 * u.get(4, i, j, k))
                        + ty1 * (-c34 * tmp2 * u.get(4, i, j, k)) + tz1 * (-r43 * c34 * tmp2 * u.get(4, i, j, k))));
                d.set(4, 2, i, j, 0.0e+00);
                d.set(4, 3, i, j, 0.0e+00);
                d.set(4, 4, i, j,
                        1.0e+00 + dt * 2.0e+00 * (tx1 * c34 * tmp1 + ty1 * c34 * tmp1 + tz1 * r43 * c34 * tmp1)
                                + dt * 2.0e+00 * (tx1 * dx4 + ty1 * dy4 + tz1 * dz4));
                d.set(4, 5, i, j, 0.0e+00);

                d.set(5, 1, i, j,
                        dt * 2.0e+00
                                * (tx1 * (-(r43 * c34 - c1345) * tmp3 * (u.get(2, i, j, k) * u.get(2, i, j, k))
                                        - (c34 - c1345) * tmp3 * (u.get(3, i, j, k) * u.get(3, i, j, k))
                                        - (c34 - c1345) * tmp3 * (u.get(4, i, j, k) * u.get(4, i, j, k))
                                        - (c1345) * tmp2 * u.get(5, i, j, k))
                                        + ty1 * (-(c34 - c1345) * tmp3 * (u.get(2, i, j, k) * u.get(2, i, j, k))
                                                - (r43 * c34 - c1345) * tmp3 * (u.get(3, i, j, k) * u.get(3, i, j, k))
                                                - (c34 - c1345) * tmp3 * (u.get(4, i, j, k) * u.get(4, i, j, k))
                                                - (c1345) * tmp2 * u.get(5, i, j, k))
                                        + tz1 * (-(c34 - c1345) * tmp3 * (u.get(2, i, j, k) * u.get(2, i, j, k))
                                                - (c34 - c1345) * tmp3 * (u.get(3, i, j, k) * u.get(3, i, j, k))
                                                - (r43 * c34 - c1345) * tmp3 * (u.get(4, i, j, k) * u.get(4, i, j, k))
                                                - (c1345) * tmp2 * u.get(5, i, j, k))));
                d.set(5, 2, i, j,
                        dt * 2.0e+00
                                * (tx1 * (r43 * c34 - c1345) * tmp2 * u.get(2, i, j, k)
                                        + ty1 * (c34 - c1345) * tmp2 * u.get(2, i, j, k)
                                        + tz1 * (c34 - c1345) * tmp2 * u.get(2, i, j, k)));
                d.set(5, 3, i, j,
                        dt * 2.0e+00
                                * (tx1 * (c34 - c1345) * tmp2 * u.get(3, i, j, k)
                                        + ty1 * (r43 * c34 - c1345) * tmp2 * u.get(3, i, j, k)
                                        + tz1 * (c34 - c1345) * tmp2 * u.get(3, i, j, k)));
                d.set(5, 4, i, j,
                        dt * 2.0e+00
                                * (tx1 * (c34 - c1345) * tmp2 * u.get(4, i, j, k)
                                        + ty1 * (c34 - c1345) * tmp2 * u.get(4, i, j, k)
                                        + tz1 * (r43 * c34 - c1345) * tmp2 * u.get(4, i, j, k)));
                d.set(5, 5, i, j,
                        1.0e+00 + dt * 2.0e+00 * (tx1 * c1345 * tmp1 + ty1 * c1345 * tmp1 + tz1 * c1345 * tmp1)
                                + dt * 2.0e+00 * (tx1 * dx5 + ty1 * dy5 + tz1 * dz5));

                // c---------------------------------------------------------------------
                // c form the first block sub-diagonal
                // c---------------------------------------------------------------------
                tmp1 = 1.0e+00 / u.get(1, i, j, k - 1);
                tmp2 = tmp1 * tmp1;
                tmp3 = tmp1 * tmp2;

                a.set(1, 1, i, j, -dt * tz1 * dz1);
                a.set(1, 2, i, j, 0.0e+00);
                a.set(1, 3, i, j, 0.0e+00);
                a.set(1, 4, i, j, -dt * tz2);
                a.set(1, 5, i, j, 0.0e+00);

                a.set(2, 1, i, j, -dt * tz2 * (-(u.get(2, i, j, k - 1) * u.get(4, i, j, k - 1)) * tmp2)
                        - dt * tz1 * (-c34 * tmp2 * u.get(2, i, j, k - 1)));
                a.set(2, 2, i, j, -dt * tz2 * (u.get(4, i, j, k - 1) * tmp1) - dt * tz1 * c34 * tmp1 - dt * tz1 * dz2);
                a.set(2, 3, i, j, 0.0e+00);
                a.set(2, 4, i, j, -dt * tz2 * (u.get(2, i, j, k - 1) * tmp1));
                a.set(2, 5, i, j, 0.0e+00);

                a.set(3, 1, i, j, -dt * tz2 * (-(u.get(3, i, j, k - 1) * u.get(4, i, j, k - 1)) * tmp2)
                        - dt * tz1 * (-c34 * tmp2 * u.get(3, i, j, k - 1)));
                a.set(3, 2, i, j, 0.0e+00);
                a.set(3, 3, i, j,
                        -dt * tz2 * (u.get(4, i, j, k - 1) * tmp1) - dt * tz1 * (c34 * tmp1) - dt * tz1 * dz3);
                a.set(3, 4, i, j, -dt * tz2 * (u.get(3, i, j, k - 1) * tmp1));
                a.set(3, 5, i, j, 0.0e+00);

                a.set(4, 1, i, j,
                        -dt * tz2
                                * (-(u.get(4, i, j, k - 1) * tmp1) * (u.get(4, i, j, k - 1) * tmp1) + 0.50e+00 * c2
                                        * ((u.get(2, i, j, k - 1) * u.get(2, i, j, k - 1)
                                                + u.get(3, i, j, k - 1) * u.get(3, i, j, k - 1) + u.get(4, i, j, k - 1)
                                                        * u.get(4, i, j, k - 1))
                                                * tmp2))
                                - dt * tz1 * (-r43 * c34 * tmp2 * u.get(4, i, j, k - 1)));
                a.set(4, 2, i, j, -dt * tz2 * (-c2 * (u.get(2, i, j, k - 1) * tmp1)));
                a.set(4, 3, i, j, -dt * tz2 * (-c2 * (u.get(3, i, j, k - 1) * tmp1)));
                a.set(4, 4, i, j, -dt * tz2 * (2.0e+00 - c2) * (u.get(4, i, j, k - 1) * tmp1)
                        - dt * tz1 * (r43 * c34 * tmp1) - dt * tz1 * dz4);
                a.set(4, 5, i, j, -dt * tz2 * c2);

                a.set(5, 1, i, j, -dt * tz2 * ((c2
                        * (u.get(2, i, j, k - 1) * u.get(2, i, j, k - 1) + u.get(3, i, j, k - 1) * u.get(3, i, j, k - 1)
                                + u.get(4, i, j, k - 1) * u.get(4, i, j, k - 1))
                        * tmp2 - c1 * (u.get(5, i, j, k - 1) * tmp1)) * (u.get(4, i, j, k - 1) * tmp1))
                        - dt * tz1
                                * (-(c34 - c1345) * tmp3 * (u.get(2, i, j, k - 1) * u.get(2, i, j, k - 1))
                                        - (c34 - c1345) * tmp3 * (u.get(3, i, j, k - 1) * u.get(3, i, j, k - 1))
                                        - (r43 * c34 - c1345) * tmp3 * (u.get(4, i, j, k - 1) * u.get(4, i, j, k - 1))
                                        - c1345 * tmp2 * u.get(5, i, j, k - 1)));
                a.set(5, 2, i, j, -dt * tz2 * (-c2 * (u.get(2, i, j, k - 1) * u.get(4, i, j, k - 1)) * tmp2)
                        - dt * tz1 * (c34 - c1345) * tmp2 * u.get(2, i, j, k - 1));
                a.set(5, 3, i, j, -dt * tz2 * (-c2 * (u.get(3, i, j, k - 1) * u.get(4, i, j, k - 1)) * tmp2)
                        - dt * tz1 * (c34 - c1345) * tmp2 * u.get(3, i, j, k - 1));
                a.set(5, 4, i, j,
                        -dt * tz2
                                * (c1 * (u.get(5, i, j, k - 1) * tmp1) - 0.50e+00 * c2
                                        * ((u.get(2, i, j, k - 1) * u.get(2, i, j, k - 1)
                                                + u.get(3, i, j, k - 1) * u.get(3, i, j, k - 1)
                                                + 3.0e+00 * u.get(4, i, j, k - 1) * u.get(4, i, j, k - 1)) * tmp2))
                                - dt * tz1 * (r43 * c34 - c1345) * tmp2 * u.get(4, i, j, k - 1));
                a.set(5, 5, i, j,
                        -dt * tz2 * (c1 * (u.get(4, i, j, k - 1) * tmp1)) - dt * tz1 * c1345 * tmp1 - dt * tz1 * dz5);

                // c---------------------------------------------------------------------
                // c form the second block sub-diagonal
                // c---------------------------------------------------------------------
                tmp1 = 1.0e+00 / u.get(1, i, j - 1, k);
                tmp2 = tmp1 * tmp1;
                tmp3 = tmp1 * tmp2;

                b.set(1, 1, i, j, -dt * ty1 * dy1);
                b.set(1, 2, i, j, 0.0e+00);
                b.set(1, 3, i, j, -dt * ty2);
                b.set(1, 4, i, j, 0.0e+00);
                b.set(1, 5, i, j, 0.0e+00);

                b.set(2, 1, i, j, -dt * ty2 * (-(u.get(2, i, j - 1, k) * u.get(3, i, j - 1, k)) * tmp2)
                        - dt * ty1 * (-c34 * tmp2 * u.get(2, i, j - 1, k)));
                b.set(2, 2, i, j,
                        -dt * ty2 * (u.get(3, i, j - 1, k) * tmp1) - dt * ty1 * (c34 * tmp1) - dt * ty1 * dy2);
                b.set(2, 3, i, j, -dt * ty2 * (u.get(2, i, j - 1, k) * tmp1));
                b.set(2, 4, i, j, 0.0e+00);
                b.set(2, 5, i, j, 0.0e+00);

                b.set(3, 1, i, j,
                        -dt * ty2
                                * (-(u.get(3, i, j - 1, k) * tmp1) * (u.get(3, i, j - 1, k) * tmp1) + 0.50e+00 * c2
                                        * ((u.get(2, i, j - 1, k) * u.get(2, i, j - 1, k)
                                                + u.get(3, i, j - 1, k) * u.get(3, i, j - 1, k)
                                                + u.get(4, i, j - 1, k) * u.get(4, i, j - 1, k)) * tmp2))
                                - dt * ty1 * (-r43 * c34 * tmp2 * u.get(3, i, j - 1, k)));
                b.set(3, 2, i, j, -dt * ty2 * (-c2 * (u.get(2, i, j - 1, k) * tmp1)));
                b.set(3, 3, i, j, -dt * ty2 * ((2.0e+00 - c2) * (u.get(3, i, j - 1, k) * tmp1))
                        - dt * ty1 * (r43 * c34 * tmp1) - dt * ty1 * dy3);
                b.set(3, 4, i, j, -dt * ty2 * (-c2 * (u.get(4, i, j - 1, k) * tmp1)));
                b.set(3, 5, i, j, -dt * ty2 * c2);

                b.set(4, 1, i, j, -dt * ty2 * (-(u.get(3, i, j - 1, k) * u.get(4, i, j - 1, k)) * tmp2)
                        - dt * ty1 * (-c34 * tmp2 * u.get(4, i, j - 1, k)));
                b.set(4, 2, i, j, 0.0e+00);
                b.set(4, 3, i, j, -dt * ty2 * (u.get(4, i, j - 1, k) * tmp1));
                b.set(4, 4, i, j,
                        -dt * ty2 * (u.get(3, i, j - 1, k) * tmp1) - dt * ty1 * (c34 * tmp1) - dt * ty1 * dy4);
                b.set(4, 5, i, j, 0.0e+00);

                b.set(5, 1, i, j, -dt * ty2 * ((c2
                        * (u.get(2, i, j - 1, k) * u.get(2, i, j - 1, k) + u.get(3, i, j - 1, k) * u.get(3, i, j - 1, k)
                                + u.get(4, i, j - 1, k) * u.get(4, i, j - 1, k))
                        * tmp2 - c1 * (u.get(5, i, j - 1, k) * tmp1)) * (u.get(3, i, j - 1, k) * tmp1))
                        - dt * ty1
                                * (-(c34 - c1345) * tmp3 * (u.get(2, i, j - 1, k) * u.get(2, i, j - 1, k))
                                        - (r43 * c34 - c1345) * tmp3 * (u.get(3, i, j - 1, k) * u.get(3, i, j - 1, k))
                                        - (c34 - c1345) * tmp3 * (u.get(4, i, j - 1, k) * u.get(4, i, j - 1, k))
                                        - c1345 * tmp2 * u.get(5, i, j - 1, k)));
                b.set(5, 2, i, j, -dt * ty2 * (-c2 * (u.get(2, i, j - 1, k) * u.get(3, i, j - 1, k)) * tmp2)
                        - dt * ty1 * (c34 - c1345) * tmp2 * u.get(2, i, j - 1, k));
                b.set(5, 3, i, j,
                        -dt * ty2
                                * (c1 * (u.get(5, i, j - 1, k) * tmp1) - 0.50e+00 * c2
                                        * ((u.get(2, i, j - 1, k) * u.get(2, i, j - 1, k)
                                                + 3.0e+00 * u.get(3, i, j - 1, k) * u.get(3, i, j - 1, k)
                                                + u.get(4, i, j - 1, k) * u.get(4, i, j - 1, k)) * tmp2))
                                - dt * ty1 * (r43 * c34 - c1345) * tmp2 * u.get(3, i, j - 1, k));
                b.set(5, 4, i, j, -dt * ty2 * (-c2 * (u.get(3, i, j - 1, k) * u.get(4, i, j - 1, k)) * tmp2)
                        - dt * ty1 * (c34 - c1345) * tmp2 * u.get(4, i, j - 1, k));
                b.set(5, 5, i, j,
                        -dt * ty2 * (c1 * (u.get(3, i, j - 1, k) * tmp1)) - dt * ty1 * c1345 * tmp1 - dt * ty1 * dy5);

                // c---------------------------------------------------------------------
                // c form the third block sub-diagonal
                // c---------------------------------------------------------------------
                tmp1 = 1.0e+00 / u.get(1, i - 1, j, k);
                tmp2 = tmp1 * tmp1;
                tmp3 = tmp1 * tmp2;

                c.set(1, 1, i, j, -dt * tx1 * dx1);
                c.set(1, 2, i, j, -dt * tx2);
                c.set(1, 3, i, j, 0.0e+00);
                c.set(1, 4, i, j, 0.0e+00);
                c.set(1, 5, i, j, 0.0e+00);

                c.set(2, 1, i, j, -dt * tx2 * (-(u.get(2, i - 1, j, k) * tmp1) * (u.get(2, i - 1, j, k) * tmp1) + c2
                        * 0.50e+00
                        * (u.get(2, i - 1, j, k) * u.get(2, i - 1, j, k) + u.get(3, i - 1, j, k) * u.get(3, i - 1, j, k)
                                + u.get(4, i - 1, j, k) * u.get(4, i - 1, j, k))
                        * tmp2) - dt * tx1 * (-r43 * c34 * tmp2 * u.get(2, i - 1, j, k)));
                c.set(2, 2, i, j, -dt * tx2 * ((2.0e+00 - c2) * (u.get(2, i - 1, j, k) * tmp1))
                        - dt * tx1 * (r43 * c34 * tmp1) - dt * tx1 * dx2);
                c.set(2, 3, i, j, -dt * tx2 * (-c2 * (u.get(3, i - 1, j, k) * tmp1)));
                c.set(2, 4, i, j, -dt * tx2 * (-c2 * (u.get(4, i - 1, j, k) * tmp1)));
                c.set(2, 5, i, j, -dt * tx2 * c2);

                c.set(3, 1, i, j, -dt * tx2 * (-(u.get(2, i - 1, j, k) * u.get(3, i - 1, j, k)) * tmp2)
                        - dt * tx1 * (-c34 * tmp2 * u.get(3, i - 1, j, k)));
                c.set(3, 2, i, j, -dt * tx2 * (u.get(3, i - 1, j, k) * tmp1));
                c.set(3, 3, i, j,
                        -dt * tx2 * (u.get(2, i - 1, j, k) * tmp1) - dt * tx1 * (c34 * tmp1) - dt * tx1 * dx3);
                c.set(3, 4, i, j, 0.0e+00);
                c.set(3, 5, i, j, 0.0e+00);

                c.set(4, 1, i, j, -dt * tx2 * (-(u.get(2, i - 1, j, k) * u.get(4, i - 1, j, k)) * tmp2)
                        - dt * tx1 * (-c34 * tmp2 * u.get(4, i - 1, j, k)));
                c.set(4, 2, i, j, -dt * tx2 * (u.get(4, i - 1, j, k) * tmp1));
                c.set(4, 3, i, j, 0.0e+00);
                c.set(4, 4, i, j,
                        -dt * tx2 * (u.get(2, i - 1, j, k) * tmp1) - dt * tx1 * (c34 * tmp1) - dt * tx1 * dx4);
                c.set(4, 5, i, j, 0.0e+00);

                c.set(5, 1, i, j, -dt * tx2 * ((c2
                        * (u.get(2, i - 1, j, k) * u.get(2, i - 1, j, k) + u.get(3, i - 1, j, k) * u.get(3, i - 1, j, k)
                                + u.get(4, i - 1, j, k) * u.get(4, i - 1, j, k))
                        * tmp2 - c1 * (u.get(5, i - 1, j, k) * tmp1)) * (u.get(2, i - 1, j, k) * tmp1))
                        - dt * tx1
                                * (-(r43 * c34 - c1345) * tmp3 * (u.get(2, i - 1, j, k) * u.get(2, i - 1, j, k))
                                        - (c34 - c1345) * tmp3 * (u.get(3, i - 1, j, k) * u.get(3, i - 1, j, k))
                                        - (c34 - c1345) * tmp3 * (u.get(4, i - 1, j, k) * u.get(4, i - 1, j, k))
                                        - c1345 * tmp2 * u.get(5, i - 1, j, k)));
                c.set(5, 2, i, j,
                        -dt * tx2
                                * (c1 * (u.get(5, i - 1, j, k) * tmp1) - 0.50e+00 * c2
                                        * ((3.0e+00 * u.get(2, i - 1, j, k) * u.get(2, i - 1, j, k)
                                                + u.get(3, i - 1, j, k) * u.get(3, i - 1, j, k)
                                                + u.get(4, i - 1, j, k) * u.get(4, i - 1, j, k)) * tmp2))
                                - dt * tx1 * (r43 * c34 - c1345) * tmp2 * u.get(2, i - 1, j, k));
                c.set(5, 3, i, j, -dt * tx2 * (-c2 * (u.get(3, i - 1, j, k) * u.get(2, i - 1, j, k)) * tmp2)
                        - dt * tx1 * (c34 - c1345) * tmp2 * u.get(3, i - 1, j, k));
                c.set(5, 4, i, j, -dt * tx2 * (-c2 * (u.get(4, i - 1, j, k) * u.get(2, i - 1, j, k)) * tmp2)
                        - dt * tx1 * (c34 - c1345) * tmp2 * u.get(4, i - 1, j, k));
                c.set(5, 5, i, j,
                        -dt * tx2 * (c1 * (u.get(2, i - 1, j, k) * tmp1)) - dt * tx1 * c1345 * tmp1 - dt * tx1 * dx5);

            }
        }

    }

    // c---------------------------------------------------------------------
    // c compute the upper triangular part of the jacobian matrix
    // c---------------------------------------------------------------------
    public void jacu(int k) {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int i, j;
        double r43;
        double c1345;
        double c34;
        double tmp1, tmp2, tmp3;

        r43 = (4.0e+00 / 3.0e+00);
        c1345 = c1 * c3 * c4 * c5;
        c34 = c3 * c4;

        for (j = jst; j <= jend; j++) {
            for (i = ist; i <= iend; i++) {

                // c---------------------------------------------------------------------
                // c form the block daigonal
                // c---------------------------------------------------------------------
                tmp1 = 1.0e+00 / u.get(1, i, j, k);
                tmp2 = tmp1 * tmp1;
                tmp3 = tmp1 * tmp2;

                d.set(1, 1, i, j, 1.0e+00 + dt * 2.0e+00 * (tx1 * dx1 + ty1 * dy1 + tz1 * dz1));
                d.set(1, 2, i, j, 0.0e+00);
                d.set(1, 3, i, j, 0.0e+00);
                d.set(1, 4, i, j, 0.0e+00);
                d.set(1, 5, i, j, 0.0e+00);

                d.set(2, 1, i, j, dt * 2.0e+00 * (tx1 * (-r43 * c34 * tmp2 * u.get(2, i, j, k))
                        + ty1 * (-c34 * tmp2 * u.get(2, i, j, k)) + tz1 * (-c34 * tmp2 * u.get(2, i, j, k))));
                d.set(2, 2, i, j,
                        1.0e+00 + dt * 2.0e+00 * (tx1 * r43 * c34 * tmp1 + ty1 * c34 * tmp1 + tz1 * c34 * tmp1)
                                + dt * 2.0e+00 * (tx1 * dx2 + ty1 * dy2 + tz1 * dz2));
                d.set(2, 3, i, j, 0.0e+00);
                d.set(2, 4, i, j, 0.0e+00);
                d.set(2, 5, i, j, 0.0e+00);

                d.set(3, 1, i, j, dt * 2.0e+00 * (tx1 * (-c34 * tmp2 * u.get(3, i, j, k))
                        + ty1 * (-r43 * c34 * tmp2 * u.get(3, i, j, k)) + tz1 * (-c34 * tmp2 * u.get(3, i, j, k))));
                d.set(3, 2, i, j, 0.0e+00);
                d.set(3, 3, i, j,
                        1.0e+00 + dt * 2.0e+00 * (tx1 * c34 * tmp1 + ty1 * r43 * c34 * tmp1 + tz1 * c34 * tmp1)
                                + dt * 2.0e+00 * (tx1 * dx3 + ty1 * dy3 + tz1 * dz3));
                d.set(3, 4, i, j, 0.0e+00);
                d.set(3, 5, i, j, 0.0e+00);

                d.set(4, 1, i, j, dt * 2.0e+00 * (tx1 * (-c34 * tmp2 * u.get(4, i, j, k))
                        + ty1 * (-c34 * tmp2 * u.get(4, i, j, k)) + tz1 * (-r43 * c34 * tmp2 * u.get(4, i, j, k))));
                d.set(4, 2, i, j, 0.0e+00);
                d.set(4, 3, i, j, 0.0e+00);
                d.set(4, 4, i, j,
                        1.0e+00 + dt * 2.0e+00 * (tx1 * c34 * tmp1 + ty1 * c34 * tmp1 + tz1 * r43 * c34 * tmp1)
                                + dt * 2.0e+00 * (tx1 * dx4 + ty1 * dy4 + tz1 * dz4));
                d.set(4, 5, i, j, 0.0e+00);

                d.set(5, 1, i, j,
                        dt * 2.0e+00
                                * (tx1 * (-(r43 * c34 - c1345) * tmp3 * (u.get(2, i, j, k) * u.get(2, i, j, k))
                                        - (c34 - c1345) * tmp3 * (u.get(3, i, j, k) * u.get(3, i, j, k))
                                        - (c34 - c1345) * tmp3 * (u.get(4, i, j, k) * u.get(4, i, j, k))
                                        - (c1345) * tmp2 * u.get(5, i, j, k))
                                        + ty1 * (-(c34 - c1345) * tmp3 * (u.get(2, i, j, k) * u.get(2, i, j, k))
                                                - (r43 * c34 - c1345) * tmp3 * (u.get(3, i, j, k) * u.get(3, i, j, k))
                                                - (c34 - c1345) * tmp3 * (u.get(4, i, j, k) * u.get(4, i, j, k))
                                                - (c1345) * tmp2 * u.get(5, i, j, k))
                                        + tz1 * (-(c34 - c1345) * tmp3 * (u.get(2, i, j, k) * u.get(2, i, j, k))
                                                - (c34 - c1345) * tmp3 * (u.get(3, i, j, k) * u.get(3, i, j, k))
                                                - (r43 * c34 - c1345) * tmp3 * (u.get(4, i, j, k) * u.get(4, i, j, k))
                                                - (c1345) * tmp2 * u.get(5, i, j, k))));
                d.set(5, 2, i, j,
                        dt * 2.0e+00
                                * (tx1 * (r43 * c34 - c1345) * tmp2 * u.get(2, i, j, k)
                                        + ty1 * (c34 - c1345) * tmp2 * u.get(2, i, j, k)
                                        + tz1 * (c34 - c1345) * tmp2 * u.get(2, i, j, k)));
                d.set(5, 3, i, j,
                        dt * 2.0e+00
                                * (tx1 * (c34 - c1345) * tmp2 * u.get(3, i, j, k)
                                        + ty1 * (r43 * c34 - c1345) * tmp2 * u.get(3, i, j, k)
                                        + tz1 * (c34 - c1345) * tmp2 * u.get(3, i, j, k)));
                d.set(5, 4, i, j,
                        dt * 2.0e+00
                                * (tx1 * (c34 - c1345) * tmp2 * u.get(4, i, j, k)
                                        + ty1 * (c34 - c1345) * tmp2 * u.get(4, i, j, k)
                                        + tz1 * (r43 * c34 - c1345) * tmp2 * u.get(4, i, j, k)));
                d.set(5, 5, i, j,
                        1.0e+00 + dt * 2.0e+00 * (tx1 * c1345 * tmp1 + ty1 * c1345 * tmp1 + tz1 * c1345 * tmp1)
                                + dt * 2.0e+00 * (tx1 * dx5 + ty1 * dy5 + tz1 * dz5));

                // c---------------------------------------------------------------------
                // c form the first block sub-diagonal
                // c---------------------------------------------------------------------
                tmp1 = 1.0e+00 / u.get(1, i + 1, j, k);
                tmp2 = tmp1 * tmp1;
                tmp3 = tmp1 * tmp2;

                a.set(1, 1, i, j, -dt * tx1 * dx1);
                a.set(1, 2, i, j, dt * tx2);
                a.set(1, 3, i, j, 0.0e+00);
                a.set(1, 4, i, j, 0.0e+00);
                a.set(1, 5, i, j, 0.0e+00);

                a.set(2, 1, i, j, dt * tx2 * (-(u.get(2, i + 1, j, k) * tmp1) * (u.get(2, i + 1, j, k) * tmp1) + c2
                        * 0.50e+00
                        * (u.get(2, i + 1, j, k) * u.get(2, i + 1, j, k) + u.get(3, i + 1, j, k) * u.get(3, i + 1, j, k)
                                + u.get(4, i + 1, j, k) * u.get(4, i + 1, j, k))
                        * tmp2) - dt * tx1 * (-r43 * c34 * tmp2 * u.get(2, i + 1, j, k)));
                a.set(2, 2, i, j, dt * tx2 * ((2.0e+00 - c2) * (u.get(2, i + 1, j, k) * tmp1))
                        - dt * tx1 * (r43 * c34 * tmp1) - dt * tx1 * dx2);
                a.set(2, 3, i, j, dt * tx2 * (-c2 * (u.get(3, i + 1, j, k) * tmp1)));
                a.set(2, 4, i, j, dt * tx2 * (-c2 * (u.get(4, i + 1, j, k) * tmp1)));
                a.set(2, 5, i, j, dt * tx2 * c2);

                a.set(3, 1, i, j, dt * tx2 * (-(u.get(2, i + 1, j, k) * u.get(3, i + 1, j, k)) * tmp2)
                        - dt * tx1 * (-c34 * tmp2 * u.get(3, i + 1, j, k)));
                a.set(3, 2, i, j, dt * tx2 * (u.get(3, i + 1, j, k) * tmp1));
                a.set(3, 3, i, j, dt * tx2 * (u.get(2, i + 1, j, k) * tmp1) - dt * tx1 * (c34 * tmp1) - dt * tx1 * dx3);
                a.set(3, 4, i, j, 0.0e+00);
                a.set(3, 5, i, j, 0.0e+00);

                a.set(4, 1, i, j, dt * tx2 * (-(u.get(2, i + 1, j, k) * u.get(4, i + 1, j, k)) * tmp2)
                        - dt * tx1 * (-c34 * tmp2 * u.get(4, i + 1, j, k)));
                a.set(4, 2, i, j, dt * tx2 * (u.get(4, i + 1, j, k) * tmp1));
                a.set(4, 3, i, j, 0.0e+00);
                a.set(4, 4, i, j, dt * tx2 * (u.get(2, i + 1, j, k) * tmp1) - dt * tx1 * (c34 * tmp1) - dt * tx1 * dx4);
                a.set(4, 5, i, j, 0.0e+00);

                a.set(5, 1, i, j, dt * tx2 * ((c2
                        * (u.get(2, i + 1, j, k) * u.get(2, i + 1, j, k) + u.get(3, i + 1, j, k) * u.get(3, i + 1, j, k)
                                + u.get(4, i + 1, j, k) * u.get(4, i + 1, j, k))
                        * tmp2 - c1 * (u.get(5, i + 1, j, k) * tmp1)) * (u.get(2, i + 1, j, k) * tmp1))
                        - dt * tx1
                                * (-(r43 * c34 - c1345) * tmp3 * (u.get(2, i + 1, j, k) * u.get(2, i + 1, j, k))
                                        - (c34 - c1345) * tmp3 * (u.get(3, i + 1, j, k) * u.get(3, i + 1, j, k))
                                        - (c34 - c1345) * tmp3 * (u.get(4, i + 1, j, k) * u.get(4, i + 1, j, k))
                                        - c1345 * tmp2 * u.get(5, i + 1, j, k)));
                a.set(5, 2, i, j,
                        dt * tx2 * (c1 * (u.get(5, i + 1, j, k) * tmp1) - 0.50e+00 * c2
                                * ((3.0e+00 * u.get(2, i + 1, j, k) * u.get(2, i + 1, j, k)
                                        + u.get(3, i + 1, j, k) * u.get(3, i + 1, j, k)
                                        + u.get(4, i + 1, j, k) * u.get(4, i + 1, j, k)) * tmp2))
                                - dt * tx1 * (r43 * c34 - c1345) * tmp2 * u.get(2, i + 1, j, k));
                a.set(5, 3, i, j, dt * tx2 * (-c2 * (u.get(3, i + 1, j, k) * u.get(2, i + 1, j, k)) * tmp2)
                        - dt * tx1 * (c34 - c1345) * tmp2 * u.get(3, i + 1, j, k));
                a.set(5, 4, i, j, dt * tx2 * (-c2 * (u.get(4, i + 1, j, k) * u.get(2, i + 1, j, k)) * tmp2)
                        - dt * tx1 * (c34 - c1345) * tmp2 * u.get(4, i + 1, j, k));
                a.set(5, 5, i, j,
                        dt * tx2 * (c1 * (u.get(2, i + 1, j, k) * tmp1)) - dt * tx1 * c1345 * tmp1 - dt * tx1 * dx5);

                // c---------------------------------------------------------------------
                // c form the second block sub-diagonal
                // c---------------------------------------------------------------------
                tmp1 = 1.0e+00 / u.get(1, i, j + 1, k);
                tmp2 = tmp1 * tmp1;
                tmp3 = tmp1 * tmp2;

                b.set(1, 1, i, j, -dt * ty1 * dy1);
                b.set(1, 2, i, j, 0.0e+00);
                b.set(1, 3, i, j, dt * ty2);
                b.set(1, 4, i, j, 0.0e+00);
                b.set(1, 5, i, j, 0.0e+00);

                b.set(2, 1, i, j, dt * ty2 * (-(u.get(2, i, j + 1, k) * u.get(3, i, j + 1, k)) * tmp2)
                        - dt * ty1 * (-c34 * tmp2 * u.get(2, i, j + 1, k)));
                b.set(2, 2, i, j, dt * ty2 * (u.get(3, i, j + 1, k) * tmp1) - dt * ty1 * (c34 * tmp1) - dt * ty1 * dy2);
                b.set(2, 3, i, j, dt * ty2 * (u.get(2, i, j + 1, k) * tmp1));
                b.set(2, 4, i, j, 0.0e+00);
                b.set(2, 5, i, j, 0.0e+00);

                b.set(3, 1, i, j,
                        dt * ty2 * (-(u.get(3, i, j + 1, k) * tmp1) * (u.get(3, i, j + 1, k) * tmp1) + 0.50e+00 * c2
                                * ((u.get(2, i, j + 1, k) * u.get(2, i, j + 1, k)
                                        + u.get(3, i, j + 1, k) * u.get(3, i, j + 1, k)
                                        + u.get(4, i, j + 1, k) * u.get(4, i, j + 1, k)) * tmp2))
                                - dt * ty1 * (-r43 * c34 * tmp2 * u.get(3, i, j + 1, k)));
                b.set(3, 2, i, j, dt * ty2 * (-c2 * (u.get(2, i, j + 1, k) * tmp1)));
                b.set(3, 3, i, j, dt * ty2 * ((2.0e+00 - c2) * (u.get(3, i, j + 1, k) * tmp1))
                        - dt * ty1 * (r43 * c34 * tmp1) - dt * ty1 * dy3);
                b.set(3, 4, i, j, dt * ty2 * (-c2 * (u.get(4, i, j + 1, k) * tmp1)));
                b.set(3, 5, i, j, dt * ty2 * c2);

                b.set(4, 1, i, j, dt * ty2 * (-(u.get(3, i, j + 1, k) * u.get(4, i, j + 1, k)) * tmp2)
                        - dt * ty1 * (-c34 * tmp2 * u.get(4, i, j + 1, k)));
                b.set(4, 2, i, j, 0.0e+00);
                b.set(4, 3, i, j, dt * ty2 * (u.get(4, i, j + 1, k) * tmp1));
                b.set(4, 4, i, j, dt * ty2 * (u.get(3, i, j + 1, k) * tmp1) - dt * ty1 * (c34 * tmp1) - dt * ty1 * dy4);
                b.set(4, 5, i, j, 0.0e+00);

                b.set(5, 1, i, j, dt * ty2 * ((c2
                        * (u.get(2, i, j + 1, k) * u.get(2, i, j + 1, k) + u.get(3, i, j + 1, k) * u.get(3, i, j + 1, k)
                                + u.get(4, i, j + 1, k) * u.get(4, i, j + 1, k))
                        * tmp2 - c1 * (u.get(5, i, j + 1, k) * tmp1)) * (u.get(3, i, j + 1, k) * tmp1))
                        - dt * ty1
                                * (-(c34 - c1345) * tmp3 * (u.get(2, i, j + 1, k) * u.get(2, i, j + 1, k))
                                        - (r43 * c34 - c1345) * tmp3 * (u.get(3, i, j + 1, k) * u.get(3, i, j + 1, k))
                                        - (c34 - c1345) * tmp3 * (u.get(4, i, j + 1, k) * u.get(4, i, j + 1, k))
                                        - c1345 * tmp2 * u.get(5, i, j + 1, k)));
                b.set(5, 2, i, j, dt * ty2 * (-c2 * (u.get(2, i, j + 1, k) * u.get(3, i, j + 1, k)) * tmp2)
                        - dt * ty1 * (c34 - c1345) * tmp2 * u.get(2, i, j + 1, k));
                b.set(5, 3, i, j,
                        dt * ty2 * (c1 * (u.get(5, i, j + 1, k) * tmp1) - 0.50e+00 * c2
                                * ((u.get(2, i, j + 1, k) * u.get(2, i, j + 1, k)
                                        + 3.0e+00 * u.get(3, i, j + 1, k) * u.get(3, i, j + 1, k)
                                        + u.get(4, i, j + 1, k) * u.get(4, i, j + 1, k)) * tmp2))
                                - dt * ty1 * (r43 * c34 - c1345) * tmp2 * u.get(3, i, j + 1, k));
                b.set(5, 4, i, j, dt * ty2 * (-c2 * (u.get(3, i, j + 1, k) * u.get(4, i, j + 1, k)) * tmp2)
                        - dt * ty1 * (c34 - c1345) * tmp2 * u.get(4, i, j + 1, k));
                b.set(5, 5, i, j,
                        dt * ty2 * (c1 * (u.get(3, i, j + 1, k) * tmp1)) - dt * ty1 * c1345 * tmp1 - dt * ty1 * dy5);

                // c---------------------------------------------------------------------
                // c form the third block sub-diagonal
                // c---------------------------------------------------------------------
                tmp1 = 1.0e+00 / u.get(1, i, j, k + 1);
                tmp2 = tmp1 * tmp1;
                tmp3 = tmp1 * tmp2;

                c.set(1, 1, i, j, -dt * tz1 * dz1);
                c.set(1, 2, i, j, 0.0e+00);
                c.set(1, 3, i, j, 0.0e+00);
                c.set(1, 4, i, j, dt * tz2);
                c.set(1, 5, i, j, 0.0e+00);

                c.set(2, 1, i, j, dt * tz2 * (-(u.get(2, i, j, k + 1) * u.get(4, i, j, k + 1)) * tmp2)
                        - dt * tz1 * (-c34 * tmp2 * u.get(2, i, j, k + 1)));
                c.set(2, 2, i, j, dt * tz2 * (u.get(4, i, j, k + 1) * tmp1) - dt * tz1 * c34 * tmp1 - dt * tz1 * dz2);
                c.set(2, 3, i, j, 0.0e+00);
                c.set(2, 4, i, j, dt * tz2 * (u.get(2, i, j, k + 1) * tmp1));
                c.set(2, 5, i, j, 0.0e+00);

                c.set(3, 1, i, j, dt * tz2 * (-(u.get(3, i, j, k + 1) * u.get(4, i, j, k + 1)) * tmp2)
                        - dt * tz1 * (-c34 * tmp2 * u.get(3, i, j, k + 1)));
                c.set(3, 2, i, j, 0.0e+00);
                c.set(3, 3, i, j, dt * tz2 * (u.get(4, i, j, k + 1) * tmp1) - dt * tz1 * (c34 * tmp1) - dt * tz1 * dz3);
                c.set(3, 4, i, j, dt * tz2 * (u.get(3, i, j, k + 1) * tmp1));
                c.set(3, 5, i, j, 0.0e+00);

                c.set(4, 1, i, j,
                        dt * tz2 * (-(u.get(4, i, j, k + 1) * tmp1) * (u.get(4, i, j, k + 1) * tmp1) + 0.50e+00 * c2
                                * ((u.get(2, i, j, k + 1) * u.get(2, i, j, k + 1)
                                        + u.get(3, i, j, k + 1) * u.get(3, i, j, k + 1)
                                        + u.get(4, i, j, k + 1) * u.get(4, i, j, k + 1)) * tmp2))
                                - dt * tz1 * (-r43 * c34 * tmp2 * u.get(4, i, j, k + 1)));
                c.set(4, 2, i, j, dt * tz2 * (-c2 * (u.get(2, i, j, k + 1) * tmp1)));
                c.set(4, 3, i, j, dt * tz2 * (-c2 * (u.get(3, i, j, k + 1) * tmp1)));
                c.set(4, 4, i, j, dt * tz2 * (2.0e+00 - c2) * (u.get(4, i, j, k + 1) * tmp1)
                        - dt * tz1 * (r43 * c34 * tmp1) - dt * tz1 * dz4);
                c.set(4, 5, i, j, dt * tz2 * c2);

                c.set(5, 1, i, j, dt * tz2 * ((c2
                        * (u.get(2, i, j, k + 1) * u.get(2, i, j, k + 1) + u.get(3, i, j, k + 1) * u.get(3, i, j, k + 1)
                                + u.get(4, i, j, k + 1) * u.get(4, i, j, k + 1))
                        * tmp2 - c1 * (u.get(5, i, j, k + 1) * tmp1)) * (u.get(4, i, j, k + 1) * tmp1))
                        - dt * tz1
                                * (-(c34 - c1345) * tmp3 * (u.get(2, i, j, k + 1) * u.get(2, i, j, k + 1))
                                        - (c34 - c1345) * tmp3 * (u.get(3, i, j, k + 1) * u.get(3, i, j, k + 1))
                                        - (r43 * c34 - c1345) * tmp3 * (u.get(4, i, j, k + 1) * u.get(4, i, j, k + 1))
                                        - c1345 * tmp2 * u.get(5, i, j, k + 1)));
                c.set(5, 2, i, j, dt * tz2 * (-c2 * (u.get(2, i, j, k + 1) * u.get(4, i, j, k + 1)) * tmp2)
                        - dt * tz1 * (c34 - c1345) * tmp2 * u.get(2, i, j, k + 1));
                c.set(5, 3, i, j, dt * tz2 * (-c2 * (u.get(3, i, j, k + 1) * u.get(4, i, j, k + 1)) * tmp2)
                        - dt * tz1 * (c34 - c1345) * tmp2 * u.get(3, i, j, k + 1));
                c.set(5, 4, i, j,
                        dt * tz2 * (c1 * (u.get(5, i, j, k + 1) * tmp1) - 0.50e+00 * c2
                                * ((u.get(2, i, j, k + 1) * u.get(2, i, j, k + 1)
                                        + u.get(3, i, j, k + 1) * u.get(3, i, j, k + 1)
                                        + 3.0e+00 * u.get(4, i, j, k + 1) * u.get(4, i, j, k + 1)) * tmp2))
                                - dt * tz1 * (r43 * c34 - c1345) * tmp2 * u.get(4, i, j, k + 1));
                c.set(5, 5, i, j,
                        dt * tz2 * (c1 * (u.get(4, i, j, k + 1) * tmp1)) - dt * tz1 * c1345 * tmp1 - dt * tz1 * dz5);

            }
        }

    }

    // c---------------------------------------------------------------------
    // c to compute the l2-norm of vector v.
    // c---------------------------------------------------------------------
    public void l2norm(int ldx, int ldy, int ldz, int nx0, int ny0, int nz0, int ist, int iend, int jst, int jend,
            Array4Ddouble v, Array1Ddouble sum) {

        // Util.printer
        // .p("ldx ").p(ldx).ln()
        // .p("ldy ").p(ldy).ln()
        // .p("nx0 ").p(nx0).ln()
        // .p("ny0 ").p(ny0).ln()
        // .p("nz0 ").p(nz0).ln()
        // .p("ist ").p(ist).ln()
        // .p("iend ").p(iend).ln()
        // .p("jst ").p(jst).ln()
        // .p("jend ").p(jend).ln();

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int i, j, k, m;
        double[] dummy = new double[5];

        for (m = 1; m <= 5; m++) {
            dummy[m - 1] = 0.0e+00;
        }

        for (k = 2; k <= nz0 - 1; k++) {
            for (j = jst; j <= jend; j++) {
                for (i = ist; i <= iend; i++) {
                    for (m = 1; m <= 5; m++) {
                        dummy[m - 1] = dummy[m - 1] + v.get(m, i, j, k) * v.get(m, i, j, k);
                    }
                }
            }
        }

        // c---------------------------------------------------------------------
        // c compute the global sum of individual contributions to dot product.
        // c---------------------------------------------------------------------

        // for (m = 0; m < 5; m++) {
        // Util.printer.p("[").p(id).p("] dummy[").p(m).p("] =
        // ").p(dummy[m]).ln();
        // }

        double[] tmp = new double[5];
        MPI.allReduce_sum(dummy, tmp);

        for (m = 1; m <= 5; m++) {
            tmp[m - 1] = Util.sqrt(tmp[m - 1] / ((nx0 - 2) * (ny0 - 2) * (nz0 - 2)));
            // if (id == 0) {
            // Util.printer.p("sum[").p(m - 1).p("] = ").e(tmp[m-1]).ln();
            // }
        }

        sum.setData(tmp);
    }

    public void pintgr() {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int i, j, k;
        int ibeg, ifin, ifin1;
        int jbeg, jfin, jfin1;
        int iglob, iglob1, iglob2;
        int jglob, jglob1, jglob2;
        int ind1, ind2;
        Array2Ddouble phi1 = new Array2Ddouble(0, isiz2 + 1, 0, isiz3 + 1);
        Array2Ddouble phi2 = new Array2Ddouble(0, isiz2 + 1, 0, isiz3 + 1);
        double frc1, frc2, frc3;
        double dummy;

        // c---------------------------------------------------------------------
        // c set up the sub-domains for intation in each processor
        // c---------------------------------------------------------------------
        ibeg = nx + 1;
        ifin = 0;
        iglob1 = ipt + 1;
        iglob2 = ipt + nx;
        if (iglob1 >= ii1 && iglob2 < ii2 + nx)
            ibeg = 1;
        if (iglob1 > ii1 - nx && iglob2 <= ii2)
            ifin = nx;
        if (ii1 >= iglob1 && ii1 <= iglob2)
            ibeg = ii1 - ipt;
        if (ii2 >= iglob1 && ii2 <= iglob2)
            ifin = ii2 - ipt;
        jbeg = ny + 1;
        jfin = 0;
        jglob1 = jpt + 1;
        jglob2 = jpt + ny;
        if (jglob1 >= ji1 && jglob2 < ji2 + ny)
            jbeg = 1;
        if (jglob1 > ji1 - ny && jglob2 <= ji2)
            jfin = ny;
        if (ji1 >= jglob1 && ji1 <= jglob2)
            jbeg = ji1 - jpt;
        if (ji2 >= jglob1 && ji2 <= jglob2)
            jfin = ji2 - jpt;
        ifin1 = ifin;
        jfin1 = jfin;
        if (ipt + ifin1 == ii2)
            ifin1 = ifin - 1;
        if (jpt + jfin1 == ji2)
            jfin1 = jfin - 1;

        // c---------------------------------------------------------------------
        // c initialize
        // c---------------------------------------------------------------------
        for (i = 0; i <= isiz2 + 1; i++) {
            for (k = 0; k <= isiz3 + 1; k++) {
                phi1.set(i, k, 0);
                phi2.set(i, k, 0);
            }
        }

        for (j = jbeg; j <= jfin; j++) {
            jglob = jpt + j;
            for (i = ibeg; i <= ifin; i++) {
                iglob = ipt + i;

                k = ki1;

                phi1.set(i, j,
                        c2 * (u.get(5, i, j, k) - 0.50e+00 * (u.get(2, i, j, k) * u.get(2, i, j, k)
                                + u.get(3, i, j, k) * u.get(3, i, j, k) + u.get(4, i, j, k) * u.get(4, i, j, k))
                                / u.get(1, i, j, k)));

                k = ki2;

                phi2.set(i, j,
                        c2 * (u.get(5, i, j, k) - 0.50e+00 * (u.get(2, i, j, k) * u.get(2, i, j, k)
                                + u.get(3, i, j, k) * u.get(3, i, j, k) + u.get(4, i, j, k) * u.get(4, i, j, k))
                                / u.get(1, i, j, k)));
            }
        }

        // c---------------------------------------------------------------------
        // c communicate in i and j directions
        // c---------------------------------------------------------------------
        exchange_4(phi1, phi2, ibeg, ifin1, jbeg, jfin1);

        frc1 = 0.0e+00;

        for (j = jbeg; j <= jfin1; j++) {
            for (i = ibeg; i <= ifin1; i++) {
                frc1 = frc1 + (phi1.get(i, j) + phi1.get(i + 1, j) + phi1.get(i, j + 1) + phi1.get(i + 1, j + 1)
                        + phi2.get(i, j) + phi2.get(i + 1, j) + phi2.get(i, j + 1) + phi2.get(i + 1, j + 1));
            }
        }

        // c---------------------------------------------------------------------
        // c compute the global sum of individual contributions to frc1
        // c---------------------------------------------------------------------
        dummy = frc1;
        frc1 = MPI.allReduce_sum(dummy);

        frc1 = dxi * deta * frc1;

        // c---------------------------------------------------------------------
        // c initialize
        // c---------------------------------------------------------------------
        for (i = 0; i <= isiz2 + 1; i++) {
            for (k = 0; k <= isiz3 + 1; k++) {
                phi1.set(i, k, 0);
                phi2.set(i, k, 0);
            }
        }
        jglob = jpt + jbeg;
        ind1 = 0;
        if (jglob == ji1) {
            ind1 = 1;
            for (k = ki1; k <= ki2; k++) {
                for (i = ibeg; i <= ifin; i++) {
                    iglob = ipt + i;
                    phi1.set(i, k, c2 * (u.get(5, i, jbeg, k) - 0.50e+00 * (u.get(2, i, jbeg, k) * u.get(2, i, jbeg, k)
                            + u.get(3, i, jbeg, k) * u.get(3, i, jbeg, k) + u.get(4, i, jbeg, k) * u.get(4, i, jbeg, k))
                            / u.get(1, i, jbeg, k)));
                }
            }
        }

        jglob = jpt + jfin;
        ind2 = 0;
        if (jglob == ji2) {
            ind2 = 1;
            for (k = ki1; k <= ki2; k++) {
                for (i = ibeg; i <= ifin; i++) {
                    iglob = ipt + i;
                    phi2.set(i, k, c2 * (u.get(5, i, jfin, k) - 0.50e+00 * (u.get(2, i, jfin, k) * u.get(2, i, jfin, k)
                            + u.get(3, i, jfin, k) * u.get(3, i, jfin, k) + u.get(4, i, jfin, k) * u.get(4, i, jfin, k))
                            / u.get(1, i, jfin, k)));
                }
            }
        }

        // c---------------------------------------------------------------------
        // c communicate in i direction
        // c---------------------------------------------------------------------
        if (ind1 == 1) {
            exchange_5(phi1, ibeg, ifin1);
        }
        if (ind2 == 1) {
            exchange_5(phi2, ibeg, ifin1);
        }

        frc2 = 0.0e+00;
        for (k = ki1; k <= ki2 - 1; k++) {
            for (i = ibeg; i <= ifin1; i++) {
                frc2 = frc2 + (phi1.get(i, k) + phi1.get(i + 1, k) + phi1.get(i, k + 1) + phi1.get(i + 1, k + 1)
                        + phi2.get(i, k) + phi2.get(i + 1, k) + phi2.get(i, k + 1) + phi2.get(i + 1, k + 1));
            }
        }

        // c---------------------------------------------------------------------
        // c compute the global sum of individual contributions to frc2
        // c---------------------------------------------------------------------
        dummy = frc2;
        frc2 = MPI.allReduce_sum(dummy);

        frc2 = dxi * dzeta * frc2;

        // c---------------------------------------------------------------------
        // c initialize
        // c---------------------------------------------------------------------
        for (i = 0; i <= isiz2 + 1; i++) {
            for (k = 0; k <= isiz3 + 1; k++) {
                phi1.set(i, k, 0);
                phi2.set(i, k, 0);
            }
        }
        iglob = ipt + ibeg;
        ind1 = 0;
        if (iglob == ii1) {
            ind1 = 1;
            for (k = ki1; k <= ki2; k++) {
                for (j = jbeg; j <= jfin; j++) {
                    jglob = jpt + j;
                    phi1.set(j, k, c2 * (u.get(5, ibeg, j, k) - 0.50e+00 * (u.get(2, ibeg, j, k) * u.get(2, ibeg, j, k)
                            + u.get(3, ibeg, j, k) * u.get(3, ibeg, j, k) + u.get(4, ibeg, j, k) * u.get(4, ibeg, j, k))
                            / u.get(1, ibeg, j, k)));
                }
            }
        }

        iglob = ipt + ifin;
        ind2 = 0;
        if (iglob == ii2) {
            ind2 = 1;
            for (k = ki1; k <= ki2; k++) {
                for (j = jbeg; j <= jfin; j++) {
                    jglob = jpt + j;
                    phi2.set(j, k, c2 * (u.get(5, ifin, j, k) - 0.50e+00 * (u.get(2, ifin, j, k) * u.get(2, ifin, j, k)
                            + u.get(3, ifin, j, k) * u.get(3, ifin, j, k) + u.get(4, ifin, j, k) * u.get(4, ifin, j, k))
                            / u.get(1, ifin, j, k)));
                }
            }
        }

        // c---------------------------------------------------------------------
        // c communicate in j direction
        // c---------------------------------------------------------------------
        if (ind1 == 1) {
            exchange_6(phi1, jbeg, jfin1);
        }
        if (ind2 == 1) {
            exchange_6(phi2, jbeg, jfin1);
        }

        frc3 = 0.0e+00;

        for (k = ki1; k <= ki2 - 1; k++) {
            for (j = jbeg; j <= jfin1; j++) {
                frc3 = frc3 + (phi1.get(j, k) + phi1.get(j + 1, k) + phi1.get(j, k + 1) + phi1.get(j + 1, k + 1)
                        + phi2.get(j, k) + phi2.get(j + 1, k) + phi2.get(j, k + 1) + phi2.get(j + 1, k + 1));
            }
        }

        // c---------------------------------------------------------------------
        // c compute the global sum of individual contributions to frc3
        // c---------------------------------------------------------------------
        dummy = frc3;
        frc3 = MPI.allReduce_sum(dummy);

        frc3 = deta * dzeta * frc3;
        frc = 0.25e+00 * (frc1 + frc2 + frc3);
        // c if (id == 0) write (*,1001) frc

        return;

        // 1001 format (//5x,'surface integral = ',1pe12.5//);

    }

    public void init_comm() {
        // c---------------------------------------------------------------------
        // c initialize MPI communication
        // c---------------------------------------------------------------------
        // call MPI_INIT( IERROR )
        //
        // c---------------------------------------------------------------------
        // c establish the global rank of this process
        // c---------------------------------------------------------------------
        // call MPI_COMM_RANK( MPI_COMM_WORLD,
        // > id,
        // > IERROR )
        //
        // c---------------------------------------------------------------------
        // c establish the size of the global group
        // c---------------------------------------------------------------------
        // call MPI_COMM_SIZE( MPI_COMM_WORLD,
        // > num,
        // > IERROR )
        //
        // ndim = nodedim(num)
        //
        // if (.not. convertdouble) then
        // dp_type = MPI_DOUBLE_PRECISION
        // else
        // dp_type = MPI_REAL
        // endif

        id = MPI.commRank();
        num = MPI.commSize();
        ndim = (int) (Util.log((double) num) / Util.log(2.0d) + 0.00001d);
    }

    public void read_input() {

        // ---------------------------------------------------------------------
        // if input file does not exist, it uses defaults
        // ipr = 1 for detailed progress output
        // inorm = how often the norm is printed (once every inorm iterations)
        // itmax = number of pseudo time steps
        // dt = time step
        // omega 1 over-relaxation factor for SSOR
        // tolrsd = steady state residual tolerance levels
        // nx, ny, nz = number of grid points in x, y, z directions
        // ---------------------------------------------------------------------
        /*
         * File f2 = new File("inputlu.data"); if (f2.exists()) { try {
         * FileInputStream fis = new FileInputStream(f2); DataInputStream
         * datafile = new DataInputStream(fis);
         * System.out.println("Reading from input file inputlu.data");
         * 
         * ipr = datafile.readInt(); inorm = datafile.readInt(); itmax =
         * datafile.readInt(); dt = datafile.readDouble(); omega =
         * datafile.readDouble(); tolrsd.set(0, datafile.readDouble());
         * tolrsd.set(1, datafile.readDouble()); tolrsd.set(2,
         * datafile.readDouble()); tolrsd.set(3, datafile.readDouble());
         * tolrsd.set(4, datafile.readDouble()); nx0 = datafile.readInt(); ny0 =
         * datafile.readInt(); nz0 = datafile.readInt();
         * 
         * fis.close(); } catch (Exception e) {
         * System.err.println("exception caught!"); } } else {
         */
        ipr = ipr_default;
        inorm = inorm_default;
        itmax = itmax_default;
        dt = dt_default;
        omega = omega_default;
        tolrsd.set(0, tolrsd1_def);
        tolrsd.set(1, tolrsd2_def);
        tolrsd.set(2, tolrsd3_def);
        tolrsd.set(3, tolrsd4_def);
        tolrsd.set(4, tolrsd5_def);
        nx0 = isiz01;
        ny0 = isiz02;
        nz0 = isiz03;
        /*
         * }
         */

        // ---------------------------------------------------------------------
        // check problem size
        // ---------------------------------------------------------------------
        int nnodes = num;
        if (id == 0) {

            if (nnodes != nnodes_compiled) {
                Util.printer.p("Warning: program is running on ").p(nnodes).ln().p(" processors but was compiled for ")
                        .p(nnodes_compiled).ln();
                Util.exit(1);
            }

            if ((nx0 < 4) || (ny0 < 4) || (nz0 < 4)) {
                Util.printer.p("PROBLEM SIZE IS TOO SMALL - ").ln().p("SET EACH OF NX, NY AND NZ AT LEAST EQUAL TO 5")
                        .ln();
                Util.exit(1);
            }

            if ((nx0 > isiz01) || (ny0 > isiz02) || (nz0 > isiz03)) {
                Util.printer.p("PROBLEM SIZE IS TOO LARGE - ").ln().p("NX, NY AND NZ SHOULD BE EQUAL TO ").ln()
                        .p("ISIZ01, ISIZ02 AND ISIZ03 RESPECTIVELY").ln();
                Util.exit(1);
            }

            Util.printer.p(" NAS Parallel Benchmarks 2.4 -- LU Benchmark").ln().p(" Size: ").p(nx0).p("x").p(ny0).p("x")
                    .p(nz0).ln().p(" Iterations: ").p(itmax).ln().p(" Number of processors: ").p(nnodes).ln();
        }

        bcast_inputs();
    }

    public void bcast_inputs() {
        int[] buf_ipr = new int[1];
        int[] buf_inorm = new int[1];
        int[] buf_itmax = new int[1];
        double[] buf_dt = new double[1];
        double[] buf_omega = new double[1];
        double[] buf_tolrsd = tolrsd.getData();
        int[] buf_nx0 = new int[1];
        int[] buf_ny0 = new int[1];
        int[] buf_nz0 = new int[1];

        if (id == 0) {
            buf_ipr[0] = ipr;
            buf_inorm[0] = inorm;
            buf_itmax[0] = itmax;
            buf_dt[0] = dt;
            buf_omega[0] = omega;
            buf_nx0[0] = nx0;
            buf_ny0[0] = ny0;
            buf_nz0[0] = nz0;
        }

        MPI.bcast(buf_ipr, 1, 0);
        MPI.bcast(buf_inorm, 1, 0);
        MPI.bcast(buf_itmax, 1, 0);
        MPI.bcast(buf_dt, 1, 0);
        MPI.bcast(buf_omega, 1, 0);
        MPI.bcast(buf_tolrsd, 5, 0);
        MPI.bcast(buf_nx0, 1, 0);
        MPI.bcast(buf_ny0, 1, 0);
        MPI.bcast(buf_nz0, 1, 0);

        if (id != 0) {
            ipr = buf_ipr[0];
            inorm = buf_inorm[0];
            itmax = buf_itmax[0];
            omega = buf_omega[0];
            nx0 = buf_nx0[0];
            ny0 = buf_ny0[0];
            nz0 = buf_nz0[0];
        }
    }

    // c---------------------------------------------------------------------
    // c
    // c set up a two-d grid for processors: column-major ordering of unknowns
    // c NOTE: assumes a power-of-two number of processors
    // c
    // c---------------------------------------------------------------------
    public void proc_grid() {
        xdim = (int) Util.pow(2, (ndim / 2));
        if (ndim % 2 == 1)
            xdim = xdim + xdim;
        ydim = num / xdim;
        row = id % xdim + 1;
        col = id / xdim + 1;
    }

    // c---------------------------------------------------------------------
    // c figure out the neighbors and their wrap numbers for each processor
    // c---------------------------------------------------------------------
    public void neighbors() {
        south = -1;
        east = -1;
        north = -1;
        west = -1;

        if (row > 1) {
            north = id - 1;
        }

        if (row < xdim) {
            south = id + 1;
        }

        if (col > 1) {
            west = id - xdim;
        }

        if (col < ydim) {
            east = id + xdim;
        }
    }

    // c---------------------------------------------------------------------
    // c
    // c set up the sub-domain sizes
    // c
    // c---------------------------------------------------------------------
    public void subdomain() {
        int mm;

        // c---------------------------------------------------------------------
        // c x dimension
        // c---------------------------------------------------------------------
        mm = nx0 % xdim;
        if (row <= mm) {
            nx = nx0 / xdim + 1;
            ipt = (row - 1) * nx;
        } else {
            nx = nx0 / xdim;
            ipt = (row - 1) * nx + mm;
        }

        // c---------------------------------------------------------------------
        // c y dimension
        // c---------------------------------------------------------------------
        mm = ny0 % ydim;
        if (col <= mm) {
            ny = ny0 / ydim + 1;
            jpt = (col - 1) * ny;
        } else {
            ny = ny0 / ydim;
            jpt = (col - 1) * ny + mm;
        }

        // c---------------------------------------------------------------------
        // c z dimension
        // c---------------------------------------------------------------------
        nz = nz0;

        // c---------------------------------------------------------------------
        // c check the sub-domain size
        // c---------------------------------------------------------------------
        if (nx < 4 || ny < 4 || nz < 4) {
            Util.printer.p("SUBDOMAIN SIZE IS TOO SMALL - ").ln().p("ADJUST PROBLEM SIZE OR NUMBER OF PROCESSORS").ln()
                    .p("SO THAT NX, NY AND NZ ARE GREATER THAN OR EQUAL").ln().p("TO 4 THEY ARE CURRENTLY ").p(nx)
                    .p(" ").p(ny).p(" ").p(nz).ln();
            Util.exit(1);
        }

        if (nx > isiz1 || ny > isiz2 || nz > isiz3) {
            Util.printer.p("SUBDOMAIN SIZE IS TOO LARGE - ").ln().p("ADJUST PROBLEM SIZE OR NUMBER OF PROCESSORS").ln()
                    .p("SO THAT NX, NY AND NZ ARE LESS THAN OR EQUAL TO ").ln()
                    .p("ISIZ1, ISIZ2 AND ISIZ3 RESPECTIVELY.  THEY ARE").ln().p("CURRENTLY ").p(nx).p(" ").p(ny).p(" ")
                    .p(nz).ln();
            Util.exit(1);
        }

        // c---------------------------------------------------------------------
        // c set up the start and end in i and j extents for all processors
        // c---------------------------------------------------------------------
        ist = 1;
        iend = nx;
        if (north == -1)
            ist = 2;
        if (south == -1)
            iend = nx - 1;

        jst = 1;
        jend = ny;
        if (west == -1)
            jst = 2;
        if (east == -1)
            jend = ny - 1;
    }

    public void rhs() {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int i, j, k, m;
        int iex;
        int L1, L2;
        int ist1, iend1;
        int jst1, jend1;
        double q;
        double u21, u31, u41;
        double tmp;
        double u21i, u31i, u41i, u51i;
        double u21j, u31j, u41j, u51j;
        double u21k, u31k, u41k, u51k;
        double u21im1, u31im1, u41im1, u51im1;
        double u21jm1, u31jm1, u41jm1, u51jm1;
        double u21km1, u31km1, u41km1, u51km1;

        for (k = 1; k <= nz; k++) {
            for (j = 1; j <= ny; j++) {
                for (i = 1; i <= nx; i++) {
                    for (m = 1; m <= 5; m++) {
                        rsd.set(m, i, j, k, -frct.get(m, i, j, k));
                    }
                }
            }
        }

        // dump_u();
        // dump_rsd();

        // c---------------------------------------------------------------------
        // c xi-direction flux differences
        // c---------------------------------------------------------------------

        // c---------------------------------------------------------------------
        // c iex = flag : iex = 0 north/south communication
        // c : iex = 1 east/west communication
        // c---------------------------------------------------------------------
        iex = 0;

        // c---------------------------------------------------------------------
        // c communicate and receive/send two rows of data
        // c---------------------------------------------------------------------
        exchange_3(u, iex);
        // dump_u();

        L1 = 0;
        if (north == -1)
            L1 = 1;
        L2 = nx + 1;
        if (south == -1)
            L2 = nx;

        for (k = 2; k <= nz - 1; k++) {
            for (j = jst; j <= jend; j++) {
                for (i = L1; i <= L2; i++) {
                    flux.set(1, i, j, k, u.get(2, i, j, k));
                    u21 = u.get(2, i, j, k) / u.get(1, i, j, k);

                    q = 0.50e+00 * (u.get(2, i, j, k) * u.get(2, i, j, k) + u.get(3, i, j, k) * u.get(3, i, j, k)
                            + u.get(4, i, j, k) * u.get(4, i, j, k)) / u.get(1, i, j, k);

                    flux.set(2, i, j, k, u.get(2, i, j, k) * u21 + c2 * (u.get(5, i, j, k) - q));
                    flux.set(3, i, j, k, u.get(3, i, j, k) * u21);
                    flux.set(4, i, j, k, u.get(4, i, j, k) * u21);
                    flux.set(5, i, j, k, (c1 * u.get(5, i, j, k) - c2 * q) * u21);
                }
            }
        }

        // dump_flux();

        for (k = 2; k <= nz - 1; k++) {
            for (j = jst; j <= jend; j++) {
                for (i = ist; i <= iend; i++) {
                    for (m = 1; m <= 5; m++) {
                        rsd.set(m, i, j, k,
                                rsd.get(m, i, j, k) - tx2 * (flux.get(m, i + 1, j, k) - flux.get(m, i - 1, j, k)));
                    }
                }

                L2 = nx + 1;
                if (south == -1)
                    L2 = nx;

                for (i = ist; i <= L2; i++) {
                    tmp = 1.0e+00 / u.get(1, i, j, k);

                    u21i = tmp * u.get(2, i, j, k);
                    u31i = tmp * u.get(3, i, j, k);
                    u41i = tmp * u.get(4, i, j, k);
                    u51i = tmp * u.get(5, i, j, k);

                    tmp = 1.0e+00 / u.get(1, i - 1, j, k);

                    u21im1 = tmp * u.get(2, i - 1, j, k);
                    u31im1 = tmp * u.get(3, i - 1, j, k);
                    u41im1 = tmp * u.get(4, i - 1, j, k);
                    u51im1 = tmp * u.get(5, i - 1, j, k);

                    flux.set(2, i, j, k, (4.0e+00 / 3.0e+00) * tx3 * (u21i - u21im1));
                    flux.set(3, i, j, k, tx3 * (u31i - u31im1));
                    flux.set(4, i, j, k, tx3 * (u41i - u41im1));
                    flux.set(5, i, j, k,
                            0.50e+00 * (1.0e+00 - c1 * c5) * tx3
                                    * ((u21i * u21i + u31i * u31i + u41i * u41i)
                                            - (u21im1 * u21im1 + u31im1 * u31im1 + u41im1 * u41im1))
                                    + (1.0e+00 / 6.0e+00) * tx3 * (u21i * u21i - u21im1 * u21im1)
                                    + c1 * c5 * tx3 * (u51i - u51im1));
                }

                for (i = ist; i <= iend; i++) {
                    rsd.set(1, i, j, k, rsd.get(1, i, j, k) + dx1 * tx1
                            * (u.get(1, i - 1, j, k) - 2.0e+00 * u.get(1, i, j, k) + u.get(1, i + 1, j, k)));
                    rsd.set(2, i, j, k, rsd.get(2, i, j, k)
                            + tx3 * c3 * c4 * (flux.get(2, i + 1, j, k) - flux.get(2, i, j, k)) + dx2 * tx1
                                    * (u.get(2, i - 1, j, k) - 2.0e+00 * u.get(2, i, j, k) + u.get(2, i + 1, j, k)));
                    rsd.set(3, i, j, k, rsd.get(3, i, j, k)
                            + tx3 * c3 * c4 * (flux.get(3, i + 1, j, k) - flux.get(3, i, j, k)) + dx3 * tx1
                                    * (u.get(3, i - 1, j, k) - 2.0e+00 * u.get(3, i, j, k) + u.get(3, i + 1, j, k)));
                    rsd.set(4, i, j, k, rsd.get(4, i, j, k)
                            + tx3 * c3 * c4 * (flux.get(4, i + 1, j, k) - flux.get(4, i, j, k)) + dx4 * tx1
                                    * (u.get(4, i - 1, j, k) - 2.0e+00 * u.get(4, i, j, k) + u.get(4, i + 1, j, k)));
                    rsd.set(5, i, j, k, rsd.get(5, i, j, k)
                            + tx3 * c3 * c4 * (flux.get(5, i + 1, j, k) - flux.get(5, i, j, k)) + dx5 * tx1
                                    * (u.get(5, i - 1, j, k) - 2.0e+00 * u.get(5, i, j, k) + u.get(5, i + 1, j, k)));
                }

                // c---------------------------------------------------------------------
                // c Fourth-order dissipation
                // c---------------------------------------------------------------------
                if (north == -1) {
                    for (m = 1; m <= 5; m++) {
                        rsd.set(m, 2, j, k, rsd.get(m, 2, j, k) - dssp
                                * (+5.0e+00 * u.get(m, 2, j, k) - 4.0e+00 * u.get(m, 3, j, k) + u.get(m, 4, j, k)));
                        rsd.set(m, 3, j, k, rsd.get(m, 3, j, k) - dssp * (-4.0e+00 * u.get(m, 2, j, k)
                                + 6.0e+00 * u.get(m, 3, j, k) - 4.0e+00 * u.get(m, 4, j, k) + u.get(m, 5, j, k)));
                    }
                }

                ist1 = 1;
                iend1 = nx;
                if (north == -1)
                    ist1 = 4;
                if (south == -1)
                    iend1 = nx - 3;

                for (i = ist1; i <= iend1; i++) {
                    for (m = 1; m <= 5; m++) {
                        rsd.set(m, i, j, k,
                                rsd.get(m, i, j, k) - dssp * (u.get(m, i - 2, j, k) - 4.0e+00 * u.get(m, i - 1, j, k)
                                        + 6.0e+00 * u.get(m, i, j, k) - 4.0e+00 * u.get(m, i + 1, j, k)
                                        + u.get(m, i + 2, j, k)));
                    }
                }

                if (south == -1) {

                    for (m = 1; m <= 5; m++) {
                        rsd.set(m, nx - 2, j, k,
                                rsd.get(m, nx - 2, j, k)
                                        - dssp * (u.get(m, nx - 4, j, k) - 4.0e+00 * u.get(m, nx - 3, j, k)
                                                + 6.0e+00 * u.get(m, nx - 2, j, k) - 4.0e+00 * u.get(m, nx - 1, j, k)));
                        rsd.set(m, nx - 1, j, k, rsd.get(m, nx - 1, j, k) - dssp * (u.get(m, nx - 3, j, k)
                                - 4.0e+00 * u.get(m, nx - 2, j, k) + 5.0e+00 * u.get(m, nx - 1, j, k)));
                    }
                }

            }

        }

        // dump_rsd();

        // c---------------------------------------------------------------------
        // c eta-direction flux differences
        // c---------------------------------------------------------------------

        // c---------------------------------------------------------------------
        // c iex = flag : iex = 0 north/south communication
        // c---------------------------------------------------------------------
        iex = 1;

        // c---------------------------------------------------------------------
        // c communicate and receive/send two rows of data
        // c---------------------------------------------------------------------
        exchange_3(u, iex);

        L1 = 0;
        if (west == -1)
            L1 = 1;
        L2 = ny + 1;
        if (east == -1)
            L2 = ny;

        for (k = 2; k <= nz - 1; k++) {
            for (i = ist; i <= iend; i++) {
                for (j = L1; j <= L2; j++) {
                    flux.set(1, i, j, k, u.get(3, i, j, k));
                    u31 = u.get(3, i, j, k) / u.get(1, i, j, k);

                    q = 0.50e+00 * (u.get(2, i, j, k) * u.get(2, i, j, k) + u.get(3, i, j, k) * u.get(3, i, j, k)
                            + u.get(4, i, j, k) * u.get(4, i, j, k)) / u.get(1, i, j, k);

                    flux.set(2, i, j, k, u.get(2, i, j, k) * u31);
                    flux.set(3, i, j, k, u.get(3, i, j, k) * u31 + c2 * (u.get(5, i, j, k) - q));
                    flux.set(4, i, j, k, u.get(4, i, j, k) * u31);
                    flux.set(5, i, j, k, (c1 * u.get(5, i, j, k) - c2 * q) * u31);
                }
            }
        }

        for (k = 2; k <= nz - 1; k++) {
            for (i = ist; i <= iend; i++) {
                for (j = jst; j <= jend; j++) {
                    for (m = 1; m <= 5; m++) {
                        rsd.set(m, i, j, k,
                                rsd.get(m, i, j, k) - ty2 * (flux.get(m, i, j + 1, k) - flux.get(m, i, j - 1, k)));
                    }
                }

                L2 = ny + 1;
                if (east == -1)
                    L2 = ny;
                for (j = jst; j <= L2; j++) {
                    tmp = 1.0e+00 / u.get(1, i, j, k);

                    u21j = tmp * u.get(2, i, j, k);
                    u31j = tmp * u.get(3, i, j, k);
                    u41j = tmp * u.get(4, i, j, k);
                    u51j = tmp * u.get(5, i, j, k);

                    tmp = 1.0e+00 / u.get(1, i, j - 1, k);
                    u21jm1 = tmp * u.get(2, i, j - 1, k);
                    u31jm1 = tmp * u.get(3, i, j - 1, k);
                    u41jm1 = tmp * u.get(4, i, j - 1, k);
                    u51jm1 = tmp * u.get(5, i, j - 1, k);

                    flux.set(2, i, j, k, ty3 * (u21j - u21jm1));
                    flux.set(3, i, j, k, (4.0e+00 / 3.0e+00) * ty3 * (u31j - u31jm1));
                    flux.set(4, i, j, k, ty3 * (u41j - u41jm1));
                    flux.set(5, i, j, k,
                            0.50e+00 * (1.0e+00 - c1 * c5) * ty3
                                    * ((u21j * u21j + u31j * u31j + u41j * u41j)
                                            - (u21jm1 * u21jm1 + u31jm1 * u31jm1 + u41jm1 * u41jm1))
                                    + (1.0e+00 / 6.0e+00) * ty3 * (u31j * u31j - u31jm1 * u31jm1)
                                    + c1 * c5 * ty3 * (u51j - u51jm1));
                }

                for (j = jst; j <= jend; j++) {

                    rsd.set(1, i, j, k, rsd.get(1, i, j, k) + dy1 * ty1
                            * (u.get(1, i, j - 1, k) - 2.0e+00 * u.get(1, i, j, k) + u.get(1, i, j + 1, k)));

                    rsd.set(2, i, j, k, rsd.get(2, i, j, k)
                            + ty3 * c3 * c4 * (flux.get(2, i, j + 1, k) - flux.get(2, i, j, k)) + dy2 * ty1
                                    * (u.get(2, i, j - 1, k) - 2.0e+00 * u.get(2, i, j, k) + u.get(2, i, j + 1, k)));

                    rsd.set(3, i, j, k, rsd.get(3, i, j, k)
                            + ty3 * c3 * c4 * (flux.get(3, i, j + 1, k) - flux.get(3, i, j, k)) + dy3 * ty1
                                    * (u.get(3, i, j - 1, k) - 2.0e+00 * u.get(3, i, j, k) + u.get(3, i, j + 1, k)));

                    rsd.set(4, i, j, k, rsd.get(4, i, j, k)
                            + ty3 * c3 * c4 * (flux.get(4, i, j + 1, k) - flux.get(4, i, j, k)) + dy4 * ty1
                                    * (u.get(4, i, j - 1, k) - 2.0e+00 * u.get(4, i, j, k) + u.get(4, i, j + 1, k)));

                    rsd.set(5, i, j, k, rsd.get(5, i, j, k)
                            + ty3 * c3 * c4 * (flux.get(5, i, j + 1, k) - flux.get(5, i, j, k)) + dy5 * ty1
                                    * (u.get(5, i, j - 1, k) - 2.0e+00 * u.get(5, i, j, k) + u.get(5, i, j + 1, k)));

                }

                // c---------------------------------------------------------------------
                // c fourth-order dissipation
                // c---------------------------------------------------------------------
                if (west == -1) {
                    for (m = 1; m <= 5; m++) {
                        rsd.set(m, i, 2, k, rsd.get(m, i, 2, k) - dssp
                                * (+5.0e+00 * u.get(m, i, 2, k) - 4.0e+00 * u.get(m, i, 3, k) + u.get(m, i, 4, k)));
                        rsd.set(m, i, 3, k, rsd.get(m, i, 3, k) - dssp * (-4.0e+00 * u.get(m, i, 2, k)
                                + 6.0e+00 * u.get(m, i, 3, k) - 4.0e+00 * u.get(m, i, 4, k) + u.get(m, i, 5, k)));
                    }
                }

                jst1 = 1;
                jend1 = ny;
                if (west == -1)
                    jst1 = 4;
                if (east == -1)
                    jend1 = ny - 3;
                for (j = jst1; j <= jend1; j++) {
                    for (m = 1; m <= 5; m++) {
                        rsd.set(m, i, j, k,
                                rsd.get(m, i, j, k) - dssp * (u.get(m, i, j - 2, k) - 4.0e+00 * u.get(m, i, j - 1, k)
                                        + 6.0e+00 * u.get(m, i, j, k) - 4.0e+00 * u.get(m, i, j + 1, k)
                                        + u.get(m, i, j + 2, k)));
                    }
                }

                if (east == -1) {
                    for (m = 1; m <= 5; m++) {
                        rsd.set(m, i, ny - 2, k,
                                rsd.get(m, i, ny - 2, k)
                                        - dssp * (u.get(m, i, ny - 4, k) - 4.0e+00 * u.get(m, i, ny - 3, k)
                                                + 6.0e+00 * u.get(m, i, ny - 2, k) - 4.0e+00 * u.get(m, i, ny - 1, k)));
                        rsd.set(m, i, ny - 1, k, rsd.get(m, i, ny - 1, k) - dssp * (u.get(m, i, ny - 3, k)
                                - 4.0e+00 * u.get(m, i, ny - 2, k) + 5.0e+00 * u.get(m, i, ny - 1, k)));
                    }
                }

            }
        }

        // c---------------------------------------------------------------------
        // c zeta-direction flux differences
        // c---------------------------------------------------------------------
        for (j = jst; j <= jend; j++) {
            for (i = ist; i <= iend; i++) {
                for (k = 1; k <= nz; k++) {
                    flux.set(1, i, j, k, u.get(4, i, j, k));
                    u41 = u.get(4, i, j, k) / u.get(1, i, j, k);

                    q = 0.50e+00 * (u.get(2, i, j, k) * u.get(2, i, j, k) + u.get(3, i, j, k) * u.get(3, i, j, k)
                            + u.get(4, i, j, k) * u.get(4, i, j, k)) / u.get(1, i, j, k);

                    flux.set(2, i, j, k, u.get(2, i, j, k) * u41);
                    flux.set(3, i, j, k, u.get(3, i, j, k) * u41);
                    flux.set(4, i, j, k, u.get(4, i, j, k) * u41 + c2 * (u.get(5, i, j, k) - q));
                    flux.set(5, i, j, k, (c1 * u.get(5, i, j, k) - c2 * q) * u41);
                }

                for (k = 2; k <= nz - 1; k++) {
                    for (m = 1; m <= 5; m++) {
                        rsd.set(m, i, j, k,
                                rsd.get(m, i, j, k) - tz2 * (flux.get(m, i, j, k + 1) - flux.get(m, i, j, k - 1)));
                    }
                }

                for (k = 2; k <= nz; k++) {
                    tmp = 1.0e+00 / u.get(1, i, j, k);

                    u21k = tmp * u.get(2, i, j, k);
                    u31k = tmp * u.get(3, i, j, k);
                    u41k = tmp * u.get(4, i, j, k);
                    u51k = tmp * u.get(5, i, j, k);

                    tmp = 1.0e+00 / u.get(1, i, j, k - 1);

                    u21km1 = tmp * u.get(2, i, j, k - 1);
                    u31km1 = tmp * u.get(3, i, j, k - 1);
                    u41km1 = tmp * u.get(4, i, j, k - 1);
                    u51km1 = tmp * u.get(5, i, j, k - 1);

                    flux.set(2, i, j, k, tz3 * (u21k - u21km1));
                    flux.set(3, i, j, k, tz3 * (u31k - u31km1));
                    flux.set(4, i, j, k, (4.0e+00 / 3.0e+00) * tz3 * (u41k - u41km1));
                    flux.set(5, i, j, k,
                            0.50e+00 * (1.0e+00 - c1 * c5) * tz3
                                    * ((u21k * u21k + u31k * u31k + u41k * u41k)
                                            - (u21km1 * u21km1 + u31km1 * u31km1 + u41km1 * u41km1))
                                    + (1.0e+00 / 6.0e+00) * tz3 * (u41k * u41k - u41km1 * u41km1)
                                    + c1 * c5 * tz3 * (u51k - u51km1));
                }

                for (k = 2; k <= nz - 1; k++) {
                    rsd.set(1, i, j, k, rsd.get(1, i, j, k) + dz1 * tz1
                            * (u.get(1, i, j, k - 1) - 2.0e+00 * u.get(1, i, j, k) + u.get(1, i, j, k + 1)));
                    rsd.set(2, i, j, k, rsd.get(2, i, j, k)
                            + tz3 * c3 * c4 * (flux.get(2, i, j, k + 1) - flux.get(2, i, j, k)) + dz2 * tz1
                                    * (u.get(2, i, j, k - 1) - 2.0e+00 * u.get(2, i, j, k) + u.get(2, i, j, k + 1)));
                    rsd.set(3, i, j, k, rsd.get(3, i, j, k)
                            + tz3 * c3 * c4 * (flux.get(3, i, j, k + 1) - flux.get(3, i, j, k)) + dz3 * tz1
                                    * (u.get(3, i, j, k - 1) - 2.0e+00 * u.get(3, i, j, k) + u.get(3, i, j, k + 1)));
                    rsd.set(4, i, j, k, rsd.get(4, i, j, k)
                            + tz3 * c3 * c4 * (flux.get(4, i, j, k + 1) - flux.get(4, i, j, k)) + dz4 * tz1
                                    * (u.get(4, i, j, k - 1) - 2.0e+00 * u.get(4, i, j, k) + u.get(4, i, j, k + 1)));
                    rsd.set(5, i, j, k, rsd.get(5, i, j, k)
                            + tz3 * c3 * c4 * (flux.get(5, i, j, k + 1) - flux.get(5, i, j, k)) + dz5 * tz1
                                    * (u.get(5, i, j, k - 1) - 2.0e+00 * u.get(5, i, j, k) + u.get(5, i, j, k + 1)));
                }

                // c---------------------------------------------------------------------
                // c fourth-order dissipation
                // c---------------------------------------------------------------------
                for (m = 1; m <= 5; m++) {
                    rsd.set(m, i, j, 2, rsd.get(m, i, j, 2)
                            - dssp * (+5.0e+00 * u.get(m, i, j, 2) - 4.0e+00 * u.get(m, i, j, 3) + u.get(m, i, j, 4)));
                    rsd.set(m, i, j, 3, rsd.get(m, i, j, 3) - dssp * (-4.0e+00 * u.get(m, i, j, 2)
                            + 6.0e+00 * u.get(m, i, j, 3) - 4.0e+00 * u.get(m, i, j, 4) + u.get(m, i, j, 5)));
                }

                for (k = 4; k <= nz - 3; k++) {
                    for (m = 1; m <= 5; m++) {
                        rsd.set(m, i, j, k,
                                rsd.get(m, i, j, k) - dssp * (u.get(m, i, j, k - 2) - 4.0e+00 * u.get(m, i, j, k - 1)
                                        + 6.0e+00 * u.get(m, i, j, k) - 4.0e+00 * u.get(m, i, j, k + 1)
                                        + u.get(m, i, j, k + 2)));
                    }
                }

                for (m = 1; m <= 5; m++) {
                    rsd.set(m, i, j, nz - 2,
                            rsd.get(m, i, j, nz - 2) - dssp * (u.get(m, i, j, nz - 4) - 4.0e+00 * u.get(m, i, j, nz - 3)
                                    + 6.0e+00 * u.get(m, i, j, nz - 2) - 4.0e+00 * u.get(m, i, j, nz - 1)));
                    rsd.set(m, i, j, nz - 1, rsd.get(m, i, j, nz - 1) - dssp * (u.get(m, i, j, nz - 3)
                            - 4.0e+00 * u.get(m, i, j, nz - 2) + 5.0e+00 * u.get(m, i, j, nz - 1)));
                }
            }
        }
    }

    public void before_dump() {
        // for (int i = 0; i < id; i++) {
        // MPI.barrier();
        // }
    }

    public void after_dump() {
        // for (int i = id + 1; i < num; i++) {
        // MPI.barrier();
        // }
        // MPI.barrier();
    }

    public void dump_ce() {
        // before_dump();
        // for (int m = 1; m <= 5; m++) {
        // double sum = 0;
        // for (int i = 1; i <= 13; i++) {
        //// Util.printer.p(m).p(", ").p(i).p(" = ").e(ce.get(m, i)).ln();
        // sum += ce.get(m, i);
        // }
        // Util.printer.p("[").p(id).p("] ce[").p(m).p(",*] = ").e(sum).ln();
        // }
        // after_dump();
    }

    public void dump_u() {
        // before_dump();
        // for (int m = 1; m <= 5; m++) {
        // double sum = 0;
        // for (int k = 1; k <= nz; k++) {
        // for (int j = 1; j <= ny; j++) {
        // for (int i = 1; i <= nx; i++) {
        // sum += u.get(m, i, j, k);
        // }
        // }
        // }
        // Util.printer.p("[").p(id).p("] u[").p(m).p(",*,*,*] = ").e(sum).ln();
        // }
        // after_dump();
    }

    public void dump_rsd() {
        // before_dump();
        // for (int m = 1; m <= 5; m++) {
        // double sum = 0;
        // for (int k = 1; k <= isiz3; k++) {
        // for (int j = -1; j <= isiz3+2; j++) {
        // for (int i = -1; i <= isiz1+2; i++) {
        // sum += rsd.get(m, i, j, k) * i;
        // }
        // }
        // }
        // Util.printer.p("[").p(id).p("] rsd[").p(m).p(",*,*,*] =
        // ").e(sum).ln();
        // }
        // after_dump();
    }

    public void dump_frct() {
        // before_dump();
        // for (int m = 1; m <= 5; m++) {
        // double sum = 0;
        // for (int k = 1; k <= nz; k++) {
        // for (int j = 1; j <= ny; j++) {
        // for (int i = 1; i <= nx; i++) {
        // sum += frct.get(m, i, j, k);
        // }
        // }
        // }
        // Util.printer.p("[").p(id).p("] frct[").p(m).p(",*,*,*] =
        // ").e(sum).ln();
        // }
        // after_dump();
    }

    public void dump_flux() {
        // before_dump();
        // for (int m = 1; m <= 5; m++) {
        // double sum = 0;
        // for (int k = 1; k <= nz; k++) {
        // for (int j = 1; j <= ny; j++) {
        // for (int i = 1; i <= nx; i++) {
        // sum += flux.get(m, i, j, k);
        // }
        // }
        // }
        // Util.printer.p("[").p(id).p("] flux[").p(m).p(",*,*,*] =
        // ").e(sum).ln();
        // }
        // after_dump();
    }

    public void dump_abcd() {
        // before_dump();
        // double sum_a = 0;
        // double sum_b = 0;
        // double sum_c = 0;
        // double sum_d = 0;
        // for (int k = 1; k <= isiz2; k++) {
        // for (int j = 1; j <= isiz1; j++) {
        // for (int i = 1; i <= 5; i++) {
        // for (int m = 1; m <= 5; m++) {
        // sum_a += a.get(m, i, j, k);
        // sum_b += b.get(m, i, j, k);
        // sum_c += c.get(m, i, j, k);
        // sum_d += d.get(m, i, j, k);
        // }
        // }
        // }
        // }
        // Util.printer.p("[").p(id).p("] a[*,*,*,*] = ").e(sum_a).ln();
        // Util.printer.p("[").p(id).p("] b[*,*,*,*] = ").e(sum_b).ln();
        // Util.printer.p("[").p(id).p("] c[*,*,*,*] = ").e(sum_c).ln();
        // Util.printer.p("[").p(id).p("] d[*,*,*,*] = ").e(sum_d).ln();
        // after_dump();
    }

    public void dump_array(String name, Array4Ddouble array) {
        // double sum = 0;
        // double buf[] = array.getData();
        // for (int i = 0; i < array.getDataSize(); i++) {
        // sum += buf[i];
        // }
        // Util.printer.p("[").p(id).p("] ").p(name).p("[*] = ").e(sum).ln();
    }

    public void dump_array(String name, Array2Ddouble array) {
        // double sum = 0;
        // double buf[] = array.getData();
        // for (int i = 0; i < array.getDataSize(); i++) {
        // sum += buf[i];
        // }
        // Util.printer.p("[").p(id).p("] ").p(name).p("[*] = ").e(sum).ln();
    }

    public void trace_recv(double buf[], int length, int src, int tag) {
        // double sum = 0;
        // for (int i = 0; i < length; i++) {
        // sum += buf[i];
        // }
        // Util.printer.p("[").p(id).p("] received sum ").e(sum).p(" len
        // ").p(length).p(" src ").p(src).p(" tag ").p(tag).ln();
    }

    public void trace_send(double buf[], int offset, int length, int dest, int tag) {
        // double sum = 0;
        // for (int i = 0; i < length; i++) {
        // sum += buf[offset + i];
        // }
        // Util.printer.p("[").p(id).p("] sent sum ").e(sum).p(" len
        // ").p(length).p(" dest ").p(dest).p(" tag ").p(tag).ln();
    }

    // c---------------------------------------------------------------------
    // c set up coefficients
    // c---------------------------------------------------------------------
    public void setcoeff() {
        dxi = 1.0 / (nx0 - 1);
        deta = 1.0 / (ny0 - 1);
        dzeta = 1.0 / (nz0 - 1);

        tx1 = 1.0 / (dxi * dxi);
        tx2 = 1.0 / (2.0 * dxi);
        tx3 = 1.0 / dxi;

        ty1 = 1.0 / (deta * deta);
        ty2 = 1.0 / (2.0 * deta);
        ty3 = 1.0 / deta;

        tz1 = 1.0 / (dzeta * dzeta);
        tz2 = 1.0 / (2.0 * dzeta);
        tz3 = 1.0 / dzeta;

        ii1 = 2;
        ii2 = nx0 - 1;
        ji1 = 2;
        ji2 = ny0 - 2;
        ki1 = 3;
        ki2 = nz0 - 1;

        // c---------------------------------------------------------------------
        // c diffusion coefficients
        // c---------------------------------------------------------------------
        dx1 = 0.75;
        dx2 = dx1;
        dx3 = dx1;
        dx4 = dx1;
        dx5 = dx1;

        dy1 = 0.75;
        dy2 = dy1;
        dy3 = dy1;
        dy4 = dy1;
        dy5 = dy1;

        dz1 = 1.00;
        dz2 = dz1;
        dz3 = dz1;
        dz4 = dz1;
        dz5 = dz1;

        // c---------------------------------------------------------------------
        // c fourth difference dissipation
        // c---------------------------------------------------------------------
        dssp = (max(dx1, dy1, dz1)) / 4.0;

        // c---------------------------------------------------------------------
        // c coefficients of the exact solution to the first pde
        // c---------------------------------------------------------------------
        ce.set(1, 1, 2.0e+00);
        ce.set(1, 2, 0.0e+00);
        ce.set(1, 3, 0.0e+00);
        ce.set(1, 4, 4.0e+00);
        ce.set(1, 5, 5.0e+00);
        ce.set(1, 6, 3.0e+00);
        ce.set(1, 7, 5.0e-01);
        ce.set(1, 8, 2.0e-02);
        ce.set(1, 9, 1.0e-02);
        ce.set(1, 10, 3.0e-02);
        ce.set(1, 11, 5.0e-01);
        ce.set(1, 12, 4.0e-01);
        ce.set(1, 13, 3.0e-01);

        // c---------------------------------------------------------------------
        // c coefficients of the exact solution to the second pde
        // c---------------------------------------------------------------------
        ce.set(2, 1, 1.0e+00);
        ce.set(2, 2, 0.0e+00);
        ce.set(2, 3, 0.0e+00);
        ce.set(2, 4, 0.0e+00);
        ce.set(2, 5, 1.0e+00);
        ce.set(2, 6, 2.0e+00);
        ce.set(2, 7, 3.0e+00);
        ce.set(2, 8, 1.0e-02);
        ce.set(2, 9, 3.0e-02);
        ce.set(2, 10, 2.0e-02);
        ce.set(2, 11, 4.0e-01);
        ce.set(2, 12, 3.0e-01);
        ce.set(2, 13, 5.0e-01);

        // c---------------------------------------------------------------------
        // c coefficients of the exact solution to the third pde
        // c---------------------------------------------------------------------
        ce.set(3, 1, 2.0e+00);
        ce.set(3, 2, 2.0e+00);
        ce.set(3, 3, 0.0e+00);
        ce.set(3, 4, 0.0e+00);
        ce.set(3, 5, 0.0e+00);
        ce.set(3, 6, 2.0e+00);
        ce.set(3, 7, 3.0e+00);
        ce.set(3, 8, 4.0e-02);
        ce.set(3, 9, 3.0e-02);
        ce.set(3, 10, 5.0e-02);
        ce.set(3, 11, 3.0e-01);
        ce.set(3, 12, 5.0e-01);
        ce.set(3, 13, 4.0e-01);

        // c---------------------------------------------------------------------
        // c coefficients of the exact solution to the fourth pde
        // c---------------------------------------------------------------------
        ce.set(4, 1, 2.0e+00);
        ce.set(4, 2, 2.0e+00);
        ce.set(4, 3, 0.0e+00);
        ce.set(4, 4, 0.0e+00);
        ce.set(4, 5, 0.0e+00);
        ce.set(4, 6, 2.0e+00);
        ce.set(4, 7, 3.0e+00);
        ce.set(4, 8, 3.0e-02);
        ce.set(4, 9, 5.0e-02);
        ce.set(4, 10, 4.0e-02);
        ce.set(4, 11, 2.0e-01);
        ce.set(4, 12, 1.0e-01);
        ce.set(4, 13, 3.0e-01);

        // c---------------------------------------------------------------------
        // c coefficients of the exact solution to the fifth pde
        // c---------------------------------------------------------------------
        ce.set(5, 1, 5.0e+00);
        ce.set(5, 2, 4.0e+00);
        ce.set(5, 3, 3.0e+00);
        ce.set(5, 4, 2.0e+00);
        ce.set(5, 5, 1.0e-01);
        ce.set(5, 6, 4.0e-01);
        ce.set(5, 7, 3.0e-01);
        ce.set(5, 8, 5.0e-02);
        ce.set(5, 9, 4.0e-02);
        ce.set(5, 10, 3.0e-02);
        ce.set(5, 11, 1.0e-01);
        ce.set(5, 12, 3.0e-01);
        ce.set(5, 13, 2.0e-01);
    }

    // c---------------------------------------------------------------------
    // c set the masks required for comm
    // c---------------------------------------------------------------------
    void sethyper() {
        // c---------------------------------------------------------------------
        // c for each column in a hyperplane, istart = first row,
        // c---------------------------------------------------------------------
        int i, j;
        int iglob, jglob;
        int kp;

        // c---------------------------------------------------------------------
        // c compute the pointers for hyperplanes
        // c---------------------------------------------------------------------
        for (kp = 2; kp <= nx0 + ny0; kp++) {
            icomms.set(kp, false);
            icommn.set(kp, false);
            icomme.set(kp, false);
            icommw.set(kp, false);

            // c---------------------------------------------------------------------
            // c check to see if comm. to south is required
            // c---------------------------------------------------------------------
            if (south != -1) {
                i = iend;
                iglob = ipt + i;
                jglob = kp - iglob;
                j = jglob - jpt;
                if (jglob >= 2 && jglob <= ny0 - 1 && j >= jst && j <= jend)
                    icomms.set(kp, true);
            }

            // c---------------------------------------------------------------------
            // c check to see if comm. to north is required
            // c---------------------------------------------------------------------
            if (north != -1) {
                i = ist;
                iglob = ipt + i;
                jglob = kp - iglob;
                j = jglob - jpt;
                if (jglob >= 2 && jglob <= ny0 - 1 && j >= jst && j <= jend)
                    icommn.set(kp, true);
            }

            // c---------------------------------------------------------------------
            // c check to see if comm. to east is required
            // c---------------------------------------------------------------------
            if (east != -1) {
                j = jend;
                jglob = jpt + j;
                iglob = kp - jglob;
                i = iglob - ipt;
                if (iglob >= 2 && iglob <= nx0 - 1 && i >= ist && i <= iend)
                    icomme.set(kp, true);
            }

            // c---------------------------------------------------------------------
            // c check to see if comm. to west is required
            // c---------------------------------------------------------------------
            if (west != -1) {
                j = jst;
                jglob = jpt + j;
                iglob = kp - jglob;
                i = iglob - ipt;
                if (iglob >= 2 && iglob <= nx0 - 1 && i >= ist && i <= iend)
                    icommw.set(kp, true);
            }
        }

        icomms.set(1, false);
        icommn.set(1, false);
        icomme.set(1, false);
        icommw.set(1, false);
        icomms.set(nx0 + ny0 + 1, false);
        icommn.set(nx0 + ny0 + 1, false);
        icomme.set(nx0 + ny0 + 1, false);
        icommw.set(nx0 + ny0 + 1, false);
    }

    // c---------------------------------------------------------------------
    // c set the boundary values of dependent variables
    // c---------------------------------------------------------------------
    public void setbv() {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        // integer i, j, k
        // integer iglob, jglob
        //
        // c---------------------------------------------------------------------
        // c set the dependent variable values along the top and bottom faces
        // c---------------------------------------------------------------------
        // do j = 1, ny
        // jglob = jpt + j
        // do i = 1, nx
        // iglob = ipt + i
        // call exact( iglob, jglob, 1, u( 1, i, j, 1 ) )
        // call exact( iglob, jglob, nz, u( 1, i, j, nz ) )
        // end do
        // end do

        int i, j, k;
        int iglob, jglob;

        for (j = 1; j <= ny; j++) {
            jglob = jpt + j;
            for (i = 1; i <= nx; i++) {
                iglob = ipt + i;
                exact(iglob, jglob, 1, u, 1, i, j, 1);
                exact(iglob, jglob, nz, u, 1, i, j, nz);
                // Util.printer.p(i).p(" ").p(iglob).p(" ").p(jglob).ln();
            }
        }

        // dump_u();

        // c---------------------------------------------------------------------
        // c set the dependent variable values along north and south faces
        // c---------------------------------------------------------------------
        // IF (west.eq.-1) then
        // do k = 1, nz
        // do i = 1, nx
        // iglob = ipt + i
        // call exact( iglob, 1, k, u( 1, i, 1, k ) )
        // end do
        // end do
        // END IF
        //
        // IF (east.eq.-1) then
        // do k = 1, nz
        // do i = 1, nx
        // iglob = ipt + i
        // call exact( iglob, ny0, k, u( 1, i, ny, k ) )
        // end do
        // end do
        // END IF

        if (west == -1) {
            for (k = 1; k <= nz; k++) {
                for (i = 1; i <= nx; i++) {
                    iglob = ipt + i;
                    exact(iglob, 1, k, u, 1, i, 1, k);
                    // Util.printer.p(k).p(" ").p(i).p(" ").p(iglob).ln();
                    // dump_u();
                }
            }
        }

        // dump_u();

        if (east == -1) {
            for (k = 1; k <= nz; k++) {
                for (i = 1; i <= nx; i++) {
                    iglob = ipt + i;
                    exact(iglob, ny0, k, u, 1, i, ny, k);
                }
            }
        }

        // dump_u();

        // c---------------------------------------------------------------------
        // c set the dependent variable values along east and west faces
        // c---------------------------------------------------------------------
        // IF (north.eq.-1) then
        // do k = 1, nz
        // do j = 1, ny
        // jglob = jpt + j
        // call exact( 1, jglob, k, u( 1, 1, j, k ) )
        // end do
        // end do
        // END IF
        //
        // IF (south.eq.-1) then
        // do k = 1, nz
        // do j = 1, ny
        // jglob = jpt + j
        // call exact( nx0, jglob, k, u( 1, nx, j, k ) )
        // end do
        // end do
        // END IF
        //
        // return
        // end

        if (north == -1) {
            for (k = 1; k <= nz; k++) {
                for (j = 1; j <= ny; j++) {
                    jglob = jpt + j;
                    exact(1, jglob, k, u, 1, 1, j, k);
                }
            }
        }

        // dump_u();

        if (south == -1) {
            for (k = 1; k <= nz; k++) {
                for (j = 1; j <= ny; j++) {
                    jglob = jpt + j;
                    exact(nx0, jglob, k, u, 1, nx, j, k);
                }
            }
        }

        // dump_u();
    }

    // c---------------------------------------------------------------------
    // c
    // c set the initial values of independent variables based on tri-linear
    // c interpolation of boundary values in the computational space.
    // c
    // c---------------------------------------------------------------------
    public void setiv() {
        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        // integer i, j, k, m
        // integer iglob, jglob
        // double precision xi, eta, zeta
        // double precision pxi, peta, pzeta
        // double precision ue_1jk(5),ue_nx0jk(5),ue_i1k(5),
        // > ue_iny0k(5),ue_ij1(5),ue_ijnz(5)

        int i, j, k, m;
        int iglob, jglob;
        double xi, eta, zeta;
        double pxi, peta, pzeta;
        Array1Ddouble ue_1jk = new Array1Ddouble(5);
        Array1Ddouble ue_nx0jk = new Array1Ddouble(5);
        Array1Ddouble ue_i1k = new Array1Ddouble(5);
        Array1Ddouble ue_iny0k = new Array1Ddouble(5);
        Array1Ddouble ue_ij1 = new Array1Ddouble(5);
        Array1Ddouble ue_ijnz = new Array1Ddouble(5);

        // do k = 2, nz - 1
        // zeta = ( dble (k-1) ) / (nz-1)
        // do j = 1, ny
        // jglob = jpt + j
        // IF (jglob.ne.1.and.jglob.ne.ny0) then
        // eta = ( dble (jglob-1) ) / (ny0-1)
        // do i = 1, nx
        // iglob = ipt + i
        // IF (iglob.ne.1.and.iglob.ne.nx0) then
        // xi = ( dble (iglob-1) ) / (nx0-1)
        // call exact (1,jglob,k,ue_1jk)
        // call exact (nx0,jglob,k,ue_nx0jk)
        // call exact (iglob,1,k,ue_i1k)
        // call exact (iglob,ny0,k,ue_iny0k)
        // call exact (iglob,jglob,1,ue_ij1)
        // call exact (iglob,jglob,nz,ue_ijnz)
        // do m = 1, 5
        // pxi = ( 1.0e+00 - xi ) * ue_1jk(m)
        // > + xi * ue_nx0jk(m)
        // peta = ( 1.0e+00 - eta ) * ue_i1k(m)
        // > + eta * ue_iny0k(m)
        // pzeta = ( 1.0e+00 - zeta ) * ue_ij1(m)
        // > + zeta * ue_ijnz(m)
        //
        // u( m, i, j, k ) = pxi + peta + pzeta
        // > - pxi * peta - peta * pzeta - pzeta * pxi
        // > + pxi * peta * pzeta
        //
        // end do
        // END IF
        // end do
        // END IF
        // end do
        // end do

        // dump_u();

        for (k = 2; k <= nz - 1; k++) {
            zeta = ((double) k - 1) / (nz - 1);
            for (j = 1; j <= ny; j++) {
                jglob = jpt + j;
                if (jglob != 1 && jglob != ny0) {
                    eta = ((double) jglob - 1) / (ny0 - 1);
                    for (i = 1; i <= nx; i++) {
                        iglob = ipt + i;
                        if (iglob != 1 && iglob != nx0) {
                            xi = ((double) iglob - 1) / (nx0 - 1);
                            exact(1, jglob, k, ue_1jk, 1);
                            exact(nx0, jglob, k, ue_nx0jk, 1);
                            exact(iglob, 1, k, ue_i1k, 1);
                            exact(iglob, ny0, k, ue_iny0k, 1);
                            exact(iglob, jglob, 1, ue_ij1, 1);
                            exact(iglob, jglob, nz, ue_ijnz, 1);
                            for (m = 1; m <= 5; m++) {
                                pxi = (1.0e+00 - xi) * ue_1jk.get(m) + xi * ue_nx0jk.get(m);
                                peta = (1.0e+00 - eta) * ue_i1k.get(m) + eta * ue_iny0k.get(m);
                                pzeta = (1.0e+00 - zeta) * ue_ij1.get(m) + zeta * ue_ijnz.get(m);
                                u.set(m, i, j, k, pxi + peta + pzeta - pxi * peta - peta * pzeta - pzeta * pxi
                                        + pxi * peta * pzeta);
                            }
                        }
                    }
                }
            }
        }

        // dump_u();
    }

    // c---------------------------------------------------------------------
    // c to perform pseudo-time stepping SSOR iterations
    // c for five nonlinear pde's.
    // c---------------------------------------------------------------------

    public double ssor() {

        // c---------------------------------------------------------------------
        // c local variables
        // c---------------------------------------------------------------------
        int i, j, k, m;
        int istep;
        double tmp;
        Array1Ddouble delunm = new Array1Ddouble(5);
        Array3Ddouble tv = new Array3Ddouble(5, isiz1, isiz2);

        // external timer_read;
        double wtime;
        // double timer_read;

        // ROOT = 0;

        // c---------------------------------------------------------------------
        // c begin pseudo-time stepping iterations
        // c---------------------------------------------------------------------
        tmp = 1.0e+00 / (omega * (2.0e+00 - omega));

        // c---------------------------------------------------------------------
        // c initialize a,b,c,d to zero (guarantees that page tables have been
        // c formed, if applicable on given architecture, before timestepping).
        // c---------------------------------------------------------------------
        for (m = 1; m <= isiz2; m++) {
            for (k = 1; k <= isiz1; k++) {
                for (j = 1; j <= 5; j++) {
                    for (i = 1; i <= 5; i++) {
                        a.set(i, j, k, m, 0);
                        b.set(i, j, k, m, 0);
                        c.set(i, j, k, m, 0);
                        d.set(i, j, k, m, 0);
                    }
                }
            }
        }

        // dump_u();
        // dump_rsd();

        // c---------------------------------------------------------------------
        // c compute the steady-state residuals
        // c---------------------------------------------------------------------
        rhs();

        // dump_rsd();
        // c---------------------------------------------------------------------
        // c compute the L2 norms of newton iteration residuals
        // c---------------------------------------------------------------------
        l2norm(isiz1, isiz2, isiz3, nx0, ny0, nz0, ist, iend, jst, jend, rsd, rsdnm);

        if (ipr == 1 && id == 0) {
            Util.printer.p("          Initial residual norms").ln().ln().p(" ").ln()
                    .p(" RMS-norm of steady-state residual for first pde  = ").e(rsdnm.get(1)).p(" ").ln()
                    .p(" RMS-norm of steady-state residual for second pde  = ").e(rsdnm.get(2)).p(" ").ln()
                    .p(" RMS-norm of steady-state residual for third pde  = ").e(rsdnm.get(3)).p(" ").ln()
                    .p(" RMS-norm of steady-state residual for forth pde  = ").e(rsdnm.get(4)).p(" ").ln()
                    .p(" RMS-norm of steady-state residual for fifth pde  = ").e(rsdnm.get(5)).ln().ln()
                    .p("Iteration RMS-residual of 5th PDE").ln();
        }

        // c if ( ipr == 1 .and. id .eq. 0 ) {
        // c write (*,*) ' Initial residual norms'
        // c write (*,*)
        // c write (*,1007) ( rsdnm.get(m), m = 1, 5 )
        // c write (*,'(/a)') 'Iteration RMS-residual of 5th PDE';
        // c }

        MPI.barrier();

        //timer.resetAllTimers();
        //timer.start(1);
        double wtime0 = Util.time();

        // c---------------------------------------------------------------------
        // c the timestep loop
        // c---------------------------------------------------------------------
        for (istep = 1; istep <= itmax; istep++) {

            // c if ( ( mod ( istep, inorm ) == 0 ) .and.
            // c > ( ipr == 1 .and. id .eq. 0 ) ) {
            // c write ( *, 1001 ) istep
            // c }
            if (id == 0) {
                if (istep % 20 == 0 || istep == itmax || istep == 1) {
                    Util.printer.p(" Time step ").p(istep).ln();
                }
            }

            // c---------------------------------------------------------------------
            // c perform SSOR iteration
            // c---------------------------------------------------------------------
            // dump_u();
            // dump_rsd();
            // dump_flux();

            for (k = 2; k <= nz - 1; k++) {
                for (j = jst; j <= jend; j++) {
                    for (i = ist; i <= iend; i++) {
                        for (m = 1; m <= 5; m++) {
                            rsd.set(m, i, j, k, dt * rsd.get(m, i, j, k));
                        }
                    }
                }
            }

            // dump_rsd();

            for (k = 2; k <= nz - 1; k++) {
                // c---------------------------------------------------------------------
                // c form the lower triangular part of the jacobian matrix
                // c---------------------------------------------------------------------
                jacld(k);

                // dump_abcd();

                // c---------------------------------------------------------------------
                // c perform the lower triangular solution
                // c---------------------------------------------------------------------
                blts(isiz1, isiz2, isiz3, nx, ny, nz, k, omega, rsd, a, b, c, d, ist, iend, jst, jend, nx0, ny0, ipt,
                        jpt);

                // dump_rsd();
            }

            // dump_u();
            // dump_rsd();
            // dump_flux();
            // dump_abcd();

            for (k = nz - 1; k >= 2; k--) {
                // c---------------------------------------------------------------------
                // c form the strictly upper triangular part of the jacobian
                // matrix
                // c---------------------------------------------------------------------
                jacu(k);

                // c---------------------------------------------------------------------
                // c perform the upper triangular solution
                // c---------------------------------------------------------------------
                buts(isiz1, isiz2, isiz3, nx, ny, nz, k, omega, rsd, tv, d, a, b, c, ist, iend, jst, jend, nx0, ny0,
                        ipt, jpt);
            }

            // dump_rsd();
            // dump_abcd();

            // c---------------------------------------------------------------------
            // c update the variables
            // c---------------------------------------------------------------------

            for (k = 2; k <= nz - 1; k++) {
                for (j = jst; j <= jend; j++) {
                    for (i = ist; i <= iend; i++) {
                        for (m = 1; m <= 5; m++) {
                            u.set(m, i, j, k, u.get(m, i, j, k) + tmp * rsd.get(m, i, j, k));
                        }
                    }
                }
            }

            // c---------------------------------------------------------------------
            // c compute the max-norms of newton iteration corrections
            // c---------------------------------------------------------------------
            // dump_u();
            // dump_rsd();
            // dump_flux();

            if (istep % inorm == 0) {
                l2norm(isiz1, isiz2, isiz3, nx0, ny0, nz0, ist, iend, jst, jend, rsd, delunm);
                // c if ( ipr == 1 .and. id .eq. 0 ) {
                // c write (*,1006) ( delunm.get(m), m = 1, 5 )
                // c else if ( ipr == 2 .and. id .eq. 0 ) {
                // c write (*,'(i5,f15.6)') istep,delunm.get(5)
                // c }
            }

            // c---------------------------------------------------------------------
            // c compute the steady-state residuals
            // c---------------------------------------------------------------------
            rhs();

            // c---------------------------------------------------------------------
            // c compute the max-norms of newton iteration residuals
            // c---------------------------------------------------------------------
            if (istep % inorm == 0 || istep == itmax) {
                l2norm(isiz1, isiz2, isiz3, nx0, ny0, nz0, ist, iend, jst, jend, rsd, rsdnm);
                // c if ( ipr == 1.and.id.eq.0 ) {
                // c write (*,1007) ( rsdnm.get(m), m = 1, 5 )
                // c }
            }

            // c---------------------------------------------------------------------
            // c check the newton-iteration residuals against the tolerance
            // levels
            // c---------------------------------------------------------------------
            if ((rsdnm.get(1) < tolrsd.get(1)) && (rsdnm.get(2) < tolrsd.get(2)) && (rsdnm.get(3) < tolrsd.get(3))
                    && (rsdnm.get(4) < tolrsd.get(4)) && (rsdnm.get(5) < tolrsd.get(5))) {
                // c if (ipr == 1 .and. id.eq.0) {
                // c write (*,1004) istep
                // c }
                break;
            }

        }

        //timer.stop(1);
        wtime = Util.time();  // timer.readTimer(1);

        maxtime = MPI.allReduce_max(wtime - wtime0);

        return maxtime;

        // 1001 format (1x/5x,'pseudo-time SSOR iteration no.=',i4/);
        // 1004 format (1x/1x,'convergence was achieved after ',i4,;
        // > ' pseudo-time steps' );
        // 1006 format (1x/1x,'RMS-norm of SSOR-iteration correction ',;
        // > 'for first pde = ',1pe12.5/,;
        // > 1x,'RMS-norm of SSOR-iteration correction ',;
        // > 'for second pde = ',1pe12.5/,;
        // > 1x,'RMS-norm of SSOR-iteration correction ',;
        // > 'for third pde = ',1pe12.5/,;
        // > 1x,'RMS-norm of SSOR-iteration correction ',;
        // > 'for fourth pde = ',1pe12.5/,;
        // > 1x,'RMS-norm of SSOR-iteration correction ',;
        // > 'for fifth pde = ',1pe12.5);
        // 1007 format (1x/1x,'RMS-norm of steady-state residual for ',;
        // > 'first pde = ',1pe12.5/,;
        // > 1x,'RMS-norm of steady-state residual for ',;
        // > 'second pde = ',1pe12.5/,;
        // > 1x,'RMS-norm of steady-state residual for ',;
        // > 'third pde = ',1pe12.5/,;
        // > 1x,'RMS-norm of steady-state residual for ',;
        // > 'fourth pde = ',1pe12.5/,;
        // > 1x,'RMS-norm of steady-state residual for ',;
        // > 'fifth pde = ',1pe12.5);
    }

    // c---------------------------------------------------------------------
    // c verification routine
    // c---------------------------------------------------------------------

    public boolean verify(Array1Ddouble xcr, Array1Ddouble xce, double xci) {

        char clss;
        boolean verified;
        Array1Ddouble xcrref = new Array1Ddouble(5);
        Array1Ddouble xceref = new Array1Ddouble(5);
        Array1Ddouble xcrdif = new Array1Ddouble(5);
        Array1Ddouble xcedif = new Array1Ddouble(5);
        double xciref, xcidif, epsilon, dtref;
        int m;

        // c---------------------------------------------------------------------
        // c tolerance level
        // c---------------------------------------------------------------------
        epsilon = 1.0e-08;

        clss = 'U';
        verified = true;

        for (m = 1; m <= 5; m++) {
            xcrref.set(m, 1.0);
            xceref.set(m, 1.0);
        }
        xciref = 1.0;
        dtref = 0;

        if ((nx0 == 12) && (ny0 == 12) && (nz0 == 12) && (itmax == 50)) {

            clss = 'S';
            dtref = 5.0e-1;
            // c---------------------------------------------------------------------
            // c Reference values of RMS-norms of residual, for the (12X12X12)
            // grid,
            // c after 50 time steps, with DT = 5.0e-01
            // c---------------------------------------------------------------------
            xcrref.set(1, 1.6196343210976702e-02);
            xcrref.set(2, 2.1976745164821318e-03);
            xcrref.set(3, 1.5179927653399185e-03);
            xcrref.set(4, 1.5029584435994323e-03);
            xcrref.set(5, 3.4264073155896461e-02);

            // c---------------------------------------------------------------------
            // c Reference values of RMS-norms of solution error, for the
            // (12X12X12) grid,
            // c after 50 time steps, with DT = 5.0e-01
            // c---------------------------------------------------------------------
            xceref.set(1, 6.4223319957960924e-04);
            xceref.set(2, 8.4144342047347926e-05);
            xceref.set(3, 5.8588269616485186e-05);
            xceref.set(4, 5.8474222595157350e-05);
            xceref.set(5, 1.3103347914111294e-03);

            // c---------------------------------------------------------------------
            // c Reference value of surface integral, for the (12X12X12) grid,
            // c after 50 time steps, with DT = 5.0e-01
            // c---------------------------------------------------------------------
            xciref = 7.8418928865937083e+00;

        } else if ((nx0 == 33) && (ny0 == 33) && (nz0 == 33) && (itmax == 300)) {

            clss = 'W'; // !SPEC95fp size;
            dtref = 1.5e-3;
            // c---------------------------------------------------------------------
            // c Reference values of RMS-norms of residual, for the (33x33x33)
            // grid,
            // c after 300 time steps, with DT = 1.5e-3
            // c---------------------------------------------------------------------
            xcrref.set(1, 0.1236511638192e+02);
            xcrref.set(2, 0.1317228477799e+01);
            xcrref.set(3, 0.2550120713095e+01);
            xcrref.set(4, 0.2326187750252e+01);
            xcrref.set(5, 0.2826799444189e+02);

            // c---------------------------------------------------------------------
            // c Reference values of RMS-norms of solution error, for the
            // (33X33X33) grid,
            // c---------------------------------------------------------------------
            xceref.set(1, 0.4867877144216e+00);
            xceref.set(2, 0.5064652880982e-01);
            xceref.set(3, 0.9281818101960e-01);
            xceref.set(4, 0.8570126542733e-01);
            xceref.set(5, 0.1084277417792e+01);

            // c---------------------------------------------------------------------
            // c Reference value of surface integral, for the (33X33X33) grid,
            // c after 300 time steps, with DT = 1.5e-3
            // c---------------------------------------------------------------------
            xciref = 0.1161399311023e+02;

        } else if ((nx0 == 64) && (ny0 == 64) && (nz0 == 64) && (itmax == 250)) {

            clss = 'A';
            dtref = 2.0e+0;
            // c---------------------------------------------------------------------
            // c Reference values of RMS-norms of residual, for the (64X64X64)
            // grid,
            // c after 250 time steps, with DT = 2.0e+00
            // c---------------------------------------------------------------------
            xcrref.set(1, 7.7902107606689367e+02);
            xcrref.set(2, 6.3402765259692870e+01);
            xcrref.set(3, 1.9499249727292479e+02);
            xcrref.set(4, 1.7845301160418537e+02);
            xcrref.set(5, 1.8384760349464247e+03);

            // c---------------------------------------------------------------------
            // c Reference values of RMS-norms of solution error, for the
            // (64X64X64) grid,
            // c after 250 time steps, with DT = 2.0e+00
            // c---------------------------------------------------------------------
            xceref.set(1, 2.9964085685471943e+01);
            xceref.set(2, 2.8194576365003349e+00);
            xceref.set(3, 7.3473412698774742e+00);
            xceref.set(4, 6.7139225687777051e+00);
            xceref.set(5, 7.0715315688392578e+01);

            // c---------------------------------------------------------------------
            // c Reference value of surface integral, for the (64X64X64) grid,
            // c after 250 time steps, with DT = 2.0e+00
            // c---------------------------------------------------------------------
            xciref = 2.6030925604886277e+01;

        } else if ((nx0 == 102) && (ny0 == 102) && (nz0 == 102) && (itmax == 250)) {

            clss = 'B';
            dtref = 2.0e+0;

            // c---------------------------------------------------------------------
            // c Reference values of RMS-norms of residual, for the
            // (102X102X102) grid,
            // c after 250 time steps, with DT = 2.0e+00
            // c---------------------------------------------------------------------
            xcrref.set(1, 3.5532672969982736e+03);
            xcrref.set(2, 2.6214750795310692e+02);
            xcrref.set(3, 8.8333721850952190e+02);
            xcrref.set(4, 7.7812774739425265e+02);
            xcrref.set(5, 7.3087969592545314e+03);

            // c---------------------------------------------------------------------
            // c Reference values of RMS-norms of solution error, for the
            // (102X102X102)
            // c grid, after 250 time steps, with DT = 2.0e+00
            // c---------------------------------------------------------------------
            xceref.set(1, 1.1401176380212709e+02);
            xceref.set(2, 8.1098963655421574e+00);
            xceref.set(3, 2.8480597317698308e+01);
            xceref.set(4, 2.5905394567832939e+01);
            xceref.set(5, 2.6054907504857413e+02);

            // c---------------------------------------------------------------------
            // c Reference value of surface integral, for the (102X102X102)
            // grid,
            // c after 250 time steps, with DT = 2.0e+00
            // c---------------------------------------------------------------------
            xciref = 4.7887162703308227e+01;

        } else if ((nx0 == 162) && (ny0 == 162) && (nz0 == 162) && (itmax == 250)) {

            clss = 'C';
            dtref = 2.0e+0;

            // c---------------------------------------------------------------------
            // c Reference values of RMS-norms of residual, for the
            // (162X162X162) grid,
            // c after 250 time steps, with DT = 2.0e+00
            // c---------------------------------------------------------------------
            xcrref.set(1, 1.03766980323537846e+04);
            xcrref.set(2, 8.92212458801008552e+02);
            xcrref.set(3, 2.56238814582660871e+03);
            xcrref.set(4, 2.19194343857831427e+03);
            xcrref.set(5, 1.78078057261061185e+04);

            // c---------------------------------------------------------------------
            // c Reference values of RMS-norms of solution error, for the
            // (162X162X162)
            // c grid, after 250 time steps, with DT = 2.0e+00
            // c---------------------------------------------------------------------
            xceref.set(1, 2.15986399716949279e+02);
            xceref.set(2, 1.55789559239863600e+01);
            xceref.set(3, 5.41318863077207766e+01);
            xceref.set(4, 4.82262643154045421e+01);
            xceref.set(5, 4.55902910043250358e+02);

            // c---------------------------------------------------------------------
            // c Reference value of surface integral, for the (162X162X162)
            // grid,
            // c after 250 time steps, with DT = 2.0e+00
            // c---------------------------------------------------------------------
            xciref = 6.66404553572181300e+01;

        } else if ((nx0 == 408) && (ny0 == 408) && (nz0 == 408) && (itmax == 300)) {

            clss = 'D';
            dtref = 1.0e+0;

            // c---------------------------------------------------------------------
            // c Reference values of RMS-norms of residual, for the
            // (408X408X408) grid,
            // c after 300 time steps, with DT = 1.0e+00
            // c---------------------------------------------------------------------
            xcrref.set(1, 0.4868417937025e+05);
            xcrref.set(2, 0.4696371050071e+04);
            xcrref.set(3, 0.1218114549776e+05);
            xcrref.set(4, 0.1033801493461e+05);
            xcrref.set(5, 0.7142398413817e+05);

            // c---------------------------------------------------------------------
            // c Reference values of RMS-norms of solution error, for the
            // (408X408X408)
            // c grid, after 300 time steps, with DT = 1.0e+00
            // c---------------------------------------------------------------------
            xceref.set(1, 0.3752393004482e+03);
            xceref.set(2, 0.3084128893659e+02);
            xceref.set(3, 0.9434276905469e+02);
            xceref.set(4, 0.8230686681928e+02);
            xceref.set(5, 0.7002620636210e+03);

            // c---------------------------------------------------------------------
            // c Reference value of surface integral, for the (408X408X408)
            // grid,
            // c after 300 time steps, with DT = 1.0e+00
            // c---------------------------------------------------------------------
            xciref = 0.8334101392503e+02;

        } else {

            verified = false;

        }

        // c---------------------------------------------------------------------
        // c verification test for residuals if gridsize is either 12X12X12 or
        // c 64X64X64 or 102X102X102 or 162X162X162 or 408X408X408
        // c---------------------------------------------------------------------

        // c---------------------------------------------------------------------
        // c Compute the difference of solution values and the known reference
        // values.
        // c---------------------------------------------------------------------
        for (m = 1; m <= 5; m++) {

            xcrdif.set(m, Math.abs((xcr.get(m) - xcrref.get(m)) / xcrref.get(m)));
            xcedif.set(m, Math.abs((xce.get(m) - xceref.get(m)) / xceref.get(m)));

        }
        xcidif = Math.abs((xci - xciref) / xciref);

        // c---------------------------------------------------------------------
        // c Output the comparison of computed results to known cases.
        // c---------------------------------------------------------------------

        if (clss != 'U') {

            Util.printer.p(" Verification being performed for class ").p(clss).ln();
            Util.printer.p(" Accuracy setting for epsilon = ").p(epsilon).ln();

            if (Math.abs(dt - dtref) > epsilon) {
                verified = false;
                clss = 'U';
                Util.printer.p(" DT does not match the reference value of ").p(dtref).ln();
            }

        } else {

            Util.printer.p(" Unknown class").ln();

        }

        /*
         * write(*, 1990) clss; 1990 format(/, ' Verification being performed
         * for class ', a); write (*,2000) epsilon; 2000 format(' Accuracy
         * setting for epsilon = ', E20.13); if (dabs(dt-dtref) > epsilon) { ;
         * verified = .false.; clss = 'U'; write (*,1000) dtref; 1000 format('
         * DT does not match the reference value of ', E15.8); } else ; write(*,
         * 1995); 1995 format(' Unknown class');
         */

        if (clss != 'U') {
            // write (*,2001) ;
            Util.printer.p(" Comparison of RMS-norms of residual").ln();
        } else {
            // write (*, 2005);
            Util.printer.p(" RMS-norms of residual").ln();
        }

        // 2001 format(' Comparison of RMS-norms of residual');
        // 2005 format(' RMS-norms of residual');

        for (m = 1; m <= 5; m++) {
            if (clss == 'U') {
                Util.printer.p("          ").p(m).e(xcr.get(m)).ln();
                // write(*, 2015) m, xcr.get(m);
            } else if (xcrdif.get(m) > epsilon) {
                verified = false;
                Util.printer.p(" FAILURE: ").p(m).e(xcr.get(m)).e(xcrref.get(m)).e(xcrdif.get(m)).ln();
                // write (*,2010) m,xcr.get(m),xcrref.get(m),xcrdif.get(m);
            } else {
                Util.printer.p("          ").p(m).e(xcr.get(m)).e(xcrref.get(m)).e(xcrdif.get(m)).ln();
                // write (*,2011) m,xcr.get(m),xcrref.get(m),xcrdif.get(m);
            }
        }

        if (clss != 'U') {
            Util.printer.p(" Comparison of RMS-norms of solution error").ln();
            // write (*,2002);
        } else {
            Util.printer.p(" RMS-norms of solution error").ln();
            // write (*,2006);
        }
        // 2002 format(' Comparison of RMS-norms of solution error');
        // 2006 format(' RMS-norms of solution error');

        for (m = 1; m <= 5; m++) {
            if (clss == 'U') {
                Util.printer.p("          ").p(m).e(xce.get(m)).ln();
                // write(*, 2015) m, xce.get(m);
            } else if (xcedif.get(m) > epsilon) {
                verified = false;
                Util.printer.p(" FAILURE: ").p(m).e(xce.get(m)).e(xceref.get(m)).e(xcedif.get(m)).ln();
                // write (*,2010) m,xce.get(m),xceref.get(m),xcedif.get(m);
            } else {
                Util.printer.p("          ").p(m).e(xce.get(m)).e(xceref.get(m)).e(xcedif.get(m)).ln();
                // write (*,2011) m,xce.get(m),xceref.get(m),xcedif.get(m);
            }
        }

        // 2010 format(' FAILURE: ', i2, 2x, E20.13, E20.13, E20.13);
        // 2011 format(' ', i2, 2x, E20.13, E20.13, E20.13);
        // 2015 format(' ', i2, 2x, E20.13);

        if (clss != 'U') {
            Util.printer.p(" Comparison of surface integral").ln();
            // write (*,2025);
        } else {
            Util.printer.p(" Surface integral").ln();
            // write (*,2026);
        }
        // 2025 format(' Comparison of surface integral');
        // 2026 format(' Surface integral');

        if (clss == 'U') {
            Util.printer.p("          ").e(xci).ln();
            // write(*, 2030) xci;
        } else if (xcidif > epsilon) {
            verified = false;
            Util.printer.p(" FAILURE: ").e(xci).e(xciref).e(xcidif).ln();
            // write(*, 2031) xci, xciref, xcidif;
        } else {
            Util.printer.p("          ").e(xci).e(xciref).e(xcidif).ln();
            // write(*, 2032) xci, xciref, xcidif;
        }

        // 2030 format(' ', 4x, E20.13);
        // 2031 format(' FAILURE: ', 4x, E20.13, E20.13, E20.13);
        // 2032 format(' ', 4x, E20.13, E20.13, E20.13);

        if (clss == 'U') {
            Util.printer.p(" No reference values provided").ln();
            Util.printer.p(" No verification performed").ln();
            // write(*, 2022);
            // write(*, 2023);
            // 2022 format(' No reference values provided');
            // 2023 format(' No verification performed');
        } else if (verified) {
            Util.printer.p(" Verification Successful").ln();
            // write(*, 2020);
            // 2020 format(' Verification Successful');
        } else {
            Util.printer.p(" Verification failed").ln();
            // write(*, 2021);
            // 2021 format(' Verification failed');
        }

        return verified;
    }

    /*
     * public void checksum(double array[], int size, String arrayname, boolean
     * stop) { double sum = 0; for (int i = 0; i < size; i++) { sum += array[i];
     * } System.out.println("array:" + arrayname + " checksum is: " + sum); if
     * (stop) System.exit(0); }
     * 
     * public double checkSum(double arr[]) { double csum = 0.0; for (int k = 0;
     * k <= nz - 1; k++) { for (int j = 0; j <= ny - 1; j++) { for (int i = 0; i
     * <= nx - 1; i++) { for (int m = 0; m <= 4; m++) { int offset = m + i *
     * isize1 + j * jsize1 + k * ksize1; csum += (arr[offset] * arr[offset]) /
     * (double) (nx * ny * nz * 5); } } } } return csum; }
     */

    public double getTime() {
        return timer.readTimer(1);
    }

    public void finalize() throws Throwable {
        System.out.println("LU: is about to be garbage collected");
        super.finalize();
    }
}
