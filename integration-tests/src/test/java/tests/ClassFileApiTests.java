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
package tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for BTrace probe kinds that exercise the ClassFile API backend on JDK 26+.
 *
 * <p>The target app ({@code resources.MainJdkApi}) repeatedly calls JDK APIs with stable bytecode
 * shapes. On JDK 26+ the JDK class files have class-file major version &ge; 70, so instrumentation
 * of those JDK classes goes through the ClassFile API backend. On older JDKs the same tests run via
 * the ASM backend for the simple Math probes. The broader smoke test asserts ClassFile API backend
 * coverage and therefore requires a JDK 26+ target runtime.
 */
public class ClassFileApiTests extends RuntimeTest {
  @BeforeAll
  public static void setup() throws Exception {
    classSetup();
  }

  @BeforeEach
  @Override
  public void reset() {
    super.reset();
  }

  @Test
  @DisplayName("ClassFile API: ENTRY probe on Math.abs")
  public void testEntry() throws Exception {
    testDynamic(
        "resources.MainJdkApi",
        "btrace/ClassFileApiEntryTest.java",
        5,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr: " + stderr);
            assertTrue(stdout.contains("Math.abs entered: abs"), "Expected entry probe output");
          }
        });
  }

  @Test
  @DisplayName("ClassFile API: RETURN probe with @Return on Math.abs")
  public void testReturnValue() throws Exception {
    testDynamic(
        "resources.MainJdkApi",
        "btrace/ClassFileApiReturnTest.java",
        5,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr: " + stderr);
            assertTrue(stdout.contains("Math.abs returned: "), "Expected return probe output");
          }
        });
  }

  @Test
  @DisplayName("ClassFile API: RETURN probe with @Duration on Math.max")
  public void testDuration() throws Exception {
    testDynamic(
        "resources.MainJdkApi",
        "btrace/ClassFileApiDurationTest.java",
        5,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr: " + stderr);
            assertTrue(stdout.contains("Math.max duration: "), "Expected duration output");
          }
        });
  }

  @Test
  @DisplayName("ClassFile API: smoke coverage for high-risk probe families")
  public void testFeatureSmoke() throws Exception {
    assumeTrue(
        targetJavaMajor() >= 26, "ClassFile API backend smoke requires a JDK 26+ target runtime");
    timeout = 5000L;
    testDynamic(
        "resources.MainJdkApi",
        "btrace/ClassFileApiFeatureSmokeTest.java",
        100,
        new ResultValidator() {
          @Override
          public void validate(String stdout, String stderr, int retcode, String jfrFile) {
            assertFalse(stdout.contains("FAILED"), "Script should not have failed");
            assertTrue(stderr.isEmpty(), "Non-empty stderr: " + stderr);
            assertContainsAll(
                stdout,
                "cfapi FIELD_GET",
                "cfapi FIELD_SET",
                "cfapi ARRAY_GET",
                "cfapi ARRAY_SET",
                "cfapi NEWARRAY",
                "cfapi SYNC_ENTRY",
                "cfapi SYNC_EXIT",
                "cfapi CALL",
                "cfapi NEW",
                "cfapi CATCH",
                "cfapi ERROR");
          }
        });
  }

  private static void assertContainsAll(String stdout, String... expectedMarkers) {
    for (String marker : expectedMarkers) {
      assertTrue(stdout.contains(marker), "Expected output marker: " + marker);
    }
  }

  private static int targetJavaMajor() {
    Path releaseFile = Paths.get(javaHome, "release");
    if (!Files.isRegularFile(releaseFile)) {
      return Runtime.version().feature();
    }

    Properties release = new Properties();
    try (InputStream input = Files.newInputStream(releaseFile)) {
      release.load(input);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read target Java release file", e);
    }
    return parseJavaMajor(release.getProperty("JAVA_VERSION", ""));
  }

  private static int parseJavaMajor(String version) {
    String normalized = version.replace("\"", "");
    if (normalized.startsWith("1.")) {
      normalized = normalized.substring(2);
    }

    StringBuilder major = new StringBuilder();
    for (int i = 0; i < normalized.length(); i++) {
      char ch = normalized.charAt(i);
      if (!Character.isDigit(ch)) {
        break;
      }
      major.append(ch);
    }
    return major.length() == 0 ? 0 : Integer.parseInt(major.toString());
  }
}
