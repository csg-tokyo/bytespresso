// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Remote method.
 *
 * <p>The method with this annotation must be static.
 * If it is called from the code translated into C,
 * it is executed by the JVM.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Remote {
}
