import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Duration;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.ProbeClassName;
import org.openjdk.btrace.core.annotations.ProbeMethodName;

@BTrace
public class DispatchScript {
  @OnMethod(clazz = "org.openjdk.btrace.bench.DispatchTarget", method = "noArgs")
  public static void onEntry(@ProbeClassName String pcn, @ProbeMethodName String pmn) {}

  @OnMethod(
      clazz = "org.openjdk.btrace.bench.DispatchTarget",
      method = "withReturn",
      location = @Location(Kind.RETURN))
  public static void onReturn(
      @ProbeClassName String pcn, @ProbeMethodName String pmn, @Duration long dur) {}
}
