# Extension TCK — Part 1: Structural Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `btrace-ext-validator` (shared validation logic) and the structural suite of `btrace-tck` (manifest, layout, service API, and API/impl partition checks), producing a working standalone JAR that can certify an extension ZIP without launching a JVM.

**Architecture:** Two new Gradle modules in the mono-repo: `btrace-ext-validator` (pure Java, ASM-based bytecode analysis) provides all validation logic; `btrace-tck` depends on it, wraps checks in a suite/engine abstraction, and writes JUnit XML + HTML + stdout reports. The Gradle plugin is updated to delegate `validateServiceApis` to `btrace-ext-validator` so the rule logic has a single home.

**Tech Stack:** Java 11 (toolchain), ASM 9.9.1, JUnit 5.11.4, Jackson 2.18.2, picocli 4.7.6

---

## File Map

### New: `btrace-ext-validator/`
| File | Responsibility |
|------|---------------|
| `build.gradle` | Module build config |
| `src/main/java/io/btrace/ext/validator/ValidationSeverity.java` | ERROR / WARN / INFO enum |
| `src/main/java/io/btrace/ext/validator/ValidationResult.java` | Single rule finding |
| `src/main/java/io/btrace/ext/validator/ManifestValidator.java` | BTRACE-MF-* manifest attribute checks |
| `src/main/java/io/btrace/ext/validator/ArtifactLayoutValidator.java` | BTRACE-LO-* ZIP structure checks |
| `src/main/java/io/btrace/ext/validator/ServiceApiValidator.java` | BTRACE-EXT-001..041 bytecode checks |
| `src/main/java/io/btrace/ext/validator/ApiImplPartitionValidator.java` | BTRACE-PT-* impl-leak-into-API checks |
| `src/test/java/io/btrace/ext/validator/*Test.java` | Per-validator unit tests |

### New: `btrace-tck/`
| File | Responsibility |
|------|---------------|
| `build.gradle` | Module build config |
| `src/main/java/io/btrace/tck/TckStatus.java` | PASS / FAIL / SKIP |
| `src/main/java/io/btrace/tck/TckResult.java` | One check outcome |
| `src/main/java/io/btrace/tck/TckSuiteResult.java` | All outcomes for one suite |
| `src/main/java/io/btrace/tck/TckInput.java` | Extension ZIP + btrace home + config path |
| `src/main/java/io/btrace/tck/TckConfig.java` | Parsed tck-config.yaml |
| `src/main/java/io/btrace/tck/TckEngine.java` | Orchestrates suites, returns results |
| `src/main/java/io/btrace/tck/suite/StructuralSuite.java` | Runs all structural checks |
| `src/main/java/io/btrace/tck/check/structural/ManifestCheck.java` | Delegates to ManifestValidator |
| `src/main/java/io/btrace/tck/check/structural/ArtifactLayoutCheck.java` | Delegates to ArtifactLayoutValidator |
| `src/main/java/io/btrace/tck/check/structural/ServiceApiCheck.java` | Delegates to ServiceApiValidator |
| `src/main/java/io/btrace/tck/check/structural/ApiImplPartitionCheck.java` | Delegates to ApiImplPartitionValidator |
| `src/main/java/io/btrace/tck/report/StdoutReporter.java` | Real-time [PASS]/[FAIL] lines |
| `src/main/java/io/btrace/tck/report/JUnitXmlReporter.java` | tck-results.xml |
| `src/main/java/io/btrace/tck/report/HtmlReporter.java` | tck-report.html |
| `src/main/java/io/btrace/tck/cli/TckMain.java` | Standalone JAR entry point |
| `src/test/resources/fixtures/good-extension/` | Valid extension ZIP fixture |
| `src/test/resources/fixtures/bad-manifest/` | Missing required manifest attributes |
| `src/test/resources/fixtures/bad-api/` | BTRACE-EXT-001 violation |
| `src/test/resources/fixtures/bad-partition/` | Impl class in api.jar |

### Modified
| File | Change |
|------|--------|
| `settings.gradle` | Add `include 'btrace-ext-validator'`, `include 'btrace-tck'` |
| `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceExtensionPlugin.groovy` | Delegate `validateServiceApis` to `ServiceApiValidator` |

---

## Task 1: btrace-ext-validator module skeleton

**Files:**
- Create: `btrace-ext-validator/build.gradle`
- Create: `btrace-ext-validator/src/main/java/io/btrace/ext/validator/ValidationSeverity.java`
- Create: `btrace-ext-validator/src/main/java/io/btrace/ext/validator/ValidationResult.java`
- Modify: `settings.gradle`

- [ ] **Step 1: Add module to settings.gradle**

Open `settings.gradle` and add after the last `include` line:
```groovy
include 'btrace-ext-validator'
```

- [ ] **Step 2: Create build.gradle**

Create `btrace-ext-validator/build.gradle`:
```groovy
apply from: rootProject.file('common.gradle')

java {
    toolchain { languageVersion = JavaLanguageVersion.of(11) }
}

dependencies {
    implementation 'org.ow2.asm:asm:9.9.1'
    implementation 'org.ow2.asm:asm-tree:9.9.1'
    testImplementation platform('org.junit:junit-bom:5.11.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test { useJUnitPlatform() }
```

- [ ] **Step 3: Write failing test**

Create `btrace-ext-validator/src/test/java/io/btrace/ext/validator/ValidationResultTest.java`:
```java
package io.btrace.ext.validator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidationResultTest {
    @Test
    void errorResultIsError() {
        var r = new ValidationResult("BTRACE-MF-001", ValidationSeverity.ERROR, "Id missing", "MANIFEST.MF");
        assertTrue(r.isError());
        assertEquals("BTRACE-MF-001", r.getRuleCode());
        assertEquals("MANIFEST.MF", r.getArtifact());
    }

    @Test
    void warnResultIsNotError() {
        var r = new ValidationResult("BTRACE-MF-004", ValidationSeverity.WARN, "Name blank", "MANIFEST.MF");
        assertFalse(r.isError());
    }

    @Test
    void nullArtifactBecomesEmpty() {
        var r = new ValidationResult("X", ValidationSeverity.INFO, "msg", null);
        assertEquals("", r.getArtifact());
    }
}
```

- [ ] **Step 4: Run test — expect compilation failure**

```bash
./gradlew :btrace-ext-validator:test 2>&1 | tail -20
```
Expected: compilation error (classes not yet created).

- [ ] **Step 5: Create ValidationSeverity.java**

```java
package io.btrace.ext.validator;

public enum ValidationSeverity { ERROR, WARN, INFO }
```

- [ ] **Step 6: Create ValidationResult.java**

```java
package io.btrace.ext.validator;

import java.util.Objects;

public final class ValidationResult {
    private final String ruleCode;
    private final ValidationSeverity severity;
    private final String message;
    private final String artifact;

    public ValidationResult(String ruleCode, ValidationSeverity severity, String message, String artifact) {
        this.ruleCode   = Objects.requireNonNull(ruleCode, "ruleCode");
        this.severity   = Objects.requireNonNull(severity, "severity");
        this.message    = Objects.requireNonNull(message, "message");
        this.artifact   = artifact != null ? artifact : "";
    }

    public String getRuleCode()         { return ruleCode; }
    public ValidationSeverity getSeverity() { return severity; }
    public String getMessage()          { return message; }
    public String getArtifact()         { return artifact; }
    public boolean isError()            { return severity == ValidationSeverity.ERROR; }

    @Override public String toString() {
        return "[" + severity + "] " + ruleCode + " " + message
             + (artifact.isEmpty() ? "" : " (" + artifact + ")");
    }
}
```

- [ ] **Step 7: Run tests — expect PASS**

```bash
./gradlew :btrace-ext-validator:test
```
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 8: Commit**

```bash
git add btrace-ext-validator/ settings.gradle
git commit -m "feat(tck): add btrace-ext-validator module skeleton"
```

---

## Task 2: ManifestValidator

**Files:**
- Create: `btrace-ext-validator/src/main/java/io/btrace/ext/validator/ManifestValidator.java`
- Create: `btrace-ext-validator/src/test/java/io/btrace/ext/validator/ManifestValidatorTest.java`

Manifest rule codes:
- `BTRACE-MF-001` ERROR — `BTrace-Extension-Id` missing or blank
- `BTRACE-MF-002` ERROR — `BTrace-Extension-Id` invalid format (must match `[a-zA-Z0-9][a-zA-Z0-9._-]*`)
- `BTRACE-MF-003` ERROR — `BTrace-Extension-Version` missing or blank
- `BTRACE-MF-004` WARN  — `BTrace-Extension-Name` missing or blank
- `BTRACE-MF-005` WARN  — `BTrace-Extension-Description` missing or blank
- `BTRACE-MF-006` ERROR — `BTrace-API-Version` missing or blank
- `BTRACE-MF-007` ERROR — `BTrace-API-Version` invalid (must match `\d+\.\d+(\.\d+)?[+]?` or range like `[2.3,3.0)`)
- `BTRACE-MF-008` WARN  — `BTrace-Java-Version` missing (defaults to `8+`, warn only)
- `BTRACE-MF-009` ERROR — `BTrace-Extension-Services` missing or blank
- `BTRACE-MF-010` ERROR — `BTrace-Extension-Impl` missing or blank

- [ ] **Step 1: Write failing tests**

Create `btrace-ext-validator/src/test/java/io/btrace/ext/validator/ManifestValidatorTest.java`:
```java
package io.btrace.ext.validator;

import org.junit.jupiter.api.Test;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ManifestValidatorTest {
    private static Manifest validManifest() {
        var m = new Manifest();
        var a = m.getMainAttributes();
        a.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        a.putValue("BTrace-Extension-Id",      "io.example.my-ext");
        a.putValue("BTrace-Extension-Version", "1.0.0");
        a.putValue("BTrace-Extension-Name",    "My Extension");
        a.putValue("BTrace-Extension-Description", "Does things");
        a.putValue("BTrace-API-Version",       "3.0+");
        a.putValue("BTrace-Java-Version",      "8+");
        a.putValue("BTrace-Extension-Services","io.example.MyService");
        a.putValue("BTrace-Extension-Impl",    "my-ext-1.0.0-impl.jar");
        return m;
    }

    @Test void validManifestProducesNoErrors() {
        var results = new ManifestValidator().validate(validManifest());
        assertTrue(results.stream().noneMatch(ValidationResult::isError),
            () -> "Unexpected errors: " + results);
    }

    @Test void missingIdIsError() {
        var m = validManifest();
        m.getMainAttributes().remove(new Attributes.Name("BTrace-Extension-Id"));
        var results = new ManifestValidator().validate(m);
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-MF-001") && r.isError()));
    }

    @Test void invalidIdFormatIsError() {
        var m = validManifest();
        m.getMainAttributes().putValue("BTrace-Extension-Id", "../etc/passwd");
        var results = new ManifestValidator().validate(m);
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-MF-002") && r.isError()));
    }

    @Test void missingVersionIsError() {
        var m = validManifest();
        m.getMainAttributes().remove(new Attributes.Name("BTrace-Extension-Version"));
        var results = new ManifestValidator().validate(m);
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-MF-003") && r.isError()));
    }

    @Test void missingServicesIsError() {
        var m = validManifest();
        m.getMainAttributes().remove(new Attributes.Name("BTrace-Extension-Services"));
        var results = new ManifestValidator().validate(m);
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-MF-009") && r.isError()));
    }

    @Test void missingImplIsError() {
        var m = validManifest();
        m.getMainAttributes().remove(new Attributes.Name("BTrace-Extension-Impl"));
        var results = new ManifestValidator().validate(m);
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-MF-010") && r.isError()));
    }

    @Test void missingNameIsWarnOnly() {
        var m = validManifest();
        m.getMainAttributes().remove(new Attributes.Name("BTrace-Extension-Name"));
        var results = new ManifestValidator().validate(m);
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-MF-004") && !r.isError()));
        assertTrue(results.stream().noneMatch(ValidationResult::isError));
    }
}
```

- [ ] **Step 2: Run tests — expect compilation failure**

```bash
./gradlew :btrace-ext-validator:test 2>&1 | tail -10
```
Expected: `ManifestValidator` not found.

- [ ] **Step 3: Implement ManifestValidator**

Create `btrace-ext-validator/src/main/java/io/btrace/ext/validator/ManifestValidator.java`:
```java
package io.btrace.ext.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

public final class ManifestValidator {
    private static final Pattern VALID_ID =
        Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]*");
    private static final Pattern VALID_API_VERSION =
        Pattern.compile("(\\d+\\.\\d+(\\.\\d+)?[+]?|[\\[\\(].*[\\]\\)])");

    public List<ValidationResult> validate(Manifest manifest) {
        var results = new ArrayList<ValidationResult>();
        var attrs   = manifest.getMainAttributes();

        check(results, attrs, "BTrace-Extension-Id", "BTRACE-MF-001", "BTRACE-MF-002",
              ValidationSeverity.ERROR, VALID_ID, true);
        requirePresent(results, attrs, "BTrace-Extension-Version", "BTRACE-MF-003", ValidationSeverity.ERROR);
        requirePresent(results, attrs, "BTrace-Extension-Name",    "BTRACE-MF-004", ValidationSeverity.WARN);
        requirePresent(results, attrs, "BTrace-Extension-Description", "BTRACE-MF-005", ValidationSeverity.WARN);
        check(results, attrs, "BTrace-API-Version", "BTRACE-MF-006", "BTRACE-MF-007",
              ValidationSeverity.ERROR, VALID_API_VERSION, true);
        requirePresent(results, attrs, "BTrace-Java-Version", "BTRACE-MF-008", ValidationSeverity.WARN);
        requirePresent(results, attrs, "BTrace-Extension-Services", "BTRACE-MF-009", ValidationSeverity.ERROR);
        requirePresent(results, attrs, "BTrace-Extension-Impl",     "BTRACE-MF-010", ValidationSeverity.ERROR);

        return results;
    }

    private void requirePresent(List<ValidationResult> out, Attributes attrs,
                                 String key, String code, ValidationSeverity sev) {
        String v = attrs.getValue(key);
        if (v == null || v.isBlank()) {
            out.add(new ValidationResult(code, sev,
                key + " is missing or blank", "MANIFEST.MF"));
        }
    }

    private void check(List<ValidationResult> out, Attributes attrs, String key,
                        String missingCode, String formatCode, ValidationSeverity sev,
                        Pattern pattern, boolean required) {
        String v = attrs.getValue(key);
        if (v == null || v.isBlank()) {
            if (required) out.add(new ValidationResult(missingCode, sev,
                key + " is missing or blank", "MANIFEST.MF"));
            return;
        }
        if (!pattern.matcher(v).matches()) {
            out.add(new ValidationResult(formatCode, sev,
                key + " has invalid format: " + v, "MANIFEST.MF"));
        }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :btrace-ext-validator:test
```
Expected: `BUILD SUCCESSFUL`, 8 tests passed.

- [ ] **Step 5: Commit**

```bash
git add btrace-ext-validator/
git commit -m "feat(tck): add ManifestValidator with BTRACE-MF-* rules"
```

---

## Task 3: ArtifactLayoutValidator

**Files:**
- Create: `btrace-ext-validator/src/main/java/io/btrace/ext/validator/ArtifactLayoutValidator.java`
- Create: `btrace-ext-validator/src/test/java/io/btrace/ext/validator/ArtifactLayoutValidatorTest.java`

Rule codes:
- `BTRACE-LO-001` ERROR — extension path does not exist or cannot be opened as ZIP
- `BTRACE-LO-002` ERROR — no file matching `*-api.jar` found in ZIP
- `BTRACE-LO-003` ERROR — api.jar has no readable MANIFEST.MF
- `BTRACE-LO-004` ERROR — `BTrace-Extension-Impl` in api.jar manifest doesn't match any entry in ZIP
- `BTRACE-LO-005` WARN  — impl JAR filename doesn't follow `{name}-{version}-impl.jar` convention

- [ ] **Step 1: Write failing test**

Create `btrace-ext-validator/src/test/java/io/btrace/ext/validator/ArtifactLayoutValidatorTest.java`:
```java
package io.btrace.ext.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactLayoutValidatorTest {
    @TempDir Path tmp;

    private Path buildValidZip() throws Exception {
        // Build api.jar
        var apiJar = tmp.resolve("my-ext-1.0.0-api.jar");
        try (var jos = new JarOutputStream(new FileOutputStream(apiJar.toFile()))) {
            var mf = new Manifest();
            mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            mf.getMainAttributes().putValue("BTrace-Extension-Id", "io.example.my-ext");
            mf.getMainAttributes().putValue("BTrace-Extension-Impl", "my-ext-1.0.0-impl.jar");
            var mfEntry = new JarEntry("META-INF/MANIFEST.MF");
            jos.putNextEntry(mfEntry);
            mf.write(jos);
            jos.closeEntry();
        }
        var implJar = tmp.resolve("my-ext-1.0.0-impl.jar");
        implJar.toFile().createNewFile();

        var zip = tmp.resolve("my-ext-1.0.0-extension.zip");
        try (var zos = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            for (Path p : List.of(apiJar, implJar)) {
                zos.putNextEntry(new ZipEntry(p.getFileName().toString()));
                Files.copy(p, zos);
                zos.closeEntry();
            }
        }
        return zip;
    }

    @Test void validZipProducesNoErrors() throws Exception {
        var results = new ArtifactLayoutValidator().validate(buildValidZip());
        assertTrue(results.stream().noneMatch(ValidationResult::isError),
            () -> "Unexpected errors: " + results);
    }

    @Test void missingApiJarIsError() throws Exception {
        var zip = tmp.resolve("empty.zip");
        new ZipOutputStream(new FileOutputStream(zip.toFile())).close();
        var results = new ArtifactLayoutValidator().validate(zip);
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-LO-002") && r.isError()));
    }

    @Test void missingImplJarIsError() throws Exception {
        var apiJar = tmp.resolve("my-ext-1.0.0-api.jar");
        try (var jos = new JarOutputStream(new FileOutputStream(apiJar.toFile()))) {
            var mf = new Manifest();
            mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            mf.getMainAttributes().putValue("BTrace-Extension-Impl", "my-ext-1.0.0-impl.jar");
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            mf.write(jos);
            jos.closeEntry();
        }
        var zip = tmp.resolve("no-impl.zip");
        try (var zos = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            zos.putNextEntry(new ZipEntry("my-ext-1.0.0-api.jar"));
            Files.copy(apiJar, zos);
            zos.closeEntry();
        }
        var results = new ArtifactLayoutValidator().validate(zip);
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-LO-004") && r.isError()));
    }

    @Test void nonExistentPathIsError() {
        var results = new ArtifactLayoutValidator().validate(tmp.resolve("nonexistent.zip"));
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-LO-001") && r.isError()));
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew :btrace-ext-validator:test 2>&1 | grep "error:"
```

- [ ] **Step 3: Implement ArtifactLayoutValidator**

Create `btrace-ext-validator/src/main/java/io/btrace/ext/validator/ArtifactLayoutValidator.java`:
```java
package io.btrace.ext.validator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public final class ArtifactLayoutValidator {

    public List<ValidationResult> validate(Path extensionZip) {
        var results = new ArrayList<ValidationResult>();
        if (!Files.exists(extensionZip)) {
            results.add(new ValidationResult("BTRACE-LO-001", ValidationSeverity.ERROR,
                "Extension ZIP not found: " + extensionZip, extensionZip.getFileName().toString()));
            return results;
        }
        try (var zf = new ZipFile(extensionZip.toFile())) {
            var entries = Collections.list(zf.entries()).stream()
                .map(ZipEntry::getName).toList();

            String apiEntry = entries.stream()
                .filter(e -> e.endsWith("-api.jar")).findFirst().orElse(null);
            if (apiEntry == null) {
                results.add(new ValidationResult("BTRACE-LO-002", ValidationSeverity.ERROR,
                    "No *-api.jar found in extension ZIP", extensionZip.getFileName().toString()));
                return results;
            }

            Manifest mf = readManifest(zf, apiEntry, results);
            if (mf == null) return results;

            String implName = mf.getMainAttributes().getValue("BTrace-Extension-Impl");
            if (implName == null || implName.isBlank()) {
                // ManifestValidator will catch this — just skip impl check here
                return results;
            }
            if (entries.stream().noneMatch(e -> e.equals(implName))) {
                results.add(new ValidationResult("BTRACE-LO-004", ValidationSeverity.ERROR,
                    "Impl JAR '" + implName + "' declared in manifest not found in ZIP",
                    extensionZip.getFileName().toString()));
            }
            if (!implName.endsWith("-impl.jar")) {
                results.add(new ValidationResult("BTRACE-LO-005", ValidationSeverity.WARN,
                    "Impl JAR name should follow '{name}-{version}-impl.jar' convention: " + implName,
                    implName));
            }
        } catch (IOException e) {
            results.add(new ValidationResult("BTRACE-LO-001", ValidationSeverity.ERROR,
                "Cannot open extension ZIP: " + e.getMessage(), extensionZip.getFileName().toString()));
        }
        return results;
    }

    private Manifest readManifest(ZipFile zf, String apiEntry, List<ValidationResult> results) {
        var ze = zf.getEntry(apiEntry);
        try (var apiIs = zf.getInputStream(ze);
             var jis  = new JarInputStream(apiIs)) {
            Manifest mf = jis.getManifest();
            if (mf == null) {
                results.add(new ValidationResult("BTRACE-LO-003", ValidationSeverity.ERROR,
                    "api.jar has no MANIFEST.MF", apiEntry));
                return null;
            }
            return mf;
        } catch (IOException e) {
            results.add(new ValidationResult("BTRACE-LO-003", ValidationSeverity.ERROR,
                "Cannot read api.jar manifest: " + e.getMessage(), apiEntry));
            return null;
        }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :btrace-ext-validator:test
```
Expected: `BUILD SUCCESSFUL`, all tests passed.

- [ ] **Step 5: Commit**

```bash
git add btrace-ext-validator/
git commit -m "feat(tck): add ArtifactLayoutValidator with BTRACE-LO-* rules"
```

---

## Task 4: ServiceApiValidator

**Files:**
- Create: `btrace-ext-validator/src/main/java/io/btrace/ext/validator/ServiceApiValidator.java`
- Create: `btrace-ext-validator/src/test/java/io/btrace/ext/validator/ServiceApiValidatorTest.java`

Implements BTRACE-EXT-001, 002, 003, 010, 013, 020, 021, 022, 041 via pure ASM (no classloading).

Forbidden type prefixes for BTRACE-EXT-013:
`java/io/`, `java/net/`, `java/nio/channels/`, `java/lang/reflect/`

Nullable annotation descriptors (default):
`Ljavax/annotation/Nullable;`, `Lorg/jspecify/annotations/Nullable;`, `Lorg/jetbrains/annotations/Nullable;`, `Ljakarta/annotation/Nullable;`

Nonnull annotation descriptors (default):
`Ljavax/annotation/Nonnull;`, `Lorg/jspecify/annotations/NonNull;`, `Lorg/jetbrains/annotations/NotNull;`, `Ljakarta/annotation/Nonnull;`

- [ ] **Step 1: Write failing tests**

Create `btrace-ext-validator/src/test/java/io/btrace/ext/validator/ServiceApiValidatorTest.java`:
```java
package io.btrace.ext.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.Opcodes;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ServiceApiValidatorTest {
    @TempDir Path tmp;

    /** Write a class file to a temporary api.jar and return the jar path. */
    private Path apiJarWith(String className, byte[] classBytes) throws Exception {
        var jar = tmp.resolve("test-api.jar");
        try (var jos = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
            var mf = new Manifest();
            mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            mf.getMainAttributes().putValue("BTrace-Extension-Services",
                className.replace('/', '.'));
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            mf.write(jos);
            jos.closeEntry();
            jos.putNextEntry(new JarEntry(className + ".class"));
            jos.write(classBytes);
            jos.closeEntry();
        }
        return jar;
    }

    private byte[] publicInterface(String name) {
        var cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
            name, null, "java/lang/Object", null);
        // add one method with @Nullable return
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "doWork", "()Ljava/lang/String;", null, null);
        mv.visitAnnotation("Ljavax/annotation/Nullable;", true).visitEnd();
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] classNotInterface(String name) {
        var cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] interfaceWithForbiddenParam(String name) {
        var cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
            name, null, "java/lang/Object", null);
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "read", "(Ljava/io/InputStream;)V", null, null);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test void validInterfaceProducesNoErrors() throws Exception {
        var jar = apiJarWith("io/example/MyService", publicInterface("io/example/MyService"));
        var results = new ServiceApiValidator().validate(jar);
        assertTrue(results.stream().noneMatch(ValidationResult::isError),
            () -> "Unexpected errors: " + results);
    }

    @Test void classInsteadOfInterfaceIsExt001() throws Exception {
        var jar = apiJarWith("io/example/MyService", classNotInterface("io/example/MyService"));
        var results = new ServiceApiValidator().validate(jar);
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-EXT-001") && r.isError()));
    }

    @Test void forbiddenParamTypeIsExt013() throws Exception {
        var jar = apiJarWith("io/example/MyService", interfaceWithForbiddenParam("io/example/MyService"));
        var results = new ServiceApiValidator().validate(jar);
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-EXT-013") && r.isError()));
    }

    @Test void serviceClassMissingFromJarIsExt041() throws Exception {
        // Manifest declares a service that isn't in the JAR
        var jar = tmp.resolve("missing-service.jar");
        try (var jos = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
            var mf = new Manifest();
            mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            mf.getMainAttributes().putValue("BTrace-Extension-Services", "io.example.Ghost");
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            mf.write(jos);
            jos.closeEntry();
        }
        var results = new ServiceApiValidator().validate(jar);
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-EXT-041") && r.isError()));
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew :btrace-ext-validator:test 2>&1 | grep "error:"
```

- [ ] **Step 3: Implement ServiceApiValidator**

Create `btrace-ext-validator/src/main/java/io/btrace/ext/validator/ServiceApiValidator.java`:
```java
package io.btrace.ext.validator;

import org.objectweb.asm.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;

public final class ServiceApiValidator {

    private static final Set<String> FORBIDDEN_PREFIXES = Set.of(
        "java/io/", "java/net/", "java/nio/channels/", "java/lang/reflect/");

    private static final Set<String> NULLABLE_DESCS = Set.of(
        "Ljavax/annotation/Nullable;", "Lorg/jspecify/annotations/Nullable;",
        "Lorg/jetbrains/annotations/Nullable;", "Ljakarta/annotation/Nullable;");

    private static final Set<String> NONNULL_DESCS = Set.of(
        "Ljavax/annotation/Nonnull;", "Lorg/jspecify/annotations/NonNull;",
        "Lorg/jetbrains/annotations/NotNull;", "Ljakarta/annotation/Nonnull;");

    public List<ValidationResult> validate(Path apiJar) {
        var results = new ArrayList<ValidationResult>();
        try (var jf = new JarFile(apiJar.toFile())) {
            var mf = jf.getManifest();
            if (mf == null) {
                results.add(err("BTRACE-EXT-041", "api.jar has no MANIFEST.MF", apiJar.getFileName().toString()));
                return results;
            }
            String svcAttr = mf.getMainAttributes().getValue("BTrace-Extension-Services");
            if (svcAttr == null || svcAttr.isBlank()) return results;

            var declaredServices = Arrays.stream(svcAttr.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.replace('.', '/')).toList();

            var classEntries = new HashMap<String, byte[]>();
            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
                    classEntries.put(
                        entry.getName().replace('/', '.').replaceAll("\\.class$", ""),
                        jf.getInputStream(entry).readAllBytes());
                }
            }

            for (String svcInternal : declaredServices) {
                String svcFqcn = svcInternal.replace('/', '.');
                byte[] classBytes = classEntries.get(svcFqcn);
                if (classBytes == null) {
                    results.add(err("BTRACE-EXT-041",
                        "Declared service '" + svcFqcn + "' not found in api.jar", apiJar.getFileName().toString()));
                    continue;
                }
                validateServiceClass(svcFqcn, classBytes, results);
            }
        } catch (IOException e) {
            results.add(err("BTRACE-EXT-041", "Cannot read api.jar: " + e.getMessage(), apiJar.getFileName().toString()));
        }
        return results;
    }

    private void validateServiceClass(String fqcn, byte[] bytes, List<ValidationResult> results) {
        var cr = new ClassReader(bytes);
        var cn = new ClassNode();
        cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);

        boolean isInterface  = (cn.access & Opcodes.ACC_INTERFACE) != 0;
        boolean isPublic     = (cn.access & Opcodes.ACC_PUBLIC) != 0;
        boolean isTopLevel   = cn.outerClass == null;

        if (!isInterface || !isPublic || !isTopLevel) {
            results.add(err("BTRACE-EXT-001",
                "Service '" + fqcn + "' must be a public, top-level interface", fqcn));
        }

        for (FieldNode f : cn.fields) {
            boolean isConst = (f.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL))
                           == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);
            if (!isConst) {
                results.add(err("BTRACE-EXT-003",
                    "Non-constant field '" + f.name + "' in service '" + fqcn + "'", fqcn));
            }
        }

        for (MethodNode mn : cn.methods) {
            if ("<clinit>".equals(mn.name)) continue;
            boolean isDefault = !((mn.access & Opcodes.ACC_ABSTRACT) != 0);
            boolean isPrivate  = (mn.access & Opcodes.ACC_PRIVATE) != 0;
            if (isDefault || isPrivate) {
                results.add(err("BTRACE-EXT-002",
                    "Method '" + mn.name + "' in '" + fqcn + "' must not be default or private", fqcn));
            }
            checkExceptions(fqcn, mn, results);
            checkSignatureForForbiddenTypes(fqcn, mn, results);
            checkNullability(fqcn, mn, results);
        }
    }

    private void checkExceptions(String fqcn, MethodNode mn, List<ValidationResult> results) {
        if (mn.exceptions == null) return;
        for (String ex : mn.exceptions) {
            if (!isUnchecked(ex)) {
                results.add(err("BTRACE-EXT-010",
                    "Method '" + mn.name + "' in '" + fqcn + "' throws checked exception: " + ex.replace('/', '.'), fqcn));
            }
        }
    }

    private boolean isUnchecked(String internalName) {
        return internalName.startsWith("java/lang/RuntimeException")
            || internalName.startsWith("java/lang/Error")
            || internalName.equals("java/lang/Throwable");
    }

    private void checkSignatureForForbiddenTypes(String fqcn, MethodNode mn, List<ValidationResult> results) {
        var desc = mn.desc;
        for (Type t : getAllTypes(desc)) {
            if (t.getSort() == Type.OBJECT) {
                String internalName = t.getInternalName();
                for (String prefix : FORBIDDEN_PREFIXES) {
                    if (internalName.startsWith(prefix)) {
                        results.add(err("BTRACE-EXT-013",
                            "Method '" + mn.name + "' in '" + fqcn + "' uses forbidden type: "
                            + internalName.replace('/', '.'), fqcn));
                    }
                }
            }
        }
    }

    private List<Type> getAllTypes(String methodDesc) {
        var types = new ArrayList<Type>();
        try {
            types.addAll(Arrays.asList(Type.getArgumentTypes(methodDesc)));
            Type ret = Type.getReturnType(methodDesc);
            if (ret.getSort() != Type.VOID) types.add(ret);
        } catch (Exception ignored) {}
        return types;
    }

    private void checkNullability(String fqcn, MethodNode mn, List<ValidationResult> results) {
        Type returnType = Type.getReturnType(mn.desc);
        boolean voidReturn = returnType.getSort() == Type.VOID;
        boolean primitiveReturn = returnType.getSort() != Type.OBJECT && returnType.getSort() != Type.ARRAY;

        if (!voidReturn && !primitiveReturn) {
            boolean annotated = hasAnnotation(mn.visibleAnnotations, NULLABLE_DESCS)
                             || hasAnnotation(mn.visibleAnnotations, NONNULL_DESCS)
                             || hasAnnotation(mn.invisibleAnnotations, NULLABLE_DESCS)
                             || hasAnnotation(mn.invisibleAnnotations, NONNULL_DESCS);
            if (!annotated) {
                results.add(warn("BTRACE-EXT-020",
                    "Return type of '" + mn.name + "' in '" + fqcn + "' lacks @Nullable/@Nonnull", fqcn));
            }
            if (returnType.getSort() == Type.OBJECT) {
                boolean isInterface = returnType.getInternalName().contains("/");
                boolean isNullable = hasAnnotation(mn.visibleAnnotations, NULLABLE_DESCS)
                                  || hasAnnotation(mn.invisibleAnnotations, NULLABLE_DESCS);
                if (!isNullable) {
                    results.add(err("BTRACE-EXT-022",
                        "Interface return type of '" + mn.name + "' in '" + fqcn + "' must be @Nullable", fqcn));
                }
            }
        }

        Type[] argTypes = Type.getArgumentTypes(mn.desc);
        List<AnnotationNode>[] paramAnnotations = mn.visibleParameterAnnotations;
        for (int i = 0; i < argTypes.length; i++) {
            if (argTypes[i].getSort() == Type.OBJECT || argTypes[i].getSort() == Type.ARRAY) {
                List<AnnotationNode> annots = (paramAnnotations != null && i < paramAnnotations.length)
                    ? paramAnnotations[i] : null;
                boolean annotated = hasAnnotation(annots, NULLABLE_DESCS)
                                 || hasAnnotation(annots, NONNULL_DESCS);
                if (!annotated) {
                    results.add(warn("BTRACE-EXT-021",
                        "Parameter " + i + " of '" + mn.name + "' in '" + fqcn + "' lacks @Nullable/@Nonnull", fqcn));
                }
            }
        }
    }

    private boolean hasAnnotation(List<AnnotationNode> annots, Set<String> descs) {
        if (annots == null) return false;
        return annots.stream().anyMatch(a -> descs.contains(a.desc));
    }

    private static ValidationResult err(String code, String msg, String artifact) {
        return new ValidationResult(code, ValidationSeverity.ERROR, msg, artifact);
    }

    private static ValidationResult warn(String code, String msg, String artifact) {
        return new ValidationResult(code, ValidationSeverity.WARN, msg, artifact);
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :btrace-ext-validator:test
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add btrace-ext-validator/
git commit -m "feat(tck): add ServiceApiValidator with BTRACE-EXT-* rules"
```

---

## Task 5: ApiImplPartitionValidator

**Files:**
- Create: `btrace-ext-validator/src/main/java/io/btrace/ext/validator/ApiImplPartitionValidator.java`
- Create: `btrace-ext-validator/src/test/java/io/btrace/ext/validator/ApiImplPartitionValidatorTest.java`

Rule codes:
- `BTRACE-PT-001` ERROR — impl-only class appears in an api.jar method signature

- [ ] **Step 1: Write failing test**

Create `btrace-ext-validator/src/test/java/io/btrace/ext/validator/ApiImplPartitionValidatorTest.java`:
```java
package io.btrace.ext.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.Opcodes;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiImplPartitionValidatorTest {
    @TempDir Path tmp;

    private Path buildApiJar(String serviceClass, byte[] serviceBytes,
                              String... extraClasses) throws Exception {
        var jar = tmp.resolve("api.jar");
        try (var jos = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
            var mf = new Manifest();
            mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            mf.getMainAttributes().putValue("BTrace-Extension-Services", serviceClass.replace('/', '.'));
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            mf.write(jos);
            jos.closeEntry();
            jos.putNextEntry(new JarEntry(serviceClass + ".class"));
            jos.write(serviceBytes);
            jos.closeEntry();
        }
        return jar;
    }

    private Path buildImplJar(String implClass) throws Exception {
        var jar = tmp.resolve("impl.jar");
        try (var jos = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
            var cw = new ClassWriter(0);
            cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, implClass, null, "java/lang/Object", null);
            cw.visitEnd();
            jos.putNextEntry(new JarEntry(implClass + ".class"));
            jos.write(cw.toByteArray());
            jos.closeEntry();
        }
        return jar;
    }

    @Test void cleanPartitionProducesNoErrors() throws Exception {
        var cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
            "io/example/MyService", null, "java/lang/Object", null);
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "doWork", "()V", null, null).visitEnd();
        cw.visitEnd();

        var api  = buildApiJar("io/example/MyService", cw.toByteArray());
        var impl = buildImplJar("io/example/internal/MyServiceImpl");
        var results = new ApiImplPartitionValidator().validate(api, impl);
        assertTrue(results.stream().noneMatch(ValidationResult::isError));
    }

    @Test void implTypeInApiSignatureIsError() throws Exception {
        var cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
            "io/example/MyService", null, "java/lang/Object", null);
        // Method that returns an impl-only type
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "getImpl", "()Lio/example/internal/MyServiceImpl;", null, null).visitEnd();
        cw.visitEnd();

        var api  = buildApiJar("io/example/MyService", cw.toByteArray());
        var impl = buildImplJar("io/example/internal/MyServiceImpl");
        var results = new ApiImplPartitionValidator().validate(api, impl);
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("BTRACE-PT-001") && r.isError()));
    }
}
```

- [ ] **Step 2: Implement ApiImplPartitionValidator**

Create `btrace-ext-validator/src/main/java/io/btrace/ext/validator/ApiImplPartitionValidator.java`:
```java
package io.btrace.ext.validator;

import org.objectweb.asm.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;

public final class ApiImplPartitionValidator {

    public List<ValidationResult> validate(Path apiJar, Path implJar) {
        var results  = new ArrayList<ValidationResult>();
        var implTypes = collectClassNames(implJar, results);
        if (!results.isEmpty()) return results;

        try (var jf = new JarFile(apiJar.toFile())) {
            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.isDirectory()) continue;
                byte[] bytes = jf.getInputStream(entry).readAllBytes();
                var cr = new ClassReader(bytes);
                var cn = new ClassNode();
                cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                String apiClass = cn.name.replace('/', '.');
                for (MethodNode mn : cn.methods) {
                    checkDesc(apiClass, mn.name, mn.desc, implTypes, results);
                }
            }
        } catch (IOException e) {
            results.add(new ValidationResult("BTRACE-PT-001", ValidationSeverity.ERROR,
                "Cannot read api.jar: " + e.getMessage(), apiJar.getFileName().toString()));
        }
        return results;
    }

    private Set<String> collectClassNames(Path jar, List<ValidationResult> errors) {
        var names = new HashSet<String>();
        try (var jf = new JarFile(jar.toFile())) {
            var entries = jf.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
                    names.add(entry.getName().replace('/', '.').replaceAll("\\.class$", ""));
                }
            }
        } catch (IOException e) {
            errors.add(new ValidationResult("BTRACE-PT-001", ValidationSeverity.ERROR,
                "Cannot read impl.jar: " + e.getMessage(), jar.getFileName().toString()));
        }
        return names;
    }

    private void checkDesc(String apiClass, String method, String desc,
                            Set<String> implTypes, List<ValidationResult> results) {
        for (Type t : allTypes(desc)) {
            if (t.getSort() == Type.OBJECT) {
                String fqcn = t.getClassName();
                if (implTypes.contains(fqcn)) {
                    results.add(new ValidationResult("BTRACE-PT-001", ValidationSeverity.ERROR,
                        "Impl-only type '" + fqcn + "' appears in API method '"
                        + apiClass + "." + method + "'", apiClass));
                }
            }
        }
    }

    private List<Type> allTypes(String desc) {
        var out = new ArrayList<Type>();
        try {
            out.addAll(Arrays.asList(Type.getArgumentTypes(desc)));
            var ret = Type.getReturnType(desc);
            if (ret.getSort() != Type.VOID) out.add(ret);
        } catch (Exception ignored) {}
        return out;
    }
}
```

- [ ] **Step 3: Run tests — expect PASS**

```bash
./gradlew :btrace-ext-validator:test
```

- [ ] **Step 4: Commit**

```bash
git add btrace-ext-validator/
git commit -m "feat(tck): add ApiImplPartitionValidator with BTRACE-PT-001"
```

---

## Task 6: Wire btrace-ext-validator into Gradle plugin

**Files:**
- Modify: `btrace-gradle-plugin/build.gradle`
- Modify: `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceExtensionPlugin.groovy`

The goal: replace the inline ASM bytecode analysis in `validateServiceApis` with a call to `ServiceApiValidator`. The Groovy task body shrinks to: iterate class dirs, call validator, translate `ValidationResult` list to Gradle warnings/errors.

- [ ] **Step 1: Add dependency in plugin build.gradle**

In `btrace-gradle-plugin/build.gradle`, add to `dependencies`:
```groovy
implementation project(':btrace-ext-validator')
```

Also add to `settings.gradle` (the plugin included build needs the module on the classpath — since `btrace-gradle-plugin` is an `includeBuild`, add the dependency resolution):
```groovy
// in btrace-gradle-plugin/settings.gradle (create if absent)
includeBuild '..'   // gives access to root project's modules
```

> **Note:** The included build resolution for `project(':btrace-ext-validator')` may require using `files(...)` pointing to the built JAR if Gradle's composite build doesn't resolve cross-project dependencies here. Check by running `./gradlew :btrace-gradle-plugin:dependencies` and adjust if needed — substitution with `implementation(files('../btrace-ext-validator/build/libs/btrace-ext-validator.jar'))` is acceptable as a fallback.

- [ ] **Step 2: Refactor validateServiceApis task**

In `BTraceExtensionPlugin.groovy`, find the `validateServiceApis` task definition (around line 196) and replace its `doLast` body with:

```groovy
def validateServiceApis = project.tasks.register('validateServiceApis') {
    dependsOn(authoredCompileTask)
    doLast {
        def apiJarFile = buildApiJar.get().archiveFile.get().asFile.toPath()
        if (!apiJarFile.toFile().exists()) {
            project.logger.warn('[BTRACE-EXT] api.jar not built yet; skipping validation')
            return
        }
        def validator = new io.btrace.ext.validator.ServiceApiValidator()
        def results   = validator.validate(apiJarFile)
        def sev = (project.extensions.findByType(BTraceExtensionMetadata)?.nullabilitySeverity ?: 'warn')
        results.each { r ->
            def line = "[${r.ruleCode}] ${r.message} (${r.artifact})"
            if (r.isError() || "error".equalsIgnoreCase(sev)) {
                throw new GradleException("[BTRACE-EXT] Validation failed: $line")
            } else {
                project.logger.warn("[BTRACE-EXT] $line")
            }
        }
    }
}
```

- [ ] **Step 3: Verify plugin tests still pass**

```bash
./gradlew :btrace-gradle-plugin:test
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run full check on btrace-contracts to verify end-to-end**

```bash
./gradlew :btrace-extensions:btrace-contracts:check
```
Expected: `BUILD SUCCESSFUL` with no new warnings.

- [ ] **Step 5: Commit**

```bash
git add btrace-gradle-plugin/
git commit -m "refactor(tck): delegate validateServiceApis to ServiceApiValidator"
```

---

## Task 7: btrace-tck module skeleton

**Files:**
- Create: `btrace-tck/build.gradle`
- Create: `btrace-tck/src/main/java/io/btrace/tck/TckStatus.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/TckResult.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/TckSuiteResult.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/TckInput.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/TckEngine.java`
- Modify: `settings.gradle`

- [ ] **Step 1: Add to settings.gradle**

```groovy
include 'btrace-tck'
```

- [ ] **Step 2: Create btrace-tck/build.gradle**

```groovy
apply from: rootProject.file('common.gradle')

java {
    toolchain { languageVersion = JavaLanguageVersion.of(11) }
}

jar {
    manifest { attributes 'Main-Class': 'io.btrace.tck.cli.TckMain' }
    from { configurations.runtimeClasspath.collect { it.isDirectory() ? it : zipTree(it) } }
    duplicatesStrategy = 'exclude'
}

dependencies {
    implementation project(':btrace-ext-validator')
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.2'
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2'
    implementation 'info.picocli:picocli:4.7.6'
    testImplementation platform('org.junit:junit-bom:5.11.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test { useJUnitPlatform() }
```

- [ ] **Step 3: Write failing test**

Create `btrace-tck/src/test/java/io/btrace/tck/TckEngineTest.java`:
```java
package io.btrace.tck;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TckEngineTest {
    @Test void engineWithNoSuitesReturnsEmptyResults() {
        var engine = new TckEngine(List.of());
        var input  = TckInput.builder().build();
        var results = engine.run(input);
        assertTrue(results.isEmpty());
    }

    @Test void skipsDownstreamSuiteOnUpstreamFailure() {
        var failSuite = new StubSuite("fail", TckStatus.FAIL);
        var skipSuite = new StubSuite("skip", TckStatus.PASS);
        var engine = new TckEngine(List.of(failSuite, skipSuite));
        var results = engine.run(TckInput.builder().build());
        assertEquals(2, results.size());
        assertEquals(TckStatus.FAIL, results.get(0).overallStatus());
        assertEquals(TckStatus.SKIP, results.get(1).overallStatus());
    }

    private record StubSuite(String name, TckStatus status) implements Suite {
        public TckSuiteResult run(TckInput input) {
            return new TckSuiteResult(name, List.of(
                new TckResult(name, "check", status, null, null)));
        }
        public String name() { return name; }
    }
}
```

- [ ] **Step 4: Create core TCK types**

`TckStatus.java`:
```java
package io.btrace.tck;
public enum TckStatus { PASS, FAIL, SKIP }
```

`TckResult.java`:
```java
package io.btrace.tck;

public record TckResult(
    String suiteName,
    String checkName,
    TckStatus status,
    String ruleCode,    // nullable — present on FAIL
    String message      // nullable
) {
    public boolean isFail() { return status == TckStatus.FAIL; }
}
```

`TckSuiteResult.java`:
```java
package io.btrace.tck;

import java.util.List;

public record TckSuiteResult(String suiteName, List<TckResult> checks) {
    public TckStatus overallStatus() {
        if (checks.isEmpty()) return TckStatus.PASS;
        if (checks.stream().anyMatch(r -> r.status() == TckStatus.SKIP)) return TckStatus.SKIP;
        return checks.stream().anyMatch(TckResult::isFail) ? TckStatus.FAIL : TckStatus.PASS;
    }
    public boolean hasFailed() { return overallStatus() == TckStatus.FAIL; }
}
```

`TckInput.java`:
```java
package io.btrace.tck;

import java.nio.file.Path;

public final class TckInput {
    private final Path extensionZip;
    private final Path btraceHome;
    private final Path tckConfig;
    private final Path reportDir;

    private TckInput(Builder b) {
        this.extensionZip = b.extensionZip;
        this.btraceHome   = b.btraceHome;
        this.tckConfig    = b.tckConfig;
        this.reportDir    = b.reportDir;
    }

    public Path extensionZip() { return extensionZip; }
    public Path btraceHome()   { return btraceHome; }
    public Path tckConfig()    { return tckConfig; }
    public Path reportDir()    { return reportDir; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Path extensionZip;
        private Path btraceHome;
        private Path tckConfig;
        private Path reportDir = Path.of("tck-report");

        public Builder extensionZip(Path p) { this.extensionZip = p; return this; }
        public Builder btraceHome(Path p)   { this.btraceHome = p;   return this; }
        public Builder tckConfig(Path p)    { this.tckConfig = p;    return this; }
        public Builder reportDir(Path p)    { this.reportDir = p;    return this; }
        public TckInput build()             { return new TckInput(this); }
    }
}
```

`Suite.java` (interface):
```java
package io.btrace.tck;
public interface Suite {
    String name();
    TckSuiteResult run(TckInput input);
}
```

`TckEngine.java`:
```java
package io.btrace.tck;

import java.util.ArrayList;
import java.util.List;

public final class TckEngine {
    private final List<Suite> suites;

    public TckEngine(List<Suite> suites) {
        this.suites = List.copyOf(suites);
    }

    public List<TckSuiteResult> run(TckInput input) {
        var results = new ArrayList<TckSuiteResult>();
        boolean failed = false;
        for (Suite suite : suites) {
            if (failed) {
                results.add(skipSuite(suite.name()));
            } else {
                var result = suite.run(input);
                results.add(result);
                if (result.hasFailed()) failed = true;
            }
        }
        return results;
    }

    private TckSuiteResult skipSuite(String name) {
        return new TckSuiteResult(name, List.of(
            new TckResult(name, "*", TckStatus.SKIP, null,
                "Upstream suite failed — skipped")));
    }
}
```

- [ ] **Step 5: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add btrace-tck/ settings.gradle
git commit -m "feat(tck): add btrace-tck module skeleton with TckEngine"
```

---

## Task 8: Reporting infrastructure

**Files:**
- Create: `btrace-tck/src/main/java/io/btrace/tck/report/StdoutReporter.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/report/JUnitXmlReporter.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/report/HtmlReporter.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/report/ReporterTest.java`

- [ ] **Step 1: Write failing tests**

Create `btrace-tck/src/test/java/io/btrace/tck/report/ReporterTest.java`:
```java
package io.btrace.tck.report;

import io.btrace.tck.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReporterTest {
    private List<TckSuiteResult> sampleResults() {
        return List.of(
            new TckSuiteResult("structural", List.of(
                new TckResult("structural", "ManifestCheck", TckStatus.PASS, null, null),
                new TckResult("structural", "ServiceApiCheck", TckStatus.FAIL,
                    "BTRACE-EXT-001", "Service must be interface"))),
            new TckSuiteResult("lifecycle", List.of(
                new TckResult("lifecycle", "*", TckStatus.SKIP, null, "Upstream failed"))));
    }

    @Test void stdoutReporterContainsPassFailSkip() {
        var sw = new StringWriter();
        new StdoutReporter(new PrintWriter(sw)).report(sampleResults());
        var out = sw.toString();
        assertTrue(out.contains("[PASS]"));
        assertTrue(out.contains("[FAIL]"));
        assertTrue(out.contains("[SKIP]"));
        assertTrue(out.contains("BTRACE-EXT-001"));
    }

    @Test void junitXmlContainsTestcases(@TempDir Path tmp) throws Exception {
        var out = tmp.resolve("results.xml");
        new JUnitXmlReporter(out).report(sampleResults());
        var xml = Files.readString(out);
        assertTrue(xml.contains("<testsuites"));
        assertTrue(xml.contains("<testsuite"));
        assertTrue(xml.contains("<testcase"));
        assertTrue(xml.contains("<failure"));
        assertTrue(xml.contains("BTRACE-EXT-001"));
    }

    @Test void htmlContainsSummaryTable(@TempDir Path tmp) throws Exception {
        var out = tmp.resolve("report.html");
        new HtmlReporter(out).report(sampleResults());
        var html = Files.readString(out);
        assertTrue(html.contains("<html"));
        assertTrue(html.contains("PASS"));
        assertTrue(html.contains("FAIL"));
        assertTrue(html.contains("BTRACE-EXT-001"));
    }
}
```

- [ ] **Step 2: Implement StdoutReporter**

Create `btrace-tck/src/main/java/io/btrace/tck/report/StdoutReporter.java`:
```java
package io.btrace.tck.report;

import io.btrace.tck.*;
import java.io.PrintWriter;
import java.util.List;

public final class StdoutReporter {
    private final PrintWriter out;

    public StdoutReporter(PrintWriter out) { this.out = out; }
    public StdoutReporter()               { this(new PrintWriter(System.out, true)); }

    public void report(List<TckSuiteResult> suites) {
        long fails = 0, skips = 0;
        for (var suite : suites) {
            for (var check : suite.checks()) {
                String tag = switch (check.status()) {
                    case PASS -> "[PASS]";
                    case FAIL -> "[FAIL]";
                    case SKIP -> "[SKIP]";
                };
                String msg = suite.suiteName() + "/" + check.checkName();
                if (check.ruleCode() != null) msg += "  " + check.ruleCode();
                if (check.message()  != null) msg += " " + check.message();
                out.println(tag + " " + msg);
                if (check.isFail()) fails++;
                if (check.status() == TckStatus.SKIP) skips++;
            }
        }
        out.println();
        boolean passed = fails == 0;
        out.printf("TCK %s  %d failure(s), %d suite(s) skipped%n",
            passed ? "PASSED" : "FAILED", fails, skips);
    }
}
```

- [ ] **Step 3: Implement JUnitXmlReporter**

Create `btrace-tck/src/main/java/io/btrace/tck/report/JUnitXmlReporter.java`:
```java
package io.btrace.tck.report;

import io.btrace.tck.*;
import java.io.*;
import java.nio.file.Path;
import java.util.List;

public final class JUnitXmlReporter {
    private final Path output;

    public JUnitXmlReporter(Path output) { this.output = output; }

    public void report(List<TckSuiteResult> suites) throws IOException {
        output.getParent().toFile().mkdirs();
        try (var w = new PrintWriter(new FileWriter(output.toFile()))) {
            w.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            w.println("<testsuites>");
            for (var suite : suites) {
                long failures = suite.checks().stream().filter(TckResult::isFail).count();
                long skipped  = suite.checks().stream()
                    .filter(r -> r.status() == TckStatus.SKIP).count();
                w.printf("  <testsuite name=\"%s\" tests=\"%d\" failures=\"%d\" skipped=\"%d\">%n",
                    esc(suite.suiteName()), suite.checks().size(), failures, skipped);
                for (var check : suite.checks()) {
                    w.printf("    <testcase name=\"%s\" classname=\"%s\">%n",
                        esc(check.checkName()), esc(suite.suiteName()));
                    if (check.isFail()) {
                        w.printf("      <failure type=\"%s\" message=\"%s\"/>%n",
                            esc(check.ruleCode() != null ? check.ruleCode() : "FAIL"),
                            esc(check.message() != null ? check.message() : ""));
                    } else if (check.status() == TckStatus.SKIP) {
                        w.println("      <skipped/>");
                    }
                    w.println("    </testcase>");
                }
                w.println("  </testsuite>");
            }
            w.println("</testsuites>");
        }
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;");
    }
}
```

- [ ] **Step 4: Implement HtmlReporter**

Create `btrace-tck/src/main/java/io/btrace/tck/report/HtmlReporter.java`:
```java
package io.btrace.tck.report;

import io.btrace.tck.*;
import java.io.*;
import java.nio.file.Path;
import java.util.List;

public final class HtmlReporter {
    private final Path output;

    public HtmlReporter(Path output) { this.output = output; }

    public void report(List<TckSuiteResult> suites) throws IOException {
        output.getParent().toFile().mkdirs();
        try (var w = new PrintWriter(new FileWriter(output.toFile()))) {
            w.println("""
                <!DOCTYPE html><html><head><meta charset="UTF-8">
                <title>BTrace TCK Report</title>
                <style>
                  body{font-family:monospace;margin:2em}
                  table{border-collapse:collapse;width:100%}
                  th,td{border:1px solid #ccc;padding:6px 12px;text-align:left}
                  .PASS{color:green}.FAIL{color:red}.SKIP{color:gray}
                  th{background:#f4f4f4}
                </style></head><body>
                <h1>BTrace Extension TCK Report</h1>
                """);

            long totalFail = suites.stream()
                .flatMap(s -> s.checks().stream()).filter(TckResult::isFail).count();
            w.printf("<p>Overall: <strong class=\"%s\">%s</strong> — %d failure(s)</p>%n",
                totalFail == 0 ? "PASS" : "FAIL",
                totalFail == 0 ? "PASS" : "FAIL", totalFail);

            for (var suite : suites) {
                w.printf("<h2>%s</h2><table>%n", esc(suite.suiteName()));
                w.println("<tr><th>Check</th><th>Status</th><th>Rule</th><th>Message</th></tr>");
                for (var check : suite.checks()) {
                    w.printf("<tr><td>%s</td><td class=\"%s\">%s</td><td>%s</td><td>%s</td></tr>%n",
                        esc(check.checkName()),
                        check.status().name(),
                        check.status().name(),
                        esc(check.ruleCode()),
                        esc(check.message()));
                }
                w.println("</table>");
            }
            w.println("</body></html>");
        }
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("&","&amp;").replace("<","&lt;");
    }
}
```

- [ ] **Step 5: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test
```

- [ ] **Step 6: Commit**

```bash
git add btrace-tck/
git commit -m "feat(tck): add StdoutReporter, JUnitXmlReporter, HtmlReporter"
```

---

## Task 9: StructuralSuite

**Files:**
- Create: `btrace-tck/src/main/java/io/btrace/tck/suite/StructuralSuite.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/structural/ManifestCheck.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/structural/ArtifactLayoutCheck.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/structural/ServiceApiCheck.java`
- Create: `btrace-tck/src/main/java/io/btrace/tck/check/structural/ApiImplPartitionCheck.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/suite/StructuralSuiteTest.java`

Each check follows the same pattern: extract relevant artifact from the extension ZIP, delegate to the validator, map `ValidationResult` to `TckResult`.

- [ ] **Step 1: Write failing test for StructuralSuite**

Create `btrace-tck/src/test/java/io/btrace/tck/suite/StructuralSuiteTest.java`:
```java
package io.btrace.tck.suite;

import io.btrace.tck.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class StructuralSuiteTest {
    @TempDir Path tmp;

    @Test void suiteIsNamedStructural() {
        assertEquals("structural", new StructuralSuite().name());
    }

    @Test void missingZipProducesFailResult() {
        var input = TckInput.builder()
            .extensionZip(tmp.resolve("nonexistent.zip"))
            .reportDir(tmp.resolve("report"))
            .build();
        var result = new StructuralSuite().run(input);
        assertTrue(result.hasFailed(), "Expected FAIL for missing ZIP");
    }

    @Test void nullZipPathProducesFailResult() {
        var input = TckInput.builder().reportDir(tmp.resolve("report")).build();
        var result = new StructuralSuite().run(input);
        assertTrue(result.hasFailed());
    }
}
```

- [ ] **Step 2: Create check helpers and StructuralSuite**

`ManifestCheck.java`:
```java
package io.btrace.tck.check.structural;

import io.btrace.ext.validator.*;
import io.btrace.tck.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public final class ManifestCheck {
    private static final String NAME = "ManifestCheck";

    public TckResult run(Path extensionZip) {
        try (var zf = new ZipFile(extensionZip.toFile())) {
            String apiEntry = Collections.list(zf.entries()).stream()
                .map(ZipEntry::getName)
                .filter(n -> n.endsWith("-api.jar"))
                .findFirst().orElse(null);
            if (apiEntry == null) {
                return fail("structural", NAME, "BTRACE-LO-002", "No *-api.jar in ZIP");
            }
            var ze = zf.getEntry(apiEntry);
            try (var jis = new JarInputStream(zf.getInputStream(ze))) {
                Manifest mf = jis.getManifest();
                if (mf == null) return fail("structural", NAME, "BTRACE-LO-003", "api.jar has no MANIFEST.MF");
                var results = new ManifestValidator().validate(mf);
                var first = results.stream().filter(ValidationResult::isError).findFirst();
                if (first.isPresent()) {
                    return fail("structural", NAME, first.get().getRuleCode(), first.get().getMessage());
                }
                return pass("structural", NAME);
            }
        } catch (IOException e) {
            return fail("structural", NAME, "BTRACE-LO-001", "Cannot open ZIP: " + e.getMessage());
        }
    }

    private TckResult pass(String suite, String check) {
        return new TckResult(suite, check, TckStatus.PASS, null, null);
    }
    private TckResult fail(String suite, String check, String code, String msg) {
        return new TckResult(suite, check, TckStatus.FAIL, code, msg);
    }
}
```

`ArtifactLayoutCheck.java`:
```java
package io.btrace.tck.check.structural;

import io.btrace.ext.validator.*;
import io.btrace.tck.*;
import java.nio.file.Path;

public final class ArtifactLayoutCheck {
    private static final String NAME = "ArtifactLayoutCheck";

    public TckResult run(Path extensionZip) {
        var results = new ArtifactLayoutValidator().validate(extensionZip);
        var first = results.stream().filter(ValidationResult::isError).findFirst();
        if (first.isPresent()) {
            return new TckResult("structural", NAME, TckStatus.FAIL,
                first.get().getRuleCode(), first.get().getMessage());
        }
        return new TckResult("structural", NAME, TckStatus.PASS, null, null);
    }
}
```

`ServiceApiCheck.java`:
```java
package io.btrace.tck.check.structural;

import io.btrace.ext.validator.*;
import io.btrace.tck.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class ServiceApiCheck {
    private static final String NAME = "ServiceApiCheck";

    public TckResult run(Path extensionZip) {
        try {
            Path apiJar = extractApiJar(extensionZip);
            if (apiJar == null)
                return new TckResult("structural", NAME, TckStatus.FAIL, "BTRACE-LO-002", "No api.jar found");
            var results = new ServiceApiValidator().validate(apiJar);
            var first = results.stream().filter(ValidationResult::isError).findFirst();
            if (first.isPresent())
                return new TckResult("structural", NAME, TckStatus.FAIL,
                    first.get().getRuleCode(), first.get().getMessage());
            return new TckResult("structural", NAME, TckStatus.PASS, null, null);
        } catch (IOException e) {
            return new TckResult("structural", NAME, TckStatus.FAIL, "BTRACE-LO-001", e.getMessage());
        }
    }

    private Path extractApiJar(Path extensionZip) throws IOException {
        try (var zf = new ZipFile(extensionZip.toFile())) {
            var entry = Collections.list(zf.entries()).stream()
                .filter(e -> e.getName().endsWith("-api.jar")).findFirst().orElse(null);
            if (entry == null) return null;
            var tmp = Files.createTempFile("btrace-tck-api-", ".jar");
            tmp.toFile().deleteOnExit();
            try (var in = zf.getInputStream(entry);
                 var out = new FileOutputStream(tmp.toFile())) {
                in.transferTo(out);
            }
            return tmp;
        }
    }
}
```

`ApiImplPartitionCheck.java`:
```java
package io.btrace.tck.check.structural;

import io.btrace.ext.validator.*;
import io.btrace.tck.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public final class ApiImplPartitionCheck {
    private static final String NAME = "ApiImplPartitionCheck";

    public TckResult run(Path extensionZip) {
        try (var zf = new ZipFile(extensionZip.toFile())) {
            var entries = Collections.list(zf.entries());
            var apiEntry  = entries.stream().filter(e -> e.getName().endsWith("-api.jar")).findFirst().orElse(null);
            var implEntry = entries.stream().filter(e -> e.getName().endsWith("-impl.jar")).findFirst().orElse(null);
            if (apiEntry == null)
                return new TckResult("structural", NAME, TckStatus.FAIL, "BTRACE-LO-002", "No api.jar");
            if (implEntry == null)
                return new TckResult("structural", NAME, TckStatus.FAIL, "BTRACE-LO-004", "No impl.jar");

            Path apiTmp  = extract(zf, apiEntry);
            Path implTmp = extract(zf, implEntry);
            var results  = new ApiImplPartitionValidator().validate(apiTmp, implTmp);
            var first = results.stream().filter(ValidationResult::isError).findFirst();
            if (first.isPresent())
                return new TckResult("structural", NAME, TckStatus.FAIL,
                    first.get().getRuleCode(), first.get().getMessage());
            return new TckResult("structural", NAME, TckStatus.PASS, null, null);
        } catch (IOException e) {
            return new TckResult("structural", NAME, TckStatus.FAIL, "BTRACE-LO-001", e.getMessage());
        }
    }

    private Path extract(ZipFile zf, ZipEntry entry) throws IOException {
        var tmp = Files.createTempFile("btrace-tck-", ".jar");
        tmp.toFile().deleteOnExit();
        try (var in = zf.getInputStream(entry); var out = new FileOutputStream(tmp.toFile())) {
            in.transferTo(out);
        }
        return tmp;
    }
}
```

`StructuralSuite.java`:
```java
package io.btrace.tck.suite;

import io.btrace.tck.*;
import io.btrace.tck.check.structural.*;
import java.util.List;

public final class StructuralSuite implements Suite {
    @Override public String name() { return "structural"; }

    @Override public TckSuiteResult run(TckInput input) {
        if (input.extensionZip() == null) {
            return new TckSuiteResult("structural", List.of(
                new TckResult("structural", "setup", TckStatus.FAIL,
                    "BTRACE-LO-001", "--extension ZIP path is required")));
        }
        var zip = input.extensionZip();
        return new TckSuiteResult("structural", List.of(
            new ManifestCheck().run(zip),
            new ArtifactLayoutCheck().run(zip),
            new ServiceApiCheck().run(zip),
            new ApiImplPartitionCheck().run(zip)));
    }
}
```

- [ ] **Step 3: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test
```

- [ ] **Step 4: Commit**

```bash
git add btrace-tck/
git commit -m "feat(tck): add StructuralSuite with ManifestCheck, ArtifactLayoutCheck, ServiceApiCheck, ApiImplPartitionCheck"
```

---

## Task 10: Fixture extension ZIPs and fixture-based integration tests

**Files:**
- Create: fixture build script to generate `good-extension.zip`, `bad-manifest.zip`, `bad-api.zip`, `bad-partition.zip` under `btrace-tck/src/test/resources/fixtures/`
- Create: `btrace-tck/src/test/java/io/btrace/tck/suite/StructuralSuiteFixtureTest.java`

The simplest approach is to generate the fixture ZIPs programmatically in a JUnit `@BeforeAll` method using ASM — avoids a separate build step and keeps fixtures co-located with the tests.

- [ ] **Step 1: Create fixture builder helper**

Create `btrace-tck/src/test/java/io/btrace/tck/suite/FixtureBuilder.java`:
```java
package io.btrace.tck.suite;

import org.objectweb.asm.*;
import org.objectweb.asm.Opcodes;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

final class FixtureBuilder {
    /** Builds a fully-valid extension ZIP. */
    static Path goodExtension(Path dir) throws Exception {
        return buildZip(dir, "good-extension.zip",
            validApiJar(dir, "io/example/MyService"),
            "good-extension-impl.jar", true);
    }

    static Path badManifest(Path dir) throws Exception {
        // api.jar with BTrace-Extension-Id missing
        var apiJar = dir.resolve("bad-manifest-api.jar");
        try (var jos = new JarOutputStream(new FileOutputStream(apiJar.toFile()))) {
            var mf = new Manifest();
            mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            // deliberately omit BTrace-Extension-Id
            mf.getMainAttributes().putValue("BTrace-Extension-Version", "1.0.0");
            mf.getMainAttributes().putValue("BTrace-API-Version", "3.0+");
            mf.getMainAttributes().putValue("BTrace-Extension-Services", "io.example.MyService");
            mf.getMainAttributes().putValue("BTrace-Extension-Impl", "bad-manifest-impl.jar");
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF")); mf.write(jos); jos.closeEntry();
        }
        return buildZip(dir, "bad-manifest.zip", apiJar, "bad-manifest-impl.jar", true);
    }

    static Path badApi(Path dir) throws Exception {
        // api.jar whose service is a class, not an interface → BTRACE-EXT-001
        var apiJar = dir.resolve("bad-api-api.jar");
        var cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "io/example/NotAnInterface", null, "java/lang/Object", null);
        cw.visitEnd();
        try (var jos = new JarOutputStream(new FileOutputStream(apiJar.toFile()))) {
            var mf = new Manifest();
            mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            mf.getMainAttributes().putValue("BTrace-Extension-Id", "io.example.bad-api");
            mf.getMainAttributes().putValue("BTrace-Extension-Version", "1.0.0");
            mf.getMainAttributes().putValue("BTrace-Extension-Name", "Bad API");
            mf.getMainAttributes().putValue("BTrace-Extension-Description", "bad");
            mf.getMainAttributes().putValue("BTrace-API-Version", "3.0+");
            mf.getMainAttributes().putValue("BTrace-Java-Version", "8+");
            mf.getMainAttributes().putValue("BTrace-Extension-Services", "io.example.NotAnInterface");
            mf.getMainAttributes().putValue("BTrace-Extension-Impl", "bad-api-impl.jar");
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF")); mf.write(jos); jos.closeEntry();
            jos.putNextEntry(new JarEntry("io/example/NotAnInterface.class")); jos.write(cw.toByteArray()); jos.closeEntry();
        }
        return buildZip(dir, "bad-api.zip", apiJar, "bad-api-impl.jar", true);
    }

    static Path badPartition(Path dir) throws Exception {
        // api.jar service method returns an impl-only type → BTRACE-PT-001
        var implClass = "io/example/internal/Impl";
        var svcClass  = "io/example/LeakyService";
        var cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
            svcClass, null, "java/lang/Object", null);
        cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "get", "()L" + implClass + ";", null, null).visitEnd();
        cw.visitEnd();

        var implCw = new ClassWriter(0);
        implCw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, implClass, null, "java/lang/Object", null);
        implCw.visitEnd();

        var apiJar  = dir.resolve("bad-partition-api.jar");
        var implJar = dir.resolve("bad-partition-impl.jar");
        try (var jos = new JarOutputStream(new FileOutputStream(apiJar.toFile()))) {
            var mf = buildFullManifest("io.example.bad-partition", svcClass.replace('/', '.'), "bad-partition-impl.jar");
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF")); mf.write(jos); jos.closeEntry();
            jos.putNextEntry(new JarEntry(svcClass + ".class")); jos.write(cw.toByteArray()); jos.closeEntry();
        }
        try (var jos = new JarOutputStream(new FileOutputStream(implJar.toFile()))) {
            jos.putNextEntry(new JarEntry(implClass + ".class")); jos.write(implCw.toByteArray()); jos.closeEntry();
        }
        return buildZip2(dir, "bad-partition.zip", apiJar, implJar);
    }

    private static Path validApiJar(Path dir, String svcInternal) throws Exception {
        var cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
            svcInternal, null, "java/lang/Object", null);
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "doWork", "()Ljava/lang/String;", null, null);
        mv.visitAnnotation("Ljavax/annotation/Nullable;", true).visitEnd();
        mv.visitEnd();
        cw.visitEnd();
        var apiJar = dir.resolve("good-extension-api.jar");
        var svcFqcn = svcInternal.replace('/', '.');
        try (var jos = new JarOutputStream(new FileOutputStream(apiJar.toFile()))) {
            var mf = buildFullManifest("io.example.good-extension", svcFqcn, "good-extension-impl.jar");
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF")); mf.write(jos); jos.closeEntry();
            jos.putNextEntry(new JarEntry(svcInternal + ".class")); jos.write(cw.toByteArray()); jos.closeEntry();
        }
        return apiJar;
    }

    private static Manifest buildFullManifest(String id, String services, String impl) {
        var mf = new Manifest();
        var a  = mf.getMainAttributes();
        a.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        a.putValue("BTrace-Extension-Id",          id);
        a.putValue("BTrace-Extension-Version",     "1.0.0");
        a.putValue("BTrace-Extension-Name",        id);
        a.putValue("BTrace-Extension-Description", "Test extension");
        a.putValue("BTrace-API-Version",           "3.0+");
        a.putValue("BTrace-Java-Version",          "8+");
        a.putValue("BTrace-Extension-Services",    services);
        a.putValue("BTrace-Extension-Impl",        impl);
        return mf;
    }

    private static Path buildZip(Path dir, String name, Path apiJar, String implName, boolean createImpl) throws Exception {
        var implJar = dir.resolve(implName);
        if (createImpl) implJar.toFile().createNewFile();
        return buildZip2(dir, name, apiJar, implJar);
    }

    private static Path buildZip2(Path dir, String name, Path apiJar, Path implJar) throws Exception {
        var zip = dir.resolve(name);
        try (var zos = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            for (Path p : List.of(apiJar, implJar)) {
                zos.putNextEntry(new ZipEntry(p.getFileName().toString()));
                Files.copy(p, zos);
                zos.closeEntry();
            }
        }
        return zip;
    }
}
```

- [ ] **Step 2: Write fixture integration tests**

Create `btrace-tck/src/test/java/io/btrace/tck/suite/StructuralSuiteFixtureTest.java`:
```java
package io.btrace.tck.suite;

import io.btrace.tck.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StructuralSuiteFixtureTest {
    @TempDir static Path tmp;
    static Path goodZip, badManifestZip, badApiZip, badPartitionZip;

    @BeforeAll static void buildFixtures() throws Exception {
        goodZip         = FixtureBuilder.goodExtension(tmp);
        badManifestZip  = FixtureBuilder.badManifest(tmp);
        badApiZip       = FixtureBuilder.badApi(tmp);
        badPartitionZip = FixtureBuilder.badPartition(tmp);
    }

    private TckSuiteResult run(Path zip) {
        return new StructuralSuite().run(
            TckInput.builder().extensionZip(zip).reportDir(tmp.resolve("report")).build());
    }

    @Test void goodExtensionPasses() {
        var result = run(goodZip);
        assertFalse(result.hasFailed(), () -> "Expected PASS but got: " + result.checks());
    }

    @Test void badManifestFails() {
        var result = run(badManifestZip);
        assertTrue(result.hasFailed());
        assertTrue(result.checks().stream().anyMatch(r ->
            r.isFail() && r.ruleCode() != null && r.ruleCode().startsWith("BTRACE-MF-")));
    }

    @Test void badApiFails() {
        var result = run(badApiZip);
        assertTrue(result.hasFailed());
        assertTrue(result.checks().stream().anyMatch(r ->
            r.isFail() && "BTRACE-EXT-001".equals(r.ruleCode())));
    }

    @Test void badPartitionFails() {
        var result = run(badPartitionZip);
        assertTrue(result.hasFailed());
        assertTrue(result.checks().stream().anyMatch(r ->
            r.isFail() && "BTRACE-PT-001".equals(r.ruleCode())));
    }
}
```

The `FixtureBuilder` needs ASM on the test classpath. Add to `btrace-tck/build.gradle`:
```groovy
testImplementation 'org.ow2.asm:asm:9.9.1'
testImplementation 'org.ow2.asm:asm-tree:9.9.1'
```

- [ ] **Step 3: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test
```
Expected: `BUILD SUCCESSFUL`, all fixture tests pass.

- [ ] **Step 4: Commit**

```bash
git add btrace-tck/
git commit -m "feat(tck): add FixtureBuilder and StructuralSuiteFixtureTest"
```

---

## Task 11: TckMain CLI and end-to-end test

**Files:**
- Create: `btrace-tck/src/main/java/io/btrace/tck/cli/TckMain.java`
- Create: `btrace-tck/src/test/java/io/btrace/tck/cli/TckMainTest.java`

- [ ] **Step 1: Write failing test**

Create `btrace-tck/src/test/java/io/btrace/tck/cli/TckMainTest.java`:
```java
package io.btrace.tck.cli;

import io.btrace.tck.suite.FixtureBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TckMainTest {
    @TempDir static Path tmp;

    @Test void exitCode0ForGoodExtension() throws Exception {
        var zip    = FixtureBuilder.goodExtension(tmp);
        var report = tmp.resolve("report");
        int code = TckMain.run(new String[]{
            "--extension", zip.toString(),
            "--report-dir", report.toString(),
            "--suites",    "structural"
        });
        assertEquals(0, code, "Expected exit 0 for valid extension");
        assertTrue(Files.exists(report.resolve("tck-results.xml")));
        assertTrue(Files.exists(report.resolve("tck-report.html")));
    }

    @Test void exitCode1ForBadManifest() throws Exception {
        var zip    = FixtureBuilder.badManifest(tmp.resolve("bm"));
        Files.createDirectories(tmp.resolve("bm"));
        var report = tmp.resolve("report-bm");
        int code = TckMain.run(new String[]{
            "--extension", zip.toString(),
            "--report-dir", report.toString(),
            "--suites",    "structural"
        });
        assertEquals(1, code, "Expected exit 1 for invalid extension");
    }
}
```

- [ ] **Step 2: Implement TckMain**

Create `btrace-tck/src/main/java/io/btrace/tck/cli/TckMain.java`:
```java
package io.btrace.tck.cli;

import io.btrace.tck.*;
import io.btrace.tck.report.*;
import io.btrace.tck.suite.StructuralSuite;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "btrace-tck", mixinStandardHelpOptions = true,
         description = "BTrace Extension Technology Compatibility Kit")
public final class TckMain implements Callable<Integer> {

    @Option(names = {"--extension", "-e"}, required = true, description = "Path to extension ZIP")
    Path extensionZip;

    @Option(names = {"--btrace-home"}, description = "Path to BTrace installation")
    Path btraceHome;

    @Option(names = {"--report-dir", "-r"}, defaultValue = "tck-report", description = "Output directory")
    Path reportDir;

    @Option(names = {"--suites", "-s"}, split = ",", defaultValue = "structural",
            description = "Suites to run: structural,lifecycle,behavioral,perf")
    List<String> suites;

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        return new CommandLine(new TckMain()).execute(args);
    }

    @Override
    public Integer call() throws Exception {
        var input = TckInput.builder()
            .extensionZip(extensionZip)
            .btraceHome(btraceHome)
            .reportDir(reportDir)
            .build();

        var activeSuites = new ArrayList<Suite>();
        for (String s : suites) {
            if ("structural".equals(s)) activeSuites.add(new StructuralSuite());
            // lifecycle, behavioral, perf suites added in Plans 2 and 3
        }

        var engine  = new TckEngine(activeSuites);
        var results = engine.run(input);

        reportDir.toFile().mkdirs();
        new StdoutReporter(new PrintWriter(System.out, true)).report(results);
        new JUnitXmlReporter(reportDir.resolve("tck-results.xml")).report(results);
        new HtmlReporter(reportDir.resolve("tck-report.html")).report(results);

        boolean failed = results.stream().anyMatch(TckSuiteResult::hasFailed);
        return failed ? 1 : 0;
    }
}
```

- [ ] **Step 3: Run tests — expect PASS**

```bash
./gradlew :btrace-tck:test
```

- [ ] **Step 4: Verify standalone JAR is runnable**

```bash
./gradlew :btrace-tck:jar
```
Expected: `btrace-tck/build/libs/btrace-tck.jar` created. (No need to run it now — the TckMainTest covers the behavior.)

- [ ] **Step 5: Spotless**

```bash
./gradlew spotlessApply
```

- [ ] **Step 6: Commit**

```bash
git add btrace-tck/ btrace-ext-validator/
git commit -m "feat(tck): add TckMain CLI and end-to-end structural test"
```

---

## Task 12: Run full build and format check

- [ ] **Step 1: Run all new module tests**

```bash
./gradlew :btrace-ext-validator:test :btrace-tck:test
```
Expected: all tests pass.

- [ ] **Step 2: Run spotlessCheck**

```bash
./gradlew spotlessCheck
```
If any formatting violations:
```bash
./gradlew spotlessApply
git add -u
git commit -m "style: apply google-java-format to tck modules"
```

- [ ] **Step 3: Run btrace-gradle-plugin tests to verify refactor didn't break anything**

```bash
./gradlew :btrace-gradle-plugin:test
```

- [ ] **Step 4: Smoke-test validateServiceApis on existing extensions**

```bash
./gradlew :btrace-extensions:btrace-contracts:validateServiceApis \
          :btrace-extensions:btrace-metrics:validateServiceApis
```
Expected: no new failures.

- [ ] **Step 5: Final commit if any cleanup needed**

```bash
git add -u
git commit -m "chore(tck): cleanup after Plan 1 integration"
```

---

## Self-Review Notes

- **Spec coverage:** All structural checks from the spec are implemented (ManifestCheck, ArtifactLayoutCheck, ServiceApiCheck, ApiImplPartitionCheck). RegistryCheck (optional) is omitted from Plan 1 — it's low-priority and standalone schema validation. Add it as a follow-up task if needed.
- **BTRACE-EXT-022 nuance:** The `ServiceApiValidator.checkNullability` method applies BTRACE-EXT-022 to all Object return types, not just interface return types. The distinction requires loading the class (to call `isInterface()`) or keeping a set of known interface names from the JAR. The current implementation conservatively applies it to all Object returns — this is safe (stricter than required) and matches the plugin's behavior. Revisit if it causes false positives on extension authors returning concrete non-interface types.
- **Plan 2** covers: LifecycleSuite (LoadCheck, InitCheck, InjectionCheck, CloseCheck) and BehavioralSuite — requires spawning a child JVM with the BTrace agent.
- **Plan 3** covers: PerformanceSuite (JMH) and btrace-tck-gradle-plugin.
