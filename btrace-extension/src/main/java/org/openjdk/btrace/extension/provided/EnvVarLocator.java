package org.openjdk.btrace.extension.provided;

import org.openjdk.btrace.core.extensions.LocatorContext;
import org.openjdk.btrace.core.extensions.ProvidedDependencyLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Locator that resolves JARs from an environment-variable-based installation root.
 *
 * <h3>Properties</h3>
 * <ul>
 *   <li>{@code envVar} — environment variable pointing to the installation root (required,
 *       e.g. {@code SPARK_HOME})
 *   <li>{@code subdir} — subdirectory under the root containing JARs (default: {@code lib})
 *   <li>{@code include} — comma-separated glob patterns to filter JARs (optional)
 *   <li>{@code fallbackPaths} — comma-separated directories to try if the env var is not set
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>
 * provided.locator=[EnvVarLocator]
 * provided.locator.envVar=SPARK_HOME
 * provided.locator.subdir=jars
 * provided.locator.fallbackPaths=/opt/spark/jars,/databricks/jars
 * provided.locator.include=spark-core-*.jar,spark-sql-*.jar
 * </pre>
 */
public final class EnvVarLocator implements ProvidedDependencyLocator {

  private static final Logger log = LoggerFactory.getLogger(EnvVarLocator.class);

  @Override
  public List<Path> locate(LocatorContext context) {
    String envVarName = context.getProperty("envVar");
    if (envVarName == null || envVarName.trim().isEmpty()) {
      log.warn("[{}] EnvVarLocator: 'envVar' property not set", context.getExtensionId());
      return Collections.emptyList();
    }

    String subdir = context.getProperty("subdir", "lib");
    String[] includePatterns = PathScanLocator.parseInclude(context.getProperty("include"));

    // Try the environment variable first
    String envValue = context.getEnv(envVarName);
    if (envValue != null && !envValue.trim().isEmpty()) {
      Path jarsDir = Paths.get(envValue.trim(), subdir);
      if (Files.isDirectory(jarsDir)) {
        log.info("[{}] EnvVarLocator: using {}={} → {}",
            context.getExtensionId(), envVarName, envValue, jarsDir);
        List<Path> result = new ArrayList<>();
        PathScanLocator.scanDirectory(jarsDir, includePatterns, result, context.getExtensionId());
        if (!result.isEmpty()) {
          log.info("[{}] EnvVarLocator: resolved {} JARs from {}",
              context.getExtensionId(), result.size(), jarsDir);
          return result;
        }
      } else {
        log.debug("[{}] EnvVarLocator: {}={} but {} is not a directory",
            context.getExtensionId(), envVarName, envValue, jarsDir);
      }
    } else {
      log.debug("[{}] EnvVarLocator: {} is not set", context.getExtensionId(), envVarName);
    }

    // Try fallback paths
    String fallbackPaths = context.getProperty("fallbackPaths");
    if (fallbackPaths != null && !fallbackPaths.trim().isEmpty()) {
      for (String pathStr : fallbackPaths.split(",")) {
        Path dir = Paths.get(pathStr.trim());
        if (Files.isDirectory(dir)) {
          log.info("[{}] EnvVarLocator: using fallback path {}",
              context.getExtensionId(), dir);
          List<Path> result = new ArrayList<>();
          PathScanLocator.scanDirectory(dir, includePatterns, result, context.getExtensionId());
          if (!result.isEmpty()) {
            log.info("[{}] EnvVarLocator: resolved {} JARs from fallback {}",
                context.getExtensionId(), result.size(), dir);
            return result;
          }
        }
      }
    }

    log.warn("[{}] EnvVarLocator: no JARs found (envVar={}, fallbacks={})",
        context.getExtensionId(), envVarName, fallbackPaths);
    return Collections.emptyList();
  }
}
