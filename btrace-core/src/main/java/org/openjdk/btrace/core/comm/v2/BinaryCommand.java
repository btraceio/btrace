package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Base class for all commands in the binary protocol.
 * This replaces the original Command class that relied on Java serialization.
 */
public abstract class BinaryCommand {
    // Command types - must match the original Command class for compatibility
    public static final byte ERROR = 0;
    public static final byte EVENT = 1;
    public static final byte EXIT = 2;
    public static final byte INSTRUMENT = 3;
    public static final byte MESSAGE = 4;
    public static final byte RENAME = 5;
    public static final byte STATUS = 6;
    public static final byte NUMBER_MAP = 7;
    public static final byte STRING_MAP = 8;
    public static final byte NUMBER = 9;
    public static final byte GRID_DATA = 10;
    public static final byte RETRANSFORMATION_START = 11;
    public static final byte RETRANSFORM_CLASS = 12;
    public static final byte SET_PARAMS = 13;
    public static final byte LIST_PROBES = 14;
    public static final byte DISCONNECT = 15;
    public static final byte RECONNECT = 16;

    public static final byte FIRST_COMMAND = ERROR;
    public static final byte LAST_COMMAND = RECONNECT;

    // Used for command registration and creation
    private static final Map<Byte, Supplier<BinaryCommand>> COMMAND_FACTORIES = new HashMap<>();

    // Register command factories
    static {
        // Commands will register themselves here
    }

    /**
     * Register a command factory for a specific command type
     */
    public static void registerCommand(byte type, Supplier<BinaryCommand> factory) {
        COMMAND_FACTORIES.put(type, factory);
    }

    /**
     * Create a command instance for the given type
     */
    public static BinaryCommand createCommand(byte type) {
        Supplier<BinaryCommand> factory = COMMAND_FACTORIES.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown command type: " + type);
        }
        return factory.get();
    }

    protected byte type;
    private boolean urgent;

    protected BinaryCommand(byte type) {
        this(type, true);
    }

    protected BinaryCommand(byte type, boolean urgent) {
        if (type < FIRST_COMMAND || type > LAST_COMMAND) {
            throw new IllegalArgumentException("Invalid command type: " + type);
        }
        this.type = type;
        this.urgent = urgent;
    }

    /**
     * Write this command to the output stream
     */
    protected abstract void write(OutputStream out) throws IOException;

    /**
     * Read this command from the input stream
     */
    protected abstract void read(InputStream in) throws IOException;

    /**
     * Get the type of this command
     */
    public final byte getType() {
        return type;
    }

    /**
     * Check if this command needs urgent processing
     */
    public final boolean isUrgent() {
        return urgent;
    }

    /**
     * Set this command as urgent
     */
    final void setUrgent() {
        urgent = true;
    }
} 