package traces;

import static io.btrace.extensions.one.MainEntry.*;
import static org.openjdk.btrace.core.BTraceUtils.*;

import io.btrace.extensions.one.MainEntry;
import io.btrace.extensions.one.Service;
import org.openjdk.btrace.core.annotations.*;

/** @author Jaroslav Bachorik */
@BTrace
public class SimpleExtensionTest {
  @OnMethod(clazz = "resources.Main", method = "callA")
  public static void noargs(@Self Object self) {
    //        String[] arr = MainEntry.aaa(6);
    //        arr[1] = "hello";
    //        arr[2] = "world";
    ext_test("hey ho");
    MainEntry i = createInstance();
    i.huhu("here we go");
    println(fld);
    fld += 10;
    println(fld);
    Service s = newService();
    s.doit();
  }
}
