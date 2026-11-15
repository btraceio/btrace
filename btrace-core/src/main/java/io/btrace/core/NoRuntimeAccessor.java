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
package io.btrace.core;

/**
 * The runtime accessor in effect until the agent sets its own at startup: there is no runtime yet.
 * A class and not a lambda, because {@link BTraceRuntime} is initialized on the agent's premain
 * path, where a first invokedynamic linkage can race the JVM's own {@code java.lang.invoke}
 * initialization.
 */
final class NoRuntimeAccessor implements BTraceRuntime.BTraceRuntimeAccessor {
  @Override
  public BTraceRuntime.Impl getRt() {
    return null;
  }
}
