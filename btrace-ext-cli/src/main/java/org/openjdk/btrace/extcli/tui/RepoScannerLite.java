package org.openjdk.btrace.extcli.tui;

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

