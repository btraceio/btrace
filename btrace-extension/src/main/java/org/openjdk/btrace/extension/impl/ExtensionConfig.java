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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for BTrace extension loading and behavior. Supports whitelisting, blacklisting, and
 * extension-specific settings.
 */
public final class ExtensionConfig {
  private static final Logger log = LoggerFactory.getLogger(ExtensionConfig.class);

  private static final String PROP_ENABLED = "extensions.enabled";
  private static final String PROP_DISABLED = "extensions.disabled";
  private static final String PROP_AUTOLOAD = "extensions.autoload";

  private final Set<String> enabledExtensions;
  private final Set<String> disabledExtensions;
  private final boolean autoload;
  private final Properties allProperties;

  private ExtensionConfig(
      Set<String> enabledExtensions,
      Set<String> disabledExtensions,
      boolean autoload,
      Properties allProperties) {
    this.enabledExtensions = enabledExtensions;
    this.disabledExtensions = disabledExtensions;
    this.autoload = autoload;
    this.allProperties = allProperties;
  }

  /**
   * Load configuration from default locations. Priority: command-line {@literal >} user config
   * {@literal >} system config {@literal >} defaults
   *
   * @param btraceHome BTRACE_HOME directory (for system config)
   * @return loaded configuration
   */
  public static ExtensionConfig load(String btraceHome) {
    // 1. Check command-line override
    String configPath = System.getProperty("btrace.extensions.config");
    if (configPath != null) {
      File configFile = new File(configPath);
      if (configFile.exists()) {
        log.info("Loading extension configuration from: {}", configPath);
        return loadFrom(configFile.toPath());
      } else {
        log.warn("Extension configuration file not found: {}", configPath);
      }
    }

    // 2. Check user config
    String userHome = System.getProperty("user.home");
    if (userHome != null) {
      Path userConfig = Paths.get(userHome, ".btrace", "extensions.conf");
      if (Files.exists(userConfig)) {
        log.info("Loading extension configuration from: {}", userConfig);
        return loadFrom(userConfig);
      }
    }

    // 3. Check system config
    if (btraceHome != null) {
      Path systemConfig = Paths.get(btraceHome, "conf", "extensions.conf");
      if (Files.exists(systemConfig)) {
        log.info("Loading extension configuration from: {}", systemConfig);
        return loadFrom(systemConfig);
      }
    }

    // 4. Use defaults
    log.debug("No extension configuration found, using defaults");
    return createDefault();
  }

  /**
   * Load configuration from a specific file.
   *
   * @param configPath path to configuration file
   * @return loaded configuration
   */
  public static ExtensionConfig loadFrom(Path configPath) {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream(configPath.toFile())) {
      props.load(in);
    } catch (IOException e) {
      log.error("Failed to load extension configuration from {}: {}", configPath, e.getMessage());
      return createDefault();
    }

    return parse(props);
  }

  /** Create default configuration (all enabled, autoload). */
  public static ExtensionConfig createDefault() {
    return new ExtensionConfig(
        Collections.emptySet(), Collections.emptySet(), true, new Properties());
  }

  /** Parse configuration from properties. */
  private static ExtensionConfig parse(Properties props) {
    // Parse enabled list
    Set<String> enabled = parseExtensionList(props.getProperty(PROP_ENABLED, ""));

    // Parse disabled list
    Set<String> disabled = parseExtensionList(props.getProperty(PROP_DISABLED, ""));

    // Parse autoload
    boolean autoload = Boolean.parseBoolean(props.getProperty(PROP_AUTOLOAD, "true"));

    return new ExtensionConfig(enabled, disabled, autoload, props);
  }

  private static Set<String> parseExtensionList(String value) {
    if (value == null || value.trim().isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> extensions = new HashSet<>();
    for (String ext : value.split("[,\\s]+")) {
      String trimmed = ext.trim();
      if (!trimmed.isEmpty()) {
        extensions.add(trimmed);
      }
    }
    return extensions;
  }

  /**
   * Check if an extension is enabled by configuration.
   *
   * @param extensionId extension identifier
   * @return true if extension should be loaded
   */
  public boolean isEnabled(String extensionId) {
    // Disabled list takes precedence
    if (disabledExtensions.contains(extensionId)) {
      return false;
    }

    // If enabled list is empty, all extensions are enabled
    // If enabled list is not empty, only listed extensions are enabled
    return enabledExtensions.isEmpty() || enabledExtensions.contains(extensionId);
  }

  /**
   * Get autoload setting.
   *
   * @return true if extensions should be loaded on demand, false to load all at startup
   */
  public boolean isAutoLoad() {
    return autoload;
  }

  /**
   * Get extension-specific properties. Returns properties with keys prefixed by {@literal
   * <extension-id>}.
   *
   * @param extensionId extension identifier
   * @return properties for this extension (without prefix)
   */
  public Properties getExtensionProperties(String extensionId) {
    Properties result = new Properties();
    String prefix = extensionId + ".";

    for (String key : allProperties.stringPropertyNames()) {
      if (key.startsWith(prefix)) {
        String unprefixedKey = key.substring(prefix.length());
        result.setProperty(unprefixedKey, allProperties.getProperty(key));
      }
    }

    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("ExtensionConfig{");
    sb.append("autoload=").append(autoload);
    if (!enabledExtensions.isEmpty()) {
      sb.append(", enabled=").append(enabledExtensions);
    }
    if (!disabledExtensions.isEmpty()) {
      sb.append(", disabled=").append(disabledExtensions);
    }
    sb.append("}");
    return sb.toString();
  }
}
