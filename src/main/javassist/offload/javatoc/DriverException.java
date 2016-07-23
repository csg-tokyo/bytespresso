// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc;

import javassist.offload.lib.Util;

@SuppressWarnings("serial")
public class DriverException extends Exception {
    private int status;

    public DriverException(String msg, int status) {
        super(makeErrorMessage(msg, status));
        this.status = status;
    }

    public DriverException(String msg, Throwable t, int status) {
        super(makeErrorMessage(msg, status), t);
        this.status = status;
    }

    public DriverException(Throwable t, int status) { this(t.getMessage(), t, status); }

    public DriverException(String msg) { this(msg, 0); }

    public DriverException(Throwable t) { this(t.getMessage(), t, 0); }

    public DriverException(String msg, Throwable t) { this(msg, t, 0); }

    public int status() { return status; }

    public static String makeErrorMessage(String s, int status) {
        StringBuilder msg = new StringBuilder();
        if (s != null)
            msg.append(s);

        if (status != 0) {
            if (s != null)
                msg.append("; ");

            msg.append("the task exited with ").append(status);
            if (status == Util.ERR_DESERIALIZE)
                msg.append(" (deserialization failure)");
            else if (status == Util.ERR_DISPATCH)
                msg.append(" (method dispatch by an unknown type)");
            else if (status == Util.ERR_CALLBACK)
                msg.append(" (callback failure)");
            else if (status == 133)
                msg.append(" (SIGTRAP - divide by zero?)");
            else if (status == 138)
                msg.append(" (SIGBUS?)");
            else if (status == 139)
                msg.append(" (SIGSEGV?)");
        }

        return msg.toString();
    }
}
