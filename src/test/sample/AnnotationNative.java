package sample;

import javassist.offload.Foreign;
import javassist.offload.Native;
import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;


public class AnnotationNative {
	
	@Native("return v1 > v2 ? v1 : v2;")
	public static float max(float a, float b) {
		return 0.0f;
	}
	
    public static void main(String[] args) throws Exception {
    	
    	new StdDriver().invoke(() -> {
    		Util.printer.p("Hello, Annotation Native").ln();
    		Util.printer.p("max(2.0f, 3.14f) = ").p(max(2.0f, 3.14f)).ln();
    	});    	
    }
	
}
