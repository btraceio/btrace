/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

class DefineClassSupportTest {
  @Test
  void unwrapsRecoverableFailure() {
    AssertionError cause = new AssertionError("bad bytes");
    IllegalStateException failure =
        DefineClassSupport.failure("test", new InvocationTargetException(cause));
    assertSame(cause, failure.getCause());
  }

  @Test
  void preservesFatalFailures() {
    OutOfMemoryError oom = new OutOfMemoryError();
    assertSame(
        oom, assertThrows(OutOfMemoryError.class, () -> DefineClassSupport.failure("test", oom)));
    ThreadDeath death = new ThreadDeath();
    assertSame(
        death, assertThrows(ThreadDeath.class, () -> DefineClassSupport.failure("test", death)));
  }
}
