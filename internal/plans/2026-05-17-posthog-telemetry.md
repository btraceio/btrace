# PostHog Telemetry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fire a single anonymous telemetry event to PostHog on agent startup so we can measure which JVM versions BTrace users actually run on.

**Architecture:** A self-contained `Telemetry` class in `btrace-agent` posts a JSON payload to the PostHog capture HTTP API using only JDK standard library (`HttpURLConnection`). It uses a two-daemon-thread pattern: a worker does the HTTP call, a guard starts it and joins with a 2-second hard wall-clock cap (covering DNS resolution, which `connectTimeout` alone does not bound). The calling thread (`fireAsync`) never blocks — both threads are daemon threads and the JVM will not wait for them. All exceptions are swallowed silently. No PostHog SDK is added — the API is a plain HTTP POST. Opt-out via `-Dbtrace.telemetry=false`.

**Tech Stack:** Java 8 (source compat), `java.net.HttpURLConnection`, PostHog Capture API v1

---

## Prerequisites (manual, before coding)

- [ ] Create a free account at https://app.posthog.com
- [ ] Create a new project (e.g. "BTrace")
- [ ] Copy the **Project API Key** (starts with `phc_`). This is a public key — safe to commit.
- [ ] Note the ingest host: `https://app.posthog.com` (US) or `https://eu.posthog.com` (EU)
- [ ] Paste the key into the `Telemetry.java` constant in Task 1

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `btrace-agent/src/main/java/io/btrace/agent/Telemetry.java` | **Create** | HTTP POST to PostHog; opt-out check; JSON build |
| `btrace-agent/src/test/java/io/btrace/agent/TelemetryTest.java` | **Create** | Unit tests for opt-out and payload structure |
| `btrace-agent/src/main/java/io/btrace/agent/Main.java` | **Modify** | Call `Telemetry.fireAsync()` after `parseArgs()` |

---

## Task 1: Create `Telemetry.java`

**Files:**
- Create: `btrace-agent/src/main/java/io/btrace/agent/Telemetry.java`

- [ ] **Step 1: Create the file**

```java
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

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.UUID;

final class Telemetry {

  // Public project API key — safe to commit (PostHog design intent).
  // Replace with actual key from your PostHog project settings.
  private static final String API_KEY = "phc_REPLACE_WITH_YOUR_KEY";

  private static final String ENDPOINT = "https://app.posthog.com/capture/";
  // Hard wall-clock cap for the guard thread (covers DNS + connect + read).
  // connectTimeout alone does not bound DNS resolution — the guard join does.
  private static final int GUARD_TIMEOUT_MS = 2000;
  private static final int CONNECT_TIMEOUT_MS = 1000;
  private static final int READ_TIMEOUT_MS = 1000;
  private static final String PROP_DISABLED = "btrace.telemetry";

  private Telemetry() {}

  static boolean isEnabled() {
    return !"false".equalsIgnoreCase(System.getProperty(PROP_DISABLED, "true"));
  }

  static String buildPayload(String btraceVersion, String agentMode) {
    String javaVersion = escape(System.getProperty("java.version", "unknown"));
    String osName = escape(System.getProperty("os.name", "unknown"));
    String distinctId = UUID.randomUUID().toString();
    return "{"
        + "\"api_key\":\"" + API_KEY + "\","
        + "\"event\":\"agent_start\","
        + "\"distinct_id\":\"" + distinctId + "\","
        + "\"properties\":{"
        + "\"java_version\":\"" + javaVersion + "\","
        + "\"os_name\":\"" + osName + "\","
        + "\"btrace_version\":\"" + escape(btraceVersion) + "\","
        + "\"agent_mode\":\"" + escape(agentMode) + "\""
        + "}"
        + "}";
  }

  // fireAsync returns immediately — two daemon threads are started and the
  // calling thread (agent startup) is never blocked or joined.
  static void fireAsync(final String btraceVersion, final String agentMode) {
    if (!isEnabled()) {
      return;
    }
    final Thread worker = new Thread(new Runnable() {
      @Override
      public void run() {
        send(btraceVersion, agentMode);
      }
    });
    worker.setDaemon(true);
    worker.setName("btrace-telemetry");

    // Guard caps total wall-clock time including DNS, which connectTimeout
    // does not cover. Both threads are daemon threads — the JVM does not
    // wait for either on shutdown.
    Thread guard = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          worker.start();
          worker.join(GUARD_TIMEOUT_MS);
          worker.interrupt();
        } catch (Throwable ignored) {}
      }
    });
    guard.setDaemon(true);
    guard.setName("btrace-telemetry-guard");
    guard.start();
  }

  private static void send(String btraceVersion, String agentMode) {
    try {
      String payload = buildPayload(btraceVersion, agentMode);
      byte[] body = payload.getBytes(Charset.forName("UTF-8"));
      URL url = new URL(ENDPOINT);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
      conn.setReadTimeout(READ_TIMEOUT_MS);
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      conn.setRequestProperty("Content-Length", String.valueOf(body.length));
      conn.setDoOutput(true);
      try (OutputStream out = conn.getOutputStream()) {
        out.write(body);
      }
      conn.getResponseCode(); // consume response so connection is released
      conn.disconnect();
    } catch (Throwable ignored) {
      // telemetry must never affect the traced application
    }
  }

  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
```

- [ ] **Step 2: Verify it compiles (no new deps needed)**

```bash
./gradlew :btrace-agent:compileJava -x test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add btrace-agent/src/main/java/io/btrace/agent/Telemetry.java
git commit -m "feat(agent): add Telemetry class for PostHog usage pings"
```

---

## Task 2: Write unit tests for `Telemetry.java`

**Files:**
- Create: `btrace-agent/src/test/java/io/btrace/agent/TelemetryTest.java`

- [ ] **Step 1: Create the test file**

```java
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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TelemetryTest {

  @AfterEach
  void clearProp() {
    System.clearProperty("btrace.telemetry");
  }

  @Test
  void enabledByDefault() {
    System.clearProperty("btrace.telemetry");
    assertTrue(Telemetry.isEnabled());
  }

  @Test
  void disabledWhenPropertyIsFalse() {
    System.setProperty("btrace.telemetry", "false");
    assertFalse(Telemetry.isEnabled());
  }

  @Test
  void enabledWhenPropertyIsTrue() {
    System.setProperty("btrace.telemetry", "true");
    assertTrue(Telemetry.isEnabled());
  }

  @Test
  void payloadContainsRequiredFields() {
    String payload = Telemetry.buildPayload("2.2.6", "premain");
    assertTrue(payload.contains("\"event\":\"agent_start\""));
    assertTrue(payload.contains("\"java_version\":\""));
    assertTrue(payload.contains("\"os_name\":\""));
    assertTrue(payload.contains("\"btrace_version\":\"2.2.6\""));
    assertTrue(payload.contains("\"agent_mode\":\"premain\""));
    assertTrue(payload.contains("\"distinct_id\":\""));
  }

  @Test
  void payloadEscapesSpecialCharacters() {
    String payload = Telemetry.buildPayload("2.2.6-\"evil\"", "premain\\x");
    // must not produce unescaped quotes or backslashes inside string values
    // strip the outer structure and check the escaped values appear
    assertTrue(payload.contains("2.2.6-\\\"evil\\\""));
    assertTrue(payload.contains("premain\\\\x"));
  }

  @Test
  void fireAsyncDoesNotThrowWhenDisabled() {
    System.setProperty("btrace.telemetry", "false");
    // should return immediately without spawning a thread or throwing
    assertDoesNotThrow(() -> Telemetry.fireAsync("2.2.6", "premain"));
  }

  @Test
  void fireAsyncReturnsImmediatelyOnNetworkFailure() throws InterruptedException {
    // The guard joins the worker with a 2-second cap, but fireAsync itself must
    // return before the guard finishes. Measure wall-clock time to verify.
    System.setProperty("btrace.telemetry", "true");
    long start = System.currentTimeMillis();
    assertDoesNotThrow(() -> Telemetry.fireAsync("2.2.6", "agentmain"));
    long elapsed = System.currentTimeMillis() - start;
    // fireAsync must return in well under 100ms regardless of network state
    assertTrue(elapsed < 100, "fireAsync blocked for " + elapsed + "ms");
    // Let daemon threads finish so they don't leak into subsequent tests
    Thread.sleep(300);
  }
}
```

- [ ] **Step 2: Run the tests**

```bash
./gradlew :btrace-agent:test --tests "io.btrace.agent.TelemetryTest"
```

Expected: all 6 tests pass

- [ ] **Step 3: Commit**

```bash
git add btrace-agent/src/test/java/io/btrace/agent/TelemetryTest.java
git commit -m "test(agent): add unit tests for Telemetry opt-out and payload"
```

---

## Task 3: Wire `Telemetry.fireAsync()` into `Main.java`

**Files:**
- Modify: `btrace-agent/src/main/java/io/btrace/agent/Main.java` (around line 188 — after `parseArgs()`)

The `readBTraceVersion()` method already exists at line 624. The `agentMode` must be tracked at the point `premain` / `agentmain` is called, before `startAgent()`.

- [ ] **Step 1: Add an `agentMode` field near the top of the class**

In `Main.java`, find the block of `private static` fields (around line 114–145) and add:

```java
  private static volatile String agentMode = "unknown";
```

- [ ] **Step 2: Set `agentMode` in `premain` and `agentmain`**

Replace (lines 147–153):

```java
  public static void premain(String args, Instrumentation inst) {
    startAgent(args, inst);
  }

  public static void agentmain(String args, Instrumentation inst) {
    startAgent(args, inst);
  }
```

With:

```java
  public static void premain(String args, Instrumentation inst) {
    agentMode = "premain";
    startAgent(args, inst);
  }

  public static void agentmain(String args, Instrumentation inst) {
    agentMode = "agentmain";
    startAgent(args, inst);
  }
```

- [ ] **Step 3: Call `Telemetry.fireAsync()` after `parseArgs()`**

In the `main()` method, after line 188 (`parseArgs();`), add:

```java
      Telemetry.fireAsync(readBTraceVersion(), agentMode);
```

So the block looks like:

```java
      parseArgs();
      if (AGENT_DEBUG) System.err.println("[BTrace Agent] Arguments parsed");
      Telemetry.fireAsync(readBTraceVersion(), agentMode);
      // settings are all built-up; set the logging system properties accordingly
      DebugSupport.initLoggers(settings.isDebug(), log);
```

- [ ] **Step 4: Run the full agent test suite**

```bash
./gradlew :btrace-agent:test
```

Expected: `BUILD SUCCESSFUL`, no regressions

- [ ] **Step 5: Apply formatting**

```bash
./gradlew spotlessApply
```

- [ ] **Step 6: Commit**

```bash
git add btrace-agent/src/main/java/io/btrace/agent/Main.java
git commit -m "feat(agent): fire PostHog telemetry ping on startup"
```

---

## Task 4: Document the opt-out

**Files:**
- Modify: `docs/README.md` (or wherever agent arguments are documented — check `docs/` for an agent args page)

- [ ] **Step 1: Find the right doc page**

```bash
grep -rl "agentmain\|agent.*arg\|port.*2020\|-javaagent" docs/ | head -5
```

- [ ] **Step 2: Add one paragraph in the agent configuration section**

Add under the agent arguments reference:

```markdown
### Telemetry

BTrace sends a single anonymous ping to [PostHog](https://posthog.com) each time the agent
starts. The event records the JVM version, OS name, and BTrace version — nothing about
your application or its data. This helps the project track which JVM versions are actively
used so we can make informed decisions about platform support.

To opt out, pass the following system property to the **target JVM** (not to BTrace itself):

```
-Dbtrace.telemetry=false
```
```

- [ ] **Step 3: Commit**

```bash
git add docs/
git commit -m "docs: document telemetry opt-out"
```

---

## Self-Review Checklist

- **Spec coverage**: opt-out ✓, JVM version captured ✓, OS name ✓, BTrace version ✓, agent mode ✓, two-daemon-thread guard pattern (DNS bounded) ✓, `fireAsync` never blocks calling thread ✓, swallows all exceptions silently ✓, no logs ✓, no new runtime deps ✓
- **Placeholder scan**: no TBDs, all code is complete
- **Type consistency**: `Telemetry.fireAsync(String, String)` used in Tasks 1, 2, 3 — consistent
- **Java 8 compat**: no lambdas (uses anonymous `Runnable`), no streams, no `var`, `Charset.forName("UTF-8")` instead of `StandardCharsets.UTF_8` ✓
- **PostHog API key**: placeholder `phc_REPLACE_WITH_YOUR_KEY` — must be replaced in Task 1 before this does anything
