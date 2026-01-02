package org.openjdk.btrace.compiler.oneliner;

/**
 * Exception thrown when parsing or validating BTrace oneliner expressions.
 * Includes position tracking for better error messages.
 */
public class OnelinerException extends RuntimeException {
  private final int position;
  private final String input;

  public OnelinerException(String message, String input, int position) {
    super(formatMessage(message, input, position));
    this.position = position;
    this.input = input;
  }

  public OnelinerException(String message, String input, int position, Throwable cause) {
    super(formatMessage(message, input, position), cause);
    this.position = position;
    this.input = input;
  }

  public int getPosition() {
    return position;
  }

  public String getInput() {
    return input;
  }

  private static String formatMessage(String message, String input, int position) {
    StringBuilder sb = new StringBuilder();
    sb.append("Oneliner syntax error at position ").append(position).append(":\n");
    sb.append(input).append("\n");

    if (position >= 0 && position < input.length()) {
      for (int i = 0; i < position; i++) {
        sb.append(" ");
      }
      sb.append("^\n");
    }

    sb.append(message);
    return sb.toString();
  }
}
