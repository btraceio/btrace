package org.openjdk.btrace.core.extensions;

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
    assertNull(
        FakeApi.class.getDeclaredMethod("counter").getAnnotation(ExternalType.Static.class));
  }

  @Test
  void retentionIsRuntime() {
    Retention r = ExternalType.class.getAnnotation(Retention.class);
    assertEquals(RetentionPolicy.RUNTIME, r.value());
  }
}
