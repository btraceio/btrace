import static io.btrace.core.BTraceUtils.println;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.OnMethod;
import org.example.btrace.hadoop.api.HadoopApi;

@BTrace
public class NonPrivilegedExtensionProbe {
  @Injected private static HadoopApi hadoop;

  @OnMethod(clazz = "OrderService", method = "processOrder")
  public static void onOrder() {
    hadoop.onOpen(null, null);
    println("release-smoke: non-privileged extension");
  }
}
