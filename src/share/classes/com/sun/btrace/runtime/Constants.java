/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.regex.Pattern;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Location;
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.OnProbe;
import com.sun.btrace.annotations.Where;
import com.sun.btrace.AnyType;
import com.sun.btrace.BTraceUtils;
import com.sun.btrace.annotations.Injected;
import com.sun.btrace.annotations.Level;
import com.sun.btrace.annotations.OnError;
import com.sun.btrace.annotations.OnEvent;
import com.sun.btrace.annotations.OnExit;
import com.sun.btrace.annotations.OnTimer;
import com.sun.btrace.annotations.ProbeClassName;
import com.sun.btrace.annotations.ProbeMethodName;
import com.sun.btrace.annotations.Sampled;
import com.sun.btrace.org.objectweb.asm.Type;
import com.sun.btrace.services.api.Service;

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

    public static final Type OBJECT_TYPE = Type.getType(Object.class);
    public static final String OBJECT_DESC = OBJECT_TYPE.getDescriptor();

    public static final Type ANYTYPE_TYPE = Type.getType(AnyType.class);
    public static final String ANYTYPE = ANYTYPE_TYPE.getInternalName();
    public static final String ANYTYPE_DESC = ANYTYPE_TYPE.getDescriptor();

    public static final Type STRING_TYPE = Type.getType(String.class);

    // BTrace specific stuff
    public static final String BTRACE_UTILS =
        Type.getInternalName(BTraceUtils.class);

    public static final String SERVICE =
        Type.getInternalName(Service.class);

    public static final String BTRACE_DESC =
        Type.getDescriptor(BTrace.class);

    public static final String ONMETHOD_DESC =
        Type.getDescriptor(OnMethod.class);

    public static final String BTRACE_PROBECLASSNAME_DESC =
        Type.getDescriptor(ProbeClassName.class);

    public static final String BTRACE_PROBEMETHODNAME_DESC =
        Type.getDescriptor(ProbeMethodName.class);

    public static final String ONTIMER_DESC =
        Type.getDescriptor(OnTimer.class);
    public static final String ONEVENT_DESC =
        Type.getDescriptor(OnEvent.class);
    public static final String ONEXIT_DESC =
        Type.getDescriptor(OnExit.class);
    public static final String ONERROR_DESC =
        Type.getDescriptor(OnError.class);

    public static final String SAMPLER_DESC =
        Type.getDescriptor(Sampled.class);

    public static final String ONPROBE_DESC =
        Type.getDescriptor(OnProbe.class);

    public static final String LOCATION_DESC =
        Type.getDescriptor(Location.class);

    public static final String LEVEL_DESC =
        Type.getDescriptor(Level.class);

    public static final String WHERE_DESC =
        Type.getDescriptor(Where.class);

    public static final String KIND_DESC =
        Type.getDescriptor(Kind.class);

    public static final String INJECTED_DESC =
        Type.getDescriptor(Injected.class);

    // class name pattern is specified with this pattern
    public static final Pattern REGEX_SPECIFIER = Pattern.compile("/.+/");

    public static final String JAVA_LANG_THREAD_LOCAL =
        Type.getInternalName(ThreadLocal.class);
    public static final String JAVA_LANG_THREAD_LOCAL_GET = "get";
    public static final String JAVA_LANG_THREAD_LOCAL_GET_DESC = "()Ljava/lang/Object;";
    public static final String JAVA_LANG_THREAD_LOCAL_SET = "set";
    public static final String JAVA_LANG_THREAD_LOCAL_SET_DESC = "(Ljava/lang/Object;)V";

    public static final String JAVA_LANG_STRING = STRING_TYPE.getInternalName();
    public static final String JAVA_LANG_STRING_DESC = STRING_TYPE.getDescriptor();

    public static final String JAVA_LANG_NUMBER =
        Type.getInternalName(Number.class);
    public static final String JAVA_LANG_BOOLEAN =
        Type.getInternalName(Boolean.class);
    public static final String JAVA_LANG_CHARACTER =
        Type.getInternalName(Character.class);
    public static final String JAVA_LANG_BYTE =
        Type.getInternalName(Byte.class);
    public static final String JAVA_LANG_SHORT =
        Type.getInternalName(Short.class);
    public static final String JAVA_LANG_INTEGER =
        Type.getInternalName(Integer.class);
    public static final String JAVA_LANG_LONG =
        Type.getInternalName(Long.class);
    public static final String JAVA_LANG_FLOAT =
        Type.getInternalName(Float.class);
    public static final String JAVA_LANG_DOUBLE =
        Type.getInternalName(Double.class);

    public static final String BOX_VALUEOF = "valueOf";
    public static final String BOX_BOOLEAN_DESC = "(Z)Ljava/lang/Boolean;";
    public static final String BOX_CHARACTER_DESC = "(C)Ljava/lang/Character;";
    public static final String BOX_BYTE_DESC = "(B)Ljava/lang/Byte;";
    public static final String BOX_SHORT_DESC = "(S)Ljava/lang/Short;";
    public static final String BOX_INTEGER_DESC = "(I)Ljava/lang/Integer;";
    public static final String BOX_LONG_DESC = "(J)Ljava/lang/Long;";
    public static final String BOX_FLOAT_DESC = "(F)Ljava/lang/Float;";
    public static final String BOX_DOUBLE_DESC = "(D)Ljava/lang/Double;";

    public static final String BOOLEAN_VALUE = "booleanValue";
    public static final String CHAR_VALUE = "charValue";
    public static final String BYTE_VALUE = "byteValue";
    public static final String SHORT_VALUE = "shortValue";
    public static final String INT_VALUE = "intValue";
    public static final String LONG_VALUE = "longValue";
    public static final String FLOAT_VALUE = "floatValue";
    public static final String DOUBLE_VALUE = "doubleValue";

    public static final String BOOLEAN_VALUE_DESC= "()Z";
    public static final String CHAR_VALUE_DESC= "()C";
    public static final String BYTE_VALUE_DESC= "()B";
    public static final String SHORT_VALUE_DESC= "()S";
    public static final String INT_VALUE_DESC= "()I";
    public static final String LONG_VALUE_DESC= "()J";
    public static final String FLOAT_VALUE_DESC= "()F";
    public static final String DOUBLE_VALUE_DESC= "()D";
}
