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
package io.btrace.instr;

import io.btrace.core.BTraceRuntime;
import io.btrace.core.DebugSupport;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single entry point for class transformation.
 *
 * <p>When a class is to be transformed all the registered {@linkplain BTraceProbe} instances are
 * asked for the appropriate instrumentation. When there are no registered probes or none of the
 * registered probes is able to instrument the class it will not be transformed.
 *
 * @author Jaroslav Bachorik
 * @since 1.3.5
 */
@SuppressWarnings("RedundantThrows")
public final class BTraceTransformer implements ClassFileTransformer {
  private static final Logger log = LoggerFactory.getLogger(BTraceTransformer.class);

  private final DebugSupport debug;
  private final ReentrantReadWriteLock setupLock = new ReentrantReadWriteLock();
  private final Collection<BTraceProbe> probes = new ArrayList<>(3);
  private final Filter filter = new Filter();

  public BTraceTransformer(DebugSupport d) {
    debug = d;
  }

  /*
   * Certain classes like java.lang.ThreadLocal and it's
   * inner classes, java.lang.Object cannot be safely
   * instrumented with BTrace. This is because BTrace uses
   * ThreadLocal class to check recursive entries due to
   * BTrace's own functions. But this leads to infinite recursions
   * if BTrace instruments java.lang.ThreadLocal for example.
   * For now, we avoid such classes till we find a solution.
   */
  private static boolean isSensitiveClass(String name) {
    return ClassFilter.isSensitiveClass(name);
  }

  // JDK lambda wrapper names from LambdaMetafactory:
  //   JDK 8:   <owner>$$Lambda$<N>                    (e.g. Main$$Lambda$36)
  //   JDK 11+: <owner>$$Lambda$<N>/0x<hex>            (hidden-class suffix)
  // Both forms are recognised by the "$$Lambda$" infix followed by digits.
  static boolean isSyntheticLambda(String internalName) {
    if (internalName == null) return false;
    int idx = internalName.indexOf("$$Lambda$");
    if (idx < 0) return false;
    int digitStart = idx + "$$Lambda$".length();
    if (digitStart >= internalName.length()) return false;
    // Next char must be a digit (the Lambda serial number)
    return Character.isDigit(internalName.charAt(digitStart));
  }

  public void register(BTraceProbe p) {
    try {
      setupLock.writeLock().lock();
      probes.add(p);
      for (OnMethod om : p.onmethods()) {
        filter.add(om);
      }
    } finally {
      setupLock.writeLock().unlock();
    }
  }

  public final void unregister(BTraceProbe p) {
    try {
      setupLock.writeLock().lock();
      probes.remove(p);
      for (OnMethod om : p.onmethods()) {
        filter.remove(om);
      }
    } finally {
      setupLock.writeLock().unlock();
    }
  }

  @Override
  public byte[] transform(
      ClassLoader loader,
      String className,
      Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain,
      byte[] classfileBuffer)
      throws IllegalClassFormatException {
    try {
      setupLock.readLock().lock();

      // Skip JVM-synthesized classes that have no binary name (JDK 8
      // Unsafe.defineAnonymousClass host-anonymous classes, JDK 15+ hidden
      // classes). These are never a user-intended tracing target: a
      // @OnMethod(clazz="<pattern>") matcher targets classes the user
      // authored, not the VM's lambda/LambdaForm scaffolding. Instrumenting
      // them also re-enters the invokedynamic machinery that the probe
      // dispatch itself uses, leading to unbounded recursion on JDK 8.
      if (className == null) {
        return null;
      }

      if (className.startsWith("io/btrace/")) {
        // do not instrument BTrace classes!
        return null;
      }

      // A special case for patching the Indy linking in order to be able to safely skip
      // BTrace probes while linking is still in progress.
      if (className.equals("java/lang/invoke/MethodHandleNatives")) {
        byte[] transformed = null;
        try {
          debug.dumpClass(className.replace('.', '/') + "_orig", classfileBuffer);
          transformed = LinkerInstrumentor.addGuard(classfileBuffer);
          debug.dumpClass(className.replace('.', '/'), transformed);
        } catch (Throwable t) {
          log.debug("Failed to instrument indy linking", t);
        }
        return transformed;
      }

      // Skip JVM-synthesized classes: reflection accessors (sun/reflect/Generated*,
      // jdk/internal/reflect/Generated*) and lambda wrappers (LambdaMetafactory names
      // them "<owner>$$Lambda$N" on JDK 8 and "<owner>$$Lambda$N/0x<hex>" on JDK 11+).
      // Instrumenting these serves no tracing purpose — they are 1:1 trampolines to a
      // target Method or a captured functional method the user can trace directly —
      // and it recursively re-enters the invokedynamic/LambdaMetafactory machinery
      // that the probe dispatch path relies on.
      if (className.startsWith("sun/reflect/Generated")
          || className.startsWith("jdk/internal/reflect/Generated")
          || isSyntheticLambda(className)) {
        return null;
      }

      if (probes.isEmpty()) return null;
      if ((loader == null || loader.equals(ClassLoader.getSystemClassLoader()))
          && isSensitiveClass(className)) {
        if (log.isDebugEnabled()) {
          log.debug("skipping transform for BTrace class {}", className); // NOI18N
        }
        return null;
      }

      if (filter.matchClass(className) == Filter.Result.FALSE) return null;

      boolean entered = BTraceRuntime.enter();
      try {
        if (debug.isDumpClasses()) {
          debug.dumpClass(className.replace('.', '/') + "_orig", classfileBuffer);
        }
        for (BTraceProbe p : probes) {
          p.notifyTransform(className);
        }
        int major = InstrumentUtils.getMajor(classfileBuffer);
        InstrumentationBackend backend = BackendSelector.select(major);
        byte[] transformed = backend.instrument(loader, classfileBuffer, probes);
        if (transformed == null) {
          // no instrumentation necessary
          if (log.isDebugEnabled()) {
            log.debug("skipping class {}", className.replace('/', '.'));
          }
          return classfileBuffer;
        } else {
          if (log.isDebugEnabled()) {
            log.debug("transformed class {}", className.replace('/', '.'));
          }
          // Optional: verify transformed class via ASM in tests.
          // ASM cannot parse class-file major versions above MAX_ASM_MAJOR_VERSION (JDK 26+);
          // skip ASM verification for those to avoid a spurious warn on every ClassFile API class.
          if (Boolean.getBoolean("btrace.verify.transformed")
              && !Boolean.TRUE.equals(VerifyGuard.IN_PROGRESS.get())
              && major <= AsmInstrumentationBackend.MAX_ASM_MAJOR_VERSION) {
            boolean allow;
            String filter = System.getProperty("btrace.verify.filter");
            if (filter != null && !filter.isEmpty()) {
              allow = className.matches(filter);
            } else {
              allow = isAppClass(className);
            }
            if (allow) {
              try {
                VerifyGuard.IN_PROGRESS.set(Boolean.TRUE);
                java.io.StringWriter sw = new java.io.StringWriter();
                org.objectweb.asm.util.CheckClassAdapter.verify(
                    new org.objectweb.asm.ClassReader(transformed),
                    false,
                    new java.io.PrintWriter(sw));
                String report = sw.toString();
                if (!report.isEmpty()) {
                  log.warn("ASM verification issues for {}:\n{}", className, report);
                } else if (log.isDebugEnabled()) {
                  log.debug("ASM verification OK for {}", className);
                }
              } catch (Throwable t) {
                // Don't break transformation on verifier diagnostics
                log.warn("ASM verification failed for {}: {}", className, t.toString());
              } finally {
                VerifyGuard.IN_PROGRESS.remove();
              }
            }
          }
          if (debug.isDumpClasses()) {
            debug.dumpClass(className.replace('.', '/'), transformed);
          }
        }
        return transformed;
      } catch (Throwable th) {
        log.warn("Failed to transform class {}", className, th);
        // Don't rethrow: a re-thrown Throwable propagates through the JVM's native
        // retransform/transform machinery and can leave an "outstanding Java exception"
        // in the JNI state that survives even after Java callers catch the exception,
        // triggering native assertions (e.g. JPLIS "!errorOutstanding" on JDK 27+).
        // Returning null here is equivalent to "skip instrumentation for this class".
        return null;
      } finally {
        if (entered) {
          BTraceRuntime.leave();
        }
      }
    } finally {
      setupLock.readLock().unlock();
    }
  }

  // Helper guard and app-class predicate for verifier
  static final class VerifyGuard {
    static final ThreadLocal<Boolean> IN_PROGRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);
  }

  private static boolean isAppClass(String internalName) {
    if (internalName == null) return false;
    return !(internalName.startsWith("java/")
        || internalName.startsWith("javax/")
        || internalName.startsWith("jdk/")
        || internalName.startsWith("sun/")
        || internalName.startsWith("com/sun/")
        || internalName.startsWith("org/ietf/")
        || internalName.startsWith("org/omg/")
        || internalName.startsWith("org/w3c/")
        || internalName.startsWith("org/xml/")
        || internalName.startsWith("io/btrace/"));
  }

  static class Filter {
    private final Map<String, Integer> nameMap = new HashMap<>();
    private final Map<Pattern, Integer> nameRegexMap = new HashMap<>();
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();
    private boolean isFast = true;
    private boolean isRegex = false;

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private static <K> void addToMap(Map<K, Integer> map, K name) {
      synchronized (map) {
        map.merge(name, 1, Integer::sum);
      }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private static <K> void removeFromMap(Map<K, Integer> map, K name) {
      synchronized (map) {
        Integer i = map.get(name);
        if (i == null) {
          return;
        }
        int freq = i - 1;
        if (freq == 0) {
          map.remove(name);
        }
      }
    }

    void add(OnMethod om) {
      if (om.isSubtypeMatcher() || om.isClassAnnotationMatcher()) {
        isFast = false;
      } else {
        if (om.isClassRegexMatcher()) {
          isRegex = true;
          String name = om.getClazz().replace("\\.", "/");
          Pattern pattern = patternCache.computeIfAbsent(name, Pattern::compile);
          addToMap(nameRegexMap, pattern);
        } else {
          String name = om.getClazz().replace('.', '/');
          addToMap(nameMap, name);
        }
      }
    }

    void remove(OnMethod om) {
      if (!(om.isSubtypeMatcher() || om.isClassAnnotationMatcher())) {
        if (om.isClassRegexMatcher()) {
          // Must compute the key exactly as add() does (replacing the escaped-dot sequence "\."),
          // otherwise patternCache.get returns null and the regex entry is never removed.
          String name = om.getClazz().replace("\\.", "/");
          Pattern pattern = patternCache.get(name);
          if (pattern != null) {
            removeFromMap(nameRegexMap, pattern);
          }
        } else {
          String name = om.getClazz().replace('.', '/');
          removeFromMap(nameMap, name);
        }
      }
    }

    public Result matchClass(String className) {
      if (isFast) {
        synchronized (nameMap) {
          if (nameMap.containsKey(className)) {
            return Result.TRUE;
          }
        }
        if (isRegex) {
          synchronized (nameRegexMap) {
            for (Pattern p : nameRegexMap.keySet()) {
              if (p.matcher(className).matches()) {
                return Result.TRUE;
              }
            }
          }
        }
        return Result.FALSE;
      }
      return Result.MAYBE;
    }

    enum Result {
      TRUE,
      FALSE,
      MAYBE
    }
  }
}
