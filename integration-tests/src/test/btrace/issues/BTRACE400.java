package traces.issues;

import static io.btrace.core.BTraceUtils.*;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.Kind;
import io.btrace.core.annotations.Location;
import io.btrace.core.annotations.OnMethod;
import io.btrace.core.annotations.Self;

/** @author Jaroslav Bachorik */
@BTrace
public class BTRACE400 {
  @OnMethod(clazz = "/.*\\.Main/", method = "callA", location = @Location(value = Kind.RETURN))
  public static void tracker(@Self Object x) {
    println(str(field("resources.Main", "id")));
    println(str(probeClass()));
  }
}
