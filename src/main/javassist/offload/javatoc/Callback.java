// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javassist.offload.javatoc.impl.Serializer;
import javassist.offload.lib.Jvm;

/**
 * A class providing a method for calling a method
 * in the generated C program.
 *
 * @see #invoke(int, Class, Object...)
 */
public class Callback {
    static class InOut {
        InputStream in;
        OutputStream out;
        InOut(InputStream i, OutputStream o) { in = i; out = o; }
    }

    private static ThreadLocal<InOut> inout = null;

    /**
     * Initializes the communication stream.
     * Internal-use only.
     */
    public static void initialize(InputStream is, OutputStream out) {
        inout = new ThreadLocal<InOut>();
        inout.set(new InOut(is, out));
    }

    /**
     * Removes the reference to the communication stream.
     * Internal-use only.
     */
    public static void erase() {
        if (inout != null)
            inout.remove();
    }

    /**
     * Invokes a callback method.
     *
     * @param methodIndex       the index in the array of the callback methods.
     * @param resultType        the type of the returned value.
     * @param args              the arguments.
     * @return                  the returned value.
     */
    public static <T> T invoke(int methodIndex, Class<T> resultType, Object... args) {
        try {
            return invoke0(methodIndex, resultType, args);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T invoke0(int methodIndex, Class<T> resultType, Object... args) throws IOException {
        // see javassist.offload.Server
        InOut obj;
        if (inout == null || (obj = inout.get()) == null)
            throw new IOException("this thread cannot invoke a callback");

        Jvm.writeBoolean(obj.out, false);
        Jvm.writeInt(obj.out, methodIndex);
        for (Object a: args)
            Serializer.writeValue(a.getClass(), a, obj.out);

        obj.out.flush();
        return (T)Serializer.readValue(resultType, obj.in);
    }
}
