package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Abstract base class for commands that contain a single string payload.
 * This is used for simple commands like EventCommand, RenameCommand, etc.
 */
public abstract class BinaryStringCommand extends BinaryCommand {
    protected String payload;

    protected BinaryStringCommand(byte type, String payload) {
        super(type, true);
        this.payload = payload;
    }

    protected BinaryStringCommand(byte type) {
        this(type, null);
    }

    @Override
    protected void write(OutputStream out) throws IOException {
        BinaryProtocol.writeString(out, payload);
    }

    @Override
    protected void read(InputStream in) throws IOException {
        payload = BinaryProtocol.readString(in);
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
} 