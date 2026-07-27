# Migrating from libs/profiles to Extensions

This guide helps you move from `btrace-libs/<profile>` to extension-based integrations.

## Why Migrate

- Security & isolation: extensions use API on bootstrap and isolated impls; libs/profiles mutate global classpaths.
- Operability: discovery, enable/disable, permissions, diagnostics.
- Maintainability: versioning and conflict handling.

## Status

`libs`/profiles are **removed**. Passing `libs=<profile>` logs an error naming the profile and
loads nothing; the jars under `btrace-libs/<profile>/` are ignored. Migrate to an extension, or
use the escape hatch below as a stopgap.

If you are arriving here because custom classes stopped resolving after upgrading, that is the
expected symptom: the agent starts normally and probes fail later on the missing types.

## Migration Steps

1. Inventory usage:
   - Identify profile(s) used (e.g., `spark-driver`, `spark-executor`, `hadoop`).
   - List the APIs probes rely on; isolate to a minimal contract.

2. Create an extension:
   - One Gradle project with a single `src/main/java`, applying `io.btrace.extension`. The plugin
     partitions it into API and implementation artifacts from the declared services plus any
     `additionalExports`/`excludedExports` — there is no separate API module to create.
   - Keep application types out of the API surface: minimal interfaces and DTOs only.
   - Declare application dependencies with `implCompileOnly` so they stay off the API artifact.

3. Replace profile dependency:
   - Remove `libs=<profile>` from agent args.
   - Enable the extension in `extensions.conf` (`extensions.enabled`, `extensions.disabled`,
     `extensions.autoload`).

4. Replace classpath assumptions:
   - Update probes to pass app objects to API methods (object hand-off) instead of importing app types.
   - In impl, resolve types via TCCL/defining loader.
   - Recompile the probes against the new API artifact.

5. Configure runtime:
   - Add role/config keys to `extensions.conf` (e.g., role=driver|executor; optional classpath hint if the app doesn’t ship libs).
   - The extension plugin scans the implementation and its dependencies and writes the merged
     permission set into the API manifest, so permissions are usually not declared by hand.
     Review what it wrote: a single transitive dependency can make the whole extension privileged.
   - Permission *grants* are a separate, operator-side file — `permissions.properties`
     (`allowExtensions`, `denyExtensions`, `allowPrivileged`), not `extensions.conf`.

6. Validate:
   - Launch/attach runs; verify extension loads and links lazily/eagerly as required.
   - Injection throws by default. Marking an injection optional, or selecting shim mode, turns a
     failed link into a no-op returning defaults — convenient in production, and the quickest way
     to make an unfinished migration look complete.
   - Use `btrace -le <PID>` to see why an extension failed to link.

## Escape Hatch (Optional)

If immediate migration is not feasible and the app must see a jar on the system classpath:

```
-Dbtrace.system.appendJar=/abs/path/lib.jar -javaagent:btrace.jar=trusted=true
```

- `trusted` is an **agent argument**, not a system property; `-Dbtrace.trusted=true` has no effect
  here.
- Restricted to `BTRACE_HOME` unless `-Dbtrace.allowExternalLibs=true`. When `BTRACE_HOME` cannot
  be determined the jar is appended anyway and a warning is logged.
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
        file('/path/to/btrace-metrics-3.0.0-extension.zip')
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
