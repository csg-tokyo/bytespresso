package sample;

import javassist.offload.Remote;
import javassist.offload.Foreign;
import javassist.offload.javatoc.Callback;
import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;
import javassist.offload.lib.MPI;
import javassist.offload.lib.MPIDriver;

class CustomizedMPIDriver extends MPIDriver {
	
	public CustomizedMPIDriver(int num) {
		super(num);
	}

	@Override public String preamble() {
        return super.preamble() + "#include <unistd.h>\n";
    }
}

public class AnnotationRemoteMPI {

	public static final String PID = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
	
	@Remote public static String getJvmPID() {
		return PID;
	}
	
	@Foreign public static int getpid() {
		return 0;
	}
			
    public static void main(String[] args) throws Exception {
    	System.out.println("PID of the host JVM: " + getJvmPID());
    	new CustomizedMPIDriver(4).invoke(() -> {
    		Util.printer.p("PID of Translated C ").p(getpid()).ln();
    		Util.printer.p("PID of @Remote JVM ").p(getJvmPID()).ln();
    	});
    }	
}
