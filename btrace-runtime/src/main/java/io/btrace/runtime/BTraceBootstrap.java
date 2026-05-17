/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;

/**
 * INVOKEDYNAMIC bootstrap for BTrace core DSL ops (io.btrace.BTrace.*). Lives on the bootstrap
 * classpath; must not reference SLF4J or any logging framework.
 */
public final class BTraceBootstrap {

  // key: opName + type.toMethodDescriptorString()  e.g. "print(Ljava/lang/String;)V"
  private static final ConcurrentHashMap<String, MethodHandle> OP_TABLE = new ConcurrentHashMap<>();

  private BTraceBootstrap() {}

  /** Returns the registered handle for a core op, or {@code null} if not registered. */
  public static MethodHandle lookupCoreOp(String name, MethodType type) {
    return OP_TABLE.get(name + type.toMethodDescriptorString());
  }

  /**
   * Called by JVM for every INVOKEDYNAMIC targeting this bootstrap. Returns a ConstantCallSite —
   * the JIT folds it after warmup.
   */
  public static CallSite bootstrap(
      @SuppressWarnings("unused") MethodHandles.Lookup lookup, String name, MethodType type) {
    MethodHandle mh = lookupCoreOp(name, type);
    if (mh == null) {
      throw new BootstrapMethodError(
          "Unknown BTrace core op: " + name + type.toMethodDescriptorString());
    }
    return new ConstantCallSite(mh);
  }

  /**
   * Register a core op. Called at agent startup before any probe fires. Re-registration of the
   * same {@link MethodHandle} instance is a no-op (safe for repeated agent attach). Registering
   * a different handle for the same key throws to prevent silent substitution.
   */
  public static void registerCoreOp(String name, MethodType type, MethodHandle impl) {
    String key = name + type.toMethodDescriptorString();
    MethodHandle existing = OP_TABLE.putIfAbsent(key, impl);
    // Identity check is safe: BTraceBootstrap is on the bootstrap classpath and is never
    // unloaded, so the same logical op always produces the same MethodHandle object across
    // re-attach cycles.
    if (existing != null && existing != impl) {
      throw new IllegalStateException("Core op already registered with a different handle: " + key);
    }
  }
}
