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
  static final ConcurrentHashMap<String, MethodHandle> OP_TABLE = new ConcurrentHashMap<>();

  private BTraceBootstrap() {}

  /**
   * Called by JVM for every INVOKEDYNAMIC targeting this bootstrap. Returns a ConstantCallSite —
   * the JIT folds it after warmup.
   */
  public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type) {
    MethodHandle mh = OP_TABLE.get(name + type.toMethodDescriptorString());
    if (mh == null) {
      throw new BootstrapMethodError(
          "Unknown BTrace core op: " + name + type.toMethodDescriptorString());
    }
    return new ConstantCallSite(mh);
  }

  /**
   * Register a core op. Called at agent startup before any probe fires. Core op names+types cannot
   * be registered twice.
   */
  public static void registerCoreOp(String name, MethodType type, MethodHandle impl) {
    String key = name + type.toMethodDescriptorString();
    if (OP_TABLE.putIfAbsent(key, impl) != null) {
      throw new IllegalStateException("Core op already registered: " + key);
    }
  }
}
