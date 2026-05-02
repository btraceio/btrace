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
package org.openjdk.btrace.core.handlers;

import java.lang.management.MemoryUsage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class LowMemoryHandler {
  public final String method;
  public final String pool;
  public final String thresholdProperty;
  public final long threshold;
  public final boolean trackUsage;
  private Method executable;

  public LowMemoryHandler(
      String method, String pool, long threshold, String thresholdProperty, boolean trackUsage) {
    this.method = method;
    this.pool = pool;
    this.threshold = threshold;
    this.thresholdProperty = thresholdProperty;
    this.trackUsage = trackUsage;
  }

  public synchronized Method getMethod(Class<?> clz) throws NoSuchMethodException {
    if (executable == null) {
      executable = trackUsage ? clz.getMethod(method, MemoryUsage.class) : clz.getMethod(method);
    }
    return executable;
  }

  public void invoke(Class<?> clz, Object... args)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    getMethod(clz).invoke(clz, null, args);
  }
}
