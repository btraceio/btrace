package org.openjdk.btrace.core.extensions;

/**
 * Exception thrown when extension operations fail.
 *
 * <p>This exception is used for extension-related errors including:
 *
 * <ul>
 *   <li>Missing or invalid extension annotations
 *   <li>Permission violations
 *   <li>Initialization failures
 *   <li>Missing dependencies
 * </ul>
 */
public class ExtensionException extends RuntimeException {
  /**
   * Constructs an exception with the specified message.
   *
   * @param message the error message
   */
  public ExtensionException(String message) {
    super(message);
  }

  /**
   * Constructs an exception with the specified message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public ExtensionException(String message, Throwable cause) {
    super(message, cause);
  }
}
