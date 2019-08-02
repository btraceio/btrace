package traces.issues;

import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.Profiler;
import org.openjdk.btrace.statsd.Statsd;

@BTrace
class BTRACE256 {
    @Property
    Profiler swingProfiler = BTraceUtils.Profiling.newProfiler();
    @Injected(factoryMethod = "getInstance")
    private Statsd sd;

    @OnMethod(clazz = "/.*\\.BTRACE256/", method = "doStuff")
    void entry(@ProbeMethodName(fqn = true) String probeMethod) {
        BTraceUtils.Profiling.recordEntry(swingProfiler, probeMethod);
    }

    @OnMethod(clazz = "/.*\\.BTRACE256/", method = "doStuff", location = @Location(value = Kind.RETURN))
    void exit(@ProbeMethodName(fqn = true) String probeMethod, @Duration long duration) {
        BTraceUtils.Profiling.recordExit(swingProfiler, probeMethod, duration);
        sd.increment("my.metric.b", "regular,distribution:gaussian");
    }

    @OnTimer(5000)
    void timer() {
        BTraceUtils.Profiling.printSnapshot("AM performance profile", swingProfiler);
    }
}
