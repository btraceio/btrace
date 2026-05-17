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
package io.btrace.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.btrace.BTrace;
import org.junit.jupiter.api.Test;

public class BTraceUtilsShimTest {

  @Test
  void stringsStrcat_delegatesToBTrace() {
    assertEquals(BTrace.concat("hello", " world"), BTraceUtils.Strings.strcat("hello", " world"));
  }

  @Test
  void stringsStr_objectProducesSameResult() {
    Object obj = 42;
    assertEquals(BTrace.str(obj), BTraceUtils.Strings.str(obj));
  }

  @Test
  void stringsStr_nullProducesSameResult() {
    assertEquals(BTrace.str((Object) null), BTraceUtils.Strings.str((Object) null));
  }
}
