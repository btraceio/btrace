package org.example.btrace.spark.impl;

import org.example.btrace.spark.api.SparkApi;
import org.example.btrace.spark.api.SparkListenerJobStartType$Ext;
import org.openjdk.btrace.core.extensions.Extension;
import org.openjdk.btrace.extension.util.ClassLoadingUtil;
import org.openjdk.btrace.extension.util.MethodHandleCache;

import java.lang.invoke.MethodHandle;

/**
 * Example implementation demonstrating provided-style linking via TCCL/defining loader.
 */
public final class SparkApiImpl extends Extension implements SparkApi {
  private final MethodHandleCache mh = new MethodHandleCache();

  @Override
  public void onJobStart(Object jobStartEvent) {
    if (jobStartEvent == null) return;
    try {
      int jobId = SparkListenerJobStartType$Ext.jobId(jobStartEvent);
      long ts = SparkListenerJobStartType$Ext.time(jobStartEvent);
      // Example: println("Spark job "+jobId+" at "+ts)
    } catch (Throwable ignored) {
      // example: swallow; real impl should log via BTrace runtime logger
    }
  }

  @Override
  public void onStageCompleted(Object stageInfo) {
    if (stageInfo == null) return;
    ClassLoadingUtil.withDefiningLoader(
        stageInfo,
        () -> {
          try {
            Class<?> cls = ClassLoadingUtil.loadFromContext(
                "org.apache.spark.scheduler.StageInfo", stageInfo);
            // Example: read simple properties via cached MethodHandles
            try {
              MethodHandle nameMH = mh.findVirtual(cls, "name", String.class);
              String name = (String) nameMH.invoke(stageInfo);
              // println("Stage completed: "+name)
            } catch (MethodHandleCache.LookupRuntimeException ignored) {}
            try {
              MethodHandle tasksMH = mh.findVirtual(cls, "numTasks", int.class);
              int tasks = (int) tasksMH.invoke(stageInfo);
              // println("Tasks: "+tasks)
            } catch (MethodHandleCache.LookupRuntimeException ignored) {}
          } catch (Throwable ignored) {
            // example: swallow; real impl should log via BTrace runtime logger
          }
          return null;
        });
  }
}
