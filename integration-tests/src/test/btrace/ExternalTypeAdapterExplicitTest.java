package btrace;

import static io.btrace.core.BTraceUtils.exit;
import static io.btrace.core.BTraceUtils.println;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.OnMethod;
import io.btrace.test.ext.ExternalTypeTestService;

/** Integration probe for explicit @ExternalType static loader selection. */
@BTrace
public class ExternalTypeAdapterExplicitTest {
  @Injected private static ExternalTypeTestService extSvc;

  @OnMethod(clazz = "resources.Main", method = "probeExternalExplicit")
  public static void onProbeExternalExplicit(Object data) {
    if (data == null) return;
    println("explicit-tag=" + extSvc.explicitTag(data));
    exit(0);
  }
}
