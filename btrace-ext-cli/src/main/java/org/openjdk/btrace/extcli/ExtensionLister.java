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
          if (json) items.add(Map.of(
              "path", d.getAbsolutePath(),
              "ok", r.ok,
              "id", r.id,
              "privileged", r.privileged
          ));
          else System.out.println((r.ok ? r.id : d.getName()) + (r.privileged ? " [PRIV]" : "") + " - " + d.getAbsolutePath());
        } catch (IOException e) {
          if (!json) System.err.println("Failed to inspect " + d + ": " + e.getMessage());
        }
      }
    }
    if (json) System.out.println(ExtensionReport.toJson(items));
  }
}

