# Example Extensions (Provided-Style)

These examples demonstrate how to build provided-style extensions that avoid mutating the JVM classpath and instead use object hand-off + context class loaders for runtime linking.

- Spark example: `btrace-extensions/examples/btrace-spark`
- Hadoop example: `btrace-extensions/examples/btrace-hadoop`

Build (from repo root):

```
./gradlew :btrace-extensions:examples:btrace-spark:build
./gradlew :btrace-extensions:examples:btrace-hadoop:build
```

Enable in `extensions.conf` (examples):

```
# Spark example
btrace-spark-example.enabled=true
# Optional role detection (example-specific key)
btrace-spark-example.role=auto           # auto|driver|executor
# Optional external classpath hint (prefer not needed)
# btrace-spark-example.classpath=/opt/spark/jars

# Hadoop example
btrace-hadoop-example.enabled=true
# Optional external classpath hint (prefer not needed)
# btrace-hadoop-example.classpath=/opt/hadoop/share/hadoop/common
```

Permissions (examples use reflection):

```
grant=REFLECTION,SYSTEM_PROPS
```

Notes
- These are templates only; they are not maintained as core modules.
- Prefer packaging application libraries with the application; avoid external classpath hints when possible.
- For rare, short-term needs, the agent supports a single-jar escape hatch (discouraged):
  - `-Dbtrace.system.appendJar=/abs/path/lib.jar -Dbtrace.trusted=true`

