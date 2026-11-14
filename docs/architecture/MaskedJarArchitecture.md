# Masked JAR Architecture

## Overview

BTrace uses a single-JAR distribution (`btrace.jar`) with a **classdata masking** technique to minimize bootstrap classloader pollution. This architecture packs agent, client, and shared classes into one JAR while keeping only essential API classes visible to the bootstrap classloader.

The key insight: files with a `.classdata` extension are invisible to the JVM's built-in classloading, so non-bootstrap classes can coexist in the same JAR without leaking into the bootstrap classloader.

## Problem

Traditional Java agents add their entire JAR to the bootstrap classloader via `Boot-Class-Path: .` in the manifest. For BTrace, this would expose ~1500+ classes (ASM, compiler, instrumentation engine, client) to every class in the JVM. This causes:

- Namespace pollution (classes visible where they shouldn't be)
- Potential conflicts with application dependencies (e.g., different ASM versions)
- Increased memory footprint in the bootstrap classloader

The previous multi-JAR approach (`btrace-agent.jar`, `btrace-boot.jar`, `btrace-client.jar`) solved this by splitting classes across JARs but introduced hardcoded co-location assumptions that broke alternative distribution methods like jbang and Maven repositories.

## Architecture

### JAR Structure

```
btrace.jar (~4.4 MB)
├── io/btrace/boot/*.class                      # Entry point (Loader, MaskedClassLoader, MaskedJarUtils)
├── io/btrace/BTrace.class                      # Bootstrap: flat DSL entry class
├── io/btrace/core/*.class                      # Bootstrap: core API (excl. Messages and comm)
├── io/btrace/core/annotations/*.class          # Bootstrap: annotations
├── io/btrace/core/extensions/*.class           # Bootstrap: extension API (also in the shared section)
├── io/btrace/core/handlers/*.class             # Bootstrap: handler interfaces
├── io/btrace/core/types/*.class                # Bootstrap: type definitions
├── io/btrace/core/jfr/*.class                  # Bootstrap: JFR integration
├── io/btrace/runtime/*.class                   # Bootstrap: selected runtime support classes
├── io/btrace/extension/ExtensionBridge.class   # Bootstrap: extension bridge interface
├── io/btrace/extension/processor/*.class       # Root, tool-only: annotation processor for extension builds
├── io/btrace/libs/org/slf4j/**                 # Bootstrap: relocated SLF4J
├── META-INF/btrace/agent/*.classdata           # Masked: agent classes
├── META-INF/btrace/client/*.classdata          # Masked: client classes
├── META-INF/btrace/shared/*.classdata          # Masked: shared classes (ASM, protocol, etc.)
├── META-INF/services/*                         # PackGenerator and relocated SLF4J providers
└── META-INF/MANIFEST.MF
```

**Bootstrap classes** (~200 total): Only the core API, the extension API, runtime support, the extension annotation processor, and SLF4J logging are stored as regular `.class` files. These are visible to the bootstrap classloader because the manifest declares `Boot-Class-Path: btrace.jar`, which the JVM resolves relative to the agent JAR's own directory. `btrace-dist/src/test/java/io/btrace/dist/BootstrapClosureTest.java` checks the root classes against a fixed list and checks that they reference only other root classes and JDK types the boot loader defines. The annotation processor is the exception: it is a tool-only root class, which javac loads from the processor path and the bootstrap loader never links, so it may reference `java.compiler` types, and no other root class may reference it.

**Masked classes** (~2200+): Agent, client, and shared classes are stored as `.classdata` files under `META-INF/btrace/`. The JVM's class loading ignores these files entirely. They are loaded on demand by `MaskedClassLoader`.

The client section also contains the instrumentation/runtime implementation used by `btracec` to
turn compiled scripts into persisted probe packs. The `io.btrace.core.PackGenerator` service
descriptor is visible from the masked JAR root, so its `io.btrace.instr.InstrPackGenerator`
provider and non-bootstrap dependencies must be loadable by the client masked classloader as well
as by the agent classloader.

### Manifest

```
Premain-Class: io.btrace.boot.Loader
Agent-Class: io.btrace.boot.Loader
Main-Class: io.btrace.boot.Loader
Can-Redefine-Classes: true
Can-Retransform-Classes: true
Boot-Class-Path: btrace.jar
BTrace-Version: <version>
BTrace-Agent-Main: io.btrace.agent.Main
BTrace-Client-Main: io.btrace.client.Main
Add-Exports: jdk.internal.jvmstat/sun.jvmstat.monitor
```

`Add-Exports` lets the client use jvmstat to list local JVMs when started with `java -jar`
(the `bin/btrace` launcher passes `--add-exports` itself). Without it, for example on a `-cp`
launch, the client lists JVMs through the Attach API instead.

`Loader` is the single entry point for all three modes. The actual agent/client main classes are specified as manifest attributes and loaded reflectively via `MaskedClassLoader`.

## Components

### btrace-boot Module

**Location:** `btrace-boot/`

Contains three classes, all loaded by the bootstrap classloader:

#### Loader (`io.btrace.boot.Loader`)

Entry point for all three modes:

| Mode | Entry Method | Invocation |
|------|-------------|------------|
| Load-time agent | `premain(String, Instrumentation)` | `-javaagent:btrace.jar` |
| Dynamic attach | `agentmain(String, Instrumentation)` | `VirtualMachine.loadAgent()` |
| Client CLI | `main(String[])` | `java -jar btrace.jar` |

Each mode creates a `MaskedClassLoader` for the appropriate section (agent or client), loads the main class from the manifest attribute, and invokes it reflectively.

**Agent mode classloader hierarchy:**
```
Bootstrap CL (core API, runtime, Loader)
    └── MaskedClassLoader[agent] (parent=null)
            Loads from: META-INF/btrace/agent/*.classdata
            Fallback:   META-INF/btrace/shared/*.classdata
```

Using `null` as the parent ensures bootstrap-visible classes (like `BTraceRuntimeAccess`) are loaded from the bootstrap classloader rather than the agent's classloader. This is critical because probe classes—defined via the bootstrap classloader—must see the same class instances the agent uses.

**Client mode classloader hierarchy:**
```
Bootstrap CL (core API, runtime, Loader)
    └── System CL
        └── MaskedClassLoader[client] (parent=System CL)
                Loads from: META-INF/btrace/client/*.classdata
                Fallback:   META-INF/btrace/shared/*.classdata
```

#### MaskedClassLoader (`io.btrace.boot.MaskedClassLoader`)

A `URLClassLoader` subclass that loads classes from `.classdata` files. The lookup order for `findClass(name)`:

1. Check section-specific path: `META-INF/btrace/{section}/{class-path}.classdata`
2. Check shared path: `META-INF/btrace/shared/{class-path}.classdata`
3. Throw `ClassNotFoundException` (delegates to parent via standard classloader contract)

Resource loading (`findResource`, `getResourceAsStream`) follows the same pattern: section-specific first, then shared, then the JAR root.

#### MaskedJarUtils (`io.btrace.boot.MaskedJarUtils`)

Utility class for detecting masked JARs. A JAR is identified as masked by the presence of a `META-INF/btrace/shared/` entry. Used by `Client.java` and integration tests to detect whether they are running from a masked JAR or the legacy multi-JAR layout.

### Bootstrap Class Selection

**Location:** `btrace-dist/build.gradle` (the `bootIncludes` filter closure)

The `bootIncludes` filter closure defined in `btrace-dist/build.gradle` determines which classes belong in the bootstrap section. It is used by the `bootstrapJar` and `btraceJar` tasks to separate classes into `.class` (bootstrap) vs `.classdata` (masked) during JAR assembly.

**Bootstrap inclusion criteria:**
- `io/btrace/core/` — core API, including annotations and handlers (excluding `Messages`, `messages.properties`, `comm/`, and `extensions/`)
- `io/btrace/runtime/` — selected runtime support classes only (`BTraceRuntimeAccess`, `LinkingFlag`, `Indy*`, `ExtensionIndy`, `BTraceBootstrap`, `auxiliary/Auxiliary`)
- `io/btrace/BTrace.class` — flat DSL entry class (needed by javac when compiling scripts)
- `io/btrace/extension/ExtensionBridge.class` — extension bridge interface (referenced by `ExtensionIndy`)
- `io/btrace/libs/org/slf4j/` — relocated SLF4J (logging from bootstrap code)

`bootIncludes` itself leaves out `io/btrace/core/extensions/`, but the `btraceJar` task adds those
classes to the root from the `btrace-core` output, because bootstrap code needs them; they are also
in the shared section. The `btraceJar` task also adds `io/btrace/extension/processor/` to the root,
so that external extension builds can use `btrace.jar` as their annotation processor path: javac's processor path loader reads only `.class` entries. The jar ships no `META-INF/services/javax.annotation.processing.Processor` entry, because a javac running in an instrumented JVM would discover the processor through the boot class path and fail on its `java.compiler` types; extension builds name it with `-processor`.

**Explicitly excluded from bootstrap:**
- ASM classes (loaded via shared section)
- JCTools queues (loaded via the agent and client sections)
- Communication protocol classes (`io/btrace/core/comm/`)

## Build Process

The `btrace-dist/build.gradle` `btraceJar` task assembles the masked JAR:

1. **`prepareAgentClassdata`**: Extracts classes from the agent shadow JAR, renames `.class` → `.classdata`, places under `META-INF/btrace/agent/`
2. **`prepareClientClassdata`**: Same for the client shadow JAR, placed under `META-INF/btrace/client/`
3. **`btraceJar`**: Combines bootstrap `.class` files (filtered by `btrace-bootstrap`) with the `.classdata` sections and shared classes into the final JAR

Shared classes (ASM, protocol, etc.) are automatically placed in `META-INF/btrace/shared/` and are accessible to both agent and client classloaders.

## Debugging

Enable debug output with:
```bash
java -Dbtrace.boot.debug=true -jar btrace.jar ...
```

This prints classloader decisions to stderr, prefixed with `[BTrace Boot]`.

## Backward Compatibility

The masked JAR coexists with the legacy multi-JAR distribution. Detection logic in `Client.java` and `RuntimeTest` checks for `btrace.jar` first and falls back to `btrace-agent.jar` if not found. The `--agent-jar` and `--boot-jar` CLI flags allow explicit path overrides for any layout.
