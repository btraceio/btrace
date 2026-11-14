# Design principles

The structural rules BTrace is built around, the change that shows what goes wrong without each,
and what enforces it. A principle that no check enforces says so: it is a judgment call that
review has to make.

Each section has the same four fields:

- **Rule** states the rule.
- **Why** names the commits and pull requests (plain text, `git show <hash>`) behind the rule.
- **Enforced by** is either `unenforced (judgment)` or one or more backticked repository paths to
  the check or test that fails when the rule is broken.
- **Exceptions** names the escape marker or baseline file, if there is one.

`scripts/guardrails/check_agent_config.py` verifies that every path under **Enforced by** exists
and that every `scripts/guardrails/check_*.py` is named here or in the
[guardrails README](../../scripts/guardrails/README.md), so a catalog that points at a deleted
check fails CI.

## P1. Module layering is fixed

**Rule:** Dependencies between BTrace modules follow one direction. `btrace-boot` and `btrace-core` import no other BTrace module; inside `btrace-agent`, `io.btrace.instr` does not import `io.btrace.agent`; `btrace-client` does not import `io.btrace.agent.*`; extensions import only `io.btrace.core.extensions.*`; `btrace-qe` and `integration-tests` do not import agent, instr or runtime classes. No string literal in a lower layer names a class of a higher one, and no Java package is split across modules.

**Why:** Nothing in the build checks it, so the exceptions accumulate quietly: three extensions import non-extension core types (`OTelImpl`, `StatsdImpl`, `ExternalTypeTestServiceImpl`), two lower-layer classes name agent classes in string literals (`BTraceRuntimeImplBase`, `Preprocessor`), `ExtensionBridgeImpl` reaches across layers reflectively, and `io.btrace.runtime` exists in both `btrace-core` and `btrace-runtime`.

**Enforced by:** unenforced (judgment)

**Exceptions:** None.

## P2. Masked-JAR placement is deliberate

**Rule:** The classes at the root of `btrace.jar` are a fixed list of packages and classes, and every type a root class references is a JDK type, a relocated SLF4J type, or another root class.

**Why:** Root classes are visible to the bootstrap class loader, which cannot see the masked sections. A root class that references a masked type fails with `NoClassDefFoundError` inside the target application. Today `ExtensionContext` references `io.btrace.core.comm.Command`, which the distribution build excludes from the root. [Masked JAR architecture](MaskedJarArchitecture.md) describes the layout.

**Enforced by:** unenforced (judgment)

**Exceptions:** None.

## P3. Main code uses only the Java 8 API

**Rule:** Main sources of every module on the Java 8 target compile against the Java 8 API, not just at source and target level 8.

**Why:** `btrace-core`, `btrace-compiler` and `btrace-client` compile with a JDK 11 compiler at source and target 8 without `--release`, so JDK 9+ APIs compile and fail only on a Java 8 target JVM: `Map.of` in `ExtensionLister` and `PolicyFile`, `List.of` in `ExtRepoBrowser`. `btracex` ships in the same distribution as a Java 8 agent. See [JDK compatibility](../JDKCompatibility.md).

**Enforced by:** unenforced (judgment)

**Exceptions:** None.

## P4. Sibling implementations stay in step

**Rule:** When the same behavior exists twice, a change to one copy is a change to both or says why not. This covers the two instrumentation backends (ASM and the ClassFile API), the V1 and V2 wire command tables, every `Kind` handled by both backends, and the shell and batch launchers.

**Why:** About nine fixes in a year landed in one of two siblings. 22d3f9a3 applied a `Kind.LINE` probe only to the methods it selects; c81a2d43 fixed `@Duration` at every return but the first. The `btracex` and `btracec` shell launchers lack `-XX:+AllowRedefinitionToAddDeleteMethods`, which their `.bat` twins pass. A probe kind unhandled by a backend falls through without failing. See [instrumentation backends](InstrumentationBackends.md).

**Enforced by:** unenforced (judgment)

**Exceptions:** None.

## P5. Never fail open

**Rule:** A dropped handler, a failed transform or an ignored option is logged at warn or above and reaches the client; it is never swallowed. A `catch` of `Throwable`, `Exception` or `RuntimeException` does not end with an empty body, a debug or trace log, or a bare `return` or `continue`. `BTraceTransformer.reportFailure` is the model.

**Why:** Failing open looks like success. 1d069ce0 logged handler mismatches at debug only, 9c4f4a63 did the same for classes that could not be instrumented, and 0962eaaf caught the failure to build a level guard and returned the unguarded handler. The number of silent `catch` blocks in main sources grew from 35 to 133 in a year.

**Enforced by:** unenforced (judgment)

**Exceptions:** None.

## P6. Nothing disturbs the application thread

**Rule:** Probe handling must not change the behavior of the thread it runs on. `Thread.interrupted()` is never called in `btrace-runtime`, because it clears the application's interrupt flag. A failure in the transformer reports and returns the original bytes instead of propagating into class loading.

**Why:** The comment in `CommandQueue` explains how clearing the flag from probe code loses an interrupt the application was waiting for. A transformer that throws aborts the target's class definition.

**Enforced by:** unenforced (judgment)

**Exceptions:** None. Coverage of the catch-all around guarded handlers is partial: golden data covers the ASM backend only.

## P7. One registry per extension point

**Rule:** Adding to an extension point means adding to every place that lists it. Each `Args` option appears in the agent usage text and in [QuickReference](../QuickReference.md); each method-targeting annotation is classified in the preprocessor's annotation sets; each message key used in code exists in `messages.properties`.

**Why:** Registries drift apart silently. The agent usage text omits `agentJar`, `libs` and `probes`, QuickReference omits `agentJar` and `libs`, and 51ac8280 fixed three agent options that were accepted and ignored.

**Enforced by:** unenforced (judgment)

**Exceptions:** None.

## P8. Classes and public API do not grow by default

**Rule:** A main-source Java file over 1,000 lines does not get longer, and no new file starts over 1,000 lines. The list of public top-level types in `btrace-core` changes only deliberately. No new `...ForTest` method goes into main sources.

**Why:** `Client.java` grew from 821 to 1,838 lines in a year and the public types in `btrace-core` from 80 to 151. Eight main-source files already carry a test-only hook.

**Enforced by:** unenforced (judgment)

**Exceptions:** None.

## P9. Code that ships is reachable

**Rule:** No speculative modules and no re-implemented helpers. Code that no entry point reaches is not merged; a helper that already exists is reused.

**Why:** About 7,000 lines were merged and later deleted as unreachable: the fat-agent Maven module (#900), the MCP server moved out of the tree (#864) and the PCPP preprocessor (#861). `readAllBytes` and `updateMinMax` each exist in more than one copy.

**Enforced by:** unenforced (judgment)

**Exceptions:** None. No dependency-free duplicate or dead-code detector is reliable enough to block a merge, so review carries this rule.
