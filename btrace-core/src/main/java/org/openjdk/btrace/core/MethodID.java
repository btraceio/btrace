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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A factory class for shared method ids
 *
 * @author Jaroslav Bachorik
 */
public class MethodID {
  static final AtomicInteger lastMethodId = new AtomicInteger(1);
  // Use a concurrent map to ensure thread-safe access without external synchronization
  private static final ConcurrentHashMap<String, Integer> methodIds = new ConcurrentHashMap<>();

  /**
   * Generates a unique method id based on the provided method tag
   *
   * @param methodTag The tag used to distinguish between methods
   * @return An ID belonging to the provided method tag
   */
  public static int getMethodId(String methodTag) {
    return methodIds.computeIfAbsent(methodTag, k -> lastMethodId.getAndIncrement());
  }

  public static int getMethodId(String className, String method, String desc) {
    return getMethodId(className + "#" + method + "#" + desc);
  }
}
