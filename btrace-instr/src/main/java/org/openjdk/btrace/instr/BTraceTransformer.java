/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
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
  private final Collection<MethodNode> cushionMethods = new HashSet<>();

  static {
    Filter.class.getName();
    ReentrantReadWriteLock.class.getName();
    ReentrantReadWriteLock.WriteLock.class.getName();
    ReentrantReadWriteLock.ReadLock.class.getName();
    ArrayList.class.getName();
    HashSet.class.getName();
    HashMap.class.getName();
  }

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
        MethodNode cushionMethod =
            new MethodNode(
                Opcodes.ASM9,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                Instrumentor.getActionMethodName(p, om.getTargetName()),
                om.getTargetDescriptor(),
                null,
                null);
        InsnList code = new InsnList();
        code.add(new InsnNode(Opcodes.RETURN));
        cushionMethod.instructions = code;
        int localSize = 0;
        for (Type t : Type.getArgumentTypes(om.getTargetDescriptor())) {
          localSize += t.getSize();
        }
        cushionMethod.maxLocals = localSize;
        cushionMethods.add(cushionMethod);
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

      className = className != null ? className : "<anonymous>";

      if (className.equals("java/lang/invoke/MethodHandleNatives")) {
        byte[] transformed = null;
        try {
          debug.dumpClass(className.replace('.', '/') + "_orig", classfileBuffer);
          transformed = LinkerInstrumentor.addGuard(classfileBuffer);
          debug.dumpClass(className.replace('.', '/'), transformed);
        } catch (Throwable t) {
          t.printStackTrace(System.out);
        }
        return transformed;
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
        BTraceClassReader cr = InstrumentUtils.newClassReader(loader, classfileBuffer);
        BTraceClassWriter cw = InstrumentUtils.newClassWriter(cr);
        cw.addCushionMethods(cushionMethods);
        for (BTraceProbe p : probes) {
          p.notifyTransform(className);
          cw.addInstrumentor(p, loader);
        }
        byte[] transformed = cw.instrument();
        if (transformed == null) {
          // no instrumentation necessary
          if (log.isDebugEnabled()) {
            log.debug("skipping class {}", cr.getJavaClassName());
          }
          return classfileBuffer;
        } else {
          if (log.isDebugEnabled()) {
            log.error("transformed class {}", cr.getJavaClassName());
          }
          if (debug.isDumpClasses()) {
            debug.dumpClass(className.replace('.', '/'), transformed);
          }
        }
        return transformed;
      } catch (Throwable th) {
        log.debug("Failed to transform class {}", className, th);
        throw th;
      } finally {
        if (entered) {
          BTraceRuntime.leave();
        }
      }
    } finally {
      setupLock.readLock().unlock();
    }
  }

  static class Filter {
    private final Map<String, Integer> nameMap = new HashMap<>();
    private final Map<Pattern, Integer> nameRegexMap = new HashMap<>();
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
          addToMap(nameRegexMap, Pattern.compile(name));
        } else {
          String name = om.getClazz().replace('.', '/');
          addToMap(nameMap, name);
        }
      }
    }

    void remove(OnMethod om) {
      String name = om.getClazz().replace('.', '/');
      if (!(om.isSubtypeMatcher() || om.isClassAnnotationMatcher())) {
        if (om.isClassRegexMatcher()) {
          removeFromMap(nameRegexMap, Pattern.compile(name));
        } else {
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
