# Provided-Style Extensions for App Integrations

This guide shows how to migrate profile-based integrations (e.g., Spark/Hadoop) to BTrace extensions without mutating the global classpath.

## Principles

- API on bootstrap: expose minimal, stable APIs with simple/value types.
- Impl in isolated CL: load implementation in an extension classloader; no shading of app libs.
- Runtime linking: access application types via object hand-off and TCCL instead of compile-time imports.
- No classpath injection: avoid boot/system CL changes; reserve the escape hatch for exceptional cases only.

## Helper Utilities

- `org.openjdk.btrace.extension.util.ClassLoadingUtil`
  - Loaders: `tccl()`, `definingLoader(Object)`
  - Class loading: `load(String, ClassLoader)`, `load(String, Object)`, `tryLoad(String, ClassLoader)`
  - Context: `withTCCL(ClassLoader, Supplier<T>)`, `withTCCL(ClassLoader, Runnable)`, `withDefiningLoader(Object, Supplier<T>)`
  - Services: `loadService(Class<T>, ClassLoader)`, `loadServices(Class<T>, ClassLoader)`
  - Resources: `getResource(String, ClassLoader)`, `openResource(String, ClassLoader)`
  - Optional child loader: `newChildURLClassLoader(List<Path>, ClassLoader)`, `safeClose(ClassLoader)`

- `org.openjdk.btrace.extension.util.MethodHandleCache`
  - Caches public `MethodHandle`s for reflective adapters.

## API Sketch (Spark example)

```java
// api module (on bootstrap)
package org.example.btrace.spark.api;
public interface SparkApi {
  void onJobStart(Object jobStartEvent);
  void onStageCompleted(Object stageInfo);
}
```

## Impl Sketch

```java
// impl module (extension CL)
package org.example.btrace.spark.impl;
import org.example.btrace.spark.api.SparkApi;
import org.openjdk.btrace.extension.util.ClassLoadingUtil;
import org.openjdk.btrace.extension.util.MethodHandleCache;
import java.lang.invoke.MethodHandle;

public final class SparkApiImpl implements SparkApi {
  private final MethodHandleCache mh = new MethodHandleCache();

  @Override
  public void onJobStart(Object evt) {
    ClassLoadingUtil.withDefiningLoader(
      evt,
      () -> {
        try {
          Class<?> cls = ClassLoadingUtil.load(
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
