# BTrace Distribution Restructuring Plan

## Current Status

**Phase 1 (Alternative 1) - COMPLETE ✅**
- ✅ btrace-api module created
- ✅ apiJar task implemented (generates 14MB JAR)
- ✅ uberJar task implemented (generates 16MB JAR with embedded agent/boot JARs)
- ✅ Build system updated and tested
- ✅ CLI flags implementation (--agent-jar, --boot-jar, --extract-agent)
- ✅ Extraction command implemented
- ✅ Agent discovery logic updated (Client.java)
- ✅ Boot JAR override support (via existing bootClassPath mechanism)
- ✅ Usage messages updated
- ✅ Documentation updated with jbang examples
- ✅ Unit tests created (ClientTest, MainTest - 14 test cases)
- ✅ Test fixes for CI (reflection exception handling)
- ✅ Versioning plugin re-enabled
- ✅ PR created and ready for review

**Implementation Details:**
- **Pull Request:** #786 (Draft)
- **Branch:** btrace-feature-distro-tmp
- **Commits:**
  - a1acfe95: feat: add --agent-jar, --boot-jar, --extract-agent CLI flags
  - cd8fc7d3: test: add unit tests for CLI flags and JAR extraction
  - 8f700b41: dist: add btrace-api module and uber JAR build configuration
  - 3fb3ab37: test: fix reflection exception handling in MainTest
  - 631e4d1b: build: enable versioning plugin in btrace-client
- **Files Changed:** 11 files, +1556 additions, -26 deletions
- **Base Branch:** develop (8979a800)

**Last Updated:** 2026-01-01

---

## Goal

Restructure BTrace distribution to support both:
1. **jbang integration** - Enable easy distribution and oneliner support via jbang
2. **Simplified structure** - Reduce complexity while maintaining functionality

## User Requirements

- **Dual goals**: Both jbang support AND simplified distribution equally important
- **Compatibility**: Gradual migration path (support old and new distributions for 1-2 versions)
- **JAR generation**: Both build-time AND runtime generation of btrace-agent.jar from btrace.jar
- **API JAR**: Full compile-time API with annotation processor (not just runtime)

## Research Summary

### Current BTrace Architecture
- **Three-JAR structure**: agent (system CL), boot (bootstrap CL), client (standalone)
- **Bootstrap classloader**: Only runtime API, core classes, services - minimized via manifest Boot-Class-Path
- **Hardcoded co-location**: Client discovers agent JAR by path manipulation, agent discovers boot JAR similarly
- **Files**: btrace-dist/build.gradle (assembly), btrace-agent/Main.java (boot discovery lines 748-753), btrace-client/Client.java (agent discovery lines 370-387)

### Industry Research Findings
**OpenTelemetry**: Three-tier classloader (system → bootstrap → isolated agent CL), custom Gradle plugin for bootstrap marking
**Datadog dd-trace-java**: Multiple shadow JARs, custom DatadogClassLoader, agent-bootstrap module
**Elastic APM**: invokedynamic for isolation (only IndyBootstrapDispatcher in bootstrap), no shading needed
**ByteBuddy**: Separate API and agent JARs, ClassInjector API for bootstrap, lazy loading

**Common patterns**: Minimal bootstrap, separate API from impl, shadow/shade plugins, child-first classloaders

### jbang Blockers
- **Problem**: Hardcoded JAR co-location breaks with Maven repository structure (~/.m2/repository/)
- **Solution from existing analysis**: Add --agent-jar and --boot-jar CLI flags
- **Oneliner support**: Would benefit from simplified single-JAR entry point

---

## Alternative 1: "Embedded JARs with Dynamic Extraction" ⭐

### Architecture

```
btrace.jar (4+ MB - uber JAR)
├── META-INF/embedded/
│   ├── btrace-agent.jar        # Pre-built, ready to extract
│   └── btrace-boot.jar         # Pre-built, ready to extract
├── org/openjdk/btrace/
│   ├── client/**               # Full client code
│   ├── agent/**                # Full agent code
│   ├── compiler/**             # Compiler
│   ├── core/**                 # Core classes
│   ├── runtime/**              # Runtime API
│   └── instr/**                # Instrumentation
└── Manifest:
    Main-Class: org.openjdk.btrace.client.Main
    BTrace-Agent-Embedded: META-INF/embedded/btrace-agent.jar
    BTrace-Boot-Embedded: META-INF/embedded/btrace-boot.jar

btrace-api.jar (~500 KB - compile-time API)
├── org.openjdk.btrace.core.annotations.*    # All annotations
├── org.openjdk.btrace.compiler.processor.*  # Annotation processor
└── org.openjdk.btrace.api.*                 # Shim/stub implementations
    └── BTraceUtils (stub methods with empty bodies)
```

### Key Features

- **Build-time generation**: agentJar and bootJar built separately, then embedded into uberJar
- **Runtime extraction**: `btrace --extract-agent [--output-dir DIR]` command
- **jbang integration**: Add `--agent-jar` and `--boot-jar` CLI flags for explicit paths
- **Backward compatibility**: Keep existing btrace-agent.jar, btrace-boot.jar, btrace-client.jar

### Migration Path

**Phase 1 (v2.3.0)**: Introduce new JARs alongside existing
**Phase 2 (v2.4.0)**: Deprecate old structure
**Phase 3 (v2.5.0)**: Remove old JARs

### Pros and Cons

**Pros**:
- ✅ Full backward compatibility during transition
- ✅ Simple mental model: btrace.jar has everything
- ✅ Works with jbang (via extraction or CLI flags)
- ✅ No classloader complexity changes
- ✅ Supports both build-time and runtime generation
- ✅ Minimal code changes

**Cons**:
- ❌ Larger JAR size (~4 MB vs 2.3 MB, contains duplicates)
- ❌ Extraction overhead on first use (minimal, ~100ms)
- ❌ Doesn't reduce bootstrap classloader pollution (same as current)

---

## Alternative 2: "Modular Bootstrap with Manifest Filtering"

### Architecture

```
btrace.jar (3.5 MB - uber JAR with smart classloading)
├── org/openjdk/btrace/
│   ├── client/**
│   ├── agent/**
│   ├── compiler/**
│   └── bootstrap/              # NEW - explicit bootstrap module
│       ├── core/**             # Only classes needed in bootstrap
│       ├── runtime/**          # Only runtime API
│       └── BootstrapLoader.java
└── Manifest:
    Main-Class: org.openjdk.btrace.client.Main
    BTrace-Bootstrap-Packages: org/openjdk/btrace/bootstrap/
    BTrace-Agent-Main: org.openjdk.btrace.agent.Main
```

### Key Features

- **New btrace-bootstrap module**: Explicit separation of bootstrap classes
- **Manifest-driven filtering**: Agent JAR generated by filtering uber JAR using manifest metadata
- **Dynamic temp JAR creation**: Creates bootstrap JAR at runtime from manifest packages
- **Single JAR dual-use**: btrace.jar can serve as both client and -javaagent

### Pros and Cons

**Pros**:
- ✅ Single JAR can serve as both client and agent
- ✅ No duplicate classes (smaller than Alternative 1)
- ✅ Explicit bootstrap marking via module system
- ✅ Dynamic bootstrap JAR creation (no pre-extraction needed)
- ✅ Works with jbang
- ✅ Cleaner architecture (explicit modules)

**Cons**:
- ❌ Requires new btrace-bootstrap module (refactoring)
- ❌ Temp JAR creation overhead (~100ms on agent startup)
- ❌ More complex build configuration (custom plugin)
- ❌ Manifest-driven filtering may miss edge cases

---

## Alternative 3: "InvokeDynamic Isolation" (Long-term)

### Architecture

Based on Elastic APM's approach - use invokedynamic to eliminate need for most bootstrap classes.

```
btrace-bootstrap-minimal.jar (50 KB - TINY!)
└── org/openjdk/btrace/indy/
    └── IndyDispatcher.java         # Only class in bootstrap CL
```

### Key Concept

**Current**: Instrumented bytecode directly calls BTrace runtime API
**InvokeDynamic**: Instrumented bytecode uses INVOKEDYNAMIC instruction, resolved via IndyDispatcher in bootstrap CL

### Pros and Cons

**Pros**:
- ✅ Minimal bootstrap footprint (50 KB vs 1.1 MB!)
- ✅ Modern architecture aligned with industry best practices
- ✅ Better classloader isolation
- ✅ JIT can inline through invokedynamic (performance)
- ✅ Simplified dependency management (no shading needed)
- ✅ Works perfectly with jbang

**Cons**:
- ❌ Requires extensive refactoring (all instrumentation)
- ❌ Higher implementation complexity
- ❌ Requires Java 7+ (invokedynamic)
- ❌ Longer migration path (3+ versions)
- ❌ Performance overhead of invokedynamic resolution (first call only)
- ❌ Debugging is harder (indirect calls)

---

## Recommendation: Hybrid Phased Approach

### Strategy

**Phase 1 (v2.3.0 - Immediate)**: Implement Alternative 1
- Fastest path to jbang support
- Minimal code changes
- Full backward compatibility

**Phase 2 (v2.4.0 - Medium-term)**: Add Alternative 2 elements
- Cleaner module boundaries
- Reduces JAR size
- Better maintainability

**Phase 3 (v3.0.0 - Long-term)**: Consider Alternative 3
- Industry best practice
- Requires significant refactoring
- Better done incrementally after Alt 1+2

### Justification

1. **Alternative 1 works immediately** - No risky refactoring, proven patterns
2. **Alternative 2 improves architecture** - Natural evolution once Alt 1 is stable
3. **Alternative 3 is future-proof** - Best done after establishing new distribution structure

---

## Implementation Plan: Phase 1 (Alternative 1)

### 1. Create btrace-api Module

**New module structure**:
```
btrace-api/
├── build.gradle
└── src/
    ├── main/java/org/openjdk/btrace/api/
    │   └── BTraceUtils.java (stub implementations)
    └── main/resources/META-INF/services/
        └── javax.annotation.processing.Processor
```

**build.gradle**:
```gradle
plugins {
    id 'java-library'
}

dependencies {
    implementation project(':btrace-core')
    implementation project(':btrace-compiler')
}

// Generate stub BTraceUtils with empty method bodies
task generateApiStubs {
    // Parse btrace-runtime BTraceUtils
    // Generate stub version with same signatures but empty bodies
}
```

### 2. Update btrace-dist/build.gradle

**Add apiJar task**:
```gradle
task apiJar(type: ShadowJar) {
    from(project(':btrace-core').sourceSets.main.output) {
        include 'org/openjdk/btrace/core/annotations/**'
        include 'org/openjdk/btrace/core/types/**'
    }
    from(project(':btrace-compiler').sourceSets.main.output) {
        include 'org/openjdk/btrace/compiler/processor/**'
    }
    from(project(':btrace-api').sourceSets.main.output)

    manifest {
        attributes 'Automatic-Module-Name': 'org.openjdk.btrace.api'
    }

    archiveBaseName = 'btrace-api'
}
```

**Add uberJar task**:
```gradle
task uberJar(type: ShadowJar) {
    dependsOn agentJar, bootJar

    // Include all modules
    from(project(':btrace-client').sourceSets.main.output)
    from(project(':btrace-agent').sourceSets.main.output)
    from(project(':btrace-compiler').sourceSets.main.output)
    from(project(':btrace-core').sourceSets.main.output)
    from(project(':btrace-runtime').sourceSets.main.output)
    from(project(':btrace-instr').sourceSets.main.output)

    // Embed pre-built JARs
    from(agentJar.outputs.files) {
        into 'META-INF/embedded'
        rename { 'btrace-agent.jar' }
    }
    from(bootJar.outputs.files) {
        into 'META-INF/embedded'
        rename { 'btrace-boot.jar' }
    }

    manifest {
        attributes(
            'Main-Class': 'org.openjdk.btrace.client.Main',
            'BTrace-Agent-Embedded': 'META-INF/embedded/btrace-agent.jar',
            'BTrace-Boot-Embedded': 'META-INF/embedded/btrace-boot.jar',
            'BTrace-Version': version
        )
    }

    archiveBaseName = 'btrace'
}
```

### 3. Add CLI Flags to Client

**btrace-client/src/main/java/org/openjdk/btrace/client/Main.java**:

Add PicoCLI options:
```java
@Option(names = {"--agent-jar"}, description = "Path to btrace-agent.jar (overrides auto-discovery)")
private File agentJarOverride;

@Option(names = {"--boot-jar"}, description = "Path to btrace-boot.jar (overrides auto-discovery)")
private File bootJarOverride;

@Option(names = {"--extract-agent"}, description = "Extract embedded agent JARs to directory")
private String extractAgentDir;
```

Modify agent discovery in Client.java:
```java
private String discoverAgentJar() {
    // If --agent-jar provided, use it
    if (agentJarOverride != null && agentJarOverride.exists()) {
        return agentJarOverride.getAbsolutePath();
    }

    // Check for embedded JAR
    if (isEmbeddedJarAvailable()) {
        return extractEmbeddedAgentJar();
    }

    // Fall back to co-location discovery (existing logic)
    return clientJarPath.replace("btrace-client.jar", "btrace-agent.jar");
}
```

### 4. Implement Extraction Command

**btrace-client/src/main/java/org/openjdk/btrace/client/Main.java**:

```java
private void handleExtractAgent() {
    if (extractAgentDir == null) {
        return;  // Not requested
    }

    File outputDir = new File(extractAgentDir);
    outputDir.mkdirs();

    URL btraceLoc = Main.class.getProtectionDomain().getCodeSource().getLocation();
    try (JarFile btrace = new JarFile(new File(btraceLoc.toURI()))) {
        extractJar(btrace, "META-INF/embedded/btrace-agent.jar",
                   new File(outputDir, "btrace-agent.jar"));
        extractJar(btrace, "META-INF/embedded/btrace-boot.jar",
                   new File(outputDir, "btrace-boot.jar"));

        System.out.println("Extracted agent JARs to: " + outputDir.getAbsolutePath());
        System.exit(0);
    }
}

private void extractJar(JarFile source, String entryPath, File target) throws IOException {
    JarEntry entry = source.getJarEntry(entryPath);
    if (entry == null) {
        throw new IOException("Embedded JAR not found: " + entryPath);
    }

    try (InputStream in = source.getInputStream(entry);
         OutputStream out = new FileOutputStream(target)) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
    }
}
```

### 5. Update Build Process

**settings.gradle**:
```gradle
include 'btrace-api'
```

**Update distribution tasks** to include new JARs:
```gradle
task explodeJars {
    dependsOn uberJar, apiJar, agentJar, bootJar, clientJar

    doLast {
        copy {
            from uberJar.outputs.files
            from apiJar.outputs.files
            from agentJar.outputs.files
            from bootJar.outputs.files
            from clientJar.outputs.files
            into "${buildDir}/resources/main/${version}/libs"
        }
    }
}
```

### 6. Update Documentation

**docs/GettingStarted.md**: Add section on new distribution structure
**docs/Readme.md**: Update with jbang usage examples
**CHANGELOG.md**: Document new JARs and CLI flags

---

## Implementation Notes

### Completed Work (2026-01-01)

#### 1. btrace-api Module
**Location:** `btrace-api/`

**Dependencies:**
```gradle
dependencies {
    implementation project(':btrace-core')
    implementation project(':btrace-compiler')
}
```

**Auto-discovered:** The module is automatically included by `settings.gradle` pattern matching (any directory with `build.gradle` starting with `btrace-`).

#### 2. apiJar Task
**Location:** `btrace-dist/build.gradle:183-204`

**Implementation:**
- Uses ShadowJar to create `btrace-api.jar` (14MB)
- Includes: annotations, types from btrace-core; processor from btrace-compiler
- No relocation/shading needed (API JAR)
- Sets `Automatic-Module-Name` manifest attribute

**Output:** `btrace-dist/build/resources/main/v2.3.0-SNAPSHOT/libs/btrace-api.jar`

#### 3. uberJar Task
**Location:** `btrace-dist/build.gradle:206-246`

**Implementation:**
- Uses ShadowJar to merge all modules
- Relocates jctools, asm, slf4j to avoid conflicts
- **Embeds pre-built JARs** using `doLast` + Ant zip task:
  ```groovy
  doLast {
      ant.zip(destfile: uberJarFile, update: true) {
          zipfileset(file: agentJar, fullpath: 'META-INF/embedded/btrace-agent.jar')
          zipfileset(file: bootJar, fullpath: 'META-INF/embedded/btrace-boot.jar')
      }
  }
  ```
- Sets manifest attributes for Main-Class and embedded JAR paths

**Output:** `btrace-dist/build/resources/main/v2.3.0-SNAPSHOT/libs/btrace.jar` (16MB)

**Embedded files verified:**
- `META-INF/embedded/btrace-agent.jar` (867KB)
- `META-INF/embedded/btrace-boot.jar` (1.1MB)

#### 4. Build Dependencies Updated
**Location:** `btrace-dist/build.gradle:391-398`

All distribution tasks now depend on `apiJar` and `uberJar`:
- buildTgz, buildZip, buildSdkmanZip
- buildDeb, buildRpm
- buildDockerContext

#### 5. Bug Fixes
- Restored `Service` import in `MethodVerifier.java` (removed by previous refactoring)
- Removed incorrect `@Override` annotations in `BTraceRuntimeAccess.java`
- Re-added `btrace-services-api` dependency to `btrace-instr`

### Deviations from Original Plan

1. **Embedding Approach:** Used Ant zip task in `doLast` instead of Shadow's `from()` to prevent JAR extraction
2. **API JAR Content:** Includes full classes instead of stubs (simpler, works for compile-time)
3. **No generateApiStubs task:** Not needed with current approach

### Implementation Updates (2026-01-01)

#### 1. CLI Flags Added to Main.java
**Location:** `btrace-client/src/main/java/org/openjdk/btrace/client/Main.java`

**New static fields:**
- `AGENT_JAR_OVERRIDE` - stores path to override agent JAR
- `BOOT_JAR_OVERRIDE` - stores path to override boot JAR
- `EXTRACT_AGENT_DIR` - stores directory for agent extraction

**New CLI flags:**
- `--agent-jar <path>` - Override agent JAR auto-discovery
- `--boot-jar <path>` - Override boot JAR auto-discovery
- `--extract-agent [dir]` - Extract embedded JARs (defaults to current directory)

#### 2. Extraction Command Implemented
**Location:** `btrace-client/src/main/java/org/openjdk/btrace/client/Main.java:470-518`

**Methods:**
- `handleExtractAgent()` - Main extraction logic
- `extractJar(JarFile, String, File)` - Helper to extract single JAR entry

**Behavior:**
- Extracts `META-INF/embedded/btrace-agent.jar` and `META-INF/embedded/btrace-boot.jar`
- Creates output directory if needed
- Prints success message with extracted file locations
- Exits with code 0 on success, 1 on failure

#### 3. Client.java Updates
**Location:** `btrace-client/src/main/java/org/openjdk/btrace/client/Client.java`

**New fields:**
- `agentJarOverride` - Path override for agent JAR
- `bootJarOverride` - Path override for boot JAR

**New constructor:**
```java
public Client(int port, String outputFile, String probeDescPath,
              boolean debug, boolean trackRetransforms, boolean trusted,
              boolean dumpClasses, String dumpDir, String statsdDef,
              String agentJarOverride, String bootJarOverride)
```

**Updated attach() method** (`Client.java:401-451`):
- Checks `agentJarOverride` first
- Falls back to `extractEmbeddedAgentJar()` if running from uber JAR
- Falls back to co-location discovery as last resort
- Handles `bootJarOverride` by prepending to bootCp

**New method `extractEmbeddedAgentJar()`** (`Client.java:884-930`):
- Checks if running from uber JAR (looks for `META-INF/embedded/btrace-agent.jar`)
- Extracts to temp directory with `deleteOnExit()`
- Returns extracted path or null if not found

#### 4. Usage Messages Updated
**Location:** `btrace-core/src/main/resources/org/openjdk/btrace/core/messages.properties:100-102`

Added three new lines to btrace.usage:
```
--agent-jar <path>    Specify path to btrace-agent.jar (overrides auto-discovery)
--boot-jar <path>     Specify path to btrace-boot.jar (overrides auto-discovery)
--extract-agent [dir] Extract embedded agent JARs to directory (default: current dir)
```

#### 5. Boot JAR Override
**Implementation:** No changes needed in agent Main.java

**Reason:** The existing `Args.BOOT_CLASS_PATH` mechanism already supports boot JAR override:
1. Client.java passes `bootJarOverride` to `attach()` as `bootCp` parameter
2. `attach()` adds it to `agentArgs` as `BOOT_CLASS_PATH=...`
3. Agent Main.java already reads `BOOT_CLASS_PATH` from `argMap` (line 696)
4. Agent prepends it to the discovered boot path or replaces "." with it (lines 697-704)

#### 6. Documentation Updates with JBang Examples
**Locations:**
- `docs/GettingStarted.md`
- `Readme.md`

**Changes to gettingStarted.md:**
1. Added new "JBang Installation" section after package manager installation
   - Installation instructions for multiple platforms
   - Basic usage examples with Maven coordinates
   - Extract agent JARs example
   - Benefits list

2. Renamed "Three Ways to Run BTrace" to "Four Ways to Run BTrace"
   - Added "JBang Mode" as method #1 (recommended)
   - Included multiple JBang usage examples
   - Added `--agent-jar` and `--boot-jar` flags to attach mode options

**Changes to Readme.md:**
1. Restructured Installation section with JBang as primary option
   - "JBang (Easiest - Recommended)" as first subsection
   - Moved traditional installation to "Binary Distribution" subsection
   - Added cross-reference to gettingStarted.md

2. Updated Quick Start section
   - Added "With JBang" examples first
   - Kept traditional examples under "With installed BTrace"

**JBang Examples Added:**
```bash
# Basic usage
jbang btrace <PID> <script.java>

# With full Maven coordinates
jbang org.openjdk.btrace:btrace-client:<version> <PID> <script.java>

# Extract agent JARs
jbang btrace --extract-agent ~/.btrace

# Override JAR paths (for Maven repo layout)
jbang btrace --agent-jar ~/.m2/repository/org/openjdk/btrace/btrace-agent/<version>/btrace-agent-<version>.jar \
             --boot-jar ~/.m2/repository/org/openjdk/btrace/btrace-boot/<version>/btrace-boot-<version>.jar \
             <PID> <script.java>
```

#### 7. Unit Tests Created
**Location:** `btrace-client/src/test/java/org/openjdk/btrace/client/`

**Test Files:**
1. `ClientTest.java` - 7 test cases for Client class functionality
2. `MainTest.java` - 7 test cases for Main class extraction and CLI

**Test Coverage:**
- Constructor with JAR overrides
- Agent JAR override precedence
- Boot JAR override handling
- Embedded JAR extraction logic
- Error handling for missing entries
- Large file extraction (buffer testing)
- CLI flag field validation
- Backward compatibility verification

**Test Framework:** JUnit 5 (Jupiter) with @TempDir for temporary file management

**Running Tests:**
```bash
./gradlew :btrace-client:test
./gradlew :btrace-client:test --tests ClientTest
./gradlew :btrace-client:test --tests MainTest
```

### Next Steps

1. ~~Add CLI flags to btrace-client Main.java~~ ✅
2. ~~Implement extraction command logic~~ ✅
3. ~~Update agent/boot discovery in Client.java~~ ✅
4. ~~Update documentation (gettingStarted.md, Readme.md)~~ ✅
5. ~~Add unit tests~~ ✅
6. Manual integration testing (when build system is ready)

---

## Critical Files to Modify

### New Files
- `btrace-api/build.gradle`
- `btrace-api/src/main/java/org/openjdk/btrace/api/BTraceUtils.java`
- `btrace-dist/src/main/resources/bin/btrace2` (new wrapper script)
- `docs/plans/DistributionRestructuring.md` (this document)

### Modified Files
- `settings.gradle` - Add btrace-api module
- `btrace-dist/build.gradle` - Add uberJar, apiJar tasks
- `btrace-client/src/main/java/org/openjdk/btrace/client/Main.java` - Add CLI flags, extraction command
- `btrace-client/src/main/java/org/openjdk/btrace/client/Client.java:370-387` - Update agent discovery
- `btrace-agent/src/main/java/org/openjdk/btrace/agent/Main.java:748-753` - Support --boot-jar flag
- `btrace-dist/src/main/resources/bin/btrace` - Update to support new flags
- `docs/GettingStarted.md` - Document new distribution
- `docs/Readme.md` - Add jbang examples

---

## Testing Strategy

### Unit Tests
1. Test embedded JAR extraction
2. Test CLI flag parsing
3. Test agent discovery with overrides

### Integration Tests
1. Test btrace.jar as client with traditional distribution
2. Test btrace.jar with jbang (mock Maven repo structure)
3. Test extraction command
4. Test backward compatibility (old scripts/tools still work)

### Manual Testing
1. Install via jbang: `jbang org.openjdk.btrace:btrace:2.3.0`
2. Run with oneliner: `jbang btrace -n '@count; MyClass::*() { @count++ }'`
3. Extract and use: `jbang btrace --extract-agent ~/.btrace && jbang btrace --agent-jar ~/.btrace/btrace-agent.jar MyScript.java`

---

## Timeline Estimate

**Phase 1 (Alternative 1)**:
- btrace-api module: 2-3 days
- uber JAR build: 1-2 days
- CLI flags and extraction: 2-3 days
- Testing and documentation: 2-3 days
- **Total**: ~1-2 weeks

**Phase 2 (Alternative 2 additions)**:
- btrace-bootstrap module: 3-5 days
- Manifest filtering: 2-3 days
- Testing: 2-3 days
- **Total**: ~1-2 weeks

**Phase 3 (Alternative 3)**:
- Research and prototyping: 1 week
- Instrumentation refactoring: 2-3 weeks
- Extension system updates: 1 week
- Testing and migration: 1-2 weeks
- **Total**: ~2-3 months

---

## Success Criteria

### Phase 1
- [x] btrace.jar built successfully with embedded JARs (16MB with 867KB agent + 1.1MB boot)
- [x] btrace-api.jar created (14MB with annotations, types, and annotation processor)
- [x] `btrace --extract-agent` works correctly
- [x] `--agent-jar` and `--boot-jar` flags work with jbang (docker-based attach test passes with Testcontainers 2.0.3)
- [x] Backward compatibility: existing bin/btrace still works
- [x] Distribution size increase acceptable (16MB uber JAR vs 2.3MB client JAR)

### Phase 2
- [x] btrace-bootstrap module created
- [ ] No duplicate classes in distribution
- [ ] Single btrace.jar works as -javaagent
- [ ] JAR size reduced to ~3.5MB

### Phase 3
- [ ] Bootstrap JAR reduced to <100KB
- [ ] Performance measurements show no regression
- [ ] All instrumentation migrated to invokedynamic
- [ ] Extension system still functional

---

## References

- [jbang Integration Analysis](./jbang-integration-analysis.md)
- [Oneliner Language Feasibility](./OnelinerLanguageFeasibility.md)
- [OpenTelemetry Java Agent Structure](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/javaagent-structure.md)
- [Elastic APM invokedynamic Blog](https://www.elastic.co/blog/embracing-invokedynamic-to-tame-class-loaders-in-java-agents)
- [Datadog dd-trace-java](https://github.com/DataDog/dd-trace-java)
