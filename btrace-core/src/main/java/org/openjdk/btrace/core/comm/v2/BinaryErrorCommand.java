package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Binary implementation of the ErrorCommand.
 * This command is used to send error information from the BTrace agent to the client.
 */
public class BinaryErrorCommand extends BinaryCommand {
    private String exceptionClass;
    private String message;
    private String stackTrace;

    static {
        // Register this command type
        BinaryCommand.registerCommand(ERROR, BinaryErrorCommand::new);
    }

    public BinaryErrorCommand(String exceptionClass, String message, String stackTrace) {
        super(ERROR, true);
        this.exceptionClass = exceptionClass;
        this.message = message;
        this.stackTrace = stackTrace;
    }

    public BinaryErrorCommand() {
        this(null, null, null);
    }

    @Override
    protected void write(OutputStream out) throws IOException {
        BinaryProtocol.writeString(out, exceptionClass);
        BinaryProtocol.writeString(out, message);
        BinaryProtocol.writeString(out, stackTrace);
    }

    @Override
    protected void read(InputStream in) throws IOException {
        exceptionClass = BinaryProtocol.readString(in);
        message = BinaryProtocol.readString(in);
        stackTrace = BinaryProtocol.readString(in);
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public void setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }
} 
