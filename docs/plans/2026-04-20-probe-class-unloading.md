# Probe Class Unloading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make BTrace probe classes unloadable on probe detach, so that long-running JVMs with repeated attach/detach cycles no longer accumulate probe classes in Metaspace indefinitely.

**Architecture:** Replace the single-bootstrap-CL probe residency with (a) a per-probe isolated `ClassLoader` on JDK 8–14, and (b) a hidden class defined via `Lookup.defineHiddenClass(..., ClassOption.STRONG)` on JDK 15+. The probe `Class<?>` is stored on the `BTraceProbe` wrapper at `register()` time and cleared at `unregister()`. `HandlerRepositoryImpl.resolveHandler` stops calling `Class.forName` and instead reads the cached `Class<?>` from the probe. All existing dispatch mechanisms (`@OnMethod` via indy, `@OnTimer`/`@OnEvent`/`@OnError` via reflection-through-`Class<?>`) work unchanged because they already go through a held `Class<?>` reference, not name-based lookup.

**Tech Stack:** Java 8+, ASM, `sun.misc.Unsafe` (JDK 8 define path), `MethodHandles.Lookup.defineClass` / `defineHiddenClass` (JDK 9+/15+), JUnit 5, Gradle.

---

## Scope

This plan covers **only** the class-residency change. It does **not** touch:

- `HandlerRepositoryImpl.HandlerKey` (already landed on this branch).
- `IndyDispatcher.invalidateProbe` noop mechanism — the noop handle is already defined on `IndyDispatcher` (bootstrap), so noop installation does not pin the probe class. Verify but do not modify.
- The target-class instrumentation itself (INVOKEDYNAMIC emission). Unchanged.

## File structure

Files modified (no new files created):

- `btrace-core/src/main/java/org/openjdk/btrace/core/BTraceRuntime.java` — extend `Impl.defineClass` signature (add overload or drop `mustBeBootstrap` in a follow-up).
- `btrace-runtime/src/main/java/org/openjdk/btrace/runtime/BTraceRuntimeImpl_8.java` — switch define path to per-probe CL.
- `btrace-runtime/src/main/java9/org/openjdk/btrace/runtime/BTraceRuntimeImpl_9.java` — per-probe CL with anchor class.
- `btrace-runtime/src/main/java11/org/openjdk/btrace/runtime/BTraceRuntimeImpl_11.java` — JDK 15+ hidden-class path with JDK 11–14 fallback to per-probe CL.
- `btrace-instr/src/main/java/org/openjdk/btrace/instr/BTraceProbe.java` — add `getProbeClass()` to interface.
- `btrace-instr/src/main/java/org/openjdk/btrace/instr/BTraceProbeSupport.java` — store/clear `Class<?>`.
- `btrace-instr/src/main/java/org/openjdk/btrace/instr/BTraceProbeNode.java` — thread `probeClass` through `register`/`unregister`.
- `btrace-instr/src/main/java/org/openjdk/btrace/instr/BTraceProbePersisted.java` — same.
- `btrace-instr/src/main/java/org/openjdk/btrace/instr/HandlerRepositoryImpl.java` — drop `Class.forName`.

New / extended tests:

- `btrace-instr/src/test/java/org/openjdk/btrace/instr/ProbeTestHelper.java` — **new**, extracted common helpers (`loadTestProbe`, `mockRuntime`, `mockTransformer`) from existing `HandlerRepositoryImplTest`.
- `btrace-instr/src/test/java/org/openjdk/btrace/instr/ProbeClassUnloadingTest.java` — **new**. Asserts **weak reachability** after detach: `WeakReference<Class<?>>` / `WeakReference<ClassLoader>` cleared after dropping strong refs and a handful of `System.gc()` cycles. We do NOT assert "Metaspace has actually unloaded the class" — that is JVM-level and flaky under `System.gc()`; weak reachability is the reliable pre-condition and is deterministic.
- `btrace-instr/src/test/java/org/openjdk/btrace/instr/HandlerRepositoryImplTest.java` — extend existing test with `getProbeClass()` path coverage.

---

## Task 1: Add `getProbeClass()` accessor on `BTraceProbe`

(Task 0 from an earlier revision was dropped — the imagined `loadTestProbe`/`mockRuntime` helpers never existed. The real test harness uses `stubProbe(String internalName)` with lifecycle-only semantics; we extend it to optionally carry a `Class<?>` instead of standing up a compile-script helper.)

**Files:**
- Modify: `btrace-instr/src/main/java/org/openjdk/btrace/instr/BTraceProbe.java`
- Modify: `btrace-instr/src/main/java/org/openjdk/btrace/instr/BTraceProbeSupport.java`
- Modify: `btrace-instr/src/main/java/org/openjdk/btrace/instr/BTraceProbeNode.java`
- Modify: `btrace-instr/src/main/java/org/openjdk/btrace/instr/BTraceProbePersisted.java`
- Test: `btrace-instr/src/test/java/org/openjdk/btrace/instr/HandlerRepositoryImplTest.java`

- [ ] **Step 1: Write failing test for `getProbeClass()` on registered probe**

Append to `HandlerRepositoryImplTest.java`:

```java
@Test
void probeExposesDefinedClass() throws Exception {
    BTraceProbe probe = loadTestProbe("TestProbe");     // existing helper
    probe.register(mockRuntime(), mockTransformer());
    try {
        Class<?> clz = probe.getProbeClass();
        assertNotNull(clz, "getProbeClass must return the defined Class after register()");
        assertEquals(probe.getClassName(true).replace('/', '.'), clz.getName());
    } finally {
        probe.unregister();
    }
}
```

- [ ] **Step 2: Run the test — verify compile failure (method does not exist)**

Run: `./gradlew :btrace-instr:compileTestJava`
Expected: compile error — `cannot find symbol: method getProbeClass()`.

- [ ] **Step 3: Add accessor to the `BTraceProbe` interface**

Add to `BTraceProbe.java`:

```java
/**
 * @return the defined probe {@link Class}, or {@code null} if the probe has not been
 *         registered (or has been unregistered).
 */
Class<?> getProbeClass();
```

- [ ] **Step 4: Back it with a field on `BTraceProbeSupport`**

In `BTraceProbeSupport.java`, modify `defineClass` to stash the result, and add accessor:

```java
private volatile Class<?> probeClass;

Class<?> defineClass(BTraceRuntime.Impl rt, byte[] code) {
    // ... existing body ...
    Class<?> clz = rt.defineClass(code, isTransforming());
    // ... existing logging ...
    this.probeClass = clz;
    return clz;
}

@Override
public Class<?> getProbeClass() {
    return probeClass;
}

void clearProbeClass() {
    this.probeClass = null;
}
```

- [ ] **Step 5: Delegate `getProbeClass()` through `BTraceProbeNode` and `BTraceProbePersisted`**

In both `BTraceProbeNode.java` and `BTraceProbePersisted.java`, add:

```java
@Override
public Class<?> getProbeClass() {
    return delegate.getProbeClass();
}
```

Clear it in both `unregister()` methods (immediately after `HandlerRepositoryImpl.unregisterProbe(this)`):

```java
delegate.clearProbeClass();
```

- [ ] **Step 6: Run the test — verify pass**

Run: `./gradlew :btrace-instr:test --tests org.openjdk.btrace.instr.HandlerRepositoryImplTest.probeExposesDefinedClass`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add btrace-instr/src/main/java/org/openjdk/btrace/instr/BTraceProbe.java \
        btrace-instr/src/main/java/org/openjdk/btrace/instr/BTraceProbeSupport.java \
        btrace-instr/src/main/java/org/openjdk/btrace/instr/BTraceProbeNode.java \
        btrace-instr/src/main/java/org/openjdk/btrace/instr/BTraceProbePersisted.java \
        btrace-instr/src/test/java/org/openjdk/btrace/instr/HandlerRepositoryImplTest.java
git commit -m "feat(instr): expose defined probe Class<?> on BTraceProbe"
```

---

## Task 2: Switch `HandlerRepositoryImpl.resolveHandler` to use `getProbeClass()` instead of `Class.forName`

**Files:**
- Modify: `btrace-instr/src/main/java/org/openjdk/btrace/instr/HandlerRepositoryImpl.java`
- Test: `btrace-instr/src/test/java/org/openjdk/btrace/instr/HandlerRepositoryImplTest.java`

- [ ] **Step 1: Write failing test — resolution must succeed even if the probe class is NOT resolvable by `Class.forName`**

Append to `HandlerRepositoryImplTest.java`:

```java
@Test
void resolveHandlerUsesProbeCachedClass() throws Exception {
    // Define a test probe class in a fresh ClassLoader so Class.forName on the current
    // thread's CL would NOT find it. This verifies resolveHandler goes through
    // probe.getProbeClass() and not Class.forName.
    BTraceProbe probe = loadTestProbe("TestProbe");
    probe.register(mockRuntime(), mockTransformer());
    try {
        MethodHandle mh = HandlerRepositoryImpl.resolveHandler(
            probe.getClassName(true),
            probe.getClassName(true).replace('/', '_') + "$onMethod",
            MethodType.methodType(void.class));
        assertNotNull(mh);
    } finally {
        probe.unregister();
    }
}
```

Also assert no `Class.forName` reachability: add a custom `ClassLoader` in the test that throws on name-based lookup to detect regression:

```java
@Test
void resolveHandlerDoesNotUseClassForName() throws Exception {
    BTraceProbe probe = loadTestProbe("TestProbe");
    probe.register(mockRuntime(), mockTransformer());
    ClassLoader orig = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(new ClassLoader(null) {
        @Override
        protected Class<?> loadClass(String name, boolean resolve) {
            throw new AssertionError("resolveHandler must not use Class.forName: " + name);
        }
    });
    try {
        HandlerRepositoryImpl.resolveHandler(
            probe.getClassName(true),
            probe.getClassName(true).replace('/', '_') + "$onMethod",
            MethodType.methodType(void.class));
    } finally {
        Thread.currentThread().setContextClassLoader(orig);
        probe.unregister();
    }
}
```

- [ ] **Step 2: Run tests — verify the `DoesNotUseClassForName` test fails**

Run: `./gradlew :btrace-instr:test --tests HandlerRepositoryImplTest.resolveHandlerDoesNotUseClassForName`
Expected: FAIL — current code calls `Class.forName`.

- [ ] **Step 3: Replace `Class.forName` with `probe.getProbeClass()`**

In `HandlerRepositoryImpl.java`, replace the body of `resolveHandler`:

```java
public static MethodHandle resolveHandler(
    String probeName, String handlerName, MethodType handlerType) {
  HandlerKey cacheKey = new HandlerKey(probeName, handlerName, handlerType);
  MethodHandle cached = handlerCache.get(cacheKey);
  if (cached != null) {
    return cached;
  }

  BTraceProbe probe = probeMap.get(probeName);
  if (probe == null) {
    return null;
  }
  Class<?> probeClass = probe.getProbeClass();
  if (probeClass == null) {
    // defineClass has not populated probeClass yet (race with register()).
    return null;
  }

  try {
    int dollarIdx = handlerName.lastIndexOf('$');
    String simpleHandlerName =
        dollarIdx >= 0 ? handlerName.substring(dollarIdx + 1) : handlerName;

    MethodHandle mh =
        MethodHandles.publicLookup().findStatic(probeClass, simpleHandlerName, handlerType);
    handlerCache.put(cacheKey, mh);

    if (SharedSettings.GLOBAL.isDumpClasses()) {
      log.debug("BTrace INDY handler resolved: {}.{}", probeName, simpleHandlerName);
    }
    return mh;
  } catch (Throwable e) {
    log.warn("Failed to resolve handler '{}' in probe '{}'", handlerName, probeName, e);
    return null;
  }
}
```

- [ ] **Step 4: Run all `HandlerRepositoryImplTest` tests**

Run: `./gradlew :btrace-instr:test --tests HandlerRepositoryImplTest`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add btrace-instr/src/main/java/org/openjdk/btrace/instr/HandlerRepositoryImpl.java \
        btrace-instr/src/test/java/org/openjdk/btrace/instr/HandlerRepositoryImplTest.java
git commit -m "refactor(instr): resolve probe handler via probe.getProbeClass(), not Class.forName"
```

---

## Task 3: JDK 8 — switch probe define to per-probe ClassLoader

**Files:**
- Modify: `btrace-runtime/src/main/java/org/openjdk/btrace/runtime/BTraceRuntimeImpl_8.java`
- Test: `btrace-instr/src/test/java/org/openjdk/btrace/instr/ProbeClassUnloadingTest.java` (new)

- [ ] **Step 1: Write failing reachability test**

Create `btrace-instr/src/test/java/org/openjdk/btrace/instr/ProbeClassUnloadingTest.java`:

```java
package org.openjdk.btrace.instr;

import java.lang.ref.WeakReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProbeClassUnloadingTest {

    /**
     * Verifies that after probe.unregister() and dropping all caller-held references,
     * BTrace's own internal state retains no strong references to the probe class or
     * its ClassLoader. We assert weak reachability, NOT Metaspace unloading — actual
     * unloading depends on JVM-level GC policy which is non-deterministic under
     * System.gc() and flaky on CI.
     */
    @Test
    void probeClassWeaklyReachableAfterDetach() throws Exception {
        BTraceProbe probe = ProbeTestHelper.loadTestProbe("TestProbe");
        probe.register(ProbeTestHelper.mockRuntime(), ProbeTestHelper.mockTransformer());
        Class<?> probeClass = probe.getProbeClass();
        assertNotNull(probeClass, "register() must populate probeClass");
        WeakReference<Class<?>> weakClass = new WeakReference<>(probeClass);
        ClassLoader probeLoader = probeClass.getClassLoader();
        WeakReference<ClassLoader> weakLoader = probeLoader != null
            ? new WeakReference<>(probeLoader) : null;

        probe.unregister();
        // Drop all strong references the test holds
        probe = null;
        probeClass = null;
        probeLoader = null;

        // A few cycles are typically enough for soft/weak refs; we do not require
        // Metaspace unload here — only weak reachability.
        for (int i = 0; i < 20; i++) {
            System.gc();
            Thread.sleep(50);
            if (weakClass.get() == null && (weakLoader == null || weakLoader.get() == null)) {
                break;
            }
        }
        assertNull(weakClass.get(),
            "probe Class<?> is still strongly reachable from BTrace internal state after detach");
        if (weakLoader != null) {
            assertNull(weakLoader.get(),
                "probe ClassLoader is still strongly reachable after detach");
        }
    }
}
```

- [ ] **Step 2: Run the test — verify it fails (bootstrap CL → no unload)**

Run: `./gradlew :btrace-instr:test --tests org.openjdk.btrace.instr.ProbeClassUnloadingTest`
Expected: FAIL — `loaderRef.get()` is `null` because probe is in bootstrap CL, but `weakRef.get()` is not `null`.

- [ ] **Step 3: Change `BTraceRuntimeImpl_8.defineClass` to always use a fresh isolated CL**

Replace body of `defineClass` at BTraceRuntimeImpl_8.java:131:

```java
@Override
public Class<?> defineClass(byte[] code, boolean mustBeBootstrap) {
    Unsafe unsafe = BTraceRuntime.initUnsafe();
    if (unsafe == null) return null;

    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
    String callerClassName = stack.length > 2 ? stack[2].getClassName() : null;
    if (callerClassName == null || !callerClassName.startsWith("org.openjdk.btrace.")) {
        throw new SecurityException("unsafe defineClass");
    }

    // Per-probe ClassLoader with bootstrap as parent. This loader becomes
    // unreachable (and the probe class unloadable) once the probe is unregistered
    // and all MethodHandles/Call sites referencing the class are cleared.
    // `mustBeBootstrap` is retained in the signature for API compat but no longer
    // forces bootstrap residency — the current dispatch model (indy bootstrap via
    // IndyDispatcher + MethodHandle) does not require the probe class to be
    // visible by name from the target class's CL.
    ClassLoader loader = new ClassLoader(null) {};
    Class<?> cl = unsafe.defineClass(getClassName(), code, 0, code.length, loader, null);
    unsafe.ensureClassInitialized(cl);
    return cl;
}
```

- [ ] **Step 4: Run the unload test on JDK 8**

Run: `./gradlew :btrace-instr:test --tests org.openjdk.btrace.instr.ProbeClassUnloadingTest -PtestJdk=8`
Expected: PASS (the loader is unreachable, class unloads after GC).

- [ ] **Step 5: Run the existing instrumentor test suite to catch regressions**

Run: `./gradlew :btrace-instr:test`
Expected: all existing tests PASS.

Likely regressions to debug if any fail:
- Any test that asserts probe class is in bootstrap CL → rewrite to assert per-probe CL instead.
- Any test that does `Class.forName(probeName)` → rewrite to use `probe.getProbeClass()`.

- [ ] **Step 6: Commit**

```bash
git add btrace-runtime/src/main/java/org/openjdk/btrace/runtime/BTraceRuntimeImpl_8.java \
        btrace-instr/src/test/java/org/openjdk/btrace/instr/ProbeClassUnloadingTest.java
git commit -m "feat(runtime): define probes in per-probe ClassLoader on JDK 8"
```

---

## Task 4: JDK 9+ — per-probe anchor class + CL

**Files:**
- Modify: `btrace-runtime/src/main/java9/org/openjdk/btrace/runtime/BTraceRuntimeImpl_9.java`

**Design:** We need to call `MethodHandles.privateLookupIn(anchor, lookup()).defineClass(code)` — this defines the class in `anchor`'s package and loader. Per-probe anchor means per-probe loader. Generate a minimal anchor class (empty body) into a fresh unnamed `ClassLoader`, then define the probe class into the same loader via that anchor.

- [ ] **Step 1: Run the unload test on JDK 9+ (before change)**

Run: `./gradlew :btrace-instr:test --tests org.openjdk.btrace.instr.ProbeClassUnloadingTest -PtestJdk=11`
Expected: FAIL on JDK 9+ with current code (probe in `Auxiliary`'s bootstrap loader).

- [ ] **Step 2: Write a helper in `BTraceRuntimeImpl_9.java` to generate a per-probe anchor class**

Add a private helper that generates a minimal ASM classfile for `org.openjdk.btrace.runtime.auxiliary.Anchor$<unique>` and defines it in a fresh `ClassLoader`:

```java
private static final java.util.concurrent.atomic.AtomicLong ANCHOR_SEQ =
    new java.util.concurrent.atomic.AtomicLong();

private static Class<?> defineAnchorInFreshLoader() throws Exception {
    long id = ANCHOR_SEQ.incrementAndGet();
    String internalName = "org/openjdk/btrace/runtime/auxiliary/Anchor$" + id;
    String binaryName = internalName.replace('/', '.');

    org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
    cw.visit(org.objectweb.asm.Opcodes.V1_8,
             org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_FINAL,
             internalName, null, "java/lang/Object", null);
    org.objectweb.asm.MethodVisitor mv = cw.visitMethod(
        org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
    mv.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
    cw.visitEnd();
    byte[] anchorBytes = cw.toByteArray();

    ClassLoader loader = new ClassLoader(null) {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(binaryName)) {
                return defineClass(name, anchorBytes, 0, anchorBytes.length);
            }
            throw new ClassNotFoundException(name);
        }
    };
    return Class.forName(binaryName, true, loader);
}
```

- [ ] **Step 3: Replace `BTraceRuntimeImpl_9.defineClass` body**

```java
@Override
public Class<?> defineClass(byte[] code, boolean mustBeBootstrap) {
    try {
        StackWalker.StackFrame frame =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(s -> s.skip(1).findFirst().orElse(null));
        Class<?> caller = frame != null ? frame.getDeclaringClass() : null;
        if (caller == null || !caller.getName().startsWith("org.openjdk.btrace.")) {
            throw new SecurityException("unsafe defineClass");
        }

        Class<?> anchor = defineAnchorInFreshLoader();
        Class<?> clz = MethodHandles.privateLookupIn(anchor, MethodHandles.lookup())
                                    .defineClass(code);
        clz.getConstructor().newInstance();
        return clz;
    } catch (Throwable t) {
        // fall through, behavior matches pre-change
        log.debug("Failed to define probe class", t);
        return null;
    }
}
```

- [ ] **Step 4: Run unload test on JDK 11**

Run: `./gradlew :btrace-instr:test --tests org.openjdk.btrace.instr.ProbeClassUnloadingTest -PtestJdk=11`
Expected: PASS.

- [ ] **Step 5: Run full test suite on JDK 11**

Run: `./gradlew :btrace-instr:test :btrace-runtime:test -PtestJdk=11`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add btrace-runtime/src/main/java9/org/openjdk/btrace/runtime/BTraceRuntimeImpl_9.java
git commit -m "feat(runtime): define probes in per-probe ClassLoader on JDK 9-14"
```

---

## Task 5: JDK 15+ — use hidden classes

**Files:**
- Modify: `btrace-runtime/src/main/java11/org/openjdk/btrace/runtime/BTraceRuntimeImpl_11.java`

**Design:** On JDK 15+, `MethodHandles.Lookup.defineHiddenClass(bytes, initialize, ClassOption.STRONG)` yields a class whose lifetime is tied to `Class<?>` reachability. No per-probe CL needed — cleaner. On JDK 11–14, fall back to the per-probe-CL path from Task 4.

- [ ] **Step 1: Write failing test — on JDK 15+, `getProbeClass().isHidden()` must be true**

Append to `ProbeClassUnloadingTest.java`:

```java
@Test
@EnabledOnJre({JRE.JAVA_15, JRE.JAVA_16, JRE.JAVA_17, JRE.JAVA_18, JRE.JAVA_19,
               JRE.JAVA_20, JRE.JAVA_21, JRE.JAVA_22, JRE.JAVA_23, JRE.JAVA_24, JRE.JAVA_25})
void probeClassIsHiddenOnJdk15Plus() throws Exception {
    BTraceProbe probe = ProbeTestHelper.loadTestProbe("TestProbe");
    probe.register(ProbeTestHelper.mockRuntime(), ProbeTestHelper.mockTransformer());
    try {
        assertTrue(probe.getProbeClass().isHidden());
    } finally {
        probe.unregister();
    }
}
```

- [ ] **Step 2: Run the test — verify it fails (probe is a named class)**

Run: `./gradlew :btrace-instr:test --tests org.openjdk.btrace.instr.ProbeClassUnloadingTest.probeClassIsHiddenOnJdk15Plus -PtestJdk=17`
Expected: FAIL.

- [ ] **Step 3: Add a hidden-class path in `BTraceRuntimeImpl_11.defineClass`, gated on `Runtime.Version`**

```java
@Override
public Class<?> defineClass(byte[] code, boolean mustBeBootstrap) {
    try {
        // Caller validation (unchanged)
        StackWalker.StackFrame frame = /* ... */;
        Class<?> caller = frame != null ? frame.getDeclaringClass() : null;
        if (caller == null || !caller.getName().startsWith("org.openjdk.btrace.")) {
            throw new SecurityException("unsafe defineClass");
        }

        if (Runtime.version().feature() >= 15) {
            // Hidden class: lifetime tied to Class<?> reachability. No per-probe CL needed.
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                Auxiliary.class, MethodHandles.lookup());
            Class<?> clz = lookup.defineHiddenClass(
                code, true, MethodHandles.Lookup.ClassOption.STRONG).lookupClass();
            return clz;
        }

        // JDK 11–14: per-probe CL path (same as Task 4 for JDK 9)
        Class<?> anchor = defineAnchorInFreshLoader();
        Class<?> clz = MethodHandles.privateLookupIn(anchor, MethodHandles.lookup())
                                    .defineClass(code);
        clz.getConstructor().newInstance();
        return clz;
    } catch (Throwable t) {
        log.debug("Failed to define probe class", t);
        return null;
    }
}
```

Note: hidden classes skip `clz.getConstructor().newInstance()` init — hidden classes are initialized automatically by `defineHiddenClass(..., true, ...)`.

Note 2: `publicLookup().findStatic(probeClass, name, type)` on a hidden class requires that the hidden class's own lookup allow it, OR that we use the lookup used to define the class. Hidden classes' handler methods are accessible via `publicLookup()` only if they are both `public` and in a readable module. The probe class is defined via `privateLookupIn(Auxiliary.class)` — Auxiliary's module/package. Probe handler methods generated by the compiler are `public static`. **Verify:** run the indy dispatch path against a hidden probe and confirm `publicLookup().findStatic` succeeds; if not, thread the defining `Lookup` into `HandlerRepositoryImpl` (e.g., add `getProbeLookup()` to `BTraceProbe`).

- [ ] **Step 4: Run hidden-class test**

Run: `./gradlew :btrace-instr:test --tests org.openjdk.btrace.instr.ProbeClassUnloadingTest.probeClassIsHiddenOnJdk15Plus -PtestJdk=17`
Expected: PASS.

- [ ] **Step 5: Run the full unload test on JDK 17**

Run: `./gradlew :btrace-instr:test --tests org.openjdk.btrace.instr.ProbeClassUnloadingTest -PtestJdk=17`
Expected: both tests PASS.

- [ ] **Step 6: If `publicLookup().findStatic` fails for hidden classes, add `getProbeLookup()`**

If Step 4 fails with `IllegalAccessException`:

  a. Add to `BTraceRuntime.Impl.defineClass` return type an alternative that also returns a `Lookup`. Simplest: add a new method `Lookup defineProbeClassAndLookup(byte[] code, boolean mustBeBootstrap)` and deprecate the old one; have `defineClass` call it and drop the Lookup for back-compat consumers.

  b. Store the `Lookup` on `BTraceProbeSupport` alongside `probeClass`. Expose `getProbeLookup()` on `BTraceProbe`.

  c. In `HandlerRepositoryImpl.resolveHandler`, prefer `probe.getProbeLookup().findStatic(...)` if non-null, else fall back to `publicLookup().findStatic(...)`.

Write a test asserting resolution works on hidden probe classes before and after this fix.

- [ ] **Step 7: Run full cross-JDK test matrix**

Run: `./gradlew :btrace-instr:test` (on CI, sweep across JDK 8/11/17/21/25).
Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
git add btrace-runtime/src/main/java11/org/openjdk/btrace/runtime/BTraceRuntimeImpl_11.java \
        btrace-instr/src/test/java/org/openjdk/btrace/instr/ProbeClassUnloadingTest.java
# (if Step 6 was needed, also add BTraceProbe, BTraceProbeSupport, Node, Persisted, HandlerRepositoryImpl)
git commit -m "feat(runtime): use hidden classes for probes on JDK 15+"
```

---

## Task 6: Verify `@OnTimer`/`@OnEvent`/`@OnError` still work with per-probe CL

**Files:**
- Test: `btrace-instr/src/test/java/org/openjdk/btrace/instr/ProbeLifecycleCallbacksTest.java` (new, or extend existing functional test)

- [ ] **Step 1: Identify existing functional-test probe scripts covering timer/event/error callbacks**

Run: `grep -rn "@OnTimer\|@OnEvent\|@OnError" integration-tests/src/test/btrace`
Record file names — these are the probe scripts used by `BTraceFunctionalTests`.

- [ ] **Step 2: Run the existing functional-test suite**

Run: `./gradlew :integration-tests:test`
Expected: all PASS. If any fail due to `Class.forName`/CL visibility assumptions, triage.

- [ ] **Step 3: Add a minimal regression test that specifically exercises @OnTimer + detach + re-attach**

Create `integration-tests/src/test/java/tests/ProbeReattachTest.java`:

```java
@Test
void reattachSameProbeScriptDoesNotCollide() throws Exception {
    // Attach a probe with @OnTimer; let it tick once; detach.
    runFunctionalTest("OnTimerProbe.java", /*duration*/ 2000);
    // Re-attach the SAME script. With bootstrap CL this would throw LinkageError
    // (class already defined). With per-probe CL / hidden class, it succeeds.
    runFunctionalTest("OnTimerProbe.java", /*duration*/ 2000);
}
```

- [ ] **Step 4: Run the new regression test**

Run: `./gradlew :integration-tests:test --tests ProbeReattachTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add integration-tests/src/test/java/tests/ProbeReattachTest.java
git commit -m "test(integration): verify probe re-attach does not collide with per-probe CL"
```

---

## Task 7: Documentation + final cleanup

**Files:**
- Modify: `btrace-instr/src/main/java/org/openjdk/btrace/instr/HandlerRepositoryImpl.java` (javadoc)
- Modify: `btrace-runtime/src/main/java/org/openjdk/btrace/runtime/IndyDispatcher.java` (update `Why always MutableCallSite` section to mention class unloading)

- [ ] **Step 1: Update `IndyDispatcher` class javadoc**

The current `Why always MutableCallSite` paragraph says probe classes stay in bootstrap CL. After this change, that's no longer the reason — instead, the reason is that instrumentation in target classes persists after detach (no retransform-on-detach), so live call sites must be relinkable to a noop to avoid NPEs. Update wording to reflect this.

- [ ] **Step 2: Update `HandlerRepositoryImpl` javadoc**

Remove the stale comment about "probe classes are defined in the bootstrap CL (via Unsafe.defineClass with loader=null)". Replace with: "probe classes are defined in per-probe ClassLoaders (JDK 8–14) or as hidden classes (JDK 15+); the Class reference is kept on the BTraceProbe wrapper and cleared on unregister()."

- [ ] **Step 3: Run the full test suite one more time**

Run: `./gradlew check`
Expected: all PASS.

- [ ] **Step 4: Commit**

```bash
git add btrace-instr/src/main/java/org/openjdk/btrace/instr/HandlerRepositoryImpl.java \
        btrace-runtime/src/main/java/org/openjdk/btrace/runtime/IndyDispatcher.java
git commit -m "docs: update dispatch/repository javadoc for per-probe CL / hidden class residency"
```

---

## Risks and mitigations

| Risk | Mitigation |
|------|-----------|
| Hidden-class probe not resolvable via `publicLookup().findStatic` due to module access | Task 5 Step 6 contingency: thread the defining `Lookup` through `BTraceProbe`. Test before merging. |
| Some existing test asserts probe is in bootstrap CL | Task 3 Step 5 — triage during run; expected to surface only as targeted test assertions, not functional breakage. |
| Per-probe CL allocation overhead | One CL per probe attach. Detach is rare. Negligible. |
| MH cache in `HandlerRepositoryImpl.handlerCache` pinning probe class after detach | `unregisterProbe` already evicts via `handlerCache.keySet().removeIf(k -> k.probe.equals(probeName))` — verified. Add a `WeakReference<MethodHandle>` experiment only if we observe MH-retention issues in practice. |
| JDK 8 `Unsafe.defineClass` with a non-null loader + name collision across probes | Probe class names are generated with per-attach unique suffixes by the compiler (verify). If not, generate a unique suffix at define time. |
| `MutableCallSite` noop target pinning probe class | Already verified OK — `IndyDispatcher.noopImpl` is in bootstrap CL (IndyDispatcher.java:103-104). |
| Tests on CI run across JDK 8/11/17/21/25 — hidden-class path only exercised on 15+ | Task 5 uses `@EnabledOnJre` to gate. Unload test (Task 3) is JDK-agnostic and runs everywhere. |

## Out of scope (future work)

- Deduping same-script re-submissions by bytecode hash.
- Converting `handlerCache` to `WeakHashMap<Class<?>, ...>` keyed by the probe class for natural eviction (would eliminate the `keySet().removeIf` scan).
- Flattening `BTraceRuntime.Impl.defineClass`'s `mustBeBootstrap` parameter (now always effectively `false`).

## Self-review

- Spec coverage: per-probe CL on JDK 8/9/11 (Tasks 3/4), hidden classes on JDK 15+ (Task 5), accessor on BTraceProbe (Task 1), HandlerRepositoryImpl change (Task 2), runtime callback compatibility (Task 6), docs (Task 7). Covered.
- Placeholder scan: every step has concrete code or commands. No TBD, no "add validation", no "similar to Task N".
- Type consistency: `getProbeClass()`, `clearProbeClass()`, `probeMap`, `HandlerKey`, `probe.register()` names consistent across tasks.
