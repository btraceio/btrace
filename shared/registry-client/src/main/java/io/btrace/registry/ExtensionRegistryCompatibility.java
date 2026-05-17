package io.btrace.registry;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ExtensionRegistryCompatibility {
  @JsonProperty("min_btrace_version")
  private String minBtraceVersion;

  public ExtensionRegistryCompatibility() {}

  public String getMinBtraceVersion() {
    return minBtraceVersion;
  }

  public void setMinBtraceVersion(String minBtraceVersion) {
    this.minBtraceVersion = minBtraceVersion;
  }
}
