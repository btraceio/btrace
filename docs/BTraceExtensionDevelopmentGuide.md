# BTrace Extension Development Guide

## Overview

BTrace extensions provide reusable services that can be injected into BTrace scripts. This guide covers the recommended, plugin-based workflow using a single Gradle module with a single authored source tree under `src/main`. The plugin still separates runtime artifacts, generates metadata, shades implementation dependencies, and prepares distributables.

For API authoring rules that the build verifies, see `docs/ExtensionInterfaceRules.md`.

## Architecture

### Classloader Isolation

Extensions are isolated while exposing only their API to scripts:

```
Bootstrap ClassLoader
├── JRE classes
├── btrace.jar bootstrap section (BTrace core + extension APIs)
└── Extension ClassLoaders (isolated)
    ├── Extension 1 (e.g., btrace-metrics)
    ├── Extension 2 (e.g., btrace-statsd)
    └── Extension N (your extension)

Script ClassLoader (parent = null)
├── Script classes
└── Accesses extensions via invokedynamic bridge
```

### Single Module, Single Source Tree

Use a single Gradle module with one authored source tree:

```
your-extension/
├── build.gradle
└── src/
    ├── main/java/...       (API + impl authored together)
    └── main/resources/...
```

- API types are resolved by scripts (end up on bootstrap).
- Impl is isolated behind an extension classloader with shaded deps.
- The plugin produces an API JAR, a shadowed Impl JAR, and a distributable ZIP.

```gradle
btraceExtension {
  services = [ "org.example.myext.api.MyService" ]
  additionalExports = [ "org.example.myext.api.MyValueType" ] // optional
}
```

- Java sources live under `src/main/java`
- resources live under `src/main/resources`
- the plugin computes the API closure from declared services and any `additionalExports`
- output artifacts remain unchanged

Package-level API/impl separation is still strongly recommended even though the physical source root is shared.

## Gradle Setup (Plugin-Based)

Apply the BTrace Gradle Extension Plugin and configure your extension via `btraceExtension`:

```gradle
plugins {
  id("org.openjdk.btrace.extension") version "<btraceVersion>"
}

repositories { mavenCentral() }

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  // Keep API free of external library types
  // Put all runtime libs under Impl (the plugin will shade them)
}

btraceExtension {
  id = "org.example.myext"                 // required: globally unique extension ID
  name = "My Extension"                    // optional
  description = "Does useful things"       // optional

  // Service interfaces that can be injected into scripts
  // Auto-detected from @ServiceDescriptor, or declare explicitly:
  services = [ "org.example.myext.api.MyService" ]

  // Shade Impl dependencies to avoid conflicts
  shadedPackages = [
    "com.example.dep" : "org.example.myext.shaded.dep"
  ]

  // Permissions
  scanPermissions = true                    // default: infer from Impl bytecode + classpath
  requiredPermissions = [ ]                 // optional additions/overrides

  // Optional: other extension IDs you depend on
  requiresExtensions = [ ]
}
```

Alternative to the DSL:
- You can document extension details via the `@ExtensionDescriptor` annotation in your API package’s `package-info.java`.
- Annotation source: `btrace-core/src/main/java/org/openjdk/btrace/core/extensions/ExtensionDescriptor.java`.
- Fields: `name`, `version`, `description`, `minBTraceVersion`, `dependencies`, `permissions`.
- The `btraceExtension` block remains the canonical source for manifest values; `@ExtensionDescriptor` mainly assists tooling and validates that declared `permissions` are covered by scanning or `requiredPermissions`.

Outputs produced by the plugin:
- API JAR: `build/libs/<name>-<version>-api.jar` (manifest + properties with extension metadata)
- Impl JAR: `build/libs/<name>-<version>-impl.jar` (shadowed/minimized, isolated at runtime)
- Distribution ZIP: `build/distributions/<name>-<version>-extension.zip` (bundles API + Impl)

Advanced (optional) knobs in `btraceExtension`:
- `additionalExports` / `excludedExports`: optional overrides for the computed exported API set.
- `autoApplyShadow` (default true): auto-apply Shadow plugin if not applied.
- `nullableAnnotations`/`nonnullAnnotations`: additional nullability annotations (FQCN) for API linting.
- `nullabilitySeverity` (`off`|`warn`|`error`): nullability lint severity.
- `shimabilitySeverity` (`warn`|`error`): shim-compatibility lint severity.
- `apiCtorSeverity` (`off`|`warn`|`error`): flag public constructors in API classes.
- `generateShimsReachableOnly` (default true): generate shims only for interfaces reachable from declared services.

## Authoring the API and Impl

### API

Define injectable service interfaces. Use the descriptors to help discovery and permission modeling.

```java
package org.example.myext.api;

import org.openjdk.btrace.core.extensions.Permission;
import org.openjdk.btrace.core.extensions.ServiceDescriptor;

@ServiceDescriptor(permissions = { Permission.THREADS })
public interface MyService {
  MyMetric metric(String name);
}
```

Keep API signatures to JDK and your own API types; avoid external library types.

### Implementation

Provide concrete implementations and extend `Extension` to access the runtime context when needed.

```java
package org.example.myext.impl;

import org.openjdk.btrace.core.extensions.Extension;
import org.example.myext.api.MyService;

public final class MyServiceImpl extends Extension implements MyService {
  public MyServiceImpl() {}
  // implement API methods...
}
```

The plugin shades external libraries present in Impl according to `shadedPackages`.

## Using Extensions in Scripts

```java
package btrace;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Injected;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.example.myext.api.MyService;

@BTrace
public class MyProbe {
  @Injected
  private static MyService svc;

  @OnMethod(clazz = "com.example.App", method = "doWork")
  public static void onDoWork() {
    svc.metric("work");
  }
}
```

## App-Type Adapters with `@ExternalType`

When an extension's impl needs to interact with application-specific classes (Spark event objects, Hadoop types, custom framework classes), use the `@ExternalType` annotation to generate reflective adapters at build time. The extension Gradle plugin auto-registers the annotation processor.

### How It Works

Declare an interface in `src/main/java` annotated with `@ExternalType("fully.qualified.AppType")`:

```java
package org.example.ext.api;

import org.openjdk.btrace.core.extensions.ExternalType;

@ExternalType("org.apache.spark.scheduler.SparkListenerJobStart")
public interface JobStartEvent {
    int jobId();
    long time();
}
```

The processor generates `JobStartEvent$Ext` in the same package with a typed `public static` dispatcher per method. Each dispatcher uses a `volatile MethodHandle` field with lazy resolution: on first call it looks up the target class via `self.getClass().getClassLoader()` (virtual methods) or TCCL (static methods), then caches the handle. If the external class is not yet loaded, the resolver throws and the field stays `null`, so the next call retries — no `ExceptionInInitializerError` at extension load time.

Use the generated class directly in the impl:

```java
// No manual MethodHandle or try/catch needed
int  id = JobStartEvent$Ext.jobId(event);
long ts = JobStartEvent$Ext.time(event);
```

### Rules

- **Target:** interfaces only (`ElementType.TYPE`). The processor emits a compile error for classes.
- **Annotation value:** non-empty fully-qualified class name. Empty string is a compile error.
- **Method types:** use `Object` for types that only exist at runtime and can't be on the compile classpath.
- **Static methods:** add `@ExternalType.Static` on the interface method — the dispatcher calls `findStatic` with TCCL-based class loading.
- **Default methods and static interface methods:** skipped (they already have bodies).

### Current Scope Limits (Planned for Future Versions)

The following are not yet handled by the processor. Use `ClassLoadingUtil` / `MethodHandleCache` directly as a workaround; these are all planned for a future `@ExternalType` version:

| Feature | Status | Manual workaround |
|---------|--------|-------------------|
| Field read/write | Planned | `MethodHandleCache.findGetter` / `findSetter` |
| Constructors | Planned | `MethodHandleCache.findConstructor` |
| `instanceof` / `checkcast` on external types | Planned | `ClassLoadingUtil.load(...)` + `Class.isInstance` |
| Chained `@ExternalType` return types | Planned | Manual adapter per level |
| Non-`public` methods | Planned | `MethodHandles.privateLookupIn` (Java 9+) |

The hand-written pattern in [`docs/architecture/provided-style-extensions.md`](architecture/provided-style-extensions.md) works alongside `@ExternalType`-generated adapters in the same impl class until these gaps are closed.

## Metadata and Permissions (Auto-Generated)

The plugin writes extension metadata into the API JAR manifest and a dedicated properties file; manual manifest editing is not needed. Key attributes include:
- `BTrace-Extension-Id`, `BTrace-Extension-Name`, `BTrace-Extension-Description`
- `BTrace-Extension-Services` (service interfaces)
- `BTrace-Extension-Permissions` (merged from scan + explicit `requiredPermissions`)
- `BTrace-Extension-Requires` (dependent extension IDs)
- `BTrace-Extension-Impl` (Impl artifact coordinates/path)
- `BTrace-Shaded-Packages` (diagnostic relocations)

Permission configuration:

```gradle
btraceExtension {
  // Disable inference and declare explicitly (optional)
  // scanPermissions = false
  requiredPermissions = [ "NETWORK", "THREADS" ]
}
```

At runtime, the agent consults this metadata to validate and enforce permissions.

## Dependency Management

- Keep the API free of external library types; prefer JDK and your API classes.
- Put all runtime libraries in Impl; use `shadedPackages` to relocate and avoid conflicts.
- Do not include BTrace modules in your Impl artifact; only external libs are shaded.

## Distribution and Installation

Build artifacts:
- API JAR: `build/libs/<name>-<version>-api.jar`
- Impl JAR: `build/libs/<name>-<version>-impl.jar`
- ZIP: `build/distributions/<name>-<version>-extension.zip`

Install by copying the ZIP contents (API + Impl) into an extensions directory:

```bash
# System-wide
unzip your-extension-<version>-extension.zip -d "$BTRACE_HOME/extensions/"

# User-specific
mkdir -p "$HOME/.btrace/extensions"
unzip your-extension-<version>-extension.zip -d "$HOME/.btrace/extensions/"
```

Discovery locations:
1. `$BTRACE_HOME/extensions/*.jar`
2. `~/.btrace/extensions/*.jar`

Configuration: `$BTRACE_HOME/conf/extensions.conf`

```hocon
autoload = true
repositories = [ "${btrace.home}/extensions", "${user.home}/.btrace/extensions" ]
```

## Fat Agent Deployment (Embedded Extensions)

For environments where installing extensions separately is impractical (Spark, Hadoop, Kubernetes), extensions can be embedded directly in a fat agent JAR.

### Building a Fat Agent with Your Extension

Use the BTrace Fat Agent Plugin to create a self-contained agent JAR:

```groovy
plugins {
    id 'org.openjdk.btrace.fat-agent' version '<btraceVersion>'
}

btraceFatAgent {
    baseName = 'my-btrace-agent'

    embedExtensions {
        // Your extension project (if in same multi-project build)
        project(':my-extension')

        // Published extensions from Maven
        maven('io.btrace:btrace-metrics:2.3.0')

        // Local extension ZIPs
        file('libs/other-extension.zip')
    }
}
```

Build the fat agent:

```bash
./gradlew fatAgentJar
```

### How Embedding Works

The plugin stages your extension:
1. **API classes** → copied as `.class` files (loaded via bootstrap)
2. **Impl classes** → renamed to `.classdata` (loaded at runtime by `ClassDataLoader`)
3. **Metadata** → written to `META-INF/btrace-extensions/{id}/extension.properties`

At agent startup, embedded extensions are automatically discovered from the JAR manifest attribute `BTrace-Embedded-Extensions`.

### Using the Fat Agent

```bash
# Start application with all embedded extensions
java -javaagent:my-btrace-agent.jar MyApp

# Extensions load automatically - no BTRACE_HOME needed
```

### Benefits for Extension Developers

- **Simplified Distribution**: Ship a single JAR with your extension pre-loaded
- **No Installation Required**: Users don't need to install extensions separately
- **Version Locking**: Ensure compatible extension versions are bundled together
- **Cloud-Native**: Perfect for containers and distributed systems

### Maven Plugin

For Maven users, the `btrace-maven-plugin` provides equivalent functionality:

```xml
<plugin>
    <groupId>org.openjdk.btrace</groupId>
    <artifactId>btrace-maven-plugin</artifactId>
    <version>${btrace.version}</version>
    <configuration>
        <extensions>
            <extension>io.btrace:btrace-metrics:${btrace.version}</extension>
        </extensions>
    </configuration>
</plugin>
```

Build with `mvn package` to create the fat agent JAR.

See [Fat Agent Plugin Architecture](architecture/fat-agent-plugin.md) for implementation details.

## Bundled Probes and Zero-Config Auto-Selection

### Overview

Extensions can ship pre-compiled BTrace probe classes inside the fat agent JAR
and optionally declare a *configurator* that tells the agent which probes to
activate automatically, without any operator input.

There are two activation modes:

| Mode | How triggered | When to use |
|------|--------------|-------------|
| **Explicit** | `probes=ProbeName` agent argument | Operator knows exactly which probe to run |
| **Automatic** | Configurator detects the environment | Extension selects the right probe based on the running framework/role |

### Declaring Bundled Probes

List probe class simple names in `extension.properties`:

```properties
probes=SparkJobTracer,SparkStageTracer,SparkExecutorTracer
```

The corresponding `.class` files must be present in the fat agent JAR under:
```
META-INF/btrace-probes/{probe-simple-name}.class
```

The fat agent plugin stages them automatically when you use `bundledProbes {}` in `btraceFatAgent`.

### Implementing a Configurator

Implement `ExtensionConfigurator` and provide a public no-arg constructor:

```java
package org.example.spark;

import org.openjdk.btrace.core.extensions.ExtensionConfigurator;
import org.openjdk.btrace.core.extensions.ProbeConfiguration;
import org.openjdk.btrace.core.extensions.RuntimeEnvironment;
import java.util.Map;

public final class SparkConfigurator implements ExtensionConfigurator {

    @Override
    public ProbeConfiguration configure(RuntimeEnvironment env, Map<String, String> args) {
        ProbeConfiguration config = new ProbeConfiguration();

        if (env.hasClass("org.apache.spark.SparkContext")) {
            // Running as a Spark driver
            config.enable("SparkJobTracer", "SparkStageTracer");
        } else if (env.hasClass("org.apache.spark.executor.Executor")) {
            // Running as a Spark executor
            config.enable("SparkExecutorTracer");
        } else {
            // Not a Spark JVM — enable nothing
            return config;
        }

        // Honour an explicit output= agent argument if present; default to JFR
        String output = args.getOrDefault("output", "jfr");
        config.setOutput(output);

        return config;
    }
}
```

`RuntimeEnvironment` provides:
- `hasClass(String)` — true if the class is loadable from the application classloader
- `getSystemProperty(String)` / `getSystemProperty(String, String)` — `System.getProperty`
- `getEnv(String)` — `System.getenv`
- `getClassLoader()` — the thread-context classloader (application loader)
- `getMainClassName()` — the JVM main class (from `sun.java.command`)

`ProbeConfiguration` lets you:
- `enable(String... probeNames)` — add probes to activate
- `setOutput(Output)` or `setOutput(String)` — choose `JFR`, `FILE`, or `STDOUT`
- `setOutputPath(String)` — file path for `FILE` output
- `setProbeParam(String probe, String key, String value)` — per-probe parameters

### Registering the Configurator

Declare the configurator class in `extension.properties`:

```properties
id=btrace-spark
version=1.0.0
probes=SparkJobTracer,SparkStageTracer,SparkExecutorTracer
configurator=org.example.spark.SparkConfigurator
```

The class must be in the extension's implementation classloader (i.e. part of
the impl JAR, not the API JAR).

### Operator Experience

With a configurator in place, operators simply attach the fat agent:

```bash
# No probes= needed — the configurator selects the right probes automatically
java -javaagent:my-btrace-agent-fat.jar org.apache.spark.deploy.SparkSubmit ...
```

If the operator does supply `probes=`, the explicit list takes priority and the
configurator is not called:

```bash
# Override: load only SparkJobTracer regardless of what the configurator would choose
java -javaagent:my-btrace-agent-fat.jar=probes=SparkJobTracer org.apache.spark.deploy.SparkSubmit ...
```

### Checklist for Configurator Extensions

- [ ] Probe `.class` files listed under `probes=` in `extension.properties`
- [ ] Fat agent plugin `bundledProbes {}` block stages the probe classes
- [ ] Configurator has a public no-arg constructor
- [ ] Configurator class is in the impl artifact (not the API JAR)
- [ ] `configurator=` key set in `extension.properties`
- [ ] `enable()` returns an empty config (not null) when the framework is absent
- [ ] Unit-tested with a mock `RuntimeEnvironment`

## Testing

- Unit test Impl logic normally (JUnit 5).
- Integration test with real BTrace scripts in `integration-tests`.
- Verify on supported JDKs (8, 11, 17+).

## Checklist

- [ ] Single module using `src/main`
- [ ] Apply `org.openjdk.btrace.extension` plugin
- [ ] Set `btraceExtension.id`, declare/annotate `services`
- [ ] Configure `shadedPackages`; optionally tune `requiredPermissions`
- [ ] Keep API clean (JDK-only) and small
- [ ] Build and install ZIP into extensions dir (or embed in fat agent)
- [ ] Unit + integration tests pass on supported JDKs
- [ ] Consider fat agent packaging for cloud/distributed deployments
- [ ] If shipping bundled probes: declare `probes=` in `extension.properties` and stage via `bundledProbes {}`
- [ ] If probes need auto-selection: implement `ExtensionConfigurator`, declare `configurator=` in `extension.properties`

## Best Practices

### Performance
- Zero-allocation hot paths; avoid boxing.
- Prefer lock-free primitives where possible.
- Lazy init; create objects only when needed.
- Ensure thread-safety; services can be called concurrently.

### API Design
- Immutable snapshots for queries.
- Clear, minimal public surface.
- Use builders/factories exposed from the service for configuration objects.

### Builder Pattern Example

```java
// API
@ServiceDescriptor
public interface MetricsService {
  HistogramConfigBuilder newHistogramConfig();
  HistogramMetric histogram(String name, HistogramConfig cfg);
}

public interface HistogramConfig {}
public interface HistogramConfigBuilder {
  HistogramConfigBuilder lowestDiscernibleValue(long v);
  HistogramConfigBuilder highestTrackableValue(long v);
  HistogramConfigBuilder significantDigits(int d);
  HistogramConfig build();
}

// Probe (no `new` in scripts)
@BTrace
class HistoProbe {
  @Injected static MetricsService metrics;
}
```

## Troubleshooting

### ClassNotFoundException
- Script references a type not present in API → export it from the API closure. Keep it in `src/main/java` and add it to `additionalExports` if the plugin cannot infer it.

### NoSuchMethodError
- Impl does not fully implement the API → keep API and Impl in lockstep.

### Extension Not Loaded
- Missing/incorrect metadata → ensure `btraceExtension.id/services` are set or APIs are annotated.

### Dependency Conflicts
- Missing relocations → add entries under `shadedPackages` for third-party libraries.

## Summary

Use a single module with `src/main` and the BTrace extension plugin to produce clean, isolated, and self-describing extensions. The plugin handles artifact separation, metadata, permissions, shading, packaging, and API export computation, so you can focus on a stable API and solid implementation.
