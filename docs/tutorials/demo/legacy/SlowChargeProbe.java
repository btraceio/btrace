/*
 * Example 2.x-style BTrace probe used by "Upgrade from 2.x in 10 Minutes"
 * (docs/tutorials/03-upgrade-from-2x.md). This file intentionally still uses
 * the pre-3.0 org.openjdk.btrace package so the tutorial has something real
 * to run scripts/migrate-btrace-script.sh against.
 */
package demo;

import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Duration;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.Return;
import static org.openjdk.btrace.core.BTraceUtils.println;
import static org.openjdk.btrace.core.BTraceUtils.str;

@BTrace
public class SlowChargeProbe {
  @OnMethod(
      clazz = "demo.OrderService",
      method = "chargeCard",
      location = @Location(Kind.RETURN))
  public static void onCharge(@Duration long duration, @Return Object result) {
    if (duration > 200_000_000L) {
      println("slow chargeCard: " + str(duration / 1_000_000) + " ms");
    }
  }
}
