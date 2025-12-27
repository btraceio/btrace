/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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

package org.openjdk.btrace.core.comm;

/**
 * Configuration for BTrace wire protocol.
 *
 * <p>This class provides centralized configuration for protocol version selection and negotiation
 * behavior. Configuration can be set via system properties or programmatically.
 *
 * <p>System properties:
 *
 * <ul>
 *   <li><b>btrace.comm.protocol</b> - Sets the protocol version (1 or 2, or "v1"/"v2"). Default:
 *       v2
 *   <li><b>btrace.comm.autoNegotiate</b> - Enable automatic protocol negotiation. Default: true
 *   <li><b>btrace.comm.forceVersion</b> - Force a specific version without negotiation. Default:
 *       false
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>
 * // Use default configuration (V2 with auto-negotiation)
 * ProtocolConfig config = ProtocolConfig.getDefault();
 *
 * // Force V1 protocol
 * ProtocolConfig config = ProtocolConfig.builder()
 *     .version(ProtocolVersion.V1)
 *     .autoNegotiate(false)
 *     .build();
 *
 * // Use system properties
 * System.setProperty("btrace.comm.protocol", "v1");
 * ProtocolConfig config = ProtocolConfig.fromSystemProperties();
 * </pre>
 */
public class ProtocolConfig {

  private static final String PROP_PROTOCOL = "btrace.comm.protocol";
  private static final String PROP_AUTO_NEGOTIATE = "btrace.comm.autoNegotiate";
  private static final String PROP_FORCE_VERSION = "btrace.comm.forceVersion";

  private final ProtocolVersion version;
  private final boolean autoNegotiate;
  private final boolean forceVersion;

  private ProtocolConfig(ProtocolVersion version, boolean autoNegotiate, boolean forceVersion) {
    this.version = version;
    this.autoNegotiate = autoNegotiate;
    this.forceVersion = forceVersion;

    if (forceVersion && autoNegotiate) {
      throw new IllegalArgumentException(
          "Cannot force version and enable auto-negotiation at the same time");
    }
  }

  /**
   * Returns the configured protocol version.
   *
   * @return the protocol version to use
   */
  public ProtocolVersion getVersion() {
    return version;
  }

  /**
   * Returns whether automatic protocol negotiation is enabled.
   *
   * @return true if auto-negotiation is enabled, false otherwise
   */
  public boolean isAutoNegotiate() {
    return autoNegotiate;
  }

  /**
   * Returns whether to force the configured version without negotiation.
   *
   * @return true if version should be forced, false otherwise
   */
  public boolean isForceVersion() {
    return forceVersion;
  }

  /**
   * Returns the default configuration.
   *
   * <p>Default: V2 protocol with auto-negotiation enabled.
   *
   * @return the default ProtocolConfig
   */
  public static ProtocolConfig getDefault() {
    return builder().build();
  }

  /**
   * Creates a configuration from system properties.
   *
   * @return a ProtocolConfig based on system properties, or default if not set
   */
  public static ProtocolConfig fromSystemProperties() {
    Builder builder = builder();

    String protocolProp = System.getProperty(PROP_PROTOCOL);
    if (protocolProp != null) {
      builder.version(parseProtocolVersion(protocolProp));
    }

    String autoNegotiateProp = System.getProperty(PROP_AUTO_NEGOTIATE);
    if (autoNegotiateProp != null) {
      builder.autoNegotiate(Boolean.parseBoolean(autoNegotiateProp));
    }

    String forceVersionProp = System.getProperty(PROP_FORCE_VERSION);
    if (forceVersionProp != null) {
      builder.forceVersion(Boolean.parseBoolean(forceVersionProp));
    }

    return builder.build();
  }

  /**
   * Parses a protocol version string.
   *
   * @param value the version string ("1", "2", "v1", "v2", "V1", "V2")
   * @return the corresponding ProtocolVersion
   * @throws IllegalArgumentException if the version string is invalid
   */
  private static ProtocolVersion parseProtocolVersion(String value) {
    if (value == null || value.trim().isEmpty()) {
      return ProtocolVersion.getDefault();
    }

    String normalized = value.trim().toLowerCase();

    switch (normalized) {
      case "1":
      case "v1":
        return ProtocolVersion.V1;
      case "2":
      case "v2":
        return ProtocolVersion.V2;
      default:
        throw new IllegalArgumentException("Invalid protocol version: " + value);
    }
  }

  /**
   * Creates a new builder for ProtocolConfig.
   *
   * @return a new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  @Override
  public String toString() {
    return "ProtocolConfig{"
        + "version="
        + version
        + ", autoNegotiate="
        + autoNegotiate
        + ", forceVersion="
        + forceVersion
        + '}';
  }

  /** Builder for ProtocolConfig. */
  public static class Builder {
    private ProtocolVersion version = ProtocolVersion.getDefault();
    private boolean autoNegotiate = true;
    private boolean forceVersion = false;

    private Builder() {}

    /**
     * Sets the protocol version.
     *
     * @param version the protocol version to use
     * @return this builder
     */
    public Builder version(ProtocolVersion version) {
      this.version = version;
      return this;
    }

    /**
     * Sets whether to enable automatic protocol negotiation.
     *
     * @param autoNegotiate true to enable auto-negotiation, false otherwise
     * @return this builder
     */
    public Builder autoNegotiate(boolean autoNegotiate) {
      this.autoNegotiate = autoNegotiate;
      return this;
    }

    /**
     * Sets whether to force the configured version without negotiation.
     *
     * @param forceVersion true to force version, false otherwise
     * @return this builder
     */
    public Builder forceVersion(boolean forceVersion) {
      this.forceVersion = forceVersion;
      return this;
    }

    /**
     * Builds the ProtocolConfig.
     *
     * @return a new ProtocolConfig instance
     * @throws IllegalArgumentException if configuration is invalid
     */
    public ProtocolConfig build() {
      return new ProtocolConfig(version, autoNegotiate, forceVersion);
    }
  }
}
