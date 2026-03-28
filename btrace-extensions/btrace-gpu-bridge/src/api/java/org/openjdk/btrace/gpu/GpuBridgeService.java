package org.openjdk.btrace.gpu;

import org.openjdk.btrace.core.extensions.ServiceDescriptor;

/**
 * BTrace extension for GPU compute and model inference observability.
 *
 * <p>Traces Java-to-GPU boundaries: ONNX Runtime inference sessions,
 * DJL (Deep Java Library) predictions, TensorFlow Java operations,
 * and Panama FFM native calls to CUDA/ROCm libraries. Zero external
 * dependencies — instruments existing client classes.
 *
 * <p>Usage in a BTrace script:
 * <pre>
 * &#64;Injected GpuBridgeService gpu;
 *
 * &#64;OnMethod(clazz = "ai.onnxruntime.OrtSession", method = "run")
 * void onInference(&#64;Duration long dur) {
 *     gpu.recordInference("onnx", "resnet50", dur);
 * }
 * </pre>
 */
@ServiceDescriptor
public interface GpuBridgeService {

  // ==================== Inference recording ====================

  /**
   * Records a model inference call with duration only.
   *
   * @param runtime runtime name (e.g. "onnx", "djl", "tensorflow")
   * @param modelName model identifier
   * @param durationNanos inference duration in nanoseconds
   */
  void recordInference(String runtime, String modelName, long durationNanos);

  /**
   * Records a model inference with batch size and tensor dimensions.
   *
   * @param runtime runtime name
   * @param modelName model identifier
   * @param batchSize batch size of the inference request
   * @param durationNanos inference duration
   */
  void recordInference(String runtime, String modelName, int batchSize, long durationNanos);

  /**
   * Starts a detailed inference record builder. Allocation-free (ThreadLocal-pooled).
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
   *
   * @param runtime runtime name
   * @param modelName model identifier
   * @return an inference record builder (thread-local, do not store)
   */
  InferenceRecord inference(String runtime, String modelName);

  // ==================== Memory tracking ====================

  /**
   * Records GPU memory allocation.
   *
   * @param deviceType device type ("cuda", "rocm", "cpu")
   * @param deviceId device index
   * @param bytes allocated bytes
   */
  void recordMemoryAlloc(String deviceType, int deviceId, long bytes);

  /**
   * Records GPU memory deallocation.
   *
   * @param deviceType device type
   * @param deviceId device index
   * @param bytes freed bytes
   */
  void recordMemoryFree(String deviceType, int deviceId, long bytes);

  // ==================== Native call tracking ====================

  /**
   * Records a native/FFM call to a GPU library (e.g. cuBLAS, cuDNN).
   *
   * @param library library name
   * @param function function name
   * @param durationNanos call duration
   */
  void recordNativeCall(String library, String function, long durationNanos);

  // ==================== Model lifecycle ====================

  /**
   * Records model load/initialization time.
   *
   * @param runtime runtime name
   * @param modelName model identifier
   * @param durationNanos load duration
   */
  void recordModelLoad(String runtime, String modelName, long durationNanos);

  // ==================== Reporting ====================

  /** Returns a formatted summary of all GPU/inference metrics. */
  String getSummary();

  /** Returns summary for a specific model. */
  String getModelSummary(String modelName);

  /** Total number of inference calls across all models. */
  long getTotalInferences();

  /** Returns estimated GPU memory currently allocated (bytes), or -1 if not tracked. */
  long getCurrentGpuMemoryBytes();

  /** Returns peak GPU memory seen (bytes), or -1 if not tracked. */
  long getPeakGpuMemoryBytes();

  /** Returns total number of native/FFM calls recorded. */
  long getTotalNativeCalls();

  /** Resets all collected metrics. */
  void reset();
}
