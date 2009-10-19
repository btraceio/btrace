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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import static com.sun.btrace.org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static com.sun.btrace.runtime.Constants.*;
import com.sun.btrace.runtime.OnMethod;
import com.sun.btrace.util.NullVisitor;
import java.lang.reflect.Method;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.Attribute;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.FieldVisitor;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.annotations.BTrace;

/**
 * This class checks whether a given target class
 * matches atleast one probe specified in a BTrace
 * class.
 *
 * @author A. Sundararajan
 */
public class ClassFilter {

    private String[] sourceClasses;
    private Pattern[] sourceClassPatterns;
    private String[] annotationClasses;
    private Pattern[] annotationClassPatterns;
    // +foo type class pattern in any @OnMethod.
    private String[] superTypes;
    // same as above but stored in internal name form ('/' instead of '.')
    private String[] superTypesInternal;

    static {
        CheckingVisitor.class.getClass();
        ClassReader.class.getClass();
        NullVisitor.class.getClass();
        AnnotationVisitor.class.getClass();
        FieldVisitor.class.getClass();
        MethodVisitor.class.getClass();
        Attribute.class.getClass();
    }

    public ClassFilter(List<OnMethod> onMethods) {
        init(onMethods);
    }

    public boolean isCandidate(Class target) {
        if (target.isInterface() || target.isPrimitive() || target.isArray()) {
            return false;
        }

        // ignore classes annotated with @BTrace -
        // We don't want to instrument tracing classes!
        if (target.getAnnotation(BTrace.class) != null) {
            return false;
        }

        String className = target.getName();
        for (String name : sourceClasses) {
            if (name.equals(className)) {
                return true;
            }
        }

        for (Pattern pat : sourceClassPatterns) {
            if (pat.matcher(className).matches()) {
                return true;
            }
        }

        for (String st : superTypes) {
            if (isSubTypeOf(target, st)) {
                return true;
            }
        }

        Annotation[] annotations = target.getAnnotations();
        String[] annoTypes = new String[annotations.length];
        for (int i = 0; i < annotations.length; i++) {
            annoTypes[i] = annotations[i].annotationType().getName();
        }

        for (String name : annotationClasses) {
            for (int i = 0; i < annoTypes.length; i++) {
                if (name.equals(annoTypes[i])) {
                    return true;
                }
            }
        }

        for (Pattern pat : annotationClassPatterns) {
            for (int i = 0; i < annoTypes.length; i++) {
                if (pat.matcher(annoTypes[i]).matches()) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isCandidate(byte[] classBytes) {
        return isCandidate(new ClassReader(classBytes));
    }

    public boolean isCandidate(ClassReader reader) {
        CheckingVisitor cv = new CheckingVisitor();
        InstrumentUtils.accept(reader, cv);
        return cv.isCandidate();
    }

    /*
     * return whether given Class is subtype of given type name
     * Note that we can not use Class.iaAssignableFrom because the other
     * type is specified by just name and not by Class object.
     */
    public static boolean isSubTypeOf(Class clazz, String typeName) {
        if (clazz == null) {
            return false;
        } else if (clazz.getName().equals(typeName)) {
            return true;
        } else {
            for (Class iface : clazz.getInterfaces()) {
                if (isSubTypeOf(iface, typeName)) {
                    return true;
                }
            }
            return isSubTypeOf(clazz.getSuperclass(), typeName);
        }
    }

    private class CheckingVisitor implements ClassVisitor {

        private boolean isInterface;
        private boolean isCandidate;
        private NullVisitor nullVisitor = new NullVisitor();

        boolean isCandidate() {
            return isCandidate;
        }

        public void visit(int version, int access, String name,
                String signature, String superName, String[] interfaces) {
            if ((access & ACC_INTERFACE) != 0) {
                isInterface = true;
                isCandidate = false;
                return;
            }
            name = name.replace('/', '.');
            for (String className : sourceClasses) {
                if (className.equals(name)) {
                    isCandidate = true;
                    return;
                }
            }

            for (Pattern pat : sourceClassPatterns) {
                if (pat.matcher(name).matches()) {
                    isCandidate = true;
                    return;
                }
            }

            for (String st : superTypesInternal) {
                if (superName.equals(st)) {
                    isCandidate = true;
                    return;
                }
                for (String iface : interfaces) {
                    if (iface.equals(st)) {
                        isCandidate = true;
                        return;
                    }
                }
            }
        }

        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (isInterface) {
                return nullVisitor;
            }

            if (BTRACE_DESC.equals(desc)) {
                // ignore classes annotated with @BTrace -
                // we don't want to instrument tracing classes!
                isCandidate = false;
                return nullVisitor;
            }

            if (!isCandidate) {
                String annoName = Type.getType(desc).getClassName();
                for (String name : annotationClasses) {
                    if (annoName.equals(name)) {
                        isCandidate = true;
                        return nullVisitor;
                    }
                }
                for (Pattern pat : annotationClassPatterns) {
                    if (pat.matcher(annoName).matches()) {
                        isCandidate = true;
                        return nullVisitor;
                    }
                }
            }

            return nullVisitor;
        }

        public void visitAttribute(Attribute attr) {
        }

        public void visitEnd() {
        }

        public FieldVisitor visitField(int access, String name,
                String desc, String signature, Object value) {
            return null;
        }

        public void visitInnerClass(String name, String outerName,
                String innerName, int access) {
        }

        public MethodVisitor visitMethod(int access, String name, String desc,
                String signature, String[] exceptions) {
            return null;
        }

        public void visitOuterClass(String owner, String name, String desc) {
        }

        public void visitSource(String source, String debug) {
        }
    }

    private void init(List<OnMethod> onMethods) {
        List<String> strSrcList = new ArrayList<String>();
        List<Pattern> patSrcList = new ArrayList<Pattern>();
        List<String> superTypesList = new ArrayList<String>();
        List<String> superTypesInternalList = new ArrayList<String>();
        List<String> strAnoList = new ArrayList<String>();
        List<Pattern> patAnoList = new ArrayList<Pattern>();

        for (OnMethod om : onMethods) {
            String className = om.getClazz();
            if (className.length() == 0) {
                continue;
            }
            char firstCh = className.charAt(0);
            if (firstCh == '/' &&
                    REGEX_SPECIFIER.matcher(className).matches()) {
                Pattern p = Pattern.compile(className.substring(1,
                        className.length() - 1));
                patSrcList.add(p);
            } else if (firstCh == '@') {
                className = className.substring(1);
                if (REGEX_SPECIFIER.matcher(className).matches()) {
                    Pattern p = Pattern.compile(
                            className.substring(1, className.length() - 1));
                    patAnoList.add(p);
                } else {
                    strAnoList.add(className);
                }
            } else if (firstCh == '+') {
                String superType = className.substring(1);
                superTypesList.add(superType);
                superTypesInternalList.add(superType.replace('.', '/'));
            } else {
                strSrcList.add(className);
            }
        }

        sourceClasses = new String[strSrcList.size()];
        strSrcList.toArray(sourceClasses);
        sourceClassPatterns = new Pattern[patSrcList.size()];
        patSrcList.toArray(sourceClassPatterns);
        superTypes = new String[superTypesList.size()];
        superTypesList.toArray(superTypes);
        superTypesInternal = new String[superTypesInternalList.size()];
        superTypesInternalList.toArray(superTypesInternal);
        annotationClasses = new String[strAnoList.size()];
        strAnoList.toArray(annotationClasses);
        annotationClassPatterns = new Pattern[patAnoList.size()];
        patAnoList.toArray(annotationClassPatterns);
    }
}
