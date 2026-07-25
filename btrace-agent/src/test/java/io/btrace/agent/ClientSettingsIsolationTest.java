/*
 * Copyright (c) 2008, 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.core.ArgsMap;
import io.btrace.core.SharedSettings;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every client must trace against its own {@link SharedSettings} copy.
 *
 * <p>A client's {@code SET_PARAMS} command is applied by {@code RemoteClient} as {@code
 * ctx.getSettings().from(params)} - an in-place mutation. If contexts share the agent-wide
 * instance, one client's parameters silently become every client's parameters. {@code trusted} is
 * the dangerous case: {@link SharedSettings#from(Map)} ORs it in and never clears it, so a single
 * client asking for {@code trusted} would permanently elevate the whole agent.
 *
 * <p>This isolation has been implemented and then accidentally reverted once already, with nothing
 * failing. These tests exist so the next revert breaks the build.
 */
class ClientSettingsIsolationTest {

  @Test
  @DisplayName("per-client settings are a copy, not the agent-wide instance")
  void clientSettingsAreDistinctFromBaseline() {
    SharedSettings baseline = new SharedSettings();
    SharedSettings clientSettings = Main.newClientSettings(baseline);

    assertNotSame(baseline, clientSettings, "client settings must not alias the baseline");
    assertNotSame(
        SharedSettings.GLOBAL,
        clientSettings,
        "client settings must not alias the global instance");
  }

  @Test
  @DisplayName("per-client settings inherit the agent baseline")
  void clientSettingsInheritBaseline() {
    SharedSettings baseline = new SharedSettings();
    baseline.setDebug(true);
    baseline.setOutputFile("agent-baseline.btrace");

    SharedSettings clientSettings = Main.newClientSettings(baseline);

    assertTrue(clientSettings.isDebug(), "baseline debug flag should be inherited");
    assertEquals("agent-baseline.btrace", clientSettings.getOutputFile());
  }

  @Test
  @DisplayName("one client's SET_PARAMS does not leak into the agent baseline")
  void setParamsDoesNotLeakToBaseline() {
    SharedSettings baseline = new SharedSettings();
    SharedSettings clientSettings = Main.newClientSettings(baseline);

    // Mirrors RemoteClient's handling of Command.SET_PARAMS.
    clientSettings.from(setParams());

    assertTrue(clientSettings.isDebug(), "the requesting client should see its own parameters");
    assertFalse(baseline.isDebug(), "debug leaked into the agent baseline");
    assertFalse(baseline.isTrusted(), "trusted leaked into the agent baseline");
    assertEquals(null, baseline.getDumpDir(), "dumpDir leaked into the agent baseline");
  }

  @Test
  @DisplayName("one client's SET_PARAMS does not leak into another client")
  void setParamsDoesNotLeakBetweenClients() {
    SharedSettings baseline = new SharedSettings();
    SharedSettings first = Main.newClientSettings(baseline);
    SharedSettings second = Main.newClientSettings(baseline);

    first.from(setParams());

    assertTrue(first.isTrusted(), "the requesting client should see its own parameters");
    assertFalse(second.isTrusted(), "trusted leaked into a concurrently connected client");
    assertFalse(second.isDebug(), "debug leaked into a concurrently connected client");
  }

  @Test
  @DisplayName("trusted cannot be downgraded, so a leak would be permanent")
  void trustedIsNonDowngrading() {
    SharedSettings settings = new SharedSettings();
    settings.from(setParams());
    assertTrue(settings.isTrusted());

    Map<String, Object> untrust = new HashMap<>();
    untrust.put(SharedSettings.TRUSTED_KEY, Boolean.FALSE);
    settings.from(untrust);

    assertTrue(
        settings.isTrusted(),
        "trusted is deliberately non-downgrading - which is exactly why sharing the instance"
            + " between clients is unsafe");
  }

  @Test
  @DisplayName("a ClientContext cannot be built on the agent-wide settings")
  void clientContextRejectsGlobalSettings() {
    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ClientContext(null, null, new ArgsMap(), SharedSettings.GLOBAL));

    assertTrue(
        failure.getMessage().contains("newClientSettings"),
        "the failure should point at the supported way to seed per-client settings");
  }

  @Test
  @DisplayName("a ClientContext accepts an isolated copy")
  void clientContextAcceptsIsolatedSettings() {
    ClientContext ctx =
        new ClientContext(null, null, new ArgsMap(), Main.newClientSettings(SharedSettings.GLOBAL));

    assertNotSame(SharedSettings.GLOBAL, ctx.getSettings());
  }

  @Test
  @DisplayName("the remote accept path builds contexts on isolated settings")
  void remoteContextDoesNotShareGlobalSettings() {
    ClientContext ctx = Main.newRemoteClientContext();

    assertNotSame(
        SharedSettings.GLOBAL,
        ctx.getSettings(),
        "the remote accept path handed out the agent-wide settings instance");
  }

  @Test
  @DisplayName("two remote connections do not share settings with each other")
  void remoteContextsAreIndependent() {
    ClientContext first = Main.newRemoteClientContext();
    ClientContext second = Main.newRemoteClientContext();

    assertNotSame(first.getSettings(), second.getSettings());

    first.getSettings().from(setParams());

    assertFalse(second.getSettings().isTrusted(), "trusted leaked between remote connections");
    assertFalse(SharedSettings.GLOBAL.isTrusted(), "trusted leaked into the agent-wide settings");
  }

  private static Map<String, Object> setParams() {
    Map<String, Object> params = new HashMap<>();
    params.put(SharedSettings.DEBUG_KEY, Boolean.TRUE);
    params.put(SharedSettings.TRUSTED_KEY, Boolean.TRUE);
    params.put(SharedSettings.DUMP_DIR_KEY, "/tmp/leaked-dump-dir");
    return params;
  }
}
