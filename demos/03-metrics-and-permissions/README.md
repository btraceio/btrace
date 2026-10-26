# Demo 03 — Percentiles from an extension, and an explained block

**Story.** The `Aggregations` API is gone in 3.0; histograms come from the bundled
`btrace-metrics` extension (HdrHistogram). It is a privileged extension, so it links only when
the agent-side policy allows it. Deployed without a policy, the probe runs with a throwing stub
and `btrace -le <PID>` tells you exactly why. Allow it and the same probe prints p50/p95/p99.

| File | Role |
|---|---|
| `LatencyHistogram.java` | the probe: `@Injected(optional = true) MetricsService`, two histograms, a 5 s report |
| `permissions.properties` | `allowExtensions=btrace-metrics`, passed to the target JVM with `-Dbtrace.permissions=` |
| `run.sh` | runs the whole story; `--keep` leaves the app running |

**By hand** (two terminals, from this directory):

```sh
java ../01-oneliner/OrderServiceApp.java                          # terminal 1
btrace <PID> LatencyHistogram.java                                # terminal 2: blocked
btrace -le <PID>                                                  # why
# restart terminal 1 with the policy:
java -Dbtrace.permissions=permissions.properties ../01-oneliner/OrderServiceApp.java
btrace <NEW_PID> LatencyHistogram.java                            # histogram
```

The policy can also live in `~/.btrace/permissions.properties` (`btracex policy set
--allowExtensions btrace-metrics` writes it); the JVM property keeps this demo self-contained.
The agent reads the policy once per JVM, which is why the app is restarted rather than the
probe redeployed.

**You should see** first:

```
! ERROR
java.lang.IllegalStateException: BTrace optional service unavailable: io.btrace.metrics.MetricsService
```

then from `-le`:

```
Failed Extensions:
  1. btrace-metrics: Blocked privileged extension. Required=[THREADS, REFLECTION, CLASSLOADER]
```

and finally, every 5 seconds:

```
=== Latency Report ===
chargeCard    p50=27ms  p95=321ms  p99=378ms  (n=41)
processOrder  p50=58ms  p95=346ms  p99=402ms  (n=41)
=======================
```

**Go deeper:** [Extensions and Permissions Without Tears](../../docs/tutorials/04-extensions-and-permissions.md),
[Permission policy](../../docs/PermissionPolicy.md).
