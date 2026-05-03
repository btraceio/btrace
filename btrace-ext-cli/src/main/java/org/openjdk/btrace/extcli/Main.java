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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.openjdk.btrace.extcli.tui.ExtRepoBrowser;

public final class Main {
  public static void main(String[] args) throws Exception {
    if (args.length == 0 || Arrays.asList("-h", "--help").contains(args[0])) {
      usage();
      return;
    }
    String cmd = args[0];
    switch (cmd) {
      case "inspect":
        if (args.length < 2) {
          // No argument: open interactive TUI in-process
          ExtRepoBrowser.launch(Arrays.copyOfRange(args, 1, args.length));
          break;
        }
        boolean json = hasFlag(args, "--json");
        String arg = args[1];
        ExtensionReport report;
        Path p = Paths.get(arg);
        if (Files.exists(p)) {
          report = ExtensionInspector.inspect(p);
        } else {
          Path resolved = RepoScanner.resolveById(arg);
          if (resolved == null) {
            err("Extension id not found in known repositories: " + arg);
            return;
          }
          report = ExtensionInspector.inspect(resolved);
        }
        if (json) System.out.println(report.toJson());
        else System.out.println(report);
        break;
      case "list":
        boolean jsonList = hasFlag(args, "--json");
        ExtensionLister.list(jsonList);
        break;
      case "policy":
        runPolicy(Arrays.copyOfRange(args, 1, args.length));
        break;
      case "install":
        if (args.length < 2) {
          err(
              "Missing coordinate or path. Usage: btracex install <group:artifact:version|zip|url> [--repo <url> ...] [--id <extId>] [--dry-run]");
          System.exit(2);
        }
        runInstall(Arrays.copyOfRange(args, 1, args.length));
        break;
      default:
        err("Unknown command: " + cmd);
        usage();
    }
  }

  private static void runInstall(String[] args) throws Exception {
    String target = null;
    List<String> repos = new ArrayList<>();
    String id = null;
    boolean dryRun = false;
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if (a.equals("--repo") && i + 1 < args.length) {
        repos.add(args[++i]);
      } else if (a.equals("--id") && i + 1 < args.length) {
        id = args[++i];
      } else if (a.equals("--dry-run")) {
        dryRun = true;
      } else if (a.startsWith("--")) {
        err("Unknown option: " + a);
        usage();
        return;
      } else if (target == null) {
        target = a;
      } else {
        err("Unexpected argument: " + a);
        usage();
        return;
      }
    }
    if (repos.isEmpty()) {
      repos.add("https://repo1.maven.org/maven2");
    }
    if (target == null) {
      err("Missing coordinate or path");
      usage();
      return;
    }
    Installer.install(target, repos, id, dryRun);
  }

  private static void runPolicy(String[] args) throws IOException {
    if (args.length == 0) {
      err("policy requires a subcommand: print|set|edit");
      usage();
      return;
    }
    String sub = args[0];
    PolicyFile target = PolicyFile.fromArgs(Arrays.copyOfRange(args, 1, args.length));
    switch (sub) {
      case "print":
        System.out.println(target.describe(hasFlag(args, "--json")));
        break;
      case "set":
        target.updateFromArgs(Arrays.copyOfRange(args, 1, args.length));
        target.save();
        System.out.println("Policy saved to " + target.getTarget());
        break;
      case "edit":
        System.err.println(
            "Interactive editor not yet implemented; use 'btracex policy set' for now.");
        System.exit(2);
        break;
      default:
        err("Unknown policy subcommand: " + sub);
        usage();
    }
  }

  private static boolean hasFlag(String[] args, String flag) {
    for (String a : args) if (flag.equals(a)) return true;
    return false;
  }

  private static void usage() {
    System.out.println(
        "btracex - BTrace extensions CLI\n"
            + "Usage:\n"
            + "  btracex inspect [<path|dir|zip|id>] [--json]  # no args opens repo browser\n"
            + "  btracex list [--json]\n"
            + "  btracex policy print [--policy-file <path>|--home|--classpath <outDir>] [--json]\n"
            + "  btracex policy set [--allowExtensions <ids>] [--denyExtensions <ids>] [--allowPrivileged <bool>] [--policy-file <path>|--home|--classpath <outDir>]\n"
            + "  btracex install <groupId:artifactId:version> [--repo <url> ...] [--id <extId>] [--dry-run]\n");
  }

  private static void err(String s) {
    System.err.println(s);
  }
}
