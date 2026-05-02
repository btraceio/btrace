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
package org.openjdk.btrace.extcli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

final class ExtensionLister {
  static void list(boolean json) throws IOException {
    List<Path> roots = new ArrayList<>();
    String home = System.getenv("BTRACE_HOME");
    if (home != null) roots.add(Path.of(home, "extensions"));
    roots.add(Path.of(System.getProperty("user.home"), ".btrace", "extensions"));
    String extra = System.getenv("BTRACE_EXT_PATH");
    if (extra != null) for (String p : extra.split(File.pathSeparator)) roots.add(Path.of(p));

    List<Object> items = new ArrayList<>();
    for (Path root : roots) {
      File rf = root.toFile();
      if (!rf.exists() || !rf.isDirectory()) continue;
      File[] dirs = rf.listFiles(File::isDirectory);
      if (dirs == null) continue;
      for (File d : dirs) {
        try {
          ExtensionReport r = ExtensionInspector.inspect(d.toPath());
          if (json)
            items.add(
                Map.of(
                    "path", d.getAbsolutePath(),
                    "ok", r.ok,
                    "id", r.id,
                    "privileged", r.privileged));
          else
            System.out.println(
                (r.ok ? r.id : d.getName())
                    + (r.privileged ? " [PRIV]" : "")
                    + " - "
                    + d.getAbsolutePath());
        } catch (IOException e) {
          if (!json) System.err.println("Failed to inspect " + d + ": " + e.getMessage());
        }
      }
    }
    if (json) System.out.println(ExtensionReport.toJson(items));
  }
}
