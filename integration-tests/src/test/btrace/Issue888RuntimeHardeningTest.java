package btrace;

import static io.btrace.core.BTraceUtils.println;

import io.btrace.core.BTraceUtils;
import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.OnError;
import io.btrace.core.annotations.OnEvent;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.OnTimer;
import io.btrace.core.annotations.Property;

@BTrace(name = "issue-888-runtime-hardening")
public class Issue888RuntimeHardeningTest {
  @Property private static long registrations = 1L;

  @OnEvent("issue-888")
  public static void onEvent() {
    println("issue-888-event-handler");
    BTraceUtils.substr("x", 2);
  }

  @OnMethod(clazz = "resources.Issue888RuntimeHardeningTarget", method = "work")
  public static void onMethod() {
    println("issue-888-method-handler");
    BTraceUtils.substr("x", 2);
  }

  @OnError
  public static void onError(Throwable throwable) {
    println("issue-888-error-handler:" + BTraceUtils.str(throwable));
    BTraceUtils.substr("x", 2);
  }

  @OnTimer(250)
  public static void onTimer() {
    println("issue-888-normal-after-failure");
  }
}
