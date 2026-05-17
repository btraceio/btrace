import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Duration;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnEvent;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.OnTimer;
import io.btrace.gpu.GpuBridgeService;

import static io.btrace.core.BTraceUtils.*;

/**
 * Traces GPU model inference via ONNX Runtime and DJL (Deep Java Library).
 * Tracks inference latency, batch sizes, and model load times.
 *
 * <p>Attach to a JVM running ONNX or DJL inference:
 * <pre>
 * btrace &lt;pid&gt; GpuBridge.java
 * </pre>
 */
@BTrace
public class GpuBridge {

  @Injected
  private static GpuBridgeService gpu;

  // ==================== ONNX Runtime ====================

  @OnMethod(
      clazz = "ai.onnxruntime.OrtSession",
      method = "run",
      location = @Location(Kind.RETURN))
  public static void onOnnxInference(@Duration long dur) {
    gpu.recordInference("onnx", "session", dur);
  }

  @OnMethod(
      clazz = "ai.onnxruntime.OrtSession",
      method = "<init>",
      location = @Location(Kind.RETURN))
  public static void onOnnxModelLoad(@Duration long dur) {
    gpu.recordModelLoad("onnx", "session", dur);
  }

  // ==================== DJL (Deep Java Library) ====================

  @OnMethod(
      clazz = "/ai\\.djl\\.inference\\.Predictor/",
      method = "predict",
      location = @Location(Kind.RETURN))
  public static void onDjlPredict(@Duration long dur) {
    gpu.recordInference("djl", "predictor", dur);
  }

  @OnMethod(
      clazz = "/ai\\.djl\\.repository\\.zoo\\.ModelZoo/",
      method = "loadModel",
      location = @Location(Kind.RETURN))
  public static void onDjlModelLoad(@Duration long dur) {
    gpu.recordModelLoad("djl", "model-zoo", dur);
  }

  // ==================== TensorFlow Java ====================

  @OnMethod(
      clazz = "/org\\.tensorflow\\.Session/",
      method = "run",
      location = @Location(Kind.RETURN))
  public static void onTensorFlowRun(@Duration long dur) {
    gpu.recordInference("tensorflow", "session", dur);
  }

  // ==================== Periodic summary ====================

  @OnTimer(30000)
  public static void periodicSummary() {
    println(gpu.getSummary());
  }

  @OnEvent("summary")
  public static void onDemandSummary() {
    println(gpu.getSummary());
  }
}
