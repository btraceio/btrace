/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.btrace.runtime;

import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.annotations.Export;
import com.sun.btrace.annotations.Injected;
import com.sun.btrace.annotations.OnError;
import com.sun.btrace.annotations.OnEvent;
import com.sun.btrace.annotations.OnExit;
import com.sun.btrace.annotations.OnTimer;
import com.sun.btrace.annotations.Return;
import com.sun.btrace.annotations.TLS;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.org.objectweb.asm.tree.AbstractInsnNode;
import com.sun.btrace.org.objectweb.asm.tree.AnnotationNode;
import com.sun.btrace.org.objectweb.asm.tree.ClassNode;
import com.sun.btrace.org.objectweb.asm.tree.FieldInsnNode;
import com.sun.btrace.org.objectweb.asm.tree.FieldNode;
import com.sun.btrace.org.objectweb.asm.tree.InsnList;
import com.sun.btrace.org.objectweb.asm.tree.InsnNode;
import com.sun.btrace.org.objectweb.asm.tree.JumpInsnNode;
import com.sun.btrace.org.objectweb.asm.tree.LabelNode;
import com.sun.btrace.org.objectweb.asm.tree.LdcInsnNode;
import com.sun.btrace.org.objectweb.asm.tree.MethodInsnNode;
import com.sun.btrace.org.objectweb.asm.tree.MethodNode;
import com.sun.btrace.org.objectweb.asm.tree.TryCatchBlockNode;
import com.sun.btrace.org.objectweb.asm.tree.TypeInsnNode;
import com.sun.btrace.org.objectweb.asm.tree.VarInsnNode;
import com.sun.btrace.services.api.Service;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class preprocesses a compiled BTrace program.
 * This is done after BTrace safety verification but
 * before instrumenting the probed classes.
 *
 * Transformations done here:
 *
 *    1. add <clinit> method, if one not found
 *    2. replace @Export fields by perf counters
 *       and replace put/get by perf counter update/read
 *    3. replace @TLS fields by ThreadLocal fields
 *       and replace put/get by ThreadLocal set/get
 *    4. In <clinit> method, add ThreadLocal creation
 *       and perf counter creation calls (for @Export and
 *       @TLS fields respectively)
 *    5. Add a field to store BTraceRuntime object and
 *       initialize the same in <clinit> method
 *    6. add prolog and epilogue in each BTrace action method
 *       to insert BTraceRuntime.enter/leave and also to call
 *       BTraceRuntime.handleException on exception catch
 *    7. initialize and reference any service instances
 *    8. add a field to store client's BTraceRuntime instance
 *    9. make all fields publicly accessible
 *
 *
 * @author A. Sundararajan
 * @author J. Bachorik (Tree API rewrite)
 */
public final class Preprocessor {
    private static enum MethodClassifier {
        /**
         * No BTrace specific classifier available
         */
        NONE,
        /**
         * An annotated method that will need access to the current
         * {@linkplain BTraceRuntime} instance.
         */
        RT_AWARE,
        /**
         * An annotated method that will use the result of
         * {@linkplain BTraceRuntime#enter(com.sun.btrace.BTraceRuntime)} to skip
         * the execution if already inside a handler. This implies the method is also
         * {@linkplain MethodClassifier#RT_AWARE}.
         */
        GUARDED
    }

    private static class LocalVarGenerator {
        private int offset = 0;

        public LocalVarGenerator(MethodNode mn) {
            Type[] args = Type.getArgumentTypes(mn.desc);
            for(Type t : args) {
                offset += t.getSize();
            }
        }

        public int newVar(Type t) {
            int ret = -offset - 1;
            offset += t.getSize();
            return ret;
        }

        public static int translateIdx(int idx) {
            if (idx < 0) {
                return -idx - 1;
            }
            return idx;
        }
    }

    private static final Type BTRACE_RUNTIME_TYPE = Type.getType(BTraceRuntime.class);
    private static final Type TLS_TYPE = Type.getType(TLS.class);
    private static final Type EXPORT_TYPE = Type.getType(Export.class);
    private static final Type INJECTED_TYPE = Type.getType(Injected.class);
    private static final Type THREAD_LOCAL_TYPE = Type.getType(ThreadLocal.class);
    private static final Type SERVICE_TYPE = Type.getType(Service.class);

    private static final Map<String, Type> boxTypeMap = new HashMap<>();
    private static final Set<String> guardedAnnots = new HashSet<>();
    private static final Set<String> rtAwareAnnots = new HashSet<>();

    static {
        boxTypeMap.put("I", Type.getType(Integer.class));
        boxTypeMap.put("S", Type.getType(Short.class));
        boxTypeMap.put("J", Type.getType(Long.class));
        boxTypeMap.put("F", Type.getType(Float.class));
        boxTypeMap.put("D", Type.getType(Double.class));
        boxTypeMap.put("B", Type.getType(Byte.class));
        boxTypeMap.put("Z", Type.getType(Boolean.class));
        boxTypeMap.put("C", Type.getType(Character.class));

        rtAwareAnnots.add(Type.getDescriptor(com.sun.btrace.annotations.OnMethod.class));
        rtAwareAnnots.add(Type.getDescriptor(OnTimer.class));
        rtAwareAnnots.add(Type.getDescriptor(OnEvent.class));
        rtAwareAnnots.add(Type.getDescriptor(OnError.class));
        rtAwareAnnots.add(Type.getDescriptor(com.sun.btrace.annotations.OnProbe.class));
        guardedAnnots.addAll(rtAwareAnnots);

        // @OnExit is rtAware but not guarded
        rtAwareAnnots.add(Type.getDescriptor(OnExit.class));
    }

    public static final Type ONMETHOD_TYPE = Type.getType(com.sun.btrace.annotations.OnMethod.class);
    public static final Type ONPROBE_TYPE = Type.getType(com.sun.btrace.annotations.OnProbe.class);
    public static final Type ONTIMER_TYPE = Type.getType(com.sun.btrace.annotations.OnTimer.class);
    public static final Type ONEVENT_TYPE = Type.getType(com.sun.btrace.annotations.OnEvent.class);
    public static final Type ONEXIT_TYPE = Type.getType(com.sun.btrace.annotations.OnExit.class);
    public static final Type ONERROR_TYPE = Type.getType(com.sun.btrace.annotations.OnError.class);

    public static interface MethodFilter {
        public static final MethodFilter ALL = new MethodFilter() {
            @Override
            public boolean test(String name, String desc) {
                return true;
            }
        };

        boolean test(String name, String desc);
    }

    private MethodNode clinit = null;
    private FieldNode rtField = null;

    private final Set<String> tlsFldNames = new HashSet<>();
    private final Set<String> exportFldNames = new HashSet<>();
    private final Map<String, AnnotationNode> injectedFlds = new HashMap<>();
    private final Map<String, Integer> serviceLocals = new HashMap<>();

    public void process(ClassNode cn) {
        addLevelField(cn);
        processClinit(cn);
        processFields(cn);

        for(MethodNode mn : getMethods(cn)) {
            preprocessMethod(cn, mn);
        }
    }

    private void addLevelField(ClassNode cn) {
        if (cn.fields == null) {
            cn.fields = new LinkedList();
        }
        cn.fields.add(
            new FieldNode(
                Opcodes.ASM5,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE,
                "$btrace$$level",
                Type.INT_TYPE.getDescriptor(),
                null,
                0
            )
        );
    }

    private void preprocessMethod(ClassNode cn, MethodNode mn) {
        // !!! The order of execution is important here !!!
        LocalVarGenerator lvg = new LocalVarGenerator(mn);
        makePublic(mn);
        checkAugmentedReturn(mn);
        scanMethodInstructions(cn, mn, lvg);
        addBTraceErrorHandler(mn, lvg);
        addBTraceRuntimeEnter(cn, mn, lvg);

        recalculateVars(mn, lvg);
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
            fn.access = fn.access &
                            ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)
                            | Opcodes.ACC_PUBLIC;
            tryProcessTLS(cn, fn);
            tryProcessExport(cn, fn);
            if (tryProcessInjected(fn) == null) {
                iter.remove();
            }
        }
    }

    private void tryProcessTLS(ClassNode cn, FieldNode fn) {
        AnnotationNode an = null;
        if ((an = getAnnotation(fn, TLS_TYPE)) != null) {
            fn.visibleAnnotations.remove(an);
            String origDesc = fn.desc;
            String boxedDesc = boxDesc(origDesc);
            fn.desc = THREAD_LOCAL_TYPE.getDescriptor();
            fn.signature = fn.desc.substring(0, fn.desc.length() - 1) +
                           "<" + boxedDesc + ">;";
            initTLS(cn, fn, Type.getType(origDesc));
        }
    }

    private void tryProcessExport(ClassNode cn, FieldNode fn) {
        AnnotationNode an = null;
        if ((an = getAnnotation(fn, EXPORT_TYPE)) != null) {
            fn.visibleAnnotations.remove(an);
            String origDesc = fn.desc;

            initExport(cn, fn, Type.getType(origDesc));
        }
    }

    private FieldNode tryProcessInjected(FieldNode fn) {
        AnnotationNode an = null;
        if ((an = getAnnotation(fn, INJECTED_TYPE)) != null) {
            String origDesc = fn.desc;
            if (fn.visibleAnnotations != null) fn.visibleAnnotations.remove(an);
            if (fn.invisibleAnnotations != null) fn.invisibleAnnotations.remove(an);

            injectedFlds.put(fn.name, an);
            return null;
        }
        return fn;
    }

    private void initTLS(ClassNode cn, FieldNode fn, Type t) {
        tlsFldNames.add(fn.name);

        initAnnotatedField(fn, t, tlsInitSequence(cn, t, fn.name, fn.desc));
    }

    private InsnList tlsInitSequence(ClassNode cn, Type t, String name, String desc) {
        InsnList initList = new InsnList();
        initList.add(
            new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                BTRACE_RUNTIME_TYPE.getInternalName(),
                "newThreadLocal",
                Type.getMethodDescriptor(THREAD_LOCAL_TYPE, Type.getType(Object.class)),
                false
            )
        );
        initList.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, name, desc));
        return initList;
    }

    private void initExport(ClassNode cn, FieldNode fn, Type t) {
        exportFldNames.add(fn.name);
        initAnnotatedField(fn, t, exportInitSequence(cn, t, fn.name, fn.desc));
    }

    private InsnList exportInitSequence(ClassNode cn, Type t, String name, String desc) {
        InsnList init = new InsnList();

        init.add(new LdcInsnNode(perfCounterName(cn, name)));
        init.add(new LdcInsnNode(desc));
        init.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                BTRACE_RUNTIME_TYPE.getInternalName(),
                "newPerfCounter",
                Type.getMethodDescriptor(
                    Type.VOID_TYPE,
                    Type.getType(Object.class),
                    Type.getType(String.class),
                    Type.getType(String.class)
                ),
                false
            )
        );

        return init;
    }

    private void initAnnotatedField(FieldNode fn, Type t, InsnList initList) {
        Object initVal = fn.value;
        fn.value = null;
        fn.access |= Opcodes.ACC_FINAL;

        InsnList l = clinit.instructions;

        MethodInsnNode boxNode;
        if (TypeUtils.isPrimitive(t)) {
            boxNode = boxNode(t);
            initList.insert(boxNode);
        }

        if (initVal != null) {
            initList.insert(new LdcInsnNode(initVal));
        } else {
            // find first fld store; this is the initialization place
            AbstractInsnNode initNode = findNodeInitialization(fn);

            if (initNode != null) {
                // found the initialization place;
                // just replace the FLD_STORE with the TLS init sequence
                l.insert(initNode, initList);
                l.remove(initNode);
                return;
            } else {
                // no initialization done; use primitive defaults or NULL
                addDefaultVal(t, initList);
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
           case Type.CHAR: {
               l.insert(new InsnNode(Opcodes.ICONST_0));
               break;
           }
           case Type.LONG: {
               l.insert(new InsnNode(Opcodes.LCONST_0));
               break;
           }
           case Type.FLOAT: {
               l.insert(new InsnNode(Opcodes.FCONST_0));
               break;
           }
           case Type.DOUBLE: {
               l.insert(new InsnNode(Opcodes.DCONST_0));
               break;
           }
           default: {
               l.insert(new InsnNode(Opcodes.ACONST_NULL));
           }
        }
    }

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

                List<AnnotationNode> annots = new LinkedList<>();
                AnnotationNode an = new AnnotationNode(Type.getDescriptor(Return.class));
                annots.add(an);
                mn.visibleParameterAnnotations = mn.visibleParameterAnnotations != null ?
                                                    Arrays.copyOf(mn.visibleParameterAnnotations, args.length) :
                                                    new List[args.length];
                mn.visibleParameterAnnotations[args.length - 1] = annots;
                mn.desc = Type.getMethodDescriptor(retType, args);

                if (mn instanceof BTraceMethodNode) {
                    BTraceMethodNode bmn = (BTraceMethodNode)mn;
                    OnMethod om = bmn.getOnMethod();

                    if (om != null && om.getTargetName().equals(mn.name) && om.getTargetDescriptor().equals(oldDesc)) {
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

        boolean checkFields = !(tlsFldNames.isEmpty() &&
                                exportFldNames.isEmpty() &&
                                injectedFlds.isEmpty());

        MethodClassifier clsf = getClassifier(mn);

        int retopcode = Type.getReturnType(mn.desc).getOpcode(Opcodes.IRETURN);
        InsnList l = mn.instructions;
        for (AbstractInsnNode n = l.getFirst(); n != null; n = n != null ? n.getNext() : null) {
            int type = n.getType();
            if (checkFields && type == AbstractInsnNode.FIELD_INSN) {
                FieldInsnNode fin = (FieldInsnNode)n;
                if (fin.owner.equals(cn.name)) {
                    if (tlsFldNames.contains(fin.name) &&
                        !fin.desc.equals(THREAD_LOCAL_TYPE.getDescriptor())) {
                        n = updateTLSUsage(fin, l);
                    } else if (exportFldNames.contains(fin.name)) {
                        n = updateExportUsage(cn, fin, l);
                    } else if (injectedFlds.containsKey(fin.name)) {
                        n = updateInjectedUsage(cn, fin, l, lvg);
                    }
                }
            } else if (type == AbstractInsnNode.METHOD_INSN) {
                MethodInsnNode min = (MethodInsnNode)n;
                n = unfoldServiceInstantiation(cn, min, l);
            } else if (n.getOpcode() == retopcode && isClassified(clsf, MethodClassifier.RT_AWARE)) {
                addBTraceRuntimeExit((InsnNode)n, l, lvg);
            }
        }
    }

    private void recalculateVars(MethodNode mn, LocalVarGenerator lvg) {
        for(AbstractInsnNode n = mn.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getType() == AbstractInsnNode.VAR_INSN) {
                VarInsnNode vin = (VarInsnNode)n;
                vin.var = LocalVarGenerator.translateIdx(vin.var);
            }
        }
    }

    private void processClinit(ClassNode cn) {
        for(MethodNode mn : getMethods(cn)) {
            if (mn.name.equals("<clinit>")) {
                clinit = mn;
                break;
            }
        }
        if (clinit == null) {
            clinit = new MethodNode(
                Opcodes.ASM5, (Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC),
                "<clinit>", "()V", null, new String[0]
            );
            clinit.instructions.add(new InsnNode(Opcodes.RETURN));
            cn.methods.add(0, clinit);
        }
        initRuntime(cn, clinit);
    }

    private void initRuntime(ClassNode cn, MethodNode clinit) {
        addRuntimeNode(cn);
        InsnList l = new InsnList();
        l.add(new LdcInsnNode(Type.getObjectType(cn.name)));
        l.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            BTRACE_RUNTIME_TYPE.getInternalName(),
            "forClass",
            Type.getMethodDescriptor(BTRACE_RUNTIME_TYPE, Type.getType(Class.class)),
            false)
        );
        l.add(new FieldInsnNode(Opcodes.PUTSTATIC, cn.name, rtField.name, rtField.desc));

        LabelNode start = new LabelNode();
        l.add(getRuntime(cn));
        l.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC, BTRACE_RUNTIME_TYPE.getInternalName(),
            "enter", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, BTRACE_RUNTIME_TYPE),
            false
        ));
        l.add(new JumpInsnNode(Opcodes.IFNE, start));
        l.add(getReturnSequence(clinit, true));
        l.add(start);

        clinit.instructions.insert(l);

        startRuntime(clinit);
    }

    private void startRuntime(MethodNode clinit1) {
        for (AbstractInsnNode n = clinit1.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getOpcode() == Opcodes.RETURN) {
                AbstractInsnNode prev = n.getPrevious();
                if (prev != null && prev.getType() == AbstractInsnNode.METHOD_INSN) {
                    MethodInsnNode min = (MethodInsnNode)prev;
                    if (min.name.equals("leave")) {
                        // don't start the runtime if we are bailing out (BTraceRuntime.leave())
                        continue;
                    }
                }
                clinit1.instructions.insertBefore(n, new MethodInsnNode(
                    Opcodes.INVOKESTATIC, BTRACE_RUNTIME_TYPE.getInternalName(),
                    "start", "()V", false
                ));
            }
        }
    }

    private FieldInsnNode getRuntime(ClassNode cn) {
        return new FieldInsnNode(Opcodes.GETSTATIC, cn.name, rtField.name, rtField.desc);
    }

    private MethodInsnNode getRuntimeExit() {
        return new MethodInsnNode(
            Opcodes.INVOKESTATIC, BTRACE_RUNTIME_TYPE.getInternalName(),
            "leave", "()V", false
        );
    }

    private void addRuntimeNode(ClassNode cn) {
        rtField = new FieldNode(
            Opcodes.ASM5, (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC),
            "runtime", Type.getDescriptor(BTraceRuntime.class),
            null, null
        );
        cn.fields.add(0, rtField);
    }

    private void addBTraceErrorHandler(MethodNode mn, LocalVarGenerator lvg) {
        if (!mn.name.equals("<clinit>") && isUnannotated(mn)) return;

        Type throwableType = Type.getType(Throwable.class);
        MethodClassifier clsf = getClassifier(mn);
        if (isClassified(clsf, MethodClassifier.RT_AWARE)) {
            LabelNode from = new LabelNode();
            LabelNode to = new LabelNode();
            InsnList l = mn.instructions;
            l.insert(from);
            l.add(to);
            l.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, BTRACE_RUNTIME_TYPE.getInternalName(),
                "handleException", Type.getMethodDescriptor(Type.VOID_TYPE, throwableType),
                false
            ));
            l.add(getReturnSequence(mn, true));
            mn.tryCatchBlocks.add(new TryCatchBlockNode(from, to, to, throwableType.getInternalName()));
        }
    }

    private void addBTraceRuntimeEnter(ClassNode cn, MethodNode mn, LocalVarGenerator lvg) {
        // no runtime check for <clinit>
        if (mn.name.equals("<clinit>")) return;

        MethodClassifier clsf = getClassifier(mn);
        if (isClassified(clsf, MethodClassifier.RT_AWARE)) {
            InsnList entryCheck = new InsnList();
            entryCheck.add(getRuntime(cn));
            if (isClassified(clsf, MethodClassifier.GUARDED)) {
                LabelNode start = new LabelNode();
                entryCheck.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC, BTRACE_RUNTIME_TYPE.getInternalName(),
                    "enter", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, BTRACE_RUNTIME_TYPE),
                    false
                ));
                entryCheck.add(new JumpInsnNode(Opcodes.IFNE, start));
                entryCheck.add(getReturnSequence(mn, false));
                entryCheck.add(start);
            }
            mn.instructions.insert(entryCheck);
        }
    }

    private void addBTraceRuntimeExit(InsnNode n, InsnList l, LocalVarGenerator lvg) {
        l.insertBefore(n, getRuntimeExit());
    }

    private List<MethodNode> getMethods(ClassNode cn) {
        return (List<MethodNode>)cn.methods;
    }

    private List<FieldNode> getFields(ClassNode cn) {
        return (List<FieldNode>)cn.fields;
    }

    public static AnnotationNode getAnnotation(FieldNode fn, Type annotation) {
        if (fn == null || (fn.visibleAnnotations == null && fn.invisibleAnnotations == null)) return null;
        String targetDesc = annotation.getDescriptor();
        if (fn.visibleAnnotations != null) {
            for(AnnotationNode an : ((List<AnnotationNode>)fn.visibleAnnotations)) {
                if (an.desc.equals(targetDesc)) {
                    return an;
                }
            }
        }
        if (fn.invisibleAnnotations != null) {
            for(AnnotationNode an : ((List<AnnotationNode>)fn.invisibleAnnotations)) {
                if (an.desc.equals(targetDesc)) {
                    return an;
                }
            }
        }
        return null;
    }

    public static AnnotationNode getAnnotation(MethodNode fn, Type annotation) {
        if (fn == null || fn.visibleAnnotations == null) return null;
        String targetDesc = annotation.getDescriptor();
        for(AnnotationNode an : ((List<AnnotationNode>)fn.visibleAnnotations)) {
            if (an.desc.equals(targetDesc)) {
                return an;
            }
        }
        return null;
    }

    private List<AnnotationNode> getAnnotations(MethodNode mn) {
        return (List<AnnotationNode>)mn.visibleAnnotations != null ?
                                        mn.visibleAnnotations :
                                        Collections.emptyList();
    }

    private MethodClassifier getClassifier(MethodNode mn) {
        // <clinit> will always be guarded by BTrace error handler
        if (mn.name.equals("<clinit>")) return MethodClassifier.GUARDED;

        for(AnnotationNode an : getAnnotations(mn)) {
            if (rtAwareAnnots.contains(an.desc)) {
                if (guardedAnnots.contains(an.desc)) {
                    return MethodClassifier.GUARDED;
                } else {
                    return MethodClassifier.RT_AWARE;
                }
            }
        }
        return MethodClassifier.NONE;
    }

    private FieldInsnNode findNodeInitialization(FieldNode fn) {
        for (AbstractInsnNode n = clinit.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getType() == AbstractInsnNode.FIELD_INSN) {
                FieldInsnNode fldInsnNode = (FieldInsnNode)n;
                if (fldInsnNode.getOpcode() == Opcodes.PUTSTATIC &&
                    fldInsnNode.name.equals(fn.name)) {
                    return fldInsnNode;
                }
            }
        }
        return null;
    }

    private MethodInsnNode findBTraceRuntimeStart() {
        for (AbstractInsnNode n = clinit.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n.getType() == AbstractInsnNode.METHOD_INSN) {
                MethodInsnNode min = (MethodInsnNode)n;
                if (min.getOpcode() == Opcodes.INVOKESTATIC &&
                    min.owner.equals(BTRACE_RUNTIME_TYPE.getInternalName()) &&
                    min.name.equals("start")) {
                    return min;
                }
            }
        }
        return null;
    }

    private AbstractInsnNode updateTLSUsage(FieldInsnNode fin, InsnList l) {
        String unboxed = fin.desc;
        int opcode = fin.getOpcode();
        // retrieve the TLS field
        fin.setOpcode(Opcodes.GETSTATIC);
        // change the desc from the contained type to TLS type
        fin.desc = THREAD_LOCAL_TYPE.getDescriptor();

        String boxed = boxDesc(unboxed);
        Type unboxedType = Type.getType(unboxed);
        Type boxedType = Type.getType(boxed);

        if (opcode == Opcodes.GETSTATIC) {
            InsnList toInsert = new InsnList();
            MethodInsnNode getNode = new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                THREAD_LOCAL_TYPE.getInternalName(),
                "get",
                Type.getMethodDescriptor(Type.getType(Object.class)),
                false
            );
            toInsert.add(getNode);
            toInsert.add(new TypeInsnNode(Opcodes.CHECKCAST, boxedType.getInternalName()));
            if (!boxed.equals(unboxed)) {
                // must unbox
                MethodInsnNode unboxNode = unboxNode(boxedType, unboxedType);
                if (unboxNode != null) {
                    toInsert.add(unboxNode);
                }
            }
            l.insert(fin, toInsert);
        } else if (opcode == Opcodes.PUTSTATIC) {
            MethodInsnNode boxNode = null;
            if (!boxed.equals(unboxed)) {
                // must box
                boxNode = boxNode(unboxedType, boxedType);
                l.insert(fin.getPrevious(), boxNode);
            }
            MethodInsnNode setNode = new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                THREAD_LOCAL_TYPE.getInternalName(),
                "set",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class)),
                false
            );
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
        Type strType = Type.getType(String.class);

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
            case Type.BOOLEAN: {
                methodName = prefix + "Int";
                tType = Type.getType(int.class);
                break;
            }
            case Type.LONG: {
                methodName = prefix + "Long";
                tType = Type.getType(long.class);
                break;
            }
            case Type.FLOAT: {
                methodName = prefix + "Float";
                tType = Type.getType(float.class);
                break;
            }
            case Type.DOUBLE: {
                methodName = prefix + "Double";
                tType = Type.getType(double.class);
                break;
            }
            case Type.OBJECT: {
                if (t.equals(strType)) {
                    methodName = prefix + "String";
                    tType = strType;
                }
                break;
            }
        }
        if (methodName == null) {
            // if the perf counter is not accessible
            // just put null on the stack for GETSTATIC
            // and remove the topmost item from the stack for PUTSTATIC
            l.insert(fin, isPut ? new InsnNode(Opcodes.POP) :
                                  new InsnNode(Opcodes.ACONST_NULL));
        } else {
            InsnList toInsert = new InsnList();
            toInsert.add(new LdcInsnNode(perfCounterName(cn, fin.name)));
            toInsert.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, BTRACE_RUNTIME_TYPE.getInternalName(),
                methodName, isPut ? Type.getMethodDescriptor(Type.VOID_TYPE, tType, strType) :
                                    Type.getMethodDescriptor(tType, strType),
                false
            ));
            l.insert(fin, toInsert);
        }
        AbstractInsnNode ret = fin.getNext();
        l.remove(fin);
        return ret;
    }

    private AbstractInsnNode updateInjectedUsage(ClassNode cn, FieldInsnNode fin, InsnList l, LocalVarGenerator lvg) {
        Integer varIdx = serviceLocals.get(fin.name);
        if (varIdx != null) {
            VarInsnNode load = new VarInsnNode(
                Type.getType(fin.desc).getOpcode(Opcodes.ILOAD),
                varIdx
            );
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
                String name = (String)iter.next();
                Object val = iter.next();
                switch (name) {
                    case "value":
                        svcType = (String)((String[])val)[1];
                        break;
                    case "factoryMethod":
                        fctryMethod = (String)val;
                        break;
                }
            }
        }
        varIdx = lvg.newVar(implType);
        if (svcType.equals("SIMPLE")) {
            if (fctryMethod == null || fctryMethod.isEmpty()) {
                toInsert.add(new TypeInsnNode(Opcodes.NEW, implType.getInternalName()));
                toInsert.add(new InsnNode(Opcodes.DUP));
                toInsert.add(new InsnNode(Opcodes.DUP));
                toInsert.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL, implType.getInternalName(),
                    "<init>", "()V", false
                ));
            } else {
                toInsert.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC, implType.getInternalName(), fctryMethod,
                    Type.getMethodDescriptor(implType), false
                ));
                toInsert.add(new InsnNode(Opcodes.DUP));
            }
        } else { // RuntimeService here
            if (fctryMethod == null || fctryMethod.isEmpty()) {
                toInsert.add(new TypeInsnNode(Opcodes.NEW, implType.getInternalName()));
                toInsert.add(new InsnNode(Opcodes.DUP));
                toInsert.add(new InsnNode(Opcodes.DUP));
                toInsert.add(getRuntime(cn));
                toInsert.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL, implType.getInternalName(),
                    "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, BTRACE_RUNTIME_TYPE), false
                ));
            } else {
                toInsert.add(getRuntime(cn));
                toInsert.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC, implType.getInternalName(), fctryMethod,
                    Type.getMethodDescriptor(implType, BTRACE_RUNTIME_TYPE), false
                ));
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

    private AbstractInsnNode unfoldServiceInstantiation(ClassNode cn, MethodInsnNode min, InsnList l) {
        if (min.owner.equals(SERVICE_TYPE.getInternalName())) {
            AbstractInsnNode next = min.getNext();
            switch (min.name) {
                case "simple": {
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

                            Type sType = (Type)((LdcInsnNode)ldcType).cst;
                            InsnList toInsert = new InsnList();
                            toInsert.add(new TypeInsnNode(Opcodes.NEW, sType.getInternalName()));
                            toInsert.add(new InsnNode(Opcodes.DUP));
                            toInsert.add(new MethodInsnNode(
                                Opcodes.INVOKESPECIAL, sType.getInternalName(),
                                "<init>", "()V", false
                            ));
                            l.insertBefore(next, toInsert);
                        }
                    } else if (args.length == 2) {
                        AbstractInsnNode ldcType = min.getPrevious();
                        AbstractInsnNode ldcFMethod = ldcType.getPrevious();
                        if (ldcType.getType() == AbstractInsnNode.LDC_INSN &&
                            ldcFMethod.getType() == AbstractInsnNode.LDC_INSN) {
                            // remove the original sequence
                            l.remove(min);
                            l.remove(ldcType);
                            l.remove(ldcFMethod);
                            if (next.getOpcode() == Opcodes.CHECKCAST) {
                                next = next.getNext();
                                l.remove(next.getPrevious());
                            }
                            // ---

                            Type sType = (Type)((LdcInsnNode)ldcType).cst;
                            String fMethod = (String)((LdcInsnNode)ldcFMethod).cst;

                            InsnList toInsert = new InsnList();
                            toInsert.add(new TypeInsnNode(Opcodes.NEW, sType.getInternalName()));
                            toInsert.add(new InsnNode(Opcodes.DUP));
                            toInsert.add(new LdcInsnNode(fMethod));
                            toInsert.add(new MethodInsnNode(
                                Opcodes.INVOKESPECIAL, sType.getInternalName(),
                                "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false
                            ));
                            l.insertBefore(next, toInsert);
                        }
                    }
                    break;
                }
                case "runtime": {
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

                            Type sType = (Type)((LdcInsnNode)ldcType).cst;
                            InsnList toInsert = new InsnList();
                            toInsert.add(new TypeInsnNode(Opcodes.NEW, sType.getInternalName()));
                            toInsert.add(new InsnNode(Opcodes.DUP));
                            toInsert.add(getRuntime(cn));
                            toInsert.add(new MethodInsnNode(
                                Opcodes.INVOKESPECIAL, sType.getInternalName(),
                                "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, BTRACE_RUNTIME_TYPE), false
                            ));
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

    private InsnList getReturnSequence(MethodNode mn, boolean addRuntimeExit) {
        InsnList l = new InsnList();
        Type retType = Type.getReturnType(mn.desc);
        if (retType != Type.VOID_TYPE) {
            int retIndex = -1;
            if (mn.visibleParameterAnnotations != null) {
                int offset = 0;
                Type[] params = Type.getArgumentTypes(mn.desc);
                for(int i = 0; i < mn.visibleParameterAnnotations.length; i++) {
                    if (mn.visibleParameterAnnotations[i] != null) {
                        for(AnnotationNode an : (List<AnnotationNode>)mn.visibleParameterAnnotations[i]) {
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
                    case Type.BOOLEAN: {
                        l.add(new InsnNode(Opcodes.ICONST_0));
                        break;
                    }
                    case Type.LONG: {
                        l.add(new InsnNode(Opcodes.LCONST_0));
                        break;
                    }
                    case Type.FLOAT: {
                        l.add(new InsnNode(Opcodes.FCONST_0));
                        break;
                    }
                    case Type.DOUBLE: {
                        l.add(new InsnNode(Opcodes.DCONST_0));
                        break;
                    }
                    case Type.ARRAY:
                    case Type.OBJECT: {
                        l.add(new InsnNode(Opcodes.ACONST_NULL));
                        break;
                    }
                }
            }
        }
        if (addRuntimeExit) {
            l.add(getRuntimeExit());
        }
        l.add(new InsnNode(retType.getOpcode(Opcodes.IRETURN)));
        return l;
    }

    // For each @Export field, we create a perf counter
    // with the name "btrace.<class name>.<field name>"
    private static final String BTRACE_COUNTER_PREFIX = "btrace.";
    private String perfCounterName(ClassNode cn, String fieldName) {
        return BTRACE_COUNTER_PREFIX + Type.getObjectType(cn.name).getInternalName() + "." + fieldName;
    }

    private String boxDesc(String desc) {
        Type boxed = boxTypeMap.get(desc);
        return boxed != null ? boxed.getDescriptor() : desc;
    }

    private MethodInsnNode boxNode(Type unboxed) {
        String boxedDesc = boxDesc(unboxed.getDescriptor());
        if (boxedDesc != null) {
            return boxNode(unboxed, Type.getType(boxedDesc));
        }
        return null;
    }

    private MethodInsnNode boxNode(Type unboxed, Type boxed) {
        return new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            boxed.getInternalName(),
            "valueOf",
            Type.getMethodDescriptor(boxed, unboxed),
            false
        );
    }

    private MethodInsnNode unboxNode(Type boxed, Type unboxed) {
        String mName = null;
        if (boxed.equals(Type.getType(Integer.class))) {
            mName = "intValue";
        } else if (boxed.equals(Type.getType(Short.class))) {
            mName = "shortValue";
        } else if (boxed.equals(Type.getType(Long.class))) {
            mName = "longValue";
        } else if (boxed.equals(Type.getType(Float.class))) {
            mName = "floatValue";
        } else if (boxed.equals(Type.getType(Double.class))) {
            mName = "doubleValue";
        } else if (boxed.equals(Type.getType(Boolean.class))) {
            mName = "booleanValue";
        } else if (boxed.equals(Type.getType(Character.class))) {
            mName = "charValue";
        }

        if (mName != null) {
            return new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                boxed.getInternalName(),
                mName,
                Type.getMethodDescriptor(unboxed),
                false
            );
        }
        return null;
    }

    private int getReturnMethodParameter(MethodNode mn) {
        if (mn.visibleParameterAnnotations != null) {
            Type[] args = Type.getArgumentTypes(mn.desc);
            for(int i=0;i<mn.visibleParameterAnnotations.length;i++) {
                List paList = (List)mn.visibleParameterAnnotations[i];
                if (paList != null) {
                    for(Object anObj : paList) {
                        AnnotationNode an = (AnnotationNode)anObj;
                        if (an.desc.equals(Type.getDescriptor(Return.class))) {
                            return i;
                        }
                    }
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean isClassified(MethodClassifier clsf, MethodClassifier requested) {
        return clsf.ordinal() >= requested.ordinal();
    }

    private static boolean isUnannotated(MethodNode mn) {
        return mn == null || mn.visibleAnnotations == null || mn.visibleAnnotations.isEmpty();
    }
}
