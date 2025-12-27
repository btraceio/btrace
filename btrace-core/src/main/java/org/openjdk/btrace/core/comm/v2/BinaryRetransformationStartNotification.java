package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Binary implementation of the RetransformationStartNotification.
 * This command is used to notify the client that class retransformation is about to start.
 */
public class BinaryRetransformationStartNotification extends BinaryCommand {
    private int numClasses;

    static {
        // Register this command type
        BinaryCommand.registerCommand(RETRANSFORMATION_START, BinaryRetransformationStartNotification::new);
    }

    public BinaryRetransformationStartNotification(int numClasses) {
        super(RETRANSFORMATION_START, true);
        this.numClasses = numClasses;
    }

    public BinaryRetransformationStartNotification() {
        this(0);
    }

    @Override
    protected void write(OutputStream out) throws IOException {
        BinaryProtocol.writeInt(out, numClasses);
    }

    @Override
    protected void read(InputStream in) throws IOException {
        numClasses = BinaryProtocol.readInt(in);
    }

    public int getNumClasses() {
        return numClasses;
    }

    public void setNumClasses(int numClasses) {
        this.numClasses = numClasses;
    }
} 