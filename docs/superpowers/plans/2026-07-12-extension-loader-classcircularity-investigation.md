# Investigation: `ClassCircularityError` in `ExtensionLoader.initialize()`

**Status:** Root cause identified. No fix implemented yet — this is a diagnosis, pending a decision on fix strategy.

## Symptom

`BTraceFunctionalTests.testJfr()` intermittently fails (observed 1-in-3 to 1-in-8 runs) on
`assertTrue(stderr.isEmpty(), "Non-empty stderr")` during the on-startup (`-javaagent`) phase.
The target JVM's `premain()` throws:

```
java.lang.ClassCircularityError: java/lang/invoke/MethodHandle$1
    at java.base/java.lang.invoke.MethodHandle.customize(MethodHandle.java:1741)
    at java.base/java.lang.invoke.MethodHandle.maybeCustomize(MethodHandle.java:1731)
    at java.base/java.lang.invoke.Invokers.maybeCustomize(Invokers.java:634)
    at java.base/java.lang.invoke.Invokers.checkCustomized(Invokers.java:628)
    at java.base/java.lang.invoke.BootstrapMethodInvoker.invoke(BootstrapMethodInvoker.java:134)
    at java.base/java.lang.invoke.CallSite.makeSite(CallSite.java:315)
    at java.base/java.lang.invoke.MethodHandleNatives.linkCallSiteImpl(MethodHandleNatives.java:281)
    at java.base/java.lang.invoke.MethodHandleNatives.linkCallSite(MethodHandleNatives.java:271)
    at io.btrace.extension.ExtensionLoader.initialize(ExtensionLoader.java:95)
    at io.btrace.agent.Main.initExtensions(Main.java:432)
    at io.btrace.agent.Main.main(Main.java:287)
    at io.btrace.agent.Main.startAgent(Main.java:161)
    at io.btrace.agent.Main.premain(Main.java:151)
```

which cascades into the JVM's native instrument-agent assertion (`*** java.lang.instrument
ASSERTION FAILED ***`, `JPLISAgent.c:422`) and dumps the whole stack to the target process's
stderr.

## Reproduction

- Confirmed via 3 sequential (non-concurrent) runs of `BTraceFunctionalTests.testJfr` alone,
  with `BTRACE_TEST_DEBUG=true`: run 1 pass, run 2 pass, run 3 fail (this exact trace).
- Also observed once during the harness-redesign work on `jb/external-type-adapter` (now merged
  into `develop` via #860).
- Load-sensitive: reproduces more readily when many JVMs have recently run on the machine
  (consistent with a startup-timing race, not a deterministic bug).

## Root cause

`ExtensionLoader.initialize()` (`btrace-core/src/main/java/io/btrace/extension/ExtensionLoader.java:95`):

```java
ServiceDeclarationRegistry.setResolver(fqcn -> instance.findExtensionForService(fqcn) != null);
```

This is a **lambda expression**. javac compiles it to an `invokedynamic` call site bound to
`LambdaMetafactory`, so the *first* time this line executes, the JVM must bootstrap that call
site — which is exactly the `MethodHandleNatives.linkCallSite` → `CallSite.makeSite` →
`MethodHandle.customize` path in the stack trace above.

`ExtensionLoader.initialize()` runs from `Main.initExtensions()` → `Main.main()`, which is called
directly from `-javaagent` `premain()` — i.e., extremely early in target-JVM startup, before
`main()` (the application's own `public static void main`) has even begun. At this point the
JVM's own `java.lang.invoke` subsystem (which lazily bootstraps `MethodHandle`, `MethodHandle$1`,
`CallSite`, etc. on first use) may not have finished its own class initialization. Triggering a
*fresh* `invokedynamic` linkage this early — under load, when JIT/class-loading threads are
contending for CPU — can race with the JVM's own in-progress initialization of the same classes,
producing `ClassCircularityError`.

This is a load-sensitive startup race, not a logic bug — which matches the observed intermittency.

## Why the existing `LinkingFlag`/`MethodHandleNatives` guard does NOT protect this

The codebase already has infrastructure that looks related but solves a **different** problem:

- `Main.main()` (`btrace-agent/src/main/java/io/btrace/agent/Main.java:252-265`), right before
  `initExtensions()`, retransforms `java.lang.invoke.MethodHandleNatives` via
  `LinkerInstrumentor.addGuard(...)` (`btrace-agent/src/main/java/io/btrace/instr/LinkerInstrumentor.java`).
  This injects `io.btrace.runtime.LinkingFlag.guardLinking()` / `.reset()` calls around
  `MethodHandleNatives.linkCallSite`/`linkMethodHandleConstant`.
- `LinkingFlag` (`btrace-core/src/main/java/io/btrace/runtime/LinkingFlag.java`) is a
  **per-thread reentrancy depth counter** (`ThreadLocal<Integer>`). Its purpose, per the comment
  at `BTraceTransformer.java:135-136`, is "to be able to safely skip BTrace probes while linking
  is still in progress" — i.e., it lets BTrace's **own instrumented probe-dispatch code** detect
  "I am currently re-entering indy-linking machinery on this thread" and bail out, avoiding
  deadlock/infinite recursion when a *user's* instrumented method itself triggers indy linking.

Two reasons this doesn't help our bug:
1. **Nothing reads `LinkingFlag` on the path that crashes.** `ExtensionLoader.initialize()`'s
   lambda doesn't check it, and nothing in the `premain()` call chain up to that point does
   either. The flag is consumed by probe-dispatch code (elsewhere), not by the agent's own
   startup sequence.
2. **It's a reentrancy guard, not a readiness barrier.** Even if consulted, `LinkingFlag` only
   answers "is a link operation already in progress on my thread" — it says nothing about
   whether the JVM's `java.lang.invoke` bootstrap has *fully completed* globally. It cannot tell
   `ExtensionLoader.initialize()` "wait, the JVM isn't ready for you to trigger indy linking yet."
3. The retransform of `MethodHandleNatives` that installs the guard is itself wrapped in a
   silent `catch (Throwable t) { log.debug(...) }` (`Main.java:260-264`) — if it fails or doesn't
   land in time, there is no fallback protection at all, and no visible signal that this happened.

## Confirmed: unrelated to the just-merged test-harness redesign

The crash occurs entirely inside the **target JVM's own `premain()`**, before the harness's
`RuntimeTest` reader code (`attach`/`attachOneliner`/`runBTrace`, all migrated onto
`OutputPump`/`Completion` in #860) reads a single line of output. Traced and confirmed on
`jb/external-type-adapter` before merge: the "Dynamic attach" phase (which exercises the
migrated `attach()`) completed and printed `Detached.` cleanly in every reproduction; the failure
always occurs later, in the separate "On-Startup" (`testStartup`) phase, which is structurally
untouched by that harness work.

## Candidate fix directions (not yet decided/implemented)

1. **Avoid the lambda at the crash site.** Replace `ExtensionLoader.java:95`'s lambda with an
   anonymous inner class implementing `ServiceDeclarationRegistry.Resolver`. Anonymous classes
   are ordinary `invokespecial`/`new`-based construction — no `invokedynamic`, no
   `LambdaMetafactory` bootstrap. Minimal, surgical, but only fixes *this one* call site; any
   other lambda/method-reference/string-concat-via-indy touched this early in `premain()` remains
   equally exposed (untargeted audit of the full `premain()`→`initExtensions()` path for other
   `invokedynamic` sites has not yet been done).
2. **Warm up `java.lang.invoke` deliberately, before the agent touches it.** Add an explicit,
   early, single-threaded "touch a trivial lambda/MethodHandle and let its bootstrap fully
   resolve" step at the very start of `premain()`/`Main.main()`, before `initExtensions()` (and
   ideally before anything else in the agent that might use `invokedynamic`). This is closer to
   your instinct: force `java.lang.invoke`'s own bootstrap to complete in a controlled context
   first, rather than avoiding lambdas piecemeal. Open questions to resolve before implementing:
   - Does forcing this warm-up *from within `premain()`* still race with the JVM's own
     concurrent bootstrap, or does it need to happen even earlier (e.g., via a bootstrap-classpath
     class, or is `premain()` early enough already)?
   - Is there a reliable, catchable signal for "warm-up succeeded" vs. silently swallowing a
     `ClassCircularityError` during warm-up itself?
3. **Combine both:** do the warm-up as the primary defense (protects all future/current
   `invokedynamic` use in early agent code), and still convert the one identified lambda to an
   anonymous class as defense-in-depth for this specific known-hot call site.

## Next steps

- Decide fix direction (1, 2, or 3 above) — recommend **2+1 combined**, given the intermittency
  suggests other undiscovered `invokedynamic` sites in the early `premain()` path could trigger
  the same race even after fixing this one lambda alone.
- If proceeding, this should go through TDD/systematic-debugging per project convention:
  reproduce reliably (this doc's repro recipe), form a single hypothesis, test minimally.
- Reproducing this race reliably enough for a regression test is itself nontrivial (it's
  load-sensitive) — likely needs either a stress-loop test (spin up N target JVMs concurrently)
  or a way to artificially delay/contend `java.lang.invoke` bootstrap in a test harness.
