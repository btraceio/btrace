# Extensions and Permissions Without Tears

Give a probe a real capability — HdrHistogram-backed latency percentiles — instead of just
printing samples. Then see, precisely, what BTrace does when that capability isn't allowed, and
how to inspect and manage that decision from the outside.

**Persona:** a Java developer who wants more than the flat DSL alone (aggregation, real metrics,
integrations) without accidentally opening a hole in the target JVM. **Time:** ~10 minutes.

## What you'll need

- JDK 11 or newer on your PATH (the demo uses single-file source launch)
- BTrace 3.0 installed — `bin/btrace` and `bin/btracex` on your PATH ([installation options](../GettingStarted.md#installation))
- Two terminal windows

## Step 1 — Start the patient

Same demo app as the rest of this series ([demo/DemoApp.java](demo/DemoApp.java)): an
order-processing service where `chargeCard` is slow about 10% of the time. Run it in terminal 1:

```sh
java DemoApp.java
```

**You should see:**

```
[demo] order service running - stop with Ctrl+C
[demo] processed 64 orders, 8 failed
[demo] processed 134 orders, 14 failed
```

[Tutorial 1](01-first-trace-in-2-minutes.md) found this latency defect with a one-liner. This time
you'll turn the raw numbers into real percentiles a probe can reason about, using a service instead
of `print`.

Get its PID in terminal 2 (`jps`, look for `DemoApp`) — you'll need it for every command below.

## Step 2 — Ask BTrace to hand your probe a metrics service

A oneliner can print or count, but it can't compute p95 latency. For that you inject a real service
— [demo/LatencyHistogram.java](demo/LatencyHistogram.java) asks for the bundled `btrace-metrics`
extension's `MetricsService` and uses it to build a histogram per method:

```java
import io.btrace.core.annotations.*;
import io.btrace.metrics.MetricsService;
import io.btrace.metrics.histogram.HistogramMetric;
import io.btrace.metrics.histogram.HistogramSnapshot;

@BTrace
public class LatencyHistogram {
  @Injected(optional = true)
  private static MetricsService metrics;

  private static HistogramMetric chargeCardLatency;
  private static HistogramMetric processOrderLatency;

  @OnMethod(clazz = "OrderService", method = "chargeCard", location = @Location(Kind.RETURN))
  public static void onChargeCardReturn(@Duration long durationNanos) {
    if (chargeCardLatency == null) {
      chargeCardLatency = metrics.histogramMillis("chargeCard.latency");
    }
    chargeCardLatency.record(durationNanos / 1_000_000);
  }

  @OnMethod(clazz = "OrderService", method = "processOrder", location = @Location(Kind.RETURN))
  public static void onProcessOrderReturn(@Duration long durationNanos) {
    if (processOrderLatency == null) {
      processOrderLatency = metrics.histogramMillis("processOrder.latency");
    }
    processOrderLatency.record(durationNanos / 1_000_000);
  }

  @OnTimer(5000)
  public static void report() {
    println("=== Latency Report ===");
    if (chargeCardLatency != null) {
      HistogramSnapshot h = chargeCardLatency.snapshot();
      println("chargeCard    p50=" + h.p50() + "ms  p95=" + h.p95() + "ms  p99=" + h.p99()
          + "ms  (n=" + h.count() + ")");
    }
    if (processOrderLatency != null) {
      HistogramSnapshot h = processOrderLatency.snapshot();
      println("processOrder  p50=" + h.p50() + "ms  p95=" + h.p95() + "ms  p99=" + h.p99()
          + "ms  (n=" + h.count() + ")");
    }
    println("=======================");
  }
}
```

> **What just happened?** `@Injected` marks a static field as a service to be wired in by BTrace's
> extension bridge — you never construct it yourself (probes can't call `new` on arbitrary types at
> all; only builders/factories handed back by injected services are allowed). `metrics` here comes
> from `btrace-metrics`, a bundled extension that wraps [HdrHistogram](https://github.com/HdrHistogram/HdrHistogram)
> for accurate, allocation-free percentile tracking entirely inside the target JVM — no server, no
> network call. `optional = true` is the one non-obvious choice: it tells BTrace that if this
> service can't be linked, hand back a stub instead of refusing to run the probe at all. You'll see
> exactly why that matters in Step 4.

## Step 3 — Grant the permission and watch the histogram fill in

Before this works, there's a fact worth knowing: `btrace-metrics` requires the `THREADS`
permission (it runs a background thread for HdrHistogram bookkeeping), and `THREADS` sits in
BTrace's **privileged** tier — permissions with real security weight that are blocked unless you
explicitly say otherwise. (The three tiers — default, standard, privileged — are documented in
[Lesson 6](../BTraceTutorial.md#lesson-6---extensions-and-permissions) and
[Permission Policy](../PermissionPolicy.md); `THREADS` is privileged in both.)

So the first time BTrace attaches to this JVM, it needs to know `btrace-metrics` is allowed to link
its real implementation. Create a policy file for it:

```sh
mkdir -p ~/.btrace
cat > ~/.btrace/permissions.properties <<'EOF'
allowExtensions=btrace-metrics
EOF
```

Now deploy the probe:

```sh
btrace <PID> LatencyHistogram.java
```

**You should see**, every 5 seconds (numbers will vary):

```
=== Latency Report ===
chargeCard    p50=27ms  p95=321ms  p99=378ms  (n=41)
processOrder  p50=58ms  p95=346ms  p99=402ms  (n=41)
=======================
=== Latency Report ===
chargeCard    p50=29ms  p95=318ms  p99=381ms  (n=89)
processOrder  p50=61ms  p95=349ms  p99=405ms  (n=89)
=======================
```

> **What just happened?** BTrace reads permission policy from (in order) `-Dbtrace.permissions=...`,
> then `~/.btrace/permissions.properties`, then a classpath resource — the first one found wins.
> Because this is the *first* time BTrace's agent has attached to this particular `DemoApp` process,
> it loads that file once and keeps the decision for the JVM's whole lifetime; re-attaching or
> re-running scripts against the same PID won't reload it. That's why the next step restarts the
> demo app instead of just editing the file again. Notice the p95/p99 gap between the two methods
> stays close: `chargeCard`'s slow ~10% *is* what makes `processOrder` slow, exactly like in
> [Tutorial 1](01-first-trace-in-2-minutes.md#step-4--catch-the-latency-bug) — this time with real
> percentiles instead of eyeballing a stream of `execution time:` lines.

## Step 4 — Deny the permission and watch it fail loudly

Stop the demo app (`Ctrl+C` in terminal 1) and flip the policy from allow to deny:

```sh
cat > ~/.btrace/permissions.properties <<'EOF'
denyExtensions=btrace-metrics
EOF
```

Start a fresh demo app (new PID — check with `jps` again), then deploy the exact same script:

```sh
java DemoApp.java   # terminal 1, new process
btrace <NEW_PID> LatencyHistogram.java   # terminal 2
```

**You should see** something like this within the first couple of seconds, repeating on every
`chargeCard`/`processOrder` return:

```
! ERROR
java.lang.IllegalStateException: BTrace optional service unavailable: io.btrace.metrics.MetricsService
	at io.btrace.runtime.ExtensionIndy$ThrowingHandler.invoke(ExtensionIndy.java:287)
	...
	at LatencyHistogram.onChargeCardReturn(LatencyHistogram.java:45)
	...
Caused by: java.lang.IllegalStateException: No implementation available for service (interface returned): io.btrace.metrics.MetricsService
	at io.btrace.runtime.ExtensionIndy.requireServiceClass(ExtensionIndy.java:166)
	at io.btrace.runtime.ExtensionIndy.bootstrapFieldGet(ExtensionIndy.java:82)
	...
```

`Ctrl+C` as soon as you've seen it once — it repeats on every probed call, which gets noisy fast.

> **What just happened?** With `denyExtensions=btrace-metrics` set, BTrace's extension bridge
> refuses to link the real `MetricsServiceImpl` and records the reason internally as `"Blocked by
> policy (denyExtensions)"` — you'll read that exact string back in the next step. Because our
> field is `@Injected(optional = true)` and no mode was pinned, BTrace falls back to its *default*
> shim mode, `THROW`: instead of a working service, `metrics` becomes a stub that throws on every
> method call. The exception happens inside your probe's own action code, so BTrace's runtime
> catches it and reports it to you as an `! ERROR` block, exactly like any other probe exception —
> your target JVM keeps running untouched. Had the field *not* been optional, BTrace would have
> refused to link the script at all rather than run it with a throwing stub — "required injections
> never fall back and fail fast if unavailable," per the
> [Extension Interface Rules](../ExtensionInterfaceRules.md). Either way, nothing silently
> half-works.

## Step 5 — Ask BTrace which extensions failed

Rather than parsing exception noise, there's a purpose-built answer to "what got blocked and why":

```sh
btrace -le <NEW_PID>
```

**You should see:**

```
Failed Extensions:
  1. btrace-metrics: Blocked by policy (denyExtensions)
```

> **What just happened?** `-le` attaches, asks the agent for its failure registry, and disconnects
> — it doesn't touch any running probes (same shape as `-lp` for listing active probes). Had the
> extension instead been blocked for being *privileged and ungranted* (the state you'd be in with
> an empty policy file and no `allowExtensions`/`allowPrivileged` at all), the reason string would
> instead read `Blocked privileged extension. Required=[THREADS]` — the same registry, a different
> cause.

## Step 6 — Inspect extensions and policy from the outside

`btracex` is the standalone tool for answering "what's installed, and what does policy currently
allow" without touching a running JVM at all.

```sh
btracex list
```

**You should see** one line per discovered extension, `[PRIV]` flagging anything with a privileged
permission:

```
btrace-metrics [PRIV] - /opt/btrace-3.0.0/extensions/btrace-metrics
```

Look closer at one of them:

```sh
btracex inspect btrace-metrics
```

**You should see:**

```
Extension: btrace-metrics
Version  : 3.0.0-SNAPSHOT
Privileged: true
Required : [THREADS]
Services : io.btrace.metrics.MetricsService
```

And check what the policy file actually says right now — the same file you were hand-editing
in Steps 3–4:

```sh
btracex policy print
```

**You should see:**

```
Policy: /home/you/.btrace/permissions.properties
  allowExtensions=
  denyExtensions=btrace-metrics
  allowPrivileged=false
```

To flip it back to allowed the supported way (equivalent to the `cat >` from Step 3, but it's the
tool's job from here on, not yours):

```sh
btracex policy set --allowExtensions btrace-metrics
```

**You should see:**

```
Policy saved to /home/you/.btrace/permissions.properties
```

> **What just happened?** `btracex list`/`inspect` read extension manifests directly — no running
> JVM needed, which is why they work identically whether or not anything is attached right now.
> `policy print`/`set` read and write the exact same `~/.btrace/permissions.properties` your probes
> load from, by default (`--policy-file`/`--classpath` target other locations). One thing `btracex`
> does *not* yet do: `btracex policy edit` is a placeholder — it prints "Interactive editor not yet
> implemented; use 'btracex policy set' for now" and exits. Use `set`.

## Step 7 — Clean up

`Ctrl+C` the `btrace` client if it's still attached (probes detach cleanly, same as every other
tutorial in this series), then `Ctrl+C` the demo app in terminal 1. If you want to leave your
BTrace installation in its original state, clear the policy file:

```sh
rm ~/.btrace/permissions.properties
```

## Troubleshooting

- **`btracex list` prints nothing** — it scans `$BTRACE_HOME/extensions/` and
  `~/.btrace/extensions/` (plus `$BTRACE_EXT_PATH` if set). Confirm `BTRACE_HOME` is set and that
  your distribution actually exploded extensions under it.
- **Histogram never appears, but no `! ERROR` either** — check `~/.btrace/permissions.properties`
  was created *before* the first `btrace` attach against that PID; policy loads once per JVM
  lifetime (see Step 3's callout). Restart the demo app after editing the file.
- **`btrace -le <PID>` says `No extension failures detected.`** — nothing was blocked; if you
  expected a denial, confirm you restarted the demo app after switching to `denyExtensions=...`
  rather than reusing the earlier allowed process.

## Go deeper

- The full permission model, tiers, and file format: [Permission Policy](../PermissionPolicy.md)
- Extensions and permissions as a concept, plus the StatsD example: [Tutorial Lesson 6](../BTraceTutorial.md#lesson-6---extensions-and-permissions)
- Writing your own extension (API/impl split, permission scanning, shims): [BTrace Extension Development Guide](../BTraceExtensionDevelopmentGuide.md)
