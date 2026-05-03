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
package io.btrace.extcli.tui;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class RepoScannerLite {
  static List<Path> roots() {
    List<Path> roots = new ArrayList<>();
    String home = System.getenv("BTRACE_HOME");
    if (home != null && !home.isEmpty()) roots.add(Path.of(home, "extensions"));
    roots.add(Path.of(System.getProperty("user.home"), ".btrace", "extensions"));
    String extra = System.getenv("BTRACE_EXT_PATH");
    if (extra != null && !extra.isEmpty()) {
      for (String p : extra.split(File.pathSeparator)) roots.add(Path.of(p));
    }
    return roots;
  }
}
