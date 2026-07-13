# BTrace Gradle Plugins

This module provides two Gradle plugins for the BTrace extension ecosystem:

1. **BTrace Extension Plugin** (`io.btrace.extension`) - Build and package BTrace extensions
2. **BTrace Fat Agent Plugin** (`io.btrace.fat-agent`) - Build self-contained fat agent JARs with embedded extensions

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
    id 'io.btrace.extension' version "${btraceVersion}"
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
    id 'io.btrace.fat-agent' version "${btraceVersion}"
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
        maven('io.btrace:btrace-kafka-extension:3.0.0')
        maven(group: 'io.btrace', name: 'btrace-flink-extension', version: '3.0.0')

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
    relocate 'org.jctools', 'io.btrace.libs.agent.org.jctools'
    relocate 'org.objectweb.asm', 'io.btrace.libs.org.objectweb.asm'

    // Auto-discovery options
    autoDiscover = true              // find extensions with btrace.extension plugin
    filterProperty = 'embedExtensions'  // use -PembedExtensions=ext1,ext2 to filter
}
```

### Zero-Config Probe Auto-Selection (Configurator)

Extensions can declare an `ExtensionConfigurator` class that the agent calls at
startup to decide which bundled probes to activate automatically — without the
operator passing `probes=` on the command line.

**Extension `extension.properties`:**
```properties
id=btrace-spark
probes=SparkJobTracer,SparkStageTracer,SparkExecutorTracer
configurator=org.example.spark.SparkConfigurator
```

**Configurator class (in the extension's impl artifact):**
```java
public final class SparkConfigurator implements ExtensionConfigurator {
    @Override
    public ProbeConfiguration configure(RuntimeEnvironment env, Map<String, String> args) {
        ProbeConfiguration config = new ProbeConfiguration();
        if (env.hasClass("org.apache.spark.SparkContext")) {
            config.enable("SparkJobTracer", "SparkStageTracer");
        } else if (env.hasClass("org.apache.spark.executor.Executor")) {
            config.enable("SparkExecutorTracer");
        }
        config.setOutput(args.getOrDefault("output", "jfr"));
        return config;
    }
}
```

**Operator usage** — attach the fat agent; no `probes=` needed:
```bash
java -javaagent:my-btrace-agent-fat.jar MyApp
```

If `probes=` is supplied by the operator it takes priority and the configurator
is skipped entirely:
```bash
java -javaagent:my-btrace-agent-fat.jar=probes=SparkJobTracer MyApp
```

See [BTraceExtensionDevelopmentGuide.md](../docs/BTraceExtensionDevelopmentGuide.md)
for the full configurator API reference.

### Auto-Discovery Mode

When `autoDiscover = true`, the plugin automatically finds all subprojects with the `io.btrace.extension` plugin applied:

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
    id 'io.btrace.fat-agent' version '3.0.0'
}

btraceFatAgent {
    baseName = 'my-custom-agent'

    // Reference base BTrace JAR (required for standalone builds)
    agentJarTask = 'btraceJar'  // or provide path

    embedExtensions {
        maven('io.btrace:btrace-metrics:3.0.0')
        maven('io.btrace:btrace-statsd:3.0.0')
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
│   │   ├── Premain-Class: io.btrace.agent.Main
│   │   ├── Agent-Class: io.btrace.agent.Main
│   │   ├── Boot-Class-Path: btrace-agent-fat.jar
│   │   └── BTrace-Embedded-Extensions: ext1,ext2,ext3
│   └── btrace-extensions/
│       ├── ext1/
│       │   └── extension.properties
│       ├── ext2/
│       │   └── extension.properties
│       └── ext3/
│           └── extension.properties
├── io/btrace/...                   # Agent + boot classes
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

The unpublished in-repository Maven `fat-agent` module was removed for 3.0.0. It targeted pre-3.0
artifacts and could create an agent whose embedded implementation classes were not loadable. Use
the Gradle plugin documented above. The external
[`btrace-maven`](https://github.com/btraceio/btrace-maven) project continues to support script
compilation.

See the main [README](../README.md) and [Getting Started Guide](../docs/GettingStarted.md) for usage instructions.
