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
package io.btrace;

import io.btrace.core.BTraceUtils;

/**
 * Flat core DSL for BTrace scripts. All methods are auto-imported by the compiler. At runtime,
 * every INVOKESTATIC to this class is rewritten to INVOKEDYNAMIC by the post-processor; these
 * static bodies serve as compile-time targets and fallbacks only.
 */
public final class BTrace {

  private BTrace() {}

  // --- Output ---

  public static void print(String s) {
    BTraceUtils.print(s);
  }

  public static void println(String s) {
    BTraceUtils.println(s);
  }

  public static void println() {
    BTraceUtils.println();
  }

  public static void printf(String fmt, Object... args) {
    BTraceUtils.print(String.format(fmt, args));
  }

  // --- Strings ---

  public static String str(Object o) {
    return o == null ? "null" : o.toString();
  }

  public static String str(boolean b) {
    return Boolean.toString(b);
  }

  public static String str(int i) {
    return Integer.toString(i);
  }

  public static String str(long l) {
    return Long.toString(l);
  }

  public static String str(float f) {
    return Float.toString(f);
  }

  public static String str(double d) {
    return Double.toString(d);
  }

  public static String concat(String a, String b) {
    return a == null ? b : b == null ? a : a + b;
  }

  public static String substr(String s, int start, int end) {
    return s.substring(start, end);
  }

  public static boolean matches(String regex, String s) {
    return s != null && s.matches(regex);
  }

  public static boolean startsWith(String s, String prefix) {
    return s != null && s.startsWith(prefix);
  }

  public static boolean endsWith(String s, String suffix) {
    return s != null && s.endsWith(suffix);
  }

  public static int length(String s) {
    return s == null ? 0 : s.length();
  }

  // --- Numbers ---

  public static long abs(long l) {
    return Math.abs(l);
  }

  public static double abs(double d) {
    return Math.abs(d);
  }

  public static long min(long a, long b) {
    return Math.min(a, b);
  }

  public static long max(long a, long b) {
    return Math.max(a, b);
  }

  public static double min(double a, double b) {
    return Math.min(a, b);
  }

  public static double max(double a, double b) {
    return Math.max(a, b);
  }

  // --- Time ---

  public static long timestamp() {
    return System.currentTimeMillis();
  }

  public static long monotonic() {
    return System.nanoTime();
  }

  // --- Threads ---

  public static Thread currentThread() {
    return Thread.currentThread();
  }

  public static String threadName(Thread t) {
    return t == null ? "" : t.getName();
  }

  public static long threadId(Thread t) {
    return t == null ? -1L : t.getId();
  }

  // --- Stack ---

  public static void printStack() {
    println(stackTrace());
  }

  public static String stackTrace() {
    StackTraceElement[] frames = Thread.currentThread().getStackTrace();
    StringBuilder sb = new StringBuilder();
    // skip getStackTrace() and stackTrace() frames
    for (int i = 2; i < frames.length; i++) {
      sb.append("\tat ").append(frames[i]).append('\n');
    }
    return sb.toString();
  }

  public static int stackDepth() {
    return Thread.currentThread().getStackTrace().length - 2;
  }

  // --- Object ---

  public static String className(Object o) {
    return o == null ? "null" : o.getClass().getName();
  }

  public static int identity(Object o) {
    return System.identityHashCode(o);
  }

  public static long size(Object o) {
    return BTraceUtils.sizeof(o);
  }

  // --- Control ---

  public static void exit(int code) {
    BTraceUtils.exit(code);
  }
}
