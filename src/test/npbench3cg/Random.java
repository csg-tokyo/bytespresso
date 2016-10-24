/*
    N  A  S    P A R A L L E L    B E N C H M A R K S  3.0

    J A V A    V E R S I O N

    R A N D O M

    This benchmark is a serial version of the NPB3_0_JAV Random number
    generating code.

    Permission to use, copy, distribute and modify this software
    for any purpose with or without fee is hereby granted.  We
    request, however, that all derived work reference the NAS
    Parallel Benchmarks 3.0. This software is provided "as is"
    without express or implied warranty.

    Information on NPB 3.0, including the Technical Report NAS-02-008
    "Implementation of the NAS Parallel Benchmarks in Java",
    original specifications, source code, results and information
    on how to submit new results, is available at:

    http://www.nas.nasa.gov/Software/NPB/

    Send comments or suggestions to  npb@nas.nasa.gov

    NAS Parallel Benchmarks Group
	NASA Ames Research Center
	Mail Stop: T27A-1
	Moffett Field, CA   94035-1000

    E-mail:  npb@nas.nasa.gov
	Fax:     (650) 604-3957

    Translation to Java and to MultiThreaded Code:
    Michael A. Frumkin

    modified by Shigeru Chiba in 2016
*/
package npbench3cg;

import javassist.offload.lib.Util;

public class Random implements Cloneable {

    // default seed
    private double tran = 314159265.0; // First 9 digits of PI

    public Random() {}

    // Random number generator with an internal seed
    public double randlc(double a) {
        double r23, r46, t23, t46, t1, t2, t3, t4, a1, a2, x1, x2, z;
        int j;

        r23 = Util.pow(0.5, 23);
        r46 = Util.pow(r23, 2);
        t23 = Util.pow(2.0, 23);
        t46 = Util.pow(t23, 2);
        // ---------------------------------------------------------------------
        // Break A into two parts such that A = 2^23 * A1 + A2.
        // ---------------------------------------------------------------------
        t1 = r23 * a;
        a1 = (int) t1;
        a2 = a - t23 * a1;
        // ---------------------------------------------------------------------
        // Break X into two parts such that X = 2^23 * X1 + X2, compute
        // Z = A1 * X2 + A2 * X1 (mod 2^23), and then
        // X = 2^23 * Z + A2 * X2 (mod 2^46).
        // ---------------------------------------------------------------------
        t1 = r23 * tran;
        j = (int)t1;
        x1 = j;
        x2 = tran - t23 * x1;
        t1 = a1 * x2 + a2 * x1;
        j = (int) (r23 * t1);
        t2 = j;
        z = t1 - t23 * t2;
        t3 = t23 * z + a2 * x2;
        j = (int) (r46 * t3);
        t4 = j;
        tran = t3 - t46 * t4;
        return (r46 * tran);
    }
}
