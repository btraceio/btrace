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
package io.btrace.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * BTrace methods annotated by this annotation are called when the traced JVM's specified memory
 * pool exceeds specified threshold size.
 *
 * <p>The handler takes no parameter, or one {@code MemoryUsage} that receives the pool's usage when
 * the threshold was crossed; any other signature fails probe verification. It runs on the probe's
 * "BTrace Worker" thread, not on the JVM's notification thread, and an exception it throws goes to
 * the probe's {@link OnError} handlers, or to the client when there are none. Pool names depend on
 * the garbage collector: for example {@code Tenured Gen} with Serial GC, {@code G1 Old Gen} with
 * G1; {@code Metaspace} exists with every collector.
 *
 * @author A. Sundararajan
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnLowMemory {
  /** The memory pool name. */
  String pool();

  /** The usage threshold, in bytes, to watch for. */
  long threshold();
}
