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

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.PseudoInstruction;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.MonitorInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import io.btrace.core.MethodID;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Sampled;
import io.btrace.core.annotations.Where;

import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrumentation backend for class file versions that ASM cannot parse (&gt; 69, i.e. Java 26+).
 * Uses the JDK ClassFile API ({@code java.lang.classfile.*}), available since JDK 24.
 *
 * <p>Supported probe kinds: {@link Kind#ENTRY}, {@link Kind#RETURN}, {@link Kind#CALL}, {@link
 * Kind#LINE}, {@link Kind#FIELD_GET}, {@link Kind#FIELD_SET}, {@link Kind#ARRAY_GET}, {@link
 * Kind#ARRAY_SET}, {@link Kind#CHECKCAST}, {@link Kind#INSTANCEOF}, {@link Kind#THROW}, {@link
 * Kind#CATCH}, {@link Kind#ERROR}, {@link Kind#NEWARRAY}, and {@link Kind#NEW}. Exception entry
 * ({@code CATCH}) and uncaught-exit ({@code ERROR}) handlers support {@code @TargetInstance} for
 * the caught/escaping throwable and {@code @Duration} for {@code ERROR}. Array-allocation
 * ({@code NEWARRAY}) handlers support ordinary arguments (element type {@code String} and
 * dimension count {@code int}) and {@code @Return} on {@code AFTER} probes. Object-allocation
 * ({@code NEW}) handlers support an ordinary {@code String} argument (allocated type name) and
 * {@code @Return} on {@code AFTER} probes; the {@code AFTER} probe fires after the constructor
 * call completes so the reference is fully initialized.
 *
 * <p>Synchronization probe kinds ({@code SYNC_ENTRY}, {@code SYNC_EXIT}) instrument
 * {@code monitorenter} and {@code monitorexit} bytecodes. {@code @TargetInstance} receives the
 * lock object. {@code @Duration} is not supported for synchronization probes.
 *
 * <p>Level guards are enforced at the MethodHandle layer ({@code HandlerRepositoryImpl}); the
 * INVOKEDYNAMIC instruction is always emitted and the guard fires at runtime. Const and Adaptive
 * sampling for {@code ENTRY}, {@code RETURN}, and {@code ERROR} probes is implemented with
 * {@link MethodTracker#hit} / {@link MethodTracker#hitAdaptive} checks emitted as bytecode
 * immediately before the probe call.
 */
public final class ClassFileApiBackend implements InstrumentationBackend {
  private static final Logger log = LoggerFactory.getLogger(ClassFileApiBackend.class);

  // BSM descriptor: (Lookup, String, MethodType, String) -> CallSite
  private static final MethodTypeDesc BSM_TYPE =
      MethodTypeDesc.of(
          ClassDesc.of("java.lang.invoke.CallSite"),
          ClassDesc.of("java.lang.invoke.MethodHandles$Lookup"),
          ClassDesc.of("java.lang.String"),
          ClassDesc.of("java.lang.invoke.MethodType"),
          ClassDesc.of("java.lang.String"));

  private static final ClassDesc INDY_DISPATCHER = ClassDesc.of("io.btrace.runtime.IndyDispatcher");
  private static final ClassDesc CD_METHOD_TRACKER =
      ClassDesc.ofInternalName("io/btrace/instr/MethodTracker");
  private static final MethodTypeDesc HIT_DESC = MethodTypeDesc.ofDescriptor("(I)Z");

  @Override
  public boolean supports(int classFileMajorVersion) {
    return classFileMajorVersion > AsmInstrumentationBackend.MAX_ASM_MAJOR_VERSION;
  }

  @Override
  public byte[] instrument(
      ClassLoader loader, byte[] classfileBuffer, Collection<BTraceProbe> probes) {
    if (probes.isEmpty()) return null;

    ClassFile cf = ClassFile.of();
    ClassModel classModel;
    try {
      classModel = cf.parse(classfileBuffer);
    } catch (Exception e) {
      log.warn("ClassFile API failed to parse class; skipping instrumentation", e);
      return null;
    }

    String internalName = classModel.thisClass().asInternalName();
    String javaClassName = internalName.replace('/', '.');
    Collection<String> annoTypes = collectAnnotationTypes(classModel);
    ClassMeta meta = buildClassMeta(javaClassName, internalName, annoTypes, loader);

    List<ProbeHandler> handlers = collectHandlers(probes, meta);
    if (handlers.isEmpty()) return null;

    List<ProbeHandler> entryHandlers = new ArrayList<>();
    List<ProbeHandler> returnHandlers = new ArrayList<>();
    List<ProbeHandler> callHandlers = new ArrayList<>();
    List<ProbeHandler> lineHandlers = new ArrayList<>();
    List<ProbeHandler> fieldGetHandlers = new ArrayList<>();
    List<ProbeHandler> fieldSetHandlers = new ArrayList<>();
    List<ProbeHandler> arrayGetHandlers = new ArrayList<>();
    List<ProbeHandler> arraySetHandlers = new ArrayList<>();
    List<ProbeHandler> checkcastHandlers = new ArrayList<>();
    List<ProbeHandler> instanceofHandlers = new ArrayList<>();
    List<ProbeHandler> throwHandlers = new ArrayList<>();
    List<ProbeHandler> catchHandlers = new ArrayList<>();
    List<ProbeHandler> errorHandlers = new ArrayList<>();
    List<ProbeHandler> newarrayHandlers = new ArrayList<>();
    List<ProbeHandler> newobjHandlers = new ArrayList<>();
    List<ProbeHandler> syncEntryHandlers = new ArrayList<>();
    List<ProbeHandler> syncExitHandlers = new ArrayList<>();
    for (ProbeHandler ph : handlers) {
      Kind kind = ph.om.getLocation().getValue();
      if (kind == Kind.ENTRY) entryHandlers.add(ph);
      else if (kind == Kind.RETURN) returnHandlers.add(ph);
      else if (kind == Kind.CALL) callHandlers.add(ph);
      else if (kind == Kind.LINE) lineHandlers.add(ph);
      else if (kind == Kind.FIELD_GET) fieldGetHandlers.add(ph);
      else if (kind == Kind.FIELD_SET) fieldSetHandlers.add(ph);
      else if (kind == Kind.ARRAY_GET) arrayGetHandlers.add(ph);
      else if (kind == Kind.ARRAY_SET) arraySetHandlers.add(ph);
      else if (kind == Kind.CHECKCAST) checkcastHandlers.add(ph);
      else if (kind == Kind.INSTANCEOF) instanceofHandlers.add(ph);
      else if (kind == Kind.THROW) throwHandlers.add(ph);
      else if (kind == Kind.CATCH) catchHandlers.add(ph);
      else if (kind == Kind.ERROR) errorHandlers.add(ph);
      else if (kind == Kind.NEWARRAY) newarrayHandlers.add(ph);
      else if (kind == Kind.NEW) newobjHandlers.add(ph);
      else if (kind == Kind.SYNC_ENTRY) syncEntryHandlers.add(ph);
      else if (kind == Kind.SYNC_EXIT) syncExitHandlers.add(ph);
      else log.debug("Skipping unsupported probe kind {} for class {}", kind, javaClassName);
    }
    if (entryHandlers.isEmpty()
        && returnHandlers.isEmpty()
        && callHandlers.isEmpty()
        && lineHandlers.isEmpty()
        && fieldGetHandlers.isEmpty()
        && fieldSetHandlers.isEmpty()
        && arrayGetHandlers.isEmpty()
        && arraySetHandlers.isEmpty()
        && checkcastHandlers.isEmpty()
        && instanceofHandlers.isEmpty()
        && throwHandlers.isEmpty()
        && catchHandlers.isEmpty()
        && errorHandlers.isEmpty()
        && newarrayHandlers.isEmpty()
        && newobjHandlers.isEmpty()
        && syncEntryHandlers.isEmpty()
        && syncExitHandlers.isEmpty()) {
      return null;
    }

    boolean[] anyMatch = {false};
    byte[] result =
        cf.transformClass(
            classModel,
            buildClassTransform(
                loader,
                javaClassName,
                entryHandlers,
                returnHandlers,
                callHandlers,
                lineHandlers,
                fieldGetHandlers,
                fieldSetHandlers,
                arrayGetHandlers,
                arraySetHandlers,
                checkcastHandlers,
                instanceofHandlers,
                throwHandlers,
                catchHandlers,
                errorHandlers,
                newarrayHandlers,
                newobjHandlers,
                syncEntryHandlers,
                syncExitHandlers,
                anyMatch));
    return anyMatch[0] ? result : null;
  }

  private static final class ProbeHandler {
    final BTraceProbe probe;
    final OnMethod om;

    ProbeHandler(BTraceProbe probe, OnMethod om) {
      this.probe = probe;
      this.om = om;
    }
  }

  private static final class CallContext {
    final InvokeInstruction instruction;
    final Type[] argumentTypes;
    final boolean staticCall;
    final int[] argumentSlots;
    final int receiverSlot;

    CallContext(
        InvokeInstruction instruction,
        Type[] argumentTypes,
        boolean staticCall,
        int[] argumentSlots,
        int receiverSlot) {
      this.instruction = instruction;
      this.argumentTypes = argumentTypes;
      this.staticCall = staticCall;
      this.argumentSlots = argumentSlots;
      this.receiverSlot = receiverSlot;
    }
  }

  private static final class FieldGetContext {
    final FieldInstruction instruction;
    final Type fieldType;
    final boolean staticAccess;
    final int ownerSlot;

    FieldGetContext(FieldInstruction instruction, Type fieldType, boolean staticAccess, int ownerSlot) {
      this.instruction = instruction;
      this.fieldType = fieldType;
      this.staticAccess = staticAccess;
      this.ownerSlot = ownerSlot;
    }
  }

  private static final class FieldSetContext {
    final FieldInstruction instruction;
    final Type fieldType;
    final boolean staticAccess;
    final int ownerSlot;
    final int valueSlot;

    FieldSetContext(
        FieldInstruction instruction,
        Type fieldType,
        boolean staticAccess,
        int ownerSlot,
        int valueSlot) {
      this.instruction = instruction;
      this.fieldType = fieldType;
      this.staticAccess = staticAccess;
      this.ownerSlot = ownerSlot;
      this.valueSlot = valueSlot;
    }
  }

  private static final class ArrayGetContext {
    final Type arrayType;
    final Type elementType;
    final TypeKind elementKind;
    final int arraySlot;
    final int indexSlot;

    ArrayGetContext(
        Type arrayType, Type elementType, TypeKind elementKind, int arraySlot, int indexSlot) {
      this.arrayType = arrayType;
      this.elementType = elementType;
      this.elementKind = elementKind;
      this.arraySlot = arraySlot;
      this.indexSlot = indexSlot;
    }
  }

  private static final class ArraySetContext {
    final Type arrayType;
    final Type elementType;
    final TypeKind elementKind;
    final int arraySlot;
    final int indexSlot;
    final int valueSlot;

    ArraySetContext(
        Type arrayType,
        Type elementType,
        TypeKind elementKind,
        int arraySlot,
        int indexSlot,
        int valueSlot) {
      this.arrayType = arrayType;
      this.elementType = elementType;
      this.elementKind = elementKind;
      this.arraySlot = arraySlot;
      this.indexSlot = indexSlot;
      this.valueSlot = valueSlot;
    }
  }

  private static final class TypeCheckContext {
    final String typeName;
    final int targetSlot;

    TypeCheckContext(String typeName, int targetSlot) {
      this.typeName = typeName;
      this.targetSlot = targetSlot;
    }
  }

  private static final class ThrowContext {
    final int throwableSlot;

    ThrowContext(int throwableSlot) {
      this.throwableSlot = throwableSlot;
    }
  }

  private static final class NewArrayContext {
    final String extName;
    final int dims;
    final Type arrayType;
    final int returnSlot;

    NewArrayContext(String extName, int dims, Type arrayType, int returnSlot) {
      this.extName = extName;
      this.dims = dims;
      this.arrayType = arrayType;
      this.returnSlot = returnSlot;
    }
  }

  private static final class PendingNew {
    final String extName;
    final String internalName;
    final List<ProbeHandler> afterHandlers;

    PendingNew(String extName, String internalName, List<ProbeHandler> afterHandlers) {
      this.extName = extName;
      this.internalName = internalName;
      this.afterHandlers = afterHandlers;
    }
  }

  private static Collection<String> collectAnnotationTypes(ClassModel classModel) {
    return classModel
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .map(
            attr ->
                attr.annotations().stream()
                    .map(
                        a -> {
                          String desc = a.classSymbol().descriptorString();
                          // ClassFile API returns annotation class descriptors; probe matching
                          // expects dot-separated Java names
                          return desc.substring(1, desc.length() - 1).replace('/', '.');
                        })
                    .collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }

  private static ClassMeta buildClassMeta(
      String javaClassName, String internalName, Collection<String> annoTypes, ClassLoader loader) {
    return new ClassMeta() {
      @Override
      public String getJavaClassName() {
        return javaClassName;
      }

      @Override
      public String getInternalName() {
        return internalName;
      }

      @Override
      public Collection<String> getAnnotationTypes() {
        return annoTypes;
      }

      @Override
      public ClassLoader getClassLoader() {
        return loader;
      }
    };
  }

  private static List<ProbeHandler> collectHandlers(
      Collection<BTraceProbe> probes, ClassMeta meta) {
    List<ProbeHandler> result = new ArrayList<>();
    for (BTraceProbe probe : probes) {
      for (OnMethod om : probe.getApplicableHandlers(meta)) {
        result.add(new ProbeHandler(probe, om));
      }
    }
    return result;
  }

  private static ClassTransform buildClassTransform(
      ClassLoader loader,
      String javaClassName,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers,
      List<ProbeHandler> callHandlers,
      List<ProbeHandler> lineHandlers,
      List<ProbeHandler> fieldGetHandlers,
      List<ProbeHandler> fieldSetHandlers,
      List<ProbeHandler> arrayGetHandlers,
      List<ProbeHandler> arraySetHandlers,
      List<ProbeHandler> checkcastHandlers,
      List<ProbeHandler> instanceofHandlers,
      List<ProbeHandler> throwHandlers,
      List<ProbeHandler> catchHandlers,
      List<ProbeHandler> errorHandlers,
      List<ProbeHandler> newarrayHandlers,
      List<ProbeHandler> newobjHandlers,
      List<ProbeHandler> syncEntryHandlers,
      List<ProbeHandler> syncExitHandlers,
      boolean[] anyMatch) {
    return (classBuilder, classElement) -> {
      if (classElement instanceof MethodModel mm) {
        String methodName = mm.methodName().stringValue();
        String methodDesc = mm.methodType().stringValue();
        Type methodReturnType = Type.getReturnType(methodDesc);
        boolean isStatic = mm.flags().has(AccessFlag.STATIC);
        List<ProbeHandler> mEntry = filterForMethod(entryHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mReturn = filterForMethod(returnHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mCall = filterForMethod(callHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mLine = filterForMethod(lineHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mFieldGet =
            filterForMethod(fieldGetHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mFieldSet =
            filterForMethod(fieldSetHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mArrayGet =
            filterForMethod(arrayGetHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mArraySet =
            filterForMethod(arraySetHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mCheckcast =
            filterForMethod(checkcastHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mInstanceof =
            filterForMethod(instanceofHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mThrow = filterForMethod(throwHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mCatch = filterForMethod(catchHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mError = filterForMethod(errorHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mNewarray =
            filterForMethod(newarrayHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mNewobj =
            filterForMethod(newobjHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mSyncEntry =
            filterForMethod(syncEntryHandlers, methodName, methodDesc, loader);
        List<ProbeHandler> mSyncExit =
            filterForMethod(syncExitHandlers, methodName, methodDesc, loader);
        if (!mEntry.isEmpty()
            || !mReturn.isEmpty()
            || !mCall.isEmpty()
            || !mLine.isEmpty()
            || !mFieldGet.isEmpty()
            || !mFieldSet.isEmpty()
            || !mArrayGet.isEmpty()
            || !mArraySet.isEmpty()
            || !mCheckcast.isEmpty()
            || !mInstanceof.isEmpty()
            || !mThrow.isEmpty()
            || !mCatch.isEmpty()
            || !mError.isEmpty()
            || !mNewarray.isEmpty()
            || !mNewobj.isEmpty()
            || !mSyncEntry.isEmpty()
            || !mSyncExit.isEmpty()) {
          if (!mEntry.isEmpty() || !mReturn.isEmpty()) {
            anyMatch[0] = true;
          }
          classBuilder.transformMethod(
              mm,
              buildMethodTransform(
                  loader,
                  javaClassName,
                  methodName,
                  methodDesc,
                  methodReturnType,
                  isStatic,
                  mEntry,
                  mReturn,
                  mCall,
                  mLine,
                  mFieldGet,
                  mFieldSet,
                  mArrayGet,
                  mArraySet,
                  mCheckcast,
                  mInstanceof,
                  mThrow,
                  mCatch,
                  mError,
                  mNewarray,
                  mNewobj,
                  mSyncEntry,
                  mSyncExit,
                  anyMatch));
        } else {
          classBuilder.with(classElement);
        }
      } else {
        classBuilder.with(classElement);
      }
    };
  }

  private static MethodTransform buildMethodTransform(
      ClassLoader loader,
      String javaClassName,
      String methodName,
      String methodDesc,
      Type methodReturnType,
      boolean isStatic,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers,
      List<ProbeHandler> callHandlers,
      List<ProbeHandler> lineHandlers,
      List<ProbeHandler> fieldGetHandlers,
      List<ProbeHandler> fieldSetHandlers,
      List<ProbeHandler> arrayGetHandlers,
      List<ProbeHandler> arraySetHandlers,
      List<ProbeHandler> checkcastHandlers,
      List<ProbeHandler> instanceofHandlers,
      List<ProbeHandler> throwHandlers,
      List<ProbeHandler> catchHandlers,
      List<ProbeHandler> errorHandlers,
      List<ProbeHandler> newarrayHandlers,
      List<ProbeHandler> newobjHandlers,
      List<ProbeHandler> syncEntryHandlers,
      List<ProbeHandler> syncExitHandlers,
      boolean[] anyMatch) {
    return (methodBuilder, methodElement) -> {
      if (methodElement instanceof CodeModel cm) {
        // Pre-scan to map handler labels to their catch types for CATCH probe injection.
        // ExceptionCatch elements may appear after the corresponding LabelTarget in the element
        // stream, so we scan the full CodeModel before transforming.
        // A multi-catch block (catch (A | B e)) compiles to multiple ExceptionCatch entries
        // sharing one handler label — store all types so canEmitCatchProbe checks each.
        Map<Label, List<String>> catchHandlerTypes = new HashMap<>();
        if (!catchHandlers.isEmpty()) {
          for (CodeElement ce : cm) {
            if (ce instanceof ExceptionCatch ec) {
              ec.catchType()
                  .ifPresent(
                      ct ->
                          catchHandlerTypes
                              .computeIfAbsent(ec.handler(), k -> new ArrayList<>())
                              .add(ct.asInternalName()));
            }
          }
        }
        methodBuilder.transformCode(
            cm,
            buildCodeTransform(
                loader,
                javaClassName,
                methodName,
                methodDesc,
                methodReturnType,
                isStatic,
                entryHandlers,
                returnHandlers,
                callHandlers,
                lineHandlers,
                fieldGetHandlers,
                fieldSetHandlers,
                arrayGetHandlers,
                arraySetHandlers,
                checkcastHandlers,
                instanceofHandlers,
                throwHandlers,
                catchHandlers,
                errorHandlers,
                newarrayHandlers,
                newobjHandlers,
                syncEntryHandlers,
                syncExitHandlers,
                catchHandlerTypes,
                anyMatch));
      } else {
        methodBuilder.with(methodElement);
      }
    };
  }

  private static CodeTransform buildCodeTransform(
      ClassLoader loader,
      String javaClassName,
      String methodName,
      String methodDesc,
      Type methodReturnType,
      boolean isStatic,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers,
      List<ProbeHandler> callHandlers,
      List<ProbeHandler> lineHandlers,
      List<ProbeHandler> fieldGetHandlers,
      List<ProbeHandler> fieldSetHandlers,
      List<ProbeHandler> arrayGetHandlers,
      List<ProbeHandler> arraySetHandlers,
      List<ProbeHandler> checkcastHandlers,
      List<ProbeHandler> instanceofHandlers,
      List<ProbeHandler> throwHandlers,
      List<ProbeHandler> catchHandlers,
      List<ProbeHandler> errorHandlers,
      List<ProbeHandler> newarrayHandlers,
      List<ProbeHandler> newobjHandlers,
      List<ProbeHandler> syncEntryHandlers,
      List<ProbeHandler> syncExitHandlers,
      Map<Label, List<String>> catchHandlerTypes,
      boolean[] anyMatch) {

    // hasDurationReturn: RETURN probes need duration → compute nanoTime on each RETURN path
    boolean hasDurationReturn =
        returnHandlers.stream().anyMatch(ph -> ph.om.getDurationParameter() != -1);
    // hasDuration: any probe needs an entry timestamp (RETURN @Duration or ERROR @Duration)
    boolean hasDuration =
        hasDurationReturn || errorHandlers.stream().anyMatch(ph -> ph.om.getDurationParameter() != -1);
    // needsExceptionHandler: an exception-exit handler must be synthesized
    boolean needsExceptionHandler = hasDuration || !errorHandlers.isEmpty();
    boolean hasReturnParam =
        returnHandlers.stream().anyMatch(ph -> ph.om.getReturnParameter() != -1);

    // Sampling support: pre-register counters for sampled ENTRY/RETURN/ERROR handlers.
    // MethodTracker.registerCounter is idempotent — safe to call multiple times with same id.
    String internalClassName = javaClassName.replace('.', '/');
    boolean hasSampledHandlers =
        entryHandlers.stream().anyMatch(ph -> ph.om.getSamplerKind() != Sampled.Sampler.None)
            || returnHandlers.stream().anyMatch(ph -> ph.om.getSamplerKind() != Sampled.Sampler.None)
            || errorHandlers.stream().anyMatch(ph -> ph.om.getSamplerKind() != Sampled.Sampler.None);
    final int samplingMethodId;
    if (hasSampledHandlers) {
      samplingMethodId = MethodID.getMethodId(internalClassName, methodName, methodDesc);
      registerSampledCounters(entryHandlers, samplingMethodId);
      registerSampledCounters(returnHandlers, samplingMethodId);
      registerSampledCounters(errorHandlers, samplingMethodId);
    } else {
      samplingMethodId = -1;
    }

    return new CodeTransform() {
      private final int[] entryTsSlot = {-1};
      private final int[] retValSlot = {-1};
      private final int[] durationSlot = {-1};
      private final boolean[] entryInjected = {false};
      private Label startLabel;
      private int pendingLine = -1;
      private int lastLine = -1;
      private final Deque<PendingNew> pendingNewStack = new ArrayDeque<>();

      @Override
      public void atStart(CodeBuilder cb) {
        if (needsExceptionHandler) {
          startLabel = cb.newLabel();
          // labelBinding deferred to accept() so it is emitted at the first real instruction
        }
      }

      @Override
      public void accept(CodeBuilder cb, CodeElement ce) {
        if (ce instanceof LineNumber lineNumber) {
          if (lastLine != -1) {
            emitLineHandlers(cb, Where.AFTER, lastLine);
          }
          pendingLine = lineNumber.line();
          lastLine = lineNumber.line();
          cb.with(ce);
          return;
        }

        if (!entryInjected[0] && !(ce instanceof PseudoInstruction)) {
          entryInjected[0] = true;
          if (needsExceptionHandler) {
            // Bind try-region start at the first real instruction
            cb.labelBinding(startLabel);
          }
          if (hasDuration) {
            entryTsSlot[0] = cb.allocateLocal(TypeKind.LONG);
            cb.invokestatic(
                ClassDesc.ofInternalName("java/lang/System"),
                "nanoTime",
                MethodTypeDesc.ofDescriptor("()J"));
            cb.storeLocal(TypeKind.LONG, entryTsSlot[0]);
          }
          for (ProbeHandler ph : entryHandlers) {
            emitWithSamplingGuard(
                cb, ph, samplingMethodId,
                () -> emitProbeCall(cb, ph, javaClassName, methodName, isStatic, true, -1, TypeKind.VOID, -1));
          }
        }

        if (pendingLine != -1 && !(ce instanceof PseudoInstruction)) {
          emitLineHandlers(cb, Where.BEFORE, pendingLine);
          pendingLine = -1;
        }

        if (!returnHandlers.isEmpty() && ce instanceof ReturnInstruction ri) {
          TypeKind returnKind = ri.typeKind();

          // @Return: dup and store (skip void silently)
          int localRetValSlot = -1;
          if (hasReturnParam && returnKind != TypeKind.VOID) {
            if (retValSlot[0] == -1) {
              // Java source-compiled methods have a single return type, so all RETURN instructions
              // use the same TypeKind. This slot reuse is valid for well-formed class files.
              retValSlot[0] = cb.allocateLocal(returnKind);
            }
            localRetValSlot = retValSlot[0];
            if (returnKind.slotSize() == 2) {
              cb.dup2();
            } else {
              cb.dup();
            }
            cb.storeLocal(returnKind, localRetValSlot);
          }

          // @Duration: compute nanoTime - entryTs (only for RETURN probes that declare @Duration)
          int localDurationSlot = -1;
          if (hasDurationReturn && entryTsSlot[0] != -1) {
            if (durationSlot[0] == -1) {
              durationSlot[0] = cb.allocateLocal(TypeKind.LONG);
            }
            localDurationSlot = durationSlot[0];
            cb.invokestatic(
                ClassDesc.ofInternalName("java/lang/System"),
                "nanoTime",
                MethodTypeDesc.ofDescriptor("()J"));
            cb.loadLocal(TypeKind.LONG, entryTsSlot[0]);
            cb.lsub();
            cb.storeLocal(TypeKind.LONG, localDurationSlot);
          }

          final int fLocalRetValSlot = localRetValSlot;
          final TypeKind fReturnKind = returnKind;
          final int fLocalDurationSlot = localDurationSlot;
          for (ProbeHandler ph : returnHandlers) {
            emitWithSamplingGuard(
                cb, ph, samplingMethodId,
                () -> emitProbeCall(
                    cb, ph, javaClassName, methodName, isStatic, false,
                    fLocalRetValSlot, methodReturnType, fReturnKind, fLocalDurationSlot, null));
          }
        }

        // NEW AFTER: fire after the first <init> that completes a pending 'new' allocation.
        // Processed before call probes so the pending state is consumed on the first <init> match.
        if (!pendingNewStack.isEmpty() && ce instanceof InvokeInstruction nii && isConstructorCall(nii)) {
          PendingNew pending = pendingNewStack.peek();
          if (pending.internalName.equals(nii.owner().asInternalName())) {
            pendingNewStack.pop();
            emitInvoke(cb, nii); // <init> returns; initialized object is now TOS
            boolean hasReturn =
                pending.afterHandlers.stream().anyMatch(ph -> ph.om.getReturnParameter() != -1);
            int newObjReturnSlot = -1;
            if (hasReturn) {
              newObjReturnSlot = cb.allocateLocal(TypeKind.REFERENCE);
              cb.dup();
              cb.astore(newObjReturnSlot);
            }
            boolean emitted = false;
            for (ProbeHandler ph : pending.afterHandlers) {
              emitted |=
                  emitNewObjectProbe(
                      cb, ph, javaClassName, methodName, isStatic,
                      pending.extName, newObjReturnSlot);
            }
            if (emitted) anyMatch[0] = true;
            return;
          }
        }

        if (!callHandlers.isEmpty() && ce instanceof InvokeInstruction ii) {
          List<ProbeHandler> matchedCallHandlers = filterForCall(callHandlers, ii);
          if (!matchedCallHandlers.isEmpty()) {
            List<ProbeHandler> emittableBefore = new ArrayList<>();
            List<ProbeHandler> emittableAfter = new ArrayList<>();
            for (ProbeHandler ph : matchedCallHandlers) {
              Where where = ph.om.getLocation().getWhere();
              if (where == Where.BEFORE && isConstructorCall(ii)) continue;
              if (!canEmitCallProbe(ph, ii, loader)) continue;
              if (where == Where.BEFORE) {
                emittableBefore.add(ph);
              } else if (where == Where.AFTER) {
                emittableAfter.add(ph);
              }
            }
            if (emittableBefore.isEmpty() && emittableAfter.isEmpty()) {
              cb.with(ce);
              return;
            }
            CallContext callContext = backupCallStack(cb, ii);
            boolean hasAfterDuration =
                emittableAfter.stream().anyMatch(ph -> ph.om.getDurationParameter() != -1);
            Type callReturnType = Type.getReturnType(ii.type().stringValue());
            TypeKind callReturnKind = typeKind(callReturnType);
            boolean hasAfterReturn =
                callReturnKind != TypeKind.VOID
                    && emittableAfter.stream().anyMatch(ph -> ph.om.getReturnParameter() != -1);
            int callStartSlot = -1;
            boolean emitted = false;
            for (ProbeHandler ph : emittableBefore) {
              emitted |= emitCallProbe(cb, ph, javaClassName, methodName, isStatic, true, callContext);
            }
            if (hasAfterDuration) {
              callStartSlot = cb.allocateLocal(TypeKind.LONG);
              cb.invokestatic(
                  ClassDesc.ofInternalName("java/lang/System"),
                  "nanoTime",
                  MethodTypeDesc.ofDescriptor("()J"));
              cb.storeLocal(TypeKind.LONG, callStartSlot);
            }
            restoreCallStack(cb, callContext);
            emitInvoke(cb, ii);
            int callReturnSlot = -1;
            if (hasAfterReturn) {
              callReturnSlot = cb.allocateLocal(callReturnKind);
              if (callReturnKind.slotSize() == 2) {
                cb.dup2();
              } else {
                cb.dup();
              }
              cb.storeLocal(callReturnKind, callReturnSlot);
            }
            int callDurationSlot = -1;
            if (hasAfterDuration) {
              callDurationSlot = cb.allocateLocal(TypeKind.LONG);
              cb.invokestatic(
                  ClassDesc.ofInternalName("java/lang/System"),
                  "nanoTime",
                  MethodTypeDesc.ofDescriptor("()J"));
              cb.loadLocal(TypeKind.LONG, callStartSlot);
              cb.lsub();
              cb.storeLocal(TypeKind.LONG, callDurationSlot);
            }
            for (ProbeHandler ph : emittableAfter) {
              emitted |=
                  emitProbeCall(
                      cb,
                      ph,
                      javaClassName,
                      methodName,
                      isStatic,
                      false,
                      callReturnSlot,
                      callReturnType,
                      callReturnKind,
                      callDurationSlot,
                      callContext);
            }
            if (emitted) {
              anyMatch[0] = true;
            }
            return;
          }
        }

        if (!fieldGetHandlers.isEmpty()
            && ce instanceof FieldInstruction fi
            && (fi.opcode() == Opcode.GETFIELD || fi.opcode() == Opcode.GETSTATIC)) {
          List<ProbeHandler> matchedFieldGetHandlers = filterForField(fieldGetHandlers, fi);
          if (!matchedFieldGetHandlers.isEmpty()) {
            List<ProbeHandler> emittableBefore = new ArrayList<>();
            List<ProbeHandler> emittableAfter = new ArrayList<>();
            Type fieldType = Type.getType(fi.type().stringValue());
            boolean staticAccess = fi.opcode() == Opcode.GETSTATIC;
            for (ProbeHandler ph : matchedFieldGetHandlers) {
              if (!canEmitFieldGetProbe(ph, fi, loader)) continue;
              Where where = ph.om.getLocation().getWhere();
              if (where == Where.BEFORE) {
                emittableBefore.add(ph);
              } else if (where == Where.AFTER) {
                emittableAfter.add(ph);
              }
            }
            if (emittableBefore.isEmpty() && emittableAfter.isEmpty()) {
              cb.with(ce);
              return;
            }

            boolean needsOwnerBackup =
                !staticAccess
                    && (!emittableBefore.isEmpty()
                        || matchedFieldGetHandlers.stream()
                            .anyMatch(ph -> ph.om.getTargetInstanceParameter() != -1));
            int ownerSlot = -1;
            if (needsOwnerBackup) {
              ownerSlot = cb.allocateLocal(TypeKind.REFERENCE);
              cb.astore(ownerSlot);
            }
            FieldGetContext fieldContext = new FieldGetContext(fi, fieldType, staticAccess, ownerSlot);
            boolean emitted = false;
            for (ProbeHandler ph : emittableBefore) {
              emitted |= emitFieldGetProbe(cb, ph, javaClassName, methodName, isStatic, fieldContext);
            }

            if (needsOwnerBackup) {
              cb.aload(ownerSlot);
            }
            emitFieldAccess(cb, fi);

            int fieldValueSlot = -1;
            TypeKind fieldKind = typeKind(fieldType);
            boolean hasAfterReturn =
                emittableAfter.stream().anyMatch(ph -> ph.om.getReturnParameter() != -1);
            if (hasAfterReturn) {
              fieldValueSlot = cb.allocateLocal(fieldKind);
              if (fieldKind.slotSize() == 2) {
                cb.dup2();
              } else {
                cb.dup();
              }
              cb.storeLocal(fieldKind, fieldValueSlot);
            }
            for (ProbeHandler ph : emittableAfter) {
              emitted |=
                  emitProbeCall(
                      cb,
                      ph,
                      javaClassName,
                      methodName,
                      isStatic,
                      false,
                      fieldValueSlot,
                      fieldType,
                      fieldKind,
                      -1,
                      null,
                      -1,
                      fieldContext);
            }
            if (emitted) {
              anyMatch[0] = true;
            }
            return;
          }
        }

        if (!fieldSetHandlers.isEmpty()
            && ce instanceof FieldInstruction fi
            && (fi.opcode() == Opcode.PUTFIELD || fi.opcode() == Opcode.PUTSTATIC)) {
          List<ProbeHandler> matchedFieldSetHandlers = filterForField(fieldSetHandlers, fi);
          if (!matchedFieldSetHandlers.isEmpty()) {
            List<ProbeHandler> emittableBefore = new ArrayList<>();
            List<ProbeHandler> emittableAfter = new ArrayList<>();
            for (ProbeHandler ph : matchedFieldSetHandlers) {
              if (!canEmitFieldSetProbe(ph, fi, loader)) continue;
              Where where = ph.om.getLocation().getWhere();
              if (where == Where.BEFORE) {
                emittableBefore.add(ph);
              } else if (where == Where.AFTER) {
                emittableAfter.add(ph);
              }
            }
            if (emittableBefore.isEmpty() && emittableAfter.isEmpty()) {
              cb.with(ce);
              return;
            }

            Type fieldType = Type.getType(fi.type().stringValue());
            TypeKind fieldKind = typeKind(fieldType);
            boolean staticAccess = fi.opcode() == Opcode.PUTSTATIC;
            int valueSlot = cb.allocateLocal(fieldKind);
            cb.storeLocal(fieldKind, valueSlot);
            int ownerSlot = -1;
            if (!staticAccess) {
              ownerSlot = cb.allocateLocal(TypeKind.REFERENCE);
              cb.astore(ownerSlot);
            }
            FieldSetContext fieldContext =
                new FieldSetContext(fi, fieldType, staticAccess, ownerSlot, valueSlot);
            boolean emitted = false;
            for (ProbeHandler ph : emittableBefore) {
              emitted |= emitFieldSetProbe(cb, ph, javaClassName, methodName, isStatic, fieldContext);
            }
            if (!staticAccess) {
              cb.aload(ownerSlot);
            }
            cb.loadLocal(fieldKind, valueSlot);
            emitFieldAccess(cb, fi);
            for (ProbeHandler ph : emittableAfter) {
              emitted |= emitFieldSetProbe(cb, ph, javaClassName, methodName, isStatic, fieldContext);
            }
            if (emitted) {
              anyMatch[0] = true;
            }
            return;
          }
        }

        if (!arrayGetHandlers.isEmpty() && ce instanceof ArrayLoadInstruction ai) {
          Type arrayType = TypeUtils.getArrayType(ai.opcode().bytecode());
          Type elementType = TypeUtils.getElementType(ai.opcode().bytecode());
          TypeKind elementKind = typeKind(elementType);
          List<ProbeHandler> matchedArrayGetHandlers =
              filterForArray(arrayGetHandlers, arrayType, elementType);
          if (!matchedArrayGetHandlers.isEmpty()) {
            List<ProbeHandler> emittableBefore = new ArrayList<>();
            List<ProbeHandler> emittableAfter = new ArrayList<>();
            for (ProbeHandler ph : matchedArrayGetHandlers) {
              if (!canEmitArrayGetProbe(ph, arrayType, elementType, loader)) continue;
              Where where = ph.om.getLocation().getWhere();
              if (where == Where.BEFORE) {
                emittableBefore.add(ph);
              } else if (where == Where.AFTER) {
                emittableAfter.add(ph);
              }
            }
            if (emittableBefore.isEmpty() && emittableAfter.isEmpty()) {
              cb.with(ce);
              return;
            }

            int indexSlot = cb.allocateLocal(TypeKind.INT);
            cb.storeLocal(TypeKind.INT, indexSlot);
            int arraySlot = cb.allocateLocal(TypeKind.REFERENCE);
            cb.astore(arraySlot);
            ArrayGetContext arrayContext =
                new ArrayGetContext(arrayType, elementType, elementKind, arraySlot, indexSlot);

            boolean emitted = false;
            for (ProbeHandler ph : emittableBefore) {
              emitted |= emitArrayGetProbe(cb, ph, javaClassName, methodName, isStatic, arrayContext);
            }

            cb.aload(arraySlot);
            cb.loadLocal(TypeKind.INT, indexSlot);
            cb.arrayLoad(ai.typeKind());

            int valueSlot = -1;
            if (!emittableAfter.isEmpty()) {
              valueSlot = cb.allocateLocal(elementKind);
              cb.storeLocal(elementKind, valueSlot);
              for (ProbeHandler ph : emittableAfter) {
                emitted |=
                    emitProbeCall(
                        cb,
                        ph,
                        javaClassName,
                        methodName,
                        isStatic,
                        false,
                        valueSlot,
                        elementType,
                        elementKind,
                        -1,
                        null,
                        -1,
                        null,
                        null,
                        arrayContext);
              }
              cb.loadLocal(elementKind, valueSlot);
            }
            if (emitted) {
              anyMatch[0] = true;
            }
            return;
          }
        }

        if (!arraySetHandlers.isEmpty() && ce instanceof ArrayStoreInstruction ai) {
          Type arrayType = TypeUtils.getArrayType(ai.opcode().bytecode());
          Type elementType = TypeUtils.getElementType(ai.opcode().bytecode());
          TypeKind elementKind = typeKind(elementType);
          List<ProbeHandler> matchedArraySetHandlers =
              filterForArray(arraySetHandlers, arrayType, elementType);
          if (!matchedArraySetHandlers.isEmpty()) {
            List<ProbeHandler> emittableBefore = new ArrayList<>();
            List<ProbeHandler> emittableAfter = new ArrayList<>();
            for (ProbeHandler ph : matchedArraySetHandlers) {
              if (!canEmitArraySetProbe(ph, arrayType, elementType, loader)) continue;
              Where where = ph.om.getLocation().getWhere();
              if (where == Where.BEFORE) {
                emittableBefore.add(ph);
              } else if (where == Where.AFTER) {
                emittableAfter.add(ph);
              }
            }
            if (emittableBefore.isEmpty() && emittableAfter.isEmpty()) {
              cb.with(ce);
              return;
            }

            int valueSlot = cb.allocateLocal(elementKind);
            cb.storeLocal(elementKind, valueSlot);
            int indexSlot = cb.allocateLocal(TypeKind.INT);
            cb.storeLocal(TypeKind.INT, indexSlot);
            int arraySlot = cb.allocateLocal(TypeKind.REFERENCE);
            cb.astore(arraySlot);
            ArraySetContext arrayContext =
                new ArraySetContext(
                    arrayType, elementType, elementKind, arraySlot, indexSlot, valueSlot);

            boolean emitted = false;
            for (ProbeHandler ph : emittableBefore) {
              emitted |= emitArraySetProbe(cb, ph, javaClassName, methodName, isStatic, arrayContext);
            }

            cb.aload(arraySlot);
            cb.loadLocal(TypeKind.INT, indexSlot);
            cb.loadLocal(elementKind, valueSlot);
            cb.arrayStore(ai.typeKind());

            for (ProbeHandler ph : emittableAfter) {
              emitted |= emitArraySetProbe(cb, ph, javaClassName, methodName, isStatic, arrayContext);
            }
            if (emitted) {
              anyMatch[0] = true;
            }
            return;
          }
        }

        if (ce instanceof TypeCheckInstruction ti
            && (ti.opcode() == Opcode.CHECKCAST || ti.opcode() == Opcode.INSTANCEOF)) {
          List<ProbeHandler> typeCheckHandlers =
              ti.opcode() == Opcode.CHECKCAST ? checkcastHandlers : instanceofHandlers;
          if (!typeCheckHandlers.isEmpty()) {
            Type targetType = Type.getObjectType(ti.type().asInternalName());
            String targetTypeName = targetType.getClassName();
            List<ProbeHandler> matchedTypeCheckHandlers =
                filterForTypeCheck(typeCheckHandlers, targetTypeName);
            if (!matchedTypeCheckHandlers.isEmpty()) {
              List<ProbeHandler> emittableBefore = new ArrayList<>();
              List<ProbeHandler> emittableAfter = new ArrayList<>();
              for (ProbeHandler ph : matchedTypeCheckHandlers) {
                if (!canEmitTypeCheckProbe(ph, loader)) continue;
                Where where = ph.om.getLocation().getWhere();
                if (where == Where.BEFORE) {
                  emittableBefore.add(ph);
                } else if (where == Where.AFTER) {
                  emittableAfter.add(ph);
                }
              }
              if (emittableBefore.isEmpty() && emittableAfter.isEmpty()) {
                cb.with(ce);
                return;
              }

              boolean emitted = false;
              int targetSlot = -1;
              boolean needsTargetBefore =
                  emittableBefore.stream().anyMatch(ph -> ph.om.getTargetInstanceParameter() != -1);
              boolean needsTargetAfter =
                  emittableAfter.stream().anyMatch(ph -> ph.om.getTargetInstanceParameter() != -1);
              if (needsTargetBefore || (ti.opcode() == Opcode.INSTANCEOF && needsTargetAfter)) {
                targetSlot = cb.allocateLocal(TypeKind.REFERENCE);
                cb.dup();
                cb.astore(targetSlot);
              }

              TypeCheckContext typeCheckContext = new TypeCheckContext(targetTypeName, targetSlot);
              for (ProbeHandler ph : emittableBefore) {
                emitted |=
                    emitTypeCheckProbe(cb, ph, javaClassName, methodName, isStatic, typeCheckContext);
              }

              emitTypeCheck(cb, ti);

              if (ti.opcode() == Opcode.CHECKCAST && needsTargetAfter && targetSlot == -1) {
                targetSlot = cb.allocateLocal(TypeKind.REFERENCE);
                cb.dup();
                cb.astore(targetSlot);
                typeCheckContext = new TypeCheckContext(targetTypeName, targetSlot);
              }
              for (ProbeHandler ph : emittableAfter) {
                emitted |=
                    emitTypeCheckProbe(cb, ph, javaClassName, methodName, isStatic, typeCheckContext);
              }
              if (emitted) {
                anyMatch[0] = true;
              }
              return;
            }
          }
        }

        if (!throwHandlers.isEmpty() && ce instanceof ThrowInstruction) {
          List<ProbeHandler> emittableThrow = new ArrayList<>();
          for (ProbeHandler ph : throwHandlers) {
            if (canEmitThrowProbe(ph, loader)) {
              emittableThrow.add(ph);
            }
          }
          if (!emittableThrow.isEmpty()) {
            boolean needsThrowable =
                emittableThrow.stream().anyMatch(ph -> ph.om.getTargetInstanceParameter() != -1);
            int throwableSlot = -1;
            if (needsThrowable) {
              throwableSlot = cb.allocateLocal(TypeKind.REFERENCE);
              cb.dup();
              cb.astore(throwableSlot);
            }
            ThrowContext throwContext = new ThrowContext(throwableSlot);
            boolean emitted = false;
            for (ProbeHandler ph : emittableThrow) {
              emitted |= emitThrowProbe(cb, ph, javaClassName, methodName, isStatic, throwContext);
            }
            cb.athrow();
            if (emitted) {
              anyMatch[0] = true;
            }
            return;
          }
        }

        // CATCH probe: fire at exception handler entry, where the caught throwable is TOS.
        // We bind the label first, then dup/store the throwable if @TargetInstance is needed.
        // A multi-catch block (catch (A | B e)) maps multiple types to the same handler label;
        // we accept any probe whose type is assignable from any of the caught types.
        if (!catchHandlers.isEmpty() && ce instanceof LabelTarget lt) {
          List<String> catchTypeNames = catchHandlerTypes.get(lt.label());
          if (catchTypeNames != null) {
            cb.with(ce); // bind the handler label; throwable is now TOS
            List<ProbeHandler> emittableCatch = new ArrayList<>();
            boolean needsThrowable = false;
            for (ProbeHandler ph : catchHandlers) {
              for (String catchTypeInternalName : catchTypeNames) {
                Type catchType = Type.getObjectType(catchTypeInternalName);
                if (canEmitCatchProbe(ph, catchType, loader)) {
                  emittableCatch.add(ph);
                  if (ph.om.getTargetInstanceParameter() != -1) needsThrowable = true;
                  break; // accepted by at least one type in the multi-catch
                }
              }
            }
            if (!emittableCatch.isEmpty()) {
              int throwableSlot = -1;
              if (needsThrowable) {
                throwableSlot = cb.allocateLocal(TypeKind.REFERENCE);
                cb.dup();
                cb.astore(throwableSlot);
              }
              boolean emitted = false;
              for (ProbeHandler ph : emittableCatch) {
                emitted |=
                    emitCatchProbe(cb, ph, javaClassName, methodName, isStatic, throwableSlot);
              }
              if (emitted) anyMatch[0] = true;
            }
            return;
          }
        }

        // NEWARRAY / ANEWARRAY / MULTIANEWARRAY: array allocation probes
        if (!newarrayHandlers.isEmpty()
            && (ce instanceof NewPrimitiveArrayInstruction
                || ce instanceof NewReferenceArrayInstruction
                || ce instanceof NewMultiArrayInstruction)) {
          String extName;
          int dims;
          Type arrayType;
          if (ce instanceof NewPrimitiveArrayInstruction pi) {
            extName = typeKindToJavaName(pi.typeKind());
            dims = 1;
            arrayType = primitiveArrayType(pi.typeKind());
          } else if (ce instanceof NewReferenceArrayInstruction ri) {
            String internalName = ri.componentType().asInternalName();
            extName = TypeUtils.getJavaType("L" + internalName + ";");
            dims = 1;
            arrayType = Type.getType("[L" + internalName + ";");
          } else {
            NewMultiArrayInstruction mi = (NewMultiArrayInstruction) ce;
            String fullDesc = mi.arrayType().asInternalName();
            dims = mi.dimensions();
            int elementStart = 0;
            while (elementStart < fullDesc.length() && fullDesc.charAt(elementStart) == '[') {
              elementStart++;
            }
            extName = TypeUtils.getJavaType(fullDesc.substring(elementStart));
            arrayType = Type.getType(fullDesc);
          }
          List<ProbeHandler> matchedHandlers = filterForNewArray(newarrayHandlers, extName);
          if (!matchedHandlers.isEmpty()) {
            List<ProbeHandler> emittableBefore = new ArrayList<>();
            List<ProbeHandler> emittableAfter = new ArrayList<>();
            for (ProbeHandler ph : matchedHandlers) {
              if (!canEmitNewArrayProbe(ph, loader)) continue;
              Where where = ph.om.getLocation().getWhere();
              if (where == Where.BEFORE) emittableBefore.add(ph);
              else if (where == Where.AFTER) emittableAfter.add(ph);
            }
            if (!emittableBefore.isEmpty() || !emittableAfter.isEmpty()) {
              boolean emitted = false;
              for (ProbeHandler ph : emittableBefore) {
                emitted |=
                    emitNewArrayProbe(
                        cb, ph, javaClassName, methodName, isStatic,
                        new NewArrayContext(extName, dims, arrayType, -1));
              }
              cb.with(ce); // execute allocation; array ref is now TOS
              if (!emittableAfter.isEmpty()) {
                int arrayRefSlot = cb.allocateLocal(TypeKind.REFERENCE);
                cb.astore(arrayRefSlot);
                for (ProbeHandler ph : emittableAfter) {
                  emitted |=
                      emitNewArrayProbe(
                          cb, ph, javaClassName, methodName, isStatic,
                          new NewArrayContext(extName, dims, arrayType, arrayRefSlot));
                }
                cb.aload(arrayRefSlot);
              }
              if (emitted) anyMatch[0] = true;
              return;
            }
          }
        }

        // NEW: object allocation probes (BEFORE before 'new'; AFTER tracked via pendingNewStack)
        if (!newobjHandlers.isEmpty() && ce instanceof NewObjectInstruction ni) {
          String newInternalName = ni.className().asInternalName();
          String newExtName = newInternalName.replace('/', '.');
          List<ProbeHandler> matched = filterForNewArray(newobjHandlers, newExtName);
          if (!matched.isEmpty()) {
            List<ProbeHandler> emittableBefore = new ArrayList<>();
            List<ProbeHandler> emittableAfter = new ArrayList<>();
            for (ProbeHandler ph : matched) {
              if (!canEmitNewObjectProbe(ph, loader)) continue;
              Where where = ph.om.getLocation().getWhere();
              if (where == Where.BEFORE) emittableBefore.add(ph);
              else if (where == Where.AFTER) emittableAfter.add(ph);
            }
            if (!emittableBefore.isEmpty() || !emittableAfter.isEmpty()) {
              boolean emitted = false;
              for (ProbeHandler ph : emittableBefore) {
                emitted |=
                    emitNewObjectProbe(
                        cb, ph, javaClassName, methodName, isStatic, newExtName, -1);
              }
              if (!emittableAfter.isEmpty()) {
                pendingNewStack.push(new PendingNew(newExtName, newInternalName, emittableAfter));
              }
              cb.with(ce); // emit 'new' instruction
              if (emitted) anyMatch[0] = true;
              return;
            }
          }
        }

        // SYNC_ENTRY / SYNC_EXIT: monitorenter and monitorexit probes
        if ((!syncEntryHandlers.isEmpty() || !syncExitHandlers.isEmpty())
            && ce instanceof MonitorInstruction mi) {
          boolean isEnter = mi.opcode() == Opcode.MONITORENTER;
          List<ProbeHandler> handlers = isEnter ? syncEntryHandlers : syncExitHandlers;
          List<ProbeHandler> emittableBefore = new ArrayList<>();
          List<ProbeHandler> emittableAfter = new ArrayList<>();
          for (ProbeHandler ph : handlers) {
            if (!canEmitSyncProbe(ph, loader)) continue;
            Where where = ph.om.getLocation().getWhere();
            if (where == Where.BEFORE) emittableBefore.add(ph);
            else if (where == Where.AFTER) emittableAfter.add(ph);
          }
          if (!emittableBefore.isEmpty() || !emittableAfter.isEmpty()) {
            boolean needsLock =
                emittableBefore.stream().anyMatch(ph -> ph.om.getTargetInstanceParameter() != -1)
                    || emittableAfter.stream()
                        .anyMatch(ph -> ph.om.getTargetInstanceParameter() != -1);
            int lockSlot = -1;
            if (needsLock) {
              lockSlot = cb.allocateLocal(TypeKind.REFERENCE);
              cb.dup();
              cb.astore(lockSlot);
            }
            boolean emitted = false;
            for (ProbeHandler ph : emittableBefore) {
              emitted |= emitSyncProbe(cb, ph, javaClassName, methodName, isStatic, lockSlot);
            }
            cb.with(ce); // emit monitorenter or monitorexit
            for (ProbeHandler ph : emittableAfter) {
              emitted |= emitSyncProbe(cb, ph, javaClassName, methodName, isStatic, lockSlot);
            }
            if (emitted) anyMatch[0] = true;
            return;
          }
        }

        cb.with(ce);
      }

      private void emitLineHandlers(CodeBuilder cb, Where where, int line) {
        for (ProbeHandler ph : lineHandlers) {
          if (ph.om.getLocation().getWhere() == where
              && lineMatches(ph.om.getLocation(), line)
              && canEmitLineProbe(ph, javaClassName, isStatic, loader)) {
            if (emitLineProbe(cb, ph, javaClassName, methodName, isStatic, line)) {
              anyMatch[0] = true;
            }
          }
        }
      }

      @Override
      public void atEnd(CodeBuilder cb) {
        if (lastLine != -1) {
          emitLineHandlers(cb, Where.AFTER, lastLine);
        }

        // Inject an exception-exit handler for @Duration (on RETURN probes) and ERROR probes.
        // startLabel is non-null iff needsExceptionHandler; entryInjected guards against
        // methods that have no real instructions (impossible for valid class files).
        if (startLabel == null || !entryInjected[0]) return;

        List<ProbeHandler> durationHandlers = new ArrayList<>();
        for (ProbeHandler ph : returnHandlers) {
          if (ph.om.getDurationParameter() != -1) durationHandlers.add(ph);
        }
        List<ProbeHandler> emittableError = new ArrayList<>();
        for (ProbeHandler ph : errorHandlers) {
          if (canEmitErrorProbe(ph, loader)) emittableError.add(ph);
        }
        if (durationHandlers.isEmpty() && emittableError.isEmpty()) return;

        Label endLabel = cb.newLabel();
        Label handlerLabel = cb.newLabel();

        // Bind end-of-try and handler-start before registering the catch range.
        cb.labelBinding(endLabel);
        cb.labelBinding(handlerLabel);
        cb.exceptionCatchAll(startLabel, endLabel, handlerLabel);

        // Stack: [Throwable]
        int exSlot = cb.allocateLocal(TypeKind.REFERENCE);
        cb.astore(exSlot);

        // Compute duration delta if any probe on this path needs it.
        int exDurationSlot = -1;
        if (hasDuration && entryTsSlot[0] != -1) {
          exDurationSlot = durationSlot[0] != -1 ? durationSlot[0] : cb.allocateLocal(TypeKind.LONG);
          cb.invokestatic(
              ClassDesc.ofInternalName("java/lang/System"),
              "nanoTime",
              MethodTypeDesc.ofDescriptor("()J"));
          cb.loadLocal(TypeKind.LONG, entryTsSlot[0]);
          cb.lsub();
          cb.storeLocal(TypeKind.LONG, exDurationSlot);
        }

        // Fire RETURN @Duration handlers (retValSlot=-1: no return value on exception path)
        final int fExDurationSlot = exDurationSlot;
        for (ProbeHandler ph : durationHandlers) {
          emitWithSamplingGuard(
              cb, ph, samplingMethodId,
              () -> emitProbeCall(
                  cb, ph, javaClassName, methodName, isStatic, false, -1, TypeKind.VOID, fExDurationSlot));
        }

        // Fire ERROR handlers
        boolean emitted = false;
        final int fExSlot = exSlot;
        for (ProbeHandler ph : emittableError) {
          emitted |= emitWithSamplingGuard(
              cb, ph, samplingMethodId,
              () -> emitErrorProbe(cb, ph, javaClassName, methodName, isStatic, fExSlot, fExDurationSlot));
        }
        if (emitted) anyMatch[0] = true;

        // Rethrow
        cb.aload(exSlot);
        cb.athrow();
      }
    };
  }

  private static List<ProbeHandler> filterForMethod(
      List<ProbeHandler> handlers, String methodName, String methodDesc, ClassLoader loader) {
    List<ProbeHandler> result = null;
    for (ProbeHandler ph : handlers) {
      String pattern = ph.om.getMethod();
      boolean nameMatch;
      if (pattern == null || pattern.isEmpty()) {
        nameMatch = true; // empty pattern matches all methods
      } else if (pattern.equals("#")) {
        nameMatch = methodName.equals(ph.om.getTargetName());
      } else if (ph.om.isMethodRegexMatcher()) {
        try {
          nameMatch = methodName.matches(pattern);
        } catch (java.util.regex.PatternSyntaxException e) {
          continue;
        }
      } else {
        nameMatch = pattern.equals(methodName);
      }
      if (!nameMatch) continue;
      if (!typeMatches(ph.om.getType(), methodDesc, loader, ph.om.isExactTypeMatch())) continue;
      if (result == null) result = new ArrayList<>();
      result.add(ph);
    }
    return result != null ? result : Collections.emptyList();
  }

  private static boolean typeMatches(
      String declaration, String descriptor, ClassLoader loader, boolean exactTypeMatch) {
    if (declaration == null || declaration.isEmpty()) return true;
    try {
      String declaredDescriptor = TypeUtils.declarationToDescriptor(declaration);
      Type[] declaredArgs = Type.getArgumentTypes(declaredDescriptor);
      Type[] methodArgs = Type.getArgumentTypes(descriptor);
      Type declaredReturn = Type.getReturnType(declaredDescriptor);
      Type methodReturn = Type.getReturnType(descriptor);
      return InstrumentUtils.isAssignable(declaredReturn, methodReturn, loader, exactTypeMatch)
          && InstrumentUtils.isAssignable(declaredArgs, methodArgs, loader, exactTypeMatch);
    } catch (IllegalArgumentException e) {
      log.debug("ClassFileApiBackend: skipping invalid type declaration '{}'", declaration);
      return false;
    }
  }

  private static List<ProbeHandler> filterForCall(List<ProbeHandler> handlers, InvokeInstruction ii) {
    List<ProbeHandler> result = null;
    String owner = ii.owner().asInternalName().replace('/', '.');
    String name = ii.name().stringValue();
    String desc = ii.type().stringValue();
    for (ProbeHandler ph : handlers) {
      Location loc = ph.om.getLocation();
      if (!matches(loc.getClazz(), owner)) continue;
      if (!matches(loc.getMethod(), name)) continue;
      if (!matches(loc.getType(), desc)) continue;
      if (result == null) result = new ArrayList<>();
      result.add(ph);
    }
    return result != null ? result : Collections.emptyList();
  }

  private static List<ProbeHandler> filterForField(
      List<ProbeHandler> handlers, FieldInstruction fi) {
    List<ProbeHandler> result = null;
    String owner = fi.owner().asInternalName().replace('/', '.');
    String name = fi.name().stringValue();
    for (ProbeHandler ph : handlers) {
      Location loc = ph.om.getLocation();
      if (!matches(loc.getClazz(), owner)) continue;
      if (!matches(loc.getField(), name)) continue;
      if (result == null) result = new ArrayList<>();
      result.add(ph);
    }
    return result != null ? result : Collections.emptyList();
  }

  private static List<ProbeHandler> filterForArray(
      List<ProbeHandler> handlers, Type arrayType, Type elementType) {
    List<ProbeHandler> result = null;
    for (ProbeHandler ph : handlers) {
      String type = ph.om.getLocation().getType();
      if (type != null
          && !type.isEmpty()
          && !type.equals(arrayType.getClassName())
          && !type.equals(elementType.getClassName())) {
        continue;
      }
      if (result == null) result = new ArrayList<>();
      result.add(ph);
    }
    return result != null ? result : Collections.emptyList();
  }

  private static List<ProbeHandler> filterForTypeCheck(
      List<ProbeHandler> handlers, String targetTypeName) {
    List<ProbeHandler> result = null;
    for (ProbeHandler ph : handlers) {
      if (!matches(ph.om.getLocation().getClazz(), targetTypeName)) continue;
      if (result == null) result = new ArrayList<>();
      result.add(ph);
    }
    return result != null ? result : Collections.emptyList();
  }

  private static List<ProbeHandler> filterForNewArray(
      List<ProbeHandler> handlers, String extName) {
    List<ProbeHandler> result = null;
    for (ProbeHandler ph : handlers) {
      if (!matches(ph.om.getLocation().getClazz(), extName)) continue;
      if (result == null) result = new ArrayList<>();
      result.add(ph);
    }
    return result != null ? result : Collections.emptyList();
  }


  private static boolean matches(String pattern, String value) {
    if (pattern == null || pattern.isEmpty()) return true;
    if (pattern.startsWith("/") && pattern.endsWith("/")) {
      return value.matches(pattern.substring(1, pattern.length() - 1));
    }
    return pattern.equals(value);
  }

  private static boolean lineMatches(Location location, int line) {
    return location.getLine() == -1 || location.getLine() == line;
  }

  private static void emitInvoke(CodeBuilder cb, InvokeInstruction ii) {
    cb.invoke(ii.opcode(), ii.method());
  }

  private static void emitFieldAccess(CodeBuilder cb, FieldInstruction fi) {
    cb.fieldAccess(fi.opcode(), fi.field());
  }

  private static void emitTypeCheck(CodeBuilder cb, TypeCheckInstruction ti) {
    if (ti.opcode() == Opcode.CHECKCAST) {
      cb.checkcast(ti.type());
    } else {
      cb.instanceOf(ti.type());
    }
  }

  private static boolean isConstructorCall(InvokeInstruction ii) {
    return Constants.CONSTRUCTOR.equals(ii.name().stringValue());
  }

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

  private static CallContext backupCallStack(CodeBuilder cb, InvokeInstruction ii) {
    Type[] args = Type.getArgumentTypes(ii.type().stringValue());
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

    return new CallContext(ii, args, staticCall, argSlots, receiverSlot);
  }

  private static void restoreCallStack(CodeBuilder cb, CallContext ctx) {
    if (!ctx.staticCall) {
      cb.aload(ctx.receiverSlot);
    }
    for (int i = 0; i < ctx.argumentTypes.length; i++) {
      cb.loadLocal(typeKind(ctx.argumentTypes[i]), ctx.argumentSlots[i]);
    }
  }

  private static int callArgumentIndex(OnMethod om, int handlerIndex) {
    return ordinaryArgumentIndex(om, handlerIndex);
  }

  private static int ordinaryArgumentIndex(OnMethod om, int handlerIndex) {
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

  private static boolean canEmitLineProbe(
      ProbeHandler ph, String javaClassName, boolean methodStatic, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    Type selfType = Type.getObjectType(javaClassName.replace('.', '/'));
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        if (!methodStatic
            && !InstrumentUtils.isAssignable(
                handlerArgTypes[i], selfType, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getClassNameParameter() || i == om.getMethodParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getReturnParameter()
          || i == om.getDurationParameter()
          || i == om.getTargetInstanceParameter()
          || i == om.getTargetMethodOrFieldParameter()) {
        return false;
      }
      int lineArgIndex = ordinaryArgumentIndex(om, i);
      if (lineArgIndex != 0 || !sameStackType(Type.INT_TYPE, handlerArgTypes[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean canEmitFieldGetProbe(
      ProbeHandler ph, FieldInstruction fi, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    Type fieldType = Type.getType(fi.type().stringValue());
    Type ownerType = Type.getObjectType(fi.owner().asInternalName());
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getClassNameParameter() || i == om.getMethodParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getTargetMethodOrFieldParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getTargetInstanceParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], ownerType, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getReturnParameter()) {
        if (om.getLocation().getWhere() != Where.AFTER) return false;
        if (typeKind(fieldType) == TypeKind.REFERENCE) {
          if (!InstrumentUtils.isAssignable(
              handlerArgTypes[i], fieldType, loader, om.isExactTypeMatch())) {
            return false;
          }
        } else if (typeKind(handlerArgTypes[i]) == TypeKind.REFERENCE) {
          if (!Constants.OBJECT_DESC.equals(handlerArgTypes[i].getDescriptor())) {
            return false;
          }
        } else if (!sameStackType(fieldType, handlerArgTypes[i])) {
          return false;
        }
        continue;
      }
      if (i == om.getDurationParameter()) {
        return false;
      }
      return false;
    }
    return true;
  }

  private static boolean canEmitFieldSetProbe(
      ProbeHandler ph, FieldInstruction fi, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    Type fieldType = Type.getType(fi.type().stringValue());
    Type ownerType = Type.getObjectType(fi.owner().asInternalName());
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getClassNameParameter() || i == om.getMethodParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getTargetMethodOrFieldParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getTargetInstanceParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], ownerType, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getReturnParameter() || i == om.getDurationParameter()) {
        return false;
      }
      int valueArgIndex = ordinaryArgumentIndex(om, i);
      if (valueArgIndex != 0) return false;
      if (typeKind(fieldType) == TypeKind.REFERENCE) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], fieldType, loader, om.isExactTypeMatch())) {
          return false;
        }
      } else if (typeKind(handlerArgTypes[i]) == TypeKind.REFERENCE) {
        if (!Constants.OBJECT_DESC.equals(handlerArgTypes[i].getDescriptor())) {
          return false;
        }
      } else if (!sameStackType(fieldType, handlerArgTypes[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean canEmitArrayGetProbe(
      ProbeHandler ph, Type arrayType, Type elementType, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getClassNameParameter() || i == om.getMethodParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getTargetInstanceParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], arrayType, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getReturnParameter()) {
        if (om.getLocation().getWhere() != Where.AFTER) return false;
        if (typeKind(elementType) == TypeKind.REFERENCE) {
          if (!InstrumentUtils.isAssignable(
              handlerArgTypes[i], elementType, loader, om.isExactTypeMatch())) {
            return false;
          }
        } else if (typeKind(handlerArgTypes[i]) == TypeKind.REFERENCE) {
          if (!Constants.OBJECT_DESC.equals(handlerArgTypes[i].getDescriptor())) {
            return false;
          }
        } else if (!sameStackType(elementType, handlerArgTypes[i])) {
          return false;
        }
        continue;
      }
      if (i == om.getDurationParameter() || i == om.getTargetMethodOrFieldParameter()) {
        return false;
      }
      int indexArgIndex = ordinaryArgumentIndex(om, i);
      if (indexArgIndex != 0 || !sameStackType(Type.INT_TYPE, handlerArgTypes[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean canEmitArraySetProbe(
      ProbeHandler ph, Type arrayType, Type elementType, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getClassNameParameter() || i == om.getMethodParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getTargetInstanceParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], arrayType, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getReturnParameter()
          || i == om.getDurationParameter()
          || i == om.getTargetMethodOrFieldParameter()) {
        return false;
      }
      int arrayArgIndex = ordinaryArgumentIndex(om, i);
      if (arrayArgIndex == 0) {
        if (!sameStackType(Type.INT_TYPE, handlerArgTypes[i])) {
          return false;
        }
      } else if (arrayArgIndex == 1) {
        if (typeKind(elementType) == TypeKind.REFERENCE) {
          if (!InstrumentUtils.isAssignable(
              handlerArgTypes[i], elementType, loader, om.isExactTypeMatch())) {
            return false;
          }
        } else if (typeKind(handlerArgTypes[i]) == TypeKind.REFERENCE) {
          if (!Constants.OBJECT_DESC.equals(handlerArgTypes[i].getDescriptor())) {
            return false;
          }
        } else if (!sameStackType(elementType, handlerArgTypes[i])) {
          return false;
        }
      } else {
        return false;
      }
    }
    return true;
  }

  private static boolean canEmitTypeCheckProbe(ProbeHandler ph, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getClassNameParameter() || i == om.getMethodParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getTargetInstanceParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.OBJECT_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getReturnParameter()
          || i == om.getDurationParameter()
          || i == om.getTargetMethodOrFieldParameter()) {
        return false;
      }
      int typeArgIndex = ordinaryArgumentIndex(om, i);
      if (typeArgIndex != 0
          || !InstrumentUtils.isAssignable(
              handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
        return false;
      }
    }
    return true;
  }

  private static boolean canEmitThrowProbe(ProbeHandler ph, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getClassNameParameter() || i == om.getMethodParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getTargetInstanceParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.THROWABLE_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      return false;
    }
    return true;
  }

  private static boolean canEmitCatchProbe(ProbeHandler ph, Type catchType, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getClassNameParameter() || i == om.getMethodParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getTargetInstanceParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], catchType, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      return false;
    }
    return true;
  }

  private static boolean canEmitErrorProbe(ProbeHandler ph, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getClassNameParameter() || i == om.getMethodParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getTargetInstanceParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.THROWABLE_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getDurationParameter()) {
        if (!Type.LONG_TYPE.equals(handlerArgTypes[i])) return false;
        continue;
      }
      return false;
    }
    return true;
  }

  private static boolean canEmitNewArrayProbe(ProbeHandler ph, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getClassNameParameter() || i == om.getMethodParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getReturnParameter()) {
        if (om.getLocation().getWhere() != Where.AFTER) return false;
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getTargetInstanceParameter()
          || i == om.getDurationParameter()
          || i == om.getTargetMethodOrFieldParameter()) {
        return false;
      }
      int argIndex = ordinaryArgumentIndex(om, i);
      if (argIndex == 0) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
      } else if (argIndex == 1) {
        if (!sameStackType(Type.INT_TYPE, handlerArgTypes[i])) return false;
      } else {
        return false;
      }
    }
    return true;
  }

  private static boolean canEmitNewObjectProbe(ProbeHandler ph, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getClassNameParameter() || i == om.getMethodParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getReturnParameter()) {
        if (om.getLocation().getWhere() != Where.AFTER) return false;
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getTargetInstanceParameter()
          || i == om.getDurationParameter()
          || i == om.getTargetMethodOrFieldParameter()) {
        return false;
      }
      int argIndex = ordinaryArgumentIndex(om, i);
      if (argIndex != 0) return false;
      if (!InstrumentUtils.isAssignable(
          handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
        return false;
      }
    }
    return true;
  }

  private static boolean emitNewObjectProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      String extName,
      int returnSlot) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] argTypes = Type.getArgumentTypes(rawDesc);

    for (int i = 0; i < argTypes.length; i++) {
      boolean satisfiable =
          i == om.getSelfParameter()
              || i == om.getClassNameParameter()
              || i == om.getMethodParameter()
              || (i == om.getReturnParameter() && returnSlot != -1)
              || ordinaryArgumentIndex(om, i) == 0;
      if (!satisfiable) {
        log.debug(
            "ClassFileApiBackend: skipping handler {}.{} — arg {} cannot be satisfied",
            ph.probe.getClassName(),
            om.getTargetName(),
            i);
        return false;
      }
    }

    for (int i = 0; i < argTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (isStatic) cb.aconst_null();
        else cb.aload(0);
      } else if (i == om.getClassNameParameter()) {
        cb.ldc(javaClassName);
      } else if (i == om.getMethodParameter()) {
        cb.ldc(methodName);
      } else if (i == om.getReturnParameter()) {
        cb.aload(returnSlot);
      } else if (ordinaryArgumentIndex(om, i) == 0) {
        cb.ldc(extName);
      }
    }

    String actionMethodName =
        InstrumentUtils.getActionPrefix(ph.probe.getClassName(true)) + om.getTargetName();
    DirectMethodHandleDesc bsmHandle =
        MethodHandleDesc.ofMethod(
            DirectMethodHandleDesc.Kind.STATIC, INDY_DISPATCHER, "bootstrap", BSM_TYPE);
    cb.invokedynamic(
        DynamicCallSiteDesc.of(
            bsmHandle,
            actionMethodName,
            MethodTypeDesc.ofDescriptor(rawDesc),
            ph.probe.getClassName(true)));
    return true;
  }

  private static boolean canEmitSyncProbe(ProbeHandler ph, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        continue;
      }
      if (i == om.getClassNameParameter() || i == om.getMethodParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getTargetInstanceParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.OBJECT_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      // @Return, @Duration, @TargetMethodOrField, and ordinary args are not satisfiable
      return false;
    }
    return true;
  }

  private static boolean emitSyncProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      int lockSlot) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] argTypes = Type.getArgumentTypes(rawDesc);

    for (int i = 0; i < argTypes.length; i++) {
      boolean satisfiable =
          i == om.getSelfParameter()
              || i == om.getClassNameParameter()
              || i == om.getMethodParameter()
              || (i == om.getTargetInstanceParameter() && lockSlot != -1);
      if (!satisfiable) {
        log.debug(
            "ClassFileApiBackend: skipping handler {}.{} — arg {} cannot be satisfied",
            ph.probe.getClassName(),
            om.getTargetName(),
            i);
        return false;
      }
    }

    for (int i = 0; i < argTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (isStatic) cb.aconst_null();
        else cb.aload(0);
      } else if (i == om.getClassNameParameter()) {
        cb.ldc(javaClassName);
      } else if (i == om.getMethodParameter()) {
        cb.ldc(methodName);
      } else if (i == om.getTargetInstanceParameter()) {
        cb.aload(lockSlot);
      }
    }

    String actionMethodName =
        InstrumentUtils.getActionPrefix(ph.probe.getClassName(true)) + om.getTargetName();
    DirectMethodHandleDesc bsmHandle =
        MethodHandleDesc.ofMethod(
            DirectMethodHandleDesc.Kind.STATIC, INDY_DISPATCHER, "bootstrap", BSM_TYPE);
    cb.invokedynamic(
        DynamicCallSiteDesc.of(
            bsmHandle,
            actionMethodName,
            MethodTypeDesc.ofDescriptor(rawDesc),
            ph.probe.getClassName(true)));
    return true;
  }

  private static boolean emitNewArrayProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      NewArrayContext ctx) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        false,
        ctx.returnSlot,
        ctx.arrayType,
        TypeKind.REFERENCE,
        -1,
        null,
        -1,
        null,
        null,
        null,
        null,
        null,
        null,
        ctx);
  }

  private static boolean canEmitCallProbe(ProbeHandler ph, InvokeInstruction ii, ClassLoader loader) {
    OnMethod om = ph.om;
    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] handlerArgTypes = Type.getArgumentTypes(rawDesc);
    Type[] callArgTypes = Type.getArgumentTypes(ii.type().stringValue());
    boolean staticCall = ii.opcode() == Opcode.INVOKESTATIC;
    Type ownerType = Type.getObjectType(ii.owner().asInternalName());
    for (int i = 0; i < handlerArgTypes.length; i++) {
      if (i == om.getSelfParameter()
          || i == om.getClassNameParameter()
          || i == om.getMethodParameter()) {
        continue;
      }
      if (i == om.getTargetMethodOrFieldParameter()) {
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], Constants.STRING_TYPE, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getTargetInstanceParameter()) {
        if (typeKind(handlerArgTypes[i]) != TypeKind.REFERENCE) return false;
        if (!InstrumentUtils.isAssignable(
            handlerArgTypes[i], ownerType, loader, om.isExactTypeMatch())) {
          return false;
        }
        continue;
      }
      if (i == om.getReturnParameter()) {
        Type returnType = Type.getReturnType(ii.type().stringValue());
        if (om.getLocation().getWhere() != Where.AFTER || returnType.equals(Type.VOID_TYPE)) {
          return false;
        }
        if (typeKind(returnType) == TypeKind.REFERENCE) {
          if (!InstrumentUtils.isAssignable(
              handlerArgTypes[i], returnType, loader, om.isExactTypeMatch())) {
            return false;
          }
        } else if (typeKind(handlerArgTypes[i]) == TypeKind.REFERENCE) {
          if (!Constants.OBJECT_DESC.equals(handlerArgTypes[i].getDescriptor())) {
            return false;
          }
        } else if (!sameStackType(returnType, handlerArgTypes[i])) {
          return false;
        }
        continue;
      }
      if (i == om.getDurationParameter()) {
        if (om.getLocation().getWhere() != Where.AFTER
            || !Type.LONG_TYPE.equals(handlerArgTypes[i])) {
          return false;
        }
        continue;
      }
      int callArgIndex = callArgumentIndex(om, i);
      if (callArgIndex < 0 || callArgIndex >= callArgTypes.length) {
        return false;
      }
      if (!sameStackType(callArgTypes[callArgIndex], handlerArgTypes[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameStackType(Type actual, Type expected) {
    TypeKind actualKind = typeKind(actual);
    TypeKind expectedKind = typeKind(expected);
    if (actualKind != expectedKind) return false;
    if (actualKind != TypeKind.REFERENCE) return true;
    if (Constants.OBJECT_DESC.equals(expected.getDescriptor())) return true;
    return actual.equals(expected);
  }

  private static boolean emitCallProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      boolean isBefore,
      CallContext callContext) {
    return emitProbeCall(
        cb, ph, javaClassName, methodName, isStatic, isBefore, -1, TypeKind.VOID, -1, callContext);
  }

  private static boolean emitLineProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      int lineNumber) {
    return emitProbeCall(
        cb, ph, javaClassName, methodName, isStatic, false, -1, null, TypeKind.VOID, -1, null, lineNumber);
  }

  private static boolean emitFieldGetProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      FieldGetContext fieldContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        false,
        -1,
        null,
        TypeKind.VOID,
        -1,
        null,
        -1,
        fieldContext);
  }

  private static boolean emitFieldSetProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      FieldSetContext fieldContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        false,
        -1,
        null,
        TypeKind.VOID,
        -1,
        null,
        -1,
        null,
        fieldContext);
  }

  private static boolean emitArrayGetProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      ArrayGetContext arrayContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        false,
        -1,
        null,
        TypeKind.VOID,
        -1,
        null,
        -1,
        null,
        null,
        arrayContext);
  }

  private static boolean emitArraySetProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      ArraySetContext arrayContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        false,
        -1,
        null,
        TypeKind.VOID,
        -1,
        null,
        -1,
        null,
        null,
        null,
        arrayContext);
  }

  private static boolean emitTypeCheckProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      TypeCheckContext typeCheckContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        false,
        -1,
        null,
        TypeKind.VOID,
        -1,
        null,
        -1,
        null,
        null,
        null,
        null,
        typeCheckContext);
  }

  private static boolean emitThrowProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      ThrowContext throwContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        false,
        -1,
        null,
        TypeKind.VOID,
        -1,
        null,
        -1,
        null,
        null,
        null,
        null,
        null,
        throwContext);
  }

  private static boolean emitCatchProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      int throwableSlot) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        false,
        -1,
        null,
        TypeKind.VOID,
        -1,
        null,
        -1,
        null,
        null,
        null,
        null,
        null,
        new ThrowContext(throwableSlot));
  }

  private static boolean emitErrorProbe(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      int throwableSlot,
      int durationSlot) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        false,
        -1,
        null,
        TypeKind.VOID,
        durationSlot,
        null,
        -1,
        null,
        null,
        null,
        null,
        null,
        new ThrowContext(throwableSlot));
  }

  private static void registerSampledCounters(List<ProbeHandler> handlers, int methodId) {
    for (ProbeHandler ph : handlers) {
      if (ph.om.getSamplerKind() != Sampled.Sampler.None && ph.om.getSamplerMean() > 0) {
        MethodTracker.registerCounter(methodId, ph.om.getSamplerMean());
      }
    }
  }

  private static boolean emitWithSamplingGuard(
      CodeBuilder cb, ProbeHandler ph, int methodId, BooleanSupplier probeEmitter) {
    if (ph.om.getSamplerKind() != Sampled.Sampler.None && methodId != -1) {
      Label skipLabel = cb.newLabel();
      cb.ldc(methodId);
      String hitMethod =
          ph.om.getSamplerKind() == Sampled.Sampler.Adaptive ? "hitAdaptive" : "hit";
      cb.invokestatic(CD_METHOD_TRACKER, hitMethod, HIT_DESC);
      cb.ifeq(skipLabel);
      boolean result = probeEmitter.getAsBoolean();
      cb.labelBinding(skipLabel);
      return result;
    }
    return probeEmitter.getAsBoolean();
  }

  private static boolean emitProbeCall(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      boolean isEntry,
      int retValSlot,
      TypeKind returnKind,
      int durationSlot) {
    return emitProbeCall(
        cb, ph, javaClassName, methodName, isStatic, isEntry, retValSlot, returnKind, durationSlot, null);
  }

  private static boolean emitProbeCall(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      boolean isEntry,
      int retValSlot,
      TypeKind returnKind,
      int durationSlot,
      CallContext callContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        isEntry,
        retValSlot,
        null,
        returnKind,
        durationSlot,
        callContext);
  }

  private static boolean emitProbeCall(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      boolean isEntry,
      int retValSlot,
      Type returnType,
      TypeKind returnKind,
      int durationSlot,
      CallContext callContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        isEntry,
        retValSlot,
        returnType,
        returnKind,
        durationSlot,
        callContext,
        -1);
  }

  private static boolean emitProbeCall(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      boolean isEntry,
      int retValSlot,
      Type returnType,
      TypeKind returnKind,
      int durationSlot,
      CallContext callContext,
      int lineNumber) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        isEntry,
        retValSlot,
        returnType,
        returnKind,
        durationSlot,
        callContext,
        lineNumber,
        null,
        null);
  }

  private static boolean emitProbeCall(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      boolean isEntry,
      int retValSlot,
      Type returnType,
      TypeKind returnKind,
      int durationSlot,
      CallContext callContext,
      int lineNumber,
      FieldGetContext fieldContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        isEntry,
        retValSlot,
        returnType,
        returnKind,
        durationSlot,
        callContext,
        lineNumber,
        fieldContext,
        null,
        null);
  }

  private static boolean emitProbeCall(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      boolean isEntry,
      int retValSlot,
      Type returnType,
      TypeKind returnKind,
      int durationSlot,
      CallContext callContext,
      int lineNumber,
      FieldGetContext fieldContext,
      FieldSetContext fieldSetContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        isEntry,
        retValSlot,
        returnType,
        returnKind,
        durationSlot,
        callContext,
        lineNumber,
        fieldContext,
        fieldSetContext,
        null);
  }

  private static boolean emitProbeCall(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      boolean isEntry,
      int retValSlot,
      Type returnType,
      TypeKind returnKind,
      int durationSlot,
      CallContext callContext,
      int lineNumber,
      FieldGetContext fieldContext,
      FieldSetContext fieldSetContext,
      ArrayGetContext arrayContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        isEntry,
        retValSlot,
        returnType,
        returnKind,
        durationSlot,
        callContext,
        lineNumber,
        fieldContext,
        fieldSetContext,
        arrayContext,
        null);
  }

  private static boolean emitProbeCall(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      boolean isEntry,
      int retValSlot,
      Type returnType,
      TypeKind returnKind,
      int durationSlot,
      CallContext callContext,
      int lineNumber,
      FieldGetContext fieldContext,
      FieldSetContext fieldSetContext,
      ArrayGetContext arrayContext,
      ArraySetContext arraySetContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        isEntry,
        retValSlot,
        returnType,
        returnKind,
        durationSlot,
        callContext,
        lineNumber,
        fieldContext,
        fieldSetContext,
        arrayContext,
        arraySetContext,
        null);
  }

  private static boolean emitProbeCall(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      boolean isEntry,
      int retValSlot,
      Type returnType,
      TypeKind returnKind,
      int durationSlot,
      CallContext callContext,
      int lineNumber,
      FieldGetContext fieldContext,
      FieldSetContext fieldSetContext,
      ArrayGetContext arrayContext,
      ArraySetContext arraySetContext,
      TypeCheckContext typeCheckContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        isEntry,
        retValSlot,
        returnType,
        returnKind,
        durationSlot,
        callContext,
        lineNumber,
        fieldContext,
        fieldSetContext,
        arrayContext,
        arraySetContext,
        typeCheckContext,
        null);
  }

  private static boolean emitProbeCall(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      boolean isEntry,
      int retValSlot,
      Type returnType,
      TypeKind returnKind,
      int durationSlot,
      CallContext callContext,
      int lineNumber,
      FieldGetContext fieldContext,
      FieldSetContext fieldSetContext,
      ArrayGetContext arrayContext,
      ArraySetContext arraySetContext,
      TypeCheckContext typeCheckContext,
      ThrowContext throwContext) {
    return emitProbeCall(
        cb,
        ph,
        javaClassName,
        methodName,
        isStatic,
        isEntry,
        retValSlot,
        returnType,
        returnKind,
        durationSlot,
        callContext,
        lineNumber,
        fieldContext,
        fieldSetContext,
        arrayContext,
        arraySetContext,
        typeCheckContext,
        throwContext,
        null);
  }

  private static boolean emitProbeCall(
      CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName,
      boolean isStatic,
      boolean isEntry,
      int retValSlot,
      Type returnType,
      TypeKind returnKind,
      int durationSlot,
      CallContext callContext,
      int lineNumber,
      FieldGetContext fieldContext,
      FieldSetContext fieldSetContext,
      ArrayGetContext arrayContext,
      ArraySetContext arraySetContext,
      TypeCheckContext typeCheckContext,
      ThrowContext throwContext,
      NewArrayContext newArrayContext) {
    OnMethod om = ph.om;

    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] argTypes = Type.getArgumentTypes(rawDesc);

    // Pre-validate: every argument must be satisfiable before we push anything onto the stack.
    // An early return mid-loop would leave orphaned stack values, causing a VerifyError.
    for (int i = 0; i < argTypes.length; i++) {
      int callArgIndex = callContext != null ? callArgumentIndex(om, i) : -1;
      int lineArgIndex = lineNumber != -1 ? ordinaryArgumentIndex(om, i) : -1;
      boolean satisfiable =
          i == om.getSelfParameter()
              || i == om.getClassNameParameter()
              || i == om.getMethodParameter()
              || (i == om.getReturnParameter() && retValSlot != -1)
              || (i == om.getDurationParameter() && durationSlot != -1)
              || (i == om.getTargetInstanceParameter()
                  && (callContext != null
                      || fieldContext != null
                      || fieldSetContext != null
                      || arrayContext != null
                      || arraySetContext != null
                      || (typeCheckContext != null && typeCheckContext.targetSlot != -1)
                      || (throwContext != null && throwContext.throwableSlot != -1)))
              || (i == om.getTargetMethodOrFieldParameter()
                  && (callContext != null || fieldContext != null || fieldSetContext != null))
              || lineArgIndex == 0
              || (fieldSetContext != null && ordinaryArgumentIndex(om, i) == 0)
              || (arrayContext != null && ordinaryArgumentIndex(om, i) == 0)
              || (arraySetContext != null
                  && (ordinaryArgumentIndex(om, i) == 0 || ordinaryArgumentIndex(om, i) == 1))
              || (typeCheckContext != null && ordinaryArgumentIndex(om, i) == 0)
              || (newArrayContext != null && ordinaryArgumentIndex(om, i) == 0)
              || (newArrayContext != null && ordinaryArgumentIndex(om, i) == 1)
              || (callArgIndex >= 0 && callArgIndex < callContext.argumentTypes.length);
      if (!satisfiable) {
        log.debug(
            "ClassFileApiBackend: skipping handler {}.{} — arg {} cannot be satisfied",
            ph.probe.getClassName(),
            om.getTargetName(),
            i);
        return false;
      }
    }

    for (int i = 0; i < argTypes.length; i++) {
      if (i == om.getSelfParameter()) {
        if (isStatic || (isEntry && methodName.equals("<init>"))) {
          cb.aconst_null();
        } else {
          cb.aload(0);
        }
      } else if (i == om.getClassNameParameter()) {
        cb.ldc(javaClassName);
      } else if (i == om.getMethodParameter()) {
        cb.ldc(methodName);
      } else if (i == om.getReturnParameter()) {
        // Use the actual store TypeKind, not the handler descriptor's TypeKind.
        // The handler may declare AnyType (→ Object), but the slot holds the raw JVM type.
        cb.loadLocal(returnKind, retValSlot);
        if (returnKind != TypeKind.REFERENCE
            && returnKind != TypeKind.VOID
            && typeKind(argTypes[i]) == TypeKind.REFERENCE) {
          boxPrimitiveReturn(cb, returnType, returnKind);
        }
      } else if (i == om.getDurationParameter()) {
        cb.loadLocal(TypeKind.LONG, durationSlot);
      } else if (i == om.getTargetInstanceParameter()) {
        if (fieldContext != null) {
          if (fieldContext.staticAccess) {
            cb.aconst_null();
          } else {
            cb.aload(fieldContext.ownerSlot);
          }
        } else if (fieldSetContext != null) {
          if (fieldSetContext.staticAccess) {
            cb.aconst_null();
          } else {
            cb.aload(fieldSetContext.ownerSlot);
          }
        } else if (arrayContext != null) {
          cb.aload(arrayContext.arraySlot);
        } else if (arraySetContext != null) {
          cb.aload(arraySetContext.arraySlot);
        } else if (typeCheckContext != null && typeCheckContext.targetSlot != -1) {
          cb.aload(typeCheckContext.targetSlot);
        } else if (throwContext != null && throwContext.throwableSlot != -1) {
          cb.aload(throwContext.throwableSlot);
        } else if (callContext == null || callContext.staticCall) {
          cb.aconst_null();
        } else {
          cb.aload(callContext.receiverSlot);
        }
      } else if (i == om.getTargetMethodOrFieldParameter()) {
        cb.ldc(
            fieldContext != null
                ? targetFieldName(om, fieldContext.instruction, fieldContext.staticAccess)
                : fieldSetContext != null
                    ? targetFieldName(om, fieldSetContext.instruction, fieldSetContext.staticAccess)
                    : targetMethodName(om, callContext));
      } else if (lineNumber != -1 && ordinaryArgumentIndex(om, i) == 0) {
        cb.ldc(lineNumber);
      } else if (fieldSetContext != null && ordinaryArgumentIndex(om, i) == 0) {
        cb.loadLocal(typeKind(fieldSetContext.fieldType), fieldSetContext.valueSlot);
        if (typeKind(fieldSetContext.fieldType) != TypeKind.REFERENCE
            && typeKind(argTypes[i]) == TypeKind.REFERENCE) {
          boxPrimitiveReturn(cb, fieldSetContext.fieldType, typeKind(fieldSetContext.fieldType));
        }
      } else if (arrayContext != null && ordinaryArgumentIndex(om, i) == 0) {
        cb.loadLocal(TypeKind.INT, arrayContext.indexSlot);
      } else if (arraySetContext != null && ordinaryArgumentIndex(om, i) == 0) {
        cb.loadLocal(TypeKind.INT, arraySetContext.indexSlot);
      } else if (arraySetContext != null && ordinaryArgumentIndex(om, i) == 1) {
        cb.loadLocal(arraySetContext.elementKind, arraySetContext.valueSlot);
        if (arraySetContext.elementKind != TypeKind.REFERENCE
            && typeKind(argTypes[i]) == TypeKind.REFERENCE) {
          boxPrimitiveReturn(cb, arraySetContext.elementType, arraySetContext.elementKind);
        }
      } else if (typeCheckContext != null && ordinaryArgumentIndex(om, i) == 0) {
        cb.ldc(typeCheckContext.typeName);
      } else if (newArrayContext != null && ordinaryArgumentIndex(om, i) == 0) {
        cb.ldc(newArrayContext.extName);
      } else if (newArrayContext != null && ordinaryArgumentIndex(om, i) == 1) {
        cb.ldc(newArrayContext.dims);
      } else if (callContext != null) {
        int callArgIndex = callArgumentIndex(om, i);
        cb.loadLocal(
            typeKind(callContext.argumentTypes[callArgIndex]),
            callContext.argumentSlots[callArgIndex]);
      }
    }

    String actionMethodName =
        InstrumentUtils.getActionPrefix(ph.probe.getClassName(true)) + om.getTargetName();
    DirectMethodHandleDesc bsmHandle =
        MethodHandleDesc.ofMethod(
            DirectMethodHandleDesc.Kind.STATIC, INDY_DISPATCHER, "bootstrap", BSM_TYPE);
    cb.invokedynamic(
        DynamicCallSiteDesc.of(
            bsmHandle,
            actionMethodName,
            MethodTypeDesc.ofDescriptor(rawDesc),
            ph.probe.getClassName(true)));
    return true;
  }

  private static void boxPrimitiveReturn(CodeBuilder cb, Type returnType, TypeKind returnKind) {
    String primitiveDesc = returnType != null ? returnType.getDescriptor() : primitiveDesc(returnKind);
    String wrapperInternalName = wrapperInternalName(primitiveDesc);
    cb.invokestatic(
        ClassDesc.ofInternalName(wrapperInternalName),
        "valueOf",
        MethodTypeDesc.ofDescriptor("(" + primitiveDesc + ")L" + wrapperInternalName + ";"));
  }

  private static String primitiveDesc(TypeKind returnKind) {
    switch (returnKind) {
      case LONG:
        return "J";
      case FLOAT:
        return "F";
      case DOUBLE:
        return "D";
      default:
        return "I";
    }
  }

  private static String wrapperInternalName(String primitiveDesc) {
    switch (primitiveDesc) {
      case "Z":
        return "java/lang/Boolean";
      case "B":
        return "java/lang/Byte";
      case "C":
        return "java/lang/Character";
      case "S":
        return "java/lang/Short";
      case "J":
        return "java/lang/Long";
      case "F":
        return "java/lang/Float";
      case "D":
        return "java/lang/Double";
      default:
        return "java/lang/Integer";
    }
  }

  private static String targetMethodName(OnMethod om, CallContext ctx) {
    String name = ctx.instruction.name().stringValue();
    if (om.isTargetMethodOrFieldFqn()) {
      return invocationKind(ctx.instruction.opcode())
          + " "
          + TypeUtils.descriptorToSimplified(
              ctx.instruction.type().stringValue(), ctx.instruction.owner().asInternalName(), name);
    }
    return name;
  }

  private static String targetFieldName(
      OnMethod om, FieldInstruction instruction, boolean staticAccess) {
    String name = instruction.name().stringValue();
    if (om.isTargetMethodOrFieldFqn()) {
      String prefix = staticAccess ? "static field" : "field";
      return prefix
          + " "
          + TypeUtils.descriptorToSimplified(
              instruction.type().stringValue(), instruction.owner().asInternalName(), name);
    }
    return name;
  }

  private static String typeKindToJavaName(TypeKind tk) {
    switch (tk) {
      case BOOLEAN:
        return "boolean";
      case CHAR:
        return "char";
      case FLOAT:
        return "float";
      case DOUBLE:
        return "double";
      case BYTE:
        return "byte";
      case SHORT:
        return "short";
      case INT:
        return "int";
      case LONG:
        return "long";
      default:
        return tk.name().toLowerCase(java.util.Locale.ROOT);
    }
  }

  private static Type primitiveArrayType(TypeKind tk) {
    switch (tk) {
      case BOOLEAN:
        return Type.getType("[Z");
      case CHAR:
        return Type.getType("[C");
      case FLOAT:
        return Type.getType("[F");
      case DOUBLE:
        return Type.getType("[D");
      case BYTE:
        return Type.getType("[B");
      case SHORT:
        return Type.getType("[S");
      case INT:
        return Type.getType("[I");
      case LONG:
        return Type.getType("[J");
      default:
        throw new IllegalArgumentException("Not a primitive TypeKind: " + tk);
    }
  }

  private static String invocationKind(Opcode opcode) {
    switch (opcode) {
      case INVOKEINTERFACE:
        return "interface";
      case INVOKESPECIAL:
        return "special";
      case INVOKESTATIC:
        return "static";
      case INVOKEVIRTUAL:
        return "virtual";
      case INVOKEDYNAMIC:
        return "dynamic";
      default:
        return "";
    }
  }
}
