// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.lib;

import java.lang.annotation.Annotation;
import java.util.HashMap;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.offload.Code;
import javassist.offload.Final;
import javassist.offload.Inline;
import javassist.offload.Intrinsic;
import javassist.offload.Metaclass;
import javassist.offload.Native;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Call;
import javassist.offload.ast.Dispatcher;
import javassist.offload.ast.Function;
import javassist.offload.ast.FunctionMetaclass;
import javassist.offload.ast.GetField;
import javassist.offload.ast.TypeDef;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.NativeFunction;
import javassist.offload.ast.VisitorException;
import javassist.offload.clang.ArrayDef;
import javassist.offload.clang.CDispatcher;
import javassist.offload.clang.CFunction;
import javassist.offload.clang.CFunctionMetaclass;
import javassist.offload.clang.CodeGen;
import javassist.offload.clang.HeapMemory;
import javassist.offload.clang.ImmutableNativeClass;
import javassist.offload.clang.NativeCFunction;
import javassist.offload.clang.NativeClass;
import javassist.offload.clang.NativeMultiArrayClass;
import javassist.offload.clang.CTypeDef;
import javassist.offload.javatoc.CCode;
import javassist.offload.javatoc.impl.JavaObjectToC;

/**
 * CUDA-related classes.  Objects are shared among CPU and GPU
 * by using unified memory.
 */
public class CUDA {
    /**
     * {@code dim3} type.  It is not available in GPU code.
     */
    @Metaclass(type=ImmutableNativeClass.class, arg = "dim3")
    public static final class Dim3 {
        private final int x, y, z;

        @Native("dim3 d; d.x = v2; d.y = v3; d.z = v4; return d;")
        public Dim3(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Native("dim3 d; d.x = v2; d.y = v3; d.z = 1; return d;")
        public Dim3(int x, int y) {
            this.x = x;
            this.y = y;
            this.z = 1;
        }

        @Native("dim3 d; d.x = v2; d.y = d.z = 1; return d;")
        public Dim3(int x) {
            this.x = x;
            this.y = 1;
            this.z = 1;
        }

        @Native("return v1.x;")
        public int x() { return this.x; }

        @Native("return v1.y;")
        public int y() { return this.y; }

        @Native("return v1.z;")
        public int z() { return this.z; }

    }

    /**
     * {@code cudaDeviceReset()}
     */
    @Native("cudaDeviceReset();")
    public static void deviceReset() {}

    /**
     * Allocates a {@code float} array by {@code cudaMalloc()}.
     *
     * @param size      the number of the elemetns.
     */
    @Native("float* ptr; cudaMalloc(&ptr, sizeof(float) * ("
            + ArrayDef.ARRAY_HEADER_SIZE + " + v1)); return ptr;")
    public static float[] mallocFloat(int size) {
        return new float[size];
    }

    /**
     * Allocates a {@code float} array by {@code cudaMalloc3D()}.
     *
     * @param xsize      the number of the elements in x-direction.
     * @param ysize      the number of the elements in y-direction.
     * @param zsize      the number of the elements in z-direction.
     */
    @Native("cudaPitchedPtr* ptr = (cudaPitchedPtr*)v1; cudaMalloc3D(ptr, make_cudaExtent(sizeof(float) * v2, v3, v4));")
    public static void mallocPitchedFloat(@NativePtr PitchedPtr devPitchedPtr, int xsize, int ysize, int zsize) {
    }

    /**
     * {@code cudaMemcpy()} from host to device.
     */
    @Native("cudaMemcpy(v1, v2, sizeof(float) * (v3 + " + ArrayDef.ARRAY_HEADER_SIZE + "), cudaMemcpyHostToDevice);")
    public static void memcpyToDevice(float[] dest, float[] src, int len) {
        System.arraycopy(src, 0, dest, 0, len);
    }

    /**
     * {@code cudaMemcpy()} from device to host.
     */
    @Native("cudaMemcpy(v1, v2, sizeof(float) * (v3 + " + ArrayDef.ARRAY_HEADER_SIZE + "), cudaMemcpyDeviceToHost);")
    public static void memcpyToHost(float[] dest, float[] src, int len) {
        System.arraycopy(src, 0, dest, 0, len);
    }

    /**
     * {@code cudaFree()}.
     */
    @Native("cudaFree(v1);")
    public static void free(Object obj) {}
    
    /**
     * {@code cudaSetDevice()}
     */
    @Native("cudaSetDevice(v1);")
    public static void setDevice(int dev) {}

    /**
     * {@code cudaDeviceSynchronize()}
     */
    @Native("cudaDeviceSynchronize();")
    public static void deviceSynchronize() {}

    /**
     * {@code __syncthreads()}.
     */
    @Intrinsic public static void syncthreads() {
        CCode.make("__syncthreads()").emit();
    }

    /**
     * {@code __ldg()}.
     *
     * @param v     a float value.
     */
    @Intrinsic public static float ldgF(Object v) {
        if (CCode.inTranslation()) {
            Code ptr = (Code)v;
            CCode.make("__ldg(&(").add(ptr).add("))").emit();
            return 0;
        }
        else
            return (float)v;
    }

    /**
     * {@code gridDim}.
     */
    public static class GridDim {
        @Intrinsic public static int x() {
            CCode.make("gridDim.x").emit();
            return 0;
        }

        @Intrinsic public static int y() {
            CCode.make("gridDim.y").emit();
            return 0;
        }
    }

    /**
     * {@code blockDim}.
     */
    public static class BlockDim {
        @Intrinsic public static int x() {
            CCode.make("blockDim.x").emit();
            return 0;
        }

        @Intrinsic public static int y() {
            CCode.make("blockDim.y").emit();
            return 0;
        }

        @Intrinsic public static int z() {
            CCode.make("blockDim.z").emit();
            return 0;
        }
    }

    /**
     * {@code blockIdx}.
     */
    public static class BlockIdx {
        @Intrinsic public static int x() {
            CCode.make("blockIdx.x").emit();
            return 0;
        }

        @Intrinsic public static int y() {
            CCode.make("blockIdx.y").emit();
            return 0;
        }

        @Intrinsic public static int z() {
            CCode.make("blockIdx.z").emit();
            return 0;
        }
    }

    /**
     * {@code threadIdx}.
     */
    public static class ThreadIdx {
        @Intrinsic public static int x() {
            CCode.make("threadIdx.x").emit();
            return 0;
        }

        @Intrinsic public static int y() {
            CCode.make("threadIdx.y").emit();
            return 0;
        }

        @Intrinsic public static int z() {
            CCode.make("threadIdx.z").emit();
            return 0;
        }
    }

    /**
     * A metaclass for {@code __global__} functions.
     *
     * @see GlobalFunc
     */
    public static class Global extends CFunctionMetaclass {
        public static final FunctionMetaclass instance = new Global();

        private Global() {}

        /**
         * Accepts only {@code __global__} functions.
         */
        public boolean accepts(Function f) {
            return f.metaclass() == this;
        }

        /**
         * Makes a {@code __global__} function.
         */
        public Function make(CtClass returnType, CtBehavior method, String name, JVariable[] parameters,
                             boolean isStatic, String arg, String[] args, Class<?> companion)
        {
            return new GlobalFunc(returnType, method, name, parameters, isStatic);
        }

        public Dispatcher makeDispatcher(CtMethod cm, String fname)
                throws NotFoundException
        {
            throw new RuntimeException("a polymorphic __global__ function is not supported.");
        }

        /**
         * Makes a native {@code __global__} function.
         */
        public NativeFunction makeNative(CtClass returnType, CtBehavior method, String name,
                                         JVariable[] parameters, String body, boolean isStatic)
        {
            throw new RuntimeException("a native __global__ function is not supported.");
        }
    }

    /**
     * A metaclass for {@code __device__} functions.
     */
    public static class Device extends CFunctionMetaclass {
        public static final FunctionMetaclass instance = new Device();

        private Device() {}

        /**
         * Accepts only {@code __device__} functions.
         */
        public boolean accepts(Function f) {
            return f.metaclass() == this;
        }

        /**
         * Makes a {@code __device__} function.
         */
        public Function make(CtClass returnType, CtBehavior method, String name, JVariable[] parameters,
                             boolean isStatic, String arg, String[] args, Class<?> companion)
        {
            return new DeviceFunc(returnType, method, name, parameters, isStatic);
        }

        /**
         * Makes a {@code __device__} dispatcher.
         */
        public Dispatcher makeDispatcher(CtMethod cm, String fname)
                throws NotFoundException
        {
            return new DeviceDispatcher(cm, fname);
        }

        /**
         * Makes a native {@code __device__} function.
         */
        public NativeFunction makeNative(CtClass returnType, CtBehavior method, String name,
                                         JVariable[] parameters, String body, boolean isStatic)
        {
            return new NativeDevFunc(returnType, method, name, parameters, body, isStatic);
        }
    }

    /**
     * {@code __global__} functions.
     * The first two parameters specify the execution configuration of the CUDA kernel function.
     * The first parameter specifies the number of blocks while the second does the number of
     * threads per block. They must be of {@code int} type or {@code dim} type.  These two
     * parameters are not available in the body of the {@code __global__} function.
     */
    public static class GlobalFunc extends CFunction {
        /**
         * Constructs a {@code __global__} function.
         */
        protected GlobalFunc(CtClass type, CtBehavior method, String fname, JVariable[] params, boolean isStatic) {
            super(type, method, fname, params, isStatic);
            if (type != CtClass.voidType)
                throw new RuntimeException("CUDA.Global must return void: " + fname);

            if (!isStatic || params.length < 2)
                throw new RuntimeException("CUDA.Global must be static and take parameters for <<<g,b>>>: " + fname);

            checkParams(fname, params[0]);
            checkParams(fname, params[1]);
        }

        private static final String dim3Name = CUDA.Dim3.class.getName();

        private static void checkParams(String fname, JVariable v) {
            CtClass t = v.type();
            if (t != CtClass.intType && !t.getName().equals(dim3Name))
                throw new RuntimeException("The first two parameters to CUDA.Global must be int or dim3: " + fname);
        }

        private static Inline noInline = new Inline() {
            public Class<? extends Annotation> annotationType() {
                return getClass();
            }

            public boolean value() { return false; }
            public boolean object() { return false; }
        };

        /**
         * Returns {@code @Inline(false)}.  {@code __global__} functions are not
         * inlined even if {@code @Inline} is specified.
         */
        public Inline inline() { return noInline; }

        /**
         * A function called from a {@code __global__} function must be
         * a {@code __devie__} function. 
         */
        @Override public FunctionMetaclass metaclass() {
            return Device.instance;
        }

        private boolean lastValueOfwithinDevice;

        /**
         * Changes the value of {@link CUDA#withinDevice}.
         */
        public void traversalBegins() {
            lastValueOfwithinDevice = withinDevice;
            withinDevice = true;
        }

        /**
         * Restores the value of {@link CUDA#withinDevice}.
         */
        public void traversalEnds() {
            withinDevice = lastValueOfwithinDevice;
        }

        /**
         * Generates a function implementation.
         */
        public void code(CodeGen gen) throws VisitorException {
            gen.append("__global__ ");
            super.code(gen);
        }

        /**
         * Generates a function prototype.
         */
        public void prototype(CodeGen gen) throws VisitorException {
            gen.append("__global__ ");
            super.prototype(gen);
        }

        /**
         * Generates the code representing the parameter list.
         *
         * @param isPrototype       if it's true, the parameter name is not
         *                          written. 
         */
        public void parametersCode(CodeGen gen, boolean isPrototype) throws VisitorException {
            JVariable[] params = parameters();
            if (params.length < 2)
                throw new RuntimeException("bad __global__ function: " + this.name());

            int gridPos = isStatic() ? 0 : 1;
            boolean first = true;
            for (int i = 0; i < params.length; i++)
                if (i != gridPos && i != gridPos + 1) {
                    if (first)
                        first = false;
                    else
                        gen.append(", ");

                    if (isPrototype)
                        gen.append(gen.typeName(params[i].type()));
                    else
                        CTypeDef.varDeclaration(gen, false, params[i]);
                }
        }

        /**
         * Generates the code of an expression for calling this
         * function.
         *
         * @param gen       the generator.
         * @param expr      the call expression.
         */
        public void callerCode(CodeGen gen, Call expr) throws VisitorException {
            int gridPos = isStatic() ? 0 : 1;
            ASTree[] args = expr.arguments();
            gen.append(name());
            gen.append("<<<").append(args[gridPos]).append(", ").append(args[gridPos + 1]).append(">>>");
            gen.append('(');
            JVariable[] params = parameters();
            boolean first = true;
            for (int i = 0; i < params.length; i++)
                if (i != gridPos && i != gridPos + 1) {
                    if (first)
                        first = false;
                    else
                        gen.append(", ");

                    int k = parameter(i);
                    ASTree value;
                    if (k == Function.ParameterMap.TARGET)
                        value = expr.target();
                    else
                        value = args[k];

                    CTypeDef.doCastOnValue(gen, params[i].type(), value);
                }

            gen.append(')');
        }
    }

    /**
     * {@code __device__} functions.
     */
    public static class DeviceFunc extends CFunction {
        /**
         * Constructs a {@code __device__} function.
         */
        protected DeviceFunc(CtClass type, CtBehavior method, String fname, JVariable[] params, boolean isStatic) {
            super(type, method, fname, params, isStatic);
        }

        /**
         * A function called from a {@code __device__} function must be
         * a {@code __devie__} function. 
         */
        @Override public FunctionMetaclass metaclass() {
            return Device.instance;
        }

        private boolean lastValueOfwithinDevice;

        /**
         * Changes the value of {@link CUDA#withinDevice}.
         */
        public void traversalBegins() {
            lastValueOfwithinDevice = withinDevice;
            withinDevice = true;
        }

        /**
         * Restores the value of {@link CUDA#withinDevice}.
         */
        public void traversalEnds() {
            withinDevice = lastValueOfwithinDevice;
        }

        /**
         * Generates a function implementation.
         */
        public void code(CodeGen gen) throws VisitorException {
            gen.append("__device__ ");
            super.code(gen);
        }

        /**
         * Generates a function prototype.
         */
        public void prototype(CodeGen out) throws VisitorException {
            out.append("__device__ ");
            super.prototype(out);
        }
    }

    /**
     * True if the body of a device or global function is processed.
     */
    @Final public static boolean withinDevice = false;

    /**
     * A dispatcher with {@code __device__}.
     */
    public static class DeviceDispatcher extends CDispatcher {
        protected DeviceDispatcher(CtMethod cm, String fname) throws NotFoundException {
            super(cm, fname);
        }

        /**
         * A function called from a {@code __device__} function must be
         * a {@code __devie__} function. 
         */
        @Override public FunctionMetaclass metaclass() {
            return Device.instance;
        }

        @Override public void add(TypeDef t, Function f) {
            if (f instanceof DeviceFunc)
                super.add(t, f);
        }

        public void prototype(CodeGen gen) throws VisitorException {
            if (!hasSingleBody()) {
                gen.append("__device__ ");
                super.prototype(gen);
            }
        }

        public void code(CodeGen gen) throws VisitorException {
            if (!hasSingleBody()) {
                gen.append("__device__ ");
                super.code(gen);
            }
        }

        protected void errorInBodyCode(CodeGen gen) {
            gen.append("  exit(").append(Util.ERR_DISPATCH).append(");\n");
        }
    }

    /**
     * A native function with {@code __device__}.
     */
    public static class NativeDevFunc extends NativeCFunction {
        protected NativeDevFunc(CtClass type, CtBehavior method, String fname, JVariable[] params,
                String body, boolean isStatic) {
            super(type, method, fname, params, body, isStatic);
        }

        /**
         * A function called from a {@code __device__} function must be
         * a {@code __devie__} function. 
         */
        @Override public FunctionMetaclass metaclass() {
            return Device.instance;
        }

        public void code(CodeGen gen) throws VisitorException {
            gen.append("__device__ ");
            super.code(gen);
        }

        public void prototype(CodeGen gen) throws VisitorException {
            gen.append("__device__ ");
            super.prototype(gen);
        }
    }

    /**
     * An abstract metaclass.
     * It checks the subclasses are used in CUDA.
     */
    static abstract class CudaNativeArrayClass extends NativeMultiArrayClass {
        public CudaNativeArrayClass(CtClass cc, CtField[] f, int uid, String arg, String[] args, Class<?> companion) {
            super(cc, f, uid, arg, args, companion);
        }

        public void invokeMethod(CodeGen gen, Call expr) throws VisitorException {
            String methodName = expr.methodName();
            if (INIT_DATA.equals(methodName))
                throw new RuntimeException(type().getName()
                                           + " has to be created at the Java side.");
            else
                super.invokeMethod(gen, expr);
        }

        protected CudaMemory cudaMemory(CodeGen gen) {
            HeapMemory heap = gen.heapMemory();
            if (heap instanceof CudaMemory)
                return (CudaMemory)heap;
            else
                throw new RuntimeException(type().getName() + " is available only in CUDA");
        }
    }

    /**
     * A metaclass for native array classes with {@code __shared__}.
     */
    public static class Shared extends CudaNativeArrayClass {
        public Shared(CtClass cc, CtField[] f, int uid, String arg, String[] args, Class<?> companion) {
            super(cc, f, uid, arg, args, companion);
        }

        @Override
        protected void declareArrayHead(Object obj, CodeGen gen, Class<?> klass, String gvarName2) {
            cudaMemory(gen).sharedDeclarationCode(gen, this, typeName, gvarName2);
        }
    }

    /**
     * A metaclass for native array classes with {@code __device__}.
     */
    public static class DeviceArray extends CudaNativeArrayClass {
        static final String DPTR_GETTER = "toDevPtr";
        static final String DPTR_FIELD = "devPtr";
        static final String DPTR_HOST_PTR = "_host";

        public DeviceArray(CtClass cc, CtField[] f, int uid, String arg, String[] args, Class<?> companion) {
            super(cc, f, uid, arg, args, companion);
        }

        @Override
        public boolean isMacro(Call expr) throws NotFoundException {
            String methodName = expr.methodName();
            if (DPTR_GETTER.equals(methodName))
                return true;
            else
                return super.isMacro(expr);
        }

        @Override
        public void invokeMethod(CodeGen gen, Call expr) throws VisitorException {
            String methodName = expr.methodName();
            if (DPTR_GETTER.equals(methodName)) {
                GetField gf = new GetField(expr.targetType(), DPTR_FIELD,
                                           expr.returnType(), false, expr.target());
                gen.append(gf);
            }
            else
                super.invokeMethod(gen, expr);
        }

        @Override
        protected void declareArrayHead(Object obj, CodeGen gen, Class<?> klass, String gvarName2)
            throws VisitorException
        {
            Object array = JavaObjectToC.getFieldValue(obj, klass, DPTR_FIELD, false);
            if (array == null)
                throw new VisitorException("not found field: " + klass.getName() + "." + DPTR_FIELD);

            String gvarName3 = gvarName2 + DPTR_HOST_PTR;
            String init = "cudaGetSymbolAddress((void**)&" + gvarName3 + ", " + gvarName2 + ");\n";
            CudaMemory cudaMem = cudaMemory(gen);
            cudaMem.addInitializer(init);
            gen.recordGivenObject(array, gvarName3);
            gen.append(typeName + "*").append(' ').append(gvarName3).append(";\n");

            cudaMemory(gen).deviceDeclarationCode(gen, this, typeName, gvarName2);
        }
    }

    /**
     * A {@code float} array with {@code __shared__}.
     * An instance of this class has to be referred to through a {@code static final}
     * field or a {@code static} field with {@code Final}.
     */
    @Metaclass(type=Shared.class, arg="float", args = { "size" })
    public static class SharedFloatArray {
        public final int size;
        private float[] data;

        /**
         * Constructs an array.
         *
         * @param s     the size.
         */
        public SharedFloatArray(int s) {
            size = s;
            initData(false);
        }

        /**
         * Constructs an array.
         *
         * @param s     the size.
         * @param notUsedInJava       if true, the memory is not allocated at the Java side.
         */
        public SharedFloatArray(int s, boolean notUsedInJava) {
            size = s;
            initData(notUsedInJava);
        }

        private void initData(boolean emptyInJava) {
            if (emptyInJava)
                data = new float[0];
            else
                data = new float[size];
        }

        /**
         * Gets the value of the element at [i].
         * It is available only in C
         * if the memory is not allocated at the Java side.
         */
        public float get(int i) { return data[i]; }

        /**
         * Sets the value of the element at [i] to v.
         * It is available only in C
         * if the memory is not allocated at the Java side.
         */
        public void set(int i, float v) { data[i] = v; }
    }

    /**
     * A two-dimensional {@code float} array with {@code __shared__}.
     * An instance of this class has to be referred to through a {@code static final}
     * field or a {@code static} field with {@code Final}.
     */
    @Metaclass(type=Shared.class, arg="float", args = { "xsize", "ysize" })
    public static class SharedFloatArray2D extends FloatArray2D {
        /**
         * Constructs an array.
         *
         * @param s     the size.
         */
        public SharedFloatArray2D(int x, int y) {
            super(x, y);
        }

        /**
         * Constructs an array.
         *
         * @param s     the size.
         * @param notUsedInJava       if true, the memory is not allocated at the Java side.
         */
        public SharedFloatArray2D(int x, int y, boolean notUsedInJava) {
            super(x, y, notUsedInJava);
        }
    }

    /**
     * A two-dimensional {@code float} array with {@code __device__}.
     * An instance of this class has to be referred to through a {@code static final}
     * field or a {@code static} field with {@code Final}.
     */
    @Metaclass(type=DeviceArray.class, arg="float", args = { "xsize", "ysize" })
    public static class DeviceFloatArray2D extends FloatArray2D {
        // a pointer obtained by cudaGetSymbolAddress()
        final float[] devPtr = new float[0];

        /**
         * Constructs an array.
         *
         * @param x     the size in the first dimension.
         * @param y     the size in the second dimension.
         */
        public DeviceFloatArray2D(int x, int y) {
            super(x, y);
        }

        /**
         * Constructs an array.
         *
         * @param x     the size in the first dimension.
         * @param y     the size in the second dimension.
         * @param notUsedInJava       if true, the memory is not allocated at the Java side.
         */
        public DeviceFloatArray2D(int x, int y, boolean notUsedInJava) {
            super(x, y, notUsedInJava);
        }

        private float[] toDevPtr() { return devPtr; }

        /**
         * Copies the array elements from host memory.
         *
         * @param a     host memory.
         */
        public void copyFrom(FloatArray2D a) {
            memcpyToDevice(toDevPtr(), a.toCArray(), xsize * ysize);
        }

        /**
         * Copies the array elements to host memory.
         *
         * @param a     host memory.
         */
        public void copyTo(FloatArray2D a) {
            memcpyToHost(a.toCArray(), toDevPtr(), xsize * ysize);
        }

        @Native("cudaMemcpy(v1, v2, sizeof(float) * v3, cudaMemcpyDeviceToHost);")
        private static void memcpyToHost(float[] dest, float[] src, int size){
            System.arraycopy(src, 0, dest, 0, size);
        }

        @Native("cudaMemcpy(v1, v2, sizeof(float) * v3, cudaMemcpyHostToDevice);")
        private static void memcpyToDevice(float[] dest, float[] src, int size){
            System.arraycopy(src, 0, dest, 0, size);
        }
    }

    /**
     * A three-dimensional {@code float} array with {@code __device__}.
     * An instance of this class has to be referred to through a {@code static final}
     * field or a {@code static} field with {@code Final}.
     */
    @Metaclass(type=DeviceArray.class, arg="float", args = { "xsize", "ysize", "zsize" })
    public static class DeviceFloatArray3D extends FloatArray3D {
        // a pointer obtained by cudaGetSymbolAddress()
        final float[] devPtr = new float[0];

        /**
         * Constructs an array.
         *
         * @param x     the size in the first dimension.
         * @param y     the size in the second dimension.
         * @param z     the size in the third dimension.
         */
        public DeviceFloatArray3D(int x, int y, int z) {
            super(x, y, z);
        }

        /**
         * Constructs an array.
         *
         * @param x     the size in the first dimension.
         * @param y     the size in the second dimension.
         * @param z     the size in the third dimension.
         * @param notUsedInJava       if true, the memory is not allocated at the Java side.
         */
        public DeviceFloatArray3D(int x, int y, int z, boolean notUsedInJava) {
            super(x, y, z, notUsedInJava);
        }

        private float[] toDevPtr() { return devPtr; }

        /**
         * Copies the array elements to host memory.
         *
         * @param a     host memory.
         */
        public void copyFrom(FloatArray3D a) {
            memcpyToDevice(toDevPtr(), a.toCArray(), xsize * ysize * zsize);
        }

        public void copyFrom(FloatArray3D a, int x, int y, int z, int size) {
            memcpyToDevice(toDevPtr(), a.toCArray(), a.offset(x, y, z), size);
        }

        /**
         * Copies the array elements from host memory.
         *
         * @param a     host memory.
         */
        public void copyTo(FloatArray3D a) {
            memcpyToHost(a.toCArray(), toDevPtr(), xsize * ysize * zsize);
        }

        public void copyTo(FloatArray3D a, int x, int y, int z, int size) {
            memcpyToHost(a.toCArray(), toDevPtr(), a.offset(x, y, z), size);
        }

        @Native("cudaMemcpy(v1, v2, sizeof(float) * v3, cudaMemcpyDeviceToHost);")
        private static void memcpyToHost(float[] dest, float[] src, int size){
            System.arraycopy(src, 0, dest, 0, size);
        }

        @Native("cudaMemcpy(v1 + v3, v2 + v3, sizeof(float) * v4, cudaMemcpyDeviceToHost);")
        private static void memcpyToHost(float[] dest, float[] src, int offset, int size){
            System.arraycopy(src, offset, dest, offset, size);
        }

        @Native("cudaMemcpy(v1, v2, sizeof(float) * v3, cudaMemcpyHostToDevice);")
        private static void memcpyToDevice(float[] dest, float[] src, int size){
            System.arraycopy(src, 0, dest, 0, size);
        }

        @Native("cudaMemcpy(v1 + v3, v2 + v3, sizeof(float) * v4, cudaMemcpyHostToDevice);")
        private static void memcpyToDevice(float[] dest, float[] src, int offset, int size){
            System.arraycopy(src, offset, dest, offset, size);
        }
    }

    /**
     * A three-dimensional {@code float} array with {@code __device__}.
     * An instance of this class has to be referred to through a {@code static final}
     * field or a {@code static} field with {@code Final}.
     */
    public static class PitchedDeviceFloatArray3D extends FloatArray3D {
        final PitchedPtr devPitchedPtr = new PitchedPtr();

        /**
         * Constructs an array.
         *
         * @param x     the size in the first dimension.
         * @param y     the size in the second dimension.
         * @param z     the size in the third dimension.
         */
        public PitchedDeviceFloatArray3D(int x, int y, int z) {
            super(x, y, z);
        }

        /**
         * Constructs an array.
         *
         * @param x     the size in the first dimension.
         * @param y     the size in the second dimension.
         * @param z     the size in the third dimension.
         * @param notUsedInJava       if true, the memory is not allocated at the Java side.
         */
        public PitchedDeviceFloatArray3D(int x, int y, int z, boolean notUsedInJava) {
            super(x, y, z, notUsedInJava);
        }

        @Override public void allocData() {
            mallocPitchedFloat(Unsafe.toNativeBody(devPitchedPtr), xsize, ysize, zsize);
        }

        public void free() {
            devPitchedPtr.free();
        }

        @Override public float get(int i, int j, int k) {
            return get(Unsafe.toNativeBody(devPitchedPtr), ysize, i, j, k);
        }

        @Native("char* devPtr = (char*)((cudaPitchedPtr*)v1)->ptr;"
                + "size_t pitch = ((cudaPitchedPtr*)v1)->pitch;"
                + "size_t slicePitch = pitch * v2;"
                + "char* slice = devPtr + v3 * slicePitch;"
                + "float* row = (float*)(slice + v4 * pitch);"
                + "return row[v5];")
        private static float get(@NativePtr PitchedPtr devPtr, int ysize, int i, int j, int k) { return 0; }


        @Override public void set(int i, int j, int k, float v) {
            set(Unsafe.toNativeBody(devPitchedPtr), ysize, i, j, k, v);
        }

        @Native("char* devPtr = (char*)((cudaPitchedPtr*)v1)->ptr;"
                + "size_t pitch = ((cudaPitchedPtr*)v1)->pitch;"
                + "size_t slicePitch = pitch * v2;"
                + "char* slice = devPtr + v3 * slicePitch;"
                + "float* row = (float*)(slice + v4 * pitch);"
                + "row[v5] = v6;")
        private static void set(@NativePtr PitchedPtr devPtr, int ysize, int i, int j, int k, float v) { }

        /**
         * Copies the array elements to host memory.
         *
         * @param a     host memory.
         */
        public void copyFrom(FloatArray3D a) {
            memcpyToDevice3D(Unsafe.toNativeBody(devPitchedPtr), a.toCArray(), xsize, ysize, zsize);
        }

        public void copyFrom(FloatArray3D a, int xoffset, int yoffset, int zoffset, int xlen, int ylen, int zlen) {
            memcpyToDevice3D(Unsafe.toNativeBody(devPitchedPtr), a.toCArray(), xsize, ysize, zsize, xoffset, yoffset, zoffset, xlen, ylen, zlen);
        }

        /**
         * Copies the array elements from host memory.
         *
         * @param a     host memory.
         */
        public void copyTo(FloatArray3D a) {
            memcpyToHost3D(a.toCArray(), Unsafe.toNativeBody(devPitchedPtr), xsize, ysize, zsize);
        }

        public void copyTo(FloatArray3D a, int xoffset, int yoffset, int zoffset, int xlen, int ylen, int zlen) {
            memcpyToHost3D(a.toCArray(), Unsafe.toNativeBody(devPitchedPtr), xsize, ysize, zsize, xoffset, yoffset, zoffset, xlen, ylen, zlen);
        }

        @Native("cudaMemcpy3DParms parms = {0};"
                + "parms.srcPtr = *((cudaPitchedPtr*)v2);"
                + "parms.srcPos = make_cudaPos(0, 0, 0);"
                + "parms.dstPtr = make_cudaPitchedPtr(v1, sizeof(float) * v5, v5, v4);"
                + "parms.dstPos = make_cudaPos(0, 0, 0);"
                + "parms.extent = make_cudaExtent(sizeof(float) * v5, v4, v3);"
                + "parms.kind = cudaMemcpyDeviceToHost;"
                + "cudaMemcpy3D(&parms);")
        private static void memcpyToHost3D(float[] dest, @NativePtr PitchedPtr src, int xsize, int ysize, int zsize){
        }

        @Native("cudaMemcpy3DParms parms = {0};"
                + "parms.srcPtr = *((cudaPitchedPtr*)v2);"
                + "parms.srcPos = make_cudaPos(sizeof(float) * v8, v7, v6);"
                + "parms.dstPtr = make_cudaPitchedPtr(v1, sizeof(float) * v5, v5, v4);"
                + "parms.dstPos = make_cudaPos(sizeof(float) * v8, v7, v6);"
                + "parms.extent = make_cudaExtent(sizeof(float) * v11, v10, v9);"
                + "parms.kind = cudaMemcpyDeviceToHost;"
                + "cudaMemcpy3D(&parms);")
        private static void memcpyToHost3D(float[] dest, @NativePtr PitchedPtr src, int xsize, int ysize, int zsize,
                                           int xoffset, int yoffset, int zoffset, int xlen, int ylen, int zlen){
        }

        @Native("cudaMemcpy3DParms parms = {0};"
                + "parms.srcPtr = make_cudaPitchedPtr(v2, sizeof(float) * v5, v5, v4);"
                + "parms.srcPos = make_cudaPos(0, 0, 0);"
                + "parms.dstPtr = *((cudaPitchedPtr*)v1);"
                + "parms.dstPos = make_cudaPos(0, 0, 0);"
                + "parms.extent = make_cudaExtent(sizeof(float) * v5, v4, v3);"
                + "parms.kind = cudaMemcpyHostToDevice;"
                + "cudaMemcpy3D(&parms);")
        private static void memcpyToDevice3D(@NativePtr PitchedPtr dest, float[] src, int xsize, int ysize, int zsize){
        }

        @Native("cudaMemcpy3DParms parms = {0};"
                + "parms.srcPtr = make_cudaPitchedPtr(v2, sizeof(float) * v5, v5, v4);"
                + "parms.srcPos = make_cudaPos(sizeof(float) * v8, v7, v6);"
                + "parms.dstPtr = *((cudaPitchedPtr*)v1);"
                + "parms.dstPos = make_cudaPos(sizeof(float) * v8, v7, v6);"
                + "parms.extent = make_cudaExtent(sizeof(float) * v11, v10, v9);"
                + "parms.kind = cudaMemcpyHostToDevice;"
                + "cudaMemcpy3D(&parms);")
        private static void memcpyToDevice3D(@NativePtr PitchedPtr dest, float[] src, int xsize, int ysize, int zsize,
                                             int xoffset, int yoffset, int zoffset, int xlen, int ylen, int zlen){
        }
    }
    
    @Metaclass(type=NativeClass.class, arg = "sizeof(cudaPitchedPtr)")
    public static final class PitchedPtr {
        public void free() {
            free(Unsafe.toNativeBody(this));
        }

        @Native("cudaPitchedPtr* ptr = (cudaPitchedPtr*)v1; cudaFree(ptr->ptr);")
        public static void free(@NativePtr PitchedPtr devPitchedPtr) {
        }
    }

    /**
     * An abstract metaclass for generating {@code __managed__} variables.
     */
    public static abstract class CudaMemory extends HeapMemory.PortableHeapMemory {
        public CudaMemory() { super(false); }

        public abstract String preamble();

        /**
         * Recommends allocating the given object on managed memory.
         * The implementation in this class ignores the recommendation.
         */
        public CudaMemory onManaged(Object obj) { return this; }

        /**
         * Recommends allocating the given object on managed memory.
         * The implementation in this class calls {@link #onManaged(Object)}.
         */
        public CudaMemory onManaged(Object ... obj) {
            for (Object e: obj)
                onManaged(e);

            return this;
        }

        /**
         * Recommends allocating the given object on device memory.
         * The implementation in this class ignores the recommendation.
         */
        public CudaMemory onDevice(Object obj) { return this; }

        /**
         * Recommends allocating the given object on managed memory.
         * The implementation in this class calls {@link #onDevice(Object)}.
         */
        public CudaMemory onDevice(Object ... obj) {
            for (Object e: obj)
                onDevice(e);

            return this;
        }

        public final void managedDeclarationCode(CodeGen gen, CTypeDef type, String typeName, String gvarName) {
            gen.append("__managed__ ");
            gen.append(typeName).append(' ').append(gvarName);
        }

        public final void deviceDeclarationCode(CodeGen gen, CTypeDef type, String typeName, String gvarName) {
            gen.append("__device__ ");
            gen.append(typeName).append(' ').append(gvarName);
        }

        public final void sharedDeclarationCode(CodeGen gen, CTypeDef type, String typeName, String gvarName) {
            gen.append("__shared__ ");
            gen.append(typeName).append(' ').append(gvarName);
        }
    }

    /**
     * A metaclass for making only selected variables to be {@code __managed__}.
     * The default metaclass used by {@link CudaDriver} is {@link CudaManagedMemory},
     * which make all variables {@code __managed__}.  To change this behavior,
     * use this metaclass.
     *
     * <p>Example: only mem1 and mem2 are allocated on managed memory.</p>
     * <pre>
     * StdDriver driver = ...
     * CUDA.CudaMemory cudaMem = new CUDA.CudaOptionalManagedMemory();
     * driver.setHeapMemory(cudaMem);
     * cudaMem.onManaged(mem1, mem2);
     * </pre>
     *
     * @see javassist.offload.javatoc.StdDriver#setHeapMemory(HeapMemory)
     */
    public static class CudaOptionalManagedMemory extends CudaMemory {
        public static enum Memory { MANAGED, DEVICE };
        private HashMap<Object,Memory> allocation = new HashMap<Object,Memory>();

        public String preamble() { return ""; }

        /**
         * Allocates the given object on managed memory.
         */
        @Override public CudaOptionalManagedMemory onManaged(Object obj) {
            if (obj != null)
                allocation.put(obj, Memory.MANAGED);

            return this;
        }

        /**
         * Allocates the given object on device memory.
         */
        @Override public CudaMemory onDevice(Object obj) {
            if (obj != null)
                allocation.put(obj, Memory.DEVICE);

            return this;
        }

        @Override public void prototypeCode(CodeGen gen, Object obj, CTypeDef type, String typeName, String gvarName) {
            Memory kind = allocation.get(obj);
            if (kind == null)
                super.prototypeCode(gen, obj, type, typeName, gvarName);
            else if (kind == Memory.MANAGED) {
                gen.append("extern ");
                managedDeclarationCode(gen, type, typeName, gvarName);
                gen.append(";\n");
            }
            else {
                gen.append("extern ");
                deviceDeclarationCode(gen, type, typeName, gvarName);
                gen.append(";\n");
            }
        }

        @Override public void declarationCode(CodeGen gen, Object obj, CTypeDef type, String typeName, String gvarName) {
            Memory kind = allocation.get(obj);
            if (kind == null)
                super.declarationCode(gen, obj, type, typeName, gvarName);
            else if (kind == Memory.MANAGED)
                managedDeclarationCode(gen, type, typeName, gvarName);
            else
                deviceDeclarationCode(gen, type, typeName, gvarName);
        }
    }

    /**
     * A metaclass for making all variables to be {@code __managed__}.
     */
    public static class CudaManagedMemory extends CudaMemory {
        public String preamble() {
            return "static void* " + malloc()
                    + "  (size_t s) { void* ptr; cudaMallocManaged(&ptr, s); return ptr; }\n"
                    + "static void* " + calloc() + "(size_t c, size_t s) {\n"
                    + "  void* ptr; cudaError_t err = cudaMallocManaged(&ptr, c * s );\n"
                    + "  if (err == cudaSuccess) cudaMemset(ptr, 0, c * s);\n"
                    + "  return ptr; }\n";
        }

        @Override public String malloc() { return "cuda__malloc"; }

        @Override public String calloc() { return "cuda__calloc"; }

        @Override public String free() { return "cudaFree"; }

        @Override public void prototypeCode(CodeGen gen, Object obj, CTypeDef type, String typeName, String gvarName) {
            gen.append("extern ");
            managedDeclarationCode(gen, type, typeName, gvarName);
            gen.append(";\n");
        }

        @Override public void declarationCode(CodeGen gen, Object obj, CTypeDef type, String typeName, String gvarName) {
            managedDeclarationCode(gen, type, typeName, gvarName);
        }
    }
}
