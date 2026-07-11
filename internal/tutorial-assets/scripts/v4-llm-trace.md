# V4 — Watch Your LLM App Think (llm-trace)

**Priority:** P1 · **Length:** 60–90s · **Format:** pure terminal, no talking head, rendered from a VHS tape.

**Based on:** `docs/tutorials/07-llm-observability.md`

---

## GOAL

Show a viewer that BTrace can put a hard latency budget on a live LLM call and surface
token/cost/violation data on one dashboard — with no SDK, log shipper, or code change to the app.

## HOOK

*(first 5s — screen only, no narration)*

Terminal 1 showing the LLM demo app's plain, uninformative status line:

```
[demo] llm ticket summarizer running - stop with Ctrl+C
[demo] answered 10 tickets
```

## SHOT LIST

| Timestamp | Terminal action | Overlay text |
|---|---|---|
| 0:00–0:05 | Static: LlmDemoApp output (HOOK) | "10 tickets answered. Which ones ran away?" |
| 0:05–0:16 | Run `btracex inspect btrace-llm-trace` and `btracex inspect btrace-contracts` back to back; both manifests print (`Privileged: true`, `Required: [THREADS]`) | "Two extensions: token/cost tracing + a latency contract" |
| 0:16–0:26 | Run `mkdir -p ~/.btrace` then `cat > ~/.btrace/permissions.properties <<'EOF' / allowExtensions=btrace-llm-trace,btrace-contracts / EOF` | "Grant both permissions, one line" |
| 0:26–0:36 | Run `btrace <PID> LlmObservability.java` | "Deploy: token counting + a 500ms budget, in one script" |
| 0:36–0:60 | Hold on the 10-second dashboard output: `=== LLM + Contracts Dashboard ===`, model summary (`Calls`, `Tokens`, `Latency`, `Est. cost`), then `=== Contract Summary ===` with `VIOLATIONS: 3 (7%)` and the `Last: Latency 3336ms exceeded budget 500ms` line | "Tokens. Cost. And which calls blew the budget." |
| 0:60–0:70 | Slow zoom/highlight on the `VIOLATIONS: 3 (7%)` and `max=3801ms` figures | "Same 8% runaway call — now with a number on it" |
| 0:70–0:78 | `Ctrl+C` to detach, `Ctrl+C` the demo app | "Detach. No SDK, no server, ever left running." |
| 0:78–0:90 | Static end card | "docs/tutorials/07-llm-observability.md" |

## CTA

Full walkthrough (including why both extensions land in the privileged `THREADS` tier via
bytecode scanning rather than a declared annotation, and the full injected-script source):
`docs/tutorials/07-llm-observability.md`.

---

## Production notes (not spoken/shown on camera)

- Every command is verbatim from the tutorial's Steps 3–5: both `btracex inspect` calls, the
  `permissions.properties` heredoc granting both extensions, and the single
  `btrace <PID> LlmObservability.java` deploy.
- The dashboard numbers shown in the shot list (`Calls: 40`, `VIOLATIONS: 3 (7%)`, etc.) are
  copied directly from the tutorial's "You should see" block in Step 6 — the tutorial itself
  flags these as illustrative ("numbers will vary"), so the actual VHS recording will show
  whatever the live run produces; don't hardcode these exact digits into the tape's overlay text,
  only into this script's planning table.
- This uses its own demo app (`LlmDemoApp.java`), not the shared `DemoApp.java` from V1–V3 — the
  HOOK and PID-finding beat are specific to that app; do not reuse V1's `DemoApp` terminal footage.
- Do not show `btrace -le` or `btracex list`/`policy print`/`policy set` here — those are V3's
  material; this tutorial only uses `btracex inspect` before granting, per its own Step 3.
