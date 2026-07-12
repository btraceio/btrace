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

import java.util.ArrayList;
import java.util.List;
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
 * <p>Scans from two roots: {@code Main.<clinit>} (the class's static field initializers, which run
 * unconditionally as soon as {@code Main} is first referenced -- before {@code premain()}'s body
 * even starts) and {@code Main.premain} itself.
 *
 * <p>If this test fails, the reported call sites are exactly the lambdas/method references that
 * need converting to anonymous classes (see {@code io.btrace.extension.ExtensionLoader#initialize}
 * for the established pattern).
 */
class BootstrapPathIndyFreedomTest {

  private static final String MAIN = "io/btrace/agent/Main";
  private static final java.util.function.Predicate<String> IN_SCOPE =
      owner -> owner.startsWith("io/btrace/");

  @Test
  void bootstrapPathReachesNoInvokedynamicCallSite() {
    ClassLoader loader = BootstrapPathIndyFreedomTest.class.getClassLoader();
    List<IndyScanner.IndySite> found = new ArrayList<>();
    found.addAll(BootstrapCallGraph.scan(MAIN, "<clinit>", "()V", IN_SCOPE, loader).indySites);
    found.addAll(
        BootstrapCallGraph.scan(
                MAIN,
                "premain",
                "(Ljava/lang/String;Ljava/lang/instrument/Instrumentation;)V",
                IN_SCOPE,
                loader)
            .indySites);

    if (!found.isEmpty()) {
      String report =
          found.stream().map(IndyScanner.IndySite::toString).collect(Collectors.joining("\n  "));
      assertTrue(
          false,
          "Main.<clinit>/premain() reaches "
              + found.size()
              + " invokedynamic call site(s) -- convert each to an anonymous class:\n  "
              + report);
    }
  }
}
