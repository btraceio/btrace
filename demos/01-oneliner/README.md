# Demo 01 — Your first trace in one line

**Story.** An order service says some orders fail and some are slow, not why. Three oneliners
attached to the live JVM answer both questions: no script file, no compile step, no restart, no
code change. Exiting the client retracts the probe and restores the original bytecode.

| File | Role |
|---|---|
| `OrderServiceApp.java` | the target: `OrderService.chargeCard` is slow for ~10% of calls, `validateOrder` throws for ~8% and the app swallows it |
| `run.sh` | runs the whole story; `--keep` leaves the app running |

**By hand** (two terminals, from this directory; `btrace` on `PATH`, `JAVA_HOME` set):

```sh
java OrderServiceApp.java                                                              # terminal 1
btrace -n 'OrderService::processOrder @return { print method, time }' '~OrderServiceApp'  # terminal 2
btrace -n 'OrderService::* @return if duration>200ms { print method, time }' '~OrderServiceApp'
btrace -n 'OrderService::validateOrder @error { print method, stack }' '~OrderServiceApp'
```

`Ctrl+C` in the BTrace terminal, then `1. exit`, ends each session.

**You should see** (numbers vary):

```
processOrder
execution time: 54 ms
processOrder
execution time: 354 ms
```

then only the slow calls, always in pairs:

```
chargeCard
execution time: 345 ms
processOrder
execution time: 394 ms
```

and for the failures, the call path below the throwing method:

```
validateOrder
OrderService.processOrder(OrderServiceApp.java:81)
OrderServiceApp.lambda$main$0(OrderServiceApp.java:47)
java.base/java.lang.Thread.run(Thread.java:842)
```

**Go deeper:** [Your First Trace in 2 Minutes](../../docs/tutorials/01-first-trace-in-2-minutes.md),
[Oneliner Guide](../../docs/OnelinerGuide.md).
