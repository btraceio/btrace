# ClassFile API CALL and TargetInstance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Java ClassFile API backend support for the currently skipped `Kind.CALL`, `@TargetInstance`, and `@TargetMethodOrField` cases needed for parity with the ASM backend on Java 26+ class files.

**Architecture:** Extend `ClassFileApiBackend` so it classifies handlers into method-entry, method-return, and method-call groups, then instruments `InvokeInstruction` elements in matching methods. Reuse the existing `emitProbeCall` invokedynamic path, but pass a small call-site context object that can satisfy call arguments, target receiver, target method name, return value, and duration without leaving partial stack state.

**Tech Stack:** Java 8 source style, JDK 24+ `java.lang.classfile`, ASM only in tests for bytecode fixture generation/inspection, JUnit Jupiter.

---

## Scope And Constraints

- Target file: `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`.
- Target tests: `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`.
- Keep `ENTRY` and `RETURN` behavior from PR #843 unchanged.
- Add support only for method call join points (`Kind.CALL`) and the special parameters needed there:
  - `@TargetInstance`: receiver object for non-static calls, `null` for static calls.
  - `@TargetMethodOrField`: called method name, using FQN when `om.isTargetMethodOrFieldFqn()` is true.
  - `@Return`: call return value for `Where.AFTER` and non-void called methods.
  - `@Duration`: call duration for `Where.AFTER`.
  - Ordinary handler parameters: called method arguments, in call descriptor order.
- Do not add support for field, array, cast, throw, catch, monitor, `NEW`, `NEWARRAY`, sampled/level bytecode guards, or type-constrained outer-method matching.
- Preserve the existing invariant: pre-validate every handler argument before pushing anything for the BTrace invokedynamic call.

## File Structure

- Modify `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`
  - Add call-handler collection and matching.
  - Add call-site context helpers for stack backup/restore and probe argument loading.
  - Generalize `emitProbeCall` so return/method-entry probes and call probes share the invokedynamic emission.
- Modify `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`
  - Add helpers to build classfile-version-70 classes containing method invocations.
  - Add bytecode inspection helpers for invokedynamic descriptors and local-slot behavior.
  - Add tests for before/after call, receiver capture, static-call null receiver, target method name, return value, duration, and no-match behavior.

---

### Task 1: Add Failing Tests For Basic `Kind.CALL` Injection

**Files:**
- Modify: `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`

- [ ] **Step 1: Add an overloaded stub-probe helper with `Location`**

Add this helper next to the existing `buildStubProbe(...)` overloads:

```java
  private static BTraceProbe buildStubProbe(
      final String probeInternalName,
      final String targetJavaClass,
      final String targetMethod,
      final Location location,
      final String targetDescriptor) {
    return buildStubProbe(
        probeInternalName, targetJavaClass, targetMethod, location, targetDescriptor, -1, -1, -1, -1);
  }

  private static BTraceProbe buildStubProbe(
      final String probeInternalName,
      final String targetJavaClass,
      final String targetMethod,
      final Location location,
      final String targetDescriptor,
      final int returnParameter,
      final int durationParameter,
      final int targetInstanceParameter,
      final int targetMethodOrFieldParameter) {

    final OnMethod om = new OnMethod();
    om.setClazz(targetJavaClass);
    om.setMethod(targetMethod);
    om.setLocation(location);
    om.setTargetName("onProbe");
    om.setTargetDescriptor(targetDescriptor);
    if (returnParameter != -1) {
      om.setReturnParameter(returnParameter);
    }
    if (durationParameter != -1) {
      om.setDurationParameter(durationParameter);
    }
    if (targetInstanceParameter != -1) {
      om.setTargetInstanceParameter(targetInstanceParameter);
    }
    if (targetMethodOrFieldParameter != -1) {
      om.setTargetMethodOrFieldParameter(targetMethodOrFieldParameter);
    }

    return buildStubProbe(probeInternalName, targetJavaClass, om);
  }
```

- [ ] **Step 2: Extract the common anonymous `BTraceProbe` construction**

Replace the current body of the most complete `buildStubProbe(...)` overload with:

```java
    final Location loc = new Location();
    loc.setValue(kind);
    final OnMethod om = new OnMethod();
    om.setClazz(targetJavaClass);
    om.setMethod(targetMethod);
    om.setLocation(loc);
    om.setTargetName("onProbe");
    om.setTargetDescriptor(targetDescriptor);
    if (returnParameter != -1) {
      om.setReturnParameter(returnParameter);
    }
    if (durationParameter != -1) {
      om.setDurationParameter(durationParameter);
    }
    return buildStubProbe(probeInternalName, targetJavaClass, om);
```

Then add the extracted helper:

```java
  private static BTraceProbe buildStubProbe(
      final String probeInternalName, final String targetJavaClass, final OnMethod om) {
    return new BTraceProbe() {
      @Override
      public String getActionPrefix() {
        return InstrumentUtils.getActionPrefix(probeInternalName);
      }

      @Override
      public Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
        return getApplicableHandlers(
            new ClassMeta() {
              @Override
              public String getJavaClassName() {
                return cr.getJavaClassName();
              }

              @Override
              public String getInternalName() {
                return cr.getClassName();
              }

              @Override
              public Collection<String> getAnnotationTypes() {
                return cr.getAnnotationTypes();
              }

              @Override
              public ClassLoader getClassLoader() {
                return cr.getClassLoader();
              }
            });
      }

      @Override
      public Collection<OnMethod> getApplicableHandlers(ClassMeta meta) {
        if (targetJavaClass.equals(meta.getJavaClassName())) {
          return Collections.singletonList(om);
        }
        return Collections.emptyList();
      }

      @Override
      public byte[] getFullBytecode() {
        return new byte[0];
      }

      @Override
      public byte[] getDataHolderBytecode() {
        return new byte[0];
      }

      @Override
      public String getClassName() {
        return probeInternalName.replace('/', '.');
      }

      @Override
      public String getClassName(boolean internal) {
        return internal ? probeInternalName : probeInternalName.replace('/', '.');
      }

      @Override
      public boolean isClassRenamed() {
        return false;
      }

      @Override
      public boolean isTransforming() {
        return true;
      }

      @Override
      public boolean isVerified() {
        return true;
      }

      @Override
      public void notifyTransform(String className) {}

      @Override
      public Iterable<OnMethod> onmethods() {
        return Collections.singletonList(om);
      }

      @Override
      public Iterable<OnProbe> onprobes() {
        return Collections.emptyList();
      }

      @Override
      public Class<?> register(BTraceRuntime.Impl rt, BTraceTransformer t) {
        return null;
      }

      @Override
      public Class<?> getProbeClass() {
        return null;
      }

      @Override
      public void unregister() {}

      @Override
      public boolean willInstrument(Class<?> clz) {
        return true;
      }

      @Override
      public void checkVerified() {}

      @Override
      public void copyHandlers(ClassVisitor cv) {}

      @Override
      public void applyArgs(ArgsMap argsMap) {}

      @Override
      public BTraceRuntime.Impl getRuntime() {
        return null;
      }

      @Override
      public Set<Permission> getRequiredPermissions() {
        return Collections.emptySet();
      }
    };
  }
```

- [ ] **Step 3: Add a bytecode fixture for a matching instance call**

Add this helper near the existing class builders:

```java
  private static byte[] buildClassWithInstanceCall(
      int majorVersion, String internalClassName, String methodName) {
    org.objectweb.asm.ClassWriter cw =
        new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        org.objectweb.asm.Opcodes.V11,
        org.objectweb.asm.Opcodes.ACC_PUBLIC,
        internalClassName,
        null,
        "java/lang/Object",
        null);

    org.objectweb.asm.MethodVisitor ctor =
        cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    ctor.visitCode();
    ctor.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
    ctor.visitMethodInsn(
        org.objectweb.asm.Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    ctor.visitInsn(org.objectweb.asm.Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();

    org.objectweb.asm.MethodVisitor target =
        cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "callTarget", "(Ljava/lang/String;J)J", null, null);
    target.visitCode();
    target.visitVarInsn(org.objectweb.asm.Opcodes.LLOAD, 2);
    target.visitInsn(org.objectweb.asm.Opcodes.LRETURN);
    target.visitMaxs(0, 0);
    target.visitEnd();

    org.objectweb.asm.MethodVisitor caller =
        cw.visitMethod(
            org.objectweb.asm.Opcodes.ACC_PUBLIC,
            methodName,
            "(Ljava/lang/String;J)J",
            null,
            null);
    caller.visitCode();
    caller.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
    caller.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 1);
    caller.visitVarInsn(org.objectweb.asm.Opcodes.LLOAD, 2);
    caller.visitMethodInsn(
        org.objectweb.asm.Opcodes.INVOKEVIRTUAL,
        internalClassName,
        "callTarget",
        "(Ljava/lang/String;J)J",
        false);
    caller.visitInsn(org.objectweb.asm.Opcodes.LRETURN);
    caller.visitMaxs(0, 0);
    caller.visitEnd();

    cw.visitEnd();
    byte[] bytes = cw.toByteArray();
    bytes[6] = (byte) (majorVersion >> 8);
    bytes[7] = (byte) (majorVersion & 0xFF);
    return bytes;
  }
```

- [ ] **Step 4: Add failing before-call and after-call tests**

Add these tests near `noInjectionForUnsupportedKind()`:

```java
  @Test
  void callProbeInjectedBeforeMatchingCall() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(io.btrace.core.annotations.Kind.CALL);
    location.setWhere(io.btrace.core.annotations.Where.BEFORE);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for matching CALL probe");
    assertEquals(1, countInvokeDynamic(patchVersion(result, 65), "callTopLevel", "$btrace$"));
  }

  @Test
  void callProbeInjectedAfterMatchingCall() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(io.btrace.core.annotations.Kind.CALL);
    location.setWhere(io.btrace.core.annotations.Where.AFTER);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for matching CALL probe");
    assertEquals(1, countInvokeDynamic(patchVersion(result, 65), "callTopLevel", "$btrace$"));
  }
```

- [ ] **Step 5: Run the targeted test and confirm the new tests fail**

Run:

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test --tests io.btrace.instr.ClassFileApiBackendTest > /tmp/classfileapi-call-test.log 2>&1; rg -n "FAILED|callProbeInjected|BUILD (SUCCESSFUL|FAILED)|tests" /tmp/classfileapi-call-test.log
```

Expected: the new `callProbeInjectedBeforeMatchingCall` and `callProbeInjectedAfterMatchingCall` tests fail because `Kind.CALL` is still skipped.

---

### Task 2: Classify And Match `Kind.CALL` Handlers

**Files:**
- Modify: `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`
- Test: `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`

- [ ] **Step 1: Import the ClassFile API invoke instruction and opcode types**

Add imports:

```java
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import io.btrace.core.annotations.Where;
```

- [ ] **Step 2: Add `callHandlers` classification**

Replace the handler split in `instrument(...)` with:

```java
    List<ProbeHandler> entryHandlers = new ArrayList<>();
    List<ProbeHandler> returnHandlers = new ArrayList<>();
    List<ProbeHandler> callHandlers = new ArrayList<>();
    for (ProbeHandler ph : handlers) {
      Kind kind = ph.om.getLocation().getValue();
      if (kind == Kind.ENTRY) entryHandlers.add(ph);
      else if (kind == Kind.RETURN) returnHandlers.add(ph);
      else if (kind == Kind.CALL) callHandlers.add(ph);
      else log.debug("Skipping unsupported probe kind {} for class {}", kind, javaClassName);
    }
    if (entryHandlers.isEmpty() && returnHandlers.isEmpty() && callHandlers.isEmpty()) return null;
```

- [ ] **Step 3: Thread `callHandlers` through class and method transforms**

Update the signatures and call sites:

```java
  private static ClassTransform buildClassTransform(
      String javaClassName,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers,
      List<ProbeHandler> callHandlers,
      boolean[] anyMatch)
```

```java
        List<ProbeHandler> mCall = filterForMethod(callHandlers, methodName);
        if (!mEntry.isEmpty() || !mReturn.isEmpty() || !mCall.isEmpty()) {
          anyMatch[0] = true;
          classBuilder.transformMethod(
              mm, buildMethodTransform(javaClassName, methodName, isStatic, mEntry, mReturn, mCall));
```

```java
  private static MethodTransform buildMethodTransform(
      String javaClassName,
      String methodName,
      boolean isStatic,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers,
      List<ProbeHandler> callHandlers)
```

```java
            cm,
            buildCodeTransform(
                javaClassName, methodName, isStatic, entryHandlers, returnHandlers, callHandlers));
```

- [ ] **Step 4: Add call-location matching helpers**

Add these helpers before `emitProbeCall(...)`:

```java
  private static List<ProbeHandler> filterForCall(List<ProbeHandler> handlers, InvokeInstruction ii) {
    List<ProbeHandler> result = new ArrayList<>();
    String owner = ii.owner().asInternalName().replace('/', '.');
    String name = ii.name().stringValue();
    String desc = ii.type().stringValue();
    for (ProbeHandler ph : handlers) {
      Location loc = ph.om.getLocation();
      if (!matches(loc.getClazz(), owner)) continue;
      if (!matches(loc.getMethod(), name)) continue;
      if (!loc.getType().isEmpty() && !loc.getType().equals(desc)) continue;
      result.add(ph);
    }
    return result;
  }

  private static boolean matches(String pattern, String value) {
    if (pattern == null || pattern.isEmpty()) return true;
    if (pattern.startsWith("/") && pattern.endsWith("/")) {
      return value.matches(pattern.substring(1, pattern.length() - 1));
    }
    return pattern.equals(value);
  }
```

- [ ] **Step 5: Preserve the original invoke instruction**

Add:

```java
  private static void emitInvoke(CodeBuilder cb, InvokeInstruction ii) {
    cb.invoke(ii.opcode(), ii.method());
  }
```

- [ ] **Step 6: Add minimal call injection with no handler arguments**

Inside `accept(...)`, before `cb.with(ce)`, add:

```java
        if (!callHandlers.isEmpty() && ce instanceof InvokeInstruction ii) {
          List<ProbeHandler> matchingCalls = filterForCall(callHandlers, ii);
          if (!matchingCalls.isEmpty()) {
            for (ProbeHandler ph : matchingCalls) {
              if (ph.om.getLocation().getWhere() == Where.BEFORE) {
                emitProbeCall(cb, ph, javaClassName, methodName, isStatic, true, -1, TypeKind.VOID, -1);
              }
            }
            emitInvoke(cb, ii);
            for (ProbeHandler ph : matchingCalls) {
              if (ph.om.getLocation().getWhere() == Where.AFTER) {
                emitProbeCall(cb, ph, javaClassName, methodName, isStatic, false, -1, TypeKind.VOID, -1);
              }
            }
            return;
          }
        }
```

- [ ] **Step 7: Run targeted tests**

Run:

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test --tests io.btrace.instr.ClassFileApiBackendTest > /tmp/classfileapi-call-test.log 2>&1; rg -n "FAILED|BUILD (SUCCESSFUL|FAILED)|tests" /tmp/classfileapi-call-test.log
```

Expected: the two basic call-injection tests pass. Existing tests still pass or skip according to JDK version.

- [ ] **Step 8: Commit**

```bash
git add btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java
git commit -m "feat(agent): match ClassFile API call probes"
```

---

### Task 3: Preserve Call Stack Arguments For `Where.BEFORE`

**Files:**
- Modify: `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`
- Test: `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`

- [ ] **Step 1: Add a failing descriptor test for called arguments**

Add:

```java
  @Test
  void callProbeBeforePassesCalledArguments() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(io.btrace.core.annotations.Kind.CALL);
    location.setWhere(io.btrace.core.annotations.Where.BEFORE);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(Ljava/lang/String;J)V");

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result);
    String desc = getInvokeDynamicDescriptor(patchVersion(result, 65), "callTopLevel", "$btrace$");
    assertEquals("(Ljava/lang/String;J)V", desc);
  }
```

- [ ] **Step 2: Add `CallContext`**

Add:

```java
  private static final class CallContext {
    final InvokeInstruction instruction;
    final Type[] argumentTypes;
    final Type returnType;
    final boolean staticCall;
    final int[] argumentSlots;
    final int receiverSlot;
    final int returnSlot;
    final TypeKind returnKind;
    final int durationSlot;

    CallContext(
        InvokeInstruction instruction,
        Type[] argumentTypes,
        Type returnType,
        boolean staticCall,
        int[] argumentSlots,
        int receiverSlot,
        int returnSlot,
        TypeKind returnKind,
        int durationSlot) {
      this.instruction = instruction;
      this.argumentTypes = argumentTypes;
      this.returnType = returnType;
      this.staticCall = staticCall;
      this.argumentSlots = argumentSlots;
      this.receiverSlot = receiverSlot;
      this.returnSlot = returnSlot;
      this.returnKind = returnKind;
      this.durationSlot = durationSlot;
    }
  }
```

- [ ] **Step 3: Add type conversion helper**

Add:

```java
  private static TypeKind typeKind(Type type) {
    switch (type.getSort()) {
      case Type.BOOLEAN:
      case Type.BYTE:
      case Type.CHAR:
      case Type.SHORT:
      case Type.INT:
        return TypeKind.INT;
      case Type.LONG:
        return TypeKind.LONG;
      case Type.FLOAT:
        return TypeKind.FLOAT;
      case Type.DOUBLE:
        return TypeKind.DOUBLE;
      case Type.VOID:
        return TypeKind.VOID;
      default:
        return TypeKind.REFERENCE;
    }
  }
```

- [ ] **Step 4: Add stack backup and restore helpers**

Add:

```java
  private static CallContext backupCallStack(CodeBuilder cb, InvokeInstruction ii) {
    Type[] args = Type.getArgumentTypes(ii.type().stringValue());
    Type returnType = Type.getReturnType(ii.type().stringValue());
    boolean staticCall = ii.opcode() == Opcode.INVOKESTATIC;
    int[] argSlots = new int[args.length];
    for (int i = args.length - 1; i >= 0; i--) {
      TypeKind kind = typeKind(args[i]);
      int slot = cb.allocateLocal(kind);
      cb.storeLocal(kind, slot);
      argSlots[i] = slot;
    }

    int receiverSlot = -1;
    if (!staticCall) {
      receiverSlot = cb.allocateLocal(TypeKind.REFERENCE);
      cb.astore(receiverSlot);
    }

    return new CallContext(ii, args, returnType, staticCall, argSlots, receiverSlot, -1, TypeKind.VOID, -1);
  }

  private static void restoreCallStack(CodeBuilder cb, CallContext ctx) {
    if (!ctx.staticCall) {
      cb.aload(ctx.receiverSlot);
    }
    for (int i = 0; i < ctx.argumentTypes.length; i++) {
      cb.loadLocal(typeKind(ctx.argumentTypes[i]), ctx.argumentSlots[i]);
    }
  }
```

- [ ] **Step 5: Use backup/restore around call instrumentation**

Replace the minimal call branch from Task 2 with:

```java
          if (!matchingCalls.isEmpty()) {
            CallContext ctx = backupCallStack(cb, ii);
            for (ProbeHandler ph : matchingCalls) {
              if (ph.om.getLocation().getWhere() == Where.BEFORE) {
                emitProbeCall(cb, ph, javaClassName, methodName, isStatic, true, -1, TypeKind.VOID, -1, ctx);
              }
            }
            restoreCallStack(cb, ctx);
            emitInvoke(cb, ii);
            for (ProbeHandler ph : matchingCalls) {
              if (ph.om.getLocation().getWhere() == Where.AFTER) {
                emitProbeCall(cb, ph, javaClassName, methodName, isStatic, false, -1, TypeKind.VOID, -1, ctx);
              }
            }
            return;
          }
```

- [ ] **Step 6: Overload `emitProbeCall` for call context**

Keep the existing method signature and make it delegate:

```java
    emitProbeCall(cb, ph, javaClassName, methodName, isStatic, isEntry, retValSlot, returnKind, durationSlot, null);
```

Then add a `CallContext ctx` parameter to the implementation method.

- [ ] **Step 7: Satisfy ordinary call arguments during validation and loading**

Inside `emitProbeCall(..., CallContext ctx)`, update pre-validation:

```java
              || (ctx != null && callArgumentIndex(om, i) != -1)
```

Add helper:

```java
  private static int callArgumentIndex(OnMethod om, int handlerIndex) {
    int callArg = 0;
    for (int i = 0; i <= handlerIndex; i++) {
      if (i == om.getSelfParameter()
          || i == om.getClassNameParameter()
          || i == om.getMethodParameter()
          || i == om.getReturnParameter()
          || i == om.getDurationParameter()
          || i == om.getTargetInstanceParameter()
          || i == om.getTargetMethodOrFieldParameter()) {
        continue;
      }
      if (i == handlerIndex) return callArg;
      callArg++;
    }
    return -1;
  }
```

In the load loop, add:

```java
      } else if (ctx != null && callArgumentIndex(om, i) != -1) {
        int callArgIndex = callArgumentIndex(om, i);
        cb.loadLocal(typeKind(ctx.argumentTypes[callArgIndex]), ctx.argumentSlots[callArgIndex]);
```

- [ ] **Step 8: Run targeted tests**

Run:

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test --tests io.btrace.instr.ClassFileApiBackendTest > /tmp/classfileapi-call-test.log 2>&1; rg -n "FAILED|BUILD (SUCCESSFUL|FAILED)|tests" /tmp/classfileapi-call-test.log
```

Expected: `callProbeBeforePassesCalledArguments` passes and the generated class loads without verification failures.

- [ ] **Step 9: Commit**

```bash
git add btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java
git commit -m "feat(agent): pass ClassFile API call arguments"
```

---

### Task 4: Add `@TargetInstance` And `@TargetMethodOrField`

**Files:**
- Modify: `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`
- Test: `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`

- [ ] **Step 1: Add failing tests for target instance and target method metadata**

Add:

```java
  @Test
  void callProbeBeforePassesTargetInstanceAndMethodName() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(io.btrace.core.annotations.Kind.CALL);
    location.setWhere(io.btrace.core.annotations.Where.BEFORE);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(Ljava/lang/Object;Ljava/lang/String;)V",
            -1,
            -1,
            0,
            1);

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result);
    String desc = getInvokeDynamicDescriptor(patchVersion(result, 65), "callTopLevel", "$btrace$");
    assertEquals("(Ljava/lang/Object;Ljava/lang/String;)V", desc);
  }
```

- [ ] **Step 2: Add a static-call fixture**

Add:

```java
  private static byte[] buildClassWithStaticCall(
      int majorVersion, String internalClassName, String methodName) {
    org.objectweb.asm.ClassWriter cw =
        new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        org.objectweb.asm.Opcodes.V11,
        org.objectweb.asm.Opcodes.ACC_PUBLIC,
        internalClassName,
        null,
        "java/lang/Object",
        null);
    org.objectweb.asm.MethodVisitor target =
        cw.visitMethod(
            org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
            "callTargetStatic",
            "(Ljava/lang/String;J)J",
            null,
            null);
    target.visitCode();
    target.visitVarInsn(org.objectweb.asm.Opcodes.LLOAD, 1);
    target.visitInsn(org.objectweb.asm.Opcodes.LRETURN);
    target.visitMaxs(0, 0);
    target.visitEnd();

    org.objectweb.asm.MethodVisitor caller =
        cw.visitMethod(
            org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
            methodName,
            "(Ljava/lang/String;J)J",
            null,
            null);
    caller.visitCode();
    caller.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
    caller.visitVarInsn(org.objectweb.asm.Opcodes.LLOAD, 1);
    caller.visitMethodInsn(
        org.objectweb.asm.Opcodes.INVOKESTATIC,
        internalClassName,
        "callTargetStatic",
        "(Ljava/lang/String;J)J",
        false);
    caller.visitInsn(org.objectweb.asm.Opcodes.LRETURN);
    caller.visitMaxs(0, 0);
    caller.visitEnd();

    cw.visitEnd();
    byte[] bytes = cw.toByteArray();
    bytes[6] = (byte) (majorVersion >> 8);
    bytes[7] = (byte) (majorVersion & 0xFF);
    return bytes;
  }
```

- [ ] **Step 3: Add a failing static target instance test**

Add:

```java
  @Test
  void callProbeBeforeUsesNullTargetInstanceForStaticCall() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithStaticCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(io.btrace.core.annotations.Kind.CALL);
    location.setWhere(io.btrace.core.annotations.Where.BEFORE);
    location.setClazz("com.example.Target");
    location.setMethod("callTargetStatic");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(Ljava/lang/Object;)V",
            -1,
            -1,
            0,
            -1);

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result);
    assertEquals("(Ljava/lang/Object;)V", getInvokeDynamicDescriptor(patchVersion(result, 65), "callTopLevel", "$btrace$"));
  }
```

- [ ] **Step 4: Remove the target-instance/target-method blanket skip**

Delete this block from `emitProbeCall`:

```java
    if (om.getTargetInstanceParameter() != -1 || om.getTargetMethodOrFieldParameter() != -1) {
      log.debug(
          "ClassFileApiBackend: skipping handler {}.{} — unsupported special params",
          ph.probe.getClassName(),
          om.getTargetName());
      return;
    }
```

- [ ] **Step 5: Validate and load target metadata**

In pre-validation, add:

```java
              || (i == om.getTargetInstanceParameter() && ctx != null)
              || (i == om.getTargetMethodOrFieldParameter() && ctx != null)
```

In the load loop, add:

```java
      } else if (i == om.getTargetInstanceParameter()) {
        if (ctx == null || ctx.staticCall) {
          cb.aconst_null();
        } else {
          cb.aload(ctx.receiverSlot);
        }
      } else if (i == om.getTargetMethodOrFieldParameter()) {
        cb.ldc(targetMethodName(om, ctx));
```

Add:

```java
  private static String targetMethodName(OnMethod om, CallContext ctx) {
    String owner = ctx.instruction.owner().asInternalName().replace('/', '.');
    String name = ctx.instruction.name().stringValue();
    String desc = ctx.instruction.type().stringValue();
    if (om.isTargetMethodOrFieldFqn()) {
      return owner + "." + name + desc;
    }
    return name;
  }
```

- [ ] **Step 6: Run targeted tests**

Run:

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test --tests io.btrace.instr.ClassFileApiBackendTest > /tmp/classfileapi-call-test.log 2>&1; rg -n "FAILED|BUILD (SUCCESSFUL|FAILED)|tests" /tmp/classfileapi-call-test.log
```

Expected: target instance and target method tests pass.

- [ ] **Step 7: Commit**

```bash
git add btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java
git commit -m "feat(agent): pass ClassFile API call target metadata"
```

---

### Task 5: Add `Where.AFTER` Return Value And Duration For Calls

**Files:**
- Modify: `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`
- Test: `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`

- [ ] **Step 1: Add failing tests for call return and duration**

Add:

```java
  @Test
  void callProbeAfterPassesReturnValueAndDuration() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(io.btrace.core.annotations.Kind.CALL);
    location.setWhere(io.btrace.core.annotations.Where.AFTER);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(JJ)V",
            0,
            1,
            -1,
            -1);

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result);
    assertEquals("(JJ)V", getInvokeDynamicDescriptor(patchVersion(result, 65), "callTopLevel", "$btrace$"));
  }
```

- [ ] **Step 2: Add entry timestamp for call-duration probes**

Before restoring the call stack and emitting the original invoke, compute call duration only when a matching `Where.AFTER` handler has `durationParameter != -1`:

```java
            int callStartSlot = -1;
            if (needsCallDuration(matchingCalls)) {
              callStartSlot = cb.allocateLocal(TypeKind.LONG);
              cb.invokestatic(
                  ClassDesc.ofInternalName("java/lang/System"),
                  "nanoTime",
                  MethodTypeDesc.ofDescriptor("()J"));
              cb.storeLocal(TypeKind.LONG, callStartSlot);
            }
```

Add helper:

```java
  private static boolean needsCallDuration(List<ProbeHandler> handlers) {
    for (ProbeHandler ph : handlers) {
      if (ph.om.getLocation().getWhere() == Where.AFTER && ph.om.getDurationParameter() != -1) {
        return true;
      }
    }
    return false;
  }
```

- [ ] **Step 3: Store call return values for `Where.AFTER`**

After `emitInvoke(cb, ii)`, build a second context with return data:

```java
            CallContext afterCtx = storeCallReturnAndDuration(cb, ctx, callStartSlot);
```

Add helper:

```java
  private static CallContext storeCallReturnAndDuration(
      CodeBuilder cb, CallContext ctx, int callStartSlot) {
    TypeKind returnKind = typeKind(ctx.returnType);
    int returnSlot = -1;
    if (returnKind != TypeKind.VOID) {
      returnSlot = cb.allocateLocal(returnKind);
      if (returnKind.slotSize() == 2) {
        cb.dup2();
      } else {
        cb.dup();
      }
      cb.storeLocal(returnKind, returnSlot);
    }

    int durationSlot = -1;
    if (callStartSlot != -1) {
      durationSlot = cb.allocateLocal(TypeKind.LONG);
      cb.invokestatic(
          ClassDesc.ofInternalName("java/lang/System"),
          "nanoTime",
          MethodTypeDesc.ofDescriptor("()J"));
      cb.loadLocal(TypeKind.LONG, callStartSlot);
      cb.lsub();
      cb.storeLocal(TypeKind.LONG, durationSlot);
    }

    return new CallContext(
        ctx.instruction,
        ctx.argumentTypes,
        ctx.returnType,
        ctx.staticCall,
        ctx.argumentSlots,
        ctx.receiverSlot,
        returnSlot,
        returnKind,
        durationSlot);
  }
```

- [ ] **Step 4: Use return and duration slots for after-call handlers**

Change the after-call `emitProbeCall` call:

```java
                emitProbeCall(
                    cb,
                    ph,
                    javaClassName,
                    methodName,
                    isStatic,
                    false,
                    afterCtx.returnSlot,
                    afterCtx.returnKind,
                    afterCtx.durationSlot,
                    afterCtx);
```

- [ ] **Step 5: Run targeted tests**

Run:

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test --tests io.btrace.instr.ClassFileApiBackendTest > /tmp/classfileapi-call-test.log 2>&1; rg -n "FAILED|BUILD (SUCCESSFUL|FAILED)|tests" /tmp/classfileapi-call-test.log
```

Expected: call return and duration tests pass. The called method return value remains on the original stack and the caller method still verifies.

- [ ] **Step 6: Commit**

```bash
git add btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java
git commit -m "feat(agent): support ClassFile API call return and duration"
```

---

### Task 6: Negative Cases, Verification, And Documentation Cleanup

**Files:**
- Modify: `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`
- Modify: `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`

- [ ] **Step 1: Add no-match tests for call owner/name/type**

Add three tests using `buildClassWithInstanceCall(...)` with mismatched `location.setClazz("com.example.Other")`, `location.setMethod("otherTarget")`, and `location.setType("()V")`. Each test should assert:

```java
    assertNull(result, "Expected null when no call site matches");
```

- [ ] **Step 2: Add void-call after return skip test**

Add a fixture with a void target call and a `Where.AFTER` handler descriptor containing `@Return`. Assert that instrumentation either returns null or contains zero `$btrace$` invokedynamics for that method, matching the existing method-return void behavior.

- [ ] **Step 3: Update class JavaDoc**

Replace the unsupported-note JavaDoc with:

```java
 * <p>Supported probe kinds: {@link Kind#ENTRY}, {@link Kind#RETURN}, and {@link Kind#CALL}.
 * Method entry/return handlers support {@code @Self}, {@code @Return}, and {@code @Duration}
 * as applicable. Method call handlers support called arguments, {@code @TargetInstance},
 * {@code @TargetMethodOrField}, {@code @Return} for {@link Where#AFTER}, and {@code @Duration}
 * for {@link Where#AFTER}. Type-constrained outer-method matching ({@code type="..."} in
 * {@code @OnMethod}) is not supported.
```

- [ ] **Step 4: Run formatting**

Run:

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:spotlessApply > /tmp/classfileapi-call-spotless.log 2>&1; rg -n "FAILED|BUILD (SUCCESSFUL|FAILED)|spotless" /tmp/classfileapi-call-spotless.log
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run targeted unit tests**

Run:

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test --tests io.btrace.instr.ClassFileApiBackendTest > /tmp/classfileapi-call-test.log 2>&1; rg -n "FAILED|BUILD (SUCCESSFUL|FAILED)|tests" /tmp/classfileapi-call-test.log
```

Expected: `BUILD SUCCESSFUL`. On JDK < 26, version-70 instrumentation tests skip via assumptions; on JDK 26+, all new tests pass.

- [ ] **Step 6: Run broader agent tests**

Run:

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test > /tmp/btrace-agent-test.log 2>&1; rg -n "FAILED|BUILD (SUCCESSFUL|FAILED)|tests" /tmp/btrace-agent-test.log
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Inspect final diff**

Run:

```bash
git diff --stat origin/develop
git diff -- btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java
```

Expected: changes are limited to `ClassFileApiBackend.java`, `ClassFileApiBackendTest.java`, and this plan file unless the user explicitly wants the plan excluded before PR update.

- [ ] **Step 8: Commit**

```bash
git add btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java docs/superpowers/plans/2026-06-04-classfileapi-call-targetinstance.md
git commit -m "test(agent): cover ClassFile API call probe edge cases"
```

---

## Risks And Notes

- The highest-risk area is JVM stack preservation around invoke instructions. The implementation must store call arguments from right to left, store the receiver after arguments for non-static calls, then restore receiver first and arguments left to right.
- `INVOKESPECIAL <init>` before-call target instance should remain conservative. If constructor call support is ambiguous, skip before-constructor target instance exactly as the ASM backend does for `Where.BEFORE`.
- Interface calls must preserve `InvokeInstruction.isInterface()` through the original `MemberRefEntry`; using `cb.invoke(ii.opcode(), ii.method())` avoids rebuilding the constant-pool reference.
- Do not rebuild BTrace action descriptors. Keep using `om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC)`.
- If ClassFile API stack-map generation rejects a transform, add a loadability assertion to the relevant test and fix the exact stack/local sequence before continuing.
