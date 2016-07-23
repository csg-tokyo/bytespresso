// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

import java.util.ArrayList;

import javassist.CtClass;
import javassist.CtField;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.offload.Options;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.ASTreeList;
import javassist.offload.ast.Assign;
import javassist.offload.ast.Block;
import javassist.offload.ast.Body;
import javassist.offload.ast.Call;
import javassist.offload.ast.Comma;
import javassist.offload.ast.Function;
import javassist.offload.ast.GetField;
import javassist.offload.ast.Goto;
import javassist.offload.ast.InlinedFunction;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.New;
import javassist.offload.ast.ObjectConstant;
import javassist.offload.ast.Return;
import javassist.offload.ast.TmpVariable;
import javassist.offload.ast.Variable;

/**
 * Static methods for function inlining.
 *
 * <p>For successful inlining, a call to the inlined function should not
 * be an operand of a larger expression.  It should be the only right-hand expression
 * of a variable assignment expression, for example, {@code v = func(i);}. 
 */
public class Inliner {
        /**
     * Inlines a function body.  It modifies {@code call} (and {@code caller})
     * if it successfully inlines the function body.
     * 
     * @param caller        the function in which the {@code callee} function is inlined.
     * @param call          the expression calling the {@code callee} function.
     * @param target        the normalized target.      It may be null.
     * @param args          the normalized arguments.   It may be null.
     * @param callee        the function being inlined.
     */
    public InlinedFunction inline(TraceContext context, Function caller, Call call,
                                  ASTree target, ASTree[] args, Function callee)
        throws NotFoundException
    {
        if (!Options.doInline)
            return null;

        if (caller == null)
            return null;

        if (canInline(context, call))
            return inlineFunctionBody(context, caller, call, target, args, callee);

        ASTree ret = isReturnOnly(callee);
        if (ret != null)
            return inlineSimpleFunction(context, caller, call, target, args, callee, ret);

        return null;
    }

    /**
     * Inlines a function body containing only a single return statement.
     * 
     * @param caller        the function in which the {@code callee} function is inlined.
     * @param call          the expression calling the {@code callee} function.
     * @param target        the normalized target.      It may be null.
     * @param args          the normalized arguments.   It may be null.
     * @param callee        the function being inlined.
     * @param ret           the return expression.
     */
    private InlinedFunction inlineSimpleFunction(TraceContext context,
            Function caller, Call call, ASTree target, ASTree[] args, Function callee, ASTree ret)
        throws NotFoundException
    {
        ArrayList<Variable> vars = new ArrayList<Variable>();
        Comma exprs = new Comma();
        JVariable.Map map = new JVariable.Map();
        inlineArguments(context, caller, call, target, args, callee, vars, exprs, map);
        ASTree body;
        if (exprs.size() < 1)
            body = ret;
        else {
            exprs.add(ret);
            body = exprs;
        }

        ASTWalker.changeVariables(body, map);
        InlinedFunction inline = context.metaclass().makeInlinedFunction(callee, body);
        caller.add(vars);
        call.setCalledFunction(inline);
        return inline;
    }

    /**
     * Obtains the return expression if the function contains only
     * a single return statement.
     * Otherwise, null is returned. 
     *
     * @return      the return expression.
     */
    private static ASTree isReturnOnly(Function f) {
        if (f.body().size() == 1) {
            Block b = f.body().get(0);
            if (b.size() == 1 && b.child(0) instanceof Return)
                return ((Return)b.child(0)).expression();
        }

        return null;
    }

    /**
     * Inlines arguments passed to the function.  It also renames local variables
     * to keep the code hygienic.
     *
     * @param args      the normalized arguments.  It may be null.
     * @param vars      the variables used in the inlined function.
     * @param exprs     the expression to that the code for assigning the arguments
     *                  to the formal parameters is added.
     * @return          the next available variable identifier number.
     */
	private int inlineArguments(TraceContext context, Function caller,
	                    Call call, ASTree target, ASTree[] args, Function callee,
	                    ArrayList<Variable> vars, ASTreeList<ASTree> exprs,
	                    JVariable.Map map)
	    throws NotFoundException
	{
        int var = maxLocalVariableNo(caller) + 1;
        int size = callee.parameters().length;
        for (int i = 0; i < size; i++) {
            int k = callee.parameter(i);
            ASTree a, typedValue;
            if (k == Function.ParameterMap.TARGET) {
                a = call.target();
                typedValue = target;
            }
            else {
                a = call.arguments()[k];
                if (args == null)
                    typedValue = null;
                else
                    typedValue = args[k];
            }

            var = inlineOneArgument(callee, vars, exprs, map, var, i, a, typedValue);
        }

        for (Variable v : callee.variables()) {
            v.setIdentifier(var++);
            vars.add(v);
        }

        return var;
    }

	/**
	 * @param arg                  the argument.
	 * @param typedValue           the normalized value of the argument.  It may be null.
	 */
    protected int inlineOneArgument(Function callee, ArrayList<Variable> vars,
                ASTreeList<ASTree> exprs, JVariable.Map map, int var, int paramIndex,
                ASTree arg, ASTree typedValue)
        throws NotFoundException 
    {
        JVariable p = callee.parameters()[paramIndex];
        if (arg instanceof JVariable && !p.isMutable()) {
            JVariable jva = (JVariable)arg;
            map.addAndMakeIdentical(p, jva);
            if (typedValue != null && !jva.type().isPrimitive()) {
                /*
                 * S s = new T(); s.foo();
                 * 
                 * Inlining s.foo() causes a problem since the type of s is S but
                 * foo() is translated under the assumption that s is of type T.
                 * So the type of s is changed into T. 
                 */
                jva.setType(typedValue.type());
            }
        }
        else
            if (!p.isMutable() && typedValue instanceof ObjectConstant) {
                /* Since the value of the parameter is statically determined,
                 * the variable will never appear in the inlined code.
                 */
                p.setValueAndType(typedValue);
            }
            else if (!p.isMutable() && isReadingFinalField(arg))
                ASTWalker.replace(callee.body(), p, arg);
            else
                var = inlineOneArgumentByDefault(callee, vars, exprs, var, paramIndex,
                                                 arg, typedValue, p);

        return var;
    }

    /**
     * @param arg                  the argument.
     * @param typedValue           the normalized value of the argument.  It may be null.
     * @param p                    the parameter variable.
     */
    protected int inlineOneArgumentByDefault(Function callee, ArrayList<Variable> vars,
            ASTreeList<ASTree> exprs, int var, int paramIndex,
            ASTree arg, ASTree typedValue, JVariable p)
        throws NotFoundException
    {
        p.setIdentifier(var++);
        vars.add(p);
        exprs.add(new Assign(p.type(), p, arg));
        if (!p.isMutable())
            p.setValueAndType(typedValue);

        return var;
    }

	private static boolean isReadingFinalField(ASTree expr) throws NotFoundException {
	    if (expr instanceof GetField) {
	        GetField getter = (GetField)expr;
	        if (getter.target() instanceof Variable) {
	            Variable var = (Variable)getter.target();
	            CtField f = getter.targetClass().getField(getter.fieldName());
	            return !var.isMutable() && Modifier.isFinal(f.getModifiers());
	        }
	        else
	            return isReadingFinalField(getter.target());
	    }

	    return false;
	}

	private static int maxLocalVariableNo(Function f) {
        int max = 0;
        for (Variable v: f.parameters())
            if (v.identifier() > max)
                max = v.identifier();

        for (Variable v: f.variables())
            if (v.identifier() > max)
                max = v.identifier();

        return max;
    }

    /**
     * Returns true if the function called by {@code call} can be
     * inlined.
     */
    private static boolean canInline(TraceContext context, Call call)
        throws NotFoundException
    {
        if (!context.doInlineAny())
            return false;

        ASTree parent = call.isStatement();
        if (parent == null)
            return false;
        else if (parent instanceof New) {
            New n = (New)parent;
            if (context.classTable().addClass(n.type()).instantiationIsSimple())
                return n.isStatement() != null;
            else
                return false;
        }
        else
            return true;
    }

    /**
     * Inlines a function body if the return type is void or if the call site is
     * an assignment expression and the right-hand expression is the call to that
     * function body, e.g. {@code a = foo();}.
     */
    private InlinedFunction inlineFunctionBody(TraceContext context,
                Function caller, Call call, ASTree target, ASTree[] args, Function callee)
        throws NotFoundException
    {
        ASTree parent = call.isStatement();
        if (parent instanceof New)
            parent = ((New)parent).isStatement();

        Assign assign = null;
        boolean dontReturn = true;
        if (parent instanceof Assign)
            assign = new Assign(parent.type(), ((Assign)parent).left(), null);
        else if (parent instanceof Return)
            dontReturn = false;
        else if (!(parent instanceof Block))
            throw new RuntimeException("fatal: " + parent.getClass().getName());

        ArrayList<Variable> vars = new ArrayList<Variable>();
        Block initBlock = new Block(0);
        JVariable.Map map = new JVariable.Map();
        int tmpVarNo = inlineArguments(context, caller, call, target, args, callee, vars, initBlock, map);
        caller.add(vars);
        ASTWalker.changeVariables(callee.body(), map);
        if (Options.deadcodeElimination)
            ASTWalker.eliminateDeadCode(callee.body());

        if (dontReturn) {
            TmpVariable tmpVar = replaceReturn(caller, callee, assign, tmpVarNo);
            if (tmpVar != null)
                caller.add(tmpVar);
        }

        InlinedFunction inline = context.metaclass().makeInlinedFunction(callee, callee.body(), initBlock);
        if (dontReturn)
            if (assign == null || assign.left() instanceof Variable)
                inline.isSimpleBlock(true);

        call.setCalledFunction(inline);
        return inline;
    }

    private static TmpVariable replaceReturn(Function caller, Function func,
                                             Assign assign, int varNo) {
        CtClass type = func.type();
        TmpVariable tmpVar;
        if (type == CtClass.voidType)
            tmpVar = null;
        else
            tmpVar = new TmpVariable(type, null, varNo);

        Body body = func.body();
        int nblocks = body.size();
        Block endB = new Block(nblocks);
        boolean found = false;
        for (int i = 0; i < nblocks; i++) {
            Block b = body.block(i);
            int size = b.size();
            for (int j = 0; j < size; j++) {
                ASTree statement = b.get(j);
                if (statement instanceof Return) {
                    ASTree expr = ((Return)statement).expression();
                    if (i == nblocks - 1 && j == size - 1) {    // if the last statement
                        if (expr == null)
                            b.remove(j);
                        else
                            if (found || assign == null) {
                                Assign a = new Assign(type, tmpVar, expr);
                                ASTWalker.updateParent(expr, a);
                                b.set(j, a);
                            }
                            else {
                                // the last statement is the only return statement
                                // in the body.
                                assign.setRight(expr);
                                ASTWalker.updateParent(expr, assign);
                                ASTree left = assign.left();
                                if (expr instanceof JVariable && left instanceof JVariable
                                    && expr.type() == left.type()) {
                                    JVariable.Map map = new JVariable.Map();
                                    map.addAndMakeIdentical((JVariable)expr, (JVariable)left);
                                    ASTWalker.changeVariables(body, map);
                                    caller.remove((JVariable)expr);
                                    b.remove(j);
                                    assign = null;
                                }
                                else
                                    b.set(j, assign);

                                tmpVar = null;      // not used
                            }
                    }
                    else {
                        found = true;
                        Goto go = new Goto(endB);
                        endB.isBranchTargetOf(go);
                        if (expr == null)
                            b.set(j, go);
                        else {
                            Assign a = new Assign(type, tmpVar, expr);
                            ASTWalker.updateParent(expr, a);
                            b.set(j, a);
                            b.add(j + 1, go);
                        }
                    }
                }
            }
        }

        if (endB.incomingJumps() > 0) {
            if (assign != null) {
                assign.setRight(tmpVar);
                endB.add(assign);
            }

            body.add(endB);
        }

        return tmpVar;
    }

    /**
     * Performs object inlining on all parameters to the inlined function.
     * It is only effective when the actual value of the parameter is
     * statically determined.  Programmers have to guarantee that no aliases
     * of the inlined objects are accessed during the execution of the function.
     */
    public static void inlineObjects(Function caller, InlinedFunction callee)
        throws NotFoundException
    {
        if (!callee.isExpression() && callee.body() instanceof Body) {
            ArrayList<JVariable> inlinedObjects = new ArrayList<JVariable>();
            for (JVariable v: callee.parameters())
                inlinedObjects.add(v);

            int varNo = maxLocalVariableNo(caller) + 1;
            ASTWalker.inlineObjects((Body)callee.body(), inlinedObjects,
                                    caller, callee, varNo);
        }
    }

    public static int updateLabels(ASTree ast, int seq) {
        InlinedFunction f;
        if (ast instanceof Assign)
            f = isInlinedCall(((Assign)ast).right());
        else if (ast instanceof Return)
            f = isInlinedCall(((Return)ast).expression());
        else if (ast instanceof Call)
            f = ((Call)ast).isInlined();
        else
            f = null;

        if (f != null) {
            ASTree body = f.body();
            if (body instanceof Body)
                for (Block b: (Body)body)
                    seq = b.setLable(seq);
        }

        return seq;
    }

    private static InlinedFunction isInlinedCall(ASTree expr) {
        Call c = toCall(expr);
        if (c == null)
            return null;
        else
            return c.isInlined();
    }

    public static Call toCall(ASTree expr) {
        if (expr instanceof Call)
            return (Call)expr;
        else if (expr instanceof New)
            return ((New)expr).constructor();

        return null;
    }
}
