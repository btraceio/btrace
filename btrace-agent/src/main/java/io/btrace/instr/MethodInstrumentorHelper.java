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

import java.util.function.Supplier;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

public interface MethodInstrumentorHelper {
  void insertFrameReplaceStack(Label l, Type... stack);

  void insertFrameAppendStack(Label l, Type... stack);

  void insertFrameSameStack(Label l);

  void addTryCatchHandler(Label start, Label handler);

  int newVar(Type t);

  int storeAsNew();

  /**
   * Get or create a MethodTrackingContext for the given method ID. Ensures multiple instrumentors
   * for the same method share the same context.
   *
   * @param methodId unique method identifier
   * @param factory supplier to create new context if needed
   * @return shared MethodTrackingContext instance
   */
  MethodTrackingContext getOrCreateTrackingContext(
      int methodId, Supplier<MethodTrackingContext> factory);

  /**
   * Returns whether level variable caching should be used. Caching is only needed when multiple
   * handlers require level checks.
   *
   * @return true if level variable should be cached, false otherwise
   */
  boolean shouldCacheLevelVar();

  interface Accessor {
    MethodInstrumentorHelper methodHelper();
  }
}
