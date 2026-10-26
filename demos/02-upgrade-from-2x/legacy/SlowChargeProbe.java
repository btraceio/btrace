/*
 * A BTrace 2.x probe, exactly as a 2.x user would have written it: it imports the pre-3.0
 * org.openjdk.btrace package. demos/02-upgrade-from-2x uses it two ways: the SlowChargeProbe.class
 * next to it was compiled with BTrace 2.2.6 and deploys on the 3.0 agent untouched, and this source
 * is what scripts/migrate-btrace-script.sh rewrites to the io.btrace package.
 */


import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Duration;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import static org.openjdk.btrace.core.BTraceUtils.println;
import static org.openjdk.btrace.core.BTraceUtils.str;

@BTrace
public class SlowChargeProbe {
  @OnMethod(
      clazz = "OrderService",
      method = "chargeCard",
      location = @Location(Kind.RETURN))
  public static void onCharge(@Duration long duration) {
    if (duration > 200_000_000L) {
      println("slow chargeCard: " + str(duration / 1_000_000) + " ms");
    }
  }
}
