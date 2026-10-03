# Fix design — agent exit-path wedge (#932)

Issue: https://github.com/btraceio/btrace/issues/932
Evidence base: the captured wedge dump + job-level CI sweep (see
`internal/plans/2026-09-26-issue-932-target-wedge-root-cause.md`)
Status: **design complete, implementation gated on the live owner capture** — per the issue's
rule, the monitor-owner frame from the hardened hunt prunes/keeps the chains below. The fix set is
deliberately layered so that each fix is justified by captured stacks alone, independent of which
owner resolution the capture produces.

## What the wedge is (dump-backed)

A target JVM that received `done` and whose main thread has exited never finishes exiting. In the
captured frame (`repro-logs/stall-dumps-iter-2/`):

- `DestroyJavaVM` parked in `ApplicationShutdownHooks.runHooks`, joining the agent's
  `"BTrace Server Shutdown"` hook.
- That hook BLOCKED at `Main.java:1479` entering `private static synchronized void shutdownServer()`.
- The per-probe hook (`Client.java:365`) BLOCKED at the entry of `completeTerminalCleanup`, whose
  first slow callee is `private synchronized void exitImpl` (`BTraceRuntimeImplBase:1320`).
- The probe timer (`Timer-1`) BLOCKED at the handler-dispatch wrapper (`BTraceRuntimeImplBase:1305`).
- The ClassCache cleanup timer BLOCKED at `ClassCache:91`.
- The command-queue drain (`Thread-2`) BLOCKED inside `Filter.matchClass` (`BTraceTransformer:348`),
  reached from `RemoteClient.onCommand` → `dispatchCommand` → V2 socket write →
  `NioSocketImpl.implWrite` → **JDK-internal hidden-class definition invoking the armed transform
  pipeline mid-write**.
- The `ControlServer.accept` loop (`Thread-0`) still parked in `accept()` — the server was never
  closed because its hook never ran.

Timeline compression (thread elapsed values): the whole lifecycle — agent init, `ready:`, probe
round trip, `done`, exit — completes in under ~0.7 s. The wedge is a **startup/exit overlap race**:
the exit path contends with agent initialization and a still-live probe session.

Caveat: the frozen capture attributes no monitor owners (no `waiting to lock` lines) — consistent
with object monitors held by not-yet-returned code, or with JVMTI class-initialization locks (which
thread dumps render as `BLOCKED` without a target). The live two-frame capture from the running
hunt decides this; the fix set below covers both readings.

## Exit-path lock inventory (code, current)

| # | Lock | Held by | Needed by (captured) |
|---|---|---|---|
| L1 | `Main.class` monitor | `private static synchronized main()` (`Main.java:262`) — the entire agent init, incl. `ControlServer.open`, transformer installation, `retransformClasses(MethodHandleNatives)`, `startScripts`, `initExtensions` | `"BTrace Server Shutdown"` hook → `static synchronized shutdownServer()` (`Main.java:1485`) |
| L2 | runtime instance monitor | `private synchronized exitImpl` (`BTraceRuntimeImplBase:1320`) — held across `timer.cancel`, notification-listener removal, `threadPool.shutdownNow()`, **probe `@OnExit` handler invocation** (`eh.getMethod(clazz).invoke(...)`), `cleanupExtensions()` | per-probe hook `handleExit` → `requestTerminalShutdown` → `completeTerminalCleanup` (`BTraceRuntimeImplBase:1012-1013`) |
| L3 | `Filter.nameMap` / `nameRegexMap` (plain `HashMap`s) | `addToMap`/`removeFromMap` via `register`/`unregister` (`BTraceTransformer:84/96 → 307-327`) | `Filter.matchClass` (`:332-350`) — the fast path of `transform()` for **every class definition in the JVM**, including JDK-internal hidden classes |
| L4 | agent `Client` instance monitor | `synchronized onExit` (`Client.java:291`) held across `cleanupTransformers()` (→ `probe.unregister()` → L3) and `retransformLoaded()` (→ `inst.retransformClasses`, a VM op) | other exit paths / terminal handshake |

Not implicated as load-bearing: `ClassCache.cacheMap` is a `ConcurrentMap` (`ClassCache:75`) —
its timer's block is a symptom of the frozen pipeline, not a link; `enter()`/`leave()` are
TLS-based and lock-free (`BTraceRuntimeAccessImpl:152`).

## Fix set

### Fix 1 — decouple the server shutdown hook from agent init (L1)

**Change**: `Main.main` and `Main.shutdownServer` stop sharing the `Main.class` monitor.

- Add `private static final Object INIT_LOCK = new Object()`; `main()` body becomes
  `synchronized (INIT_LOCK) { ... }` — the `Main.inst != null` re-entrancy guard is preserved
  verbatim (it only needs mutual exclusion with itself, not with `shutdownServer`).
- `shutdownServer()` drops `synchronized` entirely. It only touches `serverRunning` (already
  `volatile`, `Main.java:229`), `controlServer` (must gain `volatile`), and the endpoint locals.
  The method is otherwise already written snapshot-style.

**Rationale**: the exit hook must make progress *regardless* of what agent init is doing. Once
`shutdownServer()` runs, `endpoint.close()` (`ControlServer.java:103-106`) unblocks `Thread-0`'s
`accept()` and the server thread exits. Nothing about init/init-lock semantics requires sharing a
monitor with a shutdown hook — this is the single change with the widest coverage.

**Risk**: low. `main()`'s guard semantics unchanged; `shutdownServer` was already snapshot-style.
The only behavioural difference: shutdown can now interleave with a *still-running* init — which is
exactly the failing window, and is the correct outcome (stop the server, unblock the accept loop).

### Fix 2 — lock-free transform fast path (L3)

**Change**: `Filter.nameMap` and `nameRegexMap` become `ConcurrentHashMap`s.

- `matchClass` reads become lock-free: `nameMap.containsKey(className)` directly;
  `for (Pattern p : nameRegexMap.keySet())` iterates weakly-consistently.
- `addToMap`'s `map.merge(name, 1, Integer::sum)` is already CHM-atomic; `removeFromMap` becomes
  a CHM `compute` (get + conditional remove under one atomic step).

**Rationale**: `matchClass` executes for every class definition in the JVM once the transformer is
installed — including JDK-internal hidden classes defined lazily inside `NioSocketImpl` writes.
It must never block on probe (un)registration state; the captured `Thread-2` block site is exactly
this monitor pair. Weakly-consistent iteration is acceptable for a *heuristic* pre-filter: the
final instrument decision is session-based, not `matchClass`-based (`BTraceTransformer:171` uses
`Result.FALSE` only as an early skip; the `TRUE` path is re-checked against live sessions later
in `transform()`).

**Risk**: moderate, needs careful tests. Semantics of a `matchClass` TRUE produced from a pattern
being concurrently unregistered: `transform()` re-validates against sessions before instrumenting
(verify this holds for every `matchClass → instrument` path while implementing; add a dedicated
race test if not). Perf: strictly better (lock-free reads; the current synchronized-HashMap
iteration is slower than CHM anyway).

### Fix 3 — exit handlers out of the runtime monitor (L2)

**Change**: `exitImpl` keeps `synchronized` for the teardown state, but the **probe `@OnExit`
handler invocations move outside the monitor**:

- Snapshot `exitHandlers` under the lock (already nulled under it — keep the swap), then invoke
  the handlers *after* releasing it, still before `cleanupExtensions()`.
- Alternatively/additionally bound each handler invocation with the same bounded-ack pattern
  `requestTerminalShutdown` already uses (`TERMINAL_MARKER_ACK_TIMEOUT_MILLIS`), so a handler
  blocked on I/O cannot hold teardown indefinitely.

**Rationale**: `@OnExit` handlers are user code; in the captured wedge they run while holding the
runtime monitor and routinely write to the client transport — the exact path that froze in the
dump. The per-probe hook (`Thread-3`) blocked *entering* this monitor. Whether the actual owner at
wedge time was a stuck handler or another exitImpl caller, keeping user-code invocation out of the
monitor removes the block site both ways.

**Risk**: moderate. Ordering must hold: handlers must complete before runtime teardown marks the
session dead (the terminal-marker machinery from #908 already sequences this — align with it, do
not duplicate). A handler racing the timer dispatch after `timer.cancel()` is unchanged
(cancel-then-invoke remains atomic w.r.t. the Timer object's own lock).

### Fix 4 — accept-loop unblock hardening (belt and braces, with Fix 1)

**Change**: none required if Fix 1 lands — `ControlServer.close()` already closes the server
socket (`:103-106`), which unblocks `accept()`. If the live capture shows the accept loop parked
with the server socket *never* closed because the hook never ran, Fix 1 alone resolves it; no
additional change. Keep as a checkpoint against the capture, not a code change.

## What we deliberately do NOT do

- No attempt to prevent JDK-internal hidden-class definitions (impossible; the fix is our side
  being non-blocking, i.e. Fix 2).
- No removal of the per-probe shutdown hook (needed for graceful probe teardown on app exit).
- No speculative fix of `Client.onExit`'s monitor scope (L4) — no captured stack blocks on it;
  revisit only if the live capture shows one.

## Implementation order and gate

1. **Gate**: wait for the live two-frame capture from the hardened hunt
   (`repro-logs/manual/` or the two-frame sentinel dump). Reconcile owners against L1–L4:
   - Main.class held by running init → Fix 1 confirmed as primary.
   - runtime monitor held by a stuck handler → Fix 3 confirmed as primary.
   - filter monitors held across slow work → Fix 2 confirmed as primary.
   - Owners are class-init locks → Fix 2 removes the definition-site funnel; Fix 1 unblocks the
     hook; Fix 3 removes the handler-in-monitor window.
2. Implement fixes **one at a time**, each verified by the container loop
   (baseline wedge rate ≈ 0.8%/iteration; post-fix evidence of absence needs ~300+ clean
   iterations per fix for reasonable confidence).
3. Each product change ships with e2e coverage per AGENTS.md (cross-process behaviour):
   the loop + full `:integration-tests:test` + `spotlessCheck`; `clean :btrace-dist:btraceJar`
   after touching agent/runtime classes (no layout change expected, but the masked-JAR rule is
  cheap to honour).

## Regression guards that remain regardless

- `StallWatchdog` (6 min, two frames) + per-test timeout (8 min) — #935.
- Exit-wedge sentinel (this branch, committed) — every wedge leaves a dump even when masked.
- The container loop scripts under `/tmp/btrace-repro/` (loop/watcher/supervisor) — the
  reproduction harness this fix will be validated against.

## Open items

- Owner resolution of the five blocked monitors (live capture, pending — the hunt is running).
- The 17.0.19-specific correlation: hypothesis (hidden-class definition moved into the
  `NioSocketImpl.implWrite` path in 17.0.19) is diffable against JDK sources 17.0.18 ↔ 17.0.19 —
  worth one check to close the "why this tier" question in the issue, but not a fix dependency.
- Whether `exitHandlers` snapshot-then-invoke needs a bounded wait for handler completion
  (align with `requestTerminalShutdown`'s ack machinery during implementation).