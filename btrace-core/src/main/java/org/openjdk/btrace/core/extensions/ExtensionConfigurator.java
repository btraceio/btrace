package org.openjdk.btrace.core.extensions;

import java.util.Map;

/**
 * Integration point for extensions to perform environment-aware probe selection and configuration.
 *
 * <p>Extensions can provide a configurator class that BTrace calls during agent initialization to
 * determine which bundled probes to enable based on runtime environment detection.
 *
 * <p><b>Status:</b> This interface is defined and part of the extension API contract, but the
 * agent does not yet invoke configurators during initialization. Implement this interface to
 * prepare for the forthcoming integration; the {@code configure} method will be called
 * automatically once agent support is wired in.
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * public class SparkConfigurator implements ExtensionConfigurator {
 *   &#64;Override
 *   public ProbeConfiguration configure(RuntimeEnvironment env, Map&lt;String, String&gt; args) {
 *     ProbeConfiguration config = new ProbeConfiguration();
 *     if (env.hasClass("org.apache.spark.SparkContext")) {
 *       config.enable("SparkJobTracer", "SparkStageTracer");
 *     } else if (env.hasClass("org.apache.spark.executor.Executor")) {
 *       config.enable("SparkExecutorTracer");
 *     }
 *     config.setOutput(args.getOrDefault("output", "jfr"));
 *     return config;
 *   }
 * }
 * </pre>
 *
 * <p>The configurator class is specified in the extension's {@code extension.properties} file via
 * the {@code configurator} property.
 */
public interface ExtensionConfigurator {

  /**
   * Configures which probes to enable based on runtime environment and agent arguments.
   *
   * @param env runtime environment providing class detection and system property access
   * @param args agent arguments passed via {@code -javaagent:agent.jar=key=value,...}
   * @return configuration specifying which probes to load and how to configure them
   */
  ProbeConfiguration configure(RuntimeEnvironment env, Map<String, String> args);
}
