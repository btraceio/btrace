package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class InstrumentingMethodVisitorTest {
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
              1073741844,
              1073741836,
              1073741826,
              1073741827,
              1073741828,
              1073741829,
              1073741830,
              1073741831,
              1073741832,
              1073741837,
              1073741833,
              1073741834,
              1073741835,
              1073741838,
              1073741839,
              1073741840,
              1073741841,
              1073741842,
              1073741843,
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
    InstrumentingMethodVisitor instance = new InstrumentingMethodVisitor(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test.Test", "()V", mv);
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
    InstrumentingMethodVisitor instance = new InstrumentingMethodVisitor(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "test.Test", "()V", mv);
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
}
