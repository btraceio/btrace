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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
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
  private static final Map<String, Field> levelFieldCache = new ConcurrentHashMap<>();
  // Handler metadata extracted at registration time; used by initProbeRuntime.
  private static final Map<String, ProbeHandlerMetadata> handlerMetadata = new ConcurrentHashMap<>();

  static {
    IndyDispatcher.repository = INSTANCE;
  }

  // ── Handler metadata container ────────────────────────────────────────────

  /**
   * Lightweight snapshot of the timer/event/error/exit/low-memory handler data extracted from a
   * probe's bytecode at registration time. Stored per probe so that {@link #initProbeRuntime} can
   * reconstruct the arrays without re-reading class bytes at link time.
   */
  static final class ProbeHandlerMetadata {
    // Timer: [methodName, period (Long), fromProperty (String|null)]
    final List<Object[]> timers = new ArrayList<>();
    // Event: [methodName, eventName (String|null)]
    final List<Object[]> events = new ArrayList<>();
    // Error: [methodName]
    final List<String> errors = new ArrayList<>();
    // Exit: [methodName]
    final List<String> exits = new ArrayList<>();
    // LowMemory: [methodName, pool (String), threshold (Long), thresholdFrom (String|null)]
    final List<Object[]> lowMemory = new ArrayList<>();
  }

  // ── Probe registration / deregistration ───────────────────────────────────

  /**
   * Extracts and stores handler metadata from raw probe class bytes. Must be called BEFORE
   * {@link #registerProbe} (and before {@code defineClass}) because the probe's {@code <clinit>}
   * will invoke {@code initProbeRuntime} during class initialization, which needs the metadata.
   */
  public static void preRegisterProbeBytes(byte[] classBytes) {
    if (classBytes == null || classBytes.length == 0) return;
    try {
      ClassReader cr = new ClassReader(classBytes);
      String probeName = cr.getClassName();
      ProbeHandlerMetadata meta = extractHandlerMetadata(classBytes);
      handlerMetadata.put(probeName, meta);
    } catch (Exception e) {
      log.warn("Failed to pre-register probe handler metadata", e);
    }
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
    handlerMetadata.remove(probeName);
    String prefix = probeName + "#";
    handlerCache.keySet().removeIf(key -> key.startsWith(prefix));
    probeMap.remove(probeName);
  }

  // ── Handler metadata extraction via ASM ───────────────────────────────────

  private static ProbeHandlerMetadata extractHandlerMetadata(byte[] classBytes) {
    ProbeHandlerMetadata meta = new ProbeHandlerMetadata();
    if (classBytes == null) return meta;
    try {
      ClassReader cr = new ClassReader(classBytes);
      cr.accept(new HandlerMetadataExtractor(meta), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    } catch (Exception e) {
      log.warn("Failed to extract handler metadata from probe bytecode", e);
    }
    return meta;
  }

  /**
   * ASM visitor that scans method annotations and populates a {@link ProbeHandlerMetadata}.
   */
  private static final class HandlerMetadataExtractor extends ClassVisitor {
    private final ProbeHandlerMetadata meta;

    HandlerMetadataExtractor(ProbeHandlerMetadata meta) {
      super(Opcodes.ASM9);
      this.meta = meta;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      return new MethodVisitor(Opcodes.ASM9) {
        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          switch (desc) {
            case "Lorg/openjdk/btrace/core/annotations/OnTimer;":
              return new AnnotationVisitor(Opcodes.ASM9) {
                long period = -1L;
                String from = null;

                @Override
                public void visit(String n, Object value) {
                  if ("value".equals(n) && value instanceof Long) period = (Long) value;
                  else if ("from".equals(n) && value instanceof String) from = (String) value;
                }

                @Override
                public void visitEnd() {
                  meta.timers.add(new Object[]{name, period, from});
                }
              };

            case "Lorg/openjdk/btrace/core/annotations/OnEvent;":
              return new AnnotationVisitor(Opcodes.ASM9) {
                String eventName = null;

                @Override
                public void visit(String n, Object value) {
                  if ("value".equals(n)) eventName = (String) value;
                }

                @Override
                public void visitEnd() {
                  meta.events.add(new Object[]{name, eventName});
                }
              };

            case "Lorg/openjdk/btrace/core/annotations/OnError;":
              meta.errors.add(name);
              return null;

            case "Lorg/openjdk/btrace/core/annotations/OnExit;":
              meta.exits.add(name);
              return null;

            case "Lorg/openjdk/btrace/core/annotations/OnLowMemory;":
              return new AnnotationVisitor(Opcodes.ASM9) {
                String pool = "";
                long threshold = Long.MAX_VALUE;
                String thresholdFrom = null;

                @Override
                public void visit(String n, Object value) {
                  if ("pool".equals(n)) pool = (String) value;
                  else if ("threshold".equals(n) && value instanceof Long) threshold = (Long) value;
                  else if ("thresholdFrom".equals(n)) thresholdFrom = (String) value;
                }

                @Override
                public void visitEnd() {
                  meta.lowMemory.add(new Object[]{name, pool, threshold, thresholdFrom});
                }
              };

            default:
              return null;
          }
        }
      };
    }
  }

  // ── initProbeRuntime ───────────────────────────────────────────────────────

  /**
   * Called from the probe's {@code <clinit>} via INVOKEDYNAMIC "initRuntime".  Builds handler
   * arrays from pre-registered metadata and delegates to {@code BTraceRuntimeAccess.forClass}.
   */
  @Override
  public Object initProbeRuntime(Class<?> probeClass) {
    String probeName = probeClass.getName().replace('.', '/');
    ProbeHandlerMetadata meta = handlerMetadata.get(probeName);
    if (meta == null) {
      log.warn("No handler metadata registered for probe {}", probeName);
      meta = new ProbeHandlerMetadata();
    }
    try {
      // Use reflection to avoid compile-time dependency on the handler types
      // (which live in btrace-core but are masked .classdata on bootstrap).
      ClassLoader cl = HandlerRepositoryImpl.class.getClassLoader();

      Class<?> timerHandlerClass = Class.forName("org.openjdk.btrace.core.handlers.TimerHandler", true, cl);
      Class<?> eventHandlerClass = Class.forName("org.openjdk.btrace.core.handlers.EventHandler", true, cl);
      Class<?> errorHandlerClass = Class.forName("org.openjdk.btrace.core.handlers.ErrorHandler", true, cl);
      Class<?> exitHandlerClass  = Class.forName("org.openjdk.btrace.core.handlers.ExitHandler",  true, cl);
      Class<?> lowMemHandlerClass= Class.forName("org.openjdk.btrace.core.handlers.LowMemoryHandler", true, cl);
      Class<?> rtAccessClass     = Class.forName("org.openjdk.btrace.runtime.BTraceRuntimeAccess", true, cl);

      Object timerArr = buildTimerHandlers(meta, timerHandlerClass);
      Object eventArr = buildEventHandlers(meta, eventHandlerClass);
      Object errorArr = buildErrorHandlers(meta, errorHandlerClass);
      Object exitArr  = buildExitHandlers(meta, exitHandlerClass);
      Object lowMemArr= buildLowMemoryHandlers(meta, lowMemHandlerClass);

      java.lang.reflect.Method forClass = rtAccessClass.getMethod(
          "forClass",
          Class.class,
          java.lang.reflect.Array.newInstance(timerHandlerClass,  0).getClass(),
          java.lang.reflect.Array.newInstance(eventHandlerClass,  0).getClass(),
          java.lang.reflect.Array.newInstance(errorHandlerClass,  0).getClass(),
          java.lang.reflect.Array.newInstance(exitHandlerClass,   0).getClass(),
          java.lang.reflect.Array.newInstance(lowMemHandlerClass, 0).getClass());

      return forClass.invoke(null, probeClass, timerArr, eventArr, errorArr, exitArr, lowMemArr);
    } catch (Exception e) {
      log.error("Failed to initialize probe runtime for {}", probeName, e);
      return null;
    }
  }

  private static Object buildTimerHandlers(ProbeHandlerMetadata meta, Class<?> timerClass)
      throws Exception {
    Object arr = java.lang.reflect.Array.newInstance(timerClass, meta.timers.size());
    java.lang.reflect.Constructor<?> ctor = timerClass.getConstructor(String.class, long.class, String.class);
    for (int i = 0; i < meta.timers.size(); i++) {
      Object[] d = meta.timers.get(i);
      java.lang.reflect.Array.set(arr, i, ctor.newInstance(d[0], d[1], d[2]));
    }
    return arr;
  }

  private static Object buildEventHandlers(ProbeHandlerMetadata meta, Class<?> eventClass)
      throws Exception {
    Object arr = java.lang.reflect.Array.newInstance(eventClass, meta.events.size());
    java.lang.reflect.Constructor<?> ctor = eventClass.getConstructor(String.class, String.class);
    for (int i = 0; i < meta.events.size(); i++) {
      Object[] d = meta.events.get(i);
      java.lang.reflect.Array.set(arr, i, ctor.newInstance(d[0], d[1]));
    }
    return arr;
  }

  private static Object buildErrorHandlers(ProbeHandlerMetadata meta, Class<?> errorClass)
      throws Exception {
    Object arr = java.lang.reflect.Array.newInstance(errorClass, meta.errors.size());
    java.lang.reflect.Constructor<?> ctor = errorClass.getConstructor(String.class);
    for (int i = 0; i < meta.errors.size(); i++) {
      java.lang.reflect.Array.set(arr, i, ctor.newInstance(meta.errors.get(i)));
    }
    return arr;
  }

  private static Object buildExitHandlers(ProbeHandlerMetadata meta, Class<?> exitClass)
      throws Exception {
    Object arr = java.lang.reflect.Array.newInstance(exitClass, meta.exits.size());
    java.lang.reflect.Constructor<?> ctor = exitClass.getConstructor(String.class);
    for (int i = 0; i < meta.exits.size(); i++) {
      java.lang.reflect.Array.set(arr, i, ctor.newInstance(meta.exits.get(i)));
    }
    return arr;
  }

  private static Object buildLowMemoryHandlers(ProbeHandlerMetadata meta, Class<?> lowMemClass)
      throws Exception {
    Object arr = java.lang.reflect.Array.newInstance(lowMemClass, meta.lowMemory.size());
    java.lang.reflect.Constructor<?> ctor =
        lowMemClass.getConstructor(String.class, String.class, long.class, String.class);
    for (int i = 0; i < meta.lowMemory.size(); i++) {
      Object[] d = meta.lowMemory.get(i);
      java.lang.reflect.Array.set(arr, i, ctor.newInstance(d[0], d[1], d[2], d[3]));
    }
    return arr;
  }

  // ── Handler resolution ────────────────────────────────────────────────────

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
        k -> resolveRuntimeUncached(owner, name, type));
  }

  private MethodHandle resolveRuntimeUncached(String owner, String name, MethodType callSiteType) {
    ClassLoader cl = HandlerRepositoryImpl.class.getClassLoader();

    // ── initRuntime ──────────────────────────────────────────────────────────
    // Called from probe <clinit>: INVOKEDYNAMIC "initRuntime" (Class)Object
    if (Constants.BTRACERTACCESS_INTERNAL.equals(owner) && "initRuntime".equals(name)) {
      try {
        MethodHandle mh =
            MethodHandles.lookup()
                .findVirtual(
                    HandlerRepository.class,
                    "initProbeRuntime",
                    MethodType.methodType(Object.class, Class.class));
        // Bind to the singleton instance so the call site is (Class)Object.
        return mh.bindTo(INSTANCE);
      } catch (NoSuchMethodException | IllegalAccessException e) {
        log.warn("Failed to resolve initProbeRuntime", e);
        return null;
      }
    }

    // ── BTraceRuntimeAccess.enter ─────────────────────────────────────────────
    // Call site: (Object)Z — Object is the runtime field value (actually BTraceRuntimeBridge).
    // Actual method: BTraceRuntimeAccess.enter(BTraceRuntimeBridge)Z  (static).
    if (Constants.BTRACERTACCESS_INTERNAL.equals(owner) && "enter".equals(name)) {
      try {
        Class<?> rtBridgeClass =
            Class.forName(
                Constants.BTRACERTBRIDGE_INTERNAL.replace('/', '.'), true, cl);
        Class<?> rtAccessClass =
            Class.forName(
                Constants.BTRACERTACCESS_INTERNAL.replace('/', '.'), true, cl);
        MethodHandle mh =
            MethodHandles.publicLookup()
                .findStatic(
                    rtAccessClass,
                    "enter",
                    MethodType.methodType(boolean.class, rtBridgeClass));
        // Adapt: call site passes Object, actual method wants BTraceRuntimeBridge.
        return mh.asType(callSiteType);
      } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
        log.warn("Failed to resolve BTraceRuntimeAccess.enter", e);
        return null;
      }
    }

    // ── BTraceRuntimeBridge interface methods ─────────────────────────────────
    // Call site: (Object, ...args)retType  — first arg is the receiver as Object.
    // Resolve via findVirtual on the interface; adapt Object receiver to BTraceRuntimeBridge.
    if (Constants.BTRACERTBRIDGE_INTERNAL.equals(owner)) {
      try {
        Class<?> rtBridgeClass =
            Class.forName(
                Constants.BTRACERTBRIDGE_INTERNAL.replace('/', '.'), true, cl);
        // Drop the leading Object receiver from the call site type to get the method type.
        // e.g. (Object)V  →  ()V
        MethodType virtualType = callSiteType.dropParameterTypes(0, 1)
            // Replace the leading parameter types with the concrete interface type for the receiver.
            ;
        MethodHandle mh =
            MethodHandles.publicLookup().findVirtual(rtBridgeClass, name, virtualType);
        // Adapt receiver from Object to BTraceRuntimeBridge (asType handles cast).
        return mh.asType(callSiteType);
      } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
        log.warn("Failed to resolve BTraceRuntimeBridge.{}", name, e);
        return null;
      }
    }

    // ── Generic static method in agent classloader ────────────────────────────
    try {
      String className = owner.replace('/', '.');
      Class<?> clz = Class.forName(className, true, cl);
      return MethodHandles.publicLookup().findStatic(clz, name, callSiteType);
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
      log.warn("Failed to resolve runtime method {}.{}", owner, name, e);
      return null;
    }
  }
}
