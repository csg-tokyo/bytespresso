// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import java.util.HashMap;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.offload.Code;
import javassist.offload.ast.Call;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.IntrinsicFunction;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.VisitorException;
import javassist.offload.javatoc.CCode;
import javassist.offload.reify.TraceContext;

public class IntrinsicCFunction extends IntrinsicFunction implements TraitCFunction {
    protected HashMap<Call,CCode> callerCode;
    private boolean noSemicolon;
    private HeapMemory heap;

    protected IntrinsicCFunction(CtClass type, CtBehavior method, String fname,
                              JVariable[] params, boolean isStatic)
        throws NotFoundException
    {
        super(type, method, fname, params, isStatic);
        callerCode = new HashMap<Call,CCode>();
        noSemicolon = false;
        heap = null;
    }

    /**
     * Sets the heap memory.
     */
    public void setHeap(HeapMemory h) { heap = h; }

    public static HeapMemory heapMemory() {
        return (HeapMemory)codeHolderAux();
    }

    @Override public FunctionMetaclass metaclass() { return CFunctionMetaclass.instance; }

    @Override public void transformCallSite(Call call, TraceContext context)
        throws NotFoundException
    {
        setCodeHolderAux(heap);
        super.transformCallSite(call, context);
    }

    @Override protected void translateCallSite(Call call, Code code, TraceContext context)
        throws NotFoundException
    {
        if (code instanceof CCode)
            callerCode.put(call, (CCode)code);
        else
            super.translateCallSite(call, code, context);
    }

    /**
     * Generates the code at a call site to this function.
     */
    @Override
    public void callerCode(CodeGen gen, Call expr) throws VisitorException {
        CCode code = callerCode.get(expr);
        if (code == null)
            throw new VisitorException("no code for " + expr);

        code.getAST().accept(gen);
        noSemicolon = code.isNoSemicolon();
    }

    /**
     * Returns whether a semicolon has to be added to follow this intrinsic  
     * function if a call to the function consists of an expression statement. 
     * For example, this intrinsic function
     * is expanded to a pragma, a semicolon is not necessary.
     *
     * This method returns a valid value only after {@link #callerCode(OutputCode, Call)}
     * is executed.
     *
     * @return true     if no semicolon is necessary.
     */
    public boolean noSemicolon() {
        return noSemicolon;
    }

    /**
     * Any code is generated.
     */
    public void code(CodeGen gen) {}

    /**
     * Any code is generated.
     */
    public void prototype(CodeGen gen) throws VisitorException {}
}
