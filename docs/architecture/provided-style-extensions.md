# Provided-Style Extensions for App Integrations

This guide shows how to migrate profile-based integrations (e.g., Spark/Hadoop) to BTrace extensions without mutating the global classpath.

## Principles

- API on bootstrap: expose minimal, stable APIs with simple/value types.
- Impl in isolated CL: load implementation in an extension classloader; no shading of app libs.
- Runtime linking: access application types via object hand-off and TCCL instead of compile-time imports.
- No classpath injection: avoid boot/system CL changes; reserve the escape hatch for exceptional cases only.

## Helper Utilities

- `io.btrace.extension.util.ClassLoadingUtil`
  - Loaders: `tccl()`, `definingLoader(Object)`
  - Class loading: `load(String, ClassLoader)`, `load(String, Object)`, `tryLoad(String, ClassLoader)`
  - Context: `withTCCL(ClassLoader, Supplier<T>)`, `withTCCL(ClassLoader, Runnable)`, `withDefiningLoader(Object, Supplier<T>)`
  - Services: `loadService(Class<T>, ClassLoader)`, `loadServices(Class<T>, ClassLoader)`
  - Resources: `getResource(String, ClassLoader)`, `openResource(String, ClassLoader)`
  - Optional child loader: `newChildURLClassLoader(List<Path>, ClassLoader)`, `safeClose(ClassLoader)`

- `io.btrace.extension.util.MethodHandleCache`
  - Caches public `MethodHandle`s for reflective adapters.

## API Sketch (Spark example)

```java
// exported API (on bootstrap)
package org.example.btrace.spark.api;
public interface SparkApi {
  void onJobStart(Object jobStartEvent);
  void onStageCompleted(Object stageInfo);
}
```

## Impl Sketch

```java
// implementation (extension CL)
package org.example.btrace.spark.impl;
import org.example.btrace.spark.api.SparkApi;
import io.btrace.extension.util.ClassLoadingUtil;
import io.btrace.extension.util.MethodHandleCache;
import java.lang.invoke.MethodHandle;

public final class SparkApiImpl implements SparkApi {
  private final MethodHandleCache mh = new MethodHandleCache();

  @Override
  public void onJobStart(Object evt) {
    ClassLoadingUtil.withDefiningLoader(
      evt,
      () -> {
        try {
          Class<?> cls = ClassLoadingUtil.loadFromContext(
              "org.apache.spark.scheduler.SparkListenerJobStart", evt);
          MethodHandle getJobId = mh.findVirtual(cls, "jobId", int.class);
          int jobId = (int) getJobId.invoke(evt);
          // emit metrics/logs...
        } catch (Throwable t) {
          // log and continue
        }
        return null;
      });
  }
}
```

## External Type Adapters (Recommended)

Writing reflective adapters by hand with `ClassLoadingUtil` + `MethodHandleCache` works but has three ergonomic costs: string method names aren't refactor-safe, eager `static final MethodHandle` fields fail extension init if the target class isn't yet visible, and every reflective call expands into 5+ lines of try/catch and cache plumbing.

The `@ExternalType` annotation + build-time annotation processor removes all three.

### How it works

Declare an interface in your extension's exported API set marked with `@ExternalType("fully.qualified.AppType")`. In practice this means an API-facing interface under `src/main/java`. The BTrace extension Gradle plugin auto-registers the annotation processor, which generates a companion `<InterfaceSimpleName>$Ext` class in the same package with typed `public static` dispatchers for each method.

```java
package com.example.spark.api;

import io.btrace.core.extensions.ExternalType;

@ExternalType("org.apache.spark.scheduler.SparkListenerJobStart")
public interface JobStart {
  int jobId();
  long time();
}
```

The generated `JobStart$Ext` can then be called directly from the impl:

```java
int id = JobStart$Ext.jobId(event);
long ts = JobStart$Ext.time(event);
```

### What the generated code does

Each dispatcher resolves the target class lazily via `self.getClass().getClassLoader()` (virtual methods) or `Thread.currentThread().getContextClassLoader()` (static methods, see below), then calls `MethodHandles.publicLookup().findVirtual` / `findStatic`. Handles are cached in a per-method `ClassValue`, keyed by the resolved application class, so one extension can safely serve applications with isolated classloaders. Subsequent calls reuse the handle without retaining unloaded application classloaders.

If the external class isn't yet loaded when the dispatcher is first called, the resolver throws; the `volatile` field stays `null`, so the next call retries. No eager init, no `ExceptionInInitializerError` at extension load.

### Rules

- **Target:** `ElementType.TYPE`, interface only. The processor rejects classes with a compile error.
- **Annotation value:** non-empty fully-qualified class name. Empty string is a compile error.
- **Method return and parameter types:** anything resolvable at build time is fine. Types you can't put on the extension's compile classpath (app-private types, classes that only exist at runtime) must be typed as `Object`.
- **Static methods:** annotate with `@ExternalType.Static` on the interface method — the generated dispatcher calls `findStatic` and uses TCCL for class loading.
- **Default methods and static interface methods:** skipped (they already have bodies).

### Scope limits (v1) — Planned for Future Versions

The following are not yet handled by the processor. Use `ClassLoadingUtil` / `MethodHandleCache` directly as a workaround; all items in the table below are planned for a future `@ExternalType` version:

| Feature | Status | Manual workaround |
|---------|--------|-------------------|
| Field access (read/write) | Planned | `MethodHandleCache.findGetter` / `findSetter` |
| Constructors (`new ExternalType(...)`) | Planned | `MethodHandleCache.findConstructor` |
| `instanceof` / `checkcast` on external types | Planned | `ClassLoadingUtil.load(...)` + `Class.isInstance` |
| Chained `@ExternalType` references | Planned | Manual adapter per level |
| Non-`public` methods | Planned | `MethodHandles.privateLookupIn` (Java 9+) |

The hand-written pattern in the "Impl Sketch" section above works alongside `@ExternalType`-based adapters in the same impl class until these gaps are closed.

## Role Detection & Config

- Detect driver/executor via system properties or presence of marker classes using TCCL.
- `extensions.conf` (examples):

```properties
# Spark
btrace-spark.enabled=true
btrace-spark.role=auto           # auto|driver|executor
# optional: only if the app does not ship required libs
btrace-spark.classpath=/opt/spark/jars

# Hadoop
btrace-hadoop.enabled=false
# btrace-hadoop.classpath=/opt/hadoop/share/hadoop/common
```

## Permissions

- Typical: `REFLECTION`, `THREADS`, `SYSTEM_PROPS`.
- Optional: `CLASSLOADER` if creating a child `URLClassLoader` from configured paths.

## Escape Hatch (last resort)

- If absolutely unavoidable, append a single jar to the system CL:
  - `-Dbtrace.system.appendJar=/abs/path/lib.jar -Dbtrace.trusted=true`
  - Restricted to `BTRACE_HOME` by default; override with `-Dbtrace.allowExternalLibs=true`.

## Hadoop Example (Sketch)

```java
public interface HadoopApi { void onFsOp(Object op); }
public final class HadoopApiImpl implements HadoopApi {
  // Resolve org.apache.hadoop.fs.FileSystem via TCCL and reflectively extract fields
}
```

## Migration Steps

1. Extract minimal API for probes; avoid app types.
2. Move environment-specific logic to impl; resolve app types via object hand-off/TCCL.
3. Add extension config (role, optional classpath hints).
4. Request permissions; add no-op shims when unavailable.
5. Prefer eager load if APIs must be present before probes start.

## Notes

- Keep APIs small and stable; impls can evolve independently.
- Cache MethodHandles for performance; avoid repeated reflective lookups.
- Do not rely on global classpath mutation; it’s discouraged and may be removed.

## Example Projects

- Spark example: `btrace-extensions/examples/btrace-spark`
- Hadoop example: `btrace-extensions/examples/btrace-hadoop`

See also: `docs/examples/README.md` for quick build and configuration snippets.
