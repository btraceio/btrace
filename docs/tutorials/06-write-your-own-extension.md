# Write Your Own Extension in 30 Minutes

Bundled extensions like `btrace-metrics` ([Tutorial 4](04-extensions-and-permissions.md)) are
great until you need a capability specific to your own systems. This tutorial builds one from
scratch with the real `io.btrace.extension` Gradle plugin — a tiny "order counter" service — installs
it, grants it a permission, and injects it into a probe against the same demo app the rest of this
series uses.

**Persona:** an Instrumenter or Platform engineer who wants a reusable capability, not just a
one-off script. **Time:** ~25 minutes (most of it is copy-pasting four small files).

## What you'll need

- JDK 17 or newer on your PATH (the extension's own bytecode targets Java 8, but Gradle 9 itself
  needs 17+; the demo app uses single-file source launch)
- BTrace 3.0 installed — `bin/btrace` and `bin/btracex` on your PATH ([installation options](../GettingStarted.md#installation))
- A `gradle` command on your PATH, used once to generate the project's Gradle wrapper (Step 1).
  The wrapper pins Gradle 9.8.2, the version this repo builds with; the plugin needs Gradle 9.2 or
  newer

## Step 1 — Scaffold the extension project

An extension is its own small Gradle project — not something you add to the app you're tracing.
Create one next to (not inside) this repo:

```sh
mkdir -p order-counter-ext/src/main/java/com/example/orderstats
cd order-counter-ext
cat > settings.gradle <<'EOF'
rootProject.name = 'order-counter'
EOF
gradle wrapper --gradle-version 9.8.2
cat > build.gradle <<'EOF'
plugins {
    id 'io.btrace.extension' version '3.0.0'
}

group = 'com.example'
version = '3.0.0'

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    apiCompileOnly 'io.btrace:btrace:3.0.0'
    implCompileOnly 'io.btrace:btrace:3.0.0'
}

btraceExtension {
    id = 'order-counter'
    name = 'Order Counter'
    description = 'Counts order outcomes observed by BTrace probes'
    services = ['com.example.orderstats.OrderCounterService']
}
EOF
```

> **What just happened?** `gradle wrapper` ran before `build.gradle` existed, so it only needed
> `settings.gradle`; from here on, `./gradlew` uses the pinned Gradle 9.8.2 whatever version your
> `gradle` command is. `io.btrace.extension` is the plugin's real Gradle Plugin Portal ID
> (`btrace-gradle-plugin/build.gradle`'s `gradlePlugin { plugins { btraceExtension { id =
> 'io.btrace.extension' ... } } }`). Applying it also auto-applies the Shadow plugin
> (`com.gradleup.shadow`) for you unless you've turned that off, and registers the `btraceExtension
> { }` block you just filled in as a real Gradle extension object
> (`BTraceExtensionMetadata` in the plugin source). Setting `services` explicitly here isn't
> optional busywork — see the Troubleshooting section for why.

## Step 2 — Write the API

Extensions use a **single authored source tree**: API and implementation classes live side by
side, and the plugin works out which classes belong on the API side from the `services` list you
just declared. This is exactly how the bundled `btrace-metrics` extension is laid out
(`btrace-extensions/btrace-metrics/src/main/java/io/btrace/metrics/`) — same package for the
service interface and its implementation, no separate `api`/`impl` subpackages required.

```sh
cat > src/main/java/com/example/orderstats/package-info.java <<'EOF'
// Optional. `permissions` is checked against the manifest at build time; identity and version
// come from the btraceExtension block, so they are deliberately not restated here.
@ExtensionDescriptor(
    name = "order-counter",
    description = "Counts order outcomes observed by BTrace probes",
    permissions = {Permission.THREADS})
package com.example.orderstats;

import io.btrace.core.extensions.ExtensionDescriptor;
import io.btrace.core.extensions.Permission;
EOF
cat > src/main/java/com/example/orderstats/OrderCounterService.java <<'EOF'
package com.example.orderstats;

import io.btrace.core.extensions.Permission;
import io.btrace.core.extensions.ServiceDescriptor;

/** Counts named outcomes observed by BTrace probes. */
@ServiceDescriptor(permissions = {Permission.THREADS})
public interface OrderCounterService {
  void increment(String key);

  long count(String key);

  void reset();
}
EOF
```

> **What just happened?** `@ServiceDescriptor` and `@ExtensionDescriptor` both live in
> `btrace-core/src/main/java/io/btrace/core/extensions/` — this is the same package
> `btrace-metrics`'s `MetricsService` and its `package-info.java` use. Declaring
> `permissions = {Permission.THREADS}` on the service documents the requirement in one place;
> Step 4 shows where BTrace gets its authoritative answer from instead (bytecode scanning, not
> this annotation — see Troubleshooting).

## Step 3 — Write the implementation

```sh
cat > src/main/java/com/example/orderstats/OrderCounterServiceImpl.java <<'EOF'
package com.example.orderstats;

import io.btrace.core.extensions.Extension;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class OrderCounterServiceImpl extends Extension implements OrderCounterService {
  private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();

  public OrderCounterServiceImpl() {}

  @Override
  public void increment(String key) {
    counts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
  }

  @Override
  public long count(String key) {
    AtomicLong c = counts.get(key);
    return c == null ? 0L : c.get();
  }

  @Override
  public void reset() {
    counts.clear();
  }
}
EOF
```

> **What just happened?** `extends Extension` is the same base class `MetricsServiceImpl` extends
> (`io.btrace.core.extensions.Extension`) — it gives you a lifecycle (`initialize`/`close`) you
> don't need for this simple a service, so the no-arg constructor is enough. Nothing here is
> BTrace-specific: it's a plain, thread-safe counter using `ConcurrentHashMap` and `AtomicLong`.
> That choice matters for the very next step.

## Step 4 — Build it

```sh
./gradlew packageExtension
```

**You should see** (with `--console=plain`; the default console shows the same task order more
compactly):

```
[BTRACE-EXT] Applying Shadow plugin automatically (com.gradleup.shadow) for :
> Task :compileJava
> Task :generateServiceShims
> Task :compileServiceShims
> Task :generateExportsIndex
[BTRACE-EXT] exports: 1 types
> Task :generateShimIndex
> Task :processResources NO-SOURCE
> Task :classes
> Task :compileTestJava NO-SOURCE
> Task :shadowJar
> Task :buildApiJar
[BTRACE-EXT] permissions: scanned=[THREADS] merged=[THREADS]
> Task :buildImplJar
> Task :validateServiceApis
[BTRACE-EXT] validation report: build/reports/btrace/service-api-validation.txt
[BTRACE-EXT] Missing nullability on parameter 0 of 'com.example.orderstats.OrderCounterService#count'
[BTRACE-EXT] Missing nullability on parameter 0 of 'com.example.orderstats.OrderCounterService#increment'
[BTRACE-EXT] validateServiceApis: OK for 1 service(s)
> Task :packageExtension

BUILD SUCCESSFUL
```

The two `Missing nullability` lines are warnings, not failures: `validateServiceApis` defaults
`nullabilitySeverity` to `warn` (see [Extension Interface Rules](../ExtensionInterfaceRules.md)).

> **What just happened?** `packageExtension` (a `Zip` task) depends on `buildApiJar`,
> `buildImplJar`, `compileServiceShims`, `generateShimIndex`, `generateExportsIndex`, and
> `validateServiceApis` — all real task registrations in `BTraceExtensionPlugin.groovy`. Notice
> `buildApiJar` runs *after* `shadowJar`, not before: its manifest needs to scan the already-built
> impl JAR for permissions, so the plugin makes `buildApiJar` depend on `shadowJar` explicitly
> (`buildImplJar` is only a named wrapper around `shadowJar`). The
> `[BTRACE-EXT] permissions: scanned=... merged=...` line is the
> interesting one: because
> `scanPermissions` defaults to `true`, the plugin doesn't trust your `@ServiceDescriptor`
> annotation for the manifest — it decompiles `OrderCounterServiceImpl`'s bytecode
> (`PermissionScanner.groovy`) looking for JDK APIs that imply a permission. Any class whose owner
> starts with `java/util/concurrent/` — that's `ConcurrentHashMap` *and* `AtomicLong` — maps to
> `THREADS`. That's exactly what Step 3's implementation uses, so the plugin infers `THREADS` on
> its own, with no `requiredPermissions` line needed in your `btraceExtension { }` block. You'll
> end up with three artifacts: `build/libs/order-counter-3.0.0-api.jar`,
> `build/libs/order-counter-3.0.0-impl.jar` (shaded/minimized by Shadow), and
> `build/distributions/order-counter-3.0.0-extension.zip` bundling both. The plugin also disables
> the default `jar` task outright (`project.tasks.named('jar') { enabled = false }`) — don't go
> looking for a plain `order-counter-3.0.0.jar`; it isn't produced.

## Step 5 — Install it

```sh
btracex install build/distributions/order-counter-3.0.0-extension.zip
```

**You should see** (paths will differ if `BTRACE_HOME` isn't set — it then installs to
`~/.btrace/extensions/order-counter` instead):

```
Installed extension 'order-counter' into: /opt/btrace-3.0.0/extensions/order-counter
Extension: order-counter
Version  : 3.0.0
Privileged: true
Required : [THREADS]
Services : com.example.orderstats.OrderCounterService

Hint: To enable/disable this extension for implementations, edit your policy:
  btracex policy edit --home
Or set explicitly:
  btracex policy set --allowExtensions order-counter --policy-file ~/.btrace/permissions.properties

Note: This extension requires privileged permissions. You can allow all privileged extensions with:
  btracex policy set --allowPrivileged true --policy-file ~/.btrace/permissions.properties
```

> The `btracex policy edit` hint in that output is not implemented in 3.0.0: it prints
> "Interactive editor not yet implemented; use 'btracex policy set' for now" and exits with
> status 2. Use `btracex policy set` as shown on the next line.

> **What just happened?** `btracex install` (`io.btrace.extcli.Installer`) recognized the path as
> a local `.zip`, derived the id `order-counter` from the filename, and copied both jars into a
> per-extension subdirectory under your extensions root — the same layout `btracex list` showed
> for `btrace-metrics` in Tutorial 4 (`.../extensions/btrace-metrics`, a directory, not a bare
> jar). It then ran the same inspection `btracex inspect` runs and printed the report immediately.
>
> `Services :` echoes back the `services` list you declared in Step 1, and it is the same list
> BTrace's runtime uses to wire up your probe's `@Injected` field: the API JAR's
> `BTrace-Extension-Services` manifest attribute. `Required : [THREADS]` comes from that same
> manifest (`BTrace-Extension-Permissions`), which the plugin has already folded your
> `@ServiceDescriptor` permissions into — so the manifest is the whole story for both lines. You
> can confirm it yourself:
> `unzip -p build/libs/order-counter-3.0.0-api.jar META-INF/MANIFEST.MF | grep BTrace-Extension-Services`.

## Step 6 — Grant the permission

`THREADS` is a **privileged** permission (same tier as the `btrace-metrics` example in
[Tutorial 4](04-extensions-and-permissions.md)), so it needs an explicit grant before any probe
that requires it can attach:

```sh
mkdir -p ~/.btrace
cat > ~/.btrace/permissions.properties <<'EOF'
allowExtensions=order-counter
EOF
```

> **What just happened?** Same mechanism as Tutorial 4, Step 3 — `~/.btrace/permissions.properties`
> is read once per JVM, the first time BTrace's agent attaches to it. That's why the next step
> starts a *fresh* demo app rather than reusing one you had running earlier in this series.

## Step 7 — Use it from a script

Start a fresh copy of the shared demo app in terminal 1:

```sh
java DemoApp.java
```

Get its PID in terminal 2:

```sh
jps
```

Deploy the probe that injects your new service
([demo/OrderCounterProbe.java](demo/OrderCounterProbe.java)):

```java
import io.btrace.core.annotations.*;
import com.example.orderstats.OrderCounterService;

@BTrace
public class OrderCounterProbe {

  @Injected
  private static OrderCounterService counter;

  @OnMethod(clazz = "OrderService", method = "processOrder", location = @Location(Kind.RETURN))
  public static void onOrderSucceeded() {
    counter.increment("succeeded");
  }

  @OnMethod(clazz = "OrderService", method = "processOrder", location = @Location(Kind.ERROR))
  public static void onOrderFailed(Throwable t) {
    counter.increment("failed");
  }

  @OnTimer(5000)
  public static void report() {
    println("orders: succeeded=" + str(counter.count("succeeded"))
        + " failed=" + str(counter.count("failed")));
  }
}
```

```sh
btrace <PID> OrderCounterProbe.java
```

**You should see**, every 5 seconds (numbers will vary):

```
orders: succeeded=41 failed=4
orders: succeeded=89 failed=8
```

> **What just happened?** Unlike Tutorial 4's `LatencyHistogram`, `counter` here is a *required*
> `@Injected` field — no `optional = true`. If you'd skipped Step 6, BTrace would have refused
> the probe instead of running it: the client reports `Probe … was not deployed: required
> @Injected service com.example.orderstats.OrderCounterService (field 'counter') is
> unavailable: …` and exits with status 1. `processOrder`'s `@Location(Kind.ERROR)` fires whenever `validateOrder`'s
> exception propagates uncaught through it — the same call path Tutorial 1 found with `@error` —
> so `failed` tracks the ~8% validation failures and `succeeded` tracks everything else.

## Step 8 — Confirm it from the outside

With the probe still attached (or after detaching — `btracex` never touches a running JVM):

```sh
btracex list
```

**You should see** your extension alongside any others already installed:

```
order-counter [PRIV] - /opt/btrace-3.0.0/extensions/order-counter
```

## Step 9 — Clean up

`Ctrl+C` the `btrace` client to detach, then `Ctrl+C` the demo app in terminal 1. If you want to
leave your BTrace installation as you found it:

```sh
rm -rf "$BTRACE_HOME/extensions/order-counter" ~/.btrace/permissions.properties
```

## Troubleshooting

- **`[BTRACE-EXT] Shadow plugin is required`** — the plugin brings Shadow with it and applies it
  automatically, so this only happens when you set `btraceExtension { autoApplyShadow = false }`.
  Remove that line, or apply Shadow yourself with `id 'com.gradleup.shadow' version '9.6.1'` in
  your `plugins { }` block.
- **You left out `services = [...]` and nothing got exported** — automatic discovery only exports
  interfaces annotated with `io.btrace.core.extensions.ServiceDescriptor`. Confirm the annotation
  is on the compiled service interface (not only its implementation). An explicit `services` list
  remains available when an extension cannot annotate the interface it exports.
- **`btraceExtension.btraceVersion` is required** — when the plugin runs from a development or
  TestKit classpath it cannot read a published plugin manifest. Set
  `btraceExtension { btraceVersion = '3.0.0' }` explicitly. Published Plugin Portal builds carry
  that version in their implementation manifest. External builds resolve only
  `io.btrace:btrace:<btraceVersion>` for the `@ExternalType` processor; an extension's own
  `version` may therefore follow its independent release scheme.
- **`Probe … was not deployed: required @Injected service … is unavailable`** — you deployed it before Step 6's permission
  grant, or against a demo app process that was already running (and had already loaded an older
  policy) before you wrote the policy file. Start a fresh `java DemoApp.java` after Step 6.
- **`btracex list` prints nothing** — it scans `$BTRACE_HOME/extensions/` and
  `~/.btrace/extensions/` (plus `$BTRACE_EXT_PATH`). Confirm `BTRACE_HOME` is set, or check
  `~/.btrace/extensions/order-counter` if it isn't.

## Go deeper

- The full plugin-based workflow — classloader isolation, `@ExternalType` adapters, fat-agent
  embedding, publishing to a registry: [BTrace Extension Development Guide](../BTraceExtensionDevelopmentGuide.md)
- The permission model and `~/.btrace/permissions.properties` this tutorial reused:
  [Tutorial 4](04-extensions-and-permissions.md) and [Permission Policy](../PermissionPolicy.md)
- API authoring rules the build enforces (`validateServiceApis`'s nullability/shimability/purity
  checks): [Extension Interface Rules](../ExtensionInterfaceRules.md)
- Every `btracex`/`btrace` flag used above: [Quick Reference](../QuickReference.md)
