// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.List;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.offload.Metaclass;
import javassist.offload.reify.Inliner;

public interface TypeDef {
    /**
     * Returns the type.
     */
    public CtClass type();

    /**
     * Returns an function-inlining engine.
     * {@code null} may be returned.
     */
    public Inliner inliner(Callable f);

    /**
     * Returns the metaclass for the subtypes.
     * It may return null if the metaclass is the default one.
     */
    public Metaclass metaclassForSubtypes();

    /**
     * Returns all the recorded subtypes.
     */
    public List<TypeDef> getSubtypes();

    /**
     * Records a subtype of this type.
     *
     * @param t     the subtype.
     */
    public void addSubtype(TypeDef t);

    /**
     * Returns true if this type is instantiated.
     */
    public boolean hasInstances();

    /**
     * Records this type will be instantiated.
     *
     * @param yes       true if it is instantiated.
     */
    public void hasInstances(boolean yes);

    /**
     * Returns true if the code for object creation can be implemented
     * with a single function call.
     */
    public boolean instantiationIsSimple();

    /**
     * Returns true if the method is native and so the AST of the method
     * body is not constructed.
     */
    public boolean isNative(CtBehavior method);

    /**
     * Is invoked when a new method is included into the set of
     * translated methods.  It may translate the received method and
     * return it.
     *
     * @param f     the AST of the method.
     * @param m     the method (or the constructor).
     * @return      a translated method.
     */
    public Function add(Function f, CtBehavior m) throws NotFoundException;

    /**
     * Is invoked when a new dispatcher is created.
     * It may record the dispatcher for {@link #add(Dispatcher)}
     * and {@link #findDispatcher(CtMethod)}.
     * A dispatcher is an internal function
     * that invokes the appropriate method implementation according to the
     * actual type of the receiver object.  The static type of the receiver
     * must be the type represented by this TypeDef object.
     */
    public void add(Dispatcher d);

    /**
     * Returns all the recorded dispatchers.
     *
     * @return  null        if no dispatcher is recorded.
     */
    public List<Dispatcher> allDisptachers();

    /**
     * Returns true if the invoked method in this type is expanded as a macro.
     * If true, the body of the method does not have to be translated into a function in C.
     * If false is returned, a {@link Function} object is constructed
     * for the invoked method.
     *
     *　<p>If the invoked method is {@link @Intrinsic}, this method
     * should return false.</p>
     */
    public boolean isMacro(Call expr) throws NotFoundException;
}
