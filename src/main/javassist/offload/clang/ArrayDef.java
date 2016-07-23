// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.CtClass;
import javassist.NotFoundException;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Array;
import javassist.offload.ast.NewArray;
import javassist.offload.ast.VisitorException;

/**
 * Array type.
 *
 * The first 32bit is an object header.
 * The following 32bit represents the number of the elements.
 *
 * @see Array
 * @see NewArray
 * @see javassist.offload.javatoc.impl.JavaObjectToC
 * @see javassist.offload.lib.Deserializer
 */
public class ArrayDef extends CTypeDef {
    public static final String SIZE_FIELD = "size_";
    public static final String STRUCT = "struct array_object_";
    public static final int ARRAY_HEADER_SIZE = 2;

    private String refTypeName;
    private int typeId;
    public static String NEWARRAY = "new_array";

    public ArrayDef(CtClass cc, String refType, int tid) {
        super(cc);
        refTypeName = refType;
        typeId = tid;
    }

    public int typeId() { return typeId; }

    public String referenceType() { return refTypeName; }

    /**
     * Returns the component type name.
     */
    public String objectType() {
        return refTypeName.substring(0, refTypeName.length() - 1);
    }

    public void code(CodeGen gen) {}

    public void staticFields(CodeGen gen) {}

    public static void code(CodeGen gen, Array expr)
        throws VisitorException
    {
        String offset;
        CtClass type = expr.type();
        if (type == CtClass.booleanType || type == CtClass.byteType)
            offset = " + 8";
        else if (type == CtClass.charType || type == CtClass.shortType)
            offset = " + 4";
        else if (type == CtClass.intType || type == CtClass.floatType)
            offset = " + 2";
        else
            offset = " + 1";

        expr.array().accept(gen);
        gen.append('[');
        expr.index().accept(gen);
        gen.append(offset);
        gen.append(']');
    }

    public static void code(CodeGen gen, NewArray expr)
        throws VisitorException
    {
        gen.append('(');
        gen.append(gen.typeName(expr.type()));
        gen.append(')');
        gen.append(NEWARRAY);
        gen.append('(');
        if (expr.dimension() != 1)
            throw new RuntimeException("a multi-dimensional array is not available: "
                                       + expr.dimension());

        gen.append(expr.child(0));
        String size;
        CtClass compoType = expr.componentType();
        if (compoType.isPrimitive())
            if (compoType == CtClass.booleanType)
                size = ", " + CTypeDef.BOOL_ARRAY + ", 1)";
            else if (compoType == CtClass.byteType)
                size = ", " + CTypeDef.BYTE_ARRAY + ", 1)";
            else if (compoType == CtClass.charType)
                size = ", " + CTypeDef.CHAR_ARRAY + ", 2)";
            else if (compoType == CtClass.shortType)
                size = ", " + CTypeDef.SHORT_ARRAY + ", 2)";
            else if (compoType == CtClass.intType)
                size = ", " + CTypeDef.INT_ARRAY + ", 4)";
            else if (compoType == CtClass.floatType)
                size = ", " + CTypeDef.FLOAT_ARRAY + ", 4)";
            else if (compoType == CtClass.longType)
                size = ", " + CTypeDef.LONG_ARRAY + ", 8)";
            else if (compoType == CtClass.doubleType)
                size = ", " + CTypeDef.DOUBLE_ARRAY + ", 8)";
            else
                throw new RuntimeException("not supported array type: " + compoType.getName());
        else {
            CTypeDef type = gen.typeDef(expr.type());
            
            try {
                CTypeDef componentType = gen.addClass(type.type().getComponentType());
                size = ", " + type.typeId() + ", sizeof(" + componentType.referenceType() + "))";
            } catch (NotFoundException e) {
                throw new VisitorException(e);
            }
        }
    
        gen.append(size);
    }

    public static void lengthCode(CodeGen gen, ASTree array)
        throws VisitorException
    {
        gen.append("(((" + STRUCT + "*)");
        gen.append(array);
        gen.append(")->" + SIZE_FIELD + ")");
    }

    public static void preamble(CodeGen gen) {
        gen.append(STRUCT).append(" {" + HEADER_DECL + "int " + SIZE_FIELD + "; };\n");

        String calloc = gen.heapMemory().calloc();
        gen.append("static void* " + NEWARRAY + "(int num, int tid, int size) {\n");
        gen.append("  ").append(STRUCT + "* p;\n");
        gen.append("  if (size >= sizeof(" + STRUCT + ")) p = (" + STRUCT + "*)" + calloc).append("(num + 1, size);\n");
        gen.append("  else if (size >= 4) p = (" + STRUCT + "*)" + calloc + "(num + 2, size);\n");
        gen.append("  else p = (" + STRUCT + "*)" + calloc + "(num + sizeof(" + STRUCT + "), size);\n\n");
        gen.append("  p->" + HEADER_FIELD + " = (tid<<24); p->" + SIZE_FIELD + " = num; return p;\n");
        gen.append("}\n\n");
    }
}
