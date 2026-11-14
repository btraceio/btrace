# Architecture and Design Guardrails Implementation Plan

> **Executed 2026-11-14.** This plan is kept as written. Some files it names were renamed or
> removed during execution (for example `test-hooks.txt` is `main-test-hooks.txt`,
> `BTraceTransformerFailureTest` was dropped for the existing `BTraceTransformerFailureReportTest`,
> and there is no `UNGUARDED_ANNOTS`), and some premises were wrong (24 Java 9+ call sites, the
> `HEARTBEAT` exception). The spec's
> [As built (2026-11-14)](../specs/2026-11-14-architecture-guardrails-design.md#as-built-2026-11-14)
> section lists every deviation with its commit.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn BTrace's structural and design rules into blocking CI checks plus one catalog that says what enforces each rule.

**Architecture:** Source-level rules are Python checks in `scripts/guardrails/`, run by the existing `guardrails` CI job. Bytecode- and API-level rules are JUnit tests and one compiler setting in the Gradle build. Rules with existing violations start from a shrink-only baseline file. `docs/architecture/DesignPrinciples.md` lists every principle and its enforcing check, and G1 keeps that list honest.

**Tech Stack:** Python 3 standard library and `unittest` (no third-party packages); JUnit 5; Gradle; ASM (already in the version catalog) on the `btrace-dist` test classpath only.

**Spec:** [internal/specs/2026-11-14-architecture-guardrails-design.md](../specs/2026-11-14-architecture-guardrails-design.md). Read it first; section numbers below refer to it.

## Global Constraints

- No new build dependency. ASM as `testImplementation` in `btrace-dist` is the one addition and uses the existing catalog entry (`libs.asm`).
- Existing violations are not fixed here, except the three Java 9+ call sites in Task 3. Everything else goes in a baseline.
- No behaviour change to product code. If a gate seems to need one, stop and report.
- Every **[S]** number in the spec is re-measured by the gate before it is recorded in a baseline. Record what the gate finds, and say in the PR where it differs from the spec.
- Every gate is shown failing: its own tests against a bad fixture, plus the real-violation proof named in the task. Follow `AGENTS.md` on this: revert or break only the thing under test.
- All CI scripts are invoked as `python3 -I <script>`. `-I` removes the script's directory from `sys.path`, so a check that imports a sibling module must first insert its own directory into `sys.path`.
- Check tests follow `scripts/guardrails/tests/test_check_fqn.py`: build a throw-away tree (a git repository when the check diffs), run the script as a subprocess, assert exit status and message text.
- Java: target Java 8, simple type names with imports (G2 rejects fully-qualified names), Google Java Format. Run `spotlessApply` before any push.
- Gradle output is redirected to a file and filtered, never read raw: `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew <task> > build/<name>.log 2>&1`. Read test counts, not exit status.
- Commit messages and docs describe the technical change. No internal phase or iteration labels.
- Git: one commit per task, on the checked-out branch. Do not push, open PRs, rebase or touch version tags without asking the maintainer. The spec asks for stacked PRs; how the commits are split into PRs is the maintainer's call at push time.

**Done for every gate task** (not repeated below):

1. A row in the `scripts/guardrails/README.md` table (rule, how to run locally).
2. For a Python check: a step in the `guardrails` job of `.github/workflows/continuous.yml`, after "Test the guardrail checks".
3. The principle's **Enforced by** field in `docs/architecture/DesignPrinciples.md` changes from `unenforced (judgment)` to the check's path.
4. `python3 -m unittest discover -s scripts/guardrails/tests -p 'test_*.py'` passes, and `python3 -I scripts/guardrails/check_agent_config.py .` prints `agent-config: OK`.

## Review Focus

Failure modes the spec implies and that are most likely to bite. Each has a test in the task that owns it.

1. **A diff-based check with no base commit.** On a push or merge-queue event the base can be empty. The check must follow what the G2 step in `continuous.yml` does and must not report success having checked nothing. (Tasks 4, 10)
2. **A `catch` whose body is on later lines.** Only the `catch` line may be added while the body is unchanged context, or the reverse. The check must read the whole file at HEAD and attribute the block to added lines. (Task 4)
3. **Imports that are not plain single-type imports.** Static imports, wildcard imports, and class names inside comments or string literals must be classified correctly, across `src/main/java`, `java9`, `java11` and `java24` source sets. (Task 2)
4. **A baseline entry whose file was renamed or deleted.** The stale-entry rule must report it clearly instead of crashing. (Task 2)
5. **`.bat` files with CRLF line endings and `^` continuations.** Flag extraction must give the same tokens as for the shell twin. (Task 5)

---

### Task 1: Principles catalog and its G1 check

**Files:**
- Create: `docs/architecture/DesignPrinciples.md`
- Modify: `scripts/guardrails/check_agent_config.py`, `scripts/guardrails/tests/test_check_agent_config.py`, `AGENTS.md` (one line under "Non-negotiable rules", one in "Reference map"), `docs/README.md`

**Interfaces:**
- Produces: the catalog format every later task edits. One `## P<n>. <title>` section per principle (P1–P9, spec section 5), each with four bold-labelled lines: `**Rule:**`, `**Why:**`, `**Enforced by:**`, `**Exceptions:**`. `**Enforced by:**` holds either the literal `unenforced (judgment)` or one or more backticked repository paths.
- Produces: `check_enforced_by(root, errors)` in `check_agent_config.py`, called from `main`.

- [ ] **Step 1: Write the failing tests** in `test_check_agent_config.py`:
  - `test_catalog_pointing_at_missing_check_fails` — catalog with ``**Enforced by:** `scripts/guardrails/check_nope.py` `` → exit 1, message contains `check_nope.py`.
  - `test_catalog_section_without_enforced_by_fails` — a `## P1.` section lacking the field → exit 1.
  - `test_unenforced_literal_passes` and `test_existing_path_passes`.
  - `test_check_script_named_nowhere_fails` — a `scripts/guardrails/check_x.py` that neither the catalog nor `scripts/guardrails/README.md` names → exit 1.
- [ ] **Step 2: Run** `python3 -m unittest scripts/guardrails/tests/test_check_agent_config.py -v`. Expected: the five new tests fail.
- [ ] **Step 3: Implement `check_enforced_by(root, errors)`.**
- [ ] **Step 4: Write the catalog.** Nine sections from spec section 5. **Why** cites the commits and PRs the spec and the November proposal name (for example P4: 22d3f9a3, c81a2d43; P5: 1d069ce0, 9c4f4a63, 0962eaaf). Every **Enforced by** is `unenforced (judgment)` at this point. Add the links in `AGENTS.md` and `docs/README.md`.
- [ ] **Step 5: Run** the unittest discover command and `check_agent_config.py .`. Expected: all pass, `agent-config: OK`.
- [ ] **Step 6: Commit** — `docs: add the design principles catalog and check its enforcement pointers`.

### Task 2: Baseline helper and module layering (G6)

**Files:**
- Create: `scripts/guardrails/_baseline.py`, `scripts/guardrails/check_layering.py`, `scripts/guardrails/layering_rules.json`, `scripts/guardrails/baselines/layering.txt`, `scripts/guardrails/tests/test_baseline.py`, `scripts/guardrails/tests/test_check_layering.py`

**Interfaces:**
- Produces `_baseline.py`:
  - `load(path) -> list[str]` — entries, ignoring blank lines and `#` comments; a trailing ` # reason` on an entry is stripped.
  - `compare(found, path) -> list[str]` — error lines: `new violation: <entry>` for each found entry not listed, `stale baseline entry (remove it): <entry>` for each listed entry not found. Empty list means pass. A missing file is treated as empty.
- Produces the entry format used by all baselines: `<rule-id> <repo-relative-path>[:<detail>]`, never a line number.
- Produces `layering_rules.json`: `edges` (allowed `from -> to` Gradle project pairs), `imports` (per source root: forbidden package prefixes, with per-file exceptions), `upper_layer_literals` (per source root: forbidden class-name prefixes in string literals, with per-file exceptions).

Rule ids: `edge`, `import`, `literal`, `split-package`. Rules are the eight bullets of spec P1.

- [ ] **Step 1: Write failing tests for `_baseline`** — unlisted entry reported; stale entry reported; comment and reason stripping; missing file.
- [ ] **Step 2: Implement `_baseline.py`; run its tests.** Expected: pass.
- [ ] **Step 3: Write failing tests for `check_layering.py`**, each on a throw-away tree with a minimal `settings.gradle`, `build.gradle` files and Java sources:
  - `test_client_importing_agent_fails` — `btrace-client/src/main/java/.../X.java` with `import io.btrace.agent.Main;` → exit 1, message has `import btrace-client/`.
  - `test_core_importing_runtime_module_fails`.
  - `test_instr_importing_agent_fails`.
  - `test_extension_importing_core_internal_fails` — `import io.btrace.core.ArgsMap;` under `btrace-extensions/`.
  - `test_new_project_edge_fails` — `implementation project(':btrace-agent')` added to core's `build.gradle`.
  - `test_upper_layer_class_literal_fails` — `"io.btrace.agent.PerfReaderImpl"` in a runtime source.
  - `test_split_package_fails` — the same package directory under two modules.
  - `test_static_and_wildcard_imports_are_checked` — `import static io.btrace.agent.Main.x;` and `import io.btrace.agent.*;` both fail.
  - `test_name_in_comment_or_javadoc_passes`.
  - `test_java24_source_set_is_checked`.
  - `test_baselined_violation_passes` and `test_fixed_but_still_baselined_fails` (message has `stale baseline entry`).
  - `test_baseline_entry_for_deleted_file_reports_stale`.
  - `test_allowed_exception_file_passes` — `ProbePrinter.java` importing `io.btrace.instr`.
- [ ] **Step 4: Run them.** Expected: fail (script missing).
- [ ] **Step 5: Implement `check_layering.py`** — `main(argv)` takes the repository root, exits 0 or 1, prints `layering: OK` on success. Reuse the comment and string-literal stripping approach of `check_fqn.strip_code`; import rules look at `import` lines only, the literal rule looks at string literals only.
- [ ] **Step 6: Fill `layering_rules.json` and the baseline from the real tree.** Edges: the current graph (spec P1 and section 8). Exceptions: `ProbePrinter.java` (client → instr), `Loader.java` (boot naming agent classes). Run the check on the repository and record what it finds in `baselines/layering.txt`, each with a reason. Compare with the spec's list of seven and note differences.
- [ ] **Step 7: Proof.** Add `import io.btrace.agent.Main;` to a `btrace-client` source, run the check, confirm exit 1 with that file named; revert. Delete one baseline line, confirm `new violation`; restore.
- [ ] **Step 8: Commit** — `ci: check module layering against a fixed set of dependency rules`.

### Task 3: Java 8 API for all main code (G8)

**Files:**
- Modify: `common.gradle:60-66`, `btrace-extensions/btrace-contracts/build.gradle` and `btrace-extensions/btrace-utils/build.gradle` (they repeat the block), `btrace-client/src/main/java/io/btrace/extcli/ExtensionLister.java:44`, `.../extcli/PolicyFile.java:114`, `.../extcli/tui/ExtRepoBrowser.java:73`

- [ ] **Step 1: Prove the hole.** Add `List<String> x = List.of("a");` to any `btrace-core` main class; run `:btrace-core:compileJava`. Expected today: it compiles. Keep the line for step 3.
- [ ] **Step 2: Set `options.release = 8`** on `JavaCompile` tasks whose target is 8, in `common.gradle`. Leave `java9`, `java11` and `java24` source-set compiles alone.
- [ ] **Step 3: Run `./gradlew compileJava`** (all modules), log to file. Expected: the probe line from step 1 fails with `cannot find symbol ... List.of`. Remove the probe line.
- [ ] **Step 4: Resolve what else fails.** If internal APIs (`sun.misc.Unsafe` in `core/BTraceRuntime.java`, `sun.jvmstat`) are rejected under `--release 8`, switch those modules to compiling with the JDK 8 toolchain instead, as `btrace-agent/build.gradle:6` does, and repeat step 3. Count the Java 9+ call sites surfaced per module. Replace the three known ones with Java 8 equivalents (`Collections.unmodifiableMap(new HashMap…)`, `Collections.singletonList`). For a module that does not ship (`btrace-qe`, `benchmarks`), an opt-out with a one-line reason comment is allowed; for a shipped module, fix the call sites. If more than about ten sites surface in shipped modules, stop and report the count instead of fixing them.
- [ ] **Step 5: Run `./gradlew build`**, log to file. Expected: `BUILD SUCCESSFUL`, test counts unchanged from a run on the parent commit.
- [ ] **Step 6: Update the catalog** (P3 → `common.gradle`) and state in the commit body which mechanism held and the per-module counts.
- [ ] **Step 7: Commit** — `build: compile Java 8 targets against the Java 8 API`.

### Task 4: New swallowed failures (G10)

**Files:**
- Create: `scripts/guardrails/check_swallow.py`, `scripts/guardrails/tests/test_check_swallow.py`
- Modify: `.github/workflows/continuous.yml` (step using the same base-selection `case` as the G2 step)

**Interfaces:**
- CLI identical to `check_fqn.py`: `check_swallow.py <base> [<head>]` or `--all`. Scope: `*/src/main/**/*.java`.
- A violation is a `catch` of `Throwable`, `Exception` or `RuntimeException` (alone or in a multi-catch) whose block is empty, contains only `log.debug`/`log.trace` calls, or contains only a bare `return;`, `return null;`, `return false;`, `continue;` or `break;` — and where the `catch` line or any line of its block is an added line. The marker `// swallow: <reason>` on the `catch` line exempts it.

- [ ] **Step 1: Write the failing tests** (two-commit throw-away repository, as in `test_check_fqn.py`):
  - `test_new_empty_catch_fails`, `test_new_debug_only_catch_fails` (fixture reproducing `ClassFileApiBackend.java:225`'s shape), `test_new_bare_return_catch_fails`.
  - `test_catch_that_warns_passes`, `test_catch_that_rethrows_passes`, `test_narrow_exception_passes` (`catch (IOException e) {}`).
  - `test_marker_with_reason_passes`, `test_marker_without_reason_fails`.
  - `test_existing_catch_untouched_passes` — present in the base commit, unchanged.
  - `test_body_emptied_under_existing_catch_fails` — the `catch` line is old, the commit deletes the `log.warn` inside it.
  - `test_multiline_catch_header_fails` — `catch (Throwable\n t) {}`.
  - `test_test_sources_ignored`.
  - `test_empty_base_checks_nothing_and_says_so` — with an empty base argument the script behaves as `check_fqn.py` does for the same input; assert that behaviour explicitly after reading `check_fqn.main`.
- [ ] **Step 2: Run.** Expected: fail.
- [ ] **Step 3: Implement.** Parse each changed file at HEAD with brace matching over comment- and string-stripped text; map blocks to added line numbers from `git diff -U0`.
- [ ] **Step 4: Run tests.** Expected: pass. Then `check_swallow.py --all` on the repository and record the count in the commit body (expected order of magnitude from the spec: about 80; it is informational, not a baseline).
- [ ] **Step 5: Commit** — `ci: reject new catch blocks that swallow a failure silently`.

### Task 5: Launcher parity (G9, launchers)

**Files:**
- Create: `scripts/guardrails/check_launchers.py`, `scripts/guardrails/baselines/launchers.txt`, `scripts/guardrails/tests/test_check_launchers.py`

**Interfaces:**
- For each `btrace-dist/src/main/resources/bin/<name>` with a `<name>.bat` twin, compare the set of JVM flags (tokens starting `-X`, `-XX:`, `--add-`, `-D`, with the following argument attached for `--add-exports`/`--add-opens`) and the main class. Baseline entry: `launcher-flag bin/<name>:<flag>:missing-in-<sh|bat>`.

- [ ] **Step 1: Write the failing tests** — flag only in `.bat` fails; flag only in shell fails; different main class fails; a script with no twin fails; `test_crlf_and_caret_continuation_parse_like_shell`; flags inside a `JAVA_OPTS`-style variable are found; baselined difference passes; stale baseline entry fails.
- [ ] **Step 2: Run.** Expected: fail.
- [ ] **Step 3: Implement; run tests.** Expected: pass.
- [ ] **Step 4: Run on the repository** and write the baseline. Expected from the spec: `btracex` and `btracec` miss `-XX:+AllowRedefinitionToAddDeleteMethods`; `btracex` also misses an `--add-exports jdk.internal.jvmstat/...`.
- [ ] **Step 5: Proof.** Remove a flag from `btrace.bat`, confirm exit 1 naming it; revert.
- [ ] **Step 6: Commit** — `ci: check that shell and batch launchers pass the same JVM flags`.

### Task 6: Probe-kind dispatch (G9, kinds)

**Files:**
- Create: `scripts/guardrails/check_kind_dispatch.py`, `scripts/guardrails/tests/test_check_kind_dispatch.py`

**Interfaces:**
- Reads the constants of `btrace-core/src/main/java/io/btrace/core/annotations/Kind.java`. Asserts each appears as `case <NAME>` inside the `switch (loc.getValue())` of `btrace-agent/src/main/java/io/btrace/instr/Instrumentor.java`, and as `Kind.<NAME>` inside the handler-partition loop of `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`. Fails if either anchor (the switch, the loop) is not found, so a refactor cannot make it pass vacuously.

- [ ] **Step 1: Write the failing tests** — fixture ClassFile backend missing one kind fails and names it; fixture ASM switch missing one fails; all present passes; `test_missing_anchor_fails`; a kind mentioned only in a comment does not count.
- [ ] **Step 2: Run.** Expected: fail.
- [ ] **Step 3: Implement; run tests; run on the repository.** Expected: `kind-dispatch: OK` (17 kinds, both sides).
- [ ] **Step 4: Proof.** Delete the `Kind.SYNC_EXIT` arm in `ClassFileApiBackend.java`, confirm exit 1; revert.
- [ ] **Step 5: Commit** — `ci: check that both instrumentation backends dispatch every probe kind`.

### Task 7: Wire command tables (G9, commands)

**Files:**
- Create: `btrace-core/src/test/java/io/btrace/core/comm/CommandTableParityTest.java`

- [ ] **Step 1: Write the tests:**
  - `v1AndV2DeclareTheSameTypeConstants` — reflect over `public static final byte` fields of `Command` and `BinaryCommand` (excluding `FIRST_COMMAND`/`LAST_COMMAND`); names and values are equal.
  - `rangeBoundsAgree` — `FIRST_COMMAND` and `LAST_COMMAND` equal across the two, and equal the minimum and maximum declared constant.
  - `everyTypeHasAV2Factory` — `BinaryCommand.createCommand(t)` is non-null with `getType() == t` for every `t` in range.
  - `everyTypeConvertsBothWays` — for every `t` except `HEARTBEAT`, `CommandAdapter` converts the V2 instance to V1 and back, preserving the type. `HEARTBEAT` is asserted to be the only exception (`WireIO.java:101`).
- [ ] **Step 2: Run** `:btrace-core:test --tests '*CommandTableParityTest'`, log to file. Expected: pass on the current tree (the tables match at 19). If one fails, that is a real finding: stop and report.
- [ ] **Step 3: Proof.** Comment out one `COMMAND_FACTORIES` registration in `BinaryCommand.java`; expect `everyTypeHasAV2Factory` to fail naming the type; revert.
- [ ] **Step 4: Commit** — `test(core): assert the v1 and v2 command tables stay identical`.

### Task 8: Bootstrap closure of the masked JAR (G7)

**Files:**
- Create: `btrace-dist/src/test/java/io/btrace/dist/BootstrapClosureTest.java`, `btrace-dist/src/test/resources/bootstrap-root-allowlist.txt`, `btrace-dist/src/test/resources/bootstrap-closure-baseline.txt`
- Modify: `btrace-dist/build.gradle` (`testImplementation libs.asm`), `docs/architecture/MaskedJarArchitecture.md`, `.github/CODEOWNERS` (add the two resource files)

**Interfaces:**
- Locate the built JAR the way `BTraceJarPackagingTest` does.
- Allowlist: one package prefix or class per line (`io/btrace/core/annotations/`, `io/btrace/boot/Loader.class`), derived from `bootIncludes` and the root extras in `btrace-dist/build.gradle:285-354, 406-441`.
- Baseline entries: `<root class> -> <referenced class>`.

- [ ] **Step 1: Write the tests:**
  - `rootClassesMatchTheAllowlist` — every root `.class` entry (not `.classdata`, not under `META-INF/`) matches an allowlist line; every allowlist line matches at least one entry.
  - `rootClassesReferenceOnlyBootstrapVisibleTypes` — for each root class, every class named in its constant pool (via ASM `ClassReader`) is a JDK type (`java/`, `javax/`, `jdk/`, `sun/`), a relocated slf4j type, or another root entry; otherwise it must be in the baseline. A baseline line that no longer occurs fails.
- [ ] **Step 2: Build the JAR and run** — `./gradlew clean :btrace-dist:btraceJar :btrace-dist:test --tests '*BootstrapClosureTest'`, log to file. Fill the allowlist and baseline from the first run. Expected baseline from the spec: `ExtensionContext -> io/btrace/core/comm/Command`; record whatever is actually found.
- [ ] **Step 3: Correct `MaskedJarArchitecture.md`** where it disagrees with the build: the placement of `core/extensions` and the `Boot-Class-Path` value (`btrace-dist/build.gradle:439-441, 450`). Check each claim against the built JAR's manifest and entries before editing.
- [ ] **Step 4: Proof.** Add a field of type `io.btrace.core.comm.Command` to another root class (any class under `io/btrace/core/annotations/` will not do — pick a bootstrap class with code, such as one in `io.btrace.core.extensions`), rebuild, confirm the closure test fails naming it; revert.
- [ ] **Step 5: Commit** — `test(dist): check the bootstrap section's contents and reference closure`.

### Task 9: Registries (G12)

**Files:**
- Create: `btrace-core/src/test/java/io/btrace/core/ArgsRegistryTest.java`, `btrace-core/src/test/resources/args-registry-baseline.txt`, `btrace-agent/src/test/java/io/btrace/instr/HandlerAnnotationClassificationTest.java`, `scripts/guardrails/check_message_keys.py`, `scripts/guardrails/tests/test_check_message_keys.py`
- Modify: `btrace-agent/src/main/java/io/btrace/instr/Preprocessor.java` (make `RT_AWARE_ANNOTS` and `GUARDED_ANNOTS` package-private; add a package-private `UNGUARDED_ANNOTS` set holding the descriptors deliberately in neither), `.github/CODEOWNERS` (the baseline file)

The message-key check is Python, not JUnit as the spec's P7 heading says: it scans source for key literals, which is a source-level rule.

- [ ] **Step 1: `ArgsRegistryTest`:**
  - `everyOptionIsInTheUsageText` — each `public static final String` value in `io.btrace.core.Args` appears as a word in the `btrace.agent.usage` value of `messages.properties`.
  - `everyOptionIsInTheQuickReference` — each appears in `../docs/QuickReference.md` (Gradle runs tests with the module directory as working directory).
  - Both honour the baseline (`usage <name>` / `quickref <name>`) with the stale-entry rule. Expected baseline from the spec: usage `agentJar`, `libs`, `probes`; quickref `agentJar`, `libs`.
- [ ] **Step 2: `HandlerAnnotationClassificationTest.everyMethodAnnotationIsClassified`** — every annotation type in `io.btrace.core.annotations` whose `@Target` includes `METHOD` has its descriptor in `RT_AWARE_ANNOTS`, `GUARDED_ANNOTS` or `UNGUARDED_ANNOTS`. Fill `UNGUARDED_ANNOTS` with what the first run reports, each with a comment giving the reason; if the reason for one (the spec flags `@OnLowMemory`) is not evident from the code, stop and ask.
- [ ] **Step 3: `check_message_keys.py`** — tests: a key used in `Messages.get("x.y")`-style calls (derive the real accessor names from `btrace-core` first) but absent from `messages.properties` fails; present passes; a key built by concatenation is ignored. Then implement and run on the repository. Expected: `message-keys: OK`.
- [ ] **Step 4: Run** `:btrace-core:test --tests '*ArgsRegistryTest'` and `:btrace-agent:test --tests '*HandlerAnnotationClassificationTest'`, logs to file. Expected: pass.
- [ ] **Step 5: Proof.** Add a constant to `Args.java` without touching the usage text; expect `everyOptionIsInTheUsageText` to fail naming it; revert.
- [ ] **Step 6: Commit** — `test: check that agent options and handler annotations are registered everywhere they must be`.

### Task 10: Growth ratchets (G13)

**Files:**
- Create: `scripts/guardrails/check_growth.py`, `scripts/guardrails/growth_allow.txt`, `scripts/guardrails/api/btrace-core-public-types.txt`, `scripts/guardrails/baselines/test-hooks.txt`, `scripts/guardrails/tests/test_check_growth.py`
- Modify: `.github/workflows/continuous.yml` (base selection as for G2)

**Interfaces:**
- CLI: `check_growth.py <root> <base> [<head>]`. Three rules:
  - `size` — a `*/src/main/**/*.java` file with more than 1,000 lines at HEAD fails if it is new or longer than at the base, unless listed in `growth_allow.txt` (`<path> # reason`).
  - `api` — the sorted list of public top-level types under `btrace-core/src/main/java` must equal `api/btrace-core-public-types.txt` (one fully-qualified name per line). The message tells the author to update the snapshot in the same change.
  - `test-hook` — a method whose name ends in `ForTest` in main sources fails unless baselined as `test-hook <path>:<method>`.

- [ ] **Step 1: Write the failing tests** — file at 1,001 lines growing by one fails; shrinking passes; staying equal passes; new file of 1,200 lines fails; new file of 900 passes; allow-listed growth passes; new public class in core without snapshot edit fails; snapshot listing a deleted class fails; package-private class ignored; new `fooForTest` in main fails; in test sources passes; `test_empty_base_matches_g2_behaviour`.
- [ ] **Step 2: Run.** Expected: fail.
- [ ] **Step 3: Implement; run tests.** Expected: pass.
- [ ] **Step 4: Generate the snapshot and the test-hook baseline from the tree.** Record in the commit body the number of public types (spec: 151) and of files over 1,000 lines.
- [ ] **Step 5: Proof.** Append one line to `btrace-client/src/main/java/io/btrace/client/Client.java`, commit locally on a scratch branch, run the check against its parent, confirm exit 1; discard the scratch branch.
- [ ] **Step 6: Commit** — `ci: stop oversized classes and the core public API growing unnoticed`.

### Task 11: Application-thread safety (G11)

**Files:**
- Create: `scripts/guardrails/check_runtime_safety.py`, `scripts/guardrails/tests/test_check_runtime_safety.py`, `btrace-agent/src/test/java/io/btrace/instr/BTraceTransformerFailureTest.java`

- [ ] **Step 1: Python check** — `Thread.interrupted()` anywhere in `btrace-runtime/src/main/**/*.java` fails (comments and strings ignored); `Thread.currentThread().isInterrupted()` passes. Write tests, implement, run on the repository. Expected: `runtime-safety: OK`; if it finds a use, read `CommandQueue.java:57-90` and report before baselining.
- [ ] **Step 2: `BTraceTransformerFailureTest.transformReturnsNullAndReportsWhenTheBackendThrows`** — drive `BTraceTransformer.transform` with a backend or probe that throws; assert it returns `null`, does not throw, and that the failure reaches the reporting path (`reportFailure`). Use the construction pattern of the existing transformer tests in `btrace-agent/src/test`; if `BTraceTransformer` cannot be given a throwing collaborator without changing production code, stop and report rather than adding a seam.
- [ ] **Step 3: Proof.** Temporarily rethrow in the `catch` at `BTraceTransformer.java:239-246`; expect the test to fail; revert.
- [ ] **Step 4: Commit** — `test: guard the rules that keep BTrace from disturbing application threads`.

### Task 12: One-sided backend changes (G5 `Sibling:` line)

**Files:**
- Modify: `scripts/guardrails/check_pr_metadata.py`, `scripts/guardrails/tests/test_check_pr_metadata.py`, `.github/pull_request_template.md`

**Interfaces:**
- Constants in `check_pr_metadata.py`: `CLASSFILE_BACKEND = ("btrace-agent/src/main/java24/",)` and `ASM_BACKEND`, a tuple of exact paths: `Instrumentor.java`, `AsmInstrumentationBackend.java`, plus the `io/btrace/instr` files referenced only from those two. Derive the list with a reference search and put the derivation command in a comment.
- Rule inside `check(event, changed)`: if the changed files touch exactly one of the two sets, the description must contain a line matching `^Sibling:\s*\S`. Bot-authored PRs are exempt through the same test the Testing-section rule uses.

- [ ] **Step 1: Write the failing tests** — ASM-only change without the line fails; ClassFile-only without the line fails; both sides changed passes; one side with `Sibling: not needed, ASM-only bug` passes; `Sibling:` with nothing after it fails; backend-neutral file (`BackendSelector.java`) alone passes; bot author exempt.
- [ ] **Step 2: Run, implement, run.** Expected: fail, then pass.
- [ ] **Step 3: Add the `Sibling:` prompt to the PR template** with one sentence on when it applies.
- [ ] **Step 4: Proof.** Run the check on an event fixture built from PR #994's title, body and changed files (it changed `Instrumentor` only). Expected: exit 1.
- [ ] **Step 5: Commit** — `ci: require a reason when only one instrumentation backend changes`.

### Task 13: Full gate and ruleset status

- [ ] **Step 1: Run the full gate**, logs to file: `python3 -m unittest discover -s scripts/guardrails/tests -p 'test_*.py'`; every `check_*.py` as CI runs it; `./gradlew build`; `./gradlew spotlessCheck`. Without JDK 28 installed, report `classFileApiBackendTest` as NOT VERIFIED.
- [ ] **Step 2: Re-read the ruleset** — `gh api repos/btraceio/btrace/rulesets/26315659 --jq '[.rules[]|select(.type=="required_status_checks")|.parameters.required_status_checks[].context]'`. Update the paragraph in `scripts/guardrails/README.md` that says which checks are required, and state in the final report whether the Python gates can block yet.
- [ ] **Step 3: Confirm the catalog** — P1–P8 each name an existing check; P9 reads `unenforced (judgment)`.
- [ ] **Step 4: Report** each gate as `command → key result line`, list every baseline with its entry count, and list anything stopped on.
