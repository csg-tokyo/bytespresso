// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc;

import javassist.CtClass;
import javassist.offload.Code;
import javassist.offload.Intrinsic;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.AdhocAST;
import javassist.offload.ast.AdhocASTList;
import javassist.offload.ast.IntrinsicFunction;
import javassist.offload.clang.HeapMemory;
import javassist.offload.clang.IntrinsicCFunction;

/**
 * Code fragment in C.
 *
 * @see Intrinsic
 */
public class CCode extends Code {
    private AdhocASTList list;
    private boolean noSemicolon;

    protected CCode() {
        list = new AdhocASTList();
        noSemicolon = false;
    }

    public ASTree getAST() { return list; }

    /**
     * Makes code in C.
     *
     * @param s     the source code.
     * @return      the new code.
     */
    public static CCode make(String s) {
        CCode code = new CCode();
        code.list.add(new AdhocAST(CtClass.voidType, s));
        return code;
    }

    /**
     * Sets the code generated for the intrinsic function
     * currently processed.
     */
    public void emit() {
        IntrinsicFunction.setCallerCode(this);
    }

    /**
     * Appends source code to the tail.
     *
     * @param s     the source code.
     * @return      this object.
     */
    public CCode add(String s) {
        list.add(new AdhocAST(CtClass.voidType, s));
        return this;
    }


    /**
     * Append the value of the expression represented by <code>c</code>.
     * If the value is not statically obtained, <code>c</code> is
     * appended as is.
     *
     * @param c     the appended code.
     * @return      this object.
     */
    public CCode addValue(Code c) {
        Object v = c.value();
        if (v == null)
            return add(c);
        else
            return add(v.toString());
    }

    /**
     * Appends a line terminator to the tail.
     *
     * @return this object.
     */
    public CCode newLine() { return add("\n"); }

    /**
     * Appends the given code to the tail of this code.
     *
     * @return this object.
     */
    public CCode add(Code c) {
        list.add(c.getAST());
        return this;
    }

    /**
     * Records that this code does not end with a semicolon.
     *
     * @return this object.
     */
    public CCode noSemicolon() {
        noSemicolon = true;
        return this;
    }

    /**
     * Returns whether this code does not end with a semicolon.
     */
    public boolean isNoSemicolon() { return noSemicolon; }

    /**
     * Returns the current heap memory.
     */
    public static HeapMemory heapMemory() {
        return IntrinsicCFunction.heapMemory();
    }
}

