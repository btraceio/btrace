# BTrace Project Review: Top 10 Most Impactful Improvements

## Context

BTrace is a production-safe dynamic tracing tool for Java (v3.0.0-SNAPSHOT) with ~44K lines of Java across 24+ modules. After a thorough review of code quality, architecture, build system, CI/CD, documentation, and feature gaps, the following 10 improvements are ranked by their impact on reliability, maintainability, and user experience.

---

## 1. Fix Swallowed Exceptions Across Critical Modules (Fix - High Impact)

**Problem:** 30+ catch blocks silently discard exceptions with no logging, making production debugging extremely difficult. Found in btrace-compiler (`CompilerHelper.java`), btrace-client (`Client.java`, `JpsUtils.java`), btrace-extension (`ExtensionBridgeImpl.java`, `ExtensionInspector.java`), and btrace-agent.

**Solution:** Add SLF4J logging to all swallowed exceptions. For each, determine whether to log at WARN (expected/recoverable) or ERROR (unexpected) level. Where exceptions indicate genuine failures, propagate or rethrow.

**Key files:**
- `btrace-compiler/src/main/java/org/openjdk/btrace/compiler/CompilerHelper.java` (lines 150, 270, 276, 281, 297, 303)
- `btrace-client/src/main/java/org/openjdk/btrace/client/Client.java` (lines 218, 224, 239, 383)
- `btrace-client/src/main/java/org/openjdk/btrace/client/JpsUtils.java` (lines 20, 74, 79)
- `btrace-extension/src/main/java/org/openjdk/btrace/extension/impl/ExtensionBridgeImpl.java` (lines 114, 163, 176)
- `btrace-ext-cli/src/main/java/org/openjdk/btrace/extcli/ExtensionInspector.java` (lines 111, 114, 122, 159, 173, 185, 203, 237)
- `btrace-ext-cli/src/main/java/org/openjdk/btrace/extcli/Installer.java` (lines 94, 153)
- `btrace-compiler/src/main/java/org/openjdk/btrace/compiler/VerifierVisitor.java` (lines 589, 593, 627, 843, 852)

**Effort:** Medium

---

## 2. Fix Resource Leaks with try-with-resources (Fix - High Impact)

**Problem:** Multiple files create I/O resources (FileOutputStream, FileReader, BufferedReader) without try-with-resources, risking memory/handle leaks in production.

**Solution:** Convert all identified resource creations to try-with-resources blocks.

**Key files:**
- `btrace-runtime/src/main/java/org/openjdk/btrace/runtime/DOTWriter.java` (line 29 - FileOutputStream)
- `btrace-compiler/src/main/java/org/openjdk/btrace/compiler/PCPP.java` (lines 114, 787 - FileReader/BufferedReader)
- `btrace-compiler/src/main/java/org/openjdk/btrace/compiler/CompilerHelper.java` (lines 287-307 - dump() method)
- `btrace-runtime/src/main/java/org/openjdk/btrace/runtime/BTraceRuntimeImplBase.java` (lines 63-66 - FileInputStream)

**Effort:** Small

---

## 3. Replace Production Assertions with Proper Null Checks (Fix - High Impact)

**Problem:** Java `assert` statements are disabled by default in production JVMs (`-ea` flag required). Critical null checks rely on assertions, meaning they silently pass in production, leading to NullPointerExceptions downstream.

**Solution:** Replace `assert x != null` with explicit null checks that throw `NullPointerException` or `IllegalStateException` with descriptive messages.

**Key files:**
- `btrace-runtime/src/main/java/org/openjdk/btrace/runtime/BTraceRuntimeAccessImpl.java` - `assert rtw != null`
- `btrace-instr/src/main/java/org/openjdk/btrace/instr/InstrumentingMethodVisitor.java` - `assert opType != null`
- `btrace-runtime/src/main/java15/org/openjdk/btrace/runtime/Indy.java` - `assert repository != null`
- `btrace-client/src/main/java/org/openjdk/btrace/client/Client.java` - `assert protocol != null`

**Effort:** Small

---

## 4. Unify Logging: Replace System.out/err with SLF4J (Improvement - High Impact)

**Problem:** 57+ files use `System.out.println`, `System.err.println`, or `printStackTrace()` instead of SLF4J. This makes log management, filtering, and routing impossible in production deployments.

**Solution:** Systematically replace all `System.out/err` and `printStackTrace()` calls with appropriate SLF4J logger calls. Add SLF4J Logger field to classes that lack one.

**Key files:** 57+ files across all modules. Priority targets:
- `btrace-compiler/src/main/java/org/openjdk/btrace/compiler/PCPP.java`
- `btrace-compiler/src/main/java/org/openjdk/btrace/compiler/CompilerHelper.java`
- `btrace-client/src/main/java/org/openjdk/btrace/client/Client.java`
- `btrace-agent/src/main/java/org/openjdk/btrace/agent/Main.java`

**Effort:** Large

---

## 5. Add Dependency Vulnerability Scanning to CI (Improvement - Medium Impact)

**Problem:** The CI pipeline (`continuous.yml`) runs tests across multiple JDK versions but has no dependency vulnerability scanning. There's a CodeQL workflow but no OWASP/Dependabot-style dependency check.

**Solution:** Add a GitHub Actions step using `dependency-review-action` for PRs and/or OWASP dependency-check Gradle plugin for builds. Optionally add SBOM generation.

**Key files:**
- `.github/workflows/continuous.yml` - Add dependency scanning job
- `build.gradle` or `common.gradle` - Add OWASP dependency-check plugin
- Optionally add `cyclonedx-gradle-plugin` for SBOM generation

**Effort:** Small

---

## 6. Replace Generic RuntimeException Wrapping with Specific Exceptions (Improvement - Medium Impact)

**Problem:** 32+ locations wrap checked exceptions in `new RuntimeException(e)`, losing exception type information and making it impossible for callers to handle specific failure modes. Some even use `new RuntimeException(e.toString())` which loses the stack trace entirely.

**Solution:** Create a small set of BTrace-specific unchecked exceptions (e.g., `BTraceCompilationException`, `BTraceInstrumentationException`, `BTraceAgentException`) and use them instead of generic RuntimeException. Where the original exception type matters, preserve it.

**Key files:**
- `btrace-compiler/src/main/java/org/openjdk/btrace/compiler/CompilerClassWriter.java:75` - `new RuntimeException(e.toString())` loses stack trace
- `btrace-compiler/src/main/java/org/openjdk/btrace/compiler/Compiler.java:237`
- `btrace-runtime/src/main/java/org/openjdk/btrace/runtime/BTraceMBean.java` (lines 107, 145, 173)
- `btrace-runtime/src/main/java/org/openjdk/btrace/runtime/BTraceRuntimeImplBase.java` (6 locations)
- `btrace-boot/src/main/java/org/openjdk/btrace/boot/Loader.java` (lines 114, 151, 159)

**Effort:** Medium

---

## 7. Complete FIXME/HACK Implementations (Fix - Medium Impact)

**Problem:** Several production code paths have incomplete implementations marked with FIXME/HACK comments:
- `BTraceMBean.java:303`: "FIXME: This is highly incomplete, revisit..." - MBean type conversion is incomplete
- `PCPP.java:298`: `!!HACK!!` - Word token handling workaround
- `PCPP.java:464`: "FIXME: should identify some of these, like (-1), as constants"

**Solution:** Complete the type-to-OpenType conversion in BTraceMBean (add support for all JMX standard types). Address the PCPP preprocessor hacks with proper implementations.

**Key files:**
- `btrace-runtime/src/main/java/org/openjdk/btrace/runtime/BTraceMBean.java` (line 303)
- `btrace-compiler/src/main/java/org/openjdk/btrace/compiler/PCPP.java` (lines 298, 464)

**Effort:** Medium

---

## 8. Update Documentation for v3.0.0 (Improvement - Medium Impact)

**Problem:** Tutorial documentation references BTrace 2.3.0, but the project is at 3.0.0-SNAPSHOT. Users following the getting-started guide may encounter confusion with outdated version references and missing v3 migration guidance.

**Solution:** Update all documentation to reference v3.0.0. Add a migration guide from v2.x to v3.x. Ensure sample code and CLI examples match the current API.

**Key files:**
- `docs/BTraceTutorial.md` - Update version references
- `docs/GettingStarted.md` - Update installation instructions
- `docs/QuickReference.md` - Verify accuracy
- `docs/FAQ.md` - Add v3 migration section
- `README.md` - Ensure consistency

**Effort:** Medium

---

## 9. Add OpenTelemetry Extension for Modern Observability (Feature - High Impact)

**Problem:** BTrace has extensions for StatsD and HdrHistogram metrics, but lacks integration with OpenTelemetry, the emerging industry standard for observability. Users must manually bridge BTrace data to their observability platforms.

**Solution:** Create a `btrace-otel` extension module under `btrace-extensions/` that exports BTrace trace data as OpenTelemetry spans/metrics. This would allow BTrace to integrate with Jaeger, Prometheus, Grafana, Datadog, etc. out of the box.

**Key files:**
- New: `btrace-extensions/btrace-otel/` module
- Reference: `btrace-extensions/btrace-statsd/` (existing extension pattern to follow)
- Reference: `btrace-extension/src/main/java/org/openjdk/btrace/extension/` (extension SPI)
- `settings.gradle` - Register new module

**Effort:** Large

---

## 10. Add GraalVM Native Image Compatibility (Feature - Medium Impact)

**Problem:** GraalVM native image is increasingly popular for Java applications. BTrace's reliance on dynamic bytecode manipulation (ASM) and runtime attachment may not work with native images. There's no documentation or fallback strategy for GraalVM users.

**Solution:** Document GraalVM limitations clearly. Investigate and implement a compile-time instrumentation mode that weaves BTrace scripts at build time (pre-AOT), producing instrumented bytecode compatible with native image. This could leverage the existing btrace-gradle-plugin.

**Key files:**
- `btrace-gradle-plugin/` - Add compile-time weaving task
- `btrace-instr/` - Reuse existing instrumentation for build-time mode
- New: `docs/GraalVMSupport.md` - Document limitations and workarounds

**Effort:** Large

---

## Summary Table

| # | Title | Category | Impact | Effort |
|---|-------|----------|--------|--------|
| 1 | Fix swallowed exceptions (30+ locations) | Fix | High | Medium |
| 2 | Fix resource leaks with try-with-resources | Fix | High | Small |
| 3 | Replace production assertions with null checks | Fix | High | Small |
| 4 | Unify logging to SLF4J (57+ files) | Improvement | High | Large |
| 5 | Add dependency vulnerability scanning to CI | Improvement | Medium | Small |
| 6 | Replace RuntimeException with specific exceptions | Improvement | Medium | Medium |
| 7 | Complete FIXME/HACK implementations | Fix | Medium | Medium |
| 8 | Update documentation for v3.0.0 | Improvement | Medium | Medium |
| 9 | Add OpenTelemetry extension | Feature | High | Large |
| 10 | Add GraalVM native image compatibility | Feature | Medium | Large |
