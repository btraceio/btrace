package traces.issues;

import io.btrace.core.annotations.*;
import io.btrace.core.BTraceUtils;

@BTrace
class InterestingVarsTest {
    @OnMethod(clazz = "/.*\\.InterestingVarsClass/", method = "initAndStartApp")
    void entry(String a, String b, String c) {
        BTraceUtils.println(a);
    }
}
