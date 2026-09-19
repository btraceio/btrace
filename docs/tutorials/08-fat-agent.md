# One JAR to Rule Them All: Building a BTrace Fat Agent

Package BTrace plus a real extension into a single `-javaagent` JAR — no `$BTRACE_HOME`, no
separate extensions directory, nothing else to copy onto a Spark executor, a Hadoop node, or a
minimal container.

**Persona:** a platform engineer who ships BTrace as part of someone else's deployment, not just
runs it themselves. **Time:** ~15 minutes.

A note before you start: this tutorial builds real JARs with Gradle and therefore requires your own
build environment and network access to a Maven repository — this is not something a docs sandbox
can execute for you. Every command, DSL property, and file path below is traced to the Gradle plugin
source and its test suite (cited inline); where that source revealed a real gap between what's
documented and what the runtime actually does, this tutorial says so plainly instead of pretending
otherwise — see the callouts marked **Heads up**.

## What you'll need

- JDK 11 or newer on your PATH
- A Gradle 8+ build with network access to resolve BTrace and extension artifacts
- Two terminal windows (same [demo app](demo/DemoApp.java) as the rest of this series)

## Step 1 — Get something to embed

The fat-agent plugin embeds *extensions*, not scripts. This tutorial embeds `btrace-metrics` — the
same HdrHistogram-backed extension from
[Tutorial 4](04-extensions-and-permissions.md#step-2--ask-btrace-to-hand-your-probe-a-metrics-service).
Build its distributable package from the repo root:

```sh
./gradlew :btrace-extensions:btrace-metrics:packageExtension
ls btrace-extensions/btrace-metrics/build/distributions/
```

**You should see** a zip named after the module and its version, e.g.:

```
btrace-metrics-3.0.0-extension.zip
```

> **What just happened?** `packageExtension` is a real task registered by the `io.btrace.extension`
> Gradle plugin for every extension module (`BTraceExtensionPlugin.groovy`) — it zips the API jar
> and the shaded implementation jar together under classifier `extension`. Use that package with
> the fat-agent plugin's `file()` source. `maven()` remains for separately published third-party
> extensions; BTrace's bundled extensions are not Maven artifacts.

Copy it into a scratch directory. Renaming it to `btrace-metrics.zip` is purely for readability —
it's the name [demo/fat-agent-build.gradle](demo/fat-agent-build.gradle) references, and Step 3
explains why the file name is *not* where the embedded extension's id comes from:

```sh
mkdir -p ~/fat-agent-demo
cp btrace-extensions/btrace-metrics/build/distributions/btrace-metrics-*-extension.zip \
  ~/fat-agent-demo/btrace-metrics.zip
```

## Step 2 — Configure the Gradle fat-agent plugin

Copy the two demo project files in next to the zip:

```sh
cp docs/tutorials/demo/fat-agent-build.gradle ~/fat-agent-demo/build.gradle
cp docs/tutorials/demo/fat-agent-settings.gradle ~/fat-agent-demo/settings.gradle
cd ~/fat-agent-demo
```

[fat-agent-build.gradle](demo/fat-agent-build.gradle) applies the plugin and configures it:

```groovy
plugins {
    id 'io.btrace.fat-agent' version '3.0.0'
}

btraceFatAgent {
    baseName = 'demo-btrace-agent'
    btraceVersion = '3.0.0'

    embedExtensions {
        file('btrace-metrics.zip')
    }
}
```

`baseName`, `btraceVersion`, and the `embedExtensions { file(...) }` / `maven(...)` / `project(...)` source builders
are exactly the DSL surface the plugin exposes — verified against
`BTraceFatAgentExtension.groovy` (fields `baseName`, `outputDir`, `manifestAttributes`,
`relocations`, `autoDiscover`; the `ExtensionSourceSpec` builders `project()`, `maven()`, `file()`,
`files()`). Registry sources are not part of the 3.0 DSL; unknown source builders and configuration
properties fail during Gradle configuration instead of being accepted as no-ops.

## Step 3 — Build it and look inside

```sh
./gradlew fatAgentJar
```

**You should see**, among the standard Gradle task lines, these two lifecycle messages (the engine
path is wherever Gradle cached the `io.btrace:btrace:3.0.0` artifact):

```
> Task :stageBTraceEngine
[fat-agent] Staged masked BTrace engine: /home/you/.gradle/caches/.../btrace-3.0.0.jar

> Task :stageExtensions
[fat-agent] Staged 1 extension(s): btrace-metrics

> Task :fatAgentJar

BUILD SUCCESSFUL
```

> **What just happened?** `stageBTraceEngine`, `stageExtensions`, `stageProbes`, and `fatAgentJar`
> are the four tasks the plugin registers on `apply` (`BTraceFatAgentPlugin.groovy`;
> `BTraceFatAgentPluginTest.pluginCanBeApplied()` asserts that `fatAgentJar`, `stageExtensions`,
> and `stageProbes` appear in `gradle tasks --all`). Both `"[fat-agent] Staged ..."` lines are
> literal strings from the `stageBTraceEngine` and `stageExtensions` tasks; `stageProbes` only
> prints one once you configure `bundledProbes {}` (Step 5).
>
> **Heads up — the zip's file name does not decide the extension id.** `packageExtension`'s zip has
> no `extension.properties` at its root (only the API and impl jars), so
> `FileExtensionSource.extractFromDirectory()` starts from a placeholder id (the zip's file name
> minus `.zip`) and version `0.0.0`. Before anything is staged, though, the plugin calls
> `hydrateFromApiManifest()` on every resolved extension: it reads `BTrace-Extension-Id`,
> `BTrace-Extension-Version`, `BTrace-Extension-Permissions`, and the other `BTrace-Extension-*`
> attributes from the API jar's manifest (written by the `io.btrace.extension` plugin) and treats
> them as authoritative. Call the zip whatever you like — the id is `btrace-metrics` either way.
> What *does* fail the build is an API jar with no `BTrace-Extension-Permissions` attribute: the
> plugin refuses to default an embedded extension's permissions to an empty set.

Look at what actually landed in the jar:

```sh
unzip -l build/libs/demo-btrace-agent.jar | grep -E 'MANIFEST|btrace-extensions|metrics'
```

**You should see** something like (one `extension.properties` per embedded extension — there are
more than one, see the manifest note below):

```
META-INF/MANIFEST.MF
META-INF/btrace-extensions/btrace-contracts/extension.properties
...
META-INF/btrace-extensions/btrace-metrics/extension.properties
...
io/btrace/metrics/MetricsService.class
io/btrace/metrics/MetricsServiceImpl.classdata
```

(The exact class list depends on `btrace-metrics`' declared API surface — the two load-bearing
facts, verified against `BTraceFatAgentPlugin.groovy`'s `stageExtension()`, are: API interfaces stay
plain `.class` files flattened at the jar root for bootstrap loading, and implementation classes are
renamed to `.classdata` — also at the jar root, next to their package path, *not* nested under
`META-INF/btrace-extensions/<id>/` — so they're found by
`io.btrace.extension.impl.ClassDataLoader`, which resolves `.classdata` resources by exact class
name from the jar root, per `ExtensionLoaderImpl.loadEmbedded()`.)

Check the manifest:

```sh
unzip -p build/libs/demo-btrace-agent.jar META-INF/MANIFEST.MF
```

**You should see**:

```
Manifest-Version: 1.0
Premain-Class: io.btrace.boot.Loader
Agent-Class: io.btrace.boot.Loader
BTrace-Agent-Main: io.btrace.agent.Main
Can-Redefine-Classes: true
Can-Retransform-Classes: true
Boot-Class-Path: demo-btrace-agent.jar
BTrace-Embedded-Extensions: btrace-contracts,btrace-gpu-bridge,btrace-llm-trace,btrace-metrics,btrace-rag-quality,btrace-statsd,btrace-utils
```

`Boot-Class-Path` and `BTrace-Embedded-Extensions` are the two attributes `fatAgentJar` sets
itself; everything else (you'll also see `Main-Class`, `BTrace-Version`, and `BTrace-Client-Main`)
is copied verbatim from the engine jar's manifest by `seedManifestFromEngine()`. The embedded list
is *merged*, not replaced: the public `io.btrace:btrace:3.0.0` artifact is itself a fat agent —
`btrace-dist/build.gradle` publishes `fatAgentJar` under that coordinate — and already embeds
BTrace's seven default extensions, `btrace-metrics` included. Your `file()` source is deduplicated
by id against that list; it's the mechanism for adding an extension the engine *doesn't* carry
(your own, from [Tutorial 6](06-write-your-own-extension.md), or a third-party one).

## Step 4 — Attach it at JVM startup and use the embedded extension

Copy the built jar wherever you like, then start the demo app *with the fat agent already loaded* —
no separate `btrace <PID>` attach step needed to get the extension in. `btrace-metrics` is
**privileged**: its `package-info.java` declares `THREADS`, and the build-time permission scan adds
`REFLECTION` and `CLASSLOADER`, so its API jar's manifest reads
`BTrace-Extension-Permissions: THREADS,REFLECTION,CLASSLOADER`. An embedded privileged extension
is gated exactly like a filesystem-installed one, so allow it right on the `-javaagent` line:

```sh
java -javaagent:build/libs/demo-btrace-agent.jar=debug=true,allowExtensions=btrace-metrics DemoApp.java
```

**You should see** the usual demo app lines, plus (message text verified against
`Main.java`/`ExtensionLoader.java`; exact log prefixes depend on your SLF4J SimpleLogger config;
the count is 7 because the engine embeds the default extension set — see Step 3):

```
[demo] order service running - stop with Ctrl+C
Initializing BTrace extension system
Extension system initialized with 7 available extension(s)
[demo] processed 64 orders, 8 failed
```

In terminal 2, get the PID (`jps`) and deploy [Tutorial 4](04-extensions-and-permissions.md)'s
metrics probe — reused unchanged from [demo/LatencyHistogram.java](demo/LatencyHistogram.java):

```sh
btrace <PID> LatencyHistogram.java
```

**You should see** the same histogram output as Tutorial 4, every 5 seconds:

```
=== Latency Report ===
chargeCard    p50=27ms  p95=321ms  p99=378ms  (n=41)
processOrder  p50=58ms  p95=346ms  p99=402ms  (n=41)
=======================
```

> **What just happened?** Tutorial 4 granted `btrace-metrics` by hand-editing
> `~/.btrace/permissions.properties`; here the same grant travelled as an agent argument. The agent
> still runs `PermissionPolicy.loadFromDefaults()` first (`-Dbtrace.permissions=...`, then
> `~/.btrace/permissions.properties`, then a classpath resource) and *then* applies
> `allowExtensions=` / `allowPrivileged=` from the `-javaagent` arguments on top. `allowExtensions`
> is additive, and an explicit deny is checked before the privileged gate — so a leftover
> `denyExtensions=btrace-metrics` from Tutorial 4's Step 4 would still win; Tutorial 4's clean-up
> removes that file, which is why the argument above is all you need. Embedding does **not** bypass
> the gate: the fat-agent plugin copies `BTrace-Extension-Permissions` into the embedded
> `extension.properties` (`hydrateFromApiManifest()`), `EmbeddedExtensionRepository` parses that
> `permissions` property into the descriptor, and `ExtensionBridgeImpl` refuses to link a privileged
> extension unless `allowPrivileged=true` is set or its id is in `allowExtensions`. Leave the
> argument off and you get Tutorial 4's `! ERROR` block
> (`BTrace optional service unavailable: io.btrace.metrics.MetricsService`) on every probed call,
> and `btrace -le <PID>` reports
> `btrace-metrics: Blocked privileged extension. Required=[THREADS, REFLECTION, CLASSLOADER]`. The
> fix is the argument above — or `allowPrivileged=true`, which allows *every* privileged extension
> in the jar and is broader than you usually want.

## Step 5 — Add a startup probe

The Gradle plugin can bundle a compiled probe so it starts with the target JVM. Configure the
directory containing the compiled package tree and name the probe by its full binary name:

```groovy
btraceFatAgent {
    bundledProbes {
        from layout.buildDirectory.dir('compiled-probes').get().asFile
        include 'com.example.OrderStartupProbe'
    }
}
```

The class is stored at
`META-INF/btrace-probes/com/example/OrderStartupProbe.class`. Select it with:

```sh
java -javaagent:build/libs/demo-btrace-agent.jar=probes=com.example.OrderStartupProbe,output=stdout \
     DemoApp.java
```

Probe names are exact Java binary names. Invalid names, configured classes that are missing at
build time, and requested resources that are missing at startup all fail loudly instead of being
ignored. This also prevents a `probes=` value from escaping the `META-INF/btrace-probes/`
namespace.

## Step 6 — Maven status in 3.0.0

The unpublished Maven fat-agent module was removed for 3.0.0. It used a pre-3.0 artifact and
classdata contract and could report a successful build even though the embedded implementation
could not load.

Use the Gradle workflow in Steps 1–5 for fat agents. The external
[`btrace-maven`](https://github.com/btraceio/btrace-maven) project remains the Maven integration for
compiling BTrace scripts; it is not a replacement fat-agent packager.

## Step 7 — Clean up

`Ctrl+C` the demo app. Remove the scratch directories if you don't want to keep them:

```sh
rm -rf ~/fat-agent-demo
```

## Troubleshooting

- **`Could not find method fatAgentJar()` / plugin not found** — the `plugins { id
  'io.btrace.fat-agent' version '3.0.0' }` block needs `gradlePluginPortal()` (or wherever your
  BTrace release is published) in `pluginManagement.repositories`, as in
  [fat-agent-settings.gradle](demo/fat-agent-settings.gradle).
- **Build fails with `Cannot embed extension '...': ... is missing BTrace-Extension-Permissions`**
  — the zip's API jar wasn't produced by the `io.btrace.extension` plugin, which writes the
  `BTrace-Extension-*` manifest attributes (id, version, permissions, ...) that
  `hydrateFromApiManifest()` requires. Rebuild it with `packageExtension` (Step 1); renaming the zip
  can't help, because the file name is never used as the id (Step 3's callout).
- **`probes=YourProbe` fails at startup with `BundledProbeException`** — the named class is not
  staged under `META-INF/btrace-probes/` in the jar (check `bundledProbes {}` in Step 5 and the
  fully qualified class name); a missing bundled probe is a loud failure, never a silent no-op.
- **Embedded extension is blocked** (`! ERROR ... BTrace optional service unavailable`, and
  `btrace -le <PID>` says `Blocked privileged extension`) — embedded and filesystem extensions go
  through the same privileged-tier gate (Step 4's callout). Pass `allowExtensions=<id>` (or
  `allowPrivileged=true`) on the `-javaagent` line, or grant it in `~/.btrace/permissions.properties`
  as [Tutorial 4](04-extensions-and-permissions.md) does — and make sure that file doesn't still
  carry a `denyExtensions=` entry from Tutorial 4's Step 4, which takes precedence.
- **An old Maven `fat-agent` configuration no longer resolves** — the unpublished module was
  removed for 3.0.0; use the Gradle plugin from this tutorial.

## Go deeper

- Extensions, permissions, and the tier model this tutorial's callouts build on:
  [Tutorial 4](04-extensions-and-permissions.md), [Permission Policy](../PermissionPolicy.md)
- Writing an extension from scratch (the `io.btrace.extension` plugin, API/impl split, permission
  scanning): [BTrace Extension Development Guide](../BTraceExtensionDevelopmentGuide.md)
- Fat agent architecture end to end: [Fat Agent Plugin Architecture](../architecture/fat-agent-plugin.md),
  [Gradle Plugin README](../../btrace-gradle-plugin/README.md)
- Single-JAR deployment in context (Spark/K8s examples, Docker images):
  [Getting Started — Fat Agent JAR](../GettingStarted.md#fat-agent-jar-single-jar-deployment)
