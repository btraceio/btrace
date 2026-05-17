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
