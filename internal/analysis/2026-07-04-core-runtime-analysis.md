# Wide Analysis: btrace-core & btrace-runtime

Date: 2026-07-04
Scope: all sources under `btrace-core/src/main` and `btrace-runtime/src/main` (incl. java9/java11
multi-release trees), plus cross-checks against callers in `btrace-agent`, `btrace-client`, and
`btrace-instr`. Focus: outright bugs, concurrency, inconsistencies, API issues.

Method: six parallel deep-review passes (wire protocol/comm, core base classes, extension
subsystem, runtime impl core, runtime services/profiling, cross-module API consistency), followed
by manual verification of every high-severity finding against the source.

Severity legend: **HIGH** = broken feature, data corruption, cross-client leak, or DoS on a
realistic path; **MED** = wrong behavior/race on a plausible path; **LOW** = latent, edge-case,
or API-hygiene issue.

---

## HIGH severity

### H1. Instrumentation-level guards always read level 0 — `@Level` gating never works
`btrace-agent/.../HandlerRepositoryImpl.java:187-194`, `btrace-runtime/.../BTraceRuntimes.java:111-117`,
`BTraceRuntimeAccessImpl.java:246-255`, `BTraceRuntimeImplBase.java:623-641`

The MethodHandle level guard folds `BTraceRuntimes.getCurrentLevel()` in front of the handler
body. The guard runs at the indy call site **before** the handler's woven
`BTraceRuntimeAccess.enter(rt)` prologue, so the thread-local runtime is unset and
`getCurrent()` falls back to the shared `dummy` runtime (`BTraceRuntimeAccessImpl.java:253`),
whose `levelValue` is permanently 0. Meanwhile `setInstrumentationLevel()` / the `level=` script
argument write `levelValue` on the *client* runtime, which the guard never reads. Root cause:
`BTraceRuntimeImplBase.getInstrumentationLevel()/setInstrumentationLevel()` ignore `this` and
re-dispatch through `getCurrent()`. Consequence: the `samples/AllMethodsLevels.java` pattern
(`@OnMethod(enableAt=@Level(">=1"))` + `BTraceUtils.setInstrumentationLevel(1)`) never enables
the gated probes; conversely a level set while un-entered lands on the dummy and would leak
across all clients.

### H2. `handleException` inverts enter/leave discipline — `@OnError` handlers never fire, errors swallowed
`btrace-runtime/.../BTraceRuntimeImplBase.java:658-688`

When a probe body throws, the generated catch block calls `handleException` while the thread is
still entered. `handleException` then invokes each `@OnError` method reflectively — but
`@OnError` methods are themselves GUARDED (`Preprocessor` puts `ONERROR_DESC` into
`RT_AWARE_ANNOTS`, copied into `GUARDED_ANNOTS`), so their prologue calls `enter(runtime)`,
which fails (`RTWrapper.set` refuses when `rt != null`) and the handler bails out immediately.
And because `errorHandlers != null`, the `else` branch that would `enqueue(new ErrorCommand(th))`
is skipped — the exception disappears entirely: no error-handler output, no ErrorCommand to the
client. Contrast `handleEvent` (line 612), which correctly dispatches through
`BTraceRuntimeAccessImpl.doWithCurrent`/`RTWrapper.escape`. Fix shape: leave() (or escape) before
dispatching error handlers.

### H3. `CommandQueue.enqueue` clears the application thread's interrupt flag and silently drops commands
`btrace-runtime/.../CommandQueue.java:69-85`

`while (!Thread.interrupted() && !queue.relaxedOffer(cmd)) {...} return true;`
(a) `Thread.interrupted()` **clears** the interrupt status of the producing thread — and
producers are arbitrary instrumented application threads calling `BTraceUtils.println` etc. An
app thread interrupted mid-`Future.cancel(true)` that crosses a probe loses its interrupt
silently, corrupting application interrupt semantics. (b) If the flag is already set on entry,
the loop is skipped before `relaxedOffer` is ever attempted and the method returns `true` — the
command is dropped even with an empty queue, reported as success, and `droppedCommands` is not
incremented. Correct handling: attempt the offer regardless; when bailing on interrupt, restore
the flag via `Thread.currentThread().interrupt()` and count the drop.

### H4. `@OnLowMemory` is completely broken (four stacked defects)
`btrace-core/.../handlers/LowMemoryHandler.java:47-50`, `btrace-runtime/.../BTraceRuntimeImplBase.java:1069-1073`,
`btrace-agent/.../Preprocessor.java:1063-1089`

(a) `LowMemoryHandler.invoke` does `getMethod(clz).invoke(clz, null, args)` — varargs packing
sends **two** reflective arguments `{null, args}` to a one-arg (`MemoryUsage`) handler →
`IllegalArgumentException`; the call site additionally injects its own extra `null`
(`handler.invoke(clazz, null, info.getUsage())`) and swallows the failure in an empty
`catch (Throwable)` (BTraceRuntimeImplBase.java:1073). (b) On the agent side, the Preprocessor
emits a `LowMemoryHandler` constructor call with descriptor
`(String;String;J;String)V` that does not exist (actual ctor ends `..., boolean trackUsage`) →
`NoSuchMethodError`; (c) it allocates the handler array as `ExitHandler[]` but passes it as
`LowMemoryHandler[]` → `VerifyError`; (d) it emits `LDC null` for the absent `thresholdFrom`
member (the annotation has no such member at all). Net: the shipped `samples/MemAlerter.java`
cannot even define its probe class; if it could, notifications would still silently never reach
the handler. No test covers `@OnLowMemory`.

### H5. `CircularBuffer` re-delivers stale commands and is unsynchronized
`btrace-core/.../CircularBuffer.java:19-73`, used by `btrace-agent/.../RemoteClient.java:81,276,292`

(a) Logic bug: consumed slots are never nulled and `add()` advances `readIndex` by testing
`elements[nextIndex] != null` — mixing a monotonic logical index with a wrapped physical slot.
Once `size` elements have cumulatively passed through, every later `add` resets `readIndex` onto
a stale, already-consumed slot. Trace (size 3): add 1,2,3; drain; add 4 → forEach delivers
[2, 3, 4]. In production (`delayedCommands`, size 5000) a reconnect after a long disconnect
re-sends thousands of stale commands — potentially including a stale `ExitCommand`.
`getLength()` is corrupted in the same state. (b) Zero synchronization on
`writeIndex`/`readIndex` while `RemoteClient.onCommand` is invoked from at least two threads
(per-client command-handler thread and the runtime queue drain) — concurrent `add/add` can lose
a command; `add/forEach` can double-deliver or skip.

### H6. `SharedSettings.GLOBAL` is shared mutable state across all clients; `trusted` escalates stickily
`btrace-core/.../SharedSettings.java:43,65-132`, `btrace-agent/.../Main.java:118,1407`, `RemoteClient.java:118`

Every remote client's `SET_PARAMS` is applied via `settings.from(cmd.getParams())` directly onto
the GLOBAL singleton that also backs the transformer and DebugSupport. Consequences: (a) one
client's `debug`/`dumpDir`/`outputFile`/`trusted` leak to every other client and to global agent
behavior; (b) `trusted |= b` is one-way — once any client connects trusted, the agent stays
trusted for all subsequent clients (cross-session privilege escalation); (c) plain non-volatile
fields written on the accept thread and read by transformer/probe threads — no happens-before,
so updates may be unseen or torn. Related: the deprecated `UNSAFE_KEY` branch (lines 74-81) does
a plain overwrite `trusted = b`, so a legacy client sending `unsafe=false` *downgrades* a
previously trusted agent — opposite semantics of its documented synonym `TRUSTED_KEY`.

### H7. Profiler `snapshotAndReset` never resets
`btrace-runtime/.../profiling/MethodInvocationRecorder.java:189`, `MethodInvocationProfiler.java:82`

`getRecords(boolean reset)` never reads its `reset` parameter, and
`MethodInvocationProfiler.snapshot(true)` never calls `mir.reset()` either. Full chain verified:
`BTraceUtils.Profiling.snapshotAndReset` → `BTraceRuntime.snapshotAndReset` →
`profiler.snapshot(true)` → `getRecords(reset)`. A script doing periodic
`printSnapshot(snapshotAndReset(p))` silently reports cumulative totals instead of per-interval
deltas — violating the documented `Profiler.snapshot(boolean reset)` atomic-reset contract.

### H8. `MethodInvocationRecorder.reset()` corrupts the recorder when threads are mid-method
`btrace-runtime/.../profiling/MethodInvocationRecorder.java:249-269`

`reset()` copies the live stack records into the new `measured` array, executes
`Arrays.fill(stackArr, null)` — but never resets `stackPtr`. The stack now reports depth k with
null slots: the next `processExit` pops `null`, and the recursion loop
(line 153, `stackArr[i].blockName`) NPEs out of `recordExit` into the probe handler; the
onStack records copied into `newMeasured` are never popped/merged and permanently pollute every
subsequent snapshot; the next `compactMeasured` arraycopies null slots and NPEs again
(`m.onStack` at line 281), after which `getRecords` emits "Unexpected NULL record".
`reset()` is directly reachable from scripts (`BTraceUtils.Profiling.reset`) and is routinely
called while target threads are inside recorded blocks — unrecoverable corruption.

### H9. Profiler direct-recursion off-by-one inflates wall time
`btrace-runtime/.../profiling/MethodInvocationRecorder.java:153`

The recursion scan in `processExit` runs `for (i = 0; i < stackPtr; i++)` — after `pop()` the
immediate parent sits at index `stackPtr` and is excluded (everywhere else the live length is
`stackPtr + 1`). Directly self-recursive A→A: exiting the inner A finds no match, `wallTime` is
not zeroed, and both invocations contribute full wall time (~2× at depth 2, worse deeper).
Indirect recursion (A→B→A) is caught correctly, proving intent. Fix: `i <= stackPtr`.

### H10. V1 protocol cannot decode `LIST_FAILED_EXTENSIONS` — kills the session on both ends
`btrace-core/.../comm/WireIO.java:31-85` (vs `Command.java:42`)

`WireIO.read()` handles types 0–16 but has no case for `LIST_FAILED_EXTENSIONS` (17), which the
client actively sends (`Client.java:867,1131`) and the agent echoes (`RemoteClient.java:155-161`).
Under V1 (`-Dbtrace.comm.protocol=v1` or fallback after failed V2 negotiation) the receiver hits
`default: throw new RuntimeException(...)` — a bare RuntimeException (not IOException), which
escapes the agent accept path without closing the socket (client blocks forever) and escapes the
client `commandLoop` catches (skipping the synthetic `ExitCommand(-1)` cleanup).

### H11. V1 `JavaSerializationProtocol.write` races on the shared `ObjectOutputStream`
`btrace-core/.../comm/JavaSerializationProtocol.java:105-118` (vs `WireIO.java:94-103`)

`write()` calls `oos.reset()` **outside** any lock before delegating to the
`synchronized (oos)` block in `WireIO.write`; `flush()` is unsynchronized too. The protocol is
written concurrently in the agent by the runtime queue-drain thread and RemoteClient's
client-command thread (LIST_PROBES etc. replies). A concurrent `reset()` emits a TC_RESET marker
and mutates the OOS block buffer mid-command → torn V1 stream → peer StreamCorruptedException,
session dead. (The `synchronized (wireProtocol)` around flush at RemoteClient.java:285 is a
different monitor and provides no exclusion.)

### H12. Agent-side protocol negotiation has no timeout — one idle connection wedges the whole agent
`btrace-core/.../comm/ProtocolNegotiator.java:114-135,180-188`, `btrace-agent/.../Main.java:1400-1414`

`negotiateAgent` → `readFully` blocks until 4 bytes arrive; `getNegotiationTimeoutMs()` exists
but is applied only client-side (`Client.java:351-361` sets soTimeout) — grep confirms no
`setSoTimeout` anywhere in btrace-agent. `RemoteClient.getClient` runs synchronously on the
single accept thread, so `nc host 2020` (or any half-open/scanner connection) permanently stops
the agent from accepting any new clients.

### H13. Embedded extensions bypass the permission/consent gate entirely
`btrace-core/.../extension/impl/EmbeddedExtensionRepository.java:243` (vs `FatAgentMojo.java:391-392`)

`parseEmbeddedExtension` hardcodes `.requiredPermissions(PermissionSet.empty())` and never reads
the `permissions` property that the embedded-packaging pipeline explicitly writes.
`ExtensionBridgeImpl.requiresPrivileged()` therefore always returns false for embedded
extensions, silently skipping the allowPrivileged/allowExtensions consent gate — e.g. an
embedded btrace-statsd (declares `Permission.NETWORK`) opens sockets with no consent check,
while the identical filesystem-deployed extension is blocked.

### H14. `ExtensionLoaderImpl`: unbounded bootstrap-append/FD leak + cross-extension `HashMap` races
`btrace-core/.../extension/impl/ExtensionLoaderImpl.java:299-328, 46,70,224-227,234,280`

(a) `ensureApiOnBootstrap` has no already-appended tracking: every call opens a **new** `JarFile`
and calls `appendToBootstrapClassLoaderSearch` again. It is on a hot path —
`Preprocessor.ensureExtensionLoaded` invokes it per `@Injected` field per submitted script, and
`doLoad` re-appends the same API jar. Long-running agents accumulate open FDs and duplicate
bootstrap search entries without bound. (b) `openApiJars` is a plain `ArrayList` mutated under
inconsistent monitors; (c) `loadedExtensions` is a plain `HashMap` written under
`synchronized(descriptor)` — a per-descriptor monitor — so concurrent loads of two *different*
extensions race on `HashMap.put` (lost entries; on this codebase's Java 8 target semantics,
possible resize CPU-spin). Loads are genuinely concurrent: indy bootstrap on arbitrary app
threads vs. per-client handler threads.

### H15. JDK 11 impl returns `null` JFR event factory (8/9 return safe EMPTY) → NPE in probes
`btrace-runtime/src/main/java11/.../BTraceRuntimeImpl_11.java:350-357`
(vs `BTraceRuntimeImpl_8.java:218-225`, `BTraceRuntimeImpl_9.java:270-272`)

`createEventFactory` on _11 returns **null** when `isJfrAvailable()` is false; _8 falls back to
`() -> JfrEvent.EMPTY` and _9 always returns the EMPTY factory. The generated probe clinit
stores the factory in a static field and `BTraceUtils.Jfr.prepareEvent` immediately calls
`eventFactory.newEvent()` — NPE inside the probe on any JFR-less JDK 11+ (e.g. jlink image
without `jdk.jfr`), on exactly the implementation virtually all deployments use.
`JfrEvent.EMPTY` itself is a correct null object; the null factory defeats it.

### H16. `perfInt`/`perfLong`/`perfString` built-ins can never work
`btrace-runtime/.../BTraceRuntimeImplBase.java:1367-1379`, `NullPerfReaderImpl.java:27-42`

`createPerfReaderImpl` gates on
`String.class.getResource("sun/jvmstat/monitor/MonitoredHost.class")` — a *relative* path
resolved against `java/lang/` (and module-confined on JDK 9+) — null on every JDK ever, so the
reader is always `NullPerfReaderImpl`, whose every method throws
`UnsupportedOperationException`. Independently, the reflective target
`io.btrace.agent.PerfReaderImpl` is package-private in another package with no
`setAccessible(true)`, so instantiation would fail anyway. Every `Sys.VM.perfInt(...)` call
surfaces as a probe error.

---

## MEDIUM severity

### Runtime lifecycle / command pipeline

**M1. `ExitCommand` is subject to the 1 ms drop-on-backpressure policy.**
`BTraceRuntimeImplBase.java:1207`, `CommandQueue.java:69-85`. Under output flood (queue capacity
100, `DROP_TIMEOUT_MS` = 1), EXIT is likely dropped: the consumer never sets `exitting`, the
drain never terminates, `handleExit`'s `join(2000)` times out, the remote client never receives
EXIT (CLI hangs), and `Client.onExit` → `cleanupTransformers()/retransformLoaded()` never runs —
dead instrumentation left in the app permanently. EXIT is already special-cased for speculation
(line 329) but not for dropping.

**M2. cmdThread drain is un-interruptible and swallows interrupts; no fallback stop path.**
`BTraceRuntimeImplBase.java:391-406`. `waitStrategy` catches `InterruptedException` and returns 0
without restoring the flag; `exitCondition` tests only `exitting`, which is set solely via
`shutdownCmdLine` from `Client.onExit` — itself reachable only through a successfully delivered
EXIT (see M1). `handleExit` should call `shutdownCmdLine()` after the join timeout.

**M3. Speculation teardown races with in-flight probes.**
`BTraceRuntimeImplBase.java:311-316 vs 326-341, 447-451`. cmdThread finally does
`specQueueManager.clear()` (nulls non-volatile `speculativeQueues`/`currentSpeculationId`)
*before* `disabled = true`; `send()` does unsynchronized check-then-use on those fields → NPE on
app threads during shutdown. App threads' speculation ThreadLocals are never cleaned.

**M4. Client-name uniquification is check-then-act.**
`BTraceRuntimeAccessImpl.java:173-179`. `while (clients.contains(name)) ...; clients.add(name);`
— two concurrent attaches of the same probe class can both claim the same "unique" name and
silently overwrite each other in `runtimes` (`addRuntime` is a plain `put`), cross-wiring one
client's dispatch to the other's runtime. Should be `while (!clients.add(name))`.

**M5. MBean registration is never undone → probe class/loader pinned forever.**
`BTraceRuntimeImplBase.java:531`, `BTraceMBean.java:86-102`. Nothing unregisters the
`BTraceMBean` on exit/detach; the platform MBeanServer keeps a strong ref to the probe `Class`
and its loader — defeating the unloadability design (`removeRuntime()` javadoc). Name
uniquification (`$1`, `$2`, …) means repeated attach/detach accumulates MBeans.

### Handler/annotation contract mismatches (core annotations vs agent/runtime interpretation)

**M6. Bare `@OnTimer` handlers are silently dropped; `from`-fallback default is wrong.**
`Preprocessor.java:866-906` vs `OnTimer.java:38`. TimerHandler emission is guarded by
`if (anValueIterator != null)` — ASM materializes no values for a bare `@OnTimer` (documented
default 1000 ms), so no handler is created, silently. And the local default `period = -1` makes
`@OnTimer(from=...)` with an absent argument schedule with -1 → IllegalArgumentException that
kills *all* timer handlers of the script (single try/catch in `start()`).

**M7. `@PeriodicEvent.period` default `"eachChunk"` never applies.**
`Preprocessor.java:283` vs `PeriodicEvent.java:92`. Preprocessor initializes `period = null` and
ASM never materializes annotation defaults; `JfrEventFactoryImpl` registers the periodic hook
only when `getPeriod() != null` — a `@PeriodicEvent` relying on the documented default silently
never fires.

**M8. `@Event.stacktrace` default `true` never applies.**
`Preprocessor.java:284` vs `Event.java:190`. Same ASM-defaults mechanism: initialized `false`,
so every `@Event` without explicit `stacktrace=true` bakes `@StackTrace(false)` into the JFR
event type — contrary to the documented default.

**M9. Bare `@Sampled` runs the Adaptive sampler despite declared default `Sampler.Const`.**
`BTraceMethodNode.java:196,220-237` vs `Sampled.java:43`. Reader pre-sets Adaptive (mean 500)
before visiting values; the annotation's declared default is Const (mean 10). The annotation's
own javadoc contradicts its default — one of the two is wrong; the runtime picks Adaptive.

### Wire protocol

**M10. V2 silently drops `GridDataCommand.format` and coerces cell types.**
`v2/CommandAdapter.java:224-227`, `v2/BinaryGridDataCommand.java:55-108`,
`v2/ScalarEncoding.java:76-80`, `v2/NumberEncoding.java:78-82`. The user-supplied format string
of `Profiling.printSnapshot(name, snapshot, format)` arrives as null over V2 (the default
protocol); Byte/Short/BigInteger/BigDecimal cells (transported intact by V1) are stringified or
collapsed to Long. Same script → different client output depending on negotiated protocol.

**M11. V2 write lock is a JVM-global static ReentrantLock.**
`v2/BinaryWireIO.java:31-84`. All clients' streams serialize through one lock; a stalled client
blocking in `out.flush()` while holding it stalls every other client's command stream and the
queue-drain threads feeding them. Should be per-stream.

**M12. `-Dbtrace.comm.forceVersion=true` alone breaks every connection.**
`ProtocolConfig.java:61-70,115-134`. Builder defaults `autoNegotiate=true`; the constructor
rejects `forceVersion && autoNegotiate` → `fromSystemProperties()` (called per connection on
both sides) throws. The documented flag needs to implicitly clear autoNegotiate.

**M13. `protocol=v2, autoNegotiate=false` interpreted oppositely by client and agent.**
`Client.java:315-339` vs `RemoteClient.java:92-101`. Client silently degrades to V1 (force-V2
branch requires `isForceVersion()`); agent expects V2 and fails negotiation with
`IOException("expected V2")`. Every connection with these settings fails.
`WireProtocol.createWithConfig` defines a third semantics neither peer uses.

**M14. `BinaryMessageCommand.read` — unbounded allocations + zlib native leak.**
`v2/BinaryMessageCommand.java:107-150`. `originalSize`/`compressedSize`/decompressed `length`
feed `new byte[...]` with no cap (unlike `BinaryProtocol.readString`'s 100 MB cap) — corrupt
streams force ~2 GB allocations or `NegativeArraySizeException`. Caller-supplied
`Deflater`/`Inflater` are never `end()`ed and the InflaterInputStream never closed — native
memory leak on every message > 1 KB.

**M15. V1 command writes are non-atomic against serialization failure.**
`WireIO.java:95-102`, `ErrorCommand.java:36-38`. The type byte goes on the wire before
`cmd.write()`; `ErrorCommand.write` does `writeObject(cause)` with an arbitrary application
Throwable — a non-serializable cause fires `NotSerializableException` mid-stream, permanently
desynchronizing V1; the agent's `dispatchCommand` catches IOException and re-queues, re-corrupting.
Same hazard for `GridDataCommand`'s `writeObject(cell)`. V2 is immune (stringified).

**M16. One null grid cell kills the client session.**
`GridDataCommand.java:117-158`. `print()` computes column widths via `obj[column].toString()`
*before* the `"<null>"` substitution — NPE in the client listener, converted by `commandLoop`
into "Protocol closed during command processing", terminating the session. Null cells are
deliverable by both protocols.

### Core utilities

**M17. `ArgsMap` positional/parsing defects break `Sys.getpid()` and `=`-containing values.**
`ArgsMap.java:33-45,67-78`. Value-less args stored as `""` but `get(int)` tests `!= null` →
`$(0)` returns `"12345="`; `Sys.getpid()` (`Integer.parseInt($(0))`) always returns -1. And
`arg.split("=")` without limit destroys values containing '=' (should be `split("=", 2)`).

**M18. `ArgsMap.template()` doesn't quote replacements.**
`ArgsMap.java:120-138`. Values containing `\` or `$` (e.g. Windows paths) throw from
`Matcher.appendReplacement` during `@OnTimer(from=...)` / `@OnLowMemory(pool=...)` /
`@OnEvent` templating — killing handler initialization. Needs `Matcher.quoteReplacement`.

**M19. `BTraceUtils.classForName(String)` resolves against the wrong caller frame.**
`BTraceUtils.java:433-435`. Delegation to `Reflective.classForName` adds a frame, so the
`STACK_DEC`-calibrated caller-classloader lookup lands on the bootstrap-loaded `BTraceUtils`
frame → loader null → application classes unfindable, while the "equivalent"
`BTraceUtils.Reflective.classForName` works.

**M20. `scriptOutputDir` is silently ignored — output always lands in target CWD.**
`SharedSettings.java:58,272-274`, `btrace-agent/.../Client.java:151`, `Main.java:1327-1332`.
`Client.setupWriter` reads `getScriptDir()`, which has no setter and is never populated; the
carefully computed `scriptOutputDir` is never read at write time.

**M21. `BTraceUtils.Collections.copy(Collection, Collection)` is an empty method.**
`BTraceUtils.java:4231`. Annotated `@SuppressWarnings("EmptyMethod")`, no instrumentation
special-cases it; silently does nothing while the sibling `copy(Map, Map)` works.

**M22. BTrace collection wrappers leak unsynchronized views/iterators.**
`types/BTraceMap.java:92-113`, `BTraceDeque.java:170-192`, `BTraceRuntime.java:237-248`.
`keySet()/entrySet()/values()` return the raw backing views; `BTraceDeque.iterator()` is the raw
ArrayDeque iterator. `BTraceUtils.contains(deque, x)` iterates lock-free → CME inside an
instrumented application method under concurrent probe mutation.

**M23. `BTraceRuntime.compare/contains` NPE on nulls and are asymmetric.**
`BTraceRuntime.java:189-198,237-248`. `compare(null, x)` NPEs (`obj1.getClass()`); `contains`
calls it per element, so a collection legitimately containing null blows up the probe. Also
`compare("a", x)` uses equals but `compare(x, "a")` uses reference identity.

**M24. `Strings.substr(str, start, length)` treats `length` as an end index.**
`BTraceUtils.java:3340-3342`. `substr("hello", 2, 2)` returns `""`; C-style naming
(strcat/strstr context) makes the misread near-certain.

**M25. `jstack()` vs `Threads.jstack()` strip different frame counts.**
`BTraceUtils.java:80-92,112-126 vs 3024-3094`. Identical physical stack depth, different strip
constants (2 vs 1) — same probe, one extra caller frame dropped depending on which public entry
point is used. Same for `jstackStr`.

**M26. `ArgsMap.equals` violates reflexivity/symmetry.**
`ArgsMap.java:105-113`. Delegates to `map.equals(o)` where `o` is not a Map → `a.equals(a)` is
false; hashCode is simultaneously content-based.

### Extension subsystem

**M27. Version conflict resolution uses lexicographic String compare.**
`ExtensionLoaderImpl.java:439-445`. `"1.10.0" < "1.9.0"` lexicographically, so the *older*
version wins ties, contradicting "latest version wins". A correct component-wise comparator
already exists (`BTraceVersionRange.compare`) and is unused here.

**M28. `META-INF/services` scan registers provider class names as "services".**
`ExtensionMetadata.java:198-222`. The scan reads file *contents* (impl FQCNs) instead of file
*names* (interface FQCNs). Inference-discovered extensions become unusable
(`findExtensionForService(<interface>)` → null), and for manifest-based extensions the impl
names are unioned into the declared-service set, letting scripts `@Injected` the implementation
class directly — bypassing the interface abstraction the permission/shim model relies on.

**M29. TCCL `ServiceLoader` fallback lets application classpath supply the "extension impl".**
`ExtensionBridgeImpl.java:110-120`. Step 5 loads the service interface via the thread-context
classloader — at indy-link time that is the *application's* loader. Any dependency on the app
classpath can ship a `META-INF/services/<extension-api-interface>` entry and get instantiated as
the extension implementation (provider ctor runs during ServiceLoader iteration), sidestepping
the extension-identity-based PermissionPolicy. Comment says "useful in tests"; path is active in
production.

**M30. `findImplementationClass` instantiates every provider and aborts on the first bad one.**
`ExtensionBridgeImpl.java:156-176`. ServiceLoader iteration instantiates providers just to get
the class (double init: again when the runtime instantiates the winner); the loop is wrapped in
`catch (Throwable) { /* ignore */ }` so one broken provider silently disables the whole
ServiceLoader mechanism with no diagnostic.

**M31. Embedded extensions share one flat classdata namespace.**
`ClassDataLoader.java:87` (+ `ExtensionLoaderImpl.java:275-277`). `.classdata` resources are
resolved by bare FQCN from the shared agent loader, ignoring `resourceBasePath` — every embedded
extension's loader can define any embedded extension's classes; two extensions shading the same
class at different versions nondeterministically get whichever copy the fat-jar merge kept. The
"extensions cannot see each other's classes" contract does not hold for embedded extensions.

**M32. Gradle extension plugin scans for the wrong annotation package.**
`btrace-gradle-plugin/.../BTraceExtensionPlugin.groovy:280,357,704,718,1097` vs
`io.btrace.core.extensions.ServiceDescriptor`. The plugin matches
`Lorg/openjdk/btrace/core/extensions/...;` descriptors, but the shipped annotation is
`io.btrace...` (no relocation exists). `@ServiceDescriptor(permissions=...)` declarations (e.g.
`Permission.NETWORK` on Statsd) are never collected into `BTrace-Extension-Permissions`, and the
build-time "manifest covers annotated permissions" cross-check passes vacuously — a privileged
extension can ship with an empty permission manifest.

### Profiling / services

**M33. Snapshot-time spin blocks all instrumented threads.**
`MethodInvocationRecorder.java:64-142,169-187`. `recordEntry/recordExit` unconditionally call
`processDelayedRecords()` first, which spin-parks in `CAS(0→3)` until snapshot/reset (status
2/4) completes — so the delayed-record deferral machinery is effectively unreachable and every
probe-firing app thread stalls for the duration of a snapshot. Also: `CAS(1→3)` branches are
dead code; the state-key comment documents 3 as "resetting" while `reset()` uses undocumented 4.

**M34. Cross-thread snapshot merge drops min/max and NPEs on sparse arrays.**
`MethodInvocationProfiler.java:97-98,110-113`. The merge loop aggregates only
invocations/selfTime/wallTime — min/max reflect only the first thread's recorder (wrong extrema
in JMX/printSnapshot on any multi-threaded workload); and unlike the first-recorder branch it
has no null check, so a sparse `records` array from a second recorder NPEs out to
`Profiling.snapshot()`.

**M35. Per-call-site extension instances via indy bootstrap.**
`ExtensionIndy.java:83` (+ `Preprocessor.java:754,1614-1631`). One indy site is emitted per
(handler method × `@Injected` field) and `bootstrapFieldGet` creates + `initialize()`s + registers
a fresh service instance per site — N handlers using one injected field get N extension
instances; stateful extensions silently lose state sharing; teardown closes N instances.

**M36. `IndyDispatcher` invalidation races.**
`IndyDispatcher.java:115-126,209-217`. (a) `bootstrap` publishes `setTarget(mh)` *before*
`registerSite` — an `invalidateProbe` in that window misses the site forever (live handler after
detach, the exact failure the class exists to prevent). (b) `relink` re-resolves and
`setTarget(resolved)` with no re-validation — can reinstall a dead probe's handler after
invalidation.

**M37. `XMLSerializer` unusable on JDK 16+ and unbounded recursion.**
`XMLSerializer.java:101-107`. `setAccessible(true)` across java.base internals throws
`InaccessibleObjectException` for virtually any object graph (even a String field);
`write→writeObject→writeFields→write` recursion has no depth limit — a 10k-node linked list
overflows the stack in the target JVM. Reachable via `BTraceUtils.Export.toXML/writeXML`.

**M38. `Interval.union` shrinks contained intervals (latent).**
`Interval.java:86`. `previous.b = current.b` unconditionally: union({(0,100),(5,10)}) → (0,10),
losing (11,100); also corrupts `invert()` and mutates TreeSet keys in place. No production
callers today (`Interval.ge/fromString` only) — latent but wrong public API.

**M39. Dummy runtime NPEs instead of no-op'ing.**
`BTraceRuntimeImplBase.java:422-428,1128-1139`. The no-arg dummy (the `Accessor.getRt()`
fallback) null-guards only `enqueue()`; `send()`, `speculation()/speculate()/commit()/discard()`,
`sizeof()`, `handleExit()` NPE on the nulled `specQueueManager`/`instrumentation`/`cmdThread` —
inconsistent with the facade's "missing runtime is benign" contract.

**M40. `getClientNameInternal`'s `clients` set + `nextSpeculationId` grow monotonically.**
`BTraceRuntimeAccessImpl.java:89,175-179`, `BTraceRuntimeImplBase.java:370-375`. Client names
are never removed on detach; speculation ids are never reused — after 32767 `speculation()`
calls the feature permanently returns -1 and `speculate(-1)` throws into the probe.

---

## LOW severity

- **L1.** `RTWrapper.escape` swallows every `Exception` silently (no log/ErrorCommand) — a
  throwing `@OnEvent` handler vanishes; and it catches only `Exception`, so an `Error` thrown
  after enter can leave the thread-local set, permanently locking that thread out of all probes.
  `BTraceRuntimeAccessImpl.java:52-64`.
- **L2.** `BTraceRuntime.handleException` dereferences `getRt()` unconditionally while
  `enter()/leave()` are null-safe — pre-registration window NPE replaces the original exception
  inside the woven catch handler. `core/BTraceRuntime.java:136-155`.
- **L3.** String perf-counter buffers sized to the initial value, not `PERF_STRING_LIMIT`:
  `@Export String s = "a"` then a longer assignment → `BufferOverflowException` in the probe.
  `BTraceRuntimeImpl_8.java:177-188` (same in _9/_11).
- **L4.** `defineClass` failure-handling drift across MR impls: _11 throws IllegalStateException
  with cause; _8/_9 swallow and return null (later unexplained NPE). `BTraceRuntimeImpl_9.java:156-163`
  vs `BTraceRuntimeImpl_11.java:255-266`.
- **L5.** `BTraceRuntimeAccessImpl.shutdownCmdLine()` sets an instance `exitting` flag nothing
  reads — dead code masquerading as a stop control. `BTraceRuntimeAccessImpl.java:97,182-184`.
- **L6.** `exitImpl` not idempotent for command emission — both script `exit()` and agent
  `handleExit()` paths can send duplicate `ExitCommand`. `BTraceRuntimeImplBase.java:1177-1214`.
- **L7.** `cmdQueueLimit=1..3` throws from `MpscChunkedArrayQueue` (min capacity 4) and fails the
  whole attach; "Dropped N commands" notice only emitted before the *next delivered* command, so
  trailing drops are never reported. `CommandQueue.java:38-66`.
- **L8.** `init()`/`start()` write `clazz`/handler arrays/`lowMemoryHandlerMap` unsynchronized;
  read from shutdown-hook/notification threads without a happens-before pairing — handlers can
  be observed null and skipped. `BTraceRuntimeImplBase.java:485-532`.
- **L9.** `BTraceMBean` maps `wallTime.min` unguarded (`Long.MAX_VALUE` surfaces in JMX) while
  `selfTime.min` is guarded. `BTraceMBean.java:474,481`.
- **L10.** `MethodInvocationProfiler.lastTs` plain read-modify-write under concurrent
  `snapshot()` callers → skewed percent computations. `MethodInvocationProfiler.java:46,122-124`.
- **L11.** `newProfiler(0)` → zero-length buffer → AIOOBE on first record; default
  `newProfiler()` allocates 153,600 slots (600<<8) per traced thread. `MethodInvocationRecorder.java:57`.
- **L12.** `JfrEventFactoryImpl.periodicHook` non-volatile cross-thread (unregister may skip
  removal); `eMsg.replace(...)` NPEs on null exception message; final `catch (Throwable ignored)`
  swallows all periodic-registration failures. `JfrEventFactoryImpl.java:101,207,210`.
- **L13.** `DOTWriter` gates array detail on the global node index instead of a per-array count
  (arrays after the 32nd node render as "..."); `escapeString` misses `|`, `<`, `>` (DOT record
  metacharacters) and skips escaping entirely for values starting with `"`. `DOTWriter.java:238-371`.
- **L14.** `DotWriterFormatter` truncation off-by-one (`substring(0, stringLimit - 1)`);
  `stringLimit=0` (settable via `dotwriter.stringLimit`) throws StringIndexOutOfBounds.
  `DotWriterFormatter.java:33`.
- **L15.** `Profiler.Snapshot` documented immutable but exposes a mutable `Record[]` of mutable
  Records shared with JMX consumers; recorder `WeakReference` list never purged outside
  iteration (growth under thread churn); a thread dying before the next snapshot silently loses
  its data. `core/Profiler.java:106-111,200-209`, `MethodInvocationProfiler.java`.
- **L16.** `BTraceBootstrap.bootstrap` throws `BootstrapMethodError` on a missing op-table entry
  → JVM permanently poisons that indy site in the *application*; safe only while the
  registration-before-first-fire invariant holds. `BTraceBootstrap.java:50`.
- **L17.** `WireIO` unknown-type throws bare `RuntimeException` (not IOException) — bypasses
  every IOException-based recovery path (amplifies H10). `WireIO.java:84`.
- **L18.** `NumberMapDataCommand(name, null)` NPEs in the V2 adapter but works over V1;
  inconsistent null policy vs `StringMapDataCommand` (defaults to empty map).
  `v2/CommandAdapter.java:203-210`, `NumberMapDataCommand.java:39-45`.
- **L19.** `WireProtocol.createWithNegotiation` consumes the client's V2 magic but never echoes
  it — any server built on this public entry point hangs every V2 client (currently test-only).
  `WireProtocol.java:143-149`.
- **L20.** Command classes inconsistently expose internal mutable state
  (`SetSettingsCommand.getParams()`, `GridDataCommand` data list uncopied both ways,
  `NumberMapDataCommand.getData()`) while siblings defensively copy — mutation while a command
  sits in the async queue changes what gets serialized. `SetSettingsCommand.java:42-44` etc.
- **L21.** Extension failure registry mixes keys (service class name vs extension id) and never
  clears entries on later success — permanent stale "failed" reports.
  `ExtensionBridgeImpl.java:61-62,94-96,146,151`.
- **L22.** `ExtensionDescriptorDTO.setClassLoader` is public and irreversibly flips
  `loaded=true` — any holder can poison an extension with an arbitrary loader.
  `ExtensionDescriptorDTO.java:174-177`.
- **L23.** `PermissionPolicy` allow/deny setters only add, never clear — agent-arg "override" is
  actually additive union; repeated loads accumulate stale entries. `PermissionPolicy.java:51-69,131-136`.
- **L24.** `BTraceVersionRange.parseIntSafe` maps malformed components to 0 — a typo'd
  `BTrace-API-Version` collapses toward 0.0.0 and passes compatibility unconditionally.
  `BTraceVersionRange.java:95-101`.
- **L25.** `NestedJarExtensionClassLoader` is dead code with latent Windows temp-file and
  bootstrap-append-leak defects if resurrected.
- **L26.** `extensions.autoload` is parsed, javadoc'd (with inverted meaning), and never
  consulted — discovery always eager-loads. `ExtensionConfig.java:137,173-180`.
- **L27.** Generated extension adapters cache one `static volatile MethodHandle` resolved
  against the first caller's classloader — multi-classloader deployments (two webapps) get
  permanent ClassCastException for the second loader. `processor/AdapterEmitter.java:49-51,68-89`.
- **L28.** Embedded descriptor id (from properties) can diverge from the manifest extId keying
  `resourceBasePath`; FatAgentMojo omits `version` → "0.0.0" degrades conflict resolution.
  `EmbeddedExtensionRepository.java:203-208,245`.
- **L29.** `printArray` emits a trailing separator (`[a, b, ]`) while `Strings.str(Object[])`
  formats correctly; `jstackStr(Throwable)` output malformed vs its printing twin.
  `BTraceUtils.java:882-891,3167-3183`.
- **L30.** `getDouble(String, Class)` is the odd one out of the `getXxxStatic` naming family and
  overload-shadows the instance accessor when the target object is a Class.
  `BTraceUtils.java:5333-5336`.
- **L31.** Dead/duplicated state inviting drift: `BTraceRuntime.dotWriterProps` (never used),
  jvmstat constants + `PERF_STRING_LIMIT` + `messageTimestamp` duplicated between core facade
  and each runtime impl; `version()` drift across MR impls (7 vs 9 vs feature()); dead
  `getCurrent()` statement in `$()`. `core/BTraceRuntime.java:59-73`,
  `BTraceRuntimeImplBase.java:734`, `BTraceRuntimeImpl_8.java:247-250`.

---

## Verified clean (notable non-findings)

- `JfrEvent.EMPTY` null object, `JfrEventImpl` field-index mapping, `ProbeAnchor` hand-assembled
  classfile, `Auxiliary`, `ExtensionContextImpl`, `NullPerfReaderImpl` (as a class).
- `MethodID` (ConcurrentHashMap.computeIfAbsent), `PrefixMap` (populated only in static init),
  `HandlerRepository`/`LinkingFlag`/`PackGenerator`/`Messages`.
- `ClassDataLoader` locking discipline (parallel-capable + per-name lock + double-check),
  `PermissionSet` immutability, `ServiceDeclarationRegistry` volatile pattern,
  `MethodHandleCache`.
- V2 `BinaryCommand.COMMAND_FACTORIES` static-init mutation (benign re-puts), STATUS flag
  round-trip, serialVersionUID absence (commands never object-serialized whole), V1↔V2
  mixed-force handshakes (client recovers via new-socket fallback).

## Suggested triage order

1. Correctness of core contracts users depend on daily: H2 (`@OnError`), H7–H9 (profiler), H4
   (`@OnLowMemory`), M6–M9 (annotation defaults) — all silently produce wrong results.
2. Target-app safety: H3 (interrupt clearing), M33 (snapshot stalls), H5/H6 (agent state), M22.
3. Session reliability: H10–H12, M1–M2, M10–M16.
4. Extension security model: H13, M28, M29, M32 (these compose into real gate bypasses).
5. Modern-JDK enablement: H15, H16, M37.
