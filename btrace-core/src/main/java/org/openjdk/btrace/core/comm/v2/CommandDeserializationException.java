package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;

/**
 * Exception thrown when deserialization of a command fails.
 */
public class CommandDeserializationException extends IOException {
    private final byte commandType;

    public CommandDeserializationException(byte commandType, String message, Throwable cause) {
        super(String.format("Failed to deserialize command (type=%d): %s",
            commandType, message), cause);
        this.commandType = commandType;
    }

    public CommandDeserializationException(byte commandType, Throwable cause) {
        super(String.format("Failed to deserialize command (type=%d)", commandType), cause);
        this.commandType = commandType;
    }

    public byte getCommandType() {
        return commandType;
    }
}
