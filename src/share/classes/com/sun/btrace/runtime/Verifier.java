/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace.runtime;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.List;
import com.sun.btrace.VerifierException;
import com.sun.btrace.annotations.TargetInstance;
import com.sun.btrace.annotations.TargetMethodOrField;
import com.sun.btrace.annotations.Duration;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import static com.sun.btrace.runtime.Constants.*;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.ProbeClassName;
import com.sun.btrace.annotations.ProbeMethodName;
import com.sun.btrace.annotations.Return;
import com.sun.btrace.annotations.Sampled;
import com.sun.btrace.annotations.Self;
import com.sun.btrace.annotations.Where;
import com.sun.btrace.util.Messages;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.FieldVisitor;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;

/**
 * This class verifies that a BTrace program is safe
 * and well-formed.
 * Also it fills the onMethods and onProbes structures with the data taken from
 * the annotations
 *
 * @author A. Sundararajan
 * @autohr J. Bachorik
 */
public class Verifier extends ClassVisitor {
    public static final String BTRACE_SELF_DESC = Type.getDescriptor(Self.class);
    public static final String BTRACE_RETURN_DESC = Type.getDescriptor(Return.class);
    public static final String BTRACE_TARGETMETHOD_DESC = Type.getDescriptor(TargetMethodOrField.class);
    public static final String BTRACE_TARGETINSTANCE_DESC = Type.getDescriptor(TargetInstance.class);
    public static final String BTRACE_DURATION_DESC = Type.getDescriptor(Duration.class);
    public static final String BTRACE_PROBECLASSNAME_DESC = Type.getDescriptor(ProbeClassName.class);
    public static final String BTRACE_PROBEMETHODNAME_DESC = Type.getDescriptor(ProbeMethodName.class);

    private boolean seenBTrace;
    private String className;
    private List<OnMethod> onMethods;
    private List<OnProbe> onProbes;
    private boolean unsafeScript, unsafeAllowed;
    private CycleDetector cycleDetector;

    public Verifier(ClassVisitor cv, boolean unsafe) {
        super(Opcodes.ASM4, cv);
        this.unsafeAllowed = unsafe;
        onMethods = new ArrayList<OnMethod>();
        onProbes = new ArrayList<OnProbe>();
        cycleDetector = new CycleDetector();
    }

    public Verifier(ClassVisitor cv) {
        this(cv, false);
    }

    public String getClassName() {
        return className;
    }

    public List<OnMethod> getOnMethods() {
        return onMethods;
    }

    public List<OnProbe> getOnProbes() {
        return onProbes;
    }

    @Override
    public void visitEnd() {
        if (cycleDetector.hasCycle()) {
            reportError("execution.loop.danger");
        }
        super.visitEnd();
    }

    public void visit(int version, int access, String name,
            String signature, String superName, String[] interfaces) {
        if ((access & ACC_INTERFACE) != 0 ||
            (access & ACC_ENUM) != 0  ) {
            reportError("btrace.program.should.be.class");
        }
        if ((access & ACC_PUBLIC) == 0) {
            reportError("class.should.be.public", name);
        }

        if (! superName.equals(JAVA_LANG_OBJECT)) {
            reportError("object.superclass.required", superName);
        }
        if (interfaces != null && interfaces.length > 0) {
            reportError("no.interface.implementation");
        }
        className = name;
        super.visit(version, access, name, signature,
                    superName, interfaces);
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationVisitor delegate = super.visitAnnotation(desc, visible);
        if (desc.equals(BTRACE_DESC)) {
            seenBTrace = true;
            return new AnnotationVisitor(Opcodes.ASM4, delegate) {
                @Override
                public void visit(String name, Object value) {
                    if ("unsafe".equals(name) && Boolean.TRUE.equals(value)) {
                        if (!unsafeAllowed) {
                            reportError("agent.unsafe.not.allowed");
                        }
                        unsafeScript = true; // Found @BTrace(..., unsafe=true)
                    }
                    super.visit(name, value);
                }
            };
        }
        return delegate;
    }

    public FieldVisitor	visitField(int access, String name,
            String desc, String signature, Object value) {
        if (! seenBTrace) {
            reportError("not.a.btrace.program");
        }
        if ((access & ACC_STATIC) == 0) {
            reportError("agent.no.instance.variables", name);
        }
        return super.visitField(access, name, desc, signature, value);
    }

    public void visitInnerClass(String name, String outerName,
            String innerName, int access) {
        if (className.equals(outerName)) {
            reportError("no.nested.class");
        }
    }

    public MethodVisitor visitMethod(final int access, final String methodName,
            final String methodDesc, String signature, String[] exceptions) {

        if (! seenBTrace) {
            reportError("not.a.btrace.program");
        }

        if ((access & ACC_SYNCHRONIZED) != 0) {
            reportError("no.synchronized.methods", methodName + methodDesc);
        }

        if (! methodName.equals(CONSTRUCTOR)) {
            if ((access & ACC_STATIC) == 0) {
                reportError("no.instance.method", methodName + methodDesc);
            }
        }

        MethodVisitor mv = super.visitMethod(access, methodName,
                   methodDesc, signature, exceptions);

        return new MethodVerifier(this, mv, className, cycleDetector, methodName + methodDesc) {
            private OnMethod om = null;
            private boolean asBTrace = false;
            private boolean sampled = false;

            @Override
            public void visitEnd() {
                if ((access & ACC_PUBLIC) == 0 && !methodName.equals(CLASS_INITIALIZER)) {
                    if (asBTrace) { // only btrace handlers are enforced to be public
                        reportError("method.should.be.public", methodName + methodDesc);
                    }
                }
                if (Type.getReturnType(methodDesc) != Type.VOID_TYPE) {
                    if (asBTrace) {
                        reportError("return.type.should.be.void", methodName + methodDesc);
                    }
                }
                if (om != null) {
                    validateSamplerLocation();
                }
                super.visitEnd();
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, final String desc, boolean visible) {
                if (desc.equals(BTRACE_SELF_DESC)) {
                    // all allowed
                    if (om != null) {
                        om.setSelfParameter(parameter);
                    }
                }
                if (desc.equals(BTRACE_RETURN_DESC)) {
                    if (om != null) {
                        if (om.getLocation().getValue() == Kind.RETURN ||
                            (om.getLocation().getValue() == Kind.CALL && om.getLocation().getWhere() == Where.AFTER) ||
                            (om.getLocation().getValue() == Kind.ARRAY_GET && om.getLocation().getWhere() == Where.AFTER) ||
                            (om.getLocation().getValue() == Kind.FIELD_GET && om.getLocation().getWhere() == Where.AFTER) ||
                            (om.getLocation().getValue() == Kind.NEW && om.getLocation().getWhere() == Where.AFTER) ||
                            (om.getLocation().getValue() == Kind.NEWARRAY && om.getLocation().getWhere() == Where.AFTER)) {
                            om.setReturnParameter(parameter);
                        } else {
                            reportError("return.desc.invalid", methodName + methodDesc + "(" + parameter + ")");
                        }
                    }
                }
                if (desc.equals(BTRACE_TARGETMETHOD_DESC)) {
                    if (om != null) {
                        if (om.getLocation().getValue() == Kind.CALL ||
                            om.getLocation().getValue() == Kind.FIELD_GET ||
                            om.getLocation().getValue() == Kind.FIELD_SET) {
                            om.setTargetMethodOrFieldParameter(parameter);
                        } else {
                            reportError("called-method.desc.invalid", methodName + methodDesc + "(" + parameter + ")");
                        }
                    }
                }
                if (desc.equals(BTRACE_TARGETINSTANCE_DESC)) {
                    if (om != null) {
                        if (om.getLocation().getValue() == Kind.CALL ||
                            om.getLocation().getValue() == Kind.FIELD_GET ||
                            om.getLocation().getValue() == Kind.FIELD_SET) {
                            om.setTargetInstanceParameter(parameter);
                        } else {
                            reportError("called-instance.desc.invalid", methodName + methodDesc + "(" + parameter + ")");
                        }
                    }
                }
                if (desc.equals(BTRACE_DURATION_DESC)) {
                    if (om != null) {
                        if (om.getLocation().getValue() == Kind.RETURN ||
                            om.getLocation().getValue() == Kind.ERROR) {
                            om.setDurationParameter(parameter);
                        } else if (om.getLocation().getValue() == Kind.CALL &&
                                   om.getLocation().getWhere() == Where.AFTER) {
                            om.setDurationParameter(parameter);
                        } else {
                            reportError("duration.desc.invalid", methodName + methodDesc + "(" + parameter + ")");
                        }
                    }
                }
                if (desc.equals(BTRACE_PROBECLASSNAME_DESC)) {
                    // allowed for all
                    if (om != null) {
                        om.setClassNameParameter(parameter);
                    }
                }
                if (desc.equals(BTRACE_PROBEMETHODNAME_DESC)) {
                    // allowed for all
                    if (om != null) {
                        om.setMethodParameter(parameter);
                    }
                }

                return new AnnotationVisitor(Opcodes.ASM4, super.visitParameterAnnotation(parameter, desc, visible)) {

                    public void visit(String string, Object o) {
                        System.err.println(string + " : " + o);
                        if (om != null) {
                            if (string.equals("fqn")) {
                                if (desc.equals(BTRACE_TARGETMETHOD_DESC)) {
                                    om.setTargetMethodOrFieldFqn((Boolean)o);
                                } else if (desc.equals(BTRACE_PROBEMETHODNAME_DESC)) {
                                    om.setMethodFqn((Boolean)o);
                                }
                            }
                        }
                        super.visit(string, o);
                    }
                };
            }

            public AnnotationVisitor visitAnnotation(String desc,
                                  boolean visible) {
                if (desc.startsWith("Lcom/sun/btrace/annotations/")) {
                    asBTrace = true;
                    cycleDetector.addStarting(new CycleDetector.Node(methodName + methodDesc));
                }

                if (desc.equals(ONMETHOD_DESC)) {
                    om = new OnMethod();
                    onMethods.add(om);
                    om.setTargetName(methodName);
                    om.setTargetDescriptor(methodDesc);
                    return new AnnotationVisitor(Opcodes.ASM4) {
                        public void visit(String name, Object value) {
                            if (name.equals("clazz")) {
                                om.setClazz((String)value);
                            } else if (name.equals("method")) {
                                om.setMethod((String)value);
                            } else if (name.equals("type")) {
                                om.setType((String)value);
                            }
                        }

                        public AnnotationVisitor visitAnnotation(String name,
                                  String desc) {
                            if (desc.equals(LOCATION_DESC)) {
                                final Location loc = new Location();
                                return new AnnotationVisitor(Opcodes.ASM4) {
                                    public void visitEnum(String name, String desc, String value) {
                                        if (desc.equals(WHERE_DESC)) {
                                            loc.setWhere(Enum.valueOf(Where.class, value));
                                        } else if (desc.equals(KIND_DESC)) {
                                            loc.setValue(Enum.valueOf(Kind.class, value));
                                        }
                                    }

                                    public void	visit(String name, Object value) {
                                        if (name.equals("clazz")) {
                                            loc.setClazz((String)value);
                                        } else if (name.equals("method")) {
                                            loc.setMethod((String)value);
                                        } else if (name.equals("type")) {
                                            loc.setType((String)value);
                                        } else if (name.equals("field")) {
                                            loc.setField((String)value);
                                        } else if (name.equals("line")) {
                                            loc.setLine(((Number)value).intValue());
                                        }
                                    }

                                    public void visitEnd() {
                                        om.setLocation(loc);
                                    }
                                };
                            }

                            return super.visitAnnotation(name, desc);
                        }
                    };
                } else if (desc.equals(ONPROBE_DESC)) {
                    final OnProbe op = new OnProbe();
                    onProbes.add(op);
                    op.setTargetName(methodName);
                    op.setTargetDescriptor(methodDesc);
                    return new AnnotationVisitor(Opcodes.ASM4) {
                        public void visit(String name, Object value) {
                            if (name.equals("namespace")) {
                                op.setNamespace((String)value);
                            } else if (name.equals("name")) {
                                op.setName((String)value);
                            }
                        }
                    };
                } else if (desc.equals(SAMPLER_DESC)) {
                    if (om != null) {
                        om.setSamplerKind(Sampled.Sampler.Adaptive);
                        return new AnnotationVisitor(Opcodes.ASM4) {
                            private boolean meanSet = false;
                            @Override
                            public void visit(String name, Object value) {
                                if (name.equals("mean")) {
                                    om.setSamplerMean((Integer)value);
                                    meanSet = true;
                                }
                            }

                            @Override
                            public void visitEnum(String name, String desc, String value) {
                                if (name.equals("kind") && desc.equals(Type.getDescriptor(Sampled.Sampler.class))) {
                                    om.setSamplerKind(Sampled.Sampler.valueOf(value));
                                }
                            }

                            @Override
                            public void visitEnd() {
                                if (!meanSet) {
                                    if (om.getSamplerKind() == Sampled.Sampler.Adaptive) {
                                        om.setSamplerMean(500);
                                    } else if (om.getSamplerKind() == Sampled.Sampler.Const) {
                                        om.setSamplerMean(Sampled.MEAN_DEFAULT);
                                    }
                                }
                                if (om.getSamplerKind() == Sampled.Sampler.Adaptive) {
                                    // the time frame for adaptive sampling
                                    // should be at least 180ns -
                                    // (80ns timestamps + 15ns stub) * 2 safety margin
                                    if (om.getSamplerMean() < 180) {
                                        System.err.println("Setting the adaptive sampler time windows to the default of 180ns");
                                        om.setSamplerMean(180);
                                    }
                                }
                                super.visitEnd();
                            }
                        };
                    }
                    sampled = true;
                    return new AnnotationVisitor(Opcodes.ASM4) {};
                } else {
                    return new AnnotationVisitor(Opcodes.ASM4) {};
                }
            }

            private void validateSamplerLocation() {
                if (om == null && sampled) {
                    reportError("sampler.invalid.location", methodName + methodDesc);
                    return;
                }
                if (om.getSamplerKind() != Sampled.Sampler.None) {
                    switch (om.getLocation().getValue()) {
                        case ENTRY:
                        case RETURN:
                        case ERROR:
                        case CALL: {
                            // ok
                            break;
                        }
                        default: {
                            reportError("sampler.invalid.location", methodName + methodDesc);
                        }
                    }
                }
            }
        };
    }

    public void visitOuterClass(String owner, String name,
            String desc) {
        reportError("no.outer.class");
    }

    void reportError(String err) {
        reportError(err, null);
    }

    void reportError(String err, String msg) {
        if (unsafeScript && unsafeAllowed) return;
        String str = Messages.get(err);
        if (msg != null) {
            str += ": " + msg;
        }
        throw new VerifierException(str);
    }

    private static void usage(String msg) {
        System.err.println(msg);
        System.exit(1);
    }

    // simple test main
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage("java com.sun.btrace.runtime.Verifier <.class file>");
        }

        args[0] = args[0].replace('.', '/');
        File file = new File(args[0] + ".class");
        if (! file.exists()) {
            usage("file '" + args[0] + ".class' does not exist");
        }
        FileInputStream fis = new FileInputStream(file);
        ClassReader reader = new ClassReader(new BufferedInputStream(fis));
        Verifier verifier = new Verifier(new ClassVisitor(Opcodes.ASM4) {});
        reader.accept(verifier, 0);
    }
}
