package array;

import javassist.offload.Options;
import javassist.offload.lib.MPI;
import javassist.offload.lib.Util;
import array.VecMpiDSL.Cursor;
import array.VecMpiDSL.MapFunc;

public class NbodyMPI {
    public static void main(String[] args) throws Exception {
        main();
    }

    public static void printTime(long t) {
        long msec = t / 1000;
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
        Options.portableInitialization = true;
        final VecMpiDSL dsl = new VecMpiDSL(N);
        dsl.withOpenMP();

        final Vec4Array pos1 = dsl.array();
        final Vec4Array pos2 = dsl.array();
        final Vec4Array vel = dsl.array();

        final float softening = 0.01f;
        final float dumping = 1.0f;
        final float deltaTime = 0.016f;
        final MapFunc f = (Cursor c, Vec4Array posIn, Vec4Array velIn, Vec4Array posOut, Vec4Array velOut) -> {
            Vec3 pi = c.get(posIn);
            Vec3 a = posIn.sum((Vec4Array p, int j, Vec3 pj, float wj) -> {
                Vec3 r = pi.sub(pj);
                float ra = VecDSL.reciprocalSqrt(r.mult(r) + softening);
                return r.scale(wj * (ra * ra * ra));
            });
            Vec3 vi = c.get(velIn);
            Vec3 v2 = vi.add(a.scale(deltaTime)).scale(dumping);
            c.set(velOut, v2);
            c.set(posOut,v2.scale(deltaTime));
        };

        dsl.run(() -> {
            pos1.tabulate(i -> new Vec4(i, i, i, 2));
            vel.tabulate(i -> new Vec4(i, i, i, 2));

            long t1 = Util.time();
            dsl.repeat(R, () -> {       // for (int i = 0; i < R; i++) {
                dsl.map(f, pos1, vel, pos2, vel);
                dsl.map(f, pos2, vel, pos1, vel);
            });

            long t2 = Util.time();
            long time = MPI.allReduce(t2 - t1, MPI.max());

            dsl.singleNode(() -> {
                printTime(time);
                Vec3 g = pos1.sum((Vec3 v, float w) -> new Vec3(v.x / w, v.y / w, v.z / w));
                Util.print(g.x / N).print(", ").print(g.y / N).print(", ").print(g.z / N).println();
            });
        });
    }
}
