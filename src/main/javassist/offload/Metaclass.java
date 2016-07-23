// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javassist.offload.ast.UserMetaclass;

/**
 * An annotation for specifying the meta-class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Metaclass {
    /**
     * The class used as a meta-class.
     */
    Class<? extends UserMetaclass> type();

    /**
     * An argument passed to the meta class, for example,
     * the size of the body part of the instances.
     */
    String arg() default "";

    /**
     * An array of arguments passed to the meta class.
     */
    String[] args() default {};

    /**
     * A companion class.
     * The default value is {@link defaultCompanion}.
     */
    Class<?> companion() default Object.class;

    /**
     * Null value.
     */
    static Class<?> defaultCompanion = Object.class; 
}
