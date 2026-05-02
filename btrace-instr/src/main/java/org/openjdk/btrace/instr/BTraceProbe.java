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
package org.openjdk.btrace.instr;

import java.util.Collection;
import java.util.Set;
import org.objectweb.asm.ClassVisitor;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.extensions.Permission;

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
}
