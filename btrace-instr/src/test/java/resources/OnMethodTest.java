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
package resources;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * !!! Only append the new methods; line numbers need to be kept intact !!!
 *
 * @author Jaroslav Bachorik
 */
@io.btrace.core.annotations.BTrace
public class OnMethodTest {
  private int field;

  public OnMethodTest() {
    syncLock = new Object();
  }

  private OnMethodTest(String a) {
    syncLock = new Object();
  }

  @io.btrace.core.annotations.Level
  public void noargs() {}
  ;

  public static void noargs$static() {}
  ;

  public long args(String a, long b, String[] c, int[] d) {
    return 0L;
  }

  public static long args$static(String a, long b, String[] c, int[] d) {
    return 0L;
  }

  public static long callTopLevelStatic(String a, long b) {
    OnMethodTest instance = new OnMethodTest();
    return callTargetStatic(a, b) + instance.callTarget(a, b);
  }

  public static long callTargetStatic(String a, long b) {
    return 3L;
  }

  public long callTopLevel(String a, long b) {
    return callTarget(a, b) + callTargetStatic(a, b);
  }

  private long callTarget(String a, long b) {
    return 4L;
  }

  public void exception() {
    try {
      throw new IOException("hello world");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void uncaught() {
    throw new RuntimeException("ho-hey");
  }

  public void array(int a) {
    int[] arr = new int[10];

    int b = arr[a];
    arr[a] = 15;
  }

  public void field() {
    this.field = this.field + 1;
  }

  public void newObject() {
    Map<String, String> m = new HashMap<String, String>();
  }

  public void newArray() {
    int[] a = new int[1];
    int[][] b = new int[1][1];
    String[] c = new String[1];
    String[][] d = new String[1][1];
  }

  public void casts() {
    Map<String, String> c = new HashMap<String, String>();
    HashMap<String, String> d = (HashMap<String, String>) c;

    if (c instanceof HashMap) {
      System.err.println("hey ho");
    }
  }

  public void sync() {
    synchronized (this) {
      System.err.println("ho hey");
    }
  }

  public long callTopLevel1(String a, long b) {
    long i = callTarget(a, b) + callTargetStatic(a, b);
    return i + calLTargetX(a, b);
  }

  private long calLTargetX(String a, long b) {
    return 5L;
  }

  public long argsMultiReturn(String a, long b, String[] c, int[] d) {
    if (System.currentTimeMillis() > 325723059) {
      return 0L;
    }

    if (System.currentTimeMillis() > 32525) {
      return 1L;
    }

    {
      System.out.println("fdsfg");
      return -1L;
    }
  }

  public native long nativeWithReturn(int a, String b, long[] c, Object[] d);

  public native void nativeWithoutReturn(int a, String b, long[] c, Object[] d);

  private static long sField;

  public void staticField() {
    OnMethodTest.sField += 1;
  }

  public void syncM() {
    synchronized (syncLock) {
      System.err.println("ho hey");
    }
  }

  private final Object syncLock;

  public String argsTypeMatch(java.util.ArrayList<String> l) {
    return "x";
  }

  public void caught() {
    try {
      throw new RuntimeException("ho-hey");
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }
}
