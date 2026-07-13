# Instrumentation Backends

**Document Version:** 1.0
**Last Updated:** July 2026
**Status:** Implemented (v3.0.0+)

---

## Overview

BTrace performs bytecode instrumentation through a small internal SPI, `InstrumentationBackend`, with two implementations:

| Backend | Source set | Availability | Class file versions |
|---------|-----------|--------------|---------------------|
| `AsmInstrumentationBackend` | `src/main/java` (Java 8) | Always | ≤ 69 (up to Java 25) |
| `ClassFileApiBackend` | `src/main/java24` (Java 24) | Agent running on JDK 24+ | > 69 (Java 26+) |

All types live in the `io.btrace.instr` package of the **btrace-agent** module:

- `btrace-agent/src/main/java/io/btrace/instr/InstrumentationBackend.java`
- `btrace-agent/src/main/java/io/btrace/instr/AsmInstrumentationBackend.java`
- `btrace-agent/src/main/java/io/btrace/instr/BackendSelector.java`
- `btrace-agent/src/main/java/io/btrace/instr/ClassMeta.java`
- `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`

## Why: the ASM Ceiling

BTrace's instrumentation pipeline is built on ASM. ASM can only parse class files up to a major version it explicitly knows about; ASM 9.9.x tops out at class file major version **69 (Java 25)** and throws when handed anything newer:

```java
/** Highest class file major version ASM 9.9.x can parse without throwing. */
static final int MAX_ASM_MAJOR_VERSION = 69; // Java 25
```

Without an alternative backend, an application compiled for Java 26+ (class file major version 70+) could not be instrumented at all. The JDK ClassFile API (`java.lang.classfile.*`, standardized in JDK 24) always understands the class file format of the JDK it ships with, so it provides a forward-compatible path for such classes.

## The SPI

```java
interface InstrumentationBackend {

  /** Returns {@code true} when this backend can process the given class file major version. */
  boolean supports(int classFileMajorVersion);

  /**
   * Instruments {@code classfileBuffer} by applying all applicable probes.
   *
   * @param loader the classloader loading the target class (may be {@code null})
   * @param classfileBuffer raw class file bytes
   * @param probes all currently registered probes
   * @return transformed class bytes if at least one probe matched, {@code null} otherwise
   */
  byte[] instrument(ClassLoader loader, byte[] classfileBuffer, Collection<BTraceProbe> probes);
}
```

The `ClassMeta` interface complements the SPI: it exposes the minimal class metadata (Java class name, internal name, runtime-visible annotation types, classloader) needed for probe-to-class matching in `BTraceProbeSupport`, decoupling the matching logic from ASM's `ClassReader` so alternative backends can perform matching without constructing an ASM object.

## Backend Selection

`BackendSelector` chooses a backend based on the class file major version. The selection logic, verbatim:

```java
static InstrumentationBackend select(int classFileMajorVersion) {
  if (!ASM.supports(classFileMajorVersion) && CLASSFILE_API != null) {
    return CLASSFILE_API;
  }
  return ASM;
}
```

In other words:

1. Class file version ≤ 69 → ASM backend (the default, full-featured path).
2. Class file version > 69 and the ClassFile API backend is available → ClassFile API backend.
3. Class file version > 69 but the ClassFile API backend is unavailable (agent running on JDK < 24) → falls back to ASM, which will fail to parse the class; instrumentation of that class is effectively skipped.

The ClassFile API backend is loaded **reflectively** at class-initialization time so the main (Java 8-compiled) source set has no compile-time dependency on `java.lang.classfile`:

```java
Class<?> cls =
    Class.forName(
        "io.btrace.instr.ClassFileApiBackend", true, BackendSelector.class.getClassLoader());
return (InstrumentationBackend) cls.getDeclaredConstructor().newInstance();
```

On JDK < 24 the `Class.forName` fails (the compiled class targets class file version 68/Java 24 and references `java.lang.classfile`), the failure is logged at debug level, and the field stays `null`.

## ClassFile API Backend

### Requirements

- The **agent must run on JDK 24+** — the backend is compiled with `sourceCompatibility = 24` / `targetCompatibility = 24` and uses `java.lang.classfile.*`.
- It is engaged only for **class file major versions > 69** (`supports()` returns `classFileMajorVersion > AsmInstrumentationBackend.MAX_ASM_MAJOR_VERSION`).

### How It Instruments

The backend parses the class with `ClassFile.parse()`, builds a `ClassMeta` from the class model (name, runtime-visible annotations, classloader), collects applicable handlers via `BTraceProbe.getApplicableHandlers(meta)`, and injects probe calls as `invokedynamic` instructions bootstrapped by `io.btrace.runtime.IndyDispatcher.bootstrap(...)` — entry probes before the first real instruction, return probes before each `ReturnInstruction`.

### Current Limitations

Verified in `ClassFileApiBackend.java`:

- **Only `Kind.ENTRY` and `Kind.RETURN` probes are supported.** Handlers with any other probe kind (CALL, LINE, FIELD_GET/SET, ERROR, etc.) are skipped with a debug-level log; the remaining handlers are still applied.
- Method matching supports exact names and `/regex/` patterns; **type-constrained method matching** (a non-empty `type` in `@OnMethod`) is unsupported — such handlers are skipped.
- Supported handler parameters: `@ProbeClassName`, `@ProbeMethodName`, and `@Self` (on instance methods; `null` is passed for static methods and constructor entry). Handlers using other special parameters (`@Return`, `@TargetInstance`, `@Duration`, `@TargetMethodOrField`) or plain probed-method arguments are skipped.
- Classes the ClassFile API fails to parse are skipped (warning logged) rather than failing class loading.

## Packaging

The `java24` source set is declared in `btrace-agent/build.gradle` and compiled with a JDK 24 toolchain. Its output is merged into the **root** of the regular agent jar:

```gradle
jar {
    into('') {
        from sourceSets.java24.output
    }
}
```

Important consequences:

- The agent jar is **NOT a Multi-Release JAR** — there is no `META-INF/versions/24/` entry and no `Multi-Release` manifest attribute. The compiled-at-24 `ClassFileApiBackend.class` sits at the jar root next to the Java 8-compatible classes.
- The class is never referenced directly from Java 8-compiled code; it is only ever loaded **reflectively** by `BackendSelector`. On older JDKs the reflective load fails cleanly (`UnsupportedClassVersionError` caught as `Throwable`), and BTrace continues with the ASM backend only.

## Related Documents

- [ExtensionInvokeDynamicBridge](ExtensionInvokeDynamicBridge.md) — invokedynamic-based extension linkage
