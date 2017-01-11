/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.btrace.DebugSupport;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;

/**
 * Arbitrary class info type allowing access to supertype information
 * also for not-already-loaded classes.
 *
 * @author Jaroslav Bachorik
 */
public final class ClassInfo {
    private static abstract class BaseClassName implements CharSequence {
        protected final CharSequence wrapped;
        private String str = null;

        protected BaseClassName(CharSequence wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int length() {
            return wrapped.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            if (str == null) {
                char[] val = new char[wrapped.length()];
                for (int i = 0; i < wrapped.length(); i++) {
                    val[i] = charAt(i);
                }
                str = new String(val);
            }
            return str;
        }
    }

    private static final class JavaClassName extends BaseClassName {
        public JavaClassName(CharSequence wrapped) {
            super(wrapped);
        }

        @Override
        public char charAt(int index) {
            char c = wrapped.charAt(index);
            return (c == '/' ? '.' : c);
        }
    }

    private static final class InternalClassName extends BaseClassName {
        public InternalClassName(CharSequence wrapped) {
            super(wrapped);
        }

        @Override
        public char charAt(int index) {
            char c = wrapped.charAt(index);
            return (c == '.' ? '/' : c);
        }
    }

    static final class ClassName {
        private final CharSequence cName;
        private final JavaClassName jcName;
        private final InternalClassName icName;
        private String rsrcName = null;

        public ClassName(CharSequence cName) {
            this.cName = cName;
            this.jcName = new JavaClassName(cName);
            this.icName = new InternalClassName(cName);
        }

        public CharSequence getJavaClassName() {
            return jcName;
        }

        public CharSequence getInternalClassName() {
            return icName;
        }

        public String getResourcePath() {
            if (rsrcName == null) {
                rsrcName = new StringBuilder(icName).append(".class").toString();
            }
            return rsrcName;
        }

        @Override
        public String toString() {
            return new StringBuilder(cName).toString();
        }

        @Override
        public int hashCode() {
            int h = 7;
            int len = cName.length();
            for (int i = 0; i < len; i++) {
                char c = cName.charAt(i);
                h = 31 * h + (c == '.' ? '/' : c);
            }

            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ClassName other = (ClassName) obj;
            if (cName.length() != other.cName.length()) {
                return false;
            }
            for (int i = 0; i < cName.length(); i++) {
                char c1 = cName.charAt(i);
                char c2 = other.cName.charAt(i);
                switch (c1) {
                    case '.': {
                        if (c2 != '.' && c2 != '/') {
                            return false;
                        }
                        break;
                    }
                    case '/': {
                        if (c2 != '.' && c2 != '/') {
                            return false;
                        }
                        break;
                    }
                    default: {
                        if (c1 != c2) {
                            return false;
                        }
                    }
                }
                return true;
            }
            return true;
        }
    }

    private static volatile Method BSTRP_CHECK_MTD;
    private static final ClassLoader SYS_CL = ClassLoader.getSystemClassLoader();
    private final String cLoaderId;
    private final ClassName classId;
    private final Collection<ClassInfo> supertypes = new LinkedList<>();
    private final ClassCache cache;
    private boolean isInterface = false;

    ClassInfo(ClassCache cache, Class clz) {
        this.cache = cache;
        ClassLoader cl = clz.getClassLoader();
        cLoaderId = (cl != null ? cl.toString() : "<null>");
        classId = new ClassName(clz.getName());
        Class supr = clz.getSuperclass();
        if (supr != null) {
            supertypes.add(cache.get(supr));
        }
        for (Class itfc : clz.getInterfaces()) {
            if (itfc != null) {
                supertypes.add(cache.get(itfc));
            }
        }
        this.isInterface = clz.isInterface();
    }

    ClassInfo(ClassCache cache, ClassLoader cl, ClassName cName) {
        this.cache = cache;
        cLoaderId = (cl != null ? cl.toString() : "<null>");
        classId = cName;
        loadExternalClass(cl, cName);
    }

    /**
     * Retrieves supertypes (including interfaces)
     * @param onlyDirect only immediate supertype and implemented interfaces
     * @return supertypes (including interfaces)
     */
    public Collection<ClassInfo> getSupertypes(boolean onlyDirect) {
        if (onlyDirect) {
            return supertypes;
        }
        Set<ClassInfo> supers = new LinkedHashSet<>();
        supers.addAll(supertypes);
        for (ClassInfo ci : supertypes) {
            supers.addAll(ci.getSupertypes(onlyDirect));
        }
        return supers;
    }

    /**
     * Associated class loader string representation as returned by {@code cl.toString()} or {@code "<null>"}
     * @return associated class loader id
     */
    public String getLoaderId() {
        return cLoaderId;
    }

    /**
     * Class ID = internal class name
     * @return internal class name
     */
    public String getClassName() {
        return classId.getInternalClassName().toString();
    }

    public String getJavaClassName() {
        return classId.getJavaClassName().toString();
    }

    public boolean isInterface() {
        return isInterface;
    }

    private void loadExternalClass(final ClassLoader cl, final ClassName className) {
        String resourcePath = className.getResourcePath();

        InputStream typeIs = cl == null ? SYS_CL.getResourceAsStream(resourcePath) : cl.getResourceAsStream(resourcePath);
        if (typeIs != null) {
            try {
                BTraceClassReader cr = new BTraceClassReader(cl, typeIs);

                this.isInterface = cr.isInterface();
                String[] info = cr.readClassSupers();
                String superName = info[0];
                if (superName != null) {
                    ClassName superClassName = new ClassName(superName);
                    supertypes.add(cache.get(inferClassLoader(cl, superClassName), superClassName));
                }
                if (info.length > 1) {
                    for(int i = 1; i < info.length; i++) {
                        String ifc = info[i];
                        if (ifc != null) {
                            ClassName ifcClassName = new ClassName(ifc);
                            supertypes.add(cache.get(inferClassLoader(cl, ifcClassName), ifcClassName));
                        }
                    }
                }
            } catch (IOException e) {
                DebugSupport.warning(e);
            }
        }
    }

    private static ClassLoader inferClassLoader(ClassLoader initiating, ClassName className) {
        if (className == null) {
            return initiating;
        }

        String jClassName = className.getJavaClassName().toString();
        if (initiating == null || isBootstrap(jClassName)) {
            return null;
        } else {
            String rsrcName = className.getResourcePath();
            ClassLoader cl = initiating;
            ClassLoader prev = initiating;
            while (cl != null) {
                if (cl.getResource(rsrcName) == null) {
                    return prev;
                }
                prev = cl;
                cl = cl.getParent();
            }
            return initiating;
        }
    }

    private static boolean isBootstrap(String className) {
        try {
            Method m = getCheckBootstrap();
            if (m != null) {
                return m.invoke(SYS_CL, className) != null;
            }
        } catch (Throwable t) {
            DebugSupport.warning(t);
        }
        return false;
    }

    private static Method getCheckBootstrap() {
        if (BSTRP_CHECK_MTD != null) {
            return BSTRP_CHECK_MTD;
        }
        Method m = null;
        try {
            m = ClassLoader.class.getDeclaredMethod("findBootstrapClassOrNull", String.class);
            m.setAccessible(true);
        } catch (Throwable t) {
            DebugSupport.warning(t);
        }
        BSTRP_CHECK_MTD = m;
        return BSTRP_CHECK_MTD;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + Objects.hashCode(this.cLoaderId);
        hash = 37 * hash + Objects.hashCode(this.classId);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ClassInfo other = (ClassInfo) obj;
        if (!Objects.equals(this.cLoaderId, other.cLoaderId)) {
            return false;
        }
        return Objects.equals(this.classId, other.classId);
    }

    @Override
    public String toString() {
        return "ClassInfo{" + "cLoaderId=" + cLoaderId + ", classId=" + classId + ", supertypes=" + supertypes + '}';
    }

}
