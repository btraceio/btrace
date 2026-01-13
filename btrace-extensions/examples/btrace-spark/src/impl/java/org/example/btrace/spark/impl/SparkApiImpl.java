package org.example.btrace.spark.impl;

import org.example.btrace.spark.api.SparkApi;
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
    ClassLoadingUtil.withDefiningLoader(
        jobStartEvent,
        () -> {
          try {
            Class<?> evtCls = ClassLoadingUtil.loadFromContext(
                "org.apache.spark.scheduler.SparkListenerJobStart", jobStartEvent);
            MethodHandle getJobId = mh.findVirtual(evtCls, "jobId", int.class);
            int jobId = (int) getJobId.invoke(jobStartEvent);
            // Demonstrate multiple cached lookups (if method exists)
            // For illustration purposes; real implementations should catch Lookup errors
            // and log a single warning rather than per-event.
            try {
              MethodHandle submissionTime = mh.findVirtual(evtCls, "time", long.class);
              long ts = (long) submissionTime.invoke(jobStartEvent);
              // Example: println("Spark job "+jobId+" at "+ts)
            } catch (MethodHandleCache.LookupRuntimeException ignored) {
              // optional method; ignore if absent
            }
            // TODO: emit to BTrace runtime (left minimal for example)
          } catch (Throwable ignored) {
            // example: swallow; real impl should log via BTrace runtime logger
          }
          return null;
        });
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
          }
          return null;
        });
  }
}
