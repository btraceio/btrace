# Extension TCK — Part 3: Performance Suite & Gradle Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `PerformanceSuite` to `btrace-tck` (measuring impl overhead vs. NoOp shim baseline with optional author-declared budgets) and build `btrace-tck-gradle-plugin` (a thin Groovy Gradle plugin that wraps the TCK JAR), making `./gradlew tckCheck` available to extension authors.

**Architecture:** `PerformanceSuite` uses a reflection-based microbenchmark loop (warm-up + timed run) against each service method — no JMH dependency to keep the TCK JAR self-contained. The baseline is the NoOp shim's per-call latency; the impl must stay within `baseline-multiplier × baseline` (default 10×). Optional per-service budgets come from `tck-config.yaml` parsed via Jackson. `btrace-tck-gradle-plugin` is a new Gradle included-build in the mono-repo, with a single `TckCheckTask` that execs `java -jar btrace-tck.jar` and a `btraceTck {}` DSL block.

**Tech Stack:** Java 11 nanoTime-based microbenchmarks, Jackson YAML, Groovy Gradle plugin, Gradle TestKit for plugin tests.

**Prerequisite:** Plans 1 and 2 complete.

---

## File Map

### New: `btrace-tck-gradle-plugin/`
| File | Responsibility |
|------|---------------|
| `settings.gradle` | Plugin included-build settings |
| `build.gradle` | Groovy + java-gradle-plugin build config |
| `src/main/groovy/io/btrace/gradle/tck/BTraceTckPlugin.groovy` | Plugin entry point |
| `src/main/groovy/io/btrace/gradle/tck/TckCheckTask.groovy` | JavaExec wrapper task |
| `src/main/groovy/io/btrace/gradle/tck/TckExtension.groovy` | `btraceTck {}` DSL block |
| `src/test/groovy/io/btrace/gradle/tck/BTraceTckPluginTest.groovy` | Gradle TestKit tests |

### Modified: `btrace-tck/`
| File | Change |
|------|--------|
| `build.gradle` | Add YAML dependency |
| `src/main/java/io/btrace/tck/TckConfig.java` | Parse tck-config.yaml |
| `src/main/java/io/btrace/tck/TckInput.java` | Expose parsed TckConfig |
| `src/main/java/io/btrace/tck/suite/PerformanceSuite.java` | New suite |
| `src/main/java/io/btrace/tck/check/perf/BaselineCheck.java` | Measure NoOp shim latency |
| `src/main/java/io/btrace/tck/check/perf/ImplOverheadCheck.java` | Impl ≤ N× baseline |
| `src/main/java/io/btrace/tck/check/perf/BudgetCheck.java` | Per-service absolute budget |
| `src/main/java/io/btrace/tck/cli/TckMain.java` | Add perf suite + tck-config loading |

### Modified: `settings.gradle`
Add `includeBuild('btrace-tck-gradle-plugin')`.

---

## Task 1: TckConfig — parse tck-config.yaml

**Files:**
- Modify: `btrace-tck/build.gradle`
- Create: `btrace-tck/src/main/java/io/btrace/tck/TckConfig.java`
- Modify: `btrace-tck/src/main/java/io/btrace/tck/TckInput.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/TckConfigTest.java`

- [ ] **Step 1: Add Jackson YAML dependency**

In `btrace-tck/build.gradle`, the `jackson-dataformat-yaml` dependency should already be present from Plan 1. Verify:
```bash
./gradlew :btrace-tck:dependencies --configuration runtimeClasspath 2>&1 | grep yaml
```
If absent, add to `btrace-tck/build.gradle`:
```groovy
implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2'
```

- [ ] **Step 2: Write failing test**

Create `btrace-tck/src/test/java/io/btrace/tck/TckConfigTest.java`:
```java
package io.btrace.tck;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TckConfigTest {
    @TempDir Path tmp;

    @Test void defaultsApplyWhenNoFile() {
        var config = TckConfig.loadOrDefault(null);
        assertEquals(10.0, config.globalBaselineMultiplier());
    }

    @Test void parsesGlobalMultiplier() throws Exception {
        var yaml = tmp.resolve("tck-config.yaml");
        Files.writeString(yaml, "performance:\n  baseline-multiplier: 5\n");
        var config = TckConfig.loadOrDefault(yaml);
        assertEquals(5.0, config.globalBaselineMultiplier());
    }

    @Test void parsesPerServiceAbsoluteBudget() throws Exception {
        var yaml = tmp.resolve("tck-config.yaml");
        Files.writeString(yaml, """
            performance:
              baseline-multiplier: 10
              services:
                io.example.MyService:
                  max-latency-ns: 500
            """);
        var config = TckConfig.loadOrDefault(yaml);
        assertEquals(500L, config.maxLatencyNs("io.example.MyService"));
        assertTrue(config.maxLatencyNs("io.example.Other") < 0); // no budget → -1
    }

    @Test void parsesPerServiceRelativeMultiplier() throws Exception {
        var yaml = tmp.resolve("tck-config.yaml");
        Files.writeString(yaml, """
            performance:
              baseline-multiplier: 10
              services:
                io.example.TightService:
                  baseline-multiplier: 3
            """);
        var config = TckConfig.loadOrDefault(yaml);
        assertEquals(3.0, config.serviceBaselineMultiplier("io.example.TightService"));
        assertEquals(10.0, config.serviceBaselineMultiplier("io.example.Other")); // falls back to global
    }
}
```

- [ ] **Step 3: Implement TckConfig**

Create `btrace-tck/src/main/java/io/btrace/tck/TckConfig.java`:
```java
package io.btrace.tck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class TckConfig {
    private static final double DEFAULT_MULTIPLIER = 10.0;
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private final double globalBaselineMultiplier;
    private final Map<String, ServiceConfig> services;

    private TckConfig(double globalBaselineMultiplier, Map<String, ServiceConfig> services) {
        this.globalBaselineMultiplier = globalBaselineMultiplier;
        this.services                 = services != null ? services : Map.of();
    }

    /** Load from a YAML file, or return defaults if path is null or absent. */
    public static TckConfig loadOrDefault(Path configPath) {
        if (configPath == null || !configPath.toFile().exists()) {
            return new TckConfig(DEFAULT_MULTIPLIER, Map.of());
        }
        try {
            var root = YAML.readValue(configPath.toFile(), Root.class);
            if (root.performance == null) return new TckConfig(DEFAULT_MULTIPLIER, Map.of());
            double mult = root.performance.baselineMultiplier > 0
                ? root.performance.baselineMultiplier : DEFAULT_MULTIPLIER;
            return new TckConfig(mult, root.performance.services);
        } catch (IOException e) {
            System.err.println("[TCK] Warning: cannot parse tck-config.yaml: " + e.getMessage()
                + " — using defaults");
            return new TckConfig(DEFAULT_MULTIPLIER, Map.of());
        }
    }

    public double globalBaselineMultiplier() { return globalBaselineMultiplier; }

    /** Returns the per-service absolute latency budget in nanoseconds, or -1 if not set. */
    public long maxLatencyNs(String serviceClassName) {
        var sc = services.get(serviceClassName);
        return sc != null ? sc.maxLatencyNs : -1L;
    }

    /** Returns the per-service relative multiplier, falling back to the global default. */
    public double serviceBaselineMultiplier(String serviceClassName) {
        var sc = services.get(serviceClassName);
        return (sc != null && sc.baselineMultiplier > 0)
            ? sc.baselineMultiplier : globalBaselineMultiplier;
    }

    // --- Jackson model classes ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Root {
        @JsonProperty("performance") Performance performance;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Performance {
        @JsonProperty("baseline-multiplier") double baselineMultiplier;
        @JsonProperty("services")            Map<String, ServiceConfig> services = new HashMap<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ServiceConfig {
        @JsonProperty("max-latency-ns")     long   maxLatencyNs = -1L;
        @JsonProperty("baseline-multiplier") double baselineMultiplier;
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test --tests "*TckConfig*"
```

- [ ] **Step 5: Commit**

```bash
git add btrace-tck/
git commit -m "feat(tck): add TckConfig for tck-config.yaml parsing"
```

---

## Task 2: PerformanceSuite — BaselineCheck

`BaselineCheck` measures the NoOp shim's per-call latency for each service method. This becomes the reference for `ImplOverheadCheck`.

**Files:**
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/perf/Microbench.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/perf/BaselineCheck.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/check/perf/BaselineCheckTest.java`

- [ ] **Step 1: Write failing test**

Create `btrace-tck/src/test/java/io/btrace/tck/check/perf/BaselineCheckTest.java`:
```java
package io.btrace.tck.check.perf;

import io.btrace.tck.*;
import io.btrace.tck.suite.FixtureBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BaselineCheckTest {
    @TempDir static Path tmp;
    static Path goodZip;

    @BeforeAll static void buildFixtures() throws Exception {
        goodZip = FixtureBuilder.goodExtension(tmp);
    }

    @Test void baselineCheckProducesNonNegativeLatency() {
        var results = new BaselineCheck().run(goodZip, tmp.resolve("baseline-work"), TckConfig.loadOrDefault(null));
        // Baseline results carry latency in the message for downstream use
        assertNotNull(results);
        results.result().checks().forEach(r ->
            assertNotEquals(TckStatus.FAIL, r.status(), () -> "BaselineCheck failed: " + r.message()));
    }
}
```

- [ ] **Step 2: Implement Microbench helper**

Create `btrace-tck/src/main/java/io/btrace/tck/check/perf/Microbench.java`:
```java
package io.btrace.tck.check.perf;

import java.lang.reflect.*;
import java.util.*;

/**
 * Simple reflection-based microbenchmark for a single method call.
 * Performs a warmup phase, then a timed phase, and returns the median
 * nanoseconds per call.
 */
final class Microbench {
    private static final int WARMUP_CALLS  = 10_000;
    private static final int TIMED_CALLS   = 100_000;

    /**
     * @param instance  the object to call methods on
     * @param method    the method to benchmark
     * @param args      arguments to pass (pre-built null/zero/false args)
     * @return nanoseconds per call (median of three timed runs)
     */
    static long nsPerCall(Object instance, Method method, Object[] args) {
        // Warmup
        for (int i = 0; i < WARMUP_CALLS; i++) {
            try { method.invoke(instance, args); } catch (Exception ignored) {}
        }
        // Three timed runs — take the median
        long[] samples = new long[3];
        for (int r = 0; r < 3; r++) {
            long start = System.nanoTime();
            for (int i = 0; i < TIMED_CALLS; i++) {
                try { method.invoke(instance, args); } catch (Exception ignored) {}
            }
            samples[r] = (System.nanoTime() - start) / TIMED_CALLS;
        }
        Arrays.sort(samples);
        return samples[1]; // median
    }

    static Object[] nullArgs(Class<?>[] types) {
        var args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if      (types[i] == boolean.class) args[i] = false;
            else if (types[i].isPrimitive())    args[i] = 0;
            else                                args[i] = null;
        }
        return args;
    }
}
```

- [ ] **Step 3: Implement BaselineCheck**

Create `btrace-tck/src/main/java/io/btrace/tck/check/perf/BaselineCheck.java`:
```java
package io.btrace.tck.check.perf;

import io.btrace.tck.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public final class BaselineCheck {
    private static final String NAME = "BaselineCheck";

    public record BaselineResult(TckSuiteResult result, Map<String, Long> nsPerMethod) {}

    /**
     * Measures NoOp shim latency for each service method.
     * Baseline latencies are returned alongside the TckSuiteResult so
     * ImplOverheadCheck can use them without re-measuring.
     */
    public BaselineResult run(Path extensionZip, Path workDir, TckConfig config) {
        var checks  = new ArrayList<TckResult>();
        var latency = new LinkedHashMap<String, Long>();

        try {
            Path apiJar = extractApiJar(extensionZip, workDir);
            if (apiJar == null) {
                var r = new TckResult("perf", NAME, TckStatus.PASS,
                    null, "No api.jar — skipping baseline");
                return new BaselineResult(new TckSuiteResult("perf", List.of(r)), latency);
            }

            var shimsIndex = readShimsIndex(apiJar);
            if (shimsIndex.isEmpty()) {
                var r = new TckResult("perf", NAME, TckStatus.PASS,
                    null, "No shims in api.jar — baseline unavailable");
                return new BaselineResult(new TckSuiteResult("perf", List.of(r)), latency);
            }

            try (var cl = new URLClassLoader(new java.net.URL[]{apiJar.toUri().toURL()},
                                             BaselineCheck.class.getClassLoader())) {
                for (var entry : shimsIndex.entrySet()) {
                    String svcName  = entry.getKey();
                    String noopName = entry.getValue().get("noop");
                    if (noopName == null) continue;

                    Class<?> shimClass = cl.loadClass(noopName);
                    Object   shimInst  = shimClass.getDeclaredConstructor().newInstance();

                    for (Method m : shimClass.getDeclaredMethods()) {
                        if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) continue;
                        var args = Microbench.nullArgs(m.getParameterTypes());
                        long ns  = Microbench.nsPerCall(shimInst, m, args);
                        String key = svcName + "#" + m.getName();
                        latency.put(key, ns);
                        checks.add(new TckResult("perf", NAME + "/" + m.getName(), TckStatus.PASS,
                            null, "baseline=" + ns + "ns/call"));
                    }
                }
            }

            if (checks.isEmpty()) {
                checks.add(new TckResult("perf", NAME, TckStatus.PASS, null, "No shim methods found"));
            }
        } catch (Exception e) {
            checks.add(new TckResult("perf", NAME, TckStatus.FAIL,
                "BTRACE-PF-001", "Baseline measurement failed: " + e.getMessage()));
        }
        return new BaselineResult(new TckSuiteResult("perf", checks), latency);
    }

    private Path extractApiJar(Path extensionZip, Path workDir) throws IOException {
        Files.createDirectories(workDir);
        try (var zf = new ZipFile(extensionZip.toFile())) {
            var entry = Collections.list(zf.entries()).stream()
                .filter(e -> e.getName().endsWith("-api.jar")).findFirst().orElse(null);
            if (entry == null) return null;
            var dest = workDir.resolve(entry.getName());
            try (var in = zf.getInputStream(entry); var out = new FileOutputStream(dest.toFile())) {
                in.transferTo(out);
            }
            return dest;
        }
    }

    private Map<String, Map<String, String>> readShimsIndex(Path apiJar) throws IOException {
        var result = new LinkedHashMap<String, Map<String, String>>();
        try (var jf = new JarFile(apiJar.toFile())) {
            var entry = jf.getEntry("META-INF/btrace/shims.index");
            if (entry == null) return result;
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(jf.getInputStream(entry)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq < 0) continue;
                    String svc   = line.substring(0, eq).trim();
                    String pairs = line.substring(eq + 1).trim();
                    var map = new HashMap<String, String>();
                    for (String pair : pairs.split(",")) {
                        int colon = pair.indexOf(':');
                        if (colon >= 0) map.put(pair.substring(0, colon).trim(),
                                                 pair.substring(colon + 1).trim());
                    }
                    result.put(svc, map);
                }
            }
        }
        return result;
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test --tests "*BaselineCheck*"
```

- [ ] **Step 5: Commit**

```bash
git add btrace-tck/
git commit -m "feat(tck): add BaselineCheck and Microbench helper"
```

---

## Task 3: ImplOverheadCheck and BudgetCheck

**Files:**
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/perf/ImplOverheadCheck.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/perf/BudgetCheck.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/check/perf/ImplOverheadCheckTest.java`

- [ ] **Step 1: Write failing test**

Create `btrace-tck/src/test/java/io/btrace/tck/check/perf/ImplOverheadCheckTest.java`:
```java
package io.btrace.tck.check.perf;

import io.btrace.tck.*;
import io.btrace.tck.suite.FixtureBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ImplOverheadCheckTest {
    @TempDir static Path tmp;
    static Path goodZip;

    @BeforeAll static void buildFixtures() throws Exception {
        goodZip = FixtureBuilder.goodExtension(tmp);
    }

    @Test void implOverheadPassesOnGoodExtension() {
        // The good-extension fixture's impl (DoWorkImpl) is trivial —
        // it should be within 10× of the NoOp shim.
        var config  = TckConfig.loadOrDefault(null);
        var workDir = tmp.resolve("impl-work");
        var baseline = new BaselineCheck().run(goodZip, workDir.resolve("baseline"), config);
        var result   = new ImplOverheadCheck().run(
            goodZip, workDir.resolve("impl"), config, baseline.nsPerMethod());
        assertNotEquals(TckStatus.FAIL, result.status(), () -> result.message());
    }
}
```

- [ ] **Step 2: Implement ImplOverheadCheck**

Create `btrace-tck/src/main/java/io/btrace/tck/check/perf/ImplOverheadCheck.java`:
```java
package io.btrace.tck.check.perf;

import io.btrace.tck.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;

public final class ImplOverheadCheck {
    private static final String NAME = "ImplOverheadCheck";

    /**
     * Measures each service implementation method and verifies it stays within
     * config.serviceBaselineMultiplier(svc) × baseline latency.
     *
     * @param baselineNs map of "svcFqcn#methodName" → nanoseconds (from BaselineCheck)
     */
    public TckResult run(Path extensionZip, Path workDir, TckConfig config, Map<String, Long> baselineNs) {
        if (baselineNs.isEmpty()) {
            return new TckResult("perf", NAME, TckStatus.PASS,
                null, "No baseline available — skipping overhead check");
        }
        try (var session = ExtensionSession.open(extensionZip, workDir)) {
            for (String svc : session.descriptor().getServices()) {
                Class<?> implClass = session.resolveService(svc);
                if (implClass == null || implClass.isInterface()) continue;
                Object implInst = implClass.getDeclaredConstructor().newInstance();
                double multiplier = config.serviceBaselineMultiplier(svc);

                for (Method m : implClass.getDeclaredMethods()) {
                    if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) continue;
                    String key = svc + "#" + m.getName();
                    Long baseline = baselineNs.get(key);
                    if (baseline == null) continue; // no baseline for this method

                    var args   = Microbench.nullArgs(m.getParameterTypes());
                    long implNs = Microbench.nsPerCall(implInst, m, args);
                    long budget = (long)(baseline * multiplier);
                    if (implNs > budget) {
                        return new TckResult("perf", NAME, TckStatus.FAIL, "BTRACE-PF-002",
                            "Method '" + svc + "#" + m.getName()
                            + "' impl=" + implNs + "ns, baseline=" + baseline
                            + "ns, budget=" + budget + "ns (" + multiplier + "× exceeded)");
                    }
                }
            }
            return new TckResult("perf", NAME, TckStatus.PASS, null, null);
        } catch (ExtensionSession.LoadException e) {
            return new TckResult("perf", NAME, TckStatus.FAIL,
                "BTRACE-PF-002", "Cannot open extension: " + e.getMessage());
        } catch (Exception e) {
            return new TckResult("perf", NAME, TckStatus.FAIL,
                "BTRACE-PF-002", "Overhead measurement failed: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 3: Implement BudgetCheck**

Create `btrace-tck/src/main/java/io/btrace/tck/check/perf/BudgetCheck.java`:
```java
package io.btrace.tck.check.perf;

import io.btrace.tck.*;
import java.lang.reflect.*;
import java.nio.file.Path;

public final class BudgetCheck {
    private static final String NAME = "BudgetCheck";

    /**
     * Validates per-service absolute latency budgets declared in tck-config.yaml.
     * Skips services with no declared budget (those are covered by ImplOverheadCheck).
     */
    public TckResult run(Path extensionZip, Path workDir, TckConfig config) {
        try (var session = ExtensionSession.open(extensionZip, workDir)) {
            boolean anyBudget = false;
            for (String svc : session.descriptor().getServices()) {
                long maxNs = config.maxLatencyNs(svc);
                if (maxNs < 0) continue; // no budget declared for this service
                anyBudget = true;

                Class<?> implClass = session.resolveService(svc);
                if (implClass == null || implClass.isInterface()) continue;
                Object implInst = implClass.getDeclaredConstructor().newInstance();

                for (Method m : implClass.getDeclaredMethods()) {
                    if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) continue;
                    var  args  = Microbench.nullArgs(m.getParameterTypes());
                    long ns    = Microbench.nsPerCall(implInst, m, args);
                    if (ns > maxNs) {
                        return new TckResult("perf", NAME, TckStatus.FAIL, "BTRACE-PF-003",
                            "Method '" + svc + "#" + m.getName()
                            + "' measured=" + ns + "ns, budget=" + maxNs + "ns exceeded");
                    }
                }
            }
            String note = anyBudget ? null : "No per-service budgets declared in tck-config.yaml";
            return new TckResult("perf", NAME, TckStatus.PASS, null, note);
        } catch (ExtensionSession.LoadException e) {
            return new TckResult("perf", NAME, TckStatus.FAIL,
                "BTRACE-PF-003", "Cannot open extension: " + e.getMessage());
        } catch (Exception e) {
            return new TckResult("perf", NAME, TckStatus.FAIL,
                "BTRACE-PF-003", "Budget check failed: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test --tests "*ImplOverhead*"
```

- [ ] **Step 5: Commit**

```bash
git add btrace-tck/
git commit -m "feat(tck): add ImplOverheadCheck and BudgetCheck"
```

---

## Task 4: PerformanceSuite — assemble and wire into TckMain

**Files:**
- Create: `btrace-tck/src/main/java/io/btrace/tck/suite/PerformanceSuite.java`
- Modify: `btrace-tck/src/main/java/io/btrace/tck/cli/TckMain.java`
- Modify: `btrace-tck/src/main/java/io/btrace/tck/TckInput.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/suite/PerformanceSuiteTest.java`

- [ ] **Step 1: Write failing test**

Create `btrace-tck/src/test/java/io/btrace/tck/suite/PerformanceSuiteTest.java`:
```java
package io.btrace.tck.suite;

import io.btrace.tck.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceSuiteTest {
    @TempDir static Path tmp;
    static Path goodZip;

    @BeforeAll static void buildFixtures() throws Exception {
        goodZip = FixtureBuilder.goodExtension(tmp);
    }

    @Test void suiteIsNamedPerf() {
        assertEquals("perf", new PerformanceSuite().name());
    }

    @Test void goodExtensionPassesPerfChecks() {
        var input = TckInput.builder()
            .extensionZip(goodZip)
            .reportDir(tmp.resolve("report"))
            .tckConfig(null)
            .build();
        var result = new PerformanceSuite().run(input);
        assertFalse(result.hasFailed(), () -> "Perf checks failed: " + result.checks());
    }
}
```

- [ ] **Step 2: Implement PerformanceSuite**

Create `btrace-tck/src/main/java/io/btrace/tck/suite/PerformanceSuite.java`:
```java
package io.btrace.tck.suite;

import io.btrace.tck.*;
import io.btrace.tck.check.perf.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class PerformanceSuite implements Suite {
    @Override public String name() { return "perf"; }

    @Override public TckSuiteResult run(TckInput input) {
        if (input.extensionZip() == null) {
            return fail("--extension ZIP path is required");
        }
        Path workDir;
        try {
            workDir = Files.createTempDirectory("btrace-tck-perf-");
        } catch (IOException e) {
            return fail("Cannot create work dir: " + e.getMessage());
        }

        var config = TckConfig.loadOrDefault(input.tckConfig());
        var zip    = input.extensionZip();

        var baselineResult = new BaselineCheck().run(zip, workDir.resolve("baseline"), config);
        var baselineNs     = baselineResult.nsPerMethod();
        var overheadResult = new ImplOverheadCheck()
            .run(zip, workDir.resolve("overhead"), config, baselineNs);
        var budgetResult   = new BudgetCheck()
            .run(zip, workDir.resolve("budget"), config);

        var checks = new ArrayList<>(baselineResult.result().checks());
        checks.add(overheadResult);
        checks.add(budgetResult);
        return new TckSuiteResult("perf", checks);
    }

    private TckSuiteResult fail(String msg) {
        return new TckSuiteResult("perf", List.of(
            new TckResult("perf", "setup", TckStatus.FAIL, "BTRACE-PF-001", msg)));
    }
}
```

- [ ] **Step 3: Wire into TckMain**

In `TckMain.java`, update the suites loop to add `"perf"`, import `PerformanceSuite`, and update the default to `"structural,lifecycle,behavioral,perf"`:

```java
// in the switch block:
case "perf"        -> activeSuites.add(new PerformanceSuite());
```

```java
// default value annotation:
@Option(names = {"--suites", "-s"}, split = ",",
        defaultValue = "structural,lifecycle,behavioral,perf",
        description = "Suites to run: structural,lifecycle,behavioral,perf")
List<String> suites;
```

Add import:
```java
import io.btrace.tck.suite.PerformanceSuite;
```

- [ ] **Step 4: Run all tests — expect PASS**

```bash
./gradlew :btrace-tck:test
```
Performance tests can be slow (warmup + timed runs × methods). If they time out in CI, reduce `WARMUP_CALLS` in `Microbench.java` to 1000 and `TIMED_CALLS` to 10_000. The absolute numbers don't need to be precise for the TCK — what matters is the ratio.

- [ ] **Step 5: Spotless and commit**

```bash
./gradlew spotlessApply
git add btrace-tck/
git commit -m "feat(tck): add PerformanceSuite (BaselineCheck, ImplOverheadCheck, BudgetCheck)"
```

---

## Task 5: btrace-tck-gradle-plugin module skeleton

**Files:**
- Create: `btrace-tck-gradle-plugin/settings.gradle`
- Create: `btrace-tck-gradle-plugin/build.gradle`
- Modify: `settings.gradle` (root)

- [ ] **Step 1: Add to root settings.gradle**

In the root `settings.gradle`, add `includeBuild('btrace-tck-gradle-plugin')` in the `pluginManagement` block (or after the existing `includeBuild('btrace-gradle-plugin')` line):

```groovy
includeBuild('btrace-tck-gradle-plugin')
```

- [ ] **Step 2: Create btrace-tck-gradle-plugin/settings.gradle**

```groovy
rootProject.name = 'btrace-tck-gradle-plugin'
```

- [ ] **Step 3: Create btrace-tck-gradle-plugin/build.gradle**

```groovy
plugins {
    id 'groovy'
    id 'java-gradle-plugin'
    id 'maven-publish'
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(11) }
}

group   = project.findProperty('GROUP') ?: (rootProject.group ?: 'io.btrace')
version = rootProject.version ?: '3.0.0-SNAPSHOT'

repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation gradleApi()
    implementation localGroovy()
    testImplementation gradleTestKit()
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
    testRuntimeOnly    'org.junit.platform:junit-platform-launcher'
}

test { useJUnitPlatform() }

gradlePlugin {
    plugins {
        btraceTck {
            id                  = 'io.btrace.tck'
            implementationClass = 'io.btrace.gradle.tck.BTraceTckPlugin'
            displayName         = 'BTrace TCK Plugin'
            description         = 'Runs the BTrace Extension TCK against the current extension project.'
        }
    }
}
```

- [ ] **Step 4: Verify the plugin module can be assembled**

```bash
./gradlew :btrace-tck-gradle-plugin:assemble 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL` (no Groovy sources yet, just the scaffolding).

- [ ] **Step 5: Commit**

```bash
git add btrace-tck-gradle-plugin/ settings.gradle
git commit -m "feat(tck): add btrace-tck-gradle-plugin module skeleton"
```

---

## Task 6: TckExtension DSL block

**Files:**
- Create: `btrace-tck-gradle-plugin/src/main/groovy/io/btrace/gradle/tck/TckExtension.groovy`

- [ ] **Step 1: Implement TckExtension**

Create `btrace-tck-gradle-plugin/src/main/groovy/io/btrace/gradle/tck/TckExtension.groovy`:
```groovy
package io.btrace.gradle.tck

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class TckExtension {

    abstract RegularFileProperty  getExtensionZip()
    abstract Property<String>     getBtraceHome()
    abstract RegularFileProperty  getTckConfig()
    abstract DirectoryProperty    getReportDir()
    abstract ListProperty<String> getSuites()
    abstract Property<Boolean>    getWireToBuild()

    @Inject
    TckExtension(ObjectFactory objects) {
        suites.convention(['structural', 'lifecycle', 'behavioral', 'perf'])
        wireToBuild.convention(true)
        btraceHome.convention(System.env.BTRACE_HOME ?: '')
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add btrace-tck-gradle-plugin/
git commit -m "feat(tck): add TckExtension DSL block"
```

---

## Task 7: TckCheckTask and BTraceTckPlugin

**Files:**
- Create: `btrace-tck-gradle-plugin/src/main/groovy/io/btrace/gradle/tck/TckCheckTask.groovy`
- Create: `btrace-tck-gradle-plugin/src/main/groovy/io/btrace/gradle/tck/BTraceTckPlugin.groovy`

- [ ] **Step 1: Implement TckCheckTask**

`TckCheckTask` runs `java -jar btrace-tck.jar` as a child process. The `btrace-tck.jar` must be available on the Gradle classpath or resolved as a dependency.

Create `btrace-tck-gradle-plugin/src/main/groovy/io/btrace/gradle/tck/TckCheckTask.groovy`:
```groovy
package io.btrace.gradle.tck

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

@CacheableTask
abstract class TckCheckTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    abstract RegularFileProperty getExtensionZip()

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.ABSOLUTE)
    abstract RegularFileProperty getTckJar()

    @Input
    @Optional
    abstract Property<String> getBtraceHome()

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.ABSOLUTE)
    abstract RegularFileProperty getTckConfig()

    @OutputDirectory
    abstract DirectoryProperty getReportDir()

    @Input
    abstract ListProperty<String> getSuites()

    @TaskAction
    void runTck() {
        def tckJarFile = tckJar.orNull?.asFile
        if (tckJarFile == null || !tckJarFile.exists()) {
            throw new GradleException(
                "[btrace-tck] btrace-tck.jar not found. Set tckJar = file('path/to/btrace-tck.jar') " +
                "in btraceTck {} block, or ensure io.btrace:btrace-tck is resolvable.")
        }

        def args = ['java', '-jar', tckJarFile.absolutePath,
                    '--extension', extensionZip.asFile.get().absolutePath,
                    '--report-dir', reportDir.asFile.get().absolutePath,
                    '--suites',     suites.get().join(',')]

        def home = btraceHome.orNull
        if (home && !home.isBlank()) {
            args += ['--btrace-home', home]
        }
        def cfg = tckConfig.orNull?.asFile
        if (cfg?.exists()) {
            args += ['--tck-config', cfg.absolutePath]
        }

        logger.lifecycle("[btrace-tck] Running: ${args.join(' ')}")
        def proc = args.execute()
        proc.consumeProcessOutput(System.out, System.err)
        int exitCode = proc.waitFor()

        if (exitCode != 0) {
            throw new GradleException(
                "[btrace-tck] TCK FAILED (exit ${exitCode}). See report: " +
                reportDir.asFile.get().absolutePath + '/tck-report.html')
        }
        logger.lifecycle("[btrace-tck] TCK PASSED. Report: " +
            reportDir.asFile.get().absolutePath + '/tck-report.html')
    }
}
```

- [ ] **Step 2: Implement BTraceTckPlugin**

Create `btrace-tck-gradle-plugin/src/main/groovy/io/btrace/gradle/tck/BTraceTckPlugin.groovy`:
```groovy
package io.btrace.gradle.tck

import org.gradle.api.Plugin
import org.gradle.api.Project

class BTraceTckPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def ext = project.extensions.create('btraceTck', TckExtension, project.objects)

        // Resolve btrace-tck.jar: first from explicit tckJar, then from a tckRuntime config
        project.configurations.create('tckRuntime') {
            canBeConsumed = false
            canBeResolved = true
            description   = 'Runtime classpath for btrace-tck JAR'
        }

        def tckCheck = project.tasks.register('tckCheck', TckCheckTask) {
            group       = 'verification'
            description = 'Runs the BTrace Extension TCK against this extension.'

            extensionZip.set(ext.extensionZip)
            btraceHome.set(ext.btraceHome)
            tckConfig.set(ext.tckConfig)
            reportDir.set(ext.reportDir.orElse(
                project.layout.buildDirectory.dir('tck-report')))
            suites.set(ext.suites)

            // Resolve btrace-tck.jar from tckRuntime config if tckJar not set
            tckJar.set(project.providers.provider {
                def explicit = ext.extensionZip.orNull // reuse field name is wrong here
                // If the user sets it directly via TckCheckTask.tckJar, that wins.
                // Otherwise, resolve from tckRuntime config:
                def resolved = project.configurations.tckRuntime.resolvedConfiguration
                    .resolvedArtifacts.find { it.name == 'btrace-tck' }?.file
                resolved ? project.layout.projectDirectory.file(resolved.absolutePath) : null
            })
        }

        project.afterEvaluate {
            if (ext.wireToBuild.get()) {
                project.tasks.named('check').configure { it.dependsOn(tckCheck) }
            }
        }
    }
}
```

> **Note on tckJar resolution:** The `tckJar` field in the task should be set by the plugin via the `tckRuntime` configuration. Extension authors add `tckRuntime 'io.btrace:btrace-tck:3.x.y'` to their dependencies, and the plugin resolves it. If that's missing, the task throws a clear error.

- [ ] **Step 3: Verify the plugin compiles**

```bash
./gradlew :btrace-tck-gradle-plugin:assemble
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add btrace-tck-gradle-plugin/
git commit -m "feat(tck): add TckCheckTask and BTraceTckPlugin"
```

---

## Task 8: Gradle plugin test

**Files:**
- Create: `btrace-tck-gradle-plugin/src/test/groovy/io/btrace/gradle/tck/BTraceTckPluginTest.groovy`

- [ ] **Step 1: Write plugin test using Gradle TestKit**

Create `btrace-tck-gradle-plugin/src/test/groovy/io/btrace/gradle/tck/BTraceTckPluginTest.groovy`:
```groovy
package io.btrace.gradle.tck

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import static org.junit.jupiter.api.Assertions.*

class BTraceTckPluginTest {
    @TempDir Path projectDir

    @Test
    void pluginAppliesWithoutError() {
        // Write a minimal build.gradle that applies the plugin
        new File(projectDir.toFile(), 'build.gradle').text = """
            plugins {
                id 'io.btrace.tck'
            }
            // btraceTck.extensionZip is not set — tckCheck should be registered but not run
        """
        new File(projectDir.toFile(), 'settings.gradle').text = "rootProject.name = 'test-ext'"

        def result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments('tasks', '--all')
            .build()

        assertTrue(result.output.contains('tckCheck'), "tckCheck task should be registered")
    }

    @Test
    void tckCheckTaskIsRegistered() {
        new File(projectDir.toFile(), 'build.gradle').text = """
            plugins { id 'io.btrace.tck' }
        """
        new File(projectDir.toFile(), 'settings.gradle').text = "rootProject.name = 'test-ext'"

        def result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments('help', '--task', 'tckCheck')
            .build()

        assertTrue(result.output.contains('Description'))
        assertTrue(result.output.contains('tckCheck'))
    }
}
```

- [ ] **Step 2: Run plugin tests — expect PASS**

```bash
./gradlew :btrace-tck-gradle-plugin:test
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add btrace-tck-gradle-plugin/
git commit -m "feat(tck): add BTraceTckPluginTest using Gradle TestKit"
```

---

## Task 9: End-to-end smoke test and final cleanup

- [ ] **Step 1: Build the standalone JAR**

```bash
./gradlew :btrace-tck:jar
ls -lh btrace-tck/build/libs/
```
Expected: `btrace-tck-*.jar` present.

- [ ] **Step 2: Run all four suites against btrace-contracts**

```bash
./gradlew :btrace-extensions:btrace-contracts:packageExtension
CONTRACTS_ZIP=$(find btrace-extensions/btrace-contracts/build -name "*-extension.zip" | head -1)
java -jar btrace-tck/build/libs/btrace-tck-*.jar \
  --extension "$CONTRACTS_ZIP" \
  --report-dir /tmp/contracts-tck-final \
  --suites structural,lifecycle,behavioral,perf
```
Expected: `TCK PASSED`.

- [ ] **Step 3: Verify HTML report is readable**

```bash
open /tmp/contracts-tck-final/tck-report.html
```
Manually verify: summary section present, four suite sections, all checks green.

- [ ] **Step 4: Run all module tests**

```bash
./gradlew :btrace-ext-validator:test :btrace-tck:test :btrace-tck-gradle-plugin:test
```
Expected: all pass.

- [ ] **Step 5: Spotless and final commit**

```bash
./gradlew spotlessApply
git add btrace-tck/ btrace-tck-gradle-plugin/ btrace-ext-validator/ settings.gradle
git commit -m "chore(tck): Plan 3 complete — PerformanceSuite and btrace-tck-gradle-plugin"
```

---

## Usage Reference (for documentation)

After Plan 3 is complete, extension authors use the TCK as follows:

**Standalone:**
```bash
java -jar btrace-tck.jar \
  --extension my-extension-1.0.0-extension.zip \
  --btrace-home $BTRACE_HOME \
  --tck-config tck-config.yaml \
  --report-dir build/tck-report
```

**Gradle plugin:**
```groovy
plugins {
    id 'io.btrace.extension' version '3.x.y'
    id 'io.btrace.tck'       version '3.x.y'
}

dependencies {
    tckRuntime 'io.btrace:btrace-tck:3.x.y'
}

btraceTck {
    extensionZip = tasks.packageExtension.archiveFile
    btraceHome   = System.env.BTRACE_HOME ?: '/opt/btrace'
    tckConfig    = file('tck-config.yaml')   // optional
}
```

Running `./gradlew tckCheck` or `./gradlew check` (auto-wired by default) produces:
- `build/tck-report/tck-results.xml` — JUnit XML for CI
- `build/tck-report/tck-report.html` — browsable report
- Real-time stdout with `[PASS]`/`[FAIL]`/`[SKIP]` per check
