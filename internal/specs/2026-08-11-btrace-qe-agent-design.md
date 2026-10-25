# BTrace QE Agent — Design

Date: 2026-08-11
Status: Approved for implementation (approach confirmed 2026-08-11)

## Purpose

A manually-runnable, reproducible quality-engineering harness that behaves like a dedicated QE
engineer probing BTrace. It systematically attacks the product's user-facing surfaces and answers
two questions per probe:

1. **Conformance** — does observed behavior match what the documentation claims?
2. **Misuse resilience** — when the user does something absurd, does the product stay safe?

"Safe" is defined precisely: the target JVM does not crash, nothing is corrupted, nothing hangs or
deadlocks, the bad attempt is either rejected with a clean diagnostic or ignored entirely, and the
product remains able to continue operating afterwards.

A third, softer question rides along: **guidance** — even when BTrace survives, does it tell the
user what went wrong well enough that they can fix it? Silently swallowing a malformed value is
resilient but unguiding, and both facts are reported.

## Non-goals

- Not a CI gate. The task always exits 0 and produces a report; it is never wired into `check`.
- Not a replacement for `integration-tests`. Confirmed defects get a permanent regression test there;
  the QE agent is the instrument that finds them.
- Not the Extension TCK (in-flight, separate effort). That certifies third-party extension authors.
  This probes the product as shipped to end users.
- No unbounded fuzzing. All randomness is seeded and budgeted.

## Module layout

New root module `btrace-qe`. `settings.gradle` auto-includes any root directory named `btrace-*`
that contains a `build.gradle`, so no settings change is required.

```
btrace-qe/
  build.gradle
  cases/
    cli/*.yaml
    agent-args/*.yaml
    script-compile/*.yaml
    lifecycle/*.yaml
    protocol/*.yaml
    extensions/*.yaml
  src/main/java/io/btrace/qe/
    QeMain.java              entry point, CLI parsing, orchestration
    model/                   Case, Step, Expectation, Verdict, Finding, Severity
    corpus/                  YamlCaseLoader, CaseRegistry, ProgrammaticCase SPI
    exec/                    TargetJvm, ClientDriver, ProcessRunner, Timeouts
    oracle/                  the five invariant checks (see below)
    generate/                seeded mutator families
    report/                  MarkdownReporter, JsonReporter, EvidenceStore
  src/main/java/io/btrace/qe/cases/   programmatic cases (raw socket, races)
  src/main/java/qe/targets/ target applications the cases attach to
```

`build/` is already gitignored repo-wide, so reports under `btrace-qe/build/qe-report/` are not
tracked.

## Invocation

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qe
./gradlew :btrace-qe:qe -Pqe.surface=agent-args
./gradlew :btrace-qe:qe -Pqe.case=agent-args.fileroll-negative
./gradlew :btrace-qe:qe -Pqe.seed=42 -Pqe.budget=200
./gradlew :btrace-qe:qe -Pqe.baseline=btrace-qe/known-findings.json
scripts/qe-agent.sh --surface script-compile --seed 0
```

`scripts/qe-agent.sh` is a thin wrapper that forwards to the Gradle task; it exists so the agent can
be run without remembering the property names.

The `qe` task depends on `:btrace-dist:btraceJar` and `:btrace-dist:explodeExtensions`. It never runs
as part of `build` or `check`.

### Options

| Option | Property | Default | Meaning |
|---|---|---|---|
| `--surface` | `qe.surface` | all | comma-separated surface filter |
| `--case` | `qe.case` | all | run one case by id |
| `--seed` | `qe.seed` | `20260811` | seed for every generative mutator |
| `--budget` | `qe.budget` | `150` | max generated cases per surface |
| `--timeout` | `qe.timeout` | `30000` | per-step hard timeout, ms |
| `--parallel` | `qe.parallel` | `1` | concurrent cases; report order stays sorted |
| `--baseline` | `qe.baseline` | none | JSON of accepted findings to subtract from the summary |
| `--java-home` | `qe.javaHome` | `TEST_JAVA_HOME` → `JAVA_HOME` → `java.home` | JDK for target apps |

## Reproducibility contract

- The case corpus is committed; running the same corpus at the same seed on the same JDK produces
  the same set of executed probes in the same order.
- All randomness flows from a single seeded `java.util.Random` per mutator family, derived as
  `seed ^ familyHash`, so adding a family does not perturb existing families' streams.
- Cases execute sorted by id; the report is emitted sorted by id regardless of `--parallel`.
- Every report records: seed, git SHA, `btrace` version, JDK vendor/version, OS, host CPU count,
  the exact case corpus hash, and the full command line.
- Timing-derived values never enter case identity or the report body (only into evidence files).

## Case definition

### Declarative cases

`btrace-qe/cases/<surface>/<id>.yaml`. This is where the bulk of the corpus lives, and where new
probes are added without touching the runner.

```yaml
id: agent-args.fileroll-negative
surface: agent-args
intent: resilience          # resilience | conformance
title: "Negative fileRollMilliseconds is accepted by the agent"
spec:                       # required for intent: conformance, optional otherwise
  doc: docs/QuickReference.md
  claim: "fileRollMilliseconds=<n> - roll the output file every n milliseconds"
given:
  target: SteadyStateTarget          # a target app class in qe.targets
  agentArgs: "script=Noop.class,scriptOutputFile=out.txt,fileRollMilliseconds=-5"
steps:
  - launch
  - awaitReady
expect:
  reject_or_ignore: true             # either a clean error or a documented no-op
  message_must_mention: ["fileRollMilliseconds"]
  canary: true                       # a known-good probe must still work afterwards
severity_if_failed: S4
```

`steps` is a small vocabulary — `launch`, `awaitReady`, `attach`, `submit`, `sendEvent`,
`killClient`, `detach`, `reattach`, `retransformStorm`, `stdinCommand`, `awaitExit` — each with a
per-step timeout override. Adding a verb is a runner change; adding a case is not.

### Programmatic cases

A `ProgrammaticCase` SPI for probes YAML cannot express: raw-socket wire frames, concurrent attach
storms, killing the client mid-`submit`, and anything needing precise interleaving. Discovered via
`ServiceLoader`, registered under the same id namespace, reported identically.

### Generated cases

Seeded mutator families that derive probes from a valid baseline. Each generated case gets a stable
synthetic id (`<family>.<seed>.<index>`) so a finding can be re-run in isolation.

- **Script mutators** — drop a required annotation; wrong handler parameter types; `@Duration` where
  it is not permitted; thousands of probes in one script; recursive handler; `@OnMethod` matching
  `java.lang.Object.*`; NUL and astral-plane codepoints in class/method patterns; catastrophic
  backtracking regexes (`/(a+)+$/`); `Integer.MIN_VALUE` and `Long.MAX_VALUE` in `@OnTimer`;
  megabyte string constants.
- **Arg mutators** — empty values; megabyte values; duplicate keys; unknown keys; `=` and `,` inside
  values; path traversal; non-existent and unwritable directories; ports `-1`, `0`, `65536`,
  `99999999`; missing operand for a flag that consumes one.
- **Lifecycle mutators** — attach twice concurrently; attach during target shutdown; client killed
  mid-submit; detach/reattach loops; retransform storms; submit while a prior probe is unwinding.

Budget is enforced per surface; whatever is dropped by the budget is named in the report rather than
silently truncated.

## The oracle

Every case, regardless of what else it asserts, is judged against five invariants. This is what makes
the harness a QE agent rather than a collection of assertions.

| Invariant | Decision procedure |
|---|---|
| **No crash** | target JVM exit code matches the case's expectation; no `hs_err_pid*.log` appears in the run directory; the target application's own stderr contains no unhandled throwable it did not deliberately raise |
| **No hang or deadlock** | every step runs under a hard timeout; on expiry the runner issues `jcmd <pid> Thread.print` against the target, scans for `Found N deadlock`, stores the dump as evidence, and fails the case at S1 |
| **No corruption** | the target app emits a self-check line (a checksum over state it mutates) which must still be correct; instrumented classes dumped via `dumpClasses` are run through ASM `CheckClassAdapter`; any produced output file, JFR recording, or roll-over set must parse |
| **Still operating** | after the abuse, a known-good canary probe is submitted to the *same* still-running JVM and must produce its expected output. This is the operational definition of "allows continuous operation" |
| **Clean guidance** | the user-visible diagnostic must be non-empty, must mention the offending input, and on the default (non-`-v`) path must not be a raw stack trace |

A case yields a three-dimensional verdict rather than a boolean:

- `RESILIENCE`: pass / fail — invariants 1–4
- `CONFORMANCE`: pass / fail / n/a — documented behavior vs. observed
- `GUIDANCE`: good / poor — invariant 5

This matters: an agent option that silently discards a malformed value passes resilience and fails
guidance, and both facts belong in the report.

## Severity

| | Meaning |
|---|---|
| **S1** | target JVM crashed, hung, deadlocked, or its state was corrupted |
| **S2** | canary failed — after the abuse, BTrace can no longer operate on that JVM |
| **S3** | documented behavior does not match actual behavior |
| **S4** | survived, but the user is not guided: no message, unclear message, or silent discard |

## Report

Written to `btrace-qe/build/qe-report/`:

- **`report.md`** — environment header (seed, SHA, JDK, corpus hash, command line), a summary table
  of counts by surface × severity, then one section per finding containing: what was tried, the exact
  single-case repro command, expected vs. actual, severity, and paths to the evidence.
- **`report.json`** — the same content, deterministically ordered, for diffing consecutive runs.
- **`runs/<case-id>/`** — command lines, target stdout/stderr, client stdout/stderr, thread dumps,
  any `hs_err` file, and produced artifacts.

With `--baseline`, findings present in the supplied JSON are moved to an "accepted" section so new
findings stand out. The exit code is 0 either way.

## Surfaces covered in the first cut

All four, with the framework built so further surfaces slot in as case directories.

1. **CLI and agent arguments** — `btrace-client` `Main`'s hand-rolled argument loop (`args[++count]`
   indexing, flags that consume an operand, oneliner mode, `Doctor`) and `btrace-agent`
   `Main#parseArgs` / `ArgsMap`. Several agent options already swallow unparseable values without a
   diagnostic; those are expected S4 findings on the first run.
2. **Script compile and verifier conformance** — every `VerifierVisitor` rejection rule as a
   conformance case (`no.try`, `no.throw`, `no.new.object`, `execution.loop.danger`, and the rest),
   plus the script mutator family.
3. **Attach lifecycle and runtime abuse** — concurrent and reentrant attach, attach during shutdown,
   client killed mid-submit, detach/reattach storms, retransform storms, handler resource abuse.
   Highest expected S1/S2 yield and the most timing-sensitive; every case here carries a thread dump
   on timeout.
4. **Protocol, extensions, and permissions** — malformed and truncated v2 frames over a raw socket,
   illegal command sequences, version-negotiation abuse; permission-policy source precedence,
   privileged-extension blocking, and SHIM fallback behavior.

## Extending the corpus

Three routes, in increasing cost:

1. Add a YAML file under `cases/<surface>/`. No runner change.
2. Add a mutator to an existing generative family. Deterministic stream isolation means existing
   generated ids are unaffected.
3. Add a `ProgrammaticCase` or a new `steps` verb. Runner change.

A separate `/qe-explore` skill (out of scope for this spec, built afterwards) reads the docs and
source, invents novel abuse cases, and writes them as YAML files to be reviewed and committed. The
harness itself stays fully deterministic; creativity enters only through committed corpus changes.

## Target applications

Small, purpose-built apps under `qe.targets`, each printing `ready:<pid>` on startup and a
periodic self-check checksum line:

- `SteadyStateTarget` — a hot loop with a stable, verifiable state checksum
- `ShutdownRaceTarget` — exits on command, for attach-during-shutdown probes
- `ManyClassesTarget` — generates and loads many classes, for retransform storms
- `ThreadyTarget` — many threads with contended locks, for deadlock-adjacent probing

## Risks

- **Flakiness in the lifecycle surface.** Race-dependent cases can produce non-reproducible findings.
  Mitigation: each lifecycle case is re-run up to three times before being reported, and the report
  records how many of the attempts reproduced. A 1-of-3 finding is reported with that fact visible
  rather than suppressed.
- **Runtime.** A full sweep launches many JVMs. Mitigation: surface filters, `--budget`, and
  `--parallel`.
- **Duplication with the Extension TCK.** The permissions and extension cases must probe end-user
  behavior, not re-certify extension authorship. If the TCK lands, its checks are referenced rather
  than copied.

## Success criteria for the harness itself

- `./gradlew :btrace-qe:qe` runs from a clean checkout, exits 0, and writes a report.
- Two consecutive runs at the same seed produce byte-identical `report.json` apart from the
  environment header's timestamp.
- Running a single case id reproduces that case's reported outcome in isolation.
- The first full sweep surfaces at least one genuine finding in each of the four surfaces, with a
  working repro command for each.
