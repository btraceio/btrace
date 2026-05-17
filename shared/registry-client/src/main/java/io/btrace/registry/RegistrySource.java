package io.btrace.registry;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

public final class RegistrySource {
  private final URI uri;

  private RegistrySource(URI uri) {
    this.uri = Objects.requireNonNull(uri, "uri");
  }

  public static RegistrySource local(Path path) {
    return new RegistrySource(path.toUri());
  }

  public static RegistrySource uri(String uri) {
    return new RegistrySource(URI.create(uri));
  }

  public URI uri() {
    return uri;
  }
}
