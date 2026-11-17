# BTrace Hadoop Example (Provided-Style Extension)

This example demonstrates a provided-style BTrace extension for Apache Hadoop that:
- Exposes a minimal API on bootstrap (`org.example.btrace.hadoop.api.HadoopApi`).
- Loads implementation in an isolated classloader (no system/boot CL injection).
- Resolves Hadoop classes at runtime using object hand-off and the application’s defining class loader.
- Caches reflective MethodHandles for performance.

## Build

```
./gradlew :btrace-extensions:examples:btrace-hadoop:build
```

The BTrace extension plugin will produce API/impl artifacts. Use with a BTrace distribution that supports extensions.

## Configuration (`extensions.conf`)

The example reads no settings of its own. `extensions.conf` only controls whether it is loaded:

```
# Load only this extension (empty = all discovered extensions)
extensions.enabled=btrace-hadoop-example

# Or keep it from loading
# extensions.disabled=btrace-hadoop-example
```

## Permissions

The plugin's permission scan finds no required permissions for this extension (`btracex inspect`
on the built ZIP shows `Privileged: false`, `Required : []`), so no policy entry is needed. If your
adaptation makes it privileged, allow it by id:

```
# permissions.properties (see PermissionPolicy docs)
allowExtensions=btrace-hadoop-example
```

Avoid `CLASSLOADER` unless you explicitly create child loaders from configured paths.

## Usage Notes

- Provided-style: no bootstrap/system classpath mutation; probe code imports only the API.
- Object hand-off: pass Hadoop objects (`Object`) to API methods; the impl resolves types at runtime.
- Class loading: the impl runs resolution under the defining loader of the handed-off object.
- MethodHandle cache: repeated reflective calls are cached for speed.

If you absolutely must expose a jar on the system classpath temporarily:
- Use the escape hatch (discouraged): `-Dbtrace.system.appendJar=/abs/path/lib.jar` on the target JVM plus the `trusted=true` agent argument (`-javaagent:btrace.jar=trusted=true,...`). The jar must lie inside the BTrace home unless the target JVM also has `-Dbtrace.allowExternalLibs=true`. `-Dbtrace.trusted` has no effect; the agent logs a warning when it sees it.
- Prefer fixing packaging or using an extension-local child loader instead.

## Probe Integration (Conceptual)

- Probes get the API as an injected service (API is on bootstrap). Keep probe signatures generic (`Object` argument types).
- Example (pseudo):

```java
import org.example.btrace.hadoop.api.HadoopApi;
import io.btrace.core.annotations.*;

@BTrace
public class HadoopProbe {
  // The extension bridge links the service; no manual wiring needed
  @Injected private static HadoopApi hadoop;

  @OnMethod(clazz="org.apache.hadoop.fs.FileSystem", method="open")
  public static void onOpen(@Self Object fs, Object path) {
    hadoop.onOpen(fs, path);
  }
}
```

Note: The example is not maintained as part of core BTrace; adapt for your environment.

