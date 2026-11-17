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
package io.btrace.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompilerHelperBTraceHomeTest {
  @TempDir Path tmp;

  private Path jar(String dir, String name) throws IOException {
    Path d = Files.createDirectories(tmp.resolve(dir));
    return Files.createFile(d.resolve(name));
  }

  @Test
  void distributionJarOnLauncherClassPathGivesHome() throws IOException {
    // What bin/btrace and bin/btracec pass: -cp <home>/libs/btrace.jar:<tools.jar>:...
    Path btraceJar = jar("home/libs", "btrace.jar");
    String cp = btraceJar + File.pathSeparator + "/nonexistent/tools.jar";
    assertEquals(
        tmp.resolve("home").toAbsolutePath().toString(),
        CompilerHelper.btraceHomeFromClassPath(cp));
  }

  @Test
  void legacyCompilerJarStillGivesHome() throws IOException {
    Path compilerJar = jar("old/libs", "btrace-compiler-2.2.6.jar");
    assertEquals(
        tmp.resolve("old").toAbsolutePath().toString(),
        CompilerHelper.btraceHomeFromClassPath(compilerJar.toString()));
  }

  @Test
  void jarOutsideLibsOrOtherJarGivesNothing() throws IOException {
    Path notInLibs = jar("build", "btrace.jar");
    Path otherJar = jar("x/libs", "my-btrace.jar");
    assertNull(CompilerHelper.btraceHomeFromClassPath(notInLibs + File.pathSeparator + otherJar));
    assertNull(CompilerHelper.btraceHomeFromClassPath(null));
    assertNull(
        CompilerHelper.btraceHomeFromClassPath(tmp.resolve("missing/libs/btrace.jar").toString()));
  }
}
