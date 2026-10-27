# btrace-qe exploration log

Every idea explored for the corpus, kept or not, so later sessions don't repeat them. Format:
`- <date> <surface> <lens> — <idea> — <grounding> — <outcome>`. Maintained by the `qe-explore` skill (`.agents/skills/qe-explore/`; `/qe-explore` in Claude Code, `/skill:qe-explore` in Pi).

## Surface rotation

agent-args → cli → (blocked surfaces: script-compile, lifecycle, protocol, extensions)

The `demos` surface is outside the rotation: it replays the shipped demo scenarios (`demos/`,
`docs/tutorials/`) rather than probing for misuse, so a finding there means a demo is broken.

## Case shapes

- `given.mode: target` — launch a fixture JVM with the agent (`given.target`, `given.agentArgs`);
  steps `launch`, `awaitReady`, `canary`, `shutdown`, `awaitExit`.
- `given.mode: client` — run the CLI on its own (`given.clientArgs`); steps `runClient`, `awaitExit`.
- `given.mode: attach` — launch a fixture JVM *without* the agent (`given.target`) and attach the
  real CLI to it by PID (`given.clientArgs`; `${pid}`, `${port}` and `${runDir}` are substituted,
  so `-p ${port}` keeps concurrent runs off the default port 2020). Steps, in this order:
  `launch`, `awaitReady`, `attach`, `awaitOutput`, `awaitClientExit`, `detach`, `shutdown`,
  `awaitExit` (`attach` needs `awaitReady` first; `awaitOutput`/`awaitClientExit`/`detach` need
  `attach` first; each of the three is optional). `awaitClientExit` waits, within the step budget,
  for a client that should end by itself (an error on stderr only, `-l`, `-lp`, `-x`); one that never
  exits times out and is reported as a hang. Use it instead of `awaitOutput` when the client's
  answer goes to stderr, so `shutdown` does not reap the client before it reports. `awaitOutput`
  waits, within the step budget, for `expect.outputMustMatch` — regexes that must each match a line
  of the *client's* stdout, in order — and a pattern that never shows up is an S2 finding, not a
  hang. `detach` ends the client the way Ctrl+C would (TERM → shutdown hook → exit command), so the
  target must still be alive for `shutdown` to stop it cleanly. Evidence adds `client-stdout.log`
  and `client-stderr.log`, and the last line of `steps.log` adds `clientExitCode=` — the client's
  own exit code when it ended before the harness reaped it (on its own, or 143 after `detach`'s
  TERM), `null` when the harness had to kill it. A conformance attach case can assert it with
  `expect.clientExitCode: <int>` (needs an `awaitClientExit` step); a mismatch, or a client the
  harness had to kill, is an S3 conformance finding. Sample: `cases/demos/attach-oneliner-smoke.yaml`;
  fixture:
  `qe.targets.AttachDemoTarget` (prints `ready:<pid>`, calls `work(int)` every 50 ms).
- Launched-target options (`target` and `attach` modes), for replaying the shipped `demos/`:
  - `given.target` may name a `.java` file relative to the repository root
    (`demos/01-oneliner/OrderServiceApp.java`): it is run with single-file source launch (JDK 11+)
    from the file's directory, so relative paths such as `-Dbtrace.permissions=permissions.properties`
    work as in the demo's `run.sh`. Such a demo app is stopped by `shutdown` with a TERM signal (as
    `run.sh` does), so its exit code is the signal's, not 0. Class-name targets are unchanged.
  - `given.jvmArgs` (list) goes first on the java command line; `${btraceJar}` (the masked jar),
    `${runDir}`, `${port}` and `${targetDir}` (the demo file's directory) are substituted. An attach
    case can thus start a prepared JVM (`-javaagent:${btraceJar}=port=0`) and attach without `-p`.
  - `given.readyRegex` (default `^ready:`): readiness is the first stdout line matching it. With the
    default the PID is parsed from that line; otherwise it is the launched process's OS pid (demo
    apps print `[demo] ... running - stop with Ctrl+C`).
  - `${targetDir}` is also substituted in `clientArgs` (`${targetDir}/LatencyHistogram.java`) and
    is only defined for `.java` targets. Sample: `cases/demos/01-oneliner-first-trace.yaml`.
  - The harness presents the dist directory as an installation: `btrace.jar` under `<home>/libs/`,
    the exploded extensions under `<home>/extensions/`. The client gets `-Dbtrace.libs=<home>/libs`
    and both the client and the launched target get `BTRACE_HOME=<home>`, so a source probe that
    imports an extension API (`io.btrace.metrics.MetricsService`, ...) compiles and the agent finds
    the bundled extensions, as under `bin/btrace`.

## Failing the build

`./gradlew :btrace-qe:qe` only reports by default. `-Pqe.failOn=<S1|S2|S3|S4>` makes QeMain exit 1
(and the task fail) when any finding is at or above that severity or any case had a harness error;
the last `[qe] FAILED: ...` line names the counts and `build/qe-report/report.md`. An unknown value
also fails, so a misspelled gate cannot pass silently. CI uses `-Pqe.failOn=S2`.

## Log

- 2026-10-25 agent-args wrong type — non-numeric fileRollMaxRolls — agent/Main.java parseArgs (NumberFormatException swallowed) — kept agent-args.fileroll-maxrolls-non-numeric (S4)
- 2026-10-25 agent-args out of range — negative fileRollMilliseconds — agent/Main.java parseArgs — kept agent-args.fileroll-ms-negative (S4)
- 2026-10-25 agent-args missing value — empty agent options — agent/Main.java loadArgs — kept agent-args.empty-args (clean; empty options are a legal server-only setup)
- 2026-10-25 cli missing value — `-p` without operand — client/Main.java arg loop → usage() — kept cli.missing-operand (clean)
- 2026-10-25 cli unknown key — unrecognized flag — client/Main.java arg loop → usage() — kept cli.unknown-flag (clean)
- 2026-10-25 cli doc claim — attach to non-existent JVM name — client/Main.java errorExit + JpsUtils — kept cli.attach-nonexistent-target (S4: raw IllegalAccessError on jmods-less JDKs)
- 2026-10-25 agent-args out of range — `port=70000` — agent/ControlServer.java:160-161 parsePort — kept agent-args.port-out-of-range (was S1: JVM aborted at startup, exit 134; S4: message omitted the value, raw stack trace; fixed — the agent reports `invalid agent option port=70000` on one line and starts without a command server)
- 2026-10-25 agent-args conflicting value — `bindAddress=0.0.0.0` (non-loopback) — agent/ControlServer.java:174-175 resolveAddress — kept agent-args.bind-address-non-loopback (was S1: exit 134; S4: message never named bindAddress; fixed — reported as `invalid agent option bindAddress=0.0.0.0`, server not started, JVM keeps running)
- 2026-10-25 agent-args path to missing dir — `dumpClasses=true,dumpDir=<missing>` — core/DebugSupport.java:92 mkdirs — kept agent-args.dumpdir-missing-dir (clean; missing dir is created by design)
- 2026-10-25 agent-args wrong kind — unresolvable `bindAddress` hostname — agent/ControlServer.java resolveAddress — lead (observed: JVM dies at startup, exit 134; no case yet)
- 2026-10-25 cli wrong type — `-p notaport` — client/Main.java:184-187 (NumberFormatException → usage) — kept cli.port-non-numeric (S4: usage never names the rejected value)
- 2026-10-25 cli duplicate key — `-p 2020 -p 3030` — client/Main.java:182 (`!portDefined` guard → usage) — kept cli.duplicate-port (clean)
- 2026-10-25 cli doc claim — `-r help` shows remote-command help — docs/QuickReference.md; client/Main.java:154-159 — kept cli.remote-help (conformance pass)
- 2026-10-25 cli huge/unicode value — unicode JVM name — client/Main.java:271 errorExit — blocked (name lookup dies first in JpsUtils on jmods-less JDKs; needs a JDK exporting jdk.internal.jvmstat)
- 2026-10-25 cli out of range — `-p 70000 <name>` — client/Main.java:184 (no range check) — blocked (JVM lookup at :269 fails before the port is used; client mode has no live target)
- 2026-10-25 cli wrong file kind — `-n <bad oneliner>` / missing script file — client/Main.java:341,355 — blocked (JVM resolved before the script is read; client mode has no live target)
- 2026-10-25 cli missing value — option + PID but no script (`-p 2020 2147483647`) — client/Main.java file mode read `args[count + 1]` unchecked — kept cli.pid-without-script (was S4: uncaught ArrayIndexOutOfBoundsException as a raw stack trace, after a needless JVM lookup; fixed — file mode now exits 1 with "Missing BTrace script" before the lookup). Note: a lone PID never reaches this path (`args.length < 2` prints usage first)
- 2026-10-25 agent-args wrong file kind — `script=` pointing at a non-class file — agent/Main.java loadBTraceScript — lead (observed: silently ignored; earlier attempt used non-portable /etc/hosts)
- 2026-10-25 agent-args unknown key — option not in Args.java (e.g. `statsdPort`) — agent/Main.java parseArgs default branch — promoted (see agent-args.unknown-option-typo)
- 2026-10-25 agent-args unknown key — misspelled option `scriptOutputDri=` — agent/Main.java:1157-1163 (default branch handles only `$`-prefixed keys) — kept agent-args.unknown-option-typo (S4: typo silently dropped)
- 2026-10-25 agent-args path to missing file — `script=${runDir}/no-such-probe.class` — agent/Main.java:1392 + instr/BTraceProbeFactory.java:72 (canLoad false → return false) — kept agent-args.script-missing-file (S4: startup script silently not loaded)
- 2026-10-25 agent-args separator injection — `statsd=localhost:8125:9` — agent/Main.java:1080-1091 (3-part split matches neither branch; only the 2-part bad-port path warns) — kept agent-args.statsd-extra-separator (S4: silently ignored)
- 2026-10-25 agent-args wrong type — `debug=no` (and `stdout=no`) — agent/Main.java parseArgs `!"false".equals(p)` — kept agent-args.debug-non-boolean (was S4: debug=no silently enabled debug; fixed — values other than true/false now warn and leave the option off; token is the literal `debug=no`, since "debug"/"no" alone match unrelated output)
- 2026-10-25 agent-args doc claim — probe targeting an io.btrace.* class — instr/BTraceTransformer.java:129 — blocked (needs a probe aimed at an io.btrace class; loads cleanly, never fires, no warning)
- 2026-10-27 mcp doc claim — `btrace -x -n '<oneliner>' <PID>` (the MCP detach_probe path) prints the probe id that `-r`/stop_detached_probe needs — client/Main.java unattended branch (`client.disconnect()` after STATUS OK) + agent/RemoteClient.java DISCONNECT `setProbeId` + core/comm/DisconnectCommand.java:60 — kept mcp.unattended-detach-prints-probe-id (clean: `BTrace Probe: <uuid>` printed, client exits, target with the detached probe still shuts down)
- 2026-10-27 mcp conflicting keys — explicit `-p <free port>` against a JVM prepared with `port=0` (the MCP server always passes a per-call port, default 2020) — client/Client.java attach (agentAlreadyRunning → configureConnection replaces the requested port with the published `btrace.port`) — kept mcp.explicit-port-vs-prepared-agent (clean: the requested port is overridden, the probe output arrives)
- 2026-10-27 mcp doc claim — `btrace -l` marks a prepared JVM with `(+)`, the same `btrace.port` signal MCP list_jvms uses for its attachment status — client/JpsUtils.java listVms/hasBTraceServer — kept mcp.list-jvms-marks-prepared-agent (conformance pass)
- 2026-10-27 mcp path to missing value — `-r <unknown probe id> exit <PID>` (stop_detached_probe with a stale id) — client/Client.java reconnect: `IOException("Unable to reconnect to an active probe: <id>")` → Main errorExit to stderr — blocked (attach mode can only wait on client *stdout* via awaitOutput; needs an `awaitClientExit` step so a stderr-only error path isn't reaped by `shutdown`)
- 2026-10-27 mcp missing value — `-r <id> exit <PID>` against a JVM with no BTrace agent — client/Client.java discoverPreparedAgent: "No BTrace control endpoint is available for PID <pid>" — blocked (same stderr-only path as above; also the only distinctive user token is the PID, and messageMustMention does not substitute `${pid}`)
- 2026-10-27 mcp order dependence — detach (`-x`) then `-lp` then `-r <id> exit`: the full MCP detach_probe → list_probes → stop_detached_probe round trip — agent/Client.java listProbes lists only disconnected RemoteClients — blocked (needs a second sequential `attach` step against the same target, with the first client's probe id fed into the second's args)
- 2026-10-27 mcp missing value — `-lp <PID>` on a JVM with no detached probes — client/Main.java listProbes branch (attaches, which dynamically loads an agent as a side effect, then prints nothing) — blocked (empty stdout gives awaitOutput nothing to wait on; needs `awaitClientExit` + client exit-code expectation)
- 2026-10-27 mcp unknown key — MCP send_event with a named event on a live session — client/Client.java sendEvent(String) — blocked (the CLI only reaches it through the interactive Ctrl+C console or `-r <id> event <name>`, which needs a detached probe first; see the round-trip entry)
- 2026-10-27 mcp path to missing value — `-r <unknown probe id> exit <PID>` on a prepared JVM (stop_detached_probe with a stale id; was blocked, unblocked by `awaitClientExit`) — client/Client.java reconnect failure → Main errorExit — kept mcp.reconnect-unknown-probe-id (clean: stderr `Unable to reconnect to an active probe: qe-no-such-probe`, client exits in ~0.6 s)
- 2026-10-27 mcp missing value — `-r <id> exit <PID>` against a JVM with no BTrace agent (was blocked) — client/Client.java discoverPreparedAgent — kept mcp.reconnect-without-agent (clean, resilience only: stderr `No BTrace control endpoint is available for PID <pid>`; no token because the PID is the only distinctive input and is not substitutable)
- 2026-10-27 mcp missing value — `-lp <PID>` on a JVM with no detached probes (was blocked) — client/Main.java listProbes branch + agent/Client.java listProbes — kept mcp.list-probes-none-detached (clean: client returns in ~1.3 s)
- 2026-10-27 mcp missing value — `-lp` with nothing to list prints nothing at all (no "no probes" line), and loads an agent into the JVM as a side effect — client/Main.java listProbes + ListProbesCommand.print — lead (observed in mcp.list-probes-none-detached; empty output is indistinguishable from a silent failure; no case until the expected message is a documented claim)
- 2026-10-27 mcp doc claim — the `-r` path logs `Attaching BTrace to PID: <pid>` although it only reads properties and never attaches — client/Main.java (log before the -r branch) — lead (cosmetic; misleading when the JVM has no agent)
- 2026-10-27 mcp doc claim — `-r` failures exit 1: documented in docs/QuickReference.md ("If the probe id is unknown, or the JVM has no BTrace control endpoint, `-r` prints the reason and exits with code 1") after confirming both paths end in client/Main.java:409-410 `errorExit(exp.getMessage(), 1)` — mcp.reconnect-unknown-probe-id and mcp.reconnect-without-agent promoted to conformance with `expect.clientExitCode: 1` (both PASS, clientExitCode=1)
