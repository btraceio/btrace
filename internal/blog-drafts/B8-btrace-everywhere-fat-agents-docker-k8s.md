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
Premain-Class: io.btrace.agent.Main
Agent-Class: io.btrace.agent.Main
Can-Redefine-Classes: true
Can-Retransform-Classes: true
Boot-Class-Path: demo-btrace-agent.jar
BTrace-Embedded-Extensions: btrace-metrics
```

Start your app with `-javaagent:demo-btrace-agent.jar=debug=true` and the extension is just there —
no separate attach step, no policy file to edit by hand. Deploy a probe that uses it
(`LatencyHistogram.java`, reused unchanged from the extensions tutorial) and you get the same
five-second histogram you'd get from a hand-run BTrace, percentiles and all:

```
=== Latency Report ===
chargeCard    p50=27ms  p95=321ms  p99=378ms  (n=41)
processOrder  p50=58ms  p95=346ms  p99=402ms  (n=41)
=======================
```

Maven gets the same idea through a `fat-agent` goal bound to the `package` phase, with its own
`btraceVersion`, `extensions`, and `outputName` parameters, producing the same shape of artifact
from a `pom.xml` instead of a `build.gradle`.

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

All of the above works as described — but three things surfaced during verification are worth
knowing about before you build a workflow around them, rather than discovering them mid-incident.

First, embedding an extension into a fat agent currently sidesteps the privileged-permission gate
entirely. The filesystem-extension flow from the permissions tutorial requires you to explicitly
opt a privileged extension into `~/.btrace/permissions.properties` before it's allowed to run. The
fat-agent path skips that check completely: embedded extensions are parsed with an empty permission
set regardless of what they actually declare needing, so the privileged-tier gate never triggers.
If you embed something that needs a privileged permission, it's active for anyone who runs your
jar, with no separate opt-in. Treat the act of embedding itself as the grant, and review what you're
bundling accordingly.

Second, the "zero-config startup probes" feature — bundling a compiled probe class into a fat agent
so it auto-runs at JVM startup via `bundledProbes {}` and a `probes=` argument — doesn't work yet in
the current build. The Gradle plugin stages the probe class into the jar, but the agent's loader at
runtime never looks in that location; it only checks each embedded extension's own bundled-probes
list. The result is a silent no-op: the agent logs that it's loading the named probe, finds nothing,
and moves on, with no error surfaced anywhere. Until that's wired together, use the same
attach-based flow you'd use with any other BTrace script — `btrace <PID> Script.java` — for anything
you wanted to auto-start.

Third, the Maven fat-agent goal has a classifier and path mismatch that likely breaks embedding for
real extensions. The mojo looks for an implementation artifact published under an `impl` classifier,
but no extension in this repo actually publishes one — only `api`, `api-sources`, `api-javadoc`, and
`extension` classifiers exist. When that lookup fails, it's caught and logged only at debug level,
silently producing a fat jar with an extension's API on the classpath but no working implementation
behind it. Even when an implementation jar is found by other means, it's staged under a nested path
that the runtime's class loader doesn't look in — it expects a flat layout, the same one the Gradle
plugin produces. If you're building fat agents with Maven, verify the embedded implementation
actually loads before depending on it in production; the Gradle path (`file()`/`project()` sources)
doesn't share either of these gaps.

None of these are reasons to skip the fat-agent story — the JAR-embedding mechanics, the manifest
rewriting, and the Docker/Kubernetes packaging patterns are all solid and verified end to end. They're
reasons to test the specific combination you're relying on (Maven plus embedded implementation
classes, or bundled auto-start probes) before you build a deploy pipeline around it.

---

- Hands-on tutorials: [docs/tutorials/08-fat-agent.md](../../docs/tutorials/08-fat-agent.md), [docs/tutorials/09-kubernetes-sidecar.md](../../docs/tutorials/09-kubernetes-sidecar.md)
- Getting started: [../../docs/GettingStarted.md](../../docs/GettingStarted.md)
- Questions, deployment war stories, or "here's what broke for us": [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
