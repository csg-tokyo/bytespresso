// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc;

import java.util.ArrayList;

import javassist.offload.clang.HeapMemory;

/**
 * An interface to compiler driver.
 * It defines methods for obtaining the configuration
 * of the driver.
 */
public interface Settings {
    /**
     * Returns true if the target is a little endian system.
     */
    boolean isLittleEndian();

    /**
     * Returns the name of the source file.
     */
    String sourceFile();

    /**
     * Returns the compile command.
     */
    String compileCommand();

    /**
     * Returns the execution command.
     */
    String execCommand();

    /**
     * Returns a preamble appended the generated code.
     */
    String preamble();

    /**
     * Returns prologue code of the main function.
     *
     * @return      a non-null character string.
     */
    String prologue();

    /**
     * Returns epilogue code of the main function.
     *
     * @return      a non-null character string.
     */
    String epilogue();

    /**
     * Returns a {@code HeapMemory} object used during transformation.
     */
    HeapMemory heapMemory();

    /**
     * Returns objects that should be adjacently allocated.
     * This is a hint for memory allocation. 
     */
    ArrayList<Object> adjacentObjects();
}
