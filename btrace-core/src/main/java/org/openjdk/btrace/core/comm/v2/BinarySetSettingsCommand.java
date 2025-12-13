package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Binary implementation of the SetSettingsCommand.
 * This command is used to configure the BTrace agent settings.
 */
public class BinarySetSettingsCommand extends BinaryCommand {
    // Supported parameter types and their type codes
    private static final byte TYPE_STRING = 1;
    private static final byte TYPE_INTEGER = 2;
    private static final byte TYPE_LONG = 3;
    private static final byte TYPE_BOOLEAN = 4;
    private static final byte TYPE_FLOAT = 5;
    private static final byte TYPE_DOUBLE = 6;

    private final Map<String, Object> params;

    static {
        // Register this command type
        BinaryCommand.registerCommand(SET_PARAMS, BinarySetSettingsCommand::new);
    }

    public BinarySetSettingsCommand(Map<String, ?> params) {
        super(SET_PARAMS, true);
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
    }

    public BinarySetSettingsCommand() {
        this(null);
    }

    public Map<String, Object> getParams() {
        return new HashMap<>(params);
    }

    @Override
    protected void write(OutputStream out) throws IOException {
        // Write the number of parameters
        BinaryProtocol.writeInt(out, params.size());
        
        // Write each parameter
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            // Write the parameter key
            BinaryProtocol.writeString(out, entry.getKey());
            
            // Write the parameter value based on its type
            Object value = entry.getValue();
            
            if (value == null) {
                // Write a null value (type code 0)
                BinaryProtocol.writeByte(out, (byte) 0);
            } else if (value instanceof String) {
                BinaryProtocol.writeByte(out, TYPE_STRING);
                BinaryProtocol.writeString(out, (String) value);
            } else if (value instanceof Integer) {
                BinaryProtocol.writeByte(out, TYPE_INTEGER);
                BinaryProtocol.writeInt(out, (Integer) value);
            } else if (value instanceof Long) {
                BinaryProtocol.writeByte(out, TYPE_LONG);
                BinaryProtocol.writeLong(out, (Long) value);
            } else if (value instanceof Boolean) {
                BinaryProtocol.writeByte(out, TYPE_BOOLEAN);
                BinaryProtocol.writeBoolean(out, (Boolean) value);
            } else if (value instanceof Float) {
                BinaryProtocol.writeByte(out, TYPE_FLOAT);
                BinaryProtocol.writeFloat(out, (Float) value);
            } else if (value instanceof Double) {
                BinaryProtocol.writeByte(out, TYPE_DOUBLE);
                BinaryProtocol.writeDouble(out, (Double) value);
            } else {
                // For unsupported types, convert to string
                BinaryProtocol.writeByte(out, TYPE_STRING);
                BinaryProtocol.writeString(out, value.toString());
            }
        }
    }

    @Override
    protected void read(InputStream in) throws IOException {
        // Read the number of parameters
        int size = BinaryProtocol.readInt(in);
        
        // Clear any existing parameters
        params.clear();
        
        // Read each parameter
        for (int i = 0; i < size; i++) {
            // Read the parameter key
            String key = BinaryProtocol.readString(in);
            
            // Read the parameter type
            byte type = BinaryProtocol.readByte(in);
            
            // Read the parameter value based on its type
            Object value = null;
            
            switch (type) {
                case 0: // null
                    value = null;
                    break;
                case TYPE_STRING:
                    value = BinaryProtocol.readString(in);
                    break;
                case TYPE_INTEGER:
                    value = BinaryProtocol.readInt(in);
                    break;
                case TYPE_LONG:
                    value = BinaryProtocol.readLong(in);
                    break;
                case TYPE_BOOLEAN:
                    value = BinaryProtocol.readBoolean(in);
                    break;
                case TYPE_FLOAT:
                    value = BinaryProtocol.readFloat(in);
                    break;
                case TYPE_DOUBLE:
                    value = BinaryProtocol.readDouble(in);
                    break;
                default:
                    throw new IOException("Unsupported parameter type: " + type);
            }
            
            // Store the parameter
            params.put(key, value);
        }
    }
} 