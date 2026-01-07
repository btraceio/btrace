# BTrace Agent Manifest-Driven Library Paths — Design

## Overview

The BTrace agent needs a reliable, declarative way to discover and load required library JARs onto:
- the JVM bootstrap class path (for APIs used from instrumented code), and
- the application/system class loader search (for optional/runtime pieces).

Today this is achieved by a mix of heuristics and agent options. This document proposes a manifest‑driven configuration model with clear precedence and robust path resolution, and identifies problem areas in the current code.

## Current Behavior (as of HEAD)

- Agent manifest contains `Boot-Class-Path: btrace-boot.jar` (under `btrace-agent`), but the agent does not read manifest attributes explicitly. It heuristically derives `btrace-boot.jar` from the agent JAR location and appends it to the bootstrap class path at runtime (`Main.processClasspaths`).
- Agent options:
  - `bootClassPath`: optional extra JARs to append to bootstrap.
  - `systemClassPath`: optional extra JARs to append to system class loader.
  - `libs`: scans `$AGENT_DIR/btrace-libs/<libs>/boot` and `.../system` and appends any `*.jar` found.
- Optional embedded `META-INF/btrace/agent.properties` can provide default arguments that are merged into `argMap` with special handling for `script`.
- Paths are resolved relative to the agent JAR using URL string slicing; JARs are appended via `Instrumentation.appendToBootstrapClassLoaderSearch` / `appendToSystemClassLoaderSearch` with some JPMS compatibility handling.

## Problem Areas

- Hardcoded naming and heuristics:
  - `processClasspaths` assumes the agent JAR is named `btrace-agent.jar` and substitutes `btrace-boot.jar`. Renaming/repackaging breaks this.
  - Manifest `Boot-Class-Path` is not read; duplication or drift is possible between manifest and code.
- Fragile URL handling:
  - Agent JAR location is parsed from `Main.class` resource using string slicing (e.g., `replace("jar:file:", "")`, substring before `!`). This is brittle on Windows paths, URL encoding, and non‑JAR deployments.
- Attach vs. launch differences:
  - It is unspecified whether `Boot-Class-Path` is honored consistently for both `premain` and `agentmain`. Current code unconditionally appends at runtime, risking double inclusion or order differences.
- Unbounded trust surface:
  - `libs`, `bootClassPath`, and `systemClassPath` allow arbitrary external JARs to be loaded onto bootstrap/system paths, which is highly privileged. There is no default restriction to `BTRACE_HOME`.
- Deduplication and ordering:
  - No deduplication of JARs (e.g., JAR included by manifest and also by code). Load order relative to `Boot-Class-Path` is not explicit.
- Mixed configuration sources:
  - `agent.properties` is non‑standard compared to manifests; precedence and merging semantics are not clearly documented. There is no single place to inspect “effective class path.”
- Error handling:
  - A single `IOException` short‑circuits classpath processing, potentially leaving the environment in a partially configured state.

## Goals

- Deterministic, manifest‑driven configuration of agent libraries with clear precedence.
- Robust path resolution across platforms (Windows paths, URL encoding) and packaging forms.
- Consistent behavior for both launch‑time and attach‑time.
- Safety by default: restrict library resolution to agent home unless explicitly overridden.
- Deduplicate, validate, and log the effective libraries loaded.

## Proposed Design

### Manifest Attributes

Augment the agent manifest with explicit, BTrace‑scoped attributes for library configuration:

- `BTrace-Boot-Libs`: Space‑separated list of JAR paths to append to bootstrap.
- `BTrace-System-Libs`: Space‑separated list of JAR paths to append to system class loader.
- `BTrace-Libs-Root` (optional): Base directory for resolving relative entries (defaults to agent JAR parent or `BTRACE_HOME/libs`).
- `BTrace-Libs-Profile` (optional): Named subdirectory under `btrace-libs` to auto‑scan (`boot/` and `system/`). Mirrors current `libs` agent arg.

Notes:
- Space separation matches `Class-Path` semantics; entries may be relative to the agent JAR or absolute. URL‑style entries should be supported (decoded before use).
- The existing standard `Boot-Class-Path` continues to be honored by the JVM; BTrace will read it as well to unify dedup/order.

### Precedence and Merge Strategy

From highest to lowest:
1. Agent args: `bootClassPath`, `systemClassPath`, `libs` (explicit operator intent).
2. Embedded `agent.properties` defaults (if present).
3. Agent manifest: `BTrace-Boot-Libs`, `BTrace-System-Libs`, `Boot-Class-Path`, `BTrace-Libs-Profile`.
4. Heuristic default: add sibling `btrace-boot.jar` to bootstrap if none configured.

Merging rules:
- Concatenate in precedence order, deduplicating by canonical file path.
- For `libs`/`BTrace-Libs-Profile`, discover `.../boot/*.jar` (bootstrap) and `.../system/*.jar` (system) and append to their respective lists.

### Resolution and Validation

- Determine agent JAR path via `ProtectionDomain.getCodeSource().getLocation()` and open its `Manifest` safely.
- Resolve each entry:
  - If URL (e.g., `file:/...`), decode to a `Path`.
  - If relative, resolve against `BTrace-Libs-Root` (or agent JAR parent).
  - Normalize and verify existence, type (`.jar`), and reachability.
- Safety by default:
  - Reject entries outside `BTRACE_HOME` unless `-Dbtrace.allowExternalLibs=true`.
  - Log warning and skip unusable entries; continue processing others.
- Deduplicate across all sources, preserving the highest precedence order.

### Loading Algorithm

1. Read agent args (`argMap`).
2. Parse `agent.properties` (if present) into defaults that do not override explicit args.
3. Open agent JAR manifest; extract BTrace attributes and `Boot-Class-Path`.
4. Build `List<Path> bootJars` and `List<Path> systemJars` using precedence and scanning rules; deduplicate.
5. Append `bootJars` via `Instrumentation.appendToBootstrapClassLoaderSearch` in order; append `systemJars` via `appendToSystemClassLoaderSearch`.
6. Expose the effective lists at debug log level and optionally via a diagnostic command or `SharedSettings` accessor for inspection.

### Developer Experience and Documentation

- Document new attributes in `README` and agent usage help.
- Provide manifest examples and how they interact with `libs`, `bootClassPath`, `systemClassPath`.
- Add an option `ignoreManifestLibs=true` to disable manifest entries when necessary (debugging, special environments).

## Integration Points and Changes

- `btrace-agent`:
  - Add `AgentManifestLibs` utility to read/resolve manifest attributes safely.
  - Refactor `Main.processClasspaths` to:
    - Consume resolved manifest entries alongside args.
    - Replace string slicing with robust URL/Path handling.
    - Implement deduplication and safety checks.
  - Add new arg key (e.g., `$btrace.ignoreManifestLibs`) recognized via existing `$` system property mechanism.

- `btrace-core`:
  - No changes required; `SharedSettings.getBootClassPath()` use remains orthogonal. Optionally expose an immutable view of effective boot/system JARs for diagnostics.

## Backward Compatibility

- Existing deployments continue to work:
  - If no manifest attributes are present, behavior matches today (heuristic `btrace-boot.jar` + optional args + `libs`).
  - Manifest attributes only augment/clarify existing behavior.
- The JVM continues to honor `Boot-Class-Path`; BTrace’s explicit addition is deduplicated to avoid double insertion.

## Error Handling and Logging

- Process as much as possible; a failure to load one entry should not abort the rest.
- Emit concise debug logs for:
  - Source and resolution of each entry (manifest/args/libs),
  - Skipped entries with reason, and
  - Final effective lists.
- Emit warnings only for operator‑actionable issues (missing files, forbidden path outside `BTRACE_HOME`).

## Risks and Edge Cases

- JPMS constraints: Some JARs appended to bootstrap may still require `--add-opens` or module exports; this is outside the scope of path loading and should be documented.
- Non‑JAR deployments: If the agent is loaded from an exploded directory, manifest lookup may not be available. Fallback to args + heuristics.
- Platform quirks: Windows UNC paths and URL encoding must be handled via `URI`/`Path` APIs, not string splicing.
- Repackaging: Renamed agent JARs are supported because nothing is hardcoded; all paths are resolved relative to the discovered agent location or explicit manifest entries.

## Test Plan

- Unit tests (btrace-agent):
  - Manifest parsing: entries with relative, absolute, and URL forms.
  - Deduplication and precedence across manifest + args.
  - Safety enforcement for paths outside `BTRACE_HOME` with and without override.
  - Robustness on Windows‑style paths (use parameterized path normalization).
- Integration tests:
  - Launch‑time and attach‑time agents with synthetic manifests containing `BTrace-Boot-Libs` and `BTrace-System-Libs` (using temporary JARs).
  - Verify ordering and presence in effective class loader search (e.g., by loading a class known to exist only in those JARs).

## Open Questions

- Do we also support standard `Class-Path` for system libs to align with JVM behavior, or keep BTrace attributes explicit only?
- Should we surface the effective libs via a client command for diagnostics?
- Is a per‑OS profile mechanism desired (e.g., `BTrace-Libs-Profile=linux-x86_64`)? Can be layered atop the current `libs` pattern.

## Deprecation Notice

The `libs`/profiles mechanism is deprecated and will be removed after N+2 minor releases.
- Use extensions with API on bootstrap and isolated implementations.
- See `docs/architecture/provided-style-extensions.md` and `docs/architecture/migrating-from-libs-profiles.md`.
- Escape hatch (discouraged): `-Dbtrace.system.appendJar=/abs/path/lib.jar` (requires `trusted=true`).

## Libs/Profiles vs. Extensions

### Background and Usage

Historically, operators placed “integration jars” into `btrace-libs/<profile>/{boot,system}` and selected them via `libs=<profile>` to support environments such as Spark (driver/executor) and Hadoop. The intent was to make app‑specific functionality available to probes by injecting jars into the JVM classpaths.

### Capabilities Comparison

- libs/profile (classpath injection):
  - Adds arbitrary jars to bootstrap (`boot/`) and system (`system/`) classpaths.
  - No metadata, lifecycle, permissions, or dependency resolution.
  - High blast radius: jars become visible to the entire process.

- extensions (module model):
  - API jar on bootstrap; impl jar in an isolated classloader.
  - Discovery, enable/disable, dependency/conflict handling, lazy/eager load.
  - Permission policy and observable failures with shims.

### Technical Considerations

- Visibility of app types:
  - Profiles made types visible via system CL. With extensions, prefer:
    - Object hand‑off: probes pass app objects to API; impl reflects using the object’s defining loader.
    - TCCL: impl uses `Thread.currentThread().getContextClassLoader()` for reflective access.
  - Avoid importing app types in the impl at link time to stay loader‑agnostic.

- Security and isolation:
  - Profiles bypass policy and broaden the trust surface. Extensions constrain impls and document required permissions.

- Operability and versioning:
  - Profiles have no version semantics or diagnostics beyond logs. Extensions provide conflict resolution and visible failure reasons.

### Replaceability Assessment

- Boot libs: Replaceable with extensions (API on bootstrap already matches the extension model).
- System libs: In almost all cases replaceable by calling through extension APIs and reflective access via app loaders. The rare need for the application to discover classes directly from system CL should be treated as an exceptional, discouraged path.

### Migration Plan (Spark/Hadoop Examples)

- Spark driver/executor:
  - One extension with runtime role detection (system properties/class presence), or two extensions (`btrace-spark-driver`, `btrace-spark-executor`) toggled via `extensions.conf`.
  - API exposes operations probes need; impl reflects into Spark classes using TCCL or object hand‑off.

- Hadoop:
  - `btrace-hadoop` extension: minimal API on bootstrap; impl interacts with Hadoop classes via reflective adapters.

- General steps:
  1) Extract minimal probe‑facing API to an extension API jar.
  2) Move environment‑specific logic to impl jar; eliminate import‑time coupling to app types.
  3) Use permissions and extension config for endpoints and flags.
  4) Enable eager load if early availability is required.

### Deprecation Recommendation

- Deprecate `libs`/profiles as a first‑class feature:
  - Warn when used; document extension alternatives.
- Plan removal after N+2 minor releases.
- If absolutely necessary, provide a narrow, explicit escape hatch (e.g., `-Dbtrace.system.appendJar=/path.jar`) instead of directory‑based profiles, and mark it as discouraged.

### Risks and Mitigations

- Classloader surprises: Avoid importing app types in impl; use object hand‑off/TCCL and cache reflective handles.
- Role detection errors: Combine multiple signals; fall back to no‑op shims with clear logs.
- Startup ordering: Use eager load for extensions that must be present before probes run.

### Conclusion

Extensions fully subsume the legitimate needs served by libs/profiles while improving security, isolation, operability, and versioning. Profiles should be deprecated and replaced with extensions; keep only a narrowly scoped, explicit system‑CL injection flag for exceptional cases.

## “Provided”-Style Extensions (No Classpath Injection)

This section outlines how to “plant” former libs/profile integrations on top of the extension system, similar to Maven’s `provided` scope.

### Goals

- Keep the extension API on bootstrap with only simple/value types (no application classes).
- Avoid shading third‑party/application dependencies into the impl jar.
- Discover and link to application libraries at runtime using the application’s class loader(s).
- Do not mutate the global classpath (bootstrap/system) in normal operation.

### Build Strategy

- API module: only BTrace API dependencies; expose minimal interfaces/DTOs for probe calls.
- Impl module:
  - Declare application/framework dependencies as `compileOnly` (Gradle) or `provided` (Maven‑like intent).
  - Unit/integration tests may add them to `testImplementation` so tests compile and run.
  - Do not shade application libs.

### Runtime Linking Strategy

- Capability/role detection:
  - Detect environment (e.g., Spark driver vs. executor, Hadoop present) by probing marker classes via `Thread.currentThread().getContextClassLoader()` (TCCL) and/or system properties.
  - Example: presence of `org.apache.spark.SparkContext` vs. executor‑specific classes.

- Object hand‑off:
  - API methods accept `Object` (or simple DTOs) for app instances (e.g., a Spark Job/Stage object). Probes pass real app objects.
  - Impl reflects on the object’s defining class loader to reach app classes and methods (no static imports of app types).

- TCCL binding:
  - When resolving classes by name, use the app’s TCCL: `Class.forName(name, false, Thread.currentThread().getContextClassLoader())` or `MethodHandles.publicLookup()` with the target instance.
  - Cache resolved `MethodHandle`s for performance.

- Service discovery (optional):
  - If the app provides SPI via `META-INF/services`, use `ServiceLoader.load(iface, tccl)` to locate providers shipped with the app.

- Provided‑jar discovery (optional):
  - If the app does not package required libs, implement a discovery strategy:
    - Read explicit paths from extension config (`extensions.conf`), e.g., `btrace-spark.classpath=/opt/spark/jars` and create a child `URLClassLoader` used only by the extension impl for reflection.
    - Do not append jars to system/boot classpath. Only the extension impl uses them.
  - Limitations: application code will not see classes loaded this way (by design). Use extension APIs for interaction instead of expecting app‑side discovery.

### Example API Sketch

```
// API (bootstrap)
public interface SparkProbeApi {
  void onJobStart(Object sparkJob);           // object hand-off
  void onStageCompleted(Object stageInfo);
}

// Impl (extension CL)
final class SparkProbeApiImpl implements SparkProbeApi {
  public void onJobStart(Object sparkJob) {
    ClassLoader cl = sparkJob.getClass().getClassLoader();
    Class<?> jobCls = Class.forName("org.apache.spark.scheduler.SparkListenerJobStart", false, cl);
    // Resolve getters via MethodHandles and cache them
    // Extract values, map to DTOs, emit metrics/logs
  }
}
```

### Configuration

- `extensions.conf`:
  - `btrace-spark.role=auto|driver|executor` (role selection or auto‑detect)
  - `btrace-spark.classpath=/path (optional)` (only if the app does not ship libs)
  - `btrace-hadoop.enabled=true|false` and app‑specific tuning

### Permissions

- Typical permissions: `REFLECTION`, `THREADS`, possibly `SYSTEM_PROPS`.
- Avoid `CLASSLOADER` unless creating auxiliary `URLClassLoader`s from configured paths.

### Fallbacks and Escape Hatches

- If discovery fails (app libs absent), prefer:
  - No‑op shim with a clear warning, or
  - An extension‑local child loader fed by a configured path (not global CL mutation).
- As a last resort and only under an explicit, discouraged flag, allow appending a single system jar (not profiles).

### Pros/Cons

- Pros:
  - No classpath pollution; safer and more deterministic.
  - Works across diverse app CL setups (Spark, Hadoop) using TCCL/object hand‑off.
  - Clear permissions and diagnostics; easier to test and version.
- Cons:
  - Requires reflective adapters (some boilerplate); cannot rely on compile‑time typing of app types in impl.
  - If the application truly needs global visibility of third‑party classes (rare), this approach intentionally does not provide it.

### Applicability to Spark/Hadoop

- Spark:
  - One extension with role auto‑detection, or two role‑specific extensions toggled via config.
  - Object hand‑off of Spark event objects; impl reflects to extract fields/counters.

- Hadoop:
  - Extension API exposes simple operations (e.g., HDFS metrics capture); impl links to Hadoop classes via TCCL.

### Bottom Line

Yes—planting former profile integrations “on top of” the extension system is feasible and recommended. Treat application/framework dependencies as “provided” (compileOnly), keep API simple on bootstrap, and implement runtime linking in the impl via object hand‑off and TCCL/ServiceLoader. Avoid global classpath mutation; reserve a narrow, explicit escape hatch only for truly exceptional cases.
