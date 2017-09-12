package sample;

import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;
import javassist.offload.Foreign;

class AnnotationForeignDriver extends StdDriver {

	@Override public String preamble() {
        return super.preamble() + "#include <unistd.h>\n";
    }
}


public class AnnotationForeign {
	
	@Foreign
	public static float sqrtf(float f) {
		return 0.0f;
	}
	
	@Foreign public static int getpid() {
		return 0;
	}
	
    public static void main(String[] args) throws Exception {
    	
    	new AnnotationForeignDriver().invoke(() -> {
    		Util.printer.p("Hello, Annotation Foreign").ln();
    		Util.printer.p("sqrtf(2.0f) = ").p(sqrtf(2.0f)).ln();
    		Util.printer.p("getpid() = ").p(getpid()).ln();
    	});    	
    }

}
