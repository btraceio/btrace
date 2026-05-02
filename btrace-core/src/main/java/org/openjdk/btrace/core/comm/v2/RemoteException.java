package org.openjdk.btrace.core.comm.v2;

import java.io.PrintStream;
import java.io.PrintWriter;

class RemoteException extends RuntimeException {
    private final String exceptionClass;
    private final String remoteStackTrace;

    RemoteException(String exceptionClass, String message, String remoteStackTrace) {
        super(message);
        this.exceptionClass = exceptionClass;
        this.remoteStackTrace = remoteStackTrace;
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        if (remoteStackTrace != null) {
            s.print(remoteStackTrace);
            return;
        }
        super.printStackTrace(s);
    }

    @Override
    public void printStackTrace(PrintStream s) {
        if (remoteStackTrace != null) {
            s.print(remoteStackTrace);
            return;
        }
        super.printStackTrace(s);
    }

    @Override
    public String toString() {
        if (exceptionClass == null) {
            return super.toString();
        }
        String message = getMessage();
        return message == null ? exceptionClass : exceptionClass + ": " + message;
    }
}
