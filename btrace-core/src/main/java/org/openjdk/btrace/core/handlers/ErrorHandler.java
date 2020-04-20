package org.openjdk.btrace.core.handlers;

import java.lang.reflect.Method;

public final class ErrorHandler {
  public final String method;

  public ErrorHandler(String method) {
    this.method = method;
  }

  public Method getMethod(Class clz) throws NoSuchMethodException {
    return clz.getMethod(method, Throwable.class);
  }
}
