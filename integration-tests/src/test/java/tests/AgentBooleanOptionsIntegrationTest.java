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

/** On/off agent options given a value other than true/false must warn and stay off. */
public class AgentBooleanOptionsIntegrationTest extends RuntimeTest {
  private static final String DEBUG_ONLY_MARKER = "debugMode is";

  @BeforeAll
  public static void setup() {
    classSetup();
  }

  @Test
  void nonBooleanDebugValueWarnsAndLeavesDebugOff() throws Exception {
    String output = startupOutput("port=0,telemetry=false,debug=no");
    assertTrue(
        output.contains("Ignoring debug=no; expected true or false"),
        "agent did not warn about debug=no; output:\n" + output);
    assertFalse(
        output.contains(DEBUG_ONLY_MARKER),
        "debug=no still enabled debug logging; output:\n" + output);
  }

  @Test
  void debugTrueStillEnablesDebug() throws Exception {
    String output = startupOutput("port=0,telemetry=false,debug=true");
    assertTrue(
        output.contains(DEBUG_ONLY_MARKER), "debug=true did not enable debug; output:\n" + output);
    assertFalse(output.contains("Ignoring debug="), "debug=true was rejected; output:\n" + output);
  }

  private String startupOutput(String agentArguments) throws Exception {
    TestApp app = launchTestAppWithAgent("resources.Main", agentArguments);
    try {
      // getPid() blocks until "ready:"; agent startup output precedes it on the same streams
      app.getPid();
      return app.capturedOutput();
    } finally {
      app.stop();
    }
  }
}
