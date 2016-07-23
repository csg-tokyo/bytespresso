// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

/**
 * A metaclass for controlling the representation in C
 * of Java objects.  It is used to transform a Java object
 * into a global variable in C.
 */
public class HeapMemory {
    private final boolean useGC;

    /**
     * Constructs a metaclass.
     *
     * @param gc    true if conservative garbage collection is turned on.
     */
    public HeapMemory(boolean gc) { useGC = gc; }

    /**
     * Returns the name of the memory allocation function.
     * No pointers must be stored in the returned memory block.
     */
    public String malloc() {
        if (useGC)
            return "GC_malloc_atomic";
        else
            return "malloc";
    }

    /**
     * Returns the name of the contiguous memory allocation function.
     */
    public String calloc() {
        if (useGC)
            return "GC_calloc_obj";
        else
            return "calloc";
    }

    /**
     * Returns the name of the memory deallocation function.
     */
    public String free() {
        if (useGC)
            return "GC_free";
        else
            return "free";
    }

    /**
     * Generates the prototype declaration of a variable.
     * 
     * @param obj           the object stored in the variable.
     * @param type          null if the type is a primitive array.
     * @param typeName      the type name in C.
     * @param gvarName      the global variable name in C.
     */
    public void prototypeCode(CodeGen gen, Object obj, CTypeDef type, String typeName, String gvarName) {
        gen.append("static ").append(typeName).append(' ')
           .append(gvarName).append(";\n");
    }

    /**
     * Generates the declaration of a variable.
     * It does not generate a semicolon at the end.
     *
     * @param obj           the object stored in the variable.
     * @param type          null if the type is a primitive array.
     * @param typeName      the type name in C.
     * @param gvarName      the global variable name in C.
     */
    public void declarationCode(CodeGen gen, Object obj, CTypeDef type, String typeName, String gvarName) {
        gen.append("static ").append(typeName).append(' ').append(gvarName);
    }

    /**
     * Returns true if the target language follows C++ syntax.
     * For example, CUDA is a variant of C++.
     * The implementation in this class is to return {@code false}.
     */
    public boolean portableInitialization() { return false; }

    /**
     * Records a given statement as the code initializing global variables.
     * The code will be executed when the program starts.
     *
     * @param code      a statement in C.
     */
    public void addInitializer(String code) {
        throw new RuntimeException("not implemented");
    }

    /**
     * Writes the recorded statements as the code initializing global variables.
     */
    public void initializer(CodeGen gen) {}

    /**
     * A metaclass to perform portable initialization of {@code struct/union}.
     */
    public static class PortableHeapMemory extends HeapMemory {
        public PortableHeapMemory(boolean gc) { super(gc); }

        @Override public boolean portableInitialization() { return true; }

        private java.util.ArrayList<String> initializers = new java.util.ArrayList<String>();

        @Override public void addInitializer(String code) {
            initializers.add(code);
        }

        @Override public void initializer(CodeGen gen) {
            for (String s: initializers)
                gen.append(s);
        }
    }
}
