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

```
# Enable the example
btrace-hadoop-example.enabled=true

# Optional: if your application does not ship Hadoop libs
# btrace-hadoop-example.classpath=/opt/hadoop/share/hadoop/common
```

## Permissions

This example uses reflection. Ensure your policy grants the permission:

```
# permissions.properties (see PermissionPolicy docs)
grant=REFLECTION,SYSTEM_PROPS
```

Avoid `CLASSLOADER` unless you explicitly create child loaders from configured paths.

## Usage Notes

- Provided-style: no bootstrap/system classpath mutation; probe code imports only the API.
- Object hand-off: pass Hadoop objects (`Object`) to API methods; the impl resolves types at runtime.
- Class loading: the impl runs resolution under the defining loader of the handed-off object.
- MethodHandle cache: repeated reflective calls are cached for speed.

If you absolutely must expose a jar on the system classpath temporarily:
- Use the escape hatch (discouraged): `-Dbtrace.system.appendJar=/abs/path/lib.jar -Dbtrace.trusted=true`.
- Prefer fixing packaging or using an extension-local child loader instead.

## Probe Integration (Conceptual)

- Probes can call the API directly (API is on bootstrap). Keep probe signatures generic (`Object` argument types).
- Example (pseudo):

```java
import org.example.btrace.hadoop.api.HadoopApi;
import org.openjdk.btrace.core.annotations.*;

@BTrace
class HadoopProbe {
  // Using HadoopApi statically via extension linkage (no manual wiring needed)
  @OnMethod(clazz="org.apache.hadoop.fs.FileSystem", method="open")
  public static void onOpen(@Self Object fs, Object path) {
    // HadoopApi.onOpen(fs, path); // conceptual example
  }
}
```

Note: The example is not maintained as part of core BTrace; adapt for your environment.

