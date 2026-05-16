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

import io.btrace.core.annotations.Kind;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.PseudoInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrumentation backend for class file versions that ASM cannot parse (&gt; 69, i.e. Java 26+).
 * Uses the JDK ClassFile API ({@code java.lang.classfile.*}), available since JDK 24.
 *
 * <p>Supported probe kinds: {@link Kind#ENTRY}, {@link Kind#RETURN}. Handlers with unsupported
 * special parameters ({@code @Self}, {@code @Return}, {@code @TargetInstance}, {@code @Duration})
 * or type-constrained method matching are skipped with a debug-level log; the remaining handlers
 * are applied.
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

  private static final ClassDesc INDY_DISPATCHER =
      ClassDesc.of("io.btrace.runtime.IndyDispatcher");

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
    for (ProbeHandler ph : handlers) {
      Kind kind = ph.om.getLocation().getValue();
      if (kind == Kind.ENTRY) entryHandlers.add(ph);
      else if (kind == Kind.RETURN) returnHandlers.add(ph);
      else log.debug("Skipping unsupported probe kind {} for class {}", kind, javaClassName);
    }
    if (entryHandlers.isEmpty() && returnHandlers.isEmpty()) return null;

    boolean[] anyMatch = {false};
    byte[] result =
        cf.transformClass(
            classModel, buildClassTransform(javaClassName, entryHandlers, returnHandlers, anyMatch));
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

  private static Collection<String> collectAnnotationTypes(ClassModel classModel) {
    return classModel
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .map(
            attr ->
                attr.annotations().stream()
                    .map(a -> {
                      String desc = a.classSymbol().descriptorString();
                      // ClassFile API returns annotation class descriptors; probe matching expects dot-separated Java names
                      return desc.substring(1, desc.length() - 1).replace('/', '.');
                    })
                    .collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }

  private static ClassMeta buildClassMeta(
      String javaClassName,
      String internalName,
      Collection<String> annoTypes,
      ClassLoader loader) {
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
      String javaClassName,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers,
      boolean[] anyMatch) {
    return (classBuilder, classElement) -> {
      if (classElement instanceof MethodModel mm) {
        String methodName = mm.methodName().stringValue();
        List<ProbeHandler> mEntry = filterForMethod(entryHandlers, methodName);
        List<ProbeHandler> mReturn = filterForMethod(returnHandlers, methodName);
        if (!mEntry.isEmpty() || !mReturn.isEmpty()) {
          anyMatch[0] = true;
          classBuilder.transformMethod(
              mm, buildMethodTransform(javaClassName, methodName, mEntry, mReturn));
        } else {
          classBuilder.with(classElement);
        }
      } else {
        classBuilder.with(classElement);
      }
    };
  }

  private static MethodTransform buildMethodTransform(
      String javaClassName,
      String methodName,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers) {
    return (methodBuilder, methodElement) -> {
      if (methodElement instanceof CodeModel cm) {
        methodBuilder.transformCode(
            cm, buildCodeTransform(javaClassName, methodName, entryHandlers, returnHandlers));
      } else {
        methodBuilder.with(methodElement);
      }
    };
  }

  private static CodeTransform buildCodeTransform(
      String javaClassName,
      String methodName,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers) {
    boolean[] entryInjected = {false};
    return (codeBuilder, codeElement) -> {
      if (!entryInjected[0] && !(codeElement instanceof PseudoInstruction)) {
        entryInjected[0] = true;
        for (ProbeHandler ph : entryHandlers) {
          emitProbeCall(codeBuilder, ph, javaClassName, methodName);
        }
      }
      if (!returnHandlers.isEmpty() && codeElement instanceof ReturnInstruction) {
        for (ProbeHandler ph : returnHandlers) {
          emitProbeCall(codeBuilder, ph, javaClassName, methodName);
        }
      }
      codeBuilder.with(codeElement);
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

  private static void emitProbeCall(
      java.lang.classfile.CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName) {
    OnMethod om = ph.om;
    if (om.getSelfParameter() != -1
        || om.getReturnParameter() != -1
        || om.getTargetInstanceParameter() != -1
        || om.getDurationParameter() != -1
        || om.getTargetMethodOrFieldParameter() != -1) {
      log.debug(
          "ClassFileApiBackend: skipping handler {}.{} — unsupported special params",
          ph.probe.getClassName(),
          om.getTargetName());
      return;
    }

    String rawDesc =
        om.getTargetDescriptor().replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);
    Type[] argTypes = Type.getArgumentTypes(rawDesc);

    for (int i = 0; i < argTypes.length; i++) {
      if (i != om.getClassNameParameter() && i != om.getMethodParameter()) {
        log.debug(
            "ClassFileApiBackend: skipping handler {}.{} — unsupported arg at index {}",
            ph.probe.getClassName(),
            om.getTargetName(),
            i);
        return;
      }
    }

    for (int i = 0; i < argTypes.length; i++) {
      if (i == om.getClassNameParameter()) {
        cb.ldc(javaClassName);
      } else if (i == om.getMethodParameter()) {
        cb.ldc(methodName);
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
  }
}
