// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.offload.ast.Call;
import javassist.offload.ast.UserMetaclass;
import javassist.offload.clang.CodeGen;
import javassist.offload.clang.CTypeDef;

/**
 * A class representing a type in C.
 * A reference to an instance of this class is translated into {@code void*}.
 * No type declaration is generated for the class.
 */
public class ForeignClass extends CTypeDef implements UserMetaclass {
    private int typeId;

    public ForeignClass(CtClass cc, CtField[] f, int uid, String arg, String[] args, Class<?> companion)
        throws NotFoundException
    {
        super(cc);
        typeId = uid;
        if (f.length > 0)
            throw new RuntimeException(cc.getName() + " cannot have a field");
    }

    public void code(CodeGen gen) throws NotFoundException {
        gen.append("/* foreign " + type().getName() + " */\n");
    }

    public void staticFields(CodeGen gen) {}

    public boolean isMacro(Call expr) { return false; }

    public int typeId() { return typeId; }

    public String referenceType() { return "void*"; }

    public String objectType() { return "void"; }
}
