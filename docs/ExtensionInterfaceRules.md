# BTrace Extension Interface Rules

This page defines the rules for authoring BTrace extension APIs (interfaces), the build-time checks enforced by the Gradle plugin, and how optional services use shims or throwing stubs at runtime.

## Overview
- API vs Impl: Put public service interfaces under `src/api/java`. Implementations live under `src/impl/java` and are shaded/packaged into the implementation JAR.
- Classloading: The API JAR is added to the target VM bootstrap classpath; the implementation JAR is loaded by an isolated extension classloader.
- Optional injection: A probe can mark an injected service as optional via `@Injected(optional = true)` and select the fallback mode:
  - `SHIM` (production): a no‑op object that implements the interface and returns defaults.
  - `THROW` (development): an object that throws on any method call with a clear error.
  - Select per-field with `@Injected(mode = SHIM|THROW)`, or globally with `-Dbtrace.extension.shimMode=shim|throw` (default: `throw`).

## Authoring Rules
- Form:
  - Public, top‑level interfaces only. No classes or abstract classes.
  - Java 8 compatible; no default/private methods on interfaces.
- Signatures:
  - Only JDK types and other API interfaces in parameter and return types.
  - No `java.io`, `java.net`, `java.nio.channels`, or `java.lang.reflect` types in signatures.
  - Do not reference implementation types in API signatures.
- Exceptions:
  - No checked exceptions in `throws` clauses.
- State:
  - No mutable/static state. Only `public static final` constants are allowed on interfaces.
- Nullability (required):
  - Every return and parameter must be annotated with a nullability annotation.
  - Accepted by default: `javax.annotation.{Nullable, Nonnull}`, `org.jspecify.annotations.{Nullable, NonNull}`, `org.jetbrains.annotations.{Nullable, NotNull}`, `jakarta.annotation.{Nullable, Nonnull}`.
  - You can configure additional annotations via the plugin (see below).
- Shimability (optional services):
  - If a method returns an interface type, annotate it `@Nullable` to ensure the no‑op shim can safely return null.
  - Primitives return their default Java values in the no‑op shim; `void` methods do nothing.
- Permissions:
  - Declare minimal permissions required by the service via `@ServiceDescriptor(permissions = { ... })`, or rely on plugin scanning.
  - The plugin scans the implementation JAR and classpath by default and writes merged permissions to the API JAR manifest.

## Build‑Time Enforcement
The `org.openjdk.btrace.extension` Gradle plugin runs validation on the API services you declare in `btraceExtension.services`. It fails the build with clear error IDs.

Enforced checks (subset shown):
- `BTRACE-EXT-001`: Service must be a public, top‑level interface.
- `BTRACE-EXT-002`: Default/private interface methods not allowed (Java 8/shim compatibility).
- `BTRACE-EXT-003`: Interface declares non‑constant fields.
- `BTRACE-EXT-010`: Method declares checked exceptions.
- `BTRACE-EXT-013`: Method uses forbidden signature type (io/net/channels/reflect).
- `BTRACE-EXT-020`: Missing nullability on method return.
- `BTRACE-EXT-021`: Missing nullability on a parameter.
- `BTRACE-EXT-022`: Method returns an interface but is not annotated `@Nullable` (shimability).
- `BTRACE-EXT-040`: API signature references an implementation class (API purity).
- `BTRACE-EXT-032`: API annotates required permissions but plugin configuration does not provide them and permission scanning is disabled (enable scanning or declare explicitly).

## Plugin Usage
Apply the plugin in your extension project and declare services:

```
plugins {
  id 'org.openjdk.btrace.extension'
  id 'com.github.johnrengelman.shadow'
}

btraceExtension {
  id = 'btrace-metrics'
  name = 'BTrace Metrics (HDR)'
  description = 'HDR Histogram based metrics'
  services = ['org.openjdk.btrace.metrics.MetricsService']
  // Optional: configure nullability annotations if you use different ones
  nullableAnnotations = ['com.acme.NonRequired']
  nonnullAnnotations = ['com.acme.Required']
}
```

Build tasks wired by the plugin:
- `validateServiceApis` runs during `check` and fails on violations.
- `generateServiceShims` and `generateShimIndex` produce shim classes and an index bundled into the API JAR.
- `packageExtension` produces `*-extension.zip` including API and implementation JARs.

During the API JAR build, the plugin logs the permissions written to the manifest:

```
[BTRACE-EXT] permissions: scanned=[THREADS, REFLECTION] merged=[THREADS, REFLECTION]
```

## Runtime Fallback (Optional Services)
- Mark a field in a BTrace script as optional: `@Injected(optional = true)`.
- Choose mode per field: `@Injected(optional = true, mode = SHIM)` or `THROW`.
- Or set globally: `-Dbtrace.extension.shimMode=shim|throw` (default: `throw`).
- The agent prefers pre‑generated shims shipped in the API JAR. If not present, it falls back to a dynamic proxy shim.
- Required injections (`optional = false`) never fall back and fail fast if unavailable.

## Examples
Compliant interface:

```
package org.openjdk.btrace.metrics;

import org.openjdk.btrace.core.extensions.Permission;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@ServiceDescriptor(permissions = { Permission.THREADS })
public interface MetricsService {
  void inc(@Nonnull String name);

  @Nullable Meter meter(@Nonnull String name);
}
```

Violations (examples):
- Missing nullability on return/parameter (BTRACE-EXT-020/021)
- Default method or checked exception on a method (BTRACE-EXT-002/010)
- Returning an interface without @Nullable (BTRACE-EXT-022)
- Signature uses `java.io.File` (BTRACE-EXT-013)
- Signature exposes `impl.InternalMeter` (BTRACE-EXT-040)

---

For deeper background on extension loading, permission enforcement, and injection, see: `docs/btrace-extension-development-guide.md` and `docs/architecture/extension-invokedynamic-bridge.md`.

## Fixes Appendix
This section maps validation errors to concrete fixes you can apply.

- BTRACE-EXT-001: Service must be a public, top-level interface.
  - Fix: Convert the type to a public top‑level interface (no nested/inner types).
- BTRACE-EXT-002: Default method not allowed (Java 8/shim compatibility).
  - Fix: Remove default bodies from interface methods; put behavior in the implementation.
- BTRACE-EXT-003: Interface declares non‑constant fields.
  - Fix: Remove fields from interfaces unless they are public static final compile‑time constants.
- BTRACE-EXT-010: Method declares checked exceptions.
  - Fix: Remove checked exceptions from API signatures; use unchecked exceptions only if needed.
- BTRACE-EXT-013: Method uses forbidden type in signature (io/net/channels/reflect).
  - Fix: Restrict API signatures to JDK types and other API interfaces; avoid I/O, networking, reflection in API surface.
- BTRACE-EXT-020: Missing nullability on return.
  - Fix: Annotate the return with @NonNull or @Nullable using your project’s nullability annotations.
- BTRACE-EXT-021: Missing nullability on a parameter.
  - Fix: Annotate each parameter with @NonNull or @Nullable.
- BTRACE-EXT-022: Interface return requires @Nullable (shimability).
  - Fix: Mark interface return types @Nullable (or provide a documented non‑null default and avoid interface returns when optional).
- BTRACE-EXT-032: Permissions required by API annotations not covered when scan is disabled.
  - Fix: Enable `scanPermissions = true` in the plugin or add the missing permissions to `requiredPermissions`.
- BTRACE-EXT-033: Manifest permissions missing annotated requirements.
  - Fix: Ensure that the set written to the manifest (from scan + requiredPermissions) includes all permissions declared in `@ServiceDescriptor.permissions` and package-level `@ExtensionDescriptor.permissions`.
- BTRACE-EXT-040: API signature or constant pool references implementation type.
  - Fix: Remove any references to classes compiled under `src/impl/java` from API signatures and annotations.
- BTRACE-EXT-041: Declared service not found in API output.
  - Fix: Place the interface in `src/api/java` and list it in `btraceExtension.services`.
- BTRACE-EXT-050: Failed to analyze service (plugin error or unusual bytecode).
  - Fix: Re‑run with `--stacktrace`. If reproducible, file an issue with the API source and the generated class.
