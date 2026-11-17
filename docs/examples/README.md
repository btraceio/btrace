# Example Extensions (Provided-Style)

These examples demonstrate how to build provided-style extensions that avoid mutating the JVM classpath and instead use object hand-off + context class loaders for runtime linking.

- Spark example: `btrace-extensions/examples/btrace-spark`
- Hadoop example: `btrace-extensions/examples/btrace-hadoop`

Build (from repo root):

```
./gradlew :btrace-extensions:examples:btrace-spark:build
./gradlew :btrace-extensions:examples:btrace-hadoop:build
```

Both example modules use the default extension layout (`src/main`) while still producing the standard BTrace extension artifacts (`*-api.jar`, `*-impl.jar`, `*-extension.zip`).

Installed extensions are enabled by default; the examples read no settings of their own. To
restrict loading to them in `extensions.conf`:

```
extensions.enabled=btrace-spark-example,btrace-hadoop-example
```

Permissions: the plugin's permission scan gives the Spark example `CLASSLOADER`, which is
privileged (the Hadoop example scans to no permissions), so allow it to link on the
agent side (agent argument or `~/.btrace/permissions.properties`):

```
allowExtensions=btrace-spark-example,btrace-hadoop-example
```

Notes
- These are templates only; they are not maintained as core modules.
- Prefer packaging application libraries with the application; avoid external classpath hints when possible.
- For rare, short-term needs, the agent supports a single-jar escape hatch (discouraged):
  - `-Dbtrace.system.appendJar=/abs/path/lib.jar` on the target JVM plus the `trusted=true` agent argument (`-Dbtrace.trusted` has no effect); the jar must lie inside the BTrace home unless `-Dbtrace.allowExternalLibs=true` is also set
