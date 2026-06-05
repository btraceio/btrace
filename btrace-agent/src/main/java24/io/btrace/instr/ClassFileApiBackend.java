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
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Where;

import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrumentation backend for class file versions that ASM cannot parse (&gt; 69, i.e. Java 26+).
 * Uses the JDK ClassFile API ({@code java.lang.classfile.*}), available since JDK 24.
 *
 * <p>Supported probe kinds: {@link Kind#ENTRY}, {@link Kind#RETURN}, and {@link Kind#CALL}.
 * Handlers with the {@code @Self} parameter on instance methods are supported. {@code @Return}
 * parameters are supported for non-void methods (void methods are silently skipped).
 * {@code @Duration} parameters are supported covering both normal and exceptional method exits.
 * Method call handlers support ordinary called arguments.
 *
 * <p>Method call handlers support {@code @TargetInstance} and {@code @TargetMethodOrField}.
 * Type-constrained method matching ({@code type="..."} in {@code @OnMethod}) is not supported.
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
    for (ProbeHandler ph : handlers) {
      Kind kind = ph.om.getLocation().getValue();
      if (kind == Kind.ENTRY) entryHandlers.add(ph);
      else if (kind == Kind.RETURN) returnHandlers.add(ph);
      else if (kind == Kind.CALL) callHandlers.add(ph);
      else log.debug("Skipping unsupported probe kind {} for class {}", kind, javaClassName);
    }
    if (entryHandlers.isEmpty() && returnHandlers.isEmpty() && callHandlers.isEmpty()) return null;

    boolean[] anyMatch = {false};
    byte[] result =
        cf.transformClass(
            classModel,
            buildClassTransform(
                loader, javaClassName, entryHandlers, returnHandlers, callHandlers, anyMatch));
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
      boolean[] anyMatch) {
    return (classBuilder, classElement) -> {
      if (classElement instanceof MethodModel mm) {
        String methodName = mm.methodName().stringValue();
        boolean isStatic = mm.flags().has(AccessFlag.STATIC);
        List<ProbeHandler> mEntry = filterForMethod(entryHandlers, methodName);
        List<ProbeHandler> mReturn = filterForMethod(returnHandlers, methodName);
        List<ProbeHandler> mCall = filterForMethod(callHandlers, methodName);
        if (!mEntry.isEmpty() || !mReturn.isEmpty() || !mCall.isEmpty()) {
          if (!mEntry.isEmpty() || !mReturn.isEmpty()) {
            anyMatch[0] = true;
          }
          classBuilder.transformMethod(
              mm,
              buildMethodTransform(
                  loader, javaClassName, methodName, isStatic, mEntry, mReturn, mCall, anyMatch));
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
      boolean isStatic,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers,
      List<ProbeHandler> callHandlers,
      boolean[] anyMatch) {
    return (methodBuilder, methodElement) -> {
      if (methodElement instanceof CodeModel cm) {
        methodBuilder.transformCode(
            cm,
            buildCodeTransform(
                loader,
                javaClassName,
                methodName,
                isStatic,
                entryHandlers,
                returnHandlers,
                callHandlers,
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
      boolean isStatic,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers,
      List<ProbeHandler> callHandlers,
      boolean[] anyMatch) {

    boolean hasDuration =
        returnHandlers.stream().anyMatch(ph -> ph.om.getDurationParameter() != -1);
    boolean hasReturnParam =
        returnHandlers.stream().anyMatch(ph -> ph.om.getReturnParameter() != -1);

    return new CodeTransform() {
      private final int[] entryTsSlot = {-1};
      private final int[] retValSlot = {-1};
      private final int[] durationSlot = {-1};
      private final boolean[] entryInjected = {false};
      private Label startLabel;

      @Override
      public void atStart(CodeBuilder cb) {
        if (hasDuration) {
          startLabel = cb.newLabel();
          // labelBinding deferred to accept() so it is only emitted when entryTsSlot is allocated
        }
      }

      @Override
      public void accept(CodeBuilder cb, CodeElement ce) {
        if (!entryInjected[0] && !(ce instanceof PseudoInstruction)) {
          entryInjected[0] = true;
          if (hasDuration) {
            // Bind try-region start here, in sync with entryTsSlot allocation
            cb.labelBinding(startLabel);
            entryTsSlot[0] = cb.allocateLocal(TypeKind.LONG);
            cb.invokestatic(
                ClassDesc.ofInternalName("java/lang/System"),
                "nanoTime",
                MethodTypeDesc.ofDescriptor("()J"));
            cb.storeLocal(TypeKind.LONG, entryTsSlot[0]);
          }
          for (ProbeHandler ph : entryHandlers) {
            emitProbeCall(cb, ph, javaClassName, methodName, isStatic, true, -1, TypeKind.VOID, -1);
          }
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

          // @Duration: compute nanoTime - entryTs
          int localDurationSlot = -1;
          if (hasDuration && entryTsSlot[0] != -1) {
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

          for (ProbeHandler ph : returnHandlers) {
            emitProbeCall(
                cb,
                ph,
                javaClassName,
                methodName,
                isStatic,
                false,
                localRetValSlot,
                returnKind,
                localDurationSlot);
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
            boolean emitted = false;
            for (ProbeHandler ph : emittableBefore) {
              emitted |= emitCallProbe(cb, ph, javaClassName, methodName, isStatic, true, callContext);
            }
            restoreCallStack(cb, callContext);
            emitInvoke(cb, ii);
            for (ProbeHandler ph : emittableAfter) {
              emitted |= emitCallProbe(cb, ph, javaClassName, methodName, isStatic, false, callContext);
            }
            if (emitted) {
              anyMatch[0] = true;
            }
            return;
          }
        }

        cb.with(ce);
      }

      @Override
      public void atEnd(CodeBuilder cb) {
        // Inject finally-block style exception handler for @Duration
        if (!hasDuration || entryTsSlot[0] == -1 || startLabel == null) return;

        List<ProbeHandler> durationHandlers = new ArrayList<>();
        for (ProbeHandler ph : returnHandlers) {
          if (ph.om.getDurationParameter() != -1) durationHandlers.add(ph);
        }
        if (durationHandlers.isEmpty()) return;

        Label endLabel = cb.newLabel();
        Label handlerLabel = cb.newLabel();

        // Bind end-of-try and handler-start positions before registering the catch range.
        // The ClassFile API resolves label byte offsets at method build time, so all three
        // labels just need to be bound before the CodeBuilder is finalized.
        cb.labelBinding(endLabel);
        cb.labelBinding(handlerLabel);
        cb.exceptionCatchAll(startLabel, endLabel, handlerLabel);

        // Stack: [Throwable]
        int exSlot = cb.allocateLocal(TypeKind.REFERENCE);
        cb.astore(exSlot);

        // Compute duration delta
        int exDurationSlot =
            durationSlot[0] != -1 ? durationSlot[0] : cb.allocateLocal(TypeKind.LONG);
        cb.invokestatic(
            ClassDesc.ofInternalName("java/lang/System"),
            "nanoTime",
            MethodTypeDesc.ofDescriptor("()J"));
        cb.loadLocal(TypeKind.LONG, entryTsSlot[0]);
        cb.lsub();
        cb.storeLocal(TypeKind.LONG, exDurationSlot);

        // Fire handlers (retValSlot=-1: no return value on exception path)
        for (ProbeHandler ph : durationHandlers) {
          emitProbeCall(
              cb, ph, javaClassName, methodName, isStatic, false, -1, TypeKind.VOID, exDurationSlot);
        }

        // Rethrow
        cb.aload(exSlot);
        cb.athrow();
      }
    };
  }

  private static List<ProbeHandler> filterForMethod(
      List<ProbeHandler> handlers, String methodName) {
    List<ProbeHandler> result = new ArrayList<>();
    for (ProbeHandler ph : handlers) {
      String pattern = ph.om.getMethod();
      if (pattern == null || pattern.isEmpty()) continue;
      boolean nameMatch;
      if (pattern.startsWith("/") && pattern.endsWith("/")) {
        nameMatch = methodName.matches(pattern.substring(1, pattern.length() - 1));
      } else {
        nameMatch = pattern.equals(methodName);
      }
      if (!nameMatch) continue;
      // om.getType() returns the 'type' attribute of @OnMethod — only set when the user
      // explicitly constrains the handler to a specific method signature. For @Return /
      // @Duration handlers this is normally empty, so they pass through unaffected.
      String typePattern = ph.om.getType();
      if (!typePattern.isEmpty()) {
        log.debug(
            "ClassFileApiBackend: skipping type-constrained handler {}.{} — type matching unsupported",
            ph.probe.getClassName(),
            ph.om.getTargetName());
        continue;
      }
      result.add(ph);
    }
    return result;
  }

  private static List<ProbeHandler> filterForCall(List<ProbeHandler> handlers, InvokeInstruction ii) {
    List<ProbeHandler> result = new ArrayList<>();
    String owner = ii.owner().asInternalName().replace('/', '.');
    String name = ii.name().stringValue();
    String desc = ii.type().stringValue();
    for (ProbeHandler ph : handlers) {
      Location loc = ph.om.getLocation();
      if (!matches(loc.getClazz(), owner)) continue;
      if (!matches(loc.getMethod(), name)) continue;
      if (!matches(loc.getType(), desc)) continue;
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

  private static void emitInvoke(CodeBuilder cb, InvokeInstruction ii) {
    cb.invoke(ii.opcode(), ii.method());
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
      if (i == om.getReturnParameter() || i == om.getDurationParameter()) {
        return false;
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
    OnMethod om = ph.om;

    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] argTypes = Type.getArgumentTypes(rawDesc);

    // Pre-validate: every argument must be satisfiable before we push anything onto the stack.
    // An early return mid-loop would leave orphaned stack values, causing a VerifyError.
    for (int i = 0; i < argTypes.length; i++) {
      int callArgIndex = callContext != null ? callArgumentIndex(om, i) : -1;
      boolean satisfiable =
          i == om.getSelfParameter()
              || i == om.getClassNameParameter()
              || i == om.getMethodParameter()
              || (i == om.getReturnParameter() && retValSlot != -1)
              || (i == om.getDurationParameter() && durationSlot != -1)
              || (i == om.getTargetInstanceParameter() && callContext != null)
              || (i == om.getTargetMethodOrFieldParameter() && callContext != null)
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
      } else if (i == om.getDurationParameter()) {
        cb.loadLocal(TypeKind.LONG, durationSlot);
      } else if (i == om.getTargetInstanceParameter()) {
        if (callContext == null || callContext.staticCall) {
          cb.aconst_null();
        } else {
          cb.aload(callContext.receiverSlot);
        }
      } else if (i == om.getTargetMethodOrFieldParameter()) {
        cb.ldc(targetMethodName(om, callContext));
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
