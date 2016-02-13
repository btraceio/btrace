/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import static com.sun.btrace.org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static com.sun.btrace.runtime.Constants.*;
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.Attribute;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.ClassVisitor;
import com.sun.btrace.org.objectweb.asm.FieldVisitor;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.org.objectweb.asm.Opcodes;
import java.lang.ref.Reference;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.regex.PatternSyntaxException;

/**
 * This class checks whether a given target class
 * matches at least one probe specified in a BTrace
 * class.
 *
 * @author A. Sundararajan
 */
public class ClassFilter {
    private static final Class<?> referenceClz = Reference.class;

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

        if (referenceClz.equals(target)) {
            // instrumenting the <b>java.lang.ref.Reference</b> class will lead
            // to StackOverflowError in <b>java.lang.ThreadLocal.get()</b>
            return false;
        }

        if (target.getName().startsWith("sun.instrument.") ||
            target.getName().startsWith("java.lang.instrument.")) {
            // do not instrument the instrumentation related classes
            return false;
        }

        try {
            // ignore classes annotated with @BTrace -
            // We don't want to instrument tracing classes!
            if (target.getAnnotation(BTrace.class) != null) {
                return false;
            }
        } catch (Throwable t) {
            // thrown from java.lang.Class.initAnnotationsIfNecessary()
            // seems to be a case when trying to access non-existing annotations
            // on a superclass
            // * messed up situation - ignore the class *
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
            for (String annoType : annoTypes) {
                if (name.equals(annoType)) {
                    return true;
                }
            }
        }

        for (Pattern pat : annotationClassPatterns) {
            for (String annoType : annoTypes) {
                if (pat.matcher(annoType).matches()) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isCandidate(ClassLoader loader, byte[] classBytes, boolean subClassChecks) {
        return isCandidate(loader, new ClassReader(classBytes), subClassChecks);
    }

    public boolean isCandidate(ClassLoader loader, ClassReader reader, boolean subClassChecks) {
        CheckingVisitor cv = new CheckingVisitor(loader, subClassChecks);
        InstrumentUtils.accept(reader, cv);
        return cv.isCandidate();
    }

    /*
     * return whether given Class is subtype of given type name
     * Note that we can not use Class.isAssignableFrom because the other
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

    private class CheckingVisitor extends ClassVisitor {

        private boolean isInterface;
        private boolean isCandidate;
        private final ClassLoader loader;
        private final boolean subClassChecks;
        private final AnnotationVisitor nullAnnotationVisitor = new AnnotationVisitor(Opcodes.ASM5) {};

        public CheckingVisitor(ClassLoader loader, boolean subClassChecks) {
            super(Opcodes.ASM5);
            this.loader = loader != null ? loader : ClassLoader.getSystemClassLoader();
            this.subClassChecks = subClassChecks;
        }

        boolean isCandidate() {
            return isCandidate;
        }

        @Override
        public void visit(int version, int access, String name,
                String signature, String superName, String[] interfaces) {
            if ((access & ACC_INTERFACE) != 0) {
                isInterface = true;
                isCandidate = false;
                return;
            }

            if (subClassChecks) {
                Collection<String> closure = new LinkedList<>();
                InstrumentUtils.collectHierarchyClosure(loader, name, (List)closure);

                // bulgarian constant for converting list to set to improve search efficiency
                if (closure.size() > 20) {
                    closure = new HashSet<>(closure);
                }
                for(String st : ClassFilter.this.superTypesInternal) {
                    if (closure.contains(st)) {
                        isCandidate = true;
                        return;
                    }
                }
            }

            name = name.replace('/', '.');

            if (referenceClz.getName().equals(name)) {
                // instrumenting the <b>java.lang.ref.Reference</b> class will lead
                // to StackOverflowError in <b>java.lang.ThreadLocal.get()</b>
                return;
            }

            if (name.startsWith("sun.instrument.") ||
                name.startsWith("java.lang.instrument.")) {
                // do not instrument the instrumentation related classes
                return;
            }

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

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (isInterface) {
                return nullAnnotationVisitor;
            }

            if (BTRACE_DESC.equals(desc)) {
                // ignore classes annotated with @BTrace -
                // we don't want to instrument tracing classes!
                isCandidate = false;
                return nullAnnotationVisitor;
            }

            if (!isCandidate) {
                String annoName = Type.getType(desc).getClassName();
                for (String name : annotationClasses) {
                    if (annoName.equals(name)) {
                        isCandidate = true;
                        return nullAnnotationVisitor;
                    }
                }
                for (Pattern pat : annotationClassPatterns) {
                    if (pat.matcher(annoName).matches()) {
                        isCandidate = true;
                        return nullAnnotationVisitor;
                    }
                }
            }

            return nullAnnotationVisitor;
        }

        @Override
        public void visitAttribute(Attribute attr) {
        }

        @Override
        public void visitEnd() {
        }

        @Override
        public FieldVisitor visitField(int access, String name,
                String desc, String signature, Object value) {
            return null;
        }

        @Override
        public void visitInnerClass(String name, String outerName,
                String innerName, int access) {
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                String signature, String[] exceptions) {
            return null;
        }

        @Override
        public void visitOuterClass(String owner, String name, String desc) {
        }

        @Override
        public void visitSource(String source, String debug) {
        }
    }

    private void init(List<OnMethod> onMethods) {
        List<String> strSrcList = new ArrayList<>();
        List<Pattern> patSrcList = new ArrayList<>();
        List<String> superTypesList = new ArrayList<>();
        List<String> superTypesInternalList = new ArrayList<>();
        List<String> strAnoList = new ArrayList<>();
        List<Pattern> patAnoList = new ArrayList<>();

        for (OnMethod om : onMethods) {
            String className = om.getClazz();
            if (className.length() == 0) {
                continue;
            }
            char firstCh = className.charAt(0);
            if (firstCh == '/' &&
                    REGEX_SPECIFIER.matcher(className).matches()) {
                try {
                    Pattern p = Pattern.compile(className.substring(1,
                            className.length() - 1));
                    patSrcList.add(p);
                } catch (PatternSyntaxException pse) {
                    System.err.println("btrace ERROR: invalid regex pattern - " + className.substring(1, className.length() - 1));
                }
            } else if (firstCh == '@') {
                className = className.substring(1);
                if (REGEX_SPECIFIER.matcher(className).matches()) {
                    try {
                        Pattern p = Pattern.compile(
                                className.substring(1, className.length() - 1));
                        patAnoList.add(p);
                    } catch (PatternSyntaxException pse) {
                        System.err.println("btrace ERROR: invalid regex pattern - " + className.substring(1, className.length() - 1));
                    }
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
