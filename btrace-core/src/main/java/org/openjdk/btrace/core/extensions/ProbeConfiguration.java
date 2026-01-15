package org.openjdk.btrace.core.extensions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for bundled probes returned by {@link ExtensionConfigurator}.
 *
 * <p>Specifies which probes to enable, output destination, and probe-specific parameters.
 */
public final class ProbeConfiguration {

  /** Output destination for probe data. */
  public enum Output {
    /** Write to JFR (Java Flight Recorder) events. */
    JFR,
    /** Write to file. */
    FILE,
    /** Write to stdout. */
    STDOUT
  }

  private final List<String> enabledProbes = new ArrayList<>();
  private Output output = Output.JFR;
  private String outputPath;
  private final Map<String, Map<String, String>> probeParams = new HashMap<>();

  /** Creates an empty configuration with no probes enabled. */
  public ProbeConfiguration() {}

  /**
   * Enables the specified probes.
   *
   * @param probeNames names of probes to enable
   * @return this configuration for chaining
   */
  public ProbeConfiguration enable(String... probeNames) {
    enabledProbes.addAll(Arrays.asList(probeNames));
    return this;
  }

  /**
   * Sets the output destination.
   *
   * @param output the output destination
   * @return this configuration for chaining
   */
  public ProbeConfiguration setOutput(Output output) {
    this.output = output;
    return this;
  }

  /**
   * Sets the output destination from a string value.
   *
   * @param output "jfr", "file", or "stdout" (case-insensitive)
   * @return this configuration for chaining
   */
  public ProbeConfiguration setOutput(String output) {
    if (output == null || output.isEmpty()) {
      return this;
    }
    switch (output.toLowerCase()) {
      case "jfr":
        this.output = Output.JFR;
        break;
      case "file":
        this.output = Output.FILE;
        break;
      case "stdout":
        this.output = Output.STDOUT;
        break;
      default:
        // Unknown output type, keep default
        break;
    }
    return this;
  }

  /**
   * Sets the output file path (for FILE output).
   *
   * @param path the file path
   * @return this configuration for chaining
   */
  public ProbeConfiguration setOutputPath(String path) {
    this.outputPath = path;
    return this;
  }

  /**
   * Sets a parameter for a specific probe.
   *
   * @param probeName the probe name
   * @param paramName the parameter name
   * @param value the parameter value
   * @return this configuration for chaining
   */
  public ProbeConfiguration setProbeParam(String probeName, String paramName, String value) {
    probeParams.computeIfAbsent(probeName, k -> new HashMap<>()).put(paramName, value);
    return this;
  }

  /**
   * Returns the list of enabled probe names.
   *
   * @return unmodifiable list of enabled probes
   */
  public List<String> getEnabledProbes() {
    return Collections.unmodifiableList(enabledProbes);
  }

  /**
   * Returns the output destination.
   *
   * @return the output destination
   */
  public Output getOutput() {
    return output;
  }

  /**
   * Returns the output file path (may be null).
   *
   * @return the file path or null
   */
  public String getOutputPath() {
    return outputPath;
  }

  /**
   * Returns parameters for a specific probe.
   *
   * @param probeName the probe name
   * @return unmodifiable map of parameters, or empty map if none
   */
  public Map<String, String> getProbeParams(String probeName) {
    Map<String, String> params = probeParams.get(probeName);
    return params != null ? Collections.unmodifiableMap(params) : Collections.emptyMap();
  }

  /**
   * Returns whether any probes are enabled.
   *
   * @return true if at least one probe is enabled
   */
  public boolean hasEnabledProbes() {
    return !enabledProbes.isEmpty();
  }
}
