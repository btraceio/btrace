# What Changed for 2.x Users, in 15 Minutes

You know BTrace 2.x. In one sitting, on one live JVM, you will see what is different on day one:
attach with no script file, run a probe compiled by 2.2.6 untouched, migrate a 2.x source file
with one command, and replace the removed `Aggregations` API with the bundled metrics extension.
Each step links to the single-topic tutorial that covers it in depth.

![Demo 01: three oneliners against the live order service](images/01-oneliner.gif)

*Recorded from [`demos/01-oneliner/run.sh`](../../demos/01-oneliner/README.md); the MP4 is next to the GIF.*

**Persona:** someone who used BTrace 2.x and wants to know what is different now. **Time:** ~15
minutes.

## What you'll need

- JDK 11 or newer on your PATH, with `JAVA_HOME` set
- BTrace 3.0 installed: `bin/btrace` on your PATH ([installation options](../GettingStarted.md#installation))
- A checkout of the BTrace repository: the demo files and `scripts/migrate-btrace-script.sh` live
  there, not in the binary distribution
- Two terminal windows. Run every command from the repository root unless a step says otherwise.

Every file used below is in [`demos/`](../../demos/README.md); each demo directory also has a
`run.sh` that replays its whole story unattended.

## Step 1 - Start the patient

The target is the order service from [demo 01](../../demos/01-oneliner/README.md): `chargeCard` is
slow for about 10% of calls, and `validateOrder` throws for about 8% of orders and the app
swallows it. In terminal 1:

```sh
cd demos/01-oneliner
java OrderServiceApp.java
```

In terminal 2, find its PID with `jps`. That number is `<PID>` in every command below.

**You should see** in terminal 1 a banner and then, periodically, a `processed N orders` summary
line. Note that the app was started with no BTrace flags at all.

## Step 2 - Oneliners: no file, no compile

In 2.x, tracing meant writing a script, compiling it or letting the client compile it, and
deploying it. In 3.0 a probe fits on the command line. In terminal 2:

```sh
btrace -n 'OrderService::processOrder @return { print method, time }' <PID>
```

**You should see** (numbers vary):

```
processOrder
execution time: 54 ms
processOrder
execution time: 354 ms
```

Press `Ctrl+C`, then choose `1. exit`. Now keep only the slow calls on every `OrderService`
method:

```sh
btrace -n 'OrderService::* @return if duration>200ms { print method, time }' <PID>
```

**You should see** only the slow calls, always in pairs:

```
chargeCard
execution time: 345 ms
processOrder
execution time: 394 ms
```

Exit again, then hook the error path of the method whose exceptions the app swallows:

```sh
btrace -n 'OrderService::validateOrder @error { print method, stack }' <PID>
```

**You should see**, the call path below the throwing method:

```
validateOrder
OrderService.processOrder(OrderServiceApp.java:81)
OrderServiceApp.lambda$main$0(OrderServiceApp.java:47)
java.base/java.lang.Thread.run(Thread.java:842)
```

> **What just happened?** The client turned each oneliner into a small probe class, injected it
> into the running JVM, and the agent hooked the methods. Exiting the client makes the probe's
> hooks no-ops, so nothing lingers. The full syntax is in the [Oneliner Guide](../OnelinerGuide.md);
> the step-by-step version of this exact investigation is
> [Your First Trace in 2 Minutes](01-first-trace-in-2-minutes.md).

## Step 3 - From oneliner to a flat-DSL script

A oneliner is a real probe underneath, and you can look at the source it generates. The property
has to reach the client JVM, so set it through `JAVA_TOOL_OPTIONS`; appending `-D...` to the
`btrace` command line does not work, because the launcher passes its arguments straight to the
client's argument parser:

```sh
JAVA_TOOL_OPTIONS="-Dbtrace.oneliner.dump=true" btrace -n 'OrderService::processOrder @return { print method, time }' <PID>
```

**You should see**, before the trace stream starts (the numeric suffix of the class name differs):

```
Picked up JAVA_TOOL_OPTIONS: -Dbtrace.oneliner.dump=true
=== Generated oneliner source (BTraceOneliner_1752219841233.java) ===
package io.btrace.generated;
...
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

Exit the session. Your own scripts need none of that boilerplate: the flat DSL drops the
`BTraceUtils.` prefixes and the import block. Turning this probe into a script file and adding
per-thread state with `@TLS` is [From Oneliner to Script](02-oneliner-to-script.md).

## Step 4 - Your compiled 2.x probes just work

Here is the part that matters most if you have probes in production. The demo ships
`SlowChargeProbe.class`, compiled by BTrace 2.2.6 against the old `org.openjdk.btrace` package.
Check that it really is a 2.x artifact, then deploy it on the 3.0 agent as is:

```sh
cd ../02-upgrade-from-2x
strings legacy/SlowChargeProbe.class | grep -m1 org/openjdk/btrace
btrace <PID> legacy/SlowChargeProbe.class
```

**You should see** the old package name in the class file, then the probe running:

```
  4org/openjdk/btrace/runtime/auxiliary/SlowChargeProbe
[main] INFO io.btrace.client.Main - Attaching BTrace to PID: <PID>
[main] INFO io.btrace.client.Client - Successfully started BTrace probe: legacy/SlowChargeProbe.class
slow chargeCard: 260 ms

slow chargeCard: 313 ms
```

> **What just happened?** The agent rewrote the `org/openjdk/btrace` references to `io/btrace`
> in memory when it loaded the persisted probe class. The file on disk is unchanged and you did
> not recompile anything. You may also see a JVM warning about
> `AllowRedefinitionToAddDeleteMethods`; it comes from the JDK, not from BTrace.

Exit the session.

## Step 5 - Your source scripts need one line

Compiled probes are untouched, but a 2.x *source* file does not compile against 3.0 because its
imports name the old package. `scripts/migrate-btrace-script.sh` rewrites them. Work on a copy,
and preview first:

```sh
mkdir -p /tmp/migrated && cp legacy/SlowChargeProbe.java /tmp/migrated/
../../scripts/migrate-btrace-script.sh --dry-run /tmp/migrated/SlowChargeProbe.java
```

**You should see** the lines that would change, and nothing is modified yet:

```
would migrate: /tmp/migrated/SlowChargeProbe.java
    9:import org.openjdk.btrace.core.annotations.BTrace;
    10:import org.openjdk.btrace.core.annotations.OnMethod;
    ...
    15:import static org.openjdk.btrace.core.BTraceUtils.str;
Dry run: 1 file(s) would be migrated (of 1 examined).
```

Apply it and deploy the migrated source:

```sh
../../scripts/migrate-btrace-script.sh /tmp/migrated/SlowChargeProbe.java
btrace <PID> /tmp/migrated/SlowChargeProbe.java
```

**You should see:**

```
migrated: /tmp/migrated/SlowChargeProbe.java (backup: /tmp/migrated/SlowChargeProbe.java.bak)
    9:import io.btrace.core.annotations.BTrace;
    ...
Done: 1 file(s) migrated (of 1 examined).
```

and then the same `slow chargeCard: ... ms` lines as in Step 4. The script leaves a `.bak` backup
next to each file it changes. The longer version, including what else to check in a 2.x
deployment, is [Upgrade from 2.x in 10 Minutes](03-upgrade-from-2x.md) and the
[Migration guide](../Migration-2.x-to-3.0.md).

Exit the session.

## Step 6 - `Aggregations` is gone; `btrace-metrics` replaces it

The `Aggregations` API was removed in 3.0. Histograms now come from the bundled `btrace-metrics`
extension (HdrHistogram). It is a privileged extension, so the agent links it only when policy
allows. Deploy the probe from [demo 03](../../demos/03-metrics-and-permissions/README.md) against
the app as it is, with no policy:

```sh
cd ../03-metrics-and-permissions
btrace <PID> LatencyHistogram.java
```

**You should see** the probe run with a throwing stub in place of the service:

```
! ERROR
java.lang.IllegalStateException: BTrace optional service unavailable: io.btrace.metrics.MetricsService
```

Exit the session and ask the agent why:

```sh
btrace -le <PID>
```

**You should see:**

```
Failed Extensions:
  1. btrace-metrics: Blocked privileged extension. Required=[THREADS, REFLECTION, CLASSLOADER]
```

The block is explained, not silent. Allow the extension with a policy file whose only content is
`allowExtensions=btrace-metrics` ([`permissions.properties`](../../demos/03-metrics-and-permissions/permissions.properties)).
The agent reads the policy once per JVM, so restart the app with it. Stop the app in terminal 1
(`Ctrl+C`) and start it again from `demos/03-metrics-and-permissions`:

```sh
java -Dbtrace.permissions=permissions.properties ../01-oneliner/OrderServiceApp.java
```

Find the new PID with `jps`, then deploy the same probe:

```sh
btrace <NEW_PID> LatencyHistogram.java
```

**You should see**, every 5 seconds (numbers vary):

```
=== Latency Report ===
chargeCard    p50=27ms  p95=321ms  p99=378ms  (n=41)
processOrder  p50=58ms  p95=346ms  p99=402ms  (n=41)
=======================
```

> **What just happened?** The probe code did not change between the two runs; only the agent's
> policy did. `chargeCard`'s p95 is what drags `processOrder`'s p95 up. In 3.0 permissions are
> declared by extensions and granted on the agent side; the client has no `--grant` flag. The
> hands-on version, including inspecting and changing the policy, is
> [Extensions and Permissions Without Tears](04-extensions-and-permissions.md).

Exit the session.

## Step 7 - Run `btrace doctor` before attaching to anything you do not own

New in 3.0: `btrace doctor <PID>` reports whether a JVM is ready to be traced, without loading an
agent or opening a command connection to it. Try it on the order service:

```sh
btrace doctor <NEW_PID>
```

**You should see** a readiness report. For a JVM started without the BTrace agent, it looks like
this (PID and JDK details differ):

```
BTrace doctor for PID 89358
Status: PREPARATION_REQUIRED (exit 2)
Target JDK: 17.0.12 (Oracle Corporation)
Attach API: available
Target attach: accessible
Dynamic agent loading: not tested (doctor never loads an agent)
BTrace endpoint: not ready
Detail: No BTrace control endpoint is published.
Observation: doctor did not inspect the target VM flag state.
Remediation:
- Relaunch with -XX:+EnableDynamicAgentLoading before using dynamic attach.
- Relaunch with -javaagent:/path/to/btrace.jar=port=0 for reviewed prepared mode.
Actions: read target system properties and detached; no agent load or BTrace command connection was attempted.
```

Doctor tells you what to change on the *next* deployment. For production, the safer route is
prepared mode: a JVM started with the agent and no probes, which you attach to later with no
flags. That workflow, including a throughput check that proves nothing is left behind after you
exit, is [Attach to Production Without Fear](12-production-safe-attach.md).

## Step 8 - Clean up

Each `Ctrl+C` then `1. exit` already retracted its probe. Stop the order service with `Ctrl+C` in
terminal 1 and remove the scratch copy:

```sh
rm -r /tmp/migrated
```

## What else changed

One sentence each, taken from the [3.0.0 release notes](../../.github/release-notes/v3.0.0.md) and
the [Migration guide](../Migration-2.x-to-3.0.md):

- **One `btrace.jar`.** The `btrace-agent.jar` / `btrace-boot.jar` / `btrace-client.jar` layout is
  gone; point launch scripts at `libs/btrace.jar` ([packaging](../Migration-2.x-to-3.0.md#packaging-one-btracejar)).
- **Apache License 2.0.** BTrace was GPLv2 with the Classpath Exception.
- **V2 wire protocol with V1 fallback.** Mixed 2.x and 3.0 clients and agents keep working on
  dynamic attach; prepared mode is the exception, and a 2.x client cannot connect to a 3.0
  prepared endpoint ([wire protocol](../Migration-2.x-to-3.0.md#wire-protocol-mixed-versions-keep-working)).
- **ClassFile API backend.** On JDK 24+ agents, classes newer than the bundled ASM's validated
  ceiling are instrumented through the JDK's ClassFile API ([backends](../architecture/InstrumentationBackends.md)).
- **`libs=` profiles removed.** The agent logs an error and loads nothing; extensions replace
  them ([migration guide](../architecture/migrating-from-libs-profiles.md)).
- **Pre-17 targets are deprecated.** They keep working throughout 3.x with a one-time warning, and
  support ends in 4.0; `-Dbtrace.suppressJavaDeprecationWarning=true` silences it.

## Troubleshooting

- **`Can not attach to PID ...`** - run `btrace` as the same OS user as the target JVM; see
  [Troubleshooting](../Troubleshooting.md).
- **Step 6 prints no report after restarting with the policy** - confirm you passed
  `-Dbtrace.permissions=permissions.properties` from `demos/03-metrics-and-permissions` and used
  the new PID, then check `btrace -le <NEW_PID>`.
- **The dump in Step 3 never appears** - the property must reach the `btrace` client JVM through
  `JAVA_TOOL_OPTIONS`; look for the `Picked up JAVA_TOOL_OPTIONS` line.
- **A warning about your Java version** - a target older than Java 17 triggers the one-time
  deprecation warning; everything still works.

## Go deeper

- Oneliners: [Your First Trace in 2 Minutes](01-first-trace-in-2-minutes.md), [Oneliner Guide](../OnelinerGuide.md)
- Flat DSL and `@TLS`: [From Oneliner to Script](02-oneliner-to-script.md)
- Migration: [Upgrade from 2.x in 10 Minutes](03-upgrade-from-2x.md), [Migration guide](../Migration-2.x-to-3.0.md)
- Extensions and permissions: [Extensions and Permissions Without Tears](04-extensions-and-permissions.md)
- Production attach: [Attach to Production Without Fear](12-production-safe-attach.md)
- Replay everything unattended: [`demos/`](../../demos/README.md)
