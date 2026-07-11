# Write Your Own Extension in 30 Minutes

Bundled extensions like `btrace-metrics` ([Tutorial 4](04-extensions-and-permissions.md)) are
great until you need a capability specific to your own systems. This tutorial builds one from
scratch with the real `io.btrace.extension` Gradle plugin — a tiny "order counter" service — installs
it, grants it a permission, and injects it into a probe against the same demo app the rest of this
series uses.

**Persona:** an Instrumenter or Platform engineer who wants a reusable capability, not just a
one-off script. **Time:** ~25 minutes (most of it is copy-pasting four small files).

## What you'll need

- JDK 11 or newer on your PATH (the extension's own bytecode targets Java 8, but the plugin build
  itself needs 11+; the demo app uses single-file source launch)
- BTrace 3.0 installed — `bin/btrace` and `bin/btracex` on your PATH ([installation options](../GettingStarted.md#installation))
- Gradle available to build the extension project (a wrapper is fine — this repo's own build uses
  Gradle 9.5.1)

## Step 1 — Scaffold the extension project

An extension is its own small Gradle project — not something you add to the app you're tracing.
Create one next to (not inside) this repo:

```sh
mkdir -p order-counter-ext/src/main/java/com/example/orderstats
cd order-counter-ext
cat > settings.gradle <<'EOF'
rootProject.name = 'order-counter'
EOF
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
    apiCompileOnly 'io.btrace:btrace-core:3.0.0'
    implCompileOnly 'io.btrace:btrace-core:3.0.0'
}

btraceExtension {
    id = 'order-counter'
    name = 'Order Counter'
    description = 'Counts order outcomes observed by BTrace probes'
    services = ['com.example.orderstats.OrderCounterService']
}
EOF
```

> **What just happened?** `io.btrace.extension` is the plugin's real Gradle Plugin Portal ID
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
@ExtensionDescriptor(
    name = "order-counter",
    version = "3.0.0",
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

**You should see** (illustrative — reconstructed from the plugin's task graph and its own
lifecycle log lines, since building this for real needs Maven Central access this environment
doesn't have; every task name and log string below is taken directly from
`BTraceExtensionPlugin.groovy`):

```
> Task :compileJava
> Task :processResources
> Task :generateServiceShims
> Task :compileServiceShims
> Task :generateShimIndex
> Task :generateExportsIndex
[BTRACE-EXT] exports: 1 types
> Task :shadowJar
> Task :buildImplJar
> Task :buildApiJar
[BTRACE-EXT] permissions: scanned=[THREADS] merged=[THREADS]
> Task :validateServiceApis
[BTRACE-EXT] validateServiceApis: OK for 1 service(s)
> Task :packageExtension

BUILD SUCCESSFUL
```

> **What just happened?** `packageExtension` (a `Zip` task) depends on `buildApiJar`,
> `buildImplJar`, `compileServiceShims`, `generateShimIndex`, `generateExportsIndex`, and
> `validateServiceApis` — all real task registrations in `BTraceExtensionPlugin.groovy`. Notice
> `buildApiJar` runs *after* `shadowJar`/`buildImplJar`, not before: its manifest needs to scan the
> already-built impl JAR for permissions, so the plugin wires `buildApiJar.dependsOn { <the impl
> jar task> }` explicitly. The `[BTRACE-EXT] permissions: scanned=... merged=...` line is the
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
Services : (none)

Hint: To enable/disable this extension for implementations, edit your policy:
  btracex policy edit --home
Or set explicitly:
  btracex policy set --allowExtensions order-counter --policy-file ~/.btrace/permissions.properties

Note: This extension requires privileged permissions. You can allow all privileged extensions with:
  btracex policy set --allowPrivileged true --policy-file ~/.btrace/permissions.properties
```

> **What just happened?** `btracex install` (`io.btrace.extcli.Installer`) recognized the path as
> a local `.zip`, derived the id `order-counter` from the filename, and copied both jars into a
> per-extension subdirectory under your extensions root — the same layout `btracex list` showed
> for `btrace-metrics` in Tutorial 4 (`.../extensions/btrace-metrics`, a directory, not a bare
> jar). It then ran the same inspection `btracex inspect` runs and printed the report immediately.
>
> Look closely at `Services : (none)` — that's not a sign anything went wrong. Traced through
> `ExtensionInspector.readServices()`, that field only looks at `META-INF/services/*` entries
> inside the impl JAR, and the `io.btrace.extension` plugin never writes any (neither does
> `btrace-metrics`, for what it's worth). The list BTrace's runtime actually uses to wire up your
> probe's `@Injected` field is the API JAR's `BTrace-Extension-Services` manifest attribute, which
> *is* populated correctly — `Required : [THREADS]` came from that same manifest
> (`BTrace-Extension-Permissions`) and is accurate. You can see the real service list yourself:
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
> `@Injected` field — no `optional = true`. Per the Extension Interface Rules, "required
> injections (`optional = false`) never fall back and fail fast if unavailable" — if you'd skipped
> Step 6, BTrace would have refused to link this script at all, rather than running it with a
> throwing stub. `processOrder`'s `@Location(Kind.ERROR)` fires whenever `validateOrder`'s
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

- **`Shadow plugin ('com.gradleup.shadow') must be applied`** — the plugin tries to auto-apply
  Shadow and only fails loudly if that can't resolve (e.g. offline, or the portal is unreachable).
  Add `id 'com.gradleup.shadow'` to your `plugins { }` block explicitly and re-run.
- **You left out `services = [...]` and nothing got exported** — the plugin *can* auto-detect
  service interfaces from `@ServiceDescriptor` without you listing them, but as of this checkout
  that detection (`SingleSourceApiPartition.SERVICE_DESCRIPTOR_DESC` and the equivalent checks in
  `BTraceExtensionPlugin.groovy`) matches the annotation's *old* package name
  (`org.openjdk.btrace.core.extensions.ServiceDescriptor`), not the current
  `io.btrace.core.extensions.ServiceDescriptor` your API actually uses. In other words:
  auto-detection is currently dead code for this codebase's own annotations. Always declare
  `services` explicitly in `btraceExtension { }`, exactly as this tutorial and the real
  `btrace-metrics` module (`btrace-extensions/btrace-metrics/build.gradle`) both do.
- **`Could not find io.btrace:btrace-core:<your-project-version>`** — the plugin auto-registers
  the `@ExternalType` annotation processor, and outside the BTrace monorepo (no sibling
  `:btrace-core` project) it resolves that processor as
  `"io.btrace:btrace-core:${project.version}"` — your extension project's *own* `version`, not the
  BTrace release you're building against (`BTraceExtensionPlugin.groovy`, the
  `processorProject == null` branch). This is why this tutorial's `build.gradle` sets
  `version = '3.0.0'` to match the BTrace release instead of, say, `1.0`: if your extension's own
  versioning scheme diverges from BTrace's, this auto-registration will request a `btrace-core`
  coordinate that doesn't exist on Maven Central.
- **The probe fails to link instead of running** — you deployed it before Step 6's permission
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
