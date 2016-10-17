// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import javassist.offload.reify.Tracer;

/**
 * Runtime for emulating MPI on a single JVM by multiple Java threads.
 */
public class MPIRuntime {
    static class Packet {
        Object array;
        int tag;
        int offset;

        Packet(Object a, int t) {
            array = a;
            tag = t;
            offset = 0;
        }
    }

    static abstract class Receiver {
        abstract void read();
    }

    static class IntReceiver extends Receiver {
        int[] array;
        int offset;
        int length;
        int source;
        Node destination;
        int tag;

        IntReceiver(int[] array, int offset, int length, int src, Node dest, int tag) {
            this.array = array;
            this.offset = offset;
            this.length = length;
            this.source = src;
            this.destination = dest;
            this.tag = tag;
        }

        void read() {
            try {
                read0();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private void read0() throws InterruptedException {
            if (length <= 0)
                return;

            while (true) {
                Packet p = destination.take(source, tag);
                if (!(p.array instanceof int[]))
                    throw new RuntimeException("the received data is not int");

                int[] src = (int[])p.array;
                int i = p.offset;
                while (i < src.length && length > 0) {
                    array[offset++] = src[i++];
                    length--;
                }

                if (length <= 0) {
                    if (i < src.length) {
                        p.offset = i;
                        destination.undo(source, p);
                    }

                    return;
                }
            }
        }
    }

    static class FloatReceiver extends Receiver {
        float[] array;
        int offset;
        int length;
        int source;
        Node destination;
        int tag;

        FloatReceiver(float[] array, int offset, int length, int src, Node dest, int tag) {
            this.array = array;
            this.offset = offset;
            this.length = length;
            this.source = src;
            this.destination = dest;
            this.tag = tag;
        }

        void read() {
            try {
                read0();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        private void read0() throws InterruptedException {
            if (length <= 0)
                return;

            while (true) {
                Packet p = destination.take(source, tag);
                if (!(p.array instanceof float[]))
                    throw new RuntimeException("the received data is not float");

                float[] src = (float[])p.array;
                int i = p.offset;
                while (i < src.length && length > 0) {
                    array[offset++] = src[i++];
                    length--;
                }

                if (length <= 0) {
                    if (i < src.length) {
                        p.offset = i;
                        destination.undo(source, p);
                    }

                    return;
                }
            }
        }
    }

    static class Node {
        int rank = -1;
        Node[] all;
        ArrayBlockingQueue<Packet>[] mailboxes;
        Packet[] lastPackets;

        // for reduction.
        CyclicBarrier barrier;
        int intValue;
        long longValue;
        float floatValue;
        double doubleValue;

        void init(int r, Node[] nodes, int size, CyclicBarrier b) {
            rank = r;
            all = nodes;
            mailboxes = (ArrayBlockingQueue[])new ArrayBlockingQueue[size];
            for (int i = 0; i < size; i++)
                mailboxes[i] = new ArrayBlockingQueue<Packet>(4);

            lastPackets = new Packet[size];
            barrier = b;
        }

        void put(int src, Packet p) throws InterruptedException {
            mailboxes[src].put(p);
        }

        Packet take(int src, int tag) throws InterruptedException {
            if (lastPackets[src] == null)
                return mailboxes[src].take();
            else {
                Packet p = lastPackets[src];
                lastPackets[src] = null;
                return p;
            }
        }

        void undo(int src, Packet p) { lastPackets[src] = p; }
    }

    private static final ThreadLocal<Node> allNodes = new ThreadLocal<Node>() {
        @Override protected Node initialValue() { return new Node(); }
    };

    static Node thisNode() {
        Node n = allNodes.get();
        if (n.rank < 0)
            throw new RuntimeException("MPIRuntime has not been initialized");
        else
            return n;
    }

    /**
     * Starts MPI threads.
     */
    public static void start(final int numberOfNodes, final Runnable run) {
        final Runnable[] runners = new Runnable[numberOfNodes];
        runners[0] = run;
        for (int i = 1; i < numberOfNodes; i++)
            runners[i] = (Runnable)deepCopy(run);

        final Node[] nodes = new Node[numberOfNodes];
        final CyclicBarrier barrier = new CyclicBarrier(numberOfNodes + 1);

        final CyclicBarrier sharedBarrier = new CyclicBarrier(numberOfNodes);
        for (int i = 0; i < numberOfNodes; i++) {
            final int index = i;
            Thread th = new Thread(new Runnable() {
                public void run() {
                    Node n = allNodes.get();
                    nodes[index] = n;
                    n.init(index, nodes, numberOfNodes, sharedBarrier);
                    runners[index].run();
                    doAwait(barrier);
                }
            });
            th.start();
        }

        doAwait(barrier);
    }

    static void doAwait(CyclicBarrier b) {
        try {
            b.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (BrokenBarrierException e) {
            throw new RuntimeException(e);
        }
    }

    static void send(Object array, int dest, int tag) {
        Node src = thisNode();
        try {
            src.all[dest].put(src.rank, new Packet(array, tag));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static FloatReceiver receive(float[] array, int offset, int length, int src, int tag) {
        Node dest = thisNode();
        return new FloatReceiver(array, offset, length, src, dest, tag);
    }

    static IntReceiver receive(int[] array, int offset, int length, int src, int tag) {
        Node dest = thisNode();
        return new IntReceiver(array, offset, length, src, dest, tag);
    }

    static int reduce(int v, MPI.Op op) {
        Node n = thisNode();
        n.intValue = v;
        doAwait(n.barrier);
        if (op == MPI.sumOp) {
            int sum = 0;
            for (int i = 0; i < n.all.length; i++)
                sum += n.all[i].intValue;

            return sum;
        }
        else if (op == MPI.maxOp) {
            int max = n.all[0].intValue;
            for (int i = 1; i < n.all.length; i++)
                if (n.all[i].intValue > max)
                    max = n.all[i].intValue;

            return max;
        }
        else if (op == MPI.minOp) {
            int min = n.all[0].intValue;
            for (int i = 1; i < n.all.length; i++)
                if (n.all[i].intValue < min)
                    min = n.all[i].intValue;

            return min;
        }
        else
            throw new RuntimeException("bad reducation");
    }

    static long reduce(long f, MPI.Op op) {
        Node n = thisNode();
        n.longValue = f;
        doAwait(n.barrier);
        if (op == MPI.sumOp) {
            long sum = 0;
            for (int i = 0; i < n.all.length; i++)
                sum += n.all[i].longValue;

            return sum;
        }
        else if (op == MPI.maxOp) {
            long max = n.all[0].longValue;
            for (int i = 1; i < n.all.length; i++)
                if (n.all[i].longValue > max)
                    max = n.all[i].longValue;

            return max;
        }
        else if (op == MPI.minOp) {
            long min = n.all[0].longValue;
            for (int i = 1; i < n.all.length; i++)
                if (n.all[i].longValue < min)
                    min = n.all[i].longValue;

            return min;
        }
        else
            throw new RuntimeException("bad reducation");
    }

    static float reduce(float f, MPI.Op op) {
        Node n = thisNode();
        n.floatValue = f;
        doAwait(n.barrier);
        if (op == MPI.sumOp) {
            float sum = 0;
            for (int i = 0; i < n.all.length; i++)
                sum += n.all[i].floatValue;

            return sum;
        }
        else if (op == MPI.maxOp) {
            float max = n.all[0].floatValue;
            for (int i = 1; i < n.all.length; i++)
                if (n.all[i].floatValue > max)
                    max = n.all[i].floatValue;

            return max;
        }
        else if (op == MPI.minOp) {
            float min = n.all[0].floatValue;
            for (int i = 1; i < n.all.length; i++)
                if (n.all[i].floatValue < min)
                    min = n.all[i].floatValue;

            return min;
        }
        else
            throw new RuntimeException("bad reducation");
    }

    static double reduce(double f, MPI.Op op) {
        Node n = thisNode();
        n.doubleValue = f;
        doAwait(n.barrier);
        if (op == MPI.sumOp) {
            double sum = 0;
            for (int i = 0; i < n.all.length; i++)
                sum += n.all[i].doubleValue;

            return sum;
        }
        else if (op == MPI.maxOp) {
            double max = n.all[0].doubleValue;
            for (int i = 1; i < n.all.length; i++)
                if (n.all[i].doubleValue > max)
                    max = n.all[i].doubleValue;

            return max;
        }
        else if (op == MPI.minOp) {
            double min = n.all[0].doubleValue;
            for (int i = 1; i < n.all.length; i++)
                if (n.all[i].doubleValue < min)
                    min = n.all[i].doubleValue;

            return min;
        }
        else
            throw new RuntimeException("bad reducation");
    }

    static void barrier() {
        doAwait(thisNode().barrier);
    }

    /**
     * Makes a copy of the given object.
     */
    public static Object deepCopy(Object obj) {
        return deepCopy(obj, new HashMap<Object,Object>());
    }

    static Object deepCopy(Object obj, HashMap<Object,Object> objects) {
        if (obj == null)
            return null;

        Class<?> clazz = obj.getClass();
        if (clazz.isPrimitive() || clazz == String.class)
            return obj;

        Object newObj = objects.get(obj);
        if (newObj != null)
            return newObj;

        if (clazz.isArray())
            return arrayDeepCopy(obj, objects);

        newObj = invokeClone(obj);
        objects.put(obj, newObj);
        java.lang.reflect.Field[] fields = Tracer.getAllFields(clazz);
        for (java.lang.reflect.Field f: fields) {
            f.setAccessible(true);
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                try {
                    Class<?> type = f.getType();
                    if (!type.isPrimitive() && type != String.class) {
                        Object value = f.get(obj);
                        Object newValue = deepCopy(value, objects);
                        if (value != newValue)
                            f.set(newObj, newValue);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
        }
    
        return newObj;
    }

    static Object arrayDeepCopy(Object obj, HashMap<Object,Object> objects) {
        Class<?> elementType = obj.getClass().getComponentType();
        int len = java.lang.reflect.Array.getLength(obj);
        Object newObj = java.lang.reflect.Array.newInstance(elementType, len);
        objects.put(obj, newObj);
        if (elementType.isPrimitive())
            System.arraycopy(obj, 0, newObj, 0, len);
        else
            for (int i = 0; i < len; i++)
                java.lang.reflect.Array.set(newObj, i,
                        deepCopy(java.lang.reflect.Array.get(obj, i), objects));

        return newObj;
    }

    static Object invokeClone(Object obj) {
        try {
            return cloneMethod.invoke(obj);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    static private java.lang.reflect.Method cloneMethod;

    static {
        cloneMethod = null;
        try {
            cloneMethod = Object.class.getDeclaredMethod("clone");
            cloneMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
        } catch (SecurityException e) {}
    }
}
