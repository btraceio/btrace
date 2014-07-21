package scripts;


import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.Duration;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Location;
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.ProbeClassName;
import com.sun.btrace.annotations.ProbeMethodName;

import static com.sun.btrace.BTraceUtils.*;


@BTrace
public class TraceScript {
    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrumentedMethod")
    public static void onMethodEntryEmpty(@ProbeClassName String pcn, @ProbeMethodName String pmn) {

    }

    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrDuration", location = @Location(Kind.RETURN))
    public static void onMethodRetDuration(@ProbeClassName String pcn, @ProbeMethodName String pmn, @Duration long dur) {

    }

    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrDurationSampled", location = @Location(Kind.RETURN))
    public static void onMethodRetDurationSampled(@ProbeClassName String pcn, @ProbeMethodName String pmn, @Duration(samplingInterval = 10) long dur) {

    }

    @OnMethod(clazz="net.java.btrace.BTraceBench", method="testInstrumentedMethodPrintln1")
    public static void onMethodEntryPrintln1(@ProbeClassName String pcn, @ProbeMethodName String pmn) {
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
    }
}
