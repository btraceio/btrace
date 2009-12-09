/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
import com.sun.btrace.annotations.Self;
import com.sun.btrace.annotations.Where;
import com.sun.btrace.util.Messages;
import com.sun.btrace.util.NullVisitor;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.ClassAdapter;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.FieldVisitor;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
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
public class Verifier extends ClassAdapter {
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
    private boolean unsafe;


    public Verifier(ClassVisitor cv, boolean unsafe) {
        super(cv);
        this.unsafe = unsafe;
        onMethods = new ArrayList<OnMethod>();
        onProbes = new ArrayList<OnProbe>();
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
        if (desc.equals(BTRACE_DESC)) {
            seenBTrace = true;
        }
        return super.visitAnnotation(desc, visible);
    }

    public FieldVisitor	visitField(int access, String name, 
            String desc, String signature, Object value) {
        if (! seenBTrace) {
            reportError("not.a.btrace.program");
        }
        if ((access & ACC_STATIC) == 0) {
            reportError("no.instance.variables", name);
        }
        return super.visitField(access, name, desc, signature, value); 
    }
     
    public void visitInnerClass(String name, String outerName, 
            String innerName, int access) {
        if (className.equals(outerName) ||
            className.equals(innerName)) {
            reportError("no.nested.class");
        }
    }
     
    public MethodVisitor visitMethod(int access, final String methodName, 
            final String methodDesc, String signature, String[] exceptions) {
        if (! seenBTrace) {
            reportError("not.a.btrace.program");
        }
        
        if ((access & ACC_PUBLIC) == 0 && !methodName.equals(CLASS_INITIALIZER)) {
            reportError("method.should.be.public", methodName + methodDesc);
        }

        if ((access & ACC_SYNCHRONIZED) != 0) {
            reportError("no.synchronized.methods", methodName + methodDesc);
        }

        if (! methodName.equals(CONSTRUCTOR)) {
            if ((access & ACC_STATIC) == 0) {
                reportError("no.instance.method", methodName + methodDesc);
            }
        }

        if (Type.getReturnType(methodDesc) != Type.VOID_TYPE) {
            reportError("return.type.should.be.void", methodName + methodDesc);
        }

        MethodVisitor mv = super.visitMethod(access, methodName, 
                   methodDesc, signature, exceptions);
        return new MethodVerifier(this, mv, className) {
            private OnMethod om = null;

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
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
                        if (om.getLocation().getValue() == Kind.RETURN || om.getLocation().getValue() == Kind.ERROR) {
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
                return super.visitParameterAnnotation(parameter, desc, visible);
            }

            public AnnotationVisitor visitAnnotation(String desc,
                                  boolean visible) {      
                if (desc.equals(ONMETHOD_DESC)) {
                    om = new OnMethod();
                    onMethods.add(om);
                    om.setTargetName(methodName);
                    om.setTargetDescriptor(methodDesc);
                    return new NullVisitor() {
                        public void	visit(String name, Object value) {
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
                                return new NullVisitor() {
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
                    return new NullVisitor() {
                        public void	visit(String name, Object value) {
                            if (name.equals("namespace")) {
                                op.setNamespace((String)value);
                            } else if (name.equals("name")) {
                                op.setName((String)value);
                            }
                        }
                    };
                } else {
                    return new NullVisitor();
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
        if (unsafe) return;
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
        Verifier verifier = new Verifier(new NullVisitor());
        reader.accept(verifier, 0);
    }
}
