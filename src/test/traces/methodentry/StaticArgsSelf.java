/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package traces.methodentry;

import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.Self;
import static com.sun.btrace.BTraceUtils.*;

/**
 *
 * @author Jaroslav Bachorik
 */
@BTrace
public class StaticArgsSelf {
    @OnMethod(clazz="/.*\\.MethodEntryTest/", method="args$static")
    public static void args(@Self Object self, String a, int b, String[] c, int[] d) {
        println("args");
    }
}
