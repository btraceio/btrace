package io.btrace.registry;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class ExtensionRegistryEntry {
  private String id;
  private String name;
  private String description;
  private String owner;

  @JsonProperty("source_repo")
  private String sourceRepo;

  private MavenCoordinates maven;
  private ExtensionRegistryCompatibility compatibility;
  private List<String> tags;

  public ExtensionRegistryEntry() {}

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public String getSourceRepo() {
    return sourceRepo;
  }

  public void setSourceRepo(String sourceRepo) {
    this.sourceRepo = sourceRepo;
  }

  public MavenCoordinates getMaven() {
    return maven;
  }

  public void setMaven(MavenCoordinates maven) {
    this.maven = maven;
  }

  public ExtensionRegistryCompatibility getCompatibility() {
    return compatibility;
  }

  public void setCompatibility(ExtensionRegistryCompatibility compatibility) {
    this.compatibility = compatibility;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }
}
