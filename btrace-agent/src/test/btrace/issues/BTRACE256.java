package traces.issues;

import io.btrace.core.BTraceUtils;
import io.btrace.core.Profiler;
import io.btrace.core.annotations.*;
import io.btrace.core.extensions.Permission;
import io.btrace.statsd.Statsd;

@BTrace
class BTRACE256 {
  @Property Profiler swingProfiler = BTraceUtils.Profiling.newProfiler();

  @Injected
  private Statsd sd;

  @OnMethod(clazz = "/.*\\.BTRACE256/", method = "doStuff")
  void entry(@ProbeMethodName(fqn = true) String probeMethod) {
    BTraceUtils.Profiling.recordEntry(swingProfiler, probeMethod);
  }

  @OnMethod(
      clazz = "/.*\\.BTRACE256/",
      method = "doStuff",
      location = @Location(value = Kind.RETURN))
  void exit(@ProbeMethodName(fqn = true) String probeMethod, @Duration long duration) {
    BTraceUtils.Profiling.recordExit(swingProfiler, probeMethod, duration);
    sd.increment("my.metric.b", "regular,distribution:gaussian");
  }

  @OnTimer(5000)
  void timer() {
    BTraceUtils.Profiling.printSnapshot("AM performance profile", swingProfiler);
  }
}
