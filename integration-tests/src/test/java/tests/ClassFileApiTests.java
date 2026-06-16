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

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for BTrace probe kinds that exercise the ClassFile API backend on JDK 26+.
 *
 * <p>The target app ({@code resources.MainJdkApi}) calls {@code Math.abs(-7)} and {@code
 * Math.max(3, 99)} repeatedly. On JDK 26+ the JDK class files have class-file major version &ge;
 * 70, so instrumentation of those JDK classes goes through the ClassFile API backend. On older JDKs
 * the same tests run via the ASM backend, validating both code paths.
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
    // Use an ephemeral port to avoid conflicts with other test classes running in parallel
    try (ServerSocket ss = new ServerSocket(0)) {
      btracePort = ss.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException("Failed to find a free port", e);
    }
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
            assertTrue(stdout.contains("Math.abs returned: 7"), "Expected return value 7");
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
}
