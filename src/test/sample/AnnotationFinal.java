package sample;

import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;
import javassist.offload.Final;

class Point {
	int posX;
	int posY;
	double value;
	Point(int x, int y, double v) {
		posX = x;
		posY = y;
		value = v;
	}
	int getX() {
		return posX;
	}
	int getY() {
		return posY;
	}
	double getValue() {
		return value;
	}
	void setX(int x) {
		posX = x;
	}
	void setY(int y) {
		posY = y;
	}
	void setValue(double v) {
		value = v;
	}
}


public class AnnotationFinal {
	static boolean flagA;
	@Final static boolean flagB;
	final static boolean flagC = true;
    public static void main(String[] args) throws Exception {

    	flagA = true;
    	flagB = true;
    	
    	new StdDriver().invoke(() -> {
    		Util.print("Hello, Annotation Final");
    		Util.println();

    		if (flagA) {
    			Util.print("flagA is true");
        		Util.println();    			
    		} else {
    			Util.print("flagA is false");
        		Util.println();    			    			
    		}
    		
    		if (flagB) {
    			Util.print("flagB is true");
        		Util.println();    			
    		} else {
    			Util.print("flagB is false");
        		Util.println();    			    			
    		}

    		if (flagC) {
    			Util.print("flagC is true");
        		Util.println();    			
    		} else {
    			Util.print("flagC is false");
        		Util.println();    			    			
    		}

    	});    	
    }
}
