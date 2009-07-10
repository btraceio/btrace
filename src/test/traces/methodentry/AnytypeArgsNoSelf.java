/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package traces.methodentry;

import com.sun.btrace.AnyType;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.OnMethod;
import static com.sun.btrace.BTraceUtils.*;

/**
 *
 * @author Jaroslav Bachorik
 */
@BTrace
public class AnytypeArgsNoSelf {
    @OnMethod(clazz="/.*\\.MethodEntryTest/", method="args")
    public static void argsNoSelf(AnyType[] args) {
        println("args no self");
    }
}
