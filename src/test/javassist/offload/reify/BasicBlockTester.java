package javassist.offload.reify;

import javassist.*;
import javassist.offload.ast.ASTree;
import javassist.offload.javatoc.impl.CTranslator;
import javassist.offload.reify.BasicBlock;
import javassist.offload.reify.MethodTracer;
import javassist.offload.reify.UniqueID;

public class BasicBlockTester {
    public static void main(String[] args) throws Exception {
        ClassPool cp = ClassPool.getDefault();

        // CtClass cc = cp.get(args[0]);
        //String fname = "Long.class.java6";
        //CtClass cc = cp.makeClass(new java.io.FileInputStream(fname));
        //CtMethod cm = cc.getMethod("valueOf", "(J)Ljava/lang/Long;");
        CtClass cc = cp.get(BasicBlockTester.class.getName());
        CtMethod cm = cc.getDeclaredMethod("foo");
        BasicBlock[] blocks = BasicBlock.make(cp, cm);
        for (BasicBlock b: blocks)
            System.out.println(b);

        System.out.println();
        System.out.println("*** AST ***");

        // cm.getMethodInfo().rebuildStackMap(cp);
        // System.out.println(cm.getName());
        // cc.debugWriteFile("./debug");

        MethodTracer tracer = new MethodTracer(cp, cm, blocks, UniqueID.make());
        ASTree ast = tracer.trace(null, null);
        System.out.println(ast);
        // Code code = new Code();
        // ast.code(code);

        //Code code = new Driver(cp).decompile(cm);
        //System.out.println(code);
        // new Driver(cp).decompileAndRun(cm);
    }

    public static int foo(String[] args) {
        int sum = 0;
        for (int i = 0; i < args.length; i++)
            sum += args[i].length();

        return sum;
    }
}
