// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A constant field after the code is translated into C.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Final {}
