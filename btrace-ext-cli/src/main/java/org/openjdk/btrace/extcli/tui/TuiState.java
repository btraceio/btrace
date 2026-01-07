package org.openjdk.btrace.extcli.tui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class TuiState {
  String filter = "";
  String sortKey = "ID";
  boolean sortAsc = true;
  double splitRatio = 0.65d;

  static TuiState load() {
    TuiState s = new TuiState();
    try {
      Path dir = Path.of(System.getProperty("user.home"), ".btrace");
      Path f = dir.resolve("tui-state.properties");
      if (Files.exists(f)) {
        Properties p = new Properties();
        try (InputStream is = Files.newInputStream(f)) { p.load(is); }
        s.filter = p.getProperty("filter", s.filter);
        s.sortKey = p.getProperty("sortKey", s.sortKey);
        s.sortAsc = Boolean.parseBoolean(p.getProperty("sortAsc", Boolean.toString(s.sortAsc)));
        try { s.splitRatio = Double.parseDouble(p.getProperty("splitRatio", Double.toString(s.splitRatio))); } catch (NumberFormatException ignored) {}
      }
    } catch (IOException ignored) { }
    return s;
  }

  static void save(TuiState s) {
    try {
      Path dir = Path.of(System.getProperty("user.home"), ".btrace");
      Files.createDirectories(dir);
      Path f = dir.resolve("tui-state.properties");
      Properties p = new Properties();
      p.setProperty("filter", s.filter != null ? s.filter : "");
      p.setProperty("sortKey", s.sortKey != null ? s.sortKey : "ID");
      p.setProperty("sortAsc", Boolean.toString(s.sortAsc));
      p.setProperty("splitRatio", Double.toString(s.splitRatio));
      try (OutputStream os = Files.newOutputStream(f)) { p.store(os, "BTrace TUI state"); }
    } catch (IOException ignored) { }
  }
}

