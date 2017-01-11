package traces.issues;

import com.sun.btrace.annotations.*;
import com.sun.btrace.BTraceUtils;
import com.sun.btrace.Profiler;
import com.sun.btrace.services.impl.Statsd;

@BTrace class BTRACE256 {
    @Injected(factoryMethod = "getInstance") private  Statsd sd;

    @Property
    Profiler swingProfiler = BTraceUtils.Profiling.newProfiler();

    @OnMethod(clazz="/.*\\.BTRACE256/", method="doStuff")
    void entry(@ProbeMethodName(fqn=true) String probeMethod) {
        BTraceUtils.Profiling.recordEntry(swingProfiler, probeMethod);
    }

    @OnMethod(clazz="/.*\\.BTRACE256/", method="doStuff", location=@Location(value=Kind.RETURN))
    void exit(@ProbeMethodName(fqn=true) String probeMethod, @Duration long duration) {
        BTraceUtils.Profiling.recordExit(swingProfiler, probeMethod, duration);
        sd.increment("my.metric.b", "regular,distribution:gaussian");
    }

    @OnTimer(5000)
    void timer() {
        BTraceUtils.Profiling.printSnapshot("AM performance profile", swingProfiler);
    }
}
