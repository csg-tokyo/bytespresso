### NAS Parallel Benchmarks 3.0 CG for MPI

This is a program to run the CG benchmark with vector and
matrix objects.  When running it, give arguments as:

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
 