// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;
import javassist.CtClass;
import javassist.NotFoundException;

/**
 * A variable included in the original Java bytecode.
 *
 * <p>Most JVariable objects represent a simple single assignment (SSA)
 * variable.  If a JVariable object is not an SSA variable,
 * {@link #isMutable()} returns true.
 * For example,
 *
 * <ul><pre>1: if (k > 0)
 * 2:     v = 1;
 * 3: else
 * 4:     v = -1;
 * 5: w = v;</pre></ul>
 *
 * <p>This code makes two JVariable objects: one for {@code v} in line 2,
 * one for {@code v} in line 4 and 5.  Line 1 and 2, Line 4, and
 * Line 5 make a basic block, respectively.
 * The latter JVariable object is not an SSA variable.
 * {@link #value()} on this object returns null.
 * {@link #value()} on {@code w} also returns null since it is either 1 or -1 and it depends on
 * the control flow.
 *
 * <p>A {@code JVariable} object is a reference to a variable.
 * So there might be multiple {@code JVariable} objects referring to
 * the same variable.
 * </p>
 */
public class JVariable extends Variable {
    static class Identity extends ASTree {
        private int identifier;
        private CtClass type;
        private ASTree value;
        private boolean isMutable;

        Identity(int id, CtClass t, ASTree val) {
            identifier = id;
            type = t;
            value = val;
            isMutable = false;
        }

        protected void deepCopy(HashMap<ASTree,ASTree> map) {
            super.deepCopy(map);
            value = copy(value, map);
        }

        public CtClass type() { return type; }

        public void accept(Visitor v) throws VisitorException {
            throw new VisitorException(getClass().getName() + " does not accept a visitor");
        }
    }

    private int index;
    private Identity identity;

    public static final int UNKNOWN_ID = -1;

    /**
     * Makes a copy of the given array.
     */
    public static JVariable[] shallowCopy(JVariable[] array) {
        JVariable[] array2 = new JVariable[array.length];
        for (int i = 0; i < array.length; i++)
            array2[i] = array[i];

        return array2;
    }

    /**
     * Constructs a variable.
     *
     * @param t     the type.
     * @param var   the local variable index at the bytecode level.
     * @param val   the value.
     */
    public JVariable(CtClass t, int var, ASTree val) {
        index = var;
        identity = new Identity(UNKNOWN_ID, t, val);
    }

    /**
     * Constructs a variable.
     */
    public JVariable(CtClass t, int var) {
        this(t, var, null);     // method parameter.
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        identity = copy(identity, map);
    }

    public CtClass type() {
        return identity.type;
    }

    public void setType(CtClass t) {
        identity.type = t;
    }

    public void beMutable() {
        identity.value = null;
        identity.isMutable = true;
    }

    /**
     * Returns true if the value of the variable depends
     * on the control flow.
     */
    public boolean isMutable() { return identity.isMutable; }

    ASTree getValue() { return identity.value; }

    public void setValue(ASTree val) { identity.value = val; }

    /**
     * Returns the local-variable index specified in
     * the original bytecode.
     */
    public int index() { return index; }

    /**
     * Returns true if this variable refers to "this" object.
     */
    public boolean isThis() { return index == 0; }

    /**
     * Turns this variable into being identical to {@code v}. 
     */
    public void setIdentity(JVariable v) {
        if (this.isMutable())
            v.beMutable();

        identity = v.identity;
    }

    /**
     * A map from {@code JVariable} to {@code JVariable}.
     */
    public static class Map {
        HashMap<Identity,Identity> map = new HashMap<Identity,Identity>();

        /**
         * Records a map from an old variable to a new variable.
         * It also makes {@code oldVar} identical to {@code newVar}.  
         */
        public void addAndMakeIdentical(JVariable oldVar, JVariable newVar) {
            map.put(oldVar.identity, newVar.identity);
            oldVar.setIdentity(newVar);
        }

        /**
         * Changes {@code v} to be identical to a variable {@code w}
         * if a map from {@code v} to {@code w} is recorded.
         */
        public void update(JVariable v) {
            Identity id = map.get(v.identity);
            if (id != null)
                v.identity = id;
        }
    }

    public ASTree getIdentity() { return identity; }

    /**
     * Returns the identification number of this variable.
     *
     * @return a positive integer.
     */
    public int identifier() { return identity.identifier; }

    public void setIdentifier(int i) {
        identity.identifier = i;
    }

    /**
     * Returns true if the given variable is identical to this variable.
     */
    public boolean identical(JVariable v) { return identity == v.identity; }

    /**
     * Updates the value and the type if {@code value} is not null.
     */
    public void setValueAndType(ASTree value) throws NotFoundException {
        if (value != null) {
            setValue(value);
            if (!type().isPrimitive())
                setType(value.type());
        }
    }

    public String toString() {
        return "v" + identifier();
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
