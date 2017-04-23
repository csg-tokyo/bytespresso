package javassist.offload.reify;

import static org.junit.Assert.*;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.offload.Foreign;
import javassist.offload.Native;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Call;
import javassist.offload.ast.Function;
import javassist.offload.ast.JMethod;
import javassist.offload.clang.CDispatcher;
import javassist.offload.clang.CFunctionMetaclass;
import javassist.offload.clang.CodeGen;
import javassist.offload.clang.HeapMemory;
import javassist.offload.clang.TraitCFunction;
import javassist.offload.javatoc.impl.CTranslator;
import javassist.offload.javatoc.impl.OutputFile;
import javassist.offload.reify.FunctionTable;
import javassist.offload.reify.TraceContext;
import javassist.offload.reify.Tracer;
import javassist.offload.reify.UniqueID;
import javassist.offload.reify.Reifier.Snapshot;
import javassist.offload.test.StdDriver2;

import org.junit.Test;

public class Tests {
    static ClassPool cpool = ClassPool.getDefault();
    static CTranslator driver = new CTranslator(new StdDriver2());

    static void println(Object s) {
        System.out.println(s.toString());
    }

    static Function makeAST(String clazz, String method) throws Exception {
        CtClass cc = cpool.get(clazz);
        CtMethod cm = cc.getDeclaredMethod(method);
        OutputFile out = new OutputFile("./test");
        CodeGen codegen = new CodeGen(cpool, out, driver.settings().isLittleEndian(), new HeapMemory(false));
        return new Tracer().getAST(new Call(null, cm, null),
                                   cm, makeContext(codegen));
    }

    public static TraceContext makeContext(CodeGen gen) throws NotFoundException {
        return TraceContext.make(cpool, gen.classTable(), CFunctionMetaclass.class,
                new FunctionTable<TraitCFunction,CDispatcher>(), UniqueID.make());
    }

    @Test public void testASTcopy() throws Exception {
        Function f = makeAST(Tests.class.getName(), "test01");
        Function f2 = f.copy(f);
        println(f);
        // println(f2);
        visitElements(f, f2);
    }

    @Test public void testASTcopy2() throws Exception {
        Function f = makeAST(Tests.class.getName(), "test02");
        Function f2 = ASTree.copy(f);
        println(f2);
        // assertSame(f2.parameters()[0].identity(), f2.parameters()[0]);
        visitElements(f, f2);
    }

    private void visitElements(ASTree t1, ASTree t2) throws Exception {
        int n = t1.numChildren();
        assertEquals(t2.numChildren(), n);
        for (int i = 0; i < n; i++)
            visitElements(t1.child(i), t2.child(i));

        assertNotSame(t1, t2);
        assertSame(t1.getClass(), t2.getClass());
    }

    public static int test01(int k) {
        int i = k * (k + 1);
        int j = 0;
        for (int p = 0; p < 3; p++)
            j += i + k;

        return j;
    }

    @Native("return k;")
    public static int test02(int k, double d) { return 0; }

    @Test public void testArray() throws Exception {
        Function f = makeAST(Tests.Foo.class.getName(), "test03");
        println(f);
    }

    public static class Foo {
        double[] a = new double[8];
        double[] b = new double[8];
        public int test03(int n) {
            a[n] = b[n] = n;
            return n;
        }
    }

    @Test public void testReify() throws Exception {
        CtClass cc = cpool.get(Bar.class.getName());
        CtMethod cm = cc.getDeclaredMethod("baz");
        Reifier reifier = new Reifier(cm, new Object[] { new Bar() });
        Snapshot image = reifier.snap();
        for (JMethod f: image.functionTable)
            if (f != image.function)
                System.out.println(f);
    }

    public static class Bar {
        public void baz() { foo(); bar(); }
        @Native public void foo() {}
        @Foreign  public static void bar() {}
    }
}
