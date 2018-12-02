package traces.issues;

import com.sun.btrace.annotations.*;
import static com.sun.btrace.BTraceUtils.*;

@BTrace(unsafe=true) public class BTRACE_333 {
    @OnMethod(
        clazz="com.bt.pjg.game.backpack.BackpackExtensionTest",
        method="/.*/",
		location=@Location(value=Kind.SYNC_ENTRY)
    )
    public static void m3() {
        print("entered SYNC");
    }	
}
