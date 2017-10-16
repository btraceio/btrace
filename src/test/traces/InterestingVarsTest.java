package traces.issues;

import com.sun.btrace.annotations.*;
import com.sun.btrace.BTraceUtils;

@BTrace class InterestingVarsTest {
    @OnMethod(clazz="/.*\\.InterestingVarsClass/", method="initAndStartApp")
    void entry(String a, String b, String c) {
        BTraceUtils.println(a);
    }
}
