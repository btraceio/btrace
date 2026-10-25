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
- 2026-10-25 agent-args out of range — `port=70000` — agent/ControlServer.java parsePort — lead (observed: JVM dies at startup, exit 134, raw stack trace; no case yet)
- 2026-10-25 agent-args wrong kind — unresolvable `bindAddress` — agent/ControlServer.java resolveAddress — lead (observed: JVM dies at startup, exit 134; no case yet)
- 2026-10-25 agent-args wrong file kind — `script=` pointing at a non-class file — agent/Main.java loadBTraceScript — lead (observed: silently ignored; earlier attempt used non-portable /etc/hosts)
- 2026-10-25 agent-args unknown key — option not in Args.java (e.g. `statsdPort`) — agent/Main.java parseArgs default branch — lead (observed: silently ignored)
- 2026-10-25 agent-args doc claim — probe targeting an io.btrace.* class — instr/BTraceTransformer.java:129 — blocked (needs a probe aimed at an io.btrace class; loads cleanly, never fires, no warning)
