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
package io.btrace.gpu;

import io.btrace.core.extensions.Extension;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe GPU compute and inference tracking with lock-free statistics. */
public final class GpuBridgeServiceImpl extends Extension implements GpuBridgeService {

  private final Map<String, ModelStats> modelStats = new ConcurrentHashMap<>();
  private final Map<String, DeviceMemory> deviceMemory = new ConcurrentHashMap<>();
  private final Map<String, NativeCallStats> nativeStats = new ConcurrentHashMap<>();

  private final ThreadLocal<InferenceRecordImpl> inferenceRecordPool =
      ThreadLocal.withInitial(InferenceRecordImpl::new);

  // ==================== Inference recording ====================

  @Override
  public void recordInference(String runtime, String modelName, long durationNanos) {
    ModelStats stats = getOrCreateModel(runtime, modelName);
    stats.inferences.incrementAndGet();
    stats.totalDurationNanos.addAndGet(durationNanos);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, durationNanos);
  }

  @Override
  public void recordInference(String runtime, String modelName, int batchSize, long durationNanos) {
    ModelStats stats = getOrCreateModel(runtime, modelName);
    stats.inferences.incrementAndGet();
    stats.totalBatchSize.addAndGet(batchSize);
    stats.totalDurationNanos.addAndGet(durationNanos);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, durationNanos);
  }

  @Override
  public InferenceRecord inference(String runtime, String modelName) {
    return inferenceRecordPool.get().reset(this, runtime, modelName);
  }

  void commitInferenceRecord(InferenceRecordImpl rec) {
    ModelStats stats = getOrCreateModel(rec.runtime, rec.modelName);
    stats.inferences.incrementAndGet();
    stats.totalBatchSize.addAndGet(rec.batchSizeVal);
    stats.totalInputElements.addAndGet(rec.inputElem);
    stats.totalOutputElements.addAndGet(rec.outputElem);
    stats.totalDurationNanos.addAndGet(rec.durationVal);
    updateMinMax(stats.minDurationNanos, stats.maxDurationNanos, rec.durationVal);
    if (rec.deviceTypeVal != null) {
      stats.lastDeviceType = rec.deviceTypeVal;
      stats.lastDeviceId = rec.deviceIdVal;
    }
  }

  // ==================== Memory tracking ====================

  @Override
  public void recordMemoryAlloc(String deviceType, int deviceId, long bytes) {
    DeviceMemory dm = getOrCreateDevice(deviceType, deviceId);
    dm.currentBytes.addAndGet(bytes);
    dm.totalAllocated.addAndGet(bytes);
    dm.allocCount.incrementAndGet();
    // Update peak
    long cur;
    long newVal = dm.currentBytes.get();
    do {
      cur = dm.peakBytes.get();
      if (newVal <= cur) break;
    } while (!dm.peakBytes.compareAndSet(cur, newVal));
  }

  @Override
  public void recordMemoryFree(String deviceType, int deviceId, long bytes) {
    DeviceMemory dm = getOrCreateDevice(deviceType, deviceId);
    dm.currentBytes.addAndGet(-bytes);
    dm.freeCount.incrementAndGet();
  }

  // ==================== Native call tracking ====================

  @Override
  public void recordNativeCall(String library, String function, long durationNanos) {
    String key = library + "::" + function;
    NativeCallStats stats = nativeStats.computeIfAbsent(key, k -> new NativeCallStats());
    stats.calls.incrementAndGet();
    stats.totalDurationNanos.addAndGet(durationNanos);
  }

  // ==================== Model lifecycle ====================

  @Override
  public void recordModelLoad(String runtime, String modelName, long durationNanos) {
    ModelStats stats = getOrCreateModel(runtime, modelName);
    stats.loadCount.incrementAndGet();
    stats.totalLoadDurationNanos.addAndGet(durationNanos);
  }

  // ==================== Reporting ====================

  @Override
  public String getSummary() {
    if (modelStats.isEmpty() && deviceMemory.isEmpty() && nativeStats.isEmpty()) {
      return "No GPU/inference activity recorded.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("=== GPU Bridge Summary ===\n\n");

    long totalInf = 0;

    for (Map.Entry<String, ModelStats> entry : modelStats.entrySet()) {
      ModelStats s = entry.getValue();
      long inf = s.inferences.get();
      totalInf += inf;

      sb.append("Model: ").append(entry.getKey());
      if (s.lastDeviceType != null) {
        sb.append(" [").append(s.lastDeviceType).append(":").append(s.lastDeviceId).append("]");
      }
      sb.append("\n");

      // Inference stats
      sb.append("  Inferences: ").append(inf);
      long loads = s.loadCount.get();
      if (loads > 0) {
        long avgLoadMs = (s.totalLoadDurationNanos.get() / loads) / 1_000_000;
        sb.append(" (loaded ").append(loads).append("x, avg ").append(avgLoadMs).append("ms)");
      }
      sb.append("\n");

      // Latency
      if (inf > 0) {
        long avgMs = (s.totalDurationNanos.get() / inf) / 1_000_000;
        long minMs = s.minDurationNanos.get() / 1_000_000;
        long maxMs = s.maxDurationNanos.get() / 1_000_000;
        sb.append("  Latency: avg ").append(avgMs).append("ms");
        sb.append(", min ").append(minMs).append("ms");
        sb.append(", max ").append(maxMs).append("ms\n");
      }

      // Batch size
      long totalBatch = s.totalBatchSize.get();
      if (totalBatch > 0 && inf > 0) {
        sb.append("  Batch size: avg ").append(totalBatch / inf).append("\n");
      }

      // Throughput (items/sec)
      long totalDur = s.totalDurationNanos.get();
      if (totalBatch > 0 && totalDur > 0) {
        double itemsPerSec = (totalBatch * 1_000_000_000.0) / totalDur;
        sb.append("  Throughput: ")
            .append(String.format("%.1f", itemsPerSec))
            .append(" items/sec\n");
      }

      // Tensor elements
      long inEl = s.totalInputElements.get();
      long outEl = s.totalOutputElements.get();
      if (inEl > 0 || outEl > 0) {
        sb.append("  Tensors: ")
            .append(formatElements(inEl))
            .append(" in / ")
            .append(formatElements(outEl))
            .append(" out\n");
      }

      sb.append("\n");
    }

    // Device memory
    if (!deviceMemory.isEmpty()) {
      sb.append("--- Device Memory ---\n");
      for (Map.Entry<String, DeviceMemory> entry : deviceMemory.entrySet()) {
        DeviceMemory dm = entry.getValue();
        sb.append("  ").append(entry.getKey()).append(": ");
        sb.append("current ").append(formatBytes(dm.currentBytes.get()));
        sb.append(", peak ").append(formatBytes(dm.peakBytes.get()));
        sb.append(" (")
            .append(dm.allocCount.get())
            .append(" allocs, ")
            .append(dm.freeCount.get())
            .append(" frees)\n");
      }
      sb.append("\n");
    }

    // Native calls
    if (!nativeStats.isEmpty()) {
      sb.append("--- Native Calls ---\n");
      for (Map.Entry<String, NativeCallStats> entry : nativeStats.entrySet()) {
        NativeCallStats ns = entry.getValue();
        long calls = ns.calls.get();
        long avgUs = calls > 0 ? (ns.totalDurationNanos.get() / calls) / 1000 : 0;
        sb.append("  ")
            .append(entry.getKey())
            .append(": ")
            .append(calls)
            .append(" calls, avg ")
            .append(avgUs)
            .append("us\n");
      }
      sb.append("\n");
    }

    sb.append("--- Totals ---\n");
    sb.append("  Inferences: ").append(totalInf).append("\n");

    return sb.toString();
  }

  @Override
  public String getModelSummary(String modelName) {
    // Search by model name suffix
    for (Map.Entry<String, ModelStats> entry : modelStats.entrySet()) {
      if (entry.getKey().endsWith(modelName) || entry.getKey().equals(modelName)) {
        ModelStats s = entry.getValue();
        long inf = s.inferences.get();
        long avgMs = inf > 0 ? (s.totalDurationNanos.get() / inf) / 1_000_000 : 0;
        return entry.getKey() + ": " + inf + " inferences, avg " + avgMs + "ms";
      }
    }
    return "No data for model: " + modelName;
  }

  @Override
  public long getTotalInferences() {
    long total = 0;
    for (ModelStats s : modelStats.values()) {
      total += s.inferences.get();
    }
    return total;
  }

  @Override
  public long getCurrentGpuMemoryBytes() {
    if (deviceMemory.isEmpty()) return -1;
    long total = 0;
    for (DeviceMemory dm : deviceMemory.values()) {
      total += dm.currentBytes.get();
    }
    return total;
  }

  @Override
  public long getPeakGpuMemoryBytes() {
    if (deviceMemory.isEmpty()) return -1;
    long peak = 0;
    for (DeviceMemory dm : deviceMemory.values()) {
      peak = Math.max(peak, dm.peakBytes.get());
    }
    return peak;
  }

  @Override
  public long getTotalNativeCalls() {
    long total = 0;
    for (NativeCallStats ns : nativeStats.values()) {
      total += ns.calls.get();
    }
    return total;
  }

  @Override
  public void reset() {
    modelStats.clear();
    deviceMemory.clear();
    nativeStats.clear();
  }

  @Override
  public void close() {
    String summary = getSummary();
    if (!"No GPU/inference activity recorded.".equals(summary)) {
      getContext().send(summary);
    }
  }

  // ==================== Internals ====================

  private ModelStats getOrCreateModel(String runtime, String modelName) {
    String key = runtime + "/" + modelName;
    return modelStats.computeIfAbsent(key, k -> new ModelStats());
  }

  private DeviceMemory getOrCreateDevice(String deviceType, int deviceId) {
    String key = deviceType + ":" + deviceId;
    return deviceMemory.computeIfAbsent(key, k -> new DeviceMemory());
  }

  private static void updateMinMax(AtomicLong min, AtomicLong max, long value) {
    long cur;
    do {
      cur = min.get();
      if (value >= cur) break;
    } while (!min.compareAndSet(cur, value));
    do {
      cur = max.get();
      if (value <= cur) break;
    } while (!max.compareAndSet(cur, value));
  }

  static String formatBytes(long bytes) {
    if (bytes < 0) return "-" + formatBytes(-bytes);
    if (bytes < 1024) return bytes + "B";
    if (bytes < 1024 * 1024) return (bytes / 1024) + "KB";
    if (bytes < 1024L * 1024 * 1024) return (bytes / (1024 * 1024)) + "MB";
    return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
  }

  static String formatElements(long elements) {
    if (elements < 1000) return elements + "";
    if (elements < 1_000_000) return String.format("%.1fK", elements / 1000.0);
    if (elements < 1_000_000_000) return String.format("%.1fM", elements / 1_000_000.0);
    return String.format("%.1fB", elements / 1_000_000_000.0);
  }

  static final class ModelStats {
    final AtomicLong inferences = new AtomicLong();
    final AtomicLong totalBatchSize = new AtomicLong();
    final AtomicLong totalInputElements = new AtomicLong();
    final AtomicLong totalOutputElements = new AtomicLong();
    final AtomicLong totalDurationNanos = new AtomicLong();
    final AtomicLong minDurationNanos = new AtomicLong(Long.MAX_VALUE);
    final AtomicLong maxDurationNanos = new AtomicLong(0);
    final AtomicLong loadCount = new AtomicLong();
    final AtomicLong totalLoadDurationNanos = new AtomicLong();
    volatile String lastDeviceType;
    volatile int lastDeviceId;
  }

  static final class DeviceMemory {
    final AtomicLong currentBytes = new AtomicLong();
    final AtomicLong peakBytes = new AtomicLong();
    final AtomicLong totalAllocated = new AtomicLong();
    final AtomicLong allocCount = new AtomicLong();
    final AtomicLong freeCount = new AtomicLong();
  }

  static final class NativeCallStats {
    final AtomicLong calls = new AtomicLong();
    final AtomicLong totalDurationNanos = new AtomicLong();
  }
}
