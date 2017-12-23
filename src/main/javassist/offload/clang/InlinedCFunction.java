// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.clang;

import javassist.NotFoundException;
import javassist.offload.ast.ASTree;
import javassist.offload.ast.Block;
import javassist.offload.ast.Body;
import javassist.offload.ast.Call;
import javassist.offload.ast.Function;
import javassist.offload.ast.InlinedFunction;
import javassist.offload.ast.VisitorException;

public class InlinedCFunction extends InlinedFunction implements CallableCode {
    protected InlinedCFunction(Function f, ASTree body) {
        super(f, body);
    }

    protected InlinedCFunction(Function f, Body body, Block initBlock) {
        super(f, body, initBlock);
    }

    public void callerCode(CodeGen gen, Call expr) throws VisitorException {
        gen.append(body());
    }

    public void code(CodeGen gen) throws VisitorException {
        gen.append("/* inlined ").append(name()).append("() */\n");
    }

    public ASTree value() throws NotFoundException {
        if (body() != null)
            return body().value();
        else
            return super.value();
    }
}
