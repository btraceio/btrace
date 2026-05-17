/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import java.util.regex.Pattern;

import io.btrace.core.ArgsMap;
import io.btrace.core.BTraceUtils;
import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Duration;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Level;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnError;
import io.btrace.core.annotations.OnEvent;
import io.btrace.core.annotations.OnExit;
import io.btrace.core.annotations.OnLowMemory;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.OnProbe;
import io.btrace.core.annotations.OnTimer;
import io.btrace.core.annotations.PeriodicEvent;
import io.btrace.core.annotations.ProbeClassName;
import io.btrace.core.annotations.ProbeMethodName;
import io.btrace.core.annotations.Return;
import io.btrace.core.annotations.Sampled;
import io.btrace.core.annotations.Self;
import io.btrace.core.annotations.TargetInstance;
import io.btrace.core.annotations.TargetMethodOrField;
import io.btrace.core.annotations.Where;
import io.btrace.core.extensions.Extension;
import io.btrace.core.jfr.JfrEvent;
import io.btrace.runtime.LinkingFlag;

import org.objectweb.asm.Type;

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

  public static final String ANYTYPE_INTERNAL = "io/btrace/core/types/AnyType";
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

  public static final String BTRACERTACCESS_INTERNAL = "io/btrace/runtime/BTraceRuntimeAccess";
  public static final String BTRACERTACCESS_DESC = "L" + BTRACERTACCESS_INTERNAL + ";";
  public static final String BTRACERT_INTERNAL = "io/btrace/core/BTraceRuntime";
  public static final String BTRACERT_DESC = "L" + BTRACERT_INTERNAL + ";";
  public static final String BTRACERTIMPL_INTERNAL = "io/btrace/core/BTraceRuntime$Impl";
  public static final String BTRACERTIMPL_DESC = "L" + BTRACERTIMPL_INTERNAL + ";";
  public static final String BTRACERTBRIDGE_INTERNAL = "io/btrace/core/BTraceRuntimeBridge";
  public static final String BTRACERTBRIDGE_DESC = "L" + BTRACERTBRIDGE_INTERNAL + ";";
  public static final Type BTRACERT_TYPE = Type.getType(BTRACERT_DESC);

  public static final String THREAD_LOCAL_INTERNAL = "java/lang/ThreadLocal";
  public static final String THREAD_LOCAL_DESC = "L" + THREAD_LOCAL_INTERNAL + ";";
  public static final Type THREAD_LOCAL_TYPE = Type.getType(ThreadLocal.class);

  public static final Type LINKING_FLAG_TYPE = Type.getType(LinkingFlag.class);
  public static final String LINKING_FLAG_INTERNAL = LINKING_FLAG_TYPE.getInternalName();

  // BTrace specific stuff
  public static final String BTRACE_UTILS = Type.getInternalName(BTraceUtils.class);
  public static final String BTRACE_DSL = "io/btrace/BTrace";
  public static final String BTRACE_BOOTSTRAP = "io/btrace/runtime/BTraceBootstrap";
  public static final String INDY_DISPATCHER = "io/btrace/runtime/IndyDispatcher";

  public static final String EXTENSION = Type.getInternalName(Extension.class);

  public static final String EXTENSION_DESC = Type.getDescriptor(Extension.class);

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

  // RequestPermission annotations removed; permissions are managed via descriptors and manifest.

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
