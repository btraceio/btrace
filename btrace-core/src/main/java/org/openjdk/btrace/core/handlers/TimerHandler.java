package org.openjdk.btrace.core.handlers;

import java.lang.reflect.Method;

public final class TimerHandler {
  public final String method;
  public final long period;
  public final String periodArg;

  public TimerHandler(String method, long period, String periodArg) {
    this.method = method;
    this.period = period;
    this.periodArg = periodArg;
  }

  public Method getMethod(Class clz) throws NoSuchMethodException {
    return clz.getMethod(method);
  }
}
