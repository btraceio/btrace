# Architecture and design guardrails — design

Status: **approved and implemented** on branch `claude/btrace-agentic-guardrails-1b3753`, based on
`origin/develop` c968df58. The sections below are the design as approved and are left as written;
where execution disproved a premise or changed the design, the last section,
[As built (2026-11-14)](#as-built-2026-11-14), records what was done instead and in which commit.

Evidence key: **[V]** checked by hand in this worktree; **[S]** reported by a research pass over the
code and the last twelve months of history, not re-derived. Every [S] number must be re-measured by
the gate that depends on it before that gate is made blocking.

## 1. Intent

Generated code should keep BTrace's structure and design without the maintainer rewriting it. The
existing guardrails (`scripts/guardrails/`, G1–G5, see
[the November proposal](../plans/2026-11-04-agent-guardrails-proposal.md)) cover process and hygiene:
agent configuration, fully-qualified names, documented commands, PR metadata. Nothing checks
architecture: there is no dependency, layering, packaging-closure or parity check in any Gradle file,
workflow or script **[S]**.

The drift is measurable **[S]**: fixes landing in one of two sibling implementations (about 9 in a
year); silent `catch` blocks up from 35 to 133; `client/Client.java` 821 → 1,838 lines; `btrace-core`
public types 80 → 151; about 7,000 lines merged and later deleted as unreachable.

## 2. Goals, non-goals, anti-goals

**Goals**

- Each structural rule that can be checked is a blocking check, proven to fail on bad input.
- One catalog lists every principle, the case behind it, and what enforces it — or says it is
  unenforced.
- A rule with existing violations starts from a recorded baseline that can only shrink.

**Non-goals**

- Fixing the existing violations. Baselines record them; fixes are separate changes.
- Running the golden instrumentation set through both backends. That is item 10 of the November
  proposal and has its own plan; this design only adds the cheap exhaustiveness checks around it.
- Behaviour changes (for example rejecting unknown agent options). Findings are listed in section 8.
- A review agent or new skill. The November baselines showed no agent failure a skill would fix.

**Anti-goals**

- A gate that cannot fail, or whose baseline can be extended without the maintainer seeing it.
- Advisory text standing in for a check that could have been written.
- A new build dependency (no ArchUnit, no animal-sniffer).

## 3. Approaches considered

| Approach | Trade-off | Outcome |
| --- | --- | --- |
| Gates only | Strong where a rule is checkable; silent on judgment rules and on why a rule exists | Not chosen |
| Principles document plus a review agent | Covers judgment rules, but advisory; the November proposal recorded advisory rules violated five times each | Not chosen |
| **Gates plus a catalog** | Gates as above; the catalog gives agents the reasoning and keeps the unenforced remainder visible | **Chosen by the maintainer, 2026-11-14** |

## 4. The catalog

`docs/architecture/DesignPrinciples.md`, linked from `AGENTS.md` (reference map) and `docs/README.md`.
One section per principle, each with exactly these fields:

- **Rule** — one or two sentences.
- **Why** — the commit, PR or bug that shows what goes wrong without it.
- **Enforced by** — a path to a check or test, or the literal text `unenforced (judgment)`.
- **Exceptions** — the escape marker or baseline file, if any.

`check_agent_config.py` (G1) is extended: every path under "Enforced by" must exist, and every check
in `scripts/guardrails/` must be named by at least one principle or by the existing README table. A
catalog that points at a deleted check fails CI.

`AGENTS.md` gains one line under "Non-negotiable rules" pointing at the catalog; the rules themselves
are not copied there.

## 5. Principles and their gates

Gate names continue the existing series. "Python" means a check in `scripts/guardrails/` with hermetic
tests under `tests/`, run in the `guardrails` CI job. "JUnit" means a test in the existing Gradle build.

### P1. Module layering is fixed — G6 `check_layering.py` (Python)

Rules, all read from source and build scripts, no build needed:

- The set of `project(...)` edges in every `build.gradle` equals a checked-in allowlist.
- `btrace-boot` and `btrace-core` import no other BTrace module.
- Inside `btrace-agent`, `io.btrace.instr` does not import `io.btrace.agent`.
- `btrace-client` does not import `io.btrace.agent.*`; it imports `io.btrace.instr` only from
  `ProbePrinter.java`.
- Extensions import only `io.btrace.core.extensions.*` from BTrace.
- `btrace-qe` and `integration-tests` do not import `io.btrace.{agent,instr,runtime}`.
- No string literal names a class of a higher layer (`"io.btrace.agent.…"` in runtime, core or instr).
- No Java package is split across modules.

Baseline **[S]**: 3 extension imports (`OTelImpl`, `StatsdImpl`, `ExternalTypeTestServiceImpl`), 2
reflective names (`BTraceRuntimeImplBase.java:1527`, `Preprocessor.java:1507`), 1 reflective escape in
core (`ExtensionBridgeImpl.java:44`), 1 split package (`io.btrace.runtime` in core and btrace-runtime).
`Loader.java:62-63` is by design and goes in the allowlist, not the baseline.

### P2. Masked-JAR placement is deliberate — G7 bootstrap closure (JUnit, `btrace-dist`)

Runs against the built `btrace.jar`, beside `BTraceJarPackagingTest`:

- Root `.class` entries match a checked-in list of packages and classes.
- Every type referenced from a root class is a JDK type, a relocated-slf4j type, or another root class.
  References are read from the constant pool with the ASM version the build already uses.

Baseline: `ExtensionContext` → `io.btrace.core.comm.Command` **[V]** (`ExtensionContext.java:20`;
`core/comm` is excluded from the root at `btrace-dist/build.gradle:395`).

`MaskedJarArchitecture.md` is corrected in the same change where it disagrees with the build
(`core/extensions` placement, `Boot-Class-Path` value) **[S]**.

### P3. Main code uses only the Java 8 API — G8 compiler setting

`btrace-core`, `btrace-compiler` and `btrace-client` compile with a JDK 11 compiler at source/target 8
and no `--release` **[V]** (`common.gradle:61-64`), so JDK 9+ APIs compile. Three are in the tree
**[V]**: `Map.of` in `ExtensionLister.java:44` and `PolicyFile.java:114`, `List.of` in
`ExtRepoBrowser.java:73`.

Mechanism, in order of preference: `options.release = 8`; if that rejects the internal APIs these
modules use (`sun.misc.Unsafe` in `core/BTraceRuntime.java`), compile them with the JDK 8 toolchain as
`btrace-agent`, `btrace-runtime` and `btrace-boot` already do. The builder tries the first and records
which one held. The three call sites are fixed in the same change (my call: `btracex` ships in the
same distribution as a Java 8 agent, so it stays Java 8; see decision 9.1).

Scope: the setting goes in `common.gradle`, so it covers every module on the Java 8 target, including
`btrace-dtrace` and the extensions (`btrace-contracts` and `btrace-utils` repeat the block and are
changed with it). A module that does not ship in the distribution (`btrace-qe`, `benchmarks`) may opt
out with a one-line comment giving the reason. The builder counts the call sites this surfaces before
choosing between fixing and opting out, and reports the count in the PR.

### P4. Sibling implementations stay in step — G9 (JUnit + Python)

- **Probe kinds (Python, `check_kind_dispatch.py`):** a source scan asserts every constant of the `Kind`
  enum is named in the dispatch of both backends (`Instrumentor.java:281` switch,
  `ClassFileApiBackend.java:208-224` chain). It needs no JDK 24+ toolchain and no production change.
  Instrumenting one fixture per kind through both backends is the both-backends golden run and stays
  out of scope. The fail-open fallthrough itself is a production fix, listed in section 8.
- **Wire commands (JUnit, `btrace-core`):** a test over `FIRST_COMMAND..LAST_COMMAND` asserts the V1
  and V2 tables agree, every type has a V2 factory, and each round-trips through `CommandAdapter`.
  `HEARTBEAT` is the one declared V2-only exception.
- **Launchers (Python, `check_launchers.py`):** each `bin/X` and `bin/X.bat` pass the same JVM flags and
  main class. Baseline **[V]**: `btracex` and `btracec` lack `-XX:+AllowRedefinitionToAddDeleteMethods`
  that their `.bat` twins have.
- **One-sided changes (G5 extension):** a PR that changes one instrumentation backend and not the other
  must carry a `Sibling:` line in its description saying why. Presence is checked; adequacy is not.
  The two sides are explicit path lists inside the check: the ClassFile API side is
  `btrace-agent/src/main/java24/**`; the ASM side starts narrow, as `Instrumentor*.java` and
  `AsmInstrumentationBackend.java` plus the files only the ASM backend references, which the builder
  derives and records. Backend-neutral code in `io.btrace.instr` (`ClassMeta`, `BackendSelector`, the
  probe model) is on neither list. Bot-authored PRs are exempt, as they are from the Testing rule.

Runtime variants `_8/_9/_11` stay compiler-checked only; `_9` has no CI lane (decision 9.4).

### P5. Never fail open — G10 `check_swallow.py` (Python, added lines only)

Modelled on `check_fqn.py`. A newly added `catch (Throwable|Exception|RuntimeException …)` in main
sources fails when its body is empty, logs only at debug or trace, or is a bare `return`/`continue`,
unless the line ends with `// swallow: <reason>`. Existing catches are not touched, so no baseline is
needed. The catalog states the positive rule: a dropped handler, failed transform or ignored option is
logged at warn or above and reaches the client (`BTraceTransformer.reportFailure` is the model).

### P6. Nothing disturbs the application thread — G11 (Python + JUnit)

- Python: `Thread.interrupted()` is banned in `btrace-runtime` main sources (`CommandQueue.java:57-62`
  explains why).
- JUnit: `BTraceTransformer.transform` returns null and reports when the backend throws.
- The catch-all around guarded handlers is covered by golden data for ASM only; full coverage waits for
  the both-backends golden run. The catalog says so.

### P7. One registry per extension point — G12 (JUnit)

- Every constant in `core/Args.java` appears in the `btrace.agent.usage` text and in
  `docs/QuickReference.md`. Baseline **[S]**: usage omits `agentJar`, `libs`, `probes`; QuickReference
  omits `agentJar`, `libs` (both constants were later removed).
- Every method-targeting annotation in `io.btrace.core.annotations` is in `RT_AWARE_ANNOTS`,
  `GUARDED_ANNOTS`, or an explicit unguarded list (`Preprocessor.java:170-180`).
- Every message key used in code exists in `messages.properties`.

### P8. Classes and public API do not grow by default — G13 ratchets (Python)

- **File size:** compared against the base commit, like G2. A main-source Java file that is over 1,000
  lines at HEAD and longer than at the base fails; so does a new file over 1,000 lines. No sizes are
  recorded, so shrinking a file never needs a baseline edit. A deliberate exception is a line in an
  allowlist file with its reason, which code-owner review covers.
- **Public API:** the list of public top-level types in `btrace-core` is a checked-in snapshot; adding
  one means editing the snapshot in the same change.
- **Test hooks:** no new `…ForTest` method in main sources; the 8 existing files are the baseline.

### P9. Code that ships is reachable — unenforced (judgment)

No speculative modules, no re-implemented helpers. The catalog records the cases (#900, #864, #861; the
`readAllBytes` and `updateMinMax` duplicates) and is honest that nothing checks it. A duplicate-helper
or dead-code detector is deliberately left out: no dependency-free version is reliable enough to block.

## 6. Baselines

One format for every gate: a text file under `scripts/guardrails/baselines/`, one violation per line,
with a `#` comment naming the reason. A gate fails on a violation that is not listed **and** on a
listed entry that no longer occurs, so a fixed violation must be removed and the file only shrinks.
`.github/CODEOWNERS` already routes `scripts/guardrails/` to the maintainer, which covers baselines,
allowlists and the API snapshot.

## 7. Delivery

Stacked PRs, one gate each, as in the previous round. Every PR shows its gate failing: the check's own
tests against a bad fixture, plus one real violation taken from history or the baseline.

| # | Change | Proof in the same PR |
| --- | --- | --- |
| 1 | Catalog, with every principle marked `unenforced` until its gate lands; G1 extension | Catalog pointing at a missing path fails |
| 2 | G6 layering | Fixture importing `io.btrace.agent` from client fails; baseline entry removed → fails |
| 3 | G8 Java 8 API | `List.of` re-added to `btrace-core` fails to compile |
| 4 | G10 swallowed failures | Fixture diff reproducing the `ClassFileApiBackend.java:225` debug-only catch fails |
| 5 | G9 launchers, then kinds and commands | Flag removed from one `.bat` fails; fixture backend missing a `Kind` arm fails |
| 6 | G7 bootstrap closure | A `core/comm` reference added to a root class fails |
| 7 | G12 registries | An `Args` constant without usage text fails |
| 8 | G13 ratchets; G11; G5 `Sibling:` line | One line added to `Client.java` fails |

Order puts the gates that are green today and cheap first. JUnit gates and the G8 compiler setting run
in the `build` job. Python gates run in the `guardrails` job, which feeds `tests-passed`; the
`Sibling:` line runs in `pr-metadata.yml`.

**What actually blocks a merge.** The "Merging" ruleset required only `build` when read on 2026-11-14
**[V]** (`gh api repos/btraceio/btrace/rulesets/26315659`, last updated 2026-11-03). Until
`tests-passed` and `pr-metadata` are added, every Python gate here, G1–G5 before it, and the
`Sibling:` line report a failure but cannot stop a merge. The maintainer is adding them (decision 9.6).

## 8. Findings recorded while building the gates

Each item says whether it has been resolved since.

- Resolved — agent ⇄ compiler dependency cycle: broken. `btrace-agent` no longer has `implementation` on
  `btrace-compiler`; the compiler is a build tool there (`testProbeCompiler`, for `compileTestProbes`),
  so `check_layering.py` still lists the `btrace-agent -> btrace-compiler` edge. The removed edge had
  been putting the agent's own jar, and with it the `java24` ClassFile API backend, on the agent's
  test classpath. `btrace-compiler`'s `runtimeOnly` on `btrace-agent` stays: the compiler finds the
  agent's `PackGenerator` through `ServiceLoader`, and it brings `btrace-runtime` with it. The
  compiler's own `implementation` on `btrace-runtime` was unused (its one `io.btrace.runtime` import,
  `BTraceRuntimeAccess`, was in `btrace-core`; the class is now `io.btrace.core.BTraceRuntimeAccess`)
  and is removed; `btrace.jar` is unchanged.
- Open — classes present both in the JAR root and in a masked section. Re-measured on a fresh JAR:
  159 root/masked duplicates, plus 70 agent/shared and 94 client/shared, all byte-identical and
  harmless under parent-first loading. Removing them changes which section a class ships in and is
  left to the maintainer. `MaskedClassLoader`'s Javadoc, which said section-first, is corrected.
- Resolved — `ExtensionIndy` holds an SLF4J logger while a comment in `IndyDispatcher` said bootstrap
  classes must not. Relocated SLF4J and its provider are always in the root, so logging there works;
  the `IndyDispatcher` comment now gives the real reasons that class stays silent.
- Resolved — the extension API validator (`validateServiceApis`) had no negative tests. It has them
  now, and `docs/ExtensionInterfaceRules.md` describes what the validator enforces today, including
  what only warns.
- The November proposal still lists the `BackendSelector` debug-level fallback as open; it now warns
  and throws **[S]**.

## 9. Decisions made here as the designer's call

1. `btracex` and the `extcli` package stay on the Java 8 API.
2. Duplicated root/masked classes are not gated; only the root allowlist and the reference closure are.
3. The file-size threshold is 1,000 lines.
4. `BTraceRuntimeImpl_9` gets no new gate; whether to delete it or add a JDK 9 lane is left open.
5. The catalog lives in `docs/architecture/`, not `internal/`, because contributors and agents read it.
6. **Decided by the maintainer, 2026-11-14:** he adds `tests-passed` and `pr-metadata` to the
   ruleset's required checks (a repository setting). The Python checks stay in the `guardrails` job.
   Each gate PR re-reads the ruleset and states whether its check can block yet.
7. The probe-kind check is a source scan, not an instrumentation test (after the builder read).
8. G8 covers every module on the Java 8 target, with opt-outs only for modules that do not ship.
9. The file-size ratchet compares against the base commit and keeps no size baseline.

## 10. Left to the builder

- Exact Python structure and fixture layout, following the existing checks.
- Whether G8 uses `--release 8` or the JDK 8 toolchain, by trying both.
- The wording of each catalog entry, within the four fields of section 4.
- Re-measuring every **[S]** baseline number when its gate is written.

## As built (2026-11-14)

What execution changed against the sections above. Commit hashes are on this branch; counts were
read from the named files when this section was written, and the files are authoritative.

- **G8, Java 8 API (section 5, P3).** The tree held 24 Java 9+ call sites, not 3, all in
  `btrace-client`'s `io.btrace.extcli` (21 `Path.of`, 2 `Map.of`, 1 `List.of`); all were replaced
  with Java 8 equivalents. `options.release = 8` rejected the internal APIs of `btrace-core`,
  `btrace-compiler` and `btrace-client`, so those three compile their main code with the JDK 8
  toolchain, like `btrace-agent`, `btrace-runtime` and `btrace-boot`; `common.gradle` sets
  `--release 8` only for compile tasks whose compiler is JDK 9+. `integration-tests` opts out (its
  fixtures use `jdk.jfr`). f7fec3a6.
- **G7, bootstrap closure (P2).** The relocated-SLF4J exemption was removed: relocated SLF4J types
  are bootstrap-visible only as root classes. The JDK prefix rule subtracts a deny-list of packages
  that JDK 9+ defines to the platform or application loader,
  `btrace-dist/src/test/resources/bootstrap-jdk-nonboot-packages.txt`, so the root annotation
  processor's `java.compiler` references are baselined instead of passing. 4a0ac1fa (gate added in
  af95fdd4). The deny-list lines are shape-checked since 7f86a07b.
  The annotation processor's 23 baseline entries were not violations awaiting a fix: javac's
  processor path reads only `.class` entries, so the processor has to sit at the root, and nothing
  the bootstrap loader links references it. The allowlist now marks it
  `tool-only ... uses javax/annotation/processing/ javax/lang/model/ javax/tools/`; a tool-only class
  may reference its tool API packages, and a bootstrap root class that references a tool-only class
  fails regardless of the baseline. The 23 entries were removed.
  The deny-list, generated once on Temurin 21, is now checked against the JDK running the test: on
  JDK 9+ `jdkDenyListCoversTheRunningJdk` reads the boot module layer and fails on a non-boot
  package under the JDK prefixes that the list misses (34 such packages on JDK 21 and 26, all
  listed); on JDK 8 it is reported as skipped. `:btrace-dist:test` runs on the Gradle JDK, 21 in CI.
- **G12, handler annotations (P7).** No unguarded-annotation set was added to production code;
  `Preprocessor` changed visibility only. `HandlerAnnotationClassificationTest` keeps the non-handler
  annotations (`Location`, `Sampled`) in a test-side `NOT_HANDLERS` list, and
  `btrace-agent/src/test/resources/handler-annotation-baseline.txt` held `OnLowMemory`, a handler
  in neither preprocessor set with no recorded reason. fd1db7b4. `OnLowMemory` is now in
  `RT_AWARE_ANNOTS` next to `OnExit` (the runtime enters before calling both, so a guard would
  refuse to run them), and the baseline is empty.
- **G9, wire commands (P4).** `CommandAdapter` converts `HEARTBEAT` both ways; the V2-only exception
  is that the V1 stream (`WireIO`) refuses it. `CommandTableParityTest` converts every type and
  asserts the V1 stream refuses exactly `HEARTBEAT`. 0a75ec8b.
- **G11, transform failure (P6).** The JUnit half is the existing
  `BTraceTransformerFailureReportTest`, which already asserted that a failing transform returns
  `null` and reports to the client; the duplicate test added in 4c2f7228 was removed in d92ab830.
- **G12, message keys (P7).** The check is Python, `scripts/guardrails/check_message_keys.py`, not
  JUnit. fd1db7b4.
- **G5, one-sided backend changes (P4).** `ASM_BACKEND` in `scripts/guardrails/check_pr_metadata.py`
  lists 21 files, derived by `scripts/guardrails/derive_asm_backend.py`; a test fails when the two
  differ. f29daacd, with the single-line `Sibling:` fix in 41bed3be.
- **Baselines (section 6).** There is no single location. Python baselines are under
  `scripts/guardrails/baselines/` (`launchers.txt`, `layering.txt`, `main-test-hooks.txt`); the size
  allowlist is `scripts/guardrails/growth_allow.txt`, the API snapshot
  `scripts/guardrails/api/btrace-core-public-types.txt`, and the layering rules with their per-file
  exceptions `scripts/guardrails/layering_rules.json`. Java baselines are test resources of their
  module: `bootstrap-root-allowlist.txt`, `bootstrap-closure-baseline.txt` and
  `bootstrap-jdk-nonboot-packages.txt` in `btrace-dist`, `args-registry-baseline.txt` in
  `btrace-core`, `handler-annotation-baseline.txt` in `btrace-agent`. Every Python baseline entry
  must carry a `# reason` since 9797bc31, as the Java baselines did from the start.
- **Baseline sizes**, entries per file: `launchers.txt` 3 (24da36d3; the spec named two launchers,
  the third entry is `btracex`'s missing `--add-exports`); `layering.txt` 20 (0459ee82, 868e3412), then 17: the `Preprocessor` literal was removed by
  calling `ExtensionLoader.instance()` directly, and the `ExtensionBridgeImpl` and
  `BTraceRuntimeImplBase` literals, both deliberate late binding, became per-file exceptions in
  `layering_rules.json`; later 6, then 5 once `BTraceRuntimeAccess` and `LinkingFlag` moved from
  `io.btrace.runtime` to `io.btrace.core` and the split package was gone;
  `main-test-hooks.txt` 10 entries in 9 files, not 8 files, after the rule was widened to
  `...ForTests` and overloads (d7079ef0); `growth_allow.txt` none; the API snapshot 151 types
  (6fd59435); `bootstrap-root-allowlist.txt` 21 lines, then 19 when those two
  classes left `io/btrace/runtime/` for the already listed `io/btrace/core/`; `bootstrap-closure-baseline.txt` 24 (the
  `ExtensionContext` reference plus 23 from the annotation processor; af95fdd4, 4a0ac1fa), then 1
  once the processor became a tool-only allowlist line;
  `bootstrap-jdk-nonboot-packages.txt` 54 packages; `args-registry-baseline.txt` 9 at first, against
  the spec's 5, then 4 once `probes`, `output` and `help` were documented, then empty once the two
  remaining entries, `agentJar` (no reader) and `libs` (removed), were deleted along with their
  `Args` constants and the agent's `libs` error message; `handler-annotation-baseline.txt` 1, then empty once `OnLowMemory`
  was classified.
- **What blocks a merge (section 7).** The maintainer updated the "Merging" ruleset on 2026-11-14:
  it now requires `build`, `tests-passed` and `pr-metadata`, with `require_code_owner_review: false`.
  `scripts/guardrails/README.md` records the reading and which job runs each gate.
- **Delivery (section 7).** Delivered so far as one branch of commits, one or more per gate, not as
  stacked PRs; how they are split into PRs is the maintainer's call.
- **Final review fixes.** 9797bc31 moved the helpers the checks had copied into
  `scripts/guardrails/_common.py` and closed vacuous passes (catalog must hold P1 to P9; launchers
  need a main class or a `-javaagent:` launch; `Thread::interrupted` is matched). 7f86a07b declared
  `docs/QuickReference.md` as an input of `:btrace-core:test` so the build cache cannot skip
  `ArgsRegistryTest`. 1df91df9 put the catalog and the four tests that hold exception lists under
  code-owner review.
