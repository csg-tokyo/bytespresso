// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Native function.
 *
 * <p>
 * The argument specifies the body of this method in C.
 * The body does not include surrounding braces.</p>
 *
 * <p>The method parameters are available in the body
 * by {@code v1}, {@code v2}, ...  If the method is a constructor
 * or a not-{@code static} method,
 * then {@code v1} refers to the {@code this} variable.  {@code v2} refers to
 * the first parameter of the method, and so on.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Native {
    /**
     * The function body.
     */
    String value() default "";
}
