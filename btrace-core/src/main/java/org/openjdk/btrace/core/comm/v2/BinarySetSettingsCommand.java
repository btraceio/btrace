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
    private static final ScalarEncoding SCALAR =
        new ScalarEncoding((byte)0, TYPE_STRING, TYPE_INTEGER, TYPE_LONG, TYPE_FLOAT, TYPE_DOUBLE, TYPE_BOOLEAN);

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
            BinaryProtocol.writeString(out, entry.getKey());
            SCALAR.writeValue(out, entry.getValue());
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
            
            Object value = SCALAR.readValue(in);
            
            // Store the parameter
            params.put(key, value);
        }
    }
}
