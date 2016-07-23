// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inlined method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Inline {
    /**
     * True if the method (and all the other methods
     * directly/indirectly called within that method)
     * should be inlined if possible.  False if the
     * method must not be inlined.
     * The default value is true.
     */
    boolean value() default true;

    /**
     * True if object inlining is performed.
     * The default value is false.
     *
     * <p>If the object passed as an argument is statically determined,
     * the values of its fields may be cached in local variables
     * during the execution of the inlined method.
     * Then all field accesses are translated into accesses to the
     * variables.  The programmers have to guarantee that
     * during the execution of the inlined method,
     * no references to the object are used except the references
     * passed as arguments.  
     * </p>
     */
    boolean object() default false;
}
