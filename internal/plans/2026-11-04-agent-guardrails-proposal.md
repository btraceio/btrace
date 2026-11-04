# Agentic-development guardrails — evidence and proposal

Status: **proposal, awaiting decisions (section 8)**. Branch `chore/agent-guardrails`, based on
`origin/develop` 4e64361a. Nothing outside this file has been changed.

Evidence key: **[V]** re-verified by hand in this worktree; **[S]** reported by a research pass over
git/PR history (`gh pr list --state merged --limit 150`), spot-checked but not every number re-derived.

---

## 1. Phase 1 — Evidence

### 1.1 Agent instruction surface (actual shape)

| File | Size | Read by | Notes |
| --- | --- | --- | --- |
| `AGENTS.md` | 65 lines | Codex, Pi, Copilot agent, others | Map + 7 "non-negotiable" rules + build notes + doc placement |
| `CLAUDE.md` | 40 lines | Claude Code | Command list + pointers; defers to AGENTS.md |
| `.github/copilot-instructions.md` | 125 lines | Copilot | Last substantive edit 2026-05-03 (ed98c6de); stale (see 1.3) |
| `CONTRIBUTING.md` | 83 lines | humans, agents | Contains the worst drift (see 1.3) |
| `.github/pull_request_template.md` | — | humans, agents | Verification checklist, unenforced |
| `.agents/skills/qe-explore/` | SKILL.md + reference.md | Pi, Codex | Symlinked at `.claude/skills/qe-explore` — the right pattern |
| `.claude/skills/steward/SKILL.md` | 1 file | **Claude Code only** | Not in `.agents/skills/`; Codex/Pi never see it **[V]** |

No `.memer/`, no hooks, no CODEOWNERS **[V]**.

### 1.2 What CI already gates deterministically (do not rebuild)

All of these feed the aggregate job `tests-passed` (`.github/workflows/continuous.yml:248-271`), which
also runs on `merge_group` (`:12`):

- `./gradlew build` (`continuous.yml:59-60`): compile, unit tests, `spotlessCheck` + license header
  (`gradle/spotless.gradle`, `check.dependsOn spotlessCheck`), golden instrumentation data
  (`InstrumentorTestBase.java:250` assertEquals over 208 files), `classFileApiBackendTest` on JDK 28
  (fails if JDK 28 missing under `CI`), extension POM/zip checks.
- v2 protocol subset (`:61-65`).
- Integration matrix JDK 8/11/17/21/25/27/28-ea, `-Pintegration -PCI` (`:93-194`).
- btrace-qe harness `qeSelfTest` + `qe -Pqe.failOn=S2` (`:215-238`).
- CodeQL (`codeql-analysis.yml`, PR + merge_group); renovate config validation (path-filtered only).

**Not gated on PRs** **[V/S]**: API/binary compatibility; `btrace-gradle-plugin` tests (included build,
release-only — `release.yml`/`scripts/run-release-gate.sh`); unused imports (real Spotless config has no
`removeUnusedImports()`; the root `build.gradle:127-140` block that has it targets the source-less root
project and is dead config **[V]**); docs/links; anything about agent configuration.

**Swallowed failures** **[S]**: Docker-tagged integration tests skip with a warning when Docker is absent
(`integration-tests/build.gradle:197-221`, also under `-PCI`); `v2-protocol-tests.yml` summary counts
`failures=` but ignores `errors=`; `run_tests.sh:17` `|| true` (not used by CI).

### 1.3 Drift and lies in advisory text

| # | Location | Claim | Reality | Kind |
| --- | --- | --- | --- | --- |
| D1 | `CONTRIBUTING.md:34` | `./gradlew --no-daemon integration-tests:test` runs integration tests | `integration-tests/build.gradle:163-166` `onlyIf { hasProperty("integration") }` → task SKIPPED, exit 0. **A green command that ran zero tests.** **[V]** | lie |
| D2 | `CONTRIBUTING.md:10-11,60` | `GRADLE_USER_HOME=$PWD/.gradle-home` | Not gitignored (`git check-ignore .gradle-home` → nothing); `.gradle-user` is (`.gitignore:33`) and is what AGENTS.md/CLAUDE.md use. Following CONTRIBUTING creates an untracked multi-GB cache an agent can `git add -A`. **[V]** | inconsistent |
| D3 | `.github/copilot-instructions.md:9,16` | Modules `btrace-instr`, `services` | Neither exists (`ls` → no such file); instrumentation lives in `btrace-agent` `io.btrace.instr` **[V]** | lie |
| D4 | `copilot-instructions.md:28,58` | "Build toolchain: JDK 11", `TEST_JAVA_HOME` "typically JDK 11" | Build needs JDK 8 (`common.gradle:22`), 11 (`:64`), 24 (`btrace-agent/build.gradle:38`), 28 (`:191`) toolchains **[V]** | stale |
| D5 | `copilot-instructions.md:98` | "Use `/tmp` for scratch work" | Contradicts sandboxed-harness practice; nothing else in repo says it **[V]** | stale |
| D6 | `AGENTS.md:57` "never add plans … below `docs/`" | — | `docs/superpowers/{plans,specs}/` tracks 5 plan/spec files added *after* the consolidation commit b405a486 (via #843, #865, #860, #935, #948) **[V/S]** | rule violated 5× |
| D7 | `AGENTS.md:20` no fully-qualified type names | — | Violated after the rule landed (ed013600, 2026-07-12): c53a2a4a, bd7f58b7, e5fe7a02, b01e8cd4, and #998 merged today (`Client.java`: `throws java.io.IOException`) **[V for #998, S for rest]** | rule violated |
| D8 | `.claude/skills/steward` vs `docs/QEAgent.md:64` | QEAgent.md documents `.agents/skills/` + symlink as the convention | steward added the same day (#975) as a plain `.claude/` file **[V]** | inconsistent |
| D9 | `AGENTS.md:19` "Java 11 toolchain" | true for main `compileJava` only | Contributors need 8/24/28 too; only `steward` SKILL.md says so | incomplete |
| D10 | `docs/BTraceExtensionDevelopmentGuide.md:383` | "Phase 1 support" column header | Internal iteration label in shipped docs **[S]** | stale |
| D11 | `README.md:128` | same bare `integration-tests:test` as D1 | skipped, green **[V]** | lie |
| D12 | `CONTRIBUTING.md:36-43,66`, `README.md:130` | pass `-Dbtrace.permissions=…` to Gradle for integration tests | `integration-tests/build.gradle` forwards no such property (`git grep` → nothing **[V]**); `RuntimeTest.java:278-287` writes its own policy file | stale |
| D13 | `copilot-instructions.md:19,119` | "services" SPI module; "hppcrt" key dependency | No services module (settings.gradle skips them); no hppc in any `*.gradle` **[V]** | lie |
| D14 | `.github/workflows/README.md:44-49` | v2-protocol workflow runs on push/PR/path filters/`[benchmark]` | `v2-protocol-tests.yml:7-15`: labeled PR, weekly cron, manual only **[V]** | lie |
| D15 | `.claude/skills/steward/SKILL.md:23` | "unpinned `googleJavaFormat()` in `gradle/spotless.gradle`" | pinned `googleJavaFormat('1.30.0')` at `:11` **[V]** | stale |
| D16 | `CONTRIBUTING.md:7` | "JDK 11+ … tests run comfortably on 11/17" | JDK 8, 24, 28 toolchains required; foojay resolver not applied (`settings.gradle:74` catalog only) **[S]** | stale |
| D17 | `copilot-instructions.md:59` | `TEST_JAVA_HOME` "Required" | `RuntimeTest.java:134,169-180` falls back to `JAVA_TEST_HOME`, `JAVA_HOME` **[S]** | stale |
| D18 | `AGENTS.md:54` "keep docs/README.md current" | — | `docs/README.md` omits `ExtensionInterfaceRules.md` (linked from AGENTS.md:64), `releasing.md`, two `architecture/` docs **[S]** | rule violated |
| D19 | `README.md:108` | "See CLAUDE.md for development setup" | CLAUDE.md:3 says it is Claude-only orientation; setup is CONTRIBUTING.md **[S]** | inconsistent |
| D20 | `.github/workflows/README.md:12-14` | CI triggers | omits `merge_group` (`continuous.yml:12`) **[S]** | stale |

All relative markdown links in the 15 agent/contributor files resolve, and every Gradle task they name
exists in `./gradlew tasks --all` **[S]**. The drift is in *claims and semantics*, not in dangling
paths — which is why a link checker alone would not have caught D1/D11 and G4 must check `-Pintegration`
semantics, not only task existence.

**Hooks:** `git grep hooksPath|pre-commit|githooks|husky|lefthook` → one prose hit in a plan file, no
instruction **[V]**. Nothing to retract; standing rule 5 is pre-empted, not violated. Local-only
observation (not repo content): the maintainer clone's `.git/config` sets `core.hooksPath` to its own
`.git/hooks` (git-lfs hooks only) **[V]** — an explicit local value that overrides any global
`core.hooksPath`. If local hooks are ever shipped, the setup script must account for this.

### 1.4 History: the bug class that ships green

The recurring class is **silent wrong answer / silent no-op**, amplified by **fail-open fallbacks**.
CI passes; demos, recordings, and the landing-page check (#984) find it **[S]**:

- c81a2d43 — `@Duration` 0 at every return after the first (demo 06: 300–480 ms checkouts shown as 0 ms).
- 068e1a81, 1837bfad — plain `Throwable` parameter at ERROR/CATCH/THROW: handler silently never applied.
- e579b70b — `@Level` gating never suppressed; existing test "passed vacuously". Already listed as broken
  in #857 (d88e3888). 0962eaaf: `applyLevelGuard` caught construction failure and returned the unguarded
  handler — the fail-open that hid it.
- 1d069ce0, 9c4f4a63 — handler mismatches and transform failures logged at debug only.
- bd7f58b7 — oneliner wildcards never matched; `@Injected` per-site instances.
- 51ac8280 — three agent options silently ignored. #921 — misplaced `=` dropped with no diagnostic.
- **Still present [V]:** `BackendSelector.java:48-49` catches `Throwable` loading the ClassFile API
  backend and logs at **debug**, then falls back to ASM — which cannot handle class major > 71.

Second class: **state/doc drift** — #921 (three merged fixes silently overwritten by release-prep
f298251e "authored against a stale checkout"); #863 (stale `org/openjdk/btrace` descriptors made the
permission scan match nothing); d0c2353e (docs claimed a backend gap that doesn't exist); #984
(tutorials 01–04, 06–08, 10–12 didn't match real behaviour).

**Hand-kept conventions, enforced by nothing** **[S]**:

| Convention | Followed | Evidence of misses |
| --- | --- | --- |
| Conventional Commit PR title (squash subject) | 82/95 human PRs | #956, #908, #905, #903, #902, #857, #810 … |
| PR states how it was verified | 70/95 | #914, #911, #909, #894, #860, #838 have none |
| Golden data changed only via `-PupdateTestData`, said so | 11/16 commits | 0f185400, 05d682e9, 1d069ce0 silent; #857 left regen to reviewer |
| No FQN in Java source | — | D7 |
| Plans/specs under `internal/` | — | D6 |

**Youngest, least-covered pipelines** **[S]**: `demos/` (2026-10-26, 45 files, 0 tests — where most
class-A bugs surfaced); `btrace-qe` (2026-10-25); `scripts/` (release tooling, 0 tests); the ClassFile
API backend (`btrace-agent/src/main/java24/.../ClassFileApiBackend.java`, 3,770 lines, tests only on
JDK 28).

### 1.5 Multiplicities (one contract, several implementations)

| Contract | Implementations | Parity today |
| --- | --- | --- |
| `InstrumentationBackend` | `AsmInstrumentationBackend`, `ClassFileApiBackend` (java24 set) via `BackendSelector` | **None.** The 208-file golden set is ASM-only: `InstrumentorTestBase.transform()` (`:270`) calls `BTraceClassWriter` directly, never `BackendSelector` **[V]**. ClassFile API has ~50 hand-written asserts + 4 integration tests vs 40 ASM-only functional tests |
| `BTraceRuntimeImplBase` | `_8`, `_9`, `_11` (reflective tiering) | `_9` never selected in CI (no JDK 9/10 lane) |
| `WireProtocol` | V1 `JavaSerializationProtocol`, V2 `BinaryWireProtocol`; 16 V1 vs ~23 V2 command classes | No per-command round-trip across both; only `PreparedModeAuthenticationFunctionalTest` parameterizes protocol |
| Agent `Client` | `RemoteClient`, `FileClient` | `FileClient` untested; `RemoteClient` has three `switch`es over the same command set |
| Launch mode | `-javaagent` vs dynamic attach | Not the same suite in both |
| Launch scripts | `btrace-dist/.../bin/*` and `*.bat` twins | `.bat` never run (all CI on ubuntu) |
| `ExtensionRepository` | FileSystem, Embedded | FileSystem used once in tests |
| JDK tiers | 8–28 | JDK 24 (ClassFile API minimum) has no runtime test lane |

### 1.6 Harness reach and session-local state

- Skills: `.agents/skills/` is read by Pi and Codex (documented `docs/QEAgent.md:64`); Claude Code reads
  `.claude/skills/` (symlinks work — qe-explore proves it). Copilot reads `.github/copilot-instructions.md`
  and AGENTS.md; it does not load skills.
- Tracked session-local tool state: `internal/libretti/` (2 files, muse output), `internal/superpowers/`
  (16), `docs/superpowers/` (5). Untracked and **not ignored** in the main checkout: `scratchpad/`,
  `.codex/`, `demos/lib/__pycache__/`, `scripts/__pycache__/`, `.claude/settings.json`,
  `internal/libretti/…837…md` **[V]**.

---

## 2. Sorting every existing rule

| Rule / procedure (source) | Disposition | Why |
| --- | --- | --- |
| No FQN type names (AGENTS.md:20) | **Mechanize** — G2 | Grep-checkable; violated 5× since written |
| Spotless / GJF / license header | Already gated | — |
| Unused imports (copilot-instructions:35 "remove unused imports") | **Mechanize** — G3 (`removeUnusedImports()`) | Claimed enforced, isn't |
| Plans/specs never under `docs/`; no singular `doc/` (AGENTS.md:53-57) | **Mechanize** — G1 | Path check; violated 5× |
| Skills live in `.agents/skills`, `.claude/skills/*` are symlinks (QEAgent.md:64) | **Mechanize** — G1 | steward drifted the same day |
| Relative links / task paths in agent & contributor docs resolve | **Mechanize** — G1 (links), G4 (Gradle task paths via `--dry-run`) | D1, D3 |
| PR title is a Conventional Commit | **Mechanize** — G5 | Squash subject; 13/95 misses |
| PR states verification; golden-data change states regeneration | **Mechanize presence** (G5, advisory), judgment stays human | Presence is checkable, adequacy is not |
| New gate must be seen to fail (AGENTS.md:23) | **Keep invariant** + skill `btrace-verify` | Judgment |
| e2e coverage for cross-boundary behaviour (AGENTS.md:22) | **Keep invariant** + skill | Judgment about "crosses a boundary" |
| Java 8 target / toolchains | Already gated by compilation; **keep** one line, toolchain list moves to skill | — |
| Redirect Gradle output, workspace-local cache | **Skill** `btrace-verify` | Procedure |
| Masked-JAR changes need `clean :btrace-dist:btraceJar` + read arch doc | **Skill** `btrace-dist-change` (trigger: edits under `btrace-dist/`, class-loader code) | Procedure, loaded at use |
| Golden regen only via `-PupdateTestData`, never by hand | **Skill** `btrace-instr-change` + G5 presence check | Procedure + judgment ("intentional") |
| CI triage, Renovate, review follow-up (steward) | **Skill** `steward` (moved to `.agents/skills`) | Already a skill |
| Demo-bug backlog recording | Skill `steward`/`qe-explore` note; not a gate | Judgment |
| Copilot instruction file content | **Replace** with a 10-line pointer to AGENTS.md | Duplicate that drifted (D3–D5) |

## 3. Mechanized gates

All checks are plain Bash under `scripts/guardrails/` with a hermetic test runner
`scripts/guardrails/test.sh` that builds throw-away fixture trees under `mktemp -d` and asserts each
check **fails on a bad fixture with the expected message** and passes on a good one. No new dependency.
CI: one new job `guardrails` in `continuous.yml` (seconds, no JDK), placed in `tests-passed.needs` if
blocking (decision 8.2).

| Gate | Fires on (bad input) | Gate's own test | First real catch |
| --- | --- | --- | --- |
| **G1** `check-agent-config.sh` | `.claude/skills/X` not a symlink into `.agents/skills/X`; SKILL.md missing `name`/`description` or name≠dir; any file under `docs/**/{plans,specs,superpowers}/` or a `doc/` dir; broken relative link in AGENTS.md, CLAUDE.md, CONTRIBUTING.md, `.github/*.md`, `.agents/**`, `docs/**` (top-level files included); module name `btrace-*` named in agent docs but absent from `settings.gradle`/dirs | fixtures: plain-file skill, skill without frontmatter, `docs/superpowers/plans/x.md`, dangling link in a top-level file, `btrace-instr` mention | steward (D8), docs/superpowers (D6), copilot `btrace-instr` (D3) |
| **G2** `check-fqn.sh` (diff vs merge base, Java only, ignores imports/package/comments/strings; `// fqn: <reason>` escape for genuine simple-name clashes) | added line with `java.io.IOException` etc. | fixture diff reproducing #998's `Client.java` line; fixture with clash + marker passes | #998 |
| **G3** Spotless `removeUnusedImports()` in `gradle/spotless.gradle`; delete dead root block | unused import | Gradle: `spotlessCheck` on a fixture source set (or prove by adding an unused import locally and recording the failure in the PR) | tree-wide: unknown until run — may need a one-off `spotlessApply` commit |
| **G4** `check-doc-commands.sh` | a `./gradlew <task>` in agent/contributor docs whose task path doesn't resolve (`./gradlew --dry-run`); `integration-tests:test` without `-Pintegration` | fixture doc with `:btrace-instr:test` and bare `integration-tests:test` | D1 |
| **G5** `check-pr-metadata.sh` (reads `$GITHUB_EVENT_PATH`) | non-CC title; empty "Commands and results"; `instrumentorTestData` changed without `-PupdateTestData` in body | fixture event JSONs | 13/95 titles, 25/95 bodies |

Also swallowed-failure fixes (no new gate, each with a test that fails without it):
**F1** the Docker probe in `integration-tests/build.gradle:192-225` calls `exec {}`, which Gradle 9
removed: every run throws `MissingMethodException`, which is caught and logged as "Docker not available".
CI still runs Docker tests only because `/var/run/docker.sock` was assigned before the throw (run
41898525786, all five lanes log "Docker not available (…exec()…)" then "Using DOCKER_HOST=…"); on macOS
with Docker running but no `/var/run/docker.sock`, Docker-tagged tests are always skipped **[V]** (local
run, `docker info` → 29.8.0). Fix: `providers.exec`, and under `-PCI` fail instead of skip when Docker
is missing;
**F2** `v2-protocol-tests.yml` summary counts `errors=`;
**F3** `btrace-gradle-plugin` tests on PRs (currently release-only);
**F4** `BackendSelector` fallback: WARN (or fail) when a class with major > 71 arrives and the ClassFile API
backend did not load — this is the repo's own bug class, live today.

## 4. Skills

Layout: source in `.agents/skills/<name>/SKILL.md`; `.claude/skills/<name>` → `../../.agents/skills/<name>`
(enforced by G1). Copilot gets AGENTS.md pointers only.

| Skill | Takes over | Trigger (description) |
| --- | --- | --- |
| `btrace-verify` | AGENTS.md "Build and verification", CLAUDE.md commands, toolchain list (8/11/24/28), `TEST_JAVA_HOME`, log-redirect rule, "-Pintegration or nothing runs", "compare failures by name; read test counts, not exit status" with D1 as case file | Before claiming done; when choosing what to run |
| `btrace-instr-change` | Golden-data rules, both-backends rule, `classFileApiBackendTest` on JDK 28, parity expectations, case files c81a2d43 / 068e1a81 / e579b70b (assert the value, not that a line printed) | Editing `io.btrace.instr`, `src/main/java24`, `instrumentorTestData` |
| `btrace-dist-change` | AGENTS.md "Distribution changes" + MaskedJarArchitecture pointer | Editing `btrace-dist/`, class-loader / bootstrap code |
| `steward` | (exists) moved to `.agents/skills`, symlinked | PR shepherding |
| `qe-explore` | (exists) | unchanged |

AGENTS.md keeps: map, invariants (Java 8 target; masked-JAR deliberate assignment; e2e for cross-boundary;
gate must be seen to fail; doc placement), each with its case file and a "mechanized by G*" or
"procedure: skill X" pointer. Target ≤ 70 lines; CLAUDE.md shrinks to orientation + skill list.

## 5. Closing the gaps

- **Guardrail regression suite:** `scripts/guardrails/test.sh` in the `guardrails` job (section 3).
- **Agent-facing evals:** the agent-facing surface in this repo is the CLI/oneliner and btrace-qe (MCP
  server is external, `docs/MCPServer.md`). Add a btrace-qe `demos` sub-tier of **value-asserting** cases:
  replay a shipped demo and assert numbers within a range (e.g. demo 06 `@Duration` > 250 ms), not that
  output appeared. Each case is first run against the pre-fix commit of its bug (c81a2d43^, e579b70b^…)
  to prove it fails.
- **Verification path per component** (command → healthy output → the assertion for the silent-wrong-answer class):
  - instrumentation: `:btrace-agent:test` + `classFileApiBackendTest` (JDK 28) → test counts > 0 for
    both tasks → **golden set run through `ClassFileApiBackend` too** (new parity task; largest item).
  - protocol: `:btrace-core:test --tests 'io.btrace.core.comm.*'` → per-command round-trip on V1 and V2.
  - integration: `-Pintegration :integration-tests:test` → JUnit XML `tests>0`, Docker lane not skipped under CI.
  - dist: `clean :btrace-dist:btraceJar` + `scripts/verify-release-artifacts.sh`.
  - qe/demos: `:btrace-qe:qe -Pqe.failOn=S2` → value assertions.
- **Indicators, baseline taken now (2026-11-04, from 95 merged human PRs since 2025-12-27) [S]:**
  - leading: CC titles 86% (82/95); PRs with verification text 74% (70/95); golden changes w/ regen note
    69% (11/16); new FQN uses since rule: ≥5 commits; G1 violations on develop: ≥3 (D3, D6, D8 — exact
    count once G1 exists, measured before it is made blocking).
  - lagging: fixes for silent-wrong-answer/fail-open bugs found outside CI — ≥12 commits May–Nov 2026
    (1.4); drift fixes (#921, #863, d0c2353e, #984).

## 6. Execution plan (one tracking issue, one sub-issue / PR each)

| # | PR | Gate proof in the same PR |
| --- | --- | --- |
| 1 | `docs: correct drifted agent and contributor instructions` — D1–D20 (copilot file → pointer); `git mv docs/superpowers/* internal/superpowers/`; steward → `.agents/skills` + symlink; `.gitignore` additions (decision 8.3) | Run every changed command; record `integration-tests:test` SKIPPED vs `-Pintegration` executing N tests |
| 2 | `ci: add agent-config guardrail (G1)` + test runner + `guardrails` job | test.sh shows each fixture failing; job run on PR 1's parent fails, on PR 1 passes |
| 3 | `ci: reject new fully-qualified type names (G2)` | fixture of #998 line fails |
| 4 | `build: enforce unused-import removal (G3)`, drop dead root block | unused import added → `spotlessCheck` fails (log in PR) |
| 5 | `ci: check Gradle task paths in docs (G4)` | fixture with D1 fails |
| 6 | `ci: check PR title and verification section (G5)` | fixture events |
| 7 | `docs(agents): move procedures into skills; slim AGENTS.md/CLAUDE.md` | G1 green; each moved paragraph has a pointer |
| 8 | `test: stop swallowing failures (F1–F3)` | each fix shown failing without it |
| 9 | `fix(agent): warn when the ClassFile API backend is unavailable for a class it must handle (F4)` | unit test fails on current code |
| 10 | `test(agent): run golden instrumentation set through both backends` (own plan) | — |
| 11 | `test(qe): value-asserting demo cases` | each fails on the pre-fix commit |

## 7. What this proposal did not verify

- The 13/95, 70/95, 11/16 and ≥12 counts were computed by a research pass over `gh` JSON and git log;
  only #998's FQN line, the BackendSelector fallback, the golden-path bypass, and D1–D9 were re-checked by hand.
- Whether CodeQL is a required check (branch protection is not in the repo).
- Whether Copilot coding agent reads AGENTS.md in this repo's configuration.
- Whether `removeUnusedImports()` produces a tree-wide diff (not run).
- The docs sweep covered the 15 agent/contributor files, not all of `docs/**` (tutorials were audited
  by #984). No documented command was executed end-to-end; only task existence and build-script
  semantics were checked.

## 8. Decisions (answered by the maintainer, 2026-11-04)

1. **Harness scope:** Claude Code, Codex and Pi read skills from `.agents/skills/` (`.claude/skills/*`
   symlinks). `.github/copilot-instructions.md` shrinks to a short pointer to AGENTS.md.
2. **Blocking:** **all gates G1–G5 block.** Consequence for G5: it reads the PR title/body, so it runs in
   its own workflow on `pull_request` (`opened`, `edited`, `synchronize`, `reopened`) and passes trivially
   on `merge_group`; it is made required through branch protection (outside the repo), not through
   `tests-passed`. G1–G4 join `tests-passed.needs` via the `guardrails` job.
3. **Session-local directories:** `docs/superpowers/` moves to `internal/superpowers/`;
   `internal/libretti/` and `internal/superpowers/` stay tracked; `scratchpad/`, `.codex/`,
   `__pycache__/` and `.gradle-home` are gitignored. Pushed history is accepted as-is (no rewrite).
4. **Merge ownership:** `.github/CODEOWNERS` names the maintainer as required reviewer for `AGENTS.md`,
   `CLAUDE.md`, `.agents/`, `.claude/`, `.github/copilot-instructions.md`, `.github/workflows/`,
   `scripts/guardrails/`.
5. **PR shape:** stacked PRs, starting from the drift-fix PR (#1000), one step per level; the
   `guardrails` CI job lands with G1 and later gates extend it. (First answered "independent PRs";
   changed to stacked by the maintainer after #1000 was opened.)
6. **G3:** a formatting-only `spotlessApply` PR lands first; the gate PR then flips the config.
