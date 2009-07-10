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
public class NoArgs {
    @OnMethod(clazz="/.*\\.MethodEntryTest/", method="args")
    public static void argsEmpty(@Self Object x) {
        println("args empty");
    }

//    @OnMethod(clazz="/.*\\.MethodEntryTest/", method="args")
//    public static void argsNoSelf(String a, int b, String[] c, int[] d) {
//        println("args no self");
//    }
//
//    @OnMethod(clazz="/.*/", method="args")
//    public static void args(@Self Object self, String a, int b, String[] c, int[] d) {
//        println("args");
//    }
//
//    @OnMethod(clazz="/.*/", method="args$static")
//    public static void staticArgs() {
//        println("args$static");
//    }
}
