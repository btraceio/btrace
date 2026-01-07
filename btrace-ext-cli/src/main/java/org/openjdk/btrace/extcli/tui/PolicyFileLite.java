package org.openjdk.btrace.extcli.tui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.nio.file.StandardCopyOption;

final class PolicyFileLite {
  private final Properties props = new Properties();
  private final Path target;

  static PolicyFileLite homeDefault() throws IOException {
    Path t = Path.of(System.getProperty("user.home"), ".btrace", "permissions.properties");
    return new PolicyFileLite(t);
  }

  PolicyFileLite(Path target) throws IOException {
    this.target = target;
    loadIfExists();
  }

  Path getTarget() { return target; }

  boolean isAllowed(String id) { return list("allowExtensions").contains(id); }
  boolean isDenied(String id) { return list("denyExtensions").contains(id); }

  void allow(String id) { alterLists(id, true); }
  void deny(String id) { alterLists(id, false); }
  void clear(String id) {
    List<String> al = list("allowExtensions");
    if (al.remove(id)) props.setProperty("allowExtensions", String.join(",", al));
    List<String> dl = list("denyExtensions");
    if (dl.remove(id)) props.setProperty("denyExtensions", String.join(",", dl));
  }

  private void alterLists(String id, boolean allow) {
    List<String> al = list("allowExtensions");
    List<String> dl = list("denyExtensions");
    if (allow) {
      if (!al.contains(id)) al.add(id);
      dl.remove(id);
    } else {
      if (!dl.contains(id)) dl.add(id);
      al.remove(id);
    }
    props.setProperty("allowExtensions", String.join(",", al));
    props.setProperty("denyExtensions", String.join(",", dl));
  }

  private List<String> list(String key) {
    List<String> res = new ArrayList<>();
    String csv = props.getProperty(key, "");
    if (csv == null || csv.trim().isEmpty()) return res;
    for (String s : csv.split(",")) { String t = s.trim(); if (!t.isEmpty()) res.add(t); }
    return res;
  }

  void save() throws IOException {
    Files.createDirectories(target.getParent());
    Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
    try (OutputStream os = Files.newOutputStream(tmp)) { props.store(os, "BTrace permissions policy"); }
    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  String getAllowPrivileged() { return props.getProperty("allowPrivileged", "false"); }

  void toggleAllowPrivileged() {
    String cur = props.getProperty("allowPrivileged", "false");
    boolean b = Boolean.parseBoolean(cur);
    props.setProperty("allowPrivileged", Boolean.toString(!b));
  }

  private void loadIfExists() throws IOException {
    if (Files.exists(target)) try (InputStream is = Files.newInputStream(target)) { props.load(is); }
  }
}
