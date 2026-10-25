# btrace-qe: run tiers, seeded generated cases, in-process property tests

Status: approved 2026-10-25 (defaults: `qe` runs all hand-written cases; client parser refactor deferred;
no new dependency — hand-rolled seeded property helper).

## Part A — run tiers
- Optional schema key `tier: smoke | full` (default `full`); `CaseDef` field; `YamlCaseLoader` validates it.
- `CaseRegistry.filter(surface, caseId, tier)`; smoke ⊂ full. `-Pqe.tier=smoke` (forwarded by `btrace-qe/build.gradle`),
  read by `QeMain`, recorded in report metadata / command line / repro command when non-default.
- Tag ~5 committed cases `tier: smoke`: one clean + one finding per surface, including one S1
  (`cli.missing-operand`, `cli.port-non-numeric`, `agent-args.empty-args`, `agent-args.fileroll-ms-negative`,
  `agent-args.port-out-of-range`).
- Docs: `docs/QEAgent.md`, `.agents/skills/qe-explore/reference.md` (schema).

## Part C — in-process property tests (no new dependency)
- Seeded helper in test sources of `btrace-agent` and `btrace-qe`: N iterations of `new Random(seed)`;
  `-Dbtrace.prop.seed`, `-Dbtrace.prop.iterations` (defaults: fixed seed, 200); failure message includes seed,
  iteration, input. Gradle `test` tasks forward those two system properties when given.
- `Main.parseAgentArgs`: never throws on random strings over `[a-z $ , = : space unicode]`; well-formed distinct
  non-list keys round-trip; list-valued keys (`grant`, `deny`, `allowExtensions`, `denyExtensions`, `probes`) absorb
  following bare tokens until the next `key=` token.
- `Main.parseFlag`: total; TRUE iff empty or `true` (any case), FALSE iff `false` (any case), else null.
- `YamlCaseLoader` fuzz: random documents (schema + unknown keys, random value types and nesting) either load
  or throw `CaseValidationException` naming the file. Fix what it finds: wrap raw exceptions (`Intent.valueOf`,
  `Integer.valueOf(exitCode)`, non-string keys, YAML syntax errors) in `CaseValidationException` with the file;
  reject duplicate YAML keys.
- Client CLI arg loop: not in-process (exits from `main`); covered by generated `cli.flags` cases.

## Part B — seeded generated cases
- `io.btrace.qe.generate.MutatorFamily`: `List<CaseDef> generate(Random rnd, int budget)`; per-family
  `Random(seed ^ family.id().hashCode())`.
- `agent-args.values`: option names by reflection over `io.btrace.core.Args` constants, minus harness-controlled and
  file/classpath options (`script`, `scriptdir`, `port`, `noServer`, `stdout`, `systemClassPath`,
  `bootClassPath`, `agentJar`, `config`, `libs`, `authTokenFile`, `probeDescPath`, `dumpDir`,
  `scriptOutputFile`, `scriptOutputDir`) × value lenses (empty, 10k chars, negative, > Long.MAX, non-numeric,
  unicode/NUL, injected `,` `=` `:`). Target mode, startup probe + `noServer=false,stdout=true`, 5 steps, `expect: {}`.
- `cli.flags`: client argv from the client flag list with value lenses, duplication, reordering, ending in an
  unused numeric PID and a non-existent script (`2147483647 qe-no-such-script.java`). A JVM *name* would make
  every case die in the known jmods/`--add-exports` launcher bug (JpsUtils name lookup) instead of exploring flag
  handling; a numeric PID skips that lookup. Client mode, `expect: {}`.
- `help` and `telemetry` are excluded from `agent-args.values` (`help` always exits the target; `telemetry`
  must never be enabled by a lens).
- Ids `gen.<family>.<seed>.<index>`; opt-in `-Pqe.generate=<budget per family>` (default 0); repro command carries
  `-Pqe.seed` and `-Pqe.generate`; each generated case written to `runs/<id>/case.yaml`.
- Unit tests: determinism per seed, seed sensitivity, budget respected, every case passes `CaseDef.validate()`.

## Verification
Red: YAML fuzz property fails on current loader; generator tests fail before implementation.
Green: `:btrace-agent:test`, `:btrace-qe:test`, `:btrace-qe:qeSelfTest`; `qe -Pqe.tier=smoke` (smoke only);
`qe` (all hand-written); `qe -Pqe.generate=10 -Pqe.seed=42` twice → identical report.json; single generated case
re-run by id; deep property run `-Dbtrace.prop.iterations=10000`; `spotlessCheck`; `./gradlew build`
(known JDK 28 toolchain failure reported as NOT VERIFIED).

## Deferred
Extract the client CLI argument loop into an exit-free parser so it can be property-tested in-process.
