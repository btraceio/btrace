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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the sensitive-class filter entries that protect against the JDK 8 reflection-inflation
 * StackOverflowError. When the agent makes any reflective Method.invoke() past the inflation
 * threshold, the JVM defines a synthetic accessor class under sun/reflect/Generated* (JDK 8) or
 * jdk/internal/reflect/ Generated* (JDK 9-16). If those classes are not filtered, BTraceTransformer
 * will instrument them, ASM frame computation will issue more reflective calls, the cascade
 * recurses, and the agent crashes during testTraceAll on JDK 8.
 *
 * <p>Coverage is loader-independent because synthetic accessors are defined in
 * sun.reflect.DelegatingClassLoader (not bootstrap, not system); the structural early-exit in
 * BTraceTransformer.transform() ALSO does not depend on loader, but this test pins the
 * SENSITIVE_CLASSES list as defense-in-depth against future refactors of the transformer entry
 * path.
 */
class ClassFilterSensitiveTest {

  @Test
  void sunReflectGeneratedAccessorIsSensitive() {
    assertTrue(
        ClassFilter.isSensitiveClass("sun/reflect/GeneratedMethodAccessor42"),
        "sun/reflect/GeneratedMethodAccessor* must be sensitive — instrumenting it triggers JDK 8 inflation cascade");
    assertTrue(
        ClassFilter.isSensitiveClass("sun/reflect/GeneratedConstructorAccessor1"),
        "sun/reflect/GeneratedConstructorAccessor* must be sensitive — same cascade source via constructor inflation");
    assertTrue(
        ClassFilter.isSensitiveClass("sun/reflect/GeneratedSerializationConstructorAccessor7"),
        "sun/reflect/GeneratedSerializationConstructorAccessor* must be sensitive — same cascade source");
  }

  @Test
  void jdkInternalReflectIsSensitive() {
    assertTrue(
        ClassFilter.isSensitiveClass("jdk/internal/reflect/GeneratedMethodAccessor3"),
        "JDK 9-16 renames the synthetic accessors under jdk/internal/reflect — must be sensitive too");
  }

  @Test
  void appClassesAreNotSensitive() {
    assertFalse(
        ClassFilter.isSensitiveClass("com/example/MyApp"),
        "Ordinary application classes must not be filtered");
    assertFalse(
        ClassFilter.isSensitiveClass("org/junit/jupiter/api/Test"),
        "Library classes outside the sensitive prefixes must not be filtered");
  }
}
