package btrace;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.OnMethod;
import io.btrace.utils.PrinterService;

import static io.btrace.core.BTraceUtils.exit;

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
