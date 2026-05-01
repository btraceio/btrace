package org.example.btrace.spark.api;

/**
 * Spark example API exposed to BTrace probes. Uses only simple types and Object hand-off
 * to avoid coupling to application classes on bootstrap.
 */
public interface SparkApi {
  void onJobStart(Object jobStartEvent);
  void onStageCompleted(Object stageInfo);
}

