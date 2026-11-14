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
check fails CI. It also fails when the catalog does not hold exactly the principles P1 to P9 in
order, so a principle cannot be deleted unnoticed. Which CI job runs each check, and which checks
the branch rules require, is in
[Where each gate runs](../../scripts/guardrails/README.md#where-each-gate-runs-and-what-blocks-a-merge).

## P1. Module layering is fixed

**Rule:** Dependencies between BTrace modules follow one direction. `btrace-boot` and `btrace-core` import no other BTrace module; inside `btrace-agent`, `io.btrace.instr` does not import `io.btrace.agent`; `btrace-client` does not import `io.btrace.agent.*`; extensions import only `io.btrace.core.extensions.*`; `btrace-qe` and `integration-tests` do not import agent, instr or runtime classes. No string literal in a lower layer names a class of a higher one, and no Java package is split across modules.

**Why:** Nothing in the build checks it, so the exceptions accumulate quietly: extension implementations import BTrace types outside `io.btrace.core.extensions` (for example `OTelImpl`, `StatsdImpl`, `ExternalTypeTestServiceImpl`; `scripts/guardrails/baselines/layering.txt` lists them all), `BTraceRuntimeImplBase` names `io.btrace.agent.PerfReaderImpl` in a string literal, and the package `io.btrace.runtime` exists in both `btrace-core` and `btrace-runtime`.

**Enforced by:** `scripts/guardrails/check_layering.py`

**Exceptions:** `scripts/guardrails/layering_rules.json` holds the allowed project edges and the per-file exceptions (`ProbePrinter.java` imports `io.btrace.instr`; `Loader.java` names the agent's main class). `scripts/guardrails/baselines/layering.txt` lists the violations that predate the check; it only shrinks.

## P2. Masked-JAR placement is deliberate

**Rule:** The classes at the root of `btrace.jar` are a fixed list of packages and classes, and every type a root class references is another root class or a JDK type defined by the boot loader. Relocated SLF4J types count only as root classes, and JDK packages of modules that JDK 9+ defines to the platform or application loader (`java.sql`, `java.compiler` and others) do not count as bootstrap-visible.

**Why:** Root classes are visible to the bootstrap class loader, which cannot see the masked sections. A root class that references a masked type fails with `NoClassDefFoundError` inside the target application. Today `ExtensionContext` references `io.btrace.core.comm.Command`, which the distribution build excludes from the root, and the root annotation processor `ExternalTypeProcessor` references `java.compiler` types; javac loads it from the processor path, not through the boot class path. [Masked JAR architecture](MaskedJarArchitecture.md) describes the layout.

**Enforced by:** `btrace-dist/src/test/java/io/btrace/dist/BootstrapClosureTest.java`

**Exceptions:** `btrace-dist/src/test/resources/bootstrap-root-allowlist.txt` lists the packages and classes allowed at the root. `btrace-dist/src/test/resources/bootstrap-jdk-nonboot-packages.txt` lists the JDK packages that are not boot-loader visible on JDK 9+, with the command that generated it. `btrace-dist/src/test/resources/bootstrap-closure-baseline.txt` lists the references that predate the check, each with its reason; it only shrinks.

## P3. Main code uses only the Java 8 API

**Rule:** Main sources of every module on the Java 8 target compile against the Java 8 API, not just at source and target level 8.

**Why:** Source and target level 8 on a JDK 9+ compiler still accept JDK 9+ APIs, which then fail only on a Java 8 JVM. Before this was enforced, `btrace-client` held 24 such calls in `io.btrace.extcli` (`Path.of`, `Map.of`, `List.of`). `btracex` ships in the same distribution as a Java 8 agent. See [JDK compatibility](../JDKCompatibility.md).

**Enforced by:** `common.gradle`, `btrace-core/build.gradle`, `btrace-compiler/build.gradle`, `btrace-client/build.gradle`, `btrace-agent/build.gradle`, `btrace-runtime/build.gradle`, `btrace-boot/build.gradle`, `scripts/guardrails/check_layering.py`

**Exceptions:** `common.gradle` compiles main code with `--release 8`. `--release 8` omits `sun.misc`, `jdk.jfr` and the tools.jar APIs (`com.sun.source`, `com.sun.tools.attach`, `sun.jvmstat`), so `btrace-core`, `btrace-compiler`, `btrace-client`, `btrace-agent`, `btrace-runtime` and `btrace-boot` compile their main code with the JDK 8 compiler instead, which rejects JDK 9+ APIs too. Their `java9`, `java11` and `java24` source sets target later releases on purpose. `integration-tests` does not ship and opts out because its fixtures use `jdk.jfr`; it is the only entry under `release_opt_outs` in `scripts/guardrails/layering_rules.json`. `scripts/guardrails/check_layering.py` fails a module build script that clears or changes `options.release` without such an entry, and an entry for a module that no longer opts out.

## P4. Sibling implementations stay in step

**Rule:** When the same behavior exists twice, a change to one copy is a change to both or says why not. This covers the two instrumentation backends (ASM and the ClassFile API), the V1 and V2 wire command tables, every `Kind` handled by both backends, and the shell and batch launchers.

**Why:** The two backends drift apart when a fix lands in only one of them. 22d3f9a3 made the ASM backend apply a `Kind.LINE` probe only to the methods it selects, as the ClassFile API backend already did; c81a2d43 made the ASM backend measure `@Duration` at every return instead of only the first, which the ClassFile API backend already did. The `btracex` and `btracec` shell launchers lack `-XX:+AllowRedefinitionToAddDeleteMethods`, which their `.bat` twins pass. See [instrumentation backends](InstrumentationBackends.md). `check_launchers.py` compares the JVM flags and main class of each shell launcher and its `.bat` twin, `check_kind_dispatch.py` asserts that both backends dispatch every `Kind` constant (a handler whose kind a backend still has no code for is reported at warn, to the target log and the client, through `UnhandledKindReport`), and `CommandTableParityTest` asserts that the V1 and V2 wire command tables declare the same types and convert into each other. `check_pr_metadata.py` requires a `Sibling: <reason>` line in the description of a pull request that changes only one of the two backends (the ClassFile API backend is the `java24` source set of `btrace-agent`); it checks that a reason is present, not that it is adequate. The per-JDK runtime implementations (`BTraceRuntimeImpl_8` in `btrace-runtime`'s main source set, `BTraceRuntimeImpl_9` and `BTraceRuntimeImpl_11` in its `java9` and `java11` source sets) are kept in step by the compiler only.

**Enforced by:** `scripts/guardrails/check_launchers.py`, `scripts/guardrails/check_kind_dispatch.py`, `btrace-core/src/test/java/io/btrace/core/comm/CommandTableParityTest.java`, `scripts/guardrails/check_pr_metadata.py`

**Exceptions:** `scripts/guardrails/baselines/launchers.txt` lists the launcher differences that predate the check, each with its reason; it only shrinks. `HEARTBEAT` is the one command the V1 stream refuses: it exists on the V2 protocol only, and `CommandTableParityTest` fails if the V1 stream refuses any other type. A `Sibling: <reason>` line in the pull request description accepts a change to one backend, and pull requests opened by a bot are exempt from that rule.

## P5. Never fail open

**Rule:** A dropped handler, a failed transform or an ignored option is logged at warn or above and reaches the client; it is never swallowed. A `catch` of `Throwable`, `Exception` or `RuntimeException` does not end with an empty body, a debug or trace log, or a bare `return` or `continue`. `BTraceTransformer.reportFailure` is the model.

**Why:** Failing open looks like success. Each of these commits fixes a case of it: before 1d069ce0 a handler that fit no method of a matching class was skipped with at most a debug log line; before 9c4f4a63 a class that could not be instrumented was logged only in the target JVM and the client was told nothing; before 0962eaaf a failure to build a handler's level guard was caught and the unguarded handler returned, so a level-gated probe fired on every call. `python3 scripts/guardrails/check_swallow.py --all` lists the swallowing `catch` blocks that predate the check.

**Enforced by:** `scripts/guardrails/check_swallow.py`

**Exceptions:** `// swallow: <reason>` on the `catch` line.

## P6. Nothing disturbs the application thread

**Rule:** Probe handling must not change the behavior of the thread it runs on. `Thread.interrupted()` is never called in `btrace-runtime`, because it clears the application's interrupt flag. A failure in the transformer is reported to the client and `transform` returns `null`, so the class keeps its original bytes, instead of propagating into class loading.

**Why:** The comment in `CommandQueue` explains how clearing the flag from probe code loses an interrupt the application was waiting for.

**Enforced by:** `scripts/guardrails/check_runtime_safety.py` (no Thread.interrupted() call in btrace-runtime), `btrace-agent/src/test/java/io/btrace/instr/BTraceTransformerFailureReportTest.java` (a failing transform reports and returns null)

**Exceptions:** None. Coverage of the catch-all around guarded handlers is partial: golden data covers the ASM backend only.

## P7. One registry per extension point

**Rule:** Adding to an extension point means adding to every place that lists it. Each `Args` option appears in the agent usage text and in [QuickReference](../QuickReference.md); each method-targeting annotation is classified in the preprocessor's annotation sets; each message key used in code exists in `messages.properties`.

**Why:** Registries drift apart silently. 51ac8280 pins agent options that the agent drops without a word.

**Enforced by:** `btrace-core/src/test/java/io/btrace/core/ArgsRegistryTest.java`, `btrace-agent/src/test/java/io/btrace/instr/HandlerAnnotationClassificationTest.java`, `scripts/guardrails/check_message_keys.py`

**Exceptions:** `btrace-core/src/test/resources/args-registry-baseline.txt` (options missing from the usage text or QuickReference) and `btrace-agent/src/test/resources/handler-annotation-baseline.txt` (method annotations the preprocessor classifies in neither set).

## P8. Classes and public API do not grow by default

**Rule:** A main-source Java file over 1,000 lines does not get longer, and no new file starts over 1,000 lines. The list of public top-level types in `btrace-core` changes only deliberately. No new `...ForTest` or `...ForTests` method goes into main sources.

**Why:** Oversized files such as `btrace-client`'s `Client.java`, well over 1,000 lines, kept getting longer, and `btrace-core`'s public surface keeps growing; the current size exceptions, public type snapshot and hooks are the files named under Exceptions. Main sources already carry test-only hooks (`btrace-agent`'s `Client.setRuntimeForTest`, `BTraceRuntimeImplBase.speculativeQueueCountForTest`).

**Enforced by:** `scripts/guardrails/check_growth.py`

**Exceptions:** `scripts/guardrails/growth_allow.txt` (files allowed to grow past 1,000 lines), `scripts/guardrails/api/btrace-core-public-types.txt` (the public type snapshot, edited in the same change as the type), `scripts/guardrails/baselines/main-test-hooks.txt` (existing test hooks).

## P9. Code that ships is reachable

**Rule:** No speculative modules and no re-implemented helpers. Code that no entry point reaches is not merged; a helper that already exists is reused.

**Why:** Large amounts of code were merged and later deleted as unreachable: the fat-agent Maven module (#900), the MCP server moved out of the tree (#864) and the PCPP preprocessor (#861). `readAllBytes` exists in several copies (`Main`, `ClassHeaderReader`) and `updateMinMax` in the contracts and GPU-bridge extensions.

**Enforced by:** unenforced (judgment)

**Exceptions:** None. No dependency-free duplicate or dead-code detector is reliable enough to block a merge, so review carries this rule.
