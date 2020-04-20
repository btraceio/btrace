package org.openjdk.btrace.core.handlers;

import java.lang.reflect.Method;

public class ExitHandler {
  public final String method;

  public ExitHandler(String method) {
    this.method = method;
  }

  public Method getMethod(Class clz) throws NoSuchMethodException {
    return clz.getMethod(method, int.class);
  }
}
