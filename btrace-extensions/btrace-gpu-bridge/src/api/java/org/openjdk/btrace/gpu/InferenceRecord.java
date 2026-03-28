package org.openjdk.btrace.gpu;

/**
 * Fluent builder for recording a model inference with detailed metrics.
 *
 * <p>Obtain via {@link GpuBridgeService#inference(String, String)}.
 * Allocation-free (ThreadLocal-pooled). Do not store the returned reference.
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
