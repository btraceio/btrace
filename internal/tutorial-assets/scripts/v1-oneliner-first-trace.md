# V1 — Your First Trace in 2 Minutes (oneliner)

**Priority:** P0 · **Length:** 60–90s · **Format:** pure terminal, no talking head, rendered from a VHS tape.

**Based on:** `docs/tutorials/01-first-trace-in-2-minutes.md`

---

## GOAL

Show a viewer that BTrace can attach to a live, misbehaving JVM and explain a real latency bug
and a swallowed exception using nothing but one-line commands — no restart, no code change.

## HOOK

*(first 5s — screen only, no narration)*

Terminal 1 already scrolled with the demo app's own output, underlining that the app itself
gives you no clue why it's failing:

```
[demo] order service running - stop with Ctrl+C
[demo] processed 64 orders, 8 failed
[demo] processed 134 orders, 14 failed
```

Overlay freezes on `14 failed` for a beat before the shot list begins.

## SHOT LIST

| Timestamp | Terminal action | Overlay text |
|---|---|---|
| 0:00–0:05 | Static on terminal 1 showing the demo app output above (HOOK) | "8 failed. 14 failed. Why?" |
| 0:05–0:12 | Terminal 2: run `jps`, highlight the `DemoApp` PID line | "Step 1: find the PID" |
| 0:12–0:30 | Terminal 2: type and run `btrace -n 'OrderService::processOrder @return { print method, time }' <PID>`; output streams live | "One line. No restart. No code change." |
| 0:30–0:38 | Hold on the streaming output, circle/highlight one `execution time: 342 ms` line among faster ones | "Most calls: <100ms. Some: 300ms+." |
| 0:38–0:42 | `Ctrl+C` to detach | "Detach cleanly, widen the net" |
| 0:42–0:60 | Run `btrace -n 'OrderService::* @return if duration>200ms { print method, time }' <PID>`; output shows `chargeCard` and `processOrder` together | "Filter to only the slow calls" |
| 0:60–0:70 | Hold on paired `chargeCard` / `processOrder` lines | "Found it: chargeCard is the bottleneck" |
| 0:70–0:78 | `Ctrl+C` to detach, then run `btrace -n 'OrderService::validateOrder @error { print method, stack }' <PID>`; a stack trace line appears | "Catch the failures too — live stack trace" |
| 0:78–0:85 | `Ctrl+C` in the BTrace terminal, then `Ctrl+C` in terminal 1 (demo app) | "Detach. Clean. Every time." |
| 0:85–0:90 | Static end card | "docs/tutorials/01-first-trace-in-2-minutes.md" |

## CTA

Full walkthrough (including the duration/error-path callouts and troubleshooting):
`docs/tutorials/01-first-trace-in-2-minutes.md`.

---

## Production notes (not spoken/shown on camera)

- **jbang opener — DO NOT SHOOT YET.** The tutorials plan's "known blockers" (§4, §4 footnote)
  states the jbang catalog is broken for 3.0: the external `btraceio/jbang-catalog` still pins
  `io.btrace:btrace-client:2.3.0-SNAPSHOT` and an old main class, so a jbang-based 30-second
  opener would fail on screen. T1's own tutorial does not include a jbang command — there is
  nothing verbatim to shoot. Coverage matrix (§4) explicitly lists "jbang 30-second start"
  as "folded into T1 intro + GettingStarted" for the hands-on doc, and separately marks a
  "V1 opener" for video — but until the catalog is fixed, do not script or record a jbang shot
  under any circumstances (it violates the verbatim-command rule since the working command
  doesn't exist). Revisit this file once the blocker is cleared, and prepend a jbang HOOK/shot
  at that point rather than inventing one now.
- All three `btrace -n '...'` commands above are copied verbatim from Steps 3–5 of the tutorial;
  the PID placeholder `<PID>` is filled at record time with whatever `jps` actually returns.
- Do not show the `-Dbtrace.oneliner.dump=true` aside from the tutorial's "What just happened"
  box — that's V2's material (Tutorial 2 Step 1), not V1's.
