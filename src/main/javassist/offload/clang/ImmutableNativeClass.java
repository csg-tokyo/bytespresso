// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.CtClass;
import javassist.Modifier;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.offload.Metaclass;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Assign;
import javassist.offload.ast.Call;
import javassist.offload.ast.GetField;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.PutField;
import javassist.offload.ast.AdhocAST;
import javassist.offload.ast.VisitorException;
import javassist.offload.javatoc.impl.JavaObjectToC;

public class ImmutableNativeClass extends ImmutableClass {
    /**
     * Constructs an immutable native class.
     *
     * @param cc        the class.
     * @param f         the fields contained in the class.
     * @param uid       a unique identifier.
     * @param name      the type name in C.
     * @param args      ignored.
     * @param companion the companion class.
     */
    public ImmutableNativeClass(CtClass cc, CtField[] f, int uid, String name, String[] args, Class<?> companion)
        throws NotFoundException
    {
        super(cc, f, uid, name, companion);
        if (!Modifier.isFinal(cc.getModifiers()))
            throw new RuntimeException(cc.getName() + " must be final.");

        if (cc.getInterfaces().length > 0)
            throw new RuntimeException(cc.getName() + " must not implement/extend an interface.");
    }

    public Metaclass metaclassForSubtypes() { return null; }

    protected ASTree makeHeaderInitializer(final JVariable self) {
        return new AdhocAST(CtClass.voidType, "");
    }

    public void code(CodeGen gen) throws NotFoundException {}

    public void staticFields(CodeGen gen) throws VisitorException {
        for (CtField f: fields())
            if (Modifier.isStatic(f.getModifiers()))
                throw new VisitorException(type().getName() + " must not have a static field.");
    }

    /* The object data is not serialized.
     */
    public void objectToCode(Object obj, JavaObjectToC jo, CodeGen output,
                             Class<?> klass, String gvarName)
    {
        output.recordGivenObject(obj, gvarName);
        output.heapMemory().declarationCode(output, obj, this, objectType(), gvarName);
        output.append(";\n");
    }

    public void doAssign(CodeGen gen, Assign expr) throws VisitorException {
        doAssign0(gen, expr);
    }

    public void invokeMethod(CodeGen gen, Call expr) throws VisitorException {
        super.invokeMethod(gen, expr);
    }

    public void getField(CodeGen gen, GetField expr) throws VisitorException {
        notFound(expr.fieldName());
    }

    public void putField(CodeGen gen, PutField expr) throws VisitorException {
        notFound(expr.fieldName());
    }

    public void getHeader(CodeGen gen, ASTree expr) throws VisitorException {
        throw new VisitorException(type().getName() + " does not have an object header.");
    }
}
