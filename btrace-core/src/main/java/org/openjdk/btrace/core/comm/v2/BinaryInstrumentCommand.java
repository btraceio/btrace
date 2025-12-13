package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.openjdk.btrace.core.ArgsMap;

/**
 * Binary implementation of the InstrumentCommand.
 * This command is used to send BTrace code to the target VM for instrumentation.
 */
public class BinaryInstrumentCommand extends BinaryCommand {
    private byte[] code;
    private ArgsMap args;

    static {
        // Register this command type
        BinaryCommand.registerCommand(INSTRUMENT, BinaryInstrumentCommand::new);
    }

    public BinaryInstrumentCommand(byte[] code, ArgsMap args) {
        super(INSTRUMENT, true);
        this.code = code;
        this.args = args;
    }

    public BinaryInstrumentCommand(byte[] code, String[] args) {
        this(code, new ArgsMap(args));
    }

    public BinaryInstrumentCommand(byte[] code, Map<String, String> args) {
        this(code, new ArgsMap(args));
    }

    public BinaryInstrumentCommand() {
        this(null, (Map<String, String>) null);
    }

    @Override
    protected void write(OutputStream out) throws IOException {
        // Write code bytes
        BinaryProtocol.writeByteArray(out, code);
        
        // Write args count
        BinaryProtocol.writeInt(out, args.size());
        
        // Write args
        for (Map.Entry<String, String> e : args) {
            BinaryProtocol.writeString(out, e.getKey());
            BinaryProtocol.writeString(out, e.getValue() != null ? e.getValue() : "");
        }
    }

    @Override
    protected void read(InputStream in) throws IOException {
        // Read code bytes
        code = BinaryProtocol.readByteArray(in);
        
        // Read args count
        int argsCount = BinaryProtocol.readInt(in);
        
        // Read args
        args = new ArgsMap(argsCount);
        for (int i = 0; i < argsCount; i++) {
            String key = BinaryProtocol.readString(in);
            String val = BinaryProtocol.readString(in);
            args.put(key, val);
        }
    }

    public byte[] getCode() {
        return code;
    }

    public ArgsMap getArguments() {
        return args;
    }
} 