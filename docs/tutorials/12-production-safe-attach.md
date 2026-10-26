# Attach to Production Without Fear

Start a JVM in prepared mode, prove it is ready without loading anything, attach with no flags, and
prove that nothing is left behind when you leave. The target app reports its own throughput, so the
cost of the probe, and its disappearance, show up as a number you can watch.

![Demo 07: prepared mode, doctor, attach with no flags, and recovery after exit](images/07-production-safe-attach.gif)

*Recorded from [`demos/07-production-safe-attach/run.sh`](../../demos/07-production-safe-attach/README.md); the MP4 is next to the GIF.*

**Persona:** an SRE or on-call engineer who has been burned by agents that cannot be removed.
**Time:** ~10 minutes.

## What you'll need

- JDK 11 or newer with `JAVA_HOME` set (`bin/btrace` refuses to start without it)
- BTrace 3.0 installed, with `BTRACE_HOME` pointing at it and `bin/btrace` on your PATH ([installation options](../GettingStarted.md#installation))
- The three files from [demos/07-production-safe-attach](../../demos/07-production-safe-attach/README.md):
  [HotLoopApp.java](../../demos/07-production-safe-attach/HotLoopApp.java) (the target) and
  [HotPathProbe.java](../../demos/07-production-safe-attach/HotPathProbe.java) (the probe). Run the
  commands below from that directory.
- Three terminal windows

## Step 1 - Start a JVM in prepared mode

The target is a single thread calling one cheap method, `Work.step`, in a tight loop. It prints its
own throughput every two seconds. Start it in terminal 1 with the BTrace agent loaded at launch in
prepared mode (`port=0`):

```sh
java -javaagent:$BTRACE_HOME/libs/btrace.jar=port=0 HotLoopApp.java
```

**You should see** (absolute numbers depend on your machine; yours will differ):

```
[demo] hot loop running - stop with Ctrl+C
[demo] 60.6 M calls/s
[demo] 65.7 M calls/s
```

Note the baseline: that is what "healthy" looks like. Get the PID in terminal 2 (`jps`, look for
`HotLoopApp`); you need it for every command below.

> **What just happened?** The agent started, but it instrumented nothing. Prepared mode means the
> agent only opens a control endpoint: it binds a loopback address (never a wildcard or remote
> one), generates an owner-protected token file, and publishes the actual address, port, and
> token-file path through JVM properties. `port=0` asks the OS for a free port, so there is nothing
> to collide with. The token is never a process argument or system property.

## Step 2 - Ask the doctor, and read every line

Before you attach anything, check readiness. In terminal 2:

```sh
btrace doctor <PID>
```

**You should see:**

```
BTrace doctor for PID 89156
Status: READY (exit 0)
Target JDK: 17.0.12 (Oracle Corporation)
Attach API: available
Target attach: accessible
Dynamic agent loading: not tested (doctor never loads an agent)
BTrace endpoint: ready at 127.0.0.1:58360
Authentication: required; credential file readable
Detail: A BTrace control endpoint is ready.
Observation: doctor did not inspect the target VM flag state.
Actions: read target system properties and detached; no agent load or BTrace command connection was attempted.
```

(On some JDKs a `Java HotSpot ... warning: Option AllowRedefinitionToAddDeleteMethods was
deprecated` line precedes the report. It comes from the JVM, not from BTrace.) The command exits
with code `0`.

Line by line:

- `Status: READY (exit 0)` - the exit codes are stable and scriptable: `0` ready, `2`
  `PREPARATION_REQUIRED`, `3` `INACCESSIBLE`, `4` unexpected failure or invalid invocation.
  `btrace doctor <PID> --json` gives the same answer for automation.
- `Target JDK` and `Attach API` / `Target attach` - the JVM can be reached through the Attach API.
- `Dynamic agent loading: not tested` - doctor never loads an agent, so it cannot tell you whether
  one would be allowed. That is deliberate.
- `BTrace endpoint: ready at 127.0.0.1:58360` - the loopback address and the port the OS picked.
- `Authentication: required; credential file readable` - the token file exists and your user can
  read it.
- `Actions:` - the receipt. It states exactly what doctor did: read system properties and detach.

> **What just happened?** Doctor read the JVM's published properties through the Attach API and
> detached. No agent was loaded, no class was touched, and the BTrace command socket was never
> opened. It is safe to run against anything.

## Step 3 - See what "not ready" looks like

For contrast, start the same app in terminal 3 *without* the agent:

```sh
java HotLoopApp.java
```

Find its PID and ask the doctor:

```sh
btrace doctor <PLAIN_PID>
```

**You should see:**

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

The exit code is `2`.

> **What just happened?** Doctor found no published endpoint and told you your two options for the
> *next* deployment: allow dynamic agent loading, or launch in prepared mode as you did in Step 1.
> Production is where you want to have chosen prepared mode ahead of time. Stop this plain JVM now
> (`Ctrl+C` in terminal 3); you do not need it again.

## Step 4 - Attach with no flags and watch the cost

In terminal 2, attach the probe to the prepared JVM from Step 1. No `-p`, no token:

```sh
btrace <PID> HotPathProbe.java
```

**You should see:**

```
[main] INFO io.btrace.client.Main - Attaching BTrace to PID: 89156
[main] INFO io.btrace.client.Client - Successfully started BTrace probe: HotPathProbe.java
[probe] attached
```

The `[probe] attached` line repeats every two seconds. Now look at terminal 1:

```
[demo] 6.3 M calls/s
[demo] 7.2 M calls/s
[demo] 7.1 M calls/s
```

The throughput fell by roughly an order of magnitude, from the baseline you noted in Step 1.

> **What just happened?** The client found the port and the token on its own: it asked the target
> JVM through the Attach API for the published address, port, and token-file path, then used the
> token. Nothing was typed, and nothing secret appeared on a command line.
> The slowdown is real but is a worst case on purpose. [HotPathProbe.java](../../demos/07-production-safe-attach/HotPathProbe.java)
> has an *empty* handler on a method that is called tens of millions of times per second, so every
> bit of the cost is call-site dispatch, with no probe logic at all. A typical production method is
> called orders of magnitude less often.

## Step 5 - Leave, and watch the number come back

Press `Ctrl+C` in terminal 2. The client opens a small menu; choose `1. exit`. Then watch
terminal 1:

```
[demo] 7.1 M calls/s
[demo] 29.5 M calls/s
[demo] 51.7 M calls/s
```

Within a few seconds the app is back near the baseline of Step 1 (here 51.7 against 65.7 M
calls/s; a busy machine will not match the baseline exactly).

> **What just happened?** Two things, and the app's own number shows both. When the client exits,
> the agent restores the original bytecode of the classes it instrumented (a retransform), so the
> probe's code is gone from `Work.step`. And the probe handlers are dispatched through
> `invokedynamic` call sites that become no-ops once the client is gone, so even a window in which
> the old bytecode is still running leaves no lingering overhead.

## Step 6 - Clean up

Stop the app in terminal 1 with `Ctrl+C` (or `kill <PID>`). Generated token files are removed during
orderly shutdown of the JVM, and the plain JVM from Step 3 was already stopped. There is nothing
else to undo.

## Troubleshooting

- **`btrace doctor` exits `3` (`INACCESSIBLE`)** - doctor could not reach the target through the
  Attach API. The usual cause is that BTrace and the target JVM run as different OS users; run
  `btrace` as the target's user (see
  [Unable to attach to target VM](../Troubleshooting.md#unable-to-attach-to-target-vm)).
- **The app exits at startup, or `port=` fails with a `BindException`** - you used a fixed
  `port=NNNN` that is already in use. That aborts agent initialization, unlike an invalid option
  value, which only skips the command server. Prefer `port=0` whenever the port is not under your
  control.
- **A 2.x client cannot connect** - a 3.0 prepared endpoint requires authentication before every
  command, and a 2.x client does not know how to authenticate, so the connection fails. Use a 3.0
  client. (Dynamic attach remains compatible with 2.x clients.)
- **`btrace <PID>` reports no endpoint but you started the agent** - check `btrace doctor <PID>`;
  an invalid `port=` or `bindAddress=` value makes the agent skip the command server and print one
  line naming the option
  ([details](../Troubleshooting.md#invalid-agent-option-the-command-server-was-not-started)).

## Go deeper

- Prepared mode, `authTokenFile=`, `bindAddress=` and `btrace doctor`: [Getting Started](../GettingStarted.md)
- Attach failures and their fixes: [Troubleshooting: JVM Attachment Issues](../Troubleshooting.md#jvm-attachment-issues)
- The runnable version of this tutorial: [demos/07-production-safe-attach](../../demos/07-production-safe-attach/README.md)
- Same prepared-mode ideas across a container boundary: [BTrace in Kubernetes, the Sidecar Way](09-kubernetes-sidecar.md)
