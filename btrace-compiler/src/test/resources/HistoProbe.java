package test;

import static org.openjdk.btrace.core.BTraceUtils.*;
import org.openjdk.btrace.core.annotations.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@BTrace public class HistoProbe {
    private static Map<String, AtomicInteger> histo = newHashMap();
    @OnMethod(clazz = "javax.swing.JComponent", method = "<init>")
    public static void onMethod(@Self Object obj) {
        String cn = name(classOf(obj));
        AtomicInteger ai = get((Map<String, AtomicInteger>)histo, cn);
        if (ai == null) {
            ai = newAtomicInteger(1);
            put(histo, cn, ai);
        } else {
            incrementAndGet(ai);	// WORKS if commented out
        }
    }

    @OnTimer(1000)
    public static void print() {
        printNumberMap("Component Histogram", histo);
    }
}