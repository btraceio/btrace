# BTrace QE Agent — Phase 1 (Framework + CLI/Agent-Args Surface) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the `btrace-qe` module end to end — case model, YAML corpus loader, target/client execution, the five-invariant oracle, and Markdown/JSON reporting — proven out against one real surface (CLI argument parsing and agent-startup arguments), so `./gradlew :btrace-qe:qe` runs from a clean checkout and produces a real, reproducible findings report.

**Architecture:** A new, `check`-independent Gradle module. Abuse cases are declarative YAML files describing either a "target" run (launch a fixture JVM with `-javaagent:btrace.jar=...`, then canary-attach) or a "client" run (invoke the real `btrace` client entry point, `io.btrace.boot.Loader` from `btrace.jar`, with bad CLI args). An executor runs a fixed step vocabulary against real processes, each step under a hard timeout; a fixed set of oracle checks judges the resulting trace; two reporters render the verdicts as Markdown and deterministic JSON.

**Tech Stack:** Java 8 source/target on the JDK 11 toolchain (repo default), JUnit 5 (wired repo-wide by `common.gradle`), SnakeYAML (new dependency, approved 2026-10-25). JSON output is hand-rolled to keep ordering deterministic.

**Spec:** [internal/specs/2026-08-11-btrace-qe-agent-design.md](../specs/2026-08-11-btrace-qe-agent-design.md)

**Scope note:** The approved spec covers four surfaces. This plan builds the shared framework and delivers the first surface (CLI and agent-startup arguments) as a complete, reportable slice. Script-compile/verifier, attach-lifecycle, and protocol/extensions each become their own follow-up plan on this framework. Deliberately deferred from the spec: `--budget`/`--parallel`/`--baseline`, the `ProgrammaticCase` SPI, the generative mutator families, and the 3× re-run flakiness handling. Because no generator exists yet, the seed is recorded in every report but nothing consumes it in this phase.

## Global Constraints

- The `qe` and `qeSelfTest` tasks never run as part of `build` or `check`. `qe` always exits `0` regardless of findings.
- Reports live under `btrace-qe/build/qe-report/`. `build/` is already gitignored repo-wide.
- Default seed is `20260811`. Every report records seed, git SHA, BTrace version, JDK, OS, CPU count, and the exact command line.
- Cases execute and report sorted by case `id`.
- **Every step has a hard timeout** (`qe.timeout`, default 30000 ms). No harness code path may block without one.
- **License header:** every new file under `btrace-qe/src/*/java/**` starts with the repo header (spotless enforces it, and `check` depends on `spotlessCheck` via `gradle/spotless.gradle`):

  ```java
  /*
   * Copyright (c) 2026, Jaroslav Bachorik <j.bachorik@btrace.io>.
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
  ```

  The code blocks below omit the header for brevity; add it to every Java file you create.
- **Per-task gate:** the task's targeted tests **and** `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:spotlessCheck`. If spotless fails, run `:btrace-qe:spotlessApply`, review the diff, re-check.
- **Java API level:** code in the `main` and `targets` source sets uses Java 8 APIs only (no `List.of`, `Optional.isEmpty`, `Files.readString`, etc.) — the repo compiles with `-source 8` but without `--release`, so the compiler will not catch this for you. Test code may use Java 11 APIs but nothing newer (no `Stream.toList()`).
- Do not override `compileJava` or re-add JUnit dependencies; `common.gradle` already configures both.
- **Do not commit or push without the user's explicit go-ahead.** Each task ends by staging only. After Task 12, ask the user how they want it committed.
- Do not touch `.claude/worktrees/btrace-tck-impl` (the in-flight Extension TCK).

---

## File Structure

```
settings.gradle                                     (edit: add snakeyaml to the version catalog)
btrace-qe/
  build.gradle
  cases/
    agent-args/fileroll-ms-negative.yaml
    agent-args/fileroll-maxrolls-non-numeric.yaml
    cli/missing-operand.yaml
    cli/unknown-flag.yaml
    cli/attach-nonexistent-target.yaml
  src/main/btrace/
    QeStartupProbe.java          startup script for target-mode cases (prints once)
    QeCanaryProbe.java           probe submitted post-abuse to prove BTrace still works
  src/targets/java/qe/targets/
    SteadyStateTarget.java       fixture JVM; separate source set so the target's classpath holds only this
  src/main/java/io/btrace/qe/
    QeMain.java
    model/      Severity, Intent, VerdictValue, SpecRef, GivenDef, ExpectationDef,
                CaseDef, CaseValidationException, Finding, CaseVerdict
    corpus/     YamlCaseLoader, CaseRegistry
    exec/       OutputPump, TargetProcess, QeEnvironment, TargetLauncher, ClientLauncher,
                ClientProbe, StepOutcome, ExecutionTrace, CaseExecutor
    oracle/     CheckOutcome, CheckResult, InvariantCheck, NoCrashCheck, NoHangCheck,
                NoCorruptionCheck, CanaryCheck, GuidanceCheck, OracleRunner
    report/     RunMetadata, JsonWriter, JsonReporter, ReproCommand, MarkdownReporter, EvidenceStore
  src/test/java/io/btrace/qe/
    model/CaseDefTest.java                      unit (runs in `test`)
    corpus/YamlCaseLoaderTest.java              unit
    corpus/CaseRegistryTest.java                unit
    oracle/OracleRunnerTest.java                unit
    report/JsonWriterTest.java                  unit
    report/MarkdownReporterTest.java            unit
    exec/TargetLauncherSelfTest.java            @Tag("qe-self-test") — runs only in `qeSelfTest`
    exec/CaseExecutorSelfTest.java              @Tag("qe-self-test") — runs only in `qeSelfTest`
  src/test/resources/io/btrace/qe/corpus/
    valid-case.yaml
    conformance-missing-spec.yaml
scripts/qe-agent.sh
docs/QEAgent.md
docs/README.md                                      (edit: one row in the documentation map)
```

Boundaries: `model` is dumb data plus validation; `corpus` turns YAML into `model`; `exec` runs a case against real processes and produces a trace; `oracle` judges a trace; `report` renders judged cases. The `targets` source set exists so the fixture JVM's classpath contains only fixture classes. BTrace itself enters the target solely through `-javaagent:btrace.jar`, the same way it does for users.

---

### Task 1: Module scaffold, source sets, and a working no-op `qe` task

**Files:**
- Modify: `settings.gradle`
- Create: `btrace-qe/build.gradle`
- Create: `btrace-qe/src/main/java/io/btrace/qe/QeMain.java`

**Interfaces:**
- Produces: `io.btrace.qe.QeMain.main(String[])`; the `targets` source set; the `qe` task with the system-property contract every later task reads (`qe.javaHome`, `btrace.libs`, `qe.corpusDir`, `qe.reportDir`, `qe.btraceVersion`, `qe.targetsClasspath`).

- [ ] **Step 1: Add SnakeYAML to the version catalog**

In `settings.gradle`, inside `libs { ... }`, next to the other `version(...)` lines:

```groovy
            version('snakeyaml', '2.3')
```

and in the `// *** Libraries ***` section:

```groovy
            // https://mvnrepository.com/artifact/org.yaml/snakeyaml
            library ('snakeyaml', 'org.yaml', 'snakeyaml').versionRef('snakeyaml')
```

- [ ] **Step 2: Create `btrace-qe/build.gradle`**

```groovy
sourceSets {
    targets
}

// The fixture JVM may run on any supported JDK (TEST_JAVA_HOME can be 8), so pin its bytecode.
compileTargetsJava {
    sourceCompatibility = 8
    targetCompatibility = 8
    javaCompiler = javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    implementation project(':btrace-client')
    implementation project(':btrace-core')
    implementation libs.snakeyaml
}

def btraceDistProject = project(':btrace-dist')
def qeLibsDir = btraceDistProject.layout.buildDirectory.dir("resources/main/v${project.version}/libs")
def releaseBtraceJar = btraceDistProject.layout.buildDirectory.file("resources/main/v${project.version}/libs/btrace.jar")
def resolvedJavaHome = System.getenv('TEST_JAVA_HOME') ?: System.getenv('JAVA_HOME') ?: System.getProperty('java.home')

// Shared by `qe` and `qeSelfTest` so both see the same environment.
ext.configureQeEnvironment = { task ->
    task.systemProperty 'qe.javaHome', resolvedJavaHome
    task.systemProperty 'btrace.libs', qeLibsDir.get().asFile.absolutePath
    task.systemProperty 'qe.corpusDir', "${projectDir}/cases"
    task.systemProperty 'qe.reportDir', "${project.buildDir}/qe-report"
    task.systemProperty 'qe.btraceVersion', project.version
    task.systemProperty 'qe.targetsClasspath', sourceSets.targets.output.classesDirs.asPath
}

task qe(type: JavaExec) {
    dependsOn ':btrace-dist:btraceJar', ':btrace-dist:explodeExtensions', targetsClasses
    inputs.file releaseBtraceJar
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'io.btrace.qe.QeMain'
    configureQeEnvironment(it)
    // an exploration tool, never a build gate
    ignoreExitValue = true
}
```

- [ ] **Step 3: Minimal `QeMain`**

```java
package io.btrace.qe;

public final class QeMain {
  private QeMain() {}

  public static void main(String[] args) {
    try {
      System.out.println("[qe] scaffold OK. javaHome=" + System.getProperty("qe.javaHome"));
      System.out.println("[qe] btrace.libs=" + System.getProperty("btrace.libs"));
      System.out.println("[qe] targetsClasspath=" + System.getProperty("qe.targetsClasspath"));
    } finally {
      System.exit(0);
    }
  }
}
```

- [ ] **Step 4: Gate**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qe > /tmp/qe-t1.log 2>&1; grep -E '\[qe\]|BUILD|error' /tmp/qe-t1.log
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:spotlessCheck > /tmp/qe-t1-spotless.log 2>&1; grep -E 'BUILD|violations' /tmp/qe-t1-spotless.log
```

Expected: `BUILD SUCCESSFUL` for both; three `[qe]` lines with non-null paths; `ls "$(grep -o 'btrace.libs=.*' /tmp/qe-t1.log | cut -d= -f2)"` lists `btrace.jar`.

- [ ] **Step 5: Stage (do not commit)**

```bash
git add settings.gradle btrace-qe/build.gradle btrace-qe/src/main/java/io/btrace/qe/QeMain.java
git status --short
```

---

### Task 2: Case model and validation

**Files:**
- Create: `btrace-qe/src/main/java/io/btrace/qe/model/{Severity,Intent,VerdictValue,SpecRef,GivenDef,ExpectationDef,CaseValidationException,CaseDef,Finding,CaseVerdict}.java`
- Test: `btrace-qe/src/test/java/io/btrace/qe/model/CaseDefTest.java`

**Interfaces:**
- Produces: `CaseDef(id, surface, intent, title, spec, given, steps, expect)` with `.validate()`; `GivenDef.target(String target, String agentArgs)` / `GivenDef.client(List<String> clientArgs)`; `ExpectationDef(Integer exitCode, List<String> messageMustMention)`; `Finding.of(caseId, severity, summary, detail)`; `CaseVerdict(def, resilience, conformance, guidance, findings)`.

There is no `crashForbidden` field. Crash, hang, and corruption checks apply to every case unconditionally. `exitCode` is a documented-behavior claim, so it may only appear on `intent: conformance` cases, and `validate()` enforces that.

- [ ] **Step 1: Enums**

```java
package io.btrace.qe.model;

public enum Severity {
  S1,
  S2,
  S3,
  S4
}
```

```java
package io.btrace.qe.model;

public enum Intent {
  RESILIENCE,
  CONFORMANCE
}
```

```java
package io.btrace.qe.model;

public enum VerdictValue {
  PASS,
  FAIL,
  GOOD,
  POOR,
  NA
}
```

- [ ] **Step 2: `SpecRef`, `GivenDef`, `ExpectationDef`**

```java
package io.btrace.qe.model;

public final class SpecRef {
  private final String doc;
  private final String claim;

  public SpecRef(String doc, String claim) {
    this.doc = doc;
    this.claim = claim;
  }

  public String doc() {
    return doc;
  }

  public String claim() {
    return claim;
  }
}
```

```java
package io.btrace.qe.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GivenDef {
  public enum Mode {
    TARGET,
    CLIENT
  }

  private final Mode mode;
  private final String target;
  private final String agentArgs;
  private final List<String> clientArgs;

  private GivenDef(Mode mode, String target, String agentArgs, List<String> clientArgs) {
    this.mode = mode;
    this.target = target;
    this.agentArgs = agentArgs;
    this.clientArgs = clientArgs;
  }

  public static GivenDef target(String target, String agentArgs) {
    return new GivenDef(Mode.TARGET, target, agentArgs, Collections.<String>emptyList());
  }

  public static GivenDef client(List<String> clientArgs) {
    return new GivenDef(
        Mode.CLIENT, null, null, Collections.unmodifiableList(new ArrayList<>(clientArgs)));
  }

  public Mode mode() {
    return mode;
  }

  public String target() {
    return target;
  }

  public String agentArgs() {
    return agentArgs;
  }

  public List<String> clientArgs() {
    return clientArgs;
  }
}
```

```java
package io.btrace.qe.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExpectationDef {
  private final Integer exitCode;
  private final List<String> messageMustMention;

  public ExpectationDef(Integer exitCode, List<String> messageMustMention) {
    this.exitCode = exitCode;
    this.messageMustMention =
        messageMustMention == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<>(messageMustMention));
  }

  public Integer exitCode() {
    return exitCode;
  }

  public List<String> messageMustMention() {
    return messageMustMention;
  }
}
```

- [ ] **Step 3: `CaseValidationException`, `CaseDef`**

```java
package io.btrace.qe.model;

public final class CaseValidationException extends RuntimeException {
  public CaseValidationException(String message) {
    super(message);
  }
}
```

```java
package io.btrace.qe.model;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CaseDef {
  private static final Set<String> TARGET_STEPS =
      new LinkedHashSet<>(Arrays.asList("launch", "awaitReady", "canary", "shutdown", "awaitExit"));
  private static final Set<String> CLIENT_STEPS =
      new LinkedHashSet<>(Arrays.asList("runClient", "awaitExit"));

  private final String id;
  private final String surface;
  private final Intent intent;
  private final String title;
  private final SpecRef spec;
  private final GivenDef given;
  private final List<String> steps;
  private final ExpectationDef expect;

  public CaseDef(
      String id,
      String surface,
      Intent intent,
      String title,
      SpecRef spec,
      GivenDef given,
      List<String> steps,
      ExpectationDef expect) {
    this.id = id;
    this.surface = surface;
    this.intent = intent;
    this.title = title;
    this.spec = spec;
    this.given = given;
    this.steps = steps;
    this.expect = expect;
  }

  public void validate() {
    require(id != null && !id.trim().isEmpty(), "id is required");
    require(surface != null && !surface.trim().isEmpty(), "surface is required");
    require(intent != null, "intent is required");
    require(title != null && !title.trim().isEmpty(), "title is required");
    require(given != null, "given is required");
    require(steps != null && !steps.isEmpty(), "steps must not be empty");
    require(expect != null, "expect is required");
    if (intent == Intent.CONFORMANCE) {
      require(
          spec != null && spec.doc() != null && spec.claim() != null,
          "conformance cases require spec.doc and spec.claim");
    } else {
      require(
          expect.exitCode() == null,
          "expect.exitCode is a documented-behavior claim and is only allowed on conformance cases");
    }
    Set<String> allowed = given.mode() == GivenDef.Mode.TARGET ? TARGET_STEPS : CLIENT_STEPS;
    for (String step : steps) {
      require(allowed.contains(step), "step '" + step + "' is not valid for mode " + given.mode());
    }
  }

  private void require(boolean condition, String message) {
    if (!condition) {
      throw new CaseValidationException((id != null ? id : "<unknown case>") + ": " + message);
    }
  }

  public String id() {
    return id;
  }

  public String surface() {
    return surface;
  }

  public Intent intent() {
    return intent;
  }

  public String title() {
    return title;
  }

  public SpecRef spec() {
    return spec;
  }

  public GivenDef given() {
    return given;
  }

  public List<String> steps() {
    return steps;
  }

  public ExpectationDef expect() {
    return expect;
  }
}
```

- [ ] **Step 4: `Finding`, `CaseVerdict`**

```java
package io.btrace.qe.model;

public final class Finding {
  private final String caseId;
  private final Severity severity;
  private final String summary;
  private final String detail;

  private Finding(String caseId, Severity severity, String summary, String detail) {
    this.caseId = caseId;
    this.severity = severity;
    this.summary = summary;
    this.detail = detail;
  }

  public static Finding of(String caseId, Severity severity, String summary, String detail) {
    return new Finding(caseId, severity, summary, detail);
  }

  public String caseId() {
    return caseId;
  }

  public Severity severity() {
    return severity;
  }

  public String summary() {
    return summary;
  }

  public String detail() {
    return detail;
  }
}
```

```java
package io.btrace.qe.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CaseVerdict {
  private final CaseDef def;
  private final VerdictValue resilience;
  private final VerdictValue conformance;
  private final VerdictValue guidance;
  private final List<Finding> findings;

  public CaseVerdict(
      CaseDef def,
      VerdictValue resilience,
      VerdictValue conformance,
      VerdictValue guidance,
      List<Finding> findings) {
    this.def = def;
    this.resilience = resilience;
    this.conformance = conformance;
    this.guidance = guidance;
    this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
  }

  public CaseDef def() {
    return def;
  }

  public VerdictValue resilience() {
    return resilience;
  }

  public VerdictValue conformance() {
    return conformance;
  }

  public VerdictValue guidance() {
    return guidance;
  }

  public List<Finding> findings() {
    return findings;
  }
}
```

- [ ] **Step 5: Test**

`btrace-qe/src/test/java/io/btrace/qe/model/CaseDefTest.java`:
```java
package io.btrace.qe.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseDefTest {

  private static CaseDef caseOf(Intent intent, SpecRef spec, GivenDef given, List<String> steps, Integer exitCode) {
    return new CaseDef(
        "x.case", "x", intent, "title", spec, given, steps,
        new ExpectationDef(exitCode, Collections.<String>emptyList()));
  }

  @Test
  void validTargetCasePasses() {
    CaseDef def =
        caseOf(
            Intent.RESILIENCE,
            null,
            GivenDef.target("SteadyStateTarget", "script=X.class"),
            List.of("launch", "awaitReady", "canary", "shutdown", "awaitExit"),
            null);
    assertDoesNotThrow(def::validate);
  }

  @Test
  void conformanceCaseWithoutSpecFails() {
    CaseDef def =
        caseOf(Intent.CONFORMANCE, null, GivenDef.client(List.of("-p")), List.of("runClient", "awaitExit"), 1);
    CaseValidationException ex = assertThrows(CaseValidationException.class, def::validate);
    assertTrue(ex.getMessage().contains("spec"));
  }

  @Test
  void exitCodeOnResilienceCaseFails() {
    CaseDef def =
        caseOf(Intent.RESILIENCE, null, GivenDef.client(List.of("-p")), List.of("runClient", "awaitExit"), 1);
    CaseValidationException ex = assertThrows(CaseValidationException.class, def::validate);
    assertTrue(ex.getMessage().contains("exitCode"));
  }

  @Test
  void stepNotValidForModeFails() {
    CaseDef def = caseOf(Intent.RESILIENCE, null, GivenDef.client(List.of("-p")), List.of("canary"), null);
    assertThrows(CaseValidationException.class, def::validate);
  }
}
```

- [ ] **Step 6: Gate**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:test --tests 'io.btrace.qe.model.*' :btrace-qe:spotlessCheck > /tmp/qe-t2.log 2>&1; grep -E 'PASSED|FAILED|BUILD|tests completed' /tmp/qe-t2.log
```

Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 7: Stage (do not commit)**

```bash
git add btrace-qe/src/main/java/io/btrace/qe/model btrace-qe/src/test/java/io/btrace/qe/model
git status --short
```

---

### Task 3: YAML case loader and registry

**Files:**
- Create: `btrace-qe/src/main/java/io/btrace/qe/corpus/YamlCaseLoader.java`
- Create: `btrace-qe/src/main/java/io/btrace/qe/corpus/CaseRegistry.java`
- Test: `btrace-qe/src/test/java/io/btrace/qe/corpus/YamlCaseLoaderTest.java`
- Test: `btrace-qe/src/test/java/io/btrace/qe/corpus/CaseRegistryTest.java`
- Create: `btrace-qe/src/test/resources/io/btrace/qe/corpus/valid-case.yaml`
- Create: `btrace-qe/src/test/resources/io/btrace/qe/corpus/conformance-missing-spec.yaml`

**Interfaces:**
- Consumes: the Task 2 model.
- Produces: `new YamlCaseLoader().loadAll(Path corpusDir)` → validated `List<CaseDef>`; `CaseRegistry.of(List<CaseDef>)` with `.all()` and `.filter(String surfaceCsv, String caseId)`.

YAML schema (the authoritative reference for case authors):

```yaml
id: <surface>.<name>              # required, unique
surface: <name>                   # required
intent: resilience|conformance    # required
title: "<one line>"               # required
spec:                             # required iff intent: conformance
  doc: docs/<file>.md
  claim: "<the documented claim being tested>"
given:
  mode: target|client
  target: SteadyStateTarget       # mode: target
  agentArgs: "<raw -javaagent args; ${probeClassesDir} and ${runDir} are substituted>"
  clientArgs: ["<argv>", ...]     # mode: client
steps: [<verb>, ...]              # target: launch, awaitReady, canary, shutdown, awaitExit
                                  # client: runClient, awaitExit
expect:
  exitCode: <int>                 # conformance cases only
  messageMustMention: ["<token>"] # guidance tokens expected in stdout/stderr
```

Unknown keys are rejected. That stops a misspelled field (`messageMustMentoin`) from quietly turning a guidance check into N/A.

- [ ] **Step 1: Fixtures**

`valid-case.yaml`:
```yaml
id: fixture.valid
surface: fixture
intent: resilience
title: "Fixture case used by YamlCaseLoaderTest"
given:
  mode: target
  target: SteadyStateTarget
  agentArgs: "script=${probeClassesDir}/io/btrace/qe/probe/QeStartupProbe.class,stdout=true"
steps:
  - launch
  - awaitReady
  - canary
  - shutdown
  - awaitExit
expect:
  messageMustMention:
    - "example"
```

`conformance-missing-spec.yaml`:
```yaml
id: fixture.missing-spec
surface: fixture
intent: conformance
title: "Fixture case missing the required spec block"
given:
  mode: client
  clientArgs:
    - "-p"
steps:
  - runClient
  - awaitExit
expect:
  exitCode: 1
```

- [ ] **Step 2: Failing loader test**

```java
package io.btrace.qe.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseValidationException;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

class YamlCaseLoaderTest {

  private Path fixtureDir(String... names) throws Exception {
    Path tmp = Files.createTempDirectory("qe-corpus-test");
    for (String name : names) {
      Path source = Paths.get(getClass().getResource("/io/btrace/qe/corpus/" + name).toURI());
      Files.copy(source, tmp.resolve(name));
    }
    return tmp;
  }

  @Test
  void loadsAndValidatesAValidCase() throws Exception {
    List<CaseDef> cases = new YamlCaseLoader().loadAll(fixtureDir("valid-case.yaml"));
    assertEquals(1, cases.size());
    CaseDef def = cases.get(0);
    assertEquals("fixture.valid", def.id());
    assertEquals(Intent.RESILIENCE, def.intent());
    assertEquals(GivenDef.Mode.TARGET, def.given().mode());
    assertEquals(List.of("launch", "awaitReady", "canary", "shutdown", "awaitExit"), def.steps());
    assertEquals(List.of("example"), def.expect().messageMustMention());
  }

  @Test
  void rejectsAConformanceCaseMissingSpec() throws Exception {
    Path dir = fixtureDir("conformance-missing-spec.yaml");
    CaseValidationException ex =
        assertThrows(CaseValidationException.class, () -> new YamlCaseLoader().loadAll(dir));
    assertTrue(ex.getMessage().contains("spec"));
  }

  @Test
  void rejectsUnknownKeysWithTheFileName() throws Exception {
    Path dir = Files.createTempDirectory("qe-corpus-typo");
    String yaml =
        "id: fixture.typo\nsurface: fixture\nintent: resilience\ntitle: t\n"
            + "given:\n  mode: client\n  clientArgs: [\"-p\"]\n"
            + "steps: [runClient, awaitExit]\n"
            + "expect:\n  messageMustMentoin: [\"x\"]\n";
    Files.write(dir.resolve("typo.yaml"), yaml.getBytes(StandardCharsets.UTF_8));
    CaseValidationException ex =
        assertThrows(CaseValidationException.class, () -> new YamlCaseLoader().loadAll(dir));
    assertTrue(ex.getMessage().contains("typo.yaml"));
    assertTrue(ex.getMessage().contains("messageMustMentoin"));
  }

  @Test
  void skipsNonYamlFiles() throws Exception {
    Path dir = fixtureDir("valid-case.yaml");
    Files.write(dir.resolve("notes.txt"), "ignore me".getBytes(StandardCharsets.UTF_8));
    assertEquals(1, new YamlCaseLoader().loadAll(dir).size());
  }
}
```

Run `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:test --tests 'io.btrace.qe.corpus.YamlCaseLoaderTest' > /tmp/qe-t3a.log 2>&1; grep -E 'error:|BUILD' /tmp/qe-t3a.log`. Expected: compilation failure (`YamlCaseLoader` missing).

- [ ] **Step 3: Implement `YamlCaseLoader`**

```java
package io.btrace.qe.corpus;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseValidationException;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import io.btrace.qe.model.SpecRef;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;

public final class YamlCaseLoader {
  private static final Set<String> TOP_KEYS =
      new HashSet<>(Arrays.asList("id", "surface", "intent", "title", "spec", "given", "steps", "expect"));
  private static final Set<String> SPEC_KEYS = new HashSet<>(Arrays.asList("doc", "claim"));
  private static final Set<String> GIVEN_KEYS =
      new HashSet<>(Arrays.asList("mode", "target", "agentArgs", "clientArgs"));
  private static final Set<String> EXPECT_KEYS =
      new HashSet<>(Arrays.asList("exitCode", "messageMustMention"));

  public List<CaseDef> loadAll(Path corpusDir) throws IOException {
    if (!Files.isDirectory(corpusDir)) {
      throw new IOException("case corpus directory does not exist: " + corpusDir);
    }
    List<Path> files;
    try (Stream<Path> walk = Files.walk(corpusDir)) {
      files = walk.filter(p -> p.toString().endsWith(".yaml")).sorted().collect(Collectors.toList());
    }
    List<CaseDef> result = new ArrayList<>();
    Yaml yaml = new Yaml();
    for (Path file : files) {
      Object loaded;
      try (InputStream in = Files.newInputStream(file)) {
        loaded = yaml.load(in);
      }
      CaseDef def = parse(file, asMap(file, "document", loaded, TOP_KEYS));
      def.validate();
      result.add(def);
    }
    return result;
  }

  private CaseDef parse(Path file, Map<String, Object> raw) {
    String id = requireString(file, raw, "id");
    String surface = requireString(file, raw, "surface");
    Intent intent = Intent.valueOf(requireString(file, raw, "intent").toUpperCase(Locale.ROOT));
    String title = requireString(file, raw, "title");
    SpecRef spec = parseSpec(file, raw.get("spec"));
    GivenDef given = parseGiven(file, raw.get("given"));
    List<String> steps = requireStringList(file, raw, "steps");
    ExpectationDef expect = parseExpect(file, raw.get("expect"));
    return new CaseDef(id, surface, intent, title, spec, given, steps, expect);
  }

  private SpecRef parseSpec(Path file, Object rawSpec) {
    if (rawSpec == null) {
      return null;
    }
    Map<String, Object> spec = asMap(file, "spec", rawSpec, SPEC_KEYS);
    return new SpecRef(stringOrNull(spec.get("doc")), stringOrNull(spec.get("claim")));
  }

  private GivenDef parseGiven(Path file, Object rawGiven) {
    Map<String, Object> given = asMap(file, "given", rawGiven, GIVEN_KEYS);
    String mode = requireString(file, given, "mode").toUpperCase(Locale.ROOT);
    if ("TARGET".equals(mode)) {
      return GivenDef.target(requireString(file, given, "target"), requireString(file, given, "agentArgs"));
    }
    if ("CLIENT".equals(mode)) {
      return GivenDef.client(requireStringList(file, given, "clientArgs"));
    }
    throw new CaseValidationException(file + ": given.mode must be 'target' or 'client', was " + mode);
  }

  private ExpectationDef parseExpect(Path file, Object rawExpect) {
    Map<String, Object> expect = asMap(file, "expect", rawExpect, EXPECT_KEYS);
    Object rawExit = expect.get("exitCode");
    Integer exitCode = rawExit == null ? null : Integer.valueOf(rawExit.toString());
    List<String> mentions =
        expect.containsKey("messageMustMention")
            ? requireStringList(file, expect, "messageMustMention")
            : Collections.<String>emptyList();
    return new ExpectationDef(exitCode, mentions);
  }

  private Map<String, Object> asMap(Path file, String what, Object value, Set<String> allowedKeys) {
    if (!(value instanceof Map)) {
      throw new CaseValidationException(file + ": " + what + " must be a mapping");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) value;
    for (String key : map.keySet()) {
      if (!allowedKeys.contains(key)) {
        throw new CaseValidationException(file + ": unknown key '" + key + "' in " + what);
      }
    }
    return map;
  }

  private String requireString(Path file, Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    if (value == null) {
      throw new CaseValidationException(file + ": '" + key + "' is required");
    }
    return value.toString();
  }

  private List<String> requireStringList(Path file, Map<String, Object> raw, String key) {
    Object value = raw.get(key);
    if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
      throw new CaseValidationException(file + ": '" + key + "' must be a non-empty list");
    }
    List<String> out = new ArrayList<>();
    for (Object item : (List<?>) value) {
      out.add(String.valueOf(item));
    }
    return out;
  }

  private static String stringOrNull(Object value) {
    return value == null ? null : value.toString();
  }
}
```

Re-run the loader test. Expected: 4 passed.

- [ ] **Step 4: `CaseRegistry` with test**

```java
package io.btrace.qe.corpus;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseValidationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class CaseRegistry {
  private final List<CaseDef> cases;

  private CaseRegistry(List<CaseDef> cases) {
    this.cases = cases;
  }

  public static CaseRegistry of(List<CaseDef> cases) {
    List<CaseDef> sorted = new ArrayList<>(cases);
    sorted.sort(Comparator.comparing(CaseDef::id));
    Set<String> seen = new HashSet<>();
    for (CaseDef def : sorted) {
      if (!seen.add(def.id())) {
        throw new CaseValidationException("duplicate case id: " + def.id());
      }
    }
    return new CaseRegistry(sorted);
  }

  public List<CaseDef> all() {
    return Collections.unmodifiableList(cases);
  }

  public List<CaseDef> filter(String surfaceCsv, String caseId) {
    return cases.stream()
        .filter(c -> caseId == null || caseId.isEmpty() || c.id().equals(caseId))
        .filter(c -> surfaceCsv == null || surfaceCsv.isEmpty() || matchesSurface(c.surface(), surfaceCsv))
        .collect(Collectors.toList());
  }

  private static boolean matchesSurface(String surface, String csv) {
    for (String want : csv.split(",")) {
      if (surface.equals(want.trim())) {
        return true;
      }
    }
    return false;
  }
}
```

```java
package io.btrace.qe.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseValidationException;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CaseRegistryTest {

  private CaseDef caseOf(String id, String surface) {
    return new CaseDef(
        id, surface, Intent.RESILIENCE, id, null,
        GivenDef.client(List.of("-p")),
        List.of("runClient", "awaitExit"),
        new ExpectationDef(null, Collections.<String>emptyList()));
  }

  @Test
  void sortsByIdAndRejectsDuplicates() {
    CaseRegistry registry = CaseRegistry.of(List.of(caseOf("b.case", "cli"), caseOf("a.case", "cli")));
    assertEquals(
        List.of("a.case", "b.case"),
        registry.all().stream().map(CaseDef::id).collect(Collectors.toList()));
    assertThrows(
        CaseValidationException.class,
        () -> CaseRegistry.of(List.of(caseOf("dup", "cli"), caseOf("dup", "cli"))));
  }

  @Test
  void filtersBySurfaceAndCaseId() {
    CaseRegistry registry =
        CaseRegistry.of(
            List.of(caseOf("cli.one", "cli"), caseOf("cli.two", "cli"), caseOf("agent-args.one", "agent-args")));
    assertEquals(2, registry.filter("cli", null).size());
    assertEquals(3, registry.filter("cli, agent-args", null).size());
    assertEquals(1, registry.filter(null, "agent-args.one").size());
    assertEquals(3, registry.filter(null, null).size());
    assertEquals(0, registry.filter("script-compile", null).size());
  }
}
```

- [ ] **Step 5: Gate**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:test --tests 'io.btrace.qe.corpus.*' :btrace-qe:spotlessCheck > /tmp/qe-t3.log 2>&1; grep -E 'PASSED|FAILED|BUILD' /tmp/qe-t3.log
```

Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 6: Stage (do not commit)**

```bash
git add btrace-qe/src/main/java/io/btrace/qe/corpus btrace-qe/src/test/java/io/btrace/qe/corpus btrace-qe/src/test/resources
git status --short
```

---

### Task 4: Fixture target, probes, probe compilation, and the `qeSelfTest` task

**Files:**
- Create: `btrace-qe/src/targets/java/qe/targets/SteadyStateTarget.java`
- Create: `btrace-qe/src/main/btrace/QeStartupProbe.java`
- Create: `btrace-qe/src/main/btrace/QeCanaryProbe.java`
- Modify: `btrace-qe/build.gradle` (`compileQeProbes`, `qe.probeClassesDir`, `qe.timeout` forwarding, `test` tag exclusion, `qeSelfTest`)

**Interfaces:**
- Produces: `qe.targets.SteadyStateTarget`. It prints `ready:<pid>`, calls `startupMarker()` once, then calls `canaryTick()` every 10 ms and prints `checksum:<n>` about every 200 ms. It exits 0 on stdin `exit`. Also produced: `QeStartupProbe.class` and `QeCanaryProbe.class` under `build/classes/qe-probes/io/btrace/qe/probe/`, and the `qeSelfTest` task.

There are two probes so the startup script and the canary never collide. `QeStartupProbe` is the `script=` for target-mode cases and prints `qe-startup-ok` exactly once. `QeCanaryProbe` is submitted after the abuse and prints `qe-canary-ok` on every tick. Its output goes over the wire to the harness's client, so nothing is written into the build directory.

Agent `script=` must be the path to the `.class` file. `loadBTraceScript` does `new File(filePath)` (`btrace-agent/.../agent/Main.java:1461`), and `scriptdir=` only does a non-recursive `File.listFiles()` (`Main.java:836`), so it would never see a class nested in a package directory. Target-mode cases also pass `stdout=true` so the startup probe's output lands on the target's stdout, not in a file next to the probe class.

- [ ] **Step 1: Fixture target**

```java
package qe.targets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;

/**
 * Long-running fixture for the QE harness. Runs on any supported JDK, so it uses Java 8 APIs only.
 */
public final class SteadyStateTarget {
  private SteadyStateTarget() {}

  public static void main(String[] args) throws Exception {
    String vmName = ManagementFactory.getRuntimeMXBean().getName();
    String pid = vmName.contains("@") ? vmName.substring(0, vmName.indexOf('@')) : vmName;
    System.out.println("ready:" + pid);
    System.out.flush();
    startupMarker();

    Thread stdinThread = new Thread(SteadyStateTarget::watchStdin, "qe-target-stdin");
    stdinThread.setDaemon(true);
    stdinThread.start();

    long counter = 0;
    long lastChecksumAt = System.currentTimeMillis();
    while (true) {
      counter++;
      canaryTick();
      long now = System.currentTimeMillis();
      if (now - lastChecksumAt >= 200) {
        System.out.println("checksum:" + counter);
        System.out.flush();
        lastChecksumAt = now;
      }
      Thread.sleep(10);
    }
  }

  private static void watchStdin() {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if ("exit".equals(line.trim())) {
          System.out.println("exiting:0");
          System.out.flush();
          System.exit(0);
        }
      }
    } catch (IOException ignored) {
      // parent closed stdin; keep running until terminated
    }
  }

  /** Instrumentation point for the startup probe. Called exactly once. */
  public static void startupMarker() {}

  /** Instrumentation point for the canary probe. */
  public static void canaryTick() {}
}
```

- [ ] **Step 2: Probes**

`btrace-qe/src/main/btrace/QeStartupProbe.java`:
```java
package io.btrace.qe.probe;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.OnMethod;

import static io.btrace.core.BTraceUtils.println;

@BTrace
public class QeStartupProbe {
  @OnMethod(clazz = "qe.targets.SteadyStateTarget", method = "startupMarker")
  public static void onStartup() {
    println("qe-startup-ok");
  }
}
```

`btrace-qe/src/main/btrace/QeCanaryProbe.java`:
```java
package io.btrace.qe.probe;

import io.btrace.core.annotations.BTrace;
import io.btrace.core.annotations.OnMethod;

import static io.btrace.core.BTraceUtils.println;

@BTrace
public class QeCanaryProbe {
  @OnMethod(clazz = "qe.targets.SteadyStateTarget", method = "canaryTick")
  public static void onTick() {
    println("qe-canary-ok");
  }
}
```

- [ ] **Step 3: Extend `btrace-qe/build.gradle`**

Add above the `qe` task (this mirrors `integration-tests/build.gradle`'s `compileTestProbes`):

```groovy
def probeClassesDir = layout.buildDirectory.dir('classes/qe-probes')

task compileQeProbes {
    dependsOn compileJava, ':btrace-agent:jar', ':btrace-compiler:jar'
    inputs.dir 'src/main/btrace'
    outputs.dir probeClassesDir
    doLast {
        def path = sourceSets.main.runtimeClasspath
        def loader = new URLClassLoader(path.collect { f -> f.toURI().toURL() } as URL[])
        def compiler = loader.loadClass('io.btrace.compiler.Compiler')
        def outDir = probeClassesDir.get().asFile
        outDir.mkdirs()
        def args = ['-cp', path.getAsPath(), '-d', outDir.toString()]
        args.addAll(fileTree('src/main/btrace').include('**/*.java').files.collect { it.toString() }.sort())
        def prev = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = loader
            compiler.main(args as String[])
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
    }
}
```

Extend `configureQeEnvironment` with the probe directory and timeout forwarding:

```groovy
ext.configureQeEnvironment = { task ->
    task.systemProperty 'qe.javaHome', resolvedJavaHome
    task.systemProperty 'btrace.libs', qeLibsDir.get().asFile.absolutePath
    task.systemProperty 'qe.corpusDir', "${projectDir}/cases"
    task.systemProperty 'qe.reportDir', "${project.buildDir}/qe-report"
    task.systemProperty 'qe.btraceVersion', project.version
    task.systemProperty 'qe.targetsClasspath', sourceSets.targets.output.classesDirs.asPath
    task.systemProperty 'qe.probeClassesDir', probeClassesDir.get().asFile.absolutePath
    task.systemProperty 'qe.timeout', project.findProperty('qe.timeout') ?: '30000'
}
```

Add `compileQeProbes` to `qe.dependsOn`:

```groovy
    dependsOn ':btrace-dist:btraceJar', ':btrace-dist:explodeExtensions', targetsClasses, compileQeProbes
```

Then add the test split. Unit tests stay in `test`, which `check` and `build` run and which must stay fast and JVM-free. Self-tests that spawn real JVMs run only in `qeSelfTest`:

```groovy
test {
    useJUnitPlatform {
        excludeTags 'qe-self-test'
    }
}

task qeSelfTest(type: Test) {
    description = 'Self-tests of the QE harness against real JVMs (never part of check/build).'
    group = 'verification'
    dependsOn ':btrace-dist:btraceJar', ':btrace-dist:explodeExtensions', targetsClasses, compileQeProbes
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
    useJUnitPlatform {
        includeTags 'qe-self-test'
    }
    configureQeEnvironment(it)
    testLogging {
        events 'passed', 'skipped', 'failed'
    }
}
```

- [ ] **Step 4: Gate**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:compileQeProbes :btrace-qe:targetsClasses :btrace-qe:spotlessCheck > /tmp/qe-t4.log 2>&1; grep -E 'error|BUILD' /tmp/qe-t4.log
ls btrace-qe/build/classes/qe-probes/io/btrace/qe/probe/
javap -v btrace-qe/build/classes/java/targets/qe/targets/SteadyStateTarget.class | grep 'major version'
```

Expected: `BUILD SUCCESSFUL`; `QeCanaryProbe.class` and `QeStartupProbe.class` listed; `major version: 52`. If the BTrace compiler reports a verifier error, compare against the safe-mode rules in [docs/Troubleshooting.md](../../docs/Troubleshooting.md).

- [ ] **Step 5: Stage (do not commit)**

```bash
git add btrace-qe/build.gradle btrace-qe/src/targets btrace-qe/src/main/btrace
git status --short
```

---

### Task 5: Process primitives (`OutputPump`, `TargetProcess`, `QeEnvironment`, `TargetLauncher`, `ClientLauncher`)

**Files:**
- Create: `btrace-qe/src/main/java/io/btrace/qe/exec/{OutputPump,TargetProcess,QeEnvironment,TargetLauncher,ClientLauncher}.java`
- Test: `btrace-qe/src/test/java/io/btrace/qe/exec/TargetLauncherSelfTest.java`

**Interfaces:**
- Consumes: `GivenDef` (Task 2); `targets`/probe outputs (Task 4).
- Produces:
  - `QeEnvironment.fromSystemProperties()`: `javaHome()`, `btraceJarPath()`, `probeClassesDir()`, `targetsClasspath()`, `defaultTimeoutMs()`, `allocateFreePort()`, `jcmdPath()`.
  - `new TargetLauncher().launch(GivenDef, QeEnvironment, Path runDir)` → `TargetProcess`.
  - `new ClientLauncher().launch(List<String> clientArgs, QeEnvironment, Path runDir)` → `TargetProcess`.
  - `TargetProcess`: `awaitLine(prefix, timeoutMs)`, which returns early if the process dies; `writeStdin`, `stdoutSnapshot`, `stderrSnapshot`, `pid`, `assignedPort`, `process`, `isAlive`, `destroyForcibly`; package-private `assignPid`.

Client mode runs the real user entry point exactly as `btrace-dist/src/main/resources/bin/btrace` does: `java -XX:+IgnoreUnrecognizedVMOptions [--add-exports jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED when $JAVA_HOME/jmods exists] -cp btrace.jar io.btrace.boot.Loader <args>`. Running `io.btrace.client.Main` from module classes would bypass the masked-JAR loader. That would test a different artifact than users run, and `JpsUtils`'s `sun.jvmstat` access would fail on JDK 9+.

- [ ] **Step 1: `OutputPump`**

```java
package io.btrace.qe.exec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class OutputPump extends Thread {
  private final InputStream in;
  private final List<String> history;

  OutputPump(InputStream in, List<String> history, String name) {
    super(name);
    this.in = in;
    this.history = history;
    setDaemon(true);
  }

  @Override
  public void run() {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        synchronized (history) {
          history.add(line);
          history.notifyAll();
        }
      }
    } catch (IOException ignored) {
      // the process ended and closed the stream
    }
  }
}
```

- [ ] **Step 2: `TargetProcess`**

`history` and `stdout` are the same object, so `wait`/`notifyAll` share one monitor. `awaitLine` waits in ≤200 ms slices and checks liveness, so a JVM killed by an agent startup failure is reported as an early exit rather than as a 30 s "hang".

```java
package io.btrace.qe.exec;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TargetProcess {
  private final Process process;
  private final List<String> stdout = Collections.synchronizedList(new ArrayList<String>());
  private final List<String> stderr = Collections.synchronizedList(new ArrayList<String>());
  private final int port;
  private volatile long pid;

  TargetProcess(Process process, long pid, int port) {
    this.process = process;
    this.pid = pid;
    this.port = port;
    new OutputPump(process.getInputStream(), stdout, "qe-process-stdout").start();
    new OutputPump(process.getErrorStream(), stderr, "qe-process-stderr").start();
  }

  void assignPid(long pid) {
    this.pid = pid;
  }

  public long pid() {
    return pid;
  }

  public int assignedPort() {
    return port;
  }

  public Process process() {
    return process;
  }

  public List<String> stdoutSnapshot() {
    synchronized (stdout) {
      return new ArrayList<>(stdout);
    }
  }

  public List<String> stderrSnapshot() {
    synchronized (stderr) {
      return new ArrayList<>(stderr);
    }
  }

  /** Returns the first stdout line with the prefix, or null on timeout or process death. */
  public String awaitLine(String prefix, long timeoutMs) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    synchronized (stdout) {
      while (true) {
        for (String line : stdout) {
          if (line.startsWith(prefix)) {
            return line;
          }
        }
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0 || !process.isAlive()) {
          return null;
        }
        stdout.wait(Math.min(remaining, 200L));
      }
    }
  }

  public void writeStdin(String line) throws IOException {
    OutputStream os = process.getOutputStream();
    os.write((line + "\n").getBytes(StandardCharsets.UTF_8));
    os.flush();
  }

  public boolean isAlive() {
    return process.isAlive();
  }

  public void destroyForcibly() {
    process.destroyForcibly();
  }
}
```

- [ ] **Step 3: `QeEnvironment`**

```java
package io.btrace.qe.exec;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class QeEnvironment {
  private final String javaHome;
  private final Path btraceJarPath;
  private final Path probeClassesDir;
  private final String targetsClasspath;
  private final long defaultTimeoutMs;

  private QeEnvironment(
      String javaHome, Path btraceJarPath, Path probeClassesDir, String targetsClasspath, long defaultTimeoutMs) {
    this.javaHome = javaHome;
    this.btraceJarPath = btraceJarPath;
    this.probeClassesDir = probeClassesDir;
    this.targetsClasspath = targetsClasspath;
    this.defaultTimeoutMs = defaultTimeoutMs;
  }

  public static QeEnvironment fromSystemProperties() {
    String javaHome = require("qe.javaHome").replace(File.separator + "jre", "");
    Path btraceJarPath = Paths.get(require("btrace.libs"), "btrace.jar");
    if (!Files.isRegularFile(btraceJarPath)) {
      throw new IllegalStateException("btrace.jar not found at " + btraceJarPath);
    }
    Path probeClassesDir = Paths.get(require("qe.probeClassesDir"));
    String targetsClasspath = require("qe.targetsClasspath");
    long timeout = Long.parseLong(System.getProperty("qe.timeout", "30000"));
    return new QeEnvironment(javaHome, btraceJarPath, probeClassesDir, targetsClasspath, timeout);
  }

  private static String require(String key) {
    String value = System.getProperty(key);
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalStateException(key + " system property is not set");
    }
    return value;
  }

  public String javaHome() {
    return javaHome;
  }

  public String javaExecutable() {
    return Paths.get(javaHome, "bin", "java").toString();
  }

  public String jcmdPath() {
    return Paths.get(javaHome, "bin", "jcmd").toString();
  }

  public boolean isModularJdk() {
    return Files.isDirectory(Paths.get(javaHome, "jmods"));
  }

  public Path btraceJarPath() {
    return btraceJarPath;
  }

  public Path probeClassesDir() {
    return probeClassesDir;
  }

  public String targetsClasspath() {
    return targetsClasspath;
  }

  public long defaultTimeoutMs() {
    return defaultTimeoutMs;
  }

  public int allocateFreePort() {
    try (ServerSocket ss = new ServerSocket(0)) {
      return ss.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("failed to find a free port", e);
    }
  }
}
```

- [ ] **Step 4: `TargetLauncher` and `ClientLauncher`**

```java
package io.btrace.qe.exec;

import io.btrace.qe.model.GivenDef;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TargetLauncher {

  public TargetProcess launch(GivenDef given, QeEnvironment env, Path runDir) throws IOException {
    int port = env.allocateFreePort();
    String agentArgs =
        given
            .agentArgs()
            .replace("${probeClassesDir}", env.probeClassesDir().toString())
            .replace("${runDir}", runDir.toString());

    List<String> cmd = new ArrayList<>();
    cmd.add(env.javaExecutable());
    cmd.add("-cp");
    cmd.add(env.targetsClasspath());
    cmd.add("-Dbtrace.comm.protocol=2");
    cmd.add("-Dbtrace.comm.autoNegotiate=false");
    cmd.add("-Dbtrace.comm.forceVersion=true");
    cmd.add("-javaagent:" + env.btraceJarPath() + "=" + agentArgs + ",port=" + port);
    cmd.add("qe.targets." + given.target());

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.environment().remove("JAVA_TOOL_OPTIONS");
    pb.directory(runDir.toFile());
    return new TargetProcess(pb.start(), -1, port);
  }
}
```

```java
package io.btrace.qe.exec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs the btrace client the same way btrace-dist's bin/btrace launcher does. */
public final class ClientLauncher {

  public TargetProcess launch(List<String> clientArgs, QeEnvironment env, Path runDir) throws IOException {
    List<String> cmd = new ArrayList<>();
    cmd.add(env.javaExecutable());
    cmd.add("-XX:+IgnoreUnrecognizedVMOptions");
    if (env.isModularJdk()) {
      cmd.add("-XX:+AllowRedefinitionToAddDeleteMethods");
      cmd.add("--add-exports");
      cmd.add("jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED");
    }
    cmd.add("-cp");
    cmd.add(env.btraceJarPath().toString());
    cmd.add("io.btrace.boot.Loader");
    cmd.addAll(clientArgs);

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.environment().remove("JAVA_TOOL_OPTIONS");
    pb.directory(runDir.toFile());
    return new TargetProcess(pb.start(), -1, -1);
  }
}
```

`TargetLauncher` resolves the target class from `given.target()`, so validation in Task 2 only needs to check the field exists. An unknown target name fails at launch with a `ClassNotFoundException`, and that shows up as an early exit in the trace.

- [ ] **Step 5: Self-test**

```java
package io.btrace.qe.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.model.GivenDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("qe-self-test")
class TargetLauncherSelfTest {
  private static final String STARTUP =
      "script=${probeClassesDir}/io/btrace/qe/probe/QeStartupProbe.class,stdout=true";

  @Test
  void targetStartsWithStartupProbeAndExitsOnCommand() throws Exception {
    QeEnvironment env = QeEnvironment.fromSystemProperties();
    Path runDir = Files.createTempDirectory("qe-launcher");
    TargetProcess target = new TargetLauncher().launch(GivenDef.target("SteadyStateTarget", STARTUP), env, runDir);
    try {
      assertNotNull(target.awaitLine("ready:", 20_000), "no ready line; stderr=" + target.stderrSnapshot());
      assertNotNull(target.awaitLine("qe-startup-ok", 20_000), "startup probe did not run");
      target.writeStdin("exit");
      assertTrue(target.process().waitFor(10, TimeUnit.SECONDS));
      assertEquals(0, target.process().exitValue());
    } finally {
      target.destroyForcibly();
    }
  }

  @Test
  void awaitLineReturnsEarlyWhenTheProcessDies() throws Exception {
    QeEnvironment env = QeEnvironment.fromSystemProperties();
    Path runDir = Files.createTempDirectory("qe-launcher-dead");
    TargetProcess target =
        new TargetLauncher().launch(GivenDef.target("NoSuchTarget", STARTUP), env, runDir);
    long start = System.currentTimeMillis();
    assertNull(target.awaitLine("ready:", 20_000));
    assertTrue(System.currentTimeMillis() - start < 15_000, "awaitLine waited for the full timeout");
  }

  @Test
  void clientRunsThroughTheMaskedJarLoader() throws Exception {
    QeEnvironment env = QeEnvironment.fromSystemProperties();
    Path runDir = Files.createTempDirectory("qe-client");
    TargetProcess client = new ClientLauncher().launch(Arrays.asList("--version"), env, runDir);
    assertTrue(client.process().waitFor(20, TimeUnit.SECONDS));
    assertEquals(0, client.process().exitValue(), "stderr=" + client.stderrSnapshot());
    assertTrue(client.stdoutSnapshot().size() > 0);
  }
}
```

- [ ] **Step 6: Gate**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qeSelfTest --tests 'io.btrace.qe.exec.TargetLauncherSelfTest' :btrace-qe:spotlessCheck > /tmp/qe-t5.log 2>&1; grep -E 'PASSED|FAILED|BUILD|Exception' /tmp/qe-t5.log
```

Expected: `BUILD SUCCESSFUL`, 3 passed. If `ready:` never appears, read the assertion's stderr dump. Usually it means `btrace.jar` is stale (run `./gradlew :btrace-dist:btraceJar`) or the probe class is missing (run `:btrace-qe:compileQeProbes`).

- [ ] **Step 7: Stage (do not commit)**

```bash
git add btrace-qe/src/main/java/io/btrace/qe/exec btrace-qe/src/test/java/io/btrace/qe/exec
git status --short
```

---

### Task 6: Canary attach (`ClientProbe`) with a bounded lifetime

**Files:**
- Create: `btrace-qe/src/main/java/io/btrace/qe/exec/ClientProbe.java`
- Modify: `btrace-qe/src/test/java/io/btrace/qe/exec/TargetLauncherSelfTest.java`

**Interfaces:**
- Consumes: `TargetProcess`, `QeEnvironment`.
- Produces: `new ClientProbe().runCanary(TargetProcess, QeEnvironment, long timeoutMs)` → `boolean`. It returns within `timeoutMs` plus a short detach grace period, and it never blocks indefinitely.

`Client.submit` runs `commandLoop`'s `while (true)` until the session ends (`btrace-client/.../Client.java`, `commandLoop`), so it must not run on the caller's thread. The canary runs `submit` on a daemon thread and waits for `qe-canary-ok` with a timeout. It then sends `ExitCommand`, which the agent handles with `rt.handleExit` (`RemoteClient.java:299`): the canary probe is unloaded and the target keeps running, the same as `btrace` on exit. Finally it joins the session thread for at most 5 s.

- [ ] **Step 1: `ClientProbe`**

```java
package io.btrace.qe.exec;

import io.btrace.client.Client;
import io.btrace.core.comm.MessageCommand;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ClientProbe {
  private static final long DETACH_GRACE_MS = 5_000L;

  public boolean runCanary(TargetProcess target, QeEnvironment env, long timeoutMs) {
    if (target == null || target.pid() <= 0 || !target.isAlive()) {
      return false;
    }
    final Client client =
        new Client(
            target.assignedPort(), null, ".", false, false, false, false, null, null,
            env.btraceJarPath().toString());
    final CountDownLatch seen = new CountDownLatch(1);
    Thread session = null;
    try {
      client.attach(String.valueOf(target.pid()), null, null);
      final byte[] code =
          Files.readAllBytes(env.probeClassesDir().resolve("io/btrace/qe/probe/QeCanaryProbe.class"));
      session =
          new Thread(
              () -> {
                try {
                  client.submit(
                      "QeCanaryProbe.class",
                      code,
                      new String[0],
                      cmd -> {
                        if (cmd instanceof MessageCommand
                            && ((MessageCommand) cmd).getMessage().contains("qe-canary-ok")) {
                          seen.countDown();
                        }
                      });
                } catch (Exception ignored) {
                  // a failed session simply never counts down
                }
              },
              "qe-canary-session");
      session.setDaemon(true);
      session.start();
      return seen.await(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      return false;
    } finally {
      try {
        client.sendExit(0);
      } catch (Exception ignored) {
        // already disconnected
      }
      if (session != null) {
        try {
          session.join(DETACH_GRACE_MS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
}
```

- [ ] **Step 2: Extend the self-test**

In `targetStartsWithStartupProbeAndExitsOnCommand`, insert before `target.writeStdin("exit")`:

```java
      long start = System.currentTimeMillis();
      assertTrue(new ClientProbe().runCanary(target, env, 20_000), "canary failed on a healthy target");
      assertTrue(target.isAlive(), "canary detach must not kill the target");
      assertTrue(System.currentTimeMillis() - start < 30_000, "canary exceeded its bounded lifetime");
```

Add a test proving the canary is bounded even when nobody is listening:

```java
  @Test
  void canaryIsBoundedWhenTheAgentPortIsDead() throws Exception {
    QeEnvironment env = QeEnvironment.fromSystemProperties();
    Path runDir = Files.createTempDirectory("qe-canary-dead");
    TargetProcess target =
        new TargetLauncher()
            .launch(GivenDef.target("SteadyStateTarget", STARTUP + ",noServer=true"), env, runDir);
    try {
      assertNotNull(target.awaitLine("ready:", 20_000));
      target.assignPid(Long.parseLong(target.awaitLine("ready:", 1).substring("ready:".length()).trim()));
      long start = System.currentTimeMillis();
      new ClientProbe().runCanary(target, env, 5_000);
      assertTrue(System.currentTimeMillis() - start < 5_000 + 10_000, "canary blocked past its bound");
    } finally {
      target.destroyForcibly();
    }
  }
```

- [ ] **Step 3: Gate**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qeSelfTest --tests 'io.btrace.qe.exec.TargetLauncherSelfTest' :btrace-qe:spotlessCheck > /tmp/qe-t6.log 2>&1; grep -E 'PASSED|FAILED|BUILD' /tmp/qe-t6.log
```

Expected: `BUILD SUCCESSFUL`, 4 passed. If the canary fails on the healthy target, check whether `Client.attach` needs the targets classpath as its third argument (`Issue888RuntimeHardeningIntegrationTest` passes `getEventsClassPath()` there). Try `env.targetsClasspath()`, record which one works in the step's notes, and move on. Don't paper over it with a longer timeout.

- [ ] **Step 4: Stage (do not commit)**

```bash
git add btrace-qe/src/main/java/io/btrace/qe/exec/ClientProbe.java btrace-qe/src/test/java/io/btrace/qe/exec/TargetLauncherSelfTest.java
git status --short
```

---

### Task 7: Step vocabulary and `CaseExecutor` with per-step watchdog

**Files:**
- Create: `btrace-qe/src/main/java/io/btrace/qe/exec/{StepOutcome,ExecutionTrace,CaseExecutor}.java`
- Test: `btrace-qe/src/test/java/io/btrace/qe/exec/CaseExecutorSelfTest.java`

**Interfaces:**
- Consumes: Tasks 2, 5, 6.
- Produces: `new CaseExecutor(QeEnvironment).execute(CaseDef, Path runDir)` → `ExecutionTrace(steps, timedOut, exitedPrematurely, exitCode, stdout, stderr, canaryOk, threadDumpText, hasHsErrLog, runDir)`.

Rules the executor enforces:
- Every step runs on a single-thread executor under `future.get(stepTimeoutMs)`. On timeout, the executor takes a thread dump with `${javaHome}/bin/jcmd <pid> Thread.print`, cancels the step, marks `timedOut`, and stops.
- `awaitReady` with no `ready:` line and a dead process is **not** a timeout. It stops the case and leaves the process exit code to be recorded.
- `exitedPrematurely` is true when, in target mode, the process ended before the `shutdown` step ran. That is how "the agent killed the user's application" is detected.
- `exitCode` is recorded whenever the process has ended, whichever step observed it.

- [ ] **Step 1: `StepOutcome`, `ExecutionTrace`**

```java
package io.btrace.qe.exec;

public final class StepOutcome {
  private final String step;
  private final boolean success;
  private final long elapsedMs;
  private final String error;

  private StepOutcome(String step, boolean success, long elapsedMs, String error) {
    this.step = step;
    this.success = success;
    this.elapsedMs = elapsedMs;
    this.error = error;
  }

  public static StepOutcome ok(String step, long elapsedMs) {
    return new StepOutcome(step, true, elapsedMs, null);
  }

  public static StepOutcome failed(String step, long elapsedMs, String error) {
    return new StepOutcome(step, false, elapsedMs, error);
  }

  public String step() {
    return step;
  }

  public boolean success() {
    return success;
  }

  public long elapsedMs() {
    return elapsedMs;
  }

  public String error() {
    return error;
  }
}
```

```java
package io.btrace.qe.exec;

import java.nio.file.Path;
import java.util.List;

public final class ExecutionTrace {
  private final List<StepOutcome> steps;
  private final boolean timedOut;
  private final boolean exitedPrematurely;
  private final Integer exitCode;
  private final List<String> stdout;
  private final List<String> stderr;
  private final Boolean canaryOk;
  private final String threadDumpText;
  private final boolean hasHsErrLog;
  private final Path runDir;

  public ExecutionTrace(
      List<StepOutcome> steps,
      boolean timedOut,
      boolean exitedPrematurely,
      Integer exitCode,
      List<String> stdout,
      List<String> stderr,
      Boolean canaryOk,
      String threadDumpText,
      boolean hasHsErrLog,
      Path runDir) {
    this.steps = steps;
    this.timedOut = timedOut;
    this.exitedPrematurely = exitedPrematurely;
    this.exitCode = exitCode;
    this.stdout = stdout;
    this.stderr = stderr;
    this.canaryOk = canaryOk;
    this.threadDumpText = threadDumpText;
    this.hasHsErrLog = hasHsErrLog;
    this.runDir = runDir;
  }

  public List<StepOutcome> steps() {
    return steps;
  }

  public boolean timedOut() {
    return timedOut;
  }

  public boolean exitedPrematurely() {
    return exitedPrematurely;
  }

  public Integer exitCode() {
    return exitCode;
  }

  public List<String> stdout() {
    return stdout;
  }

  public List<String> stderr() {
    return stderr;
  }

  public Boolean canaryOk() {
    return canaryOk;
  }

  public String threadDumpText() {
    return threadDumpText;
  }

  public boolean hasHsErrLog() {
    return hasHsErrLog;
  }

  public Path runDir() {
    return runDir;
  }
}
```

- [ ] **Step 2: `CaseExecutor`**

```java
package io.btrace.qe.exec;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.GivenDef;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

public final class CaseExecutor {
  private final QeEnvironment env;

  public CaseExecutor(QeEnvironment env) {
    this.env = env;
  }

  /** Mutable per-case state shared with step bodies running on the watchdog thread. */
  private static final class State {
    volatile TargetProcess process;
    volatile Boolean canaryOk;
    volatile boolean shutdownSent;
    volatile boolean diedBeforeReady;
  }

  public ExecutionTrace execute(final CaseDef def, final Path runDir) {
    final long stepTimeoutMs = env.defaultTimeoutMs();
    final State state = new State();
    List<StepOutcome> outcomes = new ArrayList<>();
    boolean timedOut = false;
    String threadDump = null;

    ExecutorService watchdog =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "qe-step");
              t.setDaemon(true);
              return t;
            });
    try {
      for (final String step : def.steps()) {
        long start = System.currentTimeMillis();
        Future<?> future = watchdog.submit(() -> {
          runStep(step, def, runDir, stepTimeoutMs, state);
          return null;
        });
        try {
          future.get(stepTimeoutMs, TimeUnit.MILLISECONDS);
          outcomes.add(StepOutcome.ok(step, System.currentTimeMillis() - start));
        } catch (TimeoutException te) {
          future.cancel(true);
          timedOut = true;
          TargetProcess p = state.process;
          threadDump = p != null && p.pid() > 0 && p.isAlive() ? captureThreadDump(p.pid()) : null;
          outcomes.add(StepOutcome.failed(step, System.currentTimeMillis() - start, "timed out"));
          break;
        } catch (Exception e) {
          Throwable cause = e.getCause() != null ? e.getCause() : e;
          outcomes.add(StepOutcome.failed(step, System.currentTimeMillis() - start, String.valueOf(cause)));
          break;
        }
        if (state.diedBeforeReady) {
          break;
        }
      }
    } finally {
      watchdog.shutdownNow();
    }

    TargetProcess p = state.process;
    Integer exitCode = null;
    boolean exitedPrematurely = false;
    if (p != null) {
      if (!p.isAlive()) {
        exitCode = p.process().exitValue();
        exitedPrematurely = def.given().mode() == GivenDef.Mode.TARGET && !state.shutdownSent;
      } else {
        p.destroyForcibly();
      }
    }
    List<String> stdout = p != null ? p.stdoutSnapshot() : Collections.<String>emptyList();
    List<String> stderr = p != null ? p.stderrSnapshot() : Collections.<String>emptyList();
    return new ExecutionTrace(
        outcomes, timedOut, exitedPrematurely, exitCode, stdout, stderr,
        state.canaryOk, threadDump, hasHsErrLog(runDir), runDir);
  }

  private void runStep(String step, CaseDef def, Path runDir, long timeoutMs, State state) throws Exception {
    switch (step) {
      case "launch":
        state.process = new TargetLauncher().launch(def.given(), env, runDir);
        break;
      case "awaitReady":
        {
          String ready = state.process.awaitLine("ready:", timeoutMs);
          if (ready == null) {
            if (!state.process.isAlive()) {
              state.diedBeforeReady = true;
              return;
            }
            // still alive but silent: block so the watchdog records a timeout with a thread dump
            Thread.sleep(Long.MAX_VALUE);
          }
          state.process.assignPid(Long.parseLong(ready.substring("ready:".length()).trim()));
          break;
        }
      case "canary":
        // leave headroom inside the step budget for the canary's detach grace period
        state.canaryOk = new ClientProbe().runCanary(state.process, env, Math.max(1_000L, timeoutMs - 6_000L));
        break;
      case "shutdown":
        if (state.process.isAlive()) {
          state.process.writeStdin("exit");
        }
        state.shutdownSent = true;
        break;
      case "runClient":
        state.process = new ClientLauncher().launch(def.given().clientArgs(), env, runDir);
        break;
      case "awaitExit":
        if (!state.process.process().waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
          Thread.sleep(Long.MAX_VALUE);
        }
        break;
      default:
        throw new IllegalStateException("unknown step: " + step);
    }
  }

  private String captureThreadDump(long pid) {
    try {
      Process p =
          new ProcessBuilder(env.jcmdPath(), String.valueOf(pid), "Thread.print")
              .redirectErrorStream(true)
              .start();
      String out;
      try (InputStream in = p.getInputStream()) {
        out = new String(readAll(in), StandardCharsets.UTF_8);
      }
      p.waitFor(10, TimeUnit.SECONDS);
      return out;
    } catch (Exception e) {
      return "failed to capture thread dump: " + e;
    }
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    byte[] chunk = new byte[4096];
    int n;
    while ((n = in.read(chunk)) != -1) {
      buffer.write(chunk, 0, n);
    }
    return buffer.toByteArray();
  }

  private static boolean hasHsErrLog(Path runDir) {
    if (!Files.isDirectory(runDir)) {
      return false;
    }
    try (Stream<Path> files = Files.list(runDir)) {
      return files.anyMatch(f -> f.getFileName().toString().startsWith("hs_err_pid"));
    } catch (IOException e) {
      return false;
    }
  }
}
```

Note on `Thread.sleep(Long.MAX_VALUE)`: when a step's own wait expires with the process still alive, the step deliberately blocks. That way the single watchdog path records the timeout and takes the thread dump. `future.cancel(true)` interrupts the sleep, and the executor thread is a daemon.

- [ ] **Step 3: Self-test**

```java
package io.btrace.qe.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import io.btrace.qe.model.SpecRef;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("qe-self-test")
class CaseExecutorSelfTest {
  private static final String STARTUP =
      "script=${probeClassesDir}/io/btrace/qe/probe/QeStartupProbe.class,stdout=true";
  private static final List<String> TARGET_STEPS =
      List.of("launch", "awaitReady", "canary", "shutdown", "awaitExit");

  private static CaseDef targetCase(String id, String agentArgs) {
    return new CaseDef(
        id, "fixture", Intent.RESILIENCE, id, null,
        GivenDef.target("SteadyStateTarget", agentArgs),
        TARGET_STEPS,
        new ExpectationDef(null, Collections.<String>emptyList()));
  }

  @Test
  void happyPathTargetCase() throws Exception {
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties())
            .execute(targetCase("t.happy", STARTUP), Files.createTempDirectory("qe-exec-happy"));
    assertFalse(trace.timedOut(), "steps=" + trace.steps().size());
    assertFalse(trace.exitedPrematurely());
    assertEquals(Integer.valueOf(0), trace.exitCode());
    assertEquals(Boolean.TRUE, trace.canaryOk());
    assertTrue(trace.stdout().contains("qe-startup-ok"));
  }

  @Test
  void targetThatDiesAtStartupIsAPrematureExitNotAHang() throws Exception {
    CaseDef def =
        new CaseDef(
            "t.dies", "fixture", Intent.RESILIENCE, "dies", null,
            GivenDef.target("NoSuchTarget", STARTUP),
            TARGET_STEPS,
            new ExpectationDef(null, Collections.<String>emptyList()));
    long start = System.currentTimeMillis();
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties()).execute(def, Files.createTempDirectory("qe-exec-dies"));
    assertFalse(trace.timedOut());
    assertTrue(trace.exitedPrematurely());
    assertTrue(trace.exitCode() != null && trace.exitCode() != 0);
    assertTrue(System.currentTimeMillis() - start < 20_000, "should not wait out the step timeout");
  }

  @Test
  void clientModeCapturesExitCode() throws Exception {
    CaseDef def =
        new CaseDef(
            "c.missing-operand", "fixture", Intent.CONFORMANCE, "missing operand",
            new SpecRef("docs/QuickReference.md", "usage on bad args"),
            GivenDef.client(List.of("-p")),
            List.of("runClient", "awaitExit"),
            new ExpectationDef(1, Collections.<String>emptyList()));
    ExecutionTrace trace =
        new CaseExecutor(QeEnvironment.fromSystemProperties()).execute(def, Files.createTempDirectory("qe-exec-client"));
    assertFalse(trace.timedOut());
    assertFalse(trace.exitedPrematurely());
    assertEquals(Integer.valueOf(1), trace.exitCode(), "stderr=" + trace.stderr());
  }
}
```

- [ ] **Step 4: Gate**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qeSelfTest :btrace-qe:spotlessCheck > /tmp/qe-t7.log 2>&1; grep -E 'PASSED|FAILED|BUILD' /tmp/qe-t7.log
```

Expected: `BUILD SUCCESSFUL`, 7 self-tests passed (4 launcher + 3 executor).

- [ ] **Step 5: Stage (do not commit)**

```bash
git add btrace-qe/src/main/java/io/btrace/qe/exec btrace-qe/src/test/java/io/btrace/qe/exec
git status --short
```

---

### Task 8: The oracle

**Files:**
- Create: `btrace-qe/src/main/java/io/btrace/qe/oracle/{CheckOutcome,CheckResult,InvariantCheck,NoCrashCheck,NoHangCheck,NoCorruptionCheck,CanaryCheck,GuidanceCheck,OracleRunner}.java`
- Test: `btrace-qe/src/test/java/io/btrace/qe/oracle/OracleRunnerTest.java`

**Interfaces:**
- Consumes: Task 2 model, `ExecutionTrace` (Task 7).
- Produces: `new OracleRunner().evaluate(CaseDef, ExecutionTrace)` → `CaseVerdict`.

Severity comes from which check failed, matching the spec's table: crash/hang/corruption → S1, canary → S2, conformance mismatch → S3, guidance → S4.

Guidance follows spec invariant 5 in full. When guidance applies, it is POOR if the expected tokens are absent, **or** if a raw Java stack trace appears in user-visible output (the default, non-`-v` path). The agent's startup failure path prints one (`agent/Main.java:411`).

- [ ] **Step 1: Check plumbing**

```java
package io.btrace.qe.oracle;

public enum CheckOutcome {
  PASS,
  FAIL,
  NOT_APPLICABLE
}
```

```java
package io.btrace.qe.oracle;

public final class CheckResult {
  private final CheckOutcome outcome;
  private final String detail;

  private CheckResult(CheckOutcome outcome, String detail) {
    this.outcome = outcome;
    this.detail = detail;
  }

  public static CheckResult of(CheckOutcome outcome, String detail) {
    return new CheckResult(outcome, detail);
  }

  public CheckOutcome outcome() {
    return outcome;
  }

  public String detail() {
    return detail;
  }
}
```

```java
package io.btrace.qe.oracle;

import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.model.CaseDef;

public interface InvariantCheck {
  CheckResult check(CaseDef def, ExecutionTrace trace);
}
```

- [ ] **Step 2: The five checks**

```java
package io.btrace.qe.oracle;

import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.model.CaseDef;

public final class NoCrashCheck implements InvariantCheck {
  @Override
  public CheckResult check(CaseDef def, ExecutionTrace trace) {
    if (trace.hasHsErrLog()) {
      return CheckResult.of(CheckOutcome.FAIL, "the JVM produced a fatal error log (hs_err_pid*)");
    }
    if (trace.exitedPrematurely()) {
      return CheckResult.of(
          CheckOutcome.FAIL,
          "the target application terminated before it was asked to (exit code "
              + trace.exitCode()
              + "); BTrace took the user's application down");
    }
    for (String line : trace.stderr()) {
      if (line.startsWith("Exception in thread \"main\"")) {
        return CheckResult.of(CheckOutcome.FAIL, "the process died with an uncaught exception: " + line);
      }
    }
    return CheckResult.of(CheckOutcome.PASS, "no crash indicators observed");
  }
}
```

```java
package io.btrace.qe.oracle;

import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.model.CaseDef;
import java.util.regex.Pattern;

public final class NoHangCheck implements InvariantCheck {
  private static final Pattern DEADLOCK = Pattern.compile("Found (one|\\d+) Java-level deadlock");

  @Override
  public CheckResult check(CaseDef def, ExecutionTrace trace) {
    if (!trace.timedOut()) {
      return CheckResult.of(CheckOutcome.PASS, "every step completed within its timeout");
    }
    String dump = trace.threadDumpText();
    if (dump != null && DEADLOCK.matcher(dump).find()) {
      return CheckResult.of(CheckOutcome.FAIL, "a step timed out and the thread dump shows a deadlock");
    }
    return CheckResult.of(
        CheckOutcome.FAIL,
        "a step timed out with the process still alive"
            + (dump != null ? "; no deadlock in the thread dump (see thread-dump.txt)" : ""));
  }
}
```

The harness's thread dump comes from `jcmd Thread.print`, which reports deadlocks as `Found one Java-level deadlock:`. The pattern matches that form and its plural variant.

```java
package io.btrace.qe.oracle;

import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.model.CaseDef;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NoCorruptionCheck implements InvariantCheck {
  private static final Pattern CHECKSUM = Pattern.compile("^checksum:(\\d+)$");

  @Override
  public CheckResult check(CaseDef def, ExecutionTrace trace) {
    long previous = -1;
    for (String line : trace.stdout()) {
      Matcher m = CHECKSUM.matcher(line);
      if (m.matches()) {
        long value = Long.parseLong(m.group(1));
        if (value <= previous) {
          return CheckResult.of(
              CheckOutcome.FAIL, "target checksum did not advance: " + previous + " -> " + value);
        }
        previous = value;
      }
    }
    return CheckResult.of(
        CheckOutcome.PASS,
        previous < 0 ? "no checksum samples observed" : "checksum advanced monotonically (last=" + previous + ")");
  }
}
```

```java
package io.btrace.qe.oracle;

import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.model.CaseDef;

public final class CanaryCheck implements InvariantCheck {
  @Override
  public CheckResult check(CaseDef def, ExecutionTrace trace) {
    if (!def.steps().contains("canary")) {
      return CheckResult.of(CheckOutcome.NOT_APPLICABLE, "case has no canary step");
    }
    if (trace.exitedPrematurely()) {
      // the crash check already owns this; a dead JVM is not additionally an S2
      return CheckResult.of(CheckOutcome.NOT_APPLICABLE, "target was not alive to canary");
    }
    if (!Boolean.TRUE.equals(trace.canaryOk())) {
      return CheckResult.of(
          CheckOutcome.FAIL, "canary probe produced no output after the abuse; BTrace can no longer operate on this JVM");
    }
    return CheckResult.of(CheckOutcome.PASS, "canary probe produced output after the abuse");
  }
}
```

```java
package io.btrace.qe.oracle;

import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.model.CaseDef;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class GuidanceCheck implements InvariantCheck {
  private static final Pattern STACK_FRAME = Pattern.compile("^\\s+at [\\w$./<>]+\\(.*\\)$");

  @Override
  public CheckResult check(CaseDef def, ExecutionTrace trace) {
    List<String> required = def.expect().messageMustMention();
    String firstFrame = null;
    for (String line : trace.stderr()) {
      if (STACK_FRAME.matcher(line).matches()) {
        firstFrame = line.trim();
        break;
      }
    }
    if (required.isEmpty() && firstFrame == null) {
      return CheckResult.of(CheckOutcome.NOT_APPLICABLE, "case specifies no guidance and none was mis-shown");
    }
    if (firstFrame != null) {
      return CheckResult.of(
          CheckOutcome.FAIL, "a raw stack trace is shown to the user on the default path (first frame: " + firstFrame + ")");
    }
    String combined = String.join("\n", trace.stdout()) + "\n" + String.join("\n", trace.stderr());
    List<String> missing = new ArrayList<>();
    for (String token : required) {
      if (!combined.contains(token)) {
        missing.add(token);
      }
    }
    if (missing.isEmpty()) {
      return CheckResult.of(CheckOutcome.PASS, "diagnostic output mentions " + required);
    }
    return CheckResult.of(
        CheckOutcome.FAIL, "diagnostic output never mentions " + missing + "; the problem is not surfaced to the user");
  }
}
```

- [ ] **Step 3: `OracleRunner`**

```java
package io.btrace.qe.oracle;

import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.model.Finding;
import io.btrace.qe.model.Intent;
import io.btrace.qe.model.Severity;
import io.btrace.qe.model.VerdictValue;
import java.util.ArrayList;
import java.util.List;

public final class OracleRunner {

  public CaseVerdict evaluate(CaseDef def, ExecutionTrace trace) {
    List<Finding> findings = new ArrayList<>();
    VerdictValue resilience = VerdictValue.PASS;

    resilience = apply(new NoCrashCheck(), def, trace, Severity.S1, "process crashed", findings, resilience);
    resilience = apply(new NoHangCheck(), def, trace, Severity.S1, "process hung or deadlocked", findings, resilience);
    resilience = apply(new NoCorruptionCheck(), def, trace, Severity.S1, "target state corrupted", findings, resilience);
    resilience =
        apply(new CanaryCheck(), def, trace, Severity.S2, "BTrace stopped operating after the abuse", findings, resilience);

    VerdictValue conformance = VerdictValue.NA;
    if (def.intent() == Intent.CONFORMANCE) {
      Integer expected = def.expect().exitCode();
      boolean ok = expected == null || expected.equals(trace.exitCode());
      conformance = ok ? VerdictValue.PASS : VerdictValue.FAIL;
      if (!ok) {
        findings.add(
            Finding.of(
                def.id(),
                Severity.S3,
                "behavior does not match " + def.spec().doc() + ": " + def.spec().claim(),
                "expected exit code " + expected + ", observed " + trace.exitCode()));
      }
    }

    CheckResult guidance = new GuidanceCheck().check(def, trace);
    VerdictValue guidanceVerdict;
    if (guidance.outcome() == CheckOutcome.NOT_APPLICABLE) {
      guidanceVerdict = VerdictValue.NA;
    } else if (guidance.outcome() == CheckOutcome.PASS) {
      guidanceVerdict = VerdictValue.GOOD;
    } else {
      guidanceVerdict = VerdictValue.POOR;
      findings.add(Finding.of(def.id(), Severity.S4, "the user is not guided", guidance.detail()));
    }

    return new CaseVerdict(def, resilience, conformance, guidanceVerdict, findings);
  }

  private static VerdictValue apply(
      InvariantCheck check,
      CaseDef def,
      ExecutionTrace trace,
      Severity severity,
      String summary,
      List<Finding> findings,
      VerdictValue current) {
    CheckResult result = check.check(def, trace);
    if (result.outcome() == CheckOutcome.FAIL) {
      findings.add(Finding.of(def.id(), severity, summary, result.detail()));
      return VerdictValue.FAIL;
    }
    return current;
  }
}
```

- [ ] **Step 4: Unit tests (fixture traces, no JVMs)**

```java
package io.btrace.qe.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import io.btrace.qe.model.Severity;
import io.btrace.qe.model.SpecRef;
import io.btrace.qe.model.VerdictValue;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class OracleRunnerTest {
  private static final List<String> TARGET_STEPS =
      List.of("launch", "awaitReady", "canary", "shutdown", "awaitExit");

  private static ExecutionTrace trace(
      boolean timedOut, boolean premature, Integer exit, List<String> out, List<String> err,
      Boolean canary, String dump, boolean hsErr) {
    return new ExecutionTrace(
        Collections.emptyList(), timedOut, premature, exit, out, err, canary, dump, hsErr, null);
  }

  private static CaseDef target(String id, List<String> mentions) {
    return new CaseDef(
        id, "agent-args", Intent.RESILIENCE, id, null,
        GivenDef.target("SteadyStateTarget", "x"), TARGET_STEPS, new ExpectationDef(null, mentions));
  }

  private static CaseDef client(String id, int exit, List<String> mentions) {
    return new CaseDef(
        id, "cli", Intent.CONFORMANCE, id, new SpecRef("docs/QuickReference.md", "usage on bad args"),
        GivenDef.client(List.of("-p")), List.of("runClient", "awaitExit"), new ExpectationDef(exit, mentions));
  }

  @Test
  void silentlyIgnoredValueIsResilientButUnguiding() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                target("a", List.of("fileRollMilliseconds")),
                trace(false, false, 0, List.of("ready:1", "checksum:1", "checksum:2"), List.of(), true, null, false));
    assertEquals(VerdictValue.PASS, v.resilience());
    assertEquals(VerdictValue.NA, v.conformance());
    assertEquals(VerdictValue.POOR, v.guidance());
    assertEquals(1, v.findings().size());
    assertEquals(Severity.S4, v.findings().get(0).severity());
  }

  @Test
  void prematureExitIsS1AndNotAlsoS2() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                target("b", Collections.<String>emptyList()),
                trace(false, true, 1, List.of(), List.of("BTrace agent initialization failed: x"), null, null, false));
    assertEquals(VerdictValue.FAIL, v.resilience());
    assertEquals(1, v.findings().size());
    assertEquals(Severity.S1, v.findings().get(0).severity());
  }

  @Test
  void canaryFailureOnLiveTargetIsS2() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                target("c", Collections.<String>emptyList()),
                trace(false, false, 0, List.of("ready:1"), List.of(), false, null, false));
    assertEquals(VerdictValue.FAIL, v.resilience());
    assertEquals(Severity.S2, v.findings().get(0).severity());
  }

  @Test
  void deadlockOnTimeoutIsS1() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                target("d", Collections.<String>emptyList()),
                trace(true, false, null, List.of("ready:1"), List.of(), null,
                    "Found one Java-level deadlock:\n=============================", false));
    assertEquals(VerdictValue.FAIL, v.resilience());
    assertTrue(v.findings().stream().anyMatch(f -> f.severity() == Severity.S1));
  }

  @Test
  void rawStackTraceIsPoorGuidanceEvenWithoutExpectedTokens() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                target("e", Collections.<String>emptyList()),
                trace(false, false, 0, List.of("ready:1"),
                    List.of("java.lang.IllegalStateException: boom", "\tat io.btrace.agent.Main.main(Main.java:300)"),
                    true, null, false));
    assertEquals(VerdictValue.POOR, v.guidance());
    assertTrue(v.findings().stream().anyMatch(f -> f.severity() == Severity.S4));
  }

  @Test
  void conformanceMismatchIsS3() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(client("f", 1, Collections.<String>emptyList()), trace(false, false, 0, List.of(), List.of(), null, null, false));
    assertEquals(VerdictValue.FAIL, v.conformance());
    assertTrue(v.findings().stream().anyMatch(f -> f.severity() == Severity.S3));
  }

  @Test
  void cleanCaseHasNoFindings() {
    CaseVerdict v =
        new OracleRunner()
            .evaluate(
                client("g", 1, List.of("Usage")),
                trace(false, false, 1, List.of(), List.of("Usage: btrace <options> <pid> <btrace source>"), null, null, false));
    assertEquals(VerdictValue.PASS, v.resilience());
    assertEquals(VerdictValue.PASS, v.conformance());
    assertEquals(VerdictValue.GOOD, v.guidance());
    assertTrue(v.findings().isEmpty());
  }
}
```

- [ ] **Step 5: Gate**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:test --tests 'io.btrace.qe.oracle.*' :btrace-qe:spotlessCheck > /tmp/qe-t8.log 2>&1; grep -E 'PASSED|FAILED|BUILD' /tmp/qe-t8.log
```

Expected: `BUILD SUCCESSFUL`, 7 passed.

- [ ] **Step 6: Stage (do not commit)**

```bash
git add btrace-qe/src/main/java/io/btrace/qe/oracle btrace-qe/src/test/java/io/btrace/qe/oracle
git status --short
```

---

### Task 9: Reporting

**Files:**
- Create: `btrace-qe/src/main/java/io/btrace/qe/report/{RunMetadata,JsonWriter,JsonReporter,ReproCommand,MarkdownReporter,EvidenceStore}.java`
- Test: `btrace-qe/src/test/java/io/btrace/qe/report/{JsonWriterTest,MarkdownReporterTest}.java`

**Interfaces:**
- Consumes: Task 2 model, `ExecutionTrace` (Task 7).
- Produces: `RunMetadata.capture(long seed, String commandLine)`; `new JsonReporter().write(meta, verdicts, Path)`; `new MarkdownReporter().write(meta, verdicts, Path)`; `new EvidenceStore().persist(ExecutionTrace, Path runDir)`.

The report has no timestamp, so two runs on the same commit, JDK and seed produce byte-identical `report.json`. Elapsed times go only into evidence files.

- [ ] **Step 1: `JsonWriter`, test first**

```java
package io.btrace.qe.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonWriterTest {

  @Test
  void writesStableOrderAndEscapes() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("id", "cli.example");
    root.put("count", 2);
    root.put("ok", true);
    root.put("nested", List.of("a\"b", "line1\nline2"));
    String expected =
        "{\n"
            + "  \"id\": \"cli.example\",\n"
            + "  \"count\": 2,\n"
            + "  \"ok\": true,\n"
            + "  \"nested\": [\n"
            + "    \"a\\\"b\",\n"
            + "    \"line1\\nline2\"\n"
            + "  ]\n"
            + "}";
    assertEquals(expected, new JsonWriter().write(root));
  }

  @Test
  void writesEmptyContainersCompactly() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("list", List.of());
    root.put("map", new LinkedHashMap<>());
    assertEquals("{\n  \"list\": [],\n  \"map\": {}\n}", new JsonWriter().write(root));
  }
}
```

Run it and expect a compile failure. Then:

```java
package io.btrace.qe.report;

import java.util.List;
import java.util.Map;

public final class JsonWriter {

  public String write(Object value) {
    StringBuilder sb = new StringBuilder();
    writeValue(value, sb, 0);
    return sb.toString();
  }

  private void writeValue(Object value, StringBuilder sb, int indent) {
    if (value == null) {
      sb.append("null");
    } else if (value instanceof String) {
      writeString((String) value, sb);
    } else if (value instanceof Number || value instanceof Boolean) {
      sb.append(value);
    } else if (value instanceof Map) {
      writeMap((Map<?, ?>) value, sb, indent);
    } else if (value instanceof List) {
      writeList((List<?>) value, sb, indent);
    } else {
      writeString(String.valueOf(value), sb);
    }
  }

  private void writeMap(Map<?, ?> map, StringBuilder sb, int indent) {
    if (map.isEmpty()) {
      sb.append("{}");
      return;
    }
    sb.append("{\n");
    int i = 0;
    for (Map.Entry<?, ?> e : map.entrySet()) {
      indent(sb, indent + 1);
      writeString(String.valueOf(e.getKey()), sb);
      sb.append(": ");
      writeValue(e.getValue(), sb, indent + 1);
      if (++i < map.size()) {
        sb.append(',');
      }
      sb.append('\n');
    }
    indent(sb, indent);
    sb.append('}');
  }

  private void writeList(List<?> list, StringBuilder sb, int indent) {
    if (list.isEmpty()) {
      sb.append("[]");
      return;
    }
    sb.append("[\n");
    for (int i = 0; i < list.size(); i++) {
      indent(sb, indent + 1);
      writeValue(list.get(i), sb, indent + 1);
      if (i < list.size() - 1) {
        sb.append(',');
      }
      sb.append('\n');
    }
    indent(sb, indent);
    sb.append(']');
  }

  private static void indent(StringBuilder sb, int level) {
    for (int i = 0; i < level; i++) {
      sb.append("  ");
    }
  }

  private static void writeString(String s, StringBuilder sb) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
  }
}
```

- [ ] **Step 2: `RunMetadata`, `ReproCommand`, `JsonReporter`, `EvidenceStore`**

```java
package io.btrace.qe.report;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class RunMetadata {
  private final long seed;
  private final String gitSha;
  private final String btraceVersion;
  private final String jdk;
  private final String os;
  private final int cpuCount;
  private final String commandLine;

  public RunMetadata(
      long seed, String gitSha, String btraceVersion, String jdk, String os, int cpuCount, String commandLine) {
    this.seed = seed;
    this.gitSha = gitSha;
    this.btraceVersion = btraceVersion;
    this.jdk = jdk;
    this.os = os;
    this.cpuCount = cpuCount;
    this.commandLine = commandLine;
  }

  /** Records the JDK the targets run on ({@code qe.javaHome}), not the harness JVM. */
  public static RunMetadata capture(long seed, String commandLine) {
    return new RunMetadata(
        seed,
        readGitSha(),
        System.getProperty("qe.btraceVersion", "unknown"),
        System.getProperty("qe.javaHome", "unknown"),
        System.getProperty("os.name") + " " + System.getProperty("os.version"),
        Runtime.getRuntime().availableProcessors(),
        commandLine);
  }

  private static String readGitSha() {
    try {
      Process p = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
      try (BufferedReader r =
          new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
        String line = r.readLine();
        p.waitFor(5, TimeUnit.SECONDS);
        return line != null && p.exitValue() == 0 ? line.trim() : "unknown";
      }
    } catch (Exception e) {
      return "unknown";
    }
  }

  public long seed() {
    return seed;
  }

  public String gitSha() {
    return gitSha;
  }

  public String btraceVersion() {
    return btraceVersion;
  }

  public String jdk() {
    return jdk;
  }

  public String os() {
    return os;
  }

  public int cpuCount() {
    return cpuCount;
  }

  public String commandLine() {
    return commandLine;
  }
}
```

```java
package io.btrace.qe.report;

import io.btrace.qe.model.CaseDef;

public final class ReproCommand {
  private ReproCommand() {}

  public static String forCase(CaseDef def) {
    return "./gradlew :btrace-qe:qe -Pqe.case=" + def.id();
  }
}
```

```java
package io.btrace.qe.report;

import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.model.Finding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonReporter {

  public void write(RunMetadata meta, List<CaseVerdict> verdicts, Path outFile) throws IOException {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("seed", meta.seed());
    root.put("gitSha", meta.gitSha());
    root.put("btraceVersion", meta.btraceVersion());
    root.put("jdk", meta.jdk());
    root.put("os", meta.os());
    root.put("cpuCount", meta.cpuCount());
    root.put("commandLine", meta.commandLine());
    List<Object> cases = new ArrayList<>();
    for (CaseVerdict v : verdicts) {
      Map<String, Object> c = new LinkedHashMap<>();
      c.put("id", v.def().id());
      c.put("surface", v.def().surface());
      c.put("intent", v.def().intent().name());
      c.put("resilience", v.resilience().name());
      c.put("conformance", v.conformance().name());
      c.put("guidance", v.guidance().name());
      List<Object> findings = new ArrayList<>();
      for (Finding f : v.findings()) {
        Map<String, Object> fm = new LinkedHashMap<>();
        fm.put("severity", f.severity().name());
        fm.put("summary", f.summary());
        fm.put("detail", f.detail());
        fm.put("reproCommand", ReproCommand.forCase(v.def()));
        findings.add(fm);
      }
      c.put("findings", findings);
      cases.add(c);
    }
    root.put("cases", cases);
    Files.createDirectories(outFile.getParent());
    Files.write(outFile, (new JsonWriter().write(root) + "\n").getBytes(StandardCharsets.UTF_8));
  }
}
```

```java
package io.btrace.qe.report;

import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.exec.StepOutcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EvidenceStore {

  public void persist(ExecutionTrace trace, Path runDir) throws IOException {
    Files.createDirectories(runDir);
    write(runDir.resolve("stdout.log"), String.join("\n", trace.stdout()));
    write(runDir.resolve("stderr.log"), String.join("\n", trace.stderr()));
    StringBuilder steps = new StringBuilder();
    for (StepOutcome s : trace.steps()) {
      steps.append(s.step()).append('\t').append(s.success() ? "ok" : "FAILED").append('\t')
          .append(s.elapsedMs()).append("ms");
      if (s.error() != null) {
        steps.append('\t').append(s.error());
      }
      steps.append('\n');
    }
    steps.append("exitCode=").append(trace.exitCode())
        .append(" timedOut=").append(trace.timedOut())
        .append(" exitedPrematurely=").append(trace.exitedPrematurely())
        .append(" canaryOk=").append(trace.canaryOk()).append('\n');
    write(runDir.resolve("steps.log"), steps.toString());
    if (trace.threadDumpText() != null) {
      write(runDir.resolve("thread-dump.txt"), trace.threadDumpText());
    }
  }

  private static void write(Path file, String content) throws IOException {
    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
  }
}
```

- [ ] **Step 3: `MarkdownReporter` with test**

```java
package io.btrace.qe.report;

import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.model.Finding;
import io.btrace.qe.model.Severity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MarkdownReporter {

  public void write(RunMetadata meta, List<CaseVerdict> verdicts, Path outFile) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("# BTrace QE Report\n\n");
    sb.append("- seed: ").append(meta.seed()).append(" (recorded; no generated cases in this phase)\n");
    sb.append("- git SHA: ").append(meta.gitSha()).append('\n');
    sb.append("- btrace version: ").append(meta.btraceVersion()).append('\n');
    sb.append("- target JDK: ").append(meta.jdk()).append('\n');
    sb.append("- OS: ").append(meta.os()).append(", CPUs: ").append(meta.cpuCount()).append('\n');
    sb.append("- command: `").append(meta.commandLine()).append("`\n\n");

    Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
    for (CaseVerdict v : verdicts) {
      for (Finding f : v.findings()) {
        counts.merge(f.severity(), 1, Integer::sum);
      }
    }
    sb.append("## Summary\n\n| Cases run | S1 | S2 | S3 | S4 |\n|---|---|---|---|---|\n");
    sb.append("| ").append(verdicts.size());
    for (Severity s : Severity.values()) {
      sb.append(" | ").append(counts.getOrDefault(s, 0));
    }
    sb.append(" |\n\n");

    sb.append("## Findings\n\n");
    boolean any = false;
    for (Severity s : Severity.values()) {
      for (CaseVerdict v : verdicts) {
        for (Finding f : v.findings()) {
          if (f.severity() != s) {
            continue;
          }
          any = true;
          sb.append("### ").append(s).append(" — ").append(v.def().id()).append('\n');
          sb.append("- tried: ").append(v.def().title()).append('\n');
          sb.append("- finding: ").append(f.summary()).append('\n');
          sb.append("- detail: ").append(f.detail()).append('\n');
          sb.append("- evidence: `btrace-qe/build/qe-report/runs/").append(v.def().id()).append("/`\n");
          sb.append("- repro: `").append(ReproCommand.forCase(v.def())).append("`\n\n");
        }
      }
    }
    if (!any) {
      sb.append("No findings.\n\n");
    }

    sb.append("## All cases\n\n| id | surface | resilience | conformance | guidance |\n|---|---|---|---|---|\n");
    for (CaseVerdict v : verdicts) {
      sb.append("| ").append(v.def().id()).append(" | ").append(v.def().surface()).append(" | ")
          .append(v.resilience()).append(" | ").append(v.conformance()).append(" | ")
          .append(v.guidance()).append(" |\n");
    }
    Files.createDirectories(outFile.getParent());
    Files.write(outFile, sb.toString().getBytes(StandardCharsets.UTF_8));
  }
}
```

```java
package io.btrace.qe.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.model.ExpectationDef;
import io.btrace.qe.model.Finding;
import io.btrace.qe.model.GivenDef;
import io.btrace.qe.model.Intent;
import io.btrace.qe.model.Severity;
import io.btrace.qe.model.VerdictValue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownReporterTest {

  @Test
  void reportsFindingWithEvidenceAndRepro() throws Exception {
    CaseDef def =
        new CaseDef(
            "agent-args.fixture", "agent-args", Intent.RESILIENCE, "fixture case", null,
            GivenDef.target("SteadyStateTarget", "x"),
            List.of("launch", "awaitReady", "shutdown", "awaitExit"),
            new ExpectationDef(null, Collections.<String>emptyList()));
    CaseVerdict verdict =
        new CaseVerdict(
            def, VerdictValue.PASS, VerdictValue.NA, VerdictValue.POOR,
            List.of(Finding.of(def.id(), Severity.S4, "the user is not guided", "detail text")));
    RunMetadata meta = new RunMetadata(1L, "abc123", "3.0.0-SNAPSHOT", "jdk", "os", 4, "./gradlew :btrace-qe:qe");
    Path out = Files.createTempDirectory("qe-md").resolve("report.md");

    new MarkdownReporter().write(meta, List.of(verdict), out);

    String content = new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
    assertTrue(content.contains("### S4 — agent-args.fixture"));
    assertTrue(content.contains("-Pqe.case=agent-args.fixture"));
    assertTrue(content.contains("runs/agent-args.fixture/"));
    assertTrue(content.contains("| 1 | 0 | 0 | 0 | 1 |"));
  }
}
```

- [ ] **Step 4: Gate**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:test --tests 'io.btrace.qe.report.*' :btrace-qe:spotlessCheck > /tmp/qe-t9.log 2>&1; grep -E 'PASSED|FAILED|BUILD' /tmp/qe-t9.log
```

Expected: `BUILD SUCCESSFUL`, 3 passed.

- [ ] **Step 5: Stage (do not commit)**

```bash
git add btrace-qe/src/main/java/io/btrace/qe/report btrace-qe/src/test/java/io/btrace/qe/report
git status --short
```

---

### Task 10: `QeMain` orchestration and property forwarding

**Files:**
- Modify: `btrace-qe/src/main/java/io/btrace/qe/QeMain.java`
- Modify: `btrace-qe/build.gradle`

**Interfaces:**
- Consumes: everything above.
- Produces: the finished `qe` command.

A harness error in one case (for example, an unexpected exception in the executor) must not stop the run. It becomes a trace with a failed step, and the run continues.

- [ ] **Step 1: Replace `QeMain`**

```java
package io.btrace.qe;

import io.btrace.qe.corpus.CaseRegistry;
import io.btrace.qe.corpus.YamlCaseLoader;
import io.btrace.qe.exec.CaseExecutor;
import io.btrace.qe.exec.ExecutionTrace;
import io.btrace.qe.exec.QeEnvironment;
import io.btrace.qe.exec.StepOutcome;
import io.btrace.qe.model.CaseDef;
import io.btrace.qe.model.CaseVerdict;
import io.btrace.qe.oracle.OracleRunner;
import io.btrace.qe.report.EvidenceStore;
import io.btrace.qe.report.JsonReporter;
import io.btrace.qe.report.MarkdownReporter;
import io.btrace.qe.report.RunMetadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class QeMain {
  private QeMain() {}

  public static void main(String[] args) {
    try {
      run();
    } catch (Throwable t) {
      t.printStackTrace();
    } finally {
      System.exit(0);
    }
  }

  private static void run() throws Exception {
    System.setProperty("btrace.comm.protocol", "2");
    System.setProperty("btrace.comm.autoNegotiate", "false");
    System.setProperty("btrace.comm.forceVersion", "true");

    QeEnvironment env = QeEnvironment.fromSystemProperties();
    long seed = Long.parseLong(System.getProperty("qe.seed", "20260811"));
    String surface = System.getProperty("qe.surface");
    String caseId = System.getProperty("qe.case");

    List<CaseDef> all = new YamlCaseLoader().loadAll(Paths.get(System.getProperty("qe.corpusDir")));
    List<CaseDef> selected = CaseRegistry.of(all).filter(surface, caseId);

    Path reportDir = Paths.get(System.getProperty("qe.reportDir"));
    Path runsDir = reportDir.resolve("runs");
    Files.createDirectories(runsDir);

    List<CaseVerdict> verdicts = new ArrayList<>();
    for (CaseDef def : selected) {
      Path runDir = runsDir.resolve(def.id());
      Files.createDirectories(runDir);
      System.out.println("[qe] " + def.id());
      ExecutionTrace trace;
      try {
        trace = new CaseExecutor(env).execute(def, runDir);
      } catch (RuntimeException e) {
        trace =
            new ExecutionTrace(
                Collections.singletonList(StepOutcome.failed("harness", 0, String.valueOf(e))),
                false, false, null, Collections.<String>emptyList(),
                Collections.singletonList("QE harness error: " + e), null, null, false, runDir);
      }
      new EvidenceStore().persist(trace, runDir);
      verdicts.add(new OracleRunner().evaluate(def, trace));
    }

    RunMetadata meta = RunMetadata.capture(seed, commandLine(surface, caseId, seed));
    new JsonReporter().write(meta, verdicts, reportDir.resolve("report.json"));
    new MarkdownReporter().write(meta, verdicts, reportDir.resolve("report.md"));

    long findings = 0;
    for (CaseVerdict v : verdicts) {
      findings += v.findings().size();
    }
    System.out.println(
        "[qe] ran " + selected.size() + " case(s), " + findings + " finding(s). Report: "
            + reportDir.resolve("report.md"));
  }

  private static String commandLine(String surface, String caseId, long seed) {
    StringBuilder sb = new StringBuilder("./gradlew :btrace-qe:qe -Pqe.seed=").append(seed);
    if (surface != null) {
      sb.append(" -Pqe.surface=").append(surface);
    }
    if (caseId != null) {
      sb.append(" -Pqe.case=").append(caseId);
    }
    return sb.toString();
  }
}
```

- [ ] **Step 2: Forward `-Pqe.surface/-Pqe.case/-Pqe.seed`**

In `btrace-qe/build.gradle`, inside the `qe` task after `configureQeEnvironment(it)` (`qe.timeout` is already forwarded by `configureQeEnvironment`):

```groovy
    ['qe.surface', 'qe.case', 'qe.seed'].each { key ->
        if (project.hasProperty(key)) {
            systemProperty key, project.property(key)
        }
    }
```

- [ ] **Step 3: Gate — empty corpus run**

```bash
mkdir -p btrace-qe/cases
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qe :btrace-qe:spotlessCheck > /tmp/qe-t10.log 2>&1; grep -E '\[qe\]|BUILD' /tmp/qe-t10.log; ls btrace-qe/build/qe-report/
```

Expected: `BUILD SUCCESSFUL`, `[qe] ran 0 case(s), 0 finding(s)`, `report.json` and `report.md` present.

- [ ] **Step 4: Stage (do not commit)**

```bash
git add btrace-qe/src/main/java/io/btrace/qe/QeMain.java btrace-qe/build.gradle
git status --short
```

---

### Task 11: First corpus and wrapper script

**Files:**
- Create: `btrace-qe/cases/agent-args/fileroll-ms-negative.yaml`
- Create: `btrace-qe/cases/agent-args/fileroll-maxrolls-non-numeric.yaml`
- Create: `btrace-qe/cases/cli/missing-operand.yaml`
- Create: `btrace-qe/cases/cli/unknown-flag.yaml`
- Create: `btrace-qe/cases/cli/attach-nonexistent-target.yaml`
- Create: `scripts/qe-agent.sh`

**Interfaces:**
- Consumes: the full framework.
- Produces: a real report.

- [ ] **Step 1: Agent-args cases**

Both are grounded in `btrace-agent/.../agent/Main.java#parseArgs`. `FILE_ROLL_MILLISECONDS` catches `NumberFormatException` and nulls the value, and the `if (fileRollMilliseconds != null)` guard skips both the setter and the debug log. `FILE_ROLL_MAX_ROLLS` does the same with an empty `catch`. Neither path produces any diagnostic.

`fileroll-ms-negative.yaml`:
```yaml
id: agent-args.fileroll-ms-negative
surface: agent-args
intent: resilience
title: "Agent started with fileRollMilliseconds=-5"
given:
  mode: target
  target: SteadyStateTarget
  agentArgs: "script=${probeClassesDir}/io/btrace/qe/probe/QeStartupProbe.class,stdout=true,fileRollMilliseconds=-5"
steps:
  - launch
  - awaitReady
  - canary
  - shutdown
  - awaitExit
expect:
  messageMustMention:
    - "fileRollMilliseconds"
```

`fileroll-maxrolls-non-numeric.yaml`:
```yaml
id: agent-args.fileroll-maxrolls-non-numeric
surface: agent-args
intent: resilience
title: "Agent started with fileRollMaxRolls=not-a-number"
given:
  mode: target
  target: SteadyStateTarget
  agentArgs: "script=${probeClassesDir}/io/btrace/qe/probe/QeStartupProbe.class,stdout=true,fileRollMaxRolls=not-a-number"
steps:
  - launch
  - awaitReady
  - canary
  - shutdown
  - awaitExit
expect:
  messageMustMention:
    - "fileRollMaxRolls"
```

`-5` parses fine as a `Long`, so the first case tests whether a *semantically* invalid value is accepted silently. That is a different path from the second case's parse failure, and that is intentional. If the first case comes back with GUIDANCE=GOOD, that's a correct result, not a harness bug.

- [ ] **Step 2: CLI cases**

Grounded in `btrace-client/.../client/Main.java`: a flag with no operand hits `args.length <= count + 1 → usage()`, an unknown flag hits the final `else { usage(); }`, and `usage()` prints `Messages.get("btrace.usage")` to stderr and calls `System.exit(1)`. An unknown PID or name hits `errorExit("Unable to find JVM with either PID or name: ...", 1)`.

Before writing the `messageMustMention` token for the usage cases, read the actual banner. Run `java -cp btrace-dist/build/resources/main/v3.0.0-SNAPSHOT/libs/btrace.jar io.btrace.boot.Loader -p 2>&1 | head -3`, then use a stable word from its first line. Below, `Usage` is assumed; replace it if the banner differs.

`missing-operand.yaml`:
```yaml
id: cli.missing-operand
surface: cli
intent: conformance
title: "btrace -p (flag with no operand)"
spec:
  doc: docs/QuickReference.md
  claim: "CLI flags that take a value require one; a malformed invocation prints usage and exits non-zero"
given:
  mode: client
  clientArgs:
    - "-p"
steps:
  - runClient
  - awaitExit
expect:
  exitCode: 1
  messageMustMention:
    - "Usage"
```

`unknown-flag.yaml`:
```yaml
id: cli.unknown-flag
surface: cli
intent: conformance
title: "btrace -this-flag-does-not-exist 0"
spec:
  doc: docs/QuickReference.md
  claim: "Unrecognized CLI flags are rejected with usage, not silently accepted"
given:
  mode: client
  clientArgs:
    - "-this-flag-does-not-exist"
    - "0"
steps:
  - runClient
  - awaitExit
expect:
  exitCode: 1
  messageMustMention:
    - "Usage"
```

`attach-nonexistent-target.yaml`:
```yaml
id: cli.attach-nonexistent-target
surface: cli
intent: conformance
title: "btrace -p 65535 definitely-not-a-running-jvm-xyz"
spec:
  doc: docs/GettingStarted.md
  claim: "Attaching to a JVM that does not exist fails cleanly with a clear message"
given:
  mode: client
  clientArgs:
    - "-p"
    - "65535"
    - "definitely-not-a-running-jvm-xyz"
steps:
  - runClient
  - awaitExit
expect:
  exitCode: 1
  messageMustMention:
    - "Unable to find JVM"
```

- [ ] **Step 3: Wrapper**

`scripts/qe-agent.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

usage() {
  echo "Usage: scripts/qe-agent.sh [--surface <csv>] [--case <id>] [--seed <n>] [--timeout <ms>]" >&2
}

GRADLE_ARGS=(:btrace-qe:qe)
while [[ $# -gt 0 ]]; do
  [[ $# -ge 2 ]] || { usage; exit 64; }
  case "$1" in
    --surface) GRADLE_ARGS+=("-Pqe.surface=$2") ;;
    --case) GRADLE_ARGS+=("-Pqe.case=$2") ;;
    --seed) GRADLE_ARGS+=("-Pqe.seed=$2") ;;
    --timeout) GRADLE_ARGS+=("-Pqe.timeout=$2") ;;
    *) echo "Unknown option: $1" >&2; usage; exit 64 ;;
  esac
  shift 2
done

GRADLE_USER_HOME="$(pwd)/.gradle-user" ./gradlew "${GRADLE_ARGS[@]}"
echo "Report: btrace-qe/build/qe-report/report.md"
```

`chmod +x scripts/qe-agent.sh`

- [ ] **Step 4: Gate — full run, then read the report**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qe > /tmp/qe-t11.log 2>&1; grep -E '\[qe\]|BUILD' /tmp/qe-t11.log
cat btrace-qe/build/qe-report/report.md
```

Expected: `BUILD SUCCESSFUL`, `[qe] ran 5 case(s)`. For each case, check the verdict against the evidence in `runs/<id>/` (`steps.log`, `stderr.log`) rather than against this plan's predictions:
- `agent-args.fileroll-maxrolls-non-numeric`: expected RESILIENCE=PASS, GUIDANCE=POOR (S4).
- `agent-args.fileroll-ms-negative`: expected the same, but see the note in Step 1.
- `cli.*`: expected RESILIENCE=PASS, CONFORMANCE=PASS, GUIDANCE=GOOD.

If a verdict differs, decide which side is wrong. If the evidence shows the product behaving differently from what the case assumes, that is a real finding: leave the case alone and record the finding in the summary. If the evidence shows a harness defect (a wrong classpath, a missed line, a wrong token), fix the harness or the token and re-run.

- [ ] **Step 5: Stage (do not commit)**

```bash
git add btrace-qe/cases scripts/qe-agent.sh
git status --short
```

---

### Task 12: Spec success criteria, full gate, docs

**Files:**
- Create: `docs/QEAgent.md`
- Modify: `docs/README.md`

- [ ] **Step 1: Reproducibility**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qe -Pqe.seed=42 > /tmp/qe-a.log 2>&1 && cp btrace-qe/build/qe-report/report.json /tmp/qe-a.json
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qe -Pqe.seed=42 > /tmp/qe-b.log 2>&1 && cp btrace-qe/build/qe-report/report.json /tmp/qe-b.json
diff /tmp/qe-a.json /tmp/qe-b.json && echo IDENTICAL
```

Expected: `IDENTICAL`.

- [ ] **Step 2: Single-case isolation**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qe -Pqe.case=agent-args.fileroll-maxrolls-non-numeric > /tmp/qe-one.log 2>&1
grep -c '^| agent-args\|^| cli' btrace-qe/build/qe-report/report.md
```

Expected: `1`. The verdict matches the one from the full run.

- [ ] **Step 3: Never a build gate**

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qe > /dev/null 2>&1; echo "qe exit: $?"
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:build --dry-run | grep -E ':btrace-qe:(qe|qeSelfTest)\b' || echo "not in build graph"
```

Expected: `qe exit: 0` and `not in build graph`.

- [ ] **Step 4: Full gate** (this is a new module plus a version-catalog change, so run the repo's full build, not just the module)

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew build > /tmp/qe-full-build.log 2>&1; grep -E 'BUILD|FAILED|tests completed' /tmp/qe-full-build.log
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-qe:qeSelfTest > /tmp/qe-selftest.log 2>&1; grep -E 'BUILD|FAILED|PASSED' /tmp/qe-selftest.log
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew spotlessCheck > /tmp/qe-spotless-all.log 2>&1; grep -E 'BUILD|violations' /tmp/qe-spotless-all.log
```

Expected: all three `BUILD SUCCESSFUL`. Record each as `command → key result line` for the final report.

- [ ] **Step 5: `docs/QEAgent.md`**

````markdown
# BTrace QE Agent

`btrace-qe` is a manually run, reproducible harness that probes BTrace itself for spec-conformance
gaps and misuse-resilience defects: malformed CLI and agent arguments, absurd inputs, and similar
abuse. It is a developer tool, not a test suite. It is never part of `build` or `check`, and it
always exits `0`.

## Running it

```bash
./gradlew :btrace-qe:qe                                    # full corpus
./gradlew :btrace-qe:qe -Pqe.surface=agent-args            # one surface (comma-separated list allowed)
./gradlew :btrace-qe:qe -Pqe.case=cli.missing-operand      # one case
scripts/qe-agent.sh --surface cli --timeout 60000
```

Each run writes `btrace-qe/build/qe-report/report.md` and a deterministically ordered
`report.json`, plus per-case evidence (`stdout.log`, `stderr.log`, `steps.log`, and
`thread-dump.txt` on a timeout) under `btrace-qe/build/qe-report/runs/<case-id>/`.

`TEST_JAVA_HOME` (falling back to `JAVA_HOME`) selects the JDK the target JVMs and the client run on.

## What it checks

Every case is judged against five invariants:

- the process must not crash (including BTrace terminating the user's application at startup)
- the process must not hang or deadlock
- the target's state must not be corrupted
- BTrace must still be able to operate on the JVM afterwards (a canary probe is attached after
  the abuse)
- the user must be told what went wrong, without a raw stack trace

A case can be resilient (nothing broke) while still failing guidance (the bad input was silently
ignored). The report shows both.

Severities: S1 crash/hang/corruption · S2 BTrace unusable afterwards · S3 behavior differs from
the docs · S4 user not guided.

## Adding a case

Add a YAML file under `btrace-qe/cases/<surface>/`. The schema is documented in the
implementation plan and enforced by the loader, which rejects unknown keys. There are two modes:
`given.mode: target` launches a fixture JVM with `-javaagent:btrace.jar=<agentArgs>`, and
`given.mode: client` runs the real `btrace` client entry point with `clientArgs`. Adding a case to
an existing surface needs no code change.

## Harness self-tests

```bash
./gradlew :btrace-qe:test         # fast unit tests (part of check)
./gradlew :btrace-qe:qeSelfTest   # end-to-end self-tests against real JVMs (never part of check)
```

## Design

See `internal/specs/2026-08-11-btrace-qe-agent-design.md` for the full design and for the
surfaces planned beyond this first cut.
````

- [ ] **Step 6: `docs/README.md` row** (after the `ReleaseChecklist.md` row in the documentation map)

```markdown
| **[QE Agent](QEAgent.md)** | Reproducible harness probing BTrace's own spec conformance and misuse resilience | Contributors, maintainers |
```

- [ ] **Step 7: Stage, report, ask**

```bash
git add docs/QEAgent.md docs/README.md
git status --short
```

Don't commit. Report each gate as `command → key result line`, list the findings from the first real run, and ask the user how they want the work committed.

---

## Follow-up plans (not built here)

- **Script-compile / verifier conformance:** a `given.mode: script` that compiles a probe through `io.btrace.compiler.Compiler` and checks it against `VerifierVisitor`'s rejection rules, plus the script mutator family.
- **Attach-lifecycle abuse:** new step verbs (`sendEvent`, `killClient`, `detach`, `reattach`, `retransformStorm`), the `ProgrammaticCase` SPI, and the 3× re-run "reproduced N of 3" handling. At that point the `CaseExecutor` switch should become a verb registry.
- **Protocol / extensions:** malformed v2 frames over raw sockets, and permission-policy precedence and SHIM fallback against `docs/PermissionPolicy.md`.
- **Generative families, `--budget`, `--parallel`, `--baseline`:** once a surface needs generated cases.
- **`/qe-explore` skill:** authors new YAML cases for review.
- **Spec items not yet implemented (found in final review):** corpus hash and JDK vendor/version in the report (currently the `qe.javaHome` path); ASM `CheckClassAdapter` on dumped classes and output-file parsing as corruption checks; `NoCrashCheck` honoring an expected exit code (a target the agent deliberately refuses to start is always S1); per-step timeout overrides; `-Pqe.javaHome`; client-mode crash/hang labelled S1 although the spec defines S1 for the target JVM; JDK 8 client support (`tools.jar` on the client classpath, as `bin/btrace` does); `runCanary` capturing the failure cause for S2 diagnosis; conformance checking documented messages, not just exit codes.
- **Corpus candidates already observed:** a probe targeting an `io.btrace.*` class loads cleanly but silently never fires (no warning) — needs a probe aimed at an `io.btrace` class; port-abuse arguments (`port=-1|0|65536`) now expressible via the `${port}` placeholder.
