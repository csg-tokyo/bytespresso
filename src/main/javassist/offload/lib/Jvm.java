// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javassist.offload.Native;
import javassist.offload.clang.CodeGen;
import javassist.offload.javatoc.impl.StdMainFunction;

/**
 * Methods for exchanging data between the JVM and the C program.
 */
public class Jvm {
    /**
     * Returns the preamble.
     *
     * @see javassist.offload.clang.CodeGen
     */
    public static String preamble() {
        return "static int " + CodeGen.LCMP + "(long a, long b) { return a > b ? 1 : a == b ? 0 : -1; }\n" +
               "static int " + CodeGen.FCMP + "(float a, float b) { return a > b ? 1 : a == b ? 0 : -1; }\n" +
               "static int " + CodeGen.DCMP + "(double a, double b) { return a > b ? 1 : a == b ? 0 : -1; }\n\n";
    }

    /**
     * Reads a boolean value.
     */
    public static boolean readBoolean() {
        return (javaGetchar() & 0xff) != 0;
    }

    /**
     * Reads a boolean value.
     */
    public static boolean readBoolean(InputStream is) throws IOException {
        return (is.read() & 0xff) != 0;
    }

    /**
     * Writes a boolean value.
     */
    public static void writeBoolean(boolean b) {
        javaPutchar(b ? 1 : 0);
    }

    /**
     * Writes a boolean value.
     */
    public static void writeBoolean(OutputStream os, boolean b) throws IOException {
        os.write(b ? 1 : 0);
    }

    /**
     * Reads an unsigned 8bit value.
     */
    public static int readByte() {
        return javaGetchar() & 0xff;
    }

    /**
     * Reads an unsigned 8bit value.
     */
    public static int readByte(InputStream is) throws IOException {
        return is.read() & 0xff;
    }

    /**
     * Writes an 8bit value.
     */
    public static void writeByte(int i) {
        javaPutchar(i & 0xff);
    }

    /**
     * Writes an 8bit value.
     */
    public static void writeByte(OutputStream os, int i) throws IOException {
        os.write(i & 0xff);
    }

    /**
     * Reads an unsigned 16bit value.
     */
    public static int readShort() {
        int b0 = javaGetchar();
        int b1 = javaGetchar();
        return (b1 << 8) | b0;
    }

    /**
     * Reads an unsigned 16bit value.
     */
    public static int readShort(InputStream is) throws IOException {
        int b0 = is.read();
        int b1 = is.read();
        return (b1 << 8) | b0;
    }

    /**
     * Writes a 16bit value.
     */
    public static void writeShort(int i) {
        javaPutchar(i & 0xff);
        javaPutchar((i >> 8) & 0xff);
    }

    /**
     * Writes a 16bit value.
     */
    public static void writeShort(OutputStream os, int i) throws IOException {
        os.write(i & 0xff);
        os.write((i >> 8) & 0xff);
    }

    /**
     * Reads a signed 32bit value.
     */
    public static int readInt() {
        int b0 = javaGetchar();
        int b1 = javaGetchar();
        int b2 = javaGetchar();
        int b3 = javaGetchar();
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    /**
     * Reads a signed 32bit value.
     */
    public static int readInt(InputStream is) throws IOException {
        int b0 = is.read();
        int b1 = is.read();
        int b2 = is.read();
        int b3 = is.read();
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    /**
     * Writes a 32bit value.
     */
    public static void writeInt(int i) {
        javaPutchar(i & 0xff);
        javaPutchar((i >> 8) & 0xff);
        javaPutchar((i >> 16) & 0xff);
        javaPutchar((i >> 24) & 0xff);
    }

    /**
     * Writes a 32bit value.
     */
    public static void writeInt(OutputStream os, int i) throws IOException {
        os.write(i & 0xff);
        os.write((i >> 8) & 0xff);
        os.write((i >> 16) & 0xff);
        os.write((i >> 24) & 0xff);
    }

    /**
     * Reads a signed 64bit value.
     */
    public static long readLong() {
        long b0 = javaGetchar();
        long b1 = javaGetchar();
        long b2 = javaGetchar();
        long b3 = javaGetchar();
        long b4 = javaGetchar();
        long b5 = javaGetchar();
        long b6 = javaGetchar();
        long b7 = javaGetchar();
        return (b7 << 56) | (b6 << 48) | (b5 << 40) | (b4 << 32)
               | (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    /**
     * Reads a signed 64bit value.
     */
    public static long readLong(InputStream is) throws IOException {
        long b0 = is.read();
        long b1 = is.read();
        long b2 = is.read();
        long b3 = is.read();
        long b4 = is.read();
        long b5 = is.read();
        long b6 = is.read();
        long b7 = is.read();
        return (b7 << 56) | (b6 << 48) | (b5 << 40) | (b4 << 32)
               | (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    /**
     * Writes a 64bit value.
     */
    public static void writeLong(long i) {
        javaPutchar((int)(i & 0xff));
        javaPutchar((int)(i >> 8) & 0xff);
        javaPutchar((int)(i >> 16) & 0xff);
        javaPutchar((int)(i >> 24) & 0xff);
        javaPutchar((int)(i >> 32) & 0xff);
        javaPutchar((int)(i >> 40) & 0xff);
        javaPutchar((int)(i >> 48) & 0xff);
        javaPutchar((int)(i >> 56) & 0xff);
    }

    /**
     * Writes a 64bit value.
     */
    public static void writeLong(OutputStream os, long i) throws IOException {
        os.write((int)(i & 0xff));
        os.write((int)(i >> 8) & 0xff);
        os.write((int)(i >> 16) & 0xff);
        os.write((int)(i >> 24) & 0xff);
        os.write((int)(i >> 32) & 0xff);
        os.write((int)(i >> 40) & 0xff);
        os.write((int)(i >> 48) & 0xff);
        os.write((int)(i >> 56) & 0xff);
    }

    /**
     * Reads a float value.
     */
    public static float readFloat() {
        return toFloatBits(readInt());
    }

    /**
     * Reads a float value.
     */
    public static float readFloat(InputStream is) throws IOException {
        return toFloatBits(readInt(is));
    }

    @Native("union { int i; float f; } tmp; tmp.i = v1; return tmp.f;") 
    private static float toFloatBits(int i) {
        return Float.intBitsToFloat(i);
    }

    /**
     * Writes a float value.
     */
    public static void writeFloat(float f) {
        writeInt(toIntBits(f));
    }

    /**
     * Writes a float value.
     */
    public static void writeFloat(OutputStream os, float f) throws IOException {
        writeInt(os, toIntBits(f));
    }

    @Native("union { int i; float f; } tmp; tmp.f = v1; return tmp.i;") 
    private static int toIntBits(float f) {
        return Float.floatToIntBits(f);
    }

    /**
     * Reads a double value.
     */
    public static double readDouble() {
        return toDoubleBits(readLong());
    }

    /**
     * Reads a double value.
     */
    public static double readDouble(InputStream is) throws IOException {
        return toDoubleBits(readLong(is));
    }

    @Native("union { long i; double f; } tmp; tmp.i = v1; return tmp.f;") 
    private static double toDoubleBits(long j) {
        return Double.longBitsToDouble(j);
    }

    /**
     * Writes a double value.
     */
    public static void writeDouble(double d) {
        writeLong(toLongBits(d));
    }

    /**
     * Writes a double value.
     */
    public static void writeDouble(OutputStream os, double d) throws IOException {
        writeLong(os, toLongBits(d));
    }

    @Native("union { long i; double f; } tmp; tmp.f = v1; return tmp.i;") 
    private static long toLongBits(double d) {
        return Double.doubleToLongBits(d);
    }

    private static int readArrayLen() {
        int s = readInt();
        if (s < 0)
            return 0;
        else
            return s;
    }

    private static int readArrayLen(InputStream is) throws IOException {
        int s = readInt(is);
        if (s < 0)
            throw new IOException("end of stream");
        else
            return s;
    }

    /**
     * Reads a byte array.
     */
    public static byte[] readByteArray() {
        int s = readArrayLen();
        byte[] a = new byte[s];
        javaFread(Unsafe.toCArray(a), 1, s);
        return a;
    }

    /**
     * Reads a byte array.
     */
    public static byte[] readByteArray(InputStream is) throws IOException {
        int s = readArrayLen(is);
        byte[] a = new byte[s];
        readArray(is, s).get(a);
        return a;
    }

    /**
     * Writes a byte array.
     */
    public static void writeByte(byte[] a) {
        int s = a.length;
        writeInt(s);
        javaFwrite(Unsafe.toCArray(a), 1, s);
    }

    /**
     * Writes a byte array.
     */
    public static void writeByte(OutputStream os, byte[] a) throws IOException {
    	 int s = a.length;
         writeInt(os, s);
         WritableByteChannel ch = Channels.newChannel(os);
         ByteBuffer bbuf = ByteBuffer.allocateDirect(s);
         bbuf.order(ByteOrder.nativeOrder());
         bbuf.put(a);
         bbuf.flip();
         ch.write(bbuf);
    }

    /**
     * Reads an array of 32bit integers. 
     */
    public static int[] readIntArray() {
        int s = readArrayLen();
        int[] a = new int[s];

        javaFread(Unsafe.toCArray(a), 4, s);
        // This statement is equivalent to:
        //
        // for (int i = 0; i < s; i++)
        //     a[i] = readInt();

        return a;
    }

    /**
     * Reads an array of 32bit integers. 
     */
    public static int[] readIntArray(InputStream is) throws IOException {
        int s = readArrayLen(is);
        int[] a = new int[s];
        readArray(is, s * 4).asIntBuffer().get(a);
        return a;

        /* This code is equivalent to:

        int s = readArrayLen(is);
        int[] a = new int[s];
        for (int i = 0; i < s; i++)
            a[i] = readInt(is);

        return a;

        */
    }

    private static ByteBuffer readArray(InputStream is, int size) throws IOException {
        ReadableByteChannel ch = Channels.newChannel(is);
        ByteBuffer bbuf = ByteBuffer.allocateDirect(size);
        bbuf.order(ByteOrder.nativeOrder());
        do {
            ch.read(bbuf);
        } while (bbuf.remaining() > 0);
        bbuf.flip();
        return bbuf;
    }

    /**
     * Writes an array of 32bit integers. 
     */
    public static void writeInt(int[] a) {
        int s = a.length;
        writeInt(s);

        javaFwrite(Unsafe.toCArray(a), 4, s);
        // This statement is equivalent to:
        //
        // for (int i = 0; i < s; i++)
        //     writeInt(a[i]);
    }

    /**
     * Writes an array of 32bit integers. 
     */
    public static void writeInt(OutputStream os, int[] a) throws IOException {
        int s = a.length;
        writeInt(os, s);
        WritableByteChannel ch = Channels.newChannel(os);
        ByteBuffer bbuf = ByteBuffer.allocateDirect(s * 4);
        bbuf.order(ByteOrder.nativeOrder());
        bbuf.asIntBuffer().put(a);
        ch.write(bbuf);

        /* This code is equivalent to:

        int s = a.length;
        writeInt(os, s);
        for (int i = 0; i < s; i++)
            writeInt(os, a[i]);

         */
    }

    /**
     * Reads an array of 64bit integers. 
     */
    public static long[] readLongArray() {
        int s = readArrayLen();
        long[] a = new long[s];
        javaFread(Unsafe.toCArray(a), 8, s);
        return a;
    }

    /**
     * Reads an array of 64bit integers. 
     */
    public static long[] readLongArray(InputStream is) throws IOException {
        int s = readArrayLen(is);
        long[] a = new long[s];
        readArray(is, s * 8).asLongBuffer().get(a);
        return a;
    }

    /**
     * Writes an array of 64bit integers. 
     */
    public static void writeLong(long[] a) {
        int s = a.length;
        writeInt(s);
        javaFwrite(Unsafe.toCArray(a), 8, s);
    }

    /**
     * Writes an array of 64bit integers. 
     */
    public static void writeLong(OutputStream os, long[] a) throws IOException {
        int s = a.length;
        writeInt(os, s);
        WritableByteChannel ch = Channels.newChannel(os);
        ByteBuffer bbuf = ByteBuffer.allocateDirect(s * 8);
        bbuf.order(ByteOrder.nativeOrder());
        bbuf.asLongBuffer().put(a);
        ch.write(bbuf);
    }

    /**
     * Reads a float array.
     */
    public static float[] readFloatArray() {
        int s = readArrayLen();
        float[] a = new float[s];
        javaFread(Unsafe.toCArray(a), 4, s);
        return a;
    }

    /**
     * Reads a float array.
     */
    public static float[] readFloatArray(InputStream is) throws IOException {
        int s = readArrayLen(is);
        float[] a = new float[s];
        readArray(is, s * 4).asFloatBuffer().get(a);
        return a;
    }

    /**
     * Writes a float array.
     */
    public static void writeFloat(float[] a) {
        int s = a.length;
        writeInt(s);
        javaFwrite(Unsafe.toCArray(a), 4, s);
    }

    /**
     * Writes a float array.
     */
    public static void writeFloat(OutputStream os, float[] a) throws IOException {
    	int s = a.length;
        writeInt(os, s);
        WritableByteChannel ch = Channels.newChannel(os);
        ByteBuffer bbuf = ByteBuffer.allocateDirect(s * 4);
        bbuf.order(ByteOrder.nativeOrder());
        bbuf.asFloatBuffer().put(a);
        ch.write(bbuf);
    }

    /**
     * Reads a double array.
     */
    public static double[] readDoubleArray() {
        int s = readArrayLen();
        double[] a = new double[s];
        javaFread(Unsafe.toCArray(a), 8, s);
        return a;
    }

    /**
     * Reads a double array.
     */
    public static double[] readDoubleArray(InputStream is) throws IOException {
        int s = readArrayLen(is);
        double[] a = new double[s];
        readArray(is, s * 8).asDoubleBuffer().get(a);
        return a;
    }

    /**
     * Writes a double array.
     */
    public static void writeDouble(double[] a) {
        int s = a.length;
        writeInt(s);
        javaFwrite(Unsafe.toCArray(a), 8, s);
    }

    /**
     * Writes a double array.
     */
    public static void writeDouble(OutputStream os, double[] a) throws IOException {
        int s = a.length;
        writeInt(os, s);
        WritableByteChannel ch = Channels.newChannel(os);
        ByteBuffer bbuf = ByteBuffer.allocateDirect(s * 8);
        bbuf.order(ByteOrder.nativeOrder());
        bbuf.asDoubleBuffer().put(a);
        ch.write(bbuf);
    }

    /**
     * Reads a string.
     */
    public static String readString() {
        int len = readArrayLen();
        byte[] a = new byte[len + 1];
        for (int i = 0; i < len; i++)
            a[i] = (byte)readByte();

        a[len] = 0;         // '\0'
        Unsafe.set(a, 0, 2);    // TypeDef.CLASS_TYPE_STRING is 2.
        Unsafe.set(a, 4, len);
        return (String)(Object)a;
    }

    /**
     * Reads a string.
     */
    public static String readString(InputStream is) throws IOException {
        int s = readArrayLen(is);
        char[] a = new char[s];
        for (int i = 0; i < s; i++)
            a[i] = (char)readByte(is);

        return new String(a);
    }

    /**
     * Writes a string.
     */
    public static void writeString(String s) {
        int len = s.length();
        writeInt(len);
        for (int i = 0; i < len; i++)
            writeByte(s.charAt(i));
    }

    /**
     * Writes a string.
     */
    public static void writeString(OutputStream os, String s) throws IOException {
        int len = s.length();
        writeInt(os, len);
        for (int i = 0; i < len; i++)
            writeByte(os, s.charAt(i));
    }


    /**
     * A handler for invoking a callback method. 
     */
    public static void callbacks() {
        // the correct body will be generated.
        readInt();
        Util.exit(Util.ERR_CALLBACK);
    }

    // StdMainFunction.INPUT and .OUTPUT in the following code
    // are declared
    // in MainFunction#preamble(Generator) and
    // StandaloneMain#preamble(Generator).

    /**
     * {@code fetc()} in the standard C library.
     */
    @Native("return fgetc(" + StdMainFunction.INPUT + ");")
    public static int javaGetchar() {
        try {
            int v = System.in.read();
            return v;
        }
        catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@code fputc()} in the standard C library.
     */
    @Native("fputc(v1, " + StdMainFunction.OUTPUT + ");")
    public static void javaPutchar(int c) {
        System.err.write(c);
    }

    /**
     * {@code fread()} in the standard C library.
     *
     * @param buf           a pointer to C array.
     * @param size          the element size.
     * @param nitems        the number of elements.
     * @see Unsafe#toCArray(int[])
     */
    @Native("fread(v1, v2, v3, " + StdMainFunction.INPUT + ");")
    public static void javaFread(Object buf, int size, int nitems) {}

    /**
     * {@code fwrite()} in the standard C library.
     *
     * @param buf           a pointer to C array.
     * @param size          the element size.
     * @param nitems        the number of elements.
     * @see Unsafe#toCArray(int[])
     */
    @Native("fwrite(v1, v2, v3, " + StdMainFunction.OUTPUT + ");")
    public static void javaFwrite(Object buf, int size, int nitems) {}

    /**
     * {@code fflush()} in the standard C library.
     */
    @Native("fflush(" + StdMainFunction.OUTPUT + ");")
    public static void javaFlush() {
        System.err.flush();
    }
}
