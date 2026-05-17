# BTrace Extension TCK Design

**Date:** 2026-05-17  
**Status:** Draft  
**Scope:** Technology Compatibility Kit for BTrace extension authors and BTrace CI

---

## Problem

BTrace has a well-defined extension contract spanning 11 axes (registry metadata, manifest, service API shape, classloading, permissions, shim fallbacks, lifecycle, behavioral contracts, performance, artifact structure, and API/impl partition). Compliance is currently enforced piecemeal:

- Build-time: `io.btrace.extension` Gradle plugin (`validateServiceApis` task, BTRACE-EXT-* rules)
- Runtime: `ExtensionLoader` permission checks and service injection
- Registry: Python `validate_extension_registry.py` script

There is no standalone, portable compliance harness that a third-party extension author can run against their finished artifact to certify it is compatible with BTrace before publishing.

---

## Goals

1. Provide a standalone TCK JAR (`io.btrace:btrace-tck`) runnable against any extension ZIP
2. Provide a Gradle plugin (`io.btrace.tck`) as an ergonomic wrapper for build pipeline integration
3. Cover all compliance axes: structural, lifecycle, behavioral, and performance
4. Produce machine-readable (JUnit XML), human-readable (HTML), and CI-friendly (stdout) reports
5. Co-locate TCK with the BTrace mono-repo so contract and harness evolve atomically

---

## Non-Goals

- Replacing the `io.btrace.extension` build plugin (that enforces rules during extension authoring; TCK certifies the finished artifact)
- Hosting or publishing extensions to any registry
- Validating BTrace core itself (only extensions under test)
- Dynamic permission escalation or runtime security policy management

---

## Module Structure

Two new modules added to the BTrace mono-repo:

### `btrace-tck`

Published as `io.btrace:btrace-tck`. Contains the TCK engine, all check suite implementations, the embedded probe-target JVM app, report generation, and self-tests.

```
btrace-tck/
  src/main/java/io/btrace/tck/
    TckEngine.java               # orchestrates suites, collects TckResult
    TckConfig.java               # parsed tck-config.yaml model
    TckResult.java               # per-check pass/fail/skip + metadata
    suite/
      StructuralSuite.java
      LifecycleSuite.java
      BehavioralSuite.java
      PerformanceSuite.java
    check/structural/
      ManifestCheck.java
      ArtifactLayoutCheck.java
      ServiceApiCheck.java
      ApiImplPartitionCheck.java
      RegistryCheck.java
    check/lifecycle/
      LoadCheck.java
      InitCheck.java
      InjectionCheck.java
      CloseCheck.java
    check/behavioral/
      NullSafetyCheck.java
      ShimFallbackCheck.java
      ConcurrencyCheck.java
      RequiredServiceCheck.java
    check/perf/
      BaselineCheck.java
      ImplOverheadCheck.java
      BudgetCheck.java
    report/
      JUnitXmlReporter.java
      HtmlReporter.java
      StdoutReporter.java
    cli/
      TckMain.java               # standalone JAR entry point
  src/main/resources/
    probe-target/                # minimal JVM app embedded for lifecycle/perf tests
    report-template/             # HTML report template
  src/test/java/io/btrace/tck/
    # self-tests against btrace-contracts (good) and fixture extensions (bad)
  src/test/resources/fixtures/
    good-extension/              # valid extension ZIP
    bad-manifest/                # missing required manifest attributes
    bad-api/                     # BTRACE-EXT-013 violation (java.io type)
    bad-partition/               # impl class leaked into api.jar
    missing-shim/                # shim not generated for optional service
```

**Dependencies:**
- `btrace-core` — manifest/permission/version model types
- `btrace-ext-validator` *(new shared module, see below)* — BTRACE-EXT-* bytecode validation logic
- ASM (already a transitive dependency) — class file inspection
- JMH — performance suite benchmarks
- Jackson — `tck-config.yaml` parsing and JUnit XML generation

Does **not** depend on `btrace-agent` or `btrace-compiler`. Lifecycle/behavioral/perf tests spawn a child JVM rather than embedding the agent.

### `btrace-ext-validator` (new shared module)

The BTRACE-EXT-* validation logic currently lives in `BTraceExtensionPlugin.groovy` (Gradle code). To make it reusable by both the Gradle plugin and the standalone TCK JAR, this logic must be extracted into a plain Java module `btrace-ext-validator`. Both `btrace-tck` and `btrace-gradle-plugin` then depend on it. No existing external consumer is affected — the Gradle plugin's public API does not change.

### `btrace-tck-gradle-plugin`

Published as Gradle plugin `io.btrace.tck`. Thin Gradle tasks that delegate to the `btrace-tck` JAR via Java exec. No business logic lives here.

```
btrace-tck-gradle-plugin/
  src/main/groovy/io/btrace/gradle/tck/
    BTraceTckPlugin.groovy       # applies tasks
    TckCheckTask.groovy          # exec wrapper → btrace-tck JAR
    TckExtension.groovy          # DSL configuration block
```

---

## Check Suite Architecture

Suites run sequentially. If a suite fails, all downstream suites are skipped. This prevents cascading noise from a malformed artifact.

```
TckEngine
  ├── StructuralSuite      (pure static analysis — no JVM spawn)
  │     ├── ManifestCheck           all BTrace-* manifest attributes present and valid
  │     ├── ArtifactLayoutCheck     ZIP contains api.jar + impl.jar, correct naming
  │     ├── ServiceApiCheck         all BTRACE-EXT-* rules (reuses plugin logic)
  │     ├── ApiImplPartitionCheck   no impl classes leak into api.jar signatures
  │     └── RegistryCheck           optional: validate against extensions.json schema
  │
  ├── LifecycleSuite       (fresh child JVM #1: BTrace agent + probe-target app)
  │     ├── LoadCheck               extension loads without errors or warnings
  │     ├── InitCheck               initialize() called exactly once per session
  │     ├── InjectionCheck          @Injected field receives non-null impl (or shim)
  │     └── CloseCheck              close() called on agent shutdown
  │
  ├── BehavioralSuite      (fresh child JVM #2, same configuration as #1)
  │     ├── NullSafetyCheck         NoOp shim returns null/0/false without NPE propagating to probe
  │     ├── ShimFallbackCheck       deny extension via policy; shim substituted, probe does not crash
  │     ├── ConcurrencyCheck        8-thread × 1000 calls per service method, no data races
  │     └── RequiredServiceCheck    required service (optional=false) fails fast if absent
  │
  └── PerformanceSuite     (fresh child JVM #3: JMH fork, clean state for benchmarking)
        ├── BaselineCheck           measure no-op shim overhead (ns/call) — reference
        ├── ImplOverheadCheck       impl must be ≤ baseline-multiplier × shim (default: 10×)
        └── BudgetCheck             per-service absolute or relative budgets from tck-config.yaml
```

The **probe-target JVM** is a minimal Java application embedded in `btrace-tck` resources. It exposes enough surface (a handful of instrumented methods, a controllable lifecycle) that the TCK can attach the BTrace agent without requiring the extension author to provide a target application.

### ServiceApiCheck and the Gradle Plugin

`ServiceApiCheck` reuses the same bytecode analysis that the `io.btrace.extension` Gradle plugin (`validateServiceApis` task) performs. The TCK extracts the api.jar from the extension ZIP and runs the same checks against the compiled bytecode. This means any extension that already passes the plugin will pass this check — and any that skipped the plugin will be caught here.

---

## Author Interface

### Standalone JAR

```bash
java -jar btrace-tck.jar \
  --extension path/to/my-extension.zip \
  --btrace-home $BTRACE_HOME \
  --tck-config tck-config.yaml \
  --report-dir build/tck-report \
  --suites structural,lifecycle,behavioral,perf
```

All flags except `--extension` and `--btrace-home` are optional. Default suites: all four. Default report dir: `./tck-report`.

### Gradle Plugin

```groovy
plugins { id 'io.btrace.tck' version '3.x.y' }

btraceTck {
  extensionZip = tasks.packageExtension.archiveFile
  btraceHome   = System.env.BTRACE_HOME ?: "/opt/btrace"
  tckConfig    = file("tck-config.yaml")           // optional
  reportDir    = layout.buildDirectory.dir("tck-report")
  suites       = ['structural', 'lifecycle', 'behavioral', 'perf']
}
```

Running `./gradlew tckCheck` executes all enabled suites. The `tckCheck` task is wired into the `check` lifecycle by default (configurable via `wireToBuild = false`).

### Optional Author Config (`tck-config.yaml`)

```yaml
performance:
  baseline-multiplier: 10          # global: impl must be ≤ 10× no-op shim (default)
  services:
    io.example.MetricsService:
      max-latency-ns: 500          # absolute budget overrides multiplier for this service
    io.example.TracingService:
      baseline-multiplier: 5       # tighter relative budget for this service
```

If `tck-config.yaml` is absent, only the global `baseline-multiplier` default (10×) applies.

---

## Reporting

Three outputs written to `--report-dir` after every run.

### `tck-results.xml` — JUnit XML

Standard Ant/JUnit XML schema. Each check is a `<testcase>`, each suite is a `<testsuite>`. Failures carry:
- `message`: short description
- `type`: rule code (e.g., `BTRACE-EXT-013`) or suite name (e.g., `LifecycleSuite/InitCheck`)

Consumable by Jenkins, GitHub Actions test reporter, CircleCI, and any CI platform with JUnit XML support.

### `tck-report.html` — Browsable HTML Report

Styled after Gradle's built-in test report. Structure:
- Top-level summary: pass/fail per suite, total duration, overall verdict
- Per-suite drill-down: all checks with status badges
- Per-failure detail:
  - Rule code linked to `ExtensionInterfaceRules.md` anchor
  - Affected artifact (JAR name, manifest attribute, or class name)
  - Remediation hint (concise fix instruction)
  - For perf failures: table of observed latency / shim baseline / budget

Self-contained single HTML file (inlined CSS, no external dependencies).

### Stdout (`tck-summary.txt`) — CI Log

One line per check, printed in real time as checks execute:

```
[PASS] structural/ManifestCheck                  (12 ms)
[PASS] structural/ArtifactLayoutCheck            (3 ms)
[FAIL] structural/ServiceApiCheck                BTRACE-EXT-013 method foo(InputStream) uses forbidden type java.io.InputStream
[SKIP] lifecycle/*                               structural suite did not pass
[SKIP] behavioral/*                              structural suite did not pass
[SKIP] perf/*                                   structural suite did not pass

TCK FAILED  1 failure(s), 3 suite(s) skipped
Report: build/tck-report/tck-report.html
```

Also written to `tck-report/tck-summary.txt` for archival.

---

## Compatibility Matrix

TCK version tracks BTrace version (same mono-repo, same release train). Artifact coordinates:
- Engine JAR: `io.btrace:btrace-tck:${btraceVersion}`
- Gradle plugin: `io.btrace:btrace-tck-gradle-plugin:${btraceVersion}` (plugin ID: `io.btrace.tck`)

This makes the compatibility matrix unambiguous: TCK `3.2.0` certifies compatibility with BTrace `3.2.x`.

---

## Self-Tests

`btrace-tck` ships with its own test suite that:

1. Runs all four suites against `btrace-contracts` (the reference good extension) — must produce all `[PASS]`
2. Runs structural suite against each bad fixture (`bad-manifest/`, `bad-api/`, `bad-partition/`, `missing-shim/`) — must produce the expected `[FAIL]` with the correct rule code
3. Verifies the JUnit XML and HTML outputs are well-formed
4. Verifies `[SKIP]` propagation when structural fails

This ensures the TCK is itself verifiably correct before being used to certify extensions.

---

## Open Questions

None — all design decisions resolved during brainstorming.
