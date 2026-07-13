import static io.btrace.core.BTraceUtils.println;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;

@BTrace
public class StartupProbe {
  @OnMethod(clazz = "OrderService", method = "processOrder", location = @Location(Kind.RETURN))
  public static void onReturn() {
    println("release-smoke: startup probe");
  }
}
