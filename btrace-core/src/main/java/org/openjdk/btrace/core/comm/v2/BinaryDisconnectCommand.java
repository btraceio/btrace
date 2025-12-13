package org.openjdk.btrace.core.comm.v2;

/**
 * Binary implementation of the DisconnectCommand.
 * This command is used to disconnect a BTrace client without stopping the agent.
 */
public class BinaryDisconnectCommand extends BinaryStringCommand {
    static {
        // Register this command type
        BinaryCommand.registerCommand(DISCONNECT, BinaryDisconnectCommand::new);
    }

    public BinaryDisconnectCommand(String probeId) {
        super(DISCONNECT, probeId);
    }

    public BinaryDisconnectCommand() {
        this("");
    }

    public String getProbeId() {
        return getPayload();
    }

    public void setProbeId(String probeId) {
        setPayload(probeId);
    }
} 