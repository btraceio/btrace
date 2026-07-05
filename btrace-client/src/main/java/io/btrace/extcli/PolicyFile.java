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
package io.btrace.extcli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

final class PolicyFile {
  private Path target;
  private final Properties props = new Properties();

  static PolicyFile fromArgs(String[] args) throws IOException {
    Path t = null;
    Path classpathDir = null;
    boolean home = false;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--policy-file":
          t = Path.of(args[++i]);
          break;
        case "--home":
          home = true;
          break;
        case "--classpath":
          classpathDir = Path.of(args[++i]);
          break;
      }
    }
    if (t != null && (home || classpathDir != null))
      throw new IllegalArgumentException("Use only one of --policy-file, --home, --classpath");
    PolicyFile pf = new PolicyFile();
    if (t != null) pf.target = t;
    else if (home)
      pf.target = Path.of(System.getProperty("user.home"), ".btrace", "permissions.properties");
    else if (classpathDir != null)
      pf.target = classpathDir.resolve("META-INF/btrace/permissions.properties");
    else pf.target = Path.of(System.getProperty("user.home"), ".btrace", "permissions.properties");
    pf.loadIfExists();
    return pf;
  }

  private void loadIfExists() throws IOException {
    if (Files.exists(target))
      try (InputStream is = Files.newInputStream(target)) {
        props.load(is);
      }
  }

  void updateFromArgs(String[] args) {
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--allowExtensions":
          props.setProperty("allowExtensions", args[++i]);
          break;
        case "--denyExtensions":
          props.setProperty("denyExtensions", args[++i]);
          break;
        case "--allowPrivileged":
          props.setProperty("allowPrivileged", args[++i]);
          break;
      }
    }
  }

  String describe(boolean json) {
    Map<String, Object> m =
        Map.of(
            "target", String.valueOf(target),
            "allowExtensions", props.getProperty("allowExtensions", ""),
            "denyExtensions", props.getProperty("denyExtensions", ""),
            "allowPrivileged", props.getProperty("allowPrivileged", "false"));
    if (json) return toJson(m);
    return String.format(
        "Policy: %s\n  allowExtensions=%s\n  denyExtensions=%s\n  allowPrivileged=%s",
        target, m.get("allowExtensions"), m.get("denyExtensions"), m.get("allowPrivileged"));
  }

  void save() throws IOException {
    // A bare relative filename (e.g. --policy-file perms.properties) has a null parent; only
    // create directories when there actually is a parent path.
    Path parent = target.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
    try (OutputStream os = Files.newOutputStream(tmp)) {
      props.store(os, "BTrace permissions policy");
    }
    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  Path getTarget() {
    return target;
  }

  static String toJson(Object o) {
    return ExtensionReport.toJson(o);
  }

  List<String> getAllowList() {
    return splitCsv(props.getProperty("allowExtensions", ""));
  }

  List<String> getDenyList() {
    return splitCsv(props.getProperty("denyExtensions", ""));
  }

  boolean isAllowed(String id) {
    return getAllowList().contains(id);
  }

  boolean isDenied(String id) {
    return getDenyList().contains(id);
  }

  void allow(String id) {
    List<String> allow = new ArrayList<>(getAllowList());
    if (!allow.contains(id)) allow.add(id);
    props.setProperty("allowExtensions", String.join(",", allow));
    // remove from deny if present
    List<String> deny = new ArrayList<>(getDenyList());
    deny.remove(id);
    props.setProperty("denyExtensions", String.join(",", deny));
  }

  void deny(String id) {
    List<String> deny = new ArrayList<>(getDenyList());
    if (!deny.contains(id)) deny.add(id);
    props.setProperty("denyExtensions", String.join(",", deny));
    // remove from allow if present
    List<String> allow = new ArrayList<>(getAllowList());
    allow.remove(id);
    props.setProperty("allowExtensions", String.join(",", allow));
  }

  void clear(String id) {
    List<String> allow = new ArrayList<>(getAllowList());
    allow.remove(id);
    props.setProperty("allowExtensions", String.join(",", allow));
    List<String> deny = new ArrayList<>(getDenyList());
    deny.remove(id);
    props.setProperty("denyExtensions", String.join(",", deny));
  }

  String headerSummary() {
    String allowPriv = props.getProperty("allowPrivileged", "false");
    return String.format(
        "Policy: %s | allowExtensions=%s | denyExtensions=%s | allowPrivileged=%s",
        String.valueOf(target),
        props.getProperty("allowExtensions", ""),
        props.getProperty("denyExtensions", ""),
        allowPriv);
  }

  private static List<String> splitCsv(String csv) {
    List<String> res = new ArrayList<>();
    if (csv == null || csv.trim().isEmpty()) return res;
    for (String s : csv.split(",")) {
      String t = s.trim();
      if (!t.isEmpty()) res.add(t);
    }
    return res;
  }
}
