package traces.issues;

import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.BTraceUtils;
import static org.openjdk.btrace.core.BTraceUtils.*;

import java.util.concurrent.atomic.AtomicLong;

@BTrace(unsafe = false)
public class ScalaBigInteger {
    private static final AtomicLong hitCnt = BTraceUtils.newAtomicLong(0);

    @OnMethod(clazz = "/.*/", method = "<init>")
    public static void doall() {
        BTraceUtils.getAndIncrement(hitCnt);
    }

    @OnTimer(500)
    public static void doRecurrent() {
        long cnt = BTraceUtils.get(hitCnt);
        if (cnt > 0) {
            println("[invocations=" + cnt + "]");
        }
    }
}
