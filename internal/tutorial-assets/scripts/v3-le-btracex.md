# V3 — Extensions and Permissions Without Tears (`-le` / `btracex`)

**Priority:** P1 · **Length:** 60–90s · **Format:** pure terminal, no talking head, rendered from a VHS tape.

**Based on:** `docs/tutorials/04-extensions-and-permissions.md`

---

## GOAL

Show a viewer that BTrace extensions add real capability (HdrHistogram percentiles) behind an
explicit permission gate, and that `-le`/`btracex` make a blocked extension's cause fully
inspectable instead of a mystery.

## HOOK

*(first 5s — screen only, no narration)*

Terminal showing a stream of raw `execution time:` lines (reminiscent of V1) freezes, then an
overlay implies there's a better way than eyeballing numbers:

```
processOrder
execution time: 331 ms
```

## SHOT LIST

| Timestamp | Terminal action | Overlay text |
|---|---|---|
| 0:00–0:05 | Static: raw timing output (HOOK) | "Eyeballing latency? There's a better way." |
| 0:05–0:15 | Terminal: `mkdir -p ~/.btrace` then `cat > ~/.btrace/permissions.properties <<'EOF' / allowExtensions=btrace-metrics / EOF` | "Grant the permission first" |
| 0:15–0:28 | Run `btrace <PID> LatencyHistogram.java`; the 5-second histogram report appears (`p50=...ms p95=...ms p99=...ms`) | "Real percentiles. No server, no network call." |
| 0:28–0:32 | `Ctrl+C`, stop demo app, flip policy: `cat > ~/.btrace/permissions.properties <<'EOF' / denyExtensions=btrace-metrics / EOF` | "Now deny it, and see what BTrace does" |
| 0:32–0:44 | Start fresh `java DemoApp.java`, run `btrace <NEW_PID> LatencyHistogram.java` — the `! ERROR` / `IllegalStateException: BTrace optional service unavailable` block appears | "Blocked, and it fails loudly — not silently" |
| 0:44–0:46 | `Ctrl+C` quickly (tutorial notes this repeats fast) | " " |
| 0:46–0:56 | Run `btrace -le <NEW_PID>`; output shows `Failed Extensions: 1. btrace-metrics: Blocked by policy (denyExtensions)` | "`-le`: ask BTrace what got blocked, and why" |
| 0:56–0:66 | Run `btracex list`; one line with `[PRIV]` appears | "`btracex list`: what's installed — no JVM needed" |
| 0:66–0:76 | Run `btracex inspect btrace-metrics`; manifest details print (`Privileged: true`, `Required: [THREADS]`) | "`btracex inspect`: read any extension's manifest" |
| 0:76–0:84 | Run `btracex policy print`; current policy file contents print | "`btracex policy print`: what's actually allowed right now" |
| 0:84–0:90 | Run `btracex policy set --allowExtensions btrace-metrics`; `Policy saved to ...` prints | "`btracex policy set`: flip it back, the supported way" |

## CTA

Full walkthrough (including the privileged-tier explanation and the `-le` output for an
ungranted-privileged denial, which differs from the policy-denied one shown here):
`docs/tutorials/04-extensions-and-permissions.md`.

---

## Production notes (not spoken/shown on camera)

- Every command is verbatim from the tutorial's Steps 3–6: the two `permissions.properties`
  heredocs, both `btrace <PID>/<NEW_PID> LatencyHistogram.java` deploys, `btrace -le <NEW_PID>`,
  and the four `btracex` subcommands (`list`, `inspect`, `policy print`, `policy set`).
- The tutorial restarts the demo app with a **new PID** between the allow and deny demonstrations
  (policy loads once per JVM lifetime) — the shot list preserves that beat (0:32) rather than
  reusing the first PID, so don't compress it away in editing.
- `LatencyHistogram.java`'s full source (the injected `MetricsService`, the two histograms, the
  `@OnTimer` report) is in the tutorial's Step 2 code block — if the tape needs to show the script
  file itself (not just its output), pull that block verbatim, don't paraphrase it.
- Do not show `btracex policy edit` — the tutorial explicitly notes it's an unimplemented
  placeholder ("Interactive editor not yet implemented"); it isn't part of this happy path.
