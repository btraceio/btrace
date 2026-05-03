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

import java.lang.instrument.Instrumentation;
import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.comm.CommandListener;

public abstract class BTraceRuntimeImplFactory<T extends BTraceRuntime.Impl> {
  private final T defaultRuntime;

  protected BTraceRuntimeImplFactory(T defaultRuntime) {
    this.defaultRuntime = defaultRuntime;
  }

  public final T getDefault() {
    return defaultRuntime;
  }

  public abstract T getRuntime(
      String className, ArgsMap args, CommandListener cmdListener, Instrumentation inst);

  public abstract boolean isEnabled();
}
