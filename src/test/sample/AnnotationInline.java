package sample;

import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;


public class AnnotationInline {
    public static void main(String[] args) throws Exception {
    	new StdDriver().invoke(() -> {
    		Util.print("Hello, Annotation Inline");
    		Util.println();
    	});    	
    }
}
