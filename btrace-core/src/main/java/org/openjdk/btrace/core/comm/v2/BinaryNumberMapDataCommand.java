package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binary implementation of the NumberMapDataCommand.
 * This command is used to send numeric map data from the BTrace agent to the client.
 */
public class BinaryNumberMapDataCommand extends BinaryDataCommand {
    private Map<String, Number> data = new LinkedHashMap<>();

    static {
        // Register this command type
        BinaryCommand.registerCommand(NUMBER_MAP, BinaryNumberMapDataCommand::new);
    }

    public BinaryNumberMapDataCommand(String name, Map<String, Number> data) {
        super(NUMBER_MAP, name);
        if (data != null) {
            this.data.putAll(data);
        }
    }

    public BinaryNumberMapDataCommand() {
        this(null, null);
    }

    @Override
    protected void write(OutputStream out) throws IOException {
        // Write the name
        super.write(out);
        
        // Write the map size
        BinaryProtocol.writeInt(out, data.size());
        
        // Write each map entry
        for (Map.Entry<String, Number> entry : data.entrySet()) {
            BinaryProtocol.writeString(out, entry.getKey());
            
            Number value = entry.getValue();
            if (value == null) {
                BinaryProtocol.writeByte(out, (byte) 0);
            } else if (value instanceof Integer) {
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
                // Default to long for other number types
                BinaryProtocol.writeByte(out, (byte) 2);
                BinaryProtocol.writeLong(out, value.longValue());
            }
        }
    }

    @Override
    protected void read(InputStream in) throws IOException {
        // Read the name
        super.read(in);
        
        // Read the map size
        int size = BinaryProtocol.readInt(in);
        
        // Clear any existing data
        data.clear();
        
        // Read each map entry
        for (int i = 0; i < size; i++) {
            String key = BinaryProtocol.readString(in);
            
            byte type = BinaryProtocol.readByte(in);
            Number value = null;
            
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
            
            data.put(key, value);
        }
    }

    public Map<String, Number> getData() {
        return new LinkedHashMap<>(data);
    }

    public void setData(Map<String, Number> data) {
        this.data.clear();
        if (data != null) {
            this.data.putAll(data);
        }
    }
} 