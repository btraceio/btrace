package io.btrace.registry;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class ExtensionRegistryDocument {
  @JsonProperty("schema_version")
  private int schemaVersion;

  private List<ExtensionRegistryEntry> extensions;

  public ExtensionRegistryDocument() {}

  public ExtensionRegistryDocument(int schemaVersion, List<ExtensionRegistryEntry> extensions) {
    this.schemaVersion = schemaVersion;
    this.extensions = extensions;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public List<ExtensionRegistryEntry> getExtensions() {
    return extensions;
  }

  public void setExtensions(List<ExtensionRegistryEntry> extensions) {
    this.extensions = extensions;
  }
}
