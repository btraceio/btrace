/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package traces.methodentry;

import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.OnMethod;
import static com.sun.btrace.BTraceUtils.*;

/**
 *
 * @author Jaroslav Bachorik
 */
@BTrace
public class StaticNoArgs {
    @OnMethod(clazz="/.*\\.MethodEntryTest/", method="noargs$static")
    public static void argsEmpty() {
        println("args empty");
    }
}
