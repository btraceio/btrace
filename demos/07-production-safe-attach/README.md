# 07 - Attach to production without fear

The JVM was started with the BTrace agent in prepared mode (`port=0`): a loopback, token-protected
endpoint, nothing instrumented. `btrace doctor <pid>` says whether the JVM is ready without loading
anything. `btrace '~HotLoopApp' ...` finds the endpoint and token through the Attach API, no flags needed.
The app reports its own throughput, so the cost of the probe is visible as a number, and so is
what happens after the client exits.

## Run it

```bash
export JAVA_HOME=/path/to/jdk11+
./run.sh            # add --keep to leave the app running
```

By hand, in three terminals:

```bash
java -javaagent:$BTRACE_HOME/libs/btrace.jar=port=0 HotLoopApp.java
btrace doctor <PID>
btrace '~HotLoopApp' HotPathProbe.java
```

## You should see

Doctor on the prepared JVM, then on a plain JVM (numbers and ports vary):

```
$ btrace doctor 39936
Status: READY (exit 0)
BTrace endpoint: ready at 127.0.0.1:58103
Authentication: required; credential file readable
exit code: 0

$ btrace doctor 40319
Status: PREPARATION_REQUIRED (exit 2)
BTrace endpoint: not ready
Remediation:
- Relaunch with -javaagent:/path/to/btrace.jar=port=0 for reviewed prepared mode.
exit code: 2
```

Throughput as printed by the app (JDK 17, Intel Mac; absolute values depend on the machine, and
the empty-handler probe on a ~200+ M calls/s loop is a worst case):

```
before:   [demo] 296.8 M calls/s   [demo] 293.6 M calls/s
attached: [demo] 22.0 M calls/s    [demo] 16.9 M calls/s    [demo] 20.2 M calls/s
after:    [demo] 22.2 M calls/s    [demo] 15.8 M calls/s    [demo] 16.4 M calls/s
```

Throughput while the probe is attached drops sharply on this deliberately tight loop (an empty
handler still pays the call-site dispatch); once the client exits, the agent restores the original
bytecode and the number climbs back to the baseline within a few seconds. Absolute numbers depend on
the machine and on what else it is doing.

More: [Getting Started](../../docs/GettingStarted.md), [Troubleshooting](../../docs/Troubleshooting.md).
