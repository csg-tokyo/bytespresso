package array;

import javassist.CtClass;
import javassist.offload.Final;
import javassist.offload.Foreign;
import javassist.offload.Metaclass;
import javassist.offload.clang.Advice;
import javassist.offload.clang.LambdaClass;
import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;

public class VecDSL {
    public static class CodeAdvice implements Advice {
        public String rename(CtClass klass, String methodName) {
            String klassName = klass.getName();
            if (klassName.equals(Vec4Array.class.getName()))
                if (methodName.equals("map"))
                    return "map2";

            return null;
        }
    }

    @Metaclass(type=LambdaClass.class, companion=CodeAdvice.class)
    public static interface Code {
        void run();
    }

    @Final public static boolean rsqrtfAvailable = false;

    @Foreign
    public static float rsqrtf(float f) {
        return 1.0f / (float)Math.sqrt(f);
    }

    public static float reciprocalSqrt(float f) {
        if (rsqrtfAvailable)
            return rsqrtf(f);
        else
            return 1.0f / Util.sqrtf(f);
    }

    protected final int size;
    @Final boolean withOpenMP;
    boolean runOnJVM; 

    public VecDSL(int size) {
        this.size = size;
        this.withOpenMP = false;
        this.runOnJVM = false;
    }

    public VecDSL withOpenMP() {
        this.withOpenMP = true;
        return this;
    }

    public void onJVM() { runOnJVM = true; }

    public void run(Runnable r) throws Exception {
        if (runOnJVM)
            r.run();
        else
            new StdDriver().invoke(r);
    }

    public Vec4Array array() {
        return new Vec4Array(this, size);
    }

    public void copyToDevice() {}
    public void copyFromDevice() {}

    public void repeat(int n, Code code) {
        for (int i = 0; i < n; i++)
            code.run();
    }
}
