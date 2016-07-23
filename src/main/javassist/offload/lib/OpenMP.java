// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.Code;
import javassist.offload.Intrinsic;
import javassist.offload.Options;
import javassist.offload.javatoc.CCode;

/**
 * OpenMP pragmas.
 */
public class OpenMP {
    /**
     * Parallel for loop.
     */
    @Intrinsic public static void parallelFor() {
        CCode.make("#pragma omp parallel for").newLine().noSemicolon().emit();
        if (!Options.forLoopDetection)
            throw new RuntimeException("To use OpenMP, for-loop detection has to be on.");
    }

    /**
     * Parallel for loop.  The loop condition is {@code var <= end}.
     * @deprecated
     */
    @Intrinsic public static void loopLE(Object var, Object start, Object end) {
        Code index = (Code)var;
        CCode.make("#pragma omp parallel for").newLine()
                   .add("  for (").add(index).add(" = ").addValue((Code)start).add("; ")
                   .add(index).add(" <= ").addValue((Code)end).add("; ")
                   .add(index).add("++) {").newLine().noSemicolon().emit();
    }

    /**
     * Parallel for loop.  The loop condition is {@code var < end}.
     * @deprecated
     */
    @Intrinsic public static void loop(Object var, Object start, Object end) {
        Code index = (Code)var;
        CCode.make("#pragma omp parallel for").newLine()
                   .add("  for (").add(index).add(" = ").addValue((Code)start).add("; ")
                   .add(index).add(" < ").addValue((Code)end).add("; ")
                   .add(index).add("++) {").newLine().noSemicolon().emit();
    }

    /**
     * End of Loop.
     * @deprecated
     */
    @Intrinsic public static void end() {
        CCode.make("}").newLine().noSemicolon().emit();
    }

    /**
     * Parallel loop.
     *
     * @param var           loop variable.
     * @param start         the initial value of the loop variable.
     * @param end           the loop iterates while the value of the loop variable
     *                      is less than equal to this value. 
     * @param body          the loop body.
     * @deprecated
     */
    @Intrinsic public static void parloopLE(Object var, Object start, Object end, Object body) {
        Code index = (Code)var;
        CCode.make("#pragma omp parallel for").newLine()
                   .add("  for (").add(index).add(" = ").addValue((Code)start).add("; ")
                   .add(index).add(" <= ").addValue((Code)end).add("; ")
                   .add(index).add("++) {").newLine()
                   .add("    ").add((Code)body).add("; }").noSemicolon().emit();;
    }

    /**
     * Throws an exception if the code is running on the JVM.
     *
     * @param code      should be 0.
     * @deprecated
     */
    @Intrinsic public static void assertNotOnJVM(Object code) {
        if (!(code instanceof Code))
            throw new RuntimeException("cannot use OpenMP in Java");
    }
}
