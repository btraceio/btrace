package org.openjdk.btrace.core.extensions;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class ExtensionRepository {
  public static final String REPOSITORY_LOCATION_KEY = "btrace.extensions.dir";
  private static final ExtensionRepository INSTANCE = new ExtensionRepository();

  private final Map<String, ExtensionEntry> extensionsById = new HashMap<>();
  private final Map<String, ExtensionEntry> extensionByPackage = new HashMap<>();

  private ExtensionRepository() {
    try {
      String repositoryDir = System.getProperty(REPOSITORY_LOCATION_KEY);
      Path repoDir = repositoryDir != null ? Paths.get(repositoryDir) : null;
      if (repoDir == null) {
        CodeSource source = ExtensionRepository.class.getProtectionDomain().getCodeSource();
        if (source == null) {
          String clzUrlStr =
              ClassLoader.getSystemResource(
                      ExtensionRepository.class.getName().replace('.', '/') + ".class")
                  .toString();
          clzUrlStr = clzUrlStr.replace("jar:file:", "");
          int idx = clzUrlStr.lastIndexOf("!");
          source =
              new CodeSource(
                  Paths.get(clzUrlStr.substring(0, idx)).toUri().toURL(), (CodeSigner[]) null);
        }
        URL jarLocation = source.getLocation();
        repoDir = new File(jarLocation.toURI()).toPath().getParent().resolve("ext");
      }
      if (Files.exists(repoDir) && Files.isDirectory(repoDir)) {
        Files.walkFileTree(
            repoDir,
            new FileVisitor<Path>() {
              @Override
              public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                  throws IOException {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                  throws IOException {
                if (file.toString().toLowerCase().endsWith(".jar")) {
                  ExtensionEntry entry = new ExtensionEntry(file);
                  ExtensionEntry current =
                      extensionsById.computeIfAbsent(entry.getId(), k -> entry);
                  if (current != entry) {
                    throw new IOException();
                  }
                  for (String pkg : entry.getExports()) {
                    current = extensionByPackage.computeIfAbsent(pkg, k -> entry);
                    if (current != entry) {
                      throw new IOException();
                    }
                  }
                }
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult visitFileFailed(Path file, IOException exc)
                  throws IOException {
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                  throws IOException {
                return FileVisitResult.CONTINUE;
              }
            });
      }
    } catch (URISyntaxException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static ExtensionRepository getInstance() {
    return INSTANCE;
  }

  public ExtensionEntry getExtensionById(String id) {
    return extensionsById.get(id);
  }

  public ExtensionEntry getExtensionForType(String type) {
    type = type.replace('/', '.');
    int idx = type.lastIndexOf('.');
    if (idx > -1) {
      String pkg = type.substring(0, idx);
      return extensionByPackage.get(pkg);
    }
    return null;
  }

  public Collection<ExtensionEntry> getExtensions() {
    return extensionsById.values();
  }
}
