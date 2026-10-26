/*
 * Copyright (c) 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class BTraceClassReaderTest {

  private static Object staticField(String name) throws Exception {
    Field f = BTraceClassReader.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(null);
  }

  /** Class carrying several annotations with nested/array values, built directly with ASM. */
  private static byte[] annotatedClass() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "test/Annotated", null, "java/lang/Object", null);
    AnnotationVisitor a = cw.visitAnnotation("Ltest/First;", true);
    a.visit("name", "value");
    a.visit("count", 42);
    AnnotationVisitor arr = a.visitArray("tags");
    arr.visit(null, "x");
    arr.visit(null, "y");
    AnnotationVisitor nested = arr.visitAnnotation(null, "Ltest/Nested;");
    nested.visit("n", 1);
    nested.visitEnd();
    arr.visitEnd();
    a.visitEnum("kind", "Ltest/Kind;", "A");
    a.visitEnd();
    AnnotationVisitor b = cw.visitAnnotation("Ltest/Second;", true);
    b.visit("v", "s");
    b.visitEnd();
    cw.visitAnnotation("Ltest/Third;", true).visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  void reflectiveHooksInitialized() throws Exception {
    assertNotNull(staticField("getAttributesMthd"), "getFirstAttributeOffset hook");
    assertNotNull(staticField("readAnnotationValuesMthd"), "readElementValues hook");
    assertNotNull(staticField("itemsFld"), "cpInfoOffsets hook");
    assertNotNull(staticField("mslFld"), "maxStringLength hook");
  }

  @Test
  void bundledAsmSignatureIsTheOneResolved() throws Exception {
    Method m = (Method) staticField("readAnnotationValuesMthd");
    assertNotNull(m);
    // Bundled ASM (9.11+) takes a trailing 'depth'; an ASM bump that changes this must fail here.
    assertEquals(
        Arrays.asList(AnnotationVisitor.class, int.class, boolean.class, char[].class, int.class),
        Arrays.asList(m.getParameterTypes()));
  }

  @Test
  void allClassAnnotationsAreRead() {
    BTraceClassReader cr = new BTraceClassReader(null, annotatedClass());
    Collection<String> types = cr.getAnnotationTypes();
    assertTrue(types.contains("test.First"), types.toString());
    assertTrue(types.contains("test.Second"), types.toString());
    assertTrue(types.contains("test.Third"), types.toString());
    assertEquals(3, types.size(), types.toString());
  }
}
