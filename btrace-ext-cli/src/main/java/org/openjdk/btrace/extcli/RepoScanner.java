package org.openjdk.btrace.extcli;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class RepoScanner {
  static List<Path> roots() {
    List<Path> roots = new ArrayList<>();
    String home = System.getenv("BTRACE_HOME");
    if (home != null) roots.add(Path.of(home, "extensions"));
    roots.add(Path.of(System.getProperty("user.home"), ".btrace", "extensions"));
    String extra = System.getenv("BTRACE_EXT_PATH");
    if (extra != null) for (String p : extra.split(File.pathSeparator)) roots.add(Path.of(p));
    return roots;
  }

  static Path resolveById(String id) {
    for (Path root : roots()) {
      Path dir = root.resolve(id);
      if (dir.toFile().isDirectory()) return dir;
    }
    return null;
  }
}

