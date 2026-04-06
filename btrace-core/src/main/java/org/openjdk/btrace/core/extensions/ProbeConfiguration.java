package org.openjdk.btrace.core.extensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes which bundled probes to activate and how to route their output.
 *
 * <p>Returned by {@link ExtensionConfigurator#configure} to declaratively specify probe activation.
 * Instances are created via the {@link #builder()} method.
 *
 * <pre>{@code
 * ProbeConfiguration config = ProbeConfiguration.builder()
 *     .enableProbe("SparkJobTracer")
 *     .enableProbe("SparkStageTracer")
 *     .output(Output.JFR)
 *     .build();
 * }</pre>
 */
public final class ProbeConfiguration {

  /** Output destination for probe data. */
  public enum Output {
    /** Write to JDK Flight Recorder events. */
    JFR,
    /** Write to a file specified by {@link ProbeConfiguration#getOutputPath()}. */
    FILE,
    /** Write to standard output. */
    STDOUT
  }

  private final List<String> enabledProbes;
  private final Output output;
  private final String outputPath;
  private final Map<String, Map<String, String>> probeParams;

  private ProbeConfiguration(Builder builder) {
    this.enabledProbes = Collections.unmodifiableList(new ArrayList<>(builder.enabledProbes));
    this.output = builder.output;
    this.outputPath = builder.outputPath;
    Map<String, Map<String, String>> params = new HashMap<>();
    for (Map.Entry<String, Map<String, String>> entry : builder.probeParams.entrySet()) {
      params.put(entry.getKey(), Collections.unmodifiableMap(new HashMap<>(entry.getValue())));
    }
    this.probeParams = Collections.unmodifiableMap(params);
  }

  /** Probe names to activate (simple names or fully qualified class names). */
  public List<String> getEnabledProbes() {
    return enabledProbes;
  }

  /** Output destination. Defaults to {@link Output#STDOUT}. */
  public Output getOutput() {
    return output;
  }

  /** File path when {@link #getOutput()} is {@link Output#FILE}. May be {@code null}. */
  public String getOutputPath() {
    return outputPath;
  }

  /** Per-probe configuration parameters. Outer key is probe name, inner map is param key-value. */
  public Map<String, Map<String, String>> getProbeParams() {
    return probeParams;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final List<String> enabledProbes = new ArrayList<>();
    private Output output = Output.STDOUT;
    private String outputPath;
    private final Map<String, Map<String, String>> probeParams = new HashMap<>();

    private Builder() {}

    public Builder enableProbe(String probeName) {
      enabledProbes.add(probeName);
      return this;
    }

    public Builder enableProbes(List<String> probeNames) {
      enabledProbes.addAll(probeNames);
      return this;
    }

    public Builder output(Output output) {
      this.output = output;
      return this;
    }

    public Builder outputPath(String path) {
      this.outputPath = path;
      return this;
    }

    public Builder probeParam(String probeName, String key, String value) {
      probeParams.computeIfAbsent(probeName, k -> new HashMap<>()).put(key, value);
      return this;
    }

    public ProbeConfiguration build() {
      return new ProbeConfiguration(this);
    }
  }
}
