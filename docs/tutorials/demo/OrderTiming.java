/*
 * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Companion script for docs/tutorials/02-oneliner-to-script.md.
 *
 * <p>Deploy against the shared demo app ({@code docs/tutorials/demo/DemoApp.java}):
 *
 * <pre>{@code btrace <PID> OrderTiming.java}</pre>
 *
 * <p>No imports needed - the compiler auto-injects a static import of the flat DSL ({@code
 * io.btrace.BTrace.*}) and an import of the annotations package ({@code
 * io.btrace.core.annotations.*}) into any script that doesn't already declare them (see {@code
 * btrace-compiler/.../Compiler.java#injectDslImport}). Its check is a plain text search for the
 * import statements, so this comment deliberately never spells them out in full.
 *
 * <p>Tracks, per worker thread, how many orders that thread has attempted and how long its most
 * recently *completed* order took end to end - from {@code validateOrder} entry through {@code
 * chargeCard} return. A single method's {@code @Duration} (what the Tutorial 1 oneliner used)
 * can't express that: it only spans one method's own entry/return. Bridging state between two
 * different {@code @OnMethod} handlers on two different methods is exactly what {@code @TLS}
 * (thread-local storage) is for.
 */
@BTrace
public class OrderTiming {

    // One copy of each field per thread - order counts and timers never mix between
    // order-worker-0, order-worker-1, and order-worker-2.
    @TLS
    private static long orderStart;

    @TLS
    private static long ordersOnThisThread;

    // First thing that happens for every order: start the clock and count the attempt
    // (even orders that fail validation a few lines later still count as "attempted").
    @OnMethod(clazz = "OrderService", method = "validateOrder")
    public static void onOrderStart() {
        orderStart = monotonic();
        ordersOnThisThread = ordersOnThisThread + 1;
    }

    // Last thing that happens for a *successful* order: chargeCard returning means
    // validateOrder and lookupInventory already completed, in order, on this same thread.
    @OnMethod(clazz = "OrderService", method = "chargeCard", location = @Location(Kind.RETURN))
    public static void onOrderDone() {
        long totalMs = (monotonic() - orderStart) / 1_000_000L;
        String who = concat("worker=", threadName(currentThread()));
        String line = who + " order #" + str(ordersOnThisThread)
                + " total=" + str(totalMs) + "ms at " + str(timestamp());
        println(line);
    }
}
