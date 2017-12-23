// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;

import javassist.CtClass;
import javassist.CtBehavior;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.bytecode.MethodInfo;
import javassist.offload.Metaclass;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.ASTreeList;
import javassist.offload.ast.Callable;
import javassist.offload.ast.AdhocASTList;
import javassist.offload.ast.Assign;
import javassist.offload.ast.Block;
import javassist.offload.ast.Body;
import javassist.offload.ast.Call;
import javassist.offload.ast.Function;
import javassist.offload.ast.GetField;
import javassist.offload.ast.InlinedFunction;
import javassist.offload.ast.TypeDef;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.New;
import javassist.offload.ast.PutField;
import javassist.offload.ast.Return;
import javassist.offload.ast.UserMetaclass;
import javassist.offload.ast.Variable;
import javassist.offload.ast.VisitorException;
import javassist.offload.javatoc.impl.JavaObjectToC;
import javassist.offload.reify.Inliner;

/**
 * A metaclass for immutable-object classes.
 * The direct super type of immutable-object class must be {@code java.lang.Object}
 * but an instance of immutable-object class cannot be used as a value of  {@code java.lang.Object} type.
 * {@code null} is not assignable to a variable of an immutable-object class type.
 * An array of immutable-object class cannot be passed from Java to C.
 * A class inheriting an immutable class or implementing an immutable interface
 * is also immutable.
 *
 * <p>{@link Advice}.class can be passed as an argument to {@link ImmutableClass} in {@link @Metaclass}. 
 */
public class ImmutableClass extends ClassDef implements UserMetaclass {
    /**
     * A companion class that should be specified
     * if an immutable-object class is a functional interface.
     * This causes better inlining. 
     */
    public static class Lambda implements Advice {
        public String rename(CtClass klass, String methodName) {
            return null;
        }
    };

    private final Metaclass metaclass;
    private String[] fieldNames = null;
    private HashMap<Callable,int[]> constructorMap;

   /**
     * Constructs an immutable class.
     */
    public ImmutableClass(CtClass cc, CtField[] f, int uid, String arg, String[] args, Class<?> companion)
        throws NotFoundException
    {
        this(cc, f, uid, makeTypeName(cc, uid), companion);
    }

    private static String makeTypeName(CtClass cc, int uid) {
        String kind = cc.isInterface() ? "union " : "struct ";
        return kind + cc.getSimpleName().replace('$', '_') + "_" + uid;
    }

    protected ImmutableClass(CtClass cc, CtField[] fields, int uid, String typeName, Class<?> companion)
        throws NotFoundException
    {
        super(cc, fields, uid, typeName);
        if (!cc.getSuperclass().getName().equals("java.lang.Object"))
            throw new RuntimeException(cc.getName() + "'s direct superclass must be Object");

        metaclass = makeMetaclassForSubclasses(companion);
        fieldNames = null;
        constructorMap = new HashMap<Callable,int[]>(); 
    }

    private static Metaclass makeMetaclassForSubclasses(Class<?> companion) {
        if (companion != Metaclass.defaultCompanion) {
            try {
                companion.asSubclass(Advice.class);
            } catch (ClassCastException e) {
                throw new RuntimeException(companion.getName() + " must implement " + Advice.class.getName()
                                           + " since it is passed to " + ImmutableClass.class.getName());
            }

            if (companion.isInterface())
                throw new RuntimeException(companion.getName() + " must not be an interface type"
                                           + " since it is passed to " + ImmutableClass.class.getName());

            try {
                companion.getDeclaredConstructor(new Class[0]);
            } catch (NoSuchMethodException | SecurityException e) {
                throw new RuntimeException(companion.getName() + " must have a default constructor");
            }
        }

        return new Metaclass() {
            public Class<? extends UserMetaclass> type() {
                return ImmutableClass.class;
            }

            public String arg() { return null; }
            public String[] args() { return null; }
            public Class<?> companion() { return companion; }
            public Class<? extends Annotation> annotationType() { return null; }
        };
    }

    public Metaclass metaclassForSubtypes() { return metaclass; }

    public void update(OrderedTypes set) throws NotFoundException {
        CtClass type = type();
        for (CtClass t: type.getInterfaces())
            set.require(set.typeDef(t), this);

        super.update(set);
    }

    public String referenceType() { return objectType(); }

    /**
     * If the added method is a constructor, it is transformed.
     * If advice is given, it is applied. 
     */
    public Function add(Function f, CtBehavior m) throws NotFoundException {
        if (m instanceof CtConstructor)
            return addConstructor(f);
        else {
            Class<?> c = metaclass.companion();
            if (c != Metaclass.defaultCompanion) {
                Advice adv;
                try {
                    adv = (Advice)c.newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                applyAdvice(adv, f.body());
            }

            return super.add(f, m);
        }
    }

    /**
     * Applies {@link Advice} to the given tree.
     */
    public static void applyAdvice(Advice advice, ASTree tree) {
        int n = tree.numChildren();
        for (int i = 0; i < n; i++)
            applyAdvice(advice, tree.child(i));

        if (tree instanceof Call) {
            Call c = (Call)tree;
            String name = c.methodName();

            // The body of a Java lambda should be a static method
            // with a name starting with lambda$.
            if (c.isStatic() && name.startsWith("lambda$"))
                c.setCalleeTransformer(f -> applyAdvice(advice, f.body())); 

            String newName = advice.rename(c.targetType(), name);
            if (newName != null)
                c.methodName(newName);
        }
    }

    /**
     * If the added method is a constructor, it is transformed.
     */
    private Function addConstructor(Function f) throws NotFoundException {
        JVariable[] params = f.parameters();
        JVariable self = params[0];
        JVariable[] newParams = new JVariable[params.length - 1];
        HashMap<JVariable,ConstructorParameter> map
            = new HashMap<JVariable,ConstructorParameter>();
        for (int i = 0; i < newParams.length; i++) {
            newParams[i] = params[i + 1];
            map.put(newParams[i], new ConstructorParameter());
        }

        Body body = f.body();
        visitElements(body, body, self, map);
        f.setParameters(newParams);
        f.setType(self.type());
        f.setParameterMap(paramMap);
        f.add(self);
        constructorMap.put(f, modifyInitialization(f, map));
        return f;
    }

    private static final Function.ParameterMap paramMap
        = new Function.ParameterMap() {
            public int parameter(int n) {
                return n;
            }
        };

    static class ConstructorParameter {
        JVariable parameter = null;
        PutField initializer = null;
        Block parent = null;
    }

    /**
     * Modifies super() and return.  Also checks whether a constructor parameter
     * is directly assigned to a final field for initialization.
     */
    private ASTree visitElements(ASTree tree, ASTree parent, final JVariable self,
                                 HashMap<JVariable,ConstructorParameter> params)
        throws NotFoundException
    {
        if (tree instanceof PutField && parent instanceof Block) {
            PutField pf = (PutField)tree;
            ASTree value = pf.value();
            if (pf.target() == self && value instanceof JVariable
                && javassist.Modifier.isFinal(pf.field().getModifiers()))
            {
                ConstructorParameter found = params.get((JVariable)value);
                if (found != null) {
                    if (found.initializer == null){
                        found.initializer = pf;
                        found.parameter = (JVariable)value;
                        found.parent = (Block)parent;
                    }
                    else  // if used more than once.
                        params.remove((JVariable)value);

                    return tree;
                }
            }
        }

        int n = tree.numChildren();
        for (int i = 0; i < n; i++) {
            ASTree child = tree.child(i);
            ASTree child2 = visitElements(child, tree, self, params);
            if (child != child2)
                tree.setChild(i, child2);
        }

        if (tree instanceof Call) {
            Call call = (Call)tree;
            if (call.isInvokeSpecial()
                && MethodInfo.nameInit.equals(call.methodName())) {
                ASTree t = call.target();
                if (t instanceof JVariable && self.identical(((JVariable)t)))
                    return makeHeaderInitializer(self);
            }
        }
        else if (tree instanceof Return)
            return new Return(self.type(), self);
        else if (tree instanceof JVariable) {
            // the variable used not for field initialization
            params.remove((JVariable)tree);
        }

        return tree;
    }

    private int[] modifyInitialization(Function constructor,
            HashMap<JVariable,ConstructorParameter> map)
    {
        JVariable[] params = constructor.parameters();
        int[] indexes = new int[params.length];
        ArrayList<PutField> initializers = new ArrayList<PutField>();
        for (int i = 0; i < params.length; i++) {
            ConstructorParameter cp = map.get(params[i]);
            if (cp == null)
                indexes[i] = -1;
            else {
                indexes[i] = initializers.size();
                initializers.add(cp.initializer);
                cp.parent.remove(cp.initializer);
            }
        }

        for (int i = initializers.size() - 1; i >= 0; i--)
            constructor.block(0).add(0, initializers.get(i));

        return indexes;
    }

    /**
     * Return the code for {@code super()}.
     *
     * @param self          the {@code this} object.
     */
    protected ASTree makeHeaderInitializer(JVariable self) {
        return new AdhocASTList(self, "." + CTypeDef.HEADER_FIELD
                                + " = " + typeId() + "<<" + CTypeDef.FLAG_BITS);
    }

    static class AnInliner extends Inliner {
        int[] indexes;

        AnInliner(int[] indexes) { this.indexes = indexes; }

        @Override
        protected int inlineOneArgumentByDefault(Function callee, ArrayList<Variable> vars,
                ASTreeList<ASTree> exprs, int var, int paramIndex, ASTree arg, ASTree typedValue,
                JVariable p)
            throws NotFoundException
        {
            int putFieldIndex = indexes[paramIndex];
            if (putFieldIndex < 0)
                return super.inlineOneArgumentByDefault(callee, vars, exprs, var,
                                                        paramIndex, arg, typedValue, p);
            else {
                PutField pf = (PutField)callee.block(0).get(0); // the cast should not fail
                callee.block(0).remove(0);
                pf.setRight(arg);
                exprs.add(pf);
                return var;
            }
        }
    }

    public Inliner inliner(Callable f) {
        int[] indexes = constructorMap.get(f);
        if (indexes == null)
            return super.inliner(f);
        else
            return new AnInliner(indexes);
    }

    public void code(CodeGen gen) throws NotFoundException {
        if (!type().isInterface())
            super.code(gen);
        else {
            gen.append(objectType());
            gen.append(" {\n");
            gen.append(" ").append(CTypeDef.HEADER_DECL).append('\n');
            for (TypeDef type: getSubtypes()) {
                CTypeDef t = (CTypeDef)type;
                gen.append("  ").append(t.objectType());
                gen.append(" t").append(t.typeId()).append(";\n");
            }

            gen.append("};\n");
        }
    }

    public void objectToCode(Object obj, JavaObjectToC jo, CodeGen gen, Class<?> klass, String gvarName)
        throws IllegalAccessException, VisitorException
    {
        gen.recordGivenObject(obj, gvarName);     // temporary
        Field[] fields = jo.fieldsToCode(obj, gen, klass);
        StringBuilder sb = new StringBuilder();
        sb.append('(').append(objectType()).append("){ ");
        sb.append(typeId() << CTypeDef.FLAG_BITS);
        for (int i = 0; i < fields.length; i++) {
            if (!Modifier.isStatic(fields[i].getModifiers())) {
                Class<?> t = fields[i].getType();
                fields[i].setAccessible(true);
                Object value = fields[i].get(obj);
                sb.append(", ");
                if (t.isPrimitive())
                    sb.append(JavaObjectToC.toCvalue(value, t));
                else if (value == null)
                    sb.append('0');
                else {
                    // even if !gen.heapMemory().allowsReferenceAsInitialValue(),
                    // do the following.
                    if (t.isArray()) {
                        String cast = JavaObjectToC.castForArray(t);
                        if (cast != null)
                            sb.append(cast);
                    }
                    else {
                        if (t != value.getClass()) {
                            sb.append('(');
                            sb.append(gen.addClass(gen.getCtClass(t)).referenceType());
                            sb.append(')');
                        }
                    }

                    sb.append(gen.isGivenObject(value));
                }
            }
        }

        sb.append(" }");
        gen.recordGivenObject(obj, sb.toString()); 
    }

    public String objectToCodeInitializedLater(CodeGen gen, CTypeDef tdef,
                                        Class<?> fieldClass, String gvarName,
                                        java.lang.reflect.Field field, Object value)
        throws VisitorException
    {
        CTypeDef vtype = gen.addClass(gen.getCtClass(value.getClass()));

        StringBuilder sb = new StringBuilder();
        String memberName = tdef.fieldName(gen, field.getName());
        sb.append(gvarName).append('.').append(memberName);
        if (fieldClass.isInterface())
            sb.append(".t").append(vtype.typeId());

        sb.append(" = ");
        sb.append(gen.isGivenObject(value)).append(";\n");
        return sb.toString();
    }

    public void doCast(CodeGen gen, CtClass toType, ASTree value)
        throws NotFoundException, VisitorException
    {
        CtClass vtype = null;
        if (value instanceof GetField) {
            vtype = typeOfInlinedGetField(gen, (GetField)value);
            if (vtype != null)
                 value = new GetField((GetField)value, vtype);
        }

        if (vtype == null)
            vtype = type();

        if (vtype == toType)
            doCastForInlining(gen, vtype, value);
        else if (vtype.isInterface() && toType.subtypeOf(vtype)) {
            gen.append('(');
            value.accept(gen);
            gen.append(").t").append(gen.typeDef(toType).typeId());
        }
        else if (toType.isInterface() && gen.heapMemory().portableInitialization()) {
            CTypeDef t = gen.typeDef(toType);
            CTypeDef vt = gen.typeDef(vtype);
            gen.append('(').append(t.referenceType()).append("){.t").append(vt.typeId()).append("=(");
            value.accept(gen);
            gen.append(")}");
        }
        else
            super.doCast(gen, toType, value);
    }

    private static void doCastForInlining(CodeGen gen, CtClass vtype, ASTree value)
        throws VisitorException
    {
        if (vtype.isInterface() && value instanceof Call
            && ((Call)value).calledFunction() instanceof InlinedFunction) {
            /* When the return type is an ImmutableClass interface and the function is inlined,
             * type casting must be inserted since the type of the inlined expression may
             * not be that interface. 
             */
            gen.append("((").append(gen.typeDef(vtype).referenceType()).append(')');
            value.accept(gen);
            gen.append(')');
        }
        else
            value.accept(gen);
    }

    protected boolean castNeeded(CodeGen gen, CtClass toType, ASTree value) {
        // a cast operator will not be inserted but returning true is safe.
        return true;
    }

    public boolean hasCustomSerializer() { return true; }

    public void serializeObject(Object obj, OutputStream output)
        throws IOException
    {
        notImplemented(obj.getClass().getName());
    }

    public void instantiate(CodeGen gen, New expr) throws VisitorException {
        gen.append(expr.constructor());
    }

    @Override public boolean instantiationIsSimple() { return true; }

    public void doAssign(CodeGen out, Assign expr) throws VisitorException {
        ASTree right = expr.right();
        if (right instanceof Variable) {
            ASTree left = expr.left();
            if (left instanceof Variable && left.type() == right.type()) {
                doAssign(out, (Variable)left, (Variable)right);
                return;
            }
        }

        doAssign0(out, expr);
    }

    private void doAssign(CodeGen gen, Variable left, Variable right) throws VisitorException {
        if (fieldNames == null)
            fieldNames = nonStaticFieldNames(true);

        gen.append('(');
        boolean first = true;
        for (String name: fieldNames) {
            if (first)
                first = false;
            else
                gen.append(", ");

            gen.visitAsLvalue(left);
            gen.append('.');
            gen.append(name);
            gen.append('=');
            gen.append(right);
            gen.append('.');
            gen.append(name);
        }

        gen.append(')');
    }

    public void getField(CodeGen gen, GetField expr) throws VisitorException {
        if (inlineGetField(gen, expr))
            return;

        if (expr.target() != null)
            gen.append(expr.target()).append(".");

        gen.append(fieldName(gen, expr.fieldName()));
    }

    protected String dereference(String expr) { return expr; }

    public void putField(CodeGen gen, PutField expr) throws VisitorException {
        if (expr.target() != null) {
            gen.append(expr.target())
               .append('.');        // not an arrow
        }

        gen.append(fieldName(gen, expr.fieldName(), true))
           .append(" = ");
        CTypeDef.doCastOnValue(gen, expr.fieldType(), expr.value());
    }

    public void getHeader(CodeGen gen, ASTree expr) throws VisitorException {
        gen.append(expr)
           .append(".").append(CTypeDef.HEADER_FIELD);
    }
}
