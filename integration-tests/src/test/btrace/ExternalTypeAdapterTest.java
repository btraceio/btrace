package btrace;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Injected;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Self;
import org.openjdk.btrace.test.ext.ExternalTypeTestService;

import static org.openjdk.btrace.core.BTraceUtils.println;
import static org.openjdk.btrace.core.BTraceUtils.exit;

/**
 * Integration probe for the @ExternalType adapter.
 *
 * Intercepts resources.Main.probeExternal() and uses ExternalTypeTestService
 * (backed by the generated ExternalDataType$Ext adapter) to read from the
 * ExternalData instance — a class that exists only in the target app's classloader.
 */
@BTrace
public class ExternalTypeAdapterTest {
  @Injected private static ExternalTypeTestService extSvc;

  @OnMethod(clazz = "resources.Main", method = "probeExternal")
  public static void onProbeExternal(@Self Object self, Object data) {
    if (data == null) return;
    println("tag=" + extSvc.tag());
    println("value=" + extSvc.value(data));
    exit(0);
  }
}
