// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.Opcode;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Assign;
import javassist.offload.ast.Block;
import javassist.offload.ast.Body;
import javassist.offload.ast.Branch;
import javassist.offload.ast.Call;
import javassist.offload.ast.For;
import javassist.offload.ast.Function;
import javassist.offload.ast.GetField;
import javassist.offload.ast.Goto;
import javassist.offload.ast.Iinc;
import javassist.offload.ast.InlinedFunction;
import javassist.offload.ast.IntConstant;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.Jump;
import javassist.offload.ast.New;
import javassist.offload.ast.Null;
import javassist.offload.ast.ObjectConstant;
import javassist.offload.ast.PutField;
import javassist.offload.ast.Return;
import javassist.offload.ast.TmpVariable;
import javassist.offload.ast.Variable;

/**
 * A collection of methods for updating various properties
 * of AST nodes.
 *
 * {@link Tracer#visitElements(ASTree, Function, TraceContext)} also
 * traverses the tree.
 *
 * @see MethodTracer
 * @see javassist.offload.ast.Function
 */
public class ASTWalker {
    /**
     * Updates properties of a function body.
     * It collects {@code JVariable}s and {@code TmpVariable}s.
     * It also finds expression statements.
     *
     * @see #isSimpleAssign(ASTree) 
     */
    public static void functionBody(FlowAnalyzer analyzer, ASTree tree, ArrayList<Variable> vars,
                                    Function func, ASTree parent)
    {
        int n = tree.numChildren();
        for (int i = 0; i < n; i++)
            functionBody(analyzer, tree.child(i), vars, func, tree);

        if (parent instanceof Block) {
            if (tree instanceof Assign) {
                /* make this consistent with #isSimpleAssign(ASTree).
                 */
                ASTree right = ((Assign)tree).right();
                functionBody2(right, tree);
            }
            else if (tree instanceof Return) {
                ASTree expr = ((Return)tree).expression();
                functionBody2(expr, tree);
            }
            else
                functionBody2(tree, parent);
        }

        if (tree instanceof JVariable)
            analyzer.setVariableIdentity((JVariable)tree, vars);
        else if (tree instanceof TmpVariable)
            if (!vars.contains(tree))
                vars.add((TmpVariable)tree);

        if (tree instanceof Jump)
            ((Jump)tree).setTarget(func);
    }

    private static void functionBody2(ASTree expr, ASTree parent) {
        if (expr instanceof Call)
            ((Call)expr).isStatement(parent);
        else if (expr instanceof New)
            ((New)expr).isStatement(parent);
    }

    /**
     * Updates a {@code Call} and a {@code New} in a function body.
     *
     * @see #functionBody(FlowAnalyzer, ASTree, ArrayList, Function, ASTree)
     */
    public static ASTree updateParent(ASTree expr, Assign parent) {
        if (expr instanceof Call) {
            Call call = (Call)expr;
            if (call.isStatement() != null)
                call.isStatement(parent);
        }
        else if (expr instanceof New) {
            New n = (New)expr;
            if (n.isStatement() != null)
                n.isStatement(parent);
        }

        return parent;
    }

    /**
     * Returns true if the given expression is a simple assignment.
     * The behavior must be compatible with
     * {@link #functionBody(FlowAnalyzer, ASTree, ArrayList, Function, ASTree)}
     */
    public static boolean isSimpleAssign(ASTree expr) {
        if (expr instanceof Assign) {
            Assign assign = (Assign)expr;
            if (assign.right() instanceof Call)
                return false;
            else if (assign.right() instanceof New)
                return false;
            else
                return true;
        }
    
        return false;
    }

    /**
     * Updates the body of a synthesized function, where
     * a {@code JVariable} is not declared in the body.
     * The method recomputes the number of incoming
     * jumps into each block and it also collects
     * temporary variables used in the body.
     *
     * @see Function#Function(javassist.CtClass, String, javassist.offload.ast.JVariable[], Iterable, boolean)
     * @see Function#setChild(int, ASTree)
     */
    public static void synthesizedFunction(Function f, ASTree tree, ArrayList<Variable> vars) {
        int n = tree.numChildren();
        for (int i = 0; i < n; i++)
            synthesizedFunction(f, tree.child(i), vars);

        if (tree instanceof TmpVariable) {
            if (!vars.contains(tree))
                vars.add((TmpVariable)tree);
        }
        else if (tree instanceof Jump)
            ((Jump)tree).setTarget(f);
    }

    /**
     * Finds for loops and modify the function body.
     *
     * @param body      the function body.
     */
    public static void findForLoop(Body body) {
        for (int i = 1; i < body.size(); i++) {
            Block b = body.block(i);
            findForLoopOfEclipse(body, i, b);
            findForLoopOfJavac(body, i, b);
        }
    }

    private static void findForLoopOfEclipse(Body body, int i, Block endB) {
        int init = hasBackwardJump(endB);
        if (init >= 0) {
            Block initB = body.get(init);
            Block lastOfLoopBody = body.get(i - 1);  
            int stepPos = endsWithIinc(lastOfLoopBody);
            Goto g = endsWithInitAndGoto(initB, endB);
            if (stepPos >= 0 && g != null) {
                int initPos = initB.size() - 2;
                ASTree initExpr = initB.get(initPos);
                initB.remove(initPos);
                initB.remove(initPos);  // remove GOTO
                ASTree stepExpr = lastOfLoopBody.get(stepPos);
                lastOfLoopBody.set(stepPos, For.EMPTY);
                For.Begin forB = new For.Begin(initExpr, (Branch)endB.get(0), stepExpr, g);
                Block beginB = body.get(init + 1);
                beginB.add(0, forB);
                endB.set(0, new For.End(init + 1, beginB));
            }
        }
    }

    private static int hasBackwardJump(Block b) {
        if (b.incomingJumps() == 1 && b.size() > 0) {
            ASTree first = b.get(0);
            if (first instanceof Branch) {
                Branch bra = (Branch)first;
                int to = bra.output();
                if (0 < to && to < b.index())
                    return to - 1;
            }
        }

        return -1;
    }

    private static Goto endsWithInitAndGoto(Block b, Block to) {
        int size = b.size();
        if (size > 1) {
            ASTree last2 = b.get(size - 2);
            if (last2 instanceof Iinc || isSimpleAssign(last2)) {
                ASTree last = b.get(size - 1);
                if (last instanceof Goto) {
                    Goto g = (Goto)last;
                    if (g.jumpTo() == to)
                        return g;
                }
            }
        }

        return null;
    }

    private static int endsWithIinc(Block b) {
        int size = b.size();
        if (size > 0) {
            ASTree last = b.get(size - 1);
            if (last instanceof Iinc || isSimpleAssign(last))
                return size - 1;
        }

        return -1;
    }

    private static void findForLoopOfJavac(Body body, int i, Block endB) {
        int init = endsWithIincAndGoto(endB);
        if (init >= 0) {
            Block initB = body.get(init);
            Block beginB = body.get(init + 1);
            int initPos = beginsWithBranch(initB, beginB, i + 1);
            if (initPos >= 0) {
                ASTree initExpr = initB.get(initPos);
                initB.set(initPos, For.EMPTY);
                Branch condExpr = (Branch)beginB.get(0);
                int endBsize = endB.size();
                ASTree stepExpr = endB.get(endBsize - 2);
                For.Begin forB = new For.Begin(initExpr, condExpr.makeNegation(),
                                               stepExpr);
                beginB.set(0, forB);
                endB.remove(endBsize - 1);
                endB.set(endBsize - 2, new For.End(init + 1,  beginB));
            }
        }
    }

    private static int endsWithIincAndGoto(Block b) {
        int size = b.size();
        if (size > 1) {
            ASTree a = b.get(size - 1);
            if (a instanceof Goto) {
                Goto g = (Goto)a;
                int to = g.output();
                if (0 < to && to <= b.index()) {
                    ASTree expr = b.get(size - 2);
                    if (expr instanceof Iinc || ASTWalker.isSimpleAssign(expr))
                        return to - 1;
                }
            }
        }

        return -1;
    }

    private static int beginsWithBranch(Block b1, Block b2, int toBlock) {
        int size1 = b1.size();
        if (size1 < 1)
            return -1;

        ASTree init = b1.get(size1 - 1);
        if (init instanceof Assign) {
            int size = b2.size();
            if (size > 0 && b2.incomingJumps() == 1) {
                ASTree a = b2.get(0);
                if (a instanceof Branch) {
                    Branch bra = (Branch)a;
                    if (bra.output() == toBlock)
                        return size1 - 1;
                }
            }
        }

        return -1;
    }

    /**
     * Eliminates code blocks that a thread of control never reaches.
     * If the direction of a conditional branch is statically determined,
     * the block that the branch never jumps to is eliminated.
     */
    public static void eliminateDeadCode(Body body) throws NotFoundException {
        for (Block block: body)
            block.clearIncomingJumps();

        boolean[] incoming = new boolean[body.size()];
        if (incoming.length > 0)
            findLiveBlocks(body.get(0), body, incoming);

        removeDeadBlocks(body, incoming);
    }

    private static void findLiveBlocks(Block block, Body body, boolean[] incoming)
        throws NotFoundException
    {
        if (incoming[block.index()])
            return;
        else
            incoming[block.index()] = true;

        int size = block.size();
        ASTree st = null;
        for (int i = 0; i < size; i++) {
            st = block.get(i);
            ASTree st2 = isGoto(st);
            if (st2 == null) {
                block.remove(i--);
                size = block.size();
            }
            else if (st != st2)
                block.set(i, st2);

            if (st2 instanceof Jump) {
                Jump j = (Jump)st2;
                int n = j.outputs();
                if (n == 1 && j.always() && j.output(0) == block.index() + 1) {
                    for (int k = i; k < size; k++)
                        block.remove(i);

                    break;
                }
                else {
                    for (int k = 0; k < n; k++) {
                        Block next = body.block(j.output(k));
                        next.isBranchTargetOf(j);
                        findLiveBlocks(next, body, incoming);
                    }

                    if (j.always()) {
                        for (int k = i + 1; k < size; k++)
                            block.remove(i + 1);

                        return;
                    }
                }
            }
        }

        findLiveBlocks(body.block(block.index() + 1), body, incoming);
    }

    /**
     * Returns a goto statement if {@code st} is a branch and the
     * branch condition is a compile-time constant.
     */
    private static ASTree isGoto(ASTree st) throws NotFoundException {
        if (st instanceof Branch) {
            Branch b = (Branch)st;
            ASTree left = b.left().value();
            int op = b.operator();
            if (op == Opcode.IFNULL || op == Opcode.IFNONNULL) {
                boolean known = false;
                boolean isNull = false;
                if (left instanceof Null) {
                    known = true;
                    isNull = true;
                }
                else if (left instanceof ObjectConstant) {
                    Object v = ((ObjectConstant)left).theValue();
                    if (v != null) {
                        known = true;
                        isNull = false;
                    }
                }

                if (known)
                    if (op == Opcode.IFNULL && isNull
                        || op == Opcode.IFNONNULL && !isNull)
                        return new Goto(b.jumpTo());
                    else
                        return null;    // don't branch
            }
            else if (op == Opcode.IFEQ || op == Opcode.IFNE) {
                boolean known = false;
                int value = 0;
                if (left instanceof IntConstant) {
                    known = true;
                    value = ((IntConstant)left).intValue();
                }
                else if (left instanceof ObjectConstant) {
                    Object v = ((ObjectConstant)left).theValue();
                    if (v instanceof Number) {
                        known = true;
                        value = ((Number)v).intValue();
                    }
                    else if (v instanceof Boolean) {
                        known = true;
                        value = ((Boolean) v).booleanValue() ? 1 : 0;
                    }
                }

                if (known)
                    if (op == Opcode.IFEQ && value == 0
                        || op == Opcode.IFNE && value != 0)
                        return new Goto(b.jumpTo());
                    else
                        return null;    // don't branch
            }
        }

        return st;
    }

    private static void removeDeadBlocks(Body body, boolean[] incoming) {
        for (Block b: body)
            if (!incoming[b.index()])
                b.clear();
    }

    /**
     * Changes the identity of all the variables contained in {@code tree}
     * according to {@code map}.
     */
    public static void changeVariables(ASTree tree, JVariable.Map map) {
        if (tree instanceof JVariable)
            map.update((JVariable)tree);
        else {
            int n = tree.numChildren();
            for (int i = 0; i < n; i++)
                changeVariables(tree.child(i), map);
        }
    }

    /**
     * Substitutes {@code subst} for all occurrences of {@code orig}
     * included in {@code tree}.
     */
    public static void replace(ASTree tree, ASTree orig, ASTree subst) {
        int n = tree.numChildren();
        for (int i = 0; i < n; i++) {
            ASTree child = tree.child(i);
            if (child == orig) {
                tree.setChild(i, subst);
                replace(subst, orig, subst);
            }
            else
                replace(child, orig, subst);
        }
    }

    /**
     * Performs object inlining if an inlined object is statically determined
     * and a reference to that object does not escape from the given code.
     * The caller-side code has to be a simple variable expression such as
     * {@code v = foo(p)}, where {@code foo} is an inlined function. 
     *
     * @param body          the code body.
     * @param vars          the values of these variables are inlined.
     * @param enclosure     the function containing the code body.
     * @param tmpVarNo      available local-variable number.
     */
    public static void inlineObjects(Body body, ArrayList<JVariable> vars,
                                     Function enclosure, InlinedFunction callee,
                                     int tmpVarNo)
        throws NotFoundException
    {
        InlinedObjects inlined = new InlinedObjects(tmpVarNo);
        for (JVariable var: vars) {
            ASTree val = var.value();
            if (val instanceof ObjectConstant)
                inlined.add(var, InlineValue.make((ObjectConstant)val, var));
            else if (val instanceof New)
                inlined.add(var, InlineValue.make((New)val, var));
        }

        Block initBlock = callee.initializer();
        if (initBlock == null)
            initBlock = new Block(0);

        notOnlyFieldAccesses(body, initBlock, inlined);
        if (inlined.size() < 1) 
            return;

        inlined.doReplace(body);
        Block finalizer = new Block(0);
        ArrayList<Variable> localVars = new ArrayList<Variable>();
        for (HashMap<String,InlinedObjects.Cache> map: inlined.fields.values())
            for (InlinedObjects.Cache cache: map.values()) {
                localVars.add(cache.variable);
                initBlock.add(new Assign(cache.variable.type(), cache.variable,
                                         cache.initializer));
                if (cache.copyBack != null)
                    finalizer.add(cache.copyBack);
            }

        if (localVars.size() > 0)
            enclosure.add(localVars);

        if (callee.initializer() == null && initBlock.size() > 0)
            body.add(0, initBlock);

        if (finalizer.size() > 0)
            body.add(finalizer);
    }

    static abstract class InlineValue {
        Variable variable;

        InlineValue(Variable v) { variable = v; }

        Variable variable() { return variable; }
        abstract Object value();

        static InlineValue make(ObjectConstant obj, Variable v) {
            return new InlineObjConst(obj, v);
        }

        static InlineValue make(New obj, Variable v) {
            return new InlineObjByNew(obj, v);
        }
    }

    static class InlineObjConst extends InlineValue {
        ObjectConstant object;

        InlineObjConst(ObjectConstant c, Variable v) { super(v); object = c; }
        Object value() { return object.theValue(); }
    }

    static class InlineObjByNew extends InlineValue {
        New expr;

        InlineObjByNew(New n, Variable v) { super(v); expr = n; }
        Object value() { return this; }
    }

    static class InlinedObjects {
        static class Cache {
            TmpVariable variable;
            ASTree initializer;
            ASTree copyBack;
        }

        static class Replacement {
            ASTree substituted;
            Object inlinedObject;   // the substitution is on this object.

            Replacement(ASTree t, InlineValue oc) {
                substituted = t;
                inlinedObject = oc.value();
            }

            boolean isA(InlineValue iv) { return iv.value() == inlinedObject; }
        }

        int identifier;     // used for generating a unique variable name.

        /**
         * A collection of the identities of inlined variables.
         */
        HashMap<Object,InlineValue> inlined = new HashMap<Object,InlineValue>();

        /**
         * A collection of field accessors to the inlined objects.
         */
        HashMap<Object, HashMap<String,Cache>> fields
            = new HashMap<Object, HashMap<String,Cache>>();

        HashMap<ASTree,Replacement> replacement = new HashMap<ASTree,Replacement>();

        InlinedObjects(int varNo) { identifier = varNo; }

        /**
         * Adds an inlined object.
         *
         * @param var           the variable that refers to the inlined object.
         * @param value         the inlined object.
         */
        void add(Variable var, InlineValue value) {
            inlined.put(var.getIdentity(), value);
        }

        /**
         * Returns the value of the given variable if the variable refers to
         * an inlined object.
         */
        InlineValue get(Variable v) { return inlined.get(v.getIdentity()); }

        /**
         * Returns the number of inlined objects.
         */
        int size() { return inlined.size(); }

        /**
         * Removes an inlined object if it is included.
         *
         * @param v     the variable.
         */
        InlineValue remove(Variable v) {
            Object id = v.getIdentity();
            if (id != null) {
                InlineValue obj = inlined.get(id);
                if (obj != null) {
                    inlined.remove(id);
                    Object value = obj.value();
                    fields.remove(value);
                    Iterator<Map.Entry<ASTree,Replacement>> it = replacement.entrySet().iterator(); 
                    while (it.hasNext())
                        if (it.next().getValue().isA(obj))
                            it.remove();

                    return obj;
                }
            }

            return null;
        }

        /**
         * Records an expression substituted for an expression
         * accessing an inlined object.
         *
         * @param orig          the original expression.
         * @param subst         the substituted expression.
         * @param v             the inlined object.
         */
        void replace(ASTree orig, ASTree subst, InlineValue iv) {
            replacement.put(orig, new Replacement(subst, iv));
        }

        /**
         * Applies the recorded substitutions into the given tree.
         */
        void doReplace(ASTree tree) {
            int n = tree.numChildren();
            for (int i = 0; i < n; i++) {
                ASTree child = tree.child(i);
                Replacement r = replacement.get(child);
                if (r == null)
                    doReplace(child);
                else {
                    ASTree newChild = r.substituted;
                    tree.setChild(i, newChild);
                    doReplace(newChild);
                }
            }
        }

        /**
         * Returns a local variable temporarily holding a cached value
         * of the given field of the given object.
         *
         * @param obj           the object.
         * @param gf            the getfield instruction accessing the field.
         */
        TmpVariable getCacheVariable(InlineValue obj, GetField gf) throws NotFoundException {
            return get(obj, gf.fieldName(), gf.type(), gf, null);
        }

        /**
         * Returns a local variable temporarily holding a cached value
         * of the given field of the given object.
         *
         * @param obj           the object.
         * @param pf            the puttfield instruction accessing the field.
         */
        TmpVariable getCacheVariable(InlineValue obj, PutField pf) throws NotFoundException {
            return get(obj, pf.fieldName(), pf.fieldType(), null, pf);
        }

        private TmpVariable get(InlineValue iv, String fieldName, CtClass type,
                                GetField gf, PutField pf) throws NotFoundException
        {
            Object obj = iv.value();
            HashMap<String,Cache> map = fields.get(obj);
            if (map == null) {
                map = new HashMap<String,Cache>();
                fields.put(obj, map);
            }

            Cache inlined = map.get(fieldName);
            if (inlined == null) {
                GetField getter;
                if (gf != null)
                    getter = gf;
                else
                    getter = new GetField(pf.targetClass(), pf.fieldName(),
                                          pf.fieldType(), pf.isStatic(), iv.variable());

                inlined = new Cache();
                inlined.variable = new TmpVariable(type, getter, identifier++);
                inlined.initializer = getter;
                if (javassist.Modifier.isFinal(getter.field().getModifiers()))
                    inlined.copyBack = null;
                else
                    inlined.copyBack = new PutField(getter.targetClass(), getter.fieldName(),
                                                    getter.type(), getter.isStatic(),
                                                    iv.variable(), inlined.variable);

                map.put(fieldName, inlined);
            }

            return inlined.variable;
        }
    }

    private static void notOnlyFieldAccesses(Body body, Block initBlock, InlinedObjects inlined)
        throws NotFoundException
    {
        int n = body.numChildren();
        int i = 0;
        if (n > 0 && body.child(0) == initBlock)
            i++;

        for (; i < n; i++)
            notOnlyFieldAccesses(body.child(i), inlined);   
    }

    private static void notOnlyFieldAccesses(ASTree tree, InlinedObjects inlined)
        throws NotFoundException
    {
        if (tree instanceof Variable) {
            InlineValue iv = inlined.remove((Variable)tree);
            if (iv != null)
                return;
        }
        else if (tree instanceof Assign) {
            Assign expr = (Assign)tree;
            if (expr.left() instanceof Variable
                && expr.right() instanceof Variable) {
                if (inlined.get((Variable)expr.left()) != null)
                    return;

                InlineValue v = inlined.get((Variable)expr.right());
                if (v != null)
                    inlined.add((Variable)expr.left(), v);

                return;
            }
        }
        else if (tree instanceof GetField) {
            GetField g = (GetField)tree;
            ASTree target = g.target();  
            if (target instanceof Variable) {
                InlineValue v = inlined.get((Variable)target);
                if (v != null && g.type().isPrimitive()) {
                    TmpVariable tmpVar = inlined.getCacheVariable(v, g);
                    inlined.replace(g, tmpVar, v);
                }

                return;
            }
        }
        else if (tree instanceof PutField) {
            PutField p = (PutField)tree;
            ASTree target = p.target();
            if (target instanceof Variable) {
                InlineValue v = inlined.get((Variable)target);
                if (v != null && p.fieldType().isPrimitive()) {
                    TmpVariable tmpVar = inlined.getCacheVariable(v, p);
                    ASTree expr = new Assign(p.fieldType(), tmpVar, p.value());
                    inlined.replace(p, expr, v);
                    notOnlyFieldAccesses(p.value(), inlined);
                    return;
                }
            }
        }

        int n = tree.numChildren();
        for (int i = 0; i < n; i++)
            notOnlyFieldAccesses(tree.child(i), inlined);
    }
}
