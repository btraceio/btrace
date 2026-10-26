import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnMethod;
import static org.openjdk.btrace.core.BTraceUtils.println;

/**
 * Probe compiled with the genuine BTrace 2.2.6 compiler (org.openjdk.btrace package) to verify that
 * 2.x probes deploy unchanged on the 3.0 agent.
 */
@BTrace
public class LegacyOnMethodProbe {
  @OnMethod(clazz = "resources.Main", method = "callA", location = @Location(Kind.RETURN))
  public static void onCallA() {
    println("legacy probe fired");
  }
}
