/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.openjdk.btrace.instr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.annotations.Return;
import org.openjdk.btrace.runtime.BTraceRuntimeImplBase;

/**
 * This class preprocesses a compiled BTrace program. This is done after BTrace safety verification
 * but before instrumenting the probed classes.
 *
 * <p>Transformations done here:
 *
 * <p>1. add <clinit> method, if one not found 2. replace @Export fields by perf counters and
 * replace put/get by perf counter update/read 3. replace @TLS fields by ThreadLocal fields and
 * replace put/get by ThreadLocal set/get 4. In <clinit> method, add ThreadLocal creation and perf
 * counter creation calls (for @Export and
 *
 * @author A. Sundararajan
 * @author J. Bachorik (Tree API rewrite) @TLS fields respectively) 5. Add a field to store
 *     BTraceRuntime object and initialize the same in <clinit> method 6. add prolog and epilogue in
 *     each BTrace action method to insert BTraceRuntime.enter/leave and also to call
 *     BTraceRuntimeImplBase.handleException on exception catch 7. initialize and reference any
 *     service instances 8. add a field to store client's BTraceRuntime instance 9. make all fields
 *     publicly accessible
 */
final class Preprocessor {
  private static final String ANNOTATIONS_PREFIX = "org/openjdk/btrace/core/annotations/";
  private static final Type TLS_TYPE = Type.getType("L" + ANNOTATIONS_PREFIX + "TLS;");
  private static final Type EXPORT_TYPE = Type.getType("L" + ANNOTATIONS_PREFIX + "Export;");
  private static final Type INJECTED_TYPE = Type.getType("L" + ANNOTATIONS_PREFIX + "Injected;");
  private static final Type EVENT_TYPE = Type.getType("L" + ANNOTATIONS_PREFIX + "Event;");
  private static final Type JFRPERIODIC_TYPE = Type.getType("L" + ANNOTATIONS_PREFIX + "JfrPeriodicEventHandler;");
  private static final Type BTRACE_TYPE = Type.getType("L" + ANNOTATIONS_PREFIX + "BTrace;");
  private static final Type JFRBLOCK_TYPE = Type.getType("L" + ANNOTATIONS_PREFIX + "JfrBlock;");
  private static final String SERVICE_INTERNAL = "org/openjdk/btrace/services/api/Service";
  private static final String TIMERHANDLER_INTERNAL =
      "org/openjdk/btrace/core/handlers/TimerHandler";
  private static final String TIMERHANDLER_DESC = "L" + TIMERHANDLER_INTERNAL + ";";
  private static final String EVENTHANDLER_INTERNAL =
      "org/openjdk/btrace/core/handlers/EventHandler";
  private static final String EVENTHANDLER_DESC = "L" + EVENTHANDLER_INTERNAL + ";";
  private static final String ERRORHANDLER_INTERNAL =
      "org/openjdk/btrace/core/handlers/ErrorHandler";
  private static final String ERRORHANDLER_DESC = "L" + ERRORHANDLER_INTERNAL + ";";
  private static final String EXITHANDLER_INTERNAL = "org/openjdk/btrace/core/handlers/ExitHandler";
  private static final String EXITHANDLER_DESC = "L" + EXITHANDLER_INTERNAL + ";";
  private static final String LOWMEMORYHANDLER_INTERNAL =
      "org/openjdk/btrace/core/handlers/LowMemoryHandler";
  private static final String LOWMEMORYHANDLER_DESC = "L" + LOWMEMORYHANDLER_INTERNAL + ";";
  private static final String NEW_TLS_DESC =
      "(" + Constants.OBJECT_DESC + ")" + Constants.THREAD_LOCAL_DESC;
  private static final String TLS_SET_DESC =
      "(" + Constants.OBJECT_DESC + ")" + Constants.VOID_DESC;
  private static final String TLS_GET_DESC = "()" + Constants.OBJECT_DESC;
  private static final String NEW_PERFCOUNTER_DESC =
      "("
          + Constants.OBJECT_DESC
          + Constants.STRING_DESC
          + Constants.STRING_DESC
          + ")"
          + Constants.VOID_DESC;
  private static final String BTRACERT_FOR_CLASS_DESC =
      "("
          + Constants.CLASS_DESC
          + "["
          + TIMERHANDLER_DESC
          + "["
          + EVENTHANDLER_DESC
          + "["
          + ERRORHANDLER_DESC
          + "["
          + EXITHANDLER_DESC
          + "["
          + LOWMEMORYHANDLER_DESC
          + ")"
          + Constants.BTRACERTBASE_DESC;
  private static final String BTRACERT_ENTER_DESC =
      "(" + Constants.BTRACERTIMPL_DESC + ")" + Constants.BOOLEAN_DESC;
  private static final String BTRACERT_HANDLE_EXCEPTION_DESC =
      "(" + Constants.THROWABLE_DESC + ")" + Constants.VOID_DESC;
  private static final String RT_CTX_INTERNAL = "org/openjdk/btrace/services/api/RuntimeContext";
  private static final String RT_CTX_DESC = "L" + RT_CTX_INTERNAL + ";";
  private static final Type RT_CTX_TYPE = Type.getType(RT_CTX_DESC);
  private static final String RT_SERVICE_CTR_DESC = "(" + RT_CTX_DESC + ")V";
  private static final String SERVICE_CTR_DESC =
      "(" + Constants.STRING_DESC + ")" + Constants.VOID_DESC;
  private static final Map<String, String> BOX_TYPE_MAP = new HashMap<>();
  private static final Set<String> GUARDED_ANNOTS = new HashSet<>();
  private static final Set<String> RT_AWARE_ANNOTS = new HashSet<>();
  // For each @Export field, we create a perf counter
  // with the name "btrace.<class name>.<field name>"
  private static final String BTRACE_COUNTER_PREFIX = "btrace.";
  private static final String JFR_HANDLER_FIELD_PREFIX = "$jfr$handler$";

  static {
    BOX_TYPE_MAP.put("I", Constants.INTEGER_BOXED_DESC);
    BOX_TYPE_MAP.put("S", Constants.SHORT_BOXED_DESC);
    BOX_TYPE_MAP.put("J", Constants.LONG_BOXED_DESC);
    BOX_TYPE_MAP.put("F", Constants.FLOAT_BOXED_DESC);
    BOX_TYPE_MAP.put("D", Constants.DOUBLE_BOXED_DESC);
    BOX_TYPE_MAP.put("B", Constants.BYTE_BOXED_DESC);
    BOX_TYPE_MAP.put("Z", Constants.BOOLEAN_BOXED_DESC);
    BOX_TYPE_MAP.put("C", Constants.CHARACTER_BOXED_DESC);

    RT_AWARE_ANNOTS.add(Constants.ONMETHOD_DESC);
    RT_AWARE_ANNOTS.add(Constants.ONTIMER_DESC);
    RT_AWARE_ANNOTS.add(Constants.ONEVENT_DESC);
    RT_AWARE_ANNOTS.add(Constants.ONERROR_DESC);
    RT_AWARE_ANNOTS.add(Constants.ONPROBE_DESC);
    RT_AWARE_ANNOTS.add(Constants.JFRPERIODIC_DESC);

    GUARDED_ANNOTS.addAll(RT_AWARE_ANNOTS);

    // @OnExit is rtAware but not guarded
    RT_AWARE_ANNOTS.add(Constants.ONEXIT_DESC);
  }

  private final Set<String> tlsFldNames = new HashSet<>();
  private final Set<String> exportFldNames = new HashSet<>();
  private final Set<String> jfrHandlerNames = new HashSet<>();
  private final Map<String, AnnotationNode> eventFlds = new HashMap<>();
  private final Map<String, AnnotationNode> injectedFlds = new HashMap<>();
  private final Map<String, Integer> serviceLocals = new HashMap<>();
  private final DebugSupport debug;
  private MethodNode clinit = null;
  private FieldNode rtField = null;

  private Map<MethodNode, EnumSet<MethodClassifier>> classifierMap = new HashMap<>();
  private AbstractInsnNode clinitEntryPoint;

  public Preprocessor(DebugSupport debug) {
    this.debug = debug;
  }

  public static AnnotationNode getAnnotation(FieldNode fn, Type annotation) {
    if (fn == null || (fn.visibleAnnotations == null && fn.invisibleAnnotations == null))
      return null;
    String targetDesc = annotation.getDescriptor();
    if (fn.visibleAnnotations != null) {
      for (AnnotationNode an : fn.visibleAnnotations) {
        if (an.desc.equals(targetDesc)) {
          return an;
        }
      }
    }
    if (fn.invisibleAnnotations != null) {
      for (AnnotationNode an : fn.invisibleAnnotations) {
        if (an.desc.equals(targetDesc)) {
          return an;
        }
      }
    }
    return null;
  }

  public static AnnotationNode getAnnotation(MethodNode mn, Type annotation) {
    if (mn == null || mn.visibleAnnotations == null) return null;
    String targetDesc = annotation.getDescriptor();
    for (AnnotationNode an : mn.visibleAnnotations) {
      if (an.desc.equals(targetDesc)) {
        return an;
      }
    }
    return null;
  }

  private static boolean isUnannotated(MethodNode mn) {
    return mn == null || mn.visibleAnnotations == null || mn.visibleAnnotations.isEmpty();
  }

  public void process(ClassNode cn) {
    addLevelField(cn);
    processClinit(cn);
    processFields(cn);

    for (MethodNode mn : getMethods(cn)) {
      preprocessMethod(cn, mn);
    }

    InsnList eventsInit = new InsnList();
    for (Map.Entry<String, AnnotationNode> eventEntry : eventFlds.entrySet()) {
      String fieldName = eventEntry.getKey();
      AnnotationNode an = eventEntry.getValue();
      String name = null;
      String label = null;
      String desc = null;
      String[] category = null;
      String fields = null;
      String handler = null;
      String period = null;
      boolean stacktrace = false;
      Iterator<Object> iter = an.values.iterator();
      while (iter.hasNext()) {
        String key = (String)iter.next();
        Object value = iter.next();
        switch (key) {
          case "name": {
            name = (String)value;
            break;
          }
          case "label": {
            label = (String)value;
            label = label.isEmpty() ? null : label;
            break;
          }
          case "description": {
            desc = (String)value;
            desc = desc.isEmpty() ? null : desc;
            break;
          }
          case "category": {
            category = (String[])value;
            break;
          }
          case "fields": {
            fields = (String)value;
            break;
          }
          case "stacktrace": {
            stacktrace = (boolean)value;
            break;
          }
          case "period": {
            period = (String)value;
            period = period.isEmpty() ? null : period;
            break;
          }
        }
      }
      if (fieldName.startsWith(JFR_HANDLER_FIELD_PREFIX)) {
        handler = fieldName.substring(JFR_HANDLER_FIELD_PREFIX.length());
        jfrHandlerNames.add(handler);
      }
      eventsInit.add(new TypeInsnNode(Opcodes.NEW, "org/openjdk/btrace/core/jfr/JfrEvent$Template"));
      eventsInit.add(new InsnNode(Opcodes.DUP));
      eventsInit.add(new LdcInsnNode(cn.name.replace('/', '.')));
      eventsInit.add(new LdcInsnNode(name));
      eventsInit.add(label != null ? new LdcInsnNode(label) : new InsnNode(Opcodes.ACONST_NULL));
      eventsInit.add(desc != null ? new LdcInsnNode(desc) : new InsnNode(Opcodes.ACONST_NULL));
      eventsInit.add(category != null ? new LdcInsnNode(label) : new InsnNode(Opcodes.ACONST_NULL));
      eventsInit.add(new LdcInsnNode(fields));
      eventsInit.add(new LdcInsnNode(stacktrace));
      eventsInit.add(period != null ? new LdcInsnNode(period) : new InsnNode(Opcodes.ACONST_NULL));
      eventsInit.add(handler != null ? new LdcInsnNode(handler) : new InsnNode(Opcodes.ACONST_NULL));
      eventsInit.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "org/openjdk/btrace/core/jfr/JfrEvent$Template", "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;Ljava/lang/String;)V", false));
      eventsInit.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/openjdk/btrace/core/BTraceRuntime", "createEventFactory", "(Lorg/openjdk/btrace/core/jfr/JfrEvent$Template;)Lorg/openjdk/btrace/core/jfr/JfrEvent$Factory;", false));
      eventsInit.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, eventEntry.getKey(), "Lorg/openjdk/btrace/core/jfr/JfrEvent$Factory;"));
    }
    clinit.instructions.insertBefore(clinitEntryPoint, eventsInit);
  }

  private void addLevelField(ClassNode cn) {
    if (cn.fields == null) {
      cn.fields = new ArrayList<>();
    }
    cn.fields.add(
        new FieldNode(
            Opcodes.ASM7,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE,
            Constants.BTRACE_LEVEL_FLD,
            Constants.INT_DESC,
            null,
            0));
  }

  private void preprocessMethod(ClassNode cn, MethodNode mn) {
    // !!! The order of execution is important here !!!
    LocalVarGenerator lvg = new LocalVarGenerator(mn);
    makePublic(mn);
    checkAugmentedReturn(mn);
    scanMethodInstructions(cn, mn, lvg);
    addBTraceErrorHandler(cn, mn);
    addBTraceRuntimeEnter(cn, mn);
    addJfrHandlerField(cn, mn);

    recalculateVars(mn);
  }

  private void makePublic(MethodNode mn) {
    if (!mn.name.contains("init>") && isUnannotated(mn)) {
      if ((mn.access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC) {
        mn.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
        mn.access |= Opcodes.ACC_PUBLIC;
      }
    }
  }

  private void processFields(ClassNode cn) {
    Iterator<FieldNode> iter = getFields(cn).iterator();
    while (iter.hasNext()) {
      FieldNode fn = iter.next();
      fn.access = (fn.access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
      tryProcessTLS(cn, fn);
      tryProcessExport(cn, fn);
      if (tryProcessInjected(fn) == null) {
        iter.remove();
      }
      tryProcessEvent(fn);
    }
  }

  private void tryProcessTLS(ClassNode cn, FieldNode fn) {
    AnnotationNode an = null;
    if ((an = getAnnotation(fn, TLS_TYPE)) != null) {
      fn.visibleAnnotations.remove(an);
      String origDesc = fn.desc;
      String boxedDesc = boxDesc(origDesc);
      fn.desc = Constants.THREAD_LOCAL_DESC;
      fn.signature = fn.desc.substring(0, fn.desc.length() - 1) + "<" + boxedDesc + ">;";
      initTLS(cn, fn, origDesc);
    }
  }

  private void tryProcessExport(ClassNode cn, FieldNode fn) {
    AnnotationNode an = null;
    if ((an = getAnnotation(fn, EXPORT_TYPE)) != null) {
      fn.visibleAnnotations.remove(an);
      String origDesc = fn.desc;

      initExport(cn, fn, origDesc);
    }
  }

  private FieldNode tryProcessInjected(FieldNode fn) {
    AnnotationNode an = null;
    if ((an = getAnnotation(fn, INJECTED_TYPE)) != null) {
      if (fn.visibleAnnotations != null) fn.visibleAnnotations.remove(an);
      if (fn.invisibleAnnotations != null) fn.invisibleAnnotations.remove(an);

      injectedFlds.put(fn.name, an);
      return null;
    }
    return fn;
  }

  private void tryProcessEvent(FieldNode fn) {
    AnnotationNode an;
    if ((an = getAnnotation(fn, EVENT_TYPE)) != null) {
      if (fn.visibleAnnotations != null) fn.visibleAnnotations.remove(an);
      if (fn.invisibleAnnotations != null) fn.invisibleAnnotations.remove(an);
      eventFlds.put(fn.name, an);
    }
  }

  private void initTLS(ClassNode cn, FieldNode fn, String typeDesc) {
    tlsFldNames.add(fn.name);

    initAnnotatedField(fn, typeDesc, tlsInitSequence(cn, fn.name, fn.desc));
  }

  private InsnList tlsInitSequence(ClassNode cn, String name, String desc) {
    InsnList initList = new InsnList();
    initList.add(
        new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Constants.BTRACERTACCESSL_INTERNAL,
            "newThreadLocal",
            NEW_TLS_DESC,
            false));
    initList.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, name, desc));
    return initList;
  }

  private void initExport(ClassNode cn, FieldNode fn, String typeDesc) {
    exportFldNames.add(fn.name);
    initAnnotatedField(fn, typeDesc, exportInitSequence(cn, fn.name, fn.desc));
  }

  private InsnList exportInitSequence(ClassNode cn, String name, String desc) {
    InsnList init = new InsnList();

    init.add(getRuntimeImpl(cn));
    init.add(new InsnNode(Opcodes.SWAP));
    init.add(new LdcInsnNode(perfCounterName(cn, name)));
    init.add(new LdcInsnNode(desc));
    init.add(
        new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            Constants.BTRACERTBASE_INTERNAL,
            "newPerfCounter",
            NEW_PERFCOUNTER_DESC,
            false));

    return init;
  }

  private void initAnnotatedField(FieldNode fn, String typeDesc, InsnList initList) {
    Object initVal = fn.value;
    fn.value = null;
    fn.access |= Opcodes.ACC_FINAL;

    InsnList l = clinit.instructions;

    MethodInsnNode boxNode;
    if (TypeUtils.isPrimitive(typeDesc)) {
      boxNode = boxNode(typeDesc);
      initList.insert(boxNode);
    }

    if (initVal != null) {
      initList.insert(new LdcInsnNode(initVal));
    } else {
      // find first fld store; this is the initialization place
      AbstractInsnNode initNode = findNodeInitialization(fn);

      if (initNode != null) {
        // found the initialization place;
        // just replace the FLD_STORE with the field init sequence
        l.insert(initNode, initList);
        l.remove(initNode);
        return;
      } else {
        // no initialization done; use primitive defaults or NULL
        addDefaultVal(Type.getType(typeDesc), initList);
      }
    }
    MethodInsnNode rtStart = findBTraceRuntimeStart();
    if (rtStart != null) {
      l.insertBefore(rtStart, initList);
    } else {
      l.add(initList);
    }
  }

  private void addDefaultVal(Type t, InsnList l) {
    switch (t.getSort()) {
      case Type.INT:
      case Type.SHORT:
      case Type.BOOLEAN:
      case Type.BYTE:
      case Type.CHAR:
        {
          l.insert(new InsnNode(Opcodes.ICONST_0));
          break;
        }
      case Type.LONG:
        {
          l.insert(new InsnNode(Opcodes.LCONST_0));
          break;
        }
      case Type.FLOAT:
        {
          l.insert(new InsnNode(Opcodes.FCONST_0));
          break;
        }
      case Type.DOUBLE:
        {
          l.insert(new InsnNode(Opcodes.DCONST_0));
          break;
        }
      default:
        {
          l.insert(new InsnNode(Opcodes.ACONST_NULL));
        }
    }
  }

  @SuppressWarnings("unchecked")
  private void checkAugmentedReturn(MethodNode mn) {
    if (isUnannotated(mn)) return;

    Type retType = Type.getReturnType(mn.desc);
    if (retType.getSort() != Type.VOID) {
      if (getReturnMethodParameter(mn) == Integer.MIN_VALUE) {
        // insert the method return parameter
        String oldDesc = mn.desc;
        Type[] args = Type.getArgumentTypes(mn.desc);
        args = Arrays.copyOf(args, args.length + 1);
        args[args.length - 1] = retType;

        List<AnnotationNode> annots = new ArrayList<>();
        AnnotationNode an = new AnnotationNode(Type.getDescriptor(Return.class));
        annots.add(an);
        mn.visibleParameterAnnotations =
            mn.visibleParameterAnnotations != null
                ? Arrays.copyOf(mn.visibleParameterAnnotations, args.length)
                : new List[args.length];
        mn.visibleParameterAnnotations[args.length - 1] = annots;
        mn.desc = Type.getMethodDescriptor(retType, args);

        if (mn instanceof BTraceMethodNode) {
          BTraceMethodNode bmn = (BTraceMethodNode) mn;
          OnMethod om = bmn.getOnMethod();

          if (om != null
              && om.getTargetName().equals(mn.name)
              && om.getTargetDescriptor().equals(oldDesc)) {
            om.setReturnParameter(getReturnMethodParameter(mn));
            om.setTargetDescriptor(mn.desc);
          }
        }
      }
    }
  }

  private void scanMethodInstructions(ClassNode cn, MethodNode mn, LocalVarGenerator lvg) {
    // ignore <init> and <clinit>
    if (mn.name.startsWith("<")) return;

    serviceLocals.clear(); // initialize the service locals for this particular method

    boolean checkFields =
        !(tlsFldNames.isEmpty() && exportFldNames.isEmpty() && injectedFlds.isEmpty());

    int retopcode = Type.getReturnType(mn.desc).getOpcode(Opcodes.IRETURN);
    InsnList l = mn.instructions;
    for (AbstractInsnNode n = l.getFirst(); n != null; n = n != null ? n.getNext() : null) {
      int type = n.getType();
      if (checkFields && type == AbstractInsnNode.FIELD_INSN) {
        FieldInsnNode fin = (FieldInsnNode) n;
        if (fin.owner.equals(cn.name)) {
          if (tlsFldNames.contains(fin.name) && !fin.desc.equals(Constants.THREAD_LOCAL_DESC)) {
            n = updateTLSUsage(fin, l);
          } else if (exportFldNames.contains(fin.name)) {
            n = updateExportUsage(cn, fin, l);
          } else if (injectedFlds.containsKey(fin.name)) {
            n = updateInjectedUsage(cn, fin, l, lvg);
          }
        }
      } else if (type == AbstractInsnNode.METHOD_INSN) {
        MethodInsnNode min = (MethodInsnNode) n;
        n = unfoldServiceInstantiation(cn, min, l);
      } else if (n.getOpcode() == retopcode && getClassifiers(mn).contains(MethodClassifier.RT_AWARE)) {
        addBTraceRuntimeExit(cn, (InsnNode) n, l);
      }
    }
  }

  private void recalculateVars(final MethodNode mn) {
    for (AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
      if (n.getType() == AbstractInsnNode.VAR_INSN) {
        VarInsnNode vin = (VarInsnNode) n;
        vin.var = LocalVarGenerator.translateIdx(vin.var);
      }
    }
    StackTrackingMethodVisitor v =
        new StackTrackingMethodVisitor(
            new MethodVisitor(Opcodes.ASM7) {
              @Override
              public void visitMaxs(int maxStack, int maxLocals) {
                super.visitMaxs(maxStack, maxLocals);
                mn.maxStack = maxStack;
                mn.maxLocals = maxLocals;
              }
            },
            mn.name,
            mn.desc,
            (mn.access & Opcodes.ACC_STATIC) > 0);
    mn.accept(v);
  }

  private void processClinit(ClassNode cn) {
    for (MethodNode mn : getMethods(cn)) {
      if (mn.name.equals("<clinit>")) {
        clinit = mn;
        break;
      }
    }
    if (clinit == null) {
      clinit =
          new MethodNode(
              Opcodes.ASM7,
              (Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC),
              "<clinit>",
              "()V",
              null,
              new String[0]);
      clinit.instructions.add(new InsnNode(Opcodes.RETURN));
      cn.methods.add(0, clinit);
    }
    initRuntime(cn, clinit);
  }

  private void initRuntime(ClassNode cn, MethodNode clinit) {
    addRuntimeNode(cn);
    InsnList l = new InsnList();

    l.add(new LdcInsnNode(Type.getObjectType(cn.name)));
    l.add(loadTimerHandlers(cn));
    l.add(loadEventHandlers(cn));
    l.add(loadErrorHandlers(cn));
    l.add(loadExitHandlers(cn));
    l.add(loadLowMemoryHandlers(cn));
    l.add(
        new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Constants.BTRACERTACCESSL_INTERNAL,
            "forClass",
            BTRACERT_FOR_CLASS_DESC,
            false));
    l.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, rtField.name, rtField.desc));

    l.add(getRuntimeImpl(cn));
    addRuntimeCheck(cn, clinit, l, true);

    clinitEntryPoint = l.getLast();

    clinit.instructions.insert(l);

    startRuntime(cn, clinit);
  }

  private InsnList loadTimerHandlers(ClassNode cn) {
    InsnList il = new InsnList();
    int cnt = 0;
    for (MethodNode mn : (List<MethodNode>) cn.methods) {
      if (mn.visibleAnnotations != null) {
        AnnotationNode an = (AnnotationNode) mn.visibleAnnotations.get(0);
        if (an.desc.equals(Constants.ONTIMER_DESC)) {
          Iterator<?> anValueIterator = an.values != null ? an.values.iterator() : null;
          if (anValueIterator != null) {
            long period = -1;
            String property = null;

            while (anValueIterator.hasNext()) {
              String key = (String) anValueIterator.next();
              Object value = anValueIterator.next();

              if (value != null) {
                switch (key) {
                  case "value":
                    {
                      period = (Long) value;
                      break;
                    }
                  case "from":
                    {
                      property = (String) value;
                      break;
                    }
                }
              }
            }
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new LdcInsnNode(cnt++));
            il.add(new TypeInsnNode(Opcodes.NEW, TIMERHANDLER_INTERNAL));
            il.add(new InsnNode(Opcodes.DUP));
            il.add(new LdcInsnNode(mn.name));
            il.add(new LdcInsnNode(period));
            il.add(
                property != null ? new LdcInsnNode(property) : new InsnNode(Opcodes.ACONST_NULL));
            il.add(
                new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    TIMERHANDLER_INTERNAL,
                    "<init>",
                    "(Ljava/lang/String;JLjava/lang/String;)V",
                    false));
            il.add(new InsnNode(Opcodes.AASTORE));
          }
        }
      }
    }
    if (cnt > 0) {
      InsnList newArray = new InsnList();
      newArray.add(new LdcInsnNode(cnt));
      newArray.add(new TypeInsnNode(Opcodes.ANEWARRAY, TIMERHANDLER_INTERNAL));
      il.insert(newArray);
    } else {
      il.insert(new InsnNode(Opcodes.ACONST_NULL));
    }

    return il;
  }

  private InsnList loadEventHandlers(ClassNode cn) {
    InsnList il = new InsnList();
    int cnt = 0;
    for (MethodNode mn : (List<MethodNode>) cn.methods) {
      if (mn.visibleAnnotations != null) {
        AnnotationNode an = (AnnotationNode) mn.visibleAnnotations.get(0);
        if (an.desc.equals(Constants.ONEVENT_DESC)) {
          il.add(new InsnNode(Opcodes.DUP));
          il.add(new LdcInsnNode(cnt++));
          il.add(new TypeInsnNode(Opcodes.NEW, EVENTHANDLER_INTERNAL));
          il.add(new InsnNode(Opcodes.DUP));
          il.add(new LdcInsnNode(mn.name));
          il.add(
              an.values != null
                  ? new LdcInsnNode(an.values.get(1))
                  : new InsnNode(Opcodes.ACONST_NULL));
          il.add(
              new MethodInsnNode(
                  Opcodes.INVOKESPECIAL,
                  EVENTHANDLER_INTERNAL,
                  "<init>",
                  "(Ljava/lang/String;Ljava/lang/String;)V",
                  false));
          il.add(new InsnNode(Opcodes.AASTORE));
        }
      }
    }
    if (cnt > 0) {
      InsnList newArray = new InsnList();
      newArray.add(new LdcInsnNode(cnt));
      newArray.add(new TypeInsnNode(Opcodes.ANEWARRAY, EVENTHANDLER_INTERNAL));
      il.insert(newArray);
    } else {
      il.insert(new InsnNode(Opcodes.ACONST_NULL));
    }

    return il;
  }

  private InsnList loadErrorHandlers(ClassNode cn) {
    InsnList il = new InsnList();
    int cnt = 0;
    for (MethodNode mn : (List<MethodNode>) cn.methods) {
      if (mn.visibleAnnotations != null) {
        AnnotationNode an = (AnnotationNode) mn.visibleAnnotations.get(0);
        if (an.desc.equals(Constants.ONERROR_DESC)) {
          il.add(new InsnNode(Opcodes.DUP));
          il.add(new LdcInsnNode(cnt++));
          il.add(new TypeInsnNode(Opcodes.NEW, ERRORHANDLER_INTERNAL));
          il.add(new InsnNode(Opcodes.DUP));
          il.add(new LdcInsnNode(mn.name));
          il.add(
              new MethodInsnNode(
                  Opcodes.INVOKESPECIAL,
                  ERRORHANDLER_INTERNAL,
                  "<init>",
                  "(Ljava/lang/String;)V",
                  false));
          il.add(new InsnNode(Opcodes.AASTORE));
        }
      }
    }
    if (cnt > 0) {
      InsnList newArray = new InsnList();
      newArray.add(new LdcInsnNode(cnt));
      newArray.add(new TypeInsnNode(Opcodes.ANEWARRAY, ERRORHANDLER_INTERNAL));
      il.insert(newArray);
    } else {
      il.insert(new InsnNode(Opcodes.ACONST_NULL));
    }

    return il;
  }

  private InsnList loadExitHandlers(ClassNode cn) {
    InsnList il = new InsnList();
    int cnt = 0;
    for (MethodNode mn : (List<MethodNode>) cn.methods) {
      if (mn.visibleAnnotations != null) {
        AnnotationNode an = (AnnotationNode) mn.visibleAnnotations.get(0);
        if (an.desc.equals(Constants.ONEXIT_DESC)) {
          il.add(new InsnNode(Opcodes.DUP));
          il.add(new LdcInsnNode(cnt++));
          il.add(new TypeInsnNode(Opcodes.NEW, EXITHANDLER_INTERNAL));
          il.add(new InsnNode(Opcodes.DUP));
          il.add(new LdcInsnNode(mn.name));
          il.add(
              new MethodInsnNode(
                  Opcodes.INVOKESPECIAL,
                  EXITHANDLER_INTERNAL,
                  "<init>",
                  "(Ljava/lang/String;)V",
                  false));
          il.add(new InsnNode(Opcodes.AASTORE));
        }
      }
    }
    if (cnt > 0) {
      InsnList newArray = new InsnList();
      newArray.add(new LdcInsnNode(cnt));
      newArray.add(new TypeInsnNode(Opcodes.ANEWARRAY, EXITHANDLER_INTERNAL));
      il.insert(newArray);
    } else {
      il.insert(new InsnNode(Opcodes.ACONST_NULL));
    }

    return il;
  }

  private InsnList loadLowMemoryHandlers(ClassNode cn) {
    InsnList il = new InsnList();
    int cnt = 0;
    for (MethodNode mn : (List<MethodNode>) cn.methods) {
      if (mn.visibleAnnotations != null) {
        AnnotationNode an = (AnnotationNode) mn.visibleAnnotations.get(0);
        if (an.desc.equals(Constants.ONLOWMEMORY_DESC)) {
          String pool = "";
          long threshold = Long.MAX_VALUE;
          String thresholdProp = null;

          for (int i = 0; i < an.values.size(); i += 2) {
            String key = (String) an.values.get(i);
            Object val = an.values.get(i + 1);
            switch (key) {
              case "pool":
                {
                  pool = (String) val;
                  break;
                }
              case "threshold":
                {
                  threshold = (long) val;
                  break;
                }
              case "thresholdFrom":
                {
                  thresholdProp = (String) val;
                  break;
                }
            }
          }
          il.add(new InsnNode(Opcodes.DUP));
          il.add(new LdcInsnNode(cnt++));
          il.add(new TypeInsnNode(Opcodes.NEW, LOWMEMORYHANDLER_INTERNAL));
          il.add(new InsnNode(Opcodes.DUP));
          il.add(new LdcInsnNode(mn.name));
          il.add(new LdcInsnNode(pool));
          il.add(new LdcInsnNode(threshold));
          il.add(new LdcInsnNode(thresholdProp));
          il.add(
              new MethodInsnNode(
                  Opcodes.INVOKESPECIAL,
                  LOWMEMORYHANDLER_INTERNAL,
                  "<init>",
                  "(Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;)V",
                  false));
          il.add(new InsnNode(Opcodes.AASTORE));
        }
      }
    }
    if (cnt > 0) {
      InsnList newArray = new InsnList();
      newArray.add(new LdcInsnNode(cnt));
      newArray.add(new TypeInsnNode(Opcodes.ANEWARRAY, EXITHANDLER_INTERNAL));
      il.insert(newArray);
    } else {
      il.insert(new InsnNode(Opcodes.ACONST_NULL));
    }

    return il;
  }

  private void startRuntime(ClassNode cNode, MethodNode clinit1) {
    for (AbstractInsnNode n = clinit1.instructions.getFirst(); n != null; n = n.getNext()) {
      if (n.getOpcode() == Opcodes.RETURN) {
        AbstractInsnNode prev = n.getPrevious();
        if (prev != null && prev.getType() == AbstractInsnNode.METHOD_INSN) {
          MethodInsnNode min = (MethodInsnNode) prev;
          if (min.name.equals("leave")) {
            // don't start the runtime if we are bailing out (BTraceRuntime.leave())
            continue;
          }
        }
        InsnList il = new InsnList();
        il.add(getRuntimeImpl(cNode));
        il.add(
            new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, Constants.BTRACERTBASE_INTERNAL, "start", "()V", false));
        clinit1.instructions.insertBefore(n, il);
      }
    }
  }

  private FieldInsnNode getRuntimeImpl(ClassNode cn) {
    return new FieldInsnNode(Opcodes.GETSTATIC, cn.name, rtField.name, rtField.desc);
  }

  private InsnList getRuntimeExit(ClassNode cn) {
    InsnList il = new InsnList();
    il.add(getRuntimeImpl(cn));
    il.add(
        new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL, Constants.BTRACERTBASE_INTERNAL, "leave", "()V", false));
    return il;
  }

  private void addRuntimeNode(ClassNode cn) {
    rtField =
        new FieldNode(
            Opcodes.ASM7,
            (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC),
            "runtime",
            Type.getDescriptor(BTraceRuntimeImplBase.class),
            null,
            null);
    cn.fields.add(0, rtField);
  }

  private void addBTraceErrorHandler(ClassNode cn, MethodNode mn) {
    if (!mn.name.equals("<clinit>") && isUnannotated(mn)) return;

    EnumSet<MethodClassifier> clsf = getClassifiers(mn);
    if (!clsf.isEmpty()) {
      LabelNode from = new LabelNode();
      LabelNode to = new LabelNode();
      InsnList l = mn.instructions;
      l.insert(from);
      l.add(to);
      // add proper stackframe map node
      l.add(throwableHandlerFrame(mn));

      l.add(getRuntimeImpl(cn));
      l.add(new InsnNode(Opcodes.DUP_X1));
      l.add(new InsnNode(Opcodes.SWAP));
      l.add(
              new MethodInsnNode(
                      Opcodes.INVOKEVIRTUAL,
                      Constants.BTRACERTBASE_INTERNAL,
                      "handleException",
                      BTRACERT_HANDLE_EXCEPTION_DESC,
                      false));
      l.add(getReturnSequence(cn, mn, true));

      mn.tryCatchBlocks.add(new TryCatchBlockNode(from, to, to, Constants.THROWABLE_INTERNAL));
    }
  }

  private FrameNode throwableHandlerFrame(MethodNode mn) {
    List<Object> locals = new ArrayList<>();
    for (Type argType : Type.getArgumentTypes(mn.desc)) {
      if (TypeUtils.isPrimitive(argType)) {
        switch (argType.getSort()) {
          case Type.INT:
          case Type.BOOLEAN:
          case Type.BYTE:
          case Type.CHAR: {
            locals.add(Opcodes.INTEGER);
            break;
          }
          case Type.FLOAT: {
            locals.add(Opcodes.FLOAT);
            break;
          }
          case Type.DOUBLE: {
            locals.add(Opcodes.DOUBLE);
            break;
          }
          case Type.LONG: {
            locals.add(Opcodes.LONG);
            break;
          }
        }
      } else {
        locals.add(argType.getInternalName());
      }
    }
    return new FrameNode(Opcodes.F_FULL, locals.size(), locals.toArray(new Object[0]), 1, new Object[] {Constants.THROWABLE_INTERNAL});
  }

  private void addBTraceRuntimeEnter(ClassNode cn, MethodNode mn) {
    // no runtime check for <clinit>
    if (mn.name.equals("<clinit>")) return;

    EnumSet<MethodClassifier> clsf = getClassifiers(mn);
    if (clsf.contains(MethodClassifier.RT_AWARE)) {
      InsnList entryCheck = new InsnList();
      entryCheck.add(getRuntimeImpl(cn));
      if (clsf.contains(MethodClassifier.GUARDED)) {
        addRuntimeCheck(cn, mn, entryCheck, false);
      }
      mn.instructions.insert(entryCheck);
    }
  }

  private void addRuntimeCheck(ClassNode cn, MethodNode mn, InsnList entryCheck, boolean b) {
    LabelNode start = new LabelNode();
    entryCheck.add(
        new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            Constants.BTRACERTACCESSL_INTERNAL,
            "enter",
            BTRACERT_ENTER_DESC,
            false));
    entryCheck.add(new JumpInsnNode(Opcodes.IFNE, start));
    entryCheck.add(getReturnSequence(cn, mn, b));
    entryCheck.add(start);
    entryCheck.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
  }

  private void addBTraceRuntimeExit(ClassNode cn, InsnNode n, InsnList l) {
    l.insertBefore(n, getRuntimeExit(cn));
  }

  private void addJfrHandlerField(ClassNode cn, MethodNode mn) {
    if (mn.visibleAnnotations != null) {
      for (AnnotationNode annotation : mn.visibleAnnotations) {
        if (annotation.desc.equals(Constants.JFRPERIODIC_DESC)) {
          String fldName = JFR_HANDLER_FIELD_PREFIX + mn.name;
          cn.fields.add(new FieldNode(Opcodes.ASM7, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, fldName, Constants.JFREVENTFACTORY_DESC, null, null));
          eventFlds.put(fldName, annotation);
        }
      }
    }
  }

  private List<MethodNode> getMethods(ClassNode cn) {
    return cn.methods;
  }

  private List<FieldNode> getFields(ClassNode cn) {
    return cn.fields;
  }

  private List<AnnotationNode> getAnnotations(MethodNode mn) {
    return mn.visibleAnnotations != null
        ? mn.visibleAnnotations
        : Collections.<AnnotationNode>emptyList();
  }

  private EnumSet<MethodClassifier> getClassifiers(MethodNode mn) {
    // thread safe; check-modification will be done in single thread only for each instance of Preprocessor
    EnumSet<MethodClassifier> classifiers = classifierMap.get(mn);
    if (classifiers != null) {
      return classifiers;
    }
    // <clinit> will always be guarded by BTrace error handler
    if (mn.name.equals("<clinit>")) {
      return EnumSet.of(MethodClassifier.RT_AWARE);
    }

    // JFR event handlers will alwyas be guarded by BTrace error handler
    if (jfrHandlerNames.contains(mn.name)) {
      return EnumSet.of(MethodClassifier.RT_AWARE, MethodClassifier.GUARDED);
    }

    List<AnnotationNode> annots = getAnnotations(mn);
    classifiers = EnumSet.noneOf(MethodClassifier.class);
    if (!annots.isEmpty()) {
      for (AnnotationNode an : annots) {
        if (RT_AWARE_ANNOTS.contains(an.desc)) {
          classifiers.add(MethodClassifier.RT_AWARE);
        }
        if (GUARDED_ANNOTS.contains(an.desc)) {
          classifiers.add(MethodClassifier.GUARDED);
        }
      }
    }
    return classifiers;
  }

  private FieldInsnNode findNodeInitialization(FieldNode fn) {
    for (AbstractInsnNode n = clinit.instructions.getFirst(); n != null; n = n.getNext()) {
      if (n.getType() == AbstractInsnNode.FIELD_INSN) {
        FieldInsnNode fldInsnNode = (FieldInsnNode) n;
        if (fldInsnNode.getOpcode() == Opcodes.PUTSTATIC && fldInsnNode.name.equals(fn.name)) {
          return fldInsnNode;
        }
      }
    }
    return null;
  }

  private MethodInsnNode findBTraceRuntimeStart() {
    for (AbstractInsnNode n = clinit.instructions.getFirst(); n != null; n = n.getNext()) {
      if (n.getType() == AbstractInsnNode.METHOD_INSN) {
        MethodInsnNode min = (MethodInsnNode) n;
        if (min.getOpcode() == Opcodes.INVOKEVIRTUAL
            && min.owner.equals(Constants.BTRACERTBASE_INTERNAL)
            && min.name.equals("start")) {
          return min;
        }
      }
    }
    return null;
  }

  private AbstractInsnNode updateTLSUsage(FieldInsnNode fin, InsnList l) {
    String unboxedDesc = fin.desc;
    int opcode = fin.getOpcode();
    // retrieve the TLS field
    fin.setOpcode(Opcodes.GETSTATIC);
    // change the desc from the contained type to TLS type
    fin.desc = Constants.THREAD_LOCAL_DESC;

    String boxedDesc = boxDesc(unboxedDesc);

    if (opcode == Opcodes.GETSTATIC) {
      InsnList toInsert = new InsnList();
      MethodInsnNode getNode =
          new MethodInsnNode(
              Opcodes.INVOKEVIRTUAL, Constants.THREAD_LOCAL_INTERNAL, "get", TLS_GET_DESC, false);
      toInsert.add(getNode);
      String boxedInternal = boxedDesc.substring(1, boxedDesc.length() - 1);
      toInsert.add(new TypeInsnNode(Opcodes.CHECKCAST, boxedInternal));
      if (!boxedDesc.equals(unboxedDesc)) {
        // must unbox
        MethodInsnNode unboxNode = unboxNode(boxedDesc, boxedInternal, unboxedDesc);
        if (unboxNode != null) {
          toInsert.add(unboxNode);
        }
      }
      l.insert(fin, toInsert);
    } else if (opcode == Opcodes.PUTSTATIC) {
      MethodInsnNode boxNode = null;
      if (!boxedDesc.equals(unboxedDesc)) {
        // must box
        boxNode = boxNode(unboxedDesc, boxedDesc);
        l.insert(fin.getPrevious(), boxNode);
      }
      MethodInsnNode setNode =
          new MethodInsnNode(
              Opcodes.INVOKEVIRTUAL, Constants.THREAD_LOCAL_INTERNAL, "set", TLS_SET_DESC, false);
      l.insert(fin, setNode);
      /* The stack is
         -> ThreadLocal instance
         -> value
         ...

         and we need
         -> value
         -> ThreadLocal instance
         ...

        Therefore we need to swap the topmost 2 elements
      */
      l.insertBefore(setNode, new InsnNode(Opcodes.SWAP));
    }
    return fin;
  }

  private AbstractInsnNode updateExportUsage(ClassNode cn, FieldInsnNode fin, InsnList l) {
    String prefix = null;
    boolean isPut = false;
    // all the perf related methods start either with 'getPerf' or 'putPerf'
    // the second part of the method name is extracted from the field type
    if (fin.getOpcode() == Opcodes.GETSTATIC) {
      prefix = "getPerf";
    } else {
      isPut = true;
      prefix = "putPerf";
    }
    String methodName = null;
    Type tType = null;

    Type t = Type.getType(fin.desc);
    switch (t.getSort()) {
      case Type.INT:
      case Type.SHORT:
      case Type.BYTE:
      case Type.CHAR:
      case Type.BOOLEAN:
        {
          methodName = prefix + "Int";
          tType = Type.INT_TYPE;
          break;
        }
      case Type.LONG:
        {
          methodName = prefix + "Long";
          tType = Type.LONG_TYPE;
          break;
        }
      case Type.FLOAT:
        {
          methodName = prefix + "Float";
          tType = Type.FLOAT_TYPE;
          break;
        }
      case Type.DOUBLE:
        {
          methodName = prefix + "Double";
          tType = Type.DOUBLE_TYPE;
          break;
        }
      case Type.OBJECT:
        {
          if (t.equals(Constants.STRING_TYPE)) {
            methodName = prefix + "String";
            tType = Constants.STRING_TYPE;
          }
          break;
        }
    }
    if (methodName == null) {
      // if the perf counter is not accessible
      // just put null on the stack for GETSTATIC
      // and remove the topmost item from the stack for PUTSTATIC
      l.insert(fin, isPut ? new InsnNode(Opcodes.POP) : new InsnNode(Opcodes.ACONST_NULL));
    } else {
      InsnList toInsert = new InsnList();
      toInsert.add(getRuntimeImpl(cn));
      if (isPut) {
        // if the 'value' is on stack swap it with the rt instance to have the stack in required
        // order
        if (tType.getSize() == 1) {
          toInsert.add(new InsnNode(Opcodes.SWAP));
        } else {
          toInsert.add(new InsnNode(Opcodes.DUP_X2));
          toInsert.add(new InsnNode(Opcodes.POP));
        }
      }
      toInsert.add(new LdcInsnNode(perfCounterName(cn, fin.name)));
      toInsert.add(
          new MethodInsnNode(
              Opcodes.INVOKEVIRTUAL,
              Constants.BTRACERTBASE_INTERNAL,
              methodName,
              isPut
                  ? Type.getMethodDescriptor(Type.VOID_TYPE, tType, Constants.STRING_TYPE)
                  : Type.getMethodDescriptor(tType, Constants.STRING_TYPE),
              false));
      l.insert(fin, toInsert);
    }
    AbstractInsnNode ret = fin.getNext();
    l.remove(fin);
    return ret;
  }

  private AbstractInsnNode updateInjectedUsage(
      ClassNode cn, FieldInsnNode fin, InsnList l, LocalVarGenerator lvg) {
    if (serviceLocals.containsKey(fin.name)) {
      VarInsnNode load =
          new VarInsnNode(
              Type.getType(fin.desc).getOpcode(Opcodes.ILOAD), serviceLocals.get(fin.name));
      l.insert(fin, load);
      l.remove(fin);
      return load;
    }

    InsnList toInsert = new InsnList();
    AnnotationNode an = injectedFlds.get(fin.name);
    Type implType = Type.getType(fin.desc);
    String svcType = "SIMPLE";
    String fctryMethod = null;
    if (an.values != null) {
      Iterator<Object> iter = an.values.iterator();
      while (iter.hasNext()) {
        String name = (String) iter.next();
        Object val = iter.next();
        switch (name) {
          case "value":
            svcType = ((String[]) val)[1];
            break;
          case "factoryMethod":
            fctryMethod = (String) val;
            break;
        }
      }
    }
    int varIdx = lvg.newVar(implType);
    if (svcType.equals("SIMPLE")) {
      if (fctryMethod == null || fctryMethod.isEmpty()) {
        toInsert.add(new TypeInsnNode(Opcodes.NEW, implType.getInternalName()));
        toInsert.add(new InsnNode(Opcodes.DUP));
        toInsert.add(new InsnNode(Opcodes.DUP));
        toInsert.add(
            new MethodInsnNode(
                Opcodes.INVOKESPECIAL, implType.getInternalName(), "<init>", "()V", false));
      } else {
        toInsert.add(
            new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                implType.getInternalName(),
                fctryMethod,
                Type.getMethodDescriptor(implType),
                false));
        toInsert.add(new InsnNode(Opcodes.DUP));
      }
    } else { // RuntimeService here
      if (fctryMethod == null || fctryMethod.isEmpty()) {
        toInsert.add(new TypeInsnNode(Opcodes.NEW, implType.getInternalName()));
        toInsert.add(new InsnNode(Opcodes.DUP));
        toInsert.add(new InsnNode(Opcodes.DUP));
        toInsert.add(getRuntimeImpl(cn));
        toInsert.add(
            new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                implType.getInternalName(),
                "<init>",
                RT_SERVICE_CTR_DESC,
                false));
      } else {
        toInsert.add(getRuntimeImpl(cn));
        toInsert.add(
            new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                implType.getInternalName(),
                fctryMethod,
                Type.getMethodDescriptor(implType, RT_CTX_TYPE),
                false));
        toInsert.add(new InsnNode(Opcodes.DUP));
      }
    }
    toInsert.add(new VarInsnNode(Opcodes.ASTORE, varIdx));
    l.insert(fin, toInsert);
    AbstractInsnNode next = fin.getNext();
    l.remove(fin);
    serviceLocals.put(fin.name, varIdx);
    return next;
  }

  private AbstractInsnNode unfoldServiceInstantiation(
      ClassNode cn, MethodInsnNode min, InsnList l) {
    if (min.owner.equals(SERVICE_INTERNAL)) {
      AbstractInsnNode next = min.getNext();
      switch (min.name) {
        case "simple":
          {
            Type[] args = Type.getArgumentTypes(min.desc);
            if (args.length == 1) {
              AbstractInsnNode ldcType = min.getPrevious();
              if (ldcType.getType() == AbstractInsnNode.LDC_INSN) {
                // remove the original sequence
                l.remove(min);
                l.remove(ldcType);
                if (next.getOpcode() == Opcodes.CHECKCAST) {
                  next = next.getNext();
                  l.remove(next.getPrevious());
                }
                // ---

                String sType = ((Type) ((LdcInsnNode) ldcType).cst).getInternalName();
                InsnList toInsert = new InsnList();
                toInsert.add(new TypeInsnNode(Opcodes.NEW, sType));
                toInsert.add(new InsnNode(Opcodes.DUP));
                toInsert.add(
                    new MethodInsnNode(Opcodes.INVOKESPECIAL, sType, "<init>", "()V", false));
                l.insertBefore(next, toInsert);
              }
            } else if (args.length == 2) {
              AbstractInsnNode ldcType = min.getPrevious();
              AbstractInsnNode ldcFMethod = ldcType.getPrevious();
              if (ldcType.getType() == AbstractInsnNode.LDC_INSN
                  && ldcFMethod.getType() == AbstractInsnNode.LDC_INSN) {
                // remove the original sequence
                l.remove(min);
                l.remove(ldcType);
                l.remove(ldcFMethod);
                if (next.getOpcode() == Opcodes.CHECKCAST) {
                  next = next.getNext();
                  l.remove(next.getPrevious());
                }
                // ---

                String sType = ((Type) ((LdcInsnNode) ldcType).cst).getInternalName();
                String fMethod = (String) ((LdcInsnNode) ldcFMethod).cst;

                InsnList toInsert = new InsnList();
                toInsert.add(new TypeInsnNode(Opcodes.NEW, sType));
                toInsert.add(new InsnNode(Opcodes.DUP));
                toInsert.add(new LdcInsnNode(fMethod));
                toInsert.add(
                    new MethodInsnNode(
                        Opcodes.INVOKESPECIAL, sType, "<init>", SERVICE_CTR_DESC, false));
                l.insertBefore(next, toInsert);
              }
            }
            break;
          }
        case "runtime":
          {
            Type[] args = Type.getArgumentTypes(min.desc);
            if (args.length == 1) {
              AbstractInsnNode ldcType = min.getPrevious();
              if (ldcType.getType() == AbstractInsnNode.LDC_INSN) {
                // remove the original sequence
                l.remove(min);
                l.remove(ldcType);
                if (next.getOpcode() == Opcodes.CHECKCAST) {
                  next = next.getNext();
                  l.remove(next.getPrevious());
                }
                // ---

                String sType = ((Type) ((LdcInsnNode) ldcType).cst).getInternalName();
                InsnList toInsert = new InsnList();
                toInsert.add(new TypeInsnNode(Opcodes.NEW, sType));
                toInsert.add(new InsnNode(Opcodes.DUP));
                toInsert.add(getRuntimeImpl(cn));
                toInsert.add(
                    new MethodInsnNode(
                        Opcodes.INVOKESPECIAL, sType, "<init>", RT_SERVICE_CTR_DESC, false));
                l.insertBefore(next, toInsert);
              }
            }
            break;
          }
      }
      return next;
    }
    return min;
  }

  private InsnList getReturnSequence(ClassNode cn, MethodNode mn, boolean addRuntimeExit) {
    InsnList l = new InsnList();
    Type retType = Type.getReturnType(mn.desc);
    if (!retType.equals(Type.VOID_TYPE)) {
      int retIndex = -1;
      if (mn.visibleParameterAnnotations != null) {
        int offset = 0;
        Type[] params = Type.getArgumentTypes(mn.desc);
        for (int i = 0; i < mn.visibleParameterAnnotations.length; i++) {
          if (mn.visibleParameterAnnotations[i] != null) {
            for (AnnotationNode an : (List<AnnotationNode>) mn.visibleParameterAnnotations[i]) {
              if (an.desc.equals(Type.getDescriptor(Return.class))) {
                retIndex = offset;
              }
            }
          }
          offset += params[i].getSize();
        }
      }
      if (retIndex > -1) {
        l.add(new VarInsnNode(retType.getOpcode(Opcodes.ILOAD), retIndex));
      } else {
        switch (retType.getSort()) {
          case Type.INT:
          case Type.SHORT:
          case Type.BYTE:
          case Type.CHAR:
          case Type.BOOLEAN:
            {
              l.add(new InsnNode(Opcodes.ICONST_0));
              break;
            }
          case Type.LONG:
            {
              l.add(new InsnNode(Opcodes.LCONST_0));
              break;
            }
          case Type.FLOAT:
            {
              l.add(new InsnNode(Opcodes.FCONST_0));
              break;
            }
          case Type.DOUBLE:
            {
              l.add(new InsnNode(Opcodes.DCONST_0));
              break;
            }
          case Type.ARRAY:
          case Type.OBJECT:
            {
              l.add(new InsnNode(Opcodes.ACONST_NULL));
              break;
            }
        }
      }
    }
    if (addRuntimeExit) {
      l.add(getRuntimeExit(cn));
    }
    l.add(new InsnNode(retType.getOpcode(Opcodes.IRETURN)));
    return l;
  }

  private String perfCounterName(ClassNode cn, String fieldName) {
    return BTRACE_COUNTER_PREFIX + Type.getObjectType(cn.name).getInternalName() + "." + fieldName;
  }

  private String boxDesc(String desc) {
    String boxed_desc = BOX_TYPE_MAP.get(desc);
    return boxed_desc != null ? boxed_desc : desc;
  }

  private MethodInsnNode boxNode(String unboxedDesc) {
    String boxedDesc = boxDesc(unboxedDesc);
    if (boxedDesc != null) {
      return boxNode(unboxedDesc, boxedDesc);
    }
    return null;
  }

  private MethodInsnNode boxNode(String unboxedDesc, String boxedDesc) {
    return new MethodInsnNode(
        Opcodes.INVOKESTATIC,
        boxedDesc.substring(1, boxedDesc.length() - 1),
        "valueOf",
        "(" + unboxedDesc + ")" + boxedDesc,
        false);
  }

  /**
   * Add the instruction sequence to print the message using {@linkplain DebugSupport}
   *
   * @param msg message
   * @return the instruction list
   */
  private InsnList debugPrint(String msg) {
    InsnList list = new InsnList();
    list.add(msg != null ? new LdcInsnNode(msg) : new InsnNode(Opcodes.ACONST_NULL));
    list.add(
        new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "org/openjdk/btrace/core/DebugSupport",
            "info",
            "(Ljava/lang/String;)V"));
    return list;
  }

  private MethodInsnNode unboxNode(String boxedDesc, String boxedInternal, String unboxedDesc) {
    String mName = null;
    switch (boxedDesc) {
      case Constants.INTEGER_BOXED_DESC:
        {
          mName = "intValue";
          break;
        }
      case Constants.SHORT_BOXED_DESC:
        {
          mName = "shortValue";
          break;
        }
      case Constants.LONG_BOXED_DESC:
        {
          mName = "longValue";
          break;
        }
      case Constants.FLOAT_BOXED_DESC:
        {
          mName = "floatValue";
          break;
        }
      case Constants.DOUBLE_BOXED_DESC:
        {
          mName = "doubleValue";
          break;
        }
      case Constants.BOOLEAN_BOXED_DESC:
        {
          mName = "booleanValue";
          break;
        }
      case Constants.CHARACTER_BOXED_DESC:
        {
          mName = "charValue";
          break;
        }
    }

    if (mName != null) {
      return new MethodInsnNode(
          Opcodes.INVOKEVIRTUAL, boxedInternal, mName, "()" + unboxedDesc, false);
    }
    return null;
  }

  private int getReturnMethodParameter(MethodNode mn) {
    if (mn.visibleParameterAnnotations != null) {
      for (int i = 0; i < mn.visibleParameterAnnotations.length; i++) {
        List paList = mn.visibleParameterAnnotations[i];
        if (paList != null) {
          for (Object anObj : paList) {
            AnnotationNode an = (AnnotationNode) anObj;
            if (an.desc.equals(Constants.RETURN_DESC)) {
              return i;
            }
          }
        }
      }
    }
    return Integer.MIN_VALUE;
  }

  private enum MethodClassifier {
    /**
     * An annotated method that will need access to the current {@linkplain BTraceRuntime} instance.
     */
    RT_AWARE,
    /**
     * An annotated method that will use the result of {@linkplain BTraceRuntime#enter()} to skip
     * the execution if already inside a handler. This implies the method is also {@linkplain
     * MethodClassifier#RT_AWARE}.
     */
    GUARDED
  }

  public interface MethodFilter {
    MethodFilter ALL =
        new MethodFilter() {
          @Override
          public boolean test(String name, String desc) {
            return true;
          }
        };

    boolean test(String name, String desc);
  }

  private static class LocalVarGenerator {
    private int offset = 0;

    LocalVarGenerator(MethodNode mn) {
      Type[] args = Type.getArgumentTypes(mn.desc);
      for (Type t : args) {
        offset += t.getSize();
      }
    }

    static int translateIdx(int idx) {
      if (idx < 0) {
        return -idx - 1;
      }
      return idx;
    }

    int newVar(Type t) {
      int ret = -offset - 1;
      offset += t.getSize();
      return ret;
    }

    int maxVar() {
      return offset;
    }
  }
}
