package org.openjdk.btrace.core.extensions;

import java.nio.file.Path;
import java.util.List;

/**
 * Resolves "provided" dependencies for a BTrace extension at agent load time.
 *
 * <p>Extensions that depend on large platform libraries (e.g., Spark, Hadoop, Databricks) should
 * not bundle those JARs. Instead, they declare a locator that finds the JARs at well-known paths in
 * the target environment.
 *
 * <p>Built-in locators can be referenced by short name using bracket syntax in extension metadata:
 *
 * <ul>
 *   <li>{@code [EnvVarLocator]} — resolves from an environment variable (e.g., {@code $SPARK_HOME/jars/})
 *   <li>{@code [PathScanLocator]} — scans configured directory paths for JAR files
 *   <li>{@code [CompositeLocator]} — tries multiple strategies in order
 * </ul>
 *
 * <p>Custom locators use their fully qualified class name.
 *
 * <p>Resolved JARs are injected as an intermediate classloader between the agent's
 * MaskedClassLoader and the extension's implementation classloader, providing per-extension
 * isolation.
 *
 * @see LocatorContext
 */
public interface ProvidedDependencyLocator {

  /**
   * Locate provided dependency JARs for the requesting extension.
   *
   * <p>Called once per extension at load time. The returned paths must point to existing JAR files.
   * Returning an empty list is valid — the extension will load without provided deps (which may
   * cause {@link ClassNotFoundException} later if the deps were needed).
   *
   * @param context provides extension ID, locator properties, and environment access
   * @return list of JAR file paths to add to the extension's classloader chain; never {@code null}
   */
  List<Path> locate(LocatorContext context);
}
