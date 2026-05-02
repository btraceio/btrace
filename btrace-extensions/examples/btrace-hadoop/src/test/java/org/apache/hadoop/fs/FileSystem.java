package org.apache.hadoop.fs;

import java.net.URI;

public final class FileSystem {
  public static boolean getUriCalled;

  private final URI uri;

  public FileSystem(URI uri) {
    this.uri = uri;
  }

  public URI getUri() {
    getUriCalled = true;
    return uri;
  }

  public static void reset() {
    getUriCalled = false;
  }
}
