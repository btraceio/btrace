# BTrace Extension Development Guide

> **New to BTrace?** Start with the [BTrace Tutorial](BTraceTutorial.md). The tutorial's [Lesson 6](BTraceTutorial.md#lesson-6---extensions-and-permissions) covers using existing extensions and includes a [Writing Your Own Extension quick-start](BTraceTutorial.md#writing-your-own-extension-quick-start) that links back here for full details.

## Overview

BTrace extensions provide reusable services that can be injected into BTrace scripts. This guide covers the recommended, plugin-based workflow using a single Gradle module with a single authored source tree under `src/main`. The plugin still separates runtime artifacts, generates metadata, shades implementation dependencies, and prepares distributables.

For API authoring rules that the build verifies, see [ExtensionInterfaceRules.md](ExtensionInterfaceRules.md).

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
  id("io.btrace.extension") version "<btraceVersion>"
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

Optional companion to the DSL — `@ExtensionDescriptor`:
- Declared on your API package’s `package-info.java`. Source: `btrace-core/src/main/java/io/btrace/core/extensions/ExtensionDescriptor.java`.
- Fields: `name`, `version`, `description`, `minBTraceVersion`, `dependencies`, `permissions`.
- **`permissions` is the field that does work.** The plugin scans it and fails the build when the annotation requires a permission the manifest does not grant, so it is a useful assertion that the code's needs and the shipped manifest agree.
- **The other fields are not propagated.** The `btraceExtension` block is the single source of the manifest, and the manifest is what the runtime loads and what `btrace ext inspect` reports. A `version` stated only in the annotation describes nothing the build or the runtime will use — leave it unset rather than let it drift from the real artifact version.
- The annotation is entirely optional; most of the extensions shipped with BTrace do not declare one.

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

import io.btrace.core.extensions.Permission;
import io.btrace.core.extensions.ServiceDescriptor;

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

import io.btrace.core.extensions.Extension;
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

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.OnMethod;
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

When an extension's impl needs to interact with application-specific classes (Spark event objects, Hadoop types, custom framework classes), use the `@ExternalType` annotation to generate reflective adapters at build time. The extension Gradle plugin auto-registers the annotation processor. This is the normative reference for the supported adapter boundary.

### How It Works

Declare an interface in `src/main/java` annotated with `@ExternalType("fully.qualified.AppType")`:

```java
package org.example.ext.api;

import io.btrace.core.extensions.ExternalType;

@ExternalType("org.apache.spark.scheduler.SparkListenerJobStart")
public interface JobStartEvent {
    int jobId();
    long time();
}
```

The processor generates `JobStartEvent$Ext` in the same package with a typed `public static` dispatcher per method. A virtual dispatcher takes `Object self` first and resolves the configured owner through `self`'s defining loader. A static dispatcher has both the legacy target-arguments-only form, which resolves through the current thread context class loader (TCCL) and falls back to the system loader only when the TCCL is `null`, and a leading-`ClassLoader` form that resolves through the supplied loader exactly. Successful resolutions are cached per application loader with weak-identity loader keys; failed class or member lookups are not cached, so a later call can retry. The two static forms share that cache.

Use the generated class directly in the impl:

```java
try {
    int id = JobStartEvent$Ext.jobId(event);
    long ts = JobStartEvent$Ext.time(event);
    // emit metrics / logs...
} catch (ExternalTypeResolutionException unavailable) {
    // choose the extension's logging/degradation policy for an unavailable optional API
}
```

For a public target method whose direct signature contains another application type, link it to a
second external contract while keeping the generated API opaque:

```java
@ExternalType("vendor.Child")
interface ChildApi {
    String label();
}

@ExternalType("vendor.Parent")
interface ParentApi {
    @ExternalType.Type(ChildApi.class)
    Object child();
}

Object child = ParentApi$Ext.child(parent);
String label = ChildApi$Ext.label(child);
```

The contracts are processor metadata only: `child` remains an `Object` and does not implement
`ChildApi`. The adapter resolves `vendor.Child` through the already-resolved parent's defining
loader to form the exact method type. A same-name object from another loader fails normally rather
than being coerced; null target values are passed through unchanged.

This explicit `Object` boundary is intentional, even though it is a little more verbose. Declaring
`ChildApi` as the return type would falsely promise that the target's `vendor.Child` object
implements that extension-side interface; a Java cast would then fail. Hiding the conversion would
require rewriting every extension call site or introducing wrappers/proxies, which would add
class-loader, identity, and lifecycle problems. The marker supplies the exact lookup type while
keeping the runtime value honest and directly usable by another generated adapter.

### Overload groups: map local aliases to one target name

Use an overload group only when the target class exposes two or more public methods with the same
name. The local contract methods may need distinct Java names—especially when two target-library
types are both represented as `Object`—so give every member the same `@ExternalType.Overload`
value. That value is the **target method name**, not a new adapter name.

For example, assume the application library has these two methods:

```java
// Application code; it is not on the extension compile class path.
public final class Parent {
    public String describe(String value) { ... }
    public String describe(Child value) { ... }
}
```

Declare the generated-adapter contract as follows:

```java
@ExternalType("vendor.Child")
interface ChildApi {
    String label();
}

@ExternalType("vendor.Parent")
interface ParentApi {
    @ExternalType.Overload("describe")
    String describeText(String value);

    @ExternalType.Overload("describe")
    String describeChild(@ExternalType.Type(ChildApi.class) Object value);
}
```

The local names make the generated Java API unambiguous. At runtime, each dispatcher performs one
exact `MethodHandles` lookup; it does not inspect the value and choose a compatible candidate.

| Contract declaration | Generated call | Exact target member |
| --- | --- | --- |
| `describeText(String)` | `ParentApi$Ext.describeText(parent, text)` | `Parent.describe(String)` |
| `describeChild(@ExternalType.Type(ChildApi.class) Object)` | `ParentApi$Ext.describeChild(parent, child)` | `Parent.describe(vendor.Child)` |

`@ExternalType.Type(ChildApi.class)` changes the lookup signature from the extension-side
`Object` to `vendor.Child`; it does not cast or wrap `child`. As a result, a child object from a
different application loader is not silently accepted as the other overload.

An overload group has three required properties:

1. It contains at least two abstract adapter methods.
2. Every method that selects that target name has `@ExternalType.Overload("describe")`; do not mix
   selected and unselected methods for the same target name.
3. Each member has a distinct exact target signature (name, static/virtual kind, return type, and
   parameter types). The normal declared types—and `@ExternalType.Type` where needed—provide that
   signature.

Do not use `@ExternalType.Overload` for a one-method rename. A uniquely named contract method
already binds to the target method with the same name. It also cannot enable fields, constructors,
private members, generic/array target types, coercion, or fallback lookup; use the manual
`MethodHandleCache` path for those cases.

### Rules

- **Target:** interfaces only (`ElementType.TYPE`). The processor emits a compile error for classes.
- **Annotation value:** non-empty fully-qualified class name. Empty string is a compile error.
- **Method types:** declared erased parameter and return types must exactly match the target member's JVM signature. For a direct target-library position, use `@ExternalType.Type(ChildApi.class) Object`; it keeps the adapter boundary opaque while resolving the child's target class from the resolved owner's defining loader. The returned value does not implement `ChildApi`, but can flow into `ChildApi$Ext` directly. Markers are valid only on direct `Object` returns/parameters, not generic elements or arrays.
- **Access:** dispatch uses `MethodHandles.publicLookup()`. Only public members of public, accessible types are supported; do not add module-opening flags implicitly.
- **Static methods:** add `@ExternalType.Static` on the interface method. `version()` preserves legacy TCCL-based class loading with the documented null-TCCL system-loader fallback. `version(ClassLoader applicationLoader)` resolves with that non-null loader exactly; it rejects null immediately with `NullPointerException("applicationLoader")`. The control argument is not a target argument. Neither form changes the target's observed TCCL.
- **Overload groups:** follow [the overload-group pattern above](#overload-groups-map-local-aliases-to-one-target-name). The selector changes only the target name; declared exact types, including `Type` markers, select the overload. It is not a single-method alias or runtime coercion/search facility.
- **Default methods and static interface methods:** skipped (they already have bodies).
- **Failures:** class lookup, missing member, and inaccessible-member failures throw `ExternalTypeResolutionException` with the original cause. Exceptions thrown by the target method propagate unchanged.

### Supported boundary and manual path

Use generated adapters only for the supported row below. The manual path is the normal solution for version-variant or target-only APIs; it is not an error condition.

| Capability | Phase 1 support | Author action |
|---------|--------|-------------------|
| Public, uniquely named static or virtual methods with exact erased signatures | Supported | Use `@ExternalType`; use `Object` only where the target member itself uses `Object`. |
| A direct target-library `Object` parameter or return type | Supported | Mark it with `@ExternalType.Type(OtherContract.class) Object`; chain opaque results into another generated adapter. |
| Target types in generic elements or arrays | Unsupported | Use `ClassLoadingUtil` and `MethodHandleCache` directly. |
| Explicit target overload groups | Supported | Mark every group member with `@ExternalType.Overload("targetName")`; local names may differ. |
| Fields, constructors, `instanceof`, and casts | Unsupported | Use `ClassLoadingUtil` plus the appropriate direct `MethodHandles.publicLookup()` or `Class` operation. `MethodHandleCache` caches virtual and static method lookups only. |
| Non-public or non-exported named-module members | Unsupported | Use a public supported API or explicitly configure the target JVM outside BTrace. |

For a generated static call under an author-controlled application loader, select it explicitly. Normalize a bootstrap-owned context object's null defining loader to the system loader, whose normal delegation reaches bootstrap:

```java
ClassLoader appLoader = ClassLoadingUtil.definingLoader(context);
if (appLoader == null) appLoader = ClassLoader.getSystemClassLoader();
String version = VersionApi$Ext.version(appLoader);
```

Keep `ClassLoadingUtil.withTCCL(...)` for manual APIs that actually require ambient TCCL policy; it is not needed to call a generated static adapter. Regenerating an adapter adds this overload. A wildcard static import can therefore become ambiguous if another wildcard import provides the same new arity; use a qualified adapter call or an explicit single-member static import.

For version-variant APIs, resolve the exact public signature manually:

```java
Class<?> owner = ClassLoadingUtil.load("com.example.OptionalApi", appLoader);
MethodHandle call = handles.findStatic(owner, "version", String.class, int.class);
String version = (String) call.invoke(3);
```

`MethodHandleCache` caches successful public static/virtual method lookups only; a caught lookup failure remains retryable. Its current keys retain owner classes and can therefore retain application loaders strongly. It has no getter, setter, or constructor helper. See the [provided-style manual linking guide](architecture/provided-style-extensions.md) for the complete pattern.

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

## Publishing and Registry Listing

If you want users to discover your extension through the public catalog, publish the extension artifacts to Maven Central first and then add the extension to the BTrace registry.

The extension registry is planned to live in a dedicated GitHub repository with GitHub Pages hosting; until it is published, treat the entry shape shown below as the working draft.

Registry entries store a single recommended base coordinate:

```json
{
  "id": "my-extension",
  "name": "My Extension",
  "description": "What it does",
  "owner": "example-org",
  "source_repo": "https://github.com/example-org/my-extension",
  "maven": {
    "groupId": "org.example",
    "artifactId": "my-extension",
    "version": "1.2.3"
  }
}
```

Consumers are expected to resolve the standard BTrace extension artifacts from that base coordinate.

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

BTrace's built-in extensions are extension packages in a BTrace distribution's `extensions/`
directory (or from the matching `packageExtension` task in a source checkout). Use `file(...)` to
embed one, or `project(...)` for an in-tree/custom extension. `maven(...)` is reserved for a
separately published third-party extension; BTrace does not publish its bundled extensions as Maven
artifacts.

### Building a Fat Agent with Your Extension

Use the BTrace Fat Agent Plugin to create a self-contained agent JAR:

```groovy
plugins {
    id 'io.btrace.fat-agent' version '<btraceVersion>'
}

btraceFatAgent {
    baseName = 'my-btrace-agent'

    embedExtensions {
        // Your extension project (if in same multi-project build)
        project(':my-extension')

        // BTrace-built packages from the distribution's extensions/ directory
        file('/path/to/btrace-metrics-3.0.0-extension.zip')

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

### Maven builds

The unpublished Maven `fat-agent` module was removed for 3.0.0 because it targets the pre-3.0
extension publication and classdata layout. Use the Gradle fat-agent plugin for embedded
extensions.

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

Declare the compiled probe directory and exact binary names in the fat-agent build:

```groovy
btraceFatAgent {
    bundledProbes {
        from layout.buildDirectory.dir('compiled-probes').get().asFile
        include 'org.example.spark.SparkJobTracer'
        include 'org.example.spark.SparkStageTracer'
    }
}
```

The corresponding `.class` files are stored in the fat agent JAR under:
```
META-INF/btrace-probes/{binary-name-as-package-path}.class
```

Both explicit `probes=` selection and configurator selection resolve this same canonical location.

### Implementing a Configurator

Implement `ExtensionConfigurator` and provide a public no-arg constructor:

```java
package org.example.spark;

import io.btrace.core.extensions.ExtensionConfigurator;
import io.btrace.core.extensions.ProbeConfiguration;
import io.btrace.core.extensions.RuntimeEnvironment;
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
- [ ] Apply `io.btrace.extension` plugin
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

## Assisted Authoring

The [BTrace agent plugins](https://github.com/btraceio/agent-plugins) marketplace provides skills
for Claude Code, Codex, and Pi that apply this guide:

- [`btrace-extension-authoring`](https://github.com/btraceio/agent-plugins/blob/main/plugins/btrace-development/skills/btrace-extension-authoring/SKILL.md)
  — designing a new extension for a target library, including where `@ExternalType` stops and
  hand-written method handles take over.
- [`btrace-legacy-libs-migration`](https://github.com/btraceio/agent-plugins/blob/main/plugins/btrace-observability/skills/btrace-legacy-libs-migration/SKILL.md)
  — moving an integration off the removed `libs`/profile packaging.
