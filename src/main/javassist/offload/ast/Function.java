// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.ArrayList;
import java.util.HashMap;

import javassist.offload.Inline;
import javassist.offload.reify.ASTWalker;
import javassist.CtBehavior;
import javassist.CtClass;

/**
 * A method declaration.
 */
public class Function extends Callable implements JMethod {
    private Variable[] variables;
    private Body body;
    private Inline inlineAnnotation;

    /**
     * Constructs an empty function.
     *
     * @param type      the return type.
     * @param method    the original method or constructor.
     * @param fname     the function name in C.
     * @param params    the formal parameters.
     * @param isStatic  true if the function is a static method.
     */
    protected Function(CtClass type, CtBehavior method, String fname, JVariable[] params, boolean isStatic) {
        super(type, method, fname, params, isStatic);
        variables = null;
        body = new Body();
        inlineAnnotation = null;
    }

    /**
     * Constructs a function.
     * The constructor makes a list of local variables used in the body
     * (the local variables must be TmpVaraible objects only).
     * It also set the jump target of all the Jump statements. 
     *
     * @param type      the return type.
     * @param method    the original method or constructor.
     * @param fname     the function name in C.
     * @param params    the formal parameters.  If the method is
     *                  not static, the first element is the target/receiver object.
     * @param blocks    the function body.
     * @param isStatic  the function is a static method.
     * @see Jump#setTarget(Function)
     */
    protected Function(CtClass type, CtBehavior method, String fname, JVariable[] params,
                    Iterable<Block> blocks, boolean isStatic)
    {
        this(type, method, fname, params, isStatic);
        for (Block b: blocks)
            add(b);

        int id = 1;
        for (JVariable p: params)
            p.setIdentifier(id++);

        ArrayList<Variable> vars = new ArrayList<Variable>();
        ASTWalker.synthesizedFunction(this, body(), vars);
        setLocalVars(vars);
    }

    protected void deepCopy(HashMap<ASTree,ASTree> map) {
        super.deepCopy(map);
        if (variables != null)
            variables = copy(variables, new Variable[variables.length], map);

        body = copy(body, map);
    }

    /**
     * Internal-use only.  Don't call this method.
     */
    public void setLocalVars(ArrayList<Variable> vars) {
        variables = vars.toArray(new Variable[vars.size()]);
    }

    /**
     * Attaches an {@code Inline} annotation.
     *
     * @param in        the annotation or null.
     */
    public void setInline(Inline in) { inlineAnnotation = in; }

    /**
     * Returns an {@code Inline} annotation, or null.
     */
    public Inline inline() { return inlineAnnotation; }

    /**
     * Returns true if this function can be specialized with respect to
     * the given arguments.
     */
    public boolean specializable() { return true; }

    /**
     * Returns the metaclass of the functions directly/indirectly
     * invoked within this function.
     */
    public FunctionMetaclass metaclass() { return FunctionMetaclass.instance; }

    /**
     * Returns the local variables excluding the parameters.
     */
    public Variable[] variables() { return variables; }

    /**
     * Notify that a local variable is added.
     * If the method body is modified
     * and a new local variable is added, the Function object
     * must be notified that the local variable is added.
     *
     * @param v         the new local variable.
     */
    public void add(Variable v) {
        int len = variables.length;
        Variable[] vars = new Variable[len + 1];
        System.arraycopy(variables, 0, vars, 0, len);
        vars[len] = v;
        variables = vars;
    }

    /**
     * Notify that the given local variable is removed
     * from the function.
     */
    public void remove(Variable v) {
        int len = variables.length;
        Variable[] vars = new Variable[len - 1];
        int j = 0;
        for (int i = 0; i < len; i++)
            if (variables[i] != v)
                vars[j++] = variables[i];

        variables = vars;
    }

    /**
     * Notify that local variables are added.
     * If the method body is modified
     * and new local variables are added, the Function object
     * must be notified that the local variables are added.
     *
     * @param vars         the new local variables.
     */
    public void add(ArrayList<Variable> vars) {
        int len = variables.length;
        int len2 = vars.size();
        if (len2 == 0)
            return;

        Variable[] array = new Variable[len + len2];
        System.arraycopy(variables, 0, array, 0, len);
        for (int i = 0; i < len2; i++)
            array[len + i] = vars.get(i);

        variables = array;
    }

    /**
     * Appends a block to the method body.
     * This does not update the list of local variables.
     * So the variables method does not return the correct data.
     *
     * @see #variables()
     */
    public void add(Block a) { body.add(a); }

    /**
     * Returns the method body.
     */
    public Body body() { return body; }

    public int numChildren() { return 1; }

    public ASTree child(int n) {
        if (n == 0)
            return body;
        else
            return super.child(n);
    }

    /**
     * Sets the n-th child to the given new ASTree.
     * It also recomputes the number of incoming
     * jumps into each block and also the number
     * of temporary variables used in the body.
     */
    public void setChild(int n, ASTree c) {
        if (n == 0) {
            for (Block b: body)
                b.clearIncomingJumps();

            body = (Body)c;
            ArrayList<Variable> vars = new ArrayList<Variable>();
            ASTWalker.synthesizedFunction(this, body(), vars);
            setLocalVars(vars);
        }
        else
            super.setChild(n, c);
    }

    /**
     * Returns the n-th block. 
     */
    public Block block(int n) { return body.block(n); }

    /**
     * Sets the label of every block in the body.
     */
    public void updateLabels() {
        int seq = 0;
        for (Block b: body)
            seq = b.setLable(seq);
    }

    /**
     * Invoked when the traversal of the body starts.
     * This can change the value of a {@code @Final} static field
     * so that dead code elimination will work properly.
     * Note that this method will be called more than once
     * since body traversal is executed a few times.
     *
     * @see #traversalEnds()
     */
    public void traversalBegins() {}

    /**
     * Invoked when the traversal of the body ends.
     *
     * @see #traversalBegins()
     */
    public void traversalEnds() {}

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type().getName());
        sb.append(' ');
        sb.append(name());
        sb.append('(');
        for (JVariable v: parameters()) {
            sb.append(v);
            sb.append(':');
            sb.append(v.type().getName());
            sb.append(' ');
        }

        sb.append(")\n{\nlocal vars: ");
        if (variables != null)
            for (Variable v: variables) {
                sb.append(v);
                sb.append(':');
                sb.append(v.type().getName());
                sb.append(' ');
            }

        sb.append('\n');
        sb.append(body.toString());
        sb.append('}');
        return sb.toString();
    }

    public void accept(Visitor v) throws VisitorException {
        v.visit(this);
    }
}
