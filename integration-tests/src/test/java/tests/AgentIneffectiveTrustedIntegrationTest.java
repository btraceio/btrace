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
package tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Trusted mode is enabled only by the {@code trusted=true} agent argument. Setting the look-alike
 * {@code -Dbtrace.trusted} system property, or {@code -Dbtrace.system.appendJar} without the
 * argument, must not be ignored silently: the agent names the argument that works.
 */
public class AgentIneffectiveTrustedIntegrationTest extends RuntimeTest {
  private static final String TRUSTED_PROPERTY_WARNING = "Ignoring system property btrace.trusted";
  private static final String APPEND_JAR_WARNING = "Ignoring btrace.system.appendJar";
  private static final String CORRECT_ARGUMENT = "trusted=true agent argument";

  @BeforeAll
  public static void setup() {
    classSetup();
  }

  @Test
  void trustedSystemPropertyIsReportedWithTheWorkingArgument() throws Exception {
    String output = startupOutput("port=0,telemetry=false", "-Dbtrace.trusted=true");

    assertTrue(output.contains(TRUSTED_PROPERTY_WARNING), output);
    assertTrue(output.contains(CORRECT_ARGUMENT), output);
  }

  @Test
  void appendJarWithoutTrustedArgumentIsReportedWithTheWorkingArgument() throws Exception {
    String output =
        startupOutput("port=0,telemetry=false", "-Dbtrace.system.appendJar=/nonexistent/lib.jar");

    assertTrue(output.contains(APPEND_JAR_WARNING), output);
    assertTrue(output.contains(CORRECT_ARGUMENT), output);
  }

  @Test
  void trustedAgentArgumentSilencesThePropertyWarning() throws Exception {
    String output = startupOutput("port=0,telemetry=false,trusted=true", "-Dbtrace.trusted=true");

    assertFalse(output.contains(TRUSTED_PROPERTY_WARNING), output);
  }

  @Test
  void noWarningWithoutTheProperty() throws Exception {
    String output = startupOutput("port=0,telemetry=false");

    assertFalse(output.contains(TRUSTED_PROPERTY_WARNING), output);
    assertFalse(output.contains(APPEND_JAR_WARNING), output);
  }

  /**
   * Launches the target; {@code getPid()} returns only once the target has printed its ready line.
   */
  private String startupOutput(String agentArguments, String... jvmArguments) throws Exception {
    TestApp app = launchTestAppWithAgent("resources.Main", agentArguments, jvmArguments);
    try {
      app.getPid();
      return app.capturedOutput();
    } finally {
      app.stop();
    }
  }
}
