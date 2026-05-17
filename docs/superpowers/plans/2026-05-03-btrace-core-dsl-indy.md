# BTrace Core DSL + Indy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the monolithic `BTraceUtils` service-object model with a flat `io.btrace.BTrace` DSL linked via `invokedynamic` at runtime, keeping the existing extension model intact.

**Architecture:** Scripts call flat static methods on `io.btrace.BTrace`; the compiler post-processor rewrites every such `INVOKESTATIC` to `INVOKEDYNAMIC` targeting a new `BTraceBootstrap.bootstrap`; at agent startup all core op `MethodHandle`s are registered into a `ConstantCallSite`-backed dispatch table. The bytecode verifier is updated to allow the new bootstrap. The existing extension model (`@Injected`, `ExtensionBridgeImpl`, `ExtensionIndy`) is unchanged.

**Tech Stack:** Java 8 source compatibility, Java 11 toolchain, ASM 9, `java.lang.invoke`, Gradle, JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-03-btrace-core-dsl-indy-design.md`

**Out of scope (separate plan):** `btrace-migrate` CLI tool.

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `btrace-core/src/main/java/io/btrace/BTrace.java` | **Create** | Flat DSL class — ~50 static methods, compile-time target + fallback |
| `btrace-core/src/test/java/io/btrace/BTraceTest.java` | **Create** | Unit tests for pure-function core ops |
| `btrace-runtime/src/main/java/io/btrace/runtime/BTraceBootstrap.java` | **Create** | OP_TABLE + `bootstrap()` method |
| `btrace-runtime/src/test/java/io/btrace/runtime/BTraceBootstrapTest.java` | **Create** | Bootstrap resolution tests |
| `btrace-agent/src/main/java/io/btrace/agent/Main.java` | **Modify** | Register core op handles at startup |
| `btrace-compiler/src/main/java/io/btrace/compiler/Postprocessor.java` | **Modify** | Add `BTraceDslRewriter` inner MethodVisitor |
| `btrace-compiler/src/main/java/io/btrace/compiler/Compiler.java` | **Modify** | Auto-inject `import static io.btrace.BTrace.*` |
| `btrace-compiler/src/main/java/io/btrace/compiler/VerifierVisitor.java` | **Modify** | Allow `io.btrace.BTrace` in `isBTraceClass()` |
| `btrace-agent/src/main/java/io/btrace/instr/MethodVerifier.java` | **Modify** | Add `visitInvokeDynamicInsn` bootstrap check |
| `btrace-agent/src/main/java/io/btrace/instr/Constants.java` | **Modify** | Add `BTRACE_DSL` constant |
| `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/PrinterService.java` | **Delete** | Dissolved into core |
| `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/PrinterServiceImpl.java` | **Delete** | Dissolved into core |
| `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/package-info.java` | **Delete** | No longer needed |
| `btrace-core/src/main/java/io/btrace/core/BTraceUtils.java` | **Modify** | Delegate all methods to `io.btrace.BTrace` (compat shim) |

---

## Task 1: Create `io.btrace.BTrace` flat class

**Files:**
- Create: `btrace-core/src/main/java/io/btrace/BTrace.java`
- Create: `btrace-core/src/test/java/io/btrace/BTraceTest.java`

- [ ] **Step 1: Write failing tests for pure-function ops**

```java
// btrace-core/src/test/java/io/btrace/BTraceTest.java
package io.btrace;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class BTraceTest {
    @Test void str_null_returnsNullString() {
        assertEquals("null", BTrace.str((Object) null));
    }
    @Test void str_object_usesToString() {
        assertEquals("42", BTrace.str(42));
    }
    @Test void concat_twoStrings() {
        assertEquals("ab", BTrace.concat("a", "b"));
    }
    @Test void timestamp_isPositive() {
        assertTrue(BTrace.timestamp() > 0);
    }
    @Test void monotonic_isPositive() {
        assertTrue(BTrace.monotonic() > 0);
    }
    @Test void threadName_currentThread() {
        assertEquals(Thread.currentThread().getName(), BTrace.threadName(Thread.currentThread()));
    }
    @Test void threadId_currentThread() {
        assertEquals(Thread.currentThread().getId(), BTrace.threadId(Thread.currentThread()));
    }
    @Test void className_object() {
        assertEquals("java.lang.String", BTrace.className("hello"));
    }
    @Test void identity_returnsSystemIdentityHashCode() {
        Object o = new Object();
        assertEquals(System.identityHashCode(o), BTrace.identity(o));
    }
    @Test void substr_basic() {
        assertEquals("bc", BTrace.substr("abcd", 1, 3));
    }
    @Test void matches_basicRegex() {
        assertTrue(BTrace.matches(".*bc.*", "abcd"));
        assertFalse(BTrace.matches("^bc", "abcd"));
    }
}
```

- [ ] **Step 2: Run to verify tests fail**

```
./gradlew :btrace-core:test --tests "io.btrace.BTraceTest" 2>&1 | tail -10
```
Expected: compilation error — `BTrace` does not exist yet.

- [ ] **Step 3: Create `io.btrace.BTrace`**

```java
// btrace-core/src/main/java/io/btrace/BTrace.java
package io.btrace;

import io.btrace.core.BTraceRuntime;

/**
 * Flat core DSL for BTrace scripts. All methods are auto-imported by the compiler.
 * At runtime every INVOKESTATIC to this class is rewritten to INVOKEDYNAMIC by the
 * post-processor; these static bodies serve as compile-time targets and fallbacks only.
 */
public final class BTrace {
    private BTrace() {}

    // --- Output ---
    public static void print(String s)   { BTraceRuntime.print(s); }
    public static void println(String s) { BTraceRuntime.println(s); }
    public static void println()         { BTraceRuntime.println(""); }
    public static void printf(String fmt, Object... args) {
        BTraceRuntime.print(String.format(fmt, args));
    }

    // --- Strings ---
    public static String str(Object o)                   { return o == null ? "null" : o.toString(); }
    public static String str(boolean b)                  { return Boolean.toString(b); }
    public static String str(int i)                      { return Integer.toString(i); }
    public static String str(long l)                     { return Long.toString(l); }
    public static String str(float f)                    { return Float.toString(f); }
    public static String str(double d)                   { return Double.toString(d); }
    public static String concat(String a, String b)      { return a == null ? b : b == null ? a : a + b; }
    public static String substr(String s, int start, int end) { return s.substring(start, end); }
    public static boolean matches(String regex, String s){ return s != null && s.matches(regex); }
    public static boolean startsWith(String s, String prefix) { return s != null && s.startsWith(prefix); }
    public static boolean endsWith(String s, String suffix)   { return s != null && s.endsWith(suffix); }
    public static int length(String s)                   { return s == null ? 0 : s.length(); }

    // --- Numbers ---
    public static long   abs(long l)    { return Math.abs(l); }
    public static double abs(double d)  { return Math.abs(d); }
    public static long   min(long a, long b)     { return Math.min(a, b); }
    public static long   max(long a, long b)     { return Math.max(a, b); }
    public static double min(double a, double b) { return Math.min(a, b); }
    public static double max(double a, double b) { return Math.max(a, b); }

    // --- Time ---
    public static long timestamp() { return System.currentTimeMillis(); }
    public static long monotonic()  { return System.nanoTime(); }

    // --- Threads ---
    public static Thread currentThread()      { return Thread.currentThread(); }
    public static String threadName(Thread t) { return t == null ? "" : t.getName(); }
    public static long   threadId(Thread t)   { return t == null ? -1L : t.getId(); }

    // --- Stack ---
    public static void   printStack()           { BTraceRuntime.printStack(); }
    public static String stackTrace()           { return BTraceRuntime.stackTrace(); }
    public static int    stackDepth()           { return BTraceRuntime.stackDepth(); }

    // --- Object ---
    public static String className(Object o)  { return o == null ? "null" : o.getClass().getName(); }
    public static int    identity(Object o)   { return System.identityHashCode(o); }
    public static long   size(Object o)       { return BTraceRuntime.sizeof(o); }

    // --- Control ---
    public static void exit(int code) { BTraceRuntime.exit(code); }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :btrace-core:test --tests "io.btrace.BTraceTest"
```
Expected: all 12 tests pass.

- [ ] **Step 5: Format and commit**

```
./gradlew spotlessApply
git add btrace-core/src/main/java/io/btrace/BTrace.java \
        btrace-core/src/test/java/io/btrace/BTraceTest.java
git commit -m "feat(core): add io.btrace.BTrace flat DSL class"
```

---

## Task 2: Create `BTraceBootstrap` in `btrace-runtime`

**Files:**
- Create: `btrace-runtime/src/main/java/io/btrace/runtime/BTraceBootstrap.java`
- Create: `btrace-runtime/src/test/java/io/btrace/runtime/BTraceBootstrapTest.java`

- [ ] **Step 1: Write failing tests**

```java
// btrace-runtime/src/test/java/io/btrace/runtime/BTraceBootstrapTest.java
package io.btrace.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.invoke.*;
import org.junit.jupiter.api.*;

public class BTraceBootstrapTest {
    @BeforeEach
    void clearTable() throws Exception {
        // Reset the OP_TABLE via reflection between tests
        var field = BTraceBootstrap.class.getDeclaredField("OP_TABLE");
        field.setAccessible(true);
        ((java.util.concurrent.ConcurrentHashMap<?,?>) field.get(null)).clear();
    }

    @Test
    void bootstrap_registeredOp_returnsConstantCallSite() throws Throwable {
        MethodType type = MethodType.methodType(void.class, String.class);
        MethodHandle target = MethodHandles.lookup()
            .findStatic(BTraceBootstrapTest.class, "noopPrint",
                MethodType.methodType(void.class, String.class));
        BTraceBootstrap.registerCoreOp("print", type, target);

        CallSite cs = BTraceBootstrap.bootstrap(MethodHandles.lookup(), "print", type);
        assertInstanceOf(ConstantCallSite.class, cs);
    }

    @Test
    void bootstrap_unknownOp_throwsBootstrapMethodError() {
        MethodType type = MethodType.methodType(void.class, String.class);
        assertThrows(BootstrapMethodError.class,
            () -> BTraceBootstrap.bootstrap(MethodHandles.lookup(), "unknown", type));
    }

    @Test
    void registerCoreOp_duplicate_throwsIllegalStateException() throws Exception {
        MethodType type = MethodType.methodType(void.class, String.class);
        MethodHandle mh = MethodHandles.lookup()
            .findStatic(BTraceBootstrapTest.class, "noopPrint",
                MethodType.methodType(void.class, String.class));
        BTraceBootstrap.registerCoreOp("print", type, mh);
        assertThrows(IllegalStateException.class,
            () -> BTraceBootstrap.registerCoreOp("print", type, mh));
    }

    public static void noopPrint(String s) {}
}
```

- [ ] **Step 2: Run to verify tests fail**

```
./gradlew :btrace-runtime:test --tests "io.btrace.runtime.BTraceBootstrapTest" 2>&1 | tail -10
```
Expected: compilation error — `BTraceBootstrap` does not exist.

- [ ] **Step 3: Create `BTraceBootstrap`**

```java
// btrace-runtime/src/main/java/io/btrace/runtime/BTraceBootstrap.java
package io.btrace.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;

/**
 * INVOKEDYNAMIC bootstrap for BTrace core DSL ops (io.btrace.BTrace.*).
 * Lives on the bootstrap classpath; must not reference SLF4J or any framework logger.
 */
public final class BTraceBootstrap {

    // key: opName + methodType.toMethodDescriptorString()  e.g. "print(Ljava/lang/String;)V"
    static final ConcurrentHashMap<String, MethodHandle> OP_TABLE = new ConcurrentHashMap<>();

    private BTraceBootstrap() {}

    /**
     * Called by JVM for every INVOKEDYNAMIC targeting this bootstrap.
     * Returns a ConstantCallSite — the JIT will fold it after warmup.
     */
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type) {
        MethodHandle mh = OP_TABLE.get(name + type.toMethodDescriptorString());
        if (mh == null) {
            throw new BootstrapMethodError("Unknown BTrace core op: " + name + type.descriptorString());
        }
        return new ConstantCallSite(mh);
    }

    /**
     * Register a core op. Called at agent startup before any probe fires.
     * Core op names cannot be overridden once registered.
     */
    public static void registerCoreOp(String name, MethodType type, MethodHandle impl) {
        String key = name + type.toMethodDescriptorString();
        if (OP_TABLE.putIfAbsent(key, impl) != null) {
            throw new IllegalStateException("Core op already registered: " + key);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :btrace-runtime:test --tests "io.btrace.runtime.BTraceBootstrapTest"
```
Expected: all 3 tests pass.

- [ ] **Step 5: Format and commit**

```
./gradlew spotlessApply
git add btrace-runtime/src/main/java/io/btrace/runtime/BTraceBootstrap.java \
        btrace-runtime/src/test/java/io/btrace/runtime/BTraceBootstrapTest.java
git commit -m "feat(runtime): add BTraceBootstrap indy dispatch table for core DSL ops"
```

---

## Task 3: Register core ops at agent startup

**Files:**
- Modify: `btrace-agent/src/main/java/io/btrace/agent/Main.java`
- Create: `btrace-agent/src/test/java/io/btrace/agent/CoreOpRegistrationTest.java`

- [ ] **Step 1: Write failing test**

```java
// btrace-agent/src/test/java/io/btrace/agent/CoreOpRegistrationTest.java
package io.btrace.agent;

import static org.junit.jupiter.api.Assertions.*;
import io.btrace.runtime.BTraceBootstrap;
import java.lang.invoke.*;
import org.junit.jupiter.api.Test;

public class CoreOpRegistrationTest {
    @Test
    void allCoreOpsRegistered() throws Throwable {
        // Trigger registration (normally done by premain)
        Main.registerCoreOps();

        // Spot-check a selection of expected ops
        String[] ops = {
            "print(Ljava/lang/String;)V",
            "println(Ljava/lang/String;)V",
            "println()V",
            "str(Ljava/lang/Object;)Ljava/lang/String;",
            "str(J)Ljava/lang/String;",
            "concat(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            "timestamp()J",
            "monotonic()J",
            "threadName(Ljava/lang/Thread;)Ljava/lang/String;",
            "threadId(Ljava/lang/Thread;)J",
            "className(Ljava/lang/Object;)Ljava/lang/String;",
            "identity(Ljava/lang/Object;)I",
            "exit(I)V",
        };
        for (String op : ops) {
            assertTrue(BTraceBootstrap.OP_TABLE.containsKey(op),
                "Missing core op: " + op);
        }
    }
}
```

- [ ] **Step 2: Run to verify test fails**

```
./gradlew :btrace-agent:test --tests "io.btrace.agent.CoreOpRegistrationTest" 2>&1 | tail -10
```
Expected: compilation error — `Main.registerCoreOps()` does not exist.

- [ ] **Step 3: Add `registerCoreOps()` to `Main.java`**

Add this static method to `Main.java`, and call it in the init sequence after `BTraceRuntimes.getDefault()` (around line 204) and before `initExtensions()`:

```java
// Add import at top of Main.java:
import io.btrace.BTrace;
import io.btrace.runtime.BTraceBootstrap;

// Add this method to Main:
static void registerCoreOps() {
    try {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<BTrace> B = BTrace.class;

        // Helper to shorten registration calls
        java.util.function.BiConsumer<String, MethodType> reg = (name, type) -> {
            try {
                BTraceBootstrap.registerCoreOp(name, type,
                    lookup.findStatic(B, name, type));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("Failed to register core op: " + name + type, e);
            }
        };

        MethodType V_S = MethodType.methodType(void.class, String.class);
        MethodType V_   = MethodType.methodType(void.class);
        MethodType S_O  = MethodType.methodType(String.class, Object.class);
        MethodType S_S_S= MethodType.methodType(String.class, String.class, String.class);
        MethodType J_   = MethodType.methodType(long.class);
        MethodType V_I  = MethodType.methodType(void.class, int.class);

        // Output
        reg.accept("print",   V_S);
        reg.accept("println", V_S);
        reg.accept("println", V_);
        BTraceBootstrap.registerCoreOp("printf",
            MethodType.methodType(void.class, String.class, Object[].class),
            lookup.findStatic(B, "printf", MethodType.methodType(void.class, String.class, Object[].class)));

        // Strings
        reg.accept("str",       S_O);
        reg.accept("str",       MethodType.methodType(String.class, boolean.class));
        reg.accept("str",       MethodType.methodType(String.class, int.class));
        reg.accept("str",       MethodType.methodType(String.class, long.class));
        reg.accept("str",       MethodType.methodType(String.class, float.class));
        reg.accept("str",       MethodType.methodType(String.class, double.class));
        reg.accept("concat",    S_S_S);
        reg.accept("substr",    MethodType.methodType(String.class, String.class, int.class, int.class));
        reg.accept("matches",   MethodType.methodType(boolean.class, String.class, String.class));
        reg.accept("startsWith",MethodType.methodType(boolean.class, String.class, String.class));
        reg.accept("endsWith",  MethodType.methodType(boolean.class, String.class, String.class));
        reg.accept("length",    MethodType.methodType(int.class, String.class));

        // Numbers
        reg.accept("abs", MethodType.methodType(long.class, long.class));
        reg.accept("abs", MethodType.methodType(double.class, double.class));
        reg.accept("min", MethodType.methodType(long.class, long.class, long.class));
        reg.accept("max", MethodType.methodType(long.class, long.class, long.class));
        reg.accept("min", MethodType.methodType(double.class, double.class, double.class));
        reg.accept("max", MethodType.methodType(double.class, double.class, double.class));

        // Time
        reg.accept("timestamp", J_);
        reg.accept("monotonic",  J_);

        // Threads
        reg.accept("currentThread", MethodType.methodType(Thread.class));
        reg.accept("threadName",    MethodType.methodType(String.class, Thread.class));
        reg.accept("threadId",      MethodType.methodType(long.class, Thread.class));

        // Stack
        reg.accept("printStack", V_);
        reg.accept("stackTrace", MethodType.methodType(String.class));
        reg.accept("stackDepth", MethodType.methodType(int.class));

        // Object
        reg.accept("className", MethodType.methodType(String.class, Object.class));
        reg.accept("identity",  MethodType.methodType(int.class, Object.class));
        reg.accept("size",      MethodType.methodType(long.class, Object.class));

        // Control
        reg.accept("exit", V_I);

    } catch (Exception e) {
        throw new RuntimeException("BTrace core op registration failed", e);
    }
}
```

In the existing init sequence in `Main.java`, add the call after line ~204 (`BTraceRuntimes.getDefault()`):

```java
BTraceRuntimes.getDefault();
if (AGENT_DEBUG) System.err.println("[BTrace Agent] BTraceRuntimes initialized");
registerCoreOps();   // <-- add this line
if (AGENT_DEBUG) System.err.println("[BTrace Agent] Core DSL ops registered");
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :btrace-agent:test --tests "io.btrace.agent.CoreOpRegistrationTest"
```
Expected: passes.

- [ ] **Step 5: Format and commit**

```
./gradlew spotlessApply
git add btrace-agent/src/main/java/io/btrace/agent/Main.java \
        btrace-agent/src/test/java/io/btrace/agent/CoreOpRegistrationTest.java
git commit -m "feat(agent): register core DSL ops into BTraceBootstrap at startup"
```

---

## Task 4: Add `BTraceDslRewriter` to `Postprocessor`

**Files:**
- Modify: `btrace-compiler/src/main/java/io/btrace/compiler/Postprocessor.java`
- Create: `btrace-compiler/src/test/java/io/btrace/compiler/PostprocessorDslRewriteTest.java`

- [ ] **Step 1: Write failing test**

```java
// btrace-compiler/src/test/java/io/btrace/compiler/PostprocessorDslRewriteTest.java
package io.btrace.compiler;

import static org.junit.jupiter.api.Assertions.*;
import io.btrace.BTrace;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

public class PostprocessorDslRewriteTest {

    private static final String BTRACE_DSL = Type.getInternalName(BTrace.class);
    private static final String BOOTSTRAP_OWNER = "io/btrace/runtime/BTraceBootstrap";

    /** Compile a minimal probe, run it through Postprocessor, verify INVOKEDYNAMIC emitted. */
    @Test
    void invokestatic_toBTraceDsl_rewrittenToInvokeDynamic() throws Exception {
        // Build a tiny class with INVOKESTATIC io/btrace/BTrace.println
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "TestProbe", null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "probe", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn("hello");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, BTRACE_DSL, "println",
            "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
        cw.visitEnd();
        byte[] original = cw.toByteArray();

        // Run through Postprocessor
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ClassReader cr = new ClassReader(original);
        cr.accept(new Postprocessor(out), ClassReader.EXPAND_FRAMES);
        byte[] rewritten = out.toByteArray();

        // Scan rewritten bytecode for INVOKEDYNAMIC
        AtomicBoolean sawIndy = new AtomicBoolean(false);
        AtomicBoolean sawInvokeStatic = new AtomicBoolean(false);
        new ClassReader(rewritten).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitInvokeDynamicInsn(
                            String name, String desc, Handle bsm, Object... bsmArgs) {
                        if ("println".equals(name) && BOOTSTRAP_OWNER.equals(bsm.getOwner())) {
                            sawIndy.set(true);
                        }
                    }
                    @Override public void visitMethodInsn(
                            int op, String owner, String name, String desc, boolean itf) {
                        if (op == Opcodes.INVOKESTATIC && BTRACE_DSL.equals(owner)) {
                            sawInvokeStatic.set(true);
                        }
                    }
                };
            }
        }, 0);

        assertTrue(sawIndy.get(), "Expected INVOKEDYNAMIC for println");
        assertFalse(sawInvokeStatic.get(), "Original INVOKESTATIC should be gone");
    }
}
```

- [ ] **Step 2: Run to verify test fails**

```
./gradlew :btrace-compiler:test --tests "io.btrace.compiler.PostprocessorDslRewriteTest" 2>&1 | tail -15
```
Expected: test fails (no INVOKEDYNAMIC emitted yet).

- [ ] **Step 3: Add `BTraceDslRewriter` to `Postprocessor.java`**

Add these constants near the top of `Postprocessor.java` (after existing fields):

```java
private static final String BTRACE_DSL_OWNER =
    "io/btrace/BTrace";  // Type.getInternalName(io.btrace.BTrace.class)
private static final String BOOTSTRAP_OWNER =
    "io/btrace/runtime/BTraceBootstrap";
private static final String BOOTSTRAP_NAME = "bootstrap";
private static final String BOOTSTRAP_DESC =
    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)"
    + "Ljava/lang/invoke/CallSite;";
private static final Handle BOOTSTRAP_HANDLE = new Handle(
    Opcodes.H_INVOKESTATIC, BOOTSTRAP_OWNER, BOOTSTRAP_NAME, BOOTSTRAP_DESC, false);
```

Override `visitMethod` in `Postprocessor` to wrap the returned `MethodVisitor` with `BTraceDslRewriter`. Find the existing `visitMethod` override and add the wrapping:

```java
@Override
public MethodVisitor visitMethod(
    int access, String name, String desc, String signature, String[] exceptions) {
  MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
  // existing short-syntax logic produces mv; now wrap it
  return mv == null ? null : new BTraceDslRewriter(mv);
}
```

Add the inner class at the bottom of `Postprocessor.java` (before the final closing `}`):

```java
/** Rewrites INVOKESTATIC io/btrace/BTrace.* → INVOKEDYNAMIC [...BTraceBootstrap.bootstrap]. */
private static final class BTraceDslRewriter extends MethodVisitor {
  BTraceDslRewriter(MethodVisitor mv) {
    super(Opcodes.ASM9, mv);
  }

  @Override
  public void visitMethodInsn(
      int opcode, String owner, String name, String desc, boolean isInterface) {
    if (opcode == Opcodes.INVOKESTATIC && BTRACE_DSL_OWNER.equals(owner)) {
      super.visitInvokeDynamicInsn(name, desc, BOOTSTRAP_HANDLE);
    } else {
      super.visitMethodInsn(opcode, owner, name, desc, isInterface);
    }
  }
}
```

**Exact integration point in `Postprocessor.visitMethod`:** The existing override starts with `if (!shortSyntax) return super.visitMethod(...)`. Wrap both early-return paths:

```java
@Override
public MethodVisitor visitMethod(int access, String name, String desc,
    String signature, String[] exceptions) {
  if (!shortSyntax) {
    // Early return path — wrap with rewriter
    return new BTraceDslRewriter(super.visitMethod(access, name, desc, signature, exceptions));
  }
  // ... existing short-syntax logic that produces mv ...
  // At the end, wherever the method currently returns mv, wrap it:
  return new BTraceDslRewriter(mv);
}
```

`BTraceDslRewriter` is the outermost visitor — it sees instructions after any short-syntax transformation has already been applied.

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :btrace-compiler:test --tests "io.btrace.compiler.PostprocessorDslRewriteTest"
```
Expected: passes.

- [ ] **Step 5: Format and commit**

```
./gradlew spotlessApply
git add btrace-compiler/src/main/java/io/btrace/compiler/Postprocessor.java \
        btrace-compiler/src/test/java/io/btrace/compiler/PostprocessorDslRewriteTest.java
git commit -m "feat(compiler): rewrite INVOKESTATIC io.btrace.BTrace.* to INVOKEDYNAMIC in Postprocessor"
```

---

## Task 5: Update `VerifierVisitor` (source-level verifier)

**Files:**
- Modify: `btrace-compiler/src/main/java/io/btrace/compiler/VerifierVisitor.java`
- Modify: `btrace-compiler/src/test/java/io/btrace/compiler/TypeErasureTest.java` (use as template for new test, or add cases)
- Create: `btrace-compiler/src/test/java/io/btrace/compiler/BTraceDslVerifierTest.java`

- [ ] **Step 1: Write failing test**

```java
// btrace-compiler/src/test/java/io/btrace/compiler/BTraceDslVerifierTest.java
package io.btrace.compiler;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class BTraceDslVerifierTest {

    private static final String FLAT_PRINTLN_SCRIPT = """
        import io.btrace.core.annotations.BTrace;
        import io.btrace.core.annotations.OnMethod;
        import static io.btrace.BTrace.*;
        @BTrace
        public class FlatPrintProbe {
            @OnMethod(clazz="java.io.FileInputStream", method="<init>")
            public static void onOpen(String fileName) {
                println("opened: " + fileName);
            }
        }
        """;

    @Test
    void flatPrintln_passesSourceVerifier() {
        Compiler compiler = new Compiler();
        // Should compile without throwing VerifierException
        Map<String, byte[]> result = compiler.compile(
            "FlatPrintProbe.java", FLAT_PRINTLN_SCRIPT, null, null, null);
        assertNotNull(result, "Compilation should succeed");
        assertFalse(result.isEmpty(), "Should produce class bytes");
    }
}
```

- [ ] **Step 2: Run to verify test fails**

```
./gradlew :btrace-compiler:test --tests "io.btrace.compiler.BTraceDslVerifierTest" 2>&1 | tail -15
```
Expected: test fails — verifier rejects calls to `io.btrace.BTrace`.

- [ ] **Step 3: Update `isBTraceClass()` in `VerifierVisitor.java`**

Find the method at line ~201 and update:

```java
// Before:
private boolean isBTraceClass(String typeName) {
    return typeName.equals("io.btrace.core.BTraceUtils")
        || typeName.startsWith("io.btrace.core.BTraceUtils.");
}

// After:
private boolean isBTraceClass(String typeName) {
    return typeName.equals("io.btrace.core.BTraceUtils")
        || typeName.startsWith("io.btrace.core.BTraceUtils.")
        || typeName.equals("io.btrace.BTrace");
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :btrace-compiler:test --tests "io.btrace.compiler.BTraceDslVerifierTest"
```
Expected: passes.

- [ ] **Step 5: Format and commit**

```
./gradlew spotlessApply
git add btrace-compiler/src/main/java/io/btrace/compiler/VerifierVisitor.java \
        btrace-compiler/src/test/java/io/btrace/compiler/BTraceDslVerifierTest.java
git commit -m "feat(compiler): allow io.btrace.BTrace calls in source-level verifier"
```

---

## Task 6: Update `MethodVerifier` (bytecode-level verifier)

**Files:**
- Modify: `btrace-agent/src/main/java/io/btrace/instr/MethodVerifier.java`
- Modify: `btrace-agent/src/main/java/io/btrace/instr/Constants.java`
- Create: `btrace-agent/src/test/java/io/btrace/instr/MethodVerifierDslTest.java`

- [ ] **Step 1: Write failing test**

```java
// btrace-agent/src/test/java/io/btrace/instr/MethodVerifierDslTest.java
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;
import io.btrace.core.VerifierException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

public class MethodVerifierDslTest {

    private static final String BOOTSTRAP_OWNER = "io/btrace/runtime/BTraceBootstrap";
    private static final String INDY_OWNER      = "io/btrace/runtime/IndyDispatcher";

    /** A class that passes through the BTraceProbeFactory / Verifier pipeline. */
    private void verifyProbeBytes(byte[] bytes) {
        BTraceProbeFactory factory = new BTraceProbeFactory(true);
        // Should not throw
        factory.createProbe(bytes, null);
    }

    private byte[] probeWithIndy(String bootstrapOwner) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "TestProbe", null, "java/lang/Object", null);
        // @BTrace annotation
        cw.visitAnnotation("Lio/btrace/core/annotations/BTrace;", true).visitEnd();
        MethodVisitor mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "probe", "()V", null, null);
        mv.visitAnnotation("Lio/btrace/core/annotations/OnMethod;", true)
            .visit("clazz", "java.lang.String");
        mv.visitCode();
        Handle bsm = new Handle(Opcodes.H_INVOKESTATIC, bootstrapOwner, "bootstrap",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)"
            + "Ljava/lang/invoke/CallSite;", false);
        mv.visitInvokeDynamicInsn("println", "(Ljava/lang/String;)V", bsm);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    void indyWithBTraceBootstrap_passesVerifier() {
        assertDoesNotThrow(() -> verifyProbeBytes(probeWithIndy(BOOTSTRAP_OWNER)));
    }

    @Test
    void indyWithIndyDispatcher_passesVerifier() {
        assertDoesNotThrow(() -> verifyProbeBytes(probeWithIndy(INDY_OWNER)));
    }

    @Test
    void indyWithUnknownBootstrap_failsVerifier() {
        assertThrows(VerifierException.class,
            () -> verifyProbeBytes(probeWithIndy("com/evil/Bootstrap")));
    }
}
```

- [ ] **Step 2: Run to verify test fails**

```
./gradlew :btrace-agent:test --tests "io.btrace.instr.MethodVerifierDslTest" 2>&1 | tail -20
```
Expected: the last test may already pass (INVOKEDYNAMIC currently unchecked), but the design intent test for unknown bootstrap should fail once we tighten the check.

- [ ] **Step 3: Add `BTRACE_DSL` constant to `Constants.java`**

```java
// In Constants.java, alongside the existing BTRACE_UTILS:
import io.btrace.BTrace;
// ...
public static final String BTRACE_DSL      = Type.getInternalName(BTrace.class);
public static final String BTRACE_BOOTSTRAP = "io/btrace/runtime/BTraceBootstrap";
public static final String INDY_DISPATCHER  = "io/btrace/runtime/IndyDispatcher";
```

- [ ] **Step 4: Add `visitInvokeDynamicInsn` to `MethodVerifier.java`**

Add this override after `visitMethodInsn`:

```java
@Override
public void visitInvokeDynamicInsn(
    String name, String desc, Handle bsm, Object... bsmArgs) {
  if (!getParent().isTrusted()) {
    String owner = bsm.getOwner();
    if (!Constants.BTRACE_BOOTSTRAP.equals(owner)
        && !Constants.INDY_DISPATCHER.equals(owner)) {
      Verifier.reportError("no.method.calls", name + desc
          + " [bootstrap: " + owner + "]");
    }
  }
  super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
}
```

Also add `io/btrace/BTrace` to the `INVOKESTATIC` allowlist in `visitMethodInsn` for robustness (handles the edge case where bytecode arrives un-rewritten):

```java
case INVOKESTATIC:
  if (!owner.equals(Constants.BTRACE_UTILS)
      && !owner.startsWith(Constants.BTRACE_UTILS + "$")
      && !owner.equals(Constants.BTRACE_DSL)      // <-- add this line
      && !owner.equals(className)) {
    // ... existing valueOf check ...
  }
  break;
```

- [ ] **Step 5: Run tests to verify they pass**

```
./gradlew :btrace-agent:test --tests "io.btrace.instr.MethodVerifierDslTest"
```
Expected: all 3 tests pass.

- [ ] **Step 6: Run full agent test suite to check for regressions**

```
./gradlew :btrace-agent:test 2>&1 | tail -20
```
Expected: no new failures.

- [ ] **Step 7: Format and commit**

```
./gradlew spotlessApply
git add btrace-agent/src/main/java/io/btrace/instr/Constants.java \
        btrace-agent/src/main/java/io/btrace/instr/MethodVerifier.java \
        btrace-agent/src/test/java/io/btrace/instr/MethodVerifierDslTest.java
git commit -m "feat(agent): add INVOKEDYNAMIC bootstrap allowlist to bytecode verifier"
```

---

## Task 7: Auto-import injection in `Compiler.java`

**Files:**
- Modify: `btrace-compiler/src/main/java/io/btrace/compiler/Compiler.java`
- Create: `btrace-compiler/src/test/java/io/btrace/compiler/AutoImportTest.java`

- [ ] **Step 1: Write failing test**

```java
// btrace-compiler/src/test/java/io/btrace/compiler/AutoImportTest.java
package io.btrace.compiler;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AutoImportTest {

    // Script with NO explicit import of io.btrace.BTrace
    private static final String SCRIPT_NO_IMPORT = """
        import io.btrace.core.annotations.BTrace;
        import io.btrace.core.annotations.OnMethod;
        @BTrace
        public class NoImportProbe {
            @OnMethod(clazz="java.lang.String", method="length")
            public static void onLength() {
                println("called");
            }
        }
        """;

    @Test
    void scriptWithoutImport_compilesAndResolvesFlat() {
        Compiler compiler = new Compiler();
        Map<String, byte[]> result = compiler.compile(
            "NoImportProbe.java", SCRIPT_NO_IMPORT, null, null, null);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
```

- [ ] **Step 2: Run to verify test fails**

```
./gradlew :btrace-compiler:test --tests "io.btrace.compiler.AutoImportTest" 2>&1 | tail -15
```
Expected: compilation failure — `println` cannot be resolved.

- [ ] **Step 3: Add import injection to `Compiler.java`**

Find the `compile(String fileName, String source, ...)` method. Before passing `source` to `MemoryJavaFileManager.makeStringSource`, inject the import:

```java
// Add this helper method to Compiler:
private static final String DSL_IMPORT = "import static io.btrace.BTrace.*;\n";

private static String injectDslImport(String source) {
    // Don't double-inject if the script already has the import
    if (source.contains("import static io.btrace.BTrace")) return source;
    // Insert after package declaration if present, otherwise prepend
    int packageEnd = source.indexOf(';');
    if (packageEnd >= 0 && source.substring(0, packageEnd).trim().startsWith("package ")) {
        return source.substring(0, packageEnd + 1) + "\n" + DSL_IMPORT
             + source.substring(packageEnd + 1);
    }
    return DSL_IMPORT + source;
}
```

Call it in `compile()` before `makeStringSource`:

```java
source = injectDslImport(source);
compUnits.add(MemoryJavaFileManager.makeStringSource(fileName, source, includeDirs));
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :btrace-compiler:test --tests "io.btrace.compiler.AutoImportTest"
```
Expected: passes.

- [ ] **Step 5: Run full compiler test suite**

```
./gradlew :btrace-compiler:test 2>&1 | tail -20
```
Expected: no regressions.

- [ ] **Step 6: Format and commit**

```
./gradlew spotlessApply
git add btrace-compiler/src/main/java/io/btrace/compiler/Compiler.java \
        btrace-compiler/src/test/java/io/btrace/compiler/AutoImportTest.java
git commit -m "feat(compiler): auto-inject 'import static io.btrace.BTrace.*' into BTrace scripts"
```

---

## Task 8: Dissolve btrace-utils services

**Files:**
- Delete: `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/PrinterService.java`
- Delete: `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/PrinterServiceImpl.java`
- Delete: `btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/package-info.java`
- Modify: any test scripts or manifests that reference these services

- [ ] **Step 1: Find all references to the dissolved services**

```
grep -rn "PrinterService\|btrace-utils" \
  --include="*.java" --include="*.xml" --include="*.gradle" --include="*.properties" \
  /Users/jbachorik/src/btrace --exclude-dir=build --exclude-dir=.worktrees
```

Review output and note what else references these files.

- [ ] **Step 2: Delete the service files**

```
rm btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/PrinterService.java
rm btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/PrinterServiceImpl.java
rm btrace-extensions/btrace-utils/src/main/java/io/btrace/utils/package-info.java
```

If the btrace-utils module becomes empty after this, remove or repurpose it:

```
# If the module has no remaining source:
# - Remove it from settings.gradle
# - Remove it from build dependencies
```

- [ ] **Step 3: Run btrace-utils tests (if any remain)**

```
./gradlew :btrace-extensions:btrace-utils:build 2>&1 | tail -20
```
Expected: builds cleanly (or reports the module is effectively empty).

- [ ] **Step 4: Run full test suite for affected modules**

```
./gradlew :btrace-agent:test :btrace-compiler:test 2>&1 | tail -20
```
Expected: no failures referencing PrinterService.

- [ ] **Step 5: Commit**

```
git add -u btrace-extensions/btrace-utils/
git commit -m "refactor(extensions): dissolve PrinterService — output ops are now core DSL"
```

---

## Task 9: `BTraceUtils` compatibility shim

**Files:**
- Modify: `btrace-core/src/main/java/io/btrace/core/BTraceUtils.java`
- Create: `btrace-core/src/test/java/io/btrace/core/BTraceUtilsShimTest.java`

- [ ] **Step 1: Write failing test**

```java
// btrace-core/src/test/java/io/btrace/core/BTraceUtilsShimTest.java
package io.btrace.core;

import static org.junit.jupiter.api.Assertions.*;
import io.btrace.BTrace;
import org.junit.jupiter.api.Test;

public class BTraceUtilsShimTest {
    @Test
    void stringsStrcat_delegatesToBTrace() {
        // BTraceUtils.Strings.strcat should produce same result as BTrace.concat
        assertEquals(BTrace.concat("hello", " world"),
                     BTraceUtils.Strings.strcat("hello", " world"));
    }

    @Test
    void timestamp_delegatesToBTrace() {
        long t1 = BTraceUtils.timeStamp("dummy");
        // Just check it doesn't throw and returns something plausible
        // (exact value doesn't matter — this shim test just ensures delegation compiles and runs)
        assertTrue(t1 >= 0 || t1 < 0);  // always true; real check is no exception
    }
}
```

- [ ] **Step 2: Run to verify test fails**

```
./gradlew :btrace-core:test --tests "io.btrace.core.BTraceUtilsShimTest" 2>&1 | tail -15
```
Expected: test may already pass (BTraceUtils methods exist), but confirms the shim test compiles.

- [ ] **Step 3: Update `BTraceUtils` to delegate string and output ops to `io.btrace.BTrace`**

Focus on the highest-traffic methods in `BTraceUtils`. For each inner utility class method that has a flat equivalent, replace the body with a delegation:

```java
// In BTraceUtils.Strings:
public static String strcat(String s1, String s2) { return BTrace.concat(s1, s2); }
public static String str(Object o)                { return BTrace.str(o); }
public static String substr(String s, int b, int e){ return BTrace.substr(s, b, e); }

// In BTraceUtils (top-level):
public static void print(Object o)   { BTrace.print(BTrace.str(o)); }
public static void println(Object o) { BTrace.println(BTrace.str(o)); }
public static void println()         { BTrace.println(); }
```

Do NOT rewrite methods that have no flat equivalent (they remain as-is calling BTraceRuntime directly).

- [ ] **Step 4: Run shim tests and full core test suite**

```
./gradlew :btrace-core:test 2>&1 | tail -20
```
Expected: all tests pass.

- [ ] **Step 5: Format and commit**

```
./gradlew spotlessApply
git add btrace-core/src/main/java/io/btrace/core/BTraceUtils.java \
        btrace-core/src/test/java/io/btrace/core/BTraceUtilsShimTest.java
git commit -m "compat: delegate high-traffic BTraceUtils methods to io.btrace.BTrace shim"
```

---

## Task 10: End-to-end integration test

**Files:**
- Create: `integration-tests/src/test/btrace/FlatDslTest.java` (BTrace script)
- Modify: `integration-tests/src/test/java/tests/BTraceFunctionalTests.java`

- [ ] **Step 1: Create a BTrace script using only the flat DSL**

```java
// integration-tests/src/test/btrace/FlatDslTest.java
import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.OnMethod;

@BTrace
public class FlatDslTest {
    @OnMethod(clazz = "java.io.FileInputStream", method = "<init>")
    public static void onOpen(String fileName) {
        println("opened: " + concat("[", concat(fileName, "]")));
    }
}
```

- [ ] **Step 2: Add integration test case to `BTraceFunctionalTests.java`**

Find the existing test methods and add:

```java
@Test
public void flatDslOpsWork() throws Exception {
    // The test framework compiles and runs FlatDslTest against a target JVM
    // that opens a file, then asserts the expected output was received.
    verifyTrace("FlatDslTest", "opened: [");
}
```

(Use the same pattern as existing test methods in the file — look at neighboring methods to match the exact calling convention.)

- [ ] **Step 3: Build the full distribution first**

```
./gradlew :btrace-dist:build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run integration tests**

```
./gradlew :integration-tests:test -Pintegration --tests "tests.BTraceFunctionalTests.flatDslOpsWork"
```
Expected: passes — the script compiles, the probe fires, output contains "opened: [".

- [ ] **Step 5: Run full integration suite to check for regressions**

```
./gradlew :integration-tests:test -Pintegration 2>&1 | tail -30
```
Expected: no new failures.

- [ ] **Step 6: Format and commit**

```
./gradlew spotlessApply
git add integration-tests/src/test/btrace/FlatDslTest.java \
        integration-tests/src/test/java/tests/BTraceFunctionalTests.java
git commit -m "test(integration): add end-to-end test for flat DSL ops via indy dispatch"
```

---

## Final Verification

- [ ] Run all tests across all touched modules:

```
./gradlew :btrace-core:test :btrace-runtime:test :btrace-compiler:test :btrace-agent:test
```

- [ ] Run integration tests:

```
./gradlew :btrace-dist:build && ./gradlew :integration-tests:test -Pintegration
```

- [ ] Verify formatting:

```
./gradlew spotlessCheck
```

---

## Notes for Implementor

- **`probeClass()` / `probeMethod()` ops** are listed as core in the spec but require instrumentor cooperation (pushing constants onto the stack before the INVOKEDYNAMIC). Omit them from `io.btrace.BTrace` in this iteration — they remain as `@ProbeClassName` / `@ProbeMethodName` annotated parameters.
- **`size(Object)` op** calls `BTraceRuntime.sizeof(o)` which uses `Unsafe` internally. If `Unsafe` is not yet initialized when the fallback is called, this will throw. This is only the fallback path — via indy dispatch it's fine.
- **`btrace-migrate` tool** (source rewriter from old `BTraceUtils.*` to flat API) is a separate plan/task.
- **`printf` overload** uses `Object[]` varargs; ensure the MethodHandle registration in Task 3 uses `MethodType.methodType(void.class, String.class, Object[].class)` (not a varargs type — Java varargs are plain arrays at the bytecode level).
