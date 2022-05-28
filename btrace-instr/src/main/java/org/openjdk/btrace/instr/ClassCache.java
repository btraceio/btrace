/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.openjdk.btrace.instr.ClassInfo.ClassName;

/**
 * A simple class cache holding {@linkplain ClassInfo} instances and being searchable either by
 * {@linkplain Class} or a tuple of {@code (className, classLoader)}
 *
 * @author Jaroslav Bachorik
 */
public final class ClassCache {
  private static final class ClassLoaderReference extends PhantomReference<ClassLoader> {
    final String id;

    public ClassLoaderReference(ClassLoader referent, ReferenceQueue<? super ClassLoader> q) {
      super(referent, q);
      this.id = getClKey(referent);
    }
  }

  private final Map<ClassLoaderReference, ClassLoaderReference> loaderRefs =
      new ConcurrentHashMap<>();
  private final ReferenceQueue<ClassLoader> cleanupQueue = new ReferenceQueue<>();
  private final ConcurrentMap<String, ConcurrentMap<ClassName, ClassInfo>> cacheMap =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<ClassName, ClassInfo> bootstrapInfos = new ConcurrentHashMap<>(500);

  private final Timer cleanupTimer = new Timer(true);

  public static ClassCache getInstance() {
    return Singleton.INSTANCE;
  }

  ClassCache(long cleanupPeriod) {
    cleanupTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            ClassLoaderReference ref = null;
            while ((ref = (ClassLoaderReference) cleanupQueue.poll()) != null) {
              cacheMap.remove(ref.id);
              loaderRefs.remove(ref);
            }
          }
        },
        cleanupPeriod,
        cleanupPeriod);
  }

  public ClassInfo get(Class<?> clz) {
    return get(clz.getClassLoader(), clz.getName());
  }

  /**
   * Returns a cached {@linkplain ClassInfo} value. If the corresponding value has not been cached
   * yet then it is created and put into the cache.
   *
   * @param cl The associated {@linkplain ClassLoader}
   * @param className The Java class name or internal class name
   */
  public ClassInfo get(ClassLoader cl, String className) {
    return get(cl, new ClassName(className));
  }

  ClassInfo get(ClassLoader cl, ClassName className) {
    ConcurrentMap<ClassName, ClassInfo> infos = getInfos(cl);

    return infos.computeIfAbsent(className, k -> new ClassInfo(ClassCache.this, cl, k));
  }

  ConcurrentMap<ClassName, ClassInfo> getInfos(ClassLoader cl) {
    if (cl == null) {
      return bootstrapInfos;
    }
    boolean[] rslt = new boolean[] {false};
    ConcurrentMap<ClassName, ClassInfo> infos =
        cacheMap.computeIfAbsent(
            getClKey(cl),
            k -> {
              rslt[0] = true;
              return new ConcurrentHashMap<>(500);
            });
    if (rslt[0]) {
      ClassLoaderReference ref = new ClassLoaderReference(cl, cleanupQueue);
      loaderRefs.put(ref, ref);
    }
    return infos;
  }

  private static String getClKey(ClassLoader cl) {
    return cl.getClass().getName() + "@" + System.identityHashCode(cl);
  }

  int getSize() {
    return cacheMap.size();
  }

  private static final class Singleton {
    private static final ClassCache INSTANCE = new ClassCache(5000);
  }
}
