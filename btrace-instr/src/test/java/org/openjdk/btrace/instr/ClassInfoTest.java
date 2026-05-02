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
package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openjdk.btrace.runtime.BTraceRuntimeAccess;

class ClassInfoTest {
  @BeforeAll
  static void setupAll() throws Throwable {
    Method m = BTraceRuntimeAccess.class.getDeclaredMethod("registerRuntimeAccessor");
    m.setAccessible(true);
    m.invoke(null);
  }

  @Test
  void isBootstrap() {
    assertTrue(ClassInfo.isBootstrap(String.class.getName()));
    assertFalse(ClassInfo.isBootstrap(ClassInfoTest.class.getName()));
  }
}
