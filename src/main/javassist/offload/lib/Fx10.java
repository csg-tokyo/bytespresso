// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.Intrinsic;
import javassist.offload.Options;
import javassist.offload.javatoc.CCode;

public class Fx10 {
    /**
     * {@code #pragma loop parallel_cyclic}
     */
    @Intrinsic public static void loopParallelCyclic() {
        CCode.make("#pragma loop parallel_cyclic").newLine().noSemicolon().emit();
        if (!Options.forLoopDetection)
            throw new RuntimeException("To use loop pragma, for-loop detection has to be on.");
    }
}
