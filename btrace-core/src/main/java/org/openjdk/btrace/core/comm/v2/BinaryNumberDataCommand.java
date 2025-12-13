package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Binary implementation of the NumberDataCommand.
 * This command is used to send numeric data from the BTrace agent to the client.
 */
public class BinaryNumberDataCommand extends BinaryDataCommand {
    private Number value;

    static {
        // Register this command type
        BinaryCommand.registerCommand(NUMBER, BinaryNumberDataCommand::new);
    }

    public BinaryNumberDataCommand(String name, Number value) {
        super(NUMBER, name);
        this.value = value;
    }

    public BinaryNumberDataCommand() {
        this(null, null);
    }

    @Override
    protected void write(OutputStream out) throws IOException {
        // Write the name
        super.write(out);
        
        // Write the value type
        if (value == null) {
            BinaryProtocol.writeByte(out, (byte) 0);
            return;
        }
        
        if (value instanceof Integer) {
            BinaryProtocol.writeByte(out, (byte) 1);
            BinaryProtocol.writeInt(out, (Integer) value);
        } else if (value instanceof Long) {
            BinaryProtocol.writeByte(out, (byte) 2);
            BinaryProtocol.writeLong(out, (Long) value);
        } else if (value instanceof Float) {
            BinaryProtocol.writeByte(out, (byte) 3);
            BinaryProtocol.writeFloat(out, (Float) value);
        } else if (value instanceof Double) {
            BinaryProtocol.writeByte(out, (byte) 4);
            BinaryProtocol.writeDouble(out, (Double) value);
        } else {
            // Default to long
            BinaryProtocol.writeByte(out, (byte) 2);
            BinaryProtocol.writeLong(out, value.longValue());
        }
    }

    @Override
    protected void read(InputStream in) throws IOException {
        // Read the name
        super.read(in);
        
        // Read the value type
        byte type = BinaryProtocol.readByte(in);
        
        switch (type) {
            case 0: // null
                value = null;
                break;
            case 1: // Integer
                value = BinaryProtocol.readInt(in);
                break;
            case 2: // Long
                value = BinaryProtocol.readLong(in);
                break;
            case 3: // Float
                value = BinaryProtocol.readFloat(in);
                break;
            case 4: // Double
                value = BinaryProtocol.readDouble(in);
                break;
            default:
                throw new IOException("Unsupported number type: " + type);
        }
    }

    public Number getValue() {
        return value;
    }

    public void setValue(Number value) {
        this.value = value;
    }
} 