package npbench3cg;

import javassist.offload.Inline;
import javassist.offload.Metaclass;
import javassist.offload.clang.LambdaClass;
import javassist.offload.lib.DoubleArray;
import javassist.offload.lib.MPI;

public class Vector implements Cloneable {
    @Metaclass(type=LambdaClass.class)
    public static interface Func {
        public double apply(double d);
    }

    final Dsl dsl;
    final DoubleArray elements;

    private final double[] buffer;
    private final MPI.Request request;

    Vector(int size, Dsl d) {
        dsl = d;
        elements = new DoubleArray(size, !Dsl.java);
        buffer = new double[1];
        if (d.useMPI)
            request = new MPI.Request();
        else
            request = null;
    }

    @Inline public void set(Vector v) {
        int size = v.dsl.lastrow - v.dsl.firstrow + 2;
        for (int j = 1; j <= size; j++)
            elements.set(j, v.elements.get(j));
    }

    @Inline public void set(double scale, Vector v) {
        int size = v.dsl.lastrow - v.dsl.firstrow + 2;
        for (int j = 1; j <= size; j++)
            elements.set(j, scale * v.elements.get(j));
    }

    public void set(Vector v, Func f) {
        int size = v.dsl.lastrow - v.dsl.firstrow + 1;
        for (int j = 1; j <= size; j++)
            elements.set(j, f.apply(v.elements.get(j)));
    }

    @Inline public void set(double d) {
        int size = dsl.lastrow - dsl.firstrow + 2;
        for (int j = 1; j <= size; j++)
            elements.set(j, d);
    }

    @Inline public static double inner(Vector a, Vector b) {
        double sum = 0.0;
        for (int j = 1; j <= a.dsl.lastcol - a.dsl.firstcol + 1; j++)
            sum += a.elements.get(j) * b.elements.get(j);

        if (a.dsl.useMPI)
            for (int i = 1; i <= a.dsl.log2npcols; i++) {
                MPI.iRecv(a.buffer, 1, a.dsl.reduceExchProc[i], i, a.request);
                MPI.send(sum, a.dsl.reduceExchProc[i], i);
                MPI.wait(a.request);
                sum += a.buffer[0];
            }

        return sum;
    }

    @Inline public double norm() {
        double sum = 0.0;
        for (int j = 1; j <= dsl.lastcol - dsl.firstcol + 1; j++) {
            double e = elements.get(j);
            sum += e * e;
        }

        if (dsl.useMPI)
            for (int i = 1; i <= dsl.log2npcols; i++) {
                MPI.iRecv(buffer, 1, dsl.reduceExchProc[i], i, request);
                MPI.send(sum, dsl.reduceExchProc[i], i);
                MPI.wait(request);
                sum += buffer[0];
            }

        return sum;
    }

    /**
     * Sets to {@code v} + {@code beta} * {@code w}.
     */
    @Inline public void setToAdd(Vector v, double beta, Vector w) {
        for (int j = 1; j <= dsl.lastcol - dsl.firstcol + 1; j++) {
            elements.set(j, v.elements.get(j) + beta * w.elements.get(j)); 
        }
    }

    /**
     * Sets to {@code v} - {@code w}.
     */
    @Inline public void setToSub(Vector v, Vector w) {
        for (int j = 1; j <= dsl.lastcol - dsl.firstcol + 1; j++)
            elements.set(j, v.elements.get(j) - w.elements.get(j)); 
    }

    public void setToMult(Matrix m, Vector v) {
        m.mult(v, this);
    }
}
