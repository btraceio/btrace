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
package btrace;

import static io.btrace.core.BTraceUtils.println;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Duration;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.Return;
import io.btrace.core.annotations.TargetInstance;
import io.btrace.core.annotations.TargetMethodOrField;
import io.btrace.core.annotations.Where;

/**
 * Smoke coverage for ClassFile API probe families that are not exercised by the smaller Math-based
 * integration tests.
 */
@BTrace
public class ClassFileApiFeatureSmokeTest {
  private static boolean fieldGetPrinted;
  private static boolean fieldSetPrinted;
  private static boolean arrayGetPrinted;
  private static boolean arraySetPrinted;
  private static boolean newArrayPrinted;
  private static boolean syncEntryPrinted;
  private static boolean syncExitPrinted;
  private static boolean callPrinted;
  private static boolean newObjectPrinted;
  private static boolean catchPrinted;
  private static boolean errorPrinted;

  @OnMethod(
      clazz = "java.util.Date",
      method = "getTimeImpl",
      location =
          @Location(
              value = Kind.FIELD_GET,
              clazz = "java.util.Date",
              field = "fastTime",
              where = Where.AFTER))
  public static void onFieldGet(
      @TargetMethodOrField(fqn = true) String field, @Return long value) {
    if (!fieldGetPrinted) {
      fieldGetPrinted = true;
      println("cfapi FIELD_GET " + field + "=" + value);
    }
  }

  @OnMethod(
      clazz = "java.util.Date",
      method = "setTime",
      location =
          @Location(
              value = Kind.FIELD_SET,
              clazz = "java.util.Date",
              field = "fastTime"))
  public static void onFieldSet(@TargetMethodOrField(fqn = true) String field, long value) {
    if (!fieldSetPrinted) {
      fieldSetPrinted = true;
      println("cfapi FIELD_SET " + field + "=" + value);
    }
  }

  @OnMethod(
      clazz = "java.util.Arrays",
      method = "equals",
      location = @Location(value = Kind.ARRAY_GET, type = "java.lang.Object", where = Where.AFTER))
  public static void onArrayGet(@Return Object value, int index) {
    if (!arrayGetPrinted) {
      arrayGetPrinted = true;
      println("cfapi ARRAY_GET index=" + index);
    }
  }

  @OnMethod(
      clazz = "java.util.Arrays",
      method = "fill",
      location = @Location(value = Kind.ARRAY_SET, type = "int"))
  public static void onArraySet(@TargetInstance int[] array, int index, int value) {
    if (!arraySetPrinted) {
      arraySetPrinted = true;
      println("cfapi ARRAY_SET index=" + index + ", value=" + value);
    }
  }

  @OnMethod(
      clazz = "java.util.Arrays",
      method = "copyOf",
      location = @Location(value = Kind.NEWARRAY, clazz = "int", where = Where.AFTER))
  public static void onNewArray(String type, int dimensions, @Return int[] array) {
    if (!newArrayPrinted) {
      newArrayPrinted = true;
      println("cfapi NEWARRAY type=" + type + ", dimensions=" + dimensions);
    }
  }

  @OnMethod(
      clazz = "java.util.Collections$SynchronizedMap",
      method = "get",
      location = @Location(value = Kind.SYNC_ENTRY, where = Where.AFTER))
  public static void onSyncEntry(@TargetInstance Object lock) {
    if (!syncEntryPrinted) {
      syncEntryPrinted = true;
      println("cfapi SYNC_ENTRY");
    }
  }

  @OnMethod(
      clazz = "java.util.Collections$SynchronizedMap",
      method = "get",
      location = @Location(value = Kind.SYNC_EXIT))
  public static void onSyncExit(@TargetInstance Object lock) {
    if (!syncExitPrinted) {
      syncExitPrinted = true;
      println("cfapi SYNC_EXIT");
    }
  }

  @OnMethod(
      clazz = "java.util.Collections$SynchronizedMap",
      method = "get",
      location = @Location(value = Kind.CALL, clazz = "java.util.Map", method = "get"))
  public static void onCall(@TargetMethodOrField(fqn = true) String target, Object key) {
    if (!callPrinted) {
      callPrinted = true;
      println("cfapi CALL " + target);
    }
  }

  @OnMethod(
      clazz = "java.util.Collections$SynchronizedMap",
      method = "keySet",
      location =
          @Location(
              value = Kind.NEW,
              clazz = "java.util.Collections$SynchronizedSet",
              where = Where.AFTER))
  public static void onNewObject(String type, @Return Object set) {
    if (!newObjectPrinted) {
      newObjectPrinted = true;
      println("cfapi NEW " + type);
    }
  }

  @OnMethod(
      clazz = "java.util.Base64$Decoder",
      method = "decode",
      location = @Location(value = Kind.CATCH))
  public static void onCatch(@TargetInstance IllegalArgumentException exception) {
    if (!catchPrinted) {
      catchPrinted = true;
      println("cfapi CATCH");
    }
  }

  @OnMethod(
      clazz = "java.util.Base64$Decoder",
      method = "decode",
      location = @Location(value = Kind.ERROR))
  public static void onError(@Duration long duration, @TargetInstance Throwable exception) {
    if (!errorPrinted) {
      errorPrinted = true;
      println("cfapi ERROR duration=" + duration);
    }
  }
}
