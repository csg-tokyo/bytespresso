// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload;

/**
 * Options.
 */
public class Options {
    /**
     * Debug level.  The default value is 0.
     */
    public static int debug = 0;

    /**
     * If true, for loops in Java bytecode are detected.
     */
    public static boolean forLoopDetection = true;

    /**
     * If true, aggressive inlining is performed.
     */
    public static boolean doInline = true;

    /**
     * If true, deadcode elimination is performed.
     */
    public static boolean deadcodeElimination = true;

    /**
     * If true, {@code struct/union} are initialized in a
     * portable way.  If the target language is C++,
     * this must be true.
     */
    public static boolean portableInitialization = false;
}