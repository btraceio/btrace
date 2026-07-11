# V2 — From Oneliner to Script: The Flat DSL

**Priority:** P1 · **Length:** 60–90s · **Format:** pure terminal, no talking head, rendered from a VHS tape.

**Based on:** `docs/tutorials/02-oneliner-to-script.md`

---

## GOAL

Show a viewer that a one-liner isn't a dead end — it's real generated Java, and the same probe
becomes a version-controllable script with zero required imports, thanks to the flat DSL.

## HOOK

*(first 5s — screen only, no narration)*

Terminal showing the tail end of Tutorial 1's oneliner output, then a cut to an editor window
with a bare, empty-looking script file — the contrast is "one-off command" vs. "real file":

```
processOrder
execution time: 296 ms
```

## SHOT LIST

| Timestamp | Terminal action | Overlay text |
|---|---|---|
| 0:00–0:05 | Static: oneliner output from before (HOOK) | "That one-liner... what did it actually compile to?" |
| 0:05–0:20 | Run `JAVA_TOOL_OPTIONS="-Dbtrace.oneliner.dump=true" btrace -n 'OrderService::processOrder @return { print method, time }' <PID>`; scroll through the generated `@BTrace` class source that prints before the trace stream | "It's just Java. Generated on the fly." |
| 0:20–0:25 | `Ctrl+C` to detach | "Now write it by hand — no generator needed" |
| 0:25–0:35 | Cut to editor: show the minimal `OrderTiming.java` (clazz/method/return hook, two `println` calls), no imports visible | "Zero imports. `println`, `@OnMethod`, `@BTrace` just work." |
| 0:35–0:45 | Terminal: run `btrace <PID> OrderTiming.java` — same two-line-per-order output appears | "Same probe. Real file. `btrace <PID> file.java`" |
| 0:45–0:48 | `Ctrl+C` to detach | "Now make it richer" |
| 0:48–0:60 | Cut to editor: show the Step 3 version using `concat()`, `str()`, `timestamp()` on one built line | "Flat DSL: str(), concat(), timestamp() — no import needed" |
| 0:60–0:68 | Terminal: run `btrace <PID> OrderTiming.java` again — one-line-per-order output (`processOrder took 68ms at ...`) | "One line per order now" |
| 0:68–0:70 | `Ctrl+C` to detach | " " |
| 0:70–0:82 | Cut to editor: show the full `@TLS` version (orderStart / ordersOnThisThread fields, two hooks) | "@TLS: state that survives across probes, per thread" |
| 0:82–0:90 | Terminal: run `btrace <PID> OrderTiming.java` once more — interleaved `worker=order-worker-0/1/2` lines with `order #N total=...ms` scroll by | "Per-thread counts. End-to-end timing. Still one script." |

## CTA

Full walkthrough (including why `JAVA_TOOL_OPTIONS` — not a trailing `-D` flag — is required for
the dump, and the `@TLS` handler-type restriction):
`docs/tutorials/02-oneliner-to-script.md`.

---

## Production notes (not spoken/shown on camera)

- Every command above is verbatim from the tutorial: the `JAVA_TOOL_OPTIONS=... btrace -n '...'`
  dump command (Step 1), and the three `btrace <PID> OrderTiming.java` redeploys (Steps 2–4) using
  the three script bodies shown in the tutorial's code blocks.
- This picks up mid-series (same demo app/PID as V1) — if V1 and V2 are cut back-to-back for a
  playlist, the tape can reuse the same recorded JVM session; if cut standalone, the tape needs its
  own fresh `java DemoApp.java` + `jps` beat before shot 0:05 (mirrors Tutorial 2's own "if you
  don't have it running anymore" note). Either is fine — just don't show a `jps` shot that isn't in
  this tutorial's steps if reusing the session.
- Do not show `btracex` or extension/permission material here — that belongs to V3.
