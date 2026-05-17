# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BTrace is a safe, dynamic tracing tool for the Java platform that instruments running Java applications to inject tracing code ("bytecode tracing"). It's similar to DTrace for OpenSolaris but designed specifically for Java.

## Build Commands

### Primary Build

```bash
# Build entire project with distribution packages
./gradlew :btrace-dist:build

# Output locations:
# - Distributions: btrace-dist/build/distributions/ (*.tar.gz, *.zip, *.rpm, *.deb)
# - Exploded binary (BTRACE_HOME): btrace-dist/build/resources/main/
```

### Testing

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :btrace-instr:test
./gradlew :btrace-compiler:test

# Run specific test class
./gradlew :btrace-instr:test --tests "*CompilerTest"

# Run specific test method
./gradlew :btrace-instr:test --tests "io.btrace.compiler.CompilerTest.testSimple"

# Integration tests (requires dist built first)
./gradlew :btrace-dist:build
./gradlew :integration-tests:test -Pintegration
```

### Golden Files (Test Data)

Some instrumentation tests use golden files to verify bytecode correctness. When you intentionally change instrumented bytecode:

```bash
# Regenerate all golden files
./gradlew test -PupdateTestData

# Then commit the updated files in btrace-instr/src/test/resources/instrumentorTestData/
```

### Code Formatting

```bash
# Check formatting (runs during build)
./gradlew spotlessCheck

# Auto-format all code (Google Java Format)
./gradlew spotlessApply
```

### Module-Specific Build

```bash
# Build single module
./gradlew :<module-name>:build

# Build without tests (faster)
./gradlew :<module-name>:build -x test
```

## Architecture Overview

BTrace follows a clear separation between compile-time safety verification and runtime instrumentation:

### Core Flow: Script → Compilation → Instrumentation → Execution

```
BTrace Script (.java)
    ↓
[btrace-compiler] Compile & verify safety
    ↓
Compiled Script (.class)
    ↓
[btrace-agent] Load into target JVM
    ↓
[btrace-instr] Transform target classes
    ↓
[btrace-runtime] Execute probe code
```

### Module Responsibilities

**btrace-compiler** - Script compilation and safety verification
- Entry: `Compiler.java` - JSR 199 compilation pipeline
- Safety: `Verifier.java` - Enforces BTrace restrictions (no loops, no allocations, no exceptions, no field assignment)
- Post-processing: `Postprocessor.java` - Bytecode transformations after compilation
- Location: `btrace-compiler/src/main/java/org/openjdk/btrace/compiler/`

**btrace-agent** - Java agent for attaching to target JVMs
- Entry: `Main.java` - Implements `premain()` and `agentmain()` for instrumentation
- Client management: `Client.java`, `RemoteClient.java`, `FileClient.java`
- Starts server on port 2020 by default to accept client connections
- Location: `btrace-agent/src/main/java/org/openjdk/btrace/agent/`

**btrace-instr** - Bytecode instrumentation using ASM
- Transformer: `BTraceTransformer.java` - ClassFileTransformer implementation
- Probe factory: `BTraceProbeFactory.java` - Creates BTraceProbe instances
- Instrumentor: `Instrumentor.java` - Main ASM-based bytecode injection logic
- 15 specialized instrumentors for different probe types (method entry/exit, field access, allocation, etc.)
- Location: `btrace-instr/src/main/java/org/openjdk/btrace/instr/`

**btrace-runtime** - Runtime services for BTrace scripts
- Factory: `BTraceRuntimes.java` - Loads version-specific implementations (JDK 8, 9, 11, 15+)
- Base: `BTraceRuntimeImplBase.java` - Core runtime services (print, profiling, JMX, etc.)
- Multi-version support: Separate implementations in `src/main/java9/`, `src/main/java11/`, `src/main/java15/`
- Location: `btrace-runtime/src/main/java/org/openjdk/btrace/runtime/`

**btrace-client** - Command-line client tool
- Main: `Main.java` - CLI for connecting to agent and submitting scripts
- Client: `Client.java` - Manages socket connection to agent
- Location: `btrace-client/src/main/java/org/openjdk/btrace/client/`

**btrace-core** - Core abstractions and communication
- Wire protocols: `WireProtocol.java` - V1 (Java serialization) and V2 (binary, 3-6x faster)
- Commands: 20+ command types in `comm/` package for agent-client communication
- Annotations: `@BTrace`, `@OnMethod`, `@OnTimer`, etc. in `annotations/` package
- Location: `btrace-core/src/main/java/org/openjdk/btrace/core/`

### Key Architectural Patterns

**Safety-First Design**: Scripts are verified at compile-time to prevent:
- Loops and recursion (no infinite loops in instrumented code)
- Object allocation (minimize GC pressure on target app)
- Exception throwing (don't crash target app)
- Field assignment (maintain immutability)

**Instrumentation Pipeline**: When a class loads:
1. `BTraceTransformer.transform()` checks registered probes
2. For matching classes, `Instrumentor` creates ASM visitor chain
3. Each specialized instrumentor handles specific probe types (entry, exit, call, field access, etc.)
4. Modified bytecode with injected probe calls is returned

**Multi-Version Runtime**: The runtime has version-specific implementations to handle JDK differences:
- Base in `src/main/java/` (Java 8)
- JDK 9+ features in `src/main/java9/`
- JDK 11+ features in `src/main/java11/`
- JDK 15+ features in `src/main/java15/`

**Communication**: Agent and client communicate via pluggable WireProtocol:
- V1: Java Object Serialization (backward compatible)
- V2: Custom binary protocol (higher performance, 2-5x smaller payloads)
- Auto-negotiation ensures compatibility

## Code Conventions

**Source Compatibility**: Java 8 for main code, compiled with Java 11 toolchain
- Set in `common.gradle`: `sourceCompatibility = 8`, `targetCompatibility = 8`
- Uses Java 11 compiler for better optimization while maintaining Java 8 compatibility

**Formatting**: Google Java Format via Spotless
- Import order: `java`, `javax`, `io.btrace`, `*`, then static imports
- Always run `./gradlew spotlessApply` before committing

**Testing**:
- Unit tests in `src/test/java/` named `*Test.java`
- BTrace instrumentation scripts in `src/test/btrace/` (compiled with btracec)
- Golden files in `src/test/resources/instrumentorTestData/` for bytecode verification
- Integration tests require full distribution build first

## Important Implementation Details

**BTrace Script Restrictions** (enforced by `Verifier.java`):
- No `new` keyword (object allocation)
- No loops (`for`, `while`, `do-while`)
- No `throw` statements
- No field assignment (can read fields, but not write)
- Only whitelisted method calls allowed (BTraceUtils.*, etc.)
- Scripts must be annotated with `@BTrace`

**Instrumentation Probe Types** (`@OnMethod` locations):
- `Kind.ENTRY` - Method entry
- `Kind.RETURN` - Method return (normal)
- `Kind.ERROR` - Method return (exception)
- `Kind.CALL` - Before method call
- `Kind.LINE` - Specific line number
- `Kind.FIELD_GET` / `FIELD_SET` - Field access
- `Kind.ARRAY_GET` / `ARRAY_SET` - Array access
- `Kind.NEW` - Object/array allocation
- Plus more (see `Location.java`)

**ClassFileTransformer Thread Safety**: `BTraceTransformer` uses `ReentrantReadWriteLock` because:
- Multiple threads can load classes concurrently
- Probe registration (write) vs. class transformation (read) must be coordinated

**Performance Considerations**:
- Filter-based class matching to avoid unnecessary transforms
- Class metadata caching (`ClassCache.java`)
- Binary protocol V2 for reduced serialization overhead
- Async command queue with configurable backoff

## Module Dependencies

```
btrace-core (annotations, wire protocol, commands)
    ↓
btrace-compiler, btrace-instr, btrace-runtime
    ↓
btrace-agent (orchestrates everything)
    ↓
btrace-client, btrace-dist
```

## Working with BTrace Scripts

BTrace scripts are Java files with special annotations. They're compiled separately from regular Java code:

**Compilation**: Use `btracec` tool or Gradle tasks
```bash
# In tests
./gradlew :btrace-instr:compileTestProbes
./gradlew :integration-tests:compileTestProbes

# In benchmarks
./gradlew :benchmarks:agent-benchmark:btracec
```

**Example Script Structure**:
```java
@BTrace
public class MyTrace {
    @OnMethod(clazz="java.io.FileInputStream", method="<init>")
    public static void onFileOpen(@ProbeClassName String className, String fileName) {
        BTraceUtils.println("File opened: " + fileName);
    }
}
```

## Debugging Tips

**Enable Debug Output**: BTrace has built-in debug support via `DebugSupport.java`
- Set system properties or use agent debug flags
- Check `Main.java` for available agent arguments

**Test Isolation**: Each test should be independent
- Tests run with `cleanTest` dependency (always run, never cached)
- Integration tests require fresh distribution build
- BTrace agent defaults to port 2020; `ManifestLibsTests` uses port 2022 to avoid contention
- Port is configured via `btrace.port` system property, passed to spawned BTrace processes via `-Dbtrace.port` and `-p` CLI flag

**Bytecode Verification**: If instrumentation tests fail:
1. Check if bytecode generation changed
2. Regenerate golden files with `-PupdateTestData`
3. Verify the changes are intentional
4. Commit updated golden files

**ClassLoader Issues**: BTrace uses multiple classloaders:
- Bootstrap classpath for agent core (bootstrap section of masked `btrace.jar`)
- System classpath for client tools
- Target application classloaders remain isolated
- See `Main.java` for bootstrap setup

## Documentation Organization

The repository separates user-facing documentation from internal agent/planning documents.

**User-facing docs** → `docs/`
- End-user guides, tutorials, FAQ, quick reference, troubleshooting
- Architecture reference docs (`docs/architecture/`) for contributors and advanced users
- Developer ops docs (`docs/releasing.md`, `docs/examples/`, `docs/samples/`)
- `docs/README.md` is the documentation index — keep links here up to date

**Internal/agent docs** → `internal/`
- `internal/plans/` — session plans, implementation plans, next-steps notes
- `internal/specs/` — design specs derived from issues or external sources
- `internal/libretti/` — muse/libretto agent requirement files from GitHub issues
- `internal/superpowers/plans/` — superpowers agent implementation plans
- `internal/superpowers/specs/` — superpowers agent design specs

**Rules for agents — MANDATORY:**
- NEVER store planning documents, session notes, implementation specs, or libretto files under `docs/`
- NEVER create or write to a `doc/` directory (singular) — it does not exist; use `internal/` instead
- NEVER create `docs/plans/`, `docs/superpowers/`, or any non-user-facing subdirectory under `docs/`
- Store all agent-generated plans in `internal/plans/` or `internal/superpowers/plans/`
- Store all agent-generated design/requirement specs in `internal/specs/` or `internal/superpowers/specs/`
- Store all libretto/muse requirement files in `internal/libretti/`
- Do not link internal documents from `docs/README.md` or other user-facing docs

## Contributing Notes

From Readme.md: Pull requests can only be accepted from signers of the [Oracle Contributor Agreement](https://oca.opensource.oracle.com/)
