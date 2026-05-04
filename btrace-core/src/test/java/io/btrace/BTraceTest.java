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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class BTraceTest {

  @Test
  void str_null_returnsNullString() {
    assertEquals("null", BTrace.str((Object) null));
  }

  @Test
  void str_object_usesToString() {
    assertEquals("42", BTrace.str(42));
  }

  @Test
  void concat_twoStrings() {
    assertEquals("ab", BTrace.concat("a", "b"));
  }

  @Test
  void timestamp_isPositive() {
    assertTrue(BTrace.timestamp() > 0);
  }

  @Test
  void monotonic_isPositive() {
    assertTrue(BTrace.monotonic() > 0);
  }

  @Test
  void threadName_currentThread() {
    assertEquals(Thread.currentThread().getName(), BTrace.threadName(Thread.currentThread()));
  }

  @Test
  void threadId_currentThread() {
    assertEquals(Thread.currentThread().getId(), BTrace.threadId(Thread.currentThread()));
  }

  @Test
  void className_object() {
    assertEquals("java.lang.String", BTrace.className("hello"));
  }

  @Test
  void identity_returnsSystemIdentityHashCode() {
    Object o = new Object();
    assertEquals(System.identityHashCode(o), BTrace.identity(o));
  }

  @Test
  void substr_basic() {
    assertEquals("bc", BTrace.substr("abcd", 1, 3));
  }

  @Test
  void matches_basicRegex() {
    assertTrue(BTrace.matches(".*bc.*", "abcd"));
    assertFalse(BTrace.matches("^bc", "abcd"));
  }

  @Test
  void str_boolean_true() {
    assertEquals("true", BTrace.str(true));
  }

  @Test
  void str_boolean_false() {
    assertEquals("false", BTrace.str(false));
  }

  @Test
  void str_int() {
    assertEquals("7", BTrace.str(7));
  }

  @Test
  void str_long() {
    assertEquals("7", BTrace.str(7L));
  }

  @Test
  void str_float() {
    assertEquals("1.5", BTrace.str(1.5f));
  }

  @Test
  void str_double() {
    assertEquals("2.5", BTrace.str(2.5));
  }

  @Test
  void startsWith_true() {
    assertTrue(BTrace.startsWith("hello", "hel"));
  }

  @Test
  void startsWith_false() {
    assertFalse(BTrace.startsWith("hello", "ell"));
  }

  @Test
  void endsWith_true() {
    assertTrue(BTrace.endsWith("hello", "llo"));
  }

  @Test
  void endsWith_false() {
    assertFalse(BTrace.endsWith("hello", "hel"));
  }

  @Test
  void length_string() {
    assertEquals(5, BTrace.length("hello"));
  }

  @Test
  void length_null() {
    assertEquals(0, BTrace.length(null));
  }

  @Test
  void abs_long() {
    assertEquals(5L, BTrace.abs(-5L));
  }

  @Test
  void abs_double() {
    assertEquals(3.0, BTrace.abs(-3.0));
  }

  @Test
  void min_long() {
    assertEquals(2L, BTrace.min(2L, 5L));
  }

  @Test
  void max_long() {
    assertEquals(5L, BTrace.max(2L, 5L));
  }

  @Test
  void min_double() {
    assertEquals(1.0, BTrace.min(1.0, 2.0));
  }

  @Test
  void max_double() {
    assertEquals(2.0, BTrace.max(1.0, 2.0));
  }
}
