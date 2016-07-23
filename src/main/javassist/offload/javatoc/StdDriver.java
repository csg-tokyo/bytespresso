// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc;

import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.offload.javatoc.impl.CTranslator;
import javassist.offload.javatoc.impl.StdMainFunction;
import javassist.offload.javatoc.impl.Task;

/**
 * A standard driver for generating C code and running it.
 * It can receive a resulting value from the C code.
 */
public class StdDriver extends StandaloneDriver {
    /**
     * Constructs a driver.
     */
    public StdDriver() {}

    /**
     * Invokes the given function and returns the result.
     *
     * @param f     the function.
     */
    public int invoke(java.util.function.IntSupplier f) throws DriverException {
        return (int)invoke(StdDriver.class, "runInt", f);
    }

    static int runInt(java.util.function.IntSupplier f) {
        return f.getAsInt();
    }

    /**
     * Invokes the given function and returns the result.
     *
     * @param f     the function.
     */
    public double invoke(java.util.function.DoubleSupplier f) throws DriverException {
        return (double)invoke(StdDriver.class, "runDouble", f);
    }

    static double runDouble(java.util.function.DoubleSupplier f) {
        return f.getAsDouble();
    }

    /**
     * The return value of the method can be not only void but also
     * other types.
     */
    protected Object invoke(CtMethod cm, Object[] args, CtMethod[] callbacks)
        throws DriverException, NotFoundException
    {
        StdMainFunction.Result result = new StdMainFunction.Result();
        Task.Communicator com = StdMainFunction.returnValueReceiver(cm, result);
        invoke(cm, args, callbacks, com);
        return result.value;
    }

    /**
     * Returns a {@code CTranslator} used for the translation.
     * Override this method if a custom translator is used.
     */
    protected CTranslator makeTranslator() {
        StdMainFunction smf = new StdMainFunction();
        return new CTranslator(this, smf);
    }
}
