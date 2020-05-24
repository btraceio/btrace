package org.opensolaris.os.dtrace;

public class DTraceException extends Exception {
  public DTraceException() {}

  public DTraceException(String message) {
    super(message);
  }

  public DTraceException(String message, Throwable cause) {
    super(message, cause);
  }

  public DTraceException(Throwable cause) {
    super(cause);
  }

  public DTraceException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
