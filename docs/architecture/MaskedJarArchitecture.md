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
btrace.jar (~2.9 MB)
├── org/openjdk/btrace/boot/*.class             # Entry point (Loader, MaskedClassLoader, MaskedJarUtils)
├── org/openjdk/btrace/core/*.class             # Bootstrap: core API (~42 classes)
├── org/openjdk/btrace/core/extensions/*.class  # Bootstrap: extension API
├── org/openjdk/btrace/core/types/*.class       # Bootstrap: type definitions
├── org/openjdk/btrace/core/jfr/*.class         # Bootstrap: JFR integration
├── org/openjdk/btrace/runtime/*.class          # Bootstrap: runtime support
├── org/openjdk/btrace/libs/org/slf4j/**        # Bootstrap: relocated SLF4J
├── META-INF/btrace/agent/*.classdata           # Masked: agent classes
├── META-INF/btrace/client/*.classdata          # Masked: client classes
├── META-INF/btrace/shared/*.classdata          # Masked: shared classes (ASM, protocol, etc.)
└── META-INF/MANIFEST.MF
```

**Bootstrap classes** (~112 total): Only the core API, runtime support, and SLF4J logging are stored as regular `.class` files. These are visible to the bootstrap classloader because the manifest declares `Boot-Class-Path: .`.

**Masked classes** (~1600+): Agent, client, and shared classes are stored as `.classdata` files under `META-INF/btrace/`. The JVM's class loading ignores these files entirely. They are loaded on demand by `MaskedClassLoader`.

### Manifest

```
Premain-Class: io.btrace.boot.Loader
Agent-Class: io.btrace.boot.Loader
Main-Class: io.btrace.boot.Loader
Can-Redefine-Classes: true
Can-Retransform-Classes: true
Boot-Class-Path: .
BTrace-Agent-Main: io.btrace.agent.Main
BTrace-Client-Main: io.btrace.client.Main
```

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

### btrace-bootstrap Module

**Location:** `btrace-bootstrap/`

A build-only module that defines which classes belong in the bootstrap section via a `bootIncludes` filter closure. This filter is used by the `btraceJar` task in `btrace-dist/build.gradle` to separate classes into `.class` (bootstrap) vs `.classdata` (masked) during JAR assembly.

**Bootstrap inclusion criteria:**
- `org/openjdk/btrace/core/` — core API (excluding Messages)
- `org/openjdk/btrace/runtime/` — runtime support
- `org/openjdk/btrace/core/extensions/` — extension API
- `org/openjdk/btrace/libs/org/slf4j/` — relocated SLF4J (logging from bootstrap code)

**Explicitly excluded from bootstrap:**
- ASM classes (loaded via shared section)
- JCTools queues (loaded via shared section)
- Communication protocol classes
- Annotation and handler classes

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
