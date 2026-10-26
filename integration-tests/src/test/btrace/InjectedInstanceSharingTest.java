package btrace;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.OnTimer;
import io.btrace.test.ext.ExternalTypeTestService;

import static io.btrace.core.BTraceUtils.println;

/**
 * Verifies that all handlers of one probe share a single @Injected service instance: the
 * @OnMethod handler bumps the service counter, the @OnTimer handler reads it.
 */
@BTrace
public class InjectedInstanceSharingTest {
  @Injected private static ExternalTypeTestService svc;

  @OnMethod(clazz = "resources.Main", method = "probeExternal")
  public static void onProbeExternal() {
    svc.bump();
  }

  @OnTimer(300)
  public static void onTimer() {
    println("shared-count=" + svc.count());
  }
}
