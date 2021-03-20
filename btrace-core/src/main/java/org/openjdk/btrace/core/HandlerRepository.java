package org.openjdk.btrace.core;

/**
 * A bridge interface between a handler repository implementation and the invoke dynamic bootstrap
 * class doing the handler lookup.
 */
@FunctionalInterface
public interface HandlerRepository {
  byte[] getProbeHandler(
      String callerName, String probeName, String handlerName, String handlerDesc);
}
