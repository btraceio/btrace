package org.openjdk.btrace.core;

public interface Function<T, R> {
  R apply(T value);
}
