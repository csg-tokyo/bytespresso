// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

public interface Visitor {
    void visit(AdhocASTList list) throws VisitorException;
    void visit(Array array) throws VisitorException;
    void visit(Assign assign) throws VisitorException;
    void visit(BinaryOp binaryOp) throws VisitorException;
    void visit(Block block) throws VisitorException;
    void visit(Body body) throws VisitorException;
    void visit(Branch branch) throws VisitorException;
    void visit(Call call) throws VisitorException;
    void visit(Cast cast) throws VisitorException;
    void visit(ClassConstant classConstant) throws VisitorException;
    void visit(Coercion coercion) throws VisitorException;
    void visit(Comma comma) throws VisitorException;
    void visit(DoubleConstant doubleConstant) throws VisitorException;
    void visit(FloatConstant floatConstant) throws VisitorException;
    void visit(For.Begin begin) throws VisitorException;
    void visit(For.End end) throws VisitorException;
    void visit(For.Empty empty) throws VisitorException;
    void visit(Function function) throws VisitorException;
    void visit(GetField getField) throws VisitorException;
    void visit(Goto statement) throws VisitorException;
    void visit(Iinc iinc) throws VisitorException;
    void visit(InlinedFunction f) throws VisitorException;
    void visit(InstanceOf instanceOf) throws VisitorException;
    void visit(IntConstant intConstant) throws VisitorException;
    void visit(JVariable jVariable) throws VisitorException;
    void visit(LongConstant longConstant) throws VisitorException;
    void visit(Monitor monitor) throws VisitorException;
    void visit(New expr) throws VisitorException;
    void visit(NewArray newArray) throws VisitorException;
    void visit(Null expr) throws VisitorException;
    void visit(ObjectConstant expr) throws VisitorException;
    void visit(PutField putField) throws VisitorException;
    void visit(Return statement) throws VisitorException;
    void visit(AdhocAST ast) throws VisitorException;
    void visit(StringConstant stringConstant) throws VisitorException;
    void visit(Switch statement) throws VisitorException;
    void visit(Throw statement) throws VisitorException;
    void visit(TmpVariable tmpVariable) throws VisitorException;
    void visit(UnaryOp unaryOp) throws VisitorException;
}
