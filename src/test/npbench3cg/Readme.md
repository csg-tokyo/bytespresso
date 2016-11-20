### NAS Parallel Benchmarks 3.0 CG for MPI

This is a program to run the CG benchmark with vector and
matrix objects.  It was implemented to demonstrate the power
of Bytespresso.

The original CG benchmark is written in Fortran and
no abstraction or modularization is exploited in the source code. 
The program here is written in Java with a vector and matrix library
for MPI environment.  Since vector and matrix objects provided by
the library encapsulate data distribution and inter-node communication,
the size of the main program `CG.java` is less than half of the original size in Fortran.

The program is divided into two parts.  The first part is for preparation.
It constructs matrix and vector objects in Java.  The latter part is offloaded
to MPI with those Java objects together.  During runtime, it is dynamically
translated into a C program using MPI, compiled, and executed.
The most part of the benchmark program is included in the latter part.
This two-stage execution enables execution performance comparable to the original
Fortran program's.

The program first constructs several vectors and a matrix:

    // CG(char, int)
    dsl = new Dsl(na, numProcRows);
    mat = dsl.sparseMatrix(nz + 1);
    p = dsl.vector();
    q = dsl.vector();
    r = dsl.vector();
    x = dsl.vector();
    z = dsl.vector();

`dsl` refers to an object representing a vector DSL (domain specific language).
We call it *a language* but actually it is a library providing a language-like
programming interface.  Since such a library is often categorized into an embedded
(or library-based) DSL, we also call it a DSL.
`mat` is a matrix object and `p`, `q`, ... are vector objects.  They are
provided by the vector DSL library.  See `Matrix.java` and `Vector.java` for details.
They represent a matrix or a vector on multiple MPI processes and support basic
arithmetic methods.  They encapsulate all MPI communication necessary for computing
these methods.

Then the program translates the `run` method of a `Runner` object into a C program:

    cg.dsl.compile(new Runner(cg));

It also translates `runBenchmark` method in `CG` as well since the `run` method calls it.
The body of `runBenchmark`is the main routine of the CG benchmark.  It is a fairly direct
mapping from the original Fortran program. 
The matrix `mat` and the vectors `p`, `q`, and so on, which have been already constructed,
are also translated into C arrays.

The`runBenchmark` method first executes the initialization of the matrix and vectors:

    // runBenchMark()
    iv = new int[2 * (na + 1)];
    arow = new int[nz + 1];
    acol = new int[nz + 1];
    v = new double[na + 2];
    aelt = new double[nz + 1];
      :
    double rnorm = 0;
    double zeta = randGen.randlc(amult);
    makea(na, nz, mat, nonzer, rcond, arow, acol, aelt, v, iv, shift);
    mat.shift(dsl.firstcol - 1);

The `makea` method is equivalent to the `makea` subroutine in the original Fortran program.
It initializes the matrix `mat`.
Since the translated program runs in the MPI environment in the SIMD style,
`makea` initializes only part of the matrix elements located on a local node.
To demonstrate that we can be conscious of data distribution when accessing a matrix object,
we keep the original implementation of the Fortran program.

In the rest of the program, however, data distribution is abstracted out.  The main iteration
of the CG method is expressed as follows:

    rnorm = conj_grad(x, z, mat, p, q, r, rnorm);
    double tnorm1 = inner(x, z);
    double tnorm2 = 1.0 / Util.sqrt(z.norm());
    zeta = shift + 1.0 / tnorm1;
    x.set(tnorm2, z);                  // x = tnorm2 * z

The body of the `conj_grad` method is as follows:

    q.set(0.0);
    z.set(0.0);
    r.set(x);
    p.set(r);
    double rho = r.norm();
    for (int cgit = 1; cgit <= cgitmax; cgit++) {
        q.setToMult(a, p);             // q = A * p 
        double d = inner(p, q);        // d = p * q
        double alpha = rho / d;
        z.setToAdd(z, alpha, p);       // z = z + alpha * p
        r.setToAdd(r, -alpha, q);      // r = r - alpha * q
        double rho0 = rho;
        rho = r.norm();
        double beta = rho / rho0;
        p.setToAdd(r, beta, p);        // p = r * beta * p
    }
    r.setToMult(a, z);                 // r = a * z
    r.setToSub(x, r);                  // r = x - r
    double sum = r.norm();
    return Util.sqrt(sum);

The implementation of matrix-vector and vector-vector arithmetic is
encapsulated into the corresponding methods such as `setToMult`.
We do not have to care about MPI communication. 

#### How to run

When running it, give arguments as:

    java CG class=A np=2

This specifies the problem size is A and a matrix is partitioned
by 2 row-wise block striping.  Since the program generates
`bytespresso.c` after execution, compile it by:

    mpicc bytespresso.c

Then run by, for example,

    mpiexec -n 8 ./a.out

The total number of MPI processes are given by the `-n` option
(it is 8 in the case above).
For example, if it is 8 and `np=2` is given, a matrix is
partitioned by 2 row-wise and 4 column-wise block striping.

When `-java` option is given to the program, the benchmark
is run with multiple Java threads and MPI emulation on the JVM.
The number of emulated MPI processes is 2 * `np`.  For example,
if `np=3` is given, 6 MPI processes are created.
 
This program can be run without MPI.  Give `np=0` when running the
program.  It runs with a single thread on either the JVM or a native
CPU.
 