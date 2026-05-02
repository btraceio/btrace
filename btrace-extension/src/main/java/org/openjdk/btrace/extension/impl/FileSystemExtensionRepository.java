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
package org.openjdk.btrace.extension.impl;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.openjdk.btrace.extension.ExtensionDescriptorDTO;
import org.openjdk.btrace.extension.ExtensionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Extension repository that scans a local file system directory for extension JARs. */
public final class FileSystemExtensionRepository implements ExtensionRepository {
  private static final Logger log = LoggerFactory.getLogger(FileSystemExtensionRepository.class);

  private final Path directory;
  private final int priority;

  /**
   * Create a file system extension repository.
   *
   * @param directory directory to scan for extension JARs
   * @param priority repository priority
   */
  public FileSystemExtensionRepository(Path directory, int priority) {
    this.directory = directory;
    this.priority = priority;
  }

  @Override
  public List<ExtensionDescriptorDTO> scan() {
    List<ExtensionDescriptorDTO> extensions = new ArrayList<>();

    if (!Files.exists(directory)) {
      log.debug("Extension directory does not exist: {}", directory);
      return extensions;
    }

    if (!Files.isDirectory(directory)) {
      log.warn("Extension path is not a directory: {}", directory);
      return extensions;
    }

    log.debug("Scanning extension directory: {}", directory);

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, Files::isDirectory)) {
      for (Path extDir : stream) {
        try {
          // Look for API JAR in the extension directory
          Path apiJar = findApiJar(extDir);
          if (apiJar != null) {
            // Parse metadata from API JAR but use extension directory as the base path
            ExtensionDescriptorDTO descriptor = ExtensionMetadata.parse(apiJar, extDir, this);
            if (descriptor != null) {
              extensions.add(descriptor);
              log.debug(
                  "Discovered extension: {} version {} from {}",
                  descriptor.getId(),
                  descriptor.getVersion(),
                  extDir.getFileName());
            }
          } else {
            log.debug("Skipping directory without API JAR: {}", extDir);
          }
        } catch (Exception e) {
          log.warn("Failed to parse extension from {}: {}", extDir, e.getMessage());
        }
      }
    } catch (IOException e) {
      log.error("Failed to scan extension directory {}: {}", directory, e.getMessage());
    }

    log.info("Discovered {} extension(s) in {}", extensions.size(), directory);
    return extensions;
  }

  /** Find the API JAR in an extension directory. */
  private Path findApiJar(Path extensionDir) throws IOException {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(extensionDir, "*-api.jar")) {
      for (Path apiJar : stream) {
        return apiJar;
      }
    }
    return null;
  }

  @Override
  public String getLocation() {
    return directory.toString();
  }

  @Override
  public int getPriority() {
    return priority;
  }

  @Override
  public String toString() {
    return "FileSystemExtensionRepository{"
        + "directory="
        + directory
        + ", priority="
        + priority
        + '}';
  }
}
