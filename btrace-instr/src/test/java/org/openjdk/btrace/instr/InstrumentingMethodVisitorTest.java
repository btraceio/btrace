package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class InstrumentingMethodVisitorTest {
  private static final class LastVisitedFrame {
    final int type;
    final int nLocal;
    final Object[] local;
    final int nStack;
    final Object[] stack;

    public LastVisitedFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
      this.type = type;
      this.nLocal = nLocal;
      this.local = local != null ? Arrays.copyOf(local, nLocal) : null;
      this.nStack = nStack;
      this.stack = stack != null ? Arrays.copyOf(stack, nStack) : null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      LastVisitedFrame that = (LastVisitedFrame) o;
      return type == that.type
          && nLocal == that.nLocal
          && nStack == that.nStack
          && Arrays.equals(local, that.local)
          && Arrays.equals(stack, that.stack);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(type, nLocal, nStack);
      result = 31 * result + Arrays.hashCode(local);
      result = 31 * result + Arrays.hashCode(stack);
      return result;
    }

    @Override
    public String toString() {
      return "LastVisitedFrame{"
          + "type="
          + type
          + ", nLocal="
          + nLocal
          + ", local="
          + Arrays.toString(local)
          + ", nStack="
          + nStack
          + ", stack="
          + Arrays.toString(stack)
          + '}';
    }
  }

  private InstrumentingMethodVisitor instance;
  private LastVisitedFrame lastVisitedFrame = null;

  @BeforeEach
  void setup() throws Exception {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "test.Test", null, null, null);
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test", "()V", null, null);
    instance =
        new InstrumentingMethodVisitor(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "test.Test",
            "()V",
            mv,
            (type, nLocal, local, nStack, stack) -> {
              lastVisitedFrame = new LastVisitedFrame(type, nLocal, local, nStack, stack);
            });
  }

  @AfterEach
  void teardown() {
    lastVisitedFrame = null;
  }

  @Test
  public void testComplexSparkContextInit() {
    Object[] expected = {
      "org/apache/spark/SparkContext",
      "org/apache/spark/SparkConf",
      "java/util/concurrent/ConcurrentMap",
      "scala/Option",
      "java/lang/String",
      0,
      0,
      1,
      "scala/Option",
      "scala/Tuple2",
      "org/apache/spark/scheduler/SchedulerBackend",
      "org/apache/spark/scheduler/TaskScheduler",
      "scala/Tuple2",
      "scala/Tuple2",
      "org/apache/spark/scheduler/SchedulerBackend",
      "org/apache/spark/scheduler/TaskScheduler",
      "scala/Option",
      0,
      1,
      "org/apache/spark/scheduler/SchedulerBackend",
      "scala/Option"
    };
    VariableMapper mapper =
        new VariableMapper(
            2,
            21,
            new int[] {
              0,
              0,
              -2147483628,
              -2147483636,
              -2147483646,
              -2147483645,
              -2147483644,
              -2147483643,
              -2147483642,
              -2147483641,
              -2147483640,
              -2147483635,
              -2147483639,
              -2147483638,
              -2147483637,
              -2147483634,
              -2147483633,
              -2147483632,
              -2147483631,
              -2147483630,
              -2147483629,
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              0,
              0
            });

    Object[] fromFrame = {
      "org/apache/spark/SparkContext",
      "org/apache/spark/SparkConf",
      0,
      0,
      "scala/Option",
      "scala/Tuple2",
      "java/util/concurrent/ConcurrentMap",
      "scala/Option",
      "java/lang/String",
      0,
      0,
      1,
      "scala/Option",
      "scala/Tuple2",
      "scala/Tuple2",
      "org/apache/spark/scheduler/SchedulerBackend",
      "org/apache/spark/scheduler/TaskScheduler",
      "org/apache/spark/scheduler/SchedulerBackend",
      "org/apache/spark/scheduler/TaskScheduler",
      "scala/Option",
      0,
      1,
      "org/apache/spark/scheduler/SchedulerBackend"
    };
    Object[] locals =
        InstrumentingMethodVisitor.computeFrameLocals(2, Arrays.asList(fromFrame), null, mapper);
    assertArrayEquals(expected, locals);
  }

  @Test
  void multiArrayLoad() {
    String expected1 = "[[[Ljava/lang/String;";
    String expected2 = "[[Ljava/lang/String;";
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "test.Test", null, null, null);
    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test", "()V", null, null);
    InstrumentingMethodVisitor instance = new InstrumentingMethodVisitor(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test.Test", "test", "()V", mv);
    mv = instance;

    mv.visitLdcInsn(1);
    mv.visitLdcInsn(1);
    mv.visitLdcInsn(1);
    mv.visitMultiANewArrayInsn("[[[Ljava/lang/String;", 3);
    assertEquals(expected1, instance.introspect().stack.peek());

    mv.visitLdcInsn(0);
    mv.visitInsn(Opcodes.AALOAD);
    assertEquals(expected2, instance.introspect().stack.peek());

    mv.visitMaxs(3, 0);
    mv.visitEnd();

  }

  @ParameterizedTest
  @MethodSource("typeValues")
  void storeAsNew(Object value, Type type) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "test.Test", null, null, null);
    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test", "()V", null, null);
    InstrumentingMethodVisitor instance = new InstrumentingMethodVisitor(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test.Test", "test", "()V", mv);
    mv = instance;

    mv.visitLdcInsn(value);
    Object expected = instance.introspect().stack.peek();
    int idx = instance.storeAsNew();
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.newLocals.size());
    assertEquals(0, i.stack.size());
    instance.visitVarInsn(type.getOpcode(Opcodes.ILOAD), idx);
    assertEquals(expected, instance.introspect().stack.peek());
  }

  private static Stream<Arguments> typeValues() {
    return Stream.of(
            Arguments.of((byte)1, Type.BYTE_TYPE),
            Arguments.of((short)1, Type.SHORT_TYPE),
            Arguments.of((char)1, Type.CHAR_TYPE),
            Arguments.of((int)1, Type.INT_TYPE),
            Arguments.of(true, Type.BOOLEAN_TYPE),
            Arguments.of((long)1, Type.LONG_TYPE),
            Arguments.of((float)1, Type.FLOAT_TYPE),
            Arguments.of((double)1, Type.DOUBLE_TYPE)
    );
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
    assertEquals("[Ljava/lang/String;", instance.introspect().stack.peek());
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
    instance.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
    assertEquals("[I", instance.introspect().stack.peek());
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
    instance.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_FLOAT);
    assertEquals("[F", instance.introspect().stack.peek());
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
    instance.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
    assertEquals("[B", instance.introspect().stack.peek());
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
    instance.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_CHAR);
    assertEquals("[C", instance.introspect().stack.peek());
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
    instance.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_SHORT);
    assertEquals("[S", instance.introspect().stack.peek());
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
    instance.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_LONG);
    assertEquals("[J", instance.introspect().stack.peek());
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
    instance.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_DOUBLE);
    assertEquals("[D", instance.introspect().stack.peek());

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
  @ValueSource(
      ints = {
        Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM,
        Opcodes.IOR, Opcodes.IXOR, Opcodes.ISHR, Opcodes.ISHL, Opcodes.IUSHR
      })
  void I_binary_ops(int opcode) {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn(2);
    instance.visitInsn(opcode);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.stack.size());
    assertEquals(Opcodes.INTEGER, i.stack.peek());
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        Opcodes.LADD, Opcodes.LSUB, Opcodes.LMUL, Opcodes.LDIV, Opcodes.LREM,
        Opcodes.LOR, Opcodes.LXOR, Opcodes.LSHR, Opcodes.LSHL, Opcodes.LUSHR
      })
  void L_binary_ops(int opcode) {
    instance.visitLdcInsn(1L);
    instance.visitLdcInsn(2L);
    instance.visitInsn(opcode);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.LONG, i.stack.peek(1));
  }

  @ParameterizedTest
  @ValueSource(ints = {Opcodes.FADD, Opcodes.FSUB, Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM})
  void F_binary_ops(int opcode) {
    instance.visitLdcInsn(1f);
    instance.visitLdcInsn(2f);
    instance.visitInsn(opcode);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.stack.size());
    assertEquals(Opcodes.FLOAT, i.stack.peek());
  }

  @ParameterizedTest
  @ValueSource(ints = {Opcodes.DADD, Opcodes.DSUB, Opcodes.DMUL, Opcodes.DDIV, Opcodes.DREM})
  void D_binary_ops(int opcode) {
    instance.visitLdcInsn(1d);
    instance.visitLdcInsn(2d);
    instance.visitInsn(opcode);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(InstrumentingMethodVisitor.TOP_EXT, i.stack.peek());
    assertEquals(Opcodes.DOUBLE, i.stack.peek(1));
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        Opcodes.I2B, Opcodes.I2C, Opcodes.I2D, Opcodes.I2F, Opcodes.I2L, Opcodes.I2S,
        Opcodes.F2I, Opcodes.F2D, Opcodes.F2L, Opcodes.L2I, Opcodes.L2F, Opcodes.L2D,
        Opcodes.D2I, Opcodes.D2F, Opcodes.D2L
      })
  void testConversion(int opcode) {
    Object[] args = conversionTypes(opcode);

    instance.visitLdcInsn(args[0]);
    instance.visitInsn(opcode);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    int expectedStackSize = (int) args[1];
    assertEquals(expectedStackSize, i.stack.size());
    if (expectedStackSize == 1) {
      assertEquals(args[2], i.stack.peek());
    } else {
      for (int p = 0; p < expectedStackSize; p++) {
        assertEquals(((Object[]) args[2])[p], i.stack.peek(p));
      }
    }
  }

  @Test
  void LCMP() {
    instance.visitLdcInsn(1L);
    instance.visitLdcInsn(2L);

    instance.visitInsn(Opcodes.LCMP);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.stack.size());
    assertEquals(Opcodes.INTEGER, i.stack.pop());
  }

  @ParameterizedTest
  @ValueSource(ints = {Opcodes.FCMPG, Opcodes.FCMPL})
  void FCMP(int opcode) {
    instance.visitLdcInsn(1f);
    instance.visitLdcInsn(2f);

    instance.visitInsn(opcode);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.stack.size());
    assertEquals(Opcodes.INTEGER, i.stack.pop());
  }

  @ParameterizedTest
  @ValueSource(ints = {Opcodes.DCMPG, Opcodes.DCMPL})
  void DCMP(int opcode) {
    instance.visitLdcInsn(1d);
    instance.visitLdcInsn(2d);

    instance.visitInsn(opcode);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.stack.size());
    assertEquals(Opcodes.INTEGER, i.stack.pop());
  }

  @ParameterizedTest
  @ValueSource(
      ints = {Opcodes.ARETURN, Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN})
  void RET(int opcode) {
    switch (opcode) {
      case Opcodes.ARETURN:
        instance.visitLdcInsn("hello");
        break;
      case Opcodes.IRETURN:
        instance.visitLdcInsn(1);
        break;
      case Opcodes.FRETURN:
        instance.visitLdcInsn(1f);
        break;
      case Opcodes.LRETURN:
        instance.visitLdcInsn(1L);
        break;
      case Opcodes.DRETURN:
        instance.visitLdcInsn(1d);
        break;
      default:
        throw new RuntimeException("Unexpected opcode: " + opcode);
    }
    instance.visitInsn(opcode);

    assertEquals(0, instance.introspect().stack.size());
  }

  @Test
  void ATHROW() {
    instance.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");

    instance.visitInsn(Opcodes.ATHROW);
    assertEquals(0, instance.introspect().stack.size());
  }

  @Test
  void ARRAYLENGTH() {
    instance.visitLdcInsn(1);
    instance.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);

    instance.visitInsn(Opcodes.ARRAYLENGTH);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.stack.size());
    assertEquals(Opcodes.INTEGER, i.stack.peek());
  }

  @ParameterizedTest
  @ValueSource(ints = {Opcodes.MONITORENTER, Opcodes.MONITOREXIT})
  void MONITOR(int opcode) {
    instance.visitLdcInsn("this");

    instance.visitInsn(opcode);
    assertEquals(0, instance.introspect().stack.size());
  }

  @ParameterizedTest
  @ValueSource(ints = {Opcodes.BIPUSH, Opcodes.SIPUSH})
  void PUSH(int opcode) {
    instance.visitIntInsn(opcode, 0);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.stack.size());
    assertEquals(Opcodes.INTEGER, i.stack.peek());
  }

  @Test
  void INSTANCEOF() {
    instance.visitLdcInsn("hello");
    instance.visitTypeInsn(Opcodes.INSTANCEOF, "Ljava/util/List;");

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.stack.size());
    assertEquals(Opcodes.INTEGER, i.stack.peek());
  }

  @Test
  void CHECKCAST() {
    String expectedType = "Ljava/util/List;";
    instance.visitLdcInsn("hello");
    instance.visitTypeInsn(Opcodes.CHECKCAST, expectedType);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.stack.size());
    assertEquals(expectedType, i.stack.peek());
  }

  @ParameterizedTest
  @ValueSource(ints = {Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.LLOAD, Opcodes.DLOAD})
  void VAR_LOAD(int opcode) {
    instance.visitVarInsn(opcode, 1);

    Object[] expected = {};
    switch (opcode) {
      case Opcodes.ILOAD:
        expected = new Object[] {Opcodes.INTEGER};
        break;
      case Opcodes.FLOAD:
        expected = new Object[] {Opcodes.FLOAT};
        break;
      case Opcodes.LLOAD:
        expected = new Object[] {InstrumentingMethodVisitor.TOP_EXT, Opcodes.LONG};
        break;
      case Opcodes.DLOAD:
        expected = new Object[] {InstrumentingMethodVisitor.TOP_EXT, Opcodes.DOUBLE};
        break;
    }
    if (expected.length == 0) {
      throw new RuntimeException("Unexpected opcode: " + opcode);
    }
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    for (int p = 0; p < expected.length; p++) {
      assertEquals(
          expected[p],
          i.stack.peek(p),
          "Expected slot: " + expected[p] + ", got " + i.stack.peek(p));
    }
  }

  @Test
  void NEWARRAY_BOOLEAN() {
    instance.visitLdcInsn(1);
    instance.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN);

    assertEquals("[Z", instance.introspect().stack.peek());
  }

  @Test
  void MULTIANEWARRAY() {
    String expected1 = "[[[Ljava/lang/String;";
    String expected2 = "[[Ljava/lang/String;";
    instance.visitLdcInsn(1);
    instance.visitLdcInsn(1);
    instance.visitLdcInsn(1);
    instance.visitMultiANewArrayInsn("[[[Ljava/lang/String;", 3);
    assertEquals(expected1, instance.introspect().stack.peek());

    instance.visitLdcInsn(0);
    instance.visitInsn(Opcodes.AALOAD);
    assertEquals(expected2, instance.introspect().stack.peek());
  }

  private static Object[] conversionTypes(int conversionOp) {
    switch (conversionOp) {
      case Opcodes.I2B:
      case Opcodes.I2S:
      case Opcodes.I2C:
        return new Object[] {1, 1, Opcodes.INTEGER};
      case Opcodes.I2D:
        return new Object[] {
          1, 2, new Object[] {InstrumentingMethodVisitor.TOP_EXT, Opcodes.DOUBLE}
        };
      case Opcodes.I2F:
        return new Object[] {1, 1, Opcodes.FLOAT};
      case Opcodes.I2L:
        return new Object[] {1, 2, new Object[] {InstrumentingMethodVisitor.TOP_EXT, Opcodes.LONG}};
      case Opcodes.F2I:
        return new Object[] {1f, 1, Opcodes.INTEGER};
      case Opcodes.F2D:
        return new Object[] {
          1f, 2, new Object[] {InstrumentingMethodVisitor.TOP_EXT, Opcodes.DOUBLE}
        };
      case Opcodes.F2L:
        return new Object[] {
          1f, 2, new Object[] {InstrumentingMethodVisitor.TOP_EXT, Opcodes.LONG}
        };
      case Opcodes.L2I:
        return new Object[] {1L, 1, Opcodes.INTEGER};
      case Opcodes.L2F:
        return new Object[] {1L, 1, Opcodes.FLOAT};
      case Opcodes.L2D:
        return new Object[] {
          1L, 2, new Object[] {InstrumentingMethodVisitor.TOP_EXT, Opcodes.DOUBLE}
        };
      case Opcodes.D2I:
        return new Object[] {1d, 1, Opcodes.INTEGER};
      case Opcodes.D2F:
        return new Object[] {1d, 1, Opcodes.FLOAT};
      case Opcodes.D2L:
        return new Object[] {
          1d, 2, new Object[] {InstrumentingMethodVisitor.TOP_EXT, Opcodes.LONG}
        };
    }
    return null;
  }

  @ParameterizedTest
  @MethodSource("typeValues")
  void storeAsNew(Object value, Type type) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    cw.visit(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "test.Test", null, null, null);
    MethodVisitor mv =
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test", "()V", null, null);
    InstrumentingMethodVisitor instance =
        new InstrumentingMethodVisitor(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test.Test", "()V", mv);
    mv = instance;

    mv.visitLdcInsn(value);
    Object expected = instance.introspect().stack.peek();
    int idx = instance.storeAsNew();
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.newLocals.size());
    assertEquals(0, i.stack.size());
    instance.visitVarInsn(type.getOpcode(Opcodes.ILOAD), idx);
    assertEquals(expected, instance.introspect().stack.peek());
  }

  @ParameterizedTest
  @ValueSource(ints = {Opcodes.F_FULL, Opcodes.F_NEW})
  void visitFrameNew(int opcode) {
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 1);

    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitVarInsn(Opcodes.ALOAD, 1);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(2, i.lvTypes.size());

    Object[] newStack = new Object[] {Opcodes.FLOAT};
    Object[] newLocals = new Object[] {Opcodes.FLOAT};

    instance.visitFrame(opcode, newLocals.length, newLocals, newStack.length, newStack);

    i = instance.introspect();

    assertArrayEquals(newStack, i.stack.toArray());
    assertArrayEquals(newLocals, i.lvTypes.toArray());
  }

  @ParameterizedTest
  @ValueSource(ints = {Opcodes.F_FULL, Opcodes.F_NEW})
  void visitFrameNewWithNewVar(int opcode) {
    instance.visitLdcInsn(-1);
    int newIdx = instance.storeAsNew();
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 1);

    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitVarInsn(Opcodes.ALOAD, 1);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(3, i.lvTypes.size());

    Object[] newStack = new Object[] {Opcodes.FLOAT};
    Object[] newLocals = new Object[] {Opcodes.FLOAT};

    instance.visitFrame(opcode, newLocals.length, newLocals, newStack.length, newStack);

    i = instance.introspect();

    assertArrayEquals(newStack, i.stack.toArray());
    assertEquals(Opcodes.INTEGER, i.lvTypes.getType(0));
    Object[] toCheck = new Object[i.lvTypes.size() - 1];
    System.arraycopy(i.lvTypes.toArray(), 1, toCheck, 0, toCheck.length);
    assertArrayEquals(newLocals, toCheck);
  }

  @Test
  void visitFrameSame() {
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 1);

    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitVarInsn(Opcodes.ALOAD, 1);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(2, i.lvTypes.size());

    Object[] oldLocals = i.lvTypes.toArray();
    Object[] newStack = new Object[] {Opcodes.FLOAT};
    Object[] newLocals = new Object[] {Opcodes.FLOAT};

    instance.visitFrame(Opcodes.F_NEW, i.lvTypes.size(), i.lvTypes.toArray(), 0, null);
    instance.visitIincInsn(0, 1);
    instance.visitFrame(Opcodes.F_SAME, newLocals.length, newLocals, newStack.length, newStack);

    i = instance.introspect();

    assertArrayEquals(new Object[0], i.stack.toArray());
    assertArrayEquals(oldLocals, i.lvTypes.toArray());
  }

  @Test
  void visitFrameSameWithNewVar() {
    instance.visitLdcInsn(-1);
    int newIdx = instance.storeAsNew();
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 1);

    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitVarInsn(Opcodes.ALOAD, 1);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(3, i.lvTypes.size());

    Object[] origLocals = new Object[] {Opcodes.INTEGER, "java/lang/String"};
    Object[] newStack = new Object[] {Opcodes.FLOAT};
    Object[] newLocals = new Object[] {Opcodes.FLOAT};

    instance.visitFrame(Opcodes.F_NEW, origLocals.length, origLocals, 0, null);
    Object[] frameLocals = instance.introspect().lvTypes.toArray();

    instance.visitIincInsn(0, 1);
    instance.visitFrame(Opcodes.F_SAME, newLocals.length, newLocals, newStack.length, newStack);

    i = instance.introspect();

    assertArrayEquals(new Object[0], i.stack.toArray());
    assertArrayEquals(frameLocals, i.lvTypes.toArray());
  }

  @Test
  void visitFrameSame1() {
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 1);

    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitVarInsn(Opcodes.ALOAD, 1);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(2, i.lvTypes.size());

    Object[] oldLocals = i.lvTypes.toArray();
    Object[] newStack = new Object[] {Opcodes.FLOAT};
    Object[] newLocals = new Object[] {Opcodes.FLOAT};

    instance.visitFrame(Opcodes.F_NEW, i.lvTypes.size(), i.lvTypes.toArray(), 0, null);
    instance.visitIincInsn(0, 1);
    instance.visitFrame(Opcodes.F_SAME1, newLocals.length, newLocals, newStack.length, newStack);

    i = instance.introspect();

    assertEquals(1, i.stack.size());
    assertEquals(Opcodes.FLOAT, i.stack.peek());
    assertArrayEquals(oldLocals, i.lvTypes.toArray());
  }

  @Test
  void visitFrameSame1WithNewVar() {
    instance.visitLdcInsn(-1);
    int newIdx = instance.storeAsNew();
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 1);

    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitVarInsn(Opcodes.ALOAD, 1);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(3, i.lvTypes.size());

    Object[] origLocals = new Object[] {Opcodes.INTEGER, "java/lang/String"};
    Object[] newStack = new Object[] {Opcodes.FLOAT};
    Object[] newLocals = new Object[] {Opcodes.FLOAT};

    instance.visitFrame(Opcodes.F_NEW, origLocals.length, origLocals, 0, null);
    Object[] frameLocals = instance.introspect().lvTypes.toArray();

    instance.visitIincInsn(0, 1);
    instance.visitFrame(Opcodes.F_SAME1, newLocals.length, newLocals, newStack.length, newStack);

    i = instance.introspect();

    assertEquals(1, i.stack.size());
    assertEquals(Opcodes.FLOAT, i.stack.peek());
    assertArrayEquals(frameLocals, i.lvTypes.toArray());
  }

  @Test
  void visitFrameAppend() {
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 1);

    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitVarInsn(Opcodes.ALOAD, 1);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(2, i.lvTypes.size());

    Object[] oldLocals = i.lvTypes.toArray();
    Object[] newStack = new Object[] {Opcodes.FLOAT};
    Object[] newLocals = new Object[] {Opcodes.FLOAT};

    instance.visitFrame(Opcodes.F_NEW, i.lvTypes.size(), i.lvTypes.toArray(), 0, null);
    instance.visitIincInsn(0, 1);
    instance.visitLdcInsn(1f);
    instance.visitVarInsn(Opcodes.FSTORE, 2);
    instance.visitFrame(Opcodes.F_APPEND, newLocals.length, newLocals, newStack.length, newStack);

    i = instance.introspect();

    assertEquals(0, i.stack.size());
    assertEquals(oldLocals.length + newLocals.length, i.lvTypes.size());
    assertEquals(Opcodes.FLOAT, i.lvTypes.getType(2));
  }

  @Test
  void visitFrameAppendWithNewVar() {
    instance.visitLdcInsn(-1);
    int newIdx = instance.storeAsNew();
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 1);

    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitVarInsn(Opcodes.ALOAD, 1);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(3, i.lvTypes.size());

    Object[] origLocals = new Object[] {Opcodes.INTEGER, "java/lang/String"};
    Object[] newStack = new Object[] {Opcodes.FLOAT};
    Object[] newLocals = new Object[] {Opcodes.FLOAT};

    instance.visitFrame(Opcodes.F_NEW, origLocals.length, origLocals, 0, null);
    Object[] frameLocals = instance.introspect().lvTypes.toArray();

    instance.visitIincInsn(0, 1);
    instance.visitLdcInsn(1f);
    instance.visitVarInsn(Opcodes.FSTORE, 2);
    instance.visitFrame(Opcodes.F_APPEND, newLocals.length, newLocals, newStack.length, newStack);

    i = instance.introspect();

    assertEquals(0, i.stack.size());
    assertEquals(frameLocals.length + newLocals.length, i.lvTypes.size());
    assertEquals(Opcodes.FLOAT, i.lvTypes.getType(3));
  }

  @Test
  void visitFrameChop() {
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 1);

    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitVarInsn(Opcodes.ALOAD, 1);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(2, i.lvTypes.size());

    Object[] newStack = new Object[] {Opcodes.FLOAT};
    Object[] newLocals = new Object[] {Opcodes.FLOAT};

    instance.visitFrame(Opcodes.F_NEW, i.lvTypes.size(), i.lvTypes.toArray(), 0, null);
    instance.visitIincInsn(0, 1);
    instance.visitFrame(Opcodes.F_CHOP, 1, newLocals, newStack.length, newStack);

    i = instance.introspect();

    assertEquals(0, i.stack.size());
    assertEquals(1, i.lvTypes.size());
  }

  @Test
  void visitFrameChopWithNewVar() {
    instance.visitLdcInsn(-1);
    int newIdx = instance.storeAsNew();
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 1);

    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitVarInsn(Opcodes.ALOAD, 1);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(2, i.stack.size());
    assertEquals(3, i.lvTypes.size());

    Object[] origLocals = new Object[] {Opcodes.INTEGER, "java/lang/String"};
    Object[] newStack = new Object[] {Opcodes.FLOAT};
    Object[] newLocals = new Object[] {Opcodes.FLOAT};

    instance.visitFrame(Opcodes.F_NEW, origLocals.length, origLocals, 0, null);

    instance.visitIincInsn(0, 1);
    instance.visitFrame(Opcodes.F_CHOP, 1, newLocals, newStack.length, newStack);

    i = instance.introspect();

    assertEquals(0, i.stack.size());
    assertEquals(2, i.lvTypes.size());
  }

  private static Stream<Arguments> typeValues() {
    return Stream.of(
        Arguments.of((byte) 1, Type.BYTE_TYPE),
        Arguments.of((short) 1, Type.SHORT_TYPE),
        Arguments.of((char) 1, Type.CHAR_TYPE),
        Arguments.of((int) 1, Type.INT_TYPE),
        Arguments.of(true, Type.BOOLEAN_TYPE),
        Arguments.of((long) 1, Type.LONG_TYPE),
        Arguments.of((float) 1, Type.FLOAT_TYPE),
        Arguments.of((double) 1, Type.DOUBLE_TYPE));
  }

  @Test
  void visitLookupSwitch() {
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitInsn(Opcodes.DUP);

    Label target = new Label();
    instance.visitLookupSwitchInsn(target, new int[] {1}, new Label[] {target});

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.jumpTable.size());
    assertEquals(target, i.jumpTable.keySet().iterator().next());
    instance.visitLdcInsn(1f);
    instance.visitVarInsn(Opcodes.FSTORE, 0);
    instance.visitLdcInsn("hello");

    instance.visitLabel(target);
    InstrumentingMethodVisitor.Introspection i1 = instance.introspect();

    assertArrayEquals(i.stack.toArray(), i1.stack.toArray());
    assertArrayEquals(i.lvTypes.toArray(), i1.lvTypes.toArray());
  }

  @Test
  void visitLookupTable() {
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitInsn(Opcodes.DUP);

    Label target = new Label();
    instance.visitTableSwitchInsn(1, 1, target, target);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.jumpTable.size());
    assertEquals(target, i.jumpTable.keySet().iterator().next());
    instance.visitLdcInsn(1f);
    instance.visitVarInsn(Opcodes.FSTORE, 0);
    instance.visitLdcInsn("hello");

    instance.visitLabel(target);
    InstrumentingMethodVisitor.Introspection i1 = instance.introspect();

    assertArrayEquals(i.stack.toArray(), i1.stack.toArray());
    assertArrayEquals(i.lvTypes.toArray(), i1.lvTypes.toArray());
  }

  @ParameterizedTest
  @ValueSource(ints = {Opcodes.GOTO, Opcodes.JSR})
  void visitJump(int opcode) {
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitVarInsn(Opcodes.ILOAD, 0);

    Label target = new Label();
    instance.visitJumpInsn(opcode, target);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.jumpTable.size());
    assertEquals(target, i.jumpTable.keySet().iterator().next());
    instance.visitLdcInsn(1f);
    instance.visitVarInsn(Opcodes.FSTORE, 0);
    instance.visitLdcInsn("hello");

    instance.visitLabel(target);
    InstrumentingMethodVisitor.Introspection i1 = instance.introspect();

    assertArrayEquals(i.stack.toArray(), i1.stack.toArray());
    assertArrayEquals(i.lvTypes.toArray(), i1.lvTypes.toArray());
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        Opcodes.IFEQ,
        Opcodes.IFGT,
        Opcodes.IFGE,
        Opcodes.IFLT,
        Opcodes.IFLE,
        Opcodes.IFNE,
        Opcodes.IFNONNULL,
        Opcodes.IFNULL
      })
  void visitJumpInsnUnary(int opcode) {
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitInsn(Opcodes.DUP);

    Label target = new Label();
    instance.visitJumpInsn(opcode, target);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.jumpTable.size());
    assertEquals(target, i.jumpTable.keySet().iterator().next());
    instance.visitLdcInsn(1f);
    instance.visitVarInsn(Opcodes.FSTORE, 0);
    instance.visitLdcInsn("hello");

    instance.visitLabel(target);
    InstrumentingMethodVisitor.Introspection i1 = instance.introspect();

    assertArrayEquals(i.stack.toArray(), i1.stack.toArray());
    assertArrayEquals(i.lvTypes.toArray(), i1.lvTypes.toArray());
  }

  @ParameterizedTest
  @ValueSource(
      ints = {
        Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE, Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPGE,
        Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPNE
      })
  void visitJumpInsnBinary(int opcode) {
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitVarInsn(Opcodes.ILOAD, 0);
    instance.visitInsn(Opcodes.DUP2);

    Label target = new Label();
    instance.visitJumpInsn(opcode, target);

    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.jumpTable.size());
    assertEquals(target, i.jumpTable.keySet().iterator().next());
    instance.visitLdcInsn(1f);
    instance.visitVarInsn(Opcodes.FSTORE, 0);
    instance.visitLdcInsn("hello");

    instance.visitLabel(target);
    InstrumentingMethodVisitor.Introspection i1 = instance.introspect();

    assertArrayEquals(i.stack.toArray(), i1.stack.toArray());
    assertArrayEquals(i.lvTypes.toArray(), i1.lvTypes.toArray());
  }

  @Test
  void visitTryCatch() {
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 0);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 1);
    instance.visitVarInsn(Opcodes.ALOAD, 1);
    instance.visitVarInsn(Opcodes.ILOAD, 0);

    Label start = new Label();
    Label end = new Label();
    Label over = new Label();
    instance.visitTryCatchBlock(start, end, end, "java/lang/InterruptedException");
    instance.visitLabel(start);
    instance.visitLdcInsn(1f);
    instance.visitInsn(Opcodes.DUP);
    instance.visitVarInsn(Opcodes.FSTORE, 2);
    InstrumentingMethodVisitor.Introspection i1 = instance.introspect();
    assertEquals(Opcodes.FLOAT, i1.stack.peek());
    assertEquals(3, i1.lvTypes.size());
    assertEquals(Opcodes.FLOAT, i1.lvTypes.getType(2));

    instance.visitJumpInsn(Opcodes.GOTO, over);
    instance.visitLabel(end);
    InstrumentingMethodVisitor.Introspection i2 = instance.introspect();
    assertEquals(3, i2.stack.size());
    assertEquals("java/lang/Throwable", i2.stack.peek());

    instance.visitInsn(Opcodes.POP);
    instance.visitLabel(over);
    InstrumentingMethodVisitor.Introspection i3 = instance.introspect();
    assertEquals(3, i3.stack.size());
    assertEquals(Opcodes.FLOAT, i3.stack.peek());
  }

  @Test
  void visitMethodInsnVoid() {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn("hello");

    String desc = "(ILjava/langString;)V";
    instance.visitMethodInsn(Opcodes.INVOKESTATIC, "test/Main", "main", desc, false);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(0, i.stack.size());

    instance.visitVarInsn(Opcodes.ALOAD, 0);
    instance.visitLdcInsn(1);
    instance.visitLdcInsn("hello");
    instance.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "test/Main", "main", desc, false);
    assertEquals(0, i.stack.size());

    instance.visitVarInsn(Opcodes.ALOAD, 0);
    instance.visitLdcInsn(1);
    instance.visitLdcInsn("hello");
    instance.visitMethodInsn(Opcodes.INVOKEINTERFACE, "test/Main", "main", desc, true);
    assertEquals(0, i.stack.size());

    instance.visitVarInsn(Opcodes.ALOAD, 0);
    instance.visitLdcInsn(1);
    instance.visitLdcInsn("hello");
    instance.visitMethodInsn(Opcodes.INVOKESPECIAL, "test/Main", "<init>", desc, false);
    assertEquals(0, i.stack.size());
  }

  @Test
  void visitMethodInsnRet() {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn("hello");

    String desc = "(ILjava/lang/String;)I";
    instance.visitMethodInsn(Opcodes.INVOKESTATIC, "test/Main", "main", desc, false);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.stack.size());

    instance.visitVarInsn(Opcodes.ALOAD, 0);
    instance.visitLdcInsn(1);
    instance.visitLdcInsn("hello");
    instance.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "test/Main", "main", desc, false);
    assertEquals(1, i.stack.size());

    instance.visitVarInsn(Opcodes.ALOAD, 0);
    instance.visitLdcInsn(1);
    instance.visitLdcInsn("hello");
    instance.visitMethodInsn(Opcodes.INVOKEINTERFACE, "test/Main", "main", desc, true);
    assertEquals(1, i.stack.size());
  }

  @Test
  void visitDynamicMethodInsnVoid() {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn("hello");

    Handle h = new Handle(Opcodes.H_INVOKESTATIC, "bootstrap.Bill", "bootstrap", "(IZ)V", false);
    String desc = "(ILjava/langString;)V";
    instance.visitInvokeDynamicInsn("main", desc, h, 1, false);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(0, i.stack.size());
  }

  @Test
  void visitDynamicMethodInsnRet() {
    instance.visitLdcInsn(1);
    instance.visitLdcInsn("hello");

    Handle h = new Handle(Opcodes.H_INVOKESTATIC, "bootstrap.Bill", "bootstrap", "(IZ)V", false);
    String desc = "(ILjava/lang/String;)I";
    instance.visitInvokeDynamicInsn("main", desc, h, 1, false);
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(1, i.stack.size());
  }

  @ParameterizedTest
  @ValueSource(ints = {Opcodes.GETFIELD, Opcodes.GETSTATIC, Opcodes.PUTFIELD, Opcodes.PUTSTATIC})
  void visitFieldInsn(int opcode) {
    if (opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD) {
      instance.visitVarInsn(Opcodes.ALOAD, 0); // load 'this'
    }
    if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
      instance.visitVarInsn(Opcodes.ILOAD, 1); // load value
    }
    instance.visitFieldInsn(opcode, "test/Main", "f", "I");
    InstrumentingMethodVisitor.Introspection i = instance.introspect();
    assertEquals(opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC ? 0 : 1, i.stack.size());
  }

  @Test
  void insertFrameReplaceStack() {
    Label l = new Label();
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 1);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 2);
    instance.visitVarInsn(Opcodes.ALOAD, 2);
    instance.visitVarInsn(Opcodes.ILOAD, 1);
    instance.visitJumpInsn(Opcodes.GOTO, l); // label needs to have jump target
    instance.visitLabel(l);

    instance.insertFrameReplaceStack(l, Type.getType(String.class), Type.FLOAT_TYPE);
    LastVisitedFrame expected =
        new LastVisitedFrame(
            Opcodes.F_NEW,
            2,
            new Object[] {Opcodes.INTEGER, "java/lang/String"},
            2,
            new Object[] {"java/lang/String", Opcodes.FLOAT});
    assertEquals(expected, lastVisitedFrame);
  }

  @Test
  void insertFrameAppendStack() {
    Label l = new Label();
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 1);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 2);
    instance.visitVarInsn(Opcodes.ALOAD, 2);
    instance.visitVarInsn(Opcodes.ILOAD, 1);
    instance.visitJumpInsn(Opcodes.GOTO, l); // label needs to have jump target
    instance.visitLabel(l);

    instance.insertFrameAppendStack(l, Type.FLOAT_TYPE);
    LastVisitedFrame expected =
        new LastVisitedFrame(
            Opcodes.F_NEW,
            2,
            new Object[] {Opcodes.INTEGER, "java/lang/String"},
            3,
            new Object[] {"java/lang/String", Opcodes.INTEGER, Opcodes.FLOAT});
    assertEquals(expected, lastVisitedFrame);
  }

  @Test
  void insertFrameSameStack() {
    Label l = new Label();
    instance.visitLdcInsn(1);
    instance.visitVarInsn(Opcodes.ISTORE, 1);
    instance.visitLdcInsn("hello");
    instance.visitVarInsn(Opcodes.ASTORE, 2);
    instance.visitVarInsn(Opcodes.ALOAD, 2);
    instance.visitVarInsn(Opcodes.ILOAD, 1);
    instance.visitJumpInsn(Opcodes.GOTO, l); // label needs to have jump target
    instance.visitLabel(l);

    instance.insertFrameSameStack(l);
    LastVisitedFrame expected =
        new LastVisitedFrame(
            Opcodes.F_NEW,
            2,
            new Object[] {Opcodes.INTEGER, "java/lang/String"},
            2,
            new Object[] {"java/lang/String", Opcodes.INTEGER});
    assertEquals(expected, lastVisitedFrame);
  }
}
