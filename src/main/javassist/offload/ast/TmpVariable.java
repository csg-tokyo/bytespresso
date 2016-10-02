// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;
import javassist.CtClass;

/**
 * A temporary variable generated during decompilation.
 * It is not included in the original java bytecode.
 */
public class TmpVariable extends Variable {
    private CtClass type;
    private ASTree value;       // null if the variable is mutable.
    private int identifier;
    private boolean isMutable;

    /**
     * Constructs a variable.
     *
     * @param t     the type of this variable.
     * @param val   the value of this variable if it is constant.
     *              Otherwise, null.
     * @param id    a unique identifier number.
     *
     * @see javassist.offload.reify.UniqueID#tmpVarId()
     */
    public TmpVariable(CtClass t, ASTree val, int id) {
        if (t == null) {
            // If a tmp variable may refer to an uninitialized object,
            // t is null.
            t = val.type();
        }

        type = t;
        value = val;
        identifier = id;
        isMutable = false;
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        value = copy(value, map);
    }

    public CtClass type() { return type; }

    public void setType(CtClass t) { type = t; }

    public void beMutable() {
        value = null;
        isMutable = true;
    }

    public boolean isMutable() { return isMutable; }

    public ASTree getIdentity() { return this; }

    /**
     * Returns the identification number of this variable.
     */
    public int identifier() { return identifier; }

    public void setIdentifier(int i) { identifier = i; }

    /**
     * Returns null if the variable is mutable.
     */
    ASTree getValue() { return value; }

    public void setValue(ASTree val) { value = val; }

    /**
     * Returns the name of this variable.
     */
    public String name() { return "tmp" + identifier; }

    public String toString() { return "tmp" + identifier; }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
