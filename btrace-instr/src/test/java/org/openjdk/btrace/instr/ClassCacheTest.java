package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
