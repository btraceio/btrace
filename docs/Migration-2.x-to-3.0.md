# Migrating from BTrace 2.x to 3.0

Good news first: for most users, migrating to BTrace 3.0 requires **no action at all**. The agent transparently handles old compiled probes, the compiler handles imports for you, and old and new clients/agents keep talking to each other. The few things that do require a change are mechanical.

## TL;DR

| What you have | What you must do |
|---|---|
| Compiled/persisted probes (`.class`) | **Nothing** — the agent auto-migrates them at load time |
| BTrace script sources (`.java`) | **Nothing** for most scripts — or a one-line rename (see below) |
| Mixed 2.x/3.0 client and agent | **Nothing** — the wire protocol auto-negotiates |
| Maven/Gradle dependencies | Update coordinates to `io.btrace:btrace` |
| Launch scripts referencing multiple BTrace jars | Point to the single `btrace.jar` |
| `libs=` / profiles agent options | Migrate to extensions (deprecated but still working) |
| Target JVMs on Java 8–16 | **Nothing** — deprecated, but fully supported throughout 3.x |

## Compiled and Persisted Probes: No Action Needed

BTrace 3.0 moved from the `org.openjdk.btrace` package namespace to `io.btrace`. Probes you compiled with BTrace 2.x (or persisted probe `.class` files) still reference the old namespace — and that is fine.

When the agent loads a persisted probe, it scans the class bytes for legacy `org/openjdk/btrace/` references and, only if any are found, rewrites every class, method, field, descriptor, and annotation reference to `io/btrace/` on the fly. Probes with no legacy references pass through untouched. The same loading step also upgrades even older probes compiled against the pre-2.0 `com/sun/btrace` namespace. The migration is completely transparent: no recompilation, no flags, no changes to your deployment.

## Script Sources: Mechanical Rename (Often Just Delete Imports)

If you keep BTrace scripts as `.java` sources, the only change is the package prefix:

```
org.openjdk.btrace  →  io.btrace
```

This covers imports and fully-qualified references alike, e.g. `org.openjdk.btrace.core.BTraceUtils` becomes `io.btrace.core.BTraceUtils`, and `org.openjdk.btrace.core.annotations.OnMethod` becomes `io.btrace.core.annotations.OnMethod`.

It gets simpler still: the 3.0 compiler automatically injects

```java
import static io.btrace.BTrace.*;
import io.btrace.core.annotations.*;
```

into every script that does not already import the BTrace DSL (`io.btrace.BTrace` or `io.btrace.core.BTraceUtils`) and the annotations package. So for most scripts you can simply **drop the BTrace imports entirely** — the annotations and the flat DSL (`println(...)`, `print(...)`, ...) are available out of the box.

For batch migration, a helper script is included:

```bash
# Rewrites org.openjdk.btrace → io.btrace in place, keeping .bak backups
scripts/migrate-btrace-script.sh MyScript.java OtherScript.java

# Preview changes without touching anything
scripts/migrate-btrace-script.sh --dry-run MyScript.java

# Recurse into a directory of scripts
scripts/migrate-btrace-script.sh -r scripts-dir/
```

## Maven and Gradle Coordinates

The group id changed from `org.openjdk.btrace` to `io.btrace`, and BTrace now publishes a single artifact containing the agent, client, and compiler:

```xml
<dependency>
    <groupId>io.btrace</groupId>
    <artifactId>btrace</artifactId>
    <version>3.0.0</version>
</dependency>
```

Note that the `btrace-instr` artifact no longer exists — the instrumentation engine was merged into the agent, so there is no separate module (or jar) to depend on.

## Packaging: One `btrace.jar`

The 2.x multi-jar layout (`btrace-agent.jar`, `btrace-boot.jar`, `btrace-client.jar`) is gone. BTrace 3.0 ships a single, self-contained `btrace.jar` that serves as the agent, the client, and the launcher — its manifest declares `io.btrace.boot.Loader` as the `Premain-Class`, `Agent-Class`, and `Main-Class` all at once.

Internally the jar uses *classdata masking*: only a small core API is visible to the bootstrap classloader, while the rest (ASM, compiler, client, instrumentation engine) stays invisible to the target application. See [Masked JAR Architecture](architecture/MaskedJarArchitecture.md) for details. If your launch scripts reference the old jar names, update them to point at `btrace.jar`.

## `libs` and Profiles: Removed in Favor of Extensions

The `libs=<profile>` agent option has been removed. The agent logs an error naming the profile and loads nothing, so classes that used to reach probes through `btrace-libs/<profile>/` no longer resolve — typically surfacing as a probe failing on a type it previously saw. Extensions are the replacement — they provide isolation, permissions, and per-extension enable/disable instead of mutating the global classpath. See [Migrating from libs/profiles to Extensions](architecture/migrating-from-libs-profiles.md) for a step-by-step guide.

## Wire Protocol: Mixed Versions Keep Working

BTrace 3.0 defaults to the binary V2 wire protocol (faster, 2–5x smaller payloads), but every connection starts with an automatic negotiation: the receiving side inspects the first bytes of the stream for the V2 magic prefix (`BTR2`) and falls back to the V1 Java-serialization protocol when it is absent. A 2.x client can therefore talk to a 3.0 agent and vice versa — no configuration required.

## License

BTrace 3.0 is licensed under the **Apache License, Version 2.0** (previously GPLv2 with the Classpath Exception).

## Java Support Policy

BTrace 3.0 runs on Java 8–25+. Running BTrace against a JVM older than Java 17 is deprecated: it continues to work throughout 3.x but emits a deprecation warning. Support for Java < 17 will be removed in the next major release (4.0).

The warning is printed once per target JVM when the agent starts:

```
[BTrace] WARNING: This JVM is Java <N>. Running BTrace on Java versions older than 17 is deprecated and support will be removed in the next major release. Please upgrade to Java 17 or newer. Suppress this warning with -Dbtrace.suppressJavaDeprecationWarning=true.
```

If the warning is noise in your environment, suppress it by setting the system property `btrace.suppressJavaDeprecationWarning=true` on the target JVM.
