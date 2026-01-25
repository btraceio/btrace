# BTrace Extension Development Guide

## Overview

BTrace extensions provide reusable services that can be injected into BTrace scripts. This guide covers the recommended, plugin-based workflow using a single Gradle module with two source sets (`api`, `impl`). The plugin separates artifacts, generates metadata, shades implementation dependencies, and prepares distributables.

For API authoring rules that the build verifies, see `docs/ExtensionInterfaceRules.md`.

## Architecture

### Classloader Isolation

Extensions are isolated while exposing only their API to scripts:

```
Bootstrap ClassLoader
├── JRE classes
├── btrace-boot.jar (BTrace core + extension APIs)
└── Extension ClassLoaders (isolated)
    ├── Extension 1 (e.g., btrace-metrics)
    ├── Extension 2 (e.g., btrace-statsd)
    └── Extension N (your extension)

Script ClassLoader (parent = null)
├── Script classes
└── Accesses extensions via invokedynamic bridge
```

### Single Module, Dual Source Sets

Use a single Gradle module with two source sets:

```
your-extension/
├── build.gradle
└── src/
    ├── api/java/...        (public API visible to scripts; JDK-only deps)
    ├── api/resources/...
    ├── impl/java/...       (implementation; can use external libraries)
    └── impl/resources/...
```

- API types are resolved by scripts (end up on bootstrap).
- Impl is isolated behind an extension classloader with shaded deps.
- The plugin produces an API JAR, a shadowed Impl JAR, and a distributable ZIP.

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
- `autoApplyShadow` (default true): auto-apply Shadow plugin if not applied.
- `nullableAnnotations`/`nonnullAnnotations`: additional nullability annotations (FQCN) for API linting.
- `nullabilitySeverity` (`off`|`warn`|`error`): nullability lint severity.
- `shimabilitySeverity` (`warn`|`error`): shim-compatibility lint severity.
- `apiCtorSeverity` (`off`|`warn`|`error`): flag public constructors in API classes.
- `generateShimsReachableOnly` (default true): generate shims only for interfaces reachable from declared services.

## Authoring the API and Impl

### API (src/api/java)

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

### Implementation (src/impl/java)

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

## Testing

- Unit test Impl logic normally (JUnit 5).
- Integration test with real BTrace scripts in `integration-tests`.
- Verify on supported JDKs (8, 11, 17+).

## Checklist

- [ ] Single module using `src/api` and `src/impl`
- [ ] Apply `org.openjdk.btrace.extension` plugin
- [ ] Set `btraceExtension.id`, declare/annotate `services`
- [ ] Configure `shadedPackages`; optionally tune `requiredPermissions`
- [ ] Keep API clean (JDK-only) and small
- [ ] Build and install ZIP into extensions dir
- [ ] Unit + integration tests pass on supported JDKs

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
- Script references a type not present in API → move it to `src/api/java` as an interface.

### NoSuchMethodError
- Impl does not fully implement the API → keep API and Impl in lockstep.

### Extension Not Loaded
- Missing/incorrect metadata → ensure `btraceExtension.id/services` are set or APIs are annotated.

### Dependency Conflicts
- Missing relocations → add entries under `shadedPackages` for third-party libraries.

## Summary

Use a single module with `api` and `impl` source sets and the BTrace extension plugin to produce clean, isolated, and self-describing extensions. The plugin handles artifact separation, metadata, permissions, shading, and packaging, so you can focus on a stable API and solid implementation.
