/*
!-------------------------------------------------------------------------!
!									  !
!	 N  A  S     P A R A L L E L	 B E N C H M A R K S  3.0	  !
!									  !
!			J A V A 	V E R S I O N			  !
!									  !
!                               L U B a s e                               !
!                                                                         !
!-------------------------------------------------------------------------!
!                                                                         !
!    LUbase implements base class for LU benchmark.                       !
!									  !
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
!     Translation to Java and to MultiThreaded Code:			  !
!     Michael A. Frumkin					          !
!     Mathew Schultz	   					          !
!-------------------------------------------------------------------------!
*/
package npbench3lu;

import npbench3lu.arrayXD.*;

public class LUBase implements Cloneable {

    // ---------------------------------------------------------------------
    // See nbpparams.h
    // ---------------------------------------------------------------------
    // c number of nodes for which this version is compiled
    // integer nnodes_compiled
    // parameter (nnodes_compiled = 1)
    //
    // c full problem size
    // integer isiz01, isiz02, isiz03
    // parameter (isiz01=12, isiz02=12, isiz03=12)
    //
    // c sub-domain array size
    // integer isiz1, isiz2, isiz3
    // parameter (isiz1=12, isiz2=12, isiz3=isiz03)
    //
    // c number of iterations and how often to print the norm
    // integer itmax_default, inorm_default
    // parameter (itmax_default=50, inorm_default=50)
    // double precision dt_default
    // parameter (dt_default = 0.5d0)
    // logical convertdouble
    // parameter (convertdouble = .false.)
    // character*11 compiletime
    // parameter (compiletime='22 Dec 2016')
    // character*5 npbversion
    // parameter (npbversion='2.4.1')
    // character*6 cs1
    // parameter (cs1='mpif77')
    // character*6 cs2
    // parameter (cs2='mpif77')
    // character*31 cs3
    // parameter (cs3='-L/usr/local/mh/mpich/lib -lmpi')
    // character*20 cs4
    // parameter (cs4='-I/usr/local/include')
    // character*4 cs5
    // parameter (cs5='-O3 ')
    // character*6 cs6
    // parameter (cs6='(none)')
    // character*6 cs7
    // parameter (cs7='randi8')

    public static final String BMName = "LU";
    public final char clazz;

    public final int nnodes_compiled;
//    protected final int isiz01 = 12, isiz02 = 12, isiz03 = 12;
    protected final int isiz01, isiz02, isiz03;
    //protected final int isiz1 = 12, isiz2 = 12, isiz3 = isiz03;
    protected final int isiz1, isiz2, isiz3;

//    protected final int itmax_default = 50, inorm_default = 50;
    protected final int itmax_default, inorm_default;
//    protected final double dt_default = 0.5;
    protected final double dt_default;
    protected final boolean convertdouble = false;

    // ---------------------------------------------------------------------
    // See applu.incl
    // ---------------------------------------------------------------------
    protected final int ipr_default = 1;
    protected static final double omega_default = 1.2, tolrsd1_def = .00000001, tolrsd2_def = .00000001,
            tolrsd3_def = .00000001, tolrsd4_def = .00000001, tolrsd5_def = .00000001, c1 = 1.4, c2 = 0.4, c3 = .1,
            c4 = 1, c5 = 1.4;
    // ---------------------------------------------------------------------
    // grid
    // ---------------------------------------------------------------------
    protected int nx, ny, nz;
    protected int nx0, ny0, nz0;
    protected int ipt, ist, iend;
    protected int jst, jend;
    protected int jpt, ii1, ii2;
    protected int ji1, ji2;
    protected int ki1, ki2;
    protected double dxi, deta, dzeta;
    protected double tx1, tx2, tx3;
    protected double ty1, ty2, ty3;
    protected double tz1, tz2, tz3;

    // ---------------------------------------------------------------------
    // dissipation
    // ---------------------------------------------------------------------
    protected double dx1, dx2, dx3, dx4, dx5;
    protected double dy1, dy2, dy3, dy4, dy5;
    protected double dz1, dz2, dz3, dz4, dz5;
    protected double dssp;

    // ---------------------------------------------------------------------
    // field variables and residuals
    // to improve cache performance, second two dimensions padded by 1
    // for even number sizes only.
    // Note: corresponding array (called "v") in routines blts, buts,
    // and l2norm are similarly padded
    // ---------------------------------------------------------------------
    protected final Array4Ddouble u, rsd, frct, flux;

    // ---------------------------------------------------------------------
    // output control parameters
    // ---------------------------------------------------------------------
    protected static int ipr, inorm;

    // ---------------------------------------------------------------------
    // newton-raphson iteration control parameters
    // ---------------------------------------------------------------------
    protected int itmax, invert;
    protected double dt, omega, frc, ttotal;
    protected final Array1Ddouble tolrsd = new Array1Ddouble(5);
    protected final Array1Ddouble rsdnm = new Array1Ddouble(5);
    protected final Array1Ddouble errnm = new Array1Ddouble(5);

    protected final Array4Ddouble a, b, c, d;

    // ---------------------------------------------------------------------
    // coefficients of the exact solution
    // ---------------------------------------------------------------------
    // protected static double ce[] ={
    // 2.0, 1.0, 2.0, 2.0, 5.0,
    // 0.0, 0.0, 2.0, 2.0, 4.0,
    // 0.0, 0.0, 0.0, 0.0, 3.0,
    // 4.0, 0.0, 0.0, 0.0, 2.0,
    // 5.0, 1.0, 0.0, 0.0, .1 ,
    // 3.0, 2.0, 2.0, 2.0, .4 ,
    // .5 , 3.0, 3.0, 3.0, .3 ,
    // .02, .01, .04, .03, .05,
    // .01, .03, .03, .05, .04,
    // .03, .02, .05, .04, .03,
    // .5 , .4 , .3 , .2 , .1 ,
    // .4 , .3 , .5 , .1 , .3 ,
    // .3 , .5 , .4 , .3 , .2
    // };
    protected final Array2Ddouble ce = new Array2Ddouble(5, 13);

    // ---------------------------------------------------------------------
    // multi-processor common blocks
    // ---------------------------------------------------------------------
    // integer id, ndim, num, xdim, ydim, row, col
    // common/dim/ id,ndim,num,xdim,ydim,row,col
    //
    // integer north,south,east,west
    // common/neigh/ north,south,east, west
    //
    // integer from_s,from_n,from_e,from_w
    // parameter (from_s=1,from_n=2,from_e=3,from_w=4)
    //
    // integer npmax
    // parameter (npmax=isiz01+isiz02)
    //
    // logical icommn(npmax+1),icomms(npmax+1),
    // > icomme(npmax+1),icommw(npmax+1)
    // double precision buf(5,2*isiz2*isiz3),
    // > buf1(5,2*isiz2*isiz3)
    //
    // common/comm/ buf, buf1,
    // > icommn,icomms,
    // > icomme,icommw
    //
    // double precision maxtime
    // common/timer/maxtime

    protected int id, ndim, num, xdim, ydim, row, col;
    protected int north, south, east, west;
    protected int from_s = 1, from_n = 2, from_e = 3, from_w = 4;
    //protected int npmax = isiz01 + isiz02;
    protected final int npmax;
    protected final Array1Dboolean icommn, icomms, icomme, icommw;
    protected final Array2Ddouble buf, buf1;

    protected double maxtime;

    // ---------------------------------------------------------------------
    // timers
    // ---------------------------------------------------------------------
    public static final int t_total = 1, t_rhsx = 2, t_rhsy = 3, t_rhsz = 4, t_rhs = 5, t_jacld = 6, t_blts = 7,
            t_jacu = 8, t_buts = 9, t_add = 10, t_l2norm = 11, t_last = 11;
    public boolean timeron;
    public Timer timer = new Timer();

    //public LUBase() {
    //};

    public LUBase(char cls, int nprocs) {

        int problem_size = 12;
        int xdiv, ydiv; /* number of cells in x and y direction */

        clazz = cls;

        switch (cls) {
        case 'S':
            problem_size = 12;
            itmax_default = inorm_default = 50;
            dt_default = .5;
            break;
        case 'W':
            problem_size = 33;
            itmax_default = inorm_default = 300;
            dt_default = .0015;
            break;
        case 'A':
            problem_size = 64;
            itmax_default = inorm_default = 250;
            dt_default = 2;
            break;
        case 'B':
            problem_size = 102;
            itmax_default = inorm_default = 250;
            dt_default = 2;
            break;
        case 'C':
            problem_size = 162;
            itmax_default = inorm_default = 250;
            dt_default = 2;
            break;
        case 'D':
            problem_size = 408;
            itmax_default = inorm_default = 300;
            dt_default = 1;
            break;
        default:
            problem_size = 12;
            itmax_default = inorm_default = 50;
            dt_default = .5;
        	System.out.println("class should be one of S, W, A, B, C, D");
        	System.exit(1);
        }

        int logprocs;

        logprocs = ilog2(nprocs);
        if (logprocs < 0) {
            // printf("setparams: Number of processors must be a power of two
            // (1,2,4,...) for this benchmark\n");
            // exit(1);
        	System.out.println("Number of processors must be a power of two (1,2,4,...) for this benchmark");
        	System.exit(1);
            nprocs = 1;
        }

        // The following code is based on the code in write_lu_info() in
        // sys/setparams.
        xdiv = ydiv = ilog2(nprocs) / 2;
        if (xdiv + ydiv != ilog2(nprocs))
            xdiv += 1;
        xdiv = ipow2(xdiv);
        ydiv = ipow2(ydiv);
        if (problem_size / xdiv * xdiv < problem_size) {
        	isiz1 = problem_size / xdiv + 1;
        } else {
        	isiz1 = problem_size / xdiv;
        }
        if (problem_size / ydiv * ydiv < problem_size) {
        	isiz2 = problem_size / ydiv + 1;
        } else {
        	isiz2 = problem_size / ydiv;
        }
        isiz01 = isiz02 = isiz03 = problem_size;
        isiz3 = isiz03;
        
        nnodes_compiled = nprocs;

        // Array initialization

        // double precision u(5,-1:isiz1+2,-1:isiz2+2,isiz3),
        // > rsd(5,-1:isiz1+2,-1:isiz2+2,isiz3),
        // > frct(5,-1:isiz1+2,-1:isiz2+2,isiz3),
        // > flux(5,0:isiz1+1,0:isiz2+1,isiz3)

        u = new Array4Ddouble(1, 5, -1, isiz1 + 2, -1, isiz2 + 2, 1, isiz3);
        rsd = new Array4Ddouble(1, 5, -1, isiz1 + 2, -1, isiz2 + 2, 1, isiz3);
        frct = new Array4Ddouble(1, 5, -1, isiz1 + 2, -1, isiz2 + 2, 1, isiz3);
        flux = new Array4Ddouble(1, 5, 0, isiz1 + 1, 0, isiz2 + 1, 1, isiz3);

        // double precision a(5,5,isiz1,isiz2),
        // > b(5,5,isiz1,isiz2),
        // > c(5,5,isiz1,isiz2),
        // > d(5,5,isiz1,isiz2)

        a = new Array4Ddouble(5, 5, isiz1, isiz2);
        b = new Array4Ddouble(5, 5, isiz1, isiz2);
        c = new Array4Ddouble(5, 5, isiz1, isiz2);
        d = new Array4Ddouble(5, 5, isiz1, isiz2);

        // integer npmax
        // parameter (npmax=isiz01+isiz02)
        // logical icommn(npmax+1),icomms(npmax+1),
        // > icomme(npmax+1),icommw(npmax+1)
        // double precision buf(5,2*isiz2*isiz3),
        // > buf1(5,2*isiz2*isiz3)

        npmax = isiz01 + isiz02;
        icommn = new Array1Dboolean(npmax + 1);
        icomms = new Array1Dboolean(npmax + 1);
        icomme = new Array1Dboolean(npmax + 1);
        icommw = new Array1Dboolean(npmax + 1);
        buf = new Array2Ddouble(5, 2 * isiz2 * isiz3);
        buf1 = new Array2Ddouble(5, 2 * isiz2 * isiz3);

    }

    public void checksum(double array[], int size, String arrayname, boolean stop) {

        double sum = 0;
        for (int i = 0; i < size; i++)
            sum += array[i];
        System.out.println("array:" + arrayname + " checksum is: " + sum);
        if (stop)
            System.exit(0);
    }

    public void set_interval(int threads, int problem_size, int interval[]) {
        interval[0] = problem_size / threads;
        for (int i = 1; i < threads; i++)
            interval[i] = interval[0];
        int remainder = problem_size % threads;
        for (int i = 0; i < remainder; i++)
            interval[i]++;
    }

    // c---------------------------------------------------------------------
    // c input parameters
    // c---------------------------------------------------------------------
    // integer i, j, k
    // double precision u000ijk(*)
    //
    // c---------------------------------------------------------------------
    // c local variables
    // c---------------------------------------------------------------------
    // integer m
    // double precision xi, eta, zeta
    //
    // xi = ( dble ( i - 1 ) ) / ( nx0 - 1 )
    // eta = ( dble ( j - 1 ) ) / ( ny0 - 1 )
    // zeta = ( dble ( k - 1 ) ) / ( nz - 1 )
    //
    //
    // do m = 1, 5
    // u000ijk(m) = ce(m,1)
    // > + ce(m,2) * xi
    // > + ce(m,3) * eta
    // > + ce(m,4) * zeta
    // > + ce(m,5) * xi * xi
    // > + ce(m,6) * eta * eta
    // > + ce(m,7) * zeta * zeta
    // > + ce(m,8) * xi * xi * xi
    // > + ce(m,9) * eta * eta * eta
    // > + ce(m,10) * zeta * zeta * zeta
    // > + ce(m,11) * xi * xi * xi * xi
    // > + ce(m,12) * eta * eta * eta * eta
    // > + ce(m,13) * zeta * zeta * zeta * zeta
    // end do
    //
    // return
    // end

    public void exact(int i, int j, int k, Array4Ddouble u, int ux, int uy, int uz, int uw) {
        double xi = (double) (i - 1) / (double) (nx0 - 1);
        double eta = (double) (j - 1) / (double) (ny0 - 1);
        double zeta = (double) (k - 1) / (double) (nz - 1);

        // Util.printer.p("xi ").e(xi).p(" eta ").e(eta).p(" zeta
        // ").e(zeta).ln();

        for (int m = 1; m <= 5; m++) {
            u.set(ux + m - 1, uy, uz, uw,
                    ce.get(m, 1) + ce.get(m, 2) * xi + ce.get(m, 3) * eta + ce.get(m, 4) * zeta + ce.get(m, 5) * xi * xi
                            + ce.get(m, 6) * eta * eta + ce.get(m, 7) * zeta * zeta + ce.get(m, 8) * xi * xi * xi
                            + ce.get(m, 9) * eta * eta * eta + ce.get(m, 10) * zeta * zeta * zeta
                            + ce.get(m, 11) * xi * xi * xi * xi + ce.get(m, 12) * eta * eta * eta * eta
                            + ce.get(m, 13) * zeta * zeta * zeta * zeta);
        }
    }

    public void exact(int i, int j, int k, Array1Ddouble u, int ux) {
        double xi = (double) (i - 1) / (nx0 - 1);
        double eta = (double) (j - 1) / (ny0 - 1);
        double zeta = (double) (k - 1) / (nz - 1);

        for (int m = 1; m <= 5; m++) {
            u.set(ux + m - 1,
                    ce.get(m, 1) + ce.get(m, 2) * xi + ce.get(m, 3) * eta + ce.get(m, 4) * zeta + ce.get(m, 5) * xi * xi
                            + ce.get(m, 6) * eta * eta + ce.get(m, 7) * zeta * zeta + ce.get(m, 8) * xi * xi * xi
                            + ce.get(m, 9) * eta * eta * eta + ce.get(m, 10) * zeta * zeta * zeta
                            + ce.get(m, 11) * xi * xi * xi * xi + ce.get(m, 12) * eta * eta * eta * eta
                            + ce.get(m, 13) * zeta * zeta * zeta * zeta);
        }
    }

    protected double max(double a, double b) {
        if (a < b)
            return b;
        else
            return a;
    }

    protected double max(double a, double b, double c) {
        return max(a, max(b, c));
    }

    /*
     * integer log base two. Return error is argument isn't a power of two or is
     * less than or equal to zero
     */

    int ilog2(int i) {
        int log2;
        int exp2 = 1;
        if (i <= 0)
            return (-1);

        for (log2 = 0; log2 < 20; log2++) {
            if (exp2 == i)
                return (log2);
            exp2 *= 2;
        }
        return (-1);
    }

    int ipow2(int i) {
        int pow2 = 1;
        if (i < 0)
            return (-1);
        if (i == 0)
            return (1);
        while (i-- > 0)
            pow2 *= 2;
        return (pow2);
    }

}
