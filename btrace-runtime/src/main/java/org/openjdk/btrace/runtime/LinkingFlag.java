package org.openjdk.btrace.runtime;

public final class LinkingFlag {
  private static final ThreadLocal<Integer> linking = new ThreadLocal<>();

  public static int guardLinking() {
    Integer current = linking.get();
    current = current == null ? 0 : current;
    linking.set(current + 1);
    return current;
  }

  public static int get() {
    Integer current = linking.get();
    return current == null ? 0 : current;
  }

  public static void reset() {
    Integer current = linking.get();
    current = current == null ? 0 : current;
    linking.set(current > 0 ? current - 1 : 0);
  }
}
