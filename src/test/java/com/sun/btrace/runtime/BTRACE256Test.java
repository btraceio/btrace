package com.sun.btrace.runtime;

import org.junit.Test;

/**
 *
 * @author Jaroslav Bachorik
 */
public class BTRACE256Test extends InstrumentorTestBase {
    @Test
    public void bytecodeValidation() throws Exception {
        originalBC = loadTargetClass("issues/BTRACE256");
        transform("issues/BTRACE256");
        checkTransformation(
            "LCONST_0\n" +
            "LSTORE 1\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LSTORE 3\n" +
            "LDC \"public void resources.issues.BTRACE256#doStuff\"\n" +
            "INVOKESTATIC resources/issues/BTRACE256.$btrace$traces$issues$BTRACE256$entry (Ljava/lang/String;)V\n" +
            "INVOKESTATIC java/lang/System.nanoTime ()J\n" +
            "LLOAD 3\n" +
            "LSUB\n" +
            "LSTORE 1\n" +
            "LDC \"public void resources.issues.BTRACE256#doStuff\"\n" +
            "LLOAD 1\n" +
            "INVOKESTATIC resources/issues/BTRACE256.$btrace$traces$issues$BTRACE256$exit (Ljava/lang/String;J)V\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 5\n" +
            "\n" +
            "// access flags 0xA\n" +
            "private static $btrace$traces$issues$BTRACE256$entry(Ljava/lang/String;)V\n" +
            "@Lcom/sun/btrace/annotations/OnMethod;(clazz=\"/.*\\\\.BTRACE256/\", method=\"doStuff\")\n" +
            "@Lcom/sun/btrace/annotations/ProbeMethodName;(fqn=true) // parameter 0\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "GETSTATIC traces/issues/BTRACE256.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L0\n" +
            "RETURN\n" +
            "L0\n" +
            "FRAME SAME\n" +
            "GETSTATIC traces/issues/BTRACE256.swingProfiler : Lcom/sun/btrace/Profiler;\n" +
            "ALOAD 0\n" +
            "INVOKESTATIC com/sun/btrace/BTraceUtils$Profiling.recordEntry (Lcom/sun/btrace/Profiler;Ljava/lang/String;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "FRAME SAME1 java/lang/Throwable\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "\n" +
            "// access flags 0xA\n" +
            "private static $btrace$traces$issues$BTRACE256$exit(Ljava/lang/String;J)V\n" +
            "@Lcom/sun/btrace/annotations/OnMethod;(clazz=\"/.*\\\\.BTRACE256/\", method=\"doStuff\", location=@Lcom/sun/btrace/annotations/Location;(value=Lcom/sun/btrace/annotations/Kind;.RETURN))\n" +
            "@Lcom/sun/btrace/annotations/ProbeMethodName;(fqn=true) // parameter 0\n" +
            "@Lcom/sun/btrace/annotations/Duration;() // parameter 1\n" +
            "TRYCATCHBLOCK L0 L1 L1 java/lang/Throwable\n" +
            "GETSTATIC traces/issues/BTRACE256.runtime : Lcom/sun/btrace/BTraceRuntime;\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.enter (Lcom/sun/btrace/BTraceRuntime;)Z\n" +
            "IFNE L0\n" +
            "RETURN\n" +
            "L0\n" +
            "FRAME SAME\n" +
            "GETSTATIC traces/issues/BTRACE256.swingProfiler : Lcom/sun/btrace/Profiler;\n" +
            "ALOAD 0\n" +
            "LLOAD 1\n" +
            "INVOKESTATIC com/sun/btrace/BTraceUtils$Profiling.recordExit (Lcom/sun/btrace/Profiler;Ljava/lang/String;J)V\n" +
            "INVOKESTATIC com/sun/btrace/services/impl/Statsd.getInstance ()Lcom/sun/btrace/services/impl/Statsd;\n" +
            "DUP\n" +
            "ASTORE 3\n" +
            "LDC \"my.metric.b\"\n" +
            "LDC \"regular,distribution:gaussian\"\n" +
            "INVOKEVIRTUAL com/sun/btrace/services/impl/Statsd.increment (Ljava/lang/String;Ljava/lang/String;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "L1\n" +
            "FRAME SAME1 java/lang/Throwable\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.handleException (Ljava/lang/Throwable;)V\n" +
            "INVOKESTATIC com/sun/btrace/BTraceRuntime.leave ()V\n" +
            "RETURN\n" +
            "MAXSTACK = 4\n" +
            "MAXLOCALS = 4"
        );
    }
}
