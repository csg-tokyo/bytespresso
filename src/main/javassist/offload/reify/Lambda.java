// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.Bytecode;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.BootstrapMethodsAttribute;
import javassist.bytecode.FieldInfo;
import javassist.bytecode.MethodInfo;
import javassist.offload.Options;

public class Lambda {
    public static String getLambdaProxyName(String name) {
        /* A lambda proxy may have a class name like
         * foo.bBar$$Lambda$1/1020923989
         */
        int i = name.indexOf('/');
        if (i > 0)
            name = name.substring(0, i);

        return name;
    }

    public static CtClass makeLambdaClass(int bootstrapIndex, ClassPool pool, ClassFile cf) throws BadBytecode {
        ClassFile classFile = makeLambdaClass(bootstrapIndex, cf);
        CtClass cc = pool.makeClass(classFile);
        if (Options.debug > 1)
            cc.debugWriteFile("./debug");

        return cc;
    }

    public static ClassFile makeLambdaClass(int bootstrapIndex, ClassFile cf) throws BadBytecode {
        AttributeInfo ainfo = cf.getAttribute(BootstrapMethodsAttribute.tag);
        if (!(ainfo instanceof BootstrapMethodsAttribute))
            badBootstrap(cf, 0);

        BootstrapMethodsAttribute boot = (BootstrapMethodsAttribute)ainfo;
        BootstrapMethodsAttribute.BootstrapMethod[] bms = boot.getMethods();
        ConstPool cpool = cf.getConstPool();
        int bootIndex = cpool.getInvokeDynamicBootstrap(bootstrapIndex);
        if (bms.length <= bootIndex)
            badBootstrap(cf, bootIndex);

        BootstrapMethodsAttribute.BootstrapMethod bm = bms[bootIndex];
        if (bm.arguments.length != 3)
            badBootstrap(cf, bootIndex);

        int nt = cpool.getInvokeDynamicNameAndType(bootstrapIndex);
        String methodName = cpool.getUtf8Info(cpool.getNameAndTypeName(nt));
        String constructorDesc = cpool.getUtf8Info(cpool.getNameAndTypeDescriptor(nt));

        ClassFile clazzFile = makeClass(constructorDesc);
        addFields(clazzFile, constructorDesc);
        addConstructor(clazzFile, constructorDesc);
        addMethod(clazzFile, cpool, bm.arguments[1], methodName, bm.arguments[0]);
        return clazzFile;
    }

    private static void badBootstrap(ClassFile cf, int i) throws BadBytecode {
        throw new BadBytecode("bad bootstrap methods attribute in "
                              + cf.getName() + " " + i);
    }

    private static int seqNo = 0;

    private static ClassFile makeClass(String descriptor) {
        String name = "javassist.offload.rt.Lambda" + seqNo++;
        String supertype = descriptor.substring(descriptor.indexOf(')') + 2,
                                                 descriptor.length() - 1);
        ClassFile cf = new ClassFile(false, name, null);
        cf.setInterfaces(new String[] { supertype });
        return cf;
    }

    private static void addFields(ClassFile cf, String desc) {
        int fieldNo = 1;
        int i = 1;
        int first = 1;
        while (true) {
            char c = desc.charAt(i++);
            if (c == ')')
                return;
            else if (c == '[')
                ;
            else if (c == 'L') {
                int i2 = desc.indexOf(';', i) + 1;
                String type = desc.substring(first, i2);
                addField(cf, type, fieldNo++);
                first = i = i2;
            }
            else {
                String type = desc.substring(first, i);
                addField(cf, type, fieldNo++);
                first = i;
            }
        }
    }

    private static void addField(ClassFile cf, String type, int no) {
        FieldInfo f = new FieldInfo(cf.getConstPool(), "v" + no, type);
        f.setAccessFlags(AccessFlag.FINAL);
        cf.addField2(f);
    }

    private static void addConstructor(ClassFile cf, String desc) {
        Bytecode code = new Bytecode(cf.getConstPool());
        code.addAload(0);
        code.addInvokespecial("java/lang/Object", MethodInfo.nameInit, "()V");
        int param = 1;
        for (Object e: cf.getFields()) {
            FieldInfo fi = (FieldInfo)e;
            code.addAload(0);
            String type = fi.getDescriptor();
            char c = type.charAt(0);
            if (c == 'L' || c == '[')
                code.addAload(param);
            else if (c == 'D')
                code.addDload(param++);
            else if (c == 'F')
                code.addFload(param);
            else if (c == 'J')
                code.addLload(param++);
            else
                code.addIload(param);

            code.addPutfield(cf.getName(), fi.getName(), fi.getDescriptor());
            param++;
        }

        code.addReturn(null);   // void
        code.setMaxLocals(param);
        String d = desc.substring(0, desc.indexOf(')') + 1).concat("V");
        MethodInfo minfo = new MethodInfo(cf.getConstPool(), MethodInfo.nameInit, d);
        minfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(minfo);
    }

    private static void addMethod(ClassFile clazzFile, ConstPool srcPool, int index,
                                  String method, int methodType) {
        String desc = srcPool.getUtf8Info(srcPool.getMethodTypeInfo(methodType));
        ConstPool cpool = clazzFile.getConstPool();
        MethodInfo minfo = new MethodInfo(cpool, method, desc);
        clazzFile.addMethod2(minfo);

        Bytecode code = new Bytecode(cpool);

        for (Object e: clazzFile.getFields()) {
            FieldInfo fi = (FieldInfo)e;
            code.addAload(0);
            code.addGetfield(clazzFile.getName(), fi.getName(), fi.getDescriptor());
        }

        loadParams(code, desc);

        int mref = srcPool.getMethodHandleIndex(index);
        String targetClass = srcPool.getClassInfo(srcPool.getMethodrefClass(mref));
        int nt = srcPool.getMethodrefNameAndType(mref);
        String targetMethod = srcPool.getUtf8Info(srcPool.getNameAndTypeName(nt));
        String targetDesc = srcPool.getUtf8Info(srcPool.getNameAndTypeDescriptor(nt));

        int kind = srcPool.getMethodHandleKind(index);
        if (kind == ConstPool.REF_invokeVirtual)
            code.addInvokevirtual(targetClass, targetMethod, targetDesc);
        else if (kind == ConstPool.REF_invokeInterface)
            code.addInvokeinterface(targetClass, targetMethod, targetDesc, 0);
        else if (kind == ConstPool.REF_invokeSpecial)
            code.addInvokespecial(targetClass, targetMethod, targetDesc);
        else if (kind == ConstPool.REF_invokeStatic)
            code.addInvokestatic(targetClass, targetMethod, targetDesc);

        int retOp = returnOpcode(desc);
        if (retOp == Bytecode.RETURN) {
            int targetRetOp = returnOpcode(targetDesc);
            if (targetRetOp != Bytecode.RETURN)
                if (targetRetOp == Bytecode.DRETURN || targetRetOp == Bytecode.LRETURN)
                    code.add(Bytecode.POP2);
                else
                    code.add(Bytecode.POP);
        }

        code.add(retOp);
        minfo.setCodeAttribute(code.toCodeAttribute());
    }

    private static void loadParams(Bytecode code, String desc) {
        int param = 1;
        int i = 1;
        while (true) {
            char c = desc.charAt(i++);
            if (c == ')')
                break;
            else if (c == '[') {
                code.addAload(param++);
                do {
                    c = desc.charAt(i++);
                } while (c == '[');
                if (c == 'L')
                    i = desc.indexOf(';', i) + 1;
            }
            else if (c == 'L') {
                code.addAload(param++);
                i = desc.indexOf(';', i) + 1;
            }
            else if (c == 'D') {
                code.addDload(param);
                param += 2;
            }
            else if (c == 'F')
                code.addFload(param++);
            else if (c == 'J') {
                code.addLload(param);
                param += 2;
            }
            else
                code.addIload(param++);
        }

        code.setMaxLocals(param);
    }

    private static int returnOpcode(String desc) {
        char c = desc.charAt(desc.indexOf(')') + 1);
        if (c == 'D')
            return Bytecode.DRETURN;
        else if (c == 'F')
            return Bytecode.FRETURN;
        else if (c == 'J')
            return Bytecode.LRETURN;
        else if (c == 'L' || c == '[')
            return Bytecode.ARETURN;
        else if (c == 'V')
            return Bytecode.RETURN;
        else
            return Bytecode.IRETURN;
    }
}
