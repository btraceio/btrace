package org.openjdk.btrace.core.comm.v2;

/**
 * Binary implementation of the EventCommand.
 * This command is used to send events to BTrace agents.
 */
public class BinaryEventCommand extends BinaryStringCommand {
    static {
        // Register this command type
        BinaryCommand.registerCommand(EVENT, BinaryEventCommand::new);
    }

    public BinaryEventCommand(String event) {
        super(EVENT, event);
    }

    public BinaryEventCommand() {
        this(null);
    }

    public String getEvent() {
        return getPayload();
    }

    public void setEvent(String event) {
        setPayload(event);
    }
} 