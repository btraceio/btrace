package org.openjdk.btrace.core.comm.v2;

/**
 * Binary implementation of the RenameCommand.
 * This command is used to rename BTrace output files.
 */
public class BinaryRenameCommand extends BinaryStringCommand {
    static {
        // Register this command type
        BinaryCommand.registerCommand(RENAME, BinaryRenameCommand::new);
    }

    public BinaryRenameCommand(String newName) {
        super(RENAME, newName);
    }

    public BinaryRenameCommand() {
        this(null);
    }

    public String getNewName() {
        return getPayload();
    }

    public void setNewName(String newName) {
        setPayload(newName);
    }
} 