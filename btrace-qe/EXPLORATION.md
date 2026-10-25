# btrace-qe exploration log

Every idea explored for the corpus, kept or not, so later sessions don't repeat them. Format:
`- <date> <surface> <lens> — <idea> — <grounding> — <outcome>`. Maintained by the `qe-explore` skill (`.agents/skills/qe-explore/`; `/qe-explore` in Claude Code, `/skill:qe-explore` in Pi).

## Surface rotation

agent-args → cli → (blocked surfaces: script-compile, lifecycle, protocol, extensions)

## Log

- 2026-10-25 agent-args wrong type — non-numeric fileRollMaxRolls — agent/Main.java parseArgs (NumberFormatException swallowed) — kept agent-args.fileroll-maxrolls-non-numeric (S4)
- 2026-10-25 agent-args out of range — negative fileRollMilliseconds — agent/Main.java parseArgs — kept agent-args.fileroll-ms-negative (S4)
- 2026-10-25 agent-args missing value — empty agent options — agent/Main.java loadArgs — kept agent-args.empty-args (clean; empty options are a legal server-only setup)
- 2026-10-25 cli missing value — `-p` without operand — client/Main.java arg loop → usage() — kept cli.missing-operand (clean)
- 2026-10-25 cli unknown key — unrecognized flag — client/Main.java arg loop → usage() — kept cli.unknown-flag (clean)
- 2026-10-25 cli doc claim — attach to non-existent JVM name — client/Main.java errorExit + JpsUtils — kept cli.attach-nonexistent-target (S4: raw IllegalAccessError on jmods-less JDKs)
- 2026-10-25 agent-args out of range — `port=70000` — agent/ControlServer.java:160-161 parsePort — kept agent-args.port-out-of-range (S1: JVM aborts at startup, exit 134; S4: message omits the value, raw stack trace)
- 2026-10-25 agent-args conflicting value — `bindAddress=0.0.0.0` (non-loopback) — agent/ControlServer.java:174-175 resolveAddress — kept agent-args.bind-address-non-loopback (S1: exit 134; S4: message never names bindAddress)
- 2026-10-25 agent-args path to missing dir — `dumpClasses=true,dumpDir=<missing>` — core/DebugSupport.java:92 mkdirs — kept agent-args.dumpdir-missing-dir (clean; missing dir is created by design)
- 2026-10-25 agent-args wrong kind — unresolvable `bindAddress` hostname — agent/ControlServer.java resolveAddress — lead (observed: JVM dies at startup, exit 134; no case yet)
- 2026-10-25 cli wrong type — `-p notaport` — client/Main.java:184-187 (NumberFormatException → usage) — kept cli.port-non-numeric (S4: usage never names the rejected value)
- 2026-10-25 cli duplicate key — `-p 2020 -p 3030` — client/Main.java:182 (`!portDefined` guard → usage) — kept cli.duplicate-port (clean)
- 2026-10-25 cli doc claim — `-r help` shows remote-command help — docs/QuickReference.md; client/Main.java:154-159 — kept cli.remote-help (conformance pass)
- 2026-10-25 cli huge/unicode value — unicode JVM name — client/Main.java:271 errorExit — blocked (name lookup dies first in JpsUtils on jmods-less JDKs; needs a JDK exporting jdk.internal.jvmstat)
- 2026-10-25 cli out of range — `-p 70000 <name>` — client/Main.java:184 (no range check) — blocked (JVM lookup at :269 fails before the port is used; client mode has no live target)
- 2026-10-25 cli wrong file kind — `-n <bad oneliner>` / missing script file — client/Main.java:341,355 — blocked (JVM resolved before the script is read; client mode has no live target)
- 2026-10-25 agent-args wrong file kind — `script=` pointing at a non-class file — agent/Main.java loadBTraceScript — lead (observed: silently ignored; earlier attempt used non-portable /etc/hosts)
- 2026-10-25 agent-args unknown key — option not in Args.java (e.g. `statsdPort`) — agent/Main.java parseArgs default branch — lead (observed: silently ignored)
- 2026-10-25 agent-args wrong type — `debug=no` (and `stdout=no`) — agent/Main.java parseArgs `!"false".equals(p)` — kept agent-args.debug-non-boolean (was S4: debug=no silently enabled debug; fixed — values other than true/false now warn and leave the option off; token is the literal `debug=no`, since "debug"/"no" alone match unrelated output)
- 2026-10-25 agent-args doc claim — probe targeting an io.btrace.* class — instr/BTraceTransformer.java:129 — blocked (needs a probe aimed at an io.btrace class; loads cleanly, never fires, no warning)
