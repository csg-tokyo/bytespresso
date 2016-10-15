// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.Code;
import javassist.offload.Foreign;
import javassist.offload.Intrinsic;
import javassist.offload.Native;
import javassist.offload.javatoc.CCode;

/**
 * Utility methods.
 */
public class Util {
    /**
     * Returns preamble.
     */
    public static String preamble() {
        return "#include <stdio.h>\n" +
               "#include <stdlib.h>\n" +
               "#include <math.h>\n" +
               "#include <sys/time.h>\n\n";
    }

    // edit DriverException if a new ERR_ code is defined.

    /**
     * The exit status reporting that no such a callback function in C found.
     */
    public static final int ERR_CALLBACK = 119;

    /**
     * The exit status reporting that an error is detected.
     */
    public static final int ERR_DESERIALIZE = 120;

    /**
     * The exit status reporting that a method is called but
     * the receiver type is an unknown type.
     */
    public static final int ERR_DISPATCH = 128;

    /**
     * exit(int status) in the standard C library.
     */
    @Foreign public static void exit(int status) {
        throw new RuntimeException("exit: " + status);
    }

    /**
     * Returns the current time in micro seconds.
     */
    @Native("struct timeval time; gettimeofday(&time, NULL);\n"
            + "return time.tv_sec * 1000000 + time.tv_usec;")
    public static long time() {
        return System.nanoTime() / 1000;
    }

    private static final ThreadLocal<StringBuilder> systemOut = new ThreadLocal<StringBuilder>() {
        @Override protected StringBuilder initialValue() { return new StringBuilder(); }
    };

    /**
     * Prints a number.
     */
    @Native("printf(\"%d\", (int)v1); return 0;")
    public static Util print(int i) {
        systemOut.get().append(i);
        return null;
    }

    /**
     * Prints a number.
     */
    @Native("printf(\"%ld\", (long)v1); return 0;")
    public static Util print(long i) {
        systemOut.get().append(i);
        return null;
    }

    /**
     * Prints a number.
     */
    @Native("printf(\"%lg\", (double)v1); return 0;")
    public static Util print(float f) {
        systemOut.get().append(f);
        return null;
    }

    /**
     * Prints a number.
     */
    @Native("printf(\"%lg\", (double)v1); return 0;")
    public static Util print(double d) {
        systemOut.get().append(d);
        return null;
    }

    /**
     * Prints a number in the style E.
     */
    @Native("printf(\"%le\", (double)v1); return 0;")
    public static Util printE(float f) {
        systemOut.get().append(f);
        return null;
    }

    /**
     * Prints a number in the style E.
     */
    @Native("printf(\"%le\", (double)v1); return 0;")
    public static Util printE(double d) {
        systemOut.get().append(d);
        return null;
    }

    /**
     * Prints a character.
     */
    @Native("printf(\"%c\", v1); return 0;")
    public static Util print(char c) {
        systemOut.get().append(c);
        return null;
    }

    /**
     * Prints a line terminator.  All the data printed so far are
     * sent at once to the standard error.
     */
    @Native("printf(\"\\n\"); return 0;")
    public static Util println() {
        StringBuilder sb = systemOut.get();
        systemOut.remove();
        sb.append('\n');
        System.err.print(sb.toString());
        return null;
    }

    /**
     * Prints a character string.
     */
    public static Util print(String s) {
        print2(Unsafe.toCStr(s));
        return null;
    }

    /**
     * Prints a character string.  To terminate the line,
     * the character string must include '\0'.
     */
    @Native("printf(\"%s\", (char*)v1);")
    private static void print2(String s) {
        systemOut.get().append(s);
    }

    /**
     * Printer.
     */
    public static final Printer printer = new Printer();

    /**
     * Printer.
     */
    static public class Printer {
        /**
         * Prints a character.
         */
        public Printer p(char c) { print(c); return this; }

        /**
         * Prints an integer.
         */
        public Printer p(int i) { print(i); return this; }

        /**
         * Prints an integer.
         */
        public Printer p(long i) { print(i); return this; }

        /**
         * Prints a value.
         */
        public Printer p(float f) { print(f); return this; }

        /**
         * Prints a value.
         */
        public Printer p(double d) { print(d); return this; }

        /**
         * Prints a value in the style E.
         */
        public Printer e(float f) { printE(f); return this; }

        /**
         * Prints a value in the style E.
         */
        public Printer e(double d) { printE(d); return this; }

        /**
         * Prints a string character.
         */
        public Printer p(String s) { print(s); return this; }

        /**
         * Prints a white space.
         */
        public Printer s() { print(' '); return this; }

        /**
         * Prints a line terminator.
         */
        public Printer ln() { println(); return this; }
    }

    /**
     * Absolute value.
     */
    @Foreign
    public static double fabs(double d) {
        return Math.abs(d);
    }

    /**
     * Power.
     */
    @Foreign
    public static double pow(double a, double b) {
        return Math.pow(a, b);
    }

    /**
     * Square root.
     */
    @Foreign
    public static float sqrtf(float f) {
        return (float)Math.sqrt(f);
    }

    /**
     * Square root.
     */
    @Foreign
    public static double sqrt(double d) {
        return (double)Math.sqrt(d);
    }

    /**
     * Exponential function.
     */
    @Foreign
    public static float expf(float f) {
        return (float)Math.exp(f); 
    }

    /**
     * Exponential function.
     */
    @Foreign
    public static double exp(double f) {
        return Math.exp(f); 
    }

    /**
     * cosine function.
     */
    @Foreign
    public static float cosf(float f) {
        return (float)Math.cos(f); 
    }

    /**
     * The unroll pragma.
     *
     * @param num       how many times a loop is unrolled.
     */
    @Intrinsic public static void unroll(Object num) {
        CCode.make("#pragma unroll ").addValue((Code)num).newLine().noSemicolon().emit();
    }

    /**
     * The unroll pragma.
     */
    @Intrinsic public static void unroll() {
        CCode.make("#pragma unroll").newLine().noSemicolon().emit();
    }
}
