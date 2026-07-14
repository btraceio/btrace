# Migrating from libs/profiles to Extensions

This guide helps you move from `btrace-libs/<profile>` to extension-based integrations.

## Why Migrate

- Security & isolation: extensions use API on bootstrap and isolated impls; libs/profiles mutate global classpaths.
- Operability: discovery, enable/disable, permissions, diagnostics.
- Maintainability: versioning and conflict handling.

## Deprecation Timeline

- `libs`/profiles are deprecated now. Planned removal: N+2 minor releases.
  - Runtime warning is emitted when `libs` is used.
  - The new escape hatch is `-Dbtrace.system.appendJar=/abs/path/lib.jar` (trusted-only, discouraged).

## Migration Steps

1. Inventory usage:
   - Identify profile(s) used (e.g., `spark-driver`, `spark-executor`, `hadoop`).
   - List the APIs probes rely on; isolate to a minimal contract.

2. Create an extension:
   - API module: minimal interfaces/DTOs (no app types), packaged to bootstrap.
   - Impl module: reflective adapters; declare app deps as compileOnly/provided.

3. Replace profile dependency:
   - Remove `libs=<profile>` from agent args.
   - Enable the extension in `extensions.conf`.

4. Replace classpath assumptions:
   - Update probes to pass app objects to API methods (object hand-off) instead of importing app types.
   - In impl, resolve types via TCCL/defining loader.

5. Configure runtime:
   - Add role/config keys to `extensions.conf` (e.g., role=driver|executor; optional classpath hint if the app doesn’t ship libs).
   - Request permissions (REFLECTION, THREADS, SYSTEM_PROPS; CLASSLOADER only if needed).

6. Validate:
   - Launch/attach runs; verify extension loads and links lazily/eagerly as required.
   - Confirm failure handling (no-op shims) and logs.

## Escape Hatch (Optional)

If immediate migration is not feasible and the app must see a jar on the system classpath:

```
-Dbtrace.system.appendJar=/abs/path/lib.jar -Dbtrace.trusted=true
```

- Restricted to `BTRACE_HOME` unless `-Dbtrace.allowExternalLibs=true`.
- One jar only; discouraged; subject to removal.

## Fat Agent Deployment

For Spark/Hadoop/Kubernetes environments where managing separate extension JARs is impractical, use fat agent builds to embed extensions directly.

### Gradle Plugin

```groovy
plugins {
    id 'io.btrace.fat-agent'
}

btraceFatAgent {
    baseName = 'my-btrace-agent'
    embedExtensions {
        project(':my-spark-extension')
        maven('io.btrace:btrace-metrics:3.0.0')
    }
}
```

Build: `./gradlew fatAgentJar`

### Maven Plugin

The unpublished Maven fat-agent module was removed for 3.0.0. Migrate fat-agent builds to the
Gradle configuration above; the external Maven plugin remains available for script compilation,
not embedded-extension packaging.

### Usage

```bash
# Spark
spark-submit --conf spark.driver.extraJavaOptions=-javaagent:my-btrace-agent.jar ...

# Kubernetes
java -javaagent:/opt/btrace/my-btrace-agent.jar MyApp
```

See [Fat Agent Plugin Architecture](fat-agent-plugin.md) for details.

## Examples & Templates

- See provided-style extension guide: `docs/architecture/provided-style-extensions.md` for Spark/Hadoop templates and `extensions.conf` snippets.
