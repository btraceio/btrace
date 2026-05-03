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
package org.openjdk.btrace.core.extensions;

import java.util.Map;

/**
 * Integration point for extensions to perform environment-aware probe selection and configuration.
 *
 * <p>Extensions can provide a configurator class that BTrace calls during agent initialization to
 * determine which bundled probes to enable based on runtime environment detection.
 *
 * <p>When an extension declares a configurator, BTrace instantiates it during agent initialization
 * and invokes {@link #configure(RuntimeEnvironment, Map)} to determine probe selection and related
 * configuration from the detected runtime environment and agent arguments.
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
