import static io.btrace.core.BTraceUtils.println;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.OnMethod;
import io.btrace.metrics.MetricsService;

@BTrace
public class PrivilegedExtensionProbe {
  @Injected private static MetricsService metrics;

  @OnMethod(clazz = "OrderService", method = "processOrder")
  public static void onOrder() {
    metrics.histogram("release-smoke").record(1);
    println("release-smoke: privileged extension");
  }
}
