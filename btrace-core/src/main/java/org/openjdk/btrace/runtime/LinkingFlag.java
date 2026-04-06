package org.openjdk.btrace.runtime;

public final class LinkingFlag {
  private static final ThreadLocal<int[]> linking =
      new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
          return new int[] {0};
        }
      };

  public static int guardLinking() {
    int[] arr = linking.get();
    int current = arr[0];
    arr[0] = current + 1;
    return current;
  }

  public static int get() {
    return linking.get()[0];
  }

  public static void reset() {
    int[] arr = linking.get();
    if (arr[0] > 0) {
      arr[0]--;
    }
  }
}
