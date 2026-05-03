# Fat Agent Plugin Architecture

## Overview

The Fat Agent Plugin (`io.btrace.fat-agent`) creates self-contained agent JARs with embedded extensions for single-JAR deployment scenarios. This eliminates the need for separate extension installation in environments like Spark, Hadoop, or Kubernetes where managing multiple JARs is impractical.

## Problem Statement

Standard BTrace deployment previously required:
1. Agent JAR (`btrace-agent.jar`)
2. Boot JAR (`btrace-boot.jar`)
3. Extension JARs in `$BTRACE_HOME/extensions/`

> **Note:** The masked JAR architecture now consolidates agent and boot into a single `btrace.jar`. The multi-JAR layout above is the legacy approach.

This multi-JAR setup is problematic for:
- **Spark/Hadoop**: Driver and executors need extensions without shared filesystem
- **Kubernetes**: ConfigMaps and init containers add complexity
- **Containers**: Minimal images don't want extra layers

## Solution: Fat Agent JAR

A single JAR containing:
- All agent and boot classes
- Embedded extension API classes (as `.class` files for bootstrap)
- Embedded extension impl classes (as `.classdata` for runtime loading)
- Extension metadata in `META-INF/btrace-extensions/`

## Architecture

### Class Loading Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                    Fat Agent JAR                             │
├─────────────────────────────────────────────────────────────┤
│  Bootstrap Classpath (via Boot-Class-Path manifest)         │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  org/openjdk/btrace/agent/...     (agent classes)     │  │
│  │  org/openjdk/btrace/core/...      (core classes)      │  │
│  │  org/openjdk/btrace/instr/...     (instr classes)     │  │
│  │  org/example/ext/api/...          (extension API)     │  │
│  └───────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│  Runtime-Loaded (via ClassDataLoader)                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  org/example/ext/impl/...classdata (extension impl)   │  │
│  └───────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│  META-INF/btrace-extensions/                                │
│  ├── ext1/extension.properties                              │
│  ├── ext2/extension.properties                              │
│  └── ext3/extension.properties                              │
└─────────────────────────────────────────────────────────────┘
```

### Extension Discovery Flow

```
Agent Startup
     │
     ▼
┌─────────────────────────┐
│ Parse BTRACE_HOME       │
│ (null for embedded)     │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ Read manifest attribute │
│ BTrace-Embedded-        │
│ Extensions: ext1,ext2   │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ For each extension ID:  │
│ - Load extension.props  │
│ - Create descriptor     │
│ - Register services     │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ API classes already on  │
│ bootstrap (as .class)   │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ Impl classes loaded on  │
│ demand via ClassData-   │
│ Loader (from .classdata)│
└─────────────────────────┘
```

### Plugin Task Graph

```
fatAgentJar
├── stageExtensions
│   ├── resolveExtensions()  → ResolvedExtension[]
│   ├── stageApiClasses()    → copy as .class
│   ├── stageImplClasses()   → copy as .classdata
│   └── writeMetadata()      → extension.properties
├── stageProbes (optional)
│   ├── copyCompiledProbes() → META-INF/btrace-probes/
│   └── compileSourceProbes()
└── btraceJar (single masked JAR from btrace-dist)
```

## Implementation Details

### Extension Sources

The plugin supports three extension source types:

```groovy
embedExtensions {
    project(':my-extension')           // ProjectExtensionSource
    maven('io.btrace:ext:1.0')         // MavenExtensionSource
    file('/path/to/ext.zip')           // FileExtensionSource
}
```

Each source resolves to a `ResolvedExtension`:

```groovy
class ResolvedExtension {
    String id
    String version
    File apiJar      // contains API classes
    File implJar     // contains impl classes (shadowed)
    Properties metadata
}
```

### Staging Process

1. **API Classes**: Extracted from API JAR and copied as `.class` files
   - These end up on bootstrap classpath
   - Visible to BTrace scripts and the agent

2. **Impl Classes**: Extracted from impl JAR and renamed to `.classdata`
   - Loaded at runtime by `ClassDataLoader`
   - Isolated from target application classpath

3. **Metadata**: Written to `META-INF/btrace-extensions/{id}/extension.properties`

### Auto-Discovery

When `autoDiscover = true`, the plugin scans subprojects:

```groovy
project.gradle.projectsEvaluated {
    rootProject.subprojects.each { sp ->
        if (sp.plugins.hasPlugin('io.btrace.extension')) {
            extension.addExtensionSource(new ProjectExtensionSource(project, sp.path))
        }
    }
}
```

Filtering via property:
```bash
./gradlew fatAgentJar -PembedExtensions=btrace-metrics,btrace-statsd
```

### ShadowJar Integration

When ShadowJar is available on the classpath, the plugin uses it for:
- Package relocation (avoid classpath conflicts)
- Duplicate handling

```groovy
def jarTaskClass = Jar
try {
    jarTaskClass = Class.forName('com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar')
} catch (ClassNotFoundException e) {
    // Fall back to standard Jar
}
```

## Manifest Attributes

The fat agent JAR manifest includes:

| Attribute | Description |
|-----------|-------------|
| `Premain-Class` | `io.btrace.agent.Main` |
| `Agent-Class` | `io.btrace.agent.Main` |
| `Can-Redefine-Classes` | `true` |
| `Can-Retransform-Classes` | `true` |
| `Boot-Class-Path` | `btrace-agent-fat.jar` (self-reference) |
| `BTrace-Embedded-Extensions` | Comma-separated list of extension IDs |

## Runtime Behavior

### Extension Loading

At agent startup:

1. `Main.initExtensions()` initializes the extension system
2. `ExtensionLoader` creates `EmbeddedExtensionRepository`
3. Repository reads `BTrace-Embedded-Extensions` from manifest
4. For each extension ID, loads `META-INF/btrace-extensions/{id}/extension.properties`
5. Creates `ExtensionDescriptorDTO` with `embedded=true`
6. API classes are already on bootstrap (no loading needed)
7. Impl classes loaded on-demand via `ClassDataLoader`

### ClassDataLoader

The `ClassDataLoader` loads `.classdata` files as classes. It registers as parallel-capable via `ClassLoader.registerAsParallelCapable()` and uses per-class-name locking via `getClassLoadingLock(name)` to allow concurrent loading of different classes while serializing attempts to load the same class:

```java
public class ClassDataLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    public Class<?> findClass(String className) {
        synchronized (getClassLoadingLock(className)) {
            String resourceName = className.replace('.', '/') + ".classdata";
            InputStream is = getResourceAsStream(resourceName);
            byte[] bytes = is.readAllBytes();
            return defineClass(className, bytes, 0, bytes.length);
        }
    }
}
```

## Use Cases

### 1. Spark Deployment

```groovy
btraceFatAgent {
    baseName = 'btrace-spark-agent'
    embedExtensions {
        project(':btrace-extensions:btrace-spark')
        project(':btrace-extensions:btrace-metrics')
    }
}
```

Usage:
```bash
spark-submit --conf spark.driver.extraJavaOptions=-javaagent:btrace-spark-agent.jar ...
```

### 2. Kubernetes with Pre-loaded Extensions

```dockerfile
FROM btrace/btrace:latest AS btrace
FROM openjdk:17

# Copy only the fat agent (no extension installation needed)
COPY --from=btrace /opt/btrace/libs/btrace-agent-fat.jar /opt/btrace/
```

### 3. CI/CD Pipeline

```yaml
steps:
  - name: Build Fat Agent
    run: ./gradlew fatAgentJar -PembedExtensions=btrace-metrics

  - name: Deploy
    run: kubectl cp btrace-agent-fat.jar pod:/opt/
```

## Maven Plugin

For Maven users, the `btrace-maven-plugin` provides equivalent functionality:

```xml
<plugin>
    <groupId>io.btrace</groupId>
    <artifactId>btrace-maven-plugin</artifactId>
    <version>${btrace.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>fat-agent</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <outputName>my-btrace-agent</outputName>
        <extensions>
            <extension>io.btrace:btrace-metrics:${btrace.version}</extension>
            <extension>io.btrace:btrace-statsd:${btrace.version}</extension>
        </extensions>
    </configuration>
</plugin>
```

### Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `btraceVersion` | Plugin version | BTrace version for base agent/boot JARs |
| `extensions` | (none) | Extension coordinates (`groupId:artifactId:version`) |
| `outputName` | `btrace-agent-fat` | Output file name (without `.jar`) |
| `outputDirectory` | `${project.build.directory}` | Output directory |
| `skip` | `false` | Skip execution |

### Implementation

The Maven plugin follows the same staging process as the Gradle plugin:

1. Resolves the `btrace` artifact (single masked JAR) from Maven Central
2. Resolves each extension artifact (API JAR with classifier `api`, impl JAR with classifier `impl`)
3. Stages API classes as `.class` files (bootstrap)
4. Stages impl classes as `.classdata` files (runtime-loaded)
5. Writes extension metadata to `META-INF/btrace-extensions/{id}/`
6. Updates manifest with `BTrace-Embedded-Extensions` attribute

## Limitations

1. **No Hot-Reload**: Embedded extensions cannot be updated without rebuilding the JAR
2. **Size**: Fat JAR is larger than minimal agent
3. **Classpath Conflicts**: Careful relocation needed to avoid conflicts with target app dependencies

## Related Documentation

- [Extension Development Guide](../BTraceExtensionDevelopmentGuide.md)
- [Provided-Style Extensions](provided-style-extensions.md)
- [Migrating from libs/profiles](migrating-from-libs-profiles.md)
