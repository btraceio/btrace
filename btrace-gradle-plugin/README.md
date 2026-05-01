# BTrace Gradle Plugins

This module provides two Gradle plugins for the BTrace extension ecosystem:

1. **BTrace Extension Plugin** (`org.openjdk.btrace.extension`) - Build and package BTrace extensions
2. **BTrace Fat Agent Plugin** (`org.openjdk.btrace.fat-agent`) - Build self-contained fat agent JARs with embedded extensions

---

## BTrace Extension Plugin

Build and package BTrace extensions with sane defaults.

### Features
- Scans implementation bytecode to infer minimal required permissions
- Writes extension metadata into the API JAR manifest
- Produces three artifacts: API JAR, Impl JAR (shadowed), and distributable ZIP
- Uses a single authored source tree while preserving the API/impl runtime artifact split
- Auto-registers the `@ExternalType` annotation processor on the main source set (generates typed, lazy-resolution adapters for application types — see `docs/architecture/provided-style-extensions.md`)

### Apply the Plugin

```groovy
plugins {
    id 'org.openjdk.btrace.extension' version "${btraceVersion}"
}

repositories {
    mavenCentral()
    mavenLocal() // if you published locally for testing
}
```

### Project Layout

- `src/main/java`, `src/main/resources`: Single authored source tree
- The plugin derives the exported API closure from declared services and keeps the same runtime artifact split (`*-api.jar` + `*-impl.jar`)

### DSL Configuration

```groovy
btraceExtension {
    id = "com.example.myext"         // required: globally unique extension ID
    name = "My Extension"            // optional
    description = "Does things"      // optional

    // Optional: omit to auto-detect from @ServiceDescriptor annotations
    services = [
        "com.example.myext.api.MyService"
    ]

    additionalExports = [
        // optional extra API types to include in the exported API set
    ]
    excludedExports = [
        // optional exclusions from the computed API export set
    ]

    requiresExtensions = [
        // other extension IDs if you depend on them
    ]

    shadedPackages = [
        // from : to (relocations applied to impl JAR)
        "com.example.dep" : "com.example.myext.shaded.dep"
    ]

    // Permissions
    scanPermissions = true           // default: scan impl JAR + classpath
    requiredPermissions = [
        // "NETWORK", "FILE_READ", "FILE_WRITE", "THREADS", "EXEC", "NATIVE",
        // "REFLECTION", "CLASSLOADER", "SYSTEM_PROPS", "THREAD_INFO", "MEMORY_INFO", "JFR_EVENTS"
    ]

    // Lints
    apiCtorSeverity = 'error'        // 'off' | 'warn' | 'error' (default: 'error')
}
```

### Outputs

- **API JAR**: `build/libs/<name>-<version>-api.jar` with manifest entries:
  - `BTrace-Extension-Id`, `BTrace-Extension-Services`, `BTrace-Extension-Permissions`, etc.
- **Impl JAR**: `build/libs/<name>-<version>-impl.jar` (shadowed, minimized)
- **Distribution ZIP**: `build/distributions/<name>-<version>-extension.zip`

### Tasks

- `buildApiJar`: Builds the API JAR and writes extension metadata into the manifest
- `shadowJar`: Builds the impl JAR from the implementation portion of the extension with relocations
- `packageExtension`: Bundles API + Impl into a ZIP

---

## BTrace Fat Agent Plugin

Build self-contained fat agent JARs with embedded extensions for single-JAR deployment scenarios (Spark, Hadoop, Kubernetes, etc.).

### Features
- Embeds extensions directly into the agent JAR
- Auto-discovers extension projects in multi-project builds
- Supports project references, Maven coordinates, and local files
- API classes as `.class` files (bootstrap), impl as `.classdata` (runtime loaded)
- Integrates with ShadowJar for package relocation

### Apply the Plugin

```groovy
plugins {
    id 'org.openjdk.btrace.fat-agent' version "${btraceVersion}"
}
```

### DSL Configuration

```groovy
btraceFatAgent {
    // Output configuration
    baseName = 'btrace-agent-fat'    // default
    outputDir = file('build/libs')   // default

    // Extension sources (multiple can be combined)
    embedExtensions {
        // Project references (requires multi-project build)
        project(':btrace-spark')
        projects(':btrace-metrics', ':btrace-utils')

        // Maven coordinates
        maven('io.btrace:btrace-kafka-extension:2.3.0')
        maven(group: 'io.btrace', name: 'btrace-flink-extension', version: '2.3.0')

        // Local extension ZIPs or directories
        file('/path/to/extension.zip')
        files('/path/to/extensions/*.zip')
    }

    // Bundled probes (optional)
    bundledProbes {
        from 'src/probes/compiled'     // pre-compiled .class files
        fromSource 'src/probes/java'   // Java sources (compiled by plugin)
        include 'SparkJobTracer', 'SparkStageTracer'  // specific probes only
    }

    // Manifest customization
    manifest {
        attributes(['Custom-Attr': 'value'])
    }

    // Package relocations (requires ShadowJar on classpath)
    relocate 'org.jctools', 'org.openjdk.btrace.libs.agent.org.jctools'
    relocate 'org.objectweb.asm', 'org.openjdk.btrace.libs.org.objectweb.asm'

    // Auto-discovery options
    autoDiscover = true              // find extensions with btrace.extension plugin
    filterProperty = 'embedExtensions'  // use -PembedExtensions=ext1,ext2 to filter
}
```

### Auto-Discovery Mode

When `autoDiscover = true`, the plugin automatically finds all subprojects with the `org.openjdk.btrace.extension` plugin applied:

```groovy
btraceFatAgent {
    autoDiscover = true
    filterProperty = 'embedExtensions'
}
```

Build with specific extensions:
```bash
# Embed all discovered extensions
./gradlew fatAgentJar

# Embed only specific extensions
./gradlew fatAgentJar -PembedExtensions=btrace-metrics,btrace-statsd
```

### Standalone Project Usage

For projects outside the BTrace monorepo:

```groovy
plugins {
    id 'org.openjdk.btrace.fat-agent' version '2.3.0'
}

btraceFatAgent {
    baseName = 'my-custom-agent'

    // Reference base BTrace JAR (required for standalone builds)
    agentJarTask = 'btraceJar'  // or provide path

    embedExtensions {
        maven('io.btrace:btrace-metrics:2.3.0')
        maven('io.btrace:btrace-statsd:2.3.0')
        file('libs/my-custom-extension.zip')
    }
}
```

### Tasks

- `stageExtensions`: Resolves and stages extension content (API as `.class`, impl as `.classdata`)
- `stageProbes`: Stages bundled probes to `META-INF/btrace-probes/`
- `fatAgentJar`: Creates the fat agent JAR with all embedded content

### Output Structure

The fat agent JAR contains:
```
btrace-agent-fat.jar
├── META-INF/
│   ├── MANIFEST.MF
│   │   ├── Premain-Class: org.openjdk.btrace.agent.Main
│   │   ├── Agent-Class: org.openjdk.btrace.agent.Main
│   │   ├── Boot-Class-Path: btrace-agent-fat.jar
│   │   └── BTrace-Embedded-Extensions: ext1,ext2,ext3
│   └── btrace-extensions/
│       ├── ext1/
│       │   └── extension.properties
│       ├── ext2/
│       │   └── extension.properties
│       └── ext3/
│           └── extension.properties
├── org/openjdk/btrace/...          # Agent + boot classes
├── org/example/ext/api/...         # Extension API classes (.class)
└── org/example/ext/impl/...        # Extension impl classes (.classdata)
```

### Using the Fat Agent

```bash
# Attach to running JVM
java -javaagent:/path/to/btrace-agent-fat.jar <your-app>

# With options
java -javaagent:/path/to/btrace-agent-fat.jar=debug=true,port=2021 <your-app>
```

The embedded extensions are automatically discovered and loaded at agent startup.

---

## Local Development

### Publish Plugins Locally

```bash
./gradlew :btrace-gradle-plugin:publishToMavenLocal
```

Then in your project, add `mavenLocal()` to repositories or pluginManagement.

### Testing Fat Agent Build

```bash
# Build fat agent with all extensions
./gradlew :btrace-dist:fatAgentJar

# Build with specific extensions only
./gradlew :btrace-dist:fatAgentJar -PembedExtensions=btrace-metrics

# Verify output
jar -tf btrace-dist/build/resources/main/v*/libs/btrace-agent-fat.jar | grep btrace-extensions
```

---

## Tips

### Extension Plugin
- Keep API small and stable; only types used by BTrace scripts belong in API
- Put all runtime dependencies into `impl` and shade them
- If permission scanning is too conservative, add `requiredPermissions` explicitly

### Fat Agent Plugin
- Use auto-discovery for monorepo setups
- Use Maven coordinates for external/published extensions
- Test the fat agent in isolation before production deployment
- Package relocations help avoid classpath conflicts in target JVMs

---

## BTrace Maven Plugin

For Maven users, a Maven plugin is also available to build fat agent JARs.

### Usage

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.openjdk.btrace</groupId>
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
    </plugins>
</build>
```

### Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `btraceVersion` | Plugin version | BTrace version to use for base agent/boot JARs |
| `extensions` | (none) | List of extension coordinates (`groupId:artifactId:version`) |
| `outputName` | `btrace-agent-fat` | Output file name (without `.jar`) |
| `outputDirectory` | `${project.build.directory}` | Output directory |
| `skip` | `false` | Skip execution |

### Build

```bash
mvn package
```

The fat agent JAR is created at `target/${outputName}.jar`.

See the main [README](../README.md) and [Getting Started Guide](../docs/GettingStarted.md) for usage instructions.
