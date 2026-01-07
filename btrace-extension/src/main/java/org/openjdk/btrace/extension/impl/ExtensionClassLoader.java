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

import java.net.URL;
import java.net.URLClassLoader;

/**
 * ClassLoader for BTrace extensions.
 * Each extension is loaded in its own classloader for isolation.
 * The parent classloader should be the BTrace boot classloader that
 * contains core BTrace API classes.
 *
 * <p>Delegation model:
 * <pre>
 * Bootstrap ClassLoader (JRE classes)
 *     ↓
 * BTrace Boot ClassLoader (btrace-boot.jar - core API)
 *     ↓
 * Extension ClassLoader (extension JAR - extension code + shaded deps)
 * </pre>
 *
 * <p>Extensions can see:
 * - JRE classes (bootstrap)
 * - BTrace core API classes (parent)
 * - Their own classes and shaded dependencies (this classloader)
 *
 * <p>Extensions cannot see:
 * - Other extension's classes
 * - Agent implementation classes
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
