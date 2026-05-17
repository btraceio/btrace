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

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.extensions.Permission;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.objectweb.asm.ClassVisitor;

public interface BTraceProbe {
  /**
   * Returns the action method prefix for this probe. This is computed once and cached.
   *
   * <p>Format: BTRACE_METHOD_PREFIX + className.replace('/', '$') + "$" Example:
   * "$btrace$com$example$MyProbe$"
   *
   * @return cached action prefix
   */
  String getActionPrefix();

  Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr);

  /**
   * Returns the {@link OnMethod} handlers applicable to the described class, using raw metadata
   * instead of an ASM {@code ClassReader}. Used by backends that cannot construct a {@link
   * BTraceClassReader} (e.g. the ClassFile API backend for unsupported class versions).
   *
   * <p>The default implementation returns an empty list. Full implementations (e.g. {@link
   * BTraceProbeNode} and {@link BTraceProbePersisted}) override this to perform real matching.
   */
  default Collection<OnMethod> getApplicableHandlers(ClassMeta meta) {
    return Collections.emptyList();
  }

  byte[] getFullBytecode();

  byte[] getDataHolderBytecode();

  String getClassName();

  String getClassName(boolean internal);

  boolean isClassRenamed();

  boolean isTransforming();

  boolean isVerified();

  void notifyTransform(String className);

  Iterable<OnMethod> onmethods();

  Iterable<OnProbe> onprobes();

  Class<?> register(BTraceRuntime.Impl rt, BTraceTransformer t);

  /**
   * @return the defined probe {@link Class}, or {@code null} if the probe has not been registered
   *     (or has been unregistered).
   */
  Class<?> getProbeClass();

  void unregister();

  boolean willInstrument(Class<?> clz);

  void checkVerified();

  void copyHandlers(ClassVisitor copyingVisitor);

  void applyArgs(ArgsMap argsMap);

  BTraceRuntime.Impl getRuntime();

  /**
   * Returns the set of permissions required by this probe.
   *
   * @return unmodifiable set of required permissions
   */
  Set<Permission> getRequiredPermissions();

  /**
   * Look up a previously cached {@link MethodHandle} for a handler on this probe.
   *
   * <p>The cache is per-probe so it dies naturally when the probe object is collected — no
   * cross-probe scan required on unregister. Returns {@code null} on miss, or when the probe
   * implementation has no cache (e.g. stub probes in tests).
   */
  default MethodHandle getCachedHandler(String handlerName, MethodType type) {
    return null;
  }

  /**
   * Store a resolved handler {@link MethodHandle} for subsequent lookups. Implementations without a
   * backing cache (e.g. stub probes in tests) may silently drop the entry.
   */
  default void cacheHandler(String handlerName, MethodType type, MethodHandle mh) {
    // no-op by default
  }
}
