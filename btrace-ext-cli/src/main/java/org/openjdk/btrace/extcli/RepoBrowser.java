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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

final class RepoBrowser {
  static void browse() throws IOException {
    browse(new String[0]);
  }

  static void browse(String[] args) throws IOException {
    List<Path> items = new ArrayList<>();
    for (Path root : RepoScanner.roots()) {
      if (!Files.isDirectory(root)) continue;
      try (Stream<Path> s = Files.list(root)) {
        for (Path p : (Iterable<Path>) s::iterator) {
          if (Files.isDirectory(p)) items.add(p);
        }
      }
    }
    if (items.isEmpty()) {
      System.out.println("No extensions found in known repositories.");
      return;
    }

    // Load or create target policy (defaults to home)
    PolicyFile policy;
    try {
      policy = PolicyFile.fromArgs(args);
    } catch (Exception e) {
      System.out.println("Failed to load policy: " + e.getMessage());
      return;
    }

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    int sel = 0;
    while (true) {
      clearScreen();
      System.out.println(policy.headerSummary());
      System.out.println(
          "Use Up/Down (or j/k) to move, 'e' to enable/disable, 'i' for details, 'q' to quit.");
      System.out.println();
      for (int i = 0; i < items.size(); i++) {
        Path dir = items.get(i);
        String id = dir.getFileName() != null ? dir.getFileName().toString() : String.valueOf(dir);
        String ver = "";
        try {
          ExtensionReport r = ExtensionInspector.inspect(dir);
          if (r != null) {
            if (r.id != null && !r.id.isEmpty()) id = r.id;
            if (r.version != null) ver = r.version;
          }
        } catch (Exception ignored) {
        }
        String verTag = (ver != null && !ver.isEmpty()) ? ("[" + ver + "]") : "";
        String state = policy.isAllowed(id) ? "[A]" : (policy.isDenied(id) ? "[D]" : "[ ]");
        String cursor = (i == sel) ? "->" : "  ";
        System.out.printf("%s %s %s%s (%s)%n", cursor, state, id, verTag, dir);
      }
      System.out.print("> ");
      String line = br.readLine();
      if (line == null) return;
      if (isUp(line)) {
        sel = (sel + items.size() - 1) % items.size();
        continue;
      }
      if (isDown(line)) {
        sel = (sel + 1) % items.size();
        continue;
      }
      line = line.trim();
      if (line.equalsIgnoreCase("q")) return;
      if (line.equalsIgnoreCase("i")) {
        showDetails(items.get(sel), br);
        continue;
      }
      if (line.equalsIgnoreCase("e")) {
        Path dir = items.get(sel);
        String id = dir.getFileName() != null ? dir.getFileName().toString() : String.valueOf(dir);
        try {
          ExtensionReport r = ExtensionInspector.inspect(dir);
          if (r != null && r.id != null && !r.id.isEmpty()) id = r.id;
          boolean currentlyAllowed = policy.isAllowed(id);
          boolean currentlyDenied = policy.isDenied(id);
          if (currentlyAllowed) {
            // disable => move to deny
            policy.deny(id);
            policy.save();
          } else if (currentlyDenied) {
            // enable => move to allow
            if (confirmEnable(br, r)) {
              policy.allow(id);
              policy.save();
            }
          } else {
            // default => ask to enable
            if (confirmEnable(br, r)) {
              policy.allow(id);
              policy.save();
            }
          }
        } catch (Exception e) {
          // ignore
        }
        continue;
      }
      // If number entered, jump to index
      try {
        int idx = Integer.parseInt(line);
        if (idx >= 0 && idx < items.size()) sel = idx;
      } catch (NumberFormatException ignored) {
      }
    }
  }

  private static boolean isUp(String line) {
    return line.contains("\u001B[A") || line.equalsIgnoreCase("k");
  }

  private static boolean isDown(String line) {
    return line.contains("\u001B[B") || line.equalsIgnoreCase("j");
  }

  private static void showDetails(Path dir, BufferedReader br) throws IOException {
    clearScreen();
    try {
      ExtensionReport rep = ExtensionInspector.inspect(dir);
      System.out.println(rep.toString());
    } catch (Exception e) {
      System.out.println("Failed to inspect: " + e.getMessage());
    }
    System.out.println();
    System.out.println("(Press Enter to continue)");
    br.readLine();
  }

  private static boolean confirmEnable(BufferedReader br, ExtensionReport rep) throws IOException {
    clearScreen();
    if (rep != null) System.out.println(rep.toString());
    System.out.println();
    System.out.print("Enable this extension? [y/N]: ");
    String ans = br.readLine();
    return ans != null && (ans.equalsIgnoreCase("y") || ans.equalsIgnoreCase("yes"));
  }

  private static void clearScreen() {
    // Best-effort clear; will just print newlines if ANSI unsupported
    System.out.print("\033[H\033[2J");
    System.out.flush();
  }
}
