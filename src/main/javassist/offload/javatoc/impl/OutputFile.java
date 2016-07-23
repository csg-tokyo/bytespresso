// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc.impl;

import java.io.FileWriter;
import java.io.Writer;
import java.io.IOException;

/**
 * A generated source file.
 */
public class OutputFile extends OutputCode {
    private Writer output;

    public OutputFile(String fname)
        throws IOException
    {
        this(new FileWriter(fname));
    }

    public OutputFile(Writer w) throws IOException {
        output = w;
    }

    public void close() throws IOException {
        output.close();
    }

    public OutputCode append(char c) {
        try {
            output.append(c);
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public OutputCode append(int i) {
        try {
            output.append(Integer.toString(i));
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public OutputCode append(String s) {
        try {
            output.append(s);
            return this;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
