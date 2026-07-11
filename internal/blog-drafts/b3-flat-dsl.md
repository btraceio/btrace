# The flat DSL: BTrace scripts without the boilerplate

*How a throwaway one-liner grows into a real, version-controlled script — without you ever typing an import statement.*

> Draft B3 · target: BTrace 3.0.0 + 1 week · grounded in `docs/tutorials/02-oneliner-to-script.md`

If you've spent any time with BTrace 3.0, you've probably reached for a one-liner first. Something like `OrderService::processOrder @return { print method, time }`, fired straight from the command line at a running JVM, no file to save, no build step to wait on. It's the fastest way to answer a question about a live process. But one-liners have a ceiling: they can't hold state, they can't grow past a line or two of logic, and they vanish the moment you close the terminal. Sooner or later you want the same probe as a real file you can check into source control, hand to a teammate, or extend. That's where the flat DSL comes in — and the nice surprise is how little it asks of you to get there.

## What your one-liner was doing all along

Here's the thing most people never see: a one-liner was never really "shorthand." Under the hood, BTrace's `OnelinerCodeGenerator` was quietly turning your probe spec into a real, throwaway `@BTrace` class before handing it to `javac`. You can watch this happen by setting `-Dbtrace.oneliner.dump=true` (via `JAVA_TOOL_OPTIONS`, since the `btrace` launcher forwards its own arguments to the client rather than the JVM). What comes back looks exactly like a script you'd write by hand — imports and all:

```java
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
```

Notice the explicit `import io.btrace.core.BTraceUtils;` and every call going through `BTraceUtils.println(...)`. That's not an accident of the demo — it's because the oneliner generator predates the flat DSL and doesn't need it. It writes the classic, fully-qualified form because it can.

## The same probe, with zero imports

Your own scripts don't have to look like that. Take the exact same logic, written by hand as a real `.java` file, and it collapses to this:

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

No `import` line at all. Deploy it with `btrace <PID> OrderTiming.java` — no `-n`, no quoting, just a filename after the PID — and you get the identical two-line-per-order output. The reason is almost mundane once you know it: when the client reads a script file, it checks whether the source already imports the DSL or the annotations, and if it doesn't, the compiler prepends both `import static io.btrace.BTrace.*;` and `import io.btrace.core.annotations.*;` for you before it ever reaches `javac`. `println`, `@OnMethod`, `@BTrace` — all of it resolves because BTrace quietly did the import for you. It's the same auto-injection path whether the source came from a `.java` file or was typed straight into a one-liner; the generator just happens to write its imports out explicitly instead of relying on it.

## Making the message earn its keep

Two `println` calls for one event gets clunky fast, and the flat DSL (`io.btrace.BTrace`) has small helpers built for exactly this: `str()` to stringify a non-`String` value, `concat()` for a null-safe two-string join, and `timestamp()` for wall-clock milliseconds. One line replaces two:

```java
String line = concat(method, " took ") + str(duration / 1_000_000) + "ms at " + str(timestamp());
println(line);
```

Redeploy, and instead of a method name on one line and a duration on the next, you get `processOrder took 295ms at 1783778244337` — one legible line per order. None of this is flat-DSL-only syntax; `concat`, `str`, and `timestamp` are just methods you no longer have to qualify.

## State that survives between probes

The real payoff shows up once you want something a single `@Duration` parameter can't give you: how many orders each of the app's worker threads has handled, and how long an order takes end-to-end — from `validateOrder`'s first line to `chargeCard`'s return — even though those are two separate methods with two separate `@OnMethod` hooks. That needs state that survives *between* probe invocations, kept separate per thread. `@TLS` (thread-local storage) does exactly that: a field marked `@TLS` behaves like a `ThreadLocal`, transparently, with each thread reading and writing its own copy. One probe on `validateOrder` starts the clock and bumps a counter; a second probe on `chargeCard`'s return reads both back, and the two never race across `order-worker-0/1/2`. The output tells its own story — workers reporting independent running counts, `order-worker-2` jumping from `#1` to `#3` because a failed validation still counted as an attempt even though no `chargeCard` probe ever fired for it, and a `total=414ms` line quietly confirming the same slow-payment defect earlier tutorials caught, this time counted from further upstream.

One rule worth carrying forward: you can only read or write a `@TLS` field from inside another `@OnMethod`-annotated handler — not from `@OnTimer`, `@OnEvent`, or similar global callbacks. It's a small constraint for a lot of leverage: cross-probe, per-thread state, with no imports, no boilerplate, and no ceremony beyond an annotation on a field.

---

- Full hands-on walkthrough: [docs/tutorials/02-oneliner-to-script.md](../../docs/tutorials/02-oneliner-to-script.md)
- New to BTrace? Start here: [../GettingStarted.md](../GettingStarted.md)
- Questions, ideas, war stories: [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
