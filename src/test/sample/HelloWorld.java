package sample;

import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;

public class HelloWorld {
    public static void main(String[] args) throws Exception {
    	new StdDriver().invoke(() -> {
    		Util.printer.p("Hello, World!").ln();
    	});    	
    }    
}
