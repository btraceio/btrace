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
package io.btrace.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import io.btrace.core.ArgsMap;

class MainTest {
  @Test
  void locateScriptsEmpty() {
    ArgsMap argsMap = new ArgsMap();
    List<String> scripts = Main.locateScripts(argsMap);

    assertTrue(scripts.isEmpty());
  }

  @Test
  void locateScriptsSingle() {
    ArgsMap argsMap = new ArgsMap(new String[] {"script=script1"});
    List<String> scripts = Main.locateScripts(argsMap);

    assertEquals(1, scripts.size());
  }

  @Test
  void locateScriptsMulti() {
    ArgsMap argsMap = new ArgsMap(new String[] {"script=script1:script2"});
    List<String> scripts = Main.locateScripts(argsMap);

    assertEquals(2, scripts.size());
  }

  @Test
  void locateScriptsDir() throws Exception {
    Path dir = Files.createTempDirectory("test-");
    Path script = Files.createTempFile(dir, "script-", ".btrace");
    ArgsMap argsMap = new ArgsMap(new String[] {"scriptdir=" + dir.toString()});
    List<String> scripts = Main.locateScripts(argsMap);

    assertEquals(1, scripts.size());
  }
}
