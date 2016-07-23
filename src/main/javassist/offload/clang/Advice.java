// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.CtClass;

/**
 * An {@link Advice} object specifies how method names should be
 * renamed at every call site appearing in the method bodies declared in
 * the class associated with this advice.
 *
 * <p>A class implementing this interface has to have a constructor without
 * a parameter.</p>
 *
 * @see ImmutableClass
 */
public interface Advice {
    /**
     * Returns a new name of the given method name.
     *
     * @param klass         the apparent type of the target object.
     * @param methodName    the method name.
     * @return              null or a new method name.
     */
    String rename(CtClass klass, String methodName);
}
