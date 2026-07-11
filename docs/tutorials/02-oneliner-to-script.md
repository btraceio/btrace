# From Oneliner to Script: The Flat DSL

Turn a one-line trace into a real, version-controllable script — and give it the state a
one-liner can't have — in about 10 minutes.

**Persona:** Java devs who want a repeatable probe, not just a one-off. **Time:** ~10 minutes.

This picks up exactly where [Tutorial 1](01-first-trace-in-2-minutes.md) left off: same demo
app, same JVM, same bug hunt. If you don't have it running anymore, start it again in one
terminal (`java DemoApp.java`) and find its PID with `jps` in another — everything below uses
that `<PID>`.

## What you'll need

- JDK 11 or newer on your PATH, and the [demo app](demo/DemoApp.java) running (`java DemoApp.java`)
- BTrace 3.0 installed — `bin/btrace` on your PATH
- A text editor, and this tutorial's script,
  [demo/OrderTiming.java](demo/OrderTiming.java), open next to the demo app

## Step 1 — Peek at what your oneliner really compiled to

Tutorial 1's oneliner wasn't magic — BTrace turned it into a real Java class before compiling it.
You can watch that happen. The client reads the `btrace.oneliner.dump` system property, and
because the `btrace` launcher forwards its arguments to the client's `main()` rather than to the
JVM itself, the way to set that property is `JAVA_TOOL_OPTIONS` (the JVM will confirm it picked
the option up — that first line below is normal and harmless):

```sh
JAVA_TOOL_OPTIONS="-Dbtrace.oneliner.dump=true" btrace -n 'OrderService::processOrder @return { print method, time }' <PID>
```

**You should see**, before the familiar trace stream starts:

```
Picked up JAVA_TOOL_OPTIONS: -Dbtrace.oneliner.dump=true
=== Generated oneliner source (BTraceOneliner_1752219841233.java) ===
package io.btrace.generated;

import java.util.concurrent.atomic.AtomicInteger;
import io.btrace.core.BTraceUtils;
import io.btrace.core.annotations.*;
import io.btrace.core.types.AnyType;

@BTrace
public class BTraceOneliner_1752219841233 {

  @OnMethod(clazz="OrderService", method="processOrder",
      location=@Location(Kind.RETURN))
  public static void probe(@ProbeMethodName String method, @Duration long duration) {
    BTraceUtils.println(method);
    BTraceUtils.println("execution time: " + (duration / 1000000) + " ms");
  }
}
processOrder
execution time: 58 ms
processOrder
execution time: 296 ms
```

(The class name's numeric suffix is just `System.currentTimeMillis()` at compile time — yours
will differ. The trace stream underneath is identical to Tutorial 1's, because it's the exact
same generated class doing the work.)

> **What just happened?** The oneliner compiler (`OnelinerCodeGenerator`) turned your probe
> spec into a small, throwaway `@BTrace` class: one `@OnMethod` hook at `Kind.RETURN`, one
> `println` per action. Notice it writes classic `BTraceUtils.println(...)` calls with an
> explicit `import io.btrace.core.BTraceUtils;` — the generator predates, and doesn't need, the
> flat DSL. But *your own* scripts get the flat DSL automatically, which is what the rest of this
> tutorial is about.

`Ctrl+C` to detach before moving on.

## Step 2 — The same probe, as a real script file

Open [demo/OrderTiming.java](demo/OrderTiming.java) — for now, replace its contents with the
minimal version below (you'll build it back up over the next two steps). This is hand-written,
not generated, and needs no imports at all:

```java
@BTrace
public class OrderTiming {
    @OnMethod(clazz = "OrderService", method = "processOrder", location = @Location(Kind.RETURN))
    public static void onReturn(@ProbeMethodName String method, @Duration long duration) {
        println(method);
        println("execution time: " + (duration / 1_000_000) + " ms");
    }
}
```

Deploy it — note there's no `-n` and no quoting, just a filename after the PID:

```sh
btrace <PID> OrderTiming.java
```

**You should see** the same two-line-per-order output as Step 1:

```
processOrder
execution time: 61 ms
processOrder
execution time: 342 ms
```

> **What just happened?** `btrace <pid> <file>` is BTrace's other submission mode (`-n` is a
> shorthand for the first one). The client reads `OrderTiming.java`, and before handing it to
> `javac` it checks whether the source already imports the DSL or the annotations; since this
> file imports neither, the compiler prepends both `import static io.btrace.BTrace.*;` and
> `import io.btrace.core.annotations.*;` for you (`Compiler.injectDslImport`). That's why
> `println`, `@OnMethod`, and `@BTrace` all resolved with zero imports on your end — and it's the
> same auto-injection path whether the source came from a `.java` file like this one or was typed
> straight into a oneliner.

`Ctrl+C` to detach.

## Step 3 — Make the message richer

`println(method)` on one line and a hand-built duration string on the next is a bit clunky. The
flat DSL (`io.btrace.BTrace`) gives you small helpers for exactly this — `str()` to stringify
non-`String` values, `concat()` to join two strings, `timestamp()` for wall-clock milliseconds
(there's also `monotonic()` for nanosecond timing, which you'll use in the next step). Update the
script to build one line instead of two:

```java
@BTrace
public class OrderTiming {
    @OnMethod(clazz = "OrderService", method = "processOrder", location = @Location(Kind.RETURN))
    public static void onReturn(@ProbeMethodName String method, @Duration long duration) {
        String line = concat(method, " took ") + str(duration / 1_000_000) + "ms at " + str(timestamp());
        println(line);
    }
}
```

Redeploy the same way:

```sh
btrace <PID> OrderTiming.java
```

**You should see** one line per order instead of two:

```
processOrder took 295ms at 1783778244337
processOrder took 68ms at 1783778244419
processOrder took 48ms at 1783778244467
processOrder took 52ms at 1783778244520
```

> **What just happened?** `concat(a, b)` is a plain two-string join — it exists mainly because
> it's null-safe (returns the non-null side, or `""` if both are null), which matters once you're
> composing strings from probe data instead of literals. `str(duration / 1_000_000)` converts the
> `long` to a `String` the same way `String.valueOf` would; `+` still works fine for the rest,
> since these are ordinary Java strings. Nothing here is flat-DSL-only syntax — it's just methods
> you no longer have to import.

`Ctrl+C` to detach again — one more change to make.

## Step 4 — Track state across probes with `@TLS`

Here's something a single `@Duration` can't give you: how many orders *each* of the app's three
worker threads has handled, and how long an order actually takes end to end — from
`validateOrder`'s first line to `chargeCard`'s return — even though those are two different
methods with two different `@OnMethod` hooks. That needs state that survives between separate
probe invocations, kept separate per thread. That's what `@TLS` (thread-local storage) is for:
a field marked `@TLS` behaves like a `ThreadLocal`, transparently, with each thread seeing its own
copy.

Replace the script one more time with the full version already sitting in
[demo/OrderTiming.java](demo/OrderTiming.java):

```java
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
```

Deploy it exactly as before:

```sh
btrace <PID> OrderTiming.java
```

**You should see** each of the three workers reporting its own running count and its own timing
(numbers, thread names, and the exact interleaving will differ):

```
worker=order-worker-1 order #1 total=43ms at 1783778034521
worker=order-worker-0 order #1 total=57ms at 1783778034534
worker=order-worker-2 order #1 total=72ms at 1783778034549
worker=order-worker-0 order #2 total=53ms at 1783778034593
worker=order-worker-1 order #2 total=80ms at 1783778034620
worker=order-worker-2 order #3 total=414ms at 1783778034964
worker=order-worker-2 order #5 total=72ms at 1783778035036
```

Notice `order-worker-2` jumps from `#1` to `#3`, and later from `#3` to `#5` — that's not a bug.
`ordersOnThisThread` is incremented the moment `validateOrder` starts, so a failed validation
(roughly 1 order in 12, per Tutorial 1) still counts as an attempt; you just never see a line for
it, because `chargeCard` — and the probe on it — never runs for that order. You also just caught
the same slow-payment defect from Tutorial 1 again, this time with a number that includes
`validateOrder` and `lookupInventory` too: `total=414ms`.

> **What just happened?** `@TLS` fields are static, but each thread reads and writes its own
> independent copy — the annotation transparently rewrites field access to go through thread-local
> storage. That's what let `onOrderStart` (hooked on `validateOrder`) hand data to `onOrderDone`
> (hooked on a completely different method, `chargeCard`) without the two probes racing on a
> shared field across `order-worker-0/1/2`. One rule to know: you can only read or write a `@TLS`
> field from inside another `@OnMethod`-annotated handler — not from `@OnTimer`, `@OnEvent`, or
> similar global callbacks.

## Step 5 — Clean up

`Ctrl+C` in the BTrace terminal detaches the client and disables the probe immediately — no
restart needed, and you can redeploy as many times as you like while you iterate on a script.
Stop the demo app with `Ctrl+C` in its terminal when you're done for good.

## Troubleshooting

- **`BTrace compilation failed`** — the client prints `javac`-style errors to stderr above this
  line; the usual cause is a typo in the `@OnMethod` clause or a public class name that doesn't
  match the file name (`OrderTiming.java` must contain `public class OrderTiming`).
- **`File not found: OrderTiming.java`** — run `btrace` from the same directory as the script, or
  pass a path: `btrace <PID> demo/OrderTiming.java`.
- **The dump never appears** — `-Dbtrace.oneliner.dump=true` has to reach the JVM running the
  `btrace` client itself, not the target JVM. If you don't see the `Picked up JAVA_TOOL_OPTIONS`
  line, the property never made it in; appending `-Dbtrace.oneliner.dump=true` directly to the
  `btrace ...` command line does **not** work, because the launcher passes its arguments straight
  through to the client's argument parser, not to the JVM's own options.
- **A `@TLS` field is always `0`** — make sure you're reading it from an `@OnMethod` handler; per
  its javadoc, `@TLS` state isn't visible from any other kind of handler.

## Go deeper

- The full flat DSL method list, `@TLS` details, and the INVOKEDYNAMIC rewrite under the hood:
  [BTrace Tutorial, Lessons 7–8](../BTraceTutorial.md)
- Every `@OnMethod` location kind, annotation, and common pattern: [Quick Reference](../QuickReference.md)
- Full oneliner syntax this tutorial started from: [Oneliner Guide](../OnelinerGuide.md)
