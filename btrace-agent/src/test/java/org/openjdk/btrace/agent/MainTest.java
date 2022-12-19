package org.openjdk.btrace.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.core.ArgsMap;

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
