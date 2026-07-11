# BTrace in Kubernetes, the Sidecar Way

Package BTrace into a container the right way for three different jobs: an interactive
troubleshooting image, a lean Kubernetes sidecar, and a minimal `-javaagent` layer for a
distroless production image. You'll build each variant's Dockerfile pattern, attach exactly like
[Tutorial 1](01-first-trace-in-2-minutes.md) — just across a container boundary — and see why the
three official images are different sizes for different jobs.

**Persona:** a platform engineer who owns how Java workloads get packaged and deployed.
**Time:** ~15 minutes.

This tutorial builds real Dockerfiles and a real Kubernetes manifest against your own Docker/
Kubernetes environment; every path, flag, and file layout below is copied from BTrace's shipped
`docker/` sources (not invented for this write-up). Build/registry/cluster output is inherently
environment-specific, so **"you should see"** blocks are illustrative — image IDs, digests, and pod
names will differ — the same way earlier tutorials mark timing numbers as illustrative.

## What you'll need

- Docker (or a compatible builder) locally, and — for the Kubernetes steps — a cluster with
  `kubectl` pointed at it
- The demo app from this series ([demo/DemoApp.java](demo/DemoApp.java)) — it's what you'll layer
  BTrace onto
- Fifteen minutes and the muscle memory from [Tutorial 1](01-first-trace-in-2-minutes.md) (you'll
  reuse its exact oneliner, just from inside a container)

## Step 1 — Pick your image variant

BTrace publishes three image variants, each for a different job
([docker/README.md](../../docker/README.md)):

| Variant | Base image (from the Dockerfile) | Size | Ships | Best for |
|---|---|---|---|---|
| `btrace/btrace:3.0.0` | `bellsoft/liberica-openjdk-debian:11.0.30-cds` | ~25MB | Full toolchain, shell, samples | Development, interactive debugging |
| `btrace/btrace:3.0.0-alpine` | `alpine:3.23` + `openjdk11-jdk` | ~15MB | Full toolchain, smaller OS | Kubernetes sidecars, resource-constrained environments |
| `btrace/btrace:3.0.0-distroless` | `gcr.io/distroless/java11-debian11` | ~10MB | Runtime JARs only — **no shell, no scripts** | Production apps using `-javaagent` |

> **What just happened?** Those base images and sizes come straight from
> [`docker/Dockerfile`](../../docker/Dockerfile), [`docker/Dockerfile.alpine`](../../docker/Dockerfile.alpine),
> and [`docker/Dockerfile.distroless`](../../docker/Dockerfile.distroless). The first two `COPY`
> the entire built distribution (`bin/`, `libs/`, `docs/`, `samples/`) to `/opt/btrace/` and put
> `/opt/btrace/bin` on `PATH`; the distroless variant copies **only** `btrace/libs` to
> `/opt/btrace/libs` — there's no `bin/` and no entrypoint script, because (per the Dockerfile's
> own comment) "distroless has no shell". That one architectural difference is why Step 5 below
> looks nothing like Steps 2–4.

## Step 2 — Layer BTrace onto your app with `COPY --from`

The common case: copy the BTrace distribution out of the official image and into your own, using
a multi-stage build. This is
[`docker/README.md`](../../docker/README.md)'s "Pattern 1: Multi-Stage Build", adapted to this
series' demo app
([full Dockerfile: demo/Dockerfile.k8s-sidecar-demo](demo/Dockerfile.k8s-sidecar-demo)):

```dockerfile
FROM btrace/btrace:3.0.0 AS btrace
FROM bellsoft/liberica-openjdk-debian:11.0.30-cds

WORKDIR /app
COPY DemoApp.java /app/

COPY --from=btrace /opt/btrace /opt/btrace
ENV BTRACE_HOME=/opt/btrace
ENV PATH="${PATH}:${BTRACE_HOME}/bin"

ENTRYPOINT ["java", "DemoApp.java"]
```

> **What just happened?** `COPY --from=btrace /opt/btrace /opt/btrace` pulls the *entire* built
> tree out of the first stage — `bin/`, `libs/`, `docs/`, `samples/` — into your final image; only
> the official image's layers are discarded, not its files. `BTRACE_HOME` and `PATH` are set
> exactly the way [`docker/README.md`](../../docker/README.md)'s Quick Start does it. `DemoApp.java`
> runs via JDK 11+'s single-file source launch — no separate `javac`/`jar` step needed, same as
> running it on bare metal in [Tutorial 1](01-first-trace-in-2-minutes.md).

## Step 3 — Build it and attach, same oneliner as Tutorial 1

From `docs/tutorials/demo/`:

```sh
docker build -f Dockerfile.k8s-sidecar-demo -t demo-app-with-btrace:local .
docker run -d --name demo-app demo-app-with-btrace:local
```

**You should see** (illustrative — your image ID and container ID will differ):

```
[+] Building 4.2s (10/10) FINISHED
 => exporting to image
 => => writing image sha256:1a2b3c4d5e6f...
 => => naming to docker.io/library/demo-app-with-btrace:local
7f3a9c21b8e4c6d5a0f1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3
```

Now find the PID and attach exactly like Tutorial 1 — just via `docker exec` instead of a local
shell:

```sh
docker exec demo-app jps
docker exec demo-app btrace -n 'OrderService::processOrder @return { print method, time }' <PID>
```

**You should see** the same shape of output as
[Tutorial 1, Step 3](01-first-trace-in-2-minutes.md#step-3--your-first-trace) (numbers will vary):

```
processOrder
execution time: 58 ms
processOrder
execution time: 79 ms
processOrder
execution time: 318 ms
```

> **What just happened?** `docker exec` runs a *new* process inside the container's namespaces —
> it doesn't go through the image's `ENTRYPOINT`/`docker-entrypoint.sh` at all (that script only
> runs once, as PID 1, when the container starts). `btrace` on `PATH` is the same client binary
> from Step 2's `COPY --from`; it attaches to the `DemoApp` JVM inside the container the same way
> it attaches to any local JVM. Same bug, same fix — `chargeCard`'s slow ~10% of calls, same as
> Tutorial 1 — just packaged differently.

## Step 4 — The same app, as a Kubernetes sidecar

Rather than `docker exec`-ing into your app's own container, run BTrace in a **separate container
in the same pod** and share its process namespace. This is
[`docker/README.md`](../../docker/README.md)'s "Pattern 2: Kubernetes Sidecar"
([full manifest: demo/k8s-sidecar-pod.yaml](demo/k8s-sidecar-pod.yaml)):

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: demo-app-with-btrace
spec:
  shareProcessNamespace: true # required - lets the sidecar see the app container's PIDs
  containers:
    - name: demo-app
      image: demo-app-with-btrace:local # <-- replace with your registry/tag

    - name: btrace-sidecar
      image: btrace/btrace:3.0.0-alpine
      command: ["/bin/sh", "-c", "while true; do sleep 30; done"]
      securityContext:
        capabilities:
          add: ["SYS_PTRACE"] # required for the sidecar to attach across containers
      volumeMounts:
        - name: btrace-scripts
          mountPath: /scripts

  volumes:
    - name: btrace-scripts
      configMap:
        name: btrace-scripts
```

Push your image to a registry `kubectl` can reach, update the `image:` field above, then:

```sh
kubectl apply -f k8s-sidecar-pod.yaml
kubectl exec demo-app-with-btrace -c btrace-sidecar -- \
  sh -c 'btrace $(pgrep -f DemoApp) /scripts/trace.btrace'
```

**You should see** (illustrative — depends on your cluster and registry):

```
pod/demo-app-with-btrace created
```

> **What just happened?** `shareProcessNamespace: true` is the one field that makes this pattern
> work at all — without it, the sidecar's `pgrep`/`btrace` would only ever see its own container's
> processes, never `demo-app`'s JVM (`docker/README.md`'s own Troubleshooting section calls this
> out: `kubectl get pod myapp -o yaml | grep shareProcessNamespace` is the first thing to check when
> attach fails). The sidecar uses the **alpine** variant, not the full or distroless one — it needs
> a shell and the full toolchain (`btrace`, `jps`) to run interactively via `kubectl exec`, but
> doesn't need the ~10MB extra of samples/docs the full image carries. `SYS_PTRACE` is called out
> explicitly in `docker/README.md`'s Best Practices as a required capability alongside
> `shareProcessNamespace`; the rest of a hardened `securityContext` (`runAsNonRoot`,
> `readOnlyRootFilesystem`, dropping `ALL` other capabilities) is standard Kubernetes practice, not
> BTrace-specific, so it's left out of the minimal manifest above — add it per your own cluster's
> policy.

## Step 5 — Skip the sidecar: distroless production with `-javaagent`

For a permanently-running probe (rather than on-demand attach), skip the sidecar model entirely:
copy just the BTrace runtime jar into a distroless image and load it as a `-javaagent` at JVM
startup. This is `docker/README.md`'s "Pattern 4: Distroless Production" (like the README's own
copy, this assumes an earlier `build` stage that compiles `myapp.jar` — not shown, since it's
your application's own build, not BTrace's):

```dockerfile
# ... an earlier "build" stage that produces /app/target/myapp.jar goes here ...

FROM btrace/btrace:3.0.0-distroless AS btrace
FROM gcr.io/distroless/java11-debian11
WORKDIR /app

COPY --from=build /app/target/myapp.jar /app/
COPY --from=btrace /opt/btrace/libs /opt/btrace/libs

ENTRYPOINT ["java", \
  "-javaagent:/opt/btrace/libs/btrace.jar=script=/scripts/trace.btrace", \
  "-jar", "/app/myapp.jar"]
```

> **A correction worth flagging:** `docker/README.md`'s own copy of this pattern still shows the
> *legacy* two-JAR invocation (`-javaagent:...btrace-agent.jar=...` plus a separate
> `-Xbootclasspath/a:...btrace-boot.jar`). That layout predates the current build: BTrace 3.0 ships
> a single self-contained, masked JAR named `btrace.jar` (task `btraceJar` in
> `btrace-dist/build.gradle`, manifest attribute `Boot-Class-Path: btrace.jar` — the jar declares
> itself as its own boot classpath entry, so no second `-Xbootclasspath/a:` flag is needed). Every
> other current doc in this tree — `GettingStarted.md`, `Troubleshooting.md`, `FAQ.md`,
> `BTraceTutorial.md`, `docs/architecture/MaskedJarArchitecture.md` — already uses the one-JAR form
> shown above; this tutorial follows them rather than `docker/README.md`'s stale snippet.

> **What just happened?** `Dockerfile.distroless` only ever `COPY`s `btrace/libs` — there's no
> `bin/`, no entrypoint script, nothing to `docker exec` a shell into (distroless images ship no
> shell at all). That's the trade-off: you get the smallest, lowest-attack-surface image, but you
> give up on-demand `btrace <PID> script.java` attach entirely. The probe has to be decided at
> container build/deploy time, baked in via `-javaagent`, exactly like any other Java agent.

## Step 6 — Optional: bake in a fat agent instead of a bare JVM script

If your extension needs (say, `btrace-metrics` for percentile histograms, from
[Tutorial 4](04-extensions-and-permissions.md)) are fixed at build time, you can go one step
further than Step 5 and skip `$BTRACE_HOME` entirely with a **fat agent JAR** — the packaging
BTrace builds for exactly this situation, covered in depth in
[Tutorial 8](08-fat-agent.md). Per
[`docs/architecture/fat-agent-plugin.md`](../architecture/fat-agent-plugin.md)'s own Kubernetes use
case:

```dockerfile
FROM btrace/btrace:3.0.0 AS btrace
FROM openjdk:17

# Copy only the fat agent (no BTRACE_HOME needed)
COPY --from=btrace /opt/btrace/libs/btrace-agent-fat.jar /opt/btrace/
```

One thing worth knowing before you rely on this: the fat agent JAR is produced by an **opt-in**
Gradle task (`./gradlew :btrace-dist:fatAgentJar`, `outputDir = libsDir` — same `libs/` directory
`btrace.jar` lands in). The Docker image build tasks in `btrace-dist/build.gradle`
(`buildDockerImage`, `buildDockerImageAlpine`, `buildDockerImageDistroless`) all depend on
`btraceJar`, but none of them depend on `fatAgentJar` — so `btrace-agent-fat.jar` only ends up in
`/opt/btrace/libs` if you (or your release pipeline) ran that task before the image was built.
Don't assume a `btrace/btrace:3.0.0` image you pulled has it; build it yourself first, per
[Tutorial 8](08-fat-agent.md).

## Troubleshooting

- **`Cannot attach to process` / `Can not attach to PID`** — in plain Docker, check the target
  container was started with process namespace sharing (`docker run --pid=container:<target>`);
  in Kubernetes, confirm `shareProcessNamespace: true` actually applied:
  `kubectl get pod <pod> -o yaml | grep shareProcessNamespace`.
- **`BTRACE_HOME not found`** — verify the `COPY --from` layer landed where you expect:
  `docker exec <container> ls -la $BTRACE_HOME` and `docker exec <container> env | grep BTRACE`.
- **`Java tools not available` (`jps`/`javac` missing)** — you copied a JRE-only base image
  instead of a full JDK; the sidecar/app image needs the JDK. On Java 9+ bases you may also need
  `--add-exports jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED` on `JAVA_OPTS` for PID
  discovery tools to work.
- **The sidecar can't see the app's process at all** — Pod Security Policies/admission controllers
  can block `ptrace`-family syscalls even with `shareProcessNamespace: true`; add the `SYS_PTRACE`
  capability (Step 4) and check your cluster's PSP/OPA policy. Note GKE Autopilot in particular may
  restrict `SYS_PTRACE` outright — use standard GKE if you hit this.
- **You tried to `docker exec sh` into the distroless image** — that's expected to fail: the
  distroless variant ships no shell by design (Step 1). Use it only for the baked-in `-javaagent`
  pattern (Step 5/6), not for interactive attach.

## Clean up

```sh
docker rm -f demo-app
docker rmi demo-app-with-btrace:local
kubectl delete pod demo-app-with-btrace
kubectl delete configmap btrace-scripts   # if you created one for Step 4's scripts volume
```

## Go deeper

- All five image patterns, environment variables, and the full troubleshooting/best-practices
  list: [docker/README.md](../../docker/README.md)
- Containers and Kubernetes as a concept, plus batch-tracing multiple pods:
  [GettingStarted: BTrace in Containers and Kubernetes](../GettingStarted.md#btrace-in-containers-and-kubernetes)
- Building and using fat agent JARs end to end: [Tutorial 8](08-fat-agent.md)
- Why BTrace ships as one masked JAR instead of separate agent/boot JARs:
  [Masked JAR Architecture](../architecture/MaskedJarArchitecture.md)
