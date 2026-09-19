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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.ArgsMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MainTest {
  @Test
  void commaSeparatedGrantListSurvivesArgumentSplitting() {
    ArgsMap args = Main.parseAgentArgs("script=Foo.class,grant=NETWORK,THREADS,grantAll=false");

    assertEquals("Foo.class", args.get("script"));
    assertEquals("NETWORK,THREADS", args.get("grant"));
    assertEquals("false", args.get("grantAll"));
    assertNull(args.get("THREADS"));
  }

  @Test
  void everyListValuedKeyContinuesAcrossCommas() {
    ArgsMap args =
        Main.parseAgentArgs(
            "deny=FILE_WRITE,PROCESS,allowExtensions=a.b,c.d,denyExtensions=e,f,"
                + "probes=DriverTracer,ExecutorTracer,debug=true");

    assertEquals("FILE_WRITE,PROCESS", args.get("deny"));
    assertEquals("a.b,c.d", args.get("allowExtensions"));
    assertEquals("e,f", args.get("denyExtensions"));
    assertEquals("DriverTracer,ExecutorTracer", args.get("probes"));
    assertNull(args.get("ExecutorTracer"));
    assertEquals("true", args.get("debug"));
  }

  @Test
  void bareTokensStayKeysOutsideLists() {
    ArgsMap plain = Main.parseAgentArgs("debug=true,help");
    assertEquals("", plain.get("help"));

    ArgsMap afterList = Main.parseAgentArgs("grant=NETWORK,help");
    assertEquals("NETWORK", afterList.get("grant"));
    assertEquals("", afterList.get("help"));

    ArgsMap afterScalar = Main.parseAgentArgs("debug=true,THREADS");
    assertEquals("", afterScalar.get("THREADS"));
    assertNull(afterScalar.get("grant"));
  }

  @Test
  void nullAndEmptyArgumentStringsParseWithoutLists() {
    assertNull(Main.parseAgentArgs(null).get("grant"));
    assertNull(Main.parseAgentArgs("").get("grant"));
  }

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

  @Test
  void noServerArgumentDisablesEndpoint() {
    assertFalse(Main.shouldStartServer(new ArgsMap(new String[] {"noServer=true"})));
  }

  @Test
  void startupScriptDefaultsToNoServer() {
    assertFalse(Main.shouldStartServer(new ArgsMap(new String[] {"script=probe.class"})));
  }

  @Test
  void explicitServerOverridesStartupScriptDefault() {
    assertTrue(
        Main.shouldStartServer(new ArgsMap(new String[] {"script=probe.class", "noServer=false"})));
  }
}
