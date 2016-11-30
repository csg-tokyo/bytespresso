package sample;

import javassist.offload.Remote;
import javassist.offload.Foreign;
import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;

class CustomizedDriver extends StdDriver {

	@Override public String preamble() {
        return super.preamble() + "#include <unistd.h>\n";
    }
}

public class AnnotationRemote {
			
	public static final String PID = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
	
	@Remote public static String getJvmPID() {
		return PID;
	}
	
	@Foreign public static int getpid() {
		return 0;
	}
		
    public static void main(String[] args) throws Exception {
    	System.out.println("PID of the host JVM: " + getJvmPID());
    	new CustomizedDriver().invoke(() -> {
    		Util.printer.p("PID of Translated C ").p(getpid()).ln();
    		Util.printer.p("PID of @Remote JVM ").p(getJvmPID()).ln();
    	});
    }
}
