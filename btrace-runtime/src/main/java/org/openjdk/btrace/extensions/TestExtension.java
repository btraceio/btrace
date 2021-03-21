package org.openjdk.btrace.extensions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TestExtension {
  private static final Map<String, AtomicInteger> invocationMap = new HashMap<>();

  public static void clearInvocationMap() {
    invocationMap.clear();
  }

  public static int getInvocations(String methodName) {
    return invocationMap.getOrDefault(methodName, new AtomicInteger(0)).get();
  }

  public static void staticVoidMethod(String a, int b, byte[] c) {
    invocationMap.computeIfAbsent("staticVoidMethod", k -> new AtomicInteger(0)).incrementAndGet();
  }

  public void voidMethod(String a, int b, byte[] c) {
    invocationMap.computeIfAbsent("voidMethod", k -> new AtomicInteger(0)).incrementAndGet();
  }
}
