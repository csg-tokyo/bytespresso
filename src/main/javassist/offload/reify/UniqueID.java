// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

/**
 * A unique identifier generator.
 */
public class UniqueID {
    private UniqueID parent;
    private int variable;
    private int tmpVar;
    
    static class Root extends UniqueID {
        private int funcId = 0;

        Root() { super(null); }

        @Override public int functionId() { return funcId++; }
    }

    public static UniqueID make() {
        return new Root();
    }

    private UniqueID(UniqueID p) {
        parent = p;
        variable = 0;
        tmpVar = 0;
    }

    public UniqueID instance() {
        UniqueID id = parent;
        if (id == null)
            id = this;

        return new UniqueID(id);
    }

    /**
     * Returns a new function identifier.
     */
    public int functionId() { return parent.functionId(); }

    /**
     * Returns a new variable identifier.
     */
    public int varId() { return variable++; }

    /**
     * Returns a new temporary variable identifier.
     */
    public int tmpVarId() { return tmpVar++; }
}
