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

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassCacheTest {
  private ClassCache instance;

  @BeforeEach
  void setup() {
    instance = new ClassCache(10);
  }

  @Test
  void getClazz() {
    ClassInfo ci = instance.get(String.class);
    assertNotNull(ci);

    ClassInfo ci1 = instance.get(String.class);

    assertEquals(ci1, ci);
  }

  @Test
  void getClazzNullCL() {
    ClassInfo ci = instance.get(null, String.class.getName());
    assertNotNull(ci);
  }

  @Test
  void testCacheCleanup() throws Exception {
    ClassLoader cl = new ClassLoader(ClassCacheTest.class.getClassLoader()) {};

    Map<ClassInfo.ClassName, ClassInfo> infos = instance.getInfos(cl);

    assertNotNull(infos);
    assertTrue(infos.isEmpty());
    assertEquals(1, instance.getSize());

    // run GC but have the classloader still referred to
    System.gc();
    Thread.sleep(100);

    assertEquals(1, instance.getSize());

    // clear the reference to the classloader
    cl = null;
    // and run the gc
    System.gc();
    Thread.sleep(100);

    assertEquals(0, instance.getSize());
  }
}
