package javassist.offload.lib;

import javassist.offload.Final;
import javassist.offload.Metaclass;
import javassist.offload.javatoc.DriverException;
import javassist.offload.lib.CudaDriver;
import javassist.offload.clang.ImmutableClass;

public class CudaTest {
    public static void main(String[] args) throws DriverException {
        CudaDriver drv = new CudaDriver();
        drv.sourceOnly();
        array = new CUDA.SharedFloatArray(16);
        drv.invoke(new Runnable() {
            int value = 5;
            public void run() {
                //CUDA.initialize();
                Util.print("start").println();
                hostArray[19] = 1000;
                hostArray[1] = 1000;
                float[] f = { 1, 2, 3 };
                float[] dev = CUDA.mallocFloat(3);
                final int v = 5;
                //CUDA.memcpyToDevice(dev, f, f.length);
                CUDA.Dim3 g = new CUDA.Dim3(1, 1, 1);
                CUDA.Dim3 b = new CUDA.Dim3(2, 1, 1);
                //cudaMain(g, b, dev, 10);
                cudaMain2(1, 16, dev, 10, new Func() {
                    public int f() { return v + value; }
                });
                //CUDA.memcpyToHost(f, dev, f.length);
                CUDA.deviceSynchronize();
                float sum = 0;
                for (int i = 0; i < hostArray.length; i++)
                    sum += hostArray[i];

                Util.print("value ").print(sum).println();
                Util.print("hostArray[19] ").print(hostArray[19]).println();
                CUDA.free(dev);
                Unsafe.free(f);
            }
        });

        System.err.println(drv.compileCommand());
    }

    @Metaclass(type=ImmutableClass.class)
    static interface Func {
        int f();
    }

    public @Final static CUDA.SharedFloatArray array;
    public @Final static float[] hostArray = new float[20];

    @Metaclass(type=CUDA.Global.class)
    public static void cudaMain(CUDA.Dim3 grd, CUDA.Dim3 blk, float[] f, int i) {
        f[0] += 10;
        foo(i + 3);
    }

    @Metaclass(type=CUDA.Global.class)
    public static void cudaMain2(int grd, int blk, float[] f, int i, Func func) {
        f[0] += func.f();
        foo(i + 3);
        array.set(CUDA.ThreadIdx.x(), i);
        hostArray[CUDA.ThreadIdx.x()] = i;
    }

    public static void foo(int i) {
        if (CUDA.ThreadIdx.x() == 0)
            Util.print('>').print(CUDA.ThreadIdx.x()).print("/threadIdx.x ").print(i).println();
    }
}
