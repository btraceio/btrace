# BTrace Module Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce BTrace from 18 Gradle subprojects to 10 by merging logically-coupled modules, removing empty dead modules, and absorbing source-less packaging modules — with no changes to package names or published artifact contents.

**Architecture:** Sequential module-by-module merges, each leaving the full build green before the next begins. Source files are physically moved into target modules with no package or class renames. The single package-level change is moving `PackGenerator` from `io.btrace.compiler` to `io.btrace.core` to eliminate a wrong-direction `btrace-instr → btrace-compiler` compile dependency.

**Tech Stack:** Java 8/11 multi-toolchain (Gradle toolchains), Gradle 8.x, Shadow plugin (fat JARs), ASM 9.x, JUnit 5

---

## File Map

Files created, moved, or deleted across all tasks:

| Action | Path |
|---|---|
| DELETE | `btrace-ui/` (entire directory — no source) |
| DELETE | `btrace-api/` (entire directory — no source) |
| DELETE | `btrace-extensions/build.gradle` (parent aggregator, no source) |
| MOVE tree | `btrace-extension/src/main/java/` → `btrace-core/src/main/java/` |
| MOVE tree | `btrace-extension/src/test/java/` → `btrace-core/src/test/java/` |
| MOVE tree | `btrace-extension-processor/src/main/java/` → `btrace-core/src/main/java/` |
| MOVE tree | `btrace-extension-processor/src/test/java/` → `btrace-core/src/test/java/` |
| DELETE | `btrace-extension/` (after move) |
| DELETE | `btrace-extension-processor/` (after move) |
| MOVE + RENAME | `btrace-compiler/src/main/java/io/btrace/compiler/PackGenerator.java` → `btrace-core/src/main/java/io/btrace/core/PackGenerator.java` |
| RENAME service file | `btrace-instr/src/main/resources/META-INF/services/io.btrace.compiler.PackGenerator` → `…/io.btrace.core.PackGenerator` |
| MOVE tree | `btrace-instr/src/main/java/` → `btrace-agent/src/main/java/` |
| MOVE tree | `btrace-instr/src/test/java/` → `btrace-agent/src/test/java/` |
| MOVE tree | `btrace-instr/src/test/btrace/` → `btrace-agent/src/test/btrace/` |
| MOVE tree | `btrace-instr/src/test/resources/` → `btrace-agent/src/test/resources/` |
| DELETE | `btrace-instr/` (after move) |
| MOVE tree | `btrace-ext-cli/src/main/java/` → `btrace-client/src/main/java/` |
| MOVE tree | `btrace-ext-cli/src/test/java/` → `btrace-client/src/test/java/` |
| DELETE | `btrace-ext-cli/` (after move) |
| DELETE | `btrace-bootstrap/` (after absorbing its build logic into btrace-dist) |

Build files modified: `settings.gradle`, `btrace-core/build.gradle`, `btrace-agent/build.gradle`, `btrace-client/build.gradle`, `btrace-compiler/build.gradle`, `btrace-runtime/build.gradle`, `btrace-dist/build.gradle`, `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceExtensionPlugin.groovy`

---

## Task 1: Remove dead modules (btrace-ui and btrace-api)

**Files:**
- Delete: `btrace-ui/`
- Delete: `btrace-api/`
- Modify: `settings.gradle`
- Modify: `btrace-dist/build.gradle`

- [x] **Step 1.1: Confirm both directories are genuinely empty**

```bash
find btrace-ui/src btrace-api/src -name "*.java" 2>/dev/null | wc -l
```

Expected output: `0`

- [x] **Step 1.2: Delete btrace-ui and btrace-api**

```bash
rm -rf btrace-ui btrace-api
```

- [x] **Step 1.3: Remove includes from settings.gradle**

In `settings.gradle`, remove these two lines (they will be present as part of the auto-discovery loop since both directories had `build.gradle` files):

The auto-discovery loop at the bottom of `settings.gradle` picks up any directory containing a `build.gradle`. Both `btrace-ui/` and `btrace-api/` had `build.gradle` files, so once the directories are deleted, the loop will stop including them automatically. No explicit removal needed in the auto-discovery section.

Verify by running:
```bash
grep -n "btrace-ui\|btrace-api" settings.gradle
```

Expected: no output (they were auto-discovered, not explicitly included).

- [x] **Step 1.4: Remove apiJar task and all its dependsOn references from btrace-dist/build.gradle**

Find every occurrence:
```bash
grep -n "apiJar" btrace-dist/build.gradle
```

Expected lines (approximately 155, 524–529, 533):
```
155:task apiJar(type: Jar) {
524:shadowJar.dependsOn btraceJar, apiJar, copyDtraceLib, copyExtensions
525:buildTgz.dependsOn btraceJar, apiJar, ...
526:buildZip.dependsOn btraceJar, apiJar, ...
527:buildSdkmanZip.dependsOn btraceJar, apiJar, ...
528:buildDeb.dependsOn btraceJar, apiJar, ...
529:buildRpm.dependsOn btraceJar, apiJar, ...
533:task buildDockerContext(type: Copy, dependsOn: [btraceJar, apiJar, ...])
```

Delete the entire `task apiJar(type: Jar) { … }` block (lines ~155–164).

On each `dependsOn` line, remove `, apiJar` (including the leading comma and space). Example:

Before:
```groovy
shadowJar.dependsOn btraceJar, apiJar, copyDtraceLib, copyExtensions
buildTgz.dependsOn btraceJar, apiJar, fixPermissions, copyDtraceLib, processResources, explodeExtensions
```

After:
```groovy
shadowJar.dependsOn btraceJar, copyDtraceLib, copyExtensions
buildTgz.dependsOn btraceJar, fixPermissions, copyDtraceLib, processResources, explodeExtensions
```

For the `buildDockerContext` task defined with `dependsOn: [btraceJar, apiJar, …]`, remove `apiJar` from the list.

- [x] **Step 1.5: Verify the build passes**

```bash
./gradlew :btrace-dist:build -x test
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 1.6: Commit**

```bash
git add -A
git commit -m "refactor: remove empty btrace-ui and btrace-api modules"
```

---

## Task 2: Dissolve btrace-extensions parent aggregator

**Files:**
- Delete: `btrace-extensions/build.gradle`
- Modify: `settings.gradle`

- [x] **Step 2.1: Remove the parent include from settings.gradle**

In `settings.gradle`, find and remove exactly this line:
```groovy
include 'btrace-extensions'
```

The child includes (`btrace-extensions:btrace-metrics`, etc.) stay — do not touch them.

- [x] **Step 2.2: Delete the parent build.gradle**

```bash
rm btrace-extensions/build.gradle
```

- [x] **Step 2.3: Check nothing references the buildExtensionsApi task**

```bash
grep -rn "buildExtensionsApi" --include="*.gradle" .
```

Expected: no output (btrace-dist discovers extensions dynamically and does not use this task).

- [x] **Step 2.4: Verify the build passes**

```bash
./gradlew :btrace-dist:build -x test
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 2.5: Commit**

```bash
git add -A
git commit -m "refactor: dissolve btrace-extensions parent aggregator module"
```

---

## Task 3: Merge btrace-extension and btrace-extension-processor into btrace-core

**Files:**
- Move tree: `btrace-extension/src/` → `btrace-core/src/`
- Move tree: `btrace-extension-processor/src/` → `btrace-core/src/`
- Modify: `btrace-core/build.gradle`
- Modify: `btrace-runtime/build.gradle`
- Modify: `btrace-instr/build.gradle`
- Modify: `btrace-agent/build.gradle`
- Modify: `btrace-compiler/build.gradle` (runtimeOnly reference)
- Modify: `btrace-bootstrap/build.gradle`
- Modify: `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceExtensionPlugin.groovy`
- Modify: `settings.gradle`
- Delete: `btrace-extension/`, `btrace-extension-processor/`

- [x] **Step 3.1: Create btrace-core test directory**

```bash
mkdir -p btrace-core/src/test/java
```

- [x] **Step 3.2: Move btrace-extension source trees into btrace-core**

```bash
cp -r btrace-extension/src/main/java/. btrace-core/src/main/java/
cp -r btrace-extension/src/test/java/. btrace-core/src/test/java/
```

- [x] **Step 3.3: Move btrace-extension-processor source trees into btrace-core**

```bash
cp -r btrace-extension-processor/src/main/java/. btrace-core/src/main/java/
cp -r btrace-extension-processor/src/test/java/. btrace-core/src/test/java/
```

- [x] **Step 3.4: Update btrace-core/build.gradle — add test dependencies**

The moved test sources need JUnit. Add a `testImplementation` block. `btrace-core/build.gradle` currently has no `testImplementation` entries; add these lines inside the existing `dependencies { }` block:

```groovy
testImplementation libs.junit.jupiter
testImplementation platform(libs.junit)
```

Also add at the top of `btrace-core/build.gradle` (after existing plugins block, add `test` task configuration):

```groovy
test {
    useJUnitPlatform()
}
```

No new `implementation` deps are needed — `btrace-extension` only used `slf4j` and `slf4j-simple`, both already present in `btrace-core`.

- [x] **Step 3.5: Update btrace-runtime/build.gradle — replace btrace-extension references**

In `btrace-runtime/build.gradle`, there are four occurrences of `project(':btrace-extension')` (in `java9Implementation`, `java11Implementation`, `implementation`, and `testImplementation` scopes). Replace all with `project(':btrace-core')`:

```bash
grep -n "btrace-extension" btrace-runtime/build.gradle
```

Expected: lines 58, 61, 64, 66 (approximately). Replace each:

Before:
```groovy
java9Implementation project(':btrace-extension')
java11Implementation project(':btrace-extension')
implementation project(':btrace-extension')
testImplementation project(':btrace-extension')
```

After:
```groovy
java9Implementation project(':btrace-core')
java11Implementation project(':btrace-core')
implementation project(':btrace-core')
testImplementation project(':btrace-core')
```

Note: `btrace-runtime` already has `implementation project(':btrace-core')` — adding a duplicate project dependency is harmless in Gradle (it deduplicates), but for cleanliness remove the now-duplicate `implementation project(':btrace-core')` if it appears twice.

- [x] **Step 3.6: Update btrace-instr/build.gradle**

Replace `project(':btrace-extension')` → `project(':btrace-core')`:

Before:
```groovy
implementation project(':btrace-extension')
```

After:
```groovy
// btrace-extension merged into btrace-core; already declared above
```

(Remove the btrace-extension line entirely — btrace-core is already in btrace-instr's deps.)

- [x] **Step 3.7: Update btrace-agent/build.gradle**

Replace `project(':btrace-extension')` → `project(':btrace-core')` (btrace-core already declared, so just delete the btrace-extension line):

Before:
```groovy
implementation project(':btrace-core')
implementation project(':btrace-runtime')
implementation project(':btrace-instr')
implementation project(':btrace-extension')
```

After:
```groovy
implementation project(':btrace-core')
implementation project(':btrace-runtime')
implementation project(':btrace-instr')
```

- [x] **Step 3.8: Update btrace-bootstrap/build.gradle**

Replace `project(':btrace-extension')` → (remove, btrace-core already listed):

Before:
```groovy
implementation project(':btrace-core')
implementation project(':btrace-runtime')
implementation project(':btrace-instr')
implementation project(':btrace-extension')
```

After:
```groovy
implementation project(':btrace-core')
implementation project(':btrace-runtime')
implementation project(':btrace-instr')
```

- [x] **Step 3.9: Update BTraceExtensionPlugin.groovy — fix annotationProcessor wiring**

In `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceExtensionPlugin.groovy`, find the block (around line 160):

```groovy
def processorProject = project.rootProject.findProject(':btrace-extension-processor')
if (processorProject != null) {
    project.dependencies.add('annotationProcessor', processorProject)
} else {
    project.dependencies.add('annotationProcessor',
        "org.openjdk.btrace:btrace-extension-processor:${project.version}")
}
```

Replace with:

```groovy
def processorProject = project.rootProject.findProject(':btrace-core')
if (processorProject != null) {
    project.dependencies.add('annotationProcessor', processorProject)
} else {
    project.dependencies.add('annotationProcessor',
        "io.btrace:btrace-core:${project.version}")
}
```

- [x] **Step 3.10: Remove btrace-extension and btrace-extension-processor from settings.gradle**

Find and remove these two lines from `settings.gradle`:
```groovy
// (they are auto-discovered by the loop — once directories are deleted, gone automatically)
```

Verify first:
```bash
grep -n "btrace-extension" settings.gradle
```

If they appear as explicit `include` statements, remove those lines. If they were auto-discovered (directory-based), deleting the directories in the next step is sufficient.

- [x] **Step 3.11: Delete the source directories**

```bash
rm -rf btrace-extension btrace-extension-processor
```

- [x] **Step 3.12: Run the full test suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` with all tests passing. If `btrace-extension` tests fail in their new location in `btrace-core`, check for missing test resource files (extension test stubs use service loader; verify `META-INF/services/` files moved correctly with the source tree).

- [x] **Step 3.13: Commit**

```bash
git add -A
git commit -m "refactor: merge btrace-extension and btrace-extension-processor into btrace-core"
```

---

## Task 4: Move PackGenerator from btrace-compiler to btrace-core

**Background:** `btrace-instr` depends on `btrace-compiler` only for the `PackGenerator` interface (5 lines). Moving it to `btrace-core` breaks this wrong-direction dependency. After the move, `btrace-compiler` continues to use `PackGenerator` via `ServiceLoader` (dynamically — it does not import `InstrPackGenerator` directly). The ServiceLoader service file in `btrace-instr` must be renamed to match the new interface FQCN.

**Files:**
- Move + edit: `btrace-compiler/src/main/java/io/btrace/compiler/PackGenerator.java` → `btrace-core/src/main/java/io/btrace/core/PackGenerator.java`
- Edit: `btrace-compiler/src/main/java/io/btrace/compiler/CompilerHelper.java`
- Rename: `btrace-instr/src/main/resources/META-INF/services/io.btrace.compiler.PackGenerator` → `…/io.btrace.core.PackGenerator`
- Edit: `btrace-instr/src/main/java/io/btrace/instr/InstrPackGenerator.java`
- Edit: `btrace-instr/build.gradle`
- Edit: `btrace-compiler/build.gradle`

- [x] **Step 4.1: Move PackGenerator.java to btrace-core with updated package**

```bash
cp btrace-compiler/src/main/java/io/btrace/compiler/PackGenerator.java \
   btrace-core/src/main/java/io/btrace/core/PackGenerator.java
```

Edit `btrace-core/src/main/java/io/btrace/core/PackGenerator.java` — change the package declaration:

Before:
```java
package io.btrace.compiler;
```

After:
```java
package io.btrace.core;
```

Keep the rest of the file identical:
```java
package io.btrace.core;

import java.io.IOException;

public interface PackGenerator {
  byte[] generateProbePack(byte[] data) throws IOException;
}
```

Then delete the original:
```bash
rm btrace-compiler/src/main/java/io/btrace/compiler/PackGenerator.java
```

- [x] **Step 4.2: Update CompilerHelper.java import**

In `btrace-compiler/src/main/java/io/btrace/compiler/CompilerHelper.java`, replace:

```java
import io.btrace.compiler.PackGenerator;
```

with:

```java
import io.btrace.core.PackGenerator;
```

- [x] **Step 4.3: Rename the ServiceLoader service file in btrace-instr**

```bash
mv btrace-instr/src/main/resources/META-INF/services/io.btrace.compiler.PackGenerator \
   btrace-instr/src/main/resources/META-INF/services/io.btrace.core.PackGenerator
```

The file's content remains unchanged: `io.btrace.instr.InstrPackGenerator`

- [x] **Step 4.4: Update InstrPackGenerator.java import**

In `btrace-instr/src/main/java/io/btrace/instr/InstrPackGenerator.java`, replace:

```java
import io.btrace.compiler.PackGenerator;
```

with:

```java
import io.btrace.core.PackGenerator;
```

- [x] **Step 4.5: Remove btrace-compiler implementation dep from btrace-instr/build.gradle**

In `btrace-instr/build.gradle`, remove this line:

```groovy
implementation project(':btrace-compiler')
```

btrace-instr must now compile without any reference to `btrace-compiler`.

- [x] **Step 4.6: Update btrace-compiler's runtimeOnly reference to btrace-instr**

This stays as-is for now (it will change to `:btrace-agent` in Task 5). No action needed in this task.

- [x] **Step 4.7: Verify btrace-compiler still retains its btrace-runtime dependency**

In `btrace-compiler/build.gradle`, confirm the line:
```groovy
implementation project(path: ':btrace-runtime')
```
is still present. This is needed for `BTraceRuntimeAccess.uniqueClientClassNames` in `Compiler.java` and is unrelated to `PackGenerator`.

- [x] **Step 4.8: Compile both affected modules**

```bash
./gradlew :btrace-core:compileJava :btrace-compiler:compileJava :btrace-instr:compileJava
```

Expected: `BUILD SUCCESSFUL` for all three.

- [x] **Step 4.9: Run tests**

```bash
./gradlew :btrace-core:test :btrace-compiler:test :btrace-instr:test
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 4.10: Commit**

```bash
git add -A
git commit -m "refactor: move PackGenerator to btrace-core to break instr->compiler compile dependency"
```

---

## Task 5: Merge btrace-instr into btrace-agent

**Background:** `btrace-instr` has no purpose outside the agent; every consumer already has agent as a transitive dependency. The merge preserves: Java 8 `compileJava` toolchain, javac8-forked `compileTestJava`, the `compileTestProbes` Gradle task, golden file tests in `src/test/resources/instrumentorTestData/`, and all test probe sources in `src/test/btrace/`.

A circular dependency issue exists: `btrace-instr`'s tests have `testImplementation project(':btrace-client')`, but after the merge `btrace-client` will depend on `btrace-agent`. This is resolved by checking whether the tests actually use `btrace-client` classes at compile time; since they do not (confirmed by grep), the `testImplementation project(':btrace-client')` line is removed and tests still pass.

**Files:**
- Move trees: `btrace-instr/src/` → `btrace-agent/src/`
- Modify: `btrace-agent/build.gradle`
- Modify: `btrace-compiler/build.gradle`
- Modify: `btrace-client/build.gradle`
- Modify: `btrace-bootstrap/build.gradle`
- Modify: `settings.gradle`
- Delete: `btrace-instr/`

- [x] **Step 5.1: Move btrace-instr source into btrace-agent**

```bash
cp -r btrace-instr/src/main/java/. btrace-agent/src/main/java/
```

- [x] **Step 5.2: Move btrace-instr test sources and resources**

```bash
mkdir -p btrace-agent/src/test/java
mkdir -p btrace-agent/src/test/btrace
mkdir -p btrace-agent/src/test/resources
cp -r btrace-instr/src/test/java/. btrace-agent/src/test/java/
cp -r btrace-instr/src/test/btrace/. btrace-agent/src/test/btrace/
cp -r btrace-instr/src/test/resources/. btrace-agent/src/test/resources/
```

- [x] **Step 5.3: Update btrace-agent/build.gradle — merge compileJava blocks**

The current `btrace-agent/build.gradle` `compileJava` block adds `--add-exports` for JDK internals. The `btrace-instr/build.gradle` `compileJava` block sets the Java 8 toolchain. These must be combined.

Replace the existing `compileJava { }` block in `btrace-agent/build.gradle`:

Before:
```groovy
compileJava {
    // Keep Java 8 compatibility while accessing JDK internal APIs
    options.fork = true
    options.forkOptions.jvmArgs += [
        '--add-exports', 'jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED'
    ]
}
```

After:
```groovy
compileJava {
    options.fork = true
    options.forkOptions.jvmArgs += [
        '--add-exports', 'jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED'
    ]
    javaCompiler = javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}
```

- [x] **Step 5.4: Add compileTestJava block to btrace-agent/build.gradle**

Add this block after `compileJava { }` in `btrace-agent/build.gradle`:

```groovy
compileTestJava {
    options.fork = true
    options.forkOptions.executable = "${getJavac(8)}"
}
```

- [x] **Step 5.5: Merge btrace-instr dependencies into btrace-agent/build.gradle**

In `btrace-agent/build.gradle`, add the following to the `dependencies { }` block (btrace-core, btrace-runtime, btrace-extension are already present; add the missing ones):

```groovy
implementation libs.asm.tree
implementation libs.asm.util
implementation libs.autoService
implementation libs.jctools
implementation project(':btrace-compiler')

testImplementation libs.asm.util
testImplementation libs.slf4j.simple
testImplementation libs.junit.jupiter
testImplementation project(':btrace-extensions:btrace-statsd')
testImplementation project(':btrace-extensions:btrace-utils')
```

Note: Do NOT add `testImplementation project(':btrace-client')` — this would create a circular dependency (btrace-client → btrace-agent → btrace-client). The instr tests do not import btrace-client classes at compile time, so this dep is safe to drop.

Also add the test task configuration that was in `btrace-instr/build.gradle`:

```groovy
test {
    dependsOn cleanTest
    inputs.files compileTestProbes.outputs
    testLogging.showStandardStreams = true

    def props = new Properties()
    props.load(Files.newInputStream(Paths.get(System.getenv("JAVA_HOME"), "release")))
    if (!props.getProperty("JAVA_VERSION")?.contains("1.8")) {
        jvmArgs '-XX:+IgnoreUnrecognizedVMOptions',
                '--add-opens', 'java.base/java.lang=ALL-UNNAMED',
                '--add-opens', 'java.base/jdk.internal.reflect=ALL-UNNAMED',
                '--add-opens', 'java.base/java.lang=ALL-UNNAMED', '--add-exports', 'java.base/jdk.internal.reflect=ALL-UNNAMED'
    }
    if (project.hasProperty("updateTestData")) {
        jvmArgs '-Dupdate.test.data=true'
    }
    jvmArgs "-Dtest.resources=${projectDir}/src/test/resources"
    jvmArgs "-Dproject.version=${project.version}"
}
```

Also add the required imports at the top of `btrace-agent/build.gradle`:

```groovy
import java.nio.file.Files
import java.nio.file.Paths
```

- [x] **Step 5.6: Add compileTestProbes task to btrace-agent/build.gradle**

Copy the entire `compileTestProbes` task from `btrace-instr/build.gradle` into `btrace-agent/build.gradle`. There is one reference that must be updated within the task body:

Before (in compileTestProbes doLast):
```groovy
def path = project(':btrace-instr').sourceSets.main.runtimeClasspath
```

After:
```groovy
def path = project(':btrace-agent').sourceSets.main.runtimeClasspath
```

Full task after edit:

```groovy
task compileTestProbes {
    dependsOn compileTestJava, processTestResources,
            ':btrace-extensions:btrace-utils:buildApiJar',
            ':btrace-extensions:btrace-statsd:buildApiJar'
    doLast {
        def path = project(':btrace-agent').sourceSets.main.runtimeClasspath

        def loader = new URLClassLoader(path.collect { f -> f.toURL() } as URL[])
        def compiler = loader.loadClass('io.btrace.compiler.Compiler')
        def rtCp = sourceSets.test.runtimeClasspath

        def extraCp = files(
                buildDir.toPath().resolve("classes/java/test"),
                buildDir.toPath().resolve("classes/java/java11_dummy"),
                buildDir.toPath().resolve("resources/test")
        )
        def utilsApi = files(project(':btrace-extensions:btrace-utils').tasks.named('buildApiJar').get().archiveFile.get().asFile)
        def statsdApi = files(project(':btrace-extensions:btrace-statsd').tasks.named('buildApiJar').get().archiveFile.get().asFile)
        def fullCp = rtCp.plus(extraCp).plus(utilsApi).plus(statsdApi)
        def cpPath = fullCp.getAsPath()
        def args = [
                "-cp", cpPath,
                "-d", buildDir.toPath().resolve("classes")
        ]

        def files = fileTree(dir: "src/test/btrace", include: '**/*.java', exclude: 'verifier/**/*.java').findAll {
            it != null
        }.collect { it }

        args.addAll(files)

        def oldCp = System.getProperty('java.class.path')
        try {
            System.setProperty('btrace.allow.undeclared.services', 'true')
            System.setProperty('java.class.path', cpPath)
            compiler.main(args as String[])
        } finally {
            if (oldCp != null) System.setProperty('java.class.path', oldCp)
            System.clearProperty('btrace.allow.undeclared.services')
        }
    }
}
```

- [x] **Step 5.7: Update btrace-compiler/build.gradle — change runtimeOnly and testImplementation from btrace-instr to btrace-agent**

In `btrace-compiler/build.gradle`:

Before:
```groovy
runtimeOnly project(path: ':btrace-instr')
…
testImplementation project(path: ':btrace-instr')
```

After:
```groovy
runtimeOnly project(path: ':btrace-agent')
…
testImplementation project(path: ':btrace-agent')
```

- [x] **Step 5.8: Update btrace-client/build.gradle — change btrace-instr to btrace-agent**

In `btrace-client/build.gradle`:

Before:
```groovy
implementation project(':btrace-instr')
```

After:
```groovy
implementation project(':btrace-agent')
```

- [x] **Step 5.9: Update btrace-bootstrap/build.gradle — change btrace-instr to btrace-agent**

In `btrace-bootstrap/build.gradle`:

Before:
```groovy
implementation project(':btrace-instr')
```

After:
```groovy
implementation project(':btrace-agent')
```

- [x] **Step 5.10: Delete btrace-instr directory**

`btrace-instr` is auto-discovered in `settings.gradle` (the loop picks up any `btrace-*` dir with a `build.gradle`). Deleting the directory is sufficient to remove it from the build:

```bash
rm -rf btrace-instr
```

- [x] **Step 5.12: Compile btrace-agent**

```bash
./gradlew :btrace-agent:compileJava :btrace-agent:compileTestJava
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 5.13: Run btrace-agent tests**

```bash
./gradlew :btrace-agent:compileTestProbes
./gradlew :btrace-agent:test
```

Expected: `BUILD SUCCESSFUL`. If golden file tests fail with path errors (the `Dtest.resources` property path changed), run:

```bash
./gradlew :btrace-agent:test -PupdateTestData
```

Then review the diff of updated golden files in `btrace-agent/src/test/resources/instrumentorTestData/` — changes should be zero (path change doesn't affect bytecode content). Commit the regenerated golden files only if the diff confirms no content changes.

- [x] **Step 5.14: Run full build**

```bash
./gradlew :btrace-dist:build -x test
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 5.15: Commit**

```bash
git add -A
git commit -m "refactor: merge btrace-instr into btrace-agent"
```

---

## Task 6: Merge btrace-ext-cli into btrace-client

**Background:** Both are user-facing CLI tools. `btrace-ext-cli` (13 files) depends on `btrace-agent`, which `btrace-client` now also depends on. The `lanterna` terminal UI dependency and Java 11 toolchain requirement from `btrace-ext-cli` move to `btrace-client`.

**Files:**
- Move trees: `btrace-ext-cli/src/` → `btrace-client/src/`
- Modify: `btrace-client/build.gradle`
- Modify: `btrace-dist/build.gradle`
- Modify: `settings.gradle`
- Delete: `btrace-ext-cli/`

- [x] **Step 6.1: Move btrace-ext-cli sources into btrace-client**

```bash
cp -r btrace-ext-cli/src/main/java/. btrace-client/src/main/java/
cp -r btrace-ext-cli/src/test/java/. btrace-client/src/test/java/
```

- [x] **Step 6.2: Update btrace-client/build.gradle — add lanterna, Java 11 toolchain, and test framework**

In `btrace-client/build.gradle`, add to the `dependencies { }` block:

```groovy
implementation 'com.googlecode.lanterna:lanterna:3.1.5'

testImplementation platform(libs.junit)
testImplementation libs.junit.jupiter
```

Add a toolchain block if not already present (btrace-client currently targets Java 11 via `sourceCompatibility`; make it explicit):

```groovy
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}
```

Add a test task configuration:

```groovy
test {
    useJUnitPlatform()
}
```

- [x] **Step 6.3: Update btrace-dist/build.gradle — replace btrace-ext-cli reference**

```bash
grep -n "btrace-ext-cli" btrace-dist/build.gradle
```

Replace every occurrence of `project(':btrace-ext-cli')` with `project(':btrace-client')`.

- [x] **Step 6.4: Delete btrace-ext-cli**

```bash
rm -rf btrace-ext-cli
```

- [x] **Step 6.5: Run btrace-client tests**

```bash
./gradlew :btrace-client:test
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 6.6: Commit**

```bash
git add -A
git commit -m "refactor: merge btrace-ext-cli into btrace-client"
```

---

## Task 7: Absorb btrace-bootstrap packaging into btrace-dist

**Background:** `btrace-bootstrap` has zero Java source — its entire purpose is the `shadowJar` task with a `bootIncludes` filter that assembles a subset of classes from core/runtime/agent/extension onto the bootstrap classpath. This build logic is moved verbatim into `btrace-dist` as a new named task. A separate Gradle `Configuration` is used to control the classpath for this task without affecting `btrace-dist`'s main shadow JAR.

**Files:**
- Modify: `btrace-dist/build.gradle`
- Modify: `settings.gradle`
- Delete: `btrace-bootstrap/`

- [x] **Step 7.1: Add a bootstrapInputs configuration to btrace-dist/build.gradle**

Near the top of `btrace-dist/build.gradle` where other configurations are declared, add:

```groovy
configurations {
    bootstrapInputs
}
```

Then in the `dependencies { }` block, add:

```groovy
bootstrapInputs project(':btrace-core')
bootstrapInputs project(':btrace-runtime')
bootstrapInputs project(':btrace-agent')
```

- [x] **Step 7.2: Add the bootIncludes closure and bootstrapJar task to btrace-dist/build.gradle**

Add the following block to `btrace-dist/build.gradle` (before the `btraceJar` task). The `bootIncludes` closure is copied verbatim from `btrace-bootstrap/build.gradle`:

```groovy
def bootIncludes = {
    if (it.directory) {
        return true
    }
    if (it.path.endsWith('.jar')) {
        return true
    }
    if (it.path.startsWith('org/openjdk/btrace/core/')) {
        if (it.path == 'org/openjdk/btrace/core/Messages.class'
                || it.path == 'org/openjdk/btrace/core/messages.properties') {
            return false
        }
        if (it.path.startsWith('org/openjdk/btrace/core/extensions/')) {
            return false
        }
        if (it.path.startsWith('org/openjdk/btrace/core/handlers/')) {
            return true
        }
        if (it.path.startsWith('org/openjdk/btrace/core/comm/')) {
            return false
        }
        if (it.path.startsWith('org/openjdk/btrace/core/annotations/')) {
            return true
        }
        return true
    }
    if (it.path.startsWith('META-INF/services')) {
        return !it.path.contains('com.sun.') && !it.path.contains('javax.annotation.')
    }
    if (it.path.startsWith('org/openjdk/btrace/runtime/')) {
        if (it.path.startsWith('org/openjdk/btrace/runtime/BTraceRuntimeAccess.class')
                || it.path.startsWith('org/openjdk/btrace/runtime/BTraceRuntimeAccess$')
                || it.path.startsWith('org/openjdk/btrace/runtime/LinkingFlag')) {
            return true
        }
        if (it.path.startsWith('org/openjdk/btrace/runtime/Indy')
                || it.path.startsWith('org/openjdk/btrace/runtime/ExtensionIndy')) {
            return true
        }
        if (it.path.startsWith('org/openjdk/btrace/runtime/auxiliary/Auxiliary')) {
            return true
        }
        return false
    }
    if (it.path == 'org/openjdk/btrace/extension/ExtensionBridge.class') {
        return true
    }
    if (it.path.startsWith('org/slf4j/')) {
        return true
    }
    return false
}

tasks.register('bootstrapJar', ShadowJar) {
    archiveBaseName.set('btrace-bootstrap')
    archiveVersion.set('')
    archiveClassifier.set('')

    include bootIncludes

    configurations = [project.configurations.bootstrapInputs]
    relocate 'org.jctools', 'io.btrace.libs.boot.org.jctools'
    relocate 'org.objectweb.asm', 'io.btrace.libs.org.objectweb.asm'
    relocate 'org.slf4j', 'io.btrace.libs.org.slf4j'
}
```

- [x] **Step 7.3: Update btrace-dist/build.gradle — replace project(':btrace-bootstrap') references**

```bash
grep -n "btrace-bootstrap" btrace-dist/build.gradle
```

There are two occurrences (approximately lines 279 and 287):

Before:
```groovy
dependsOn project(':btrace-bootstrap').tasks.shadowJar
…
from(zipTree(project(':btrace-bootstrap').tasks.shadowJar.archiveFile)) {
```

After:
```groovy
dependsOn bootstrapJar
…
from(zipTree(bootstrapJar.archiveFile)) {
```

- [x] **Step 7.4: Delete btrace-bootstrap**

```bash
rm -rf btrace-bootstrap
```

- [x] **Step 7.5: Build btrace-dist**

```bash
./gradlew :btrace-dist:build -x test
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 7.6: Verify bootstrap JAR contents unchanged**

```bash
# Build should have produced btrace-dist/build/resources/main/v<version>/libs/
ls btrace-dist/build/resources/main/*/libs/
```

Confirm `btrace.jar` is present (the fat JAR that embeds bootstrap classes). If you have a reference build from before this task, you can compare JAR contents:

```bash
jar tf btrace-dist/build/resources/main/*/libs/btrace.jar | grep "^io/btrace/runtime/BTraceRuntimeAccess" | head -5
```

Expected: at least one `.class` entry for `BTraceRuntimeAccess` (confirming bootstrap classes were included).

- [x] **Step 7.7: Commit**

```bash
git add -A
git commit -m "refactor: absorb btrace-bootstrap packaging into btrace-dist"
```

---

## Task 8: Final wiring cleanup and full validation

**Files:**
- Modify: `integration-tests/build.gradle`
- Modify: `benchmarks/agent-benchmark/build.gradle` (if references btrace-instr)
- Modify: `benchmarks/runtime-benchmarks/build.gradle` (if references btrace-instr)
- Verify: `btrace-gradle-plugin/build.gradle` and tests

- [x] **Step 8.1: Scan all build files for stale references**

```bash
grep -rn "btrace-instr\|btrace-extension\b\|btrace-extension-processor\|btrace-ext-cli\|btrace-bootstrap\|btrace-api\|btrace-ui" \
    --include="*.gradle" --include="*.groovy" . | grep -v "^./build/" | grep -v ".gradle.kts"
```

Expected: no output. If any stale references remain, update them following the same pattern used in Tasks 3–7.

- [x] **Step 8.2: Update integration-tests/build.gradle if needed**

```bash
grep -n "btrace-instr\|btrace-extension\b" integration-tests/build.gradle
```

Replace any `project(':btrace-instr')` → `project(':btrace-agent')` and `project(':btrace-extension')` → `project(':btrace-core')`.

- [x] **Step 8.3: Update benchmark build files if needed**

```bash
grep -rn "btrace-instr\|btrace-extension\b" benchmarks/ --include="*.gradle"
```

Apply the same replacements as Step 8.2.

- [x] **Step 8.4: Verify btrace-gradle-plugin tests still pass**

The plugin's test harness runs a full Gradle build that compiles extensions; it previously invoked `:btrace-extension-processor:jar`. After the merge it will use `:btrace-core` as the annotation processor. The `BTraceExtensionPlugin.groovy` change in Task 3 handles this at runtime. Verify:

```bash
./gradlew :btrace-gradle-plugin:test
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 8.5: Run the full test suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` with all tests passing across all modules.

- [x] **Step 8.6: Build the full distribution**

```bash
./gradlew :btrace-dist:build
```

Expected: `BUILD SUCCESSFUL`. Distribution archives produced in `btrace-dist/build/distributions/`.

- [x] **Step 8.7: Run integration tests**

```bash
./gradlew :btrace-dist:build && ./gradlew :integration-tests:test -Pintegration
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 8.8: Verify final module count**

```bash
./gradlew projects 2>/dev/null | grep "Project '" | wc -l
```

Expected: 10 (btrace-core, btrace-runtime, btrace-compiler, btrace-boot, btrace-agent, btrace-client, btrace-dist, btrace-dtrace, btrace-gradle-plugin, btrace-maven-plugin) plus btrace-extensions subprojects and test/benchmark modules.

- [x] **Step 8.9: Final commit**

```bash
git add -A
git commit -m "refactor: final wiring cleanup after module consolidation"
```
