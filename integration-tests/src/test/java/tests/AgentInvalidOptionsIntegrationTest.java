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
 * An invalid command-server option on {@code -javaagent} must not take the target JVM down. The
 * agent names the rejected option and value on one line, starts without a command server, and the
 * application runs on.
 */
public class AgentInvalidOptionsIntegrationTest extends RuntimeTest {
  @BeforeAll
  public static void setup() {
    classSetup();
  }

  @Test
  void outOfRangePortKeepsTargetRunningWithoutServer() throws Exception {
    String output = startupOutput("telemetry=false,port=70000");
    assertRejectedWithoutCrash(output, "port=70000");
  }

  @Test
  void nonLoopbackBindAddressKeepsTargetRunningWithoutServer() throws Exception {
    String output = startupOutput("telemetry=false,port=0,bindAddress=0.0.0.0");
    assertRejectedWithoutCrash(output, "bindAddress=0.0.0.0");
  }

  private static void assertRejectedWithoutCrash(String output, String rejectedOption) {
    assertTrue(
        output.contains(rejectedOption),
        "agent output does not name the rejected option " + rejectedOption + ":\n" + output);
    assertFalse(output.contains("FATAL"), "agent reported a fatal failure:\n" + output);
    assertFalse(
        output.contains("\tat io.btrace."),
        "agent printed a stack trace for a configuration error:\n" + output);
  }

  /**
   * Launches the target; {@code getPid()} returns only once the target has printed its ready line.
   */
  private String startupOutput(String agentArguments) throws Exception {
    TestApp app = launchTestAppWithAgent("resources.Main", agentArguments);
    try {
      app.getPid();
      return app.capturedOutput();
    } finally {
      app.stop();
    }
  }
}
