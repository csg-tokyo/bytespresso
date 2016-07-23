// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.offload.ast.VisitorException;
import javassist.offload.clang.CodeGen;
import javassist.offload.clang.CTypeDef;
import javassist.offload.lib.Jvm;
import javassist.offload.reify.Tracer;

/**
 * Object serializer.
 * This serializer supports object types, int, long, float, double, and
 * an array type of int, long, float, or double.  An object array is not
 * supported.
 *
 * @see javassist.offload.lib.Deserializer
  */
public class Serializer {
    public static int HEADER_SIZE = 1; /* the object header is 1 word (32 bits) */
    private ByteArrayOutputStream output = new ByteArrayOutputStream();
    private int counter = 0;

    public void serialize(Object obj, CodeGen gen)
        throws IOException
    {
        HashMap<Object,Integer> objects = new HashMap<Object,Integer>();
        try {
            serialize(obj, objects, gen);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void write(OutputStream os) throws IOException {
        Jvm.writeShort(os, counter);
        output.writeTo(os);
    }

    public ByteArrayOutputStream getBinary() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out);
        // dumpBinary();
        return out;
    }

    /*
    private void dumpBinary() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out);
        byte[] bin = out.toByteArray();
        System.out.print("sent data: ");
        for (int i = 0; i < bin.length; i++) {
            System.out.print(Integer.toHexString(bin[i] & 0xff));
            System.out.print(' ');
        }

        System.out.println();
    }*/

    private void serialize(Object obj,
                           HashMap<Object,Integer> objects, CodeGen gen)
        throws IOException, IllegalArgumentException,
               IllegalAccessException, VisitorException
    {
        if (obj == null) {
            // the object is null.
            Jvm.writeByte(output, CTypeDef.CLASS_TYPE_HEAD);
            Jvm.writeShort(output, CTypeDef.CLASS_TYPE_NULL);
            return;
        }

        Integer oid = objects.get(obj);
        if (oid != null) {
            // the object has been already serialized.
            // so only the object identifier is written into the stream.
            Jvm.writeByte(output, CTypeDef.CLASS_TYPE_HEAD);
            Jvm.writeShort(output, oid | CTypeDef.OBJECT_ID_BIT);
            return;
        }

        objects.put(obj, counter++);
        Class<?> klass = obj.getClass();
        if (klass.isArray()) {
            if (klass == int[].class) {
                Jvm.writeByte(output, CTypeDef.INT_ARRAY);
                Jvm.writeInt(output, (int[])obj);
            }
            else if (klass == long[].class) {
                Jvm.writeByte(output, CTypeDef.LONG_ARRAY);
                Jvm.writeLong(output, (long[])obj);
            }
            else if (klass == float[].class) {
                Jvm.writeByte(output, CTypeDef.FLOAT_ARRAY);
                Jvm.writeFloat(output, (float[])obj);
            }
            else if (klass == double[].class) {
                Jvm.writeByte(output, CTypeDef.DOUBLE_ARRAY);
                Jvm.writeDouble(output, (double[])obj);
            }
            else
                throw new IOException("not supported type " + klass.getName());
        }
        else {
            CTypeDef def = gen.addClass(gen.getCtClass(klass));
            gen.classTable().hasInstances(def);
            if (def.hasCustomSerializer())
                def.serializeObject(obj, output);
            else
                serializeObject(obj, klass, def, objects, gen);
        }
    }

    private void serializeObject(Object obj, Class<?> klass, CTypeDef def,
                HashMap<Object,Integer> objects, CodeGen gen)
        throws IOException, IllegalArgumentException,
               IllegalAccessException, VisitorException
    {
        Field[] fields = Tracer.getAllFields(klass);
        int size = HEADER_SIZE;   // in 32bit-words
        for (int i = 0; i < fields.length; i++)
            if (!Modifier.isStatic(fields[i].getModifiers())) {
                Class<?> t = fields[i].getType();
                int delta = 2;
                if (t.isPrimitive())
                    if (t != long.class && t != double.class)
                        delta = 1;

                if (delta == 2 && (size % 2 > 0))
                    delta = 3;

                size += delta;
            }

        Jvm.writeByte(output, CTypeDef.CLASS_TYPE_HEAD);
        Jvm.writeShort(output, def.typeId());
        Jvm.writeShort(output, size);

        for (int i = 0; i < fields.length; i++) {
            if (!Modifier.isStatic(fields[i].getModifiers())) {
                Class<?> t = fields[i].getType();
                fields[i].setAccessible(true);
                Object value = fields[i].get(obj);
                if (t.isPrimitive())
                    if (t == int.class) {
                        Jvm.writeByte(output, CTypeDef.INT);
                        Jvm.writeInt(output, ((Integer)value).intValue());
                    }
                    else if (t == long.class) {
                        Jvm.writeByte(output, CTypeDef.LONG);
                        Jvm.writeLong(output, ((Long)value).longValue());
                    }
                    else if (t == float.class) {
                        Jvm.writeByte(output, CTypeDef.FLOAT);
                        Jvm.writeFloat(output, ((Float)value).floatValue());
                    }
                    else if (t == double.class) {
                        Jvm.writeByte(output, CTypeDef.DOUBLE);
                        Jvm.writeDouble(output, ((Double)value).doubleValue());
                    }
                    else
                        throw new IOException("not supported type " + t + " in " + klass.getName());
                else
                    serialize(value, objects, gen);
            }
        }
    }

    /**
     * This returns only a static method.
     *
     * @return  Jvm.writeInt() etc. or null if type is void.
     */
    public static CtMethod findWriteMethod(CtClass type, CtClass receiver, String kind)
        throws NotFoundException
    {
        String methodName, descriptor;
        if (type == CtClass.voidType)
            return null;
        else if (type == CtClass.intType) {
            methodName = "writeInt";
            descriptor = "(I)V";
        }
        else if (type == CtClass.floatType) {
            methodName = "writeFloat";
            descriptor = "(F)V";
        }
        else if (type == CtClass.doubleType) {
            methodName = "writeDouble";
            descriptor = "(D)V";
        }
        else if (type == CtClass.longType) {
            methodName = "writeLong";
            descriptor = "(J)V";
        }
        else if (type == CtClass.shortType) {
            methodName = "writeShort";
            descriptor = "(S)V";
        }
        else if (type == CtClass.byteType) {
            methodName = "writeByte";
            descriptor = "(B)V";
        }
        else if (type == CtClass.booleanType) {
            methodName = "writeBoolean";
            descriptor = "(Z)V";
        }
        else if (type.isArray()) {
            CtClass compType = type.getComponentType();
            if (compType == CtClass.intType) {
                methodName = "writeInt";
                descriptor = "([I)V";
            }
            else if (compType == CtClass.floatType) {
                methodName = "writeFloat";
                descriptor = "([F)V";
            }
            else if (compType == CtClass.doubleType) {
                methodName = "writeDouble";
                descriptor = "([D)V";
            }
            else if (compType == CtClass.longType) {
                methodName = "writeLong";
                descriptor = "([J)V";
            }
            else if (compType == CtClass.byteType) {
                methodName = "writeByte";
                descriptor = "([B)V";
            }
            else
                throw makeErrorMsg(type, kind);
        }
        else if (type.getName().equals(String.class.getName())) {
            methodName = "writeString";
            descriptor = "(Ljava/lang/String;)V";
        }
        else
            throw makeErrorMsg(type, kind);

        return receiver.getMethod(methodName, descriptor);
    }

    /**
     * @return  null if type is void.
     * @see javassist.offload.lib.Deserializer#read()
     */
    public static CtMethod findReadMethod(CtClass type, CtClass receiver, String kind)
        throws NotFoundException
    {
        String methodName;
        String descriptor;

        if (type == CtClass.intType) {
            methodName = "readInt";
            descriptor = "()I";
        }
        else if (type == CtClass.longType) {
            methodName = "readLong";
            descriptor = "()J";
        }
        else if (type == CtClass.floatType) {
            methodName = "readFloat";
            descriptor = "()F";
        }
        else if (type == CtClass.doubleType) {
            methodName = "readDouble";
            descriptor = "()D";
        }
        else if (type == CtClass.shortType) {
            methodName = "readShort";
            descriptor = "()S";
        }
        else if (type == CtClass.byteType) {
            methodName = "readByte";
            descriptor = "()B";
        }
        else if (type == CtClass.booleanType) {
            methodName = "readBoolean";
            descriptor = "()Z";
        }
        else if (type == CtClass.voidType)
            return null;
        else if (type.isArray()) {
            CtClass compType = type.getComponentType();
            if (compType == CtClass.intType) {
                methodName = "readIntArray";
                descriptor = "()[I";
            }
            else if (compType == CtClass.floatType) {
                methodName = "readFloatArray";
                descriptor = "()[F";
            }
            else if (compType == CtClass.doubleType) {
                methodName = "readDoubleArray";
                descriptor = "()[D";
            }
            else if (compType == CtClass.longType) {
                methodName = "readLongArray";
                descriptor = "()[J";
            }
            else if (compType == CtClass.byteType) {
                methodName = "readByteArray";
                descriptor = "()[B";
            }
            else
                throw makeErrorMsg(type, kind);
        }
        else if (type.getName().equals(String.class.getName())) {
            methodName = "readString";
            descriptor = "()Ljava/lang/String;";
        }
        else
            throw makeErrorMsg(type, kind);

        return receiver.getMethod(methodName, descriptor);
    }

    public static Object readValue(CtClass type, CtClass compType, InputStream is)
            throws IOException
    {
        if (type == CtClass.voidType)
            return void.class;        // if the type is void, void.class is returned. 
        else if (type == CtClass.intType)
            return Jvm.readInt(is); 
        else if (type == CtClass.floatType)
            return Jvm.readFloat(is); 
        else if (type == CtClass.doubleType)
            return Jvm.readDouble(is); 
        else if (type == CtClass.longType)
            return Jvm.readLong(is); 
        else if (type == CtClass.shortType)
            return Jvm.readShort(is); 
        else if (type == CtClass.byteType)
            return Jvm.readByte(is); 
        else if (type == CtClass.booleanType)
            return Jvm.readBoolean(is); 
        else if (type.isArray()) {
            if (compType == CtClass.intType)
                return Jvm.readIntArray(is);
            else if (compType == CtClass.floatType)
                return Jvm.readFloatArray(is);
            else if (compType == CtClass.doubleType)
                return Jvm.readDoubleArray(is);
            else if (compType == CtClass.longType)
                return Jvm.readLongArray(is);
        }
        else if (type.getName().equals(String.class.getName()))
            return Jvm.readString(is);

        throw makeErrorMsg(type, "return");
    }

    public static Object readValue(Class<?> type, InputStream is)
        throws IOException
    {
        if (type == int.class)
            return Jvm.readInt(is); 
        else if (type == float.class)
            return Jvm.readFloat(is); 
        else if (type == double.class)
            return Jvm.readDouble(is); 
        else if (type == long.class)
            return Jvm.readLong(is); 
        else if (type == short.class)
            return Jvm.readShort(is); 
        else if (type == byte.class)
            return Jvm.readByte(is); 
        else if (type == boolean.class)
            return Jvm.readBoolean(is); 
        else if (type.isArray()) {
            if (type == int[].class)
                return Jvm.readIntArray(is);
            else if (type == float[].class)
                return Jvm.readFloatArray(is);
            else if (type == double[].class)
                return Jvm.readDoubleArray(is);
            else if (type == long[].class)
                return Jvm.readLongArray(is);
            else if (type == byte[].class)
                return Jvm.readByteArray(is);
        }
        else if (type == String.class)
            return Jvm.readString(is);

        throw makeErrorMsg(type, "remote parameter");
    }

    public static void writeValue(Class<?> type, Object value, OutputStream os)
            throws IOException
    {
        if (type == int.class || type == Integer.class)
            Jvm.writeInt(os, (Integer)value);
        else if (type == float.class || type == Float.class)
            Jvm.writeFloat(os, (Float)value); 
        else if (type == double.class || type == Double.class)
            Jvm.writeDouble(os, (Double)value); 
        else if (type == long.class || type == Long.class)
            Jvm.writeLong(os, (Long)value); 
        else if (type == short.class || type == Short.class)
            Jvm.writeShort(os, (Short)value); 
        else if (type == byte.class || type == Byte.class)
            Jvm.writeByte(os, (Byte)value); 
        else if (type == boolean.class || type == Boolean.class)
            Jvm.writeBoolean(os, (Boolean)value); 
        else if (type.isArray()) {
            if (type == int[].class)
                Jvm.writeInt(os, (int[])value); 
            else if (type == float[].class)
                Jvm.writeFloat(os, (float[])value); 
            else if (type == double[].class)
                Jvm.writeDouble(os, (double[])value); 
            else if (type == long[].class)
                Jvm.writeLong(os, (long[])value);
            else if (type == byte[].class)
                Jvm.writeByte(os, (byte[])value);
            else
                throw makeErrorMsg(type, "remote return");
        }
        else if (type == String.class)
            Jvm.writeString(os, (String)value);
        else
            throw makeErrorMsg(type, "remote return");
    }

    private static RuntimeException makeErrorMsg(CtClass type, String kind) {
        return new RuntimeException("not supported " + kind + " type: " + type.getName());
    }

    private static RuntimeException makeErrorMsg(Class<?> type, String kind) {
        return new RuntimeException("not supported " + kind + " type: " + type.getName());
    }
}
