// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.offload.ast.TypeDef;

/**
 * A metaclass that can be given to {@code @Metaclass}.
 *
 * <p>If a class implementing this interface is not a subclass
 * of {@code FunctionMetaclasss}, then it has to be a subclass
 * of {@link TypeDef}.  It also has to have a constructor
 * taking the following types of arguments
 * {@code (CtClass, CtField[], int, String, String[])}.
 * </p>
 *
 * @see javassist.offload.reify.ClassTable#makeTypeDef(javassist.CtClass, javassist.offload.Metaclass, javassist.CtField[])
 */
public interface UserMetaclass {
}
