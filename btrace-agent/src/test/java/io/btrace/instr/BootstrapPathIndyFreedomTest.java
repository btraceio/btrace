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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Enforces that BTrace's {@code -javaagent premain()} bootstrap path never reaches a
 * lambda/method-reference expression. A first-time {@code invokedynamic} linkage this early in
 * target-JVM startup can race the JVM's own concurrent {@code java.lang.invoke} initialization and
 * throw {@code ClassCircularityError} (observed via {@code java.lang.ClassCircularityError:
 * java/lang/invoke/MethodHandle$1}) -- see
 * docs/superpowers/plans/2026-07-12-extension-loader-classcircularity-investigation.md for the full
 * root-cause writeup.
 *
 * <p>If this test fails, the reported call sites are exactly the lambdas/method references that
 * need converting to anonymous classes (see {@code io.btrace.extension.ExtensionLoader#initialize}
 * for the established pattern).
 */
class BootstrapPathIndyFreedomTest {

  @Test
  void premainReachesNoInvokedynamicCallSite() {
    BootstrapCallGraph.Result result =
        BootstrapCallGraph.scan(
            "io/btrace/agent/Main",
            "premain",
            "(Ljava/lang/String;Ljava/lang/instrument/Instrumentation;)V",
            owner -> owner.startsWith("io/btrace/"),
            BootstrapPathIndyFreedomTest.class.getClassLoader());

    if (!result.indySites.isEmpty()) {
      String report =
          result.indySites.stream()
              .map(IndyScanner.IndySite::toString)
              .collect(Collectors.joining("\n  "));
      assertTrue(
          false,
          "premain() reaches "
              + result.indySites.size()
              + " invokedynamic call site(s) -- convert each to an anonymous class:\n  "
              + report);
    }
  }
}
