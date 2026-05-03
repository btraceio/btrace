# Repackaging org.openjdk.btrace → io.btrace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename every occurrence of the `org.openjdk.btrace` Java package namespace to `io.btrace` across all source files, build scripts, resources, and documentation, while keeping a transparent backwards-compatibility shim so pre-compiled probes (`.class` files referencing `org/openjdk/btrace/` classes) still load and run correctly.

**Architecture:** Pure mechanical text-and-directory transformation for the rename, plus a new `ProbePackageMigrator` class in `btrace-instr` that uses ASM `ClassRemapper` to silently remap any probe bytecode still referencing `org/openjdk/btrace/` to `io/btrace/` at load time. The shim is inserted into `BTraceProbePersisted.upgradeBytecode()`, which is already the probe-upgrade pipeline. The Gradle `group` is changed from `org.openjdk.btrace` to `io.btrace`.

**Tech Stack:** Java 8+/11 toolchain, Gradle 8, ASM 9 (ClassRemapper / SimpleRemapper), Google Java Format (Spotless), ServiceLoader SPI, Maven plugin descriptor.

**Working directory for all commands:** `/Users/jbachorik/src/btrace/.worktrees/jb/repackaging`

---

## Status

| Task | Status |
|---|---|
| Task 1: Gradle build files | ✅ DONE (commits `7bb65e94`, `95e9a816`) |
| Tasks 2–13 | Pending |

---

## Scope reference

| Category | Count | Notes |
|---|---|---|
| Java source files (non-probe) | ~508 | Package declarations + imports + string literals |
| BTrace probe scripts (`src/test/btrace/`) | ~220 | Imports only – no `package` declaration |
| Test resource Java files | 3 | In `btrace-compiler/src/test/resources/` |
| Build files (.gradle) | 23 | ✅ Already done |
| SPI service files | 2 | Must rename the file + update content |
| MANIFEST.MF | 1 | `Premain-Class` / `Agent-Class` |
| Maven plugin.xml | 1 | `<groupId>` + `<implementation>` |
| Probe XML files | 2 | Filename + content |
| Documentation (.md) | ~20 | Code examples and class references |
| Golden files | 368/396 | Regenerated automatically at end |

---

## Task 2: Replace org.openjdk.btrace in Main Java Source Content

Replace `package` declarations, `import` statements, and string literals in all main source sets. Do **not** move directories yet.

**Source roots affected:**
- `*/src/main/java/`
- `btrace-runtime/src/main/java9/`
- `btrace-runtime/src/main/java11/`
- `btrace-runtime/src/main/java15/`
- `*/src/jmh/java/`

- [ ] **Step 1: Replace in all main source sets**

```bash
cd /Users/jbachorik/src/btrace/.worktrees/jb/repackaging

find . -not -path "*/build/*" \
  \( -path "*/src/main/java/*" \
  -o -path "*/src/main/java9/*" \
  -o -path "*/src/main/java11/*" \
  -o -path "*/src/main/java15/*" \
  -o -path "*/src/jmh/java/*" \) \
  -name "*.java" \
  | xargs sed -i '' 's/org\.openjdk\.btrace/io.btrace/g'
```

- [ ] **Step 2: Verify no old references remain**

```bash
find . -not -path "*/build/*" \
  \( -path "*/src/main/java/*" \
  -o -path "*/src/main/java9/*" \
  -o -path "*/src/main/java11/*" \
  -o -path "*/src/main/java15/*" \
  -o -path "*/src/jmh/java/*" \) \
  -name "*.java" \
  | xargs grep -l "org\.openjdk\.btrace" 2>/dev/null
```
Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add $(find . -not -path "*/build/*" \
  \( -path "*/src/main/java/*" \
  -o -path "*/src/main/java9/*" \
  -o -path "*/src/main/java11/*" \
  -o -path "*/src/main/java15/*" \
  -o -path "*/src/jmh/java/*" \) \
  -name "*.java")
git commit -m "refactor: replace org.openjdk.btrace with io.btrace in main source content"
```

---

## Task 3: Move Main Java Source Directories

Rename the physical directory tree so paths match the new package name.

- [ ] **Step 1: Move all main source roots**

```bash
cd /Users/jbachorik/src/btrace/.worktrees/jb/repackaging

for root in \
  btrace-agent/src/main/java \
  btrace-api/src/main/java \
  btrace-boot/src/main/java \
  btrace-bootstrap/src/main/java \
  btrace-client/src/main/java \
  btrace-compiler/src/main/java \
  btrace-core/src/main/java \
  btrace-dtrace/src/main/java \
  btrace-ext-cli/src/main/java \
  btrace-extension/src/main/java \
  btrace-extension-processor/src/main/java \
  btrace-extensions/btrace-ext-test/src/api/java \
  btrace-extensions/btrace-ext-test/src/impl/java \
  btrace-extensions/btrace-metrics/src/api/java \
  btrace-extensions/btrace-metrics/src/impl/java \
  btrace-extensions/btrace-statsd/src/api/java \
  btrace-extensions/btrace-statsd/src/impl/java \
  btrace-extensions/btrace-utils/src/main/java \
  btrace-extensions/examples/btrace-hadoop/src/main/java \
  btrace-extensions/examples/btrace-spark/src/main/java \
  btrace-gradle-plugin/src/main/java \
  btrace-instr/src/main/java \
  btrace-maven-plugin/src/main/java \
  btrace-runtime/src/main/java \
  btrace-runtime/src/main/java9 \
  btrace-runtime/src/main/java11 \
  btrace-runtime/src/main/java15 \
  btrace-ui/src/main/java \
  benchmarks/agent-benchmark/src/main/java \
  benchmarks/runtime-benchmarks/src/jmh/java \
  btrace-core/src/jmh/java \
  integration-tests/src/main/java; do
  src="$root/org/openjdk/btrace"
  dst="$root/io/btrace"
  if [ -d "$src" ]; then
    mkdir -p "$(dirname $dst)"
    git mv "$src" "$dst"
    git rm -r --cached "$root/org" 2>/dev/null || true
    rm -rf "$root/org"
  fi
done
```

- [ ] **Step 2: Verify no old paths remain**

```bash
find . -not -path "*/build/*" -path "*/src/main/java/org/openjdk/btrace" -type d | head -5
```
Expected: no output.

```bash
find . -not -path "*/build/*" -path "*/src/main/java/io/btrace" -type d | head -5
```
Expected: several entries.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: move main source directories from org/openjdk/btrace to io/btrace"
```

---

## Task 4: Replace and Move Test Java Source Files

- [ ] **Step 1: Replace text in test Java sources**

```bash
find . -not -path "*/build/*" -path "*/src/test/java/*" -name "*.java" \
  | xargs sed -i '' 's/org\.openjdk\.btrace/io.btrace/g'
```

- [ ] **Step 2: Verify no old references remain**

```bash
find . -not -path "*/build/*" -path "*/src/test/java/*" -name "*.java" \
  | xargs grep -l "org\.openjdk\.btrace" 2>/dev/null
```
Expected: no output.

- [ ] **Step 3: Move test source directories**

```bash
for root in \
  btrace-agent/src/test/java \
  btrace-client/src/test/java \
  btrace-compiler/src/test/java \
  btrace-core/src/test/java \
  btrace-extension/src/test/java \
  btrace-instr/src/test/java \
  btrace-runtime/src/test/java \
  integration-tests/src/test/java; do
  src="$root/org/openjdk/btrace"
  dst="$root/io/btrace"
  if [ -d "$src" ]; then
    mkdir -p "$(dirname $dst)"
    git mv "$src" "$dst"
    git rm -r --cached "$root/org" 2>/dev/null || true
    rm -rf "$root/org"
  fi
done
```

- [ ] **Step 4: Verify**

```bash
find . -not -path "*/build/*" -path "*/src/test/java/org" -type d | head -5
```
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: replace and move test source from org/openjdk/btrace to io/btrace"
```

---

## Task 5: Update BTrace Probe Scripts and Test Resource Java Files

- [ ] **Step 1: Replace in probe scripts**

```bash
find . -not -path "*/build/*" -path "*/src/test/btrace/*" -name "*.java" \
  | xargs sed -i '' 's/org\.openjdk\.btrace/io.btrace/g'
```

- [ ] **Step 2: Replace in test resource Java files**

```bash
find . -not -path "*/build/*" -path "*/src/test/resources/*" -name "*.java" \
  | xargs sed -i '' 's/org\.openjdk\.btrace/io.btrace/g'
```

- [ ] **Step 3: Verify**

```bash
find . -not -path "*/build/*" \
  \( -path "*/src/test/btrace/*" -o -path "*/src/test/resources/*.java" \) \
  -name "*.java" \
  | xargs grep -l "org\.openjdk\.btrace" 2>/dev/null
```
Expected: no output.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: replace org.openjdk.btrace with io.btrace in probe scripts and test resource Java files"
```

---

## Task 6: Rename and Update SPI Service Files

**Files:**
- `btrace-dtrace/src/main/resources/META-INF/services/org.openjdk.btrace.core.extensions.Extension`
- `btrace-instr/src/main/resources/META-INF/services/org.openjdk.btrace.compiler.PackGenerator`

- [ ] **Step 1: Rename and update the DTrace extension SPI file**

```bash
cd /Users/jbachorik/src/btrace/.worktrees/jb/repackaging
OLD=btrace-dtrace/src/main/resources/META-INF/services/org.openjdk.btrace.core.extensions.Extension
NEW=btrace-dtrace/src/main/resources/META-INF/services/io.btrace.core.extensions.Extension
git mv "$OLD" "$NEW"
sed -i '' 's/org\.openjdk\.btrace/io.btrace/g' "$NEW"
```

Verify:
```bash
cat btrace-dtrace/src/main/resources/META-INF/services/io.btrace.core.extensions.Extension
```
Expected: `io.btrace.dtrace.DTraceExtension`

- [ ] **Step 2: Rename and update the PackGenerator SPI file**

```bash
OLD=btrace-instr/src/main/resources/META-INF/services/org.openjdk.btrace.compiler.PackGenerator
NEW=btrace-instr/src/main/resources/META-INF/services/io.btrace.compiler.PackGenerator
git mv "$OLD" "$NEW"
sed -i '' 's/org\.openjdk\.btrace/io.btrace/g' "$NEW"
```

Verify:
```bash
cat btrace-instr/src/main/resources/META-INF/services/io.btrace.compiler.PackGenerator
```
Expected: `io.btrace.instr.InstrPackGenerator`

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: rename and update SPI service files from org.openjdk.btrace to io.btrace"
```

---

## Task 7: Update MANIFEST.MF and Maven plugin.xml

- [ ] **Step 1: Update MANIFEST.MF**

```bash
sed -i '' 's/org\.openjdk\.btrace/io.btrace/g' \
  btrace-agent/src/main/resources/META-INF/MANIFEST.MF
```

Verify:
```bash
cat btrace-agent/src/main/resources/META-INF/MANIFEST.MF
```
Expected: `Premain-Class: io.btrace.agent.Main`

- [ ] **Step 2: Update Maven plugin.xml**

```bash
sed -i '' 's/org\.openjdk\.btrace/io.btrace/g' \
  btrace-maven-plugin/src/main/resources/META-INF/maven/plugin.xml
```

Verify:
```bash
grep -E "groupId|implementation" btrace-maven-plugin/src/main/resources/META-INF/maven/plugin.xml | head -5
```
Expected: contains `io.btrace`.

- [ ] **Step 3: Commit**

```bash
git add btrace-agent/src/main/resources/META-INF/MANIFEST.MF \
        btrace-maven-plugin/src/main/resources/META-INF/maven/plugin.xml
git commit -m "refactor: update MANIFEST.MF and Maven plugin.xml for io.btrace namespace"
```

---

## Task 8: Rename and Update Probe XML Files

**Files:**
- `btrace-instr/src/test/btrace/org.openjdk.btrace.xml`
- `integration-tests/src/test/btrace/org.openjdk.btrace.xml`

- [ ] **Step 1: Rename and update btrace-instr probe XML**

```bash
git mv btrace-instr/src/test/btrace/org.openjdk.btrace.xml \
       btrace-instr/src/test/btrace/io.btrace.xml
sed -i '' 's/org\.openjdk\.btrace/io.btrace/g' btrace-instr/src/test/btrace/io.btrace.xml
```

- [ ] **Step 2: Rename and update integration-tests probe XML**

```bash
git mv integration-tests/src/test/btrace/org.openjdk.btrace.xml \
       integration-tests/src/test/btrace/io.btrace.xml
sed -i '' 's/org\.openjdk\.btrace/io.btrace/g' integration-tests/src/test/btrace/io.btrace.xml
```

- [ ] **Step 3: Check for hardcoded filename references**

```bash
grep -r "org\.openjdk\.btrace\.xml" . --include="*.java" --include="*.gradle" \
  --include="*.properties" -l | grep -v "/build/"
```
For each file found, apply: `sed -i '' 's/org\.openjdk\.btrace\.xml/io.btrace.xml/g' <file>`

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: rename and update probe XML files from org.openjdk.btrace to io.btrace"
```

---

## Task 9: Update Documentation

- [ ] **Step 1: Replace in all markdown/adoc files**

```bash
find . -not -path "*/build/*" -not -path "*/.worktrees/*" \
  \( -name "*.md" -o -name "*.adoc" \) \
  | xargs grep -l "org\.openjdk\.btrace" \
  | xargs sed -i '' 's/org\.openjdk\.btrace/io.btrace/g'
```

- [ ] **Step 2: Verify**

```bash
find . -not -path "*/build/*" -not -path "*/.worktrees/*" \
  \( -name "*.md" -o -name "*.adoc" \) \
  | xargs grep -l "org\.openjdk\.btrace" 2>/dev/null
```
Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add -A -- '*.md' '*.adoc'
git commit -m "docs: replace org.openjdk.btrace with io.btrace in all documentation"
```

---

## Task 10: Catch-All Sweep for Remaining References

- [ ] **Step 1: Find any remaining occurrences**

```bash
grep -r "org\.openjdk\.btrace" . \
  --include="*.java" --include="*.gradle" --include="*.xml" \
  --include="*.properties" --include="*.MF" --include="*.md" \
  --include="*.txt" --include="*.sh" --include="*.kts" \
  --exclude-dir=build --exclude-dir=".worktrees" \
  -l
```

- [ ] **Step 2: Fix any remaining files**

```bash
# For each file found above:
sed -i '' 's/org\.openjdk\.btrace/io.btrace/g' <file>
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: fix remaining org.openjdk.btrace references"
```

---

## Task 11: Probe Backwards-Compatibility Migration Shim

Add transparent migration of pre-compiled probe bytecode that still references `org/openjdk/btrace/` classes. Old probes that were compiled against the old namespace must load and run without any changes by the user.

**Files:**
- Create: `btrace-instr/src/main/java/io/btrace/instr/ProbePackageMigrator.java`
- Modify: `btrace-instr/src/main/java/io/btrace/instr/BTraceProbePersisted.java` (lines ~678–681)
- Create: `btrace-instr/src/test/java/io/btrace/instr/ProbePackageMigratorTest.java`

**Context:** `BTraceProbePersisted.upgradeBytecode()` is the existing probe upgrade pipeline, called at line ~205 during `read_1()` and `read_2()` (the persisted probe loading methods). `ProbeUpgradeVisitor_1_2` already shows the pattern: take a `ClassReader`, produce upgraded `byte[]` via `ClassWriter`. The new migrator follows the same pattern using ASM's `ClassRemapper`.

- [ ] **Step 1: Write the failing test**

Create `btrace-instr/src/test/java/io/btrace/instr/ProbePackageMigratorTest.java`:

```java
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class ProbePackageMigratorTest {

  /** Builds minimal valid bytecode whose constant pool contains org/openjdk/btrace references. */
  private static byte[] buildOldPackageClass() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        "org/openjdk/btrace/test/FakeProbe",
        null,
        "java/lang/Object",
        null);
    // A method that references an old-namespace class in its descriptor
    MethodVisitor mv =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "probe",
            "(Lorg/openjdk/btrace/core/BTraceRuntime;)V",
            null,
            null);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(0, 1);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  @Test
  void migratesOldPackageReferences() {
    byte[] old = buildOldPackageClass();
    byte[] migrated = ProbePackageMigrator.migrate(old);

    // Read the migrated class and collect class name and method descriptor
    final String[] info = new String[2];
    new ClassReader(migrated)
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public void visit(
                  int version,
                  int access,
                  String name,
                  String signature,
                  String superName,
                  String[] interfaces) {
                info[0] = name;
              }

              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                if ("probe".equals(name)) info[1] = descriptor;
                return null;
              }
            },
            0);

    assertEquals("io/btrace/test/FakeProbe", info[0]);
    assertEquals("(Lio/btrace/core/BTraceRuntime;)V", info[1]);
  }

  @Test
  void noOpForNewPackage() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        "io/btrace/test/NewProbe",
        null,
        "java/lang/Object",
        null);
    cw.visitEnd();
    byte[] original = cw.toByteArray();

    byte[] result = ProbePackageMigrator.migrate(original);

    // Should not throw; class name must be io/btrace (unchanged)
    ClassReader cr = new ClassReader(result);
    assertEquals("io/btrace/test/NewProbe", cr.getClassName());
  }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
./gradlew :btrace-instr:test --tests "io.btrace.instr.ProbePackageMigratorTest" 2>&1 | tail -20
```
Expected: compilation failure (`ProbePackageMigrator` does not exist yet).

- [ ] **Step 3: Implement `ProbePackageMigrator`**

Create `btrace-instr/src/main/java/io/btrace/instr/ProbePackageMigrator.java`:

```java
package io.btrace.instr;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/** Transparently migrates probe bytecode compiled against the old org.openjdk.btrace namespace. */
final class ProbePackageMigrator {

  private static final String OLD_PREFIX = "org/openjdk/btrace/";
  private static final String NEW_PREFIX = "io/btrace/";

  private ProbePackageMigrator() {}

  /**
   * If {@code bytes} references the old {@code org/openjdk/btrace/} namespace, returns new
   * bytecode with all such references remapped to {@code io/btrace/}. Otherwise returns {@code
   * bytes} unchanged.
   */
  static byte[] migrate(byte[] bytes) {
    if (!needsMigration(bytes)) {
      return bytes;
    }
    ClassReader cr = new ClassReader(bytes);
    ClassWriter cw = new ClassWriter(cr, 0);
    cr.accept(new ClassRemapper(cw, REMAPPER), ClassReader.EXPAND_FRAMES);
    return cw.toByteArray();
  }

  private static boolean needsMigration(byte[] bytes) {
    // Fast scan: look for the old prefix as a UTF-8 byte sequence in the constant pool.
    // ClassReader.b is the raw bytes; we scan them directly to avoid a full parse.
    String classContent = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
    return classContent.contains("org/openjdk/btrace/");
  }

  private static final Remapper REMAPPER =
      new Remapper() {
        @Override
        public String map(String internalName) {
          if (internalName.startsWith(OLD_PREFIX)) {
            return NEW_PREFIX + internalName.substring(OLD_PREFIX.length());
          }
          return internalName;
        }
      };
}
```

- [ ] **Step 4: Wire the migrator into `BTraceProbePersisted.upgradeBytecode()`**

Open `btrace-instr/src/main/java/io/btrace/instr/BTraceProbePersisted.java` and update the `upgradeBytecode()` method (around line 678) from:

```java
  private void upgradeBytecode() {
    fullData = ProbeUpgradeVisitor_1_2.upgrade(new ClassReader(fullData));
    dataHolder = ProbeUpgradeVisitor_1_2.upgrade(new ClassReader(dataHolder));
  }
```

to:

```java
  private void upgradeBytecode() {
    fullData = ProbeUpgradeVisitor_1_2.upgrade(new ClassReader(ProbePackageMigrator.migrate(fullData)));
    dataHolder = ProbeUpgradeVisitor_1_2.upgrade(new ClassReader(ProbePackageMigrator.migrate(dataHolder)));
  }
```

- [ ] **Step 5: Run the test to confirm it passes**

```bash
./gradlew :btrace-instr:test --tests "io.btrace.instr.ProbePackageMigratorTest" 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, 2 tests passing.

- [ ] **Step 6: Commit**

```bash
git add btrace-instr/src/main/java/io/btrace/instr/ProbePackageMigrator.java \
        btrace-instr/src/main/java/io/btrace/instr/BTraceProbePersisted.java \
        btrace-instr/src/test/java/io/btrace/instr/ProbePackageMigratorTest.java
git commit -m "feat(instr): add ProbePackageMigrator shim for transparent org.openjdk.btrace → io.btrace probe migration"
```

---

## Task 12: Verify Build Compiles and Apply Spotless

- [ ] **Step 1: Run spotless auto-format**

```bash
./gradlew spotlessApply
```

- [ ] **Step 2: Commit any reformatting**

```bash
git add -A
git diff --cached --stat
git commit -m "style: apply spotless formatting after repackaging" 2>/dev/null || echo "nothing to commit"
```

- [ ] **Step 3: Build all modules (skip tests)**

```bash
./gradlew build -x test
```
Expected: `BUILD SUCCESSFUL`

If build fails, read the error, identify the file, fix the missed reference, re-run.

- [ ] **Step 4: Commit any compilation fixes**

```bash
git add -A
git commit -m "fix: address compilation errors after repackaging"
```

---

## Task 13: Regenerate Golden Files and Run Full Test Suite

368/396 golden files reference the old package internally and need regeneration.

- [ ] **Step 1: Regenerate golden files**

```bash
./gradlew :btrace-instr:test -PupdateTestData
```

- [ ] **Step 2: Run full test suite**

```bash
./gradlew test
```
Expected: `BUILD SUCCESSFUL` with all tests passing.

If tests fail: check the error, fix residual `org.openjdk.btrace` references, re-run.

- [ ] **Step 3: Commit regenerated golden files**

```bash
git add btrace-instr/src/test/resources/instrumentorTestData/
git commit -m "test: regenerate golden files after org.openjdk.btrace → io.btrace repackaging"
```

- [ ] **Step 4: Run integration tests**

```bash
./gradlew :btrace-dist:build
./gradlew :integration-tests:test -Pintegration
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit any test fixes**

```bash
git add -A
git commit -m "fix: address test failures after repackaging"
```

---

## Self-Review Checklist

- [x] Java source content — Tasks 2 + 4 + 5 cover all source roots including multi-version (java9/11/15), jmh, test, probe scripts, and test resource Java files
- [x] Directory moves — Tasks 3 + 4
- [x] Build files — ✅ Already done (Tasks 1 / commits `7bb65e94`, `95e9a816`)
- [x] SPI service files — Task 6
- [x] MANIFEST.MF — Task 7
- [x] Maven plugin.xml — Task 7
- [x] Probe XML files — Task 8
- [x] Documentation — Task 9
- [x] Catch-all — Task 10
- [x] Backwards-compat shim — Task 11 (`ProbePackageMigrator`)
- [x] Compilation check + Spotless — Task 12
- [x] Golden file regeneration + full test run — Task 13
