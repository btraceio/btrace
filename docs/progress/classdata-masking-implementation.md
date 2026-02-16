# Implementation Progress: Single JAR with .classdata Masking

**Date:** 2025-01-25
**Status:** Partially Complete - Needs Bootstrap Class Review

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
btrace.jar (3.6MB)
├── org/openjdk/btrace/boot/Loader*.class       # Entry point (3 classes)
├── org/openjdk/btrace/core/*.class             # Bootstrap: core API
├── org/openjdk/btrace/runtime/*.class          # Bootstrap: runtime
├── org/openjdk/btrace/extension/*.class        # Bootstrap: extensions
├── org/openjdk/btrace/libs/org/objectweb/asm/* # Bootstrap: relocated ASM
├── org/openjdk/btrace/libs/org/slf4j/*         # Bootstrap: relocated SLF4J
├── org/openjdk/btrace/libs/boot/org/jctools/*  # Bootstrap: relocated JCTools
├── META-INF/btrace/agent/*.classdata           # Masked agent classes (520)
├── META-INF/btrace/client/*.classdata          # Masked client classes (951)
└── META-INF/MANIFEST.MF
```

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

## Outstanding Issue: Bootstrap Class Count Too High

**Current state:** 736 classes in bootstrap (should be ~200-300)

**Problem breakdown by package:**
```
101 org/openjdk/btrace/libs/boot/org/jctools/queues
 65 org/openjdk/btrace/libs/boot/org/jctools/queues/atomic
 64 org/openjdk/btrace/libs/boot/org/jctools/queues/unpadded
 60 org/openjdk/btrace/libs/boot/org/jctools/queues/atomic/unpadded
 45 org/openjdk/btrace/runtime
 42 org/openjdk/btrace/core
 39 org/openjdk/btrace/libs/boot/org/jctools/maps
 38 org/openjdk/btrace/libs/org/objectweb/asm/tree          <-- Should not be in bootstrap
 35 org/openjdk/btrace/libs/org/objectweb/asm
 34 org/openjdk/btrace/core/annotations
 33 org/openjdk/btrace/core/comm
 31 org/openjdk/btrace/core/comm/v2
 17 org/openjdk/btrace/core/aggregation
 16 org/openjdk/btrace/libs/org/slf4j/helpers
 14 org/openjdk/btrace/libs/org/objectweb/asm/tree/analysis <-- Should not be in bootstrap
 14 org/openjdk/btrace/libs/boot/org/jctools/util
 10 org/openjdk/btrace/libs/org/slf4j/impl
 10 org/openjdk/btrace/extension
```

**Classes that should NOT be in bootstrap:**
- `org/objectweb/asm/tree/**` - ASM tree API (for instrumentation, not runtime)
- `org/objectweb/asm/tree/analysis/**` - ASM analysis (for instrumentation)
- Many JCTools queue variants (only need basic collections)
- `org/slf4j/impl/**` and `org/slf4j/helpers/**` - Only need SLF4J API

**Root cause:** The `btrace-bootstrap/build.gradle` includes too much:
```groovy
// Current - too broad:
if (it.path.startsWith('org/objectweb/asm/')) {
    // Excludes commons/util/xml but includes tree/
    return true
}
return it.path.startsWith('org/jctools/')  // Includes ALL jctools
```

---

## Next Steps

### 1. Fix btrace-bootstrap includes filter

Update `btrace-bootstrap/build.gradle` to exclude:
```groovy
def bootIncludes = {
    // ... existing checks ...

    if (it.path.startsWith('org/objectweb/asm/')) {
        // Exclude tree, analysis, commons, util, xml
        if (it.path.startsWith('org/objectweb/asm/tree/') ||
            it.path.startsWith('org/objectweb/asm/commons/') ||
            it.path.startsWith('org/objectweb/asm/util/') ||
            it.path.startsWith('org/objectweb/asm/xml/')) {
            return false
        }
        return true
    }

    // Only include essential JCTools classes
    if (it.path.startsWith('org/jctools/')) {
        // Include only maps and essential utilities
        return it.path.startsWith('org/jctools/maps/') ||
               it.path.startsWith('org/jctools/util/')
    }

    // SLF4J - only API, not impl/helpers
    if (it.path.startsWith('org/slf4j/')) {
        return !it.path.startsWith('org/slf4j/impl/') &&
               !it.path.startsWith('org/slf4j/helpers/')
    }
}
```

### 2. Verify agent functionality works
- Test `-javaagent:btrace.jar` with a real application
- Run integration tests: `./gradlew :integration-tests:test`

### 3. Test jbang compatibility
```bash
jbang run io.btrace:btrace:3.0.0-SNAPSHOT <PID> script.java
```

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
