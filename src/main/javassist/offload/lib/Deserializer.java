// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.offload.clang.ArrayDef;
import javassist.offload.clang.CTypeDef;

/**
 * A utility method for deserializing an object.
 */
public class Deserializer {
    private static final int ARRAY_HEADER_SIZE = ArrayDef.ARRAY_HEADER_SIZE;

    /**
     * Reads an object.
     */
    public static Object read() {
        int num = Jvm.readShort();
        if (num < 1)
            return null;

        Object[] objects = new Object[num];
        int i = 0;
        while (i < num) {
            int type = Jvm.readByte();
            i = read(type, i, objects, null, 0);
        }

        Object result = objects[0];
        Unsafe.free(objects);
        return result;
    }

    private static int read(int type, int index, Object[] objects, int[] target, int offset) {
        Object value = null;
        if (type == CTypeDef.INT_ARRAY)
            value = Jvm.readIntArray();
        else if (type == CTypeDef.LONG_ARRAY)
            value = Jvm.readLongArray();
        else if (type == CTypeDef.FLOAT_ARRAY)
            value = Jvm.readFloatArray();
        else if (type == CTypeDef.DOUBLE_ARRAY)
            value = Jvm.readDoubleArray();
        else if (type == CTypeDef.CLASS_TYPE_HEAD) {
            int typeid = Jvm.readShort();    // since CLASS_TYPE_HEAD is 0.
            if (typeid == CTypeDef.CLASS_TYPE_CUSTOM)
                value = readRawData();
            else
                return readObject(index, objects, target, offset, typeid);
        }
        else
            Util.exit(Util.ERR_DESERIALIZE);

        if (target != null)
            Unsafe.set(target, offset, value);

        objects[index] = value;
        return index + 1;
    }

    private static Object readRawData() {
        int header = Jvm.readInt();
        byte[] value = Jvm.readByteArray();
        Unsafe.set(value, 0, header);
        return value;
    }

    private static int readObject(int index, Object[] objects, int[] target,
                                  int targetOffset, int typeid)
    {
        if (typeid == CTypeDef.CLASS_TYPE_NULL) {
            if (target != null)
                Unsafe.set(target, targetOffset, null);

            return index;
        }
        else if ((typeid & CTypeDef.OBJECT_ID_BIT) != 0) {
            if (target != null)
                Unsafe.set(target, targetOffset,
                           objects[typeid & ~CTypeDef.OBJECT_ID_BIT]);

            return index;
        }

        int size = Jvm.readShort();    // in word
        int[] object = new int[size - ARRAY_HEADER_SIZE];
        objects[index++] = object;
        if (target != null)
            Unsafe.set(target, targetOffset, object);

        Unsafe.set(object, 0, typeid << CTypeDef.FLAG_BITS);     // initialize the object header.
        int offset = 1;
        while (offset < size) {
            int type = Jvm.readByte();
            if (type == CTypeDef.INT || type == CTypeDef.FLOAT)
                Unsafe.set(object, offset++, Jvm.readInt());
            else {
                if (offset % 2 > 0)
                    Unsafe.set(object, offset++, 0);

                if (type == CTypeDef.LONG || type == CTypeDef.DOUBLE)
                    Unsafe.set(object, offset, Jvm.readLong());
                else
                    index = read(type, index, objects, object, offset);

                offset += 2;
            }
        }

        return index;
    }
}
