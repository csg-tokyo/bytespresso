// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import java.util.HashMap;

import javassist.CtClass;

public class For {
    /**
     * An opening brace of {@code for} statement.
     * The jump target of this object indicates the block after the closing brace.
     */
    public static class Begin extends ASTree implements Jump {
        private ASTree initializer;
        private Branch condition;
        private ASTree step;

        /**
         * Constructs an opening brace.
         * All the arguments must be non-null.
         */
        public Begin(ASTree init, Branch cond, ASTree s) {
            initializer = init;
            condition = cond;
            step = s;
        }

        /**
         * Constructs an opening brace.
         * All the arguments must be non-null.
         */
        public Begin(ASTree init, Branch cond, ASTree step, Goto g) {
            this(init, new Branch(cond, g), step);
        }

        protected void deepCopy(HashMap<ASTree,ASTree> map) {
            super.deepCopy(map);
            initializer = copy(initializer, map);
            condition = copy(condition, map);
            step = copy(step, map);
        }

        /**
         * Returns the initializer expression.  It may be null.
         */
        public ASTree initializer() { return initializer; }

        /**
         * Returns the condition expression.  It may be null.
         */
        public Branch condition() { return condition; }

        /**
         * Returns the iteration-step expression.  It may be null.
         */
        public ASTree step() { return step; }

        public int numChildren() { return 3; }

        public ASTree child(int n) {
            if (n == 0)
                return initializer;
            else if (n == 1)
                return condition;
            else if (n == 2)
                return step;
            else
                return super.child(n);
        }

        public void setChild(int n, ASTree c) {
            if (n == 0)
                initializer = c;
            else if (n == 1)
                condition = (Branch)c;
            else if (n == 2)
                step = c;

            super.setChild(n, c);
        }

        public CtClass type() { return CtClass.voidType; }

        public boolean always() { return false; }

        public String toString() {
            String init = initializer == null ? "" : initializer.toString();
            String cond = condition == null ? "" : condition.conditionToString();
            String s = step == null ? "" : step.toString();
            return "for (" + init + ";" + cond + ";" + s + ") BEGIN"; 
        }

        public int outputs() { return condition.outputs(); }

        public int output(int i) { return condition.output(i); }

        public Block jumpTo(int i) { return condition.jumpTo(i); }

        public void setTarget(Function f) {
            condition.setTarget(f);
        }

        public void accept(Visitor v) throws VisitorException {
            v.visit(this);
        }
    }

    /**
     * A closing brace of {@code for} statement.
     * The jump target of this object indicates the first block of the body.
     * The opening brace is the first element of the jump target. 
     */
    public static class End extends JumpNode {
        /**
         * Constructs a closing brace.
         */
        public End(int beginIndex, Block begin) {
            super(beginIndex, begin);
        }

        public CtClass type() { return CtClass.voidType; }

        /**
         * Returns false since this object will substitute a {@code Branch} object.
         */
        public boolean always() { return false; }

        public String toString() { return "END"; }

        public void accept(Visitor v) throws VisitorException {
            v.visit(this);
        }
    }

    /**
     * A singleton instance of {@code Empty}.
     */
    public static final Empty EMPTY = new Empty();

    /**
     * An empty expression/statement.
     */
    public static class Empty extends ASTree {
        public CtClass type() { return CtClass.voidType; }
        public String toString() { return ""; }

        public void accept(Visitor v) throws VisitorException {
            v.visit(this);
        }
    }
}
