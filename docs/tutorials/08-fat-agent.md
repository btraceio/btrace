# One JAR to Rule Them All: Building a BTrace Fat Agent

Package BTrace plus a real extension into a single `-javaagent` JAR — no `$BTRACE_HOME`, no
separate extensions directory, nothing else to copy onto a Spark executor, a Hadoop node, or a
minimal container.

**Persona:** a platform engineer who ships BTrace as part of someone else's deployment, not just
runs it themselves. **Time:** ~15 minutes.

A note before you start: this tutorial builds real JARs with Gradle and Maven, which need your own
build environment and network access to a Maven repository — this is not something a docs sandbox
can execute for you. Every command, DSL property, and file path below is traced to the plugin/mojo
source and its test suite (cited inline); where that source revealed a real gap between what's
documented and what the runtime actually does, this tutorial says so plainly instead of pretending
otherwise — see the callouts marked **Heads up**.

## What you'll need

- JDK 11 or newer on your PATH
- A Gradle 8+ or Maven 3.9+ build with network access to resolve BTrace and extension artifacts
- Two terminal windows (same [demo app](demo/DemoApp.java) as the rest of this series)

## Step 1 — Get something to embed

The fat agent plugins embed *extensions*, not scripts. This tutorial embeds `btrace-metrics` — the
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
> and the shaded implementation jar together under classifier `extension`. That's the artifact
> shape the fat-agent plugin's `file()` and `maven()` sources expect to unpack.

Copy it into a scratch directory, renamed to match the extension's real id (`btrace-metrics`) —
you'll see exactly why that naming matters in Step 3:

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

    embedExtensions {
        file('btrace-metrics.zip')
    }
}
```

`baseName` and the `embedExtensions { file(...) }` / `maven(...)` / `project(...)` source builders
are exactly the DSL surface the plugin exposes — verified against
`BTraceFatAgentExtension.groovy` (fields `baseName`, `outputDir`, `manifestAttributes`,
`relocations`, `autoDiscover`; the `ExtensionSourceSpec` builders `project()`, `maven()`, `file()`,
`files()`). Don't reach for a `registry(...)` source, even though `BTraceFatAgentExtension` has a
`registryUrl` field whose doc comment says it's "for resolving `registry(\"id\")` sources", and
`BTraceFatAgentPluginTest.registrySourceFailsForUnknownId()` calls exactly that — there is no
`registry(...)` method anywhere on `ExtensionSourceSpec` in this checkout, so that test (and any
build using `registry(...)`) fails outright rather than doing what its name implies.

## Step 3 — Build it and look inside

```sh
./gradlew fatAgentJar
```

**You should see**, among the standard Gradle task lines, this exact lifecycle message:

```
> Task :stageExtensions
[fat-agent] Staged 1 extension(s): btrace-metrics

> Task :fatAgentJar

BUILD SUCCESSFUL
```

> **What just happened?** `stageExtensions`, `stageProbes`, and `fatAgentJar` are the three tasks
> the plugin registers on `apply` — confirmed by `BTraceFatAgentPluginTest.pluginCanBeApplied()`,
> which asserts all three names appear in `gradle tasks --all`. The `"[fat-agent] Staged ..."` line
> is a literal string from `BTraceFatAgentPlugin.groovy`'s `stageExtensions` task.
>
> **Heads up — naming your `file()` zip matters.** `packageExtension`'s zip has no standalone
> `extension.properties` at its root (only the nested API/impl jars) — verified by reading the
> `packageExtension` task, which only adds `buildApiJar` and the shadow jar's output files.
> `FileExtensionSource.extractFromDirectory()` therefore falls back to the *zip's own filename
> minus `.zip`* as the extension id, and to `0.0.0` as its version, whenever it can't find that
> properties file. That's why Step 1 had you rename the file to `btrace-metrics.zip` — call it
> anything else and your embedded extension shows up under the wrong id.

Look at what actually landed in the jar:

```sh
unzip -l build/libs/demo-btrace-agent.jar | grep -E 'MANIFEST|btrace-extensions|metrics'
```

**You should see** something like:

```
META-INF/MANIFEST.MF
META-INF/btrace-extensions/btrace-metrics/extension.properties
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
Premain-Class: io.btrace.agent.Main
Agent-Class: io.btrace.agent.Main
Can-Redefine-Classes: true
Can-Retransform-Classes: true
Boot-Class-Path: demo-btrace-agent.jar
BTrace-Embedded-Extensions: btrace-metrics
```

Every one of these attribute keys and values is written verbatim by the `manifest { attributes(...) }`
block in `BTraceFatAgentPlugin.groovy`'s `fatAgentJar` task registration.

## Step 4 — Attach it at JVM startup and use the embedded extension

Copy the built jar wherever you like, then start the demo app *with the fat agent already loaded* —
no separate `btrace <PID>` attach step needed to get the extension in:

```sh
java -javaagent:build/libs/demo-btrace-agent.jar=debug=true DemoApp.java
```

**You should see** the usual demo app lines, plus (message text verified against
`Main.java`/`ExtensionLoader.java`; exact log prefixes depend on your SLF4J SimpleLogger config):

```
[demo] order service running - stop with Ctrl+C
Initializing BTrace extension system
Extension system initialized with 1 available extension(s)
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

> **What just happened?** Tutorial 4 needed you to hand-edit `~/.btrace/permissions.properties`
> with `allowExtensions=btrace-metrics` before this worked, because `btrace-metrics` requires the
> privileged `THREADS` permission. This time it just worked, with no policy file at all — and that's
> a real, verified difference worth knowing before you embed something privileged: embedded
> extensions are parsed by `EmbeddedExtensionRepository.parseEmbeddedExtension()`, which hardcodes
> `.requiredPermissions(PermissionSet.empty())` regardless of what the extension actually needs.
> `ExtensionBridgeImpl.requiresPrivileged()` then always returns `false` for an empty permission set,
> so the privileged-tier gate from [Tutorial 4](04-extensions-and-permissions.md) never triggers for
> anything you bundle into a fat agent. If you embed an extension that needs a privileged
> permission, it is active for anyone who runs your jar, with no separate opt-in — treat the act of
> embedding itself as the grant.

## Step 5 — Heads up: "zero-config startup probes" don't work yet in this checkout

`docs/BTraceExtensionDevelopmentGuide.md` documents a second mechanism: bundle a *compiled probe*
class into the fat agent (via `btraceFatAgent { bundledProbes { from(...) } }`) so it can run
automatically at JVM startup — either because you pass `-javaagent:jar=probes=MyProbe` explicitly,
or because an extension's `ExtensionConfigurator` picks it for you. That's a genuinely useful
capability for a platform engineer (a Spark executor that self-instruments with no operator
action), so it's worth knowing precisely where it stands.

Independently tracing the two ends of this feature turned up a mismatch:

- The Gradle plugin's `stageProbes` task copies compiled probe classes to
  `META-INF/btrace-probes/` inside the jar (`BTraceFatAgentPlugin.groovy`), matching what
  `docs/architecture/fat-agent-plugin.md` and `docs/BTraceExtensionDevelopmentGuide.md` both
  describe.
- The agent's actual loader never reads from that path. `Main.loadProbesFromNames()` only looks at
  each *embedded extension's own* `getBundledProbes()` list (populated from a `probes=` key inside
  that extension's own `extension.properties`), and then loads the named class from
  `<extension's resourceBasePath>/probes/<Name>.class` — i.e.
  `META-INF/btrace-extensions/<extension-id>/probes/<Name>.class`
  (`EmbeddedExtensionRepository.discoverBundledProbes()` /
  `Main.loadEmbeddedProbe()`). Nothing reads `META-INF/btrace-probes/` at runtime.

So as of this checkout, staging a probe with the fat-agent plugin's `bundledProbes {}` block and
then passing `probes=YourProbe` (or relying on a configurator) is a silent no-op: the agent logs
`"Loading bundled probes: YourProbe"` (a real, verified log line from `Main.java`), scans embedded
extensions for one whose bundled-probes list contains that name, finds none, and moves on — no
error, and no probe. Until that's wired up, use the attach-based flow from Step 4
(`btrace <PID> Script.java`) for anything you'd otherwise want auto-started.

## Step 6 — Do the same thing with Maven

The Maven equivalent is a real goal, `fat-agent`, bound to the `package` phase by default
(`@Mojo(name = "fat-agent", defaultPhase = LifecyclePhase.PACKAGE)` in `FatAgentMojo.java`). Copy
the demo pom in:

```sh
cp docs/tutorials/demo/fat-agent-pom.xml ~/fat-agent-demo-maven/pom.xml
cd ~/fat-agent-demo-maven
mvn package
```

[fat-agent-pom.xml](demo/fat-agent-pom.xml)'s `<configuration>` block uses the mojo's real
`@Parameter` field names — `btraceVersion`, `outputName` (default `btrace-agent-fat`),
`outputDirectory` (default `${project.build.directory}`), `extensions` (a list of
`groupId:artifactId:version` strings) — confirmed by reading `FatAgentMojo.java`'s field
declarations directly.

**You should see** these exact log lines among Maven's output (verified: `FatAgentMojo.execute()`'s
`getLog().info(...)` calls):

```
[INFO] Building BTrace fat agent JAR...
[INFO] Resolved btrace-agent: .../btrace-agent-3.0.0.jar
[INFO] Resolved btrace-boot: .../btrace-boot-3.0.0.jar
[INFO] Processing extension: io.btrace:btrace-metrics:3.0.0
[INFO] Created fat agent JAR: .../target/demo-btrace-agent.jar
[INFO] Embedded 1 extension(s): [btrace-metrics]
```

> **What just happened?** The goal resolves `io.btrace:btrace-agent` and `io.btrace:btrace-boot` as
> the base jars, extracts each configured extension's `api`/`impl`-classified artifacts, renames the
> impl classes to `.classdata`, rewrites the manifest with `BTrace-Embedded-Extensions`, and replaces
> your project's build artifact with the fat jar (`project.getArtifact().setFile(outputJar)`) — so
> `target/demo-btrace-agent.jar` is what gets installed/deployed, not a plain `demo-btrace-agent-1.0.0.jar`.

**Heads up — two things this tutorial could *not* confirm work end to end for Maven**, found while
tracing the mojo against how `btrace-metrics` actually publishes:

1. **Classifier mismatch.** `FatAgentMojo.processExtension()` resolves an extension's implementation
   via `resolveArtifactWithClassifier(groupId, artifactId, version, "impl")`. But
   `BTraceExtensionPlugin.groovy`'s `maven-publish` block (the `mavenExtension` publication) only
   ever attaches classifiers `api`, `api-sources`, `api-javadoc`, and `extension` — there is no plain
   `impl`-classified artifact published anywhere in this repo (confirmed by grepping for
   `classifier = 'impl'` across the whole tree: no matches). If that holds for your release too, the
   mojo's impl lookup fails, is caught, and logged only at `debug` ("No Impl JAR found for
   ...") — silently producing a fat jar with the extension's API on the bootstrap classpath but no
   working implementation behind it.
2. **`.classdata` path mismatch.** Even when an impl jar *is* found, `FatAgentMojo.stageImplClasses()`
   writes `.classdata` files under `META-INF/btrace-extensions/<extensionId>/impl/...`. But
   `io.btrace.extension.impl.ClassDataLoader.findClass()` looks up `.classdata` resources by the
   class's own package path from the jar root (`name.replace('.', '/') + ".classdata"`) — the same
   flat layout the Gradle plugin produces, not the nested one Maven produces here.

Both are real, source-confirmed gaps in this checkout, not something this tutorial is guessing at —
but this tutorial cannot rebuild and run the actual 3.0.0 release artifacts to prove or disprove
them empirically (no network in this environment). Treat the Maven goal's *configuration surface*
(goal name, parameter names, output naming, manifest rewriting) as verified, and independently
confirm an embedded extension's implementation actually loads before you rely on the Maven path in
production. If you hit this, the Gradle plugin's `file()`/`project()` sources (Steps 1–4) don't have
either gap.

## Step 7 — Clean up

`Ctrl+C` the demo app. Remove the scratch directories if you don't want to keep them:

```sh
rm -rf ~/fat-agent-demo ~/fat-agent-demo-maven
```

## Troubleshooting

- **`Could not find method fatAgentJar()` / plugin not found** — the `plugins { id
  'io.btrace.fat-agent' version '3.0.0' }` block needs `gradlePluginPortal()` (or wherever your
  BTrace release is published) in `pluginManagement.repositories`, as in
  [fat-agent-settings.gradle](demo/fat-agent-settings.gradle).
- **Embedded extension shows up with the wrong id, or version `0.0.0`** — see Step 3's callout: a
  `file()` source without a top-level `extension.properties` in the zip falls back to the zip's own
  filename. Rename the file to match the real extension id.
- **`probes=YourProbe` does nothing at startup, no error either** — expected in this checkout; see
  Step 5. Attach with `btrace <PID> Script.java` instead.
- **Extension works when embedded but was blocked by policy when filesystem-installed (or vice
  versa)** — see Step 4's callout: embedded and filesystem extensions are gated differently right
  now; embedding bypasses the privileged-permission check that
  [Tutorial 4](04-extensions-and-permissions.md) demonstrates for filesystem extensions.
- **A probe using `@Injected` against a Maven-built fat jar's embedded service gets a throwing/no-op
  shim, or the service class can't be found at all** — see Step 6's two callouts; confirm the impl
  artifact actually resolved (`mvn package -X` and look for "No Impl JAR found") and that its
  `.classdata` files ended up where `ClassDataLoader` expects them.

## Go deeper

- Extensions, permissions, and the tier model this tutorial's callouts build on:
  [Tutorial 4](04-extensions-and-permissions.md), [Permission Policy](../PermissionPolicy.md)
- Writing an extension from scratch (the `io.btrace.extension` plugin, API/impl split, permission
  scanning): [BTrace Extension Development Guide](../BTraceExtensionDevelopmentGuide.md)
- Fat agent architecture end to end: [Fat Agent Plugin Architecture](../architecture/fat-agent-plugin.md),
  [Gradle Plugin README](../../btrace-gradle-plugin/README.md)
- Single-JAR deployment in context (Spark/K8s examples, Docker images):
  [Getting Started — Fat Agent JAR](../GettingStarted.md#fat-agent-jar-single-jar-deployment)
