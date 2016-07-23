// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Foreign function.
 *
 * <p>The method with this annotation must be static.
 */
@Retention(value=RetentionPolicy.RUNTIME)
@Target(value=ElementType.METHOD)
public @interface Foreign {
}
