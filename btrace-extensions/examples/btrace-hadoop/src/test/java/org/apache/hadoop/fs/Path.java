package org.apache.hadoop.fs;

public final class Path {
  public static boolean toStringCalled;

  private final String value;

  public Path(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    toStringCalled = true;
    return value;
  }

  public static void reset() {
    toStringCalled = false;
  }
}
