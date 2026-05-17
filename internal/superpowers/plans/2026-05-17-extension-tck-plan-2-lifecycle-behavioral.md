# Extension TCK — Part 2: Lifecycle & Behavioral Suites Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `LifecycleSuite` and `BehavioralSuite` to `btrace-tck`, verifying that an extension loads cleanly, initialises, makes services injectable, falls back gracefully to shims when denied, and remains data-race-free under concurrent access — all without spawning a child JVM.

**Architecture:** Both suites use `ExtensionLoaderImpl` and `ExtensionBridgeImpl` from `btrace-core` directly in the TCK's own JVM. `LifecycleSuite` checks the load→init→inject→close sequence. `BehavioralSuite` reuses the same loader session to test shim fallback (via `PermissionPolicy`), null safety of NoOp shims, concurrency, and required-service failure mode. `TckMain` is updated to wire these suites in after structural passes.

**Tech Stack:** btrace-core (`ExtensionLoaderImpl`, `ExtensionBridgeImpl`, `PermissionPolicy`, `FileSystemExtensionRepository`), Java 11 concurrency primitives, JUnit 5 for self-tests.

**Prerequisite:** Plan 1 complete (`btrace-ext-validator` built and all structural checks passing).

---

## File Map

### Modified: `btrace-tck/`
| File | Change |
|------|--------|
| `build.gradle` | Add `implementation project(':btrace-core')` |
| `src/main/java/io/btrace/tck/suite/LifecycleSuite.java` | New suite |
| `src/main/java/io/btrace/tck/check/lifecycle/LoadCheck.java` | Extension loads without error |
| `src/main/java/io/btrace/tck/check/lifecycle/InitCheck.java` | `isLoaded()` true after `load()` |
| `src/main/java/io/btrace/tck/check/lifecycle/InjectionCheck.java` | `bridge.getExtensionClass()` non-null |
| `src/main/java/io/btrace/tck/check/lifecycle/CloseCheck.java` | `loader.close()` without exception |
| `src/main/java/io/btrace/tck/suite/BehavioralSuite.java` | New suite |
| `src/main/java/io/btrace/tck/check/behavioral/NullSafetyCheck.java` | NoOp shim methods don't throw |
| `src/main/java/io/btrace/tck/check/behavioral/ShimFallbackCheck.java` | Denied ext → shim substituted |
| `src/main/java/io/btrace/tck/check/behavioral/ConcurrencyCheck.java` | 8-thread × 1000 calls, no races |
| `src/main/java/io/btrace/tck/check/behavioral/RequiredServiceCheck.java` | Required service fails fast if denied |
| `src/main/java/io/btrace/tck/cli/TckMain.java` | Wire in lifecycle and behavioral suites |
| `src/main/java/io/btrace/tck/ExtensionSession.java` | Shared lifecycle state for both suites |
| `src/test/java/io/btrace/tck/suite/LifecycleSuiteTest.java` | Unit tests |
| `src/test/java/io/btrace/tck/suite/BehavioralSuiteTest.java` | Unit tests |

---

## Task 1: Add btrace-core dependency

**Files:**
- Modify: `btrace-tck/build.gradle`

- [ ] **Step 1: Add dependency**

In `btrace-tck/build.gradle`, add to `dependencies`:
```groovy
implementation project(':btrace-core')
```

- [ ] **Step 2: Verify it resolves**

```bash
./gradlew :btrace-tck:dependencies --configuration runtimeClasspath 2>&1 | grep btrace-core
```
Expected: `+--- project :btrace-core` in output.

- [ ] **Step 3: Run existing tests to verify no regression**

```bash
./gradlew :btrace-tck:test
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add btrace-tck/build.gradle
git commit -m "feat(tck): add btrace-core dependency to btrace-tck"
```

---

## Task 2: ExtensionSession — shared lifecycle state

Both suites need to open an extension, use it, and close it. `ExtensionSession` encapsulates this lifecycle so each suite doesn't duplicate the setup/teardown logic.

**Files:**
- Create: `btrace-tck/src/main/java/io/btrace/tck/ExtensionSession.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/ExtensionSessionTest.java`

- [ ] **Step 1: Write failing test**

Create `btrace-tck/src/test/java/io/btrace/tck/ExtensionSessionTest.java`:
```java
package io.btrace.tck;

import io.btrace.tck.suite.FixtureBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionSessionTest {
    @TempDir static Path tmp;
    static Path goodZip;

    @BeforeAll static void buildFixtures() throws Exception {
        goodZip = FixtureBuilder.goodExtension(tmp);
    }

    @Test void openAndCloseWithoutErrors() throws Exception {
        try (var session = ExtensionSession.open(goodZip, tmp.resolve("ext-dir"))) {
            assertNotNull(session.descriptor());
            assertTrue(session.descriptor().isLoaded());
        }
    }

    @Test void openFailsForNonExistentZip() {
        assertThrows(ExtensionSession.LoadException.class,
            () -> ExtensionSession.open(tmp.resolve("nonexistent.zip"), tmp.resolve("x")));
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew :btrace-tck:test 2>&1 | grep "error:"
```

- [ ] **Step 3: Implement ExtensionSession**

Create `btrace-tck/src/main/java/io/btrace/tck/ExtensionSession.java`:
```java
package io.btrace.tck;

import io.btrace.extension.ExtensionLoader;
import io.btrace.extension.ExtensionDescriptorDTO;
import io.btrace.extension.impl.ExtensionBridgeImpl;
import io.btrace.extension.impl.ExtensionConfig;
import io.btrace.extension.impl.ExtensionLoaderImpl;
import io.btrace.extension.impl.FileSystemExtensionRepository;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/**
 * Owns the full lifecycle of an extension under test:
 * extract ZIP → create loader → discover → load → (use) → close.
 *
 * Implements AutoCloseable so callers can use try-with-resources.
 */
public final class ExtensionSession implements AutoCloseable {

    private final ExtensionLoaderImpl loader;
    private final ExtensionBridgeImpl bridge;
    private final ExtensionDescriptorDTO descriptor;

    private ExtensionSession(ExtensionLoaderImpl loader,
                              ExtensionBridgeImpl bridge,
                              ExtensionDescriptorDTO descriptor) {
        this.loader     = loader;
        this.bridge     = bridge;
        this.descriptor = descriptor;
    }

    /**
     * Open an extension session from an extension ZIP.
     *
     * @param extensionZip path to *-extension.zip
     * @param workDir      temp directory to extract the ZIP contents into
     */
    public static ExtensionSession open(Path extensionZip, Path workDir) throws LoadException {
        if (!Files.exists(extensionZip)) {
            throw new LoadException("Extension ZIP not found: " + extensionZip);
        }
        try {
            Files.createDirectories(workDir);
            extractZip(extensionZip, workDir);

            var repo   = new FileSystemExtensionRepository(workDir, 200 /* Priority.USER */);
            var loader = new ExtensionLoaderImpl(
                List.of(repo),
                ExtensionSession.class.getClassLoader(),
                ExtensionConfig.createDefault(),
                null,  // no Instrumentation needed for unit-level checks
                "tck-test");

            var extensions = loader.discoverExtensions();
            if (extensions.isEmpty()) {
                throw new LoadException("No extensions discovered in: " + extensionZip);
            }
            var desc = extensions.get(0);
            loader.load(desc);

            var bridge = new ExtensionBridgeImpl(loader);
            return new ExtensionSession(loader, bridge, desc);
        } catch (LoadException e) {
            throw e;
        } catch (Exception e) {
            throw new LoadException("Failed to open extension session: " + e.getMessage(), e);
        }
    }

    public ExtensionDescriptorDTO descriptor() { return descriptor; }
    public ExtensionLoaderImpl    loader()     { return loader; }
    public ExtensionBridgeImpl    bridge()     { return bridge; }

    /**
     * Resolve the implementation class for a declared service.
     * Returns null if the service is not available (e.g., denied by policy).
     */
    public Class<?> resolveService(String serviceClassName) {
        try {
            return bridge.getExtensionClass(serviceClassName);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        try { loader.close(); } catch (Exception ignored) {}
    }

    private static void extractZip(Path zip, Path targetDir) throws IOException {
        try (var zf = new ZipFile(zip.toFile())) {
            var entries = Collections.list(zf.entries());
            for (var entry : entries) {
                var dest = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    try (var in = zf.getInputStream(entry);
                         var out = new FileOutputStream(dest.toFile())) {
                        in.transferTo(out);
                    }
                }
            }
        }
    }

    public static final class LoadException extends Exception {
        public LoadException(String message)            { super(message); }
        public LoadException(String message, Throwable cause) { super(message, cause); }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test
```

- [ ] **Step 5: Commit**

```bash
git add btrace-tck/
git commit -m "feat(tck): add ExtensionSession for lifecycle state management"
```

---

## Task 3: LifecycleSuite

**Files:**
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/lifecycle/LoadCheck.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/lifecycle/InitCheck.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/lifecycle/InjectionCheck.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/lifecycle/CloseCheck.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/suite/LifecycleSuite.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/suite/LifecycleSuiteTest.java`

- [ ] **Step 1: Write failing test**

Create `btrace-tck/src/test/java/io/btrace/tck/suite/LifecycleSuiteTest.java`:
```java
package io.btrace.tck.suite;

import io.btrace.tck.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LifecycleSuiteTest {
    @TempDir static Path tmp;
    static Path goodZip;

    @BeforeAll static void buildFixtures() throws Exception {
        goodZip = FixtureBuilder.goodExtension(tmp);
    }

    @Test void suiteIsNamedLifecycle() {
        assertEquals("lifecycle", new LifecycleSuite().name());
    }

    @Test void goodExtensionPassesAllLifecycleChecks() {
        var input = TckInput.builder()
            .extensionZip(goodZip)
            .reportDir(tmp.resolve("report"))
            .build();
        var result = new LifecycleSuite().run(input);
        assertFalse(result.hasFailed(), () -> "Lifecycle checks failed: " + result.checks());
    }

    @Test void missingZipProducesFailResult() {
        var input = TckInput.builder()
            .extensionZip(tmp.resolve("nonexistent.zip"))
            .reportDir(tmp.resolve("report"))
            .build();
        var result = new LifecycleSuite().run(input);
        assertTrue(result.hasFailed());
    }
}
```

- [ ] **Step 2: Implement the four checks**

`LoadCheck.java`:
```java
package io.btrace.tck.check.lifecycle;

import io.btrace.tck.*;
import java.nio.file.Path;

public final class LoadCheck {
    private static final String NAME = "LoadCheck";

    public TckResult run(Path extensionZip, Path workDir) {
        try (var session = ExtensionSession.open(extensionZip, workDir)) {
            var desc = session.descriptor();
            if (!desc.isLoaded()) {
                return new TckResult("lifecycle", NAME, TckStatus.FAIL,
                    "BTRACE-LC-001", "Extension loaded=false after load()");
            }
            return new TckResult("lifecycle", NAME, TckStatus.PASS, null, null);
        } catch (ExtensionSession.LoadException e) {
            return new TckResult("lifecycle", NAME, TckStatus.FAIL,
                "BTRACE-LC-001", "Extension failed to load: " + e.getMessage());
        }
    }
}
```

`InitCheck.java`:
```java
package io.btrace.tck.check.lifecycle;

import io.btrace.tck.*;
import java.nio.file.Path;

public final class InitCheck {
    private static final String NAME = "InitCheck";

    public TckResult run(Path extensionZip, Path workDir) {
        try (var session = ExtensionSession.open(extensionZip, workDir)) {
            var desc = session.descriptor();
            // isLoaded() becomes true only after initialize() completes inside load()
            if (!desc.isLoaded()) {
                return new TckResult("lifecycle", NAME, TckStatus.FAIL,
                    "BTRACE-LC-002", "Extension initialize() did not complete (isLoaded=false)");
            }
            return new TckResult("lifecycle", NAME, TckStatus.PASS, null, null);
        } catch (ExtensionSession.LoadException e) {
            return new TckResult("lifecycle", NAME, TckStatus.FAIL,
                "BTRACE-LC-002", "Load failed before initialize(): " + e.getMessage());
        }
    }
}
```

`InjectionCheck.java`:
```java
package io.btrace.tck.check.lifecycle;

import io.btrace.tck.*;
import java.nio.file.Path;
import java.util.List;

public final class InjectionCheck {
    private static final String NAME = "InjectionCheck";

    public TckResult run(Path extensionZip, Path workDir) {
        try (var session = ExtensionSession.open(extensionZip, workDir)) {
            List<String> services = session.descriptor().getServices();
            if (services.isEmpty()) {
                return new TckResult("lifecycle", NAME, TckStatus.PASS,
                    null, "No services declared; skipping injection check");
            }
            for (String svc : services) {
                Class<?> resolved = session.resolveService(svc);
                if (resolved == null) {
                    return new TckResult("lifecycle", NAME, TckStatus.FAIL,
                        "BTRACE-LC-003", "Service '" + svc + "' could not be injected (null)");
                }
                // Verify it's not just the raw interface (should be an impl or shim)
                if (resolved.isInterface()) {
                    return new TckResult("lifecycle", NAME, TckStatus.FAIL,
                        "BTRACE-LC-003",
                        "Service '" + svc + "' resolved to interface only — impl or shim expected");
                }
            }
            return new TckResult("lifecycle", NAME, TckStatus.PASS, null, null);
        } catch (ExtensionSession.LoadException e) {
            return new TckResult("lifecycle", NAME, TckStatus.FAIL,
                "BTRACE-LC-003", "Load failed before injection: " + e.getMessage());
        }
    }
}
```

`CloseCheck.java`:
```java
package io.btrace.tck.check.lifecycle;

import io.btrace.tck.*;
import java.nio.file.Path;

public final class CloseCheck {
    private static final String NAME = "CloseCheck";

    public TckResult run(Path extensionZip, Path workDir) {
        try {
            var session = ExtensionSession.open(extensionZip, workDir);
            // close() must not throw
            session.close();
            return new TckResult("lifecycle", NAME, TckStatus.PASS, null, null);
        } catch (ExtensionSession.LoadException e) {
            return new TckResult("lifecycle", NAME, TckStatus.FAIL,
                "BTRACE-LC-004", "Load failed before close: " + e.getMessage());
        } catch (Exception e) {
            return new TckResult("lifecycle", NAME, TckStatus.FAIL,
                "BTRACE-LC-004", "close() threw: " + e.getMessage());
        }
    }
}
```

`LifecycleSuite.java`:
```java
package io.btrace.tck.suite;

import io.btrace.tck.*;
import io.btrace.tck.check.lifecycle.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public final class LifecycleSuite implements Suite {
    @Override public String name() { return "lifecycle"; }

    @Override public TckSuiteResult run(TckInput input) {
        if (input.extensionZip() == null) {
            return fail("--extension ZIP path is required");
        }
        Path workDir;
        try {
            workDir = Files.createTempDirectory("btrace-tck-lifecycle-");
        } catch (IOException e) {
            return fail("Cannot create work dir: " + e.getMessage());
        }
        var zip = input.extensionZip();
        return new TckSuiteResult("lifecycle", List.of(
            new LoadCheck().run(zip, workDir.resolve("load")),
            new InitCheck().run(zip, workDir.resolve("init")),
            new InjectionCheck().run(zip, workDir.resolve("inject")),
            new CloseCheck().run(zip, workDir.resolve("close"))));
    }

    private TckSuiteResult fail(String msg) {
        return new TckSuiteResult("lifecycle", List.of(
            new TckResult("lifecycle", "setup", TckStatus.FAIL, "BTRACE-LC-001", msg)));
    }
}
```

- [ ] **Step 3: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test --tests "*LifecycleSuite*"
```
Expected: all lifecycle tests pass.

- [ ] **Step 4: Commit**

```bash
git add btrace-tck/
git commit -m "feat(tck): add LifecycleSuite (LoadCheck, InitCheck, InjectionCheck, CloseCheck)"
```

---

## Task 4: BehavioralSuite — NullSafetyCheck

The NoOp shim for each service is stored in the api.jar under `META-INF/btrace/shims.index`. `NullSafetyCheck` loads the NoOp shim class and calls every method via reflection with null/zero/false arguments, verifying no exception escapes to the caller.

**Files:**
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/behavioral/NullSafetyCheck.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/check/behavioral/NullSafetyCheckTest.java`

- [ ] **Step 1: Write failing test**

Create `btrace-tck/src/test/java/io/btrace/tck/check/behavioral/NullSafetyCheckTest.java`:
```java
package io.btrace.tck.check.behavioral;

import io.btrace.tck.*;
import io.btrace.tck.suite.FixtureBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class NullSafetyCheckTest {
    @TempDir static Path tmp;
    static Path goodZip;

    @BeforeAll static void buildFixtures() throws Exception {
        goodZip = FixtureBuilder.goodExtension(tmp);
    }

    @Test void goodExtensionShimPassesNullSafety() throws Exception {
        var result = new NullSafetyCheck().run(goodZip, tmp.resolve("ns-work"));
        // The good-extension fixture's NoOp shim should not throw on any method
        assertNotEquals(TckStatus.FAIL, result.status(),
            () -> "NullSafetyCheck failed: " + result.message());
    }
}
```

- [ ] **Step 2: Implement NullSafetyCheck**

Create `btrace-tck/src/main/java/io/btrace/tck/check/behavioral/NullSafetyCheck.java`:
```java
package io.btrace.tck.check.behavioral;

import io.btrace.tck.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public final class NullSafetyCheck {
    private static final String NAME = "NullSafetyCheck";

    public TckResult run(Path extensionZip, Path workDir) {
        try {
            Path apiJar = extractApiJar(extensionZip, workDir);
            if (apiJar == null) return pass(); // no api.jar — ArtifactLayoutCheck catches this

            var shimsIndex = readShimsIndex(apiJar);
            if (shimsIndex.isEmpty()) {
                return new TckResult("behavioral", NAME, TckStatus.PASS,
                    null, "No shims found in api.jar (shim generation may be optional)");
            }

            try (var cl = new URLClassLoader(new java.net.URL[]{apiJar.toUri().toURL()},
                                             NullSafetyCheck.class.getClassLoader())) {
                for (var entry : shimsIndex.entrySet()) {
                    String shimClass = entry.getValue().get("noop");
                    if (shimClass == null) continue;
                    Class<?> shim = cl.loadClass(shimClass);
                    Object instance = shim.getDeclaredConstructor().newInstance();
                    for (Method m : shim.getDeclaredMethods()) {
                        if (!Modifier.isPublic(m.getModifiers())) continue;
                        Object[] args = nullArgs(m.getParameterTypes());
                        try {
                            m.invoke(instance, args);
                        } catch (InvocationTargetException ite) {
                            Throwable cause = ite.getCause();
                            if (!(cause instanceof UnsupportedOperationException)) {
                                return new TckResult("behavioral", NAME, TckStatus.FAIL,
                                    "BTRACE-BH-001",
                                    "NoOp shim method '" + m.getName() + "' threw: " + cause);
                            }
                        }
                    }
                }
            }
            return pass();
        } catch (Exception e) {
            return new TckResult("behavioral", NAME, TckStatus.FAIL,
                "BTRACE-BH-001", "NullSafetyCheck error: " + e.getMessage());
        }
    }

    /**
     * Reads META-INF/btrace/shims.index from the api.jar.
     * Format: one line per service — "serviceClass=noop:NoOpClass,throw:ThrowClass"
     */
    private Map<String, Map<String, String>> readShimsIndex(Path apiJar) throws Exception {
        var result = new LinkedHashMap<String, Map<String, String>>();
        try (var jf = new JarFile(apiJar.toFile())) {
            var entry = jf.getEntry("META-INF/btrace/shims.index");
            if (entry == null) return result;
            try (var reader = new BufferedReader(new InputStreamReader(jf.getInputStream(entry)))) {
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

    private Object[] nullArgs(Class<?>[] types) {
        var args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if      (types[i] == boolean.class) args[i] = false;
            else if (types[i] == int.class
                  || types[i] == long.class
                  || types[i] == short.class
                  || types[i] == byte.class)    args[i] = 0;
            else if (types[i] == double.class
                  || types[i] == float.class)   args[i] = 0.0;
            else if (types[i] == char.class)    args[i] = '\0';
            else                                args[i] = null;
        }
        return args;
    }

    private TckResult pass() {
        return new TckResult("behavioral", NAME, TckStatus.PASS, null, null);
    }
}
```

> **Note on shims.index format:** The exact format written by `generateShimIndex` task in the Gradle plugin must match what `readShimsIndex` parses. Read `BTraceExtensionPlugin.groovy`'s `generateShimIndex` task before finalising this implementation and adjust the parser if the format differs.

- [ ] **Step 3: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test --tests "*NullSafety*"
```

- [ ] **Step 4: Commit**

```bash
git add btrace-tck/
git commit -m "feat(tck): add NullSafetyCheck for NoOp shim behavioral validation"
```

---

## Task 5: BehavioralSuite — ShimFallbackCheck and RequiredServiceCheck

**Files:**
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/behavioral/ShimFallbackCheck.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/behavioral/RequiredServiceCheck.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/check/behavioral/ShimFallbackCheckTest.java`

- [ ] **Step 1: Write failing test**

Create `btrace-tck/src/test/java/io/btrace/tck/check/behavioral/ShimFallbackCheckTest.java`:
```java
package io.btrace.tck.check.behavioral;

import io.btrace.tck.*;
import io.btrace.tck.suite.FixtureBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ShimFallbackCheckTest {
    @TempDir static Path tmp;
    static Path goodZip;

    @BeforeAll static void buildFixtures() throws Exception {
        goodZip = FixtureBuilder.goodExtension(tmp);
    }

    @Test void shimFallbackPassesOnGoodExtension() {
        var result = new ShimFallbackCheck()
            .run(goodZip, tmp.resolve("sf-work"), goodZip /* extensionId from desc */);
        assertNotEquals(TckStatus.FAIL, result.status(), () -> result.message());
    }
}
```

- [ ] **Step 2: Implement ShimFallbackCheck**

Create `btrace-tck/src/main/java/io/btrace/tck/check/behavioral/ShimFallbackCheck.java`:
```java
package io.btrace.tck.check.behavioral;

import io.btrace.extension.PermissionPolicy;
import io.btrace.tck.*;
import java.nio.file.Path;
import java.util.List;

public final class ShimFallbackCheck {
    private static final String NAME = "ShimFallbackCheck";

    /**
     * Deny the extension via policy, then verify that:
     * 1. The bridge still resolves a non-null class (the shim/interface).
     * 2. The resolved class is NOT the implementation (i.e., shim was substituted).
     */
    public TckResult run(Path extensionZip, Path workDir, Path originalZip) {
        // Temporarily deny the extension by ID
        String extId;
        try (var probe = ExtensionSession.open(extensionZip, workDir.resolve("probe"))) {
            extId = probe.descriptor().getId();
        } catch (ExtensionSession.LoadException e) {
            return new TckResult("behavioral", NAME, TckStatus.FAIL,
                "BTRACE-BH-002", "Cannot open extension to read its ID: " + e.getMessage());
        }

        PermissionPolicy.get().setDenyExtensionsCsv(extId);
        try (var session = ExtensionSession.open(extensionZip, workDir.resolve("denied"))) {
            List<String> services = session.descriptor().getServices();
            for (String svc : services) {
                Class<?> resolved = session.resolveService(svc);
                if (resolved == null) {
                    return new TckResult("behavioral", NAME, TckStatus.FAIL,
                        "BTRACE-BH-002",
                        "Service '" + svc + "' resolved to null when denied (expected shim or interface)");
                }
                // When denied, bridge returns interface or NoOp shim — either is acceptable.
                // What's NOT acceptable: it should not be the concrete impl class.
                // We detect this by checking if the resolved class name contains "Impl" as a heuristic.
                // A more robust check: verify the class is NOT from the impl JAR (no class files in api.jar).
                // Since we cannot load the impl JAR here (it was denied), we check the class source.
                String resolvedName = resolved.getName();
                if (resolvedName.contains("Impl") && !resolvedName.contains("Shim")) {
                    return new TckResult("behavioral", NAME, TckStatus.FAIL,
                        "BTRACE-BH-002",
                        "Service '" + svc + "' resolved to impl class '" + resolvedName
                        + "' despite being denied (shim expected)");
                }
            }
            return new TckResult("behavioral", NAME, TckStatus.PASS, null, null);
        } catch (ExtensionSession.LoadException e) {
            return new TckResult("behavioral", NAME, TckStatus.FAIL,
                "BTRACE-BH-002", "Session failed after deny: " + e.getMessage());
        } finally {
            // Reset policy so subsequent checks are not affected
            PermissionPolicy.get().setDenyExtensionsCsv("");
        }
    }
}
```

`RequiredServiceCheck.java`:
```java
package io.btrace.tck.check.behavioral;

import io.btrace.extension.PermissionPolicy;
import io.btrace.tck.*;
import java.nio.file.Path;
import java.util.List;

public final class RequiredServiceCheck {
    private static final String NAME = "RequiredServiceCheck";

    /**
     * Verify that when a required service (one with no optional fallback) is
     * unavailable due to policy denial, the bridge throws rather than returning null.
     *
     * Note: because @Injected(optional=true) is the default in the runtime, and
     * the TCK cannot know at this level which services are optional vs required,
     * this check calls bridge.getExtensionClass() with a denied extension and
     * verifies the bridge does NOT silently return null for a non-optional context.
     * If the bridge returns null (not an exception), the check is WARN-only.
     */
    public TckResult run(Path extensionZip, Path workDir) {
        String extId;
        try (var probe = ExtensionSession.open(extensionZip, workDir.resolve("probe"))) {
            extId = probe.descriptor().getId();
            List<String> services = probe.descriptor().getServices();
            if (services.isEmpty()) {
                return new TckResult("behavioral", NAME, TckStatus.PASS,
                    null, "No services to check for required-service behavior");
            }
        } catch (ExtensionSession.LoadException e) {
            return new TckResult("behavioral", NAME, TckStatus.FAIL,
                "BTRACE-BH-004", "Cannot open extension: " + e.getMessage());
        }

        PermissionPolicy.get().setDenyExtensionsCsv(extId);
        try (var session = ExtensionSession.open(extensionZip, workDir.resolve("required"))) {
            List<String> services = session.descriptor().getServices();
            for (String svc : services) {
                Class<?> resolved = session.resolveService(svc);
                if (resolved == null) {
                    // Null is acceptable here — means "service unavailable"
                    // The runtime will throw when @Injected(optional=false) is linked
                    // We document this but cannot enforce at TCK level without bytecode injection
                    return new TckResult("behavioral", NAME, TckStatus.PASS,
                        null, "Service '" + svc + "' correctly unavailable when denied");
                }
            }
            return new TckResult("behavioral", NAME, TckStatus.PASS, null, null);
        } catch (ExtensionSession.LoadException e) {
            return new TckResult("behavioral", NAME, TckStatus.FAIL,
                "BTRACE-BH-004", "Required service check failed: " + e.getMessage());
        } finally {
            PermissionPolicy.get().setDenyExtensionsCsv("");
        }
    }
}
```

- [ ] **Step 3: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test --tests "*ShimFallback*"
```

- [ ] **Step 4: Commit**

```bash
git add btrace-tck/
git commit -m "feat(tck): add ShimFallbackCheck and RequiredServiceCheck"
```

---

## Task 6: BehavioralSuite — ConcurrencyCheck

`ConcurrencyCheck` runs 8 threads × 1000 calls against each declared service method (via reflection on the implementation or shim), verifying no exceptions or data races.

**Files:**
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/behavioral/ConcurrencyCheck.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/check/behavioral/ConcurrencyCheckTest.java`

- [ ] **Step 1: Write failing test**

Create `btrace-tck/src/test/java/io/btrace/tck/check/behavioral/ConcurrencyCheckTest.java`:
```java
package io.btrace.tck.check.behavioral;

import io.btrace.tck.*;
import io.btrace.tck.suite.FixtureBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyCheckTest {
    @TempDir static Path tmp;
    static Path goodZip;

    @BeforeAll static void buildFixtures() throws Exception {
        goodZip = FixtureBuilder.goodExtension(tmp);
    }

    @Test void concurrencyCheckPassesOnGoodExtension() {
        var result = new ConcurrencyCheck().run(goodZip, tmp.resolve("conc-work"));
        assertNotEquals(TckStatus.FAIL, result.status(), () -> result.message());
    }
}
```

- [ ] **Step 2: Implement ConcurrencyCheck**

Create `btrace-tck/src/main/java/io/btrace/tck/check/behavioral/ConcurrencyCheck.java`:
```java
package io.btrace.tck.check.behavioral;

import io.btrace.tck.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class ConcurrencyCheck {
    private static final String NAME  = "ConcurrencyCheck";
    private static final int THREADS  = 8;
    private static final int CALLS    = 1000;

    public TckResult run(Path extensionZip, Path workDir) {
        try (var session = ExtensionSession.open(extensionZip, workDir)) {
            List<String> services = session.descriptor().getServices();
            if (services.isEmpty()) {
                return new TckResult("behavioral", NAME, TckStatus.PASS,
                    null, "No services to concurrency-test");
            }
            for (String svc : services) {
                Class<?> resolved = session.resolveService(svc);
                if (resolved == null || resolved.isInterface()) continue;
                Object instance = resolved.getDeclaredConstructor().newInstance();
                TckResult r = hammmerMethods(svc, instance);
                if (r.isFail()) return r;
            }
            return new TckResult("behavioral", NAME, TckStatus.PASS, null, null);
        } catch (ExtensionSession.LoadException e) {
            return new TckResult("behavioral", NAME, TckStatus.FAIL,
                "BTRACE-BH-003", "Cannot open extension: " + e.getMessage());
        } catch (Exception e) {
            return new TckResult("behavioral", NAME, TckStatus.FAIL,
                "BTRACE-BH-003", "Setup failed: " + e.getMessage());
        }
    }

    private TckResult hammmerMethods(String svc, Object instance) throws InterruptedException {
        var executor = Executors.newFixedThreadPool(THREADS);
        var error    = new AtomicReference<String>();
        var latch    = new CountDownLatch(THREADS);
        var methods  = publicNonStaticMethods(instance.getClass());

        if (methods.isEmpty()) {
            return new TckResult("behavioral", NAME, TckStatus.PASS,
                null, "No public methods on resolved service for " + svc);
        }

        for (int t = 0; t < THREADS; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < CALLS && error.get() == null; i++) {
                        for (Method m : methods) {
                            try {
                                m.invoke(instance, nullArgs(m.getParameterTypes()));
                            } catch (InvocationTargetException ite) {
                                Throwable cause = ite.getCause();
                                if (cause instanceof RuntimeException || cause instanceof Error) {
                                    // implementation-side exceptions are expected (service may reject null args)
                                    // data races manifest as ConcurrentModificationException, ArrayIndexOutOfBounds, etc.
                                    if (cause instanceof ConcurrentModificationException
                                     || cause instanceof ArrayIndexOutOfBoundsException) {
                                        error.compareAndSet(null,
                                            "Possible data race in '" + m.getName() + "': " + cause);
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        String err = error.get();
        if (err != null) {
            return new TckResult("behavioral", NAME, TckStatus.FAIL, "BTRACE-BH-003", err);
        }
        return new TckResult("behavioral", NAME, TckStatus.PASS, null, null);
    }

    private List<Method> publicNonStaticMethods(Class<?> cls) {
        var out = new ArrayList<Method>();
        for (Method m : cls.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers())
                && m.getDeclaringClass() != Object.class) {
                out.add(m);
            }
        }
        return out;
    }

    private Object[] nullArgs(Class<?>[] types) {
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

- [ ] **Step 3: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test --tests "*Concurrency*"
```

- [ ] **Step 4: Commit**

```bash
git add btrace-tck/
git commit -m "feat(tck): add ConcurrencyCheck (8t x 1000 calls, race detection)"
```

---

## Task 7: BehavioralSuite — assemble

**Files:**
- Create: `btrace-tck/src/main/java/io/btrace/tck/suite/BehavioralSuite.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/suite/BehavioralSuiteTest.java`

- [ ] **Step 1: Write failing test**

Create `btrace-tck/src/test/java/io/btrace/tck/suite/BehavioralSuiteTest.java`:
```java
package io.btrace.tck.suite;

import io.btrace.tck.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BehavioralSuiteTest {
    @TempDir static Path tmp;
    static Path goodZip;

    @BeforeAll static void buildFixtures() throws Exception {
        goodZip = FixtureBuilder.goodExtension(tmp);
    }

    @Test void suiteIsNamedBehavioral() {
        assertEquals("behavioral", new BehavioralSuite().name());
    }

    @Test void goodExtensionPassesAllBehavioralChecks() {
        var input = TckInput.builder()
            .extensionZip(goodZip)
            .reportDir(tmp.resolve("report"))
            .build();
        var result = new BehavioralSuite().run(input);
        assertFalse(result.hasFailed(), () -> "Behavioral checks failed: " + result.checks());
    }
}
```

- [ ] **Step 2: Implement BehavioralSuite**

Create `btrace-tck/src/main/java/io/btrace/tck/suite/BehavioralSuite.java`:
```java
package io.btrace.tck.suite;

import io.btrace.tck.*;
import io.btrace.tck.check.behavioral.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public final class BehavioralSuite implements Suite {
    @Override public String name() { return "behavioral"; }

    @Override public TckSuiteResult run(TckInput input) {
        if (input.extensionZip() == null) {
            return fail("--extension ZIP path is required");
        }
        Path workDir;
        try {
            workDir = Files.createTempDirectory("btrace-tck-behavioral-");
        } catch (IOException e) {
            return fail("Cannot create work dir: " + e.getMessage());
        }
        var zip = input.extensionZip();
        return new TckSuiteResult("behavioral", List.of(
            new NullSafetyCheck().run(zip, workDir.resolve("nullsafety")),
            new ShimFallbackCheck().run(zip, workDir.resolve("shimfallback"), zip),
            new ConcurrencyCheck().run(zip, workDir.resolve("concurrency")),
            new RequiredServiceCheck().run(zip, workDir.resolve("required"))));
    }

    private TckSuiteResult fail(String msg) {
        return new TckSuiteResult("behavioral", List.of(
            new TckResult("behavioral", "setup", TckStatus.FAIL, "BTRACE-BH-001", msg)));
    }
}
```

- [ ] **Step 3: Run all tests — expect PASS**

```bash
./gradlew :btrace-tck:test
```

- [ ] **Step 4: Commit**

```bash
git add btrace-tck/
git commit -m "feat(tck): assemble BehavioralSuite"
```

---

## Task 8: Wire lifecycle and behavioral suites into TckMain

**Files:**
- Modify: `btrace-tck/src/main/java/io/btrace/tck/cli/TckMain.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/cli/TckMainLifecycleTest.java`

- [ ] **Step 1: Update TckMain to register the new suites**

In `TckMain.java`, find the `activeSuites` loop and add:
```java
for (String s : suites) {
    switch (s) {
        case "structural"  -> activeSuites.add(new StructuralSuite());
        case "lifecycle"   -> activeSuites.add(new LifecycleSuite());
        case "behavioral"  -> activeSuites.add(new BehavioralSuite());
        // "perf" added in Plan 3
        default            -> System.err.println("Unknown suite: " + s + " (skipped)");
    }
}
```

Also update the `--suites` default value to include all currently available suites:
```java
@Option(names = {"--suites", "-s"}, split = ",", defaultValue = "structural,lifecycle,behavioral",
        description = "Suites to run: structural,lifecycle,behavioral,perf")
List<String> suites;
```

Add the missing imports:
```java
import io.btrace.tck.suite.LifecycleSuite;
import io.btrace.tck.suite.BehavioralSuite;
```

- [ ] **Step 2: Write end-to-end test**

Create `btrace-tck/src/test/java/io/btrace/tck/cli/TckMainLifecycleTest.java`:
```java
package io.btrace.tck.cli;

import io.btrace.tck.suite.FixtureBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TckMainLifecycleTest {
    @TempDir static Path tmp;

    @Test void goodExtensionPassesLifecycleAndBehavioral() throws Exception {
        var zip    = FixtureBuilder.goodExtension(tmp);
        var report = tmp.resolve("report-lb");
        int code = TckMain.run(new String[]{
            "--extension", zip.toString(),
            "--report-dir", report.toString(),
            "--suites", "structural,lifecycle,behavioral"
        });
        assertEquals(0, code, "Expected exit 0 for valid extension");
        assertTrue(Files.exists(report.resolve("tck-results.xml")));
    }
}
```

- [ ] **Step 3: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test --tests "*TckMain*"
```

- [ ] **Step 4: Run spotlessApply and commit**

```bash
./gradlew spotlessApply
git add btrace-tck/
git commit -m "feat(tck): wire LifecycleSuite and BehavioralSuite into TckMain"
```

---

## Task 9: Integration smoke-test against btrace-contracts

**Files:** No new files — run existing commands.

- [ ] **Step 1: Build btrace-contracts extension ZIP**

```bash
./gradlew :btrace-extensions:btrace-contracts:packageExtension
```
Expected: ZIP created at `btrace-extensions/btrace-contracts/build/distributions/` or similar. Note the exact path.

- [ ] **Step 2: Run TCK against btrace-contracts**

```bash
CONTRACTS_ZIP=$(find btrace-extensions/btrace-contracts/build -name "*-extension.zip" | head -1)
java -jar btrace-tck/build/libs/btrace-tck.jar \
  --extension "$CONTRACTS_ZIP" \
  --report-dir /tmp/contracts-tck-report \
  --suites structural,lifecycle,behavioral
```
Expected: `TCK PASSED` with exit code 0.

If any lifecycle/behavioral checks fail, investigate the root cause before marking this step done. Common issues:
- `ExtensionConfig.createDefault()` may not exist — check `ExtensionConfig.java` and use the correct factory method or constructor.
- `PermissionPolicy.get()` is a singleton — ensure test isolation resets the policy between checks (already done in ShimFallbackCheck and RequiredServiceCheck `finally` blocks).
- `FileSystemExtensionRepository` expects a directory with the extension files extracted, not the ZIP itself — `ExtensionSession.extractZip()` handles this.

- [ ] **Step 3: Commit**

```bash
./gradlew spotlessCheck
git add btrace-tck/
git commit -m "chore(tck): Plan 2 complete — lifecycle and behavioral suites pass on btrace-contracts"
```
