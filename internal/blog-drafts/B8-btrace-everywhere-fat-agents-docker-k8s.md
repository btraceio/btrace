# BTrace Everywhere: Fat Agents, Docker Layers, and Kubernetes Sidecars

### One tracer, three packaging shapes, and an honest look at where the edges are still rough

BTrace has always been happy to attach to a JVM you already have running. BTrace 3.0 spends real
effort making sure it's just as happy showing up *inside* the thing you're deploying — as a single
self-contained JAR, as a layer in three differently-sized Docker images, or as a sidecar container
sharing a pod with your app. If you run Java workloads on Spark executors, Hadoop nodes, minimal
containers, or Kubernetes clusters where nobody wants to `kubectl exec` a shell into production,
this is the release that was built with you in mind.

## One JAR to rule them all

The starting point is the fat agent: BTrace plus a real extension, packaged into a single
`-javaagent` JAR with no `$BTRACE_HOME`, no separate extensions directory, nothing else to copy
onto a node you don't fully control. The Gradle plugin (`io.btrace.fat-agent`) gives you a small,
declarative DSL —

```groovy
btraceFatAgent {
    baseName = 'demo-btrace-agent'

    embedExtensions {
        file('btrace-metrics.zip')
    }
}
```

— and a `fatAgentJar` task that stages your chosen extension's API and implementation classes,
renames the implementation to `.classdata` so it loads through BTrace's extension class loader, and
writes a manifest that makes the result a drop-in `-javaagent`:

```
Manifest-Version: 1.0
Premain-Class: io.btrace.boot.Loader
Agent-Class: io.btrace.boot.Loader
BTrace-Agent-Main: io.btrace.agent.Main
Can-Redefine-Classes: true
Can-Retransform-Classes: true
Boot-Class-Path: demo-btrace-agent.jar
BTrace-Embedded-Extensions: btrace-metrics
```

(`io.btrace.boot.Loader` is the same masked-jar bootstrap the regular `btrace.jar` uses;
`BTrace-Agent-Main` tells it where the real agent entry point lives inside the jar.) Start your app
with `-javaagent:demo-btrace-agent.jar=debug=true` and the extension is just there — no separate
attach step, no extensions directory to install into. Deploy a probe that uses it
(`LatencyHistogram.java`, reused unchanged from the extensions tutorial) and you get the same
five-second histogram you'd get from a hand-run BTrace, percentiles and all:

```
=== Latency Report ===
chargeCard    p50=27ms  p95=321ms  p99=378ms  (n=41)
processOrder  p50=58ms  p95=346ms  p99=402ms  (n=41)
=======================
```

There's no Maven equivalent in 3.0.0. An unpublished in-repository Maven `fat-agent` module was
removed before release — it targeted pre-3.0 artifacts and could report a successful build even
though the embedded implementation couldn't load. The external
[`btrace-maven`](https://github.com/btraceio/btrace-maven) project remains the Maven integration for
script compilation; for fat agents, the Gradle plugin is the supported path.

## Layered into Docker, three ways

Once you've got a jar (fat or otherwise), BTrace's official Docker images give you three variants
tuned for three different jobs, and the size difference between them is the whole point. The full
image (`btrace/btrace:3.0.0`, ~25MB) ships the entire toolchain — shell, samples, docs — and is
built for development and interactive debugging. The alpine variant (~15MB) trims the OS down but
keeps the full toolchain, which makes it the right choice for a Kubernetes sidecar that needs to
run `btrace` and `jps` interactively but doesn't need the extra samples and docs weight. The
distroless variant (~10MB) ships only the runtime jars — no shell, no scripts, nothing to `exec`
into — built for a production app that loads BTrace purely as a baked-in `-javaagent`.

The common pattern is a multi-stage `COPY --from`: pull `/opt/btrace` out of the official image and
into your own, set `BTRACE_HOME` and `PATH`, and you're attaching to your own containerized app with
the exact same oneliner from lesson one of this series — `docker exec` in place of a local shell,
same bug, same fix, just packaged differently.

For a permanently-running probe rather than an on-demand attach, skip the toolchain entirely: copy
just `btrace.jar` into a distroless image and load it as a `-javaagent` at JVM startup. That's the
smallest, lowest-attack-surface shape BTrace comes in, and it trades away interactive attach
entirely in exchange — the probe has to be decided at build or deploy time, not requested later.

## The sidecar, for when you can't bake it in

The most Kubernetes-native pattern doesn't touch your app's own image at all: run BTrace in a
second container in the same pod, with `shareProcessNamespace: true` so it can see your app's PIDs,
and an added `SYS_PTRACE` capability so it's actually allowed to attach across the container
boundary. That one `shareProcessNamespace` field is what makes the whole pattern work — without it
the sidecar's `btrace`/`pgrep` only ever see their own container's processes. The sidecar uses the
alpine image, not the full or distroless one: it needs a shell and the toolchain for interactive
`kubectl exec`, but not the extra weight of samples and docs. From there, `kubectl exec` into the
sidecar and run `btrace $(pgrep -f YourApp) trace.btrace` — same tracer, now living one container
over from the app it's watching.

## Known rough edges

All of the above works as described — but two behaviours are worth knowing about before you build
a workflow around them, rather than discovering them mid-incident.

First, embedding an extension into a fat agent does **not** bypass the privileged-permission gate.
The fat-agent plugin copies each embedded extension's `BTrace-Extension-Permissions` manifest
attribute into the embedded descriptor, and the agent honours it when it loads the extension — so an
embedded `btrace-metrics` (privileged, because of `THREADS`) is gated exactly like a
filesystem-installed one. If your app's JVM has no policy granting it (`allowExtensions=btrace-metrics`
or `allowPrivileged=true` in `~/.btrace/permissions.properties`, or the equivalent
`btracex policy set`), the extension is blocked and a probe that injects it will fail to link (or,
for an `@Injected(optional = true)` field, get the throwing stub from the permissions tutorial).
Embedding decides what ships in the jar; the policy on the target host still decides what runs.

Second, the "zero-config startup probes" feature — bundling a compiled probe class into a fat agent
so it auto-runs at JVM startup via `bundledProbes {}` and a `probes=` agent argument — fails loudly
rather than quietly. The Gradle plugin stages each probe class under `META-INF/btrace-probes/`, and
the agent honours `probes=` directly: it validates every name as an exact Java binary name (so a
value can't escape that namespace), loads each class from that location, and throws a
`BundledProbeException` at startup if a named probe isn't there or can't be loaded. A typo in
`probes=` or a class you forgot to list in `bundledProbes {}` is a startup failure, never a silent
no-op — which is what you want from something that's supposed to be running before your first
request lands.

Neither of these is a reason to skip the fat-agent story — the JAR-embedding mechanics, the manifest
rewriting, and the Docker/Kubernetes packaging patterns are all solid and verified end to end. They're
reasons to test the specific combination you're relying on (a privileged embedded extension plus the
policy on the target host, or bundled auto-start probes) before you build a deploy pipeline around it.

---

- Hands-on tutorials: [docs/tutorials/08-fat-agent.md](../../docs/tutorials/08-fat-agent.md), [docs/tutorials/09-kubernetes-sidecar.md](../../docs/tutorials/09-kubernetes-sidecar.md)
- Getting started: [../../docs/GettingStarted.md](../../docs/GettingStarted.md)
<!-- TODO: replace with the per-post Discussions thread before publishing -->
- Questions, deployment war stories, or "here's what broke for us": [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
