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

package org.openjdk.btrace.instr;

import java.util.regex.Pattern;
import org.objectweb.asm.Type;
import org.openjdk.btrace.core.ArgsMap;
import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Duration;
import org.openjdk.btrace.core.annotations.Injected;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Level;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnError;
import org.openjdk.btrace.core.annotations.OnEvent;
import org.openjdk.btrace.core.annotations.OnExit;
import org.openjdk.btrace.core.annotations.OnLowMemory;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.OnProbe;
import org.openjdk.btrace.core.annotations.OnTimer;
import org.openjdk.btrace.core.annotations.PeriodicEvent;
import org.openjdk.btrace.core.annotations.ProbeClassName;
import org.openjdk.btrace.core.annotations.ProbeMethodName;
import org.openjdk.btrace.core.annotations.Return;
import org.openjdk.btrace.core.annotations.Sampled;
import org.openjdk.btrace.core.annotations.Self;
import org.openjdk.btrace.core.annotations.TargetInstance;
import org.openjdk.btrace.core.annotations.TargetMethodOrField;
import org.openjdk.btrace.core.annotations.Where;
import org.openjdk.btrace.core.jfr.JfrEvent;
import org.openjdk.btrace.runtime.LinkingFlag;
import org.openjdk.btrace.services.api.Service;

/**
 * Constants shared by few classes.
 *
 * @author A. Sundararajan
 */
public abstract class Constants {
  public static final String BTRACE_METHOD_PREFIX = "$btrace$";

  public static final String CONSTRUCTOR = "<init>";
  public static final String CLASS_INITIALIZER = "<clinit>";

  public static final Type NULL_TYPE = Type.getType("L$$null");
  public static final Type TOP_TYPE = Type.getType("L$$top");

  public static final Type VOIDREF_TYPE = Type.getType("Ljava/lang/Void;");

  public static final String OBJECT_INTERNAL = "java/lang/Object";
  public static final String OBJECT_DESC = "L" + OBJECT_INTERNAL + ";";
  public static final Type OBJECT_TYPE = Type.getType(OBJECT_DESC);

  public static final String ANYTYPE_INTERNAL = "org/openjdk/btrace/core/types/AnyType";
  public static final String ANYTYPE_DESC = "L" + ANYTYPE_INTERNAL + ";";
  public static final Type ANYTYPE_TYPE = Type.getType(ANYTYPE_DESC);

  public static final String CLASS_DESC = "Ljava/lang/Class;";
  public static final Type CLASS_TYPE = Type.getType(CLASS_DESC);

  public static final String STRING_INTERNAL = "java/lang/String";
  public static final String STRING_DESC = "L" + STRING_INTERNAL + ";";
  public static final Type STRING_TYPE = Type.getType(STRING_DESC);

  public static final String STRING_BUILDER_INTERNAL = "java/lang/StringBuilder";
  public static final String STRING_BUILDER_DESC = "L" + STRING_BUILDER_INTERNAL + ";";
  public static final Type STRING_BUILDER_TYPE = Type.getType(STRING_BUILDER_DESC);

  public static final String VOID_DESC = "V";
  public static final String BOOLEAN_DESC = "Z";
  public static final String INT_DESC = "I";

  public static final String THROWABLE_INTERNAL = "java/lang/Throwable";
  public static final String THROWABLE_DESC = "L" + THROWABLE_INTERNAL + ";";
  public static final Type THROWABLE_TYPE = Type.getType(THROWABLE_DESC);

  public static final String BTRACERTACCESS_INTERNAL =
      "org/openjdk/btrace/runtime/BTraceRuntimeAccess";
  public static final String BTRACERTACCESS_DESC = "L" + BTRACERTACCESS_INTERNAL + ";";
  public static final String BTRACERT_INTERNAL = "org/openjdk/btrace/core/BTraceRuntime";
  public static final String BTRACERT_DESC = "L" + BTRACERT_INTERNAL + ";";
  public static final String BTRACERTIMPL_INTERNAL = "org/openjdk/btrace/core/BTraceRuntime$Impl";
  public static final String BTRACERTIMPL_DESC = "L" + BTRACERTIMPL_INTERNAL + ";";
  public static final String BTRACERTBASE_INTERNAL =
      "org/openjdk/btrace/runtime/BTraceRuntimeImplBase";
  public static final String BTRACERTBASE_DESC = "L" + BTRACERTBASE_INTERNAL + ";";
  public static final Type BTRACERT_TYPE = Type.getType(BTRACERT_DESC);

  public static final String THREAD_LOCAL_INTERNAL = "java/lang/ThreadLocal";
  public static final String THREAD_LOCAL_DESC = "L" + THREAD_LOCAL_INTERNAL + ";";
  public static final Type THREAD_LOCAL_TYPE = Type.getType(ThreadLocal.class);

  public static final Type LINKING_FLAG_TYPE = Type.getType(LinkingFlag.class);
  public static final String LINKING_FLAG_INTERNAL = LINKING_FLAG_TYPE.getInternalName();

  // BTrace specific stuff
  public static final String BTRACE_UTILS = Type.getInternalName(BTraceUtils.class);

  public static final String SERVICE = Type.getInternalName(Service.class);

  public static final String BTRACE_DESC = Type.getDescriptor(BTrace.class);

  public static final String ONMETHOD_DESC = Type.getDescriptor(OnMethod.class);

  public static final String JFRPERIODIC_DESC = Type.getDescriptor(PeriodicEvent.class);

  public static final String JFREVENTFACTORY_DESC = Type.getDescriptor(JfrEvent.Factory.class);

  public static final String BTRACE_PROBECLASSNAME_DESC = Type.getDescriptor(ProbeClassName.class);

  public static final String BTRACE_PROBEMETHODNAME_DESC =
      Type.getDescriptor(ProbeMethodName.class);

  public static final String ONTIMER_DESC = Type.getDescriptor(OnTimer.class);
  public static final String ONEVENT_DESC = Type.getDescriptor(OnEvent.class);
  public static final String ONEXIT_DESC = Type.getDescriptor(OnExit.class);
  public static final String ONERROR_DESC = Type.getDescriptor(OnError.class);
  public static final String ONLOWMEMORY_DESC = Type.getDescriptor(OnLowMemory.class);

  public static final String SAMPLED_DESC = Type.getDescriptor(Sampled.class);

  public static final String SAMPLER_DESC = Type.getDescriptor(Sampled.Sampler.class);

  public static final String ONPROBE_DESC = Type.getDescriptor(OnProbe.class);

  public static final String LOCATION_DESC = Type.getDescriptor(Location.class);

  public static final String LEVEL_DESC = Type.getDescriptor(Level.class);

  public static final String WHERE_DESC = Type.getDescriptor(Where.class);

  public static final String KIND_DESC = Type.getDescriptor(Kind.class);

  public static final String INJECTED_DESC = Type.getDescriptor(Injected.class);

  public static final String RETURN_DESC = Type.getDescriptor(Return.class);

  public static final String SELF_DESC = Type.getDescriptor(Self.class);

  public static final String TARGETMETHOD_DESC = Type.getDescriptor(TargetMethodOrField.class);

  public static final String TARGETINSTANCE_DESC = Type.getDescriptor(TargetInstance.class);

  public static final String DURATION_DESC = Type.getDescriptor(Duration.class);

  public static final String ARGSMAP_DESC = Type.getDescriptor(ArgsMap.class);

  // class name pattern is specified with this pattern
  public static final Pattern REGEX_SPECIFIER = Pattern.compile("/.+/");

  public static final String JAVA_LANG_THREAD_LOCAL = Type.getInternalName(ThreadLocal.class);
  public static final String JAVA_LANG_THREAD_LOCAL_GET = "get";
  public static final String JAVA_LANG_THREAD_LOCAL_GET_DESC = "()Ljava/lang/Object;";
  public static final String JAVA_LANG_THREAD_LOCAL_SET = "set";
  public static final String JAVA_LANG_THREAD_LOCAL_SET_DESC = "(Ljava/lang/Object;)V";

  public static final String NUMBER_INTERNAL = "java/lang/Number";
  public static final String INTEGER_BOXED_INTERNAL = "java/lang/Integer";
  public static final String INTEGER_BOXED_DESC = "L" + INTEGER_BOXED_INTERNAL + ";";
  public static final String SHORT_BOXED_INTERNAL = "java/lang/Short";
  public static final String SHORT_BOXED_DESC = "L" + SHORT_BOXED_INTERNAL + ";";
  public static final String LONG_BOXED_INTERNAL = "java/lang/Long";
  public static final String LONG_BOXED_DESC = "L" + LONG_BOXED_INTERNAL + ";";
  public static final String FLOAT_BOXED_INTERNAL = "java/lang/Float";
  public static final String FLOAT_BOXED_DESC = "L" + FLOAT_BOXED_INTERNAL + ";";
  public static final String DOUBLE_BOXED_INTERNAL = "java/lang/Double";
  public static final String DOUBLE_BOXED_DESC = "L" + DOUBLE_BOXED_INTERNAL + ";";
  public static final String BYTE_BOXED_INTERNAL = "java/lang/Byte";
  public static final String BYTE_BOXED_DESC = "L" + BYTE_BOXED_INTERNAL + ";";
  public static final String BOOLEAN_BOXED_INTERNAL = "java/lang/Boolean";
  public static final String BOOLEAN_BOXED_DESC = "L" + BOOLEAN_BOXED_INTERNAL + ";";
  public static final String CHARACTER_BOXED_INTERNAL = "java/lang/Character";
  public static final String CHARACTER_BOXED_DESC = "L" + CHARACTER_BOXED_INTERNAL + ";";

  public static final String BOX_VALUEOF = "valueOf";
  public static final String BOX_BOOLEAN_DESC = "(" + BOOLEAN_DESC + ")" + BOOLEAN_BOXED_DESC;
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

  public static final String BOOLEAN_VALUE_DESC = "()Z";
  public static final String CHAR_VALUE_DESC = "()C";
  public static final String BYTE_VALUE_DESC = "()B";
  public static final String SHORT_VALUE_DESC = "()S";
  public static final String INT_VALUE_DESC = "()I";
  public static final String LONG_VALUE_DESC = "()J";
  public static final String FLOAT_VALUE_DESC = "()F";
  public static final String DOUBLE_VALUE_DESC = "()D";
  public static final String EMBEDDED_BTRACE_SECTION_HEADER = "META-INF/btrace/";

  public static final String BTRACE_LEVEL_FLD = "$btrace$$level";
}
