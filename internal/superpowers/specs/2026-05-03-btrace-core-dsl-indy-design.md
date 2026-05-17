# BTrace Core DSL + Indy Design

**Date:** 2026-05-03
**Branch context:** `feature/btrace-utils-split` (partially superseded by this design)
**Scope:** Major version — disruptive changes are acceptable; migration tooling provided

---

## Problem

The `btrace-utils-split` branch decomposed `BTraceUtils` into full service objects
(`StringsService`, `NumbersService`, `PrinterService`, etc.) with interface + impl +
extension manifest for every utility category — even trivial, stateless operations like
string concatenation. This introduces ceremony where none is warranted and conflates
the extension model (designed for stateful, pluggable, system-level integrations) with
simple pure-function utilities.

---

## Goals

- Replace `BTraceUtils.*` with a flat, discoverable core DSL (~40–50 methods)
- Link core ops via `invokedynamic` so the verifier never sees raw JDK calls
- Keep the existing extension model unchanged for stateful/complex integrations
- Provide a mechanical migration path for existing scripts
- Major version bump: backward compatibility is a migration concern, not a design constraint

---

## What Is Core vs Extension

**Rule of thumb:** if a probe body can use the operation in a one-liner, it is core.
Everything else is an extension.

| Core | Extension |
|---|---|
| `@On*` annotation processing | Aggregations (→ statsd, etc.) |
| print, println, printf | JFR events |
| str, concat, substr, matches | jvmstat counters |
| timestamp, monotonic | DTrace probes |
| threadName, threadId, currentThread | JMX operations |
| stackTrace, printStack, stackDepth | Custom metrics backends |
| className, identity, size, str(Object) | |
| probeClass, probeMethod | |
| exit | |

---

## Section 1: API Surface

The entire core DSL is one flat class: `io.btrace.BTrace`.

Scripts use it without an explicit import — the compiler preprocessor auto-injects
`import static io.btrace.BTrace.*` into every `@BTrace`-annotated class.

```java
@BTrace
public class MyTrace {
    @OnMethod(clazz = "java.io.FileInputStream", method = "<init>")
    public static void onFileOpen(String fileName) {
        println("File opened: " + str(fileName));
        printStack();
    }
}
```

**Core op groups (~40–50 methods total):**

| Group | Methods |
|---|---|
| Output | `print`, `println`, `printf` |
| Strings | `str`, `concat`, `substr`, `matches`, `startsWith`, `endsWith`, `length` |
| Numbers | `str(long)`, `str(double)`, `abs`, `min`, `max` |
| Time | `timestamp`, `monotonic` |
| Threads | `currentThread`, `threadName`, `threadId` |
| Stack | `printStack`, `stackTrace`, `stackDepth` |
| Object | `identity`, `size`, `className`, `str(Object)` |
| Probe context | `probeClass`, `probeMethod` ¹ |
| Control | `exit` |

¹ `probeClass()` and `probeMethod()` are currently instrumentor-filled parameters
(`@ProbeClassName`, `@ProbeMethodName`). As flat ops they require instrumentor
cooperation to push probe metadata onto the stack before the `INVOKEDYNAMIC` call —
this is a known implementation constraint, not a runtime lookup.

`io.btrace.BTrace` contains real static implementations for each method. These serve
as the compile-time target (so javac resolves them) and as a safe fallback. The
post-processor replaces them with `INVOKEDYNAMIC` at build time.

---

## Section 2: Indy Dispatch Mechanism

### Compiler post-processor rewrite

Every `INVOKESTATIC` targeting `io/btrace/BTrace` is rewritten:

```
// Before rewrite:
INVOKESTATIC io/btrace/BTrace.print(Ljava/lang/String;)V

// After rewrite:
INVOKEDYNAMIC print(Ljava/lang/String;)V [bootstrap: io/btrace/runtime/BTraceBootstrap.bootstrap]
```

The rewriter targets `io/btrace/BTrace` by name — no marker annotation needed.
Extension DSL stub classes are not needed — extensions use `@Injected` instead
(see Section 3).

### Bootstrap

```java
// io.btrace.runtime.BTraceBootstrap
public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type) {
    MethodHandle mh = OP_TABLE.get(name + type.descriptorString());
    if (mh == null) throw new BootstrapMethodError("Unknown BTrace op: " + name);
    return new ConstantCallSite(mh);
}
```

`OP_TABLE` is a `Map<String, MethodHandle>` populated once at agent startup from
`btrace-runtime`. After JIT warmup, `ConstantCallSite` folds to a direct call —
zero overhead vs. `INVOKESTATIC`.

### Level guards

Level guards (the existing `MethodHandles.guardWithTest` pattern for probe-level
checks) are composed around the core handle at *registration time*, not at bootstrap
time. The call site stays `ConstantCallSite`; the guard is inlined by the JIT.

### Verifier integration

The verifier's allowlist for `INVOKEDYNAMIC` is bootstrap-owner based. Two bootstrap
methods are permitted:

- `io/btrace/runtime/BTraceBootstrap.bootstrap` — core DSL ops (new)
- `io/btrace/runtime/IndyDispatcher.bootstrap` — probe handler dispatch (existing, unchanged)

All other `INVOKEDYNAMIC` call sites are rejected. The verifier needs zero knowledge
of individual op names — the bootstrap owner is the trust boundary. All actual JDK
calls (`System.out`, etc.) happen on the trusted runtime side, invisible to the
verifier.

---

## Section 3: Extension Model

The existing extension model is **unchanged**: `Extension` base class, `@ServiceDescriptor`,
`ExtensionContext`, API+impl JAR split, permission declarations, `ExtensionBridgeImpl`,
versioning and conflict resolution — all retained as-is.

### Script usage: `@Injected`

Extensions expose their APIs to scripts via `@Injected`, exactly as today:

```java
@Injected StatsdService statsd;

@OnMethod(clazz = "com.example.Api", method = "handle")
public static void onHandle(@Duration long ns) {
    println("duration: " + str(ns));   // core op
    statsd.gauge("api.duration", ns);  // extension, typed
}
```

The compiler resolves `statsd.gauge(...)` through the typed `StatsdService` interface.
The post-processor rewrites `@Injected` field access to `INVOKEDYNAMIC` via the
existing `ExtensionIndy.bootstrapFieldGet` path (unchanged).

### Why not flat extension ops

Providing flat extension ops (e.g., `gauge(...)` without a class qualifier) would
require extension authors to maintain a companion stub class alongside their service
interface — the same mechanism as `@Injected` but with extra boilerplate and no
additional capability. `@Injected` is the right model for extensions.

### Extension bootstrap is separate

`BTraceBootstrap` (core ops) and `ExtensionIndy` (extension `@Injected` fields) are
distinct bootstrap methods. Core ops are never resolved through the extension bridge;
extension ops are never resolved through `BTraceBootstrap`.

---

## Section 4: Migration

### `BTraceUtils` → flat core ops (script authors)

The mapping is mechanical:

| Old | New |
|---|---|
| `BTraceUtils.println(x)` | `println(x)` |
| `BTraceUtils.print(x)` | `print(x)` |
| `BTraceUtils.Strings.strcat(a, b)` | `concat(a, b)` |
| `BTraceUtils.Strings.str(x)` | `str(x)` |
| `BTraceUtils.jstackStr()` | `stackTrace()` |
| `BTraceUtils.timestamp()` | `timestamp()` |
| `BTraceUtils.threadName(t)` | `threadName(t)` |
| `BTraceUtils.sizeof(o)` | `size(o)` |
| `BTraceUtils.classNameOf(o)` | `className(o)` |
| `BTraceUtils.exit(code)` | `exit(code)` |

**`btrace-migrate` tool** — ships as a Gradle task and standalone CLI; takes old
`.java` BTrace scripts and emits new ones using the flat API. Handles import cleanup
and method renames automatically.

**Compatibility shim** — `BTraceUtils` is retained in the major release with all
static methods delegating to the core ops. Existing scripts compile and run without
modification. The shim is removed in the next major version.

### `btrace-utils-split` branch rework

The services introduced on that branch are dissolved under this design:

| Branch artifact | Disposition |
|---|---|
| `StringsService` / `StringsServiceImpl` | Dissolved — ops become core static methods |
| `NumbersService` / `NumbersServiceImpl` | Dissolved — ops become core static methods |
| `TimeService` / `TimeServiceImpl` | Dissolved — ops become core static methods |
| `ReferencesService` / `ReferencesServiceImpl` | Dissolved — ops become core static methods |
| `PrinterService` / `PrinterServiceImpl` | Dissolved — `print`/`println` become core ops |
| `BTraceUtilsBootstrap` | Replaced by `BTraceBootstrap` (simpler, no service lookup) |
| `BTraceUtilsCallRewriter` | Simplified — rewrites to op name only, no service class arg |
| Extension framework improvements | **Kept** — discovery, versioning, permissions retained |

---

## Key Invariants

1. Core ops are registered before any extension loads; extensions cannot override core op names.
2. The verifier whitelist is a single bootstrap owner check, not a per-method allowlist.
3. `BTraceBootstrap` and `ExtensionIndy` are distinct — no shared state.
4. `io.btrace.BTrace` static implementations are always present as fallbacks (safe even without indy rewrite).
5. Extension authors implement `Extension`, declare `@ServiceDescriptor`, expose via `@Injected` — no new SPI required.
