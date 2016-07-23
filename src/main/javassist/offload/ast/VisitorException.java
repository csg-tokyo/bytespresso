// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.ast;

import javassist.NotFoundException;

/**
 * An exception that a visitor may throw.
 *
 * @see Visitor
 */
@SuppressWarnings("serial")
public class VisitorException extends Exception {
    /**
     * Constructs an exception.
     */
    public VisitorException(String string) {
        super(string);
    }

    /**
     * Constructs an exception.
     */
    public VisitorException(String msg, Throwable cause) {
        super(msg + " because of " + cause.toString(), cause);
    }

    /**
     * Constructs an exception.
     */
    public VisitorException(NotFoundException e) {
        super(e.getMessage(), e);
    }
}
