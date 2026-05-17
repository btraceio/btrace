import io.btrace.contracts.ContractService;
import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Duration;
import io.btrace.core.annotations.Injected;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnEvent;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.OnTimer;
import io.btrace.core.annotations.ProbeClassName;
import io.btrace.core.annotations.ProbeMethodName;
import io.btrace.core.annotations.Return;

import static io.btrace.core.BTraceUtils.*;

/**
 * Runtime behavioral contracts: latency budgets, call-rate limits, null-safety, and tagged
 * code path profiling — without modifying the target code.
 *
 * <p>Requires the btrace-contracts extension in $BTRACE_HOME/extensions/.
 *
 * <p>Attach to a running JVM:
 * <pre>
 * btrace &lt;pid&gt; ContractCheck.java
 * </pre>
 *
 * <p>Trigger an on-demand summary:
 * <pre>
 * btrace &lt;pid&gt; --event summary
 * </pre>
 */
@BTrace
public class ContractCheck {

  @Injected
  private static ContractService contracts;

  // ==================== Latency budgets ====================

  /**
   * Enforce a 500ms latency budget on all methods in the target package.
   * Adjust the clazz pattern to match your project structure.
   */
  @OnMethod(
      clazz = "/com\\.myapp\\..*/",
      method = "/.*/",
      location = @Location(Kind.RETURN))
  public static void checkLatency(
      @ProbeClassName String cls,
      @ProbeMethodName String method,
      @Duration long dur) {
    contracts.checkLatency(strcat(cls, strcat(".", method)), dur, 500_000_000L);
  }

  // ==================== Null safety ====================

  @OnMethod(
      clazz = "/com\\.myapp\\..*/",
      method = "/.*/",
      location = @Location(Kind.RETURN))
  public static void checkNullReturn(
      @ProbeClassName String cls,
      @ProbeMethodName String method,
      @Return Object ret) {
    contracts.checkNotNull(strcat(cls, strcat(".", method)), ret);
  }

  // ==================== Tagged path comparison ====================

  // Example: compare two implementations of the same operation.
  // Tag them differently so getSummary() renders them side by side.

  @OnMethod(
      clazz = "com.myapp.CachedQueryService",
      method = "query",
      location = @Location(Kind.RETURN))
  public static void onCachedQuery(@Duration long dur) {
    contracts.trackCodePath("QueryService.query", dur, "cached");
  }

  @OnMethod(
      clazz = "com.myapp.DirectQueryService",
      method = "query",
      location = @Location(Kind.RETURN))
  public static void onDirectQuery(@Duration long dur) {
    contracts.trackCodePath("QueryService.query", dur, "direct");
  }

  // ==================== Reporting ====================

  @OnTimer(10000)
  public static void periodicCheck() {
    if (contracts.hasViolations()) {
      println("=== CONTRACT VIOLATIONS DETECTED ===");
      println(contracts.getSummary());
    }
  }

  @OnEvent("summary")
  public static void onDemandSummary() {
    println(contracts.getSummary());
  }
}
