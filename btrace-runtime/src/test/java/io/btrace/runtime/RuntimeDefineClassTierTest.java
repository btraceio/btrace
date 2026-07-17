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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.ArgsMap;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Loads version-specific runtime implementations reflectively so this Java-8 test source can
 * exercise every deterministic definition-failure seam when higher-version output is present.
 */
class RuntimeDefineClassTierTest {
  @Test
  void everyTierUsesTheSharedFailureBoundary() throws Exception {
    LinkageError java8Cause = new LinkageError("Java 8 runtime");
    IllegalStateException java8Failure = BTraceRuntimeImpl_8.definitionFailureForTest(java8Cause);
    assertSame(java8Cause, java8Failure.getCause());
    assertTrueMessage(java8Failure, "Java 8 runtime");
    assertTierFailure("io.btrace.runtime.BTraceRuntimeImpl_9", "Java 9 runtime");
    assertTierFailure("io.btrace.runtime.BTraceRuntimeImpl_11", "Java 11 runtime");
  }

  @Test
  void java8DefinitionOfInvalidBytesReturnsNormalizedFailure() {
    BTraceRuntimeImpl_8 runtime =
        new BTraceRuntimeImpl_8("issue-888-invalid", new ArgsMap(), command -> {}, null);
    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> runtime.defineClass(new byte[] {0, 1, 2}));
    assertNotNull(failure.getCause());
    assertTrueMessage(failure, "Java 8 runtime");
  }

  private static void assertTierFailure(String className, String tier) throws Exception {
    Class<?> type = Class.forName(className);
    Method seam = type.getDeclaredMethod("definitionFailureForTest", Throwable.class);
    seam.setAccessible(true);
    LinkageError cause = new LinkageError(className);
    IllegalStateException failure = (IllegalStateException) seam.invoke(null, cause);
    assertSame(cause, failure.getCause());
    assertTrueMessage(failure, tier);
  }

  private static void assertTrueMessage(IllegalStateException failure, String tier) {
    assertTrue(failure.getMessage().contains(tier));
  }
}
