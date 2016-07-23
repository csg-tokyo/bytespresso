// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.offload.clang.CodeGen;
import javassist.offload.javatoc.Server;

/**
 * A simple main function.  It is supposed to be invoked by the JVM.
 * The resulting value of the main function is sent back to the JVM
 * through the standard input/output.
 * This main function supports calls to @Remote methods running on the JVM.
 */
public class StdMainFunction extends MainFunction {
    @Override public void preamble(CodeGen gen) {
        gen.append("#include <stdio.h>\n");
        gen.append("#include <stdlib.h>\n\n");
        gen.append("static FILE *" + OUTPUT + ", *" + INPUT + ";\n");     // see Jvm#javaGetchar() etc.
    }

    public boolean sendResult() { return true; }

    public static final String OUTPUT = "java_output_";
    public static final String INPUT = "java_input_";

    @Override protected void prologue(CodeGen gen, CTranslator.EnvSnapshot prog) {
        gen.append(OUTPUT + " = stderr; " + INPUT + " = stdin;\n");
    }

    @Override
    protected void epilogue(CodeGen gen, CTranslator.EnvSnapshot prog) {
        gen.append(prog.boolSender().name()).append("(1);\n");
        if (prog.sender() != null) {
            gen.append(prog.sender().name());
            gen.append("(result);");
        }

        gen.append(" return 0; }\n");
    }

    public static class Result {
        public Object value;
    }

    /**
     * @see Server
     */
    public static Task.Communicator returnValueReceiver(CtMethod cm, final Result result)
        throws NotFoundException
    {
        final CtClass returnType = cm.getReturnType();
        final CtClass compType = returnType.getComponentType();
        class Comm extends Server implements Task.Communicator {
            @Override public void start(InputStream in, OutputStream out) throws IOException {
                result.value = mainLoop(in, out, returnType, compType);
            }
        }

        return new Comm();
    }
}
