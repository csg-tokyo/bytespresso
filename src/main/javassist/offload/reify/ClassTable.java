// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.offload.Metaclass;
import javassist.offload.ast.TypeDef;

/**
 * Class table.
 */
public interface ClassTable {
    /**
     * Records that the given type will be instantiated.
     *
     * @param tdef      the type.
     */
    public void hasInstances(TypeDef tdef);

    /**
     * Returns the number of types that will be instantiated.
     */
    int instantiatedTypes();

    /**
     * Returns all the types recorded in the table.
     */
    Collection<? extends TypeDef> allTypes();

    /**
     * Records a class and makes the type definition of the class.
     * If the class is already recorded, it only returns the type
     * definition.
     *
     * @param cc        the recorded class.
     * @return          the type definition of the recorded class.
     */
    public TypeDef addClass(CtClass cc) throws NotFoundException;

    /**
     * Records two classes by calling {@link #addClass(CtClass)}.
     * {@code cc2} may depend on {@code cc1}.  For example, {@code cc1}
     * might be a field type contained in {@code cc2}. 
     *
     * @param cc1       the first recorded class.
     * @param cc2       the second recorded class.
     * @return          the type definition of the second class.
     */
    public TypeDef addClass(CtClass cc1, CtClass cc2) throws NotFoundException;

    /**
     * Returns a new unique identifier, which will be assigned
     * to a type definition.
     */
    public int newClassId();

    /**
     * Obtains the metaclass for the given class.
     */
    default Metaclass selectMetaclass(CtClass cc) throws NotFoundException {
        try {
            Object anno = cc.getAnnotation(Metaclass.class);
            if (anno != null)
                return (Metaclass)anno;
        }
        catch (ClassNotFoundException e) {
            throw new NotFoundException(e.getMessage());
        }

        Metaclass meta = null;
        CtClass sup = cc.getSuperclass();
        if (sup != null)
            meta = addClass(sup).metaclassForSubtypes();

        for (CtClass i: cc.getInterfaces()) {
            Metaclass m = addClass(i).metaclassForSubtypes();
            if (m != null)
                if (meta == null)
                    meta = m;
                else
                    throw new NotFoundException("more than one metaclass candidates: "
                                                + meta.getClass().getName() + ", "
                                                + m.getClass());
        }

        return meta;
    }

    /**
     * Makes a type definition as an instance of the given metaclass.
     *
     * @param meta          the metaclass.
     * @param cc            the class represented by the constructed type definition.
     */
    default TypeDef makeTypeDef(CtClass cc, Metaclass meta, CtField[] fields)
        throws NotFoundException
    {
        Class<? extends TypeDef> clazz;
        try {
            clazz = meta.type().asSubclass(TypeDef.class);
        }
        catch (ClassCastException e) {
            throw new NotFoundException("bad metaclass: " + meta.type().getName());
        }

        TypeDef def;
        try {
            def = clazz.getDeclaredConstructor(CtClass.class, CtField[].class, int.class, String.class,
                                               String[].class, Class.class)
                    .newInstance(cc, fields, newClassId(), meta.arg(), meta.args(), meta.companion());
        } catch (NoSuchMethodException e) {
            throw new NotFoundException(clazz.getName() + " (no constructor)", e);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            throw new RuntimeException(clazz.getName() + ", instantiation failed: " + t.getMessage(), t);
        } catch (Exception e) {
            throw new RuntimeException(clazz.getName() + " (instantiation failed)", e);
        }
        return def;
    }
}
