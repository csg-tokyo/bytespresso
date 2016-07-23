package array;

import javassist.offload.Inline;
import javassist.offload.Metaclass;
import javassist.offload.clang.ImmutableClass;
import javassist.offload.clang.LambdaClass;
import javassist.offload.lib.MPI;
import javassist.offload.lib.MPIDriver;

public class VecMpiDSL extends VecDSL {
    public VecMpiDSL(int arraySize) {
        super(arraySize);
    }

    public void run(Runnable r) throws Exception {
        new MPIDriver().invoke(r);
    }

    @Metaclass(type=ImmutableClass.class)
    public static class Cursor {
        final int index;
        public Cursor(int i) { index = i; }
        public Vec3 get(Vec4Array a) { return a.get(index); }
        public void set(Vec4Array a, Vec3 value) { a.set(index, value); }
    }

    @Metaclass(type=LambdaClass.class)
    static interface MapFunc {
        void apply(Cursor c, Vec4Array in1, Vec4Array in2, Vec4Array out1, Vec4Array out2);
    }

    @Inline public void map(MapFunc f, Vec4Array in1, Vec4Array in2, Vec4Array out1, Vec4Array out2) {
        int len = size / MPI.commSize();
        int rank = MPI.commRank();
        int base = rank * len;
        for (int i = base; i < base + len; i++) {
            Cursor c = new Cursor(i);
            f.apply(c, in1, in2, out1, out2);
        }

        MPI.allGatherC(out1.data.toCArray(), len * 4);
        MPI.allGatherC(out2.data.toCArray(), len * 4);
    }

    public void singleNode(Code c) {
        if (MPI.commRank() == 0)
            c.run();
    }
}
