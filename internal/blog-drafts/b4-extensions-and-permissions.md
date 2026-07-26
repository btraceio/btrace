# Extensions: BTrace's new superpower (safely)

*Real metrics, real capabilities — and exactly what BTrace does the moment you don't have permission for them.*

> Draft B4 · target: BTrace 3.0.0 + 2 weeks · grounded in `docs/tutorials/04-extensions-and-permissions.md`

A one-liner can print a number. It can even count them. What it can't do is give you p95 latency — that takes real bookkeeping, and real bookkeeping is exactly what BTrace's extension system is for. The interesting part of this story isn't the happy path, though. It's what happens the first time you *don't* have permission for the capability you just asked for, and how loudly — and safely — BTrace tells you so.

## Asking for a real capability

The demo here is the same order-processing app the rest of the tutorial series uses, where `chargeCard` runs slow about 10% of the time. Tutorial 1 caught that with a one-liner and a stream of `execution time:` lines. This time, instead of eyeballing raw numbers, a script asks BTrace to hand it a real metrics service — `btrace-metrics`, a bundled extension that wraps HdrHistogram for allocation-free percentile tracking entirely inside the target JVM, no server, no network call:

```java
@Injected(optional = true)
private static MetricsService metrics;

private static HistogramMetric chargeCardLatency;

@OnMethod(clazz = "OrderService", method = "chargeCard", location = @Location(Kind.RETURN))
public static void onChargeCardReturn(@Duration long durationNanos) {
  if (chargeCardLatency == null) {
    chargeCardLatency = metrics.histogramMillis("chargeCard.latency");
  }
  chargeCardLatency.record(durationNanos / 1_000_000);
}
```

`@Injected` marks a static field as a service BTrace wires in for you — probes can't call `new` on arbitrary types at all, only builders and factories handed back by injected services. `optional = true` is the one non-obvious choice here, and it's the choice that makes the rest of this story survivable rather than fatal.

## The permission that has to be earned

Here's the fact worth knowing before you deploy anything: `btrace-metrics` needs the `THREADS` permission, because it runs a background thread for HdrHistogram's own bookkeeping — and `THREADS` sits in BTrace's **privileged** tier, blocked unless you explicitly say otherwise. Grant it with a policy file:

```sh
mkdir -p ~/.btrace
cat > ~/.btrace/permissions.properties <<'EOF'
allowExtensions=btrace-metrics
EOF
```

Deploy the script against a fresh JVM and the histograms fill in exactly as you'd hope, ticking every five seconds: `chargeCard    p50=27ms  p95=321ms  p99=378ms  (n=41)`. One detail matters more than it looks: BTrace reads this policy file once, the first time it attaches to a given process, and keeps that decision for the JVM's whole lifetime. Edit the file after the fact and nothing changes until you restart the target.

## What "safely" actually means

Now flip the policy the other way:

```sh
cat > ~/.btrace/permissions.properties <<'EOF'
denyExtensions=btrace-metrics
EOF
```

Start a fresh demo app, deploy the identical script, and within a couple of seconds you get this, repeating on every `chargeCard`/`processOrder` return:

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

This is the "safely" part of the headline, and it's worth spelling out precisely because it's easy to imagine this going worse. With `denyExtensions=btrace-metrics` in place, BTrace's extension bridge refuses to link the real `MetricsServiceImpl` and records the reason internally as `"Blocked by policy (denyExtensions)"`. Because the field was declared `@Injected(optional = true)`, BTrace doesn't refuse to run the script at all — it falls back to its default shim mode, `THROW`, handing back a stub that throws on every call instead of a working service. The exception happens inside the probe's own action code, so BTrace's runtime catches it and reports it as an `! ERROR` block, exactly like any other probe exception. The target JVM keeps running, untouched, the whole time. Had the field *not* been optional, BTrace would have refused to link the script in the first place rather than run it with a throwing stub — required injections never fall back and fail fast if unavailable. Either way, nothing silently half-works, and nothing you didn't grant gets through.

## Diagnosing it without reading stack traces

You don't have to parse exception noise to find out what got blocked. There's a purpose-built answer:

```sh
btrace -le <NEW_PID>
```

```
Failed Extensions:
  1. btrace-metrics: Blocked by policy (denyExtensions)
```

`-le` attaches, asks the agent for its failure registry, and disconnects — it doesn't touch any running probes, the same shape as `-lp` for listing active ones. Had `btrace-metrics` instead been blocked for being privileged and simply ungranted — an empty policy file, no `allowExtensions` or `allowPrivileged` set at all — the same registry would report a different cause: `Blocked privileged extension. Required=[THREADS]`.

And when you want to know what's installed and what policy allows *without* touching a running JVM at all, that's `btracex`'s job:

```
$ btracex list
btrace-metrics [PRIV] - /opt/btrace-3.0.0/extensions/btrace-metrics

$ btracex inspect btrace-metrics
Extension: btrace-metrics
Version  : 3.0.0-SNAPSHOT
Privileged: true
Required : [CLASSLOADER,REFLECTION,THREADS]
Services : io.btrace.metrics.MetricsService

$ btracex policy print
Policy: /home/you/.btrace/permissions.properties
  allowExtensions=
  denyExtensions=btrace-metrics
  allowPrivileged=false
```

And to flip a decision the supported way instead of hand-editing the file: `btracex policy set --allowExtensions btrace-metrics`, which reports back `Policy saved to /home/you/.btrace/permissions.properties`. (`btracex policy edit` isn't there yet — it prints a placeholder message and exits, so `set` is the tool for now.)

That's the whole shape of the story: a real capability, a real permission gate in front of it, a loud and specific failure when the gate is closed, and two independent ways — `-le` from inside a running JVM, `btracex` from outside any JVM at all — to find out exactly why.

---

- Full hands-on walkthrough: [docs/tutorials/04-extensions-and-permissions.md](../../docs/tutorials/04-extensions-and-permissions.md)
- New to BTrace? Start here: [../GettingStarted.md](../GettingStarted.md)
- Questions, ideas, war stories: [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
