// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.ArrayList;

import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.offload.clang.CTypeDef;

/**
 * A dynamic method dispatcher.
 * It is a function implementing a dynamic method call.
 * A {@link Call} object may refer to a {@link Dispatcher} object
 * as the called function. The dispatcher will select the most
 * appropriate method implementation and invoke it.
 */
public class Dispatcher extends Function {
    private ArrayList<TypeDef> types;
    private CtMethod method;
    private ArrayList<Function> functions;

    /**
     * Constructs a dispatcher.
     *
     * @param cm        the method specified by invokevirtual.
     * @param fname     the name of the C function implementing this dispatcher.
     */
    protected Dispatcher(CtMethod cm, String fname)
        throws NotFoundException
    {
        super(cm.getReturnType(), cm, fname, makeParameters(cm), new ArrayList<Block>(), false);
        types = new ArrayList<TypeDef>();
        method = cm;
        functions = new ArrayList<Function>(); 
    }

    private static JVariable[] makeParameters(CtMethod cm) throws NotFoundException {
        CtClass[] params = cm.getParameterTypes();
        JVariable[] vars = new JVariable[params.length + 1];
        vars[0] = new JVariable(cm.getDeclaringClass(), 0);
        for (int i = 0; i < params.length; i++)
            vars[i + 1] = new JVariable(params[i], i + 1);

        return vars;
    }

    public boolean specializable() { return false; }

    /**
     * Returns the called method.
     */
    public CtMethod method() { return method; }

    /**
     * Returns the list of the target (receiver) types.
     */
    public ArrayList<TypeDef> targetTypes() { return types; }

    /**
     * Returns the i-th target (receiver) type.
     *
     * @param i         specifies which type is returned.
     */
    @SuppressWarnings("unchecked")
    public <T extends TypeDef> T targetType(int i) { return (T)types.get(i); }

    /**
     * Returns the list of functions.
     *
     * @return  non null.
     */
    public ArrayList<Function> functions() { return functions; }

    /**
     * Records a pair of receiver type and function body.
     * The function body implements the method for the
     * receiver type.
     */
    public void add(TypeDef t, Function f) {
        types.add(t);
        functions.add(f);
    }

    /**
     * Returns true if the given type is already recorded.
     *
     * @param type      the type.
     * @see #add(CTypeDef, Function)
     */
    public boolean supports(TypeDef type) {
        for (TypeDef t: types)
            if (t == type)
                return true;

        return false;
    }

    protected boolean hasSingleBody() {
        return types.size() == 1;
    }

    public String name() {
        if (hasSingleBody())
            return functions.get(0).name();
        else
            return super.name();
    }

    public JVariable[] parameters() {
        if (hasSingleBody())
            return functions.get(0).parameters();
        else
            return super.parameters();
    }

    public CtClass type() {
        if (hasSingleBody())
            return functions.get(0).type();
        else
            return super.type();
    }
}
