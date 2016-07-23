// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.offload.ast.Call;
import javassist.offload.ast.VisitorException;

/**
 * A function or an inlined function.
 */
public interface CallableCode {
    /**
     * Generates the code of an expression for calling this
     * function.
     *
     * @param gen       the generator.
     * @param expr      the call expression.
     */
    void callerCode(CodeGen gen, Call expr) throws VisitorException;

    /**
     * Generates the code for implementing this function.
     * @param gen
     * @throws VisitorException
     */
    void code(CodeGen gen) throws VisitorException;
}
