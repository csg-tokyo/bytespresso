// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc.impl;

import java.util.ArrayList;
import java.util.HashMap;

import javassist.CtClass;
import javassist.offload.clang.CTypeDef;

/**
 * Ordered Typedef set.
 */
public class OrderedClasses implements CTypeDef.OrderedTypes {
    private HashMap<CTypeDef,ArrayList<CTypeDef>> classes;
    private ClassTableForC classTable;

    /**
     * Constructs a set.
     */
    public OrderedClasses(ClassTableForC ct) {
        classes = new HashMap<CTypeDef,ArrayList<CTypeDef>>();
        classTable = ct;
    }

    public CTypeDef typeDef(CtClass cc) { return classTable.typeDef(cc); }

    /**
     * Records that t1 requires t2.
     */
    public void require(CTypeDef t1, CTypeDef t2) {
       ArrayList<CTypeDef> list = classes.get(t1);
       if (list == null) {
           list = new ArrayList<CTypeDef>();
           classes.put(t1, list);
       }

       for (CTypeDef t: list)
           if (t == t2)
               return;

       list.add(t2);
    }

    /**
     * Returns an ordered sequence of the classes contained in this set.
     *
     * @param set       the types included in the returned sequence are added to this set.
     */
    public ArrayList<CTypeDef> get(HashMap<CTypeDef,CTypeDef> set) {
        ArrayList<CTypeDef> list = new ArrayList<CTypeDef>(classes.size());
        for (CTypeDef t: classes.keySet())
            visit(t, list, set);

        return list;
    }

    private void visit(CTypeDef type, ArrayList<CTypeDef> list, HashMap<CTypeDef,CTypeDef> visited) {
        if (visited.put(type, type) == null) {
            ArrayList<CTypeDef> req = classes.get(type);
            if (req != null)
                for (CTypeDef t: req)
                    visit(t, list, visited);

            list.add(type);
        }
    }
}
