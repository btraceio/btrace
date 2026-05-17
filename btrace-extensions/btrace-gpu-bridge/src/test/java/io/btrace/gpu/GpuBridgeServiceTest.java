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

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GpuBridgeServiceTest {

  private GpuBridgeServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new GpuBridgeServiceImpl();
  }

  // ==================== Inference recording ====================

  @Test
  void durationOnlyInference() {
    service.recordInference("onnx", "resnet50", 10_000_000L);
    assertEquals(1, service.getTotalInferences());
    assertTrue(service.getSummary().contains("onnx/resnet50"));
  }

  @Test
  void inferenceWithBatchSize() {
    service.recordInference("djl", "bert", 32, 50_000_000L);
    assertEquals(1, service.getTotalInferences());
    assertTrue(service.getSummary().contains("avg 32"));
  }

  @Test
  void fluentBuilder() {
    service
        .inference("onnx", "yolo-v8")
        .batchSize(16)
        .inputElements(640L * 640 * 3)
        .outputElements(8400L * 84)
        .deviceType("cuda")
        .deviceId(0)
        .duration(25_000_000L)
        .record();

    assertEquals(1, service.getTotalInferences());
    String summary = service.getSummary();
    assertTrue(summary.contains("cuda:0"));
    assertTrue(summary.contains("items/sec"));
  }

  @Test
  void fluentBuilderMinimal() {
    service.inference("tensorflow", "mobilenet").duration(5_000_000L).record();

    assertEquals(1, service.getTotalInferences());
  }

  @Test
  void multipleModelsTrackedSeparately() {
    service.recordInference("onnx", "resnet50", 10_000_000L);
    service.recordInference("onnx", "bert", 20_000_000L);
    service.recordInference("onnx", "resnet50", 12_000_000L);

    assertEquals(3, service.getTotalInferences());
    assertTrue(service.getModelSummary("resnet50").contains("2 inferences"));
    assertTrue(service.getModelSummary("bert").contains("1 inferences"));
  }

  @Test
  void latencyMinMax() {
    service.recordInference("onnx", "model", 5_000_000L);
    service.recordInference("onnx", "model", 50_000_000L);
    service.recordInference("onnx", "model", 10_000_000L);

    String summary = service.getSummary();
    assertTrue(summary.contains("min 5ms"));
    assertTrue(summary.contains("max 50ms"));
  }

  // ==================== Memory tracking ====================

  @Test
  void memoryAllocAndFree() {
    service.recordMemoryAlloc("cuda", 0, 1024 * 1024 * 100); // 100MB
    assertEquals(100 * 1024 * 1024L, service.getCurrentGpuMemoryBytes());
    assertEquals(100 * 1024 * 1024L, service.getPeakGpuMemoryBytes());

    service.recordMemoryFree("cuda", 0, 1024 * 1024 * 50); // free 50MB
    assertEquals(50 * 1024 * 1024L, service.getCurrentGpuMemoryBytes());
    assertEquals(100 * 1024 * 1024L, service.getPeakGpuMemoryBytes()); // peak unchanged
  }

  @Test
  void noMemoryTracked() {
    assertEquals(-1, service.getCurrentGpuMemoryBytes());
    assertEquals(-1, service.getPeakGpuMemoryBytes());
  }

  @Test
  void memorySummary() {
    service.recordMemoryAlloc("cuda", 0, 500 * 1024 * 1024L);
    String summary = service.getSummary();
    assertTrue(summary.contains("Device Memory"));
    assertTrue(summary.contains("500MB"));
  }

  // ==================== Native call tracking ====================

  @Test
  void nativeCalls() {
    service.recordNativeCall("cublas", "sgemm", 500_000L);
    service.recordNativeCall("cublas", "sgemm", 600_000L);
    service.recordNativeCall("cudnn", "conv_forward", 1_000_000L);

    assertEquals(3, service.getTotalNativeCalls());
    String summary = service.getSummary();
    assertTrue(summary.contains("cublas::sgemm"));
    assertTrue(summary.contains("2 calls"));
  }

  // ==================== Model lifecycle ====================

  @Test
  void modelLoad() {
    service.recordModelLoad("onnx", "bert", 2_000_000_000L); // 2 seconds
    service.recordInference("onnx", "bert", 10_000_000L);

    String summary = service.getSummary();
    assertTrue(summary.contains("loaded 1x"));
    assertTrue(summary.contains("2000ms"));
  }

  // ==================== Reporting ====================

  @Test
  void noDataSummary() {
    assertEquals("No GPU/inference activity recorded.", service.getSummary());
  }

  @Test
  void unknownModelSummary() {
    assertEquals("No data for model: unknown", service.getModelSummary("unknown"));
  }

  @Test
  void formatBytes() {
    assertEquals("100B", GpuBridgeServiceImpl.formatBytes(100));
    assertEquals("10KB", GpuBridgeServiceImpl.formatBytes(10 * 1024));
    assertEquals("256MB", GpuBridgeServiceImpl.formatBytes(256 * 1024 * 1024));
    assertEquals("1.5GB", GpuBridgeServiceImpl.formatBytes((long) (1.5 * 1024 * 1024 * 1024)));
  }

  @Test
  void formatElements() {
    assertEquals("500", GpuBridgeServiceImpl.formatElements(500));
    assertEquals("1.5K", GpuBridgeServiceImpl.formatElements(1500));
    assertEquals("2.0M", GpuBridgeServiceImpl.formatElements(2_000_000));
    assertEquals("1.0B", GpuBridgeServiceImpl.formatElements(1_000_000_000));
  }

  @Test
  void reset() {
    service.recordInference("onnx", "model", 1L);
    service.recordMemoryAlloc("cuda", 0, 1024);
    service.recordNativeCall("cublas", "fn", 1L);
    service.reset();

    assertEquals(0, service.getTotalInferences());
    assertEquals(-1, service.getCurrentGpuMemoryBytes());
    assertEquals(0, service.getTotalNativeCalls());
    assertEquals("No GPU/inference activity recorded.", service.getSummary());
  }

  @Test
  void concurrentInference() throws Exception {
    int threads = 8;
    int infsPerThread = 1000;
    CountDownLatch latch = new CountDownLatch(threads);

    for (int t = 0; t < threads; t++) {
      new Thread(
              () -> {
                try {
                  for (int i = 0; i < infsPerThread; i++) {
                    service.recordInference("onnx", "model", 32, 1_000_000L);
                  }
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();

    assertEquals(threads * infsPerThread, service.getTotalInferences());
  }

  @Test
  void concurrentBuilderInference() throws Exception {
    int threads = 8;
    int infsPerThread = 500;
    CountDownLatch latch = new CountDownLatch(threads);

    for (int t = 0; t < threads; t++) {
      new Thread(
              () -> {
                try {
                  for (int i = 0; i < infsPerThread; i++) {
                    service.inference("djl", "bert").batchSize(16).duration(2_000_000L).record();
                  }
                } finally {
                  latch.countDown();
                }
              })
          .start();
    }
    latch.await();

    assertEquals(threads * infsPerThread, service.getTotalInferences());
  }
}
