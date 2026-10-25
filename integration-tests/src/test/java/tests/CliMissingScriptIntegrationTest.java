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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The btrace CLI given a PID but no script must explain what is missing, not crash. */
public class CliMissingScriptIntegrationTest extends RuntimeTest {

  @BeforeAll
  public static void setup() {
    classSetup();
  }

  @Test
  void pidWithoutScriptReportsTheMissingScript() throws Exception {
    List<String> output = Collections.synchronizedList(new ArrayList<String>());
    runBTrace(
        new String[] {"-p", "2020", "2147483647"},
        new ProcessOutputProcessor() {
          @Override
          public boolean onStdout(int lineno, String line) {
            output.add(line);
            return true;
          }

          @Override
          public boolean onStderr(int lineno, String line) {
            output.add(line);
            return true;
          }
        });
    String all = String.join("\n", output);
    assertFalse(all.contains("ArrayIndexOutOfBoundsException"), "client crashed; output:\n" + all);
    assertTrue(
        all.contains("Missing BTrace script"),
        "client did not report the missing script; output:\n" + all);
  }
}
