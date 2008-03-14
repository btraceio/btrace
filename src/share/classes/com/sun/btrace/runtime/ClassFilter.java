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
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static com.sun.btrace.runtime.Constants.*;
import com.sun.btrace.runtime.OnMethod;
import com.sun.btrace.util.NullVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

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

    public ClassFilter(List<OnMethod> onMethods) {
        init(onMethods);
    }

    public boolean isCandidate(Class target) {
        if (target.isInterface() || target.isPrimitive() || target.isArray()) {
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
            for (int i = 0; i < annoTypes.length; i++)  {
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

    private class CheckingVisitor implements ClassVisitor {
        private boolean isInterface;
        private boolean isCandidate;
        private NullVisitor nullVisitor = new NullVisitor();
        boolean isCandidate() {
            return isCandidate;
        }

        public void visit(int version, int access, String name, 
            String signature, String superName, String[] interfaces) {
            if ((access & ACC_INTERFACE) != 0)  {
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
        }

        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (isInterface) {
                return nullVisitor;
            }

            if (!isCandidate) {
                String annoName = Type.getType(desc).getClassName();
                for (String name: annotationClasses) {
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
                if (REGEX_SPECIFIER.matcher(className ).matches()) {
                    Pattern p = Pattern.compile(
                        className.substring(1, className.length() - 1));
                    patAnoList.add(p);
                } else {
                    strAnoList.add(className);
                }                        
            } else {
                strSrcList.add(className);
            }     
        }
     
        sourceClasses = new String[strSrcList.size()];
        strSrcList.toArray(sourceClasses);
        sourceClassPatterns = new Pattern[patSrcList.size()];
        patSrcList.toArray(sourceClassPatterns);

        annotationClasses = new String[strAnoList.size()];
        strAnoList.toArray(annotationClasses);
        annotationClassPatterns = new Pattern[patAnoList.size()];
        patAnoList.toArray(annotationClassPatterns);
    }
}
