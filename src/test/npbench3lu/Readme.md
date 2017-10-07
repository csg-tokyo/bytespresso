### NAS Parallel Benchmarks 3.0 LU for MPI

This is a program to run the LU benchmark to demonstrate the power
of Bytespresso.  It is a fairly straightforward translation of
the original Fortran program.

#### How to run

When running it, give arguments as:

    java npbench3lu.LU class=A np=8

This specifies the problem size is A and the program will run
with eight MPI processes.  Since the program generates
`bytespresso.c` after execution, compile it by:

    mpicc -O2 bytespresso.c

Then run by, for example,

    mpiexec -n 8 ./a.out

The total number of MPI processes are given by the `-n` option
(it is 8 in the case above) again.
