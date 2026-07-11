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
package io.btrace.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JavaVersionCheckTest {
  @ParameterizedTest
  @CsvSource({
    "1.8, 8",
    "1.8.0_392, 8",
    "9, 9",
    "9.0.4, 9",
    "10, 10",
    "11, 11",
    "11.0.21, 11",
    "16, 16",
    "17, 17",
    "17-ea, 17",
    "21+35, 21",
    "21.0.11, 21",
    "25, 25"
  })
  void parsesFeatureVersion(String versionString, int expected) {
    assertEquals(expected, JavaVersionCheck.parseFeatureVersion(versionString));
  }

  @Test
  void unparsableVersionsYieldMinusOne() {
    assertEquals(-1, JavaVersionCheck.parseFeatureVersion(null));
    assertEquals(-1, JavaVersionCheck.parseFeatureVersion(""));
    assertEquals(-1, JavaVersionCheck.parseFeatureVersion("garbage"));
    assertEquals(-1, JavaVersionCheck.parseFeatureVersion("1."));
  }

  @Test
  void deprecationFloorIs17() {
    assertTrue(JavaVersionCheck.isDeprecated(8));
    assertTrue(JavaVersionCheck.isDeprecated(9));
    assertTrue(JavaVersionCheck.isDeprecated(11));
    assertTrue(JavaVersionCheck.isDeprecated(16));
    assertFalse(JavaVersionCheck.isDeprecated(17));
    assertFalse(JavaVersionCheck.isDeprecated(21));
    assertFalse(JavaVersionCheck.isDeprecated(25));
    // unknown versions must not warn
    assertFalse(JavaVersionCheck.isDeprecated(-1));
    assertFalse(JavaVersionCheck.isDeprecated(0));
  }

  @Test
  void currentJvmVersionIsDetected() {
    // the build toolchain is always a modern JDK; the important property is that detection works
    assertTrue(JavaVersionCheck.javaFeatureVersion() >= 8);
  }

  @Test
  void warningTextMentionsVersionAndSuppression() {
    String warning = JavaVersionCheck.deprecationWarning(11);
    assertTrue(warning.contains("Java 11"));
    assertTrue(warning.contains("deprecated"));
    assertTrue(warning.contains("next major release"));
    assertTrue(warning.contains("-D" + JavaVersionCheck.SUPPRESS_PROP + "=true"));
  }
}
