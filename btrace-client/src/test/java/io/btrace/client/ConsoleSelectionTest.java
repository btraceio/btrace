/*
 * Copyright (c) 2008, 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
package io.btrace.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.JavaVersionCheck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The client bypasses {@link System#console()} on Java 22 only, because that release writes
 * standard output to stderr.
 *
 * <p>The decision must follow the JVM actually running the client. It used to be derived from a
 * {@code release} file located through {@code JAVA_HOME}, which is not required to be set, need not
 * point at the running JVM, and cost a file read plus two unchecked-exception surfaces inside a
 * static initializer - where any throw becomes an {@code ExceptionInInitializerError} that stops
 * the CLI from starting at all.
 */
class ConsoleSelectionTest {

  @Test
  @DisplayName("Java 22 bypasses the console")
  void java22BypassesConsole() {
    assertTrue(Main.suppressConsole(22));
  }

  @ParameterizedTest(name = "Java {0} uses the console")
  @ValueSource(ints = {8, 11, 17, 21, 23, 24, 25, 26, 27})
  @DisplayName("every other release uses the console")
  void otherVersionsUseConsole(int featureVersion) {
    assertFalse(
        Main.suppressConsole(featureVersion),
        "only Java 22 has the stderr redirection; " + featureVersion + " must keep the console");
  }

  @Test
  @DisplayName("an undeterminable version falls back to using the console")
  void unknownVersionUsesConsole() {
    assertFalse(
        Main.suppressConsole(-1),
        "an unknown version must behave as before: use the console rather than guess");
  }

  @Test
  @DisplayName("the running JVM's version is resolved without JAVA_HOME")
  void runningVersionResolvesIndependentlyOfJavaHome() {
    // The suite runs both with and without JAVA_HOME set; neither may change the answer, and
    // neither may leave the version undeterminable.
    int featureVersion = JavaVersionCheck.javaFeatureVersion();

    assertTrue(
        featureVersion >= 8,
        "the running JVM's feature version must be resolvable from system properties alone,"
            + " independently of JAVA_HOME; got "
            + featureVersion);
    assertEquals(
        JavaVersionCheck.parseFeatureVersion(System.getProperty("java.specification.version")),
        featureVersion,
        "the version must come from the running JVM, not from an external installation");
  }
}
