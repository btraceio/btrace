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

/**
 * Fluent builder for recording a model inference with detailed metrics.
 *
 * <p>Obtain via {@link GpuBridgeService#inference(String, String)}. Allocation-free
 * (ThreadLocal-pooled). Do not store the returned reference.
 *
 * <pre>
 * gpu.inference("onnx", "bert-base")
 *     .batchSize(32)
 *     .inputElements(512 * 768)
 *     .outputElements(512 * 2)
 *     .deviceType("cuda")
 *     .deviceId(0)
 *     .duration(durationNanos)
 *     .record();
 * </pre>
 */
public interface InferenceRecord {

  /** Batch size for this inference. */
  InferenceRecord batchSize(int size);

  /** Total number of input tensor elements. */
  InferenceRecord inputElements(long elements);

  /** Total number of output tensor elements. */
  InferenceRecord outputElements(long elements);

  /** Device type: "cuda", "rocm", "cpu", "mps", etc. */
  InferenceRecord deviceType(String type);

  /** Device index (for multi-GPU). */
  InferenceRecord deviceId(int id);

  /** Inference duration in nanoseconds. */
  InferenceRecord duration(long nanos);

  /** Commits this inference record. */
  void record();
}
