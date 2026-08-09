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
package io.btrace.core.extensions;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.*;
import org.junit.jupiter.api.Test;

class ExternalTypeAnnotationTest {
  @ExternalType("com.example.App")
  interface FakeApi {
    @ExternalType.Static
    Object create(String name);

    int counter();
  }

  @Test
  void annotationValuePreserved() {
    ExternalType a = FakeApi.class.getAnnotation(ExternalType.class);
    assertNotNull(a);
    assertEquals("com.example.App", a.value());
  }

  @Test
  void staticAnnotationOnMethod() throws NoSuchMethodException {
    assertNotNull(
        FakeApi.class
            .getDeclaredMethod("create", String.class)
            .getAnnotation(ExternalType.Static.class));
    assertNull(FakeApi.class.getDeclaredMethod("counter").getAnnotation(ExternalType.Static.class));
  }

  @Test
  void retentionIsRuntime() {
    Retention r = ExternalType.class.getAnnotation(Retention.class);
    assertEquals(RetentionPolicy.RUNTIME, r.value());
  }

  @Test
  void typeMarkerIsSourceOnlyOnDeclarations() {
    Retention retention = ExternalType.Type.class.getAnnotation(Retention.class);
    Target target = ExternalType.Type.class.getAnnotation(Target.class);
    assertEquals(RetentionPolicy.SOURCE, retention.value());
    assertArrayEquals(
        new ElementType[] {ElementType.METHOD, ElementType.PARAMETER}, target.value());
  }

  @Test
  void phaseFiveOperationMarkersAreSourceOnlyMethodDeclarations() {
    assertMarker(ExternalType.Getter.class, true);
    assertMarker(ExternalType.Setter.class, true);
    assertMarker(ExternalType.Constructor.class, false);
    assertMarker(ExternalType.InstanceOf.class, false);
    assertMarker(ExternalType.Cast.class, false);
  }

  private static void assertMarker(Class<? extends Annotation> marker, boolean hasValue) {
    assertEquals(RetentionPolicy.SOURCE, marker.getAnnotation(Retention.class).value());
    assertArrayEquals(
        new ElementType[] {ElementType.METHOD}, marker.getAnnotation(Target.class).value());
    assertEquals(hasValue ? 1 : 0, marker.getDeclaredMethods().length);
    if (hasValue) {
      assertEquals(String.class, marker.getDeclaredMethods()[0].getReturnType());
      assertEquals("value", marker.getDeclaredMethods()[0].getName());
    }
  }
}
