# Your First Trace in 2 Minutes

Trace a live JVM — no code changes, no restart, no config. You'll find a latency bug and an
intermittent exception in a running application using nothing but one-line commands.

**Persona:** anyone with a JVM that's misbehaving. **Time:** ~5 minutes (first trace in 2).

## What you'll need

- JDK 11 or newer on your PATH (the demo uses single-file source launch)
- BTrace 3.0 installed — `bin/btrace` on your PATH ([installation options](../GettingStarted.md#installation))
- Two terminal windows

## Step 1 — Start the patient

The demo app ([demo/DemoApp.java](demo/DemoApp.java)) is a tiny order-processing service with two
deliberate defects hidden inside. Run it in terminal 1:

```sh
java DemoApp.java
```

**You should see** (numbers will vary):

```
[demo] order service running - stop with Ctrl+C
[demo] processed 64 orders, 8 failed
[demo] processed 134 orders, 14 failed
```

Orders are being processed, and some of them fail. Why? The app won't tell you — let's ask the JVM
directly.

## Step 2 — Find its PID

In terminal 2:

```sh
jps
```

**You should see** a line like `12345 DemoApp`. That number is the `<PID>` in every command below.

## Step 3 — Your first trace

A BTrace *oneliner* attaches to the running JVM and prints every call of a method, live:

```sh
btrace -n 'OrderService::processOrder @return { print method, time }' <PID>
```

**You should see** a stream like:

```
processOrder
execution time: 61 ms
processOrder
execution time: 74 ms
processOrder
execution time: 342 ms
```

> **What just happened?** BTrace compiled your one-liner into a tiny instrumentation program,
> injected it into the running JVM, and hooked the return of `OrderService.processOrder` — all
> while the app kept serving orders. Nothing was restarted, and when you detach, every hook is
> cleanly removed. Curious what it generated? Re-run with `-Dbtrace.oneliner.dump=true` to see the
> generated source.

Notice something? Most orders take well under 100 ms, but every now and then one takes 300+ ms.
Let's isolate the slow ones.

## Step 4 — Catch the latency bug

Stop the previous trace with `Ctrl+C`, then widen the net to *all* `OrderService` methods and keep
only the slow calls:

```sh
btrace -n 'OrderService::* @return if duration>200ms { print method, time }' <PID>
```

**You should see** two method names, always together:

```
chargeCard
execution time: 306 ms
processOrder
execution time: 331 ms
```

Case closed: `chargeCard` is the culprit (a "slow payment provider" hits ~10% of calls), and its
latency is what makes `processOrder` slow. You found it with one line, without reading a single
line of application code.

> `if duration > 200ms` filters on the method's execution time. In `print`, the raw `duration`
> value is in **nanoseconds**; the `time` action prints it converted to milliseconds for you.

## Step 5 — Catch the failures

The summary line also reported failed orders. Hook the *error path* — this probe only fires when a
method exits by throwing:

```sh
btrace -n 'OrderService::validateOrder @error { print method, stack }' <PID>
```

**You should see**, for roughly one order in twelve:

```
validateOrder
OrderService.validateOrder(DemoApp.java:93)
OrderService.processOrder(DemoApp.java:85)
...
```

The exception's origin and the exact call path — captured live, from a method that the application
catches and silently swallows.

## Step 6 — Clean up

`Ctrl+C` in the BTrace terminal detaches the client. In BTrace 3.0, detaching disables all
injected probes on the spot (they become no-ops — no restart, no lingering overhead), and you can
re-attach at any time. Stop the demo app with `Ctrl+C` in terminal 1 when you're done.

## Troubleshooting

- **`Can not attach to PID ...`** — make sure you run `btrace` as the same OS user as the target
  JVM, and check [Troubleshooting: attachment issues](../Troubleshooting.md).
- **A warning about your Java version** — if the target JVM is older than Java 17 you'll see the
  3.0 deprecation warning. Everything still works; see the
  [Java support policy](../Migration-2.x-to-3.0.md) for what it means.
- **No output appears** — the probe may not match: patterns are exact class names plus `*`/`?`
  wildcards. Try `OrderService::*` first, then narrow down.

## Go deeper

- Full oneliner syntax (filters, `args`, `count`, regex patterns): [Oneliner Guide](../OnelinerGuide.md)
- Turn a oneliner into a real script with the flat DSL: [Tutorial Lesson 7](../BTraceTutorial.md)
- Everything you can hook (`@OnMethod` locations, JFR, timers): [Quick Reference](../QuickReference.md)
