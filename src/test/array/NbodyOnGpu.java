package array;

import javassist.offload.lib.Util;

public class NbodyOnGpu {
    public static void main(String[] args) throws Exception {
        main();
    }

    public static void printTime(long t1, long t2) {
        long msec = (t2 - t1) / 1000;
        Util.print(msec).print("msec, ").print(gflops(msec)).print(" GFlops").println();
    }

    public static float gflops(long msec) {
        int flopsPerInteraction = 20;
        float interactionsPerSecond = (float)N * (float)N;
        interactionsPerSecond *= 1e-9 * R * 2 * 1000 / msec;
        return interactionsPerSecond * (float)flopsPerInteraction;
    }

    static final int R = 5;     // repeat 2R times
    static final int N = 30208;

    public static void main() throws Exception {
        final VecCudaDSL dsl = new VecCudaDSL(N);

        final Vec4Array pos1 = dsl.array();
        final Vec4Array pos2 = dsl.array();
        final Vec4Array vel = dsl.array();
        final Vec4CudaArrayOnShared shared = dsl.sharedMemory();

        final float softening = 0.01f;
        final float dumping = 1.0f;
        final float deltaTime = 0.016f;

        final Func f1 = (Vec4Array pos, int i, Vec3 pi, float wi) -> {
            Vec3 a = shared.fold(pos, acc -> {
                Vec3 sum = shared.sum((Vec4Array p, int j, Vec3 pj, float wj) -> {
                    Vec3 r = pi.sub(pj);
                    float ra = VecDSL.reciprocalSqrt(r.mult(r) + softening);
                    return r.scale(wj * (ra * ra * ra));
                });
                return acc.add(sum);
            });
            Vec3 v2 = vel.get(i).add(a.scale(deltaTime)).scale(dumping);
            vel.set(i, v2);
            return pi.add(v2.scale(deltaTime));
        };

        final Func f2 = (Vec4Array pos, int i, Vec3 pi, float wi) -> {
            Vec3 a = pos.sum((Vec4Array p, int j, Vec3 pj, float wj) -> { 
                Vec3 r = pi.sub(pj);
                float ra = VecDSL.reciprocalSqrt(r.mult(r) + softening);
                return r.scale(wj * (ra * ra * ra));
            });
            Vec3 v2 = vel.get(i).add(a.scale(deltaTime)).scale(dumping);
            vel.set(i, v2);
            return pi.add(v2.scale(deltaTime));
        };

        Func f = f1;
        dsl.run(() -> {
            pos1.tabulate(i -> new Vec4(i, i, i, 2));
            vel.tabulate(i -> new Vec4(i, i, i, 2));

            long t1 = Util.time();
            
            dsl.repeat(R, () -> {  // for (int i = 0; i < R; i++) {
                pos2.map(f, pos1);
                pos1.map(f,  pos2);
            });

            long t2 = Util.time();
            printTime(t1, t2);
            // no copy from GPU to CPU for sum()?
            Vec3 g = pos1.sum((Vec3 v, float w) -> new Vec3(v.x / w, v.y / w, v.z / w));
            Util.print(g.x / N).print(", ").print(g.y / N).print(", ").print(g.z / N).println();
        });
    }
}
