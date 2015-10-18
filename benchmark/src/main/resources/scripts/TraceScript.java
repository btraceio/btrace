package scripts;


import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.Duration;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Location;
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.ProbeClassName;
import com.sun.btrace.annotations.ProbeMethodName;

import static com.sun.btrace.BTraceUtils.*;
import com.sun.btrace.annotations.Sampled;


@BTrace
public class TraceScript {
    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrumentedMethod")
    public static void onMethodEntryEmpty(@ProbeClassName String pcn, @ProbeMethodName String pmn) {

    }

    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrumentedMethodSampled")
    @Sampled(kind = Sampled.Sampler.Const)
    public static void onMethodEntryEmptySampled(@ProbeClassName String pcn, @ProbeMethodName String pmn) {

    }

    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrDuration", location = @Location(Kind.RETURN))
    public static void onMethodRetDuration(@ProbeClassName String pcn, @ProbeMethodName String pmn, @Duration long dur) {

    }

    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrDurationSampled", location = @Location(Kind.RETURN))
    @Sampled(kind = Sampled.Sampler.Const)
    public static void onMethodRetDurationSampled(@ProbeClassName String pcn, @ProbeMethodName String pmn, @Duration long dur) {

    }

    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrDurationSampledAdaptive", location = @Location(Kind.RETURN))
    @Sampled
    public static void onMethodRetDurationSampledAdaptive(@ProbeClassName String pcn, @ProbeMethodName String pmn, @Duration long dur) {

    }

    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrumentedMethodPrintln1")
    public static void onMethodEntryPrintln1(@ProbeClassName String pcn, @ProbeMethodName String pmn) {
        println(pcn);
    }

    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrumentedMethodPrintln1Sampled")
    @Sampled
    public static void onMethodEntryPrintln1Sampled(@ProbeClassName String pcn, @ProbeMethodName String pmn) {
        println(pcn);
    }

    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrumentedMethodPrintln2")
    public static void onMethodEntryPrintln2(@ProbeClassName String pcn, @ProbeMethodName String pmn) {
        println(pcn);
        println(pmn);
    }

    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrumentedMethodPrintln3")
    public static void onMethodEntryPrintln3(@ProbeClassName String pcn, @ProbeMethodName String pmn) {
        println(pcn);
        println(pmn);
        println(pmn);
    }

    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrumentedMethodPrintln24")
    public static void onMethodEntryPrintln24(@ProbeClassName String pcn, @ProbeMethodName String pmn) {
        println(pcn);
        println(pmn);
        println(pmn);
        println(pcn);
        println(pmn);
        println(pmn);
        println(pcn);
        println(pmn);
        println(pmn);
        println(pcn);
        println(pmn);
        println(pmn);
        println(pcn);
        println(pmn);
        println(pmn);
        println(pcn);
        println(pmn);
        println(pmn);
        println(pcn);
        println(pmn);
        println(pmn);
        println(pcn);
        println(pmn);
        println(pmn);
    }
}
