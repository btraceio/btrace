# Implementation Progress: Single JAR with .classdata Masking

**Date:** 2026-02-08
**Status:** Complete - Bootstrap optimization done, integration tests updated

---

## What Was Implemented

### 1. New Module: btrace-boot
**Location:** `btrace-boot/`

Created new module with:
- `build.gradle` - Simple Java library build
- `src/main/java/org/openjdk/btrace/boot/Loader.java` - Entry point and MaskedClassLoader

**Loader.java features:**
- `premain(String args, Instrumentation inst)` - Agent load-time entry
- `agentmain(String args, Instrumentation inst)` - Dynamic attach entry
- `main(String[] args)` - Client CLI entry
- `MaskedClassLoader` inner class - Loads `.classdata` files from `META-INF/btrace/{agent,client}/`
- Reads main class names from manifest attributes (`BTrace-Agent-Main`, `BTrace-Client-Main`)
- Debug mode via `-Dbtrace.boot.debug=true`

### 2. Build Changes: btrace-dist/build.gradle

**New tasks:**
- `btraceJar` - Creates the masked JAR structure
- `prepareAgentClassdata` - Extracts agent classes from agentJar and renames to `.classdata`
- `prepareClientClassdata` - Extracts client classes from clientJar and renames to `.classdata`

**Modified tasks:**
- `uberJar` - Now has classifier 'uber' (outputs `btrace-uber.jar`)
- `clientJar` - Changed SLF4J/ASM relocation to match bootstrap (fixes type mismatch)

**Publishing:**
- Updated to publish `btraceJar` as the primary artifact

### 3. Current JAR Structure
```
btrace.jar (~2.9MB)
├── org/openjdk/btrace/boot/*.class             # Entry point (4 classes)
├── org/openjdk/btrace/core/*.class             # Bootstrap: core API (42 classes)
├── org/openjdk/btrace/core/extensions/*.class  # Bootstrap: extensions (9 classes)
├── org/openjdk/btrace/core/types/*.class       # Bootstrap: types (5 classes)
├── org/openjdk/btrace/core/jfr/*.class         # Bootstrap: JFR (5 classes)
├── org/openjdk/btrace/runtime/*.class          # Bootstrap: runtime (3 classes)
├── org/openjdk/btrace/libs/org/slf4j/**        # Bootstrap: relocated SLF4J (44 classes)
├── META-INF/btrace/agent/*.classdata           # Masked agent classes (655)
├── META-INF/btrace/client/*.classdata          # Masked client classes (779)
├── META-INF/btrace/shared/*.classdata          # Shared classes: ASM, comm (197)
└── META-INF/MANIFEST.MF

Total bootstrap: 112 classes (target was 200-300)
```

**Note:** ASM is correctly in the shared section (loaded by MaskedClassLoader) since bootstrap classes don't depend on it.

**Manifest attributes:**
```
Premain-Class: org.openjdk.btrace.boot.Loader
Agent-Class: org.openjdk.btrace.boot.Loader
Main-Class: org.openjdk.btrace.boot.Loader
Can-Redefine-Classes: true
Can-Retransform-Classes: true
Boot-Class-Path: .
BTrace-Version: 3.0.0-SNAPSHOT
BTrace-Agent-Main: org.openjdk.btrace.agent.Main
BTrace-Client-Main: org.openjdk.btrace.client.Main
```

---

## What Works

1. **Client mode** - `java -jar btrace.jar --version` works
2. **Usage help** - `java -jar btrace.jar` shows help
3. **Build** - `./gradlew :btrace-dist:btraceJar` succeeds
4. **JAR structure** - Correct separation of .class and .classdata files

---

## Resolved: Bootstrap Class Count Optimized

**Current state:** 112 classes in bootstrap (target was 200-300) ✅

**Bootstrap class breakdown:**
```
42 org/openjdk/btrace/core
16 org/openjdk/btrace/libs/org/slf4j/helpers
10 org/openjdk/btrace/libs/org/slf4j/impl
 9 org/openjdk/btrace/libs/org/slf4j
 9 org/openjdk/btrace/core/extensions
 5 org/openjdk/btrace/libs/org/slf4j/event
 5 org/openjdk/btrace/core/types
 5 org/openjdk/btrace/core/jfr
 4 org/openjdk/btrace/libs/org/slf4j/spi
 4 org/openjdk/btrace/boot
 3 org/openjdk/btrace/runtime
```

**Key optimizations made:**
- ASM moved to shared classdata (loaded by MaskedClassLoader, not bootstrap)
- JCTools queues excluded from bootstrap
- Only essential core classes in bootstrap
- comm/annotations/handlers in shared section

---

## Completed Steps

### 1. Bootstrap Class Optimization ✅
The btrace-bootstrap module now correctly filters classes:
- Only essential core/runtime/extension classes in bootstrap
- ASM moved to shared classdata section
- JCTools excluded from bootstrap
- Result: 112 classes (below 200-300 target)

### 2. Integration Test Updates ✅
Updated test infrastructure to support new JAR structure:
- `RuntimeTest.createClientForTests()` - checks for btrace.jar first, falls back to btrace-agent.jar
- `RuntimeTest.locateAgent()` - prefers btrace.jar over btrace-agent.jar
- `JBangAttachDockerTest` - handles both JAR structures

### 3. Client Mode Verified ✅
```bash
java -jar btrace.jar --version  # Works
java -jar btrace.jar            # Shows usage
java --add-exports jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED -jar btrace.jar -l  # Lists JVMs
```

## Remaining Steps

### 1. Test jbang compatibility
```bash
./gradlew publishToMavenLocal
jbang run io.btrace:btrace:3.0.0-SNAPSHOT <PID> script.java
```

### 2. Run full integration tests
```bash
./gradlew :integration-tests:test -Pintegration
```
Note: May need to kill any lingering BTrace processes on port 2020

---

## Files Modified

| File | Change |
|------|--------|
| `btrace-boot/build.gradle` | **NEW** - Build config for boot module |
| `btrace-boot/src/main/java/org/openjdk/btrace/boot/Loader.java` | **NEW** - Entry point + MaskedClassLoader |
| `btrace-dist/build.gradle` | Added btraceJar task, prepare*Classdata tasks, updated dependencies |
| `btrace-bootstrap/build.gradle` | **NEEDS UPDATE** - Reduce bootstrap includes |

---

## Commands to Continue

```bash
# Rebuild after fixing bootstrap includes
./gradlew clean :btrace-dist:btraceJar --no-daemon

# Verify bootstrap class count (target: ~200-300)
jar -tf btrace-dist/build/resources/main/v3.0.0-SNAPSHOT/libs/btrace.jar | grep '\.class$' | wc -l

# Test client mode
java -jar btrace-dist/build/resources/main/v3.0.0-SNAPSHOT/libs/btrace.jar --version

# Run integration tests
./gradlew :integration-tests:test
```

---

## Session Reference
Full conversation transcript available at:
`/Users/jbachorik/.claude/projects/-Users-jbachorik-src-btrace-archive-work/67d7634c-01ab-4c6b-95fc-3e2eb8f73a87.jsonl`
