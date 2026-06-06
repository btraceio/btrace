/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceRuntime;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Where;
import io.btrace.core.extensions.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Tests for ClassFileApiBackend. All tests are enabled only on JDK 24+ where java.lang.classfile is
 * available (and the backend class is loadable).
 */
@EnabledForJreRange(min = JRE.JAVA_24)
class ClassFileApiBackendTest {

  @Test
  void supportsVersionAbove69() {
    InstrumentationBackend backend = BackendSelector.select(70);
    assertFalse(
        backend instanceof AsmInstrumentationBackend,
        "Expected ClassFile API backend for version 70 on JDK 24+");
    assertTrue(backend.supports(70));
    assertTrue(backend.supports(80));
    assertFalse(backend.supports(69));
  }

  @Test
  void returnsNullWhenNoProbesMatch() {
    InstrumentationBackend backend = BackendSelector.select(70);
    // Pass empty probe list — no match possible
    byte[] result = backend.instrument(null, buildMinimalClass(70), Collections.emptyList());
    assertNull(result);
  }

  @Test
  void entryProbeInjectedIntoMatchingMethod() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "doWork");
    BTraceProbe probe =
        buildStubProbe("com/example/MyTrace", "com.example.Target", "doWork", Kind.ENTRY, "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes when probe matches");
    // Patch back to ASM-readable version to inspect the result
    assertTrue(
        containsInvokeDynamic(patchVersion(result, 65), "doWork", "$btrace$"),
        "Expected INVOKEDYNAMIC for BTrace probe in doWork");
  }

  @Test
  void entryProbeNotInjectedWhenMethodNameMismatches() {
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "doWork");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace", "com.example.Target", "otherMethod", Kind.ENTRY, "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNull(result, "Expected null when no method name matches");
  }

  @Test
  void entryProbeInjectedWhenTypeConstraintMatches() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithStaticCall(70, "com/example/Target", "callTopLevel");
    BTraceProbe probe =
        buildStubProbeWithType(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            Kind.ENTRY,
            "()V",
            "long (java.lang.String, long)");

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected type-constrained ENTRY probe to match method descriptor");
    assertTrue(containsInvokeDynamic(patchVersion(result, 65), "callTopLevel", "$btrace$"));
  }

  @Test
  void entryProbeSkippedWhenTypeConstraintMismatches() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithStaticCall(70, "com/example/Target", "callTopLevel");
    BTraceProbe probe =
        buildStubProbeWithType(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            Kind.ENTRY,
            "()V",
            "int (java.lang.String, long)");

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNull(result, "Expected mismatched type-constrained ENTRY probe to be skipped");
  }

  @Test
  void returnProbeInjectedBeforeReturn() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "compute");
    BTraceProbe probe =
        buildStubProbe("com/example/MyTrace", "com.example.Target", "compute", Kind.RETURN, "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for RETURN probe");
    assertTrue(
        containsInvokeDynamic(patchVersion(result, 65), "compute", "$btrace$"),
        "Expected INVOKEDYNAMIC for BTrace probe in compute");
  }

  @Test
  void noInjectionWhenCallProbeHasNoCallSite() {
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "doWork");
    BTraceProbe probe =
        buildStubProbe("com/example/MyTrace", "com.example.Target", "doWork", Kind.CALL, "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNull(result, "Expected null when CALL probe has no matching call site");
  }

  @Test
  void lineProbeInjectedBeforeMatchingLine() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithLineNumber(70, "com/example/Target", "doWork", 42);
    Location location = new Location();
    location.setValue(Kind.LINE);
    location.setLine(42);
    BTraceProbe probe =
        buildStubProbe("com/example/MyTrace", "com.example.Target", "doWork", location, "(I)V");

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for matching LINE probe");
    byte[] readable = patchVersion(result, 65);
    assertEquals("(I)V", getInvokeDynamicDescriptor(readable, "doWork", "$btrace$"));
    assertBTraceBeforeOpcode(readable, "doWork", Opcodes.RETURN);
  }

  @Test
  void lineProbeNotInjectedWhenLineMismatches() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithLineNumber(70, "com/example/Target", "doWork", 42);
    Location location = new Location();
    location.setValue(Kind.LINE);
    location.setLine(43);
    BTraceProbe probe =
        buildStubProbe("com/example/MyTrace", "com.example.Target", "doWork", location, "(I)V");

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNull(result, "Expected no instrumentation for a non-matching line");
  }

  @Test
  void phaseZeroFixturesContainExpectedBytecodeShapes() {
    byte[] lineFixture =
        patchVersion(buildClassWithLineNumber(70, "com/example/Target", "line", 42), 65);
    assertTrue(containsLineNumber(lineFixture, "line", 42));

    byte[] fieldFixture =
        patchVersion(buildClassWithFieldAccesses(70, "com/example/Target", "fields"), 65);
    assertEquals(
        1, countFieldInsn(fieldFixture, "fields", Opcodes.GETFIELD, "com/example/Target", "value"));
    assertEquals(
        1, countFieldInsn(fieldFixture, "fields", Opcodes.PUTFIELD, "com/example/Target", "value"));
    assertEquals(
        1,
        countFieldInsn(
            fieldFixture, "fields", Opcodes.GETSTATIC, "com/example/Target", "staticValue"));
    assertEquals(
        1,
        countFieldInsn(
            fieldFixture, "fields", Opcodes.PUTSTATIC, "com/example/Target", "staticValue"));

    byte[] arrayFixture =
        patchVersion(buildClassWithArrayAccesses(70, "com/example/Target", "arrays"), 65);
    assertEquals(1, countOpcode(arrayFixture, "arrays", Opcodes.IALOAD));
    assertEquals(1, countOpcode(arrayFixture, "arrays", Opcodes.IASTORE));
    assertEquals(1, countOpcode(arrayFixture, "arrays", Opcodes.AALOAD));
    assertEquals(1, countOpcode(arrayFixture, "arrays", Opcodes.AASTORE));

    byte[] typeFixture =
        patchVersion(buildClassWithTypeChecks(70, "com/example/Target", "types"), 65);
    assertEquals(1, countTypeInsn(typeFixture, "types", Opcodes.INSTANCEOF, "java/util/List"));
    assertEquals(1, countTypeInsn(typeFixture, "types", Opcodes.CHECKCAST, "java/lang/String"));

    byte[] allocationFixture =
        patchVersion(buildClassWithArrayAllocations(70, "com/example/Target", "allocations"), 65);
    assertEquals(
        1, countIntInsn(allocationFixture, "allocations", Opcodes.NEWARRAY, Opcodes.T_INT));
    assertEquals(
        1, countTypeInsn(allocationFixture, "allocations", Opcodes.ANEWARRAY, "java/lang/String"));
    assertEquals(1, countOpcode(allocationFixture, "allocations", Opcodes.MULTIANEWARRAY));

    byte[] objectAllocationFixture =
        patchVersion(buildClassWithObjectAllocation(70, "com/example/Target", "objects"), 65);
    assertEquals(
        1,
        countTypeInsn(objectAllocationFixture, "objects", Opcodes.NEW, "java/lang/StringBuilder"));

    byte[] exceptionFixture =
        patchVersion(buildClassWithThrowAndCatch(70, "com/example/Target", "exceptions"), 65);
    assertEquals(2, countOpcode(exceptionFixture, "exceptions", Opcodes.ATHROW));
    assertEquals(1, countTryCatchBlocks(exceptionFixture, "exceptions"));
    assertEquals(
        1,
        handlerStartsForCatchType(exceptionFixture, "exceptions", "java/lang/RuntimeException")
            .size());

    byte[] monitorFixture =
        patchVersion(buildClassWithMonitorBlock(70, "com/example/Target", "monitor"), 65);
    assertEquals(1, countOpcode(monitorFixture, "monitor", Opcodes.MONITORENTER));
    assertEquals(2, countOpcode(monitorFixture, "monitor", Opcodes.MONITOREXIT));
    assertEquals(2, countTryCatchBlocks(monitorFixture, "monitor"));
  }

  @Test
  void callProbeInjectedBeforeMatchingCall() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.BEFORE);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace", "com.example.Target", "callTopLevel", location, "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for matching CALL probe");
    assertEquals(1, countInvokeDynamic(patchVersion(result, 65), "callTopLevel", "$btrace$"));
    assertTrue(
        isBTraceCallBeforeTargetCall(patchVersion(result, 65), "callTopLevel", "callTarget"),
        "Expected BTrace call before matched callTarget invocation");
  }

  @Test
  void callProbeBeforePassesCalledArguments() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.BEFORE);
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
    assertTrue(
        isBTraceCallBeforeTargetCall(patchVersion(result, 65), "callTopLevel", "callTarget"),
        "Expected BTrace call before matched callTarget invocation");
  }

  @Test
  void callProbeBeforeSkipsIncompatibleCalledArgumentDescriptor() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.BEFORE);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(Ljava/lang/Integer;J)V");

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNull(result, "Expected incompatible call argument descriptor to be skipped");
  }

  @Test
  void callProbeBeforePassesTargetInstanceAndMethodName() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.BEFORE);
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
            1,
            true);

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result);
    byte[] readable = patchVersion(result, 65);
    String desc = getInvokeDynamicDescriptor(readable, "callTopLevel", "$btrace$");
    assertEquals("(Ljava/lang/Object;Ljava/lang/String;)V", desc);
    String targetMethod = "virtual long com.example.Target#callTarget(java.lang.String, long)";
    assertTrue(
        containsLdc(readable, "callTopLevel", targetMethod),
        "Expected ASM-compatible FQN target method string");
    assertTrue(
        loadsReferenceBeforeLdcAndBTrace(readable, "callTopLevel", targetMethod),
        "Expected target receiver loaded before target method string");
  }

  @Test
  void callProbeBeforeUsesNullTargetInstanceForStaticCall() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithStaticCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.BEFORE);
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
    byte[] readable = patchVersion(result, 65);
    String desc = getInvokeDynamicDescriptor(readable, "callTopLevel", "$btrace$");
    assertEquals("(Ljava/lang/Object;)V", desc);
    assertTrue(
        loadsNullBeforeBTrace(readable, "callTopLevel"),
        "Expected static target instance argument to be null");
  }

  @Test
  void callProbeBeforeSkipsInvalidStaticTargetInstanceDescriptor() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithStaticCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.BEFORE);
    location.setClazz("com.example.Target");
    location.setMethod("callTargetStatic");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(Ljava/lang/Integer;)V",
            -1,
            -1,
            0,
            -1);

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNull(result, "Expected incompatible static @TargetInstance descriptor to be skipped");
  }

  @Test
  void callProbeBeforeAllowsAssignableTargetInstanceAndMethodName() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithArrayListCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.BEFORE);
    location.setClazz("java.util.ArrayList");
    location.setMethod("size");
    location.setType("()I");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(Ljava/util/List;Ljava/lang/Object;)V",
            -1,
            -1,
            0,
            1);

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result);
    byte[] readable = patchVersion(result, 65);
    String desc = getInvokeDynamicDescriptor(readable, "callTopLevel", "$btrace$");
    assertEquals("(Ljava/util/List;Ljava/lang/Object;)V", desc);
    assertTrue(
        loadsReferenceBeforeLdcAndBTrace(readable, "callTopLevel", "size"),
        "Expected assignable target metadata arguments before probe call");
  }

  @Test
  void callProbeBeforeSkipsInvalidTargetMethodDescriptor() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.BEFORE);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(I)V",
            -1,
            -1,
            -1,
            0);

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNull(result, "Expected non-String @TargetMethodOrField descriptor to be skipped");
  }

  @Test
  void callProbeBeforeSkipsInvalidTargetInstanceDescriptor() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.BEFORE);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(Ljava/lang/Integer;)V",
            -1,
            -1,
            0,
            -1);

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNull(result, "Expected incompatible @TargetInstance descriptor to be skipped");
  }

  @Test
  void callProbeAfterPassesReturnValue() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.AFTER);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(J)V",
            0,
            -1,
            -1,
            -1);

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result);
    byte[] readable = patchVersion(result, 65);
    String desc = getInvokeDynamicDescriptor(readable, "callTopLevel", "$btrace$");
    assertEquals("(J)V", desc);
    assertTrue(
        isBTraceCallAfterTargetCall(readable, "callTopLevel", "callTarget"),
        "Expected return probe after target call");
  }

  @Test
  void callProbeAfterPassesDuration() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.AFTER);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(J)V",
            -1,
            0,
            -1,
            -1);

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result);
    byte[] readable = patchVersion(result, 65);
    String desc = getInvokeDynamicDescriptor(readable, "callTopLevel", "$btrace$");
    assertEquals("(J)V", desc);
    assertEquals(2, countMethodCalls(readable, "callTopLevel", "java/lang/System", "nanoTime"));
    assertTrue(
        isBTraceCallAfterTargetCall(readable, "callTopLevel", "callTarget"),
        "Expected duration probe after target call");
    assertTrue(
        loadsLongBeforeBTrace(readable, "callTopLevel"),
        "Expected computed duration loaded before probe call");
  }

  @Test
  void callProbeAfterBoxesPrimitiveReturnForObjectHandler() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.AFTER);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(Ljava/lang/Object;)V",
            0,
            -1,
            -1,
            -1);

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result);
    byte[] readable = patchVersion(result, 65);
    String desc = getInvokeDynamicDescriptor(readable, "callTopLevel", "$btrace$");
    assertEquals("(Ljava/lang/Object;)V", desc);
    assertEquals(1, countMethodCalls(readable, "callTopLevel", "java/lang/Long", "valueOf"));
  }

  @Test
  void callProbeAfterSkipsVoidReturnParameter() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithVoidCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.AFTER);
    location.setClazz("com.example.Target");
    location.setMethod("callTargetVoid");
    location.setType("()V");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "callTopLevel",
            location,
            "(Ljava/lang/Object;)V",
            0,
            -1,
            -1,
            -1);

    byte[] result =
        BackendSelector.select(70).instrument(null, classBytes, Collections.singletonList(probe));

    assertNull(result, "Expected @Return on void call to be skipped");
  }

  @Test
  void callProbeInjectedAfterMatchingCall() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithInstanceCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.AFTER);
    location.setClazz("com.example.Target");
    location.setMethod("callTarget");
    location.setType("(Ljava/lang/String;J)J");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace", "com.example.Target", "callTopLevel", location, "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for matching CALL probe");
    assertEquals(1, countInvokeDynamic(patchVersion(result, 65), "callTopLevel", "$btrace$"));
    assertFalse(
        isBTraceCallBeforeTargetCall(patchVersion(result, 65), "callTopLevel", "callTarget"),
        "Expected BTrace call after matched callTarget invocation");
  }

  @Test
  void callProbeBeforeConstructorCallIsSkipped() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithConstructorCall(70, "com/example/Target", "callTopLevel");
    Location location = new Location();
    location.setValue(Kind.CALL);
    location.setWhere(Where.BEFORE);
    location.setClazz("com.example.Target");
    location.setMethod("<init>");
    location.setType("()V");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace", "com.example.Target", "callTopLevel", location, "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNull(result, "Expected no instrumentation before constructor call site");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Loads the bytes of a known class file on the classpath and patches bytes 6-7 to simulate a
   * future/EA JDK class version. This produces a syntactically valid class body (only the version
   * header is patched), which is sufficient for ClassFileApiBackend to parse.
   */
  private static byte[] buildMinimalClass(int majorVersion) {
    try {
      byte[] bytes;
      try (java.io.InputStream is =
          ClassFileApiBackendTest.class.getResourceAsStream(
              "/io/btrace/instr/AsmInstrumentationBackend.class")) {
        if (is == null)
          throw new IllegalStateException("AsmInstrumentationBackend.class not found on classpath");
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        bytes = baos.toByteArray();
      }
      bytes[6] = (byte) (majorVersion >> 8);
      bytes[7] = (byte) (majorVersion & 0xFF);
      return bytes;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Builds a class with a single static void method using ASM, then patches the class file major
   * version to the given value.
   */
  private static byte[] buildClassWithMethod(
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
    org.objectweb.asm.MethodVisitor mv =
        cw.visitMethod(
            org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
            methodName,
            "()V",
            null,
            null);
    mv.visitCode();
    mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();
    byte[] bytes = cw.toByteArray();
    bytes[6] = (byte) (majorVersion >> 8);
    bytes[7] = (byte) (majorVersion & 0xFF);
    return bytes;
  }

  private static byte[] buildClassWithInstanceCall(
      int majorVersion, String internalClassName, String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

    MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    ctor.visitCode();
    ctor.visitVarInsn(Opcodes.ALOAD, 0);
    ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();

    MethodVisitor target =
        cw.visitMethod(Opcodes.ACC_PUBLIC, "callTarget", "(Ljava/lang/String;J)J", null, null);
    target.visitCode();
    target.visitVarInsn(Opcodes.LLOAD, 2);
    target.visitInsn(Opcodes.LRETURN);
    target.visitMaxs(0, 0);
    target.visitEnd();

    MethodVisitor caller =
        cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, "(Ljava/lang/String;J)J", null, null);
    caller.visitCode();
    caller.visitVarInsn(Opcodes.ALOAD, 0);
    caller.visitVarInsn(Opcodes.ALOAD, 1);
    caller.visitVarInsn(Opcodes.LLOAD, 2);
    caller.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, internalClassName, "callTarget", "(Ljava/lang/String;J)J", false);
    caller.visitInsn(Opcodes.LRETURN);
    caller.visitMaxs(0, 0);
    caller.visitEnd();

    cw.visitEnd();
    byte[] bytes = cw.toByteArray();
    bytes[6] = (byte) (majorVersion >> 8);
    bytes[7] = (byte) (majorVersion & 0xFF);
    return bytes;
  }

  private static byte[] buildClassWithConstructorCall(
      int majorVersion, String internalClassName, String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

    MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    ctor.visitCode();
    ctor.visitVarInsn(Opcodes.ALOAD, 0);
    ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();

    MethodVisitor caller =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, "()V", null, null);
    caller.visitCode();
    caller.visitTypeInsn(Opcodes.NEW, internalClassName);
    caller.visitInsn(Opcodes.DUP);
    caller.visitMethodInsn(Opcodes.INVOKESPECIAL, internalClassName, "<init>", "()V", false);
    caller.visitInsn(Opcodes.POP);
    caller.visitInsn(Opcodes.RETURN);
    caller.visitMaxs(0, 0);
    caller.visitEnd();

    cw.visitEnd();
    byte[] bytes = cw.toByteArray();
    bytes[6] = (byte) (majorVersion >> 8);
    bytes[7] = (byte) (majorVersion & 0xFF);
    return bytes;
  }

  private static byte[] buildClassWithArrayListCall(
      int majorVersion, String internalClassName, String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

    MethodVisitor caller = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()I", null, null);
    caller.visitCode();
    caller.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
    caller.visitInsn(Opcodes.DUP);
    caller.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
    caller.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
    caller.visitInsn(Opcodes.IRETURN);
    caller.visitMaxs(0, 0);
    caller.visitEnd();

    cw.visitEnd();
    byte[] bytes = cw.toByteArray();
    bytes[6] = (byte) (majorVersion >> 8);
    bytes[7] = (byte) (majorVersion & 0xFF);
    return bytes;
  }

  private static byte[] buildClassWithStaticCall(
      int majorVersion, String internalClassName, String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

    MethodVisitor target =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "callTargetStatic",
            "(Ljava/lang/String;J)J",
            null,
            null);
    target.visitCode();
    target.visitVarInsn(Opcodes.LLOAD, 1);
    target.visitInsn(Opcodes.LRETURN);
    target.visitMaxs(0, 0);
    target.visitEnd();

    MethodVisitor caller =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            methodName,
            "(Ljava/lang/String;J)J",
            null,
            null);
    caller.visitCode();
    caller.visitVarInsn(Opcodes.ALOAD, 0);
    caller.visitVarInsn(Opcodes.LLOAD, 1);
    caller.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        internalClassName,
        "callTargetStatic",
        "(Ljava/lang/String;J)J",
        false);
    caller.visitInsn(Opcodes.LRETURN);
    caller.visitMaxs(0, 0);
    caller.visitEnd();

    cw.visitEnd();
    byte[] bytes = cw.toByteArray();
    bytes[6] = (byte) (majorVersion >> 8);
    bytes[7] = (byte) (majorVersion & 0xFF);
    return bytes;
  }

  private static byte[] buildClassWithVoidCall(
      int majorVersion, String internalClassName, String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

    MethodVisitor target = cw.visitMethod(Opcodes.ACC_PUBLIC, "callTargetVoid", "()V", null, null);
    target.visitCode();
    target.visitInsn(Opcodes.RETURN);
    target.visitMaxs(0, 0);
    target.visitEnd();

    MethodVisitor caller = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null);
    caller.visitCode();
    caller.visitVarInsn(Opcodes.ALOAD, 0);
    caller.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, internalClassName, "callTargetVoid", "()V", false);
    caller.visitInsn(Opcodes.RETURN);
    caller.visitMaxs(0, 0);
    caller.visitEnd();

    cw.visitEnd();
    byte[] bytes = cw.toByteArray();
    bytes[6] = (byte) (majorVersion >> 8);
    bytes[7] = (byte) (majorVersion & 0xFF);
    return bytes;
  }

  private static byte[] buildClassWithLineNumber(
      int majorVersion, String internalClassName, String methodName, int line) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, "()V", null, null);
    Label start = new Label();
    mv.visitCode();
    mv.visitLabel(start);
    mv.visitLineNumber(line, start);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();
    return patchVersion(cw.toByteArray(), majorVersion);
  }

  private static byte[] buildClassWithFieldAccesses(
      int majorVersion, String internalClassName, String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);
    cw.visitField(Opcodes.ACC_PUBLIC, "value", "I", null, null).visitEnd();
    cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "staticValue", "J", null, null)
        .visitEnd();

    MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    ctor.visitCode();
    ctor.visitVarInsn(Opcodes.ALOAD, 0);
    ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            methodName,
            "(L" + internalClassName + ";)J",
            null,
            null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitFieldInsn(Opcodes.GETFIELD, internalClassName, "value", "I");
    mv.visitInsn(Opcodes.POP);
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitFieldInsn(Opcodes.PUTFIELD, internalClassName, "value", "I");
    mv.visitInsn(Opcodes.LCONST_0);
    mv.visitFieldInsn(Opcodes.PUTSTATIC, internalClassName, "staticValue", "J");
    mv.visitFieldInsn(Opcodes.GETSTATIC, internalClassName, "staticValue", "J");
    mv.visitInsn(Opcodes.LRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();
    return patchVersion(cw.toByteArray(), majorVersion);
  }

  private static byte[] buildClassWithArrayAccesses(
      int majorVersion, String internalClassName, String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            methodName,
            "([I[Ljava/lang/Object;)I",
            null,
            null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitInsn(Opcodes.IALOAD);
    mv.visitVarInsn(Opcodes.ISTORE, 2);
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitInsn(Opcodes.IASTORE);
    mv.visitVarInsn(Opcodes.ALOAD, 1);
    mv.visitInsn(Opcodes.ICONST_0);
    mv.visitInsn(Opcodes.AALOAD);
    mv.visitInsn(Opcodes.POP);
    mv.visitVarInsn(Opcodes.ALOAD, 1);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitInsn(Opcodes.ACONST_NULL);
    mv.visitInsn(Opcodes.AASTORE);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();
    return patchVersion(cw.toByteArray(), majorVersion);
  }

  private static byte[] buildClassWithTypeChecks(
      int majorVersion, String internalClassName, String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            methodName,
            "(Ljava/lang/Object;)Z",
            null,
            null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitTypeInsn(Opcodes.INSTANCEOF, "java/util/List");
    mv.visitInsn(Opcodes.POP);
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
    mv.visitInsn(Opcodes.POP);
    mv.visitInsn(Opcodes.ICONST_1);
    mv.visitInsn(Opcodes.IRETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();
    return patchVersion(cw.toByteArray(), majorVersion);
  }

  private static byte[] buildClassWithArrayAllocations(
      int majorVersion, String internalClassName, String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            methodName,
            "()Ljava/lang/Object;",
            null,
            null);
    mv.visitCode();
    mv.visitIntInsn(Opcodes.BIPUSH, 3);
    mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
    mv.visitInsn(Opcodes.POP);
    mv.visitIntInsn(Opcodes.BIPUSH, 2);
    mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
    mv.visitInsn(Opcodes.POP);
    mv.visitInsn(Opcodes.ICONST_2);
    mv.visitInsn(Opcodes.ICONST_3);
    mv.visitMultiANewArrayInsn("[[Ljava/lang/String;", 2);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();
    return patchVersion(cw.toByteArray(), majorVersion);
  }

  private static byte[] buildClassWithObjectAllocation(
      int majorVersion, String internalClassName, String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            methodName,
            "()Ljava/lang/Object;",
            null,
            null);
    mv.visitCode();
    mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
    mv.visitInsn(Opcodes.ARETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();
    return patchVersion(cw.toByteArray(), majorVersion);
  }

  private static byte[] buildClassWithThrowAndCatch(
      int majorVersion, String internalClassName, String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, "()V", null, null);
    Label start = new Label();
    Label end = new Label();
    Label handler = new Label();
    Label done = new Label();
    mv.visitTryCatchBlock(start, end, handler, "java/lang/RuntimeException");
    mv.visitCode();
    mv.visitLabel(start);
    mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
    mv.visitInsn(Opcodes.ATHROW);
    mv.visitLabel(end);
    mv.visitJumpInsn(Opcodes.GOTO, done);
    mv.visitLabel(handler);
    mv.visitVarInsn(Opcodes.ASTORE, 0);
    mv.visitLabel(done);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();
    return patchVersion(cw.toByteArray(), majorVersion);
  }

  private static byte[] buildClassWithMonitorBlock(
      int majorVersion, String internalClassName, String methodName) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, "()V", null, null);
    Label start = new Label();
    Label end = new Label();
    Label handler = new Label();
    Label done = new Label();
    mv.visitTryCatchBlock(start, end, handler, null);
    mv.visitTryCatchBlock(handler, done, handler, null);
    mv.visitCode();
    mv.visitTypeInsn(Opcodes.NEW, "java/lang/Object");
    mv.visitInsn(Opcodes.DUP);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitVarInsn(Opcodes.ASTORE, 0);
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitInsn(Opcodes.DUP);
    mv.visitVarInsn(Opcodes.ASTORE, 1);
    mv.visitInsn(Opcodes.MONITORENTER);
    mv.visitLabel(start);
    mv.visitVarInsn(Opcodes.ALOAD, 1);
    mv.visitInsn(Opcodes.MONITOREXIT);
    mv.visitLabel(end);
    mv.visitJumpInsn(Opcodes.GOTO, done);
    mv.visitLabel(handler);
    mv.visitVarInsn(Opcodes.ALOAD, 1);
    mv.visitInsn(Opcodes.MONITOREXIT);
    mv.visitInsn(Opcodes.ATHROW);
    mv.visitLabel(done);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();
    return patchVersion(cw.toByteArray(), majorVersion);
  }

  /** Patches bytes 6-7 of a class file to the given major version. */
  private static byte[] patchVersion(byte[] bytes, int majorVersion) {
    byte[] copy = Arrays.copyOf(bytes, bytes.length);
    copy[6] = (byte) (majorVersion >> 8);
    copy[7] = (byte) (majorVersion & 0xFF);
    return copy;
  }

  /**
   * Returns the running JDK's major version using only Java 8-compatible APIs. {@code
   * java.specification.version} is {@code "1.8"} on Java 8 and {@code "9"}, {@code "10"}, … {@code
   * "26"} on Java 9+.
   */
  private static int javaMajorVersion() {
    String spec = System.getProperty("java.specification.version", "8");
    try {
      if (spec.startsWith("1.")) {
        return Integer.parseInt(spec.substring(2));
      }
      return Integer.parseInt(spec);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * Skips the test on JDK versions that cannot parse class file version 70 (Java 26). The ClassFile
   * API only supports class files up to the running JDK's own major version (JDK 24 → v68, JDK 25 →
   * v69, JDK 26 → v70). Tests that instrument version-70 class files require JDK 26+.
   */
  private static void requireJdk26ForVersion70() {
    int major = javaMajorVersion();
    Assumptions.assumeTrue(
        major >= 26,
        "ClassFile API on JDK "
            + major
            + " cannot parse class file version 70; test requires JDK 26+");
  }

  /**
   * Returns true if the named method in the class bytes contains an INVOKEDYNAMIC instruction whose
   * name contains the given substring.
   */
  private static boolean containsInvokeDynamic(
      byte[] classBytes, String methodName, String nameSubstring) {
    final boolean[] found = {false};
    ClassReader cr = new ClassReader(classBytes);
    cr.accept(
        new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
          @Override
          public org.objectweb.asm.MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exs) {
            if (!name.equals(methodName)) return null;
            return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
              @Override
              public void visitInvokeDynamicInsn(
                  String name, String desc, Handle bsm, Object... bsmArgs) {
                if (name.contains(nameSubstring)) found[0] = true;
              }
            };
          }
        },
        0);
    return found[0];
  }

  /**
   * Builds a minimal BTraceProbe stub that matches targetJavaClass#targetMethod and reports one
   * OnMethod handler for the given kind with the supplied descriptor. Uses -1 for both
   * returnParameter and durationParameter (absent).
   */
  private static BTraceProbe buildStubProbe(
      final String probeInternalName,
      final String targetJavaClass,
      final String targetMethod,
      final Kind kind,
      final String targetDescriptor) {
    return buildStubProbe(
        probeInternalName, targetJavaClass, targetMethod, kind, targetDescriptor, -1, -1);
  }

  /**
   * Builds a minimal BTraceProbe stub that matches targetJavaClass#targetMethod and reports one
   * OnMethod handler for the given kind with the supplied descriptor. {@code returnParameter} and
   * {@code durationParameter} specify the handler parameter index for {@code @Return} and
   * {@code @Duration} respectively; pass -1 if not used.
   */
  private static BTraceProbe buildStubProbe(
      final String probeInternalName,
      final String targetJavaClass,
      final String targetMethod,
      final Kind kind,
      final String targetDescriptor,
      final int returnParameter,
      final int durationParameter) {

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
    // All other special parameter indices default to -1 (absent) — no @Self etc.

    return buildStubProbe(probeInternalName, targetJavaClass, om);
  }

  private static BTraceProbe buildStubProbeWithType(
      final String probeInternalName,
      final String targetJavaClass,
      final String targetMethod,
      final Kind kind,
      final String targetDescriptor,
      final String typeDeclaration) {
    final Location loc = new Location();
    loc.setValue(kind);
    final OnMethod om = new OnMethod();
    om.setClazz(targetJavaClass);
    om.setMethod(targetMethod);
    om.setLocation(loc);
    om.setTargetName("onProbe");
    om.setTargetDescriptor(targetDescriptor);
    om.setType(typeDeclaration);
    return buildStubProbe(probeInternalName, targetJavaClass, om);
  }

  private static BTraceProbe buildStubProbe(
      final String probeInternalName,
      final String targetJavaClass,
      final String targetMethod,
      final Location location,
      final String targetDescriptor) {
    return buildStubProbe(
        probeInternalName,
        targetJavaClass,
        targetMethod,
        location,
        targetDescriptor,
        -1,
        -1,
        -1,
        -1);
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
    return buildStubProbe(
        probeInternalName,
        targetJavaClass,
        targetMethod,
        location,
        targetDescriptor,
        returnParameter,
        durationParameter,
        targetInstanceParameter,
        targetMethodOrFieldParameter,
        false);
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
      final int targetMethodOrFieldParameter,
      final boolean targetMethodOrFieldFqn) {

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
      om.setTargetMethodOrFieldFqn(targetMethodOrFieldFqn);
    }

    return buildStubProbe(probeInternalName, targetJavaClass, om);
  }

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

  // ---------------------------------------------------------------------------
  // @Return / @Duration helpers
  // ---------------------------------------------------------------------------

  /**
   * Builds a class with a single static method returning the given type using ASM, then patches the
   * class file major version to the given value.
   */
  private static byte[] buildClassWithNonVoidMethod(
      int majorVersion, String internalClassName, String methodName, String returnDescriptor) {
    org.objectweb.asm.ClassWriter cw =
        new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_FRAMES);
    cw.visit(
        org.objectweb.asm.Opcodes.V11,
        org.objectweb.asm.Opcodes.ACC_PUBLIC,
        internalClassName,
        null,
        "java/lang/Object",
        null);
    String methodDesc = "()" + returnDescriptor;
    org.objectweb.asm.MethodVisitor mv =
        cw.visitMethod(
            org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
            methodName,
            methodDesc,
            null,
            null);
    mv.visitCode();
    // emit appropriate return for the type
    switch (returnDescriptor) {
      case "I":
      case "Z":
      case "B":
      case "S":
      case "C":
        mv.visitInsn(org.objectweb.asm.Opcodes.ICONST_0);
        mv.visitInsn(org.objectweb.asm.Opcodes.IRETURN);
        break;
      case "J":
        mv.visitInsn(org.objectweb.asm.Opcodes.LCONST_0);
        mv.visitInsn(org.objectweb.asm.Opcodes.LRETURN);
        break;
      case "F":
        mv.visitInsn(org.objectweb.asm.Opcodes.FCONST_0);
        mv.visitInsn(org.objectweb.asm.Opcodes.FRETURN);
        break;
      case "D":
        mv.visitInsn(org.objectweb.asm.Opcodes.DCONST_0);
        mv.visitInsn(org.objectweb.asm.Opcodes.DRETURN);
        break;
      default: // reference type
        mv.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);
        mv.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
        break;
    }
    mv.visitMaxs(0, 0);
    mv.visitEnd();
    cw.visitEnd();
    byte[] bytes = cw.toByteArray();
    bytes[6] = (byte) (majorVersion >> 8);
    bytes[7] = (byte) (majorVersion & 0xFF);
    return bytes;
  }

  /**
   * Returns the INVOKEDYNAMIC instruction descriptor for the first matching call site, or null if
   * not found.
   */
  private static String getInvokeDynamicDescriptor(
      byte[] classBytes, String methodName, String nameSubstring) {
    final String[] found = {null};
    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(classBytes);
    cr.accept(
        new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
          @Override
          public org.objectweb.asm.MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exs) {
            if (!name.equals(methodName)) return null;
            return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
              @Override
              public void visitInvokeDynamicInsn(
                  String name, String desc, org.objectweb.asm.Handle bsm, Object... bsmArgs) {
                if (name.contains(nameSubstring) && found[0] == null) found[0] = desc;
              }
            };
          }
        },
        0);
    return found[0];
  }

  /** Counts INVOKEDYNAMIC instructions whose name contains the given substring. */
  private static int countInvokeDynamic(
      byte[] classBytes, String methodName, String nameSubstring) {
    int[] count = {0};
    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(classBytes);
    cr.accept(
        new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
          @Override
          public org.objectweb.asm.MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exs) {
            if (!name.equals(methodName)) return null;
            return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
              @Override
              public void visitInvokeDynamicInsn(
                  String name, String desc, org.objectweb.asm.Handle bsm, Object... bsmArgs) {
                if (name.contains(nameSubstring)) count[0]++;
              }
            };
          }
        },
        0);
    return count[0];
  }

  private static List<InvokeDynamicInsnNode> btraceInvokeDynamics(
      byte[] classBytes, String methodName) {
    MethodNode method = treeMethod(classBytes, methodName);
    if (method == null) return Collections.emptyList();
    List<InvokeDynamicInsnNode> result = new ArrayList<>();
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof InvokeDynamicInsnNode
          && ((InvokeDynamicInsnNode) instruction).name.contains("$btrace$")) {
        result.add((InvokeDynamicInsnNode) instruction);
      }
    }
    return result;
  }

  private static List<Integer> instructionOpcodes(byte[] classBytes, String methodName) {
    MethodNode method = treeMethod(classBytes, methodName);
    if (method == null) return Collections.emptyList();
    List<Integer> opcodes = new ArrayList<>();
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction.getOpcode() != -1) {
        opcodes.add(instruction.getOpcode());
      }
    }
    return opcodes;
  }

  private static void assertBTraceBeforeOpcode(
      byte[] classBytes, String methodName, int targetOpcode) {
    MethodNode method = requireTreeMethod(classBytes, methodName);
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof InvokeDynamicInsnNode
          && ((InvokeDynamicInsnNode) instruction).name.contains("$btrace$")) {
        AbstractInsnNode next = nextExecutable(instruction);
        if (next != null && next.getOpcode() == targetOpcode) {
          return;
        }
      }
    }
    fail("Expected BTrace invokedynamic immediately before opcode " + targetOpcode);
  }

  private static void assertBTraceAfterOpcode(
      byte[] classBytes, String methodName, int targetOpcode) {
    MethodNode method = requireTreeMethod(classBytes, methodName);
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction.getOpcode() == targetOpcode) {
        AbstractInsnNode next = nextExecutable(instruction);
        if (next instanceof InvokeDynamicInsnNode
            && ((InvokeDynamicInsnNode) next).name.contains("$btrace$")) {
          return;
        }
      }
    }
    fail("Expected BTrace invokedynamic immediately after opcode " + targetOpcode);
  }

  private static void assertBTraceBetweenOpcodes(
      byte[] classBytes, String methodName, int beforeOpcode, int afterOpcode) {
    MethodNode method = requireTreeMethod(classBytes, methodName);
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction.getOpcode() == beforeOpcode) {
        AbstractInsnNode next = nextExecutable(instruction);
        if (next instanceof InvokeDynamicInsnNode
            && ((InvokeDynamicInsnNode) next).name.contains("$btrace$")) {
          AbstractInsnNode afterBtrace = nextExecutable(next);
          if (afterBtrace != null && afterBtrace.getOpcode() == afterOpcode) {
            return;
          }
        }
      }
    }
    fail("Expected BTrace invokedynamic between opcodes " + beforeOpcode + " and " + afterOpcode);
  }

  private static void assertStoresBeforeOpcode(
      byte[] classBytes, String methodName, int targetOpcode, int... storeOpcodes) {
    MethodNode method = requireTreeMethod(classBytes, methodName);
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction.getOpcode() == targetOpcode) {
        AbstractInsnNode current = previousExecutable(instruction);
        for (int i = storeOpcodes.length - 1; i >= 0; i--) {
          assertNotNull(current, "Expected store opcode " + storeOpcodes[i]);
          assertEquals(storeOpcodes[i], current.getOpcode());
          current = previousExecutable(current);
        }
        return;
      }
    }
    fail("Expected target opcode " + targetOpcode);
  }

  private static void assertLoadsBeforeBTrace(
      byte[] classBytes, String methodName, int... loadOpcodes) {
    MethodNode method = requireTreeMethod(classBytes, methodName);
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof InvokeDynamicInsnNode
          && ((InvokeDynamicInsnNode) instruction).name.contains("$btrace$")) {
        AbstractInsnNode current = previousExecutable(instruction);
        for (int i = loadOpcodes.length - 1; i >= 0; i--) {
          assertNotNull(current, "Expected load opcode " + loadOpcodes[i]);
          assertEquals(loadOpcodes[i], current.getOpcode());
          current = previousExecutable(current);
        }
        return;
      }
    }
    fail("Expected BTrace invokedynamic in method " + methodName);
  }

  private static void assertNanoTimeBeforeOpcode(
      byte[] classBytes, String methodName, int targetOpcode) {
    MethodNode method = requireTreeMethod(classBytes, methodName);
    for (AbstractInsnNode instruction : method.instructions) {
      if (isNanoTimeCall(instruction)) {
        AbstractInsnNode next = nextExecutable(instruction);
        if (next != null && next.getOpcode() == targetOpcode) {
          return;
        }
      }
    }
    fail("Expected System.nanoTime call immediately before opcode " + targetOpcode);
  }

  private static void assertNanoTimeAfterOpcode(
      byte[] classBytes, String methodName, int targetOpcode) {
    MethodNode method = requireTreeMethod(classBytes, methodName);
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction.getOpcode() == targetOpcode) {
        AbstractInsnNode next = nextExecutable(instruction);
        if (isNanoTimeCall(next)) {
          return;
        }
      }
    }
    fail("Expected System.nanoTime call immediately after opcode " + targetOpcode);
  }

  private static int countTryCatchBlocks(byte[] classBytes, String methodName) {
    return requireTreeMethod(classBytes, methodName).tryCatchBlocks.size();
  }

  private static List<AbstractInsnNode> handlerStartsForCatchType(
      byte[] classBytes, String methodName, String internalCatchType) {
    MethodNode method = requireTreeMethod(classBytes, methodName);
    List<AbstractInsnNode> starts = new ArrayList<>();
    method.tryCatchBlocks.forEach(
        block -> {
          if (internalCatchType == null
              ? block.type == null
              : internalCatchType.equals(block.type)) {
            starts.add(nextExecutable(block.handler));
          }
        });
    return starts;
  }

  private static int countOpcode(byte[] classBytes, String methodName, int opcode) {
    int count = 0;
    for (int instructionOpcode : instructionOpcodes(classBytes, methodName)) {
      if (instructionOpcode == opcode) count++;
    }
    return count;
  }

  private static int countFieldInsn(
      byte[] classBytes, String methodName, int opcode, String owner, String fieldName) {
    int count = 0;
    MethodNode method = requireTreeMethod(classBytes, methodName);
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof FieldInsnNode && instruction.getOpcode() == opcode) {
        FieldInsnNode field = (FieldInsnNode) instruction;
        if (owner.equals(field.owner) && fieldName.equals(field.name)) count++;
      }
    }
    return count;
  }

  private static int countTypeInsn(
      byte[] classBytes, String methodName, int opcode, String descriptor) {
    int count = 0;
    MethodNode method = requireTreeMethod(classBytes, methodName);
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof TypeInsnNode && instruction.getOpcode() == opcode) {
        TypeInsnNode type = (TypeInsnNode) instruction;
        if (descriptor.equals(type.desc)) count++;
      }
    }
    return count;
  }

  private static int countIntInsn(byte[] classBytes, String methodName, int opcode, int operand) {
    int count = 0;
    MethodNode method = requireTreeMethod(classBytes, methodName);
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof IntInsnNode && instruction.getOpcode() == opcode) {
        IntInsnNode intInsn = (IntInsnNode) instruction;
        if (intInsn.operand == operand) count++;
      }
    }
    return count;
  }

  private static boolean containsLineNumber(byte[] classBytes, String methodName, int line) {
    MethodNode method = treeMethod(classBytes, methodName);
    if (method == null) return false;
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof LineNumberNode && ((LineNumberNode) instruction).line == line) {
        return true;
      }
    }
    return false;
  }

  private static boolean isNanoTimeCall(AbstractInsnNode instruction) {
    if (!(instruction instanceof MethodInsnNode)) return false;
    MethodInsnNode methodInsn = (MethodInsnNode) instruction;
    return "java/lang/System".equals(methodInsn.owner) && "nanoTime".equals(methodInsn.name);
  }

  private static boolean isBTraceCallBeforeTargetCall(
      byte[] classBytes, String methodName, String targetCallName) {
    List<String> calls = new ArrayList<>();
    ClassReader cr = new ClassReader(classBytes);
    cr.accept(
        new ClassVisitor(Opcodes.ASM9) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exs) {
            if (!name.equals(methodName)) return null;
            return new MethodVisitor(Opcodes.ASM9) {
              @Override
              public void visitInvokeDynamicInsn(
                  String name, String desc, Handle bsm, Object... bsmArgs) {
                if (name.contains("$btrace$")) calls.add("btrace");
              }

              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String desc, boolean isInterface) {
                if (name.equals(targetCallName)) calls.add("target");
              }
            };
          }
        },
        0);
    assertTrue(calls.contains("btrace"), "Expected BTrace invokedynamic marker in call sequence");
    assertTrue(calls.contains("target"), "Expected target call marker in call sequence");
    return calls.indexOf("btrace") < calls.indexOf("target");
  }

  private static boolean isBTraceCallAfterTargetCall(
      byte[] classBytes, String methodName, String targetCallName) {
    List<String> calls = new ArrayList<>();
    ClassReader cr = new ClassReader(classBytes);
    cr.accept(
        new ClassVisitor(Opcodes.ASM9) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exs) {
            if (!name.equals(methodName)) return null;
            return new MethodVisitor(Opcodes.ASM9) {
              @Override
              public void visitInvokeDynamicInsn(
                  String name, String desc, Handle bsm, Object... bsmArgs) {
                if (name.contains("$btrace$")) calls.add("btrace");
              }

              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String desc, boolean isInterface) {
                if (name.equals(targetCallName)) calls.add("target");
              }
            };
          }
        },
        0);
    assertTrue(calls.contains("btrace"), "Expected BTrace invokedynamic marker in call sequence");
    assertTrue(calls.contains("target"), "Expected target call marker in call sequence");
    return calls.indexOf("btrace") > calls.indexOf("target");
  }

  private static int countMethodCalls(
      byte[] classBytes, String methodName, String ownerName, String targetMethodName) {
    int[] count = {0};
    ClassReader cr = new ClassReader(classBytes);
    cr.accept(
        new ClassVisitor(Opcodes.ASM9) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exs) {
            if (!name.equals(methodName)) return null;
            return new MethodVisitor(Opcodes.ASM9) {
              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String desc, boolean isInterface) {
                if (ownerName.equals(owner) && targetMethodName.equals(name)) count[0]++;
              }
            };
          }
        },
        0);
    return count[0];
  }

  private static boolean containsLdc(byte[] classBytes, String methodName, String expectedValue) {
    boolean[] found = {false};
    ClassReader cr = new ClassReader(classBytes);
    cr.accept(
        new ClassVisitor(Opcodes.ASM9) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exs) {
            if (!name.equals(methodName)) return null;
            return new MethodVisitor(Opcodes.ASM9) {
              @Override
              public void visitLdcInsn(Object value) {
                if (expectedValue.equals(value)) found[0] = true;
              }
            };
          }
        },
        0);
    return found[0];
  }

  private static boolean loadsReferenceBeforeLdcAndBTrace(
      byte[] classBytes, String methodName, String expectedLdc) {
    MethodNode method = treeMethod(classBytes, methodName);
    if (method == null) return false;
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof InvokeDynamicInsnNode
          && ((InvokeDynamicInsnNode) instruction).name.contains("$btrace$")) {
        AbstractInsnNode previous = previousExecutable(instruction);
        if (!(previous instanceof LdcInsnNode)
            || !expectedLdc.equals(((LdcInsnNode) previous).cst)) {
          continue;
        }
        AbstractInsnNode beforeLdc = previousExecutable(previous);
        return beforeLdc instanceof VarInsnNode && beforeLdc.getOpcode() == Opcodes.ALOAD;
      }
    }
    return false;
  }

  private static boolean loadsNullBeforeBTrace(byte[] classBytes, String methodName) {
    MethodNode method = treeMethod(classBytes, methodName);
    if (method == null) return false;
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof InvokeDynamicInsnNode
          && ((InvokeDynamicInsnNode) instruction).name.contains("$btrace$")) {
        AbstractInsnNode previous = previousExecutable(instruction);
        return previous instanceof InsnNode && previous.getOpcode() == Opcodes.ACONST_NULL;
      }
    }
    return false;
  }

  private static boolean loadsLongBeforeBTrace(byte[] classBytes, String methodName) {
    MethodNode method = treeMethod(classBytes, methodName);
    if (method == null) return false;
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof InvokeDynamicInsnNode
          && ((InvokeDynamicInsnNode) instruction).name.contains("$btrace$")) {
        AbstractInsnNode previous = previousExecutable(instruction);
        return previous instanceof VarInsnNode && previous.getOpcode() == Opcodes.LLOAD;
      }
    }
    return false;
  }

  private static MethodNode treeMethod(byte[] classBytes, String methodName) {
    ClassNode cn = new ClassNode();
    ClassReader cr = new ClassReader(classBytes);
    cr.accept(cn, 0);
    for (MethodNode method : cn.methods) {
      if (method.name.equals(methodName)) return method;
    }
    return null;
  }

  private static MethodNode requireTreeMethod(byte[] classBytes, String methodName) {
    MethodNode method = treeMethod(classBytes, methodName);
    assertNotNull(method, "Expected method " + methodName);
    return method;
  }

  private static AbstractInsnNode previousExecutable(AbstractInsnNode instruction) {
    AbstractInsnNode current = instruction.getPrevious();
    while (current != null
        && (current.getType() == AbstractInsnNode.LABEL
            || current.getType() == AbstractInsnNode.LINE
            || current.getType() == AbstractInsnNode.FRAME)) {
      current = current.getPrevious();
    }
    return current;
  }

  private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
    AbstractInsnNode current = instruction.getNext();
    while (current != null
        && (current.getType() == AbstractInsnNode.LABEL
            || current.getType() == AbstractInsnNode.LINE
            || current.getType() == AbstractInsnNode.FRAME)) {
      current = current.getNext();
    }
    return current;
  }

  /**
   * Scans local variable store instructions in the named method and returns the set of slot indices
   * that are written to. Used to verify no slot collisions.
   */
  private static java.util.Set<Integer> getStoredLocalSlots(byte[] classBytes, String methodName) {
    java.util.Set<Integer> slots = new java.util.HashSet<>();
    org.objectweb.asm.ClassReader cr = new org.objectweb.asm.ClassReader(classBytes);
    cr.accept(
        new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
          @Override
          public org.objectweb.asm.MethodVisitor visitMethod(
              int access, String name, String desc, String sig, String[] exs) {
            if (!name.equals(methodName)) return null;
            return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
              @Override
              public void visitVarInsn(int opcode, int varIndex) {
                // All xSTORE opcodes
                if (opcode == org.objectweb.asm.Opcodes.ISTORE
                    || opcode == org.objectweb.asm.Opcodes.LSTORE
                    || opcode == org.objectweb.asm.Opcodes.FSTORE
                    || opcode == org.objectweb.asm.Opcodes.DSTORE
                    || opcode == org.objectweb.asm.Opcodes.ASTORE) {
                  slots.add(varIndex);
                }
              }
            };
          }
        },
        0);
    return slots;
  }

  // ---------------------------------------------------------------------------
  // @Return tests
  // The following tests instrument class file version 70 (Java 26+). Each test calls
  // requireJdk26ForVersion70() to skip on JDK < 26 — the ClassFile API can only parse
  // class files up to the running JDK's own major version (JDK 24→v68, JDK 25→v69, JDK 26→v70).
  // The class-level @EnabledForJreRange(min = JRE.JAVA_24) covers only basic parsing tests.
  // ---------------------------------------------------------------------------

  @Test
  void returnProbeInjectedWithReturnValueInt() {
    requireJdk26ForVersion70();
    // Build class with method returning int
    byte[] classBytes = buildClassWithNonVoidMethod(70, "com/example/Target", "compute", "I");
    // Handler descriptor: (I)V — index 0 is the return value
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "compute",
            Kind.RETURN,
            "(I)V",
            0, // returnParameter at index 0
            -1);

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for @Return probe on int method");
    byte[] readable = patchVersion(result, 65);
    String desc = getInvokeDynamicDescriptor(readable, "compute", "$btrace$");
    assertNotNull(desc, "Expected INVOKEDYNAMIC in instrumented compute method");
    // Descriptor must include int parameter at index 0
    assertTrue(
        desc.startsWith("(I"), "Expected int parameter at position 0 in descriptor, got: " + desc);
  }

  @Test
  void returnProbeInjectedWithReturnValueLong() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithNonVoidMethod(70, "com/example/Target", "compute", "J");
    // Handler descriptor: (J)V — index 0 is long return value (2 slots)
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "compute",
            Kind.RETURN,
            "(J)V",
            0, // returnParameter at index 0
            -1);

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result);
    String desc = getInvokeDynamicDescriptor(patchVersion(result, 65), "compute", "$btrace$");
    assertNotNull(desc);
    assertTrue(desc.startsWith("(J"), "Expected long parameter in descriptor, got: " + desc);
  }

  @Test
  void returnProbeBoxesBooleanForObjectHandler() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithNonVoidMethod(70, "com/example/Target", "compute", "Z");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "compute",
            Kind.RETURN,
            "(Ljava/lang/Object;)V",
            0,
            -1);

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result);
    byte[] readable = patchVersion(result, 65);
    String desc = getInvokeDynamicDescriptor(readable, "compute", "$btrace$");
    assertEquals("(Ljava/lang/Object;)V", desc);
    assertEquals(1, countMethodCalls(readable, "compute", "java/lang/Boolean", "valueOf"));
    assertEquals(0, countMethodCalls(readable, "compute", "java/lang/Integer", "valueOf"));
  }

  @Test
  void returnProbeSkippedForVoidMethod() {
    requireJdk26ForVersion70();
    // void method: @Return handler should be silently skipped (no INVOKEDYNAMIC emitted)
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "doWork");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "doWork",
            Kind.RETURN,
            "(I)V", // handler expects int return value, but method is void
            0, // returnParameter
            -1);

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    // Either null (nothing matched) or result with zero INVOKEDYNAMIC instructions
    if (result != null) {
      byte[] loadable = patchVersion(result, 65);
      int count = countInvokeDynamic(loadable, "doWork", "$btrace$");
      assertEquals(0, count, "Expected no INVOKEDYNAMIC for @Return on void method");

      // Load instrumented bytes to confirm no VerifyError from stack corruption
      assertDoesNotThrow(
          () -> {
            ClassLoader cl =
                new ClassLoader(null) {
                  @Override
                  protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if ("com.example.Target".equals(name)) {
                      return defineClass(name, loadable, 0, loadable.length);
                    }
                    throw new ClassNotFoundException(name);
                  }
                };
            cl.loadClass("com.example.Target");
          },
          "Instrumented void-return class must load without VerifyError");
    }
  }

  // ---------------------------------------------------------------------------
  // @Duration tests
  // ---------------------------------------------------------------------------

  @Test
  void durationProbeInjectedOnNormalReturn() {
    requireJdk26ForVersion70();
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "timed");
    // Handler descriptor: (J)V — index 0 is duration (long)
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "timed",
            Kind.RETURN,
            "(J)V",
            -1, // no @Return
            0); // durationParameter at index 0

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for @Duration probe");
    String desc = getInvokeDynamicDescriptor(patchVersion(result, 65), "timed", "$btrace$");
    assertNotNull(desc, "Expected INVOKEDYNAMIC in instrumented timed method");
    assertTrue(
        desc.startsWith("(J"), "Expected long (duration) parameter in descriptor, got: " + desc);
  }

  @Test
  void durationProbeInjectedOnExceptionExit() {
    requireJdk26ForVersion70();
    // Build a class whose method can throw (we'll verify the exception handler block exists)
    byte[] classBytes = buildClassWithMethod(70, "com/example/Target", "risky");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "risky",
            Kind.RETURN,
            "(J)V",
            -1, // no @Return
            0); // durationParameter

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes for @Duration probe");
    // The instrumented class must have an exception table entry (for the finally-block pattern).
    // We verify by checking that an exception handler INVOKEDYNAMIC exists
    // (the exception handler block contains its own INVOKEDYNAMIC for the duration probe).
    byte[] readable = patchVersion(result, 65);
    int indyCount = countInvokeDynamic(readable, "risky", "$btrace$");
    // There should be 2 INVOKEDYNAMIC calls: one for normal return, one in exception handler
    assertEquals(
        2,
        indyCount,
        "Expected 2 INVOKEDYNAMIC calls (normal exit + exception handler), got: " + indyCount);
  }

  // ---------------------------------------------------------------------------
  // @Return + @Duration combined test
  // ---------------------------------------------------------------------------

  @Test
  void returnAndDurationSlotsDoNotCollide() {
    requireJdk26ForVersion70();
    // Method returns int; handler has both @Return (int, index 0) and @Duration (long, index 1)
    byte[] classBytes = buildClassWithNonVoidMethod(70, "com/example/Target", "combined", "I");
    BTraceProbe probe =
        buildStubProbe(
            "com/example/MyTrace",
            "com.example.Target",
            "combined",
            Kind.RETURN,
            "(IJ)V", // @Return at 0, @Duration at 1
            0, // returnParameter
            1); // durationParameter

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, Collections.singletonList(probe));

    assertNotNull(result, "Expected instrumented bytes");
    byte[] readable = patchVersion(result, 65);

    // Verify INVOKEDYNAMIC is present
    assertTrue(containsInvokeDynamic(readable, "combined", "$btrace$"));

    // Verify no slot collision: @Return slot and @Duration slot must be distinct
    java.util.Set<Integer> storedSlots = getStoredLocalSlots(readable, "combined");
    // There must be at least 2 store instructions (one for retVal, one for duration)
    assertTrue(
        storedSlots.size() >= 2,
        "Expected at least 2 distinct local slots (retVal + duration), got: " + storedSlots);
  }
}
