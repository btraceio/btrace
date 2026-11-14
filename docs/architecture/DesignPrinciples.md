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

**Why:** Nothing in the build checks it, so the exceptions accumulate quietly: extension implementations import BTrace types outside `io.btrace.core.extensions` (for example `OTelImpl`, `StatsdImpl`, `ExternalTypeTestServiceImpl`; `scripts/guardrails/baselines/layering.txt` lists them all), `BTraceRuntimeImplBase` names `io.btrace.agent.PerfReaderImpl` in a string literal, and the package `io.btrace.runtime` exists in both `btrace-core` and `btrace-runtime`.

**Enforced by:** `scripts/guardrails/check_layering.py`

**Exceptions:** `scripts/guardrails/layering_rules.json` holds the allowed project edges and the per-file exceptions (`ProbePrinter.java` imports `io.btrace.instr`; `Loader.java` names the agent's main class). `scripts/guardrails/baselines/layering.txt` lists the violations that predate the check; it only shrinks.

## P2. Masked-JAR placement is deliberate

**Rule:** The classes at the root of `btrace.jar` are a fixed list of packages and classes, and every type a root class references is a JDK type, a relocated SLF4J type, or another root class.

**Why:** Root classes are visible to the bootstrap class loader, which cannot see the masked sections. A root class that references a masked type fails with `NoClassDefFoundError` inside the target application. Today `ExtensionContext` references `io.btrace.core.comm.Command`, which the distribution build excludes from the root. [Masked JAR architecture](MaskedJarArchitecture.md) describes the layout.

**Enforced by:** `btrace-dist/src/test/java/io/btrace/dist/BootstrapClosureTest.java`

**Exceptions:** `btrace-dist/src/test/resources/bootstrap-root-allowlist.txt` lists the packages and classes allowed at the root. `btrace-dist/src/test/resources/bootstrap-closure-baseline.txt` lists the references that predate the check, each with its reason; it only shrinks.

## P3. Main code uses only the Java 8 API

**Rule:** Main sources of every module on the Java 8 target compile against the Java 8 API, not just at source and target level 8.

**Why:** Source and target level 8 on a JDK 9+ compiler still accept JDK 9+ APIs, which then fail only on a Java 8 JVM. Before this was enforced, `btrace-client` held 24 such calls in `io.btrace.extcli` (`Path.of`, `Map.of`, `List.of`). `btracex` ships in the same distribution as a Java 8 agent. See [JDK compatibility](../JDKCompatibility.md).

**Enforced by:** `common.gradle`, `btrace-core/build.gradle`, `btrace-compiler/build.gradle`, `btrace-client/build.gradle`, `btrace-agent/build.gradle`, `btrace-runtime/build.gradle`, `btrace-boot/build.gradle`

**Exceptions:** `common.gradle` compiles main code with `--release 8`. `--release 8` omits `sun.misc`, `jdk.jfr` and the tools.jar APIs (`com.sun.source`, `com.sun.tools.attach`, `sun.jvmstat`), so `btrace-core`, `btrace-compiler`, `btrace-client`, `btrace-agent`, `btrace-runtime` and `btrace-boot` compile their main code with the JDK 8 compiler instead, which rejects JDK 9+ APIs too. Their `java9`, `java11` and `java24` source sets target later releases on purpose. `integration-tests` does not ship and opts out because its fixtures use `jdk.jfr`.

## P4. Sibling implementations stay in step

**Rule:** When the same behavior exists twice, a change to one copy is a change to both or says why not. This covers the two instrumentation backends (ASM and the ClassFile API), the V1 and V2 wire command tables, every `Kind` handled by both backends, and the shell and batch launchers.

**Why:** Fixes keep landing in only one of two siblings. 22d3f9a3 applied a `Kind.LINE` probe only to the methods it selects; c81a2d43 fixed `@Duration` at every return but the first. The `btracex` and `btracec` shell launchers lack `-XX:+AllowRedefinitionToAddDeleteMethods`, which their `.bat` twins pass. See [instrumentation backends](InstrumentationBackends.md). Three sibling pairs are enforced so far: `check_launchers.py` compares the JVM flags and main class of each shell launcher and its `.bat` twin, `check_kind_dispatch.py` asserts that both backends dispatch every `Kind` constant, and `CommandTableParityTest` asserts that the V1 and V2 wire command tables declare the same types and convert into each other. One-sided backend changes are not yet enforced.

**Enforced by:** `scripts/guardrails/check_launchers.py`, `scripts/guardrails/check_kind_dispatch.py`, `btrace-core/src/test/java/io/btrace/core/comm/CommandTableParityTest.java`

**Exceptions:** None.

## P5. Never fail open

**Rule:** A dropped handler, a failed transform or an ignored option is logged at warn or above and reaches the client; it is never swallowed. A `catch` of `Throwable`, `Exception` or `RuntimeException` does not end with an empty body, a debug or trace log, or a bare `return` or `continue`. `BTraceTransformer.reportFailure` is the model.

**Why:** Failing open looks like success. 1d069ce0 logged handler mismatches at debug only, 9c4f4a63 did the same for classes that could not be instrumented, and 0962eaaf caught the failure to build a level guard and returned the unguarded handler. Silent `catch` blocks are common in main sources.

**Enforced by:** `scripts/guardrails/check_swallow.py`

**Exceptions:** `// swallow: <reason>` on the `catch` line.

## P6. Nothing disturbs the application thread

**Rule:** Probe handling must not change the behavior of the thread it runs on. `Thread.interrupted()` is never called in `btrace-runtime`, because it clears the application's interrupt flag. A failure in the transformer reports and returns the original bytes instead of propagating into class loading.

**Why:** The comment in `CommandQueue` explains how clearing the flag from probe code loses an interrupt the application was waiting for.

**Enforced by:** unenforced (judgment)

**Exceptions:** None. Coverage of the catch-all around guarded handlers is partial: golden data covers the ASM backend only.

## P7. One registry per extension point

**Rule:** Adding to an extension point means adding to every place that lists it. Each `Args` option appears in the agent usage text and in [QuickReference](../QuickReference.md); each method-targeting annotation is classified in the preprocessor's annotation sets; each message key used in code exists in `messages.properties`.

**Why:** Registries drift apart silently. 51ac8280 pins agent options that the agent drops without a word.

**Enforced by:** `btrace-core/src/test/java/io/btrace/core/ArgsRegistryTest.java`, `btrace-agent/src/test/java/io/btrace/instr/HandlerAnnotationClassificationTest.java`, `scripts/guardrails/check_message_keys.py`

**Exceptions:** `btrace-core/src/test/resources/args-registry-baseline.txt` (options missing from the usage text or QuickReference) and `btrace-agent/src/test/resources/handler-annotation-baseline.txt` (method annotations the preprocessor classifies in neither set).

## P8. Classes and public API do not grow by default

**Rule:** A main-source Java file over 1,000 lines does not get longer, and no new file starts over 1,000 lines. The list of public top-level types in `btrace-core` changes only deliberately. No new `...ForTest` method goes into main sources.

**Why:** `btrace-client`'s `Client.java` is 1,838 lines, and `btrace-core`'s public surface keeps growing. Main sources already carry test-only hooks (`Client.setRuntimeForTest`, `BTraceRuntimeImplBase.speculativeQueueCountForTest`).

**Enforced by:** unenforced (judgment)

**Exceptions:** None.

## P9. Code that ships is reachable

**Rule:** No speculative modules and no re-implemented helpers. Code that no entry point reaches is not merged; a helper that already exists is reused.

**Why:** Large amounts of code were merged and later deleted as unreachable: the fat-agent Maven module (#900), the MCP server moved out of the tree (#864) and the PCPP preprocessor (#861). `readAllBytes` exists in several copies (`Main`, `ClassHeaderReader`) and `updateMinMax` in the contracts and GPU-bridge extensions.

**Enforced by:** unenforced (judgment)

**Exceptions:** None. No dependency-free duplicate or dead-code detector is reliable enough to block a merge, so review carries this rule.
