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
package io.btrace.qe.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class TargetLauncherTest {
  private static final Path PROBES = Paths.get("/probes");
  private static final Path RUN = Paths.get("/run");

  @Test
  void emptyArgsResolveToExactlyThePort() {
    assertEquals("port=4242", TargetLauncher.resolveAgentArgs("", 4242, PROBES, RUN));
  }

  @Test
  void portIsAppendedWhenTheCaseSetsNone() {
    assertEquals(
        "script=/probes/P.class,scriptOutputFile=/run/out,port=4242",
        TargetLauncher.resolveAgentArgs(
            "script=${probeClassesDir}/P.class,scriptOutputFile=${runDir}/out", 4242, PROBES, RUN));
  }

  @Test
  void portPlaceholderIsSubstitutedAndNotAppendedTwice() {
    assertEquals(
        "noServer=false,port=4242",
        TargetLauncher.resolveAgentArgs("noServer=false,port=${port}", 4242, PROBES, RUN));
  }

  @Test
  void explicitPortKeyIsLeftAloneForPortAbuseCases() {
    assertEquals("port=-1", TargetLauncher.resolveAgentArgs("port=-1", 4242, PROBES, RUN));
  }

  @Test
  void keysThatMerelyContainPortDoNotSuppressTheHarnessPort() {
    assertEquals(
        "probePort=1,port=4242", TargetLauncher.resolveAgentArgs("probePort=1", 4242, PROBES, RUN));
  }
}
