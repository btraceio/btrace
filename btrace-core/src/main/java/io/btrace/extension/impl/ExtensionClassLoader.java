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
package io.btrace.extension.impl;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * ClassLoader for BTrace extensions. Each extension is loaded in its own classloader for isolation.
 * The parent classloader should be the BTrace boot classloader that contains core BTrace API
 * classes.
 *
 * <p>Delegation model:
 *
 * <pre>
 * Bootstrap ClassLoader (JRE classes)
 *     ↓
 * BTrace Boot ClassLoader (btrace-boot.jar - core API)
 *     ↓
 * Extension ClassLoader (extension JAR - extension code + shaded deps)
 * </pre>
 *
 * <p>Extensions can see: - JRE classes (bootstrap) - BTrace core API classes (parent) - Their own
 * classes and shaded dependencies (this classloader)
 *
 * <p>Extensions cannot see: - Other extension's classes - Agent implementation classes
 */
public final class ExtensionClassLoader extends URLClassLoader {
  private final String extensionId;
  private final String extensionVersion;

  /**
   * Create an extension classloader.
   *
   * @param extensionId extension identifier
   * @param extensionVersion extension version
   * @param urls URLs to load extension classes from (typically single JAR)
   * @param parent parent classloader (typically BTrace boot classloader)
   */
  public ExtensionClassLoader(
      String extensionId, String extensionVersion, URL[] urls, ClassLoader parent) {
    super(urls, parent);
    this.extensionId = extensionId;
    this.extensionVersion = extensionVersion;
  }

  /**
   * Get the extension identifier.
   *
   * @return extension ID
   */
  public String getExtensionId() {
    return extensionId;
  }

  /**
   * Get the extension version.
   *
   * @return extension version
   */
  public String getExtensionVersion() {
    return extensionVersion;
  }

  @Override
  public String toString() {
    return "ExtensionClassLoader{"
        + "extension='"
        + extensionId
        + "' version='"
        + extensionVersion
        + "'}";
  }
}
