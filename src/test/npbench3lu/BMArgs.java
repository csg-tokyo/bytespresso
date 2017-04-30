/*
!-------------------------------------------------------------------------!
!									  !
!	 N  A  S     P A R A L L E L	 B E N C H M A R K S  3.0	  !
!									  !
!			J A V A 	V E R S I O N			  !
!									  !
!                               B M A R G S                               !
!                                                                         !
!-------------------------------------------------------------------------!
!                                                                         !
!    BMArgs implements Command Line Benchmark Arguments class             !
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
!-------------------------------------------------------------------------!
*/
package npbench3lu;

import java.io.*;

import javassist.offload.lib.Util;

public class BMArgs implements Serializable {
    public static char clazz = 'U';

    public BMArgs() {
        clazz = 'U';
    }

    static public void ParseCmdLineArgs(String argv[], String BMName) {
        for (int i = 0; i < argv.length; i++) {
            if (argv[i].startsWith("class=") || argv[i].startsWith("CLASS=") || argv[i].startsWith("-class")
                    || argv[i].startsWith("-CLASS")) {

                if (argv[i].length() > 6)
                    clazz = Character.toUpperCase(argv[i].charAt(6));
                if (clazz != 'A' && clazz != 'B' && clazz != 'C' && clazz != 'S' && clazz != 'W') {
                    System.out.println("classes allowed are A,B,C,W and S.");
                    commandLineError(BMName);
                }
            }
        }
    }

    public static void commandLineError(String BMName) {
        System.out.println("synopsis: java " + BMName + " CLASS=[ABCWS] -serial [-NPnnn]");
        System.out.println("[ABCWS] is the size class \n" + "-serial specifies the serial version and\n"
                + "-NP specifies number of threads where nnn " + "is an integer");
        System.exit(1);
    }

    public static void outOfMemoryMessage() {
        System.out.println("The java maximum heap size is " + "to small to run this benchmark class");
        System.out.println(
                "To allocate more memory, use the -mxn option" + " where n is the number of bytes to be allocated");
    }

    public static void Banner(String BMName, char clss) {
        Util.printer.p(" NAS Parallel Benchmarks Bytespresso version").ln();
        Util.printer.p(" MPI Version LU.").p(clss).ln();
    }
}
