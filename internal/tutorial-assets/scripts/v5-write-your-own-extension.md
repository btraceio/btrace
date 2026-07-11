# V5 — Write Your Own Extension in 30 Minutes (deep-dive)

**Priority:** P2 · **Length:** 8–12 min · **Format:** voiced deep-dive, screen recording (editor + terminal).

**Based on:** `docs/tutorials/06-write-your-own-extension.md`

---

## GOAL

Walk a viewer through building, installing, granting, and using a real BTrace extension end to
end — from an empty Gradle project to a live probe reading data through an injected service —
using the actual `io.btrace.extension` Gradle plugin.

## HOOK

*(first 5s — screen only, brief narration starts immediately after)*

Split screen: on the left, the finished `order-counter` extension's live output
(`orders: succeeded=41 failed=4`); on the right, an empty terminal prompt.

**Narration (0:00–0:15):** "Bundled extensions like `btrace-metrics` are great — until you need
something specific to your own systems. In the next ten minutes we'll build one from scratch:
a real Gradle plugin, a real permission, and a live probe using it against the same demo app
from earlier in this series."

## SHOT LIST

| Timestamp | Terminal/editor action | Overlay text | Narration |
|---|---|---|---|
| 0:00–0:15 | HOOK split screen | "Write your own extension" | (see HOOK above) |
| 0:15–0:45 | Terminal: `mkdir -p order-counter-ext/src/main/java/com/example/orderstats`, `cd order-counter-ext`, then the two `cat > settings.gradle` / `cat > build.gradle` heredocs from Step 1 | "Step 1: scaffold the Gradle project" | "An extension is its own small Gradle project — not something bolted onto the app you're tracing. We apply `io.btrace.extension`, and declare the extension's id, name, and description in a `btraceExtension {}` block." |
| 0:45–1:00 | Editor: highlight the `services = ['com.example.orderstats.OrderCounterService']` line inside `build.gradle` | "Note this line — we'll come back to it" | "One line here matters more than it looks: we're declaring `services` explicitly. Hold that thought — it's the subject of a quick pro tip in a few minutes." |
| 1:00–1:40 | Terminal: the two `cat >` heredocs for `package-info.java` and `OrderCounterService.java` from Step 2 | "Step 2: write the API" | "The API is an interface — `increment`, `count`, `reset` — plus a `package-info.java` declaring the `@ExtensionDescriptor` and the `THREADS` permission. Same package holds both interface and implementation; no separate api/impl subpackages needed." |
| 1:40–2:20 | Terminal: the `cat >` heredoc for `OrderCounterServiceImpl.java` from Step 3 | "Step 3: write the implementation" | "The implementation extends BTrace's `Extension` base class, same as the bundled `MetricsServiceImpl`, and it's just a thread-safe counter — a `ConcurrentHashMap` of `AtomicLong`s. Nothing BTrace-specific here. But that choice of concurrency utilities is exactly what the build is about to notice." |
| 2:20–3:10 | Terminal: `./gradlew packageExtension`; scroll through the task graph output ending in `BUILD SUCCESSFUL` | "Step 4: build it" | "Watch the permissions line: `scanned=[THREADS] merged=[THREADS]`. The plugin doesn't trust our annotation for the manifest — it decompiles the impl looking for JDK APIs that imply a permission. Anything under `java.util.concurrent` maps to `THREADS`, and that's exactly what our counter uses." |
| 3:10–3:40 | **PRO TIP interlude** — editor: re-highlight the `services = [...]` line from 0:45, then cut to the Troubleshooting section of the tutorial on screen | "Pro tip: auto-detection is currently broken" | "Here's that line from Step 1 again. The plugin *can*, in principle, auto-detect service interfaces from the `@ServiceDescriptor` annotation without you listing them in `services`. As of this checkout, don't rely on that: the detection logic still matches the annotation's old package name, `org.openjdk.btrace.core.extensions.ServiceDescriptor`, not the `io.btrace.core.extensions.ServiceDescriptor` this codebase actually uses — so for this repo, auto-detection is dead code. Always declare `services` explicitly, exactly like `btrace-metrics` itself does. It's a one-line fix, but it's the difference between an extension that installs cleanly and one that silently exports nothing." |
| 3:40–4:20 | Terminal: `btracex install build/distributions/order-counter-3.0.0-extension.zip`; full install report prints (`Installed extension 'order-counter' into: ...`, hint block) | "Step 5: install it" | "`btracex install` copies both jars into a per-extension directory and immediately reports back what it found — including the hint to grant its permission, which is exactly what we do next." |
| 4:20–4:45 | Terminal: `mkdir -p ~/.btrace`, `cat > ~/.btrace/permissions.properties <<'EOF' / allowExtensions=order-counter / EOF` | "Step 6: grant the permission" | "Same mechanism as the extensions tutorial earlier in this series — `THREADS` is privileged, so it needs an explicit allow before any probe using it can attach." |
| 4:45–5:15 | Terminal: `java DemoApp.java` in one pane, `jps` in another to get the PID | "Step 7a: start a fresh demo app" | "A fresh process, because policy loads once per JVM lifetime — this has to be a JVM BTrace hasn't attached to yet." |
| 5:15–5:45 | Editor: show `OrderCounterProbe.java` full source (the `@Injected OrderCounterService counter`, the two `@OnMethod` hooks, the `@OnTimer` report) | "Step 7b: the probe" | "This probe injects our brand-new service as a *required* field — no `optional = true` this time — and counts successes on return, failures on the error path, reporting both every five seconds." |
| 5:45–6:15 | Terminal: `btrace <PID> OrderCounterProbe.java`; output `orders: succeeded=41 failed=4` / `orders: succeeded=89 failed=8` appears | "Deploy it" | "And there it is — our own service, our own counter, live inside someone else's running JVM." |
| 6:15–6:40 | Terminal: `btracex list`; `order-counter [PRIV] - ...` line appears | "Step 8: confirm it from the outside" | "`btracex list` never touches the running JVM — it just confirms the extension is installed and flagged privileged, whether or not anything is attached right now." |
| 6:40–7:00 | Terminal: `Ctrl+C` the btrace client, `Ctrl+C` the demo app, then `rm -rf "$BTRACE_HOME/extensions/order-counter" ~/.btrace/permissions.properties` | "Step 9: clean up" | "Detach, stop the app, and — if you want your install back to its original state — remove what we added." |
| 7:00–7:20 | End card with tutorial link | "docs/tutorials/06-write-your-own-extension.md" | "That's the whole loop: API, implementation, build, install, grant, inject, verify. Full source and the two other gotchas this tutorial found — the Shadow plugin and the btrace-core version match — are linked below." |

*(Timestamps assume a ~7-minute cut; pad with slower pacing/B-roll on the Gradle build and dashboard
holds to reach the target 8–12 min runtime without adding any command not already listed above.)*

## CTA

Full walkthrough, including the Shadow-plugin auto-apply failure mode and the
`btrace-core` version-matching gotcha (both in Troubleshooting):
`docs/tutorials/06-write-your-own-extension.md`.

---

## Production notes (not spoken/shown on camera)

- Every command/heredoc/code block shown is copied verbatim from the tutorial's Steps 1–9.
- The pro-tip at 3:10–3:40 is scoped to ~30s as instructed — it explains the real gotcha
  (auto-detection matches the old `org.openjdk.btrace...` package name and is currently dead code
  for this repo) but does not become the video's central thread; the video's spine remains the
  full build-install-grant-inject loop, matching the tutorial's own emphasis.
- Do not show `btracex inspect order-counter` or the `unzip -p ... MANIFEST.MF` verification
  aside from Step 5's callout — neither is a numbered step in the tutorial, they're supplementary
  narration in a "What just happened?" box; keeping the shot list to numbered steps keeps this
  script's commands strictly verbatim-and-in-sequence.
