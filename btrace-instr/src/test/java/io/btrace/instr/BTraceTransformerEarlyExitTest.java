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

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import io.btrace.core.DebugSupport;
import io.btrace.core.SharedSettings;

/**
 * Pins the load-bearing structural early-exit in {@link BTraceTransformer#transform} for
 * JVM-synthesized reflective accessor classes. This is the primary defense against the JDK 8
 * reflection-inflation StackOverflowError observed in testTraceAll on btraceio/btrace#830.
 *
 * <p>The early-exit must trigger regardless of class loader because synthetic accessor classes are
 * defined by {@code sun.reflect.DelegatingClassLoader} (JDK 8) — neither null nor the system class
 * loader — so the loader-gated {@code isSensitiveClass()} branch in the same method does NOT filter
 * them. This test pins the loader-independence so a future refactor that accidentally moves the
 * early-exit below the loader gate will fail loudly here, even on JDK 11+ build JVMs where the SOE
 * itself is not reproducible.
 */
class BTraceTransformerEarlyExitTest {

  private BTraceTransformer newTransformer() {
    return new BTraceTransformer(new DebugSupport(new SharedSettings()));
  }

  /**
   * A class loader that is neither {@code null} nor the system CL — emulates DelegatingClassLoader.
   */
  private ClassLoader nonBootstrapNonSystemLoader() {
    return new ClassLoader(null) {};
  }

  @Test
  void sunReflectGeneratedAccessorReturnsNull() throws Exception {
    BTraceTransformer transformer = newTransformer();
    byte[] result =
        transformer.transform(
            nonBootstrapNonSystemLoader(),
            "sun/reflect/GeneratedMethodAccessor42",
            null,
            null,
            new byte[] {0, 0});
    assertNull(result, "synthetic JDK 8 accessor must NOT be transformed (returns null)");
  }

  @Test
  void sunReflectGeneratedConstructorAccessorReturnsNull() throws Exception {
    BTraceTransformer transformer = newTransformer();
    byte[] result =
        transformer.transform(
            nonBootstrapNonSystemLoader(),
            "sun/reflect/GeneratedConstructorAccessor7",
            null,
            null,
            new byte[] {0, 0});
    assertNull(
        result, "synthetic JDK 8 constructor accessor must NOT be transformed (returns null)");
  }

  @Test
  void jdkInternalReflectGeneratedAccessorReturnsNull() throws Exception {
    BTraceTransformer transformer = newTransformer();
    byte[] result =
        transformer.transform(
            nonBootstrapNonSystemLoader(),
            "jdk/internal/reflect/GeneratedMethodAccessor3",
            null,
            null,
            new byte[] {0, 0});
    assertNull(result, "synthetic JDK 9-16 accessor must NOT be transformed (returns null)");
  }

  @Test
  void nullClassNameReturnsNull() throws Exception {
    BTraceTransformer transformer = newTransformer();
    byte[] result =
        transformer.transform(nonBootstrapNonSystemLoader(), null, null, null, new byte[] {0, 0});
    assertNull(
        result,
        "classes with no binary name (JDK 8 host-anonymous, JDK 15+ hidden) must NOT be transformed");
  }

  @Test
  void jdk8LambdaWrapperReturnsNull() throws Exception {
    BTraceTransformer transformer = newTransformer();
    byte[] result =
        transformer.transform(
            nonBootstrapNonSystemLoader(),
            "org/openjdk/btrace/agent/Main$$Lambda$36",
            null,
            null,
            new byte[] {0, 0});
    assertNull(result, "JDK 8 synthetic lambda wrapper (Main$$Lambda$N) must NOT be transformed");
  }

  @Test
  void jdk11LambdaWrapperReturnsNull() throws Exception {
    BTraceTransformer transformer = newTransformer();
    byte[] result =
        transformer.transform(
            nonBootstrapNonSystemLoader(),
            "org/openjdk/btrace/agent/Main$$Lambda$12/0x00000008000ab040",
            null,
            null,
            new byte[] {0, 0});
    assertNull(
        result, "JDK 11+ synthetic lambda wrapper (Main$$Lambda$N/0x...) must NOT be transformed");
  }

  @Test
  void userClassWithDoubleDollarInNameIsNotSkipped() {
    // A class whose internal name merely contains "$$" but NOT the specific
    // "$$Lambda$<digit>" pattern is a legitimate user class (e.g. Kotlin,
    // Scala, or Groovy synthetic) and must remain eligible for tracing.
    // Bypassing the short-circuit ensures the regex is anchored correctly.
    org.junit.jupiter.api.Assertions.assertFalse(
        BTraceTransformer.isSyntheticLambda("com/example/My$$Bridge"),
        "user class without Lambda$<digit> must NOT match the synthetic-lambda predicate");
    org.junit.jupiter.api.Assertions.assertFalse(
        BTraceTransformer.isSyntheticLambda("com/example/My$$Lambda$"),
        "malformed name with no digit after $$Lambda$ must NOT match");
    org.junit.jupiter.api.Assertions.assertFalse(
        BTraceTransformer.isSyntheticLambda("com/example/My$$LambdaBridge"),
        "substring match without the trailing $ must NOT match");
    org.junit.jupiter.api.Assertions.assertTrue(
        BTraceTransformer.isSyntheticLambda("Foo$$Lambda$0"),
        "JDK 8-style Lambda wrapper must match");
    org.junit.jupiter.api.Assertions.assertTrue(
        BTraceTransformer.isSyntheticLambda("Foo$$Lambda$99/0xdeadbeef"),
        "JDK 11+ named-hidden Lambda wrapper must match");
  }
}
