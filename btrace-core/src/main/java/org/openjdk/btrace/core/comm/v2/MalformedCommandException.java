package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;

/**
 * Exception thrown when a command has invalid structure or data.
 */
public class MalformedCommandException extends IOException {
    private final byte commandType;

    public MalformedCommandException(byte commandType, String message) {
        super(String.format("Malformed command (type=%d): %s", commandType, message));
        this.commandType = commandType;
    }

    public MalformedCommandException(byte commandType, String message, Throwable cause) {
        super(String.format("Malformed command (type=%d): %s", commandType, message), cause);
        this.commandType = commandType;
    }

    public byte getCommandType() {
        return commandType;
    }
}
