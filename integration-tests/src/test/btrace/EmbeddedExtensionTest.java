package btrace;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.OnMethod;
import io.btrace.utils.PrinterService;

import static io.btrace.core.BTraceUtils.exit;

/**
 * Integration probe for extensions embedded in the published artifact.
 *
 * Injects a service from one of the default extensions and prints through it. The output is
 * emitted by the extension rather than by BTraceUtils on purpose: printing directly would produce
 * the expected line even if the service had never resolved, which is the whole thing under test.
 */
@BTrace
public class EmbeddedExtensionTest {
  @Injected private static PrinterService printer;

  @OnMethod(clazz = "resources.Main", method = "callA")
  public static void onCall() {
    printer.println("embedded-extension-linked");
    exit(0);
  }
}
