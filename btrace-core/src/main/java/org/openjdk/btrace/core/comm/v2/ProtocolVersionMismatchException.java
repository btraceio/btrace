package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;

/**
 * Exception thrown when the protocol version in the stream doesn't match
 * the expected version.
 */
public class ProtocolVersionMismatchException extends IOException {
    private final int expectedVersion;
    private final int actualVersion;

    public ProtocolVersionMismatchException(int expectedVersion, int actualVersion) {
        super(String.format("Protocol version mismatch: expected %d, got %d",
            expectedVersion, actualVersion));
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public int getExpectedVersion() {
        return expectedVersion;
    }

    public int getActualVersion() {
        return actualVersion;
    }
}
