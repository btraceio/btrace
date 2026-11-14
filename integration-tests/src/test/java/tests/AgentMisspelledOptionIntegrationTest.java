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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * An agent argument that is one typo away from a BTrace option is reported instead of being dropped
 * silently; the agent still starts. Names that are not near an option are script arguments and stay
 * quiet.
 */
public class AgentMisspelledOptionIntegrationTest extends RuntimeTest {
  private static final String WARNING = "Unknown agent option";

  @BeforeAll
  public static void setup() {
    classSetup();
  }

  @Test
  void misspelledOptionIsReportedAndTheAgentStillStarts() throws Exception {
    String output = startupOutput("port=0,telemetry=false,debgu=true");

    assertTrue(output.contains(WARNING + " 'debgu'"), output);
    assertTrue(output.contains("did you mean 'debug'"), output);
  }

  @Test
  void scriptArgumentsAndSystemPropertiesAreNotReported() throws Exception {
    String output =
        startupOutput("port=0,telemetry=false,level=1,filterValue=custom,otel.endpoint=x,$a.b=c");

    assertFalse(output.contains(WARNING), output);
  }

  @Test
  void removedLibsAndAgentJarOptionsAreIgnoredLikeAnyUnknownArgument() throws Exception {
    String output = startupOutput("port=0,telemetry=false,libs=foo,agentJar=/x.jar");

    assertFalse(output.contains(WARNING), output);
    assertFalse(output.contains("has no effect"), output);
    assertFalse(output.contains("WARN"), output);
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
