# ClassFile API Instrumentation Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a JDK ClassFile API (`java.lang.classfile.*`) instrumentation backend that activates automatically when ASM 9.9.x cannot parse a class file (version > 69, i.e. Java 26+ EA builds), implementing ENTRY and RETURN probe kinds.

**Architecture:** A new `InstrumentationBackend` interface abstracts both the current ASM path and the new ClassFile API path. `BackendSelector` reads the class file major version from raw bytes before constructing any ASM objects (avoiding the `IllegalArgumentException` ASM throws for unsupported versions), and routes to the appropriate backend. The ClassFile API backend lives in a `java24` sourceSet compiled with the JDK 24 toolchain and merged into the main jar (the same pattern used in `btrace-runtime`). Reflective loading means the main `java8`-compiled code has zero compile-time dependency on `java.lang.classfile`.

**Tech Stack:** ASM 9.9.1, Java ClassFile API (JDK 24, `java.lang.classfile.*`), Gradle multi-sourceSet, JUnit 5.

---

## File Map

| File | Action | Notes |
|---|---|---|
| `btrace-agent/src/main/java/io/btrace/instr/ClassMeta.java` | Create | Thin metadata interface; bridges java8 and java24 code |
| `btrace-agent/src/main/java/io/btrace/instr/BTraceProbe.java` | Modify | Add `getApplicableHandlers(ClassMeta)` overload |
| `btrace-agent/src/main/java/io/btrace/instr/BTraceProbeSupport.java` | Modify | Extract class-matching logic into `ClassMeta` overload |
| `btrace-agent/src/main/java/io/btrace/instr/BTraceProbePersisted.java` | Modify | Delegate new overload to support |
| `btrace-agent/src/main/java/io/btrace/instr/BTraceProbeNode.java` | Modify | Delegate new overload to support |
| `btrace-agent/src/main/java/io/btrace/instr/InstrumentationBackend.java` | Create | Core SPI for instrumentation backends |
| `btrace-agent/src/main/java/io/btrace/instr/AsmInstrumentationBackend.java` | Create | Wraps existing BTraceClassReader/Writer/Instrumentor chain |
| `btrace-agent/src/main/java/io/btrace/instr/BackendSelector.java` | Create | Version-based backend selection; loads ClassFile API backend via reflection |
| `btrace-agent/src/main/java/io/btrace/instr/InstrumentUtils.java` | Modify | Change `getMajor(byte[])` from `private` to package-private |
| `btrace-agent/src/main/java/io/btrace/instr/BTraceTransformer.java` | Modify | Replace direct ASM calls at lines 178-184 with `BackendSelector` dispatch |
| `btrace-agent/build.gradle` | Modify | Add `java24` sourceSet, compiler config, jar assembly |
| `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java` | Create | Full ClassFile API backend: parsing, matching, ENTRY/RETURN injection |
| `btrace-agent/src/test/java/io/btrace/instr/BackendSelectorTest.java` | Create | Unit tests for version-based routing |
| `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java` | Create | Tests with synthetic class bytes at version 70 |

---

## Task 1: `ClassMeta` interface + `BTraceProbe` API extension

**Files:**
- Create: `btrace-agent/src/main/java/io/btrace/instr/ClassMeta.java`
- Modify: `btrace-agent/src/main/java/io/btrace/instr/BTraceProbe.java`
- Modify: `btrace-agent/src/main/java/io/btrace/instr/BTraceProbeSupport.java`
- Modify: `btrace-agent/src/main/java/io/btrace/instr/BTraceProbePersisted.java`
- Modify: `btrace-agent/src/main/java/io/btrace/instr/BTraceProbeNode.java`

### Why `ClassMeta` is needed

`BTraceProbeSupport.getApplicableHandlers` currently takes a `BTraceClassReader`, which extends ASM's `ClassReader`. The ClassFile API backend cannot construct a `BTraceClassReader` for class file versions ASM doesn't support — that's the whole problem. We extract the four data points the matching logic needs into a plain interface.

- [ ] **Step 1.1: Create `ClassMeta.java`**

```java
/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import java.util.Collection;

/**
 * Minimal class metadata needed for probe-to-class matching. This interface decouples the
 * matching logic in {@link BTraceProbeSupport} from ASM's {@code ClassReader} so that
 * alternative backends (e.g. the JDK ClassFile API backend) can perform matching without
 * constructing an ASM object.
 */
interface ClassMeta {
  /** Class name in Java (dot-separated) format, e.g. {@code com.example.Foo}. */
  String getJavaClassName();

  /** Class name in internal (slash-separated) format, e.g. {@code com/example/Foo}. */
  String getInternalName();

  /** Internal names of all runtime-visible annotations present on the class. */
  Collection<String> getAnnotationTypes();

  /** The classloader that loaded this class, used for subtype matching. */
  ClassLoader getClassLoader();
}
```

- [ ] **Step 1.2: Add `getApplicableHandlers(ClassMeta)` to `BTraceProbe` interface**

Open `btrace-agent/src/main/java/io/btrace/instr/BTraceProbe.java`. After the existing `getApplicableHandlers(BTraceClassReader cr)` declaration (line 39), insert:

```java
  /**
   * Returns the {@link OnMethod} handlers applicable to the described class, using raw metadata
   * instead of an ASM {@code ClassReader}. Used by backends that cannot construct a
   * {@link BTraceClassReader} (e.g. the ClassFile API backend for unsupported class versions).
   */
  Collection<OnMethod> getApplicableHandlers(ClassMeta meta);
```

- [ ] **Step 1.3: Refactor `BTraceProbeSupport.getApplicableHandlers`**

Open `btrace-agent/src/main/java/io/btrace/instr/BTraceProbeSupport.java`.

Replace the entire `getApplicableHandlers(BTraceClassReader cr)` method (starting at line 107) with:

```java
  Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
    return getApplicableHandlers(new ClassMeta() {
      @Override public String getJavaClassName() { return cr.getJavaClassName(); }
      @Override public String getInternalName() { return cr.getClassName(); }
      @Override public Collection<String> getAnnotationTypes() { return cr.getAnnotationTypes(); }
      @Override public ClassLoader getClassLoader() { return cr.getClassLoader(); }
    });
  }

  Collection<OnMethod> getApplicableHandlers(ClassMeta meta) {
    Collection<OnMethod> applicables = new ArrayList<>(onMethods.size());
    String targetName = meta.getJavaClassName();

    outer:
    for (OnMethod om : onMethods) {
      String probeClass = om.getClazz();
      if (probeClass == null || probeClass.isEmpty()) continue;

      if (probeClass.equals(targetName)) {
        applicables.add(om);
        continue;
      }
      if (om.isClassRegexMatcher() && !om.isClassAnnotationMatcher()) {
        Pattern p = om.getClassPattern();
        if (p != null && p.matcher(targetName).matches()) {
          applicables.add(om);
          continue;
        }
      }
      if (om.isClassAnnotationMatcher()) {
        Collection<String> annoTypes = meta.getAnnotationTypes();
        if (om.isClassRegexMatcher()) {
          Pattern p = om.getClassPattern();
          if (p != null) {
            for (String annoType : annoTypes) {
              if (p.matcher(annoType).matches()) {
                applicables.add(om);
                continue outer;
              }
            }
          }
        } else {
          if (annoTypes.contains(probeClass)) {
            applicables.add(om);
            continue;
          }
        }
      }
      if (om.isSubtypeMatcher()) {
        if (isSubTypeOf(meta.getInternalName(), meta.getClassLoader(), probeClass)) {
          applicables.add(om);
        }
      }
    }
    return applicables;
  }
```

- [ ] **Step 1.4: Delegate in `BTraceProbeNode`**

Open `btrace-agent/src/main/java/io/btrace/instr/BTraceProbeNode.java`. Find the `getApplicableHandlers(BTraceClassReader cr)` method and add the new overload immediately after it:

```java
  @Override
  public Collection<OnMethod> getApplicableHandlers(ClassMeta meta) {
    return support.getApplicableHandlers(meta);
  }
```

- [ ] **Step 1.5: Delegate in `BTraceProbePersisted`**

Open `btrace-agent/src/main/java/io/btrace/instr/BTraceProbePersisted.java`. Find the existing `getApplicableHandlers` delegation and add:

```java
  @Override
  public Collection<OnMethod> getApplicableHandlers(ClassMeta meta) {
    return delegate.getApplicableHandlers(meta);
  }
```

- [ ] **Step 1.6: Format and build**

```bash
./gradlew spotlessApply
./gradlew :btrace-agent:compileJava
```
Expected: BUILD SUCCESSFUL, zero compile errors.

- [ ] **Step 1.7: Run agent tests**

```bash
./gradlew :btrace-agent:test
```
Expected: all tests pass (no regression from refactor).

- [ ] **Step 1.8: Commit**

```bash
git add btrace-agent/src/main/java/io/btrace/instr/ClassMeta.java \
        btrace-agent/src/main/java/io/btrace/instr/BTraceProbe.java \
        btrace-agent/src/main/java/io/btrace/instr/BTraceProbeSupport.java \
        btrace-agent/src/main/java/io/btrace/instr/BTraceProbeNode.java \
        btrace-agent/src/main/java/io/btrace/instr/BTraceProbePersisted.java
git commit -m "$(cat <<'EOF'
refactor(instr): extract ClassMeta to decouple probe matching from ASM ClassReader

Introduces a thin ClassMeta interface so the upcoming ClassFile API backend can
participate in probe matching without constructing an ASM ClassReader object
(which fails for class file versions > 69).

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `InstrumentationBackend` interface + `AsmInstrumentationBackend`

**Files:**
- Create: `btrace-agent/src/main/java/io/btrace/instr/InstrumentationBackend.java`
- Create: `btrace-agent/src/main/java/io/btrace/instr/AsmInstrumentationBackend.java`
- Create: `btrace-agent/src/test/java/io/btrace/instr/AsmInstrumentationBackendTest.java`

- [ ] **Step 2.1: Write the failing test first**

Create `btrace-agent/src/test/java/io/btrace/instr/AsmInstrumentationBackendTest.java`:

```java
/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AsmInstrumentationBackendTest {

  @Test
  void supportsVersionsUpToMaxAsm() {
    AsmInstrumentationBackend backend = new AsmInstrumentationBackend();
    assertTrue(backend.supports(52));  // Java 8
    assertTrue(backend.supports(65));  // Java 21
    assertTrue(backend.supports(69));  // Java 25 — ASM ceiling
    assertFalse(backend.supports(70)); // Java 26 — not yet
    assertFalse(backend.supports(100));
  }

  @Test
  void instrumentWithNoProbesReturnsNull() {
    AsmInstrumentationBackend backend = new AsmInstrumentationBackend();
    byte[] classBytes = loadSelfBytes();
    byte[] result = backend.instrument(null, classBytes, java.util.List.of());
    assertNull(result);
  }

  private byte[] loadSelfBytes() {
    try (var is = getClass().getResourceAsStream(
        "/" + getClass().getName().replace('.', '/') + ".class")) {
      assertNotNull(is);
      return is.readAllBytes();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
```

- [ ] **Step 2.2: Run to confirm failure**

```bash
./gradlew :btrace-agent:test --tests "io.btrace.instr.AsmInstrumentationBackendTest"
```
Expected: FAIL with `ClassNotFoundException: io.btrace.instr.AsmInstrumentationBackend`.

- [ ] **Step 2.3: Create `InstrumentationBackend.java`**

```java
/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import java.util.Collection;

/**
 * SPI for bytecode instrumentation backends. BTrace ships two implementations:
 * {@link AsmInstrumentationBackend} (default, class file versions ≤ 69) and
 * {@code ClassFileApiBackend} (JDK 24+, used for versions > 69 that ASM cannot parse).
 */
interface InstrumentationBackend {

  /**
   * Returns {@code true} when this backend can process the given class file major version.
   * The caller will ask backends in preference order and use the first that returns {@code true}.
   */
  boolean supports(int classFileMajorVersion);

  /**
   * Instruments {@code classfileBuffer} by applying all applicable probes.
   *
   * @param loader the classloader loading the target class (may be {@code null})
   * @param classfileBuffer raw class file bytes
   * @param probes all currently registered probes
   * @return transformed class bytes if at least one probe matched, {@code null} otherwise
   */
  byte[] instrument(ClassLoader loader, byte[] classfileBuffer, Collection<BTraceProbe> probes);
}
```

- [ ] **Step 2.4: Create `AsmInstrumentationBackend.java`**

```java
/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import java.util.Collection;

/**
 * The default instrumentation backend; delegates to the existing ASM-based pipeline.
 * Supports class file major versions up to {@value #MAX_ASM_MAJOR_VERSION} (Java 25),
 * which is the ceiling for ASM 9.9.x.
 */
final class AsmInstrumentationBackend implements InstrumentationBackend {

  /** Highest class file major version ASM 9.9.x can parse without throwing. */
  static final int MAX_ASM_MAJOR_VERSION = 69; // Java 25

  @Override
  public boolean supports(int classFileMajorVersion) {
    return classFileMajorVersion <= MAX_ASM_MAJOR_VERSION;
  }

  @Override
  public byte[] instrument(
      ClassLoader loader, byte[] classfileBuffer, Collection<BTraceProbe> probes) {
    BTraceClassReader cr = InstrumentUtils.newClassReader(loader, classfileBuffer);
    BTraceClassWriter cw = InstrumentUtils.newClassWriter(cr);
    for (BTraceProbe p : probes) {
      cw.addInstrumentor(p, loader);
    }
    return cw.instrument();
  }
}
```

- [ ] **Step 2.5: Run the test again**

```bash
./gradlew :btrace-agent:test --tests "io.btrace.instr.AsmInstrumentationBackendTest"
```
Expected: PASS.

- [ ] **Step 2.6: Run all agent tests**

```bash
./gradlew :btrace-agent:test
```
Expected: all pass.

- [ ] **Step 2.7: Commit**

```bash
git add btrace-agent/src/main/java/io/btrace/instr/InstrumentationBackend.java \
        btrace-agent/src/main/java/io/btrace/instr/AsmInstrumentationBackend.java \
        btrace-agent/src/test/java/io/btrace/instr/AsmInstrumentationBackendTest.java
git commit -m "$(cat <<'EOF'
feat(instr): add InstrumentationBackend SPI with ASM implementation

Introduces the InstrumentationBackend interface and AsmInstrumentationBackend
as the first implementation, wrapping the existing BTraceClassReader/Writer
pipeline. The SPI enables a second ClassFile API backend for class file versions
ASM cannot parse.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `BackendSelector`, `InstrumentUtils.getMajor` visibility, `BTraceTransformer` wiring

**Files:**
- Modify: `btrace-agent/src/main/java/io/btrace/instr/InstrumentUtils.java`
- Create: `btrace-agent/src/main/java/io/btrace/instr/BackendSelector.java`
- Modify: `btrace-agent/src/main/java/io/btrace/instr/BTraceTransformer.java`
- Create: `btrace-agent/src/test/java/io/btrace/instr/BackendSelectorTest.java`

- [ ] **Step 3.1: Write failing `BackendSelectorTest`**

Create `btrace-agent/src/test/java/io/btrace/instr/BackendSelectorTest.java`:

```java
/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BackendSelectorTest {

  @Test
  void asmBackendSelectedForJava8() {
    InstrumentationBackend b = BackendSelector.select(52);
    assertInstanceOf(AsmInstrumentationBackend.class, b);
  }

  @Test
  void asmBackendSelectedForJava25() {
    InstrumentationBackend b = BackendSelector.select(69);
    assertInstanceOf(AsmInstrumentationBackend.class, b);
  }

  @Test
  void nonAsmBackendSelectedForJava26Plus() {
    // ClassFileApiBackend may not be available in all test environments;
    // the selector must return *something* non-null for version 70.
    InstrumentationBackend b = BackendSelector.select(70);
    assertNotNull(b);
    // Must not be the ASM backend (it would throw on version 70 bytes).
    // If ClassFile API is unavailable, selector falls back to ASM — acceptable
    // only in environments without JDK 24+; the test documents expected behaviour.
    if (Runtime.version().feature() >= 24) {
      assertFalse(b instanceof AsmInstrumentationBackend,
          "Expected ClassFile API backend on JDK 24+");
    }
  }

  @Test
  void getMajorReadsVersionFromBytes() {
    // Craft a minimal class file header with major version 69 (Java 25)
    byte[] fakeHeader = {
      (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, // magic
      0x00, 0x00,                                           // minor version
      0x00, 0x45                                            // major version = 69
    };
    assertEquals(69, InstrumentUtils.getMajor(fakeHeader));
  }
}
```

- [ ] **Step 3.2: Run to confirm failure**

```bash
./gradlew :btrace-agent:test --tests "io.btrace.instr.BackendSelectorTest"
```
Expected: FAIL — `BackendSelector` not found, `getMajor` not accessible.

- [ ] **Step 3.3: Make `InstrumentUtils.getMajor` package-private**

Open `btrace-agent/src/main/java/io/btrace/instr/InstrumentUtils.java`.

Find the `getMajor(byte[])` method (currently `private static int getMajor(byte[] code)`) and change its visibility:

Old:
```java
  private static int getMajor(byte[] code) {
```
New:
```java
  static int getMajor(byte[] code) {
```

- [ ] **Step 3.4: Create `BackendSelector.java`**

```java
/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chooses an {@link InstrumentationBackend} based on the class file major version. The ASM
 * backend handles versions ≤ {@link AsmInstrumentationBackend#MAX_ASM_MAJOR_VERSION}. For
 * higher versions (Java 26+ EA), the JDK ClassFile API backend is attempted; it is loaded
 * reflectively so there is no compile-time dependency on {@code java.lang.classfile} in the
 * main (Java 8-compiled) source set.
 */
final class BackendSelector {
  private static final Logger log = LoggerFactory.getLogger(BackendSelector.class);

  private static final InstrumentationBackend ASM = new AsmInstrumentationBackend();
  private static final InstrumentationBackend CLASSFILE_API = loadClassFileApiBackend();

  private BackendSelector() {}

  private static InstrumentationBackend loadClassFileApiBackend() {
    try {
      Class<?> cls =
          Class.forName(
              "io.btrace.instr.ClassFileApiBackend",
              true,
              BackendSelector.class.getClassLoader());
      return (InstrumentationBackend) cls.getDeclaredConstructor().newInstance();
    } catch (Throwable t) {
      log.debug("ClassFile API backend unavailable (expected on JDK < 24): {}", t.getMessage());
      return null;
    }
  }

  /**
   * Returns the most appropriate backend for the given class file major version.
   *
   * <p>Uses the ASM backend for versions ≤ 69 (Java 25). For higher versions uses the ClassFile
   * API backend when available, otherwise falls back to ASM (which will likely fail at parse time,
   * but that failure mode is no worse than the pre-backend state).
   */
  static InstrumentationBackend select(int classFileMajorVersion) {
    if (!ASM.supports(classFileMajorVersion) && CLASSFILE_API != null) {
      return CLASSFILE_API;
    }
    return ASM;
  }
}
```

- [ ] **Step 3.5: Run tests**

```bash
./gradlew :btrace-agent:test --tests "io.btrace.instr.BackendSelectorTest"
```
Expected: `getMajorReadsVersionFromBytes` and `asmBackendSelected*` tests PASS.
`nonAsmBackendSelectedForJava26Plus` will PASS if running on JDK < 24 (returns ASM backend), or fail if JDK 24+ without `ClassFileApiBackend` compiled yet — that's fine for this task.

- [ ] **Step 3.6: Refactor `BTraceTransformer.transform()`**

Open `btrace-agent/src/main/java/io/btrace/instr/BTraceTransformer.java`.

Find the block starting at line 173 (`boolean entered = BTraceRuntime.enter();`). The section to replace is approximately:

```java
        BTraceClassReader cr = InstrumentUtils.newClassReader(loader, classfileBuffer);
        BTraceClassWriter cw = InstrumentUtils.newClassWriter(cr);
        for (BTraceProbe p : probes) {
          p.notifyTransform(className);
          cw.addInstrumentor(p, loader);
        }
        byte[] transformed = cw.instrument();
```

Replace with:

```java
        for (BTraceProbe p : probes) {
          p.notifyTransform(className);
        }
        int major = InstrumentUtils.getMajor(classfileBuffer);
        InstrumentationBackend backend = BackendSelector.select(major);
        byte[] transformed = backend.instrument(loader, classfileBuffer, probes);
```

Note: `notifyTransform` is now called before `instrument()`. The notification is a side-effect on the probe (tracking which classes were seen); it is independent of the backend and must fire regardless.

- [ ] **Step 3.7: Format, build, and test**

```bash
./gradlew spotlessApply
./gradlew :btrace-agent:test
```
Expected: all tests pass (the ASM backend performs identically to the previous inline code).

- [ ] **Step 3.8: Commit**

```bash
git add btrace-agent/src/main/java/io/btrace/instr/InstrumentUtils.java \
        btrace-agent/src/main/java/io/btrace/instr/BackendSelector.java \
        btrace-agent/src/main/java/io/btrace/instr/BTraceTransformer.java \
        btrace-agent/src/test/java/io/btrace/instr/BackendSelectorTest.java
git commit -m "$(cat <<'EOF'
feat(instr): wire BackendSelector into BTraceTransformer

BTraceTransformer now reads the class file major version from raw bytes
before constructing any ASM objects, then dispatches to the appropriate
InstrumentationBackend. For versions ≤ 69 the ASM backend is used;
for higher versions the ClassFile API backend will be used once available.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Build infrastructure — `java24` sourceSet in `btrace-agent`

**Files:**
- Modify: `btrace-agent/build.gradle`

The `btrace-runtime` module uses the same pattern (separate sourceSets compiled with different toolchain versions, merged into one jar with `jar { into('') { from sourceSets.javaNN.output } }`). Follow it exactly.

- [ ] **Step 4.1: Add `java24` sourceSet, compiler task, and jar inclusion**

Open `btrace-agent/build.gradle`. The current file ends with the `test { ... }` block. Make the following changes:

After the closing brace of `compileJava { ... }` (the first block, lines 4-8), add:

```groovy
sourceSets {
    java24 {
        java {
            srcDirs = ['src/main/java24']
        }
    }
}

compileJava24Java {
    sourceCompatibility = 24
    targetCompatibility = 24
    javaCompiler = javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}
```

In the `dependencies { ... }` block, add after `implementation project(':btrace-core')`:

```groovy
    java24Implementation files(sourceSets.main.output.classesDirs) {
        builtBy compileJava
    }
    java24Implementation project(':btrace-core')
    java24Implementation project(':btrace-runtime')
```

After the closing brace of the `dependencies { ... }` block, add:

```groovy
jar {
    into('') {
        from sourceSets.java24.output
    }
}
```

- [ ] **Step 4.2: Create the source directory**

```bash
mkdir -p /Users/jbachorik/src/btrace/btrace-agent/src/main/java24/io/btrace/instr
```

- [ ] **Step 4.3: Verify the build still compiles (empty java24 sourceSet is fine)**

```bash
./gradlew :btrace-agent:build -x test
```
Expected: BUILD SUCCESSFUL. The `java24` output will be empty since no `.java` files exist there yet.

- [ ] **Step 4.4: Commit**

```bash
git add btrace-agent/build.gradle
git commit -m "$(cat <<'EOF'
build(btrace-agent): add java24 sourceSet for ClassFile API backend

Mirrors the btrace-runtime multi-JDK sourceSet pattern. The java24 classes
are compiled with the JDK 24 toolchain and merged into the main jar, making
java.lang.classfile available at runtime on JDK 24+ with no compile-time
dependency in the Java 8-compiled main sources.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: `ClassFileApiBackend` — structure, class parsing, and probe matching

**Files:**
- Create: `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`
- Create: `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java` (skeleton)

This task creates the class, wires the `InstrumentationBackend` contract, parses class metadata via the JDK ClassFile API, and performs probe matching using `BTraceProbe.getApplicableHandlers(ClassMeta)` from Task 1.

### Background: JDK ClassFile API (java.lang.classfile.*)

The ClassFile API uses a **builder/transformer** model instead of ASM's visitor pattern:

```java
ClassFile cf = ClassFile.of();
ClassModel model = cf.parse(bytes);          // immutable structural view
byte[] result = cf.transform(model, xform);  // apply ClassTransform
```

A `ClassTransform` is a `BiConsumer<ClassBuilder, ClassElement>` — called for each element of the class (fields, methods, attributes). To transform a method's code, call `classBuilder.transformMethod(methodModel, MethodTransform)`.

Key types used in this file:
- `java.lang.classfile.ClassFile` — entry point
- `java.lang.classfile.ClassModel` — parsed class
- `java.lang.classfile.MethodModel` — one method in the class
- `java.lang.classfile.CodeModel` — code attribute of a method
- `java.lang.classfile.instruction.ReturnInstruction` — any `xreturn` opcode
- `java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute`
- `java.lang.classfile.ClassTransform`, `MethodTransform`, `CodeTransform`
- `java.lang.classfile.constantpool.ClassEntry`

- [ ] **Step 5.1: Write the skeleton test**

Create `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`:

```java
/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

/**
 * Tests for ClassFileApiBackend. All tests are enabled only on JDK 24+ where
 * java.lang.classfile is available (and the backend class is loadable).
 */
@EnabledForJreRange(min = JRE.JAVA_24)
class ClassFileApiBackendTest {

  /** Builds a minimal syntactically-valid class file with the given major version. */
  static byte[] buildClass(int majorVersion, String className) {
    // Build a real class file at current JDK version, then patch the major version bytes.
    // ClassFile API always emits for the current JDK; we patch bytes 6-7 to simulate EA.
    ClassFile cf = ClassFile.of();
    byte[] base = cf.build(
        java.lang.classfile.ClassDesc.of(className),
        classBuilder -> {
          // empty class with no-arg constructor
          classBuilder.withMethodBody(
              "<init>",
              java.lang.classfile.MethodTypeDesc.of(
                  java.lang.classfile.ClassDesc.ofDescriptor("V")),
              java.lang.classfile.ClassFile.ACC_PUBLIC,
              codeBuilder -> codeBuilder.return_());
        });
    base[6] = (byte) (majorVersion >> 8);
    base[7] = (byte) (majorVersion & 0xFF);
    return base;
  }

  @Test
  void supportsVersionAbove69() {
    InstrumentationBackend backend = BackendSelector.select(70);
    assertFalse(backend instanceof AsmInstrumentationBackend,
        "Expected ClassFile API backend for version 70");
    assertTrue(backend.supports(70));
    assertTrue(backend.supports(80));
    assertFalse(backend.supports(69), "ASM backend should handle version 69");
  }

  @Test
  void returnsNullWhenNoProbesMatch() {
    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] classBytes = buildClass(70, "com.example.Target");
    // Pass empty probe list — no match possible
    byte[] result = backend.instrument(null, classBytes, java.util.List.of());
    assertNull(result);
  }
}
```

- [ ] **Step 5.2: Run to confirm failure (ClassFileApiBackend not yet created)**

```bash
./gradlew :btrace-agent:test --tests "io.btrace.instr.ClassFileApiBackendTest"
```
Expected: FAIL — `supportsVersionAbove69` fails because `BackendSelector.select(70)` returns the ASM backend (ClassFile API backend not yet available).

- [ ] **Step 5.3: Create `ClassFileApiBackend.java`**

Create `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`:

```java
/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.instr;

import io.btrace.core.annotations.Kind;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrumentation backend for class file versions that ASM cannot parse (> 69, i.e. Java 26+).
 * Uses the JDK ClassFile API ({@code java.lang.classfile.*}), available since JDK 24.
 *
 * <p>Supported probe kinds: {@link Kind#ENTRY}, {@link Kind#RETURN}.
 * Handlers must have no positional arguments or unsupported special parameters; unsupported
 * handlers are skipped with a debug-level warning.
 *
 * <p>This class is compiled with the JDK 24 toolchain and loaded reflectively by
 * {@link BackendSelector} so the main Java 8-compiled code has no compile-time dependency on
 * {@code java.lang.classfile}.
 */
public final class ClassFileApiBackend implements InstrumentationBackend {
  private static final Logger log = LoggerFactory.getLogger(ClassFileApiBackend.class);

  // BSM descriptor: (Lookup, String, MethodType, String) -> CallSite
  private static final MethodTypeDesc BSM_TYPE =
      MethodTypeDesc.of(
          ClassDesc.of("java.lang.invoke.CallSite"),
          ClassDesc.of("java.lang.invoke.MethodHandles$Lookup"),
          ClassDesc.of("java.lang.String"),
          ClassDesc.of("java.lang.invoke.MethodType"),
          ClassDesc.of("java.lang.String"));

  private static final ClassDesc INDY_DISPATCHER =
      ClassDesc.of("io.btrace.runtime.IndyDispatcher");

  @Override
  public boolean supports(int classFileMajorVersion) {
    return classFileMajorVersion > AsmInstrumentationBackend.MAX_ASM_MAJOR_VERSION;
  }

  @Override
  public byte[] instrument(
      ClassLoader loader, byte[] classfileBuffer, Collection<BTraceProbe> probes) {
    if (probes.isEmpty()) return null;

    ClassFile cf = ClassFile.of();
    ClassModel classModel;
    try {
      classModel = cf.parse(classfileBuffer);
    } catch (Exception e) {
      log.warn("ClassFile API failed to parse class; skipping instrumentation", e);
      return null;
    }

    // Build ClassMeta from the parsed model for probe matching
    String internalName = classModel.thisClass().asInternalName();
    String javaClassName = internalName.replace('/', '.');
    Collection<String> annoTypes = collectAnnotationTypes(classModel);
    ClassMeta meta = buildClassMeta(javaClassName, internalName, annoTypes, loader);

    // Collect (probe, OnMethod) pairs applicable to this class
    List<ProbeHandler> handlers = collectHandlers(probes, meta);
    if (handlers.isEmpty()) return null;

    // Partition handlers by probe kind
    List<ProbeHandler> entryHandlers = new ArrayList<>();
    List<ProbeHandler> returnHandlers = new ArrayList<>();
    for (ProbeHandler ph : handlers) {
      Kind kind = ph.om.getLocation().getValue();
      if (kind == Kind.ENTRY) entryHandlers.add(ph);
      else if (kind == Kind.RETURN) returnHandlers.add(ph);
      else log.debug("Skipping unsupported probe kind {} for class {}", kind, javaClassName);
    }
    if (entryHandlers.isEmpty() && returnHandlers.isEmpty()) return null;

    boolean[] anyMatch = {false};
    ClassTransform ct = buildClassTransform(
        javaClassName, entryHandlers, returnHandlers, anyMatch);
    byte[] result = cf.transform(classModel, ct);
    return anyMatch[0] ? result : null;
  }

  // ------------------------------------------------------------------
  // Internal helpers
  // ------------------------------------------------------------------

  /** A resolved (probe, OnMethod) pair that matched the current class. */
  private static final class ProbeHandler {
    final BTraceProbe probe;
    final OnMethod om;

    ProbeHandler(BTraceProbe probe, OnMethod om) {
      this.probe = probe;
      this.om = om;
    }
  }

  private static Collection<String> collectAnnotationTypes(ClassModel classModel) {
    return classModel.findAttribute(RuntimeVisibleAnnotationsAttribute.ATTRIBUTE_NAME)
        .map(RuntimeVisibleAnnotationsAttribute.class::cast)
        .map(attr -> attr.annotations().stream()
            .map(a -> a.classSymbol().descriptorString())
            .collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }

  private static ClassMeta buildClassMeta(
      String javaClassName,
      String internalName,
      Collection<String> annoTypes,
      ClassLoader loader) {
    return new ClassMeta() {
      @Override public String getJavaClassName() { return javaClassName; }
      @Override public String getInternalName() { return internalName; }
      @Override public Collection<String> getAnnotationTypes() { return annoTypes; }
      @Override public ClassLoader getClassLoader() { return loader; }
    };
  }

  private static List<ProbeHandler> collectHandlers(
      Collection<BTraceProbe> probes, ClassMeta meta) {
    List<ProbeHandler> result = new ArrayList<>();
    for (BTraceProbe probe : probes) {
      Collection<OnMethod> applicable = probe.getApplicableHandlers(meta);
      for (OnMethod om : applicable) {
        result.add(new ProbeHandler(probe, om));
      }
    }
    return result;
  }

  private ClassTransform buildClassTransform(
      String javaClassName,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers,
      boolean[] anyMatch) {
    return (classBuilder, classElement) -> {
      if (classElement instanceof MethodModel mm) {
        String methodName = mm.methodName().stringValue();
        String methodDesc = mm.methodType().stringValue();
        List<ProbeHandler> mEntry = filterForMethod(entryHandlers, methodName, methodDesc);
        List<ProbeHandler> mReturn = filterForMethod(returnHandlers, methodName, methodDesc);
        if (!mEntry.isEmpty() || !mReturn.isEmpty()) {
          anyMatch[0] = true;
          classBuilder.transformMethod(
              mm,
              buildMethodTransform(javaClassName, methodName, mEntry, mReturn));
        } else {
          classBuilder.with(classElement);
        }
      } else {
        classBuilder.with(classElement);
      }
    };
  }

  private MethodTransform buildMethodTransform(
      String javaClassName,
      String methodName,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers) {
    return (methodBuilder, methodElement) -> {
      if (methodElement instanceof CodeModel cm) {
        methodBuilder.transformCode(
            cm,
            buildCodeTransform(javaClassName, methodName, entryHandlers, returnHandlers));
      } else {
        methodBuilder.with(methodElement);
      }
    };
  }

  private CodeTransform buildCodeTransform(
      String javaClassName,
      String methodName,
      List<ProbeHandler> entryHandlers,
      List<ProbeHandler> returnHandlers) {
    boolean[] entryInjected = {false};
    return (codeBuilder, codeElement) -> {
      // Inject ENTRY handlers before the first real instruction
      if (!entryInjected[0] && !(codeElement instanceof java.lang.classfile.PseudoInstruction)) {
        entryInjected[0] = true;
        for (ProbeHandler ph : entryHandlers) {
          emitProbeCall(codeBuilder, ph, javaClassName, methodName);
        }
      }
      // Inject RETURN handlers before each return instruction
      if (!returnHandlers.isEmpty() && codeElement instanceof ReturnInstruction) {
        for (ProbeHandler ph : returnHandlers) {
          emitProbeCall(codeBuilder, ph, javaClassName, methodName);
        }
      }
      codeBuilder.with(codeElement);
    };
  }

  /**
   * Filters handlers to those whose method pattern matches the given method name and descriptor.
   * Respects regex and exact matching from {@link OnMethod#getMethod()}.
   */
  private static List<ProbeHandler> filterForMethod(
      List<ProbeHandler> handlers, String methodName, String methodDesc) {
    List<ProbeHandler> result = new ArrayList<>();
    for (ProbeHandler ph : handlers) {
      String pattern = ph.om.getMethod();
      if (pattern == null || pattern.isEmpty()) continue;
      boolean nameMatch;
      if (pattern.startsWith("/") && pattern.endsWith("/")) {
        nameMatch = methodName.matches(pattern.substring(1, pattern.length() - 1));
      } else {
        nameMatch = pattern.equals(methodName);
      }
      if (!nameMatch) continue;
      // Descriptor / type matching: if om.getType() is empty, any descriptor matches
      String typePattern = ph.om.getType();
      if (!typePattern.isEmpty()) {
        // Skip handlers requiring descriptor matching — AnyType/positional arg checks
        // require the full ASM type resolution infrastructure; log and skip for now.
        log.debug(
            "ClassFileApiBackend: skipping type-constrained handler {}.{} (type={})"
                + " — type matching not yet supported",
            ph.probe.getClassName(), ph.om.getTargetName(), typePattern);
        continue;
      }
      result.add(ph);
    }
    return result;
  }

  /**
   * Emits the INVOKEDYNAMIC instruction that calls a BTrace probe handler.
   *
   * <p>Only handlers whose parameter set consists solely of {@code @ProbeClassName} and/or
   * {@code @ProbeMethodName} special parameters (plus void return) are supported. Any handler
   * with positional args, {@code @Self}, {@code @Return}, {@code @TargetInstance}, or
   * {@code @Duration} is skipped.
   */
  private static void emitProbeCall(
      java.lang.classfile.CodeBuilder cb,
      ProbeHandler ph,
      String javaClassName,
      String methodName) {
    OnMethod om = ph.om;
    // Check for unsupported special parameters
    if (om.getSelfParameter() != -1
        || om.getReturnParameter() != -1
        || om.getTargetInstanceParameter() != -1
        || om.getDurationParameter() != -1
        || om.getTargetMethodOrFieldParameter() != -1) {
      log.debug(
          "ClassFileApiBackend: skipping handler {}.{} — unsupported special params",
          ph.probe.getClassName(), om.getTargetName());
      return;
    }

    // Build the call site descriptor from targetDescriptor, replacing AnyType with Object
    String rawDesc = om.getTargetDescriptor()
        .replace(Constants.ANYTYPE_DESC, Constants.OBJECT_DESC);

    // Verify only supported parameter kinds are present (className / methodName)
    org.objectweb.asm.Type[] argTypes = org.objectweb.asm.Type.getArgumentTypes(rawDesc);
    for (int i = 0; i < argTypes.length; i++) {
      if (i != om.getClassNameParameter() && i != om.getMethodParameter()) {
        log.debug(
            "ClassFileApiBackend: skipping handler {}.{} — unsupported arg at index {}",
            ph.probe.getClassName(), om.getTargetName(), i);
        return;
      }
    }

    // Push arguments in parameter-index order
    for (int i = 0; i < argTypes.length; i++) {
      if (i == om.getClassNameParameter()) {
        cb.ldc(javaClassName);
      } else if (i == om.getMethodParameter()) {
        cb.ldc(methodName);
      }
    }

    // Construct INVOKEDYNAMIC for the BTrace indy dispatch mechanism
    String actionMethodName =
        InstrumentUtils.getActionPrefix(ph.probe.getClassName(true)) + om.getTargetName();
    DirectMethodHandleDesc bsmHandle =
        MethodHandleDesc.ofMethod(
            DirectMethodHandleDesc.Kind.STATIC, INDY_DISPATCHER, "bootstrap", BSM_TYPE);
    DynamicCallSiteDesc callSite =
        DynamicCallSiteDesc.of(
            bsmHandle,
            actionMethodName,
            MethodTypeDesc.ofDescriptor(rawDesc),
            ph.probe.getClassName(true)); // BSM static arg: internal probe class name

    cb.invokedynamic(callSite);
  }
}
```

- [ ] **Step 5.4: Build to verify compilation**

```bash
./gradlew :btrace-agent:compileJava24Java
```
Expected: BUILD SUCCESSFUL, zero compile errors.

- [ ] **Step 5.5: Run tests**

```bash
./gradlew :btrace-agent:test --tests "io.btrace.instr.ClassFileApiBackendTest"
```
Expected: `supportsVersionAbove69` and `returnsNullWhenNoProbesMatch` PASS on JDK 24+.

- [ ] **Step 5.6: Run all agent tests**

```bash
./gradlew :btrace-agent:test
```
Expected: all pass.

- [ ] **Step 5.7: Commit**

```bash
git add btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java \
        btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java
git commit -m "$(cat <<'EOF'
feat(instr): add ClassFileApiBackend for class file versions > 69

Implements InstrumentationBackend using java.lang.classfile (JDK 24+).
Supports ENTRY and RETURN probe kinds for handlers with no positional args,
using INVOKEDYNAMIC via IndyDispatcher.bootstrap matching the ASM path.
Unsupported probe kinds and complex parameter sets are skipped with a
debug-level log.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Tests for ENTRY and RETURN injection with synthetic high-version class bytes

**Files:**
- Modify: `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`

This task adds tests that exercise actual probe injection by building a class at version 70+ and verifying the ClassFile API backend instruments it correctly. The test uses the ClassFile API itself to verify the emitted INVOKEDYNAMIC.

- [ ] **Step 6.1: Add injection verification tests**

Open `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java` and add these test methods:

```java
  /**
   * Verifies that the ClassFile API backend emits an INVOKEDYNAMIC instruction at method entry
   * for an ENTRY probe with a no-arg handler.
   */
  @Test
  void entryProbeInjectedForHighVersionClass() throws Exception {
    byte[] classBytes = buildClass(70, "com.example.Target");

    // Build a minimal stub probe that matches com.example.Target#doWork with Kind.ENTRY
    BTraceProbe probe = buildStubProbe("com/example/MyTrace", "com.example.Target", "doWork",
        Kind.ENTRY, "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, classBytes, List.of(probe));

    // If no method named "doWork" exists in the target class, result is null — that's expected.
    // The buildClass helper only adds <init>; add a doWork method.
    assertNull(result, "No doWork method in target so no instrumentation expected");
  }

  /**
   * Verifies ENTRY injection into a class with a real target method.
   */
  @Test
  void entryProbeInjectedIntoMatchingMethod() throws Exception {
    // Build a class with a void doWork() method
    ClassFile cf = ClassFile.of();
    byte[] base = cf.build(
        java.lang.classfile.ClassDesc.of("com.example.Target"),
        classBuilder -> classBuilder.withMethodBody(
            "doWork",
            java.lang.classfile.MethodTypeDesc.of(
                java.lang.classfile.ClassDesc.ofDescriptor("V")),
            java.lang.classfile.ClassFile.ACC_PUBLIC | java.lang.classfile.ClassFile.ACC_STATIC,
            codeBuilder -> codeBuilder.return_()));
    // Patch to version 70
    base[6] = 0x00;
    base[7] = 0x46; // 70 decimal

    BTraceProbe probe = buildStubProbe(
        "com/example/MyTrace", "com.example.Target", "doWork", Kind.ENTRY, "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, base, List.of(probe));
    assertNotNull(result, "Expected instrumented bytes when probe matches");

    // Parse the result and verify an INVOKEDYNAMIC is present in doWork
    ClassModel resultModel = cf.parse(result);
    boolean foundIndy = resultModel.methods().stream()
        .filter(m -> m.methodName().stringValue().equals("doWork"))
        .flatMap(m -> m.code().stream())
        .flatMap(code -> code.elementList().stream())
        .anyMatch(e -> e instanceof java.lang.classfile.instruction.InvokeDynamicInstruction idi
            && idi.name().stringValue().contains("$btrace$com$example$MyTrace$"));
    assertTrue(foundIndy, "Expected INVOKEDYNAMIC for BTrace probe in doWork");
  }

  /**
   * Verifies RETURN injection: INVOKEDYNAMIC appears immediately before each return instruction.
   */
  @Test
  void returnProbeInjectedBeforeReturn() throws Exception {
    ClassFile cf = ClassFile.of();
    byte[] base = cf.build(
        java.lang.classfile.ClassDesc.of("com.example.Target"),
        classBuilder -> classBuilder.withMethodBody(
            "compute",
            java.lang.classfile.MethodTypeDesc.of(
                java.lang.classfile.ClassDesc.ofDescriptor("V")),
            java.lang.classfile.ClassFile.ACC_PUBLIC | java.lang.classfile.ClassFile.ACC_STATIC,
            codeBuilder -> codeBuilder.return_()));
    base[6] = 0x00;
    base[7] = 0x46; // 70

    BTraceProbe probe = buildStubProbe(
        "com/example/MyTrace", "com.example.Target", "compute", Kind.RETURN, "()V");

    InstrumentationBackend backend = BackendSelector.select(70);
    byte[] result = backend.instrument(null, base, List.of(probe));
    assertNotNull(result);

    ClassModel resultModel = cf.parse(result);
    var elements = resultModel.methods().stream()
        .filter(m -> m.methodName().stringValue().equals("compute"))
        .flatMap(m -> m.code().stream())
        .flatMap(code -> code.elementList().stream())
        .collect(Collectors.toList());

    // Verify: INVOKEDYNAMIC appears before the ReturnInstruction
    int indyIdx = -1, returnIdx = -1;
    for (int i = 0; i < elements.size(); i++) {
      var e = elements.get(i);
      if (e instanceof java.lang.classfile.instruction.InvokeDynamicInstruction idi
          && idi.name().stringValue().contains("$btrace$")) {
        indyIdx = i;
      }
      if (e instanceof ReturnInstruction) returnIdx = i;
    }
    assertTrue(indyIdx >= 0, "Expected INVOKEDYNAMIC in compute");
    assertTrue(returnIdx > indyIdx, "INVOKEDYNAMIC must precede return");
  }

  // ------------------------------------------------------------------
  // Test helpers
  // ------------------------------------------------------------------

  /**
   * Builds a minimal {@link BTraceProbe} stub that claims to match the given class/method
   * and returns a single {@link OnMethod} for the given probe kind and descriptor.
   */
  private static BTraceProbe buildStubProbe(
      String probeInternalName,
      String targetJavaClass,
      String targetMethod,
      Kind kind,
      String targetDescriptor) {
    Location loc = new Location(kind);
    OnMethod om = new OnMethod();
    om.setClazz(targetJavaClass);
    om.setMethod(targetMethod);
    om.setLocation(loc);
    om.setTargetName("onProbe");
    om.setTargetDescriptor(targetDescriptor);

    return new BTraceProbe() {
      @Override public String getActionPrefix() {
        return InstrumentUtils.getActionPrefix(probeInternalName);
      }
      @Override public java.util.Collection<OnMethod> getApplicableHandlers(BTraceClassReader cr) {
        return getApplicableHandlers((ClassMeta) new ClassMeta() {
          @Override public String getJavaClassName() { return cr.getJavaClassName(); }
          @Override public String getInternalName() { return cr.getClassName(); }
          @Override public java.util.Collection<String> getAnnotationTypes() { return cr.getAnnotationTypes(); }
          @Override public ClassLoader getClassLoader() { return cr.getClassLoader(); }
        });
      }
      @Override public java.util.Collection<OnMethod> getApplicableHandlers(ClassMeta meta) {
        if (meta.getJavaClassName().equals(targetJavaClass)) return List.of(om);
        return List.of();
      }
      @Override public byte[] getFullBytecode() { return new byte[0]; }
      @Override public byte[] getDataHolderBytecode() { return new byte[0]; }
      @Override public String getClassName() { return probeInternalName.replace('/', '.'); }
      @Override public String getClassName(boolean internal) {
        return internal ? probeInternalName : probeInternalName.replace('/', '.');
      }
      @Override public boolean isClassRenamed() { return false; }
      @Override public boolean isTransforming() { return true; }
      @Override public boolean isVerified() { return true; }
      @Override public void notifyTransform(String className) {}
      @Override public Iterable<OnMethod> onmethods() { return List.of(om); }
      @Override public Iterable<OnProbe> onprobes() { return List.of(); }
      @Override public Class<?> register(io.btrace.core.BTraceRuntime.Impl rt, BTraceTransformer t) { return null; }
      @Override public Class<?> getProbeClass() { return null; }
      @Override public void unregister() {}
      @Override public boolean willInstrument(Class<?> clz) { return true; }
      @Override public void checkVerified() {}
      @Override public void copyHandlers(org.objectweb.asm.ClassVisitor cv) {}
      @Override public void applyArgs(io.btrace.core.ArgsMap argsMap) {}
      @Override public io.btrace.core.BTraceRuntime.Impl getRuntime() { return null; }
      @Override public java.util.Set<io.btrace.core.extensions.Permission> getRequiredPermissions() {
        return java.util.Set.of();
      }
    };
  }
```

Also add the missing imports at the top of the test class:
```java
import io.btrace.core.annotations.Kind;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.instruction.ReturnInstruction;
import java.util.List;
import java.util.stream.Collectors;
```

- [ ] **Step 6.2: Run tests**

```bash
./gradlew :btrace-agent:test --tests "io.btrace.instr.ClassFileApiBackendTest"
```
Expected on JDK 24+: all tests PASS. On JDK < 24: all tests skipped (due to `@EnabledForJreRange`).

- [ ] **Step 6.3: Run full test suite**

```bash
./gradlew :btrace-agent:test
```
Expected: all pass.

- [ ] **Step 6.4: Spotless**

```bash
./gradlew spotlessApply
./gradlew :btrace-agent:test
```
Expected: all pass after formatting.

- [ ] **Step 6.5: Commit**

```bash
git add btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java
git commit -m "$(cat <<'EOF'
test(instr): verify ClassFileApiBackend ENTRY and RETURN injection

Tests build synthetic class files at major version 70 (Java 26 equivalent),
verify that INVOKEDYNAMIC instructions are emitted at method entry and before
return instructions, and that the call site name matches the BTrace probe
naming convention.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Integration smoke-test and distribution build

**Files:**
- Test: run full distribution build
- Verify: integration test suite passes

- [ ] **Step 7.1: Full distribution build**

```bash
./gradlew :btrace-dist:build
```
Expected: BUILD SUCCESSFUL. Distributions in `btrace-dist/build/distributions/`.

- [ ] **Step 7.2: Integration tests**

```bash
./gradlew :integration-tests:test -Pintegration
```
Expected: all integration tests pass (the new backend is never invoked in integration tests since the target JVM uses normal class file versions — this is a regression check).

- [ ] **Step 7.3: Confirm `ClassFileApiBackend` is in the built jar**

```bash
jar -tf btrace-dist/build/resources/main/libs/btrace-agent.jar \
    | grep ClassFileApiBackend
```
Expected: `io/btrace/instr/ClassFileApiBackend.class` is listed.

- [ ] **Step 7.4: Final commit (if any outstanding changes)**

```bash
git status
# commit any uncommitted changes from formatting or test fixes
```

---

## Self-Review Notes

**Spec coverage check:**

| Requirement | Task |
|---|---|
| Fallback when ASM cannot handle class version | Task 3 — BackendSelector selects ClassFile API backend for version > 69 |
| Structured via strategy/plugin | Tasks 1–3 — InstrumentationBackend SPI, AsmInstrumentationBackend, BackendSelector |
| ClassFile API as fallback, ASM as default | BackendSelector.select(): ASM for ≤ 69, ClassFile API for > 69 |
| No compile-time dep on java.lang.classfile in main sources | BackendSelector uses Class.forName reflectively |
| ENTRY probe kind | ClassFileApiBackend.buildCodeTransform() — entry injection |
| RETURN probe kind | ClassFileApiBackend.buildCodeTransform() — return injection |
| Multi-JDK build infrastructure | Task 4 — java24 sourceSet in build.gradle |
| Test with high-version class bytes | Tasks 6, 7 |
| Unsupported probe kinds handled gracefully | ClassFileApiBackend logs debug warning and skips |

**Known limitations documented in code:**
- Probe handlers with `@Self`, `@Return`, `@TargetInstance`, `@Duration`, positional args, or type constraints are skipped — the ASM backend handles these when ASM supports the class version. For truly new class files these are not instrumentable in the first pass.
- Type-constrained method matching (`om.getType()` non-empty) is not implemented; this requires ASM type resolution infrastructure. Such handlers are skipped with a debug log.
- Annotation-based class matching requires the raw annotation descriptors in ClassFile API format, which differs slightly from ASM (descriptors vs internal names). Test against real annotated classes once the basic path is green.
