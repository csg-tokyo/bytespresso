package javassist.offload.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import org.junit.Test;
import static org.junit.Assert.*;

import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.offload.Code;
import javassist.offload.Final;
import javassist.offload.Inline;
import javassist.offload.Intrinsic;
import javassist.offload.Metaclass;
import javassist.offload.Native;
import javassist.offload.Options;
import javassist.offload.Remote;
import javassist.offload.ast.VisitorException;
import javassist.offload.clang.CodeGen;
import javassist.offload.clang.HeapMemory;
import javassist.offload.clang.ImmutableClass;
import javassist.offload.clang.NativeArrayClass;
import javassist.offload.clang.NativeClass;
import javassist.offload.javatoc.CCode;
import javassist.offload.javatoc.Callback;
import javassist.offload.javatoc.DriverException;
import javassist.offload.javatoc.Settings;
import javassist.offload.javatoc.StdDriver;
import javassist.offload.javatoc.impl.CTranslator;
import javassist.offload.javatoc.impl.MainFunction;
import javassist.offload.javatoc.impl.OutputFile;
import javassist.offload.javatoc.impl.Serializer;
import javassist.offload.javatoc.impl.StandaloneMain;
import javassist.offload.javatoc.impl.StdMainFunction;
import javassist.offload.javatoc.impl.Task;
import javassist.offload.lib.Deserializer;
import javassist.offload.lib.DoubleArray;
import javassist.offload.lib.DoubleArray2D;
import javassist.offload.lib.FloatArray2D;
import javassist.offload.lib.FloatArray3D;
import javassist.offload.lib.FloatArray4D;
import javassist.offload.lib.FloatArray;
import javassist.offload.lib.ForeignClass;
import javassist.offload.lib.IntArray;
import javassist.offload.lib.Jvm;
import javassist.offload.lib.MPI;
import javassist.offload.lib.MPIRuntime;
import javassist.offload.lib.NativePtr;
import javassist.offload.lib.Util;
import javassist.offload.lib.Unsafe;
import javassist.offload.reify.Reifier;

public class Runner {
    /**
     * True if conservative gc is used.
     */
    public static final boolean useGC = false;

    /**
     * To run, ./javassit.jar must exist.
     * The JVM option -Djdk.internal.lambda.dumpProxyClasses=./bin must be given,
     * where ./bin is a directory included in CLASSPATH.
     */
    public static void main(String[] args) throws Exception {
        new Runner().main2(args);
    }

    public static void main1(String[] args) throws Exception {
        new Runner().testDouble2Array();
    }

    public void main2(String[] args) throws Exception {
        // tester("javassist.offload.decompiler.BasicBlock", "make");
        // tester("javassist.offload.decompiler.Runner", "fib0");

        testUnsafe();
        test1();
        arrayTest();
        testObj();
        testObj1();
        testObj2();
        testObj3();
        testObj10();
        testObj11();
        testObj15();
        testString();
        testExchangeString();
        testRemote();
        testRemote2();
        testStandalone();
        testStdDriver2();
        testStdDriver();
        testStdDriver4();
        testStdDriver5();
        testNativeClass();
        testInline1();
        testDispatcher();
        testDispatcher1();
        testDispatcher2();
        testDispatcher3();
        testClosure();
        testMultiArray();
        testMultiArray2();
        testInnerClass();
        testImmutable();
        testImmutable2();
        testImmutable3();
        testImmutable4();
        testImmutable5();
        testImmutableArray();
        testRemoteAndCallback();
        testRemoteBandwidth();
        testRemoteBandwidthInt();
        testRemoteBandwidthByte();
        testConsObject();
        testArrays();
        testF3Array();
        testFloat2Array();
        testDouble2Array();
        testIntArray();
        testFinalAnno();
        testIntrinsic();
        testIntrinsicLoop();
        testIntrinsic2();
        testRecursion();
        testForeignClass();
        testFieldAccess();
        testNativeArrayClass();
        testArrayInit();
        testDataflow();
        testDataflow2();
        testInlining();
        testInlining2();
        testInlining3();
        testInlining4();
        testInlining5();
        testInlining6();
        testInlining7();
        testInlining8();
        testInlining9();
        testInlining10();
        testForLoop();
        try {
            testSimpleNew();
        } catch (Exception e) {
            e.printStackTrace();
        }
        testInlineImmutable();
        testInlineImmutable2();
        testMPIRuntime();
        testMPIRuntime2();
        testMallocFree();
        testDeadcode();
        testDeadcode2();
        testDeadcode3();
        testDeadcode4();
        testCast();
        testLambda();
        testLambda2();
        testLambda3();
        testObjectInlining();
        testObjectInliningB();
        testObjectInliningC();
        testObjectInliningD();
        testIntfType();
        testReifier();
        testFinal();
        testFinalInline();
    }

    public static void fib0() {
        int n = 10;
        for (int i = 0; i < 5; i++) {
            print(fib(n));
        }
        Jvm.writeInt(fib(n));
    }

    public static void tester(String clazz, String method, Task.Sender sender, Task.Receiver recv)
            throws Exception
    {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get(clazz);
        CtMethod cmth = cc.getDeclaredMethod(method);
        StdDriver stdDrv = new StdDriver2();
        CTranslator drv = new CTranslator(stdDrv);
        drv.translateAndWriteCode(cmth, new Object[0], new CtMethod[0], new HeapMemory(false));
        drv.compileCode();
        drv.runCode(null, sender, recv);
    }

    public static void tester(String clazz, String method, Object[] args, final Task.Sender sender, final Task.Receiver recv)
        throws Exception
    {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get(clazz);
        CtMethod cm = cc.getDeclaredMethod(method);
        Task.Communicator com = new Task.Communicator() {
            public void start(InputStream in, OutputStream out) throws IOException {
                if (sender != null)
                    sender.write(out);

                if (recv != null)
                    recv.read(in);
            }
        };
        StdDriver drv = new StdDriver2();
        new CTranslator(drv).compileAndRun(cm, args, new CtMethod[0], com, new HeapMemory(false));
    }

    public static Object tester(String clazz, String method, Object[] args)
        throws Exception
    {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get(clazz);
        CtMethod cm = cc.getDeclaredMethod(method);
        StdMainFunction smf = new StdMainFunction();
        StdDriver stdDrv = new StdDriver2();
        StdMainFunction.Result result = new StdMainFunction.Result();
        Task.Communicator com = StdMainFunction.returnValueReceiver(cm, result);
        new CTranslator(stdDrv, smf).compileAndRun(cm, args, new CtMethod[0], com, new HeapMemory(false));
        return result.value;
    }

    public static void aloneTester(String clazz, String method, Object[] args)
        throws Exception
    {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get(clazz);
        CtMethod cm = cc.getDeclaredMethod(method);
        MainFunction mf = new StandaloneMain();
        StdDriver drv = new StdDriver2();
        new CTranslator(drv, mf).compileAndRun(cm, args, new CtMethod[0], null, new HeapMemory(false));
    }

    private static ByteArrayOutputStream decompile(ClassPool cp, CTranslator driver, HeapMemory heap, CtMethod cm, Object arg)
        throws Exception
    {
        // sending the arguments by a pipe to the main function in C.
        Settings settings = driver.settings();
        OutputFile file = new OutputFile(settings.sourceFile());
        CodeGen codegen = new CodeGen(cp, file, settings.isLittleEndian(), heap);
        String ccCmd = driver.settings().compileCommand();
        Serializer s = new Serializer();
        s.serialize(arg, codegen);
        ByteArrayOutputStream out = s.getBinary();
        driver.translateIntoC(cm, new Object[0], new CtMethod[0], codegen);
        file.close();
        if (new Task(ccCmd).run() != 0)
            throw new DriverException("compilation failed", 1);

        return out;
    }

    public @Test void testUnsafe() throws Exception {
        float fvalue = 3.0F;
        double dvalue = 7.0;
        Object r = tester("javassist.offload.test.Runner", "identityUnsafe", new Object[] { fvalue, dvalue });
        int[] a = (int[])r;
        long j = Double.doubleToLongBits(dvalue);
        if (a[0] == Float.floatToIntBits(fvalue))
            if ((a[1] == (int)(j >> 32) && a[2] == (int)j) || (a[1] == (int)j && a[2] == (int)(j >> 32))) {
                System.out.println("testUnsafe OK");
                return;
            }
        
        throw new Exception("testUnsafe failed");
    }

    public static int[] identityUnsafe(float f, double d) {
        int[] a = new int[8];
        a[0] = a[1] = a[2] = 0;
        Unsafe.set(a, 2, f);
        Unsafe.set(a, 3, d);
        printHex(a[0]);
        printHex(a[1]);
        printHex(a[2]);
        return a;
    }

    public @Test void test1() throws Exception {
        final Task.Sender sender = new Task.Sender() {
            public void write(OutputStream out) throws IOException {
                final int num = Integer.MIN_VALUE;
                Jvm.writeInt(out, num);
                final float lnum = 0.3f;
                System.out.println("float " + lnum);
                Jvm.writeFloat(out, lnum);
                final long jnum = Long.MIN_VALUE;
                System.out.println("long " + jnum);
                Jvm.writeLong(out, jnum);
                final long jnum2 = Long.MAX_VALUE;
                System.out.println("long " + jnum2);
                Jvm.writeLong(out, jnum2);
                Jvm.writeShort(out, -1);
            }
        };

        final Task.Receiver recv = new Task.Receiver() {
            public void read(InputStream is) throws IOException {
                int i = Jvm.readInt(is);
                float f = Jvm.readFloat(is);
                long j = Jvm.readLong(is);
                long j2 = Jvm.readLong(is);
                int s = (short)Jvm.readShort(is);
                System.out.println(" test1 output: " + i + " " + f
                                   + " " + j + " " + j2 + " " + s);
                if (i != Integer.MIN_VALUE || f != 0.3f || j != Long.MIN_VALUE || j2 != Long.MAX_VALUE
                    || s != -1)
                    throw new IOException("test1" + i + " " + f + " " + j + " " + j2 + " " + s);

        }};

        tester("javassist.offload.test.Runner", "identity", sender, recv);
    }

    public static void identity() {
        int i = Jvm.readInt();
        float f = Jvm.readFloat();
        long j = Jvm.readLong();
        long j2 = Jvm.readLong();
        int s = Jvm.readShort();

        print(s);
        print(i);
        print(f);
        if (i == 0)
            Util.exit(1);

        print(test7(3));
        print(test8(new Klass8()));
        print(test8(new Klass8(111, 0.9)));
        Jvm.writeInt(i);
        Jvm.writeFloat(f);
        Jvm.writeLong(j);
        Jvm.writeLong(j2);
        Jvm.writeShort(s);
    }

    @Test public void testUtil() throws Exception {
        StdDriver2 drv = new StdDriver2();
        final int i = 3;
        final double d = 13.0;
        double res = (double)drv.invoke(Runner.class, "utilTest", null,
                                  new Object[] { i, d });
        double expected = utilTest(i, d);
        assertEquals(expected, res, expected * 0.01);
    }

    public static double utilTest(int i, double d) {
        Util.printer.p(i).p(',').p(31L).p(" ").p(0.3f).ln().p(d).s().e(0.3f).s().e(d).ln();
        return Util.pow(d, i) + Util.exp(d) + Util.sqrt(d) + Util.fabs(-0.3 * d);
    }

    public @Test void arrayTest() throws Exception {
        Task.Sender sender = new Task.Sender() {
            public void write(OutputStream out) throws IOException {
                byte[] ba = new byte[] { 10, 20, 30 };
                int[] ia = new int[] { 1, 2, 3 };
                long[] ja = new long[] { 4L, 5L, 6L };
                float[] fa = new float[] { 0.7F, 0.8F, 0.9F };
                double[] da = new double[] { 1.1, 1.2, 1.3 };

                Jvm.writeByte(out, ba);
                Jvm.writeInt(out, ia);
                Jvm.writeFloat(out, fa);
                Jvm.writeLong(out, ja);
                Jvm.writeDouble(out, da);
            }
        };

        Task.Receiver recv = new Task.Receiver() {
            public void read(InputStream is) throws IOException {
                byte[] ba = Jvm.readByteArray(is);
                int[] ia = Jvm.readIntArray(is);
                long[] ja = Jvm.readLongArray(is);
                float[] fa = Jvm.readFloatArray(is);
                double[] da = Jvm.readDoubleArray(is);
                System.out.println("ia.length() " + ia.length);
                System.out.println("da.length() " + da.length);
                double sum = 0.0;
                for (int i = 0; i < ia.length; i++) {
                    System.out.println(ba[i] + " " + ia[i] + " " + ja[i] + " " + fa[i] + " " + da[i]);
                    sum = sum + ia[i] + ja[i] + fa[i] + da[i];
                }

                System.out.println(" output: " + sum);
                byte[] ba2 = { 11, 21, 31 };
                int[] ia2 = { 2, 3, 4 };
                long[] ja2 = { 5L, 6L, 7L };
                float[] fa2 = { 1.7F, 1.8F, 1.9F };
                double[] da2 = { 2.1, 2.2, 2.3 };
                for (int i = 0; i < ia2.length; i++)
                    if (ba[i] != ba2[i])
                        throw new IOException("arrayTest: " + ba[i]);
                for (int i = 0; i < ia2.length; i++)
                    if (ia[i] != ia2[i])
                        throw new IOException("arrayTest: " + ia[i]);
                for (int i = 0; i < ja2.length; i++)
                    if (ja[i] != ja2[i])
                        throw new IOException("arrayTest: " + ja[i]);
                for (int i = 0; i < fa2.length; i++)
                    if (fa[i] != fa2[i])
                        throw new IOException("arrayTest: " + fa[i]);
                for (int i = 0; i < da2.length; i++)
                    if (da[i] != da2[i])
                        throw new IOException("arrayTest: " + da[i]);
            }
        };

        tester("javassist.offload.test.Runner", "identity2", sender, recv);
    }

    public static void identity2() {
        byte[] ba = Jvm.readByteArray();
        int[] ia = Jvm.readIntArray();
        float[] fa = Jvm.readFloatArray();
        long[] ja = Jvm.readLongArray();
        double[] da = Jvm.readDoubleArray();

        long[] ja2 = getLongArray();
        print(ja2.length);
        print(ja2[0]);
        print(ja2[1]);
        print(ja2[2]);

        for (int i = 0; i < ia.length; i++) {
            ba[i]++;
            ia[i]++;
            ja[i]++;
            fa[i]++;
            da[i]++;
        }

        printMark();
        Jvm.writeByte(ba);
        Jvm.writeInt(ia);
        Jvm.writeLong(ja);
        Jvm.writeFloat(fa);
        Jvm.writeDouble(da);
    }

    public static long[] getLongArray() {
        long value = 5L;
        int s = 3;
        long[] ja = new long[s];
        for (int i = 0; i < s; i++)
            ja[i] = value++;

        return ja;
    }

    public @Test void testObj() throws Exception {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get(Runner.class.getName());
        CtMethod cm = cc.getDeclaredMethod("identity3");
        StdDriver std = new StdDriver2();
        CTranslator drv = new CTranslator(std);
        Object arg = null;
        final ByteArrayOutputStream binary = decompile(cp, drv, new HeapMemory(false), cm, arg);

        Task.Sender sender = new Task.Sender() {
            public void write(OutputStream out) throws IOException {
                binary.writeTo(out);
            }
        };

        Task.Receiver recv = new Task.Receiver() {
            public void read(InputStream is) throws IOException {
                int[] ia = Jvm.readIntArray(is);
                for (int i = 0; i < ia.length; i++) {
                    System.out.println(ia[i]);
                }
                if (ia[0] != 1)
                    throw new IOException("testObj " + ia[0]);
        }};

        drv.runCode(null, sender, recv);
    }

    public static void identity3() {
        Object arg = Deserializer.read();
        int k;
        if (arg == null) {
            print(1);
            k = 1;
        }
        else {
            print(0);
            k = 0;
        }

        int m = arg == null ? 1 : 0;
        Jvm.writeInt(new int[] { arg == null ? 1 : 0, m, k, 123, 456 });
    }
 
    public static class C1 {
        double value;
        long value2;
        public C1(double d) {
            value = d;
            value2 = 123L;
        }
    }

    public @Test void testObj1() throws Exception {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get(Runner.class.getName());
        CtMethod cm = cc.getDeclaredMethod("identity35");
        StdDriver std = new StdDriver2();
        CTranslator drv = new CTranslator(std);
        C1 arg = new C1(789.6);
        final ByteArrayOutputStream binary = decompile(cp, drv, new HeapMemory(false), cm, arg);

        Task.Sender sender = new Task.Sender() {
            public void write(OutputStream out) throws IOException {
                binary.writeTo(out);
            }
        };

        Task.Receiver recv = new Task.Receiver() {
            public void read(InputStream is) throws IOException {
                int[] ia = Jvm.readIntArray(is);
                if (ia[0] != 1 || ia[1] != 1)
                    throw new IOException("testObj1 " + ia[0] + " " + ia[1]);
        }};

        drv.runCode(null, sender, recv);
    }

    public static void identity35() {
        C1 arg = (C1)Deserializer.read();
        int[] res = { arg.value == 789.6 ? 1 : 0, arg.value2 == 123L ? 1 : 0 };
        print(arg.value);
        Jvm.writeInt(res);
    }

    public @Test void testObj2() throws Exception {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get(Runner.class.getName());
        CtMethod cm = cc.getDeclaredMethod("identity4");
        StdDriver std = new StdDriver2();
        CTranslator drv = new CTranslator(std);
        C4 c4 = new C4(789);
        C4 c4b = new C4(456);
        c4.left = c4b;
        c4.right = c4;
        c4b.left = c4;
        c4b.dval = 8.8;
        D4 d4 = new D4(c4);
        Object arg = d4;
        final ByteArrayOutputStream binary = decompile(cp, drv, new HeapMemory(false), cm, arg);

        Task.Sender sender = new Task.Sender() {
            public void write(OutputStream out) throws IOException {
                binary.writeTo(out);
            }
        };

        Task.Receiver recv = new Task.Receiver() {
            public void read(InputStream is) throws IOException {
                int[] ia = Jvm.readIntArray(is);
                for (int i = 0; i < ia.length; i++) {
                    System.out.println("identity4 " + ia[i]);
                }
                if (ia[0] != 789 || ia[1] != 456)
                    throw new IOException("testObj2 " + ia[0] + " " + ia[1]);
        }};

        drv.runCode(null, sender, recv);
    }

    static class C4 {
        public int value;
        public C4 left, right;
        public double dval;

        public C4(int i) {
            value = i;
            left = right = null;
            dval = 7.7;
        }
    }

    static public class D4 {
        public static final int si = 76;
        public C4 c4;
        public D4(C4 obj) { c4 = obj; }
        public C4 get() { return c4; }
    }

    public static void identity4() {
        D4 d4 = (D4)Deserializer.read();
        printHex(d4);
        C4 c4 = d4.get();
        printHex(0xeee);
        printHex(c4);
        printHex(c4.value);
        printHex(c4.left);
        printHex(c4.right);
        if (c4.value == 789  && c4.left != null && c4.right == c4) {
            C4 c4b = c4.left;
            printHex(c4.left);
            print(c4b.value);
            printHex(c4b.left);
            printHex(c4);
            printHex(c4b.right);
            print(c4.dval);
            print(c4b.dval);
            if (c4b.value == 456 && c4b.left == c4 && c4b.right == null
                && c4.dval == 7.7 && c4b.dval == 8.8) {
                Jvm.writeInt(new int[] { c4.value, c4.left.value });
                return;
            }
        }

        print(10101);
        Util.exit(99);
    }

    public static class C5 {
        public double d;
        public int b;
        public int[] ia;
        public float f;
        public long j;
    }

    public @Test void testObj3() throws Exception {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get(Runner.class.getName());
        CtMethod cm = cc.getDeclaredMethod("identity5");
        StdDriver2 std = new StdDriver2();
        CTranslator drv = new CTranslator(std);
        C5 c5 = new C5();
        c5.d = 7.7;
        c5.b = -1;
        c5.ia = new int[] { 300, 301, 302 };
        c5.f = -1;
        c5.j = 0xfff;
        Object arg = c5;
        final ByteArrayOutputStream binary = decompile(cp, drv, new HeapMemory(false), cm, arg);

        Task.Sender sender = new Task.Sender() {
            public void write(OutputStream out) throws IOException {
                binary.writeTo(out);
            }
        };

        Task.Receiver recv = new Task.Receiver() {
            public void read(InputStream is) throws IOException {
                int i = Jvm.readInt(is);
                System.out.println("identity5 " + i);
            }
        };

        drv.runCode(null, sender, recv);
    }

    public static void identity5() {
        C5 c5 = (C5)Deserializer.read();
        if (c5.d == 7.7 && c5.b == -1 && c5.f == -1F && c5.j == 0xfff) {
            Jvm.writeInt(777);
            return;
        }

        print(10101);
        Util.exit(99);
    }

    public static class C10 {
        boolean b;
        char c;
        short s;
        int k;
        long j;
        float f;
        double d;

        boolean[] ba;
        char[] ca;
        short[] sa;
        int[] ka;
        long[] ja;
        float[] fa;
        double[] da;
        C10E[] array;
    }

    public static class C10E {
        int value;
        public C10E() { value = 7; }
    }

    public @Test void testObj10() throws Exception {
        Task.Sender sender = new Task.Sender() {
            public void write(OutputStream out) throws IOException {
            }
        };

        Task.Receiver recv = new Task.Receiver() {
            public void read(InputStream is) throws IOException {
                int i = Jvm.readInt(is);
                System.out.println(" output: " + i);
                if (i != 111)
                    throw new RuntimeException("wrong!");
        }};

        C10 c10 = new C10();
        c10.b = false;
        c10.c = 0xffff;
        c10.s = 0x7fff;
        c10.k = 111;
        c10.j = 0xffffff;
        c10.f = -1.23F;
        c10.d = -1.2333;
        c10.ba = new boolean[] { true, false };
        c10.ca = new char[] { 'a', 'A' };
        c10.sa = new short[] { -1, 3 };
        c10.ka = new int[] { -10, 30 };
        c10.ja = new long[] { -100, 300 };
        c10.fa = new float[] { -1.23F, 3.141F };
        c10.da = new double[] { -1.23, 3.1415 };
        c10.array = new C10E[] { new C10E(), new C10E() };

        C4 c4 = new C4(789);
        C4 c4b = new C4(456);
        c4.left = c4b;
        c4.right = c4;
        c4b.left = c4;
        c4b.dval = 8.8;
        D4 d4 = new D4(c4);
        tester("javassist.offload.test.Runner", "identity10", new Object[] { c10, d4 }, null, recv);
    }

    public static void identity10(C10 c10, D4 d4) {
        C4 c4 = d4.get();

        if (c10.b) print("true "); else print("false ");
        printHex(c10.c);
        printHex(c10.s);
        print(c10.k);
        print(c10.j);   // printHex(c10.j);  calls Long.valueOf()
        print(c10.f);
        print(c10.d);
        print(c10.ba.length);
        if (c10.ba[0]) print("true"); else print("false");
        print(c10.fa[1]);
        print(c10.da[1]);
        print(c10.array[1].value);
        print(c4.value);
        printHex(c4.left);
        printHex(c4.right);
        printHex(c4);
        
        if (c10.b == false && c10.c == 0xffff && c10.s == 0x7fff && c10.k == 111 && c10.j == 0xffffff
            && c10.f == -1.23F && c10.d == -1.2333
            && c10.ba.length == 2 && c10.ba[0] == true && c10.fa[1] == 3.141F && c10.da[1] == 3.141500
            && c10.array[1].value == 7)
            if (c4.value == 789  && c4.left != null && c4.right == c4) {
                C4 c4b = c4.left;
                printHex(c4.left);
                if (c4b.value == 456 && c4b.left == c4 && c4b.right == null
                        && c4.dval == 7.7 && c4b.dval == 8.8) {
                    Jvm.writeInt(c10.k);
                    return;
                }
            }

        print(10101);
        Util.exit(99);
    }

    public @Test void testObj11() throws Exception {
        Object r = tester("javassist.offload.test.Runner", "identity11", new Object[] { new C10E() });
        int i = ((Integer)r).intValue();
        if (i != 7)
            throw new Exception("int return value: " + i);
        else
            System.out.println("testObj11 OK");

        r = tester("javassist.offload.test.Runner", "identity12", new Object[] { new C10E() });
        double d = ((Double)r).doubleValue();
        if (d != 7.0)
            throw new Exception("double return value: " + d);
        else
            System.out.println("testObj12 OK");

        r = tester("javassist.offload.test.Runner", "identity13", new Object[] { new C10E() });
        boolean b = ((Boolean)r).booleanValue();
        if (b)
            throw new Exception("boolean return value: " + b);
        else
            System.out.println("testObj13 OK");

        r = tester("javassist.offload.test.Runner", "identity14", new Object[] { new C10E() });
        double[] da = (double[])r;
        if (da.length == 2 && da[1] == 7.0)
            System.out.println("testObj14 OK");
        else
            throw new Exception("double[] return value: " + b);
    }

    public static int identity11(C10E c10) {
        return c10.value;
    }

    public static double identity12(C10E c10) {
        return c10.value;
    }

    public static boolean identity13(C10E c10) {
        return c10.value != 7;
    }

    public static double[] identity14(C10E c10) {
        return new double[] { -c10.value, c10.value };
    }

    static class C15 {
        int id;
        C15 next;
        C15(int i) { id = i; next = null; } 
        C15(int i, C15 n) { id = i; next = n; } 

        static int svalue = 123;
        static C15 c15 = new C15(7);
        static C15 c15s = new C15sub(9);
        static double dvalue;
        static C15b c15b = new C15b(3);
    }

    static class C15sub extends C15 {
        C15sub(int i) { super(i * 10); }
    }

    static class C15b {
        int value;
        C15b(int i) { value = i; }
    }

    public @Test void testObj15() throws Exception {
        C15 c15 = new C15(2, new C15sub(3));
        Object r = tester("javassist.offload.test.Runner", "identity15", new Object[] { c15, c15.next, c15.next });
        int i = ((Integer)r).intValue();
        if (i != 7)
            throw new Exception("int return value: " + i);
        else
            System.out.println("testObj15 OK");
    }

    public static int identity15(C15 c15, C15sub c15s, C15 c15s2) {
        if (c15.next != c15s || c15.next != c15.next
            || c15.next != (C15)c15s || c15s != c15s2)
            Util.exit(99);

        if (c15.id != 2 || c15s.id != 30)
            Util.exit(98);

        if (C15.svalue != 123 || C15.c15.id != 7 || C15.c15s.id != 90)
            Util.exit(97);

        return 7;
    }

    public static class Cstr {
        String s;
        long j;
    }

    public @Test void testString() throws Exception {
        Cstr c = new Cstr();
        c.s = "1foo";
        Object r = tester("javassist.offload.test.Runner", "identityStr", new Object[] { "Hello\n", c });
        int i = ((Integer)r).intValue();
        if (i != 14)
            throw new Exception("int return value: " + i);
        else
            System.out.println("testString OK");
    }

    public static int identityStr(String s, Cstr c) {
        printHex(c.s);
        printHex(c);
        String s2 = c.s;
        printHex(s2.length());
        print(s); print();
        printHex(s.charAt(0));
        String s3 = "123";
        return s.length() + c.s.length() + s3.length() + "1".length();
    }

    public @Test void testExchangeString() throws Exception {
        Task.Sender sender = new Task.Sender() {
            public void write(OutputStream out) throws IOException {
                Jvm.writeString(out, "ABD ;");
                Jvm.writeString(out, "");
            }
        };

        Task.Receiver recv = new Task.Receiver() {
            public void read(InputStream is) throws IOException {
                String s = Jvm.readString(is);
                String s2 = Jvm.readString(is);

                System.out.println(" testExchString output: `" + s + "`, `" + s2 + "`");
        }};

        tester("javassist.offload.test.Runner", "identityStr2", sender, recv);
    }

    public static void identityStr2() {
        String s = Jvm.readString();
        String s2 = Jvm.readString();

        print(s.length());
        printHex(s.charAt(0));
        print(s);
        print(s2);

        Jvm.writeString(s);
        Jvm.writeString(s2);
    }

    public @Test void testRemote() throws Exception {
        Object r = tester("javassist.offload.test.Runner", "identityRemote", new Object[] { 1000 });
        int i = ((Integer)r).intValue();
        if (i != 1127)
            throw new Exception("int return value: " + i);
        else
            System.out.println("testRemote OK");
    }

    public static int identityRemote(int k) {
        print("call @remote\n");
        double[] d = { 1.1, 2.2, 3.3 };
        double[] d2 = callbackRemote(d);
        int r = callbackRemote(124);
        print(r); print();
        return r + k + d2.length;
    }
 
    @Remote public static int callbackRemote(int k) {
        return k;
    }

    @Remote public static double[] callbackRemote(double[] d) {
        for (double v: d) {
            System.out.print(v);
            System.out.print(", ");
        }
        System.out.println();
        return d;
    }

    public @Test void testRemote2() throws Exception {
        String str = "remoteStr";
        Object r = tester("javassist.offload.test.Runner", "identityRemote2", new Object[] { str });
        if (!r.equals(str))
            throw new Exception("testRemote2: " + r);
        else
            System.out.println("testRemote2 OK");
    }

    public static String identityRemote2(String k) {
        return callbackRemote2(k);
    }
 
    @Remote public static String callbackRemote2(String s) {
        return s;
    }

    public @Test void testStandalone() throws Exception {
        aloneTester("javassist.offload.test.Runner", "identityAlone", new Object[] { 3000 });
        System.out.println("testStandalone OK");
    }

    public static int identityAlone(int k) {
        print(k);
        print("standalone\n");
        // double[] d = { 1.1, 2.2, 3.3 };
        // double[] d2 = callbackRemote(d);
        int r = callbackRemote(3344);
        print(r);
        print("done\n");
        return 0;
    }

    public static class StdDrvTest {
        int value;
        public StdDrvTest(int v) { value = v; }
        public int test(int i) {
            print(i);
            print("StdDrvTest\n");
            return i + value;
        }
    }

    public static class StdDrvTest2 extends StdDrvTest {
        int value;
        public StdDrvTest2(int v) { super(v); }
        public int test(int i) {
            print(i);
            print("StdDrvTest2\n");
            double[] d = { 1.1, 2.2, 3.3 };
            printC(Unsafe.toCArray(d));
            if (get1st(Unsafe.toCArray(d)) != 2.2)
                Util.exit(10);

            printStr(Unsafe.toCStr("unsafe"));
            return i + value - 1;
        }

        @Native("fprintf(stdout, \"%f --\", v1[1]);")
        public static void printC(double[] d) {}

        @Native("return v2[1];")
        public double get1st(double[] d) { return d[1]; }

        @Native("fputs((char*)v1, stdout);")
        public static void printStr(String s) {}
    }

    public @Test void testStdDriver2() throws Exception {
        StdDriver2 d = new StdDriver2();
        int i = (Integer)d.invoke(StdDrvTest.class, "test", new StdDrvTest(4000), new Object[] { 123 });
        if (i == 4123)
            System.out.println("testStdDriver OK");
        else
            throw new Exception("testStdDriver " + i);

        int j = (Integer)d.invoke(StdDrvTest.class.getDeclaredMethod("test", new Class<?>[] { int.class } ),
                                  new StdDrvTest2(4000), new Object[] { 123 }, new java.lang.reflect.Method[0]);
        if (j == 4122)
            System.out.println("testStdDriver OK");
        else
            throw new Exception("testStdDriver " + j);
    }

    public static class StdDrvTest3 {
        int value;
        public StdDrvTest3(int i) { value = i; }
        public void foo(int i) {
            Util.print(i * value).println();
        }
    }

    public @Test void testStdDriver() throws Exception {
        StdDriver drv = new StdDriver();
        final StdDrvTest3 sdt3 = new StdDrvTest3(10);
        final int i = 7;
        drv.invoke(new Runnable() {
            Runnable runnable;
            public void run() {
                sdt3.foo(i);
                runnable = new Runnable() {
                    public void run() {
                        Util.print("foo").println();
                    }
                };
                runnable.run();
            }
        });
    }

    public @Test void testStdDriver4() throws Exception {
        int i = new StdDriver().invoke(() -> {
            return 77;
        });
        if (i == 77)
            System.out.println("testStdDriver4 OK");
        else
            throw new Exception("testStdDriver4 " + i);
    }

    public @Test void testStdDriver5() throws Exception {
        double i = new StdDriver().invoke(() -> {
            return 77.7;
        });
        if (i == 77.7)
            System.out.println("testStdDriver5 OK");
        else
            throw new Exception("testStdDriver5 " + i);
    }

    @Metaclass(type=NativeClass.class, arg = "sizeof(double)")
    public static class NCTest { double i; }

    public @Test void testNativeClass() throws Exception {
        StdDriver2 d = new StdDriver2();
        int i = (Integer)d.invoke(Runner.class, "testNC", null, new Object[] { new NCTest() });
        if (i == 7)
            System.out.println("testNativeClass OK");
        else
            throw new Exception("testNativeClass " + i);
    }

    public static int testNC(NCTest nct) {
        NCTest nct2 = new NCTest();
        testNCb(Unsafe.toNativeBody(nct));
        testNCb(Unsafe.toNativeBody(nct2));
        Util.print(testNC2(nct)).print(" ").print(testNC2(nct2));
        testNC2(nct);
        testNC2(nct2);
        return 3 + testNC2(nct) + testNC2(nct2);
    }

    @Native("((double*)v1)[0] = 7.0;")
    public static void testNCb(NCTest obj) {}
    //public static void testNCb(byte[] obj) {}

    @Native("return v1->" + NativeClass.FLAG_FIELD + "++;")
    public static int testNC2(NCTest nct) { return 0; }

    public static class Inline1 {
        public int foo() { return 1; }
    }

    public static class Inline2 extends Inline1 {
        public int foo() { return 2; }
    }

    public static int testIn1(Inline1 in) {
        return in.foo();
    }

    public @Test void testInline1() throws Exception {
        StdDriver2 d = new StdDriver2();
        int i = (Integer)d.invoke(Runner.class, "testIn1", null, new Object[] { new Inline2() });
        if (i == 2)
            System.out.println("testInline1 OK");
        else
            throw new Exception("testInline1 " + i);
    }

    public static class CDispatch {
        public int foo() { return 1; }
        public int bar() { return 1; }
    }

    public static class CDispatch2 extends CDispatch {
        public int foo() { return 2; }
        public int bar() { return 2; }
    }

    public static class CDispatch3 extends CDispatch {
        public int foo() { return 3; }
        public int bar() { return 3; }
    }

    public @Test void testDispatcher() throws Exception {
        Object r = tester("javassist.offload.test.Runner", "funcDispatcher", new Object[] { 3, new CDispatch2() });
        int i = ((Integer)r).intValue();
        if (i != 232)
            throw new Exception("int return value: " + i);
        else
            System.out.println("testDispatcher OK");
    }

    public static int funcDispatcher(int k, CDispatch c) {
        CDispatch c2 = new CDispatch3();
        CDispatch c3;
        if (k < 0)
            c3 = new CDispatch();
        else
            c3 = new CDispatch2();

        return c.foo() + c2.foo() * 10 + c3.bar() * 100;
    }

    public @Test void testDispatcher1() throws Exception {
        Object r = tester("javassist.offload.test.Runner", "funcDispatcher1", new Object[] { 3, new CDispatch() });
        int i = ((Integer)r).intValue();
        if (i != 101)
            throw new Exception("int return value: " + i);
        else
            System.out.println("testDispatcher OK");
    }

    public static int funcDispatcher1(int k, CDispatch c) {
        CDispatch c3;
        if (k < 0)
            c3 = new CDispatch();
        else
            c3 = new CDispatch();

        return c.foo() + c3.bar() * 100;
    }

    public @Test void testDispatcher2() throws Exception {
        Object r = tester("javassist.offload.test.Runner", "funcDispatcher2", new Object[] { 3, new CDispatch2() });
        int i = ((Integer)r).intValue();
        if (i != 331)
            throw new Exception("int return value: " + i);
        else
            System.out.println("testDispatcher2 OK");
    }

    public static int funcDispatcher2(int k, CDispatch c) {
        int r = 0;
        CDispatch c3 = new CDispatch();
        if (k < 0) {
            r += c3.foo();
            c3 = new CDispatch2();
            r += c3.foo() * 10;
        }
        else {
            r += c3.foo();
            c3 = new CDispatch3();
            r += c3.foo() * 10;
        }

        return r + c3.foo() * 100;
    }

    public @Test void testDispatcher3() throws Exception {
        Object r = tester("javassist.offload.test.Runner", "funcDispatcher3", new Object[] { 3, new CDispatch2() });
        int i = ((Integer)r).intValue();
        if (i != 2222331)
            throw new Exception("int return value: " + i);
        else
            System.out.println("testDispatcher3 OK");
    }

    public static int funcDispatcher3(int k, CDispatch c) {
        int r = 0;
        CDispatch c3 = new CDispatch();
        for (int i = 1; i < 100000; i *= 100) {
            if (i % 2 == 0) {
                r += c3.foo() * i;
                c3 = new CDispatch2();
                r += c3.foo() * i * 10;
            }
            else {
                r += c3.foo() * i;
                c3 = new CDispatch3();
                r += c3.foo() * i * 10;
            }
        }

        return r + c3.foo() * 1000000;
    }

    static public interface Closure {
        double body(int i);
    }

    public @Test void testClosure() throws Exception {
        StdDriver2 d = new StdDriver2();
        double res = (Double)d.invoke(Runner.class, "closureTest", null, new Object[] {});
        if (res == 45.0)
            System.out.println("tetClosure OK");
        else
            throw new Exception("testClosure " + res);
    }

    public static double closureTest() {
        double sum = 0.0;
        final double[] a = new double[1];
        a[0] = 0.0;
        for (int i = 0; i < 10; i++)
            sum += new Closure() {
                    public double body(int i) {
                        a[0] += i;
                        return i;
                    }
                }.body(i);

        return sum;
    }

    public @Test void testMultiArray() throws Exception {
        StdDriver2 d = new StdDriver2();
        MultiArray ma = new MultiArray();
        double res = (Double)d.invoke(Runner.class, "multArrayTest", null, new Object[] { ma });
        if (res != 2000000.0)
            throw new Exception("etstMultiArray: " + res);

        System.out.println("testMultiArray: " + res);
    }

    public static double multArrayTest(MultiArray ma) {
        for (int i = 0; i < ma.xlen(); i++)
            for (int j = 0; j < ma.ylen(); j++)
                ma.set(i, j, 1.0);

        double sum = 0.0;
        for (int i = 0; i < ma.xlen(); i++)
            for (int j = 0; j < ma.ylen(); j++)
                sum += ma.get(i, j);

        return sum;
    }
    
    @Metaclass(type=NativeArrayClassMD.class, arg="double", args = { "xlength", "ylength" })
    public static class MultiArray {
        double[] data;

        public static final int xlength = 1000;
        public static final int ylength = 2000;
        public static int xlen() { return xlength; }
        public static int ylen() { return ylength; }

        @Native("")
        public MultiArray() { data = new double[xlen() * ylen()]; }

        @Native("return v1->body[v2][v3];")
        public double get(int i, int j) { return data[i * ylen() + j]; }

        @Native("v1->body[v2][v3] = v4;")
        public void set(int i, int j, double v) { data[i * ylen() + j] = v; }
    }

    public @Test void testMultiArray2() throws Exception {
        StdDriver2 d = new StdDriver2();
        DoubleArray2D ma = new DoubleArray2D(7, 3);
        double res = (Double)d.invoke(Runner.class, "multArrayTest2", null, new Object[] { ma });
        if (res != 9921.0)
            throw new Exception("testMultiArray2: " + res);

        System.out.println("testMultiArray2: " + res);
    }

    public static double multArrayTest2(DoubleArray2D ma) {
        for (int i = 0; i < ma.xsize; i++)
            for (int j = 0; j < ma.ysize; j++)
                ma.set(i, j, 1.0);

        double sum = 0.0;
        for (int i = 0; i < ma.xsize; i++)
            for (int j = 0; j < ma.ysize; j++)
                sum += ma.get(i, j);

        DoubleArray2D ma2 = new DoubleArray2D(9, 11);
        for (int i = 0; i < ma2.xsize; i++)
            for (int j = 0; j < ma2.ysize; j++)
                ma2.set(i, j, 100.0);

        for (int i = 0; i < ma2.xsize; i++)
            for (int j = 0; j < ma2.ysize; j++)
                sum += ma2.get(i, j);

        return sum;
    }

    public interface InnerI {
        int doit(int j);
    }

    public @Test void testInnerClass() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (Integer)d.invoke(Runner.class, "innerClassTest", null, new Object[] { 7 });
        if (res != 118)
            throw new Exception("testInnerClass: " + res);

        System.out.println("testInnerClass: " + res);
    }

    public static int innerDoit(InnerI i, int j) { return i.doit(j); }

    public static int innerClassTest(final int k) {
        int i = innerDoit(new InnerI() {
            int value = 10;
            public int doit(int j) {
                return value + j + k + 1;
            }
        }, 100);

        return i;
    }

    public @Test void testImmutable() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (Integer)d.invoke(Runner.class, "immutableTest", null, new Object[] { 7 });
        if (res != 37)
            throw new Exception("testImmutableClass: " + res);

        System.out.println("testImmutableClass: " + res);
    }

    @Metaclass(type=ImmutableClass.class)
    public static class ImmutableObj {
        public final int value;
        public ImmutableObj(int v) {
            value = v;
        }

        public int get() { return value; }
    }

    public static int immutableTest(int k) {
        ImmutableObj obj = new ImmutableObj(30);
        return obj.get() + k;
    }

    public @Test void testImmutable2() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (Integer)d.invoke(Runner.class, "immutableTest2", null, new Object[] { 8, 10 });
        if (res != 807070)
            throw new Exception("testImmutableClass2: " + res);

        System.out.println("testImmutableClass2: " + res);
    }

    @Metaclass(type=ImmutableClass.class)
    public static interface ImmutableObj2I {
        int test(int k);
    }

    public static class ImmutableObj2A implements ImmutableObj2I {
        public final int value;
        public ImmutableObj2A(int v) {
            value = v;
        }

        public int test(int k) { return value * k; }
    }

    public static class ImmutableObj2B implements ImmutableObj2I {
        public final int value;
        public final int value2;
        public ImmutableObj2B(int v) {
            value = v;
            value2 = v + 1;
        }

        public int test(int k) { return value * k * 100; }
    }

    public static int immutableTest2(int p, int k) {
        final int value = p;
        int res = immutableTest2sub(
                      new ImmutableObj2I() {
                        public int test(int k) {
                            return k * value * 10000;
                        }
                      }, k);
        return immutableTest2sub(new ImmutableObj2A(7), k)
               + immutableTest2sub(new ImmutableObj2B(7), k)
               + res; 
    }

    public static int immutableTest2sub(ImmutableObj2I obj, int k) {
        return obj.test(k);
    }

    public @Test void testImmutable3() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (Integer)d.invoke(Runner.class, "immutableTest3", null, new Object[] { -8, 10 });
        if (res != 7000)
            throw new Exception("testImmutableClass3: " + res);

        System.out.println("testImmutableClass3: " + res);
    }

    public static int immutableTest3(int p, int k) {
        ImmutableObj2I obj;
        if (p > 0)
            obj = new ImmutableObj2A(7);
        else
            obj = new ImmutableObj2B(7);

        return immutableTest2sub(obj, k);
    }

    public static class ImmutableObj2C {
        public ImmutableObj2I value;
        public ImmutableObj2C(ImmutableObj2I v) {
            value = v;
        }

        public int test(int k) { return value.test(k); }
    }

    public @Test void testImmutable4() throws Exception {
        StdDriver2 d = new StdDriver2();
        ImmutableObj2B b = new ImmutableObj2B(3);
        ImmutableObj2I i = b;
        ImmutableObj2C c = new ImmutableObj2C(i);
        int res = (Integer)d.invoke(Runner.class, "immutableTest4", null, new Object[] { b, i, c });
        if (res != 3780000)
            throw new Exception("testImmutableClass4: " + res);

        System.out.println("testImmutableClass4: " + res);
    }

    public static int immutableTest4(ImmutableObj2B b, ImmutableObj2I i, ImmutableObj2C c) {
        ImmutableObj2I obj;
        if (b.value > 0)
            obj = new ImmutableObj2A(7);
        else
            obj = i;

        return immutableTest2sub(obj, i.test(2) * c.test(3));
    }

    public static class ImmutableObj2D implements ImmutableObj2I {
        public int value;
        public ImmutableObj2D(int v) {
            value = v;
        }

        public int test(int k) { return value * k; }
    }

    public @Test void testImmutable5() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (Integer)d.invoke(Runner.class, "immutableTest5", null, new Object[0]);
        if (res != 418)
            throw new Exception("testImmutableClass5: " + res);

        System.out.println("testImmutableClass5: " + res);
    }

    public static int immutableTest5() {
        ImmutableObj5 obj = new ImmutableObj5();
        ImmutableObj5 obj2 = immutableTest5c();
        ImmutableObj5 obj3 = obj2;
        int k = immutableTest5b();
        return obj.value + obj2.value + obj3.value + obj2.value2 + obj3.value2 + k;
    }

    @Inline public static int immutableTest5b() {
        ImmutableObj5 obj = new ImmutableObj5();
        ImmutableObj5 obj2 = immutableTest5c();
        ImmutableObj5 obj3 = obj2;
        return obj.value + obj2.value + obj3.value + obj2.value2 + obj3.value2;
    }

    public static ImmutableObj5 immutableTest5c() {
        return new ImmutableObj5();
    }

    @Metaclass(type=ImmutableClass.class)
    public static class ImmutableObj5 {
        public final int value = 3;
        public final int value2 = 100;
    }

    public @Test void testImmutableArray() throws Exception {
        StdDriver2 d = new StdDriver2();
        ImmutableObj2D b = new ImmutableObj2D(3);
        ImmutableObj2I i = b;
        int res = (Integer)d.invoke(Runner.class, "immutableTestArray", null, new Object[] { b, i });
        if (res != 336)
            throw new Exception("testImmutableArray: " + res);

        System.out.println("testImmutableArray: " + res);
    }

    public static int immutableTestArray(ImmutableObj2D b, ImmutableObj2I i) {
        ImmutableObj2D[] array = new ImmutableObj2D[10];
        ImmutableObj2I[] array2 = new ImmutableObj2I[10];
        ImmutableObj2I[] array3 = array;

        array[0] = b;
        array[1].value = b.value;
        array2[0] = b;
        array3[0] = i;
        return array[0].value + array2[0].test(10) + array3[0].test(100) + array[1].value;
    }

    public @Test void testRemoteAndCallback() throws Exception {
        StdDriver2 d = new StdDriver2();
        int j = (Integer)d.invoke(Runner.class.getDeclaredMethod("remoteAndCallback", new Class<?>[] { int.class } ),
                                  null, new Object[] { 3 },
                                  new java.lang.reflect.Method[] { Runner.class.getDeclaredMethod("remoteAndCallback3", new Class<?>[] { int.class })});
        if (j == 5)
            System.out.println("testRemoteAndCallback OK");
        else
            throw new Exception("testRemoteAndCallback " + j);

        new Thread() {
            @Override public void run() {
                try {
                    Callback.invoke(0, int.class, 3);
                }
                catch (RuntimeException e) {
                    System.out.println("OK " + e.getCause().getMessage());
                }
            }
        }.start();
    }

    public static int remoteAndCallback(int k) {
        return remoteAndCallback2(k);
    }

    @Remote public static int remoteAndCallback2(int k) {
        return Callback.invoke(0, int.class, k + 1);
    }

    public static int remoteAndCallback3(int k) {
        Util.print("get ");
        Util.print(k);
        Util.println();
        return k + 1;
    }

    public @Test void testRemoteBandwidth() throws Exception {
        StdDriver2 d = new StdDriver2();
        int k = 1000000; // 1,000 K
        double j = (Double)d.invoke(Runner.class.getDeclaredMethod("remoteBandwidth", new Class<?>[] { int.class } ),
                                  null, new Object[] { k },
                                  new java.lang.reflect.Method[] { Runner.class.getDeclaredMethod("remoteBandwidth3", new Class<?>[] { double[].class })});
        if (j == 99.9)
            System.out.println("testRemoteBandwidth OK");
        else
            throw new Exception("testRemoteBandwidth " + j);
    }

    public static double remoteBandwidth(int k) {
        double[] d = new double[k];
        d[k - 1] = 99.9;
        double r = remoteBandwidth2(d);
        long t0 = Util.time();
        int n = 1;
        for (int i = 0; i < n; i++)
            r = remoteBandwidth2(d);

        long t = Util.time() - t0;
        Util.print(t);
        Util.print(" micro sec. ");
        Util.print(k * 8 * 2 * n / t);
        Util.print(" Mbyte/s (double). ");
        Util.println();

        t = Util.time();
        double[] d2 = new double[k];
        for (int j = 0; j < 10; j++)
            for (int i = 0; i < d2.length; i++)
                d2[i] = d[i];

        Util.print(k * 8 * 10 / (Util.time() - t));
        Util.print(" Mbyte/s (double within a process)");
        Util.println();

        Unsafe.free(d2);
        Unsafe.free(d);
        return r;
    }

    @Remote public static double remoteBandwidth2(double[] k) {
    	return Callback.invoke(0, double.class, k);
        // return remoteBandwidth3(k);
    }

    public static double remoteBandwidth3(double[] k) {
        double r = k[k.length - 1];
        Unsafe.free(k);
        return r;
    }

    public @Test void testRemoteBandwidthInt() throws Exception {
        StdDriver2 d = new StdDriver2();
        int k = 1000000; // 1,000 K
        int j = (Integer)d.invoke(Runner.class.getDeclaredMethod("remoteBandwidthInt", new Class<?>[] { int.class } ),
                                  null, new Object[] { k },
                                  new java.lang.reflect.Method[] { Runner.class.getDeclaredMethod("remoteBandwidthInt3", new Class<?>[] { int[].class })});
        if (j == 999)
            System.out.println("testRemoteBandwidthInt OK");
        else
            throw new Exception("testRemoteBandwidthInt " + j);
    }

    public static int remoteBandwidthInt(int k) {
        int[] d = new int[k];
        d[k - 1] = 999;
        int r = remoteBandwidthInt2(d);
        long t0 = Util.time();
        int n = 1;
        for (int i = 0; i < n; i++)
            r = remoteBandwidthInt2(d);

        long t = Util.time() - t0;
        Util.print(t);
        Util.print(" micro sec. ");
        Util.print(k * 4 * 2 * n / t);
        Util.print(" Mbyte/s (int). ");
        Util.println();

        t = Util.time();
        int[] d2 = new int[k];
        for (int j = 0; j < 10; j++)
            for (int i = 0; i < d2.length; i++)
                d2[i] = d[i];

        Util.print(k * 4 * 10 / (Util.time() - t));
        Util.print(" Mbyte/s (int within a process)");
        Util.println();

        Unsafe.free(d2);
        Unsafe.free(d);
        return r;
    }

    @Remote public static int remoteBandwidthInt2(int[] k) {
        // return remoteBandwidthInt3(k);
        return Callback.invoke(0, int.class, k);
    }

    public static int remoteBandwidthInt3(int[] k) {
        int r = k[k.length - 1];
        Unsafe.free(k);
        return r;
    }

    public @Test void testRemoteBandwidthByte() throws Exception {
        StdDriver2 d = new StdDriver2();
        int k = 4000000 * 10; // 40 MB
        int j = (Integer)d.invoke(Runner.class.getDeclaredMethod("remoteBandwidthByte", new Class<?>[] { int.class } ),
                                  null, new Object[] { k },
                                  new java.lang.reflect.Method[] { Runner.class.getDeclaredMethod("remoteBandwidthByte3", new Class<?>[] { byte[].class })});
        if (j == 99)
            System.out.println("testRemoteBandwidthByte OK");
        else
            throw new Exception("testRemoteBandwidthByte " + j);
    }

    public static int remoteBandwidthByte(int k) {
        byte[] d = new byte[k];
        d[k - 1] = 99;
        int r = remoteBandwidthByte2(d);
        long t0 = Util.time();
        int n = 1;
        for (int i = 0; i < n; i++)
            r = remoteBandwidthByte2(d);

        long t = Util.time() - t0;
        Util.print(t);
        Util.print(" micro sec. ");
        Util.print(k * 2 * n / t);
        Util.print(" Mbyte/s (byte). ").println();

        t = Util.time();
        byte[] d2 = new byte[k];
        for (int j = 0; j < 10; j++)
            for (int i = 0; i < d2.length; i++)
                d2[i] = d[i];

        Util.print(k * 10 / (Util.time() - t));
        Util.print(" Mbyte/s (byte within a process)");
        Util.println();

        Unsafe.free(d);
        Unsafe.free(d2);
        return r;
    }

    @Remote public static int remoteBandwidthByte2(byte[] k) {
        // return remoteBandwidthInt3(k);
        return Callback.invoke(0, int.class, k);
    }

    public static int remoteBandwidthByte3(byte[] k) {
        int r = k[k.length - 1];
        Unsafe.free(k);
        return r;
    }

    public static class ConsObj1 {
        public int foo() { return 1; }
    }

    public static class ConsObj2 extends ConsObj1 {
        public int foo() { return 2; }
    }

    public static class ConsObj {
        final ConsObj1 obj;
        public ConsObj(ConsObj1 p) {
            obj = p;
        }

        public ConsObj(ConsObj1 p, long i) {
            obj = new ConsObj1();
        }

        public ConsObj(ConsObj1 p, int i) {
            if (i > 0)
                obj = p;
            else
                obj = p;
        }

        public ConsObj(ConsObj1 p, double i) {
            if (i > 0.0)
                obj = new ConsObj1();
            else
                obj = p;
        }

        public int bar() { return obj.foo(); } 
    }

    public @Test void testConsObject() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "consObjectTest", null, new Object[] { new ConsObj2() });
        if (res == 6)
            System.out.println("tetConsObject OK");
        else
            throw new Exception("testConsObject " + res);
    }

    public static int consObjectTest(ConsObj1 p) {
        ConsObj obj = new ConsObj(p);
        ConsObj obj2 = new ConsObj(p, 3L);
        int r = obj.bar() + obj2.bar();
        return r + new ConsObj(p, 1).bar() + new ConsObj(p, 1.0).bar();
    }

    public @Test void testArrays() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "arraysTest", null, new Object[] { new int[3], new double[4], new Object[10] });
        if (res == 617)
            System.out.println("testArrays OK");
        else
            throw new Exception("testArrays " + res);
    }

    public static int arraysTest(int[] ia, double[] da, Object[] oa) {
        int[] ia2;
        double[] da2;
        Object[] oa2;
        if (true) {
            ia2 = ia;
            da2 = da;
            oa2 = oa;
        }
        ia2[0] = 100;
        da2[0] = 100.0;
        oa2[0] = new Object();
        int[] ia3 = new int[100];
        double[] da3 = new double[200];
        Object[] oa3 = new Object[300];
        ia3[0] = 10;
        da3[0] = 200.0;
        oa3[0] = new Object();
        return ia2.length + da2.length + oa2.length + ia3.length + da3.length + oa3.length;
    }

    public @Test void testF3Array() throws Exception {
        StdDriver2 d = new StdDriver2();
        FloatArray3D a = new FloatArray3D(10, 20, 30);
        float res = (float)d.invoke(Runner.class, "f3arrayTest", null, new Object[] { a }); 
        if (res == a.xsize * a.ysize * a.zsize * 2.0F)
            System.out.println("testF3Array OK");
        else
            throw new Exception("testF3Array " + res);

        FloatArray3D b = new FloatArray3D(10, 20, 30, false);
        float res2 = f3arrayTest(b);
        if (res2 == b.xsize * b.ysize * b.zsize * 2.0F)
            System.out.println("testF3Array (java) OK");
        else
            throw new Exception("testF3Array (java) " + res);
    }

    public static float f3arrayTest(FloatArray3D a) {
        for (int i = 0; i < a.xsize; i++)
            for (int j = 0; j < a.ysize; j++)
                for (int k = 0; k < a.zsize; k++)
                    a.set(i, j, k, 2.0F);

        float f = 0.0F;
        for (int i = 0; i < a.xsize; i++)
            for (int j = 0; j < a.ysize; j++)
                for (int k = 0; k < a.zsize; k++)
                    f += a.get(i, j, k);

        return f;
    }

    public @Test void testFloat2Array() throws Exception {
        StdDriver2 drv = new StdDriver2();
        FloatArray fa1 = new FloatArray(8, false);
        FloatArray2D fa2 = new FloatArray2D(8, 4, false);
        FloatArray4D fa4 = new FloatArray4D(8, 4, 2, 2, false);
        int res = (int)drv.invoke(Runner.class, "float2ArrayTest", null,
                            new Object[] { fa1, fa2, fa4, new Float2ArrayTestClass(1) });
        if (res == 311)
            System.out.println("testFloat2Array OK");
        else
            throw new Exception("testFloat2Array " + res);
    }

    public static int float2ArrayTest(FloatArray fa1, FloatArray2D fa2, FloatArray4D fa4, Float2ArrayTestClass cc) {
        fa1.set(1, 1);
        fa2.set(2,  3, 10);
        fa4.set(7, 3, 1,  0, 100);
        fa4.set(7, 3, 1,  1, 200);
        return (int)(fa1.get(1) + fa2.get(2, 3) + fa4.get(7, 3, 1, 0) + cc.value(fa4));
    }

    static class Float2ArrayTestClass {
        final int i;
        public Float2ArrayTestClass(int v) { i = v; }
        @Inline public float value(FloatArray4D fa) {
            return fa.get(7, 3, 1, i);
        }
    }

    public @Test void testDouble2Array() throws Exception {
        StdDriver2 drv = new StdDriver2();
        DoubleArray fa1 = new DoubleArray(8, false);
        DoubleArray2D fa2 = new DoubleArray2D(8, 4, false);
        int res = (int)drv.invoke(Runner.class, "double2ArrayTest", null,
                            new Object[] { fa1, fa2, new Double2ArrayTestClass(1) });
        if (res == 12)
            System.out.println("testDouble2Array OK");
        else
            throw new Exception("testDouble2Array " + res);
    }

    public static int double2ArrayTest(DoubleArray fa1, DoubleArray2D fa2, Double2ArrayTestClass cc) {
        fa1.set(1, 1);
        fa2.set(2,  3, 10);
        return (int)(fa1.get(1) + fa2.get(2, 3) + cc.value(fa1));
    }

    static class Double2ArrayTestClass {
        final int i;
        public Double2ArrayTestClass(int v) { i = v; }
        @Inline public double value(DoubleArray fa) {
            return fa.get(i);
        }
    }

    public @Test void testIntArray() throws Exception {
        StdDriver2 drv = new StdDriver2();
        IntArray ia1 = new IntArray(8, true);
        int res = (int)drv.invoke(Runner.class, "intArrayTest", null,
                            new Object[] { ia1 });
        if (res == 90)
            System.out.println("testIntArray OK");
        else
            throw new Exception("testIntArray " + res);
    }

    public static int intArrayTest(IntArray ia1) {
        ia1.set(2, 90);
        return ia1.get(2);
    }

    public @Test void testFinalAnno() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = -1;
        try {
            res = (int)d.invoke(Runner.class, "finalAnnoTest", null, new Object[0]); 
        } catch (DriverException e) {
            if (e.getCause().getMessage().contains("cannot set a @Final")) {
                System.out.println("testFinalAnno OK");
                return;
            }
        }

        throw new Exception("testFinalAnno " + res);
    }

    public static class FinalAnno {
        @Final int value = 0;
        void update(int k) { value = k; }
    }

    public static int finalAnnoTest() {
        FinalAnno f = new FinalAnno();
        f.update(3);
        return f.value;
    }

    public static class TestIntrinsic {
        public static int i1;
        public static Integer i2;
    }

    public @Test void testIntrinsic() throws Exception {
        StdDriver2 d = new StdDriver2();
        TestIntrinsic.i1 = 600;
        TestIntrinsic.i2 = 8000;
        int res = (int)d.invoke(Runner.class, "intrinsicTest", null, new Object[] { 3, 7 });
        if (res == 8673)
            System.out.println("testIntrinsic OK");
        else
            throw new Exception("testIntrinsic " + res);
    }

    public static int intrinsicTest(int i, Integer j) {
        Object obj = Integer.valueOf(7);
        Object obj2 = 3;
        String s = "poi";
        intrinsicTestF("foo", s, j, 13);
        return i + j.intValue() * 10 + TestIntrinsic.i1 + TestIntrinsic.i2;
    }

    @Intrinsic public static void intrinsicTestF(Object foo, Object var, Object var2, Object var3) {
        System.err.println("var2 " + var2 + " " + var3);
        Code c = (Code)foo;
        Object value = c.value();
        String s = value == null ? "" : value.toString();
        String s2 = ((Code)var).value().toString();
        CCode.make("/* intrinsic ").add(s).add(" ").add((Code)var).add(" ").add(s2).add(" */").emit();;
    }

    public @Test void testIntrinsicLoop() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "intrinsicLoopTest", null, new Object[] { 3, 7 });
        if (res == 73)
            System.out.println("testIntrinsicLoop OK");
        else
            throw new Exception("testIntrinsicLoop " + res);
    }

    @Metaclass(type=ImmutableClass.class)
    public interface IntrinsicLoopI {
        int run(int i);
    }

    public static int intrinsicLoopTest(int j, Integer k) {
        final double d[] = new double[1000];

        loop(d.length, new IntrinsicLoopI() {
            public int run(int i) {
                d[i] = 3.0;
                return 0;
            }
         });

        loop(d.length, new IntrinsicLoopI() {
            public int run(int i) {
                d[i] *= 2.0;
                return 0;
            }
         });

        return j + k.intValue() * 10;
    }

    public static void loop(int n, IntrinsicLoopI body) {
        int i = 0;
        testIntrinsicPragma(i, n, body.run(i));
    }

    @Intrinsic public static void testIntrinsicPragma(Object var, Object count, Object expr) {
        Code v = (Code)var;
        CCode.make("#pragma omp parallel").newLine()
                   .add("for (").add(v).add(" = 0; ").add(v).add(" < ").addValue((Code)count)
                   .add("; ").add(v).add("++){").newLine()
                   .add((Code)expr).add(";").newLine().add("}").noSemicolon().emit();
    }

    public @Test void testIntrinsic2() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "intrinsicTest2", null, new Object[] { 8, 7 });
        if (res == 834)
            System.out.println("testIntrinsi2c OK");
        else
            throw new Exception("testIntrinsic2 " + res);
    }

    public static int intrinsicTest2(int i, Integer j) {
        String s = "poi";
        int k = intrinsicTestF2("fooo", s, i, j);
        return k;
    }

    @Intrinsic public static int intrinsicTestF2(Object foo, Object var1, Object var2, Object var3) {
        // var2 is a Code whose type is int.
        // var3 is a Code whose type is Integer.
        System.err.println("var2/3 " + var2 + " " + var3);
        if (Code.inTranslation()) {
            Code c = (Code)foo;
            Object value = c.value();
            String s = value == null ? "" : value.toString();
            String s2 = ((Code)var1).value().toString();
            java.util.function.IntUnaryOperator f = i -> s.length() + s2.length() * 10 + i * 100;
            Code.changeCaller(f, (Code)var2);
            return 1;
        }
        else
            return 0;
    }

    public @Test void testIntrinsic3() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "intrinsicTest3", null, new Object[2]);
        if (res == 83)
            System.out.println("testIntrinsi2c OK");
        else
            throw new Exception("testIntrinsic2 " + res);
    }

    public static interface VecFunc {
        Vec apply(Vec v);
    }

    public static interface VecFunc3 {
        Vec apply(Vec v, Vec v2, Vec v3);
    }

    @Metaclass(type=ImmutableClass.class)
    public static final class Vec {
        // used as a target when invoking an @Intrinsic method
        public final static Vec instance = new Vec(0); 

        public int x;
        public Vec(int x) { this.x = x; }

        @Intrinsic public Vec add(Object vec) throws NotFoundException {
            if (Code.inTranslation()) {
                Code arg = (Code)vec;
                Code target = Code.calledObject();
                CtBehavior m = target.getMethodIfCall();
                if (m != null && m.getName().equals("add")) {
                    Code target2 = target.getCalledObjectIfCall();
                    Code[] args2 = target.getArgumentsIfCall();
                    VecFunc3 f = (v1, v2, v3) -> new Vec(v1.x + v2.x + v3.x);
                    Code.changeCaller(f, target2, args2[0], arg);
                }
                else {
                    VecFunc f = i -> i;
                    Code.changeCaller(f, arg);
                }

                return null;
            }
            else {
                return new Vec(this.x + ((Vec)vec).x);
            }
        }
    }

    public static int intrinsicTest3(int i, Integer j) throws NotFoundException {
        Vec v = new Vec(3);
        Vec v2 = new Vec(40);
        return v.add(v2).add(v2).x;     // add(add(v, v2), v2)
    }

    public @Test void testRecursion() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "recursionTest", null, new Object[] { 3, 7 });
        if (res == 27)
            System.out.println("testRecursion OK");
        else
            throw new Exception("testRecursion " + res);
    }

    public static int recursionTest(int j, int k) {
        return new RecTest().foo(j, k, null) + RecTest.fact(3);
    }

    public static class RecTest {
        public int foo(int n, int k, RecTest rt) {
            if (n > 1)
                return foo(n - 1, k, new RecTest()) + k;
            else
                return k;
        }
        public static int fact(int n) {
            if (n > 1)
                return fact(n - 1) * n;
            else
                return 1;
        }
    }

    public @Test void testForeignClass() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "foreignClassTest", null, new Object[] {});
        if (res == 1)
            System.out.println("testForeignClass OK");
        else
            throw new Exception("testForeginClass " + res);
    }

    @Metaclass(type=ForeignClass.class)
    public static class ForeignType {}

    @Native("return 0;")
    public static ForeignType getForeignType() { return null; }

    public static int foreignClassTest() {
        ForeignType t = getForeignType();
        Object obj = t;
        ForeignType t2 = (ForeignType)obj;
        return t2 == null ? 1 : 0; 
    }

    public static class FATest {
        public int i = 1;
        public static int s = 2;
    }

    public static class FATest2 extends FATest {
        public int j = 10;
        public static int t = 20; 
    }

    public @Test void testFieldAccess() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "fieldAccessTest", null, new Object[] { new FATest2(), new FATest2() });
        if (res == 36)
            System.out.println("testFieldAccess OK");
        else
            throw new Exception("testFieldAccess " + res);
    }

    public static int fieldAccessTest(FATest f, FATest2 f2) {
        return f.i + f.s + f2.i + f2.s + f2.j + f2.t;
    }

    @Metaclass(type=NativeArrayClass.class, arg="sizeof(FILE)")
    public static class NativeArrayC {
        @Native("")
        public NativeArrayC(int s) {}

        @Native("return ((void*)v1->body + v2 * sizeof(FILE));")
        public @NativePtr Object get(int i) { return null; }    
    }

    public @Test void testNativeArrayClass() throws Exception {
        StdDriver2 d = new StdDriver2();
        long res = (long)d.invoke(Runner.class, "nativeArrayTest", null, new Object[] {});
        if (res == 0L)
            System.out.println("testNativeArrayClass OK");
        else
            throw new Exception("testNativeArrayClass " + res);
    }

    public static long nativeArrayTest() {
        NativeArrayC a = new NativeArrayC(3);
        Object b = a.get(2);
        return checkNativeArray(b) - checkNativeArray2(a.get(0), 2);
    }

    @Native("return (long)v1;")
    public static long checkNativeArray(Object p) { return 0; }

    @Native("return (long)&((FILE*)v1)[v2];")
    public static long checkNativeArray2(Object p, int i) { return 0; }

    static class ArrayInit {
        int value;
        ArrayInit(int v) { value = v; }
    }

    public @Test void testArrayInit() throws Exception {
        StdDriver2 d = new StdDriver2();
        ArrayInit[] a = { new ArrayInit(3), new ArrayInit(40) };
        int[] a2 = { 0, 0 };
        int res = (int)d.invoke(Runner.class, "arrayInitTest", null, new Object[] { a, a2 });
        if (res == 4343)
            System.out.println("testArrayInit OK");
        else
            throw new Exception("testArrayInit " + res);
    }

    public static int arrayInitTest(ArrayInit[] a, int[] a2) {
        ArrayInit[] b = { new ArrayInit(300), new ArrayInit(4000) };
        int b2[] = { 0, 0, 0 };
        return a[0].value + a[1].value + b[0].value + b[1].value
               + a2[0] + a2[1] + b2[0] + b2[1];
    }

    public @Test void testDataflow() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "dataflowTest", null, new Object[] { new Inline1(), new Inline1(), 0});
        if (res == 23)
            System.out.println("testDataflow OK");
        else
            throw new Exception("testDataflow " + res);
    }

    public static int dataflowTest(Inline1 in1, Inline1 in2, int sum) {
        do {
            sum += in1.foo();
            in1 = new Inline2();
        } while (sum < 2);

        in2 = new Inline2();
        return sum + in2.foo() * 10;
    }

    public @Test void testDataflow2() throws Exception {
        StdDriver2 d = new StdDriver2();
        d.invoke(Runner.class, "dataflowTest2b", null, new Object[] { 2 });
        System.out.println("testDataflow2 OK");
    }

    public static void dataflowTest2b(int i) {
        while (true) {
            if (i-- <= 0)
                break;

            Util.print(i).println();
        }

        return;
    }

    public @Test void testInlining() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "inliningTest", null, new Object[] { new InlineT0(), new InlineT2() });
        if (res == 2212)
            System.out.println("testInlining OK");
        else
            throw new Exception("testInlining " + res);
        
    }

    public static class InlineT0 {
        final InlineT1 t1 = new InlineT2();
        int get() { return this.t1.get(); }
        int get2() { return this.t1.get2(); }
    }

    public static class InlineT1 {
        int get() { return 1; }
        int foo() { return 0; }
        int get2() { foo(); return 1; }
    }

    public static class InlineT2 extends InlineT1 {
        int v = 2;
        @Override int get() { return v; }
        @Override int foo() { return 0; }
        @Override int get2() { foo(); return v; }
    }

    @Inline public static int inliningTest(InlineT0 t0, InlineT1 t1b) {
        InlineT1 t1 = new InlineT1();
        int r = t1b.get2();
        return t0.get() + t1.get() * 10 + r * 100 + t0.get2() * 1000;
    }

    public @Test void testInlining2() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "inliningTest2", null, new Object[] {});
        if (res == 7)
            System.out.println("testInlining2 OK");
        else
            throw new Exception("testInlining2 " + res);
        
    }

    @Inline public static int inliningTest2() {
        return inliningTest2b(new Klass8());
    }

    public static int inliningTest2b(Klass8 k) {
        Klass8.classVar = 77;
        return k.getI();
    }

    public @Test void testInlining3() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "inliningTest3", null, new Object[] {});
        if (res == 7)
            System.out.println("testInlining3 OK");
        else
            throw new Exception("testInlining3 " + res);
    }

    @Inline public static int inliningTest3() {
        return inliningTest3b(3);
    }

    public static int inliningTest3b(int i) {
        int k;
        return k = 7;
    }

    public @Test void testInlining4() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "inliningTest4", null, new Object[] {});
        if (res == 6666667)
            System.out.println("testInlining4 OK");
        else
            throw new Exception("testInlining4 " + res);
    }

    @Inline public static int inliningTest4() {
        int i = inliningTest4b(4);
        int sum = i; 
        i = inliningTest4c(10);
        sum += i;
        i = inliningTest4d(100);
        sum += i;
        i = inliningTest4e(1000);
        sum += i;
        inliningTest4c2(10000);
        inliningTest4d2(100000);
        inliningTest4e2(1000000);
        return sum + inliningTest4Var;
    }

    public static int inliningTest4b(int i) {
        int k = 3;
        return k += i;
    }

    public static int inliningTest4c(int k) {
        int sum = 0;
        for (int i = 0; i < 4; i++)
            sum += i * k;

        return sum;
    }

    public static int inliningTest4d(int k) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            if (i >= 4)
                break;

            sum += i * k;
        }

        return sum;
    }

    public static int inliningTest4e(int k) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            if (i >= 4)
                return sum;

            sum += i * k;
        }

        return sum;
    }

    public static int inliningTest4Var = 0;

    public static void inliningTest4b2(int i) {
        int k = 3;
        inliningTest4Var += k + i;
    }

    public static void inliningTest4c2(int k) {
        int sum = 0;
        for (int i = 0; i < 4; i++)
            sum += i * k;

        inliningTest4Var += sum;
    }

    public static void inliningTest4d2(int k) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            if (i >= 4)
                break;

            sum += i * k;
        }

        inliningTest4Var += sum;
    }

    public static void inliningTest4e2(int k) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            if (i >= 4) {
                inliningTest4Var += sum;
                return;
            }

            sum += i * k;
        }

        inliningTest4Var += sum;
    }

    public @Test void testInlining5() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "inliningTest5", null, new Object[] {});
        if (res == 4)
            System.out.println("testInlining5 OK");
        else
            throw new Exception("testInlining5 " + res);
    }

    @Inline public static int inliningTest5() {
        byte[] a = inliningTest5b(3);
        return a.length;
    }

    public static byte[] inliningTest5b(int i) {
        int s = inliningTest5c();
        byte[] a = new byte[s];
        return a;
    }

    public static int inliningTest5c() {
        int s = inliningTest5d();
        if (s < 0)
            return 0;
        else
            return s;
    }

    public static int inliningTest5d() {
        int b0 = 4;
        int b1 = 0;
        int b2 = 0;
        int b3 = 0;
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    public @Test void testInlining6() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "inliningTest6", null, new Object[] {});
        if (res == 63)
            System.out.println("testInlining6 OK");
        else
            throw new Exception("testInlining6 " + res);
    }

    @Inline public static int inliningTest6() {
        return inliningTest6b(4, 3);
    }

    public static int inliningTest6b(int i, int j) {
        i = i + j;
        int k = inliningTest_fact(10);
        i += k;
        return inliningTest6c(i);
    }

    public static int inliningTest6c(int i) {
        return i + 1;
    }

    @Inline(value=false) public static int inliningTest_fact(int n) {
        if (n > 1) {
            int f = inliningTest_fact(n - 1);
            return f + n;
        }
        else
            return 1;
    }

    public @Test void testInlining7() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "inliningTest7", null, new Object[] {});
        if (res == inliningTest7())
            System.out.println("testInlining7 OK");
        else
            throw new Exception("testInlining7 " + res);
    }

    @Inline public static int inliningTest7() {
        int x = 3;
        return inliningTest7b(x * 10) + x;
    }

    public static int inliningTest7b(int i) {
        return i + inliningTest7c(i);
    }

    @Inline public static int inliningTest7c(int j) {
        if (j < 0)
            j += 3;
        else
            j = 100;

        return j * 1000;
    }

    @Metaclass(type=ImmutableClass.class, companion=ImmutableClass.Lambda.class)
    public static interface InliningTest8 {
        int applyAsInt(int i);
    }

    public @Test void testInlining8() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "inliningTest8", null, new Object[] {});
        if (res == inliningTest8())
            System.out.println("testInlining8 OK");
        else
            throw new Exception("testInlining8 " + res);
    }

    public static int inliningTest8() {
        InliningTest8 op = i -> i + 1;
        return op.applyAsInt(7);
    }

    public @Test void testInlining9() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "inliningTest9", null, new Object[] {});
        if (res == inliningTest9())
            System.out.println("testInlining9 OK");
        else
            throw new Exception("testInlining9 " + res);
    }

    public static int inliningTest9() {
        int k = inliningTest9b(8).value;
        return k + 1;
    }

    public static class Inline9 {
        public int value = 10;
    }

    @Inline public static Inline9 inliningTest9b(int k) {
        return new Inline9();
    }

    public @Test void testInlining10() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "inliningTest10", null, new Object[] { 3 });
        if (res == inliningTest10(3))
            System.out.println("testInlining10 OK");
        else
            throw new Exception("testInlining10 " + res);
    }

    @Metaclass(type=ImmutableClass.class)
    public static class InlineTest10 {
        public final int value;
        public final int value2;

        public InlineTest10(int v, int v2) {
            if (v2 > 0)
                value2 = v2;
            else
                value2 = -v2;

            value = v;
        }
    }

    @Inline public static int inliningTest10(int k) {
        InlineTest10 obj = new InlineTest10(k + 8, -3);
        return obj.value + obj.value2 * 100;
    }

    public @Test void testForLoop() throws Exception {
        StdDriver2 d = new StdDriver2();
        String[] args = { "one", "two", "three" }; 
        int res = (int)d.invoke(Runner.class, "forLoopTest", null, new Object[] { args }); 
        if (res == 16)
            System.out.println("forLoopTest OK");
        else
            throw new Exception("forLoopTest " + res);
    }

    public static int forLoopTest(String[] args) {
        int sum = 0;
        for (int i = 0; i < args.length; i++) {
            int s = args[i].length();
            for (int j = 0; j < s; j++)
                sum++;
        }

        for (int j = 0; j < 10; j += 2)
            sum++;

        return sum;
    }

    public @Test void testSimpleNew() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "simpleNewTest", null, new Object[0]);
        if (res == 7)
            System.out.println("simpleNewTest OK");
        else
            throw new Exception("simpleNewTest " + res);
    }

    public static int simpleNewTest() {
        new Object() {
            int value = 3;
            public String toString() { return "" + value; }
        };
        return 7;
    }

    public @Test void testInlineImmutable() throws Exception {
        //javassist.offload.Settings.Options.doInline = false;
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "inlineImmutableTest", null, new Object[] { new InlineImmKC() }); 
        if (res == 714)
            System.out.println("InlineImmutable OK");
        else
            throw new Exception("InlineImmutable " + res);
    }

    @Metaclass(type=ImmutableClass.class)
    public static interface InlineImmutableI {
        int foo();
    }

    public static class InlineImmutableC implements InlineImmutableI {
        int x = 7;
        public int foo() { return x; }
    }

    public static interface InlineImmutableI2 {
        int foo();
    }

    public static class InlineImmutableC2 implements InlineImmutableI2 {
        int x = 700;
        public int foo() { return x; }
    }

    public static interface InlineImmK {
        int foo(InlineImmutableI i);
        int bar(InlineImmutableI2 i);
    }

    public static class InlineImmKC implements InlineImmK {
        public int foo(InlineImmutableI i) { return i.foo(); }
        public int bar(InlineImmutableI2 i) { return i.foo(); }
    }

    @Inline public static int inlineImmutableTest(InlineImmK kernel) {
        int r = 0;
        for (int p = 0; p < 1; p++) {
            InlineImmutableI i = new InlineImmutableC();
            InlineImmutableI2 i2 = new InlineImmutableC2();
            if (p == 0) {
                int k = kernel.foo(i);
                int k2 = kernel.bar(i2);
                int m = i.foo();
                r += k + k2 + m;
            }
            else {
                int k = kernel.foo(i);
                int k2 = kernel.bar(i2);
                int m = i.foo();
                r += k + k2 + m;
            }
        }
        return r;
    }
   
    public @Test void testInlineImmutable2() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "inlineImmutableTest2", null, new Object[0]);
        if (res == 787)
            System.out.println("InlineImmutable2 OK");
        else
            throw new Exception("InlineImmutable2 " + res);
    }

    @Metaclass(type=ImmutableClass.class)
    public static class ImmutableTest2 {
        public final int k;
        public ImmutableTest2() { k = 7; }
    }

    public static class ImmutableTest2b {
        public final int k;
        public ImmutableTest2b() { k = 80; }
    }

    public static int inlineImmutableTest2() {
        return inlineImmutableTest2b();
    }

    @Inline public static int inlineImmutableTest2b() {
        ImmutableTest2 t = new ImmutableTest2();
        ImmutableTest2b t2 = new ImmutableTest2b();
        ImmutableTest2 t3 = inlineImmutableTest2c();
        return t.k + t2.k + t3.k * 100;
    }

    public static ImmutableTest2 inlineImmutableTest2c() {
        return new ImmutableTest2();
    }

    public @Test void testMPIRuntime() throws Exception {
        final int N = 3;
        MPIRuntime.start(N, new MpiRunTest1(N));
    }

    public static class MpiRunTest1 implements Runnable, Cloneable {
        int N;

        public MpiRunTest1(int n) { N = n; }

        public void run() {
            int rank = MPI.commRank();
            int size = MPI.commSize();
            if (size != N)
                throw new RuntimeException("testMPIRuntime size: " + size);

            if (rank == 0) {
                float[] a = new float[8];
                int[] b = new int[4];
                MPI.Request req = new MPI.Request();
                MPI.Request req2 = new MPI.Request();
                MPI.Request req3 = new MPI.Request();
                MPI.Request req4 = new MPI.Request();
                MPI.iRecv(a, 0, 3, 1, 0, req);
                MPI.iRecv(a, 4, 3, 2, 0, req2);
                MPI.iRecv(b, 0, 2, 1, 0, req3);
                MPI.iRecv(b, 2, 2, 2, 0, req4);
                MPI.wait(req);
                MPI.wait(req2);
                MPI.wait(req3);
                MPI.wait(req4);

                MPI.barrier();
                System.out.println(rank);
                float rf = MPI.allReduce(1f, MPI.sum());
                int ri = MPI.allReduce(10, MPI.sum());
                MPI.barrier();
                System.out.println(".." + rank);

                if (rf != 3f || ri != 30)
                    throw new RuntimeException("testMPIRuntime reduction: " + rf + " " + ri);

                float sum = 0;
                for (float e: a)
                    sum += e;

                if (sum != 9)
                    throw new RuntimeException("testMPIRuntime sum: " + sum);

                int isum = 0;
                for (int e: b)
                    isum += e;

                if (isum != 60)
                    throw new RuntimeException("testMPIRuntime (int) sum: " + isum);

                System.out.println("testMPIRuntime OK");
            }
            else {
                float[] a = new float[2];
                a[0] = a[1] = rank;
                int[] b = new int[2];
                b[0] = 10;
                b[1] = 20;
                MPI.Request req = new MPI.Request();
                MPI.iSend(a, 1, 0, 0, req);
                MPI.Request req2 = new MPI.Request();
                MPI.iSend(a, a.length, 0, 0, req2);

                MPI.Request req3 = new MPI.Request();
                MPI.iSend(b, b.length, 0, 0, req3);

                MPI.wait(req);
                MPI.wait(req2);
                MPI.wait(req3);

                MPI.barrier();
                System.out.println(rank);
                float fsum = MPI.allReduce(1f, MPI.sum());
                int isum = MPI.allReduce(10, MPI.sum());
                MPI.barrier();
                System.out.println(".." + rank + " " + fsum + " " + isum);
            }
        }
    }

    static class MpiRunC implements Cloneable {
        MpiRunC next;
        int value;
        String s;
    }

    static class MpiRunC2 extends MpiRunC {
        int[] iarray;
        String[] sarray;
        MpiRunC[] oarray;
    }

    public @Test void testMPIRuntime2() throws Exception {
        MpiRunC2 c2 = new MpiRunC2();
        c2.value = 7;
        c2.s = "foo";
        c2.iarray = new int[] { 2, 3 };
        c2.sarray = new String[] { "a", null, "b" };
        c2.oarray = new MpiRunC[2];

        MpiRunC c1 = new MpiRunC();
        c1.value = 8;
        c1.s = "bar";
        c1.next = null;

        MpiRunC c1b = new MpiRunC();
        c1b.value = 9;
        c1b.s = "baz";
        c1b.next = null;

        c2.oarray[1] = c1;
        c1.next = c1b;
        c1b.next = c2;
        c2.next = c1;

        MpiRunC2 d2 = (MpiRunC2)javassist.offload.lib.MPIRuntime.deepCopy(c2);
        if (d2.value == c2.value && d2.s == c2.s && d2.iarray[1] == 3
            && d2.sarray[1] == null && d2.sarray[0] == c2.sarray[0]
            && d2.next.value == c1.value && d2.next.next.next == d2)
            System.out.println("testMPIRuntime2 OK");
        else
            throw new Exception("testMPIRuntime2");
    }

    public @Test void testMallocFree() throws Exception {
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "mallocFreeTest", null, new Object[] { 3 }); 
        if (res == 4)
            System.out.println("mallocFree OK");
        else
            throw new Exception("mallocFree " + res);

        if (useGC)
            d.doGarbageCollection();

        res = (int)d.invoke(Runner.class, "mallocFreeTest", null, new Object[] { 3 }); 
        if (res == 4)
            System.out.println("mallocFree 2 OK");
        else
            throw new Exception("mallocFree 2 " + res);
    }

    static class MallocFree {
        int i;
        MallocFree mf;
    }

    public static int mallocFreeTest(int i) {
        MallocFree mf = new MallocFree();
        MallocFree mf2 = new MallocFree();
        mf.i = i;
        mf2.i = i + 1;
        mf.mf = mf2;
        int j = mf.mf.i;
        Unsafe.free(mf.mf);
        Unsafe.free(mf);
        return j;
    }

    public static class DeadcodeFoo {
        @Final public DeadcodeFoo next;
        @Final public static DeadcodeFoo value;
    }

    public @Test void testDeadcode() throws Exception {
        DeadcodeFoo foo = new DeadcodeFoo();
        foo.next = null;
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "deadcodeTest", null, new Object[] { foo, null }); 
        if (res == 1017)
            System.out.println("deadcodeTest OK");
        else
            throw new Exception("deadcodeTest " + res);
    }

    public static int deadcodeTest(DeadcodeFoo df1, DeadcodeFoo df2) {
        int k = 0;
        DeadcodeFoo df3 = df1.next;

        if (df1.next == null)
            k += 7;

        if (df1.next != null) {
            elimintated();
            k += 1;
        }

        if (df2 == null)
            k += 10;

        if (df2 != null) {
            elimintated();
            k += 100;
        }

        if (df3 == null)
            k += 1000;
        else {
            elimintated();
            k += 10000;
        }

        return k;
    }

    public static class DeadcodeFoo2 {
        @Final public boolean value;
        @Final public DeadcodeFoo2 next;
        @Final public static boolean svalue;
    }

    public @Test void testDeadcode2() throws Exception {
        DeadcodeFoo2 foo = new DeadcodeFoo2();
        foo.next = new DeadcodeFoo2();
        StdDriver2 d = new StdDriver2();
        int res = (int)d.invoke(Runner.class, "deadcodeTest2", null, new Object[] { foo, false }); 
        if (res == 0)
            System.out.println("deadcodeTest2 OK");
        else
            throw new Exception("deadcodeTest2 " + res);
    }

    public static int deadcodeTest2(DeadcodeFoo2 df, boolean b) {
        int k = 0;
        boolean b2 = df.value;
        boolean b3 = df.next.value;

        if (b) {
            elimintated();
            k += 1;
        }

        if (b2) {
            elimintated();
            k += 10;
        }

        if (b3) {
            elimintated();
            k += 100;
        }

        if (df.value) {
            elimintated();
            k += 1000;
        }

        if (df.next.value) {
            elimintated();
            k += 10000;
        }

        if (df.value){
            int v = k;
            for (int j = 1; j < 10; j++) {
                elimintated();
                v += j;
            }

            if (v > 0) {
                elimintated();
                k += v;
            }
        }

        return k;
    }

    @Intrinsic
    public static void elimintated() {
        throw new RuntimeException("not eliminated!");
    }

    public static class DeadcodeVec {
        public float x, y;
        /* @Final */ DeadcodeVec next;

        public DeadcodeVec(float x, float y) {
            this.x = x;
            this.y = y;
            this.next = null;
        }

        public float evalX() {
            if (next == null)
                return x;
            else
                return x + next.evalX(); 
        }

        public float evalY() {
            if (next == null)
                return y;
            else
                return y + next.evalY(); 
        }

        public void evalSet() {
            x = next.evalX();
            y = next.evalY();
        }
    }

    public @Test void testDeadcode3() throws Exception {
        DeadcodeVec a = new DeadcodeVec(1, 20);
        DeadcodeVec b = new DeadcodeVec(3, 40);
        DeadcodeVec c = new DeadcodeVec(5, 30);
        DeadcodeVec d = new DeadcodeVec(0, 0);
        a.next = b;
        b.next = c;
        d.next = a;

        StdDriver2 drv = new StdDriver2();
        int res = (int)drv.invoke(Runner.class, "deadcodeTest3", null, new Object[] { d });
        if (res == 99)
            System.out.println("deadcodeTest3 OK");
        else
            throw new Exception("deadcodeTest3 " + res);
    }
    
    public static int deadcodeTest3(DeadcodeVec expr) {
        expr.evalSet();
        return (int)(expr.x + expr.y);
    }

    public static class DeadcodeVec2 {
        public float x, y;
        @Final DeadcodeVec2 next;

        public DeadcodeVec2(float x, float y) {
            this.x = x;
            this.y = y;
            this.next = null;
        }

        public float evalX() {
            if (next == null)
                return x;
            else
                return x + next.evalX(); 
        }

        public float evalY() {
            if (next == null)
                return y;
            else
                return y + next.evalY(); 
        }

        public void evalSet() {
            x = next.evalX();
            y = next.evalY();
        }
    }

    public @Test void testDeadcode4() throws Exception {
        DeadcodeVec2 a = new DeadcodeVec2(1, 20);
        DeadcodeVec2 b = new DeadcodeVec2(3, 40);
        DeadcodeVec2 c = new DeadcodeVec2(5, 30);
        DeadcodeVec2 d = new DeadcodeVec2(0, 0);
        a.next = b;
        b.next = c;
        d.next = a;

        StdDriver2 drv = new StdDriver2();
        int res = (int)drv.invoke(Runner.class, "deadcodeTest4", null, new Object[] { d });
        if (res == 99)
            System.out.println("deadcodeTest4 OK");
        else
            throw new Exception("deadcodeTest4 " + res);
    }
    
    public static int deadcodeTest4(DeadcodeVec2 expr) {
        expr.evalSet();
        return (int)(expr.x + expr.y);
    }

    @Metaclass(type=ImmutableClass.class)
    interface CastTestFunc {
        int apply();
    }

    static class CastTestFunc2 implements CastTestFunc {
        public int apply() { return 1; }
    }

    static class CastTestBody {
        final CastTestFunc f = new CastTestFunc2(); 
    }

    public @Test void testCast() throws Exception {
        StdDriver2 drv = new StdDriver2();
        int res = (int)drv.invoke(Runner.class, "castTest", null, new Object[] { new CastTestBody() });
        if (res == 1)
            System.out.println("castTest OK");
        else
            throw new Exception("castTest " + res);
    }

    public static int castTest(CastTestBody b) {
        return castTestSub(b.f);
    }

    public static int castTestSub(CastTestFunc f) {
        return f.apply();
    }

    public @Test void testLambda() throws Exception {
        StdDriver2 drv = new StdDriver2();
        int debug = Options.debug;
        //Settings.Options.debug = 2;
        int res = (int)drv.invoke(Runner.class, "lambdaTest", null, new Object[0]);
        Options.debug = debug;
        if (res == 12)
            System.out.println("lambdaTest OK");
        else
            throw new Exception("lambdaTest " + res);
    }

    public static int lambdaTest() {
        int i = 3;
        double d = 0.14;
        Runnable r = () -> Util.print(d).print(i).println();
        r.run();
        Consumer<String> f = lambdaTest1a();
        f.accept("Hello World");
        return lambdaTest1c(10.0).apply(2);
    }

    public static Consumer<String> lambdaTest1a() {
        return Runner::lambdaTest1b;
    }

    public static void lambdaTest1b(String s) {
        Util.print(s).println();
    }

    public static IntFunction<Integer> lambdaTest1c(double d) {
        return i -> (int)d + i;
    }

    public @Test void testLambda2() throws Exception {
        StdDriver2 drv = new StdDriver2();
        int debug = Options.debug;
        //Settings.Options.debug = 2;
        int res = (int)drv.invoke(Runner.class, "lambdaTest2", null, new Object[0]);
        Options.debug = debug;
        if (res == 13)
            System.out.println("lambdaTest2 OK");
        else
            throw new Exception("lambdaTest2 " + res);
    }

    @Metaclass(type=ImmutableClass.class)
    public interface LambdaFuncF {
        float apply(float a, float b);
    }

    public static int lambdaTest2() {
        float delta = 10;
        return (int)lambdaTest2((a, b) -> a + b + delta);
    }

    @Inline public static float lambdaTest2(LambdaFuncF f) {
        return f.apply(1, 2);
    }

    public @Test void testLambda3() throws Exception {
        StdDriver2 drv = new StdDriver2();
        int debug = Options.debug;
        //Settings.Options.debug = 2;
        int res = (int)drv.invoke(Runner.class, "lambdaTest3", null, new Object[] { 4 });
        Options.debug = debug;
        if (res == 10)
            System.out.println("lambdaTest3 OK");
        else
            throw new Exception("lambdaTest3 " + res);
    }

    public static int lambdaTest3(int k) {
        float j = k + 3;
        LambdaFuncF f = (a, b) -> {
            float sum = j + a;
            return sum + b;
        };
        return lambdaTest3b(f);
    }

    public static int lambdaTest3b(LambdaFuncF f) {
        float a = lambdaTest3(f);
        return (int)a;
    }

    @Inline public static float lambdaTest3(LambdaFuncF f) {
        return f.apply(1, 2);
    }

    public @Test void testObjectInlining() throws Exception {
        StdDriver2 drv = new StdDriver2();
        int res = (int)drv.invoke(Runner.class, "objectInlineTest", null, new Object[] { new InlinedObj() });
        if (res == 73 * 3)
            System.out.println("objectInlineTest OK");
        else
            throw new Exception("objectInlineTest " + res);
    }

    static class InlinedObj {
        int value;
    }

    public static int objectInlineTest(InlinedObj obj) {
        obj.value = 3;
        int i = objectInlineTest2(obj);
        return obj.value + i;
    }

    @Inline(object=true) static int objectInlineTest2(InlinedObj obj) {
        obj.value += 70;
        return obj.value + objectInlineTest3(obj);
    }

    static int objectInlineTest3(InlinedObj obj) {
        return obj.value;
    }

    public @Test void testObjectInliningB() throws Exception {
        StdDriver2 drv = new StdDriver2();
        int res = (int)drv.invoke(Runner.class, "objectInlineTestB", null,
                            new Object[] { new InlinedObj(), new Object(), new InlinedObj() });
        if (res == 73 * 3 + 2000)
            System.out.println("objectInlineTestB OK");
        else
            throw new Exception("objectInlineTestB " + res);
    }

    public static int objectInlineTestB(InlinedObj obj, Object obj2, InlinedObj obj3) {
        obj.value = 3;
        obj3.value = 1000;
        int i = objectInlineTestB2(obj, obj2, obj3);
        return obj.value + i;
    }

    @Inline(object=true) static int objectInlineTestB2(InlinedObj obj, Object obj2, InlinedObj obj3) {
        obj.value += 70;
        return obj.value + objectInlineTestB3(obj, obj2) + objectInlineTestB4(obj3);
    }

    static int objectInlineTestB3(InlinedObj obj, Object obj2) {
        return obj.value;
    }

    /* This will not be inlined because the body has two statements.
     */
    static int objectInlineTestB4(InlinedObj obj) {
        obj.value += 1000;
        return obj.value;
    }

    public @Test void testObjectInliningC() throws Exception {
        StdDriver2 drv = new StdDriver2();
        int res = (int)drv.invoke(Runner.class, "objectInlineTestC", null,
                            new Object[] { new InlinedObj(), new Object(), new InlinedObj() });
        if (res == 17400 + 75 * 2)
            System.out.println("objectInlineTestC OK");
        else
            throw new Exception("objectInlineTestC " + res);
    }

    public static int objectInlineTestC(InlinedObj obj, Object obj2, InlinedObj obj3) {
        obj.value = 3;
        obj3.value = 10000;
        int i = objectInlineTestC2(obj, obj2, obj3);
        return obj.value + i;
    }

    @Inline(object=true) static int objectInlineTestC2(InlinedObj obj, Object obj2, InlinedObj obj3) {
        obj.value += 70;
        int i = objectInlineTestC3(obj, obj2, obj3);
        obj.value++;
        return obj.value + i + obj3.value;
    }

    static int objectInlineTestC3(InlinedObj obj, Object obj2, InlinedObj obj3) {
        // if (obj2 != obj3)
        obj.value += objectInlineTestC4(obj2, obj3);

        return obj.value * 100;
    }

    @Inline(value=false)
    static int objectInlineTestC4(Object obj2, InlinedObj obj3) {
        return 1;
    }

    static class InlineObjDArray {
        int value = 10;
        int get(int x) { return x + value; }
    }

    static class InlineObjDArray2 {
        final InlineObjDArray array = new InlineObjDArray();
        final FloatArray2D farray = new FloatArray2D(2, 2);
    }

    @Metaclass(type=ImmutableClass.class)
    static class InlineObjDCursor {
        int x;
        InlineObjDCursor(int x) { this.x = x; }
        int value(InlineObjDArray2 a) { return a.array.get(x); }
        float value2(InlineObjDArray2 a) { return a.farray.get(x, 0); }
    }

    public @Test void testObjectInliningD() throws Exception {
        StdDriver2 drv = new StdDriver2();
        int res = (int)drv.invoke(Runner.class, "objectInlineTestD", null,
                            new Object[] { new InlineObjDArray2() });
        if (res == 17)
            System.out.println("objectInlineTestD OK");
        else
            throw new Exception("objectInlineTestD " + res);
    }

    public static int objectInlineTestD(InlineObjDArray2 a) {
        a.farray.set(3, 0, 17);
        InlineObjDCursor c = new InlineObjDCursor(3);
        int r = objectInlineTestD2(c, a);
        return r;
    }

    @Inline(object=true) static int objectInlineTestD2(InlineObjDCursor c, InlineObjDArray2 a) {
        int r = (int)c.value2(a);
        return r;
    }

    public @Test void testIntfType() throws Exception {
        StdDriver2 drv = new StdDriver2();
        try {
            drv.invoke(Runner.class, "interfaceTypeTest", null,
                    new Object[] { 3, null });
        }
        catch (DriverException e) {
            if (e.getCause() instanceof VisitorException)
                if (e.getCause().getMessage().startsWith("how to cast")) {
                    System.out.println("testIntfType() OK");
                    return;
                }
        }

        throw new Exception("testIntfType()");
    }

    @Metaclass(type=ImmutableClass.class)
    static interface IntfTypeI  {
        int foo();
    }

    public static int interfaceTypeTest(int k, IntfTypeI i) {
        k = interfaceTypeTest2(i);
        k += interfaceTypeTest2(() -> 10);
        return k;
    }

    public static int interfaceTypeTest2(IntfTypeI i) {
        if (i != null)
            return i.foo();
        else
            return 3;
    }

    public @Test void testReifier() throws Exception {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get(Runner.class.getName());
        CtMethod cm = cc.getDeclaredMethod("objectInlineTestD");
        Object[] args = { new InlineObjDArray2() };
        // new Reifier().reifyAndPrint(cm, args);
        Reifier.Snapshot ast = new Reifier(cm, args).snap();
        System.out.println(ast.function);
    }

    public @Test void testFinal() throws Exception {
        StdDriver2 drv = new StdDriver2();
        FinalTest ft = new FinalTest();
        FinalTest ft2 = new FinalTest();
        ft.value = 70;
        ft2.value = 9;
        try {
            ft.ft = null;
            int res = (int)drv.invoke(Runner.class, "finalTest", null,
                            new Object[] { ft });
            throw new Exception("finalTest FAILs");
        }
        catch (DriverException e) {
            if (!(e.getCause() instanceof VisitorException))
                throw new Exception("finalTest FAILs");
        }

        try {
            ft.ft = null;
            int res = (int)drv.invoke(Runner.class, "finalTest2", null,
                                 new Object[] { ft });
            throw new Exception("finalTest2 FAILs");
        }
        catch (DriverException e) {
            if (e.status() != 139)
                throw new Exception("finalTest2 FAILs");
        }

        ft.ft = ft2;
        int res = (int)drv.invoke(Runner.class, "finalTest2", null,
                new Object[] { ft });
        if (res == 79)
            System.out.println("finalTest OK");
        else
            throw new Exception("finalTest " + res);
    }

    public static class FinalTest {
        @Final public FinalTest ft;
        @Final public int value;
    }

    public static int finalTest(FinalTest ft) {
        ft.ft.value = 900; return 1;
    }

    public static int finalTest2(FinalTest ft) {
        return ft.value + ft.ft.value;
    }

    public @Test void testFinalInline() throws Exception {
        StdDriver2 drv = new StdDriver2();
        int res = (int)drv.invoke(Runner.class, "finalInline", null,
                            new Object[0]);
        if (res == 321)
            System.out.println("testFinalInline OK");
        else
            throw new Exception("testFinalInline " + res);
    }

    public static class FinalTest2 {
        final public FinalTest2 ft;
        public FinalTest2(FinalTest2 f) { ft = f; }
        public int value;
    }

    public static int finalInline() {
        FinalTest2 ft3 = new FinalTest2(null);
        ft3.value = 300;
        FinalTest2 ft2 = new FinalTest2(ft3);
        ft2.value = 20;
        FinalTest2 ft = new FinalTest2(ft2);
        ft.value = 1;

        return finalInlineA(ft) + finalInlineA(ft.ft) + finalInlineA(ft.ft.ft);
    }

    @Inline public static int finalInlineA(FinalTest2 ft) {
        return ft.value;
    }

    // utility functions

    @Native("fputs(((struct java_string*)v1)->body, stdout);")
    public static void print(String s) {}

    @Native("fprintf(stdout, \"%f -- \", (double)v1);")
    public static void print(double d) {}

    @Native("fprintf(stdout, \"%f -- \", (double)v1);")
    public static void print(float f) {}

    @Native("fprintf(stdout, \"%d -- \", (int)v1);")
    public static void print(long[] j) {}

    @Native("fprintf(stdout, \"%d -- \", v1);")
    public static void print(int i) {}

    @Native("fprintf(stdout, \"0x%x -- \", v1);")
    public static void printHex(int i) {}

    @Native("fprintf(stdout, \"0x%lx -- \", (unsigned long)v1);")
    public static void printHex(Object v) {}

    @Native("fprintf(stdout, \"%ld -- \", v1);")
    public static void print(long i) {}

    @Native("fprintf(stdout, \"\\n \");")
    public static void print() {}

    @Native("fprintf(stdout, \"<mark>\\n \");")
    public static void printMark() {}

    public static int fib(int n) {
        if (n < 2)
            return 1;
        else
            return fib(n - 1) + fib(n - 2);
    }

    public double test(double d, int i, long j) {
        if (d > 3.0) {
            int k = i + 1;
            j += k;
        }
        else {
            float k = 1.0f;
            d += k;
        }

        return d + j;
    }

    public int test2(int p) {
        int k = 3;
        int m = 100;
        int sum = 0;
        for (int i = 0; i < k; i++)
            sum += i++ + --m + p;

        return sum;
    }

    public int test3(int p, int q) {
        do {
            p -= q;
        } while (p > 0);
        return p;
    }

    public int test4(String name) throws Exception {
        int k = name.length();
        try {
            if (k > 10)
                throw new Exception();
        }
        catch (Exception e) {
            k = 3;
            throw e;
        }
        finally {
            k++;
            System.out.println(name);
        }
        return k;
    }

    public int test5(int i) {
        int r;
        switch (i + 5) {
        case 0:
            r = i + 1;
            break;
        case 1:
            r = i + 2;
        case 2:
            r = i + 3;
            break;
        default:
            r = 0;
            break;
        }

        int s = 1;
        switch (i + 5) {
        case -1:
            r = i + 1;
            break;
        case 3:
            r = i + 2;
        case 7:
            r = i + 3;
            break;
        default:
            r = 0;
            break;
        }
        return r;
    }

    static class Test6C {
        int i;
        double[] d;
        Test6C self;
    }

    public static int test6(int i) {
        int[] array = null;
        return test6a(array, new double[0], null);
    }

    public static int test7(int i) {
        boolean[] barray = new boolean[1];
        char[] carray = new char[1];
        short[] sarray = new short[1];
        long[] jarray = new long[1];
        float[] farray = new float[1];

        int[] array = new int[i];
        double[] darray = new double[1];
        int[] zero = new int[0];
        array[0] = 11 + zero.length;
        darray[0] = 123.0;
        return test6a(array, darray, null) + test6b(barray, carray, sarray, jarray, farray);
    }

    public static int test6a(int[] a, double[] d, Test6C t) {
        if (a != null && d != null)
            return a[0] + a.length + d.length;
        else
            return 0;
    }

    public static int test6b(boolean[] b, char[] c, short[] s, long[] j, float[] f) {
        b[0] = true;
        c[0] = 254;
        s[0] = -1;
        j[0] = -1;
        f[0] = -123.3f;
        int len = b.length + c.length + s.length + j.length + f.length;
        if (f[0] < 0 && b[0])
            return c[0] + s[0] + (int)j[0];
        else
            return 0;
    }

    public static class Klass8 {
        static int classVar = 3;
        int i;
        double j;
        Klass8 next;
        public Klass8() { i = 7; j = 3.0; }
        public Klass8(int k, double d) { i = k; j = d; }
        public int getI() { return i; }
    }

    public static int test8(Klass8 k) {
        Klass8.classVar = 77;
        return k.i + k.getI() + Klass8.classVar;
    }
}
