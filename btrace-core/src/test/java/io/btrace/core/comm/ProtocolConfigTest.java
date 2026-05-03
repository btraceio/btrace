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
package io.btrace.core.comm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tests for ProtocolConfig. */
public class ProtocolConfigTest {

  @AfterEach
  public void clearSystemProperties() {
    System.clearProperty("btrace.comm.protocol");
    System.clearProperty("btrace.comm.autoNegotiate");
    System.clearProperty("btrace.comm.forceVersion");
  }

  @Test
  public void testDefaultConfig() {
    ProtocolConfig config = ProtocolConfig.getDefault();

    assertEquals(ProtocolVersion.V2, config.getVersion());
    assertTrue(config.isAutoNegotiate());
    assertFalse(config.isForceVersion());
  }

  @Test
  public void testBuilderDefaultValues() {
    ProtocolConfig config = ProtocolConfig.builder().build();

    assertEquals(ProtocolVersion.V2, config.getVersion());
    assertTrue(config.isAutoNegotiate());
    assertFalse(config.isForceVersion());
  }

  @Test
  public void testBuilderCustomVersion() {
    ProtocolConfig config = ProtocolConfig.builder().version(ProtocolVersion.V1).build();

    assertEquals(ProtocolVersion.V1, config.getVersion());
    assertTrue(config.isAutoNegotiate());
    assertFalse(config.isForceVersion());
  }

  @Test
  public void testBuilderDisableAutoNegotiate() {
    ProtocolConfig config = ProtocolConfig.builder().autoNegotiate(false).build();

    assertEquals(ProtocolVersion.V2, config.getVersion());
    assertFalse(config.isAutoNegotiate());
    assertFalse(config.isForceVersion());
  }

  @Test
  public void testBuilderForceVersion() {
    ProtocolConfig config =
        ProtocolConfig.builder()
            .version(ProtocolVersion.V1)
            .autoNegotiate(false)
            .forceVersion(true)
            .build();

    assertEquals(ProtocolVersion.V1, config.getVersion());
    assertFalse(config.isAutoNegotiate());
    assertTrue(config.isForceVersion());
  }

  @Test
  public void testBuilderInvalidConfiguration() {
    // Cannot have both forceVersion and autoNegotiate enabled
    assertThrows(
        IllegalArgumentException.class,
        () -> ProtocolConfig.builder().autoNegotiate(true).forceVersion(true).build());
  }

  @Test
  public void testFromSystemPropertiesNoProperties() {
    ProtocolConfig config = ProtocolConfig.fromSystemProperties();

    // Should return default config when no properties are set
    assertEquals(ProtocolVersion.V2, config.getVersion());
    assertTrue(config.isAutoNegotiate());
    assertFalse(config.isForceVersion());
  }

  @Test
  public void testFromSystemPropertiesProtocolV1() {
    System.setProperty("btrace.comm.protocol", "v1");
    ProtocolConfig config = ProtocolConfig.fromSystemProperties();

    assertEquals(ProtocolVersion.V1, config.getVersion());
    assertTrue(config.isAutoNegotiate());
    assertFalse(config.isForceVersion());
  }

  @Test
  public void testFromSystemPropertiesProtocolV2() {
    System.setProperty("btrace.comm.protocol", "v2");
    ProtocolConfig config = ProtocolConfig.fromSystemProperties();

    assertEquals(ProtocolVersion.V2, config.getVersion());
    assertTrue(config.isAutoNegotiate());
  }

  @Test
  public void testFromSystemPropertiesProtocolNumeric() {
    System.setProperty("btrace.comm.protocol", "1");
    ProtocolConfig config = ProtocolConfig.fromSystemProperties();
    assertEquals(ProtocolVersion.V1, config.getVersion());

    System.setProperty("btrace.comm.protocol", "2");
    config = ProtocolConfig.fromSystemProperties();
    assertEquals(ProtocolVersion.V2, config.getVersion());
  }

  @Test
  public void testFromSystemPropertiesProtocolUppercase() {
    System.setProperty("btrace.comm.protocol", "V1");
    ProtocolConfig config = ProtocolConfig.fromSystemProperties();
    assertEquals(ProtocolVersion.V1, config.getVersion());

    System.setProperty("btrace.comm.protocol", "V2");
    config = ProtocolConfig.fromSystemProperties();
    assertEquals(ProtocolVersion.V2, config.getVersion());
  }

  @Test
  public void testFromSystemPropertiesInvalidProtocol() {
    System.setProperty("btrace.comm.protocol", "invalid");
    assertThrows(IllegalArgumentException.class, () -> ProtocolConfig.fromSystemProperties());
  }

  @Test
  public void testFromSystemPropertiesAutoNegotiate() {
    System.setProperty("btrace.comm.autoNegotiate", "false");
    ProtocolConfig config = ProtocolConfig.fromSystemProperties();

    assertFalse(config.isAutoNegotiate());
  }

  @Test
  public void testFromSystemPropertiesForceVersion() {
    System.setProperty("btrace.comm.protocol", "v1");
    System.setProperty("btrace.comm.autoNegotiate", "false");
    System.setProperty("btrace.comm.forceVersion", "true");
    ProtocolConfig config = ProtocolConfig.fromSystemProperties();

    assertEquals(ProtocolVersion.V1, config.getVersion());
    assertFalse(config.isAutoNegotiate());
    assertTrue(config.isForceVersion());
  }

  @Test
  public void testFromSystemPropertiesAllProperties() {
    System.setProperty("btrace.comm.protocol", "v2");
    System.setProperty("btrace.comm.autoNegotiate", "true");
    System.setProperty("btrace.comm.forceVersion", "false");
    ProtocolConfig config = ProtocolConfig.fromSystemProperties();

    assertEquals(ProtocolVersion.V2, config.getVersion());
    assertTrue(config.isAutoNegotiate());
    assertFalse(config.isForceVersion());
  }

  @Test
  public void testToString() {
    ProtocolConfig config = ProtocolConfig.getDefault();
    String str = config.toString();

    assertTrue(str.contains("version="));
    assertTrue(str.contains("autoNegotiate="));
    assertTrue(str.contains("forceVersion="));
  }

  @Test
  public void testBuilderChaining() {
    ProtocolConfig config =
        ProtocolConfig.builder()
            .version(ProtocolVersion.V1)
            .autoNegotiate(false)
            .forceVersion(false)
            .build();

    assertEquals(ProtocolVersion.V1, config.getVersion());
    assertFalse(config.isAutoNegotiate());
    assertFalse(config.isForceVersion());
  }

  @Test
  public void testMultipleBuilds() {
    ProtocolConfig.Builder builder = ProtocolConfig.builder();

    ProtocolConfig config1 = builder.version(ProtocolVersion.V1).build();
    ProtocolConfig config2 = builder.version(ProtocolVersion.V2).build();

    // Each build should create independent instances
    assertNotSame(config1, config2);
  }
}
