package org.openjdk.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

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
}
