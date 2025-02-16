package org.openjdk.btrace.core;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Function;

/**
 * A bridge interface between a handler repository implementation and the invoke dynamic bootstrap
 * class doing the handler lookup.
 */
@FunctionalInterface
public interface HandlerRepository {
  final class Handler {
    private final MethodHandles.Lookup lookup;

    public Handler(MethodHandles.Lookup lookup) {
      this.lookup = lookup;
    }

    public MethodHandle findProbeMethod(String methodName, MethodType signature) throws Exception {
      return lookup.findStatic(lookup.lookupClass(), methodName, signature);
    }
  }

  Handler getProbeHandler(
          MethodHandles.Lookup caller, String probeName, Function<byte[], MethodHandles.Lookup> loader);
}
