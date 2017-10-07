// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.Metaclass;
import javassist.offload.Native;
import javassist.offload.clang.NativeArrayClass;
import javassist.offload.clang.NativeClass;

/**
 * Message Passing Interface.
 *
 * <p><code>MPI_Init()</code> and <code>MPI_Finalize()</code> are
 * called by {@link MPIDriver}.   User programs do not have to
 * explicitly call them.  If necessary, call {@link #abort()}
 * (<code>MPI_Abort</code>).
 * </p>
 *
 * @see MPIDriver
 */
public class MPI {
    /**
     * <code>MPI_Request</code>.
     */
    @Metaclass(type=NativeClass.class, arg = "sizeof(MPI_Request)")
    public static final class Request implements Cloneable {
        private int flag;
        MPIRuntime.Receiver receiver;

        /**
         * Constructs a request.
         */
        @Native("")
        public Request() { flag = 0; receiver = null; }

        /**
         * Returns the value of flag.
         */
        @Native("return v1->" + NativeClass.FLAG_FIELD + ";")
        public int flag() { return flag; }

        /**
         * Sets flag.
         *
         * @param f     a new value.
         */
        @Native("v1->" + NativeClass.FLAG_FIELD + " = v2;")
        public void setFlag(int f) { flag = f; }

        /**
         * Sets flag to 1 if the old value is 0.
         * Otherwise, an error is reported.
         */
        public void use() {
            if (flag() == 0)
                setFlag(1);
            else
                error(0);
        }

        /**
         * Sets flag to 0 if the old value is 1.
         * Otherwise, an error is reported.
         */
        public void release() {
            if (flag() == 1)
                setFlag(0);
            else
                error(1);
        }

        private void error(int i) {
            Util.print("Error: bad use of MPI_Request. flag ").print(flag())
                .print("!=").print(i)
                .print(". Rank=").print(commRank()).println();
            abort();
        }

        void isSend() { receiver = null; }
        void isReceive(MPIRuntime.Receiver r) { receiver = r; }

        void receiveIf() {
            if (receiver != null) {
                receiver.read();
                receiver = null;
            }
        }
    }

    /**
     * <code>MPI_Request</code> array.
     */
    @Metaclass(type=NativeArrayClass.class, arg = "sizeof(MPI_Request)")
    public static class RequestArray implements Cloneable {
        int length;

        /**
         * Constructs an array of {@code MPI_Request}.
         */
        @Native("")
        public RequestArray(int len) { length = len; }
    
        /**
         * Returns a pointer to the i-th element of the array.
         */
        @Native("return (int*)((char*)v1->body + v2 * sizeof(MPI_Request));")
        public @NativePtr int[] get(int i) { return null; }
    }

    /**
     * Constant <code>MPI_PROC_NULL</code>
     */
    @Native("return MPI_PROC_NULL;")
    public static int MPI_PROC_NULL() {
        return -1;
    }

    /**
     * Constant <code>MPI_ANY_SOURCE</code>
     */
    @Native("return MPI_ANY_SOURCE;")
    public static int MPI_ANY_SOURCE() {
        return -2;
    }

    /**
     * Constant <code>MPI_ROOT</code>
     */
    @Native("return MPI_ROOT;")
    public static int MPI_ROOT() {
        return -3;
    }

    /**
     * Constant <code>MPI_ANY_TAG</code>
     */
    @Native("return MPI_ANY_TAG;")
    public static int MPI_ANY_TAG() {
        return -1;
    }

    /**
     * <code>MPI_Comm_rank()</code> function.
     */
    @Native("int pid; MPI_Comm_rank(MPI_COMM_WORLD, &pid); return pid;")
    public static int commRank() {
        return MPIRuntime.thisNode().rank;
    }

    /**
     * <code>MPI_Comm_size</code> function.
     */
    @Native("int nprocs; MPI_Comm_size(MPI_COMM_WORLD, &nprocs); return nprocs;")
    public static int commSize() {
        return MPIRuntime.thisNode().all.length;
    }

    /**
     * <code>MPI_Wtime</code> function.
     */
    @Native("return MPI_Wtime();")
    public static double wtime() {
        return (double)System.nanoTime();
    }

    /**
     * <code>MPI_Isend()</code> function.
     */
    public static void iSend(int[] buf, int length, int dest, int tag, Request req) {
        req.use();
        send2(Unsafe.toCArray(buf), 0, length, dest, tag, Unsafe.toNativeBody(req));
    }

    /**
     * <code>MPI_Isend()</code> function.
     */
    public static void iSend(int[] buf, int offset, int length, int dest, int tag, Request req) {
        req.use();
        send2(Unsafe.toCArray(buf), offset, length, dest, tag, Unsafe.toNativeBody(req));
    }

    @Native("MPI_Isend(v1 + v2, v3, MPI_INT, v4, v5, MPI_COMM_WORLD, (MPI_Request*)v6);")
    private static void send2(@NativePtr int[] buf, int offset, int length, int dest, int tag, @NativePtr Request req) {
        int[] data = new int[length];
        for (int i = 0; i < length; i++)
            data[i] = buf[offset + i];

        MPIRuntime.send(data, dest, tag);
        req.isSend();
    }

    /**
     * <code>MPI_Irecv()</code> function.
     */
    public static void iRecv(int[] buf, int length, int src, int tag, Request req) {
        req.use();
        receive2(Unsafe.toCArray(buf), 0, length, src, tag, Unsafe.toNativeBody(req));
    }

    /**
     * <code>MPI_Irecv()</code> function.
     */
    public static void iRecv(int[] buf, int offset, int length, int src, int tag, Request req) {
        req.use();
        receive2(Unsafe.toCArray(buf), offset, length, src, tag, Unsafe.toNativeBody(req));
    }

    @Native("MPI_Irecv(v1 + v2, v3, MPI_INT, v4, v5, MPI_COMM_WORLD, (MPI_Request*)v6);")
    private static void receive2(@NativePtr int[] buf, int offset, int length, int src, int tag, @NativePtr Request req) {
        req.isReceive(MPIRuntime.receive(buf, offset, length, src, tag));
    }

    /**
     * <code>MPI_Isend()</code> function.
     */
    public static void iSend(float[] buf, int length, int dest, int tag, Request req) {
        req.use();
        send2(Unsafe.toCArray(buf), 0, length, dest, tag, Unsafe.toNativeBody(req));
    }

    /**
     * <code>MPI_Isend()</code> function.
     */
    public static void iSend(float[] buf, int offset, int length, int dest, int tag, Request req) {
        req.use();
        send2(Unsafe.toCArray(buf), offset, length, dest, tag, Unsafe.toNativeBody(req));
    }

    /**
     * <code>MPI_Isend()</code> function.
     *
     * @param buf           a C pointer to the data sent.  
     */
    public static void iSendC(@NativePtr float[] buf, int offset, int length, int dest, int tag, Request req) {
        req.use();
        send2(buf, offset, length, dest, tag, Unsafe.toNativeBody(req));
    }

    @Native("MPI_Isend(v1 + v2, v3, MPI_FLOAT, v4, v5, MPI_COMM_WORLD, (MPI_Request*)v6);")
    private static void send2(@NativePtr float[] buf, int offset, int length, int dest, int tag, @NativePtr Request req) {
        float[] data = new float[length];
        for (int i = 0; i < length; i++)
            data[i] = buf[offset + i];

        MPIRuntime.send(data, dest, tag);
        req.isSend();
    }

    /**
     * <code>MPI_Isend()</code> function.
     * <code>reqs.get(index)</code> is used as an <code>MPI_Request</code> object.
     *
     * @param buf           a C pointer to the data sent.  
     */
    public static void iSendC(@NativePtr float[] buf, int offset, int length, Datatype type, int dest, int tag, Comm comm, RequestArray reqs, int index) {
        send2(buf, offset, length, Unsafe.toNativeBody(type), dest, tag, Unsafe.toNativeBody(comm), reqs.get(index));
    }

    @Native("MPI_Isend(v1 + v2, v3, *((MPI_Datatype*)v4), v5, v6, *((MPI_Comm*)v7), (MPI_Request*)v8);")
    private static void send2(@NativePtr float[] buf, int offset, int length, @NativePtr Datatype type,
                              int dest, int tag, @NativePtr Comm comm, @NativePtr int[] req) {
        error();
    }

    /**
     * <code>MPI_Irecv()</code> function.
     */
    public static void iRecv(float[] buf, int length, int src, int tag, Request req) {
        req.use();
        receive2(Unsafe.toCArray(buf), 0, length, src, tag, Unsafe.toNativeBody(req));
    }

    /**
     * <code>MPI_Irecv()</code> function.
     */
    public static void iRecv(float[] buf, int offset, int length, int src, int tag, Request req) {
        req.use();
        receive2(Unsafe.toCArray(buf), offset, length, src, tag, Unsafe.toNativeBody(req));
    }

    /**
     * <code>MPI_Irecv()</code> function.
     *
     * @param buf       a C pointer to the float array where the received data are stored.
     */
    public static void iRecvC(@NativePtr float[] buf, int offset, int length, int src, int tag, Request req) {
        req.use();
        receive2(buf, offset, length, src, tag, Unsafe.toNativeBody(req));
    }

    @Native("MPI_Irecv(v1 + v2, v3, MPI_FLOAT, v4, v5, MPI_COMM_WORLD, (MPI_Request*)v6);")
    private static void receive2(@NativePtr float[] buf, int offset, int length, int src, int tag, @NativePtr Request req) {
        req.isReceive(MPIRuntime.receive(buf, offset, length, src, tag));
    }

    /**
     * <code>MPI_Irecv()</code> function.
     * <code>reqs.get(index)</code> is used as an <code>MPI_Request</code> object.
     *
     * @param buf       a C pointer to the float array where the received data are stored.
     */
    public static void iRecvC(@NativePtr float[] buf, int offset, int length, Datatype type, int src, int tag, Comm comm, RequestArray reqs, int index) {
        receive2(buf, offset, length, Unsafe.toNativeBody(type), src, tag, Unsafe.toNativeBody(comm), reqs.get(index));
    }

    @Native("MPI_Irecv(v1 + v2, v3, *((MPI_Datatype*)v4), v5, v6, *((MPI_Comm*)v7), (MPI_Request*)v8);")
    private static void receive2(@NativePtr float[] buf, int offset, int length, @NativePtr Datatype type,
                                 int src, int tag, @NativePtr Comm comm, @NativePtr int[] req) {
        error();
    }

    /**
     * <code>MPI_Isend()</code> function.
     */
    public static void iSend(double[] buf, int length, int dest, int tag, Request req) {
        req.use();
        send2(Unsafe.toCArray(buf), 0, length, dest, tag, Unsafe.toNativeBody(req));
    }

    /**
     * <code>MPI_Isend()</code> function.
     */
    public static void iSend(double[] buf, int offset, int length, int dest, int tag, Request req) {
        req.use();
        send2(Unsafe.toCArray(buf), offset, length, dest, tag, Unsafe.toNativeBody(req));
    }

    @Native("MPI_Isend(v1 + v2, v3, MPI_DOUBLE, v4, v5, MPI_COMM_WORLD, (MPI_Request*)v6);")
    private static void send2(@NativePtr double[] buf, int offset, int length, int dest, int tag,
                              @NativePtr Request req) {
        double[] data = new double[length];
        for (int i = 0; i < length; i++)
            data[i] = buf[offset + i];

        MPIRuntime.send(data, dest, tag);
        req.isSend();
    }

    /**
     * <code>MPI_Send()</code> function.
     */
    public static void send(double[] buf, int offset, int length, int dest, int tag) {
        sendC(Unsafe.toCArray(buf), offset, length, dest, tag);
    }

    /**
     * <code>MPI_Send()</code> function.
     *
     * @param buf       a C pointer to send buffer.
     */
    @Native("MPI_Send(v1 + v2, v3, MPI_DOUBLE, v4, v5, MPI_COMM_WORLD);")
    public static void sendC(@NativePtr double[] buf, int offset, int length, int dest, int tag) {
        double[] data = new double[length];
        for (int i = 0; i < length; i++)
            data[i] = buf[offset + i];

        MPIRuntime.send(data, dest, tag);
    }

    /**
     * <code>MPI_Send()</code> function.
     *
     * @param value     the value sent by <code>MPI_Send()</code>.
     */
    @Native("MPI_Send(&v1, 1, MPI_DOUBLE, v2, v3, MPI_COMM_WORLD);")
    public static void send(double value, int dest, int tag) {
        double[] data = { value };
        MPIRuntime.send(data, dest, tag);
    }

    /**
     * <code>MPI_Irecv()</code> function.
     */
    public static void iRecv(double[] buf, int length, int src, int tag, Request req) {
        req.use();
        receive2(Unsafe.toCArray(buf), 0, length, src, tag, Unsafe.toNativeBody(req));
    }

    /**
     * <code>MPI_Irecv()</code> function.
     */
    public static void iRecv(double[] buf, int offset, int length, int src, int tag, Request req) {
        req.use();
        receive2(Unsafe.toCArray(buf), offset, length, src, tag, Unsafe.toNativeBody(req));
    }

    /**
     * <code>MPI_Irecv()</code> function.
     *
     * @param buf   a C pointer to receive buffer.
     */
    public static void iRecvC(@NativePtr double[] buf, int offset, int length,
                              int src, int tag, Request req) {
        req.use();
        receive2(buf, offset, length, src, tag, Unsafe.toNativeBody(req));
    }

    @Native("MPI_Irecv(v1 + v2, v3, MPI_DOUBLE, v4, v5, MPI_COMM_WORLD, (MPI_Request*)v6);")
    private static void receive2(@NativePtr double[] buf, int offset, int length, int src,
                                 int tag, @NativePtr Request req) {
        req.isReceive(MPIRuntime.receive(buf, offset, length, src, tag));
    }

    /**
     * <code>MPI_allGather</code> function with <code>MPI_IN_PLACE</code>.
     *
     * @param recvbuf       a receive buffer.
     * @param recvcount     the number of elements received from each node.
     */
    public static void allGather(float[] recvbuf, int recvcount) {
        allGatherC(Unsafe.toCArray(recvbuf), recvcount);
    }

    /**
     * <code>MPI_allGather</code> function with <code>MPI_IN_PLACE</code>.
     *
     * @param recvbuf       a C pointer to a receive buffer.
     * @param recvcount     the number of elements received from each node.
     */
    @Native("MPI_Allgather(MPI_IN_PLACE, 0, MPI_FLOAT, v1, v2, MPI_FLOAT, MPI_COMM_WORLD);")
    public static void allGatherC(@NativePtr float[] recvbuf, int recvcount) {
        error();
    }

    /**
     * <code>MPI_bcast</code> function for an array.
     *
     * @param buffer    a send/receive buffer.
     * @param count     the number of elements to broadcast.
     * @param root      rank of broadcast root.
     */
    public static void bcast(int[] buffer, int count, int root) {
        bcastIntC(Unsafe.toCArray(buffer), count, root);
    }

    public static void bcast(float[] buffer, int count, int root) {
        bcastFloatC(Unsafe.toCArray(buffer), count, root);
    }

    public static void bcast(double[] buffer, int count, int root) {
        bcastDoubleC(Unsafe.toCArray(buffer), count, root);
    }

    /**
     * <code>MPI_bcast</code> function for an array.
     *
     * @param buffer    a C pointer to a send/receive buffer.
     * @param count     the number of elements to broadcast.
     * @param root      rank of broadcast root.
     */
    @Native("MPI_Bcast(v1, v2, MPI_INT, v3, MPI_COMM_WORLD);")
    public static void bcastIntC(@NativePtr int[] buffer, int count, int root) {
        error();
    }

    @Native("MPI_Bcast(v1, v2, MPI_FLOAT, v3, MPI_COMM_WORLD);")
    public static void bcastFloatC(@NativePtr float[] buffer, int count, int root) {
        error();
    }

    @Native("MPI_Bcast(v1, v2, MPI_DOUBLE, v3, MPI_COMM_WORLD);")
    public static void bcastDoubleC(@NativePtr double[] buffer, int count, int root) {
        error();
    }

    /**
     * <code>MPI_Wait</code> function.
     */
    public static void wait(Request req) {
        req.release();
        wait0(Unsafe.toNativeBody(req));
    }

    @Native("MPI_Status status; MPI_Wait((MPI_Request*)v1, &status);")
    private static void wait0(Request req) {
        req.receiveIf();
    }

    /**
     * <code>MPI_Waitall</code> function.
     */
    public static void waitall(int length, RequestArray reqs) {
        waitall0(length, Unsafe.toNativeBody(reqs));
    }

    @Native("MPI_Status status[v1]; MPI_Waitall(v1, (MPI_Request*)v2, status);")
    private static void waitall0(int length, RequestArray reqs) {
        error();
    }


    /**
     * <code>MPI_Barrier</code> function.
     */
    @Native("MPI_Barrier(MPI_COMM_WORLD);")
    public static void barrier() {
        MPIRuntime.barrier();
    }

    /**
     * <code>MPI_Datatype</code>.
     */
    @Metaclass(type=NativeClass.class, arg = "sizeof(MPI_Datatype)")
    public static final class Datatype {
        /**
         * Returns the value of flag.
         */
        @Native("return v1->" + NativeClass.FLAG_FIELD + ";")
        public int flag() { return 0; }

        /**
         * Sets flag.
         *
         * @param f     a new value.
         */
        @Native("v1->" + NativeClass.FLAG_FIELD + " = v2;")
        public void setFlag(int f) {}

        /**
         * Sets flag to 1 if the old value is 0.
         * Otherwise, an error is reported.
         */
        public void use() {
            if (flag() == 0)
                setFlag(1);
            else
                usageError();
        }

        /**
         * Sets flag to 0 if the old value is 1.
         * Otherwise, an error is reported.
         */
        public void release() {
            if (flag() == 1)
                setFlag(0);
            else
                usageError();
        }

        private static void usageError() {
            Util.print("Error: bad use of MPI_Datatype.").println();
            abort();
        }

        /**
         * <code>MPI_Type_vector()</code> function.
         */
        public static void vector(int count, int blocklength, int stride, Datatype newType) {
            newType.use();
            vector2(count, blocklength, stride, Unsafe.toNativeBody(newType));
        }

        @Native("MPI_Type_vector(v1, v2, v3, MPI_FLOAT, (MPI_Datatype*)v4);")
        private static void vector2(int count, int blocklength, int stride, Datatype newType) {
            error();
        }

        /**
         * <code>MPI_Type_commit()</code> function.
         */
        public static void commit(Datatype type) {
            type.release();
            commit2(Unsafe.toNativeBody(type));
        }

        @Native("MPI_Type_commit((MPI_Datatype*)v1);")
        private static void commit2(Datatype type) {
            error();
        }
    }
    
    /**
     * {@code MPI_Op} type.
     */
    @Metaclass(type=ForeignClass.class)
    public static class Op {}

    static final Op sumOp = new Op();
    static final Op maxOp = new Op();
    static final Op minOp = new Op();

    /**
     * Returns {@code MPI_SUM}.
     */
    @Native("return (void*)MPI_SUM;")
    public static Op sum() { return sumOp; }

    /**
     * Returns {@code MPI_MAX}.
     */
    @Native("return (void*)MPI_MAX;")
    public static Op max() { return maxOp; }

    /**
     * Returns {@code MPI_MIN}.
     */
    @Native("return (void*)MPI_MIN;")
    public static Op min() { return minOp; }

    /**
     * {@code MPI_Allreduce()} function.
     *
     * @param local     the data sent.
     * @param result    the data received.
     * @param op        the reduction operation.
     */
    @Native("int src[1]; int dest[1]; src[0] = v1;\n"
            + "MPI_Allreduce(src, dest, 1, MPI_INT, (MPI_Op)v2, MPI_COMM_WORLD);\n"
            + "return dest[0];")
    public static int allReduce(int local, MPI.Op op) {
        return MPIRuntime.reduce(local, op);
    }

    /**
     * {@code MPI_Allreduce()} function.
     *
     * @param local     the data sent.
     * @param result    the data received.
     * @param op        the reduction operation.
     */
    @Native("long src[1]; long dest[1]; src[0] = v1;\n"
            + "MPI_Allreduce(src, dest, 1, MPI_LONG, (MPI_Op)v2, MPI_COMM_WORLD);\n"
            + "return dest[0];")
    public static long allReduce(long local, MPI.Op op) {
        return MPIRuntime.reduce(local, op);
    }

    /**
     * {@code MPI_Allreduce()} function.
     *
     * @param local     the data sent.
     * @param result    the data received.
     * @param op        the reduction operation.
     */
    @Native("float src[1]; float dest[1]; src[0] = v1;\n"
            + "MPI_Allreduce(src, dest, 1, MPI_FLOAT, (MPI_Op)v2, MPI_COMM_WORLD);\n"
            + "return dest[0];")
    public static float allReduce(float local, MPI.Op op) {
        return MPIRuntime.reduce(local, op);
    }

    /**
     * {@code MPI_Allreduce()} function.
     *
     * @param local     the data sent.
     * @param result    the data received.
     * @param op        the reduction operation.
     */
    public static void allReduce(float[] local, float[] result, MPI.Op op) {
        allReduce2(Unsafe.toCArray(local), Unsafe.toCArray(result), local.length, op);
    }

    @Native("MPI_Allreduce(v1, v2, v3, MPI_FLOAT, (MPI_Op)v4, MPI_COMM_WORLD);")
    private static void allReduce2(float[] local, float[] result, int length, MPI.Op op) {
        error();
    }

    /**
     * {@code MPI_Allreduce()} function.
     *
     * @param local     the data sent.
     * @param result    the data received.
     * @param op        the reduction operation.
     */
    @Native("double src[1]; double dest[1]; src[0] = v1;\n"
            + "MPI_Allreduce(src, dest, 1, MPI_DOUBLE, (MPI_Op)v2, MPI_COMM_WORLD);\n"
            + "return dest[0];")
    public static double allReduce(double local, MPI.Op op) {
        return MPIRuntime.reduce(local, op);
    }

    @Native("double src[1]; double dest[1]; src[0] = v1;\n"
            + "MPI_Allreduce(src, dest, 1, MPI_DOUBLE, MPI_SUM, MPI_COMM_WORLD);\n"
            + "return dest[0];")
    public static double allReduce_sum(double local) {
        return MPIRuntime.reduce(local, MPI.sum());
    }

    @Native("double src[1]; double dest[1]; src[0] = v1;\n"
            + "MPI_Allreduce(src, dest, 1, MPI_DOUBLE, MPI_MAX, MPI_COMM_WORLD);\n"
            + "return dest[0];")
    public static double allReduce_max(double local) {
        return MPIRuntime.reduce(local, MPI.max());
    }

    /**
     * {@code MPI_Allreduce()} function.
     *
     * @param local     the data sent.
     * @param result    the data received.
     * @param op        the reduction operation.
     */
    public static void allReduce(double[] local, double[] result, MPI.Op op) {
        allReduce2(Unsafe.toCArray(local), Unsafe.toCArray(result), local.length, op);
    }

    @Native("MPI_Allreduce(v1, v2, v3, MPI_DOUBLE, (MPI_Op)v4, MPI_COMM_WORLD);")
    private static void allReduce2(double[] local, double[] result, int length, MPI.Op op) {
        error();
    }

    public static void allReduce_sum(double[] local, double[] result) {
        allReduce2_sum(Unsafe.toCArray(local), Unsafe.toCArray(result), local.length);
    }

    @Native("MPI_Allreduce(v1, v2, v3, MPI_DOUBLE, MPI_SUM, MPI_COMM_WORLD);")
    private static void allReduce2_sum(double[] local, double[] result, int length) {
        error();
    }

    /**
     * <code>MPI_Abort()</code> function.
     */
    @Native("MPI_Abort(MPI_COMM_WORLD, 1);")
    public static void abort() {
        throw new RuntimeException("MPI_Abort");
    }

    private static void error() {
        throw new RuntimeException("not implemented");
    }

    /**
     * <code>MPI_Comm</code>.
     */
    @Metaclass(type=NativeClass.class, arg = "sizeof(MPI_Comm)")
    public static final class Comm {
        /**
         * Returns the value of flag.
         */
        @Native("return v1->" + NativeClass.FLAG_FIELD + ";")
        public int flag() { return 0; }

        /**
         * Sets flag.
         *
         * @param f     a new value.
         */
        @Native("v1->" + NativeClass.FLAG_FIELD + " = v2;")
        public void setFlag(int f) {}

        /**
         * Sets flag to 1 if the old value is 0.
         * Otherwise, an error is reported.
         */
        public void use() {
            if (flag() == 0)
                setFlag(1);
            else
                usageError();
        }

        /**
         * Sets flag to 0 if the old value is 1.
         * Otherwise, an error is reported.
         */
        public void release() {
            if (flag() == 1)
                setFlag(0);
            else
                usageError();
        }

        private static void usageError() {
            Util.print("Error: bad use of MPI_Comm.").println();
            abort();
        }
    }

    /**
     * <code>MPI_Cart_create()</code> function.
     */
    public static void cartCreate(int[] dims, int[] periods, int reorder, Comm comm) {
        comm.use();
        cartCreate2(dims.length, Unsafe.toCArray(dims), Unsafe.toCArray(periods), reorder, Unsafe.toNativeBody(comm));
    }

    @Native("MPI_Comm oldComm = MPI_COMM_WORLD; MPI_Cart_create(oldComm, v1, v2, v3, v4, (MPI_Comm*)v5);")
    private static void cartCreate2(int ndims, int[] dims, int[] period, int reorder, Comm newComm) {
        error();
    }

    /**
     * <code>MPI_Cart_get()</code> function.
     */
    public static void cartGet(Comm comm, int[] dims, int[] periods, int[] coords) {
        comm.release();
        cartGet2(Unsafe.toNativeBody(comm), dims.length, Unsafe.toCArray(dims), Unsafe.toCArray(periods), Unsafe.toCArray(coords));
    }

    @Native("MPI_Cart_get(*((MPI_Comm*)v1), v2, v3, v4, v5);")
    private static void cartGet2(Comm comm, int maxdims, int[] dims, int[] period, int[] coords) {
        error();
    }

    /**
     * <code>MPI_Cart_shift()</code> function.
     */
    public static void cartShift(Comm comm, int direction, int disp, int[] neighbors) {
        cartShift2(Unsafe.toNativeBody(comm), direction, disp, Unsafe.toCArray(neighbors));
    }

    @Native("MPI_Cart_shift(*((MPI_Comm*)v1), v2, v3, &v4[0], &v4[1]);")
    private static void cartShift2(Comm comm, int direction, int disp, int[] neighbors) {
        error();
    }
}
