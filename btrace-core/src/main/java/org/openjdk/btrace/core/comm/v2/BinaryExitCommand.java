package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Binary implementation of the ExitCommand.
 * This command is used to signal the BTrace agent to exit.
 */
public class BinaryExitCommand extends BinaryCommand {
    private int exitCode;

    static {
        // Register this command type
        BinaryCommand.registerCommand(EXIT, BinaryExitCommand::new);
    }

    public BinaryExitCommand(int exitCode) {
        super(EXIT, true);
        this.exitCode = exitCode;
    }

    public BinaryExitCommand() {
        this(0);
    }

    @Override
    protected void write(OutputStream out) throws IOException {
        BinaryProtocol.writeInt(out, exitCode);
    }

    @Override
    protected void read(InputStream in) throws IOException {
        exitCode = BinaryProtocol.readInt(in);
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }
} 