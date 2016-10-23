## Bytespresso

by Shigeru Chiba

### Overview

Bytespresso-C is a translator from Java bytecode into C or C-like languages such as CUDA.
It has been designed for offloading part of the execution of Java code onto external hardware
such as GPU, a PC cluster, and a supercomputer.  Since the target application domain is
high-performance computing, Bytespresso-C has not been designed for translating a Java
program largely exploiting object orientation.  The best platform for such a program
is the JVM.  Bytespresso rather focuses on parallel numerical computing written with arrays
but constructed on top of an object-oriented framework.

Bytespresso-C is similar to a JIT compiler.
It dynamically translates the bytecode of a specified method in the running program
(and the methods directly or indirectly invoked by that method).
The C code generated after the translation is compiled by an external compiler
and executed in a separate process.  JNI (Java Native Interface) is not used.
The resulting value is sent back to the Java code through the Unix pipe.
Since translation is done at runtime, some optimization techniques are applied
to the code generation.  They include constant propagation and devirtualization.

### Running examples

To compile the source code, include `javassist.jar` (and JUnit) in the class path.
The option `-Djdk.internal.lambda.dumpProxyClasses=./bin`
(if `./bin` is a directory included in the class path) must be given to the JVM.

Examples are in `src/test/array`.  `Nbody.java` is a program of N-body simulation.
When calling `main` in `Nbody`, it generates C code (`bytespresso.c`), comiles it,
and runs it.  The default compiler is `cc` and it is supposed to generate `a.out`
as executable binary.
To change the configuration, see the Javadoc comments
in `javassist.offload.javatoc.StandaloneDriver`.

The examples also include `NbodyMPI.java` and `NbodyOnGPU.java`.  The former one
runs with MPI and the latter one runs with CUDA.  See `VecMpiDSL.java` and
`VecCudaDSL.java`, which implement embedded DSLs for processing vectors.
They launch compilers through `javassist.offload.lib.MPIDriver` and `CudaDriver`. 


### How to translate and run a Java method

The packages the users should look at are:

* `javassist.offload`
* `javassist.offload.lib`
* `javassist.offload.javatoc`

The other packages are for the implementation of the translator.

The following code translates a lambda function passed to `DSL.run` 
into C and execute it:

    import javassist.offload.javatoc.DriverException;
    import javassist.offload.javatoc.StdDriver;
    import javassist.offload.lib.Util;
    
    public class DSL {
        public static void main(String[] args) throws Exception {
            DSL.run(() -> {
                hello("World");
            });
        }

        public static void hello(String m) {
            Util.print("Hello ").print(m).println();
        }

        static void run(Runnable code) throws DriverException {
            new StdDriver().invoke(code);
        }
    }

The `run` method above is used only for giving a good look to the
source code.  So the following version of `main` is equivalent to
the `main` method above:

        public static void main(String[] args) throws Exception {
            new StdDriver().invoke(() -> {
                hello("World");
            });
        }

`StdDriver` also provides `invoke` methods taking an instance of
`java.util.function.IntSupplier` or `DoubleSupplier`, which returns a resulting value.
They execute the given lambda function and return the result.

The code translated into a C program is 
the body of the function given to the `invoke` method
in `StdDriver`.  The `hello` method
and other methods called directly or indirectly from the function are also
translated into the C program.
`StdDriver` collects all but only necessary methods that will be invoked
while the function given to `invoke` is running.  They are translated and
included in the generated C program.

The program above can be compiled by a normal Java compiler
and run on the normal Java VM
although the JVM option shown in Section "Java Lambdas" is needed.
The Java program running on the JVM dynamically generates a source program in C,
compiles it, and executes the compiled binary.
Since the generated C program is compiled by an external C compiler, 
the external compiler (the `cc` command by default) has to be available.
The compilation command can be changed by calling `setCompiler` method
in `StdDriver`.

If the translated code is run in a distributed environment with MPI, `MPIDriver` should
be used instead of `StdDriver`.  It runs the translated code by `mpiexec` command, that is,
it runs multiple copies of the translated code on different distributed nodes.
The translated code is executed without communicating with the JVM where `MPIDriver` is
running.
So the return value from the translated code is not sent back from the invoke method
in `MPIDriver`.  It is discarded.

`MPIDriver` provides `compileOnly` method.  If this method is called, it translates a
specified method and generates the C code but it does not compile or execute the C code.
This option is convenient if the target hardware is a supercomputer, where the
executable code must be submitted to the job queue for execution and it is not
immediately executed.

### How to write a translated Java method

From the viewpoint of the semantics, the Java method specified for the translation
is executed in a separate new environment as if it is a remote method and executed
through Java RMI (Remote Method Invocation).  All the values of the static fields
referred to during the execution are copied to the new environment as well as the
arguments to the method.  The copying is "deep copy" at the translation time.
Any references do not point to objects in the original environment.
The updates in the new environment are never reflected on the original environment
after the execution.  Only the return value is copied back to the original environment.
Furthermore, the translation assumes that all the classes necessary for the execution
in the new environment are statically known.  No unknown new class will be loaded by reflection
API.  So the translator can statically find a "leaf class",
which is a class that is not inherited by any subclass.  This helps devirtualization.
Not only a final class but also a non-final class may be a leaf class.

Currently, the translated Java method must be subject to the following restrictions:

* Only conservative garbage collection is available if it is explicitly specified
  (see `doGarbageCollection()` in `javassist.offload.javatoc.StdDriver`).
  Otherwise, all objects must be explicitly deallocated.

* No exception is thrown.  `NullPointerException` and `ArrayIndexOutOfBoundsException` are
ignored and may cause a segmentation fault.  A `try-catch` statement cannot be used.  

* Standard Java API is not available.
    * Only `charAt` and `length` methods are available on a `String` object.  String concatenation
      by `+` is not available.
    * `System.out.println()` or `System.err.println()` are not available.
      Use `javassist.offload.lib.Util.print()`.

* The value of a field annotated with `@Final` will not change during the execution of the
translated method.  It can be updated at any time before the translation.
This annotation is used for constant propagation during the translation.  

### Interaction between Java and C

If the method translated into C calls a static method annotated with `@Remote`,
the call is translated into a remote method call.  The called method annotated
with `@Remote` is executed by the JVM where the `StdDriver` is running.  If
the `MPIDriver` is used for the translation, the `@Remote` method is executed
by the JVM launched on the local node (every MPI node launches a different
instance of the JVM).  Since the`@Remote` method runs on the JVM,
it can exploit all the features of Java, including the standard Java API,
exceptions, and garbage collection.  The `@Remote` method enables a "callback"
from C to Java.

In the current implementation, the types of the parameters and the return value of
the `@Remote` method must be a primitive type, an array type of primitive type,
or `java.lang.String`.  The other types are not supported.
This restriction is also applied to the return type of the method invoked by
the `StdDriver` or the `MPIDriver`.  The type must be a primitive type and so on.

### Using native language constructs available in C

To maximize the execution performance, native languages constructs of C are
available from the translated code.
For example, a `@Native` method can be easily implemented.

    @Native("fprintf(stdout, \"%d\", (int)v1); return 0;")
    public static Util print(int i) {
        System.err.print(i);
        return null;
    }

This method takes one integer parameter.
If this method is called on the JVM, the body of this method written in Java is executed.
On the other hand, if it is called from the C code generated after the translation,
the C code given as the argument to `@Native` is executed.
It is translated into the code similar to the following function in C:

    struct Util* Util_print(int v1) {
        fprintf(stdout, "%d", (int)v1);
        return 0;
    }

The first parameter to the function is `v1`, the second one is `v2`, ...
A primitive type in Java is translated into the corresponding primitive type in C.

Another kind of native method is a static method annotated with `@Foreign`.
The function body is not given to such a native method.  Bytespresso-C assumes that
there is a library function with the same name as the `@Foreign` method.
For example, `javassist.offload.lib.Unsafe.free` is a `@Foreign` method.
If it is called, the library function `free` in C is called.
Since garbage collection is not available, all the objects created by `new` in the
translated code must be deallocated by this `free` method after they become garbage.

Besides native methods, Bytespresso-C provides compile-time reflection or a compile-time
metaobject protocol.  A metaclass is specified by the `@Metaclass` annotation attached
to the class declaration.  For example, the metaclass of `javassist.offload.lib.MPI.Request`
class is `NativeClass`.  The `MPI.Request` class in Java implements a native type
`MPI_Request` in C.  The translation of the code related to `MPI.Request` is controlled
by the `NativeClass` metaclass.  The `MPI.Request` class is translated into a `struct` type
holding a value of type `MPI_Request`.  A pointer to that value can be obtained by
`toNativeBody` method in `javassist.offload.lib.Unsafe`.

Another example of class with a non-standard metaclass is `Float2Array` class
in `javassist.offload.lib`.  This class is translated into a two-dimensional `float` array in C,
i.e. `float a[][]`, if the array size is statically determined.
Bytespresso-C determines it by data-flow analysis.
Note that the use of multi-dimensional array often gives better chance of optimization
to a C compiler.
If a `Float2Array` object is constructed on the JVM and passed to the translated code
through an argument or a static field, the array size is statically determined and
the object is translated into a two-dimensional `float` array in C.
If the object is accessed through
a pointer but the data-flow analysis cannot statically determine which object that pointer
points to, then the `Float2Array` object is accessed as a one-dimensional array of `float`.

`ImmutableClass` is also a metaclass.  A class with this metaclass is an immutable class;
An instance of such a class is an immutable object.  An immutable class:

* contains only `final` fields.
* does not recursively contain a field of that immutable class.
* does not extend a superclass except `java.lang.Object` but may implement
an immutable interface.
* is not extended by a subclass.
* is not a subtype of `java.lang.Object`.
An immutable object cannot be assigned to a variable of type `java.lang.Object`.
* is not an element type of array that is passed between the JVM and the C code.

Because of these properties, an immutable object is passed by value.  It is always
allocated on stack memory and hence it does not have to be explicitly deallocated by
`free` method in `javassist.offload.lib.Util`.
The use of an immutable object is
a key technique to apply object inlining and devirtualization to the translated
program.  A drawback of immutable objects is that the whole object is copied
when they are assigned to a variable.

### Intrinsics

To be written.

### Offload onto a remote machine

The execution of a Java method can be offloaded onto a remote machine.
For example, if you want to use a remote machine named `playground`,
first write the following two shell scripts:

    # compile.sh
    scp bytespresso.c playground:
    ssh playground "source .profile; cc -O3 bytespresso.c"

    # run.sh
    ssh playground "source .profile; ./a.out"

Here `bytespresso.c` is the default name of the generated source file
(for CUDA, it is `bytespresso.cu`).
Then start the program using Bytespresso-C with the following VM arguments
to the Java command:

    -Dc.compiler=./compile.sh -Dc.exec=./run.sh

These arguments sets Java's system properties.

### Java Lambdas

If a Java program is written with lambdas, the following VM arguments has to be
passed to the Java command:

    -Djdk.internal.lambda.dumpProxyClasses=./bin

Here `./bin` is a folder name (or a directory name) included in the class path.

### Deep reification

The implementation of Bytespresso-C uses a component named deep reification
(this deep-reification component is called Bytespresso.  Bytespresso-C is the
name of a translator using this component).
It is a component for constructing an abstract syntax tree from Java bytecode.
It extracts all the methods including not only a given method but also the
methods directly or indirectly called by that given method.  Then it
constructs the abstract syntax trees of those methods.
It also collects all the classes that the methods refer to and
the objects accessed through static fields.

The component consists of three packages `javassist.offload`,
`javassist.offload.reify` and `javassist.offload.ast`.
The main class of the component is `javassist.offload.reify.Reifier`.

The following code is an example:

    CtMethod cm = ...
    Object[] args = { ... };
    Reifier reifier = new Reifier(cm, args);
    Reifier.Snapshot image = reifier.snap();

    System.out.println(" -- types --");
    for (TypeDef t: image.classTable.allTypes())
        System.out.println(t);

    System.out.println(" -- functions --");
    System.out.println(image.function);
    for (JMethod f: image.functionTable)
        if (f != image.function)
            System.out.println(f);

See `reifyAndPrint` method in `javassist.offload.reify.Reifier`
for the complete code.  Also see `main` in
`javassist.offload.reify.Reifier`.
`CtMethod` is a class provided by Javassist bytecode engineering
toolkit (see `www.javassist.org`).  It is similar to
`java.lang.reflect.Method` but enables an access to bytecode.

`reifier.snap()` extracts a snapshot of the current execution
environment that is necessary to invoke `cm` with arguments `args`.
It is a self-contained minimum part of the environment.
It reads the bytecode of the method specified
by `cm` and constructs the abstract syntax tree for the bytecode
under the assumption that the actual arguments are `args`.
The returned value `image` contains not only the abstract syntax
tree of `cm` but also the tress of the other methods directly
or indirectly called by `cm`.  It also contains the list of
the classes and objects used for the execution of `cm`.

The abstract syntax tree supports Visitor pattern.  All the
node classes provide `accept` method.  They also provide
methods for accessing and changing child nodes. 

Since `snap` receives the actual arguments passed to `cm`,
it specializes the method bodies by propagating the arguments
and also referring to their final fields and static final
fields.  Primitive type values are ignored for specialization.

As for dynamic method dispatch, `snap` collects all possible
target classes on each call.  A method call is represented
by a `Call` node, which has `calledFunction` method.
If there are multiple target classes, `calledFunction`
returns a `Dispatcher` object.  If it is determined that
there is only a single target class, for example, by
specialization, `calledFunction` returns a `Function` object,
which represents a single method body. 

#### Extending deep reification

The component for deep reification can be extended.
By giving a constructor, a custom class-table object,
a custom function-table object, and  
a factory object for tree nodes,
`Reifier` is extended to construct customized
abstract syntax trees.

`Reifier` uses a `TypeDef` object for representing
a type.  Since `TypeDef` is an interface,
the developers can define a new class implementing `TypeDef`
and  a class table for containing the instances of that class. 
If this class table is given to the constructor of
`Reifier`, it is used when constructing abstract syntax
trees.  The `TypeDef` objects included in the tree will
be instances of that new class implementing `TypeDef`.

A factory object given to a constructor of `Reifier` is
an instance of `FunctionMetaclass` or its subclass.
It is a factory of objects representing an abstract
syntax tree for method.  The developers can give
a custom factory object for instantiating an appropriate
class. 
Note that the classes of objects representing an abstract
syntax tree for method are also specified by the
`@Metaclass` annotation attached to the method.
The factory object is used as the default option
when no `@Metaclass` is attached to the method.


### Developers' notes

`src/test/javassist/offload/test/Runner.java` runs tests.

### What is a metaclass?

To be written.

### Publications

Shigeru Chiba, YungYu Zhuang, Maximilian Scherr, "Deeply Reifying
Running Code for Constructing a Domain-Specific Language",
Proc. of the 13th International Conference on Principles and Practices
of Programming on the Java Platform: Virtual Machines, Languages,
and Tools (PPPJ'16), Article No. 1, August 2016.

Shigeru Chiba, YungYu Zhuang, Maximilian Scherr, "A Design of Deep Reification",
MASS 2016 - Workshop on Modularity Across the System Stack, MODULARITY Companion'16,
pp.168-171, March, 2016.

### Copyright notices

Copyright (C) 2016- by Shigeru Chiba, All rights reserved.

This software is distributed under the MPL/LGPL/Apache triple license
as Javassist is.
