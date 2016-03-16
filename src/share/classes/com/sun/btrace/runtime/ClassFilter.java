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
import com.sun.btrace.org.objectweb.asm.AnnotationVisitor;
import com.sun.btrace.org.objectweb.asm.Attribute;
import com.sun.btrace.org.objectweb.asm.ClassReader;
import com.sun.btrace.org.objectweb.asm.FieldVisitor;
import com.sun.btrace.org.objectweb.asm.MethodVisitor;
import com.sun.btrace.annotations.BTrace;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
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

    private Set<String> sourceClasses;
    private Pattern[] sourceClassPatterns;
    private String[] annotationClasses;
    private Pattern[] annotationClassPatterns;
    // +foo type class pattern in any @OnMethod.
    private String[] superTypes;
    // same as above but stored in internal name form ('/' instead of '.')
    private String[] superTypesInternal;

    private final List<OnMethod> onMethods;

    static {
        ClassReader.class.getClass();
        AnnotationVisitor.class.getClass();
        FieldVisitor.class.getClass();
        MethodVisitor.class.getClass();
        Attribute.class.getClass();
    }

    public ClassFilter(List<OnMethod> onMethods) {
        this.onMethods = new ArrayList<>(onMethods);
        init();
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
        if (isNameMatching(className)) {
            return true;
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

    Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
        final Collection<OnMethod> applicables = new ArrayList<>(onMethods.size());
        final String targetName = cr.getClassName().replace('/', '.');

        outer:
        for(OnMethod om : onMethods) {
            String probeClass = om.getClazz();
            if (probeClass == null || probeClass.isEmpty()) continue;

            if (probeClass.equals(targetName)) {
                applicables.add(om);
                continue;
            }
            // Check regex match
            if (om.isClassRegexMatcher() && !om.isClassAnnotationMatcher()) {
                if (Pattern.matches(probeClass, targetName)) {
                    applicables.add(om);
                    continue;
                }
            }
            if (om.isClassAnnotationMatcher()) {
                Collection<String> annoTypes = cr.getAnnotationTypes();
                if (om.isClassRegexMatcher()) {
                    Pattern annoCheck = Pattern.compile(probeClass);
                    for(String annoType : annoTypes) {
                        if (annoCheck.matcher(annoType).matches()) {
                            applicables.add(om);
                            continue outer;
                        }
                    }
                } else {
                    if (annoTypes.contains(probeClass)) {
                        applicables.add(om);
                        continue;
                    }
                }
            }
            // And, finally, check the class hierarchy
            if (om.isSubtypeMatcher()) {
                // internal name of super type.
                if (isSubTypeOf(cr.getClassName(), cr.getClassLoader(), probeClass)) {
                    applicables.add(om);
                }
            }
        }
        return applicables;
    }

    public boolean isNameMatching(String clzName) {
        if (isSensitiveClass(clzName)) {
            // do not instrument the sensitive classes
            return false;
        }

        if (sourceClasses.contains(clzName))  {
            return true;
        }

        for (Pattern pat : sourceClassPatterns) {
            if (pat.matcher(clzName).matches()) {
                return true;
            }
        }
        return false;
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

    /**
     * Return whether given Class <i>typeA</i> is subtype of any of the
     * given type names.
     * @param typeA the type to check
     * @param loader the classloader for loading the type (my be null)
     * @param types any requested supertypes
     **/
    public static boolean isSubTypeOf(String typeA, ClassLoader loader, String ... types) {
        if (typeA == null) {
            return false;
        }
        if (types.length == 0) {
            return false;
        }

        boolean internal = types[0].contains("/");

        loader = (loader != null ? loader : ClassLoader.getSystemClassLoader());

        Set<String> typeSet = new HashSet<>(Arrays.asList(types));
        if (typeSet.contains(typeA)) {
            return true;
        }

        LinkedHashSet<String> closure = new LinkedHashSet<>();
        InstrumentUtils.collectHierarchyClosure(loader, typeA, closure, internal);

        closure.retainAll(typeSet);
        return !closure.isEmpty();
    }

    /*
     * Certain classes like java.lang.ThreadLocal and it's
     * inner classes, java.lang.Object cannot be safely
     * instrumented with BTrace. This is because BTrace uses
     * ThreadLocal class to check recursive entries due to
     * BTrace's own functions. But this leads to infinite recursions
     * if BTrace instruments java.lang.ThreadLocal for example.
     * For now, we avoid such classes till we find a solution.
     */
    private static boolean isSensitiveClass(String name) {
        return name.equals("java.lang.Object") || // NOI18N
               name.startsWith("java.lang.ThreadLocal") || // NOI18N
               name.startsWith("sun.reflect") || // NOI18N
               name.equals("sun.misc.Unsafe")  || // NOI18N
               name.startsWith("sun.security/") || // NOI18N
               name.equals("java.lang.VerifyError") || // NOI18N
               name.startsWith("sun.instrument.") || // NOI18N
               name.startsWith("java.lang.instrument."); // NOI18N
    }

    private void init() {
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
            if (om.isClassRegexMatcher()) {
                try {
                    Pattern p = Pattern.compile(className);
                    if (om.isClassAnnotationMatcher()) {
                        patAnoList.add(p);
                    } else {
                        patSrcList.add(p);
                    }
                } catch (PatternSyntaxException pse) {
                    System.err.println("btrace ERROR: invalid regex pattern - " + className.substring(1, className.length() - 1));
                }
            } else if (om.isClassAnnotationMatcher()) {
                strAnoList.add(className);
            } else if (om.isSubtypeMatcher()) {
                superTypesList.add(className);
                superTypesInternalList.add(className.replace('.', '/'));
            } else {
                strSrcList.add(className);
            }
        }

        sourceClasses = new HashSet(strSrcList.size());
        sourceClasses.addAll(strSrcList);
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
