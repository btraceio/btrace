# Building your first BTrace extension

*Bundled extensions cover a lot of ground. Sooner or later you need one that knows about your own systems — here's how little that actually takes, and a gotcha worth knowing before you hit it.*

> Draft B5 · target: BTrace 3.0.0 + 3 weeks · grounded in `docs/tutorials/06-write-your-own-extension.md`

`btrace-metrics` gets you HdrHistogram-backed percentiles for free. But at some point you'll want a capability that's specific to your own domain — an order counter, a cache-hit tracker, whatever your systems actually need — and that means writing an extension of your own. The good news is that BTrace 3.0 ships a real Gradle plugin for exactly this, and building one is closer to filling in a template than writing a framework integration. The tutorial this post is based on builds a tiny "order counter" service from scratch, end to end, in about 25 minutes — and along the way it makes one build-file choice explicit that's worth understanding before it costs you twenty minutes of head-scratching.

## The scaffold

An extension is its own small Gradle project, built separately from whatever app you're tracing. The whole thing starts with a `build.gradle` that applies the plugin and declares a handful of facts about the extension:

```groovy
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
```

`io.btrace.extension` is the plugin's real Gradle Plugin Portal ID. Applying it auto-applies the Shadow plugin for you, and registers the `btraceExtension { }` block as a real Gradle extension object. Notice that `services` line — hold onto it, because it's the thing standing between "this works" and "this silently exports nothing" whenever the plugin can't discover your service interface on its own.

From there, the extension itself is refreshingly plain: BTrace extensions use a single authored source tree, meaning the API interface and its implementation live side by side in the same package — exactly how the bundled `btrace-metrics` extension is laid out. The API is a `package-info.java` carrying an `@ExtensionDescriptor`, plus a small interface:

```java
@ServiceDescriptor(permissions = {Permission.THREADS})
public interface OrderCounterService {
  void increment(String key);
  long count(String key);
  void reset();
}
```

And the implementation is a plain, thread-safe counter — nothing BTrace-specific about it at all:

```java
public final class OrderCounterServiceImpl extends Extension implements OrderCounterService {
  private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();

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
```

`extends Extension` is the same base class `MetricsServiceImpl` extends. Nothing here needs the lifecycle hooks that base class offers, so a no-arg constructor is enough.

## One build, three artifacts — and an inferred permission

`./gradlew packageExtension` runs the whole task graph: compile, generate service shims, build the API and impl jars, and — this is the part worth pausing on — scan the compiled implementation's bytecode for permissions. The plugin doesn't trust the `@ServiceDescriptor` annotation for its manifest; it decompiles `OrderCounterServiceImpl` looking for JDK APIs that imply a permission, and any class whose owner starts with `java/util/concurrent/` — `ConcurrentHashMap` and `AtomicLong` both count — maps to `THREADS`. That's exactly what the implementation above uses, so the build infers `THREADS` on its own, with no `requiredPermissions` line needed anywhere. You end up with an API jar, an impl jar, and a distributable zip, which `btracex install build/distributions/order-counter-3.0.0-extension.zip` drops straight into your extensions directory, printing back a full inspection report — `Privileged: true`, `Required: [THREADS]` — the moment it's installed.

## The gotcha: `services` isn't optional busywork

Here's the thing to know plainly, not bury in a footnote: that `services = [...]` line in `build.gradle` is *load-bearing*. The plugin does auto-detect service interfaces when you leave it out — it scans your compiled classes for *interfaces* carrying the `io.btrace.core.extensions.ServiceDescriptor` annotation and exports those. But that discovery only sees the annotation when it's on the compiled service interface itself, not just on the implementation. Put `@ServiceDescriptor` on the wrong side, or on a class rather than an interface, and nothing gets exported: the build logs `No services declared or detected`, reports success, and ships a service-less extension.

The fix costs nothing — declare `services` explicitly, exactly as this tutorial does and exactly as the real `btrace-metrics` module does. It also decides which classes land on the API side of the split, which is why the tutorial treats it as mandatory rather than optional. If you write your own extension against BTrace 3.0 today, list `services` by hand and let auto-detection be a fallback, not the plan.

## Wiring it into a probe

Once installed and granted (`THREADS` is privileged, same tier as `btrace-metrics`, so it needs the same `allowExtensions=order-counter` treatment in `~/.btrace/permissions.properties`), a probe pulls it in with an ordinary `@Injected` field — this time *required*, not optional, so a missing grant fails the script at link time rather than at runtime:

```java
@Injected
private static OrderCounterService counter;

@OnMethod(clazz = "OrderService", method = "processOrder", location = @Location(Kind.RETURN))
public static void onOrderSucceeded() {
  counter.increment("succeeded");
}
```

Running against the shared demo app, it reports back `orders: succeeded=41 failed=4` every five seconds, ticking up as the app processes orders — your own domain concept, counted by your own service, wired into BTrace's injection system exactly the way `btrace-metrics` is.

---

- Full hands-on walkthrough: [docs/tutorials/06-write-your-own-extension.md](../../docs/tutorials/06-write-your-own-extension.md)
- New to BTrace? Start here: [docs/GettingStarted.md](../../docs/GettingStarted.md)
<!-- TODO: replace with the per-post Discussions thread before publishing -->
- Questions, ideas, war stories: [GitHub Discussions](https://github.com/btraceio/btrace/discussions)
