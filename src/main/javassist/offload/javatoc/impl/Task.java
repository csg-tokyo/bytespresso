// Copyright (C) 2016- Shigeru Chiba.  All Rights Reserved.

package javassist.offload.javatoc.impl;

import java.io.*;
import java.util.ArrayList;

import javassist.offload.javatoc.DriverException;

/**
 * A process running an external command.
 *
 * <p>Example:</p>
 *
 * <p><code>new Task("ls -l").run()</code> executes "<code>ls -l</code>".
 * <p><code>new Task("ls *.java").pipe("wc").run()</code> executes
 * "<code>ls *.java | wc</code>".
 * <p><code>new Task("sort").in("input").out("output").run()</code> executes
 * "<code>sort &lt; input &gt; output</code>".
 */
public class Task {
    private Sender sender;
    private InputStream inputFile;
    private Task inputTask;

    private Receiver receiver;
    private File output;
    private File error;

    private Communicator communicator;

    private String[] commands;
    private String[] environment;
    private File workingDir;

    /**
     * Creates a new process.
     *
     * @param cmd  an array containing the command name and the arguments.
     */
    public Task(String[] cmd) {
        commands = cmd;
        environment = null;
        workingDir = null;
        communicator = null;
        sender = null;
        inputFile = null;
        inputTask = null;
        receiver = null;
        output = null;
        error = null;
    }

    /**
     * Creates a new process.
     *
     * @param cmd       the command line.
     */
    public Task(String cmd) {
        this(parseCmd(cmd));
    }

    private static String[] parseCmd(String cmd) {
        if (cmd.equals(""))
            return new String[0];

        ArrayList<String> cmds = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        int len = cmd.length();

        for (int i = 0; i < len; i++) {
            char c = cmd.charAt(i);
            if (c == ' ') {
                if (sb.length() > 0) {
                    cmds.add(sb.toString());
                    sb = new StringBuilder();
                }

                while (i + 1 < len && cmd.charAt(i + 1) == ' ')
                    ++i;
            }
            else if (c == '\'') {
                do {
                    sb.append(cmd.charAt(i++));
                } while (i < len && cmd.charAt(i) != '\'');
                sb.append('\'');
            }
            else if (c == '"') {
                do {
                    sb.append(cmd.charAt(i++));
                } while (i < len && cmd.charAt(i) != '"');
                sb.append('"');
            }
            else
                sb.append(cmd.charAt(i));
        }

        if (sb.length() > 0)
            cmds.add(sb.toString());

        return cmds.toArray(new String[cmds.size()]);
    }

    /**
     * A communicator to the task.
     */
    public static interface Communicator {
        /**
         * Exchanges data with the task.
         *
         * @param in        from the standard error.
         * @param out       to the standard input.
         */
        void start(InputStream in, OutputStream out) throws IOException;
    }

    /**
     * Redirects the standard input/error stream
     * from/to the communicator.
     */
    public Task inout(Communicator c) {
        communicator = c;
        inputFile = null;
        sender = null;
        inputTask = null;
        receiver = null;
        output = null;
        return this;
    }

    /**
     * Redirects the standard input stream from the specified file.
     *
     * @return this object.
     */
    public Task in(String fileName) throws FileNotFoundException {
        return in(new File(fileName));
    }

    /**
     * Redirects the standard input stream from the specified file.
     *
     * @return this object.
     */
    public Task in(File file) throws FileNotFoundException {
        return in(new FileInputStream(file));
    }

    /**
     * Redirects the standard input stream from the specified stream
     * such as a <code>java.io.ByteArrayInputStream</code> object.
     */
    public Task in(InputStream is) {
        inputFile = is;
        sender = null;
        inputTask = null;
        communicator = null;
        return this;
    }

    /**
     * A sender to the task.
     */
    public static interface Sender {
        /**
         * Writes the data to the given output stream.
         */
        void write(OutputStream out) throws IOException;
    }

    /**
     * Redirects the standard input stream from the specified sender.
     */
    public Task in(Sender s) {
        sender = s;
        inputFile = null;
        inputTask = null;
        communicator = null;
        return this;
    }

    /**
     * Redirects the standard output stream to the specified file.
     *
     * @return this object.
     */
    public Task out(String fileName) {
        return out(new File(fileName));
    }

    /**
     * Redirects the standard output stream to the specified file.
     *
     * @return this object.
     */
    public Task out(File file) {
        output = file;
        receiver = null;
        communicator = null;
        return this;
    }

    /**
     * A receiver from the task.
     */
    public static interface Receiver {
        /**
         * Reads the data from the given input stream.
         * It has to read all the data until it receives
         * the end of stream.
         *
         * @param is            the input stream.
         */
        void read(InputStream is) throws IOException;
    }

    /**
     * Redirects the standard error stream to the specified
     * receiver.
     */
    public Task out(Receiver r) {
        receiver = r;
        output = null;
        communicator = null;
        return this;
    }

    /**
     * Creates a new process and connects the standard output stream
     * of this process to the standard input stream of that new
     * process.
     *
     * @param cmds  an array containing the command name and the arguments.
     * @return the process created.
     */
    public Task pipe(String[] cmds) {
        Task t = new Task(cmds);
        t.sender = null;
        t.inputFile = null;
        t.inputTask = this;
        output = null;
        receiver = null;
        communicator = null;
        return t;
    }

    /**
     * Creates a new process and connects the standard output stream
     * of this process to the standard input stream of that new
     * proces.
     *
     * @param cmds          the command line.
     * @return the process created.
     */
    public Task pipe(String cmd) {
        return pipe(parseCmd(cmd));
    }

    /**
     * Redirects the standard error stream to the specified file.
     *
     * @return this object.
     */
    public Task err(String fileName) {
        return err(new File(fileName));
    }

    /**
     * Redirects the standard error stream to the specified file.
     *
     * @return this object.
     */
    public Task err(File file) {
        error = file;
        return this;
    }

    /**
     * Sets the working directory.
     * 
     * @return this object.
     */
    public Task dir(File f) {
        workingDir = f;
        return this;
    }

    /**
     * Sets the environment variables.
     *
     * @param e     each element is in format <i>name</i>=<i>value</i>.
     * @return this object.
     */
    public Task env(String[] e) {
        environment = e;
        return this;
    }

    private InputStream getInput() throws IOException {
        if (inputFile != null)
            return inputFile;
        else if (inputTask != null)
            return inputTask.run2().getInputStream();
        else
            return null;
    }

    /**
     * Starts the process.
     *
     * @return the exit status.  It is zero if the process
     * normally terminates. 
     */
    public int run() throws DriverException {
        Process proc = null;
        InputStream is = null;
        try {
            proc = run2();
            is = proc.getErrorStream();
            if (communicator != null)
                communicator.start(is, proc.getOutputStream());
            else if (receiver != null)
                receiver.read(is);
            else if (error == null)
                printAll(is, System.err);
            else
                writeAll(is, error);
        }
        catch (Throwable e) {
            if (proc != null)
                proc.destroy();

            try {
                if (proc != null)
                    proc.waitFor();
            } catch (InterruptedException e1) {
                throw new DriverException(e);
            }

            int status;
            if (proc == null)
                status = 0;
            else
                status = proc.exitValue();

            throw new DriverException(e, status);
        }
        finally {
            if (is != null)
                try {
                    is.close();
                }
                catch (IOException e) {}
        }

        try {
            return proc.waitFor();
        }
        catch (InterruptedException e) {
            throw new DriverException(e);
        }
    }

    Process run2() throws IOException {
        final InputStream input = getInput();
        final Process proc;
        try {
            proc = Runtime.getRuntime().exec(commands,
                                             environment, workingDir);
        }
        catch (IOException e) {
            if (input != null)
                input.close();

            throw e;
        }

        /* If this.communicator != null, then
         * both input and sender are null.
         */
        if (input != null || sender != null)
            new Thread() {
                public void run() {
                    OutputStream outs = proc.getOutputStream();
                    try {
                        if (sender == null)
                            connectStreams(input, outs);
                        else
                            sender.write(outs);
                            
                    }
                    catch (IOException e){
                        printWarning(e);
                        proc.destroy();
                    }
                    try {
                        if (input != null)
                            input.close();
                    }
                    catch (IOException e){}
                    try {
                        outs.close();
                    }
                    catch (IOException e){}
                }
            }.start();

        new Thread() {
            public void run() {
                InputStream is = proc.getInputStream();
                try {
                    if (output == null)
                        printAll(is, System.out);
                    else
                        writeAll(is, output);
                }
                catch (IOException e) {
                    printWarning(e);
                    proc.destroy();
                }

                try {
                    is.close();
                }
                catch (IOException e) {}
            }
        }.start();

        return proc;
    }

    static void printWarning(Exception e) {
        System.err.println("Broken pipe: " + e);
    }

    private static void printAll(InputStream in, PrintStream out)
        throws IOException
    {
        BufferedReader reader
            = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = reader.readLine()) != null)
            out.println(line);
    }

    private static void writeAll(InputStream in, File fout)
        throws IOException
    {
        OutputStream out = new FileOutputStream(fout);
        try {
            connectStreams(in, out);
        }
        finally {
            out.close();
        }
    }

    private static void connectStreams(InputStream in, OutputStream out)
        throws IOException
    {
        byte[] buffer = new byte[4096 * 8];
        int len;
        while ((len = in.read(buffer)) >= 0)
            out.write(buffer, 0, len);
    }

}
