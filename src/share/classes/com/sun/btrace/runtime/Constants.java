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

import java.util.regex.Pattern;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Location;
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.OnProbe;
import com.sun.btrace.annotations.Where;
import com.sun.btrace.BTraceUtils;
import com.sun.btrace.AnyType;
import com.sun.btrace.org.objectweb.asm.Type;

/**
 * Constants shared by few classes.
 *
 * @author A. Sundararajan
 */
public abstract class Constants { 
    public static final String BTRACE_METHOD_PREFIX =
        "$btrace$";

    public static final String JAVA_LANG_OBJECT = 
        Type.getInternalName(Object.class);
    public static final String JAVA_LANG_THROWABLE =
        Type.getInternalName(Throwable.class);
    public static final String CONSTRUCTOR = "<init>";
    public static final String CLASS_INITIALIZER = "<clinit>";

    public static final String OBJECT_DESC = Type.getDescriptor(Object.class);
    public static final String ANYTYPE_DESC = Type.getDescriptor(AnyType.class);


    // BTrace specific stuff
    public static final String BTRACE_UTILS =
        Type.getInternalName(BTraceUtils.class);
    public static final String BTRACE_DESC =
        Type.getDescriptor(BTrace.class);

    public static final String ONMETHOD_DESC =
        Type.getDescriptor(OnMethod.class);

    public static final String ONPROBE_DESC =
        Type.getDescriptor(OnProbe.class);

    public static final String LOCATION_DESC =
        Type.getDescriptor(Location.class);

    public static final String WHERE_DESC =
        Type.getDescriptor(Where.class);

    public static final String KIND_DESC =
        Type.getDescriptor(Kind.class);

    // class name pattern is specified with this pattern
    public static final Pattern REGEX_SPECIFIER = Pattern.compile("/.+/");
}
