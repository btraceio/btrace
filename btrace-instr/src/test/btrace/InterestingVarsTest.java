package traces.issues;

import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.BTraceUtils;

@BTrace
class InterestingVarsTest {
    @OnMethod(clazz = "/.*\\.InterestingVarsClass/", method = "initAndStartApp")
    void entry(String a, String b, String c) {
        BTraceUtils.println(a);
    }
}
