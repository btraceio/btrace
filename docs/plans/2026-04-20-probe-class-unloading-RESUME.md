# Session resume — probe-class-unloading work (PR #830)

**Last updated:** 2026-04-20
**Branch:** `phase3-invokedynamic-dispatch` @ `73c3422d` (pushed) + 1 uncommitted WIP on `Client.java`
**Remote:** `origin/phase3-invokedynamic-dispatch`
**PR:** https://github.com/btraceio/btrace/pull/830

## Current Session Finding

**Investigation revealed:** Test failure root cause is NOT the race condition that `synchronized loadClass` was supposed to fix. Bisection shows the test started failing at commit **906d924d** (per-probe ClassLoader/hidden class definition), not at ec451089 (removeRuntime wire-up). 

The test failure signature is: probe loads successfully ("Successfully started BTrace probe") but the traced app produces zero output from probe methods. This indicates the per-probe ClassLoader change broke probe method invocation or output capture, not probe loading itself.

**Next session must focus on:** Diagnosing why probe methods don't execute or don't produce output after the per-probe ClassLoader change. The synchronized loadClass is a valid improvement to prevent the race (and prevents NPE crashes), but doesn't fix the root test failure.

## Status

**Committed + pushed:**
- `07842ea8` perf(instr): key handler cache by composite HandlerKey, not concatenated string
- `27dae4ac` feat(instr): expose defined probe Class<?> on BTraceProbe
- `b3501afe` refactor(instr): resolve probe handler via probe.getProbeClass(), not Class.forName
- `906d924d` feat(runtime): define probes in per-probe ClassLoader / hidden class (JDK 8, 9-14, 15+)
- `76fd6a28` refactor(runtime): shared ProbeAnchor helper, drop mustBeBootstrap, add BTraceRuntimes.removeRuntime
- `1054b463` test(instr): add same-name re-definition test; docs: correct probe-residency javadoc
- `ec451089` fix(agent): unregister BTraceRuntime on probe detach to prevent leak
- `73c3422d` refactor(instr): move handler MethodHandle cache to per-probe instance

**Unit tests (`:btrace-instr:test`): green on both JDK 8 and JDK 21.**

**CI failures on PR #830** (`gh pr checks 830`):
- `test (11.0.30-tem)` FAIL: `testJfr`, `testOnMethodLevel`, `testAttachWithMaskedJar`
- `test (8.0.482-tem)` FAIL: `testOnMethodLevel`, `testAttachWithMaskedJar`
- JDK 17/21/25/26 jobs were pending at last check

## Root cause identified

**Race between `Client.loadClass` (Thread-0, probe load) and `Client.onExit` (Thread-1, detach) introduced by `ec451089`.**

Before `ec451089`:
- `Client.cleanupTransformers()` did not touch `BTraceRuntimeAccessImpl.runtimes` map.
- Race between load and onExit was benign (map stayed populated).

After `ec451089`:
- `cleanupTransformers` calls `BTraceRuntimes.removeRuntime(probe.getClassName())` which does `runtimes.remove(className)`.
- If Thread-1 fires before Thread-0 finishes `probe.register()` → `defineClass` → probe class `<clinit>` (which calls `BTraceRuntimeAccess.forClass(probe.class)` → `runtimes.get(cl.getName())`), the map is empty, `runtime.init(...)` NPEs.

The probe's command-listener thread (Thread-1) is **started from inside the probe's `<clinit>`** (via `BTraceRuntimeImplBase.init()`), so it can receive an `ExitCommand` off the wire before `<clinit>` finishes running on Thread-0.

### Debug trace (key evidence)

Captured on `JAVA_TEST_HOME=$(sdk home java 11.0.23-tem) ./gradlew :integration-tests:test --tests tests.BTraceFunctionalTests.testOnMethodLevel -Pintegration`:

```
[traced app] [DEBUG-addRuntime] key=org.openjdk.btrace.runtime.auxiliary.OnMethodLevelTest runtimes@... accessClass@... via=org.openjdk.btrace.boot.MaskedClassLoader@...
[traced app] [DEBUG-addRuntime] after put, size=1 keys=[org.openjdk.btrace.runtime.auxiliary.OnMethodLevelTest]
[traced app] [Thread-0] DEBUG org.openjdk.btrace.instr.BTraceProbeSupport - about to defineClass org.openjdk.btrace.runtime.auxiliary.OnMethodLevelTest
[traced app] [Thread-1] DEBUG org.openjdk.btrace.agent.Client - onExit:
[traced app] [DEBUG-forClass] cl.getName()=... keys=[]   ← map cleared by Thread-1 before <clinit> read it
[traced app] [DEBUG-removeRuntime] caller: Client.cleanupTransformers(Client.java:520)
[traced app] java.lang.ExceptionInInitializerError
[traced app]   at ...BTraceRuntimeImpl_11.defineClass(BTraceRuntimeImpl_11.java:246)   ← clz.getConstructor().newInstance()
[traced app]   at ...BTraceProbeSupport.defineClass(BTraceProbeSupport.java:311)
[traced app]   at ...BTraceProbePersisted.register(BTraceProbePersisted.java:567)
[traced app]   at ...Client.loadClass(Client.java:425)
[traced app]   at ...RemoteClient.<init>(RemoteClient.java:194)
[traced app] Caused by: java.lang.NullPointerException
[traced app]   at ...BTraceRuntimeAccessImpl.forClassInternal(BTraceRuntimeAccessImpl.java:192)   ← runtime = runtimes.get(cl.getName()); runtime.init(...)
```

Confirmed `runtimes@<id>` and `accessClass@<id>` are identical between `addRuntime` and `forClassInternal` — same static map, same agent-CL `BTraceRuntimeAccessImpl` class. Classloader concerns ruled out. Pure thread race.

## Fix attempt in progress (UNCOMMITTED)

Added `synchronized` modifier to `Client.loadClass` in `btrace-agent/src/main/java/org/openjdk/btrace/agent/Client.java:330`:

```diff
- final Class<?> loadClass(InstrumentCommand instr) throws IOException {
+ final synchronized Class<?> loadClass(InstrumentCommand instr) throws IOException {
```

Rationale: `onExit` is already `synchronized void onExit(int exitCode)` on the same `this` monitor (Client.java:293). Making loadClass synchronized forces mutual exclusion so `onExit` waits for `loadClass` to finish before running `cleanupTransformers`.

**Status when session interrupted:** the edit landed (verified via grep). A test run was about to start to confirm the fix but was interrupted. The one earlier test run after this edit still showed Thread-1 proceeding while Thread-0 was in defineClass, which contradicts the synchronization. Possible explanations to investigate on resume:
1. The test was run against a stale build (Gradle cache issue). Re-check with a clean build.
2. `loadClass` is being called somewhere that bypasses the synchronized method dispatch (unlikely — Java resolves synchronized via method descriptor).
3. `onExit` was entered BEFORE `loadClass` acquired the lock — i.e., Thread-1 got the lock first, released it (because no probe yet, onExit no-ops), and by the time Thread-0 runs loadClass, the map is empty. Need to check whether an early onExit can fire before any loadClass.

## Known-to-fail tests (local reproduction)

```bash
source ~/.sdkman/bin/sdkman-init.sh
JAVA_TEST_HOME=$(sdk home java 11.0.23-tem) ./gradlew :integration-tests:test \
    --tests 'tests.BTraceFunctionalTests.testOnMethodLevel' -Pintegration
```

With `BTRACE_TEST_DEBUG=true` set, you get the full `[traced app] [btrace out] [traced app]` dump including probe `<clinit>` stack traces.

Also failing: `tests.BTraceFunctionalTests.testJfr` (JDK 11 only), `tests.JBangAttachDockerTest.testAttachWithMaskedJar` (both). The JBang one failed on CI but local run skips it if Docker isn't running.

## Root Cause Analysis (Per-Probe ClassLoader Issue)

The per-probe ClassLoader change (906d924d) creates each probe in a fresh unnamed ClassLoader. Initial hypothesis was ClassLoader visibility issue (probe can't see BTraceUtils).

**Attempt 1 - Add app ClassLoader as parent**: Updated all BTraceRuntimeImpl versions to pass app ClassLoader as parent to per-probe ClassLoader. **Result: FAILED**. Tests still fail with zero output from probes.

**Revised hypothesis**: The issue is NOT ClassLoader visibility but something deeper:
- Probes load successfully ("Successfully started BTrace probe" appears)
- But traced app produces ZERO output, not different output
- This suggests the instrumented bytecode in traced app is not invoking the probe at all
- Likely cause: INVOKEDYNAMIC dispatch or handler resolution broken when probe is in isolated CL

**Evidence**:
- Test passes on 07842ea8 with full probe output: `[btrace out] none [this, noargs]`
- Test fails on 906d924d with zero output from probes
- ClassLoader parent fix didn't help, ruling out visibility as root cause
- Handler cache refactoring (73c3422d) is still suspect - needs investigation

## Session 2 Work Summary

**Investigation & Attempts:**
1. Added `synchronized` to `Client.loadClass` to prevent race condition (valid fix but doesn't solve test failure)
2. Used git bisection to identify 906d924d (per-probe ClassLoader) as breaking change
3. Hypothesized ClassLoader visibility issue (per-probe CL can't see BTraceUtils)
4. **Fix Attempt**: Pass app ClassLoader as parent to per-probe ClassLoader
   - Modified ProbeAnchor.defineAnchor() to accept parent parameter
   - Updated BTraceRuntimeImpl_8/9/11 to pass app CL as parent
   - **Result**: FAILED - tests still produce zero output from probes
   - **Conclusion**: Visibility is NOT the root cause

**Revised diagnosis**: Issue is INVOKEDYNAMIC dispatch or handler resolution failure when probe in isolated CL.

## Session 3 Work Summary - REFINED DIAGNOSIS

**CORRECTED: Methods ARE present in instrumented bytecode**

Initial analysis was incorrect - when using javap with full disassembly (`-c -private`), callA() and callB() ARE present in the instrumented bytecode with proper INVOKEDYNAMIC instructions.

**What we found:**
- Instrumented Main.class HAS callA() and callB() methods
- Both methods contain conditional INVOKEDYNAMIC instructions that invoke probe handlers
- The INVOKEDYNAMIC is guarded by: `if (LinkingFlag.get() != 0) skip invokedynamic`
- INVOKEDYNAMIC is inserted at correct locations in the method bodies

**CRITICAL ROOT CAUSE IDENTIFIED:**
- The `$btrace$$level` static field in the probe class is initialized to 0
- Instrumented code checks: `if (probeClass.$btrace$$level >= 100) { invokedynamic }`
- Since level is 0 and the check requires >= 100, the condition is ALWAYS FALSE
- Therefore INVOKEDYNAMIC is never executed, bootstrap is never called, handlers never run
- The field is never updated to the correct level value (100, 150) from @Level annotations

This explains:
- Why INVOKEDYNAMIC instruction exists in bytecode but isn't executed
- Why IndyDispatcher.bootstrap() is never called
- Why probe handlers produce zero output
- Why tests are failing on all JDK versions

**The issue is NOT related to per-probe ClassLoaders** - it's a probe-level initialization problem that existed before or was introduced by recent changes.

## CRITICAL FIX REQUIRED

**The probe level field is not being initialized to the enableAt level values (100, 150)**

The `$btrace$$level` static field must be set during probe class initialization to match the @Level values from @OnMethod annotations. Currently it stays at 0, so the level check always fails.

### Next session must fix:

1. **Find where `$btrace$$level` should be initialized**
   - Search BTraceProbeSupport.defineClass() and related code
   - Check BTraceProbePersisted for probe registration logic
   - Look for where Level metadata is converted to runtime values

2. **Understand the initialization flow**
   - How does the probe class get the correct level value (100, 150)?
   - Was this initialization code broken by per-probe ClassLoader changes?
   - Does the isolated ClassLoader prevent normal field access/modification?

3. **Implement the fix**
   - Set `$btrace$$level = <enableAt level value>` when probe class is initialized
   - Ensure this works with per-probe ClassLoaders and hidden classes
   - May need reflection to set the field if normal access is blocked

4. **Verify the fix**
   - Run testOnMethodLevel - handlers should now execute
   - Verify output contains "[this, noargs]", "[this, args]", "{xxx}"
   - Check all failing tests pass

3. **Verify no leftover debug code** in BTraceRuntimeAccessImpl.java (already confirmed clean).

2. **Re-run the failing integration test** after the `synchronized loadClass` edit to confirm or refute the fix:
   ```
   source ~/.sdkman/bin/sdkman-init.sh
   JAVA_TEST_HOME=$(sdk home java 11.0.23-tem) ./gradlew :integration-tests:test \
       --tests 'tests.BTraceFunctionalTests.testOnMethodLevel' -Pintegration
   ```
   Expected on success: PASS. If still fails, likely cause: `onExit` was entered before `loadClass` because of out-of-order command delivery on the wire.

3. **If synchronized fix doesn't work**, fall back to a state-flag approach:
   - Add `private volatile boolean probeLoaded = false;` to `Client`.
   - Set true at the end of `loadClass` inside the try block, AFTER `probe.register()` returns successfully.
   - In `cleanupTransformers()`, only call `BTraceRuntimes.removeRuntime(probeName)` if `probeLoaded` is true.
   - Accept the trade-off: if load fails before the flag is set, we leak one runtime entry — but a failed load is exceptional and a crash on a normal detach is far worse.

4. **Also investigate `testJfr` and `testAttachWithMaskedJar`** — may or may not share the same root cause. Look at their stack traces on CI via `gh api repos/btraceio/btrace/actions/jobs/72048627587/logs` (JDK 11) and `.../72048627596/logs` (JDK 8).

5. **Full verification** once fix is in place:
   - `./gradlew :btrace-instr:test` (default JDK 21)
   - `JAVA_TEST_HOME=$(sdk home java 8.0.482-librca) ./gradlew :btrace-instr:test` (JDK 8)
   - `JAVA_TEST_HOME=$(sdk home java 11.0.23-tem) ./gradlew :integration-tests:test -Pintegration`
   - `JAVA_TEST_HOME=$(sdk home java 8.0.482-librca) ./gradlew :integration-tests:test -Pintegration`
   - Push, wait for full CI matrix to go green.

6. **Commit the fix** with message along the lines of `fix(agent): serialize probe load and detach to avoid runtime-registry race`.

## Files with uncommitted WIP

- `btrace-agent/src/main/java/org/openjdk/btrace/agent/Client.java` — added `synchronized` to `loadClass`.

## Useful commands to reproduce state

```bash
# current branch / HEAD
git status
git log --oneline -8

# which commit broke integration tests (bisect confirmed)
git log --oneline master..HEAD

# inspect the raced methods
grep -n "loadClass\b\|onExit\b\|synchronized" btrace-agent/src/main/java/org/openjdk/btrace/agent/Client.java
grep -n "runtimes\.\(clear\|remove\|put\)" btrace-runtime/src/main/java/org/openjdk/btrace/runtime/BTraceRuntimeAccessImpl.java

# CI status
gh pr checks 830
gh api repos/btraceio/btrace/actions/jobs/72048627587/logs
gh api repos/btraceio/btrace/actions/jobs/72048627596/logs

# local reproduce
source ~/.sdkman/bin/sdkman-init.sh
BTRACE_TEST_DEBUG=true JAVA_TEST_HOME=$(sdk home java 11.0.23-tem) \
    ./gradlew :integration-tests:test --tests 'tests.BTraceFunctionalTests.testOnMethodLevel' -Pintegration 2>&1 | tail -60
```

## Architectural context (for anyone new to the work)

- PR #830 moved probe classes out of bootstrap CL into per-probe ClassLoaders (JDK 8/9-14) or hidden classes (JDK 15+) so that probe detach actually releases the class for unloading.
- Handler `MethodHandle` cache moved from a static `HandlerRepositoryImpl.handlerCache` to a per-probe field on `BTraceProbeSupport`.
- `BTraceRuntime.Impl.defineClass(byte[], boolean)` signature reduced to `(byte[])` — `mustBeBootstrap` was dead after the residency change.
- New public primitive `BTraceRuntimes.removeRuntime(String)` added so callers can release the static runtime registry entry.
- `Client.cleanupTransformers` wired to call `removeRuntime` at detach — **this wire-up is what introduced the race covered in this document**.

Full implementation plan: `docs/plans/2026-04-20-probe-class-unloading.md`.

## Memory (user prefs relevant to this work)

- `JAVA_TEST_HOME=<path>` is the canonical way to run tests on a non-default JDK. Default dev JDK is 21.
- sdkman has 8.0.482-librca, 11.0.23-tem, 11.0.17-tem, 17.0.5/8/11/13/14/18-tem, 21.0.x-tem variants available locally.
- Do not leak internal phase/iteration labels (e.g. "Phase 3") into public javadoc or shipped comments.
