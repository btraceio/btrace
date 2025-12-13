package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Abstract base class for data commands.
 * These commands are used to send monitoring data from the BTrace agent to the client.
 */
public abstract class BinaryDataCommand extends BinaryCommand {
    protected String name;

    protected BinaryDataCommand(byte type, String name) {
        super(type, false);
        this.name = name;
    }

    protected BinaryDataCommand(byte type) {
        this(type, null);
    }

    @Override
    protected void write(OutputStream out) throws IOException {
        BinaryProtocol.writeString(out, name);
    }

    @Override
    protected void read(InputStream in) throws IOException {
        name = BinaryProtocol.readString(in);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
} 