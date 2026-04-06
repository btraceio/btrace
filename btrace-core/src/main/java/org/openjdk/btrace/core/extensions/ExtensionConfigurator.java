package org.openjdk.btrace.core.extensions;

import java.util.Map;

/**
 * Extension integration point for runtime environment detection and probe selection.
 *
 * <p>Implementations perform environment-aware configuration: they inspect the runtime (e.g.
 * detecting Spark, Hadoop, or Kubernetes) and return a {@link ProbeConfiguration} specifying which
 * bundled probes to activate and how to configure output.
 *
 * <p>A configurator is declared in an extension's {@code extension.properties} via the {@code
 * configurator} key and is instantiated reflectively during agent initialization.
 *
 * @see ProbeConfiguration
 * @see RuntimeEnvironment
 */
public interface ExtensionConfigurator {

  /**
   * Inspect the runtime environment and return a probe configuration.
   *
   * @param env provides environment detection capabilities (class presence, system properties, etc.)
   * @param args agent arguments relevant to this extension (may be empty, never null)
   * @return configuration describing which probes to enable and how to route output, or {@code null}
   *     if no probes should be activated
   */
  ProbeConfiguration configure(RuntimeEnvironment env, Map<String, String> args);
}
