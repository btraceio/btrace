package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class InstrStackTest {
  InstrumentingMethodVisitor instance;

  @BeforeEach
  void setup() throws Exception {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "test.Test", null, null, null);
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test", "()V", null, null);
    instance =
        new InstrumentingMethodVisitor(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test.Test", "test", "()V", mv);
  }

  @Test
  void ACONST_NULL() {
    instance.visitInsn(Opcodes.ACONST_NULL);
    assertEquals(Opcodes.NULL, instance.introspect().stack.peek());
  }

  @Test
  void ICONST_1() {
    instance.visitInsn(Opcodes.ICONST_1);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void ICONST_2() {
    instance.visitInsn(Opcodes.ICONST_2);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void ICONST_3() {
    instance.visitInsn(Opcodes.ICONST_3);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void ICONST_4() {
    instance.visitInsn(Opcodes.ICONST_4);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void ICONST_5() {
    instance.visitInsn(Opcodes.ICONST_5);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void ICONST_M1() {
    instance.visitInsn(Opcodes.ICONST_M1);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void FCONST_0() {
    instance.visitInsn(Opcodes.FCONST_0);
    assertEquals(Opcodes.FLOAT, instance.introspect().stack.peek());
  }

  @Test
  void FCONST_1() {
    instance.visitInsn(Opcodes.FCONST_1);
    assertEquals(Opcodes.FLOAT, instance.introspect().stack.peek());
  }

  @Test
  void FCONST_2() {
    instance.visitInsn(Opcodes.FCONST_2);
    assertEquals(Opcodes.FLOAT, instance.introspect().stack.peek());
  }

  @Test
  void DCONST_0() {
    instance.visitInsn(Opcodes.DCONST_0);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.DOUBLE, i.stack.peekX1());
  }

  @Test
  void DCONST_1() {
    instance.visitInsn(Opcodes.DCONST_1);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.DOUBLE, i.stack.peekX1());
  }

  @Test
  void LCONST_0() {
    instance.visitInsn(Opcodes.LCONST_0);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.LONG, i.stack.peekX1());
  }

  @Test
  void LCONST_1() {
    instance.visitInsn(Opcodes.LCONST_1);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.LONG, i.stack.peekX1());
  }

  @Test
  void AALOAD() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.AALOAD);
    assertEquals("java/lang/String", instance.introspect().stack.peek());
  }

  @Test
  void AALOAD_TYPEDESC() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "Ljava/lang/String;");
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.AALOAD);
    assertEquals("java/lang/String", instance.introspect().stack.peek());
  }

  @Test
  void AALOAD_NULL() {
    instance.visitLdcInsn(1);
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.AALOAD);
    assertEquals(Opcodes.NULL, instance.introspect().stack.peek());
  }

  @Test
  void AALOAD_INVALID() {
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.AALOAD);
    assertEquals("java/lang/Object", instance.introspect().stack.peek());
  }

  @Test
  void IALOAD() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "I");
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.IALOAD);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void IALOAD_NULL() {
    instance.visitLdcInsn(1);
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.IALOAD);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void IALOAD_INVALID() {
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.IALOAD);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void FALOAD() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "F");
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.FALOAD);
    assertEquals(Opcodes.FLOAT, instance.introspect().stack.peek());
  }

  @Test
  void FALOAD_NULL() {
    instance.visitLdcInsn(1);
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.FALOAD);
    assertEquals(Opcodes.FLOAT, instance.introspect().stack.peek());
  }

  @Test
  void FALOAD_INVALID() {
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.FALOAD);
    assertEquals(Opcodes.FLOAT, instance.introspect().stack.peek());
  }

  @Test
  void BALOAD() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "B");
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.BALOAD);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void BALOAD_NULL() {
    instance.visitLdcInsn(1);
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.BALOAD);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void BALOAD_INVALID() {
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.BALOAD);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void CALOAD() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "C");
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.CALOAD);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void CALOAD_NULL() {
    instance.visitLdcInsn(1);
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.CALOAD);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void CALOAD_INVALID() {
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.CALOAD);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void SALOAD() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "S");
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.SALOAD);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void SALOAD_NULL() {
    instance.visitLdcInsn(1);
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.SALOAD);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void SALOAD_INVALID() {
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.SALOAD);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void LALOAD() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "J");
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.LALOAD);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.LONG, i.stack.peekX1());
  }

  @Test
  void LALOAD_NULL() {
    instance.visitLdcInsn(1);
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.LALOAD);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.LONG, i.stack.peekX1());
  }

  @Test
  void LALOAD_INVALID() {
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.LALOAD);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.LONG, i.stack.peekX1());
  }

  @Test
  void DALOAD() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "D");
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.DALOAD);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.DOUBLE, i.stack.peekX1());
  }

  @Test
  void DALOAD_NULL() {
    instance.visitLdcInsn(1);
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.DALOAD);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.DOUBLE, i.stack.peekX1());
  }

  @Test
  void DALOAD_INVALID() {
    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.DALOAD);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.DOUBLE, i.stack.peekX1());
  }

  @Test
  void AASTORE() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String"); // arrayref
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn("hello"); // value
    instance.visitInsn(Opcodes.AASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void AASTORE_TYPEDESC() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "Ljava/lang/String;"); // arrayref
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn("hello"); // value
    instance.visitInsn(Opcodes.AASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void AASTORE_NULL() {
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn("hello"); // value
    instance.visitInsn(Opcodes.AASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void AASTORE_INVALID() {
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn("hello"); // value
    instance.visitInsn(Opcodes.AASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void IASTORE() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "I"); // arrayref
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.IASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void IASTORE_NULL() {
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.IASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void IASTORE_INVALID() {
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.IASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void BASTORE() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "B"); // arrayref
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.BASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void BASTORE_NULL() {
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.BASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void BASTORE_INVALID() {
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.BASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void CASTORE() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "C"); // arrayref
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.CASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void CASTORE_NULL() {
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.CASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void CASTORE_INVALID() {
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.CASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void SASTORE() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "S"); // arrayref
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.SASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void SASTORE_NULL() {
    instance.visitInsn(Opcodes.ACONST_NULL);
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.SASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void SASTORE_INVALID() {
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.SASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void FASTORE() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "F"); // arrayref
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1f); // value
    instance.visitInsn(Opcodes.FASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void FASTORE_NULL() {
    instance.visitInsn(Opcodes.ACONST_NULL); // arrayref
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1f); // value
    instance.visitInsn(Opcodes.FASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void FASTORE_INVALID() {
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1f); // value
    instance.visitInsn(Opcodes.FASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void LASTORE() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "J"); // arrayref
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1L); // value
    instance.visitInsn(Opcodes.LASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void LASTORE_NULL() {
    instance.visitInsn(Opcodes.ACONST_NULL); // arrayref
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1L); // value
    instance.visitInsn(Opcodes.LASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void LASTORE_INVALID() {
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.LASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void DASTORE() {
    instance.visitLdcInsn(1);
    instance.visitTypeInsn(Opcodes.ANEWARRAY, "D"); // arrayref
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1d); // value
    instance.visitInsn(Opcodes.DASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void DASTORE_NULL() {
    instance.visitInsn(Opcodes.ACONST_NULL); // arrayref
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1d); // value
    instance.visitInsn(Opcodes.DASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void DASTORE_INVALID() {
    instance.visitLdcInsn(0); // index
    instance.visitLdcInsn(1); // value
    instance.visitInsn(Opcodes.DASTORE);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void POP() {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn("hello");
    instance.visitInsn(Opcodes.POP);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());

    instance.visitInsn(Opcodes.POP);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());

    instance.visitInsn(Opcodes.POP);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());
  }

  @Test
  void POP2() {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn(2);
    instance.visitInsn(Opcodes.POP2);
    assertEquals(Opcodes.TOP, instance.introspect().stack.peek());

    instance.visitLdcInsn(1);
    instance.visitLdcInsn(2L);
    instance.visitInsn(Opcodes.POP2);
    assertEquals(Opcodes.INTEGER, instance.introspect().stack.peek());
  }

  @Test
  void DUP() {
    instance.visitLdcInsn(1);
    instance.visitInsn(Opcodes.DUP);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();

    assertEquals(2, i.stack.size());
    assertEquals(Opcodes.INTEGER, i.stack.peek());
  }

  @Test
  void DUP_X1() {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn("hello");
    instance.visitLdcInsn(2f);

    assertEquals(Opcodes.FLOAT, instance.introspect().stack.peek());

    instance.visitInsn(Opcodes.DUP_X1);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(4, i.stack.size());
    assertEquals(Opcodes.FLOAT, i.stack.peek(2));
  }

  @Test
  void DUP_X2() {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn("hello");
    instance.visitLdcInsn(2f);

    assertEquals(Opcodes.FLOAT, instance.introspect().stack.peek());

    instance.visitInsn(Opcodes.DUP_X2);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(4, i.stack.size());
    assertEquals(Opcodes.FLOAT, i.stack.peek(3));
  }

  @Test
  void DUP_X2_LONG() {
    instance.visitLdcInsn("hello");
    instance.visitLdcInsn(1L);
    instance.visitLdcInsn(2f);

    assertEquals(Opcodes.FLOAT, instance.introspect().stack.peek());

    instance.visitInsn(Opcodes.DUP_X2);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(5, i.stack.size());
    assertEquals(Opcodes.FLOAT, i.stack.peek(3));
  }

  @Test
  void DUP2() {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn(2f);
    assertEquals(Opcodes.FLOAT, instance.introspect().stack.peek());

    instance.visitInsn(Opcodes.DUP2);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(4, i.stack.size());
    assertEquals(Opcodes.FLOAT, i.stack.peek());
    assertEquals(Opcodes.INTEGER, i.stack.peek(1));
  }

  @Test
  void DUP2_LONG() {
    instance.visitLdcInsn(1L);
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, instance.introspect().stack.peek());

    instance.visitInsn(Opcodes.DUP2);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(4, i.stack.size());
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.LONG, i.stack.peek(1));
  }

  @Test
  void DUP2_X1() {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn(2f);
    instance.visitLdcInsn("hello");

    assertEquals("java/lang/String", instance.introspect().stack.peek());

    instance.visitInsn(Opcodes.DUP2_X1);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(5, i.stack.size());
    assertEquals("java/lang/String", i.stack.peek(3));
    assertEquals(Opcodes.FLOAT, i.stack.peek(4));
  }

  @Test
  void DUP2_X1_LONG() {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn(2L);

    assertEquals(InstrumentingMethodVisitor.TOP_EXT, instance.introspect().stack.peek());

    instance.visitInsn(Opcodes.DUP2_X1);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(5, i.stack.size());
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek(3));
    assertEquals(Opcodes.LONG, i.stack.peek(4));
  }

  @Test
  void DUP2_X2() {
    instance.visitLdcInsn(0);
    instance.visitLdcInsn(1);
    instance.visitLdcInsn(2f);
    instance.visitLdcInsn("hello");

    assertEquals("java/lang/String", instance.introspect().stack.peek());

    instance.visitInsn(Opcodes.DUP2_X2);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(6, i.stack.size());
    assertEquals("java/lang/String", i.stack.peek(4));
    assertEquals(Opcodes.FLOAT, i.stack.peek(5));
  }

  @Test
  void DUP2_X2_LONG() {
    instance.visitLdcInsn(0);
    instance.visitLdcInsn(1);
    instance.visitLdcInsn(2L);

    assertEquals(InstrumentingMethodVisitor.TOP_EXT, instance.introspect().stack.peek());

    instance.visitInsn(Opcodes.DUP2_X2);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(6, i.stack.size());
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek(4));
    assertEquals(Opcodes.LONG, i.stack.peek(5));
  }

  @Test
  void SWAP() {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn(2f);

    assertEquals(Opcodes.FLOAT, instance.introspect().stack.peek());

    instance.visitInsn(Opcodes.SWAP);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(Opcodes.INTEGER, i.stack.peek());
    assertEquals(Opcodes.FLOAT, i.stack.peek(1));
  }

  @ParameterizedTest
  @MethodSource("arithmeticOpArguments")
  void ADD(Type type, int stackType, Object left, Object right) {
    instance.visitLdcInsn(left);
    instance.visitLdcInsn(right);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(stackType, type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek());

    instance.visitInsn(type.getOpcode(Opcodes.IADD));
    i = instance.introspect();
    assertEquals(type.getSize(), i.stack.size());
    Object slotType = type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek();
    assertEquals(stackType, slotType);
  }

  @ParameterizedTest
  @MethodSource("arithmeticOpArguments")
  void SUB(Type type, int stackType, Object left, Object right) {
    instance.visitLdcInsn(left);
    instance.visitLdcInsn(right);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(stackType, type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek());

    instance.visitInsn(type.getOpcode(Opcodes.ISUB));
    i = instance.introspect();
    assertEquals(type.getSize(), i.stack.size());
    Object slotType = type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek();
    assertEquals(stackType, slotType);
  }

  @ParameterizedTest
  @MethodSource("arithmeticOpArguments")
  void MUL(Type type, int stackType, Object left, Object right) {
    instance.visitLdcInsn(left);
    instance.visitLdcInsn(right);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(stackType, type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek());

    instance.visitInsn(type.getOpcode(Opcodes.IMUL));
    i = instance.introspect();
    assertEquals(type.getSize(), i.stack.size());
    Object slotType = type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek();
    assertEquals(stackType, slotType);
  }

  @ParameterizedTest
  @MethodSource("arithmeticOpArguments")
  void DIV(Type type, int stackType, Object left, Object right) {
    instance.visitLdcInsn(left);
    instance.visitLdcInsn(right);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(stackType, type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek());

    instance.visitInsn(type.getOpcode(Opcodes.IDIV));
    i = instance.introspect();
    assertEquals(type.getSize(), i.stack.size());
    Object slotType = type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek();
    assertEquals(stackType, slotType);
  }

  @ParameterizedTest
  @MethodSource("arithmeticOpArguments")
  void REM(Type type, int stackType, Object left, Object right) {
    instance.visitLdcInsn(left);
    instance.visitLdcInsn(right);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(stackType, type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek());

    instance.visitInsn(type.getOpcode(Opcodes.IREM));
    i = instance.introspect();
    assertEquals(type.getSize(), i.stack.size());
    Object slotType = type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek();
    assertEquals(stackType, slotType);
  }

  @ParameterizedTest
  @MethodSource("bitwiseOpArguments")
  void OR(Type type, int stackType, Object left, Object right) {
    instance.visitLdcInsn(left);
    instance.visitLdcInsn(right);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(stackType, type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek());

    instance.visitInsn(type.getOpcode(Opcodes.IOR));
    i = instance.introspect();
    assertEquals(type.getSize(), i.stack.size());
    Object slotType = type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek();
    assertEquals(stackType, slotType);
  }

  @ParameterizedTest
  @MethodSource("bitwiseOpArguments")
  void AND(Type type, int stackType, Object left, Object right) {
    instance.visitLdcInsn(left);
    instance.visitLdcInsn(right);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(stackType, type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek());

    instance.visitInsn(type.getOpcode(Opcodes.IAND));
    i = instance.introspect();
    assertEquals(type.getSize(), i.stack.size());
    Object slotType = type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek();
    assertEquals(stackType, slotType);
  }

  @ParameterizedTest
  @MethodSource("bitwiseOpArguments")
  void XOR(Type type, int stackType, Object left, Object right) {
    instance.visitLdcInsn(left);
    instance.visitLdcInsn(right);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(stackType, type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek());

    instance.visitInsn(type.getOpcode(Opcodes.IXOR));
    i = instance.introspect();
    assertEquals(type.getSize(), i.stack.size());
    Object slotType = type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek();
    assertEquals(stackType, slotType);
  }

  @ParameterizedTest
  @MethodSource("bitwiseOpArguments")
  void SHR(Type type, int stackType, Object left, Object right) {
    instance.visitLdcInsn(left);
    instance.visitLdcInsn(right);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(stackType, type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek());

    instance.visitInsn(type.getOpcode(Opcodes.ISHR));
    i = instance.introspect();
    assertEquals(type.getSize(), i.stack.size());
    Object slotType = type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek();
    assertEquals(stackType, slotType);
  }

  @ParameterizedTest
  @MethodSource("bitwiseOpArguments")
  void SHL(Type type, int stackType, Object left, Object right) {
    instance.visitLdcInsn(left);
    instance.visitLdcInsn(right);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(stackType, type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek());

    instance.visitInsn(type.getOpcode(Opcodes.ISHL));
    i = instance.introspect();
    assertEquals(type.getSize(), i.stack.size());
    Object slotType = type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek();
    assertEquals(stackType, slotType);
  }

  @ParameterizedTest
  @MethodSource("bitwiseOpArguments")
  void USHR(Type type, int stackType, Object left, Object right) {
    instance.visitLdcInsn(left);
    instance.visitLdcInsn(right);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(stackType, type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek());

    instance.visitInsn(type.getOpcode(Opcodes.IUSHR));
    i = instance.introspect();
    assertEquals(type.getSize(), i.stack.size());
    Object slotType = type.getSize() == 2 ? i.stack.peekX1() : i.stack.peek();
    assertEquals(stackType, slotType);
  }

  private static Stream<Arguments> arithmeticOpArguments() {
    return Stream.of(
        Arguments.of(Type.BYTE_TYPE, Opcodes.INTEGER, (byte) 1, (byte) 2),
        Arguments.of(Type.CHAR_TYPE, Opcodes.INTEGER, (char) 1, (char) 2),
        Arguments.of(Type.SHORT_TYPE, Opcodes.INTEGER, (short) 1, (short) 2),
        Arguments.of(Type.INT_TYPE, Opcodes.INTEGER, 1, 2),
        Arguments.of(Type.FLOAT_TYPE, Opcodes.FLOAT, 1f, 2f),
        Arguments.of(Type.LONG_TYPE, Opcodes.LONG, 1L, 2L),
        Arguments.of(Type.DOUBLE_TYPE, Opcodes.DOUBLE, 1d, 2d));
  }

  private static Stream<Arguments> bitwiseOpArguments() {
    return Stream.of(
        Arguments.of(Type.BYTE_TYPE, Opcodes.INTEGER, (byte) 1, (byte) 2),
        Arguments.of(Type.CHAR_TYPE, Opcodes.INTEGER, (char) 1, (char) 2),
        Arguments.of(Type.SHORT_TYPE, Opcodes.INTEGER, (short) 1, (short) 2),
        Arguments.of(Type.INT_TYPE, Opcodes.INTEGER, 1, 2),
        Arguments.of(Type.LONG_TYPE, Opcodes.LONG, 1L, 2L));
  }

  @ParameterizedTest
  @MethodSource("numCastArguments")
  void NUMCAST(Type from, Type to, Object value, int stackTypeFrom, int stackTypeTo) {
    instance.visitLdcInsn(value);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(stackTypeFrom, from.getSize() == 2 ? i.stack.peekX1() : i.stack.peek());

    int opcode = -1;
    switch (from.getSort()) {
      case Type.INT:
        {
          switch (to.getSort()) {
            case Type.BYTE:
              opcode = Opcodes.I2B;
              break;
            case Type.CHAR:
              opcode = Opcodes.I2C;
              break;
            case Type.SHORT:
              opcode = Opcodes.I2S;
              break;
            case Type.FLOAT:
              opcode = Opcodes.I2F;
              break;
            case Type.LONG:
              opcode = Opcodes.I2L;
              break;
            case Type.DOUBLE:
              opcode = Opcodes.I2D;
              break;
          }
          break;
        }
      case Type.FLOAT:
        {
          switch (to.getSort()) {
            case Type.INT:
              opcode = Opcodes.F2I;
              break;
            case Type.LONG:
              opcode = Opcodes.F2L;
              break;
            case Type.DOUBLE:
              opcode = Opcodes.F2D;
              break;
          }
          break;
        }
      case Type.LONG:
        {
          switch (to.getSort()) {
            case Type.INT:
              opcode = Opcodes.L2I;
              break;
            case Type.FLOAT:
              opcode = Opcodes.L2F;
              break;
            case Type.DOUBLE:
              opcode = Opcodes.L2D;
              break;
          }
          break;
        }
      case Type.DOUBLE:
        {
          switch (to.getSort()) {
            case Type.INT:
              opcode = Opcodes.D2I;
              break;
            case Type.LONG:
              opcode = Opcodes.D2L;
              break;
            case Type.FLOAT:
              opcode = Opcodes.D2F;
              break;
          }
          break;
        }
    }
    assertNotEquals(-1, opcode);
    instance.visitInsn(opcode);
    i = instance.introspect();
    assertEquals(to.getSize(), i.stack.size());
    Object slotType = to.getSize() == 2 ? i.stack.peekX1() : i.stack.peek();
    assertEquals(stackTypeTo, slotType);
  }

  private static Stream<Arguments> numCastArguments() {
    return Stream.of(
        Arguments.of(Type.INT_TYPE, Type.BYTE_TYPE, 1, Opcodes.INTEGER, Opcodes.INTEGER),
        Arguments.of(Type.INT_TYPE, Type.CHAR_TYPE, 1, Opcodes.INTEGER, Opcodes.INTEGER),
        Arguments.of(Type.INT_TYPE, Type.SHORT_TYPE, 1, Opcodes.INTEGER, Opcodes.INTEGER),
        Arguments.of(Type.INT_TYPE, Type.FLOAT_TYPE, 1, Opcodes.INTEGER, Opcodes.FLOAT),
        Arguments.of(Type.INT_TYPE, Type.LONG_TYPE, 1, Opcodes.INTEGER, Opcodes.LONG),
        Arguments.of(Type.INT_TYPE, Type.DOUBLE_TYPE, 1, Opcodes.INTEGER, Opcodes.DOUBLE),
        Arguments.of(Type.FLOAT_TYPE, Type.INT_TYPE, 1f, Opcodes.FLOAT, Opcodes.INTEGER),
        Arguments.of(Type.FLOAT_TYPE, Type.LONG_TYPE, 1f, Opcodes.FLOAT, Opcodes.LONG),
        Arguments.of(Type.FLOAT_TYPE, Type.DOUBLE_TYPE, 1f, Opcodes.FLOAT, Opcodes.DOUBLE),
        Arguments.of(Type.DOUBLE_TYPE, Type.INT_TYPE, 1d, Opcodes.DOUBLE, Opcodes.INTEGER),
        Arguments.of(Type.DOUBLE_TYPE, Type.LONG_TYPE, 1d, Opcodes.DOUBLE, Opcodes.LONG),
        Arguments.of(Type.DOUBLE_TYPE, Type.FLOAT_TYPE, 1d, Opcodes.DOUBLE, Opcodes.FLOAT),
        Arguments.of(Type.LONG_TYPE, Type.INT_TYPE, 1L, Opcodes.LONG, Opcodes.INTEGER),
        Arguments.of(Type.LONG_TYPE, Type.DOUBLE_TYPE, 1L, Opcodes.LONG, Opcodes.DOUBLE),
        Arguments.of(Type.LONG_TYPE, Type.FLOAT_TYPE, 1L, Opcodes.LONG, Opcodes.FLOAT));
  }

  @ParameterizedTest
  @MethodSource("cmpArguments")
  void CMP(int opcode, Object val) {
    instance.visitLdcInsn(val);
    instance.visitLdcInsn(val);

    instance.visitInsn(opcode);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.stack.size());
    assertEquals(Opcodes.INTEGER, i.stack.peek());
  }

  private static Stream<Arguments> cmpArguments() {
    return Stream.of(
        Arguments.of(Opcodes.LCMP, 1L),
        Arguments.of(Opcodes.DCMPG, 1d),
        Arguments.of(Opcodes.DCMPL, 1d),
        Arguments.of(Opcodes.FCMPG, 1f),
        Arguments.of(Opcodes.FCMPL, 1f));
  }

  @ParameterizedTest
  @MethodSource("retArguments")
  void RET(Type type, Object value) {
    instance.visitLdcInsn(value);

    instance.visitInsn(type.getOpcode(Opcodes.IRETURN));

    assertEquals(0, instance.introspect().stack.size());
  }

  private static Stream<Arguments> retArguments() {
    return Stream.of(
        Arguments.of(Type.BYTE_TYPE, (byte) 1),
        Arguments.of(Type.CHAR_TYPE, (char) 1),
        Arguments.of(Type.SHORT_TYPE, (short) 1),
        Arguments.of(Type.INT_TYPE, 1),
        Arguments.of(Type.FLOAT_TYPE, 1f),
        Arguments.of(Type.DOUBLE_TYPE, 1d),
        Arguments.of(Type.LONG_TYPE, 1L),
        Arguments.of(Type.getType(String.class), "hello"));
  }

  @Test
  void THROW() {
    instance.visitTypeInsn(Opcodes.NEW, Type.getDescriptor(RuntimeException.class));
    instance.visitInsn(Opcodes.ATHROW);

    assertEquals(0, instance.introspect().stack.size());
  }

  @Test
  void ARRAYLENGTH() {
    instance.visitLdcInsn(10); // load array size
    instance.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT); // instantiate new array

    instance.visitInsn(Opcodes.ARRAYLENGTH);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();

    assertEquals(1, i.stack.size());
    assertEquals(Opcodes.INTEGER, i.stack.peek());
  }

  @Test
  void MONITORENTER() {
    instance.visitTypeInsn(Opcodes.NEW, Type.getDescriptor(Object.class));
    instance.visitInsn(Opcodes.MONITORENTER);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();

    assertEquals(0, i.stack.size());
  }

  @Test
  void MONITOREXIT() {
    instance.visitTypeInsn(Opcodes.NEW, Type.getDescriptor(Object.class));
    instance.visitInsn(Opcodes.MONITOREXIT);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();

    assertEquals(0, i.stack.size());
  }
}
