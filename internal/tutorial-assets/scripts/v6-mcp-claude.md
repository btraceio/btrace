# V6 — Let an AI Assistant Debug Your JVM (MCP with Claude)

**Priority:** P0 · **Length:** 60–90s · **Format:** screen-capture, chat transcript + terminal split, no talking head.

**Based on:** `docs/tutorials/05-mcp-server.md`

---

## GOAL

Show a viewer describing a symptom in plain English to Claude Code and watching it attach
BTrace, deploy and refine a probe, and diagnose a real latency bug — without the viewer typing a
single BTrace command themselves.

## HOOK

*(first 5s — screen only, no narration)*

Split screen: left half is a blank Claude Code chat input; right half is terminal 1 showing the
demo app's own uninformative output:

```
[demo] order service running - stop with Ctrl+C
[demo] processed 61 orders, 7 failed
```

## SHOT LIST

| Timestamp | Screen action (chat left / terminal right) | Overlay text |
|---|---|---|
| 0:00–0:05 | HOOK split screen | "What if you just... asked?" |
| 0:05–0:12 | Chat: user types "Some orders are coming out slow. Can you find out why?" | "No command. Just describe the symptom." |
| 0:12–0:20 | Chat: assistant text "Let me see what's running on this machine." + tool-call card `list_jvms`; terminal right pane (optional) shows the discovered JVM list output | "`list_jvms` — Claude finds the target itself" |
| 0:20–0:22 | Chat: highlight `PID: 54021 \| Main Class: DemoApp` in the tool result | "Found it: PID 54021" |
| 0:22–0:35 | Chat: assistant text "I'll time `processOrder` first..." + tool-call card `deploy_oneliner` with `oneliner: "OrderService::processOrder @return { print method, time }"`; result shows `Probe deployed successfully...` and the three sample lines (`64 ms`, `71 ms`, `318 ms`) | "`deploy_oneliner` — same probe engine as the CLI" |
| 0:35–0:42 | Chat: assistant text about needing to widen the probe + tool-call card `detach_probe` with `pid: "54021"`; result `Detached from probe on PID 54021. Probe continues running.` | "Detach the narrow probe first" |
| 0:42–0:48 | Chat: tool-call card `list_probes`; result shows the one detached probe's UUID | "Confirm what's still out there" |
| 0:48–1:02 | Chat: tool-call card `deploy_oneliner` with `oneliner: "OrderService::* @return if duration>200ms { print method, time }"`; result shows `chargeCard` (337 ms) and `processOrder` (351 ms) together | "Widen the net — same filter idea as the CLI tutorial" |
| 1:02–1:14 | Chat: assistant's final answer text: "Found it: `chargeCard` is the bottleneck... roughly one call in ten takes 250–400 ms... I've left a filtered probe attached... just ask me to remove it when you're done." | "Diagnosis, in plain English" |
| 1:14–1:22 | Chat: user types "Remove the BTrace probe from PID 54021." + tool-call card `exit_probe`; result `Probe stopped and removed from PID 54021.` | "One more sentence to clean up" |
| 1:22–1:30 | End card / outro | "One thing to know before you rely on this:" then "`detach_probe` is one-way — no `reconnect` tool. Use `exit_probe` if you want the assistant to clean up later." |

## CTA

Full walkthrough, including the built-in `profile_method`/`diagnose_slow_endpoint`/
`find_exception_source` diagnostic prompts and the `.mcp.json` setup:
`docs/tutorials/05-mcp-server.md`.

---

## Production notes (not spoken/shown on camera)

- Every chat line and tool call above is transcribed verbatim from the tutorial's Step 5
  ("Ask, don't type") transcript and Step 7 (cleanup) — including the exact tool names
  (`list_jvms`, `deploy_oneliner`, `detach_probe`, `list_probes`, `exit_probe`), the exact
  oneliner strings, and the exact sample output lines. Do not paraphrase the assistant's dialogue.
- This shot list deliberately skips Step 1 (building the MCP server with `./gradlew
  :btrace-mcp-server:build`), Step 3 (`.mcp.json` setup), and Step 4 (`claude mcp list`) — those
  are one-time setup, not part of the "watch a diagnosis happen" happy path this short is selling;
  they belong in the linked tutorial, not the video. Do not add a "here's how to set it up" shot
  under the 60–90s budget — there isn't room to do it justice without cutting the diagnosis itself.
- The required caveat — per the parent brief — is placed in the outro/CTA beat (1:22–1:30), phrased
  exactly as instructed: "one thing to know before you rely on this." This matches the tutorial's
  own "What just happened?" callout after Step 5: `list_probes` only shows probes a client has
  *detached* from (not everything currently streaming), and the server exposes no `reconnect` tool,
  so `detach_probe` is effectively one-way from the assistant's own perspective — reconnecting
  requires the plain `btrace -r` CLI, outside the assistant's reach. `exit_probe` (shown at
  1:14–1:22) is the tool that actually cleans up completely, which is why the transcript's own
  final action uses it rather than `detach_probe`.
- Do not show `profile_method` or the other two diagnostic prompts (Step 6) — this is the
  "improvised" transcript from Step 5 only; the prompt-template material is a distinct beat in the
  tutorial and doesn't fit this format's runtime without cutting the caveat above.
