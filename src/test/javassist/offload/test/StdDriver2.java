package javassist.offload.test;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.offload.javatoc.DriverException;
import javassist.offload.javatoc.StdDriver;

/**
 * A standard compiler driver for testing.
 */
public class StdDriver2 extends StdDriver {
    /**
     * Constructs a driver.
     *
     * <p>If the system property <code>c.compiler</code> is set,
     * then the value is used as a compiler command.
     * If <code>c.endian</code> is <code>big</code>, then the endian
     * is set to big endian.  Otherwise, little endian.
     * </p>
     *
     * <p>To set a system property, start the JVM like:
     * <ul>
     * {@code java -Dc.compiler="gcc -O4" -Dc.endian="little"}
     * </ul>
     * </p>
     */
    public StdDriver2() {}

    /**
     * Invokes the method directly declared in the class of the receiver object.
     * If the method is overloaded, one of the method with the given name
     * is arbitrarily selected.
     *
     * @param methodName        the method name.
     * @param receiver          the receiver object.
     * @param args              the arguments.
     * @return the result returned by the method.
     */
    public Object invoke(String methodName, Object receiver, Object[] args) throws DriverException {
        return invoke(receiver.getClass(), methodName, receiver, args);
    }

    /**
     * Invokes the method directly declared in the given class.
     * If the method is overloaded, one of the method with the given name
     * is arbitrarily selected.
     *
     * @param clazz             the class.
     * @param methodName        the method name.
     * @param receiver          the receiver object or null.
     * @param args              the arguments.
     * @return the result returned by the method.
     */
    public Object invoke(Class<?> clazz, String methodName, Object receiver, Object[] args)
        throws DriverException
    {
        try {
            CtClass cc = classPool.get(clazz.getName());
            CtMethod cm = cc.getDeclaredMethod(methodName);
            return invoke(cm, makeArguments(cm, receiver, args), new CtMethod[0]);
        }
        catch (NotFoundException e) {
            throw new DriverException(e);
        }
    }

    /**
     * Invokes the method on the receiver object.
     *
     * @param method        the method.
     * @param receiver      the receiver object or null.
     * @param args          the arguments.
     * @param callbacks     the callback methods.
     *                      They must be declared on the receiver object.
     * @return the result returned by the method.
     */
    public Object invoke(java.lang.reflect.Method method, Object receiver, Object[] args,
                         java.lang.reflect.Method[] callbacks)
        throws DriverException
    {
        try {
            Class<?> clazz;
            if (receiver == null)
                clazz = method.getDeclaringClass();
            else
                clazz = receiver.getClass();

            CtClass cc = classPool.get(clazz.getName());
            CtMethod cm = StdDriver.getMethod(cc, method);
            Object[] args2 = makeArguments(cm, receiver, args);
            CtMethod[] callbacks2 = new CtMethod[callbacks.length];
            for (int i = 0; i < callbacks.length; i++)
                callbacks2[i] = StdDriver.getMethod(cc, callbacks[i]);

            return invoke(cm, args2, callbacks2);
        }
        catch (NotFoundException e) {
            throw new DriverException(e);
        }
    }
}
