package org.openjdk.btrace.instr;

/**
 * Dummy, non-stack-collecting runtime exception. It is used for execution control in ClassReader
 * instances in order to avoid processing the complete class file when the relevant info is
 * available right at the beginning of parsing.
 */
final class BailoutException extends RuntimeException {
  /** Shared instance to optimize the cost of throwing */
  static final BailoutException INSTANCE = new BailoutException();

  private BailoutException() {}

  @Override
  public synchronized Throwable fillInStackTrace() {
    // we don't need the stack here
    return this;
  }
}
