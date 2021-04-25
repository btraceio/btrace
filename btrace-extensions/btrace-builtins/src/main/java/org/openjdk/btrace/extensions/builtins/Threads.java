package org.openjdk.btrace.extensions.builtins;

import org.openjdk.btrace.core.BTraceContext;

import java.util.Map;
import java.util.Set;

import static org.openjdk.btrace.extensions.builtins.Logger.print;
import static org.openjdk.btrace.extensions.builtins.Logger.println;

public class Threads {
    // the number of stack frames taking a thread dump adds
    private static final int THRD_DUMP_FRAMES = 1;

    /**
     * Tests whether this thread has been interrupted. The <i>interrupted status</i> of the thread
     * is unaffected by this method.
     *
     * <p>A thread interruption ignored because a thread was not alive at the time of the interrupt
     * will be reflected by this method returning false.
     *
     * @return <code>true</code> if this thread has been interrupted; <code>false</code> otherwise.
     */
    public static boolean isInteruppted() {
        return Thread.currentThread().isInterrupted();
    }

    /** Prints the java stack trace of the current thread. */
    public static void jstack() {
        jstack(1, -1);
    }

    /**
     * Prints the java stack trace of the current thread. But, atmost given number of frames.
     *
     * @param numFrames number of frames to be printed. When this is negative all frames are
     *     printed.
     */
    public static void jstack(int numFrames) {
        // passing '5' to skip our own frames to generate stack trace
        jstack(1, numFrames);
    }

    private static void jstack(int strip, int numFrames) {
        if (numFrames == 0) return;
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        stackTrace(st, strip + 2, numFrames);
    }

    /** Prints Java stack traces of all the Java threads. */
    public static void jstackAll() {
        jstackAll(1, -1);
    }

    /**
     * Prints Java stack traces of all the Java threads. But, at most given number of frames.
     *
     * @param numFrames number of frames to be printed. When this is negative all frames are
     *     printed.
     */
    public static void jstackAll(int numFrames) {
        jstackAll(1, numFrames);
    }

    private static void jstackAll(int strip, int numFrames) {
        stackTraceAll(numFrames);
    }

    /**
     * Returns the stack trace of current thread as a String.
     *
     * @return the stack trace as a String.
     */
    public static String jstackStr() {
        return jstackStr(1, -1);
    }

    /**
     * Returns the stack trace of the current thread as a String but includes atmost the given
     * number of frames.
     *
     * @param numFrames number of frames to be included. When this is negative all frames are
     *     included.
     * @return the stack trace as a String.
     */
    public static String jstackStr(int numFrames) {
        if (numFrames == 0) {
            return "";
        }
        return jstackStr(1, numFrames);
    }

    private static String jstackStr(int strip, int numFrames) {
        if (numFrames == 0) {
            return "";
        }
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        return stackTraceStr(st, strip + 2, numFrames);
    }

    /**
     * Returns the stack traces of all Java threads as a String.
     *
     * @return the stack traces as a String.
     */
    public static String jstackAllStr() {
        return jstackAllStr(-1);
    }

    /**
     * Returns atmost given number of frames in stack traces of all threads as a String.
     *
     * @param numFrames number of frames to be included. When this is negative all frames are
     *     included.
     * @return the stack traces as a String.
     */
    public static String jstackAllStr(int numFrames) {
        if (numFrames == 0) {
            return "";
        }
        return stackTraceAllStr(numFrames);
    }

    /**
     * Prints the stack trace of the given exception object.
     *
     * @param exception throwable for which stack trace is printed.
     */
    public static void jstack(Throwable exception) {
        jstack(exception, -1);
    }

    /**
     * Prints the stack trace of the given exception object. But, prints atmost given number of
     * frames.
     *
     * @param exception throwable for which stack trace is printed.
     * @param numFrames maximum number of frames to be printed.
     */
    public static void jstack(Throwable exception, int numFrames) {
        if (numFrames == 0) {
            return;
        }
        StackTraceElement[] st = exception.getStackTrace();
        println(exception.toString());
        stackTrace("\t", st, 0, numFrames);
        Throwable cause = exception.getCause();
        while (cause != null) {
            print("Caused by: ");
            println(cause.toString());
            st = cause.getStackTrace();
            stackTrace("\t", st, 0, numFrames);
            cause = cause.getCause();
        }
    }

    /**
     * Returns the stack trace of given exception object as a String.
     *
     * @param exception the throwable for which stack trace is returned.
     */
    public static String jstackStr(Throwable exception) {
        return jstackStr(exception, -1);
    }

    /**
     * Returns stack trace of given exception object as a String.
     *
     * @param exception throwable for which stack trace is returned.
     * @param numFrames maximum number of frames to be returned.
     */
    public static String jstackStr(Throwable exception, int numFrames) {
        if (numFrames == 0) {
            return "";
        }
        StackTraceElement[] st = exception.getStackTrace();
        StringBuilder buf = new StringBuilder();
        buf.append(Strings.str(exception));
        buf.append(stackTraceStr("\t", st, 0, numFrames));
        Throwable cause = exception.getCause();
        while (cause != null) {
            buf.append("Caused by:");
            st = cause.getStackTrace();
            buf.append(stackTraceStr("\t", st, 0, numFrames));
            cause = cause.getCause();
        }
        return buf.toString();
    }

    /**
     * Returns a reference to the currently executing thread object.
     *
     * @return the currently executing thread.
     */
    public static Thread currentThread() {
        return Thread.currentThread();
    }

    /**
     * Returns the identifier of the given Thread. The thread ID is a positive <tt>long</tt> number
     * generated when the given thread was created. The thread ID is unique and remains unchanged
     * during its lifetime. When a thread is terminated, the thread ID may be reused.
     */
    public static long threadId(Thread thread) {
        return thread.getId();
    }

    /**
     * Returns the state of the given thread. This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     */
    public static Thread.State threadState(Thread thread) {
        return thread.getState();
    }

    /**
     * Returns <tt>true</tt> if and only if the current thread holds the monitor lock on the
     * specified object.
     *
     * <p>This method is designed to allow a program to assert that the current thread already holds
     * a specified lock:
     *
     * <pre>
     *     assert Thread.holdsLock(obj);
     * </pre>
     *
     * @param obj the object on which to test lock ownership
     * @return <tt>true</tt> if the current thread holds the monitor lock on the specified object.
     * @throws NullPointerException if obj is <tt>null</tt>
     */
    public static boolean holdsLock(Object obj) {
        return Thread.holdsLock(obj);
    }

    /** Prints the Java level deadlocks detected (if any). */
    public static void deadlocks() {
        deadlocks(true);
    }

    /**
     * Prints deadlocks detected (if any). Optionally prints stack trace of the deadlocked threads.
     *
     * @param stackTrace boolean flag to specify whether to print stack traces of deadlocked threads
     *     or not.
     */
    public static void deadlocks(boolean stackTrace) {
        String msg = BTraceContext.INSTANCE.deadlocksStr(stackTrace);
        if (msg != null) {
            BTraceContext.INSTANCE.sendMsg(msg);
        }
    }

    /**
     * Returns the name of the given thread.
     *
     * @param thread thread whose name is returned
     */
    public static String name(Thread thread) {
        return thread.getName();
    }


    // stack trace functions
    private static String stackTraceAllStr(int numFrames, boolean printWarning) {
        Set<Map.Entry<Thread, StackTraceElement[]>> traces = Thread.getAllStackTraces().entrySet();
        StringBuilder buf = new StringBuilder();
        for (Map.Entry<Thread, StackTraceElement[]> t : traces) {
            buf.append(t.getKey());
            buf.append(System.lineSeparator());
            buf.append(System.lineSeparator());
            StackTraceElement[] st = t.getValue();
            buf.append(stackTraceStr("\t", st, 0, numFrames, printWarning));
            buf.append(System.lineSeparator());
        }
        return buf.toString();
    }

    static String stackTraceAllStr(int numFrames) {
        return stackTraceAllStr(numFrames, false);
    }

    static void stackTraceAll(int numFrames) {
        BTraceContext.INSTANCE.sendMsg(stackTraceAllStr(numFrames, true));
    }

    static String stackTraceStr(StackTraceElement[] st, int strip, int numFrames) {
        return stackTraceStr(null, st, strip, numFrames, false);
    }

    static String stackTraceStr(String prefix, StackTraceElement[] st, int strip, int numFrames) {
        return stackTraceStr(prefix, st, strip, numFrames, false);
    }

    private static String stackTraceStr(
            String prefix, StackTraceElement[] st, int strip, int numFrames, boolean printWarning) {
        strip = strip > 0 ? strip + THRD_DUMP_FRAMES : 0;
        numFrames = numFrames > 0 ? numFrames : st.length - strip;

        int limit = strip + numFrames;
        limit = Math.min(limit, st.length);

        if (prefix == null) {
            prefix = "";
        }

        StringBuilder buf = new StringBuilder();
        for (int i = strip; i < limit; i++) {
            buf.append(prefix);
            buf.append(st[i]);
            buf.append(System.lineSeparator());
        }
        if (printWarning && limit < st.length) {
            buf.append(prefix);
            buf.append(st.length - limit);
            buf.append(" more frame(s) ...");
            buf.append(System.lineSeparator());
        }
        return buf.toString();
    }

    static void stackTrace(StackTraceElement[] st, int strip, int numFrames) {
        stackTrace(null, st, strip, numFrames);
    }

    static void stackTrace(String prefix, StackTraceElement[] st, int strip, int numFrames) {
        BTraceContext.INSTANCE.sendMsg(stackTraceStr(prefix, st, strip, numFrames, true));
    }
}
