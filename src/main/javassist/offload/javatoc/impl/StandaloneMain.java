// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc.impl;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.offload.ast.VisitorException;
import javassist.offload.clang.CodeGen;
import javassist.offload.javatoc.Settings;

/**
 * A main function that launches the JVM.  The C program starting from this main function
 * communicates with the launched JVM. 
 */
public class StandaloneMain extends MainFunction {
    public String javaOption = "\"-cp\", \"bin:javassist.jar\", ";

    @Override public void preamble(CodeGen gen) {
        gen.append("#include <stdio.h>\n");
        gen.append("#include <stdlib.h>\n\n");
        gen.append("#include <sys/wait.h>\n");
        gen.append("#include <unistd.h>\n\n");

        // see Jvm#javaGetchar() etc.
        gen.append("static FILE *" + StdMainFunction.OUTPUT + ", *" + StdMainFunction.INPUT + ";\n");

        gen.append("static pid_t java_start() {\n" +
                   "  int pipe1[2], pipe2[2]; pid_t child_pid;\n" +
                   "  if (pipe(pipe1) == -1) exit(1);\n" +
                   "  if (pipe(pipe2) == -1) exit(1);\n" +
                   "  child_pid = fork(); if (child_pid == -1) exit(1);\n" +
                   "  if (child_pid == 0) {\n" +
                   // child process
                   "    dup2(pipe1[0], 0); dup2(pipe2[1], 2); close(pipe1[1]); close(pipe2[0]);\n" +
                   "    execlp(\"java\", \"java\", " + javaOption + "\"javassist.offload.Server\", NULL);\n" +
                   "    fputs(\"*** java: command not found ***\\n\", stderr);\n" +
                   "    exit(127); }\n\n" +     // 127: command not found
                   // parent process
                   "  close(pipe1[0]); close(pipe2[1]);\n" +
                   "  " + StdMainFunction.INPUT + " = fdopen(pipe2[0], \"r\"); "
                        + StdMainFunction.OUTPUT + " = fdopen(pipe1[1], \"w\");\n" +
                   "  if (" + StdMainFunction.INPUT + " == NULL || "
                        + StdMainFunction.OUTPUT + " == NULL) exit(1);\n" +
                   "  return child_pid;\n" +
                   "}\n\n");
    }

    @Override
    public void generate(CodeGen gen, CTranslator.EnvSnapshot program, CtMethod method,
                         Object[] args, Settings settings)
        throws VisitorException
    {
        CtClass t = program.function().type();
        if (t != CtClass.voidType && t != CtClass.intType)
            throw new VisitorException("the return type of the main function must be int or void."); 

        super.generate(gen, program, method, args, settings);
    }

    @Override public boolean sendResult() {
        return false;
    }

    @Override protected void prologue(CodeGen gen, CTranslator.EnvSnapshot prog) {
        if (prog.hasRemoteMethods())
            gen.append(" pid_t javavm_pid = java_start();\n");
    }

    @Override
    protected void epilogue(CodeGen gen, CTranslator.EnvSnapshot prog) {
        if (prog.hasRemoteMethods())
            gen.append(" fputc(1, " + StdMainFunction.OUTPUT + "); fclose(" + StdMainFunction.INPUT
                       + "); fclose(" + StdMainFunction.OUTPUT + "); waitpid(javavm_pid, NULL, 0);\n");

        super.epilogue(gen, prog);
    }
}
