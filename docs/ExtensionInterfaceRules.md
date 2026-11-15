# BTrace Extension Interface Rules

This page defines the rules for authoring BTrace extension APIs (interfaces), the build-time checks enforced by the Gradle plugin, and how optional services use shims or throwing stubs at runtime.

## Overview
- API vs Impl: Sources live under `src/main`, but the same API/impl artifact split still applies. Public service interfaces and exported API types still end up in the API JAR; implementation classes still end up in the implementation JAR.
- Classloading: The API JAR is added to the target VM bootstrap classpath; the implementation JAR is loaded by an isolated extension classloader.
- Optional injection: A probe can mark an injected service as optional via `@Injected(optional = true)` and select the fallback mode:
  - `SHIM` (production): a no‑op object that implements the interface and returns defaults.
  - `THROW` (development): an object that throws on any method call with a clear error.
  - Select per-field with `@Injected(optional = true, mode = SHIM|THROW)`, or globally with `-Dbtrace.extension.shimMode=shim|throw` (default: `throw`). `mode` is only consulted when `optional = true`; a non-optional field never falls back.

## Authoring Rules
Each rule below says whether the build enforces it. `validateServiceApis` checks only the services in `btraceExtension.services`, or, when that list is empty, the interfaces annotated with `@ServiceDescriptor`.

- Form:
  - Public, top‑level interface. Enforced: a service that is not an interface, is not public, or is a nested type fails the build.
  - Java 8 compatible; no default methods. Enforced for default methods. Private interface methods need Java 9 and are not checked separately.
- Signatures:
  - No `java.io`, `java.net`, `java.nio.channels`, or `java.lang.reflect` types as a return or parameter type. Enforced. The check is on the erased type of each parameter and the return type; generic arguments are not inspected.
  - Do not reference implementation types in API signatures. Enforced only for an implementation type that is not exported with the service; see "Implementation types in signatures" below.
  - Only JDK types and other API interfaces in signatures. Not enforced: any other type passes, and an authored class referenced from a service signature is exported with the service.
- Exceptions:
  - No checked exceptions in `throws` clauses. Enforced.
- State:
  - Interface fields. Enforced: a declared field that is not `public static final` fails the build. Java already makes every interface field `public static final`, so this cannot fail for source-compiled interfaces. Not enforced: that the initialiser is a compile-time constant.
- Nullability:
  - Every reference-typed return and parameter should carry a nullability annotation. Not enforced by default: a missing return annotation only logs a warning (there is no setting to make it an error), and a missing parameter annotation logs a warning unless `nullabilitySeverity` is changed (see below).
  - Accepted by default: `javax.annotation.{Nullable, Nonnull}`, `org.jspecify.annotations.{Nullable, NonNull}`, `org.jetbrains.annotations.{Nullable, NotNull}`, `jakarta.annotation.{Nullable, Nonnull}`.
  - `nullableAnnotations` and `nonnullAnnotations` replace these default lists when set; they do not add to them.
- Shimability (optional services):
  - If a method returns an interface type, annotate it `@Nullable` so the no‑op shim can safely return null. Enforced: this is an error by default; `shimabilitySeverity = 'warn'` turns it into a warning.
  - Primitives return their default Java values in the no‑op shim; `void` methods do nothing.
- Permissions:
  - Declare the permissions the service needs via `@ServiceDescriptor(permissions = { ... })` and, optionally, package-level `@ExtensionDescriptor`, or rely on plugin scanning.
  - With `scanPermissions = true` (the default) the plugin scans the implementation JAR and classpath, merges the permissions found in annotations into the set, and writes the result to the API JAR manifest. The annotation coverage check therefore only applies when scanning is off; see "Permission coverage" below.
- Extension descriptor:
  - At most one package-level `@ExtensionDescriptor` per extension project. Enforced when there is more than one; a project with none is accepted.
- Implementation and constructors:
  - Every declared service needs an implementation: a class named `<service>Impl` or a `META-INF/services/<service>` provider file naming an existing class. Enforced.
  - Exported classes should not have public constructors. Enforced as an error by default (`apiCtorSeverity`, see below).

## Build‑Time Enforcement
The `io.btrace.extension` Gradle plugin runs `validateServiceApis` on the services you declare. It does not emit numeric rule ids. Each problem is logged as `[BTRACE-EXT] <message>` followed by a `Fix:` hint, and the task then fails with `BTrace extension API validation failed with N error(s)`. A grouped report is written to `build/reports/btrace/service-api-validation.txt`.

Problems that fail the build (message text in quotes):
- Service not a public, top‑level interface: `must be a public top-level interface`, `must be public`, `must be top-level (not inner)`.
- Interface field that is not `public static final`: `Field '...' not allowed`.
- Default method: `Default method '...' not allowed`.
- Checked exception: `declares checked exception: <type>`.
- Forbidden signature type: `uses forbidden type in signature: <type>`.
- Implementation type in a signature: `API surface leaks implementation type in signature: <type>`.
- Interface return without `@Nullable`: `must be @Nullable for shim compatibility` (default; a warning with `shimabilitySeverity = 'warn'`).
- Parameter without nullability annotation: `Missing nullability on parameter N`, only with `nullabilitySeverity = 'error'`.
- Annotated permissions not covered while `scanPermissions = false`: `requires permissions [...] not covered by plugin requiredPermissions`. Building the API JAR repeats this check and fails with `Manifest permissions missing annotated requirements`.
- More than one package-level `@ExtensionDescriptor`: `Found N package-level @ExtensionDescriptor annotations`.
- Declared service without an implementation: `No implementation found for declared service(s)`.
- Declared service not in the compiled output: `not found in the exported API output`.
- Exported class with a public constructor: `API classes with public constructors` (default; see `apiCtorSeverity`).
- Unexpected failure while analysing a service: `Failed to analyze service`.

Problems that only warn: `Missing nullability on return of '...'` (always), and `Missing nullability on parameter N` under the default `nullabilitySeverity = 'warn'`.

Severity settings in `btraceExtension`:
- `nullabilitySeverity`: `'off'`, `'warn'` (default) or `'error'`; applies to parameters only.
- `shimabilitySeverity`: `'warn'` or `'error'` (default).
- `apiCtorSeverity`: `'off'`, `'warn'` or `'error'` (default).

### Implementation types in signatures
Types referenced from a service signature are exported with the service, so an authored class used in a signature becomes part of the API. The implementation-type check therefore fires only for a type that is excluded from the exports, for example with `excludedExports = ['com.acme.impl.InternalMeter']`. Without `excludedExports`, such a type is exported silently. Not enforced: keeping implementation classes out of signatures in general.

### Permission coverage
With `scanPermissions = true`, permissions named by `@ServiceDescriptor` and package-level `@ExtensionDescriptor` are merged into the manifest permissions, so they are always covered and the check cannot fail. With `scanPermissions = false` the annotated permissions must all appear in `requiredPermissions`; otherwise both `validateServiceApis` and the API JAR build fail.

## Plugin Usage
Apply the plugin in your extension project and declare services:

```
plugins {
  id 'io.btrace.extension'
  id 'com.gradleup.shadow'
}

btraceExtension {
  id = 'btrace-metrics'
  name = 'BTrace HDR Histogram'
  description = 'HDR Histogram based metrics'
  services = ['io.btrace.metrics.MetricsService']
  // additionalExports = ['io.btrace.metrics.histogram.HistogramMetric']
  // Optional: replace the default nullability annotations (these lists replace, not extend, the defaults)
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
package io.btrace.metrics;

import io.btrace.core.extensions.Permission;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@ServiceDescriptor(permissions = { Permission.THREADS })
public interface MetricsService {
  void inc(@Nonnull String name);

  @Nullable Meter meter(@Nonnull String name);
}
```

Violations (examples):
- Default method or checked exception on a method (error)
- Returning an interface without @Nullable (error by default)
- Signature uses `java.io.File` (error)
- Missing nullability on a return or parameter (warning by default)

---

For deeper background on extension loading, permission enforcement, and injection, see: `docs/BTraceExtensionDevelopmentGuide.md` and `docs/architecture/ExtensionInvokeDynamicBridge.md`.

## Fixes Appendix
This section maps validation messages to concrete fixes.

- Service must be a public top-level interface: convert the type to a public top‑level interface (no nested types).
- Default method not allowed: remove default bodies from interface methods; put behavior in the implementation.
- Interface field not allowed: remove the field, or declare it `public static final`.
- Method declares checked exceptions: remove them from API signatures; use unchecked exceptions if needed.
- Forbidden type in signature (io/net/channels/reflect): restrict signatures to JDK types and other API interfaces.
- API surface leaks implementation type: remove the type from the signature, or keep it exported.
- Missing nullability on return or parameter: annotate with `@NonNull` or `@Nullable` from the configured annotations.
- Interface return must be `@Nullable`: mark interface returns `@Nullable`, or set `shimabilitySeverity = 'warn'`.
- Permissions not covered: enable `scanPermissions = true`, or add the missing permissions to `requiredPermissions`.
- More than one `@ExtensionDescriptor`: keep a single package-level descriptor in the API package.
- No implementation found: name the class `<service>Impl` in the service's package, or add `META-INF/services/<service>` naming the implementing class.
- Declared service not found in API output: keep the interface under `src/main/java` and list it in `btraceExtension.services`.
- Public constructor on an exported class: provide a factory or builder on the service, or set `apiCtorSeverity` to `'warn'` or `'off'`.
- Failed to analyze service: re‑run with `--stacktrace`; if reproducible, file an issue with the API source and the generated class.
