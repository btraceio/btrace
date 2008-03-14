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
import static org.objectweb.asm.Opcodes.*;
import static com.sun.btrace.runtime.Constants.*;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Where;
import com.sun.btrace.util.Messages;
import com.sun.btrace.util.NullVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * This class verifies that a BTrace program is safe
 * and well-formed.
 *
 * @author A. Sundararajan
 */
public class Verifier extends ClassAdapter {
    private boolean seenBTrace;
    private String className;
    private List<OnMethod> onMethods;
    private List<OnProbe> onProbes;


    public Verifier(ClassVisitor cv) {
        super(cv);
        onMethods = new ArrayList<OnMethod>();
        onProbes = new ArrayList<OnProbe>();
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
        return new MethodVerifier(mv, className) {
            public AnnotationVisitor visitAnnotation(String desc,
                                  boolean visible) {      
                if (desc.equals(ONMETHOD_DESC)) {
                    final OnMethod om = new OnMethod();
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

    static void reportError(String err) {
        reportError(err, null);
    }

    static void reportError(String err, String msg) {
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
