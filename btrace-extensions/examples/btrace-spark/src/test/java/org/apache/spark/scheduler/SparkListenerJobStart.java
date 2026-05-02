package org.apache.spark.scheduler;

public final class SparkListenerJobStart {
  private final int jobId;
  private final long time;

  public SparkListenerJobStart(int jobId, long time) {
    this.jobId = jobId;
    this.time = time;
  }

  public int jobId() {
    return jobId;
  }

  public long time() {
    return time;
  }
}
