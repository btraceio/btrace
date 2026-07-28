import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.OnMethod;
import io.btrace.utils.PrinterService;

@BTrace
public class NonPrivilegedExtensionProbe {
  @Injected private static PrinterService printer;

  @OnMethod(clazz = "OrderService", method = "processOrder")
  public static void onOrder() {
    // Emitted through the injected service on purpose. Printing via BTraceUtils instead would
    // make this probe produce the asserted line even when the service never resolved, which is
    // exactly the failure the embedded-extension leg exists to catch.
    printer.println("release-smoke: non-privileged extension");
  }
}
