# BTrace Spark Example (Provided-Style Extension)

This example demonstrates a provided-style BTrace extension for Apache Spark that:
- Exposes a minimal API on bootstrap (`org.example.btrace.spark.api.SparkApi`).
- Loads implementation in an isolated classloader (no system/boot CL injection).
- Resolves Spark classes at runtime using object hand-off and the application’s defining class loader.
- Caches reflective MethodHandles for performance.

## Build

```
./gradlew :btrace-extensions:examples:btrace-spark:build
```

The BTrace extension plugin will produce API/impl artifacts. Use with a BTrace distribution that supports extensions.

## Configuration (`extensions.conf`)

The example reads no settings of its own. `extensions.conf` only controls whether it is loaded:

```
# Load only this extension (empty = all discovered extensions)
extensions.enabled=btrace-spark-example

# Or keep it from loading
# extensions.disabled=btrace-spark-example
```

## Permissions

The plugin's permission scan marks this extension as requiring `CLASSLOADER`, a privileged
permission (`btracex inspect` on the built ZIP shows `Required : [CLASSLOADER]`), so the
target JVM's policy must allow it:

```
# permissions.properties (see PermissionPolicy docs)
allowExtensions=btrace-spark-example
```

## Usage Notes

- Provided-style: no bootstrap/system classpath mutation; probe code imports only the API.
- Object hand-off: pass Spark event objects (`Object`) to API methods; the impl resolves types at runtime.
- Class loading: the impl runs resolution under the defining loader of the handed-off object.
- MethodHandle cache: repeated reflective calls are cached for speed.

If you absolutely must expose a jar on the system classpath temporarily:
- Use the escape hatch (discouraged): `-Dbtrace.system.appendJar=/abs/path/lib.jar` on the target JVM plus the `trusted=true` agent argument (`-javaagent:btrace.jar=trusted=true,...`). The jar must lie inside the BTrace home unless the target JVM also has `-Dbtrace.allowExternalLibs=true`. `-Dbtrace.trusted` has no effect; the agent logs a warning when it sees it.
- Prefer fixing packaging or using an extension-local child loader instead.

## Probe Integration (Conceptual)

- Probes get the API as an injected service (API is on bootstrap). Keep probe signatures generic (`Object` argument types).
- Example (pseudo):

```java
import org.example.btrace.spark.api.SparkApi;
import io.btrace.core.annotations.*;

@BTrace
public class SparkProbe {
  // The extension bridge links the service; no manual wiring needed
  @Injected private static SparkApi spark;

  @OnMethod(clazz="org.apache.spark.scheduler.DAGScheduler", method="handleJobStart")
  public static void onJobStart(@Self Object self, Object jobStart) {
    spark.onJobStart(jobStart);
  }
}
```

`SparkApi` is declared only through `btraceExtension.services` and carries no `@ServiceDescriptor`;
`btracec` rejects the `@Injected` field above with `Injected type is not declared as an extension
service` until the interface is annotated (as `HadoopApi` in the Hadoop example is).

Note: The example is not maintained as part of core BTrace; adapt for your environment.

