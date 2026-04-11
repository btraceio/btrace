package org.openjdk.btrace.core;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/**
 * A bridge interface between a handler repository implementation and the invoke dynamic bootstrap
 * class doing the handler lookup.
 */
@FunctionalInterface
public interface HandlerRepository {
  MethodHandle resolveHandler(String probeName, String handlerName, MethodType handlerType);
}