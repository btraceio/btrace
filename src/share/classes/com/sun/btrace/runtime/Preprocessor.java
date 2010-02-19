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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.sun.btrace.org.objectweb.asm.Opcodes.*;
import static com.sun.btrace.runtime.Constants.*;
import com.sun.btrace.BTraceRuntime;
import com.sun.btrace.annotations.Export;
import com.sun.btrace.annotations.TLS;
import com.sun.btrace.annotations.Property;
import com.sun.btrace.util.NullVisitor;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.Attribute;
import com.sun.btrace.org.objectweb.asm.ClassAdapter;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.ClassWriter;
import com.sun.btrace.org.objectweb.asm.FieldVisitor;
import com.sun.btrace.org.objectweb.asm.Label;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import com.sun.btrace.org.objectweb.asm.Type;

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
 *    6. add prolog and epilog in each BTrace action method
 *       to insert BTraceRuntime.enter/leave and also to call
 *       BTraceRuntime.handleException on exception catch
 *    7. add a field to store client's BTraceRuntime instance
 *
 * 
 * @author A. Sundararajan
 */
public class Preprocessor extends ClassAdapter {
    public static final String JAVA_LANG_THREAD_LOCAL_DESC = "Ljava/lang/ThreadLocal;";
    // btrace specific stuff
    public static final String BTRACE_EXPORT_DESC =
        Type.getDescriptor(Export.class);
    public static final String BTRACE_TLS_DESC =
        Type.getDescriptor(TLS.class);
    public static final String BTRACE_PROPERTY_DESC =
        Type.getDescriptor(Property.class);
    public static final String BTRACE_PROPERTY_NAME = "name";
    public static final String BTRACE_PROPERTY_DESCRIPTION = "description";
    
        
    // stuff from BTraceRuntime class
    public static final String BTRACE_RUNTIME =
        Type.getInternalName(BTraceRuntime.class);
    public static final String BTRACE_RUNTIME_DESC =
        Type.getDescriptor(BTraceRuntime.class);

    public static final String BTRACE_RUNTIME_FIELD_NAME = "runtime";
    public static final String BTRACE_FIELD_PREFIX = "$";

    public static final String BTRACE_RUNTIME_HANDLE_EXCEPTION;
    public static final String BTRACE_RUNTIME_HANDLE_EXCEPTION_DESC;
    public static final String BTRACE_RUNTIME_ENTER;
    public static final String BTRACE_RUNTIME_ENTER_DESC;
    public static final String BTRACE_RUNTIME_LEAVE;
    public static final String BTRACE_RUNTIME_LEAVE_DESC; 
    public static final String BTRACE_RUNTIME_START;
    public static final String BTRACE_RUNTIME_START_DESC; 
    public static final String BTRACE_RUNTIME_FOR_CLASS;
    public static final String BTRACE_RUNTIME_FOR_CLASS_DESC;
    public static final String BTRACE_RUNTIME_NEW_THREAD_LOCAL;
    public static final String BTRACE_RUNTIME_NEW_THREAD_LOCAL_DESC;
    public static final String BTRACE_RUNTIME_NEW_PERFCOUNTER;
    public static final String BTRACE_RUNTIME_NEW_PERFCOUNTER_DESC;
    public static final String BTRACE_RUNTIME_GET_PERFSTRING;
    public static final String BTRACE_RUNTIME_GET_PERFSTRING_DESC;
    public static final String BTRACE_RUNTIME_GET_PERFINT;
    public static final String BTRACE_RUNTIME_GET_PERFINT_DESC;
    public static final String BTRACE_RUNTIME_GET_PERFLONG;
    public static final String BTRACE_RUNTIME_GET_PERFLONG_DESC;
    public static final String BTRACE_RUNTIME_GET_PERFFLOAT;
    public static final String BTRACE_RUNTIME_GET_PERFFLOAT_DESC;
    public static final String BTRACE_RUNTIME_GET_PERFDOUBLE;
    public static final String BTRACE_RUNTIME_GET_PERFDOUBLE_DESC;
    public static final String BTRACE_RUNTIME_PUT_PERFSTRING;
    public static final String BTRACE_RUNTIME_PUT_PERFSTRING_DESC;
    public static final String BTRACE_RUNTIME_PUT_PERFINT;
    public static final String BTRACE_RUNTIME_PUT_PERFINT_DESC;
    public static final String BTRACE_RUNTIME_PUT_PERFLONG;
    public static final String BTRACE_RUNTIME_PUT_PERFLONG_DESC;
    public static final String BTRACE_RUNTIME_PUT_PERFFLOAT;
    public static final String BTRACE_RUNTIME_PUT_PERFFLOAT_DESC;
    public static final String BTRACE_RUNTIME_PUT_PERFDOUBLE;
    public static final String BTRACE_RUNTIME_PUT_PERFDOUBLE_DESC;

    static {     
       try {
           Method handleException = 
                       BTraceRuntime.class.getMethod(
                       "handleException", 
                       new Class[] { Throwable.class });
           BTRACE_RUNTIME_HANDLE_EXCEPTION = 
                       handleException.getName();
           BTRACE_RUNTIME_HANDLE_EXCEPTION_DESC = 
                       Type.getMethodDescriptor(handleException);

           Method enter = BTraceRuntime.class.getMethod(
                       "enter",
                       new Class[] { BTraceRuntime.class });
           BTRACE_RUNTIME_ENTER = enter.getName();
           BTRACE_RUNTIME_ENTER_DESC = 
                       Type.getMethodDescriptor(enter);

           Method leave = BTraceRuntime.class.getMethod(
                       "leave",
                       new Class[0]);
           BTRACE_RUNTIME_LEAVE = leave.getName();
           BTRACE_RUNTIME_LEAVE_DESC = 
                       Type.getMethodDescriptor(leave);

           Method start = BTraceRuntime.class.getMethod(
                       "start",
                       new Class[0]);
           BTRACE_RUNTIME_START = start.getName();
           BTRACE_RUNTIME_START_DESC = 
                       Type.getMethodDescriptor(start);

           Method forClass = BTraceRuntime.class.getMethod(
                       "forClass",
                       new Class[] { Class.class });
           BTRACE_RUNTIME_FOR_CLASS = forClass.getName();
           BTRACE_RUNTIME_FOR_CLASS_DESC = 
                       Type.getMethodDescriptor(forClass);

           Method newThreadLocal = BTraceRuntime.class.getMethod(
                       "newThreadLocal",
                       new Class[] { Object.class });
           BTRACE_RUNTIME_NEW_THREAD_LOCAL = newThreadLocal.getName();
           BTRACE_RUNTIME_NEW_THREAD_LOCAL_DESC = 
                       Type.getMethodDescriptor(newThreadLocal);

           Method newPerfCounter = BTraceRuntime.class.getMethod(
                       "newPerfCounter",
                       new Class[] { String.class, String.class, Object.class });
           BTRACE_RUNTIME_NEW_PERFCOUNTER = newPerfCounter.getName();
           BTRACE_RUNTIME_NEW_PERFCOUNTER_DESC = 
                       Type.getMethodDescriptor(newPerfCounter);

           Method getPerfString = BTraceRuntime.class.getMethod(
                       "getPerfString",
                       new Class[] { String.class });
           BTRACE_RUNTIME_GET_PERFSTRING = getPerfString.getName();
           BTRACE_RUNTIME_GET_PERFSTRING_DESC = 
                       Type.getMethodDescriptor(getPerfString);

           Method getPerfInt = BTraceRuntime.class.getMethod(
                       "getPerfInt",
                       new Class[] { String.class });
           BTRACE_RUNTIME_GET_PERFINT = getPerfInt.getName();
           BTRACE_RUNTIME_GET_PERFINT_DESC = 
                       Type.getMethodDescriptor(getPerfInt);

           Method getPerfLong = BTraceRuntime.class.getMethod(
                       "getPerfLong",
                       new Class[] { String.class });
           BTRACE_RUNTIME_GET_PERFLONG = getPerfLong.getName();
           BTRACE_RUNTIME_GET_PERFLONG_DESC = 
                       Type.getMethodDescriptor(getPerfLong);

           Method getPerfFloat = BTraceRuntime.class.getMethod(
                       "getPerfFloat",
                       new Class[] { String.class });
           BTRACE_RUNTIME_GET_PERFFLOAT = getPerfFloat.getName();
           BTRACE_RUNTIME_GET_PERFFLOAT_DESC = 
                       Type.getMethodDescriptor(getPerfFloat);

           Method getPerfDouble = BTraceRuntime.class.getMethod(
                       "getPerfDouble",
                       new Class[] { String.class });
           BTRACE_RUNTIME_GET_PERFDOUBLE = getPerfDouble.getName();
           BTRACE_RUNTIME_GET_PERFDOUBLE_DESC = 
                       Type.getMethodDescriptor(getPerfDouble);

           Method putPerfString = BTraceRuntime.class.getMethod(
                       "putPerfString",
                       new Class[] { String.class, String.class });
           BTRACE_RUNTIME_PUT_PERFSTRING = putPerfString.getName();
           BTRACE_RUNTIME_PUT_PERFSTRING_DESC = 
                       Type.getMethodDescriptor(putPerfString);

           Method putPerfInt = BTraceRuntime.class.getMethod(
                       "putPerfInt",
                       new Class[] { int.class, String.class });
           BTRACE_RUNTIME_PUT_PERFINT = putPerfInt.getName();
           BTRACE_RUNTIME_PUT_PERFINT_DESC = 
                       Type.getMethodDescriptor(putPerfInt);

           Method putPerfLong = BTraceRuntime.class.getMethod(
                       "putPerfLong",
                       new Class[] { long.class, String.class });
           BTRACE_RUNTIME_PUT_PERFLONG = putPerfLong.getName();
           BTRACE_RUNTIME_PUT_PERFLONG_DESC = 
                       Type.getMethodDescriptor(putPerfLong);

           Method putPerfFloat = BTraceRuntime.class.getMethod(
                       "putPerfFloat",
                       new Class[] { float.class, String.class });
           BTRACE_RUNTIME_PUT_PERFFLOAT = putPerfFloat.getName();
           BTRACE_RUNTIME_PUT_PERFFLOAT_DESC = 
                       Type.getMethodDescriptor(putPerfFloat);

           Method putPerfDouble = BTraceRuntime.class.getMethod(
                       "putPerfDouble",
                       new Class[] { double.class, String.class });
           BTRACE_RUNTIME_PUT_PERFDOUBLE = putPerfDouble.getName();
           BTRACE_RUNTIME_PUT_PERFDOUBLE_DESC = 
                       Type.getMethodDescriptor(putPerfDouble);           
       } catch (RuntimeException re) {
           throw re;
       } catch (Exception exp) {
           throw new RuntimeException(exp);
       }
    }

    // class name as appears in .class file
    private String className;
    // external class name
    private String externalClassName;
    // Type of the currently visited class
    private Type classType;

    // FieldDescriptor (see below) for all fields
    private List<FieldDescriptor> fields;
    private Map<String, FieldDescriptor> threadLocalFields;
    private Map<String, FieldDescriptor> exportFields;

    // flag to tell whether we have seen <clinit> or not
    private boolean classInitializerFound;

    public Preprocessor(ClassVisitor cv) {
        super(cv);
        fields = new ArrayList<FieldDescriptor>();
        threadLocalFields = new HashMap<String, FieldDescriptor>();
        exportFields = new HashMap<String, FieldDescriptor>();
    }

    public void visit(int version,
                  int access,
                  String name,
                  String signature,
                  String superName,
                  String[] interfaces) {
        className = name;     
        classType = Type.getObjectType(className);
        super.visit(version, access, name,
                    signature, superName, interfaces);
    }
    
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        final AnnotationVisitor zupr = super.visitAnnotation(desc, visible);
        if ("Lcom/sun/btrace/annotations/BTrace;".equals(desc)) {
            return new AnnotationVisitor() {

                public void visit(String string, Object o) {
                    if ("name".equals(string)) {
                        externalClassName = (String)o;
                    }
                    zupr.visit(string, o);
                }

                public void visitEnum(String string, String string1, String string2) {
                    zupr.visitEnum(string, string1, string2);
                }

                public AnnotationVisitor visitAnnotation(String string, String string1) {
                    // do nothing
                    return zupr.visitAnnotation(string, string1);
                }

                public AnnotationVisitor visitArray(String string) {
                    // do nothing
                    return zupr.visitArray(string);
                }

                public void visitEnd() {
                    zupr.visitEnd();
                }
            };
        }
        return zupr;
    }
    
    // prefix for BTrace perf counter names.
    private String externalClassName() {
        if (externalClassName == null) {
            externalClassName = className.replace('/', '.');
        }
        return externalClassName;
    }

    // For each @Export field, we create a perf counter
    // with the name "btrace.<class name>.<field name>"
    private static final String BTRACE_COUNTER_PREFIX = "btrace.";
    private String perfCounterName(String fieldName) {
        return BTRACE_COUNTER_PREFIX + externalClassName() + "." + fieldName;
    }

    // save interesting bits of each field
    private static class FieldDescriptor {
        int access;
        String name, desc, signature;
        Object value;
        List<Attribute> attributes;
        boolean isThreadLocal;
        boolean isExport;
        boolean isProperty;
        String propertyName;
        String propertyDescription;
        int var = -1;
        boolean initialized;

        FieldDescriptor(int acc, String n, String d,
                        String sig, Object val, List<Attribute> attrs,
                        boolean tls, boolean isExp, boolean isProp,
                        String propName, String propDescription) {
            access = acc;
            name = n;
            desc = d;
            signature = sig;
            value = val;
            attributes = attrs;
            isThreadLocal = tls;
            isExport = isExp;
            isProperty = isProp;
            propertyName = propName;
            propertyDescription = propDescription;
        }
    }

    public FieldVisitor visitField(final int access, final String name, 
            final String desc, final String signature, final Object value) {        
        final List<Attribute> attrs = new ArrayList<Attribute>();
        return new FieldVisitor() {
            boolean isExport;
            boolean isThreadLocal;
            boolean isProperty;
            String propName = "";
            String propDescription = "";
            
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (desc.equals(BTRACE_TLS_DESC)) {
                    isThreadLocal = true;
                } else if (desc.equals(BTRACE_EXPORT_DESC)) {
                    isExport = true;                    
                } else if (desc.equals(BTRACE_PROPERTY_DESC)) {
                    isProperty = true;
                    return new NullVisitor() {
                        @Override
                        public void visit(String name, Object value) {
                            if (name.equals(BTRACE_PROPERTY_NAME)) {
                                propName = value.toString();
                            } else if (name.equals(BTRACE_PROPERTY_DESCRIPTION)) {
                                propDescription = value.toString();
                            }
                        }
                    };
                }
                return new NullVisitor();
            }

            public void visitAttribute(Attribute attr) {
                attrs.add(attr);
            }

            public void visitEnd() {
                FieldDescriptor fd = new FieldDescriptor(access, name, desc, 
                                    signature, value, attrs,
                                    isThreadLocal, isExport, isProperty,
                                    propName, propDescription);
                fields.add(fd);
                if (isThreadLocal) {         
                    threadLocalFields.put(name, fd);
                } else if (isExport) {
                    exportFields.put(name, fd);
                }
            }
        };
    }
    
    public void visitEnd() {
        if (! classInitializerFound) {
            // add a dummy <clinit> method that just returns.
            MethodVisitor clinit = visitMethod(ACC_STATIC|ACC_PUBLIC,
                               CLASS_INITIALIZER, "()V", null, null);
            clinit.visitCode();
            clinit.visitInsn(RETURN);
            clinit.visitMaxs(0, 0);
            clinit.visitEnd();
        }
        addFields();
        super.visitEnd();
    }

    private void addFields() {
        for (FieldDescriptor fd : fields) {
            String fieldName = fd.name;
            if (fd.isExport) {
                // no need to add a field for exported fields
                continue;
            }
            int fieldAccess = fd.access;
            String fieldDesc = fd.desc;
            String fieldSignature = fd.signature;
            Object fieldValue = fd.value;
            if (fd.isThreadLocal) {
                fieldAccess &= ~ACC_FINAL;
                fieldDesc = JAVA_LANG_THREAD_LOCAL_DESC;
                fieldSignature = null;
                fieldValue = null;
            }

            fieldAccess &= ~ACC_PRIVATE;
            fieldAccess &= ~ACC_PROTECTED;
            fieldAccess |= ACC_PUBLIC;

            FieldVisitor fv = super.visitField(fieldAccess,  
                                 BTRACE_FIELD_PREFIX + fieldName,
                                 fieldDesc, fieldSignature, fieldValue);
            if (fd.isProperty) {
                AnnotationVisitor av = fv.visitAnnotation(BTRACE_PROPERTY_DESC, true);
                if (av != null) {
                    av.visit(BTRACE_PROPERTY_NAME, fd.propertyName);
                    av.visit(BTRACE_PROPERTY_DESCRIPTION, fd.propertyDescription);
                }
            }
            for (Attribute attr : fd.attributes) {
                fv.visitAttribute(attr);
            }
            fv.visitEnd();
        }

        // add a special field to store client's BTraceRuntime
        super.visitField(ACC_PUBLIC|ACC_STATIC, BTRACE_RUNTIME_FIELD_NAME,
                   BTRACE_RUNTIME_DESC, null, null);        
    }

    public MethodVisitor visitMethod(int access, String name, 
            String desc, String signature, String[] exceptions) {
        if (name.equals(CONSTRUCTOR)) {
            return super.visitMethod(access, name, desc, signature, exceptions);
        } else {            
            /*
             * For each probe method, we generate epilog and prolog as shown
             * below:
             *
             *        if (! BTraceRuntime.enter()) {
             *            return;
             *        } else {
             *            try {
             *                // user's probe code here with this change:
             *                // bfore every return in user's code, call 
             *                // BTraceRuntime.leave();
             *            } catch (Throwable t) {
             *                BTraceRuntime.handleException(t);
             *            }
             *        }
             *
             */
            MethodVisitor adaptee = super.visitMethod(access, name, desc, 
                                                    signature, exceptions);
            final boolean isClassInitializer = name.equals(CLASS_INITIALIZER);
            classInitializerFound = isClassInitializer;
            return new MethodInstrumentor(adaptee, access, name, desc) {
                private Label start = new Label();
                private Label handler = new Label();
                private int nextVar = 0;

                private void generateExportGet(String name, String desc) {
                    int typeCode = desc.charAt(0);
                    switch (typeCode) {
                        case '[':
                            visitInsn(ACONST_NULL);
                            break;
                        case 'L':
                            if (desc.equals(JAVA_LANG_STRING_DESC)) {
                                visitLdcInsn(name);
                                visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_GET_PERFSTRING,
                                    BTRACE_RUNTIME_GET_PERFSTRING_DESC);
                            } else {
                                visitInsn(ACONST_NULL);
                            }
                            break;
                        case 'Z':
                        case 'C':
                        case 'B':
                        case 'S':
                        case 'I':
                            visitLdcInsn(name);
                            visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_GET_PERFINT,
                                    BTRACE_RUNTIME_GET_PERFINT_DESC);
                            break;
                        case 'J':
                            visitLdcInsn(name);
                            visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_GET_PERFLONG,
                                    BTRACE_RUNTIME_GET_PERFLONG_DESC);
                            break;
                        case 'F':
                            visitLdcInsn(name);
                            visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_GET_PERFFLOAT,
                                    BTRACE_RUNTIME_GET_PERFFLOAT_DESC);
                            break;
                        case 'D':
                            visitLdcInsn(name);
                            visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_GET_PERFDOUBLE,
                                    BTRACE_RUNTIME_GET_PERFDOUBLE_DESC);
                            break;
                    }
                }

                private void generateExportPut(String name, String desc) {
                    int typeCode = desc.charAt(0);
                    switch (typeCode) {
                        case '[':
                            visitInsn(POP);
                            break;
                        case 'L':
                            if (desc.equals(JAVA_LANG_STRING_DESC)) {
                                visitLdcInsn(name);
                                visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_PUT_PERFSTRING, 
                                    BTRACE_RUNTIME_PUT_PERFSTRING_DESC);
                            } else {
                                visitInsn(POP);
                            }
                            break;
                        case 'Z':
                        case 'C':
                        case 'B':
                        case 'S':
                        case 'I':
                            visitLdcInsn(name);
                            visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_PUT_PERFINT,
                                    BTRACE_RUNTIME_PUT_PERFINT_DESC);
                            break;
                        case 'J':
                            visitLdcInsn(name);
                            visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_PUT_PERFLONG,
                                    BTRACE_RUNTIME_PUT_PERFLONG_DESC);
                            break;
                        case 'F':
                            visitLdcInsn(name);
                            visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_PUT_PERFFLOAT,
                                    BTRACE_RUNTIME_PUT_PERFFLOAT_DESC);
                            break;
                        case 'D':
                            visitLdcInsn(name);
                            visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_PUT_PERFDOUBLE,
                                    BTRACE_RUNTIME_PUT_PERFDOUBLE_DESC);
                            break;
                    }
                }

                private void generateThreadLocalGet(FieldDescriptor fd) {
                    if (isClassInitializer) {
                        if (fd.initialized) {
                            super.visitVarInsn(Type.getType(fd.desc).getOpcode(Opcodes.ILOAD), fd.var);
                        } else if (fd.value != null) {
                            visitLdcInsn(fd.value);
                        } else {
                            defaultValue(fd.desc);
                        }
                    } else {
                        String fieldName = BTRACE_FIELD_PREFIX + fd.name;
                        super.visitFieldInsn(GETSTATIC, className, fieldName, JAVA_LANG_THREAD_LOCAL_DESC);
                        visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_THREAD_LOCAL,
                                     JAVA_LANG_THREAD_LOCAL_GET, JAVA_LANG_THREAD_LOCAL_GET_DESC);
                        unbox(fd.desc);
                    }
                }

                private void generateThreadLocalPut(FieldDescriptor fd) {
                    if (isClassInitializer) {
                        super.visitVarInsn(Type.getType(fd.desc).getOpcode(Opcodes.ISTORE), fd.var);
                        fd.initialized = true;
                    } else {
                        String fieldName = BTRACE_FIELD_PREFIX + fd.name;
                        box(fd.desc);
                        super.visitFieldInsn(GETSTATIC, className, fieldName, JAVA_LANG_THREAD_LOCAL_DESC);
                        visitInsn(SWAP);
                        visitMethodInsn(INVOKEVIRTUAL, JAVA_LANG_THREAD_LOCAL,
                                    JAVA_LANG_THREAD_LOCAL_SET, JAVA_LANG_THREAD_LOCAL_SET_DESC);
                    }
                }

                public void visitCode() {
                    visitTryCatchBlock(start, handler, handler,
                                    JAVA_LANG_THROWABLE);

                    if (isClassInitializer) {  
                         visitLdcInsn(classType);
                         visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_FOR_CLASS, 
                                    BTRACE_RUNTIME_FOR_CLASS_DESC);
                         super.visitFieldInsn(PUTSTATIC, className,
                                   BTRACE_RUNTIME_FIELD_NAME,
                                   BTRACE_RUNTIME_DESC);                      
                    }
                    visitFieldInsn(GETSTATIC, className,
                                   BTRACE_RUNTIME_FIELD_NAME,
                                   BTRACE_RUNTIME_DESC);
                    visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,                           
                                    BTRACE_RUNTIME_ENTER, 
                                    BTRACE_RUNTIME_ENTER_DESC);
                    if (isClassInitializer) {
                         for (FieldDescriptor fd : threadLocalFields.values()) {
                             fd.var = nextVar;
                             nextVar += Type.getType(fd.desc).getSize();
                         }
                         for (FieldDescriptor fd : exportFields.values()) {
                             visitLdcInsn(perfCounterName(fd.name));
                             visitLdcInsn(fd.desc);
                             if (fd.value == null) {
                                 visitInsn(ACONST_NULL);
                             } else {
                                 visitLdcInsn(fd.value);
                                 box(fd.desc);
                             }
                             visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_NEW_PERFCOUNTER, 
                                    BTRACE_RUNTIME_NEW_PERFCOUNTER_DESC);                             
                         }
                    }

                    visitJumpInsn(IFNE, start);
                    super.visitInsn(RETURN);
                    visitLabel(start);
                    super.visitCode();
                }

                public void visitFieldInsn(int opcode, String owner, 
                                               String name, String desc) {
                    String fieldName = name;
                    if (owner.equals(className)) {   
                        if (exportFields.get(name) != null) {
                            if (opcode == GETSTATIC) {
                                generateExportGet(perfCounterName(name), desc);
                            } else {
                                generateExportPut(perfCounterName(name), desc);
                            }
                            return;                       
                        }

                        if (! name.equals(BTRACE_RUNTIME_FIELD_NAME)) {
                            fieldName = BTRACE_FIELD_PREFIX + name; 
                        }

                        FieldDescriptor fd = threadLocalFields.get(name);
                        if (fd != null) {
                            if (opcode == GETSTATIC) {                                
                                generateThreadLocalGet(fd);
                            } else {
                                generateThreadLocalPut(fd);
                            }
                            return;
                        } // else fall through
                    } // else fall through
                    super.visitFieldInsn(opcode, owner, fieldName, desc);
                }

                public void visitVarInsn(int opcode, int var) {
                    super.visitVarInsn(opcode, var + nextVar);
                }

                public void visitInsn(int opcode) {
                    if (opcode == RETURN) {
                        if (isClassInitializer) {
                            for (FieldDescriptor fd : threadLocalFields.values()) {
                                generateThreadLocalGet(fd); // generates var load here
                                box(fd.desc);
                                visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                        BTRACE_RUNTIME_NEW_THREAD_LOCAL,
                                        BTRACE_RUNTIME_NEW_THREAD_LOCAL_DESC);
                                super.visitFieldInsn(PUTSTATIC, className,
                                        BTRACE_FIELD_PREFIX + fd.name,
                                        JAVA_LANG_THREAD_LOCAL_DESC);
                            }
                            visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,                      
                                    BTRACE_RUNTIME_START, 
                                    BTRACE_RUNTIME_START_DESC);
                        } else {
                            visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_LEAVE, 
                                    BTRACE_RUNTIME_LEAVE_DESC);
                        }
                    }
                    super.visitInsn(opcode);        
                }

                public void visitMaxs(int maxStack, int maxLocals) {
                    visitLabel(handler);
                    visitMethodInsn(INVOKESTATIC, BTRACE_RUNTIME,
                                    BTRACE_RUNTIME_HANDLE_EXCEPTION,
                                    BTRACE_RUNTIME_HANDLE_EXCEPTION_DESC);
                    super.visitInsn(RETURN);
                    super.visitMaxs(maxStack, maxLocals);
                }
            };
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args.length > 2) {
            System.err.println("Usage: java com.sun.btrace.runtime.Preprocessor <class> [<new-class-name>]");
            System.exit(1);
        }

        boolean renamed = (args.length == 2);
        String className = args[0].replace('.', '/');
        String newName = className;
        if (renamed) {
            newName = args[1].replace('.', '/');
        }
        FileInputStream fis = new FileInputStream(className + ".class");
        ClassReader reader = new ClassReader(new BufferedInputStream(fis));
        FileOutputStream fos = new FileOutputStream(newName + ".class");
        ClassWriter writer = InstrumentUtils.newClassWriter();  
        ClassVisitor cv;
        if (renamed) {
            cv = new ClassRenamer(args[1], new Preprocessor(writer));
        } else {
            cv = new Preprocessor(writer);
        }
        InstrumentUtils.accept(reader, cv);
        fos.write(writer.toByteArray());
    } 
}
