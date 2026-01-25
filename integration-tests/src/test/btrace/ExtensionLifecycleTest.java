package btrace;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Injected;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.utils.PrinterService;

import static org.openjdk.btrace.core.BTraceUtils.exit;

@BTrace
public class ExtensionLifecycleTest {
  private static boolean exited = false;

  @Injected private static PrinterService printer;

  @OnMethod(clazz = "resources.Main", method = "callB")
  public static void onCallB() {
    printer.println("extension callB");
    if (!exited) {
      exited = true;
      exit();
    }
  }
}
