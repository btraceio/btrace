/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.btrace.extension.impl;

import org.openjdk.btrace.extension.ExtensionDescriptorDTO;
import org.openjdk.btrace.extension.ExtensionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Extension repository that scans a local file system directory for extension JARs.
 */
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
              log.debug("Discovered extension: {} version {} from {}",
                  descriptor.getId(), descriptor.getVersion(), extDir.getFileName());
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

  /**
   * Find the API JAR in an extension directory.
   */
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
    return "FileSystemExtensionRepository{" + "directory=" + directory + ", priority=" + priority + '}';
  }
}
