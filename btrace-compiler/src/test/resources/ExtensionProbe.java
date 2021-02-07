package test;

import org.openjdk.btrace.core.BTraceUtils;
import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.jfr.JfrEvent;
import static io.btrace.extensions.one.MainEntry.*;

@BTrace public class ExtensionProbe {
    @OnMethod(clazz = "/.*/", method = "/.*/")
    public static void onMethod() {
        ext_test("hello there");
    }
}