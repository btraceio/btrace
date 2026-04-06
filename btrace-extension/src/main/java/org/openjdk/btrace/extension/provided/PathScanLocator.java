package org.openjdk.btrace.extension.provided;

import org.openjdk.btrace.core.extensions.LocatorContext;
import org.openjdk.btrace.core.extensions.ProvidedDependencyLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Locator that scans configured directory paths for JAR files.
 *
 * <h3>Properties</h3>
 * <ul>
 *   <li>{@code paths} — comma-separated list of directories to scan (required)
 *   <li>{@code include} — comma-separated glob patterns to filter JARs (optional, e.g.
 *       {@code spark-core-*.jar,spark-sql-*.jar}). If not set, all {@code *.jar} files are included.
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>
 * provided.locator=[PathScanLocator]
 * provided.locator.paths=/opt/spark/jars,/databricks/jars
 * provided.locator.include=spark-core-*.jar,spark-sql-*.jar
 * </pre>
 */
public final class PathScanLocator implements ProvidedDependencyLocator {

  private static final Logger log = LoggerFactory.getLogger(PathScanLocator.class);

  @Override
  public List<Path> locate(LocatorContext context) {
    String pathsProp = context.getProperty("paths");
    if (pathsProp == null || pathsProp.trim().isEmpty()) {
      log.warn("[{}] PathScanLocator: 'paths' property not set", context.getExtensionId());
      return Collections.emptyList();
    }

    String[] includePatterns = parseInclude(context.getProperty("include"));

    List<Path> result = new ArrayList<>();
    for (String dirStr : pathsProp.split(",")) {
      Path dir = Paths.get(dirStr.trim());
      if (!Files.isDirectory(dir)) {
        log.debug("[{}] PathScanLocator: directory does not exist: {}", context.getExtensionId(), dir);
        continue;
      }
      scanDirectory(dir, includePatterns, result, context.getExtensionId());
    }

    log.info("[{}] PathScanLocator: resolved {} JARs", context.getExtensionId(), result.size());
    return result;
  }

  static void scanDirectory(Path dir, String[] includePatterns, List<Path> result, String extId) {
    if (includePatterns.length > 0) {
      // Scan with each glob pattern
      for (String pattern : includePatterns) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, pattern)) {
          for (Path entry : stream) {
            if (Files.isRegularFile(entry) && entry.toString().endsWith(".jar")) {
              result.add(entry);
            }
          }
        } catch (IOException e) {
          log.debug("[{}] PathScanLocator: error scanning {} with pattern {}: {}",
              extId, dir, pattern, e.getMessage());
        }
      }
    } else {
      // Include all JARs
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
        for (Path entry : stream) {
          if (Files.isRegularFile(entry)) {
            result.add(entry);
          }
        }
      } catch (IOException e) {
        log.debug("[{}] PathScanLocator: error scanning {}: {}", extId, dir, e.getMessage());
      }
    }
  }

  static String[] parseInclude(String include) {
    if (include == null || include.trim().isEmpty()) {
      return new String[0];
    }
    String[] parts = include.split(",");
    List<String> patterns = new ArrayList<>();
    for (String part : parts) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        patterns.add(trimmed);
      }
    }
    return patterns.toArray(new String[0]);
  }
}
