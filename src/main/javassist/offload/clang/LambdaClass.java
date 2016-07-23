// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.offload.Metaclass;

/**
 * A metaclass for Java labmda interfaces.
 * It is equivalent to {@link ImmutableClass} except that the default
 * companion class is {@link ImmutableClass.Lambda}, which will cause
 * better inlining.
 */
public class LambdaClass extends ImmutableClass {
    /**
     * Constructs a lambda class.
     */
    public LambdaClass(CtClass cc, CtField[] f, int uid, String arg,
                       String[] args, Class<?> companion) throws NotFoundException {
        super(cc, f, uid, arg, args, getCompanion(companion));
    }

    private static Class<?> getCompanion(Class<?> c) {
        if (c == Metaclass.defaultCompanion)
            return ImmutableClass.Lambda.class;
        else
            return c;
    }
}
