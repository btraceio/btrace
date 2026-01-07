# BTrace Extension Development Guide

## Overview

BTrace extensions allow you to provide reusable services that can be injected into BTrace scripts. This guide covers how to create, structure, and package BTrace extensions.

For concrete API authoring rules enforced at build time and how optional services are handled, see `docs/ExtensionInterfaceRules.md`.

## Extension Architecture

### Classloader Isolation

Extensions are loaded in isolated classloaders to prevent classpath conflicts:

```
Bootstrap ClassLoader
├── JRE classes
├── btrace-boot.jar (BTrace core + extension APIs)
└── Extension ClassLoaders (isolated)
    ├── Extension 1 (e.g., btrace-metrics)
    ├── Extension 2 (e.g., btrace-statsd)
    └── Extension N (your custom extension)

Script ClassLoader (parent=null)
├── Script classes
└── Accesses extensions via invokedynamic bridge
```

### API/Implementation Split

**CRITICAL REQUIREMENT:** Extensions MUST be split into two modules:

1. **API Module** (`yourextension-api`)
   - Contains interfaces and abstract classes
   - NO external dependencies (or only JDK dependencies)
   - Added to `btrace-boot.jar` (bootstrap classloader)
   - Scripts reference these types in their bytecode

2. **Implementation Module** (`yourextension`)
   - Contains concrete implementations
   - Can have external dependencies (will be shaded)
   - Loaded in isolated extension classloader
   - Packaged as shadow JAR for distribution

**Why this split is required:**
- Script bytecode contains type references (method signatures, local variables, etc.)
- These type references must be resolvable by the script classloader
- Scripts can't see extension classloader classes
- Solution: Put API types in bootstrap, implementations in extension classloader
- At runtime, invokedynamic provides implementation instances of API interfaces

## Module Structure

### API Module: `yourextension-api`

```
yourextension-api/
├── build.gradle
└── src/main/java/
    └── org/openjdk/btrace/yourextension/
        ├── YourService.java          (interface)
        ├── YourMetric.java           (interface)
        ├── YourSnapshot.java         (interface)
        └── YourConfig.java           (concrete class - constants only)
```

**build.gradle:**
```gradle
apply plugin: 'java'

dependencies {
    // Should be empty unless you want to shade them and expose their types
}

compileJava {
    sourceCompatibility = 8
    targetCompatibility = 8
}
```

**Key Rules for API Module:**
- ✅ Only interfaces and abstract classes (except config/constants)
- ✅ Only primitive types, JDK types, and other API types in signatures
- ❌ NO external library dependencies (HdrHistogram, etc.)
- ❌ NO implementation details
- ❌ NO static methods that create instances (use service interface)

### Implementation Module: `yourextension`

```
yourextension/
├── build.gradle
└── src/main/java/
    └── org/openjdk/btrace/yourextension/
        ├── YourServiceImpl.java      (implements YourService)
        ├── YourMetricImpl.java       (implements YourMetric)
        ├── YourSnapshotImpl.java     (implements YourSnapshot)
        └── internal/
            └── ...                   (private implementation classes)
```

**build.gradle:**
```gradle
plugins {
    id 'java'
    alias(libs.plugins.shadow)
}

java {
    sourceCompatibility = 8
    targetCompatibility = 8
}

compileJava {
    javaCompiler = javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    // API dependency (exposed to consumers)
    api project(':yourextension-api')

    // BTrace dependencies
    implementation project(':btrace-core')

    // External libraries (will be shaded)
    implementation 'com.example:library:1.0.0'

    testImplementation libs.junit.jupiter
}

test {
    useJUnitPlatform()
}

// Shadow JAR to relocate dependencies and avoid classpath conflicts
shadowJar {
    archiveClassifier.set('')

    // Relocate external dependencies to avoid conflicts
    relocate 'com.example.library', 'org.openjdk.btrace.yourextension.shaded.example'

    // Only include external dependencies, not BTrace modules
    dependencies {
        include(dependency('com.example:library'))
    }

    // Minimize JAR by removing unused classes
    minimize()

    // BTrace extension metadata in MANIFEST.MF
    manifest {
        attributes(
            'BTrace-Extension-Id': 'btrace-yourextension',
            'BTrace-Extension-Version': project.version,
            'BTrace-Extension-Name': 'Your Extension Name',
            'BTrace-Extension-Description': 'Description of what your extension does',
            'BTrace-API-Version': '2.3+',
            'BTrace-Java-Version': '8+',
            'BTrace-Extension-Services': 'org.openjdk.btrace.yourextension.YourServiceImpl',
            'BTrace-Shaded-Packages': 'com.example.library->org.openjdk.btrace.yourextension.shaded.example'
        )
    }
}

// Use shadow JAR as main artifact
jar {
    enabled = false
}

// Replace the default jar artifact with shadowJar
configurations {
    apiElements.outgoing.artifacts.clear()
    apiElements.outgoing.artifact(shadowJar)
    runtimeElements.outgoing.artifacts.clear()
    runtimeElements.outgoing.artifact(shadowJar)
}

artifacts {
    archives shadowJar
}
```

## Service Interface

Annotate injectable API interfaces with `@ServiceDescriptor`. The Gradle plugin auto-detects these and writes them into the API JAR manifest (no need to list services manually).

You can also declare service-level permissions within `@ServiceDescriptor`.

**API Interface:**
```java
package org.openjdk.btrace.yourextension;

import org.openjdk.btrace.core.extensions.ServiceDescriptor;
import org.openjdk.btrace.core.extensions.Permission;

@ServiceDescriptor(permissions = { Permission.THREADS })
public interface YourService {
    YourMetric createMetric(String name);
    void reset();
}
```

**Implementation (Extends Extension):**
```java
package org.openjdk.btrace.yourextension;

import org.openjdk.btrace.core.extensions.Extension;

public final class YourServiceImpl extends Extension implements YourService {

    public YourServiceImpl() {
        // no-arg constructor required
    }

    @Override
    public YourMetric createMetric(String name) {
        return new YourMetricImpl(name);
    }

    @Override
    public void reset() {
        // Implementation
    }
}
```

// No separate "simple" vs "runtime" service split; the runtime provides
// an ExtensionContext via initialize(). Use getContext() inside methods when needed.

## Extension Metadata (MANIFEST.MF)

### Required Attributes

| Attribute | Description | Example |
|-----------|-------------|---------|
| `BTrace-Extension-Id` | Unique identifier (kebab-case) | `btrace-metrics` |
| `BTrace-Extension-Version` | Extension version | `2.3.0-SNAPSHOT` |
| `BTrace-Extension-Name` | Human-readable name | `BTrace Metrics` |
| `BTrace-Extension-Description` | What the extension does | `High-performance metrics...` |
| `BTrace-API-Version` | Minimum BTrace version | `2.3+` |
| `BTrace-Java-Version` | Minimum Java version | `8+` |
| `BTrace-Extension-Services` | Comma-separated service interface names | `org.openjdk.btrace.yourextension.YourService` |

### Optional Attributes

| Attribute | Description | Example |
|-----------|-------------|---------|
| `BTrace-Shaded-Packages` | Relocated packages (for diagnostics) | `com.foo->org.openjdk.btrace.ext.shaded.foo` |

## Using Extensions in Scripts

### Injecting Services

```java
package btrace;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Injected;
import org.openjdk.btrace.core.annotations.OnMethod;
// @Injected has no parameters in the new model.
import org.openjdk.btrace.yourextension.YourService;
import org.openjdk.btrace.yourextension.YourMetric;

@BTrace
public class MyScript {

    // Preferred: type-only injection. The bridge auto-detects how to construct it.
    @Injected
    private static YourService service;

    // You can inject any extension service interface the same way:

    @OnMethod(clazz = "com.example.App", method = "doWork")
    public static void onDoWork() {
        // Service is automatically initialized via invokedynamic
        YourMetric metric = service.createMetric("work");
        // Use metric...
    }
}
```

## Extension Discovery and Loading

### Discovery Mechanism

Extensions are discovered from two locations:

1. **Built-in extensions:** `$BTRACE_HOME/extensions/*.jar`
2. **User extensions:** `~/.btrace/extensions/*.jar`

### Extension Configuration

Located at `$BTRACE_HOME/conf/extensions.conf`:

```hocon
# BTrace Extension Configuration

# Auto-load extensions on agent startup
autoload = true

# Extension repositories (searched in order)
repositories = [
  "${btrace.home}/extensions",
  "${user.home}/.btrace/extensions"
]
```

### Loading Process

1. Agent starts, initializes extension system
2. ExtensionLoader scans repository directories for JAR files
3. Reads MANIFEST.MF from each JAR
4. Creates ExtensionDescriptor for each valid extension
5. Extensions loaded on-demand when first @Injected field is accessed
6. ExtensionClassLoader created for each extension (isolated)
7. Service instances created via invokedynamic bridge

## Declaring Required Permissions

The build plugin scans your implementation JAR to infer required permissions (e.g., NETWORK,
FILE_WRITE, THREADS) and writes them into the API JAR manifest as `BTrace-Extension-Permissions`.

Override or add permissions in `build.gradle`:

```gradle
plugins {
  id 'org.openjdk.btrace.extension'
  // Required: Shadow plugin for shading the impl JAR
  id 'com.github.johnrengelman.shadow' version '8.1.1'
}

btraceExtension {
  // Optional: disable scanning and declare explicitly
  // scanPermissions = false

  // Optional: add or override permissions detected by scanner
  requiredPermissions = [ 'NETWORK', 'THREADS' ]
}
```

At runtime, the agent reads `BTrace-Extension-Permissions` from the manifest and can validate
them against the probe’s granted permissions.
annotations you put on API types.

## Dependency Management

### Shading External Dependencies

**Always shade external dependencies** to avoid conflicts:

```gradle
shadowJar {
    // Relocate packages
    relocate 'org.HdrHistogram', 'org.openjdk.btrace.metrics.shaded.hdrhistogram'
    relocate 'com.google.common', 'org.openjdk.btrace.metrics.shaded.guava'

    // Only include specific dependencies
    dependencies {
        include(dependency('org.hdrhistogram:HdrHistogram'))
        include(dependency('com.google.guava:guava'))
    }

    // Minimize - removes unused classes
    minimize()
}
```

### Avoiding BTrace Module Dependencies

```gradle
dependencies {
    // ❌ DON'T shade BTrace modules
    implementation project(':btrace-core')  // NOT shaded

    // ✅ DO shade external libraries
    implementation 'com.example:library:1.0'  // WILL be shaded
}

shadowJar {
    dependencies {
        // Only include external dependencies
        include(dependency('com.example:library'))
    }
}
```

## Best Practices

### Performance

1. **Zero-allocation hot paths:** Use primitives, avoid boxing
2. **Lock-free when possible:** AtomicLong, LongAdder for counters
3. **Lazy initialization:** Create objects only when needed
4. **Thread-safe:** Extensions may be called from multiple threads

### API Design

1. **Immutable snapshots:** Return immutable objects for queries
2. **Fluent interfaces:** Support method chaining where appropriate
3. **Clear naming:** Use domain-specific terminology
4. **Minimal API surface:** Only expose what's necessary

### Implementation

1. **Package-private internals:** Hide implementation details
2. **Final classes:** Make implementation classes final
3. **Defensive copying:** When accepting/returning mutable objects
4. **Clear documentation:** Javadoc on all public APIs

## Testing Extensions

### Unit Tests

```java
public class YourServiceImplTest {
    private YourService service;

    @BeforeEach
    void setUp() {
        service = new YourServiceImpl();
    }

    @Test
    void testCreateMetric() {
        YourMetric metric = service.createMetric("test");
        assertNotNull(metric);
        assertEquals("test", metric.getName());
    }
}
```

### Integration Tests

Test with actual BTrace scripts in `integration-tests` module:

```java
// integration-tests/src/test/btrace/YourExtensionTest.java
@BTrace
public class YourExtensionTest {
    @Injected
    private static YourService service;

    @OnMethod(clazz = "resources.Main", method = "main")
    public static void onMain() {
        YourMetric metric = service.createMetric("test");
        BTraceUtils.println("Metric created: " + metric.getName());
    }
}
```

## Distribution

### Adding to Boot JAR

To make your API available to scripts, add it to `btrace-boot.jar`:

**In `btrace-dist/build.gradle`:**

```gradle
task bootJar(type: ShadowJar) {
    // ...existing configuration...

    dependencies {
        // Add your API module
        include(dependency(":btrace-yourextension-api"))
    }
}
```

### Packaging Extension JAR

The shadow JAR is your distribution artifact:

```
btrace-yourextension-2.3.0.jar
├── META-INF/
│   └── MANIFEST.MF (with BTrace-Extension-* attributes)
├── org/openjdk/btrace/yourextension/
│   ├── YourServiceImpl.class
│   └── ...
└── org/openjdk/btrace/yourextension/shaded/
    └── ... (relocated dependencies)
```

### Installation

Users install by copying to extensions directory:

```bash
# System-wide
cp btrace-yourextension-2.3.0.jar $BTRACE_HOME/extensions/

# User-specific
mkdir -p ~/.btrace/extensions
cp btrace-yourextension-2.3.0.jar ~/.btrace/extensions/
```

## Checklist for Creating Extensions

- [ ] Create API module (`btrace-yourextension-api`)
  - [ ] Define service interface
  - [ ] Define metric/data interfaces
  - [ ] Define configuration classes (if needed)
  - [ ] No external dependencies
  - [ ] All public types are interfaces/abstracts

- [ ] Create implementation module (`btrace-yourextension`)
  - [ ] Service impl extends Extension
  - [ ] All impls implement API interfaces
  - [ ] No-arg constructor; acquire runtime resources in initialize(ExtensionContext)
  - [ ] build.gradle with shadow plugin
  - [ ] Relocate all external dependencies
  - [ ] MANIFEST.MF with BTrace-Extension-* attributes

- [ ] Add API to boot JAR
  - [ ] Update btrace-dist/build.gradle bootJar task

- [ ] Testing
  - [ ] Unit tests for service logic
  - [ ] Integration test with BTrace script
  - [ ] Test on Java 8, 11, 17+

- [ ] Documentation
  - [ ] README with usage examples
  - [ ] Javadoc on all public APIs
  - [ ] Performance characteristics documented

## Example: Minimal Extension

See `btrace-metrics` in the BTrace repository for a complete, production-quality example demonstrating all these concepts.

## Troubleshooting

### ClassNotFoundException at Runtime

**Problem:** Script references extension type but gets ClassNotFoundException

**Cause:** Extension type not in API module (not in boot JAR)

**Solution:** Move type to API module as interface

### NoSuchMethodError

**Problem:** Method exists in API but not in implementation

**Cause:** Implementation doesn't fully implement API interface

**Solution:** Ensure implementation class implements all API methods

### Extension Not Loaded

**Problem:** Extension JAR present but service not available

**Cause:** Missing or incorrect MANIFEST.MF attributes

**Solution:** Verify `BTrace-Extension-Services` points to correct implementation class

### Dependency Conflicts

**Problem:** ClassCastException or LinkageError with library classes

**Cause:** Extension dependency conflicts with application or other extension

**Solution:** Shade and relocate all external dependencies

## Summary

Creating a BTrace extension requires:

1. **API/Impl split** - API in boot JAR, impl in extension JAR
2. **Proper service class** - Extend Extension
3. **Shadow JAR** - Relocate dependencies to avoid conflicts
4. **Extension metadata** - MANIFEST.MF with BTrace-Extension-* attributes
5. **Testing** - Both unit and integration tests
6. **Distribution** - Copy shadow JAR to extensions directory

Follow these guidelines to create extensions that are performant, isolated, and easy to use in BTrace scripts.
Note: You can optionally document your extension at the package level by placing `@ExtensionDescriptor` in `package-info.java` of your API package. The Gradle plugin still emits the manifest as the single source of truth.
Optional package-level descriptor
- Place `@ExtensionDescriptor` in `package-info.java` of your API package to document name/version/description and extension-level permissions, for example:

```java
@ExtensionDescriptor(
  name = "btrace-metrics",
  version = "1.0",
  description = "High-performance metrics",
  permissions = { Permission.THREADS }
)
package org.openjdk.btrace.metrics;

import org.openjdk.btrace.core.extensions.ExtensionDescriptor;
import org.openjdk.btrace.core.extensions.Permission;
```

The Gradle plugin still writes the manifest as the single source of truth; these annotations assist tooling and can be inspected at runtime.

## Descriptor-Based Permissions

- Define permissions declaratively in descriptors:
  - Package-level: `@ExtensionDescriptor(permissions = { Permission.* })` in your API package’s `package-info.java` for extension-wide permissions.
  - Service-level: `@ServiceDescriptor(permissions = { Permission.* })` on each injectable service interface.
- The Gradle plugin validates and merges these with scanned implementation permissions and writes the final set into `BTrace-Extension-Permissions` in the API JAR manifest.
- The verifier and runtime consult the manifest to enforce permissions.

## Builder-Based Configuration (Recommended)

- Probes cannot construct arbitrary objects (no `new`). Configuration objects should be created behind the service interface.
- Provide builders or factories from the injected service, and accept the built config back on service methods.

Example pattern:
```java
// API (service)
@ServiceDescriptor(permissions = { Permission.THREADS })
public interface MetricsService {
  HistogramConfigBuilder newHistogramConfig();
  HistogramMetric histogram(String name, HistogramConfig cfg);
}

// API (builder + config handles)
public interface HistogramConfig {}
public interface HistogramConfigBuilder {
  HistogramConfigBuilder lowestDiscernibleValue(long value);
  HistogramConfigBuilder highestTrackableValue(long value);
  HistogramConfigBuilder significantDigits(int digits);
  HistogramConfig build();
}

// Probe (no `new`):
@BTrace
class HistoProbe {
  @Injected static MetricsService metrics;

  static final HistogramConfig cfg;
  static {
    // Alternatively, use a convenience creator like histogramMicros/histogramMillis
  }

  @OnMethod(clazz="com.example.Foo", method="bar", location=@Location(Kind.RETURN))
  static void onReturn(@Duration long d) {
    metrics.histogramMicros("foo.bar.latency").record(d / 1000);
  }
}
```

- This keeps probes allocation-free on the hot path and within the verifier’s constraints (calls on service-derived objects are allowed).
