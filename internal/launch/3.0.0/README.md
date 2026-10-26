# BTrace 3.0.0 launch copy

Drafts for the announcement wave. Nothing here is published by the repository; you post it.

| File | Channel | Lead |
|---|---|---|
| `press-release.md` | canonical, quotable | all three headlines |
| `linkedin-launch.md` | LinkedIn, day 0 | 2.x users: oneliners, flat DSL, production-safe attach |
| `x-thread-launch.md` | X, day 0 | same |
| `linkedin-ai.md` | LinkedIn, day 1 or 2 | coding-agent plugin, AI-workload extensions |
| `x-thread-ai.md` | X, day 1 or 2 | same |

## Before posting

1. Tag `v3.0.0` must exist so the `blob/v3.0.0/...` links resolve.
2. Render the GIFs and MP4s (`demos/README.md`, Recording) and attach the ones each file names; the MP4s are the better fit for LinkedIn and X.
3. Record the coding-agent session for demo 06 with a rehearsed question; do not post the AI
   copy with a recording that shows the agent fumbling.
4. Check the plugin is installable from `btraceio/agent-plugins` at posting time; the AI copy
   depends on it.
5. Replace the release-notes link if the GitHub release is published under a different tag name.

## Claims and their sources

| Claim | Source |
|---|---|
| oneliner syntax | `docs/tutorials/01-first-trace-in-2-minutes.md` |
| flat DSL, auto-imports | release notes, `docs/tutorials/02-oneliner-to-script.md` |
| `port=0`, token, loopback, doctor | `docs/GettingStarted.md` prepared mode section |
| call sites become no-ops on client exit | release notes, "Behavior changes worth knowing" |
| Aggregations removed, `libs=` removed | release notes, breaking changes |
| compiled probes need no change | release notes, highlights |
| plugin behavior and skill list | `btraceio/agent-plugins` `plugins/btrace-observability/README.md` (fetched 2026-10-26) |
| extension capabilities | `docs/BTraceTutorial.md` lessons 11 and 12, extension service interfaces |
| Java 8–27, pre-17 deprecated | release notes, deprecated |
