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
public class ConstructorArgs {
    @OnMethod(clazz="/.*\\.MethodEntryTest/", method="<init>")
    public static void args(@Self Object self, String a) {
        println("constructor args");
    }
}
