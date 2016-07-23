// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.reify;

import java.util.ArrayList;
import java.util.HashMap;

import javassist.NotFoundException;
import javassist.offload.ast.JVariable;
import javassist.offload.ast.Variable;

/**
 * A data-flow analyzer.  It maintains def-use relations.
 */
public class FlowAnalyzer {
    private HashMap<JVariable,ArrayList<JVariable>> variables;
    private UniqueID uid;

    /**
     * Constructs an analyzer.
     */
    public FlowAnalyzer(UniqueID uid) {
        this.variables = new HashMap<JVariable,ArrayList<JVariable>>();
        this.uid = uid;
    }

    /**
     * Specifies that the two variables are identical.
     * The type of v1 must be equivalent or a super type
     * of the type of v2. 
     */
    public void merge(JVariable v1, JVariable v2) {
        if (v1 == v2)
            return;

        ArrayList<JVariable> found1 = variables.get(v1);
        ArrayList<JVariable> found2 = variables.get(v2);
        if (found1 == null)
            if (found2 == null) {
                ArrayList<JVariable> list = new ArrayList<JVariable>();
                list.add(v1);
                list.add(v2);
                variables.put(v1, list);
                variables.put(v2, list);
            }
            else {
                // the uppermost type is the first element.
                JVariable fst = found2.get(0);
                try {
                    if (fst != v2 && fst.type().subtypeOf(v1.type()))
                        found2.add(0, v1);
                    else
                        found2.add(v1);
                }
                catch (NotFoundException e) {
                    throw new RuntimeException(e);
                }

                variables.put(v1, found2);
            }
        else
            if (found2 == null) {
                found1.add(v2);
                variables.put(v2, found1);
            }
            else {
                found1.addAll(found2);
                for (JVariable v: found2)
                    variables.put(v, found1);
            }
    }

    public void setVariableIdentity(JVariable v,  ArrayList<Variable> vars) {
        if (v.identifier() != JVariable.UNKNOWN_ID)
            return;

        if (vars != null)
            vars.add(v);

        ArrayList<JVariable> found = variables.get(v);
        if (found == null)
            v.setIdentifier(uid.varId());
        else {
            int id = uid.varId();
            JVariable idvar = found.get(0);
            idvar.setIdentifier(id);
            for (JVariable var: found)
                var.setIdentity(idvar);
        }
    }
}
