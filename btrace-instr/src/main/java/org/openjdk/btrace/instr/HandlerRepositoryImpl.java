/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.openjdk.btrace.instr;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.openjdk.btrace.core.HandlerRepository;
import org.openjdk.btrace.indy.IndyDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HandlerRepositoryImpl implements HandlerRepository {
  private static final Logger log = LoggerFactory.getLogger(HandlerRepositoryImpl.class);

  private static final HandlerRepositoryImpl INSTANCE = new HandlerRepositoryImpl();

  private static final Map<String, BTraceProbe> probeMap = new ConcurrentHashMap<>();
  private static final Map<String, MethodHandle> handlerCache = new ConcurrentHashMap<>();
  // Cached Field for $btrace$$level per probe — avoids repeated getDeclaredField on every entry.
  private static final Map<String, Field> levelFieldCache =
      new ConcurrentHashMap<>();

  static {
    IndyDispatcher.repository = INSTANCE;
  }

  public static void registerProbe(BTraceProbe probe) {
    String probeName = probe.getClassName(true);
    probeMap.put(probeName, probe);
    Class<?> clz = probe.getDefinedClass();
    if (clz != null) {
      try {
        Field f = clz.getDeclaredField("$btrace$$level");
        f.setAccessible(true);
        levelFieldCache.put(probeName, f);
      } catch (NoSuchFieldException ignored) {
        // no $btrace$$level field — probe has no level restriction
      }
    }
  }

  public static void unregisterProbe(BTraceProbe probe) {
    String probeName = probe.getClassName(true);
    levelFieldCache.remove(probeName);
    String prefix = probeName + "#";
    handlerCache.keySet().removeIf(key -> key.startsWith(prefix));
    probeMap.remove(probeName);
  }

  @Override
  public MethodHandle resolveHandler(
      String probeName, String handlerName, MethodType handlerType) {
    String cacheKey = probeName + "#" + handlerName + handlerType.toMethodDescriptorString();

    // computeIfAbsent does not store null, so a null result (probe not yet ready) leaves
    // the cache empty. BTrace guarantees probes are registered before instrumented classes
    // can execute, so this path is only reached transiently during agent initialization.
    return handlerCache.computeIfAbsent(
        cacheKey,
        k -> {
          BTraceProbe probe = probeMap.get(probeName);
          if (probe == null) {
            log.warn("No probe registered for {}", probeName);
            return null;
          }
          Class<?> probeClass = probe.getDefinedClass();
          if (probeClass == null) {
            log.warn("Probe {} not yet defined", probeName);
            return null;
          }

          // Strip action prefix to get the actual method name in the probe class.
          // The handlerName is prefixed (e.g. "$btrace$com$example$MyProbe$onEntry"),
          // and the actual method in the probe class is the part after the last '$'.
          String actualName = handlerName;
          int idx = handlerName.lastIndexOf('$');
          if (idx > -1) {
            actualName = handlerName.substring(idx + 1);
          }

          try {
            return MethodHandles.publicLookup().findStatic(probeClass, actualName, handlerType);
          } catch (NoSuchMethodException | IllegalAccessException e) {
            log.warn(
                "Failed to resolve handler {}.{} with type {}", probeName, actualName, handlerType, e);
            return null;
          }
        });
  }

  @Override
  public int getLevel(String probeName) {
    Field f = levelFieldCache.get(probeName);
    if (f == null) {
      // Not in cache: probe has no $btrace$$level field (no level restriction),
      // or probe is not registered / not yet defined.
      BTraceProbe probe = probeMap.get(probeName);
      if (probe == null) {
        return Integer.MIN_VALUE;
      }
      Class<?> clz = probe.getDefinedClass();
      return clz == null ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
    try {
      Object val = f.get(null);
      return val instanceof Integer ? (Integer) val : Integer.MIN_VALUE;
    } catch (IllegalAccessException e) {
      return Integer.MIN_VALUE;
    }
  }

  @Override
  public MethodHandle resolveRuntime(String owner, String name, MethodType type) {
    String cacheKey = "rt#" + owner + "." + name + type.toMethodDescriptorString();

    return handlerCache.computeIfAbsent(
        cacheKey,
        k -> {
          try {
            String className = owner.replace('/', '.');
            Class<?> clz =
                Class.forName(className, true, HandlerRepositoryImpl.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(clz, name, type);
          } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            log.warn("Failed to resolve runtime method {}.{}", owner, name, e);
            return null;
          }
        });
  }
}
