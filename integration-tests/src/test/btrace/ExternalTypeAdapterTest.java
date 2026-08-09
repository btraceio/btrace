package btrace;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.Self;
import io.btrace.test.ext.ExternalTypeTestService;

import static io.btrace.core.BTraceUtils.println;
import static io.btrace.core.BTraceUtils.exit;

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
    println("child=" + extSvc.childMarker(data));
    println("accepted-child=" + extSvc.acceptedChildMarker(data));
    println("overloads=" + extSvc.overloadMarkers(data));
    println("phase5=" + extSvc.phaseFiveMarkers(data));
    exit(0);
  }
}
