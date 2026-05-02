package org.example.btrace.spark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.spark.scheduler.SparkListenerJobStart;
import org.apache.spark.scheduler.StageInfo;
import org.example.btrace.spark.api.SparkListenerJobStartType$Ext;
import org.example.btrace.spark.impl.SparkApiImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SparkApiImplTest {
  @BeforeEach
  void reset() {
    StageInfo.reset();
  }

  @Test
  void externalTypeAdapterDispatchesAgainstTestSparkType() {
    SparkListenerJobStart jobStart = new SparkListenerJobStart(17, 42L);

    assertEquals(17, SparkListenerJobStartType$Ext.jobId(jobStart));
    assertEquals(42L, SparkListenerJobStartType$Ext.time(jobStart));
  }

  @Test
  void implementationInvokesReflectiveStageInfoMethods() {
    SparkApiImpl api = new SparkApiImpl();

    api.onStageCompleted(new StageInfo("stage-a", 3));

    assertTrue(StageInfo.nameCalled, "Expected StageInfo.name() to be invoked");
    assertTrue(StageInfo.numTasksCalled, "Expected StageInfo.numTasks() to be invoked");
  }
}
