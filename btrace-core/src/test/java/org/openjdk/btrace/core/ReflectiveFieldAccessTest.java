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
package org.openjdk.btrace.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReflectiveFieldAccessTest {

  @Test
  public void getIntTest() {
    D a = new D();
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> BTraceUtils.Reflective.getInt("notExist", a));
    assertTrue(exception.getMessage().contains("notExist"));
  }

  static class A {
    int a;
  }

  static class B extends A {}

  static class C extends A {}

  static class D extends C {}
}
