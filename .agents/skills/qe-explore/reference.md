# qe-explore reference

## Case schema (enforced by `YamlCaseLoader`; unknown keys are rejected)

```yaml
id: <surface>.<kebab-name>            # unique; file at btrace-qe/cases/<surface>/<kebab-name>.yaml
surface: agent-args | cli             # surfaces the harness can execute today
intent: resilience | conformance
tier: smoke | full                   # optional, default full; `-Pqe.tier=smoke` runs only smoke cases
title: "<what the user does, in one line>"
spec:                                 # conformance only (required there)
  doc: docs/<File>.md
  claim: "<verbatim sentence from that doc>"
given:
  mode: target                        # launch qe.targets.<target> with -javaagent:btrace.jar=<agentArgs>
  target: SteadyStateTarget
  agentArgs: "<raw agent options>"
  # or
  mode: client                        # run the real btrace CLI (btrace.jar via io.btrace.boot.Loader)
  clientArgs: ["<argv>", ...]
steps: [...]                          # target: launch, awaitReady, [canary], shutdown, awaitExit
                                      #   (launch first; awaitReady before canary/shutdown)
                                      # client: runClient, awaitExit
expect:                               # mapping, may be {}
  exitCode: <int>                     # conformance only
  messageMustMention: ["<token>", ...]
```

Placeholders in `agentArgs`: `${probeClassesDir}`, `${runDir}` (fresh per case), `${port}` (harness-allocated). If `agentArgs` has no `port=` key, the harness appends `port=<allocated>`; set `port=` yourself only when port is what you're abusing.

## Pitfalls (each produced a wrong verdict before)

| Pitfall | Do this instead |
|---|---|
| Startup script given as a class name or via `scriptdir=` | `script=${probeClassesDir}/io/btrace/qe/probe/QeStartupProbe.class` (a file path; `scriptdir` is not recursive) |
| Script case without `noServer=false` | Add `noServer=false`, otherwise the canary hits a second, dynamically loaded agent |
| `scriptOutputFile=` in a case with a `canary` step | Use `stdout=true`; file output also swallows the canary's output → false S2 |
| Probe or target in package `io.btrace.*` | BTrace never instruments `io/btrace/*`; it silently never fires |
| `exitCode` on a resilience case | Loader rejects it; use conformance with a doc claim |
| Guidance token for input that is actually valid | No token; resilience only (e.g. empty agent options are a legal "server only" setup) |
| Client case about a script/oneliner error (missing file, compile failure) | Blocked: the CLI resolves the JVM (`JpsUtils.findVmByName`) before reading the script, and client mode has no live target |
| Port-abuse case where the harness can't reach the agent | Expect no canary success; omit the `canary` step if the agent can't listen by design |

## Where to look, per surface

| Surface | Parse/act site | Doc claims |
|---|---|---|
| agent-args | `btrace-core/.../core/Args.java` (keys), `btrace-agent/.../agent/Main.java` `parseArgs`, `loadArgs`, `locateScripts`, `loadBTraceScript`; `agent/ControlServer.java` (`parsePort`, `resolveAddress`) | `docs/QuickReference.md` (agent options), `docs/GettingStarted.md`, `docs/PermissionPolicy.md` |
| cli | `btrace-client/.../client/Main.java` arg loop (`-p -u -o -d -pd -cp -statsd -host -r -lp -le -x -n -v -l --version`), `JpsUtils`, `Doctor` | `docs/QuickReference.md` (CLI), `docs/OnelinerGuide.md`, `docs/Troubleshooting.md` |
| not executable yet (log as *blocked*) | script compile/verifier (`btrace-compiler` `VerifierVisitor`), attach lifecycle, wire protocol, extensions | — |

## Mutation lenses (use ≥2 per batch)

missing value · wrong type · out of range / sign · unknown key · duplicate or conflicting keys · path to missing file / directory / wrong file kind · huge or unicode/NUL value · separator injection (`,` `=` `:` inside values) · order dependence · doc claim taken literally

## EXPLORATION.md entry format

```
- <date> <surface> <lens> — <idea> — <grounding> — <outcome: kept <id> | rejected (why) | blocked (needs ...) | lead (observed, no case yet)>
```
