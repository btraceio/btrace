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
}
