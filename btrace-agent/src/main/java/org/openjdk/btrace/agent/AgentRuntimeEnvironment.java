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
package io.btrace.agent;

import io.btrace.core.extensions.RuntimeEnvironment;

/**
 * Agent-side implementation of {@link RuntimeEnvironment} passed to {@link
 * io.btrace.core.extensions.ExtensionConfigurator} instances during probe auto-selection.
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
