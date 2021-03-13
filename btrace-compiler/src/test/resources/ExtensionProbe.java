package test;

import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.jfr.JfrEvent;
import static io.btrace.extensions.one.MainEntry.*;
import io.btrace.extensions.one.Service;

@BTrace public class ExtensionProbe {
    @OnMethod(clazz = "/.*/", method = "/.*/")
    public static void onMethod() {
        ext_test("hello there");
        BTraceUtils.println("=== " + fld);
        fld = 10;
        Service s = newService();
        s.doit();
    }
}