package io.btrace.registry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ExtensionRegistryClient {
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final RegistrySource source;
  private final Path cacheFile;

  public ExtensionRegistryClient(RegistrySource source, Path cacheFile) {
    this.source = Objects.requireNonNull(source, "source");
    this.cacheFile = Objects.requireNonNull(cacheFile, "cacheFile");
  }

  public List<ExtensionRegistryEntry> list() {
    return load().getExtensions();
  }

  public ExtensionRegistryEntry findById(String id) {
    for (ExtensionRegistryEntry entry : list()) {
      if (entry.getId().equals(id)) {
        return entry;
      }
    }
    throw new IllegalArgumentException("Unknown extension id: " + id);
  }

  private ExtensionRegistryDocument load() {
    try {
      ExtensionRegistryDocument document = fetch();
      writeCache(document);
      return validate(document);
    } catch (IOException e) {
      if (Files.isRegularFile(cacheFile)) {
        try {
          return validate(OBJECT_MAPPER.readValue(cacheFile.toFile(), ExtensionRegistryDocument.class));
        } catch (IOException cacheError) {
          throw new IllegalStateException("Failed to load registry from source or cache", cacheError);
        }
      }
      throw new IllegalStateException("Failed to load registry from " + source.uri(), e);
    }
  }

  private ExtensionRegistryDocument fetch() throws IOException {
    URI uri = source.uri();
    if ("file".equals(uri.getScheme())) {
      return OBJECT_MAPPER.readValue(Path.of(uri).toFile(), ExtensionRegistryDocument.class);
    }

    HttpURLConnection connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
    connection.setConnectTimeout(10_000);
    connection.setReadTimeout(20_000);
    connection.setInstanceFollowRedirects(true);
    int status = connection.getResponseCode();
    if (status != 200) {
      throw new IOException("HTTP " + status + " for " + uri);
    }
    try (InputStream input = connection.getInputStream()) {
      return OBJECT_MAPPER.readValue(input, ExtensionRegistryDocument.class);
    }
  }

  private void writeCache(ExtensionRegistryDocument document) throws IOException {
    if (cacheFile.getParent() != null) {
      Files.createDirectories(cacheFile.getParent());
    }
    Path tmp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), document);
    Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  private ExtensionRegistryDocument validate(ExtensionRegistryDocument document) {
    if (document.getSchemaVersion() != 1) {
      throw new IllegalStateException(
          "Unsupported registry schema_version: " + document.getSchemaVersion());
    }
    if (document.getExtensions() == null) {
      throw new IllegalStateException("Registry extensions list is missing");
    }

    Set<String> ids = new HashSet<>();
    Set<String> coordinates = new HashSet<>();
    List<ExtensionRegistryEntry> validated = new ArrayList<>(document.getExtensions().size());
    for (ExtensionRegistryEntry entry : document.getExtensions()) {
      validateEntry(entry, ids, coordinates);
      validated.add(entry);
    }
    return new ExtensionRegistryDocument(document.getSchemaVersion(), List.copyOf(validated));
  }

  private void validateEntry(
      ExtensionRegistryEntry entry, Set<String> ids, Set<String> coordinates) {
    requireText(entry.getId(), "Extension id");
    requireText(entry.getName(), "Extension name");
    requireText(entry.getDescription(), "Extension description");
    requireText(entry.getOwner(), "Extension owner");
    requireText(entry.getSourceRepo(), "Extension source repo");
    if (!entry.getSourceRepo().startsWith("https://")) {
      throw new IllegalStateException(
          "Extension source repo must be https: " + entry.getSourceRepo());
    }

    if (!ids.add(entry.getId())) {
      throw new IllegalStateException("Duplicate extension id: " + entry.getId());
    }

    MavenCoordinates maven = Objects.requireNonNull(entry.getMaven(), "Extension maven coordinates");
    requireText(maven.getGroupId(), "Extension groupId");
    requireText(maven.getArtifactId(), "Extension artifactId");
    requireText(maven.getVersion(), "Extension version");
    String coordinate = maven.getGroupId() + ":" + maven.getArtifactId();
    if (!coordinates.add(coordinate)) {
      throw new IllegalStateException("Duplicate Maven coordinate: " + coordinate);
    }

    if (entry.getTags() != null) {
      for (String tag : entry.getTags()) {
        requireText(tag, "Extension tag");
      }
    }
  }

  private void requireText(String value, String label) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalStateException(label + " must be present");
    }
  }
}
