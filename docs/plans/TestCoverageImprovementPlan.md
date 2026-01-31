# Test Coverage Improvement Plan

**Status:** Proposed
**Created:** 2026-01-31
**Related:** docs/review/Version230ReadinessReview.md
**Target Version:** 2.3.1 or 2.4.0

---

## Overview

This plan addresses the five testing gaps identified in the 2.3.0 readiness review. These were documented as post-release technical debt and should be addressed to improve confidence in future releases.

### Testing Gaps

1. No v2-only integration test suite
2. No `btrace-ext-cli` module tests
3. No oneliner runtime integration test
4. No extension lifecycle integration test
5. No extension loading concurrency test

---

## 1. V2 Protocol Integration Test Suite

### Problem Statement

Unit tests in `btrace-core/src/test/java/org/openjdk/btrace/core/comm/` verify serialization/deserialization of individual commands, but no end-to-end test runs a BTrace script through the v2 protocol and validates the complete client-agent communication flow.

### Proposed Solution

Create a new test class `V2ProtocolIntegrationTest` in `integration-tests` that forces v2 protocol usage and validates round-trip behavior for all command types.

### Implementation

#### 1.1 New Test Class

**File:** `integration-tests/src/test/java/tests/V2ProtocolIntegrationTest.java`

```java
package tests;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that force v2 binary protocol and validate
 * end-to-end client-agent communication.
 */
public class V2ProtocolIntegrationTest extends RuntimeTest {

    @BeforeAll
    public static void classSetup() throws Exception {
        // Force v2 protocol for all tests
        System.setProperty("btrace.comm.forceVersion", "2");
        System.setProperty("btrace.comm.autoNegotiate", "false");
        RuntimeTest.classSetup();
    }

    @AfterAll
    public static void classTeardown() {
        System.clearProperty("btrace.comm.forceVersion");
        System.clearProperty("btrace.comm.autoNegotiate");
    }

    @Test
    public void testMessageCommandV2() throws Exception {
        // Test MessageCommand serialization through v2 protocol
        testDynamic("resources.Main", "btrace/OnMethodTest.java", 14,
            (stdout, stderr, retcode, jfrFile) -> {
                assertTrue(stderr.isEmpty(), "No errors expected");
                // Validate expected probe output arrived correctly
            });
    }

    @Test
    public void testErrorCommandV2() throws Exception {
        // Submit invalid script to trigger ErrorCommand
        // Validate exception class, message, and stack trace preserved
    }

    @Test
    public void testNumberMapCommandV2() throws Exception {
        // Test probe that emits BigInteger/BigDecimal values
        // Validate numeric precision preserved through v2 encoding
    }

    @Test
    public void testGridDataCommandV2() throws Exception {
        // Test probe with histogram/aggregation output
        // Validate HistogramData and column names preserved
    }

    @Test
    public void testLargePayloadCompressionV2() throws Exception {
        // Test probe that generates large output
        // Validate compression works correctly
    }
}
```

#### 1.2 New BTrace Probe Scripts

**File:** `integration-tests/src/test/btrace/protocol/NumberMapTest.java`

```java
@BTrace
public class NumberMapTest {
    @OnMethod(clazz = "resources.Main", method = "callA")
    public static void onCallA() {
        // Emit BigInteger and BigDecimal values
        BTraceUtils.printNumberMap("bigint", new BigInteger("12345678901234567890"));
        BTraceUtils.printNumberMap("bigdec", new BigDecimal("3.141592653589793238"));
    }
}
```

**File:** `integration-tests/src/test/btrace/protocol/GridDataTest.java`

```java
@BTrace
public class GridDataTest {
    private static Aggregation agg = Aggregations.newAggregation(AggregationFunction.QUANTIZE);

    @OnMethod(clazz = "resources.Main", method = "callA")
    public static void onCallA(@Duration long duration) {
        Aggregations.addToAggregation(agg, duration);
    }

    @OnTimer(1000)
    public static void onTimer() {
        Aggregations.printAggregation("latency", agg);
    }
}
```

#### 1.3 Test Validation Helper

**File:** `integration-tests/src/test/java/tests/V2ProtocolValidator.java`

```java
package tests;

/**
 * Validates v2 protocol-specific behaviors in test output.
 */
public class V2ProtocolValidator {

    public static void assertBigIntegerPreserved(String output, String expected) {
        // Validate BigInteger value not truncated to long
    }

    public static void assertBigDecimalPreserved(String output, String expected) {
        // Validate BigDecimal precision maintained
    }

    public static void assertHistogramFormatted(String output) {
        // Validate histogram buckets rendered correctly
    }

    public static void assertErrorDetailPreserved(String output,
            String expectedClass, String expectedMessage) {
        // Validate exception class and message in error output
    }
}
```

### Test Cases

| Test Case | Command Type | Validation |
|-----------|--------------|------------|
| `testMessageCommandV2` | MessageCommand | Basic probe output arrives |
| `testErrorCommandV2` | ErrorCommand | Exception class, message, stack trace |
| `testNumberMapCommandV2` | NumberMapDataCommand | BigInteger/BigDecimal precision |
| `testGridDataCommandV2` | GridDataCommand | HistogramData formatting |
| `testLargePayloadCompressionV2` | MessageCommand | Compression/decompression |
| `testEventCommandV2` | EventCommand | Event name and payload |
| `testStatusCommandV2` | StatusCommand | Probe status reporting |

### Acceptance Criteria

- [ ] All tests pass with `-Dbtrace.comm.forceVersion=2`
- [ ] Tests fail gracefully if v2 negotiation fails
- [ ] Tests verify data integrity, not just command delivery
- [ ] Coverage for all command types that changed in v2

---

## 2. Extension CLI Module Tests

### Problem Statement

The `btrace-ext-cli` module provides CLI commands for extension management but has no test coverage.

### Proposed Solution

Add unit tests for CLI command parsing and execution, plus integration tests for end-to-end extension operations.

### Implementation

#### 2.1 Locate Extension CLI Module

First, identify the module structure:

```
btrace-ext-cli/
  src/main/java/org/openjdk/btrace/cli/ext/
    ExtensionCommands.java      # CLI command handlers
    ExtensionListCommand.java   # List installed extensions
    ExtensionInstallCommand.java # Install extension
    ExtensionRemoveCommand.java  # Remove extension
```

#### 2.2 Unit Tests

**File:** `btrace-ext-cli/src/test/java/org/openjdk/btrace/cli/ext/ExtensionCommandsTest.java`

```java
package org.openjdk.btrace.cli.ext;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionCommandsTest {

    @TempDir
    Path tempDir;

    @Test
    void testListExtensions_EmptyRepository() {
        // Setup empty extension repository
        // Execute list command
        // Validate empty output format
    }

    @Test
    void testListExtensions_WithExtensions() {
        // Setup repository with mock extensions
        // Execute list command
        // Validate extension metadata displayed
    }

    @Test
    void testInstallExtension_ValidJar() {
        // Create mock extension JAR with valid manifest
        // Execute install command
        // Validate JAR copied to repository
        // Validate extension discoverable
    }

    @Test
    void testInstallExtension_InvalidManifest() {
        // Create JAR without required manifest entries
        // Execute install command
        // Validate appropriate error message
    }

    @Test
    void testRemoveExtension_Exists() {
        // Install extension first
        // Execute remove command
        // Validate extension removed from repository
    }

    @Test
    void testRemoveExtension_NotFound() {
        // Execute remove for non-existent extension
        // Validate appropriate error message
    }
}
```

#### 2.3 Integration Tests

**File:** `btrace-ext-cli/src/test/java/org/openjdk/btrace/cli/ext/ExtensionCLIIntegrationTest.java`

```java
package org.openjdk.btrace.cli.ext;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/**
 * End-to-end tests for extension CLI operations.
 */
class ExtensionCLIIntegrationTest {

    @TempDir
    Path tempExtRepo;

    @Test
    void testFullExtensionLifecycle() {
        // 1. List (empty)
        // 2. Install extension
        // 3. List (shows extension)
        // 4. Remove extension
        // 5. List (empty again)
    }

    @Test
    void testInstallFromUrl() {
        // Test installing extension from URL if supported
    }

    @Test
    void testExtensionVersionConflict() {
        // Install v1.0
        // Try to install v1.0 again
        // Validate conflict handling
    }
}
```

### Test Cases

| Test Case | Category | Description |
|-----------|----------|-------------|
| List empty | Unit | No extensions installed |
| List with extensions | Unit | Display extension metadata |
| Install valid | Unit | JAR with correct manifest |
| Install invalid | Unit | Missing manifest entries |
| Install duplicate | Unit | Same extension twice |
| Remove existing | Unit | Successfully remove |
| Remove missing | Unit | Handle gracefully |
| Full lifecycle | Integration | Install -> List -> Remove |

### Acceptance Criteria

- [ ] 100% coverage of CLI command handlers
- [ ] Error messages are user-friendly
- [ ] Exit codes are correct (0 success, non-zero failure)
- [ ] Help text is accurate

---

## 3. Oneliner Runtime Integration Test

### Problem Statement

The oneliner language has comprehensive parser and code generator tests, but no test verifies that generated scripts actually work when attached to a running JVM.

### Proposed Solution

Add integration tests that:
1. Generate BTrace script from oneliner syntax
2. Compile the generated script
3. Attach to a target JVM
4. Validate probe output

### Implementation

#### 3.1 Test Class

**File:** `integration-tests/src/test/java/tests/OnelinerIntegrationTest.java`

```java
package tests;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for oneliner language feature.
 * Validates that oneliner scripts compile and execute correctly.
 */
public class OnelinerIntegrationTest extends RuntimeTest {

    @BeforeAll
    public static void classSetup() throws Exception {
        RuntimeTest.classSetup();
    }

    @Test
    public void testSimpleMethodEntry() throws Exception {
        String oneliner = "probe method:resources.Main#callA { println(\"entered callA\"); }";

        testDynamicOneliner("resources.Main", oneliner, 5,
            (stdout, stderr, retcode, jfrFile) -> {
                assertTrue(stderr.isEmpty(), "No errors: " + stderr);
                assertTrue(stdout.contains("entered callA"),
                    "Probe output missing: " + stdout);
            });
    }

    @Test
    public void testMethodWithArguments() throws Exception {
        String oneliner = "probe method:resources.Main#callWithArgs(String) " +
                         "{ println(\"arg=\" + $1); }";

        testDynamicOneliner("resources.Main", oneliner, 5,
            (stdout, stderr, retcode, jfrFile) -> {
                assertTrue(stdout.contains("arg="), "Argument capture failed");
            });
    }

    @Test
    public void testMethodReturn() throws Exception {
        String oneliner = "probe method:resources.Main#getValue / return " +
                         "{ println(\"returned: \" + $return); }";

        testDynamicOneliner("resources.Main", oneliner, 5,
            (stdout, stderr, retcode, jfrFile) -> {
                assertTrue(stdout.contains("returned:"), "Return capture failed");
            });
    }

    @Test
    public void testTimerProbe() throws Exception {
        String oneliner = "probe timer:500ms { println(\"tick\"); }";

        testDynamicOneliner("resources.Main", oneliner, 3,
            (stdout, stderr, retcode, jfrFile) -> {
                // Should see at least 2 ticks in test duration
                int tickCount = countOccurrences(stdout, "tick");
                assertTrue(tickCount >= 2, "Expected at least 2 ticks, got: " + tickCount);
            });
    }

    @Test
    public void testRegexClassMatch() throws Exception {
        String oneliner = "probe method:/resources\\.Main/#call.* " +
                         "{ println(\"matched: \" + probeName()); }";

        testDynamicOneliner("resources.Main", oneliner, 10,
            (stdout, stderr, retcode, jfrFile) -> {
                assertTrue(stdout.contains("matched: callA"), "callA not matched");
                assertTrue(stdout.contains("matched: callB"), "callB not matched");
            });
    }

    @ParameterizedTest
    @CsvSource({
        "method:java.io.FileInputStream#<init>, File opened",
        "method:java.lang.Thread#start, Thread started",
        "method:java.net.Socket#connect, Socket connecting"
    })
    public void testJdkClassInstrumentation(String probe, String expectedOutput)
            throws Exception {
        String oneliner = String.format("probe %s { println(\"%s\"); }",
                                        probe, expectedOutput);
        // ... test implementation
    }

    @Test
    public void testSyntaxError() throws Exception {
        String invalidOneliner = "probe invalid syntax here";

        // Validate compilation fails with helpful error
        assertThrows(CompilationException.class, () -> {
            compileOneliner(invalidOneliner);
        });
    }

    @Test
    public void testUnsafeOperation() throws Exception {
        String unsafeOneliner = "probe method:resources.Main#callA " +
                               "{ new Object(); }"; // allocation not allowed

        // Validate verifier rejects unsafe code
        assertThrows(VerifierException.class, () -> {
            compileOneliner(unsafeOneliner);
        });
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
```

#### 3.2 Test Target Application Enhancement

**File:** `integration-tests/src/test/java/resources/Main.java` (additions)

```java
// Add methods for oneliner testing
public String callWithArgs(String arg) {
    return "processed: " + arg;
}

public int getValue() {
    return 42;
}

public void callB() {
    // Another method for regex matching tests
}
```

### Test Cases

| Test Case | Oneliner Feature | Validation |
|-----------|------------------|------------|
| Simple entry | `probe method:Class#method` | Probe fires on entry |
| With arguments | `$1`, `$2`, etc. | Argument capture works |
| Return value | `$return` | Return value captured |
| Timer | `probe timer:500ms` | Timer fires periodically |
| Regex class | `/pattern/` | Pattern matching works |
| JDK classes | `java.io.*` | JDK instrumentation works |
| Syntax error | Invalid syntax | Helpful error message |
| Unsafe code | `new Object()` | Verifier rejects |

### Acceptance Criteria

- [ ] Tests cover all oneliner language features documented in OnelinerGuide.md
- [ ] Error messages for invalid oneliners are helpful
- [ ] Both dynamic attach and startup modes work
- [ ] Performance overhead is acceptable (no timeout failures)

---

## 4. Extension Lifecycle Integration Test

### Problem Statement

No test verifies that extensions are properly initialized when scripts load and cleaned up when scripts detach or the agent shuts down.

### Proposed Solution

Create integration tests that verify the complete extension lifecycle:
1. Extension loading and initialization
2. Service injection into scripts
3. Service usage during probe execution
4. Extension cleanup on script detach
5. Extension cleanup on agent shutdown

### Implementation

#### 4.1 Test Extension with Observable Lifecycle

**File:** `integration-tests/src/test/java/extensions/LifecycleTrackingExtension.java`

```java
package extensions;

import org.openjdk.btrace.core.extensions.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;

/**
 * Extension that tracks lifecycle events for testing.
 * Writes lifecycle events to a file for external validation.
 */
public class LifecycleTrackingExtension extends Extension {

    private static final AtomicInteger initCount = new AtomicInteger(0);
    private static final AtomicInteger closeCount = new AtomicInteger(0);
    private File lifecycleLog;

    @Override
    public void initialize(ExtensionContext context) {
        super.initialize(context);
        initCount.incrementAndGet();
        lifecycleLog = new File(System.getProperty("lifecycle.log", "/tmp/ext-lifecycle.log"));
        log("INIT:" + System.currentTimeMillis());
    }

    @Override
    public void close() {
        closeCount.incrementAndGet();
        log("CLOSE:" + System.currentTimeMillis());
        super.close();
    }

    public void serviceCall() {
        log("SERVICE_CALL:" + System.currentTimeMillis());
    }

    private void log(String message) {
        try (FileWriter fw = new FileWriter(lifecycleLog, true)) {
            fw.write(message + "\n");
        } catch (IOException e) {
            // Ignore for test purposes
        }
    }

    // For test assertions
    public static int getInitCount() { return initCount.get(); }
    public static int getCloseCount() { return closeCount.get(); }
    public static void resetCounts() {
        initCount.set(0);
        closeCount.set(0);
    }
}
```

#### 4.2 Integration Test

**File:** `integration-tests/src/test/java/tests/ExtensionLifecycleIntegrationTest.java`

```java
package tests;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests extension lifecycle: initialization, usage, and cleanup.
 */
public class ExtensionLifecycleIntegrationTest extends RuntimeTest {

    @TempDir
    Path tempDir;

    private Path lifecycleLog;

    @BeforeAll
    public static void classSetup() throws Exception {
        RuntimeTest.classSetup();
    }

    @BeforeEach
    public void setup() throws Exception {
        super.reset();
        lifecycleLog = tempDir.resolve("ext-lifecycle.log");
        System.setProperty("lifecycle.log", lifecycleLog.toString());
    }

    @Test
    public void testExtensionInitializedOnScriptLoad() throws Exception {
        testDynamic("resources.Main", "btrace/ExtensionLifecycleTest.java", 5,
            (stdout, stderr, retcode, jfrFile) -> {
                assertTrue(stderr.isEmpty(), "No errors expected");

                // Verify lifecycle log shows initialization
                List<String> events = Files.readAllLines(lifecycleLog);
                assertTrue(events.stream().anyMatch(e -> e.startsWith("INIT:")),
                    "Extension should be initialized");
            });
    }

    @Test
    public void testExtensionServiceInjection() throws Exception {
        testDynamic("resources.Main", "btrace/ExtensionServiceTest.java", 10,
            (stdout, stderr, retcode, jfrFile) -> {
                // Verify service was called
                List<String> events = Files.readAllLines(lifecycleLog);
                long serviceCalls = events.stream()
                    .filter(e -> e.startsWith("SERVICE_CALL:"))
                    .count();
                assertTrue(serviceCalls > 0, "Service should be called");
            });
    }

    @Test
    public void testExtensionClosedOnScriptDetach() throws Exception {
        // This requires programmatic control of script attachment/detachment
        TestApp app = launchTestApp("resources.Main");
        try {
            // Attach script
            attach(app.pid, "btrace/ExtensionLifecycleTest.java", 5, null);

            // Detach script (send exit command)
            detachScript(app.pid);

            // Wait for cleanup
            Thread.sleep(1000);

            // Verify close was called
            List<String> events = Files.readAllLines(lifecycleLog);
            assertTrue(events.stream().anyMatch(e -> e.startsWith("CLOSE:")),
                "Extension should be closed on detach");
        } finally {
            app.stop();
        }
    }

    @Test
    public void testExtensionClosedOnAgentShutdown() throws Exception {
        TestApp app = launchTestApp("resources.Main");
        try {
            attach(app.pid, "btrace/ExtensionLifecycleTest.java", 5, null);

            // Stop the target app (triggers agent shutdown)
            app.stop();

            // Wait for cleanup
            Thread.sleep(1000);

            // Verify close was called
            List<String> events = Files.readAllLines(lifecycleLog);
            assertTrue(events.stream().anyMatch(e -> e.startsWith("CLOSE:")),
                "Extension should be closed on shutdown");
        } finally {
            if (app.isRunning()) {
                app.stop();
            }
        }
    }

    @Test
    public void testMultipleScriptsShareExtension() throws Exception {
        TestApp app = launchTestApp("resources.Main");
        try {
            // Attach two scripts that use the same extension
            attach(app.pid, "btrace/ExtensionLifecycleTest.java", 5, null);
            attach(app.pid, "btrace/ExtensionServiceTest.java", 5, null);

            // Extension should be initialized only once
            List<String> events = Files.readAllLines(lifecycleLog);
            long initCount = events.stream()
                .filter(e -> e.startsWith("INIT:"))
                .count();
            assertEquals(1, initCount, "Extension should be initialized once");
        } finally {
            app.stop();
        }
    }
}
```

#### 4.3 BTrace Test Scripts

**File:** `integration-tests/src/test/btrace/ExtensionServiceTest.java`

```java
import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.extensions.Injected;
import extensions.LifecycleTrackingExtension;

@BTrace
public class ExtensionServiceTest {

    @Injected
    private static LifecycleTrackingExtension ext;

    @OnMethod(clazz = "resources.Main", method = "callA")
    public static void onCallA() {
        ext.serviceCall();
    }
}
```

### Test Cases

| Test Case | Lifecycle Event | Validation |
|-----------|-----------------|------------|
| Init on load | `initialize()` | Called when script loads |
| Service injection | `@Injected` | Service available in probe |
| Close on detach | `close()` | Called when script detaches |
| Close on shutdown | `close()` | Called when agent stops |
| Shared extension | Single instance | Multiple scripts share one |
| Init order | Dependencies | Extensions init in order |

### Acceptance Criteria

- [ ] Extensions initialized before any probe fires
- [ ] `@Injected` services are non-null in probes
- [ ] `close()` always called (detach, shutdown, error)
- [ ] Extensions are singleton per agent
- [ ] Resource leaks detected by lifecycle tracking

---

## 5. Extension Loading Concurrency Test

### Problem Statement

No test exercises concurrent extension loading scenarios to verify thread-safety of the extension loader.

### Proposed Solution

Expand the existing `ExtensionLoaderImplConcurrencyTest` and add integration-level concurrency tests.

### Implementation

#### 5.1 Enhanced Unit Test

**File:** `btrace-extension/src/test/java/org/openjdk/btrace/extension/ExtensionLoaderConcurrencyTest.java`

```java
package org.openjdk.btrace.extension;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive concurrency tests for extension loading.
 */
class ExtensionLoaderConcurrencyTest {

    @TempDir
    Path tempDir;

    private ExtensionLoaderImpl loader;
    private ExecutorService executor;

    @BeforeEach
    void setup() {
        loader = new ExtensionLoaderImpl(/* mock instrumentation */);
        executor = Executors.newFixedThreadPool(16);
    }

    @AfterEach
    void teardown() {
        executor.shutdownNow();
    }

    @Test
    void testConcurrentLoadSameExtension() throws Exception {
        // Create extension JAR
        Path extJar = createMockExtensionJar("test-ext", "1.0");
        ExtensionDescriptor desc = createDescriptor("test-ext", extJar);

        AtomicInteger loadCount = new AtomicInteger(0);
        int numThreads = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        // Submit concurrent load requests
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    loader.load(desc);
                    loadCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Load failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));

        // All loads should succeed
        assertEquals(numThreads, loadCount.get());

        // Extension should be loaded exactly once
        // (verified by instrumentation callback count)
    }

    @Test
    void testConcurrentLoadDifferentExtensions() throws Exception {
        int numExtensions = 20;
        List<ExtensionDescriptor> descriptors = new ArrayList<>();

        for (int i = 0; i < numExtensions; i++) {
            Path jar = createMockExtensionJar("ext-" + i, "1.0");
            descriptors.add(createDescriptor("ext-" + i, jar));
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numExtensions * 10);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Each extension loaded by 10 threads
        for (ExtensionDescriptor desc : descriptors) {
            for (int j = 0; j < 10; j++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        loader.load(desc);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(60, TimeUnit.SECONDS));

        assertEquals(numExtensions * 10, successCount.get(), "All loads should succeed");
        assertEquals(0, errorCount.get(), "No errors expected");
    }

    @Test
    void testLoadWhileUnloading() throws Exception {
        // Test race between load and potential unload operations
        // (if unload is ever supported)
    }

    @Test
    void testConcurrentIsLoadedCheck() throws Exception {
        Path extJar = createMockExtensionJar("check-ext", "1.0");
        ExtensionDescriptor desc = createDescriptor("check-ext", extJar);

        // Load extension
        loader.load(desc);

        // Concurrent isLoaded checks during potential reload
        int numChecks = 1000;
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < numChecks; i++) {
            futures.add(executor.submit(() -> desc.isLoaded()));
        }

        // All checks should return consistent result
        for (Future<Boolean> f : futures) {
            assertTrue(f.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void testDeadlockPrevention() throws Exception {
        // Create extensions with circular dependency risk
        // Verify no deadlock occurs during concurrent loading

        Path extA = createMockExtensionJar("ext-a", "1.0");
        Path extB = createMockExtensionJar("ext-b", "1.0");

        ExtensionDescriptor descA = createDescriptor("ext-a", extA);
        ExtensionDescriptor descB = createDescriptor("ext-b", extB);

        CountDownLatch done = new CountDownLatch(2);

        executor.submit(() -> {
            try {
                loader.load(descA);
                loader.load(descB);
            } finally {
                done.countDown();
            }
        });

        executor.submit(() -> {
            try {
                loader.load(descB);
                loader.load(descA);
            } finally {
                done.countDown();
            }
        });

        // Should complete without deadlock
        assertTrue(done.await(10, TimeUnit.SECONDS),
            "Should complete without deadlock");
    }

    // Helper methods
    private Path createMockExtensionJar(String name, String version) {
        // Create JAR with required manifest entries
        return tempDir.resolve(name + "-" + version + ".jar");
    }

    private ExtensionDescriptor createDescriptor(String id, Path jar) {
        // Create descriptor with JAR reference
        return new ExtensionDescriptor(id, jar);
    }
}
```

#### 5.2 Integration Test

**File:** `integration-tests/src/test/java/tests/ExtensionConcurrencyIntegrationTest.java`

```java
package tests;

import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for concurrent extension loading in running agent.
 */
public class ExtensionConcurrencyIntegrationTest extends RuntimeTest {

    @BeforeAll
    public static void classSetup() throws Exception {
        RuntimeTest.classSetup();
    }

    @Test
    public void testConcurrentScriptAttachWithExtensions() throws Exception {
        TestApp app = launchTestApp("resources.Main");
        ExecutorService executor = Executors.newFixedThreadPool(10);

        try {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(10);
            AtomicInteger successCount = new AtomicInteger(0);

            // Attach 10 scripts concurrently, all using extensions
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        attach(app.pid, "btrace/ExtensionLifecycleTest.java",
                               5, null);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // Log but don't fail - some may timeout
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(60, TimeUnit.SECONDS));

            // At least some should succeed
            assertTrue(successCount.get() > 0,
                "At least one attach should succeed");

        } finally {
            executor.shutdownNow();
            app.stop();
        }
    }
}
```

### Test Cases

| Test Case | Scenario | Expected Behavior |
|-----------|----------|-------------------|
| Same extension | 100 threads load same | Exactly 1 actual load |
| Different extensions | 20 ext x 10 threads | All succeed, no conflicts |
| isLoaded check | Concurrent reads | Consistent results |
| Deadlock prevention | Circular load order | No deadlock |
| Live agent | Concurrent attaches | Extensions shared properly |

### Acceptance Criteria

- [ ] No deadlocks under any load pattern
- [ ] Extension loaded exactly once regardless of concurrent requests
- [ ] `isLoaded()` returns consistent results
- [ ] No race conditions in classloader setup
- [ ] Memory usage stable (no duplicate extensions)

---

## Implementation Timeline

### Phase 1: Infrastructure (Week 1)
- [ ] Create test extension with lifecycle tracking
- [ ] Add helper methods to RuntimeTest for detach operations
- [ ] Set up extension CLI test module structure

### Phase 2: Unit Tests (Week 2)
- [ ] Extension CLI unit tests
- [ ] Enhanced extension concurrency unit tests
- [ ] Oneliner error case unit tests

### Phase 3: Integration Tests (Week 3-4)
- [ ] V2 protocol integration tests
- [ ] Oneliner runtime integration tests
- [ ] Extension lifecycle integration tests
- [ ] Extension concurrency integration tests

### Phase 4: Documentation & Cleanup (Week 5)
- [ ] Update test documentation
- [ ] Add CI configuration for new tests
- [ ] Code review and refinement

---

## Dependencies

- JUnit 5.x (already in use)
- `@TempDir` for isolated test directories
- Mock instrumentation for unit tests
- Full distribution build for integration tests

---

## Success Metrics

1. **Coverage**: All 5 testing gaps addressed with passing tests
2. **Reliability**: Tests pass consistently (no flaky tests)
3. **Performance**: Integration tests complete within 5 minutes
4. **Maintenance**: Tests are self-documenting and easy to update

---

## References

- `integration-tests/src/test/java/tests/RuntimeTest.java` - Base test infrastructure
- `btrace-core/src/test/java/org/openjdk/btrace/core/comm/` - Protocol tests
- `btrace-extension/src/test/java/` - Existing extension tests
- `docs/review/Version230ReadinessReview.md` - Original findings
