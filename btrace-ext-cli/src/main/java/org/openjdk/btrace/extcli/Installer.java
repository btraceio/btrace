package org.openjdk.btrace.extcli;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import java.util.Map;

final class Installer {
  private Installer() {}

  static void install(String target, List<String> repos, String id, boolean dryRun) throws Exception {
    Path zipPath;
    String derivedId = id;

    if (isUrl(target)) {
      if (dryRun) {
        System.out.println("[DRY-RUN] Would download: " + target);
        return;
      }
      zipPath = downloadToTemp(target);
      derivedId = derivedId != null ? derivedId : deriveIdFromZipName(zipPath.getFileName() != null ? zipPath.getFileName().toString() : zipPath.toString());
    } else if (target.endsWith(".zip") && Files.exists(Path.of(target))) {
      zipPath = Path.of(target);
      derivedId = derivedId != null ? derivedId : deriveIdFromZipName(Path.of(target).getFileName().toString());
    } else if (target.contains(":")) {
      // GAV coordinate: group:artifact:version
      String[] parts = target.split(":");
      if (parts.length != 3) throw new IllegalArgumentException("Invalid coordinate. Expected groupId:artifactId:version");
      String groupId = parts[0];
      String artifactId = parts[1];
      String version = parts[2];
      if (derivedId == null) derivedId = artifactId;
      List<String> candidates = new ArrayList<>();
      String groupPath = groupId.replace('.', '/');
      for (String repo : repos) {
        String base = repo.endsWith("/") ? repo.substring(0, repo.length()-1) : repo;
        candidates.add(base + "/" + groupPath + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "-extension.zip");
        candidates.add(base + "/" + groupPath + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".zip");
      }
      if (dryRun) {
        System.out.println("[DRY-RUN] Candidate URLs:");
        for (String c : candidates) System.out.println("  " + c);
        return;
      }
      zipPath = tryDownloadAny(candidates);
      if (zipPath == null) throw new IOException("Failed to download extension from provided repositories.");
    } else {
      throw new IllegalArgumentException("Unrecognized input: provide a zip path, URL, or group:artifact:version");
    }

    // Validate zip contains -api.jar and -impl.jar, and install
    if (dryRun) {
      System.out.println("[DRY-RUN] Would install zip: " + zipPath);
      return;
    }
    installZip(zipPath, derivedId);
  }

  private static boolean isUrl(String s) {
    return s.startsWith("http://") || s.startsWith("https://");
  }

  private static Path downloadToTemp(String url) throws IOException {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setConnectTimeout(10000);
    conn.setReadTimeout(20000);
    conn.setInstanceFollowRedirects(true);
    int code = conn.getResponseCode();
    if (code != 200) throw new IOException("HTTP " + code + " for " + url);
    Path tmp = Files.createTempFile("btracex-download-", ".zip");
    try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
      Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
    }
    tmp.toFile().deleteOnExit();
    return tmp;
  }

  private static Path tryDownloadAny(List<String> urls) {
    for (String u : urls) {
      try {
        return downloadToTemp(u);
      } catch (IOException ignored) { }
    }
    return null;
  }

  private static String deriveIdFromZipName(String name) {
    if (name == null) return "extension";
    // <id>-<version>-extension.zip or <id>-<version>.zip -> <id>
    String base = name;
    if (base.endsWith("-extension.zip")) base = base.substring(0, base.length()-"-extension.zip".length());
    else if (base.endsWith(".zip")) base = base.substring(0, base.length()-".zip".length());
    int idx = base.lastIndexOf('-');
    return idx > 0 ? base.substring(0, idx) : base;
  }

  private static void installZip(Path zipPath, String id) throws IOException {
    if (id == null || id.isEmpty()) id = deriveIdFromZipName(zipPath.getFileName() != null ? zipPath.getFileName().toString() : zipPath.toString());
    Path targetRoot = getExtensionsRoot();
    Path targetDir = targetRoot.resolve(id);
    Files.createDirectories(targetDir);

    try (FileSystem fs = FileSystems.newFileSystem(zipPath, (ClassLoader) null)) {
      Path root = fs.getPath("/");
      // Validate presence of -api.jar and -impl.jar
      boolean hasApi = false, hasImpl = false;
      try (Stream<Path> s = Files.walk(root)) {
        for (Path p : (Iterable<Path>) s::iterator) {
          if (p.getFileName() == null) continue;
          String fn = p.getFileName().toString();
          if (fn.endsWith("-api.jar")) hasApi = true;
          if (fn.endsWith("-impl.jar")) hasImpl = true;
        }
      }
      if (!hasApi || !hasImpl) throw new IOException("Invalid extension zip; missing -api.jar or -impl.jar");
      // Copy all contents
      try (Stream<Path> s = Files.walk(root)) {
        for (Path p : (Iterable<Path>) s::iterator) {
          if (Files.isDirectory(p)) continue;
          Path rel = root.relativize(p);
          Path dest = targetDir.resolve(rel.toString());
          Files.createDirectories(dest.getParent());
          Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
    System.out.println("Installed extension '" + id + "' into: " + targetDir);
    try {
      ExtensionReport rep = ExtensionInspector.inspect(targetDir);
      System.out.println(rep.toString());
      System.out.println();
      System.out.println("Hint: To enable/disable this extension for implementations, edit your policy:");
      System.out.println("  btracex policy edit --home");
      System.out.println("Or set explicitly:");
      System.out.println("  btracex policy set --allowExtensions " + id + " --policy-file ~/.btrace/permissions.properties");
      if (rep.privileged) {
        System.out.println();
        System.out.println("Note: This extension requires privileged permissions. You can allow all privileged extensions with:");
        System.out.println("  btracex policy set --allowPrivileged true --policy-file ~/.btrace/permissions.properties");
      }
    } catch (Exception ignored) { }
  }

  private static Path getExtensionsRoot() throws IOException {
    String home = System.getenv("BTRACE_HOME");
    if (home != null && !home.isEmpty()) {
      Path p = Path.of(home, "extensions");
      Files.createDirectories(p);
      return p;
    }
    Path user = Path.of(System.getProperty("user.home"), ".btrace", "extensions");
    Files.createDirectories(user);
    return user;
  }
}
