# Fat Agent Example

This example shows how to create a **single deployable BTrace agent JAR** that
bundles extensions and probes. No `BTRACE_HOME` or separate extension
directories are needed at deployment time.

## Quick Start

```bash
# 1. Build the base BTrace distribution first
./gradlew :btrace-dist:btraceJar

# 2. Build the fat agent JAR
./gradlew :btrace-examples:fat-agent-example:fatAgentJar

# 3. Use it
java -javaagent:build/libs/my-app-agent.jar=probes=MyTracer,stdout=true -jar my-app.jar
```

## How It Works

The `org.openjdk.btrace.fat-agent` plugin:

1. Takes a base `btrace.jar` (the masked JAR with agent, client, and shared
   classes)
2. Extracts extension ZIPs, converts impl classes to `.classdata`, and writes
   `extension.properties` metadata
3. Places everything under `META-INF/btrace-extensions/` in the output JAR
4. Adds extension API classes to the bootstrap section (regular `.class` files)
5. Updates the `BTrace-Embedded-Extensions` manifest attribute

## Project Structure

```
fat-agent-example/
├── build.gradle          # Plugin configuration
├── src/
│   └── probes/           # Pre-compiled probe .class files go here
└── README.md
```

## Plugin DSL

```groovy
plugins {
    id 'org.openjdk.btrace.fat-agent'
}

btraceFatAgent {
    // Base agent JAR (required)
    agentJar = file('libs/btrace.jar')

    // Output name (optional, defaults to "{project}-agent")
    outputName = 'my-app-agent'

    // Extensions to embed
    extensions {
        project ':my-extension'                          // local project
        file 'libs/custom-extension.zip'                 // local file
        maven 'org.openjdk.btrace:btrace-metrics:2.3.0:extension@zip'  // Maven
    }

    // Pre-compiled probes
    probes {
        from 'src/probes'       // directory of .class files
        from 'build/probes'     // another directory
    }
}
```

## Creating Extension Projects

Use the `org.openjdk.btrace.extension` plugin to create extensions:

```groovy
// my-extension/build.gradle
plugins {
    id 'org.openjdk.btrace.extension'
}

btraceExtension {
    id = 'my-extension'
    services = ['com.example.MyService']
}
```

Then reference it in the fat agent: `extensions { project ':my-extension' }`

## Agent Arguments

When using the fat agent JAR, pass arguments via the `-javaagent` string:

| Argument | Description |
|----------|-------------|
| `probes=A,B` | Comma-separated probe names to activate |
| `output=stdout` | Route probe output to stdout |
| `output=file` | Route probe output to files (default template) |
| `stdout=true` | Shorthand for `output=stdout` |
| `script=X.class` | Load a probe script (standard BTrace) |
