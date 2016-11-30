package sample;

import javassist.offload.Options;
import javassist.offload.lib.MPI;
import javassist.offload.lib.MPIDriver;
import javassist.offload.lib.Util;

public class HelloMPI {
    public static void main(String[] args) throws Exception {
        main();
    }

    public static void main() throws Exception {
        Options.portableInitialization = true;
        
        new MPIDriver(4).invoke(() -> {
        	int rank;
        	int procs;
        	rank = MPI.commRank();
        	procs = MPI.commSize();
        	for (int i = 0; i < procs; i++) {
        		MPI.barrier();
        		if (i == rank) {
	        		Util.print("Hello ");
	        		Util.print(MPI.commRank());
	        		Util.println();
        		}
        	}
        });        
    }
}
