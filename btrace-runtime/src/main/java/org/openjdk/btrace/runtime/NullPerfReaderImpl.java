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
package org.openjdk.btrace.runtime;

/**
 * Dummy perf reader that throws UnsupportedOperationException always. We use this when we don't
 * have access to jvmstat classes.
 *
 * @author A. Sundararajan
 */
final class NullPerfReaderImpl implements PerfReader {
  @Override
  public int perfInt(String name) {
    throw new UnsupportedOperationException(
        "jvmstat not supported, do you have tools.jar (or classes.jar) in CLASSPATH?");
  }

  @Override
  public long perfLong(String name) {
    throw new UnsupportedOperationException(
        "jvmstat not supported, do you have tools.jar (or classes.jar) in CLASSPATH?");
  }

  @Override
  public String perfString(String name) {
    throw new UnsupportedOperationException(
        "jvmstat not supported, do you have tools.jar (or classes.jar) in CLASSPATH?");
  }
}
