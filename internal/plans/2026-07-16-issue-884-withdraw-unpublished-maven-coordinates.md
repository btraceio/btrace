# Issue #884 implementation plan: withdraw unpublished Maven coordinates

Date: 2026-07-16  
Design input: `internal/specs/2026-07-16-issue-884-withdraw-unpublished-maven-coordinates.md`  
Decision: retain PR #900's deletion of `btrace-maven-plugin`. The only public Maven Central
engine/library artifact is `io.btrace:btrace`; `io.btrace.extension` and `io.btrace.fat-agent` are
the only supported plugins and publish through the Gradle Plugin Portal.

## Baseline safety rules

1. Start every gate with `git status --short`; preserve all existing unrelated and untracked work.
   Do not use `git reset`, `git checkout`, `git clean`, or blanket formatting.
2. Do not commit any #884 change until all implementation and verification gates pass, per
   `AGENTS.md`. Stage only the intentional final #884 files.
3. Run Gradle with `GRADLE_USER_HOME=$(pwd)/.gradle-user`, redirect each command's output to a file,
   filter it before reading, and add the repository IPv4 `JAVA_TOOL_OPTIONS` if the environment
   requires it.
4. Treat the masked `btrace-dist` JAR as opaque base content. This issue must not change class
   partitions, entry-point manifest attributes, loader behavior, or `META-INF/btrace/` layout.
5. Do not restore, implement, test, publish, or document a Maven fat-agent plugin. The removed
   module is not a fallback for any gate in this plan.

## Gate 0 — repository-wide inventory and Maven-plugin deletion proof

**Files changed:** none initially. Save the categorized results in PR/release evidence, not a new
user-facing guide.

1. Run root-level searches (including `.github`, excluding generated build outputs, Git metadata, and
   the workspace cache):

   ```sh
   rg -n --hidden --glob '!**/build/**' --glob '!.git/**' --glob '!.gradle-user/**' \
     "io\\.btrace:btrace-[[:alnum:]_.-]+" .
   rg -n --hidden --glob '!**/build/**' --glob '!.git/**' --glob '!.gradle-user/**' \
     "btrace-client:\+|btrace-core:\$\\{project\\.version\\}|btrace-(agent|boot)" .
   rg -n --hidden --glob '!**/build/**' --glob '!.git/**' --glob '!.gradle-user/**' \
     "btrace-maven-plugin|FatAgentMojo|fat-agent-pom" .
   git merge-base --is-ancestor 9bd52b9b HEAD
   test -z "$(git ls-tree -d --name-only HEAD btrace-maven-plugin)"
   test -z "$(git ls-files btrace-maven-plugin)"
   test -z "$(find btrace-maven-plugin -mindepth 1 -print -quit 2>/dev/null)"
   test ! -e docs/tutorials/demo/fat-agent-pom.xml
   ```

2. Categorize every hit as one of:

   - active public dependency, command, Maven plugin/application guidance, plugin JavaDoc, executable
     sample, release task, or production resolver — must be removed/migrated;
   - active source module/path notation — allowed only when it cannot be read as a public Maven GAV;
   - historical architecture/removal wording — allowed only when it explicitly says the module or GAV
     is internal, deleted, or non-installable;
   - `internal/plans/`, `internal/specs/`, or other historical internal records — classify and retain
     as evidence rather than rewriting accepted history merely to make `rg` empty.

3. `9bd52b9b` (PR #900) must be an ancestor of `HEAD`; stop the implementation if it is not. Also
   require the tracked-tree checks above to show no module. This current checkout may retain an empty,
   ignored worktree directory at `btrace-maven-plugin/`; it is not a restored module and must be left
   untouched. Stop and obtain direction if that ignored directory contains build/source/resource files
   or becomes tracked, rather than deleting user work. Confirm no `btrace-maven-plugin` entry in
   `settings.gradle`, no tracked source/resource/plugin descriptor path, no documentation demo POM,
   and no release task, summary, Central wait check, or artifact list can select/name it. PR #900
   removal wording is the only permitted historical reference.
4. Record the allowed release contract: Central stages only `io.btrace:btrace`; Plugin Portal handles
   only the implementation plus markers for `io.btrace.extension` and `io.btrace.fat-agent`.

**Pass:** PR #900 is verified in `HEAD`, every active occurrence has a migration/deletion
disposition, and the Maven plugin is absent from the tracked source, build, resources, demo, settings,
release, and public installation surfaces.

## Gate 1 — pin external Gradle plugin resolution to the masked engine

**Production files:**

- `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceExtensionPlugin.groovy`
- `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceFatAgentExtension.groovy`
- `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceFatAgentPlugin.groovy`
- add one package-private version-resolution helper in
  `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/` if that prevents duplicate validation.

**Focused tests:**

- `btrace-gradle-plugin/src/test/java/io/btrace/gradle/BTraceExtensionPluginTest.java`
- `btrace-gradle-plugin/src/test/java/io/btrace/gradle/BTraceFatAgentPluginTest.java`
- reusable fixtures only under `btrace-gradle-plugin/src/test/resources/`; otherwise use each
  TestKit test's temporary project directory.

1. Add a DSL `btraceVersion` (or clearly equivalent documented setting) which is independent of the
   extension author's `project.version` and `btraceExtension.version`.
2. Make the published plugin implementation JAR carry its concrete release value in
   `Implementation-Version`; add one helper which reads that version from the loaded plugin class's
   package/code source. This manifest attribute is the sole implicit default. If it is unavailable in
   TestKit/development, require explicit `btraceVersion`; never guess from the consumer project.
3. Validate before resolving: reject null/blank, `+`, ranges/selectors, `unspecified`, and release
   publication snapshot/mismatch values with an actionable message naming the required setting.
4. Preserve the in-tree `project(':btrace-core')` annotation-processor branch exactly. In an external
   root with no sibling core project, add only
   `io.btrace:btrace:<validated-btraceVersion>` to `annotationProcessor`.
5. Replace `BTraceFatAgentPlugin.findBTraceCompiler()`'s dynamic `btrace-client:+` resolution with
   the same pinned `io.btrace:btrace:<validated-btraceVersion>`. For the masked artifact, launch
   `io.btrace.boot.Loader` with `-Dbtrace.client.main=io.btrace.compiler.Compiler`, pass the existing
   compiler arguments, and require a zero exit status. Do not use a conventional client JAR classpath
   or revive any withdrawn GAV.
6. Define the external fat-agent engine assembly path. Create one non-consumable, resolvable
   configuration/provider (for example `btraceEngine`) that is populated with exactly
   `io.btrace:btrace:<validated-btraceVersion>` and exposes one resolved masked JAR. Register a lazy
   unpack/staging task fed by that provider; `fatAgentJar` must depend on it and package its staged
   contents. The external consumer path must work with no `agentJarTask`, `bootJarTask`, sibling
   project, or `includeBuild`.
7. Seed the output manifest from the resolved engine's original manifest before adding any fat-agent
   attributes. Preserve the masked engine's `Main-Class`, `Premain-Class`, `Agent-Class`, loader/main
   routing attributes, and BTrace metadata; only add/adjust the documented fat-agent attributes such
   as embedded-extension information. Do not generate a fresh manifest from hard-coded entry points,
   and do not overlay a separate boot JAR. Keep explicit in-tree producer-task compatibility only when
   it produces the same masked engine contract.
8. Make the unpacked engine provider a declared task input and validate the final artifact against
   both the required `io/btrace/boot/Loader.class`/masked entries and the preserved manifest contract.
   A missing, conventional, or mismatched input JAR must fail before a fat-agent output is accepted.
9. Add TestKit coverage proving:

   - in-tree annotation processing remains the project dependency;
   - an external project with deliberately different project/extension versions records exactly the
     configured `io.btrace:btrace:<btraceVersion>` dependency;
   - a separately resolved, locally published plugin implementation with a manifest version permits
     omitted DSL `btraceVersion` and uses that exact version; TestKit classpath execution without the
     manifest fails with the explicit-setting remedy;
   - missing/blank/dynamic/ranged values fail before dependency resolution;
   - an external consumer with no agent/boot task resolves the single pinned `btraceEngine`, runs
     `fatAgentJar`, and unpacks its local masked-JAR fixture. Assert the exact GAV request, the
     dependency/staging task relationship, Loader and representative `META-INF/btrace/` entries, and
     that the output's required Loader/agent manifest attributes match the source engine;
   - `bundledProbes.fromSource(...)` resolves only pinned `btrace`, launches Loader with the client
     main property, compiles a valid minimal probe from a locally published masked-JAR fixture, and
     creates the expected probe output. Skipping compilation, warning-only behavior, a nonzero exit,
     or any client/core GAV request fails the test.

**Commands:**

```sh
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -p btrace-gradle-plugin test > /tmp/issue-884-gradle-plugin.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|BTraceExtensionPluginTest|BTraceFatAgentPluginTest" /tmp/issue-884-gradle-plugin.log
rg -n "io\\.btrace:btrace-(core|client):|btrace-client:\+" btrace-gradle-plugin/src/main btrace-gradle-plugin/src/test
```

**Pass:** external resolution and base assembly are exactly the single pinned masked `btrace` engine;
the monorepo path remains intact; no dynamic, separate boot, or withdrawn engine/client coordinate
remains.

## Gate 2 — remove obsolete public documentation and Maven-fat-agent remnants

**Files to inspect/change according to Gate 0:**

- `README.md`
- `docs/GettingStarted.md`
- `docs/QuickReference.md`
- `docs/BTraceExtensionDevelopmentGuide.md`
- `docs/architecture/migrating-from-libs-profiles.md`
- `docs/architecture/fat-agent-plugin.md`
- `docs/tutorials/06-write-your-own-extension.md`
- `docs/tutorials/08-fat-agent.md`
- delete `docs/tutorials/demo/fat-agent-pom.xml`
- `btrace-gradle-plugin/README.md`
- `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceFatAgentExtension.groovy`
- `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceFatAgentPlugin.groovy`
- `docs/releasing.md`, `docs/ReleaseChecklist.md`, and `.github/workflows/release.yml` wherever
  Gate 0 finds Maven-plugin release/application wording.

1. Migrate every direct JBang command from `io.btrace:btrace-client:<version>` to
   `io.btrace:btrace:<version>`; retain `jbang btrace` and explain the masked JAR's `Main-Class`
   dispatch only where useful.
2. Replace BTrace-owned Gradle extension GAV examples (including metrics, statsd, and the
   `btrace-kafka` JavaDoc examples) with actual built/distribution extension ZIP paths used by
   `file(...)`. Retain `project(...)` for in-tree/custom extensions; state that `maven(...)` is only
   for third-party extensions which their publisher actually makes available.
3. Remove all Maven fat-agent sections, Maven embedding snippets, `<extension>` examples, demo-POM
   links/copies, and Maven plugin installation/application claims. Redirect the deployment use case
   to the Gradle `file(...)`/`project(...)` workflow; do not translate Maven XML into a fictitious ZIP
   parameter.
4. Remove public `btrace-maven-plugin` and `FatAgentMojo` mentions. Where a release/removal history
   must retain one, label it explicitly "deleted by PR #900; non-installable" and do not include an
   invocation or coordinate users could copy.
5. For extension-author documentation, first compile the documented external project against
   `io.btrace:btrace`. Use that dependency only if the regular masked-JAR entries expose the required
   API; otherwise document the supported source/distribution path and file a follow-up API issue —
   never restore `btrace-core` as a public coordinate.
6. Use only normal Plugin Portal consumer snippets:

   ```groovy
   plugins {
       id 'io.btrace.extension' version '<release-version>'
       // or: id 'io.btrace.fat-agent' version '<release-version>'
   }
   ```

   Do not advertise Maven Central marker GAVs, `resolutionStrategy`, or local `includeBuild` as the
   public installation mechanism.

**Commands:**

```sh
rg -n --hidden --glob '!**/build/**' --glob '!.git/**' --glob '!.gradle-user/**' \
  "io\\.btrace:btrace-[[:alnum:]_.-]+|btrace-maven-plugin|FatAgentMojo|fat-agent-pom" .
rg -n --hidden --glob '!**/build/**' --glob '!.git/**' --glob '!.gradle-user/**' \
  "jbang io\\.btrace:btrace-client|maven\('io\\.btrace:btrace-|<extension>io\\.btrace:btrace-" .
test ! -e docs/tutorials/demo/fat-agent-pom.xml
```

Manually classify remaining internal historical hits. Every other public, executable, JavaDoc, or
production-resolver hit must be removed or migrated.

**Pass:** no public consumer guidance claims a deleted Maven plugin, Maven fat-agent path, or
unpublished BTrace module/extension coordinate; the Gradle local-package route is actionable.

## Gate 3 — configure Plugin Portal-only publication and version/signing safeguards

**Files:**

- `btrace-gradle-plugin/build.gradle`
- `btrace-gradle-plugin/settings.gradle` only if it is actually introduced for plugin management
- `.github/workflows/release.yml`
- `docs/releasing.md`, `docs/ReleaseChecklist.md`, and Portal-facing snippets identified in Gate 2.

1. Keep `btrace-dist` as the only Central publication. Do not add Central repositories, publications,
   signing work, or sources/Javadoc tasks for a removed Maven plugin.
2. Apply a pinned Gradle-compatible `com.gradle.plugin-publish` plus `signing` in the included Gradle
   build; retain `java-gradle-plugin`. Configure Portal metadata (site, VCS, descriptions, useful
   tags) and Portal-supported signing for both IDs. Do not publish their implementation or markers to
   Maven Central.
3. Make `-PbtraceVersion=<release-version>` the included build's mandatory release contract. It must
   drive the implementation JAR manifest, implementation publication, and both marker POM versions.
   A visibly development-only fallback is acceptable locally; remote publication must fail early for
   missing, snapshot, unspecified, dynamic, or mismatched values.
4. Keep `stage-maven` limited to
   `:btrace-dist:publishAllPublicationsToSonatypeRepository`, with Central credentials/GPG material
   for `io.btrace:btrace` only. Its artifact summary and Central wait check must name/check only that
   artifact.
5. Add an explicitly dependent Portal publish job from the same RC tag. Run
   `./gradlew -p btrace-gradle-plugin -PbtraceVersion=${{ inputs.release_version }} publishPlugins --validate-only`
   before the real `publishPlugins`, injecting `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET` only
   for Portal upload. Report both IDs, exact version, validate-only result, upload result, and a
   bounded `gradlePluginPortal()` availability check.
6. Wire `finalize-tag`, `create-github-release`, downstream public-release consumers, and `summary`
   to require both successful `wait-for-maven` and Portal availability. The summary must separately
   list Central's sole `btrace` artifact and both Portal IDs/statuses; no final tag or GitHub release
   may claim plugin availability before both IDs resolve.

**Commands/evidence:**

```sh
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -p btrace-gradle-plugin -PbtraceVersion=<release-version> publishPlugins --validate-only > /tmp/issue-884-plugin-portal-validate.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|validate|publish" /tmp/issue-884-plugin-portal-validate.log
```

Inspect the implementation POM and both marker POMs in the isolated publication repository; each
must have exactly `<release-version>`. Re-run remote-publication configuration with missing and
mismatched `btraceVersion` and require failure before upload. Any quality/format task configured for
the included build must be invoked with `./gradlew -p btrace-gradle-plugin <task>`, never as a root
subproject task.

**Pass:** only `btrace` is Central-ready; the two Gradle plugin IDs/markers are Portal-ready, signed,
and version-locked to the release property.

## Gate 4 — hermetic pre-release functional E2E

**Integration implementation:**

- add `integration-tests/src/test/java/tests/Issue884PublishedFatAgentE2ETest.java`, reusing the
  process/attach/protocol utilities in `integration-tests/src/test/java/tests/RuntimeTest.java` and
  target fixtures in `integration-tests/src/test/java/resources/`;
- add one minimal dedicated probe/target resource under `integration-tests/src/test/java/resources/`
  only if existing `TestApp`/script fixtures cannot make an unambiguous observable assertion;
- add test build wiring in `integration-tests/build.gradle` only if the scenario cannot obtain the
  existing distribution/integration-test outputs through current tasks.

1. Build the exact release-version masked engine, then publish only
   `io.btrace:btrace:<release-version>` and the Gradle plugin implementation plus both marker
   artifacts to an isolated temporary Maven repository. Do not query Maven Central or the Plugin
   Portal for pre-release evidence.
2. Create a standalone temporary Gradle consumer with a fresh `GRADLE_USER_HOME`, no sibling BTrace
   projects, no `includeBuild`, and no TestKit plugin-classpath injection. Its
   `pluginManagement.repositories` and normal dependency repositories point solely at the isolated
   repository. Resolve both marker IDs through normal `plugins {}` declarations; use the fat-agent
   plugin's external `btraceEngine` provider path (with no `agentJarTask` or `bootJarTask`) to build
   a fat agent from the exact masked engine.
3. Configure the consumer with an explicit release `btraceVersion` in one run and a second run with
   the setting omitted, proving the locally published plugin implementation's `Implementation-Version`
   supplies the exact release version. Record resolved component IDs/GAVs and assert no
   `btrace-core`, `btrace-client`, agent/boot, extension, Maven-plugin, dynamic, or external
   repository dependency is used.
4. Assert that the generated fat agent contains the locally resolved engine's Loader and masked
   entries and preserves its required Loader/agent manifest attributes before starting it. Start a
   real target JVM with that generated fat agent. From the *locally published masked*
   `io.btrace:btrace:<release-version>` artifact, invoke the real BTrace client to dynamically attach
   to that target and submit a real probe. The client/agent interaction must use the actual attach and
   wire-protocol path, not an in-process `Client` mock or agent-startup-only assertion.
5. Wait for and assert a deterministic observable probe effect (for example a uniquely formatted
   probe output line caused by an exercised target method). Capture child-process stdout/stderr,
   attach exit status, protocol completion, and cleanup/timeout diagnostics. A successful agent JVM
   launch without an attach, probe submission, and observable output is a test failure.
6. Inspect the masked engine and generated fat-agent manifests/entries with `jar tf` and `unzip -p`:
   `Main-Class`, `Premain-Class`, `Agent-Class`, and representative `META-INF/btrace/` content must
   survive. This test is required integration coverage for a user-visible cross-process behavior;
   TestKit and unit tests in Gate 1 do not substitute for it.

**Commands:**

```sh
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build > /tmp/issue-884-dist.log 2>&1
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:test > /tmp/issue-884-integration.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Issue884PublishedFatAgentE2ETest|tests" /tmp/issue-884-dist.log /tmp/issue-884-integration.log
jar tf <isolated-repo-btrace-jar> | rg "META-INF/btrace|io/btrace/boot/Loader"
unzip -p <generated-fat-agent.jar> META-INF/MANIFEST.MF
```

**Pass:** an isolated ordinary consumer resolves local markers/engine, creates a fat agent, and a
real locally published BTrace client attaches to a real agent target, sends a probe, and observes its
expected effect.

## Gate 5 — release smoke and final audit

1. After real Central release and Plugin Portal approval only, use a clean standalone consumer with
   default `gradlePluginPortal()` to apply both public IDs and Maven Central to resolve
   `io.btrace:btrace:<release-version>`. Record exact Portal URLs, Central URL, IDs, versions, and
   output; local success does not substitute for this external state.
2. Re-run Gate 0's root-level inventory and manually classify the internal historical results. Verify
   there is no active module directory/source/resources/demo/settings/release reference to the Maven
   plugin and no production resolver/public consumer reference to a withdrawn GAV.
3. Run final verification, always reading filtered logs:

   ```sh
   GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -p btrace-gradle-plugin test > /tmp/issue-884-gradle-plugin.log 2>&1
   GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build > /tmp/issue-884-dist.log 2>&1
   GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:test > /tmp/issue-884-integration.log 2>&1
   GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew spotlessCheck > /tmp/issue-884-spotless.log 2>&1
   rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|tests" /tmp/issue-884-*.log
   git diff --check
   ```

4. Before committing, compare the final changed-file list to Gates 1–4 and preserve unrelated
   changes. The PR description/release evidence must state the release value, `-PbtraceVersion`,
   isolated repository, three exact publication POM versions, signed Portal commands, E2E target/
   client/probe observable result, manifest proof, and post-release Central/Portal availability.

**Completion criteria:**

- `btrace-maven-plugin`, its source/resources/build/release/demo/install surface, and any active
  Maven fat-agent guidance remain deleted; historical wording is explicitly non-installable.
- `btrace-dist` remains the sole Maven Central engine/library artifact.
- External Gradle consumers resolve only pinned `io.btrace:btrace:<btraceVersion>` and use neither
  dynamic selectors nor withdrawn module coordinates; in-tree processing still uses its project
  dependency.
- Both public Gradle plugin markers and implementation publish through the Plugin Portal with exact
  release version and signing; Central never receives markers or a Maven plugin.
- The isolated pre-release E2E has proven normal marker resolution, generated-fat-agent startup, real
  client attach/probe protocol flow, and observable output using the exact locally published engine.
