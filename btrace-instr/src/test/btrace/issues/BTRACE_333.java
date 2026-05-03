package traces.issues;

import static org.openjdk.btrace.core.BTraceUtils.*;

import org.openjdk.btrace.core.annotations.*;

@BTrace(unsafe = true)
public class BTRACE_333 {
  @OnMethod(
      clazz = "com.bt.pjg.game.backpack.BackpackExtensionTest",
      method = "/.*/",
      location = @Location(value = Kind.SYNC_ENTRY))
  public static void m3() {
    print("entered SYNC");
  }
}
