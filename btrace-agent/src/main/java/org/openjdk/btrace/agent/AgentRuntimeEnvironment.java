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
package org.openjdk.btrace.agent;

import org.openjdk.btrace.core.extensions.RuntimeEnvironment;

/**
 * Agent-side implementation of {@link RuntimeEnvironment} passed to {@link
 * org.openjdk.btrace.core.extensions.ExtensionConfigurator} instances during probe auto-selection.
 */
final class AgentRuntimeEnvironment implements RuntimeEnvironment {

  private final ClassLoader appLoader;

  AgentRuntimeEnvironment() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    appLoader = cl != null ? cl : ClassLoader.getSystemClassLoader();
  }

  @Override
  public boolean hasClass(String className) {
    try {
      Class.forName(className, false, appLoader);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @Override
  public String getSystemProperty(String key) {
    return System.getProperty(key);
  }

  @Override
  public String getSystemProperty(String key, String defaultValue) {
    return System.getProperty(key, defaultValue);
  }

  @Override
  public String getEnv(String name) {
    return System.getenv(name);
  }

  @Override
  public ClassLoader getClassLoader() {
    return appLoader;
  }

  @Override
  public String getMainClassName() {
    // sun.java.command = "mainClass [args]" on HotSpot; first token is the main class or jar
    String cmd = System.getProperty("sun.java.command");
    if (cmd != null && !cmd.isEmpty()) {
      int space = cmd.indexOf(' ');
      return space > 0 ? cmd.substring(0, space) : cmd;
    }
    return null;
  }
}
