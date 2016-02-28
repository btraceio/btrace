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
import com.sun.btrace.annotations.ProbeClassName;
import com.sun.btrace.annotations.ProbeMethodName;
import com.sun.btrace.annotations.Return;
import com.sun.btrace.annotations.Self;
import com.sun.btrace.util.Messages;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.FieldVisitor;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;
import java.util.HashSet;
import java.util.Set;

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
    private boolean classRenamed;
    private final boolean unsafeAllowed;

    private final BTraceClassNode cn;

    public Verifier(BTraceClassNode cv, boolean unsafe) {
        super(Opcodes.ASM5, cv);
        this.unsafeAllowed = unsafe;
        this.cn = cv;
    }


    public Verifier(BTraceClassNode cv) {
        this(cv, false);
    }

    public boolean isClassRenamed() {
        return classRenamed;
    }

    public String getClassName() {
        return cn.name;
    }

    @Override
    public void visitEnd() {
        if (cn.getGraph().hasCycle()) {
            Verifier.this.reportSafetyError("execution.loop.danger");
        }
        super.visitEnd();
    }

    @Override
    public void visit(int version, int access, String name,
            String signature, String superName, String[] interfaces) {
        if ((access & ACC_INTERFACE) != 0 ||
            (access & ACC_ENUM) != 0  ) {
            Verifier.this.reportSafetyError("btrace.program.should.be.class");
        }
        if ((access & ACC_PUBLIC) == 0) {
            reportSafetyError("class.should.be.public", name);
        }

        if (! superName.equals(JAVA_LANG_OBJECT)) {
            reportSafetyError("object.superclass.required", superName);
        }
        if (interfaces != null && interfaces.length > 0) {
            Verifier.this.reportSafetyError("no.interface.implementation");
        }
        super.visit(version, access, name, signature,
                    superName, interfaces);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String type, boolean visible) {
        AnnotationVisitor delegate = super.visitAnnotation(type, visible);
        if (type.equals(BTRACE_DESC)) {
            seenBTrace = true;
            return new AnnotationVisitor(Opcodes.ASM5, delegate) {
                @Override
                public void visit(String name, Object value) {
                    if ("unsafe".equals(name) && Boolean.TRUE.equals(value)) {
                        if (!unsafeAllowed) {
                            Verifier.this.reportSafetyError("agent.unsafe.not.allowed");
                        }
                        cn.setUnsafe(); // Found @BTrace(..., unsafe=true)
                    }
                    super.visit(name, value);
                }
            };
        }
        return delegate;
    }

    @Override
    public FieldVisitor	visitField(int access, final String name,
            String desc, String signature, Object value) {
        if (! seenBTrace) {
            reportSafetyError("not.a.btrace.program");
        }
        if ((access & ACC_STATIC) == 0) {
            reportSafetyError("agent.no.instance.variables", name);
        }
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public void visitInnerClass(String name, String outerName,
            String innerName, int access) {
        if (cn.name.equals(outerName)) {
            reportSafetyError("no.nested.class");
        }
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String methodName,
            final String methodDesc, String signature, String[] exceptions) {

        if (! seenBTrace) {
            reportSafetyError("not.a.btrace.program");
        }

        if ((access & ACC_SYNCHRONIZED) != 0) {
            reportSafetyError("no.synchronized.methods", methodName + methodDesc);
        }

        if (! methodName.equals(CONSTRUCTOR)) {
            if ((access & ACC_STATIC) == 0) {
                reportSafetyError("no.instance.method", methodName + methodDesc);
            }
        }

        return super.visitMethod(access, methodName, methodDesc, signature, exceptions);
    }

    @Override
    public void visitOuterClass(String owner, String name,
            String desc) {
        reportSafetyError("no.outer.class");
    }

    void reportSafetyError(String err) {
        reportSafetyError(err, null);
    }

    void reportSafetyError(String err, String msg) {
        if (cn.isUnsafe()) return;
        reportError(err, msg);
    }

    public static void reportError(String err) {
        reportError(err, null);
    }

    public static void reportError(String err, String msg) {
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
        Verifier verifier = new Verifier(new BTraceClassNode());
        reader.accept(verifier, 0);
    }
}
