# BTrace QE Agent

`btrace-qe` is a manually run, reproducible harness that probes BTrace itself for spec-conformance
gaps and misuse-resilience defects: malformed CLI and agent arguments, absurd inputs, and similar
abuse. It is a developer tool, not a test suite. It is never part of `build` or `check`, and it
always exits `0`.

## Running it

```bash
./gradlew :btrace-qe:qe                                    # full corpus
./gradlew :btrace-qe:qe -Pqe.surface=agent-args            # one surface (comma-separated list allowed)
./gradlew :btrace-qe:qe -Pqe.case=cli.missing-operand      # one case
./gradlew :btrace-qe:qe -Pqe.seed=42                       # recorded seed (default 20260811)
./gradlew :btrace-qe:qe -Pqe.timeout=60000                 # per-step timeout in ms (default 30000, minimum 10000)
scripts/qe-agent.sh --surface cli --timeout 60000
```

A non-default `-Pqe.timeout` is recorded in the report's command line and in every repro command.
Each case's `runs/<case-id>/` directory is wiped before that case runs, so its evidence is always
from the current run; other cases' directories are left untouched.

Each run writes `btrace-qe/build/qe-report/report.md` and a deterministically ordered
`report.json`, plus per-case evidence (`stdout.log`, `stderr.log`, `steps.log`, and
`thread-dump.txt` on a timeout) under `btrace-qe/build/qe-report/runs/<case-id>/`.

`TEST_JAVA_HOME` (falling back to `JAVA_HOME`) selects the JDK for the target JVMs and for the
`btrace` client process launched by client-mode cases. The canary client runs inside the harness
JVM itself (the Gradle toolchain JDK), not on that JDK.

## What it checks

Every case is judged against five invariants:

- the process must not crash (including BTrace terminating the user's application at startup)
- the process must not hang or deadlock
- the target's state must not be corrupted
- BTrace must still be able to operate on the JVM afterwards (a canary probe is attached after
  the abuse)
- the user must be told what went wrong, without a raw stack trace

A case can be resilient (nothing broke) while still failing guidance (the bad input was silently
ignored). The report shows both.

Severities: S1 crash/hang/corruption · S2 BTrace unusable afterwards · S3 behavior differs from
the docs · S4 user not guided.

A step that fails without timing out and without the target dying (for example the target JVM
could not be launched) is a harness error, not a product defect: the case is reported with
`NA/NA/NA`, no findings, and the error text in the `harness error` column (`harnessError` in
`report.json`).

## Adding a case

Add a YAML file under `btrace-qe/cases/<surface>/`. The schema is the Task 3 schema block in
`internal/plans/2026-09-25-btrace-qe-agent-phase1.md`; the loader enforces it and rejects unknown
keys. There are two modes: `given.mode: target` launches a fixture JVM with
`-javaagent:btrace.jar=<agentArgs>`, and `given.mode: client` runs the real `btrace` client entry
point with `clientArgs`. A target case must start with `launch` and run `awaitReady` before
`canary` and `shutdown`; a client case must start with `runClient`. Adding a case to an existing
surface needs no code change.

`agentArgs` supports these placeholders:

- `${port}` — the free port the harness allocated for this case. If the resolved `agentArgs` sets
  no `port=` key, the harness appends `port=<allocated>` itself (empty `agentArgs` becomes exactly
  `port=<allocated>`); set `port=` explicitly to place or abuse the port.
- `${runDir}` — the case's evidence directory, `btrace-qe/build/qe-report/runs/<case-id>/`
  (also the target JVM's working directory).
- `${probeClassesDir}` — the directory holding the compiled QE probes, e.g.
  `${probeClassesDir}/io/btrace/qe/probe/QeStartupProbe.class`.

A target case that loads a script (`script=`) must also pass `noServer=false`. With a script the
agent starts no command server by default, so the canary would otherwise have to load a second,
dynamic agent instead of exercising the instance under test.

## Harness self-tests

```bash
./gradlew :btrace-qe:test         # fast unit tests (part of check)
./gradlew :btrace-qe:qeSelfTest   # end-to-end self-tests against real JVMs (never part of check)
```

## Design

See `internal/specs/2026-08-11-btrace-qe-agent-design.md` for the full design and for the
surfaces planned beyond this first cut.
