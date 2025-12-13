package org.openjdk.btrace.core.comm.v2;

/**
 * Binary implementation of the ReconnectCommand.
 * This command is used to reconnect to a running BTrace agent.
 */
public class BinaryReconnectCommand extends BinaryStringCommand {
    public static final int STATUS_FLAG = 8;

    static {
        // Register this command type
        BinaryCommand.registerCommand(RECONNECT, BinaryReconnectCommand::new);
    }

    public BinaryReconnectCommand(String probeId) {
        super(RECONNECT, probeId);
    }

    public BinaryReconnectCommand() {
        this(null);
    }

    public String getProbeId() {
        return getPayload();
    }

    public void setProbeId(String probeId) {
        setPayload(probeId);
    }
} 