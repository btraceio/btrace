# Issue #888: runtime pre-release hardening

Status: design contract for [#888](https://github.com/btraceio/btrace/issues/888)

## Context and objective

Issue #888 groups seven MAJOR defects found in `btrace-runtime` before the 3.0 release. They are
independent defects on probe detach, handler dispatch, class definition, and profiling paths, but
all can retain application state, hide a failed trace, or consume an application thread
indefinitely. This change makes those paths bounded, observable, and safe to use while a probe is
being detached.

The implementation must retain BTrace's Java 8 main-source target and the existing Java 9
multi-release source layout.

## Decisions

The following choices are idiomatic for the current Java and repository conventions, so no product
decision is required.

1. Detach will make the speculative queue manager inert rather than nulling objects that probes can
   still read. A `volatile` closed/disabled state plus stable queue and thread-local references is
   the smallest safe publication contract for concurrent `send`, `speculate`, `commit`, and
   `discard` calls. Calls racing with detach may drop speculative output; they must not throw or
   enqueue output after teardown.
2. MBean registration will return a probe-owned registration handle, rather than treating a
   canonical `ObjectName` as proof of ownership. The handle contains the canonical name and an
   unguessable registration token. `BTraceMBean` exposes that token in its top-level `MBeanInfo`
   descriptor under a reserved BTrace field, so it is queryable through
   `MBeanServer.getMBeanInfo(ObjectName)` without retaining a probe-class reference. Immediately
   before unregistering, close reads that descriptor and calls `unregisterMBean` only when its token
   exactly matches the handle. A name collision is never replaced: registration records no owned
   handle, reports the collision, and later cleanup leaves the pre-existing MBean alone.
   `handleExit` closes the owned handle from a `finally`-safe cleanup path, so extension shutdown,
   exit delivery, command-thread joining, or runtime cleanup failures cannot skip it. Repeated
   close/unregister remains harmless. JMX provides no atomic compare-and-unregister, so this protects
   pre-existing collisions and a replacement observed by the immediate check, but does not claim to
   prevent a truly concurrent third-party unregister/register between that check and unregister.
   `DisconnectCommand` is deliberately non-terminal: it only ends/half-closes the remote client
   transport and must leave the probe runtime and its owned MBean alive. Terminal cleanup belongs
   to the real `ExitCommand` path. `handleExit(int)` and `shutdownCmdLine()` will delegate to one
   idempotent shutdown state machine. Its first caller atomically captures the requested exit code;
   every later request is a no-op and cannot replace that code. It closes the MBean, creates one stable
   `MessageCommand` marker, and enqueues only that marker while the runtime command queue remains
   live. `ConsumerWrapper` acknowledges it only after the command listener has dispatched the
   marker; that acknowledgment alone enqueues the one final runtime `ExitCommand` carrying the
   captured first code. Therefore no exit
   command is available to consume before the marker has been dispatched. `exitting` is set only
   when that queued ExitCommand is consumed, and speculative/normal queues are cleared only in the
   command thread's post-exit `finally` path. An off-command-thread caller may wait only a bounded
   marker-ack interval; on timeout it returns with the state machine still responsible for queuing
   Exit after the eventual acknowledgment, rather than dropping the marker or forcing early exit.
   A command-thread caller never waits for or joins itself. The runtime is the sole producer of
   that terminal ExitCommand: agent cleanup code must not create an additional exit echo or
   re-enter runtime shutdown.
3. A failing `@OnError` or `@OnEvent` handler will be logged at error level with its throwable and
   will not escape into the target application. `RTWrapper.escape` will always restore the precise
   prior runtime state, including `null`; handler failure therefore cannot leave the calling thread
   permanently untraced or reentrant.
4. Profiler coordination must never busy-wait indefinitely. The recorder will release only a state
   it acquired. Delayed records will use a FIFO concurrent queue (`ConcurrentLinkedQueue` or an
   equivalent non-blocking queue), not the current `LinkedList` protected by a state-changing spin.
   An application-side entry/exit that cannot immediately acquire the idle writer state appends one
   immutable delayed record and returns; it never waits for state 2 (snapshot) or state 3 (drain).
   The next successful owner first atomically claims the drain state, drains the FIFO queue, then
   performs its own record/snapshot transition. Its state acquisition is the linearization point
   for direct work; queue insertion is the linearization point for deferred work. An owner must
   recheck/drain pending work before processing a later direct record, so an already-enqueued
   record cannot be overtaken. A snapshot may exclude records enqueued after it acquired state 2;
   those records must appear in the next drain/snapshot. No application thread may wait for a
   whole snapshot merely to queue delayed work.
5. Both pre-11 and 11+ `defineClass` paths will report a failed definition consistently by throwing
   an unchecked exception that preserves the cause. Returning `null` is not a valid result of a
   failed probe definition. Each tier has an explicit boundary around its complete definition and
   initialization attempt: unwrap `InvocationTargetException`, wrap every recoverable
   `Throwable` (including linkage/verification failures) in `IllegalStateException` with the
   operation and runtime-tier context, and immediately rethrow `VirtualMachineError` and
   `ThreadDeath`. This is the failure boundary; no tier may silently catch a failure outside it.
6. `MethodInvocationProfiler` will remove cleared weak references during both `snapshot` and
   `reset` traversals. Weak references preserve the intended ownership model; pruning their empty
   wrappers prevents the profiler's collection itself from growing with dead application threads.

### Assumptions for unspecified issue detail

- A detach may race already-instrumented application threads. Dropping a speculative command after
  detach is preferable to synchronizing or retaining a probe-owned queue indefinitely.
- A trace handler is extension/probe code, not trusted runtime code. Diagnostics go through the
  runtime logger; they are not recursively sent through BTrace command queues.
- An MBean is created only for a probe with `@Property` fields. Teardown must not unregister an
  unrelated bean sharing a name; the canonical name alone is insufficient. It must use the
  registration handle/token issued only after this runtime successfully registered its own bean and
  re-query the BTrace `MBeanInfo` descriptor immediately before unregistering. That sequential
  check cannot make JMX's non-atomic check/unregister sequence safe against a simultaneous hostile
  unregister/register operation.
- The issue does not require the 8 MINOR and 3 INFO findings mentioned in its review report, API
  redesign, or a changed public profiler data model.

## Scope

### F1 — speculative queue detach race

`BTraceRuntimeImplBase.SpeculativeQueueManager` currently clears then nulls its
`ConcurrentHashMap` and `ThreadLocal` from the command thread while probe application threads call
`send`. Refactor its teardown and each public operation so a post-detach/racing operation observes
a stable disabled state and returns without an NPE. Preserve normal speculative buffering,
commit, and discard behavior before detach. The command-thread `finally` path remains responsible
for clearing buffered commands.

### F2 — per-probe MBean lifecycle

`BTraceRuntimeImplBase.init` calls `BTraceMBean.registerMBean(clazz)`, while probe exit does not
unregister it. Replace the fire-and-forget registration with an owned registration handle derived
from the canonical probe name and a per-registration ownership token. `BTraceMBean` must publish
the token in a reserved top-level `MBeanInfo` descriptor field, and close must immediately call
`getMBeanInfo(canonicalName)`, compare that field with the handle token, then call
`unregisterMBean(canonicalName)` only on a match. Missing/foreign/invalid descriptor data is a
non-owned no-op with a diagnostic. If another MBean already occupies the name, registration must
neither unregister nor replace it and must return no owned handle. Invoke the handle's idempotent
close during normal `handleExit` cleanup from a `finally` path that runs even when
`cleanupExtensions`, `exitImpl`, command-thread join, or `cleanupRuntime` fails. This protects
against pre-existing collisions and sequentially replaced entries, but cannot atomically protect
against a third party that unregisters and registers between the descriptor check and the JMX
unregister call. It must not keep the probe class or defining loader reachable through the platform
MBean server. `DisconnectCommand` is not a probe-unload trigger and must not call this close path.
The terminal `ExitCommand` path must instead call an idempotent shared shutdown helper through
`shutdownCmdLine()`. `handleExit(int)` must use the same helper for in-runtime/shutdown-hook exits.
The helper atomically captures the first requested exit code and uses that exact code for the one
runtime-generated ExitCommand after marker acknowledgment; `shutdownCmdLine()` without an explicit
code retains its existing zero/default code, while the agent's inbound Exit bridge supplies the
received code through the shared code-carrying helper. Subsequent `handleExit`, shutdown-hook, or
agent requests must neither re-run cleanup nor replace the stored code.
The helper first completes MBean cleanup and enqueues a stable `MessageCommand` marker containing
the terminal-cleanup outcome (including whether the owned MBean was closed or was absent/foreign)
without setting `exitting` or clearing either queue. `ConsumerWrapper` acknowledges successful
listener dispatch of that exact marker and only then enqueues the runtime ExitCommand; consuming
that ExitCommand sets `exitting` and lets the command thread perform its ordinary queue-clear
finally path. An off-thread caller may await the acknowledgment for a finite configured timeout,
then return without forcing an exit if it expires; `cmdThread` continues the handshake. A
command-thread caller neither waits for its own acknowledgment nor joins itself.

`RemoteClient` must change its inbound Exit handling accordingly. On an ExitCommand read from the
remote client it must *not* echo that command or call final agent `onExit` first. It passes that
exit code to the runtime's code-carrying terminal-shutdown bridge (implemented by the common
`handleExit(int)`/shutdown helper path) while the output protocol stays open. The inbound reader's
generic `finally` must recognize the terminal handshake and must not close the socket/protocol on
reader return. Instead `RemoteClient` creates one `TerminalHandshake` with a completion latch.
The normal runtime dispatch writes and flushes the marker; its acknowledged, runtime-generated
ExitCommand is the only command that `RemoteClient` writes and flushes as the terminal exit. That
successful write counts down the latch and calls one idempotent `completeTerminalExit` operation,
which alone performs final agent `onExit` cleanup and I/O close. The reader may await the latch for
a bounded diagnostic interval, but a timeout leaves output retained and finalization owned by the
eventual Exit dispatch; it must never fall back to generic-finally close. The same ordering is
required for a runtime-originated exit: marker dispatch → runtime ExitCommand (with the first
requested code) → `RemoteClient.completeTerminalExit`. This gives the client a deterministic
diagnostic-before-termination contract on both paths. `Client.onExit` is therefore finalization
only: it must not send an ExitCommand itself and must not call the runtime shutdown helper again;
the runtime-generated Exit is the sole terminal command and its dispatch owns the transition into
agent cleanup.

### F3 — handler failure diagnostics and runtime restoration

`BTraceRuntimeAccessImpl.RTWrapper.escape` currently swallows every `Exception`, and only restores
the runtime when the old runtime is non-null. Make `escape` restore its saved value unconditionally
and report a handler failure in a way that keeps BTrace's fault-isolation rule: the application
thread proceeds and the failed handler is diagnosable. Ensure both event dispatch in
`BTraceRuntimeImplBase.handleEvent` and error-handler dispatch in `handleException` use that
behavior. Do not change normal `@OnEvent`/`@OnError` callback ordering or turn a bad handler into a
target-application exception.

### F4 — bounded snapshot state release

`MethodInvocationRecorder.getRecords` currently unconditionally loops until it can change
`writerStatus` from 2 to 0 in `finally`. If setup fails before 2 was acquired, this loop never
terminates. Track acquisition explicitly and release only the acquired snapshot state. Preserve
the atomic snapshot/reset semantics and live-stack preservation already covered by existing tests.

### F5 — uniform failed-class-definition contract

`BTraceRuntimeImpl_8#defineClass` and the Java 9 implementation must not disagree about failure:
the former can return `null` when `Unsafe` is unavailable and the latter discards several reflective
definition failures. Establish the explicit throwable boundary described above on Java 8, 9--10,
and 11+, including the 11--14 anchor-class path and the 15+ reflective hidden-class path. Unwrap
reflection wrappers once, preserve the root cause, and rethrow VM-fatal failures unchanged.
Successful definitions remain isolated as today. The agent/client callers that consume the result
must be checked so they neither continue with a `null` class nor lose the original diagnostic.

### F6 — profiler weak-reference pruning

Prune each cleared `WeakReference<MethodInvocationRecorder>` from
`MethodInvocationProfiler.recorders` while traversing it for both `snapshot(boolean)` and `reset()`.
Do not retain strong references beyond one operation, and do not change aggregation results for live
recorders. The data structure must remain safe with recorder creation on application threads.

### F7 — delayed-record snapshot contention

`MethodInvocationRecorder.processDelayedRecords` currently spins until it changes state 0 to 3.
Because `recordEntry`/`recordExit` call it before attempting their own fast path, application
threads block for the duration of a snapshot (state 2). Replace the state-protected `LinkedList`
with the decision-4 concurrent FIFO queue and make draining opportunistic: only an immediate
0→3 claim drains it; a failed claim causes the record operation to enqueue and return. A successful
record/snapshot owner must drain already-pending FIFO work before accepting later direct work,
which defines the per-recorder ordering/linearization rule. Snapshot is allowed to exclude work
enqueued after it acquired state 2, but the following drain/snapshot must retain that work and
preserve entry/exit stack consistency.

## Affected components

- `btrace-runtime/src/main/java/io/btrace/runtime/BTraceRuntimeImplBase.java` — speculative queue
  lifecycle, probe `handleExit` cleanup, and event/error handler dispatch call sites.
- `btrace-runtime/src/main/java/io/btrace/runtime/BTraceRuntimeAccessImpl.java` — runtime wrapper
  restoration and handler diagnostics.
- `btrace-runtime/src/main/java/io/btrace/runtime/BTraceMBean.java` — symmetric name derivation,
  server-queryable `MBeanInfo` descriptor ownership markers, collision-safe registration handles,
  and idempotent conditional unregister.
- `btrace-runtime/src/main/java/io/btrace/runtime/BTraceRuntimeImplBase.java` — one idempotent
  terminal-shutdown state machine shared by `handleExit` and `shutdownCmdLine`, with marker
  dispatch acknowledgment, first-exit-code capture, command-thread-safe bounded waiting, and
  marker-before-Exit queueing.
- `btrace-agent/src/main/java/io/btrace/agent/Client.java` and `RemoteClient.java` — preserve the
  non-terminal `DisconnectCommand` behavior and reorder inbound Exit handling as runtime terminal
  shutdown, acknowledged marker delivery, sole runtime ExitCommand delivery, and latch-gated final
  agent cleanup/I/O close without reader-finally premature close.
- `btrace-runtime/src/main/java/io/btrace/runtime/BTraceRuntimeImpl_8.java`,
  `btrace-runtime/src/main/java9/io/btrace/runtime/BTraceRuntimeImpl_9.java`, and
  `btrace-runtime/src/main/java11/io/btrace/runtime/BTraceRuntimeImpl_11.java` — consistent class
  definition failure reporting (and any common base/caller needed to preserve the contract) across
  all runtime-selected implementation tiers.
- `btrace-runtime/src/main/java/io/btrace/runtime/profiling/MethodInvocationRecorder.java` and
  `MethodInvocationProfiler.java` — recorder ownership, state transitions, and dead-recorder
  removal.
- Focused runtime unit tests under `btrace-runtime/src/test/java/io/btrace/runtime/` and
  `.../profiling/`; add integration coverage under `integration-tests` if a real attach/detach
  regression cannot be exercised with the runtime fixture alone.

## Compatibility, security, and lifecycle effects

- Public trace scripts keep their annotations, profiler API, and normal result format. A handler
  that already threw remains non-fatal to the target, but its failure becomes observable in runtime
  logs; this is intentional diagnostic behavior, not a wire-protocol change.
- Failed probe definition changes from a possible delayed null dereference/silent `null` to a
  deterministic unchecked failure for every recoverable definition/initialization throwable. VM
  fatal errors and `ThreadDeath` retain their normal propagation. This is a failure-path contract
  correction. Caller-facing messages must retain the original root cause so attach diagnostics
  remain actionable.
- Probe detach now releases only its owned platform-MBean registration, allowing the probe class
  and its isolated loader to become collectible after existing agent registries are released. The
  immediate server-side descriptor/token check leaves a same-name pre-existing or sequentially
  replaced MBean untouched. This is not an atomic compare-and-unregister guarantee: JMX cannot
  prevent a concurrent third-party replacement after the check and before the unregister call.
- A transport `DisconnectCommand` remains backward-compatible and non-terminal: it must not be
  advertised, tested, or relied on as a probe-unload/MBean-removal operation. Terminal resource
  release happens once through the shared `shutdownCmdLine`/`handleExit` helper on a real
  `ExitCommand`. The added shutdown `MessageCommand` is a diagnostic marker preceding the terminal
  ExitCommand, not a changed probe-result or wire-protocol format. Its listener-dispatch
  acknowledgment, rather than producer enqueue order, is the ordering boundary: queue shutdown
  cannot begin before the marker is delivered. A bounded off-thread wait may time out without
  changing that order; the terminal handshake completes asynchronously when dispatch resumes. The
  first terminal request supplies the sole exit code; repeated requests cannot emit another Exit or
  alter the stored code. An inbound reader retains its output protocol until the terminal
  `MessageCommand` and the sole runtime ExitCommand have been successfully written and the
  `TerminalHandshake` completion latch has been counted down.
- The concurrency changes only weaken work after teardown: no callback may revive a disabled
  runtime, and no delayed profiler operation may cause unbounded CPU spinning or block a target
  thread for an externally requested snapshot.
- No release metadata, distribution layout, or user documentation change is required unless an
  existing public API document claims the old `defineClass` null behavior (none is assumed).

## Acceptance criteria and test design

1. **F1:** A deterministic concurrency test races `send` (and, where practical, speculative
   operations) with `clear`/normal runtime exit repeatedly. It completes without NPE, does not
   resurrect a queue, and normal pre-detach buffering still works.
2. **F2:** A probe class with `@Property` registers `btrace:name=<canonical-name>` on the platform
   MBean server and receives an owned registration handle whose random token appears in the
   reserved top-level `MBeanInfo` descriptor field. `handleExit` must immediately re-query that
   descriptor and remove exactly the matching registration. Seed a same-name foreign MBean before
   registration and prove BTrace neither replaces it nor later unregisters it. In a separate
   sequential replacement test, register BTrace, unregister it from the test, register a same-name
   foreign MBean, then invoke the saved handle/exit cleanup; prove the descriptor mismatch leaves
   the foreign MBean intact. Repeated exit/unregister is harmless, including when a preceding
   extension/exit cleanup action throws. A no-property probe produces no registration. Each test
   cleans only the registrations it owns in `finally`; it must not claim coverage for a concurrent
   third-party unregister/register race that JMX cannot make atomic. Focused shutdown tests record
   the command-listener sequence and prove, for both `handleExit` and `shutdownCmdLine`, that the
   exact terminal MessageCommand is dispatched before the runtime ExitCommand and that neither
   `exitting` nor queue clearing happens before that dispatch. A command-thread test invokes
   terminal shutdown through `shutdownCmdLine()` and proves the owned handle closes exactly once
   without waiting for/self-joining `cmdThread`. An off-thread test holds marker dispatch past the
   bounded acknowledgment timeout, proves the caller returns without an early ExitCommand, releases
   dispatch, and then observes marker followed by Exit. A non-terminal Disconnect path leaves the
   registration intact. A first-code-wins test races or sequences multiple terminal requests and
   asserts one ExitCommand with the first code. A focused `RemoteClient` protocol test injects an
   inbound ExitCommand and asserts it is not echoed immediately; after the reader returns its
   generic-finally path retains the output until the `TerminalHandshake` completion latch is counted
   down by the successful runtime-generated Exit write. The wire sequence is shutdown marker then
   exactly one ExitCommand carrying the inbound first code, followed only afterward by final agent
   close.
3. **F3:** Event and error handlers that throw produce an error-level diagnostic (captured with the
   repository's logging test support or a test appender), do not throw to their caller, and leave
   the current runtime wrapper restored both when the prior value is a runtime and when it is null.
4. **F4:** Force a failure before snapshot-state acquisition (or inject an equivalent contention
   condition) and assert `getRecords` returns/throws promptly instead of spinning. Existing
   snapshot/reset and live-frame tests continue to pass.
5. **F5:** Unit-test the Java-8-compatible common failure normalizer directly with verification,
   linkage, reflective-wrapper, and VM-fatal inputs: recoverable inputs become the documented
   `IllegalStateException` with the unwrapped root cause, while `VirtualMachineError` and
   `ThreadDeath` are rethrown. Each `_8`, `_9`, and `_11` tier must use that normalizer at its
   definition/initialization boundary; test the selected factory with invalid bytes on every test
   JVM, and add tier-specific tests through an injectable/package-private definition seam rather
   than depending on a second installed JDK. The JVM matrix must additionally execute the natural
   Java 8 `_8`, Java 9--10 `_9`, Java 11--14 `_11` anchor, and Java 15+ `_11` hidden-class paths
   where those runtimes are available. A successful definition remains a `Class<?>`.
6. **F6:** Create recorder references, allow selected ones to clear (or use a test-visible cleanup
   seam), run both `snapshot` and `reset`, and assert dead wrappers are removed while live recorders
   still contribute records.
7. **F7:** Hold snapshot state 2 with a test latch while another thread calls both `recordEntry` and
   `recordExit`. Each application-side call must complete within a bounded timeout without polling
   for snapshot completion, and its delayed record must be present, in FIFO order, in the next
   drain/snapshot. The test must release the held state in `finally`, assert a valid stack and
   aggregation result after the delayed exit, and use a thread-safe queue test seam or observable
   queue size rather than timing alone.
8. **Real lifecycle and fault-isolation path:** Add an `integration-tests` scenario that builds the
   distribution and dynamically attaches a staged masked client to a real target JVM using a probe
   with `@Property`, a deliberately throwing `@OnEvent`, and a deliberately throwing `@OnError`.
   Trigger the named event through the real client/agent protocol so both handler paths execute;
   assert target-side runtime logs include each handler failure and throwable, while the target
   remains healthy and a subsequent normal probe result reaches the client over the forced V2
   protocol. Do not use `DisconnectCommand` for the unload assertion: it is non-terminal and may
   only be tested as preserving the active probe/MBean. Send a real nonzero `ExitCommand` to
   terminate the probe through the same raw/forced-V2 protocol path users use, record received
   command types and assert the exact terminal-cleanup `MessageCommand` marker (with its
   MBean-close diagnostic) is dispatched before exactly one final ExitCommand carrying that same
   code; no post-exit output may substitute for that ordering. The protocol reader must remain able
   to receive both commands after it has accepted the inbound exit, proving its generic-finally
   path did not close output early. Then assert clean client/target termination and that the
   descriptor-token-matched probe MBean is absent. The target-side helper that reports MBean state
   must use the same canonical-name and descriptor-token lookup as production cleanup; if
   cross-process platform-MBean visibility is unavailable, invoke that helper in the target process
   rather than substituting an unrelated unit test.

## Validation

Run validation from the dedicated issue worktree with a workspace-local Gradle cache. Redirect each
Gradle invocation to a log and inspect filtered relevant lines rather than consuming raw output.

1. `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-runtime:spotlessCheck`
2. `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-runtime:test`
3. `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build`
4. `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration :integration-tests:test --tests <new-issue-888-test>`
5. `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration :integration-tests:test`
6. `git diff --check` and a final diff review against every acceptance criterion.

Use the repository's IPv4 `JAVA_TOOL_OPTIONS` workaround if the restricted environment requires it.
