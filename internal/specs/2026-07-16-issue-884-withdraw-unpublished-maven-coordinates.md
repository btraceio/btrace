# Issue #884: Withdraw unpublished Maven coordinates

## Status and decision

**Issue:** [#884](https://github.com/btraceio/btrace/issues/884), "[3.0] Published Maven surface omits coordinates the README and plugins resolve".

**Final operator decision:** retain PR #900's deletion of the Maven fat-agent plugin. The single
public Maven Central artifact is `io.btrace:btrace`. `btrace-maven-plugin` must be absent from the
repository, build, release surface, and all public installation/application documentation. Explicit
historical or removal-status wording is allowed where needed to record PR #900, provided it clearly
states that the plugin is deleted and non-installable. BTrace must not document, restore, publish, or
internally require published `btrace-core`, `btrace-agent`, `btrace-boot`, `btrace-client`,
`btrace-runtime`, `btrace-compiler`, `btrace-maven-plugin`, or `btrace-extensions/*` coordinates.

The two Gradle plugins are the explicit exception: `io.btrace.extension` and
`io.btrace.fat-agent` are supported public plugins and must be published through the Gradle Plugin
Portal. Publishing those plugins must never be used to publish the withdrawn engine/library,
extension, or Maven-plugin modules.

## Context

`btrace-dist` produces the signed `io.btrace:btrace` publication.  The release workflow currently
stages only `:btrace-dist:publishAllPublicationsToSonatypeRepository`.  It is a masked, self-contained
JAR: its manifest uses `io.btrace.boot.Loader` for `Main-Class`, `Premain-Class`, and `Agent-Class`;
the agent, client, compiler, boot, core API, and runtime support are deliberately packaged within it.
See [Masked JAR Architecture](../../docs/architecture/MaskedJarArchitecture.md).

The repository nevertheless exposes paths that resolve unpublished module or extension GAVs.  They
fail for a released build even though the normal distribution/JBang alias path works.  These failures
are an inaccurate public contract, not a request to publish the individual modules.

## Goals

1. Make every supported external engine dependency resolve `io.btrace:btrace:<btrace-version>`.
2. Make the external Gradle extension-plugin annotation-processor fallback resolve the same masked
   distribution rather than `btrace-core`.
3. Remove user-facing instructions that present bundled BTrace extensions as Maven Central artifacts,
   Maven fat-agent support, or the deleted Maven plugin.
   Teach users to use the extension packages bundled with a BTrace distribution or an extension ZIP
   they built/obtained separately.
4. Publish and sign the public Gradle plugins to the Gradle Plugin Portal for 3.0, including marker
   artifacts.
5. Preserve the in-tree build path and the existing masked-JAR layout/loader behavior.

## Non-goals

- Publishing or signing individual BTrace library modules or BTrace extension modules.
- Changing the masked-JAR class partitioning, manifest entry points, or loader behavior.
- Restoring, reimplementing, testing, or publishing `btrace-maven-plugin` or its `fat-agent` goal.
  The module is intentionally deleted and must not remain as dormant source or build configuration.
- Removing support for third-party extension Maven repositories.  The generic `maven(...)` extension
  source remains valid for coordinates that their publishers actually publish; only BTrace-owned,
  unpublished examples and implied support are withdrawn.
- Changing local-project or local-file extension embedding.

## Affected components and required changes

### 1. Gradle extension plugin external fallback

**Files:**

- `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceExtensionPlugin.groovy`
- `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceFatAgentExtension.groovy`
- `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceFatAgentPlugin.groovy`
- focused TestKit/unit tests under `btrace-gradle-plugin/src/test/`

Keep the in-tree branch, which supplies `project(':btrace-core')`, unchanged.  In the external branch
where the root project has no `:btrace-core`, register `io.btrace:btrace:<version>` on
`annotationProcessor` rather than `io.btrace:btrace-core:<version>`.

The fallback version must be BTrace's version, not the extension author's arbitrary
`project.version`.  Add a `btraceVersion` setting to the extension-plugin DSL (or an equivalently
named, documented setting) which defaults to the published plugin's implementation version.  Validate
an explicit value as a non-empty, concrete release/version selector before dependency creation; when
the implementation version is unavailable in a development/TestKit environment, require the explicit
setting instead of guessing.  Use that resolved BTrace version for the external
`annotationProcessor` dependency.  It is independent of `btraceExtension.version`, which remains the
version of the extension being built.

Tests must verify all of the following: the in-tree branch remains a project dependency; an external
synthetic project with a deliberately different project/extension version requests exactly
`io.btrace:btrace:<btraceVersion>`; and missing/invalid BTrace version configuration fails with an
actionable error rather than resolving a dynamic or project-derived coordinate.

#### Gradle fat-agent compiler lookup

`BTraceFatAgentPlugin.findBTraceCompiler()` also dynamically resolves the withdrawn
`io.btrace:btrace-client:+` coordinate for `bundledProbes.fromSource(...)`.  Replace it with the
version-pinned `io.btrace:btrace:<btraceVersion>` engine artifact, using the same validated
BTrace-version source (published plugin implementation version or explicit `btraceVersion`) as above.
Never use `+`, a version range, or an extension project's version.

The implementation must make the masked distribution usable by the probe-compilation path rather
than assuming a conventional standalone `btrace-client` JAR.  It may use the distribution's supported
loader/CLI entry point if `io.btrace.compiler.Compiler` is not directly classpath-loadable from the
masked JAR; it must not restore an unpublished dependency as a workaround.  Add a TestKit regression
that records the configured/resolved dependency and runs the source-probe path against a minimal
locally published masked `btrace` fixture.  It must prove the exact pinned GAV and successful probe
compilation (or a clear, non-resolution failure if the fixture intentionally lacks compiler content).

### 2. Public documentation and examples

**Primary files known to contain invalid BTrace-owned coordinates:**

- `README.md`
- `docs/GettingStarted.md`
- `docs/QuickReference.md`
- `docs/BTraceExtensionDevelopmentGuide.md`
- `docs/architecture/migrating-from-libs-profiles.md`
- `docs/architecture/fat-agent-plugin.md`
- `docs/tutorials/06-write-your-own-extension.md`
- `docs/tutorials/08-fat-agent.md`
- `docs/tutorials/demo/fat-agent-pom.xml`
- `btrace-gradle-plugin/README.md`
- JavaDoc/examples in `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceFatAgentPlugin.groovy`

The implementation must perform a repository-wide final search rather than limiting itself to this
list.  User-facing documentation may not advertise any of these as resolvable BTrace artifacts:

```
io.btrace:btrace-client
io.btrace:btrace-agent
io.btrace:btrace-boot
io.btrace:btrace-core
io.btrace:btrace-runtime
io.btrace:btrace-compiler
io.btrace:btrace-metrics
io.btrace:btrace-statsd
io.btrace:btrace-kafka-extension
io.btrace:btrace-flink-extension
io.btrace:btrace-maven-plugin
```

#### CLI/JBang migration

Change direct JBang invocations from:

```
jbang io.btrace:btrace-client:<version> <PID> <script.java>
```

to:

```
jbang io.btrace:btrace:<version> <PID> <script.java>
```

Explain, where appropriate, that the masked JAR dispatches to the client through its `Main-Class`.
Retain the `jbang btrace` alias path.

#### Extension/embed migration

Replace **Gradle** examples such as `maven('io.btrace:btrace-metrics:...')` with a supported
local-file/package workflow.  The documentation must state both facts below:

1. Built-in extensions are distributed in the BTrace distribution's `extensions/` directory as
   extension packages (and may be obtained by building `packageExtension` in a source checkout).
2. `file(...)` is the supported way to embed one of those packages in a fat agent; `project(...)`
   remains the supported in-tree/custom project option.  `maven(...)` is only for a separately
   published third-party extension, not BTrace's bundled extensions.

Examples must use an actual package filename/path appropriate to the consuming tool rather than
inventing an unpublished GAV.  Preserve tutorial-specific explanations of an extension ZIP's expected
extension ID and packaging layout.

`file(...)` is a Gradle fat-agent DSL feature only. Remove all Maven fat-agent embedding examples,
Maven demo POM configuration, and Maven-plugin installation/application guidance. Do not replace
Maven `<extension>` elements with ZIP paths and do not describe a Gradle `file(...)` package as a
Maven capability. Redirect users who need an embedded extension to the documented Gradle
file/project workflow. `btrace-maven-plugin` and its `fat-agent` goal are deleted; no future Maven
local-package feature is in scope for this issue.

For the extension-development tutorial, replace compile-only `btrace-core` dependencies with the
supported `btrace` artifact only if its regular class entries provide the needed extension API.  The
implementation gate below requires compiling the documented external consumer before this claim is
published.  If that artifact cannot provide the required API, do not reintroduce a private GAV:
document the supported distribution/source-based extension-development route and record the missing
public API as a separate issue.

### 3. Required Gradle-plugin publication workstream

**Files:**

- `btrace-gradle-plugin/build.gradle`
- `btrace-gradle-plugin/settings.gradle` if the Plugin Publish plugin/version is centrally declared
- `.github/workflows/release.yml`
- release-facing README/documentation that names a plugin application coordinate

`btrace-gradle-plugin` currently applies `maven-publish`, but does not yet apply `signing` or configure
the Gradle Plugin Portal. It is an included build, not a root subproject, so its publication tasks
must be invoked through that build explicitly. The existing `stage-maven` job invokes only
`:btrace-dist:publishAllPublicationsToSonatypeRepository`; it cannot publish the Gradle plugins.

#### Included-build release-version contract

The Gradle plugin build has an independent root and currently sets `version = rootProject.version`.
When it is run as an included build, that root has no release version supplied by the main build;
publishing it would otherwise produce an unspecified or stale version.  Define one explicit shared
release property, `btraceVersion`, consumed by `btrace-gradle-plugin/build.gradle`.  For a release,
`-PbtraceVersion=${{ inputs.release_version }}` is mandatory, must be validated as the workflow's
concrete release version, and must become the version of the implementation publication and both
plugin-marker publications.  A normal development build may retain a clearly identified development
fallback, but any non-local publication must fail rather than publish an unspecified, snapshot, or
mismatched version.

The release workflow must pass the exact same release version to every publication command.  In
particular, it must invoke the included build independently, for example using
`./gradlew -p btrace-gradle-plugin -PbtraceVersion=${{ inputs.release_version }} <publish-task>`;
root-build task paths do not publish its artifacts.  Apply this rule to its local/staging verification
commands as well as its real Plugin Portal release command.

#### Required artifacts and binding publication channels

The Gradle plugins must publish through the **Gradle Plugin Portal**, not Maven Central. Add and
configure a pinned, Gradle-compatible `com.gradle.plugin-publish` version in
`btrace-gradle-plugin`, retaining `java-gradle-plugin` and the metadata required by the Portal.
Publish the implementation and marker artifacts for both
`io.btrace.extension` and `io.btrace.fat-agent`. Supply a website, VCS URL, descriptions, and useful
tags/categories in the `gradlePlugin` metadata so the Portal review and consumer listing are complete.
Apply `signing` so Portal-published artifacts are signed through the Plugin Publish plugin's supported
signing integration.

The public installation examples must use the ordinary Portal consumer route:

```groovy
plugins {
    id 'io.btrace.extension' version '<release-version>'
    // or: id 'io.btrace.fat-agent' version '<release-version>'
}
```

Consumers may use the default `gradlePluginPortal()` repository (or explicitly add it in
`pluginManagement.repositories`). Do not document a Maven Central marker coordinate or a
`resolutionStrategy` workaround for these public plugins. The maintainer decision does not permit
relying on the repository's local `includeBuild` behavior as a release mechanism.

#### Signing and release workflow

Keep the existing signing/release-safe GPG configuration solely for the `io.btrace:btrace` Central
publication. Configure the Gradle plugin's `signing` integration for Plugin Portal publication. Add
the required CI secrets:

- Central Portal token/user credentials and the GPG signing key/password for
  `io.btrace:btrace` only.
- `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET` for the Gradle Plugin Portal API key/secret.

Keep `stage-maven` limited to publishing `io.btrace:btrace` to Maven Central from the release-candidate
tag. Add a separate, explicitly dependent Plugin Portal publish job that invokes the included build with
`./gradlew -p btrace-gradle-plugin -PbtraceVersion=${{ inputs.release_version }} publishPlugins` and
the Portal credential environment variables. Before that real upload, run the identical included-build
command with `publishPlugins --validate-only`. Do not assume the `pluginManagement { includeBuild(...) }`
declaration exposes publication tasks on the root build.

The Central staging summary must list `io.btrace:btrace` only, never the deleted Maven plugin or
Gradle plugin markers. Preserve the manual Central Portal review/release step for that artifact.
The Portal job must report both IDs, their version, the validate-only result, upload result, and the
Portal availability check. Initial Portal approval can delay public availability; the release must
surface that status explicitly and must not claim that either Gradle plugin is installable until the
Portal resolves it.

No release job may publish `btrace-core`, `btrace-agent`, `btrace-boot`, `btrace-client`, runtime,
compiler, `btrace-maven-plugin`, or BTrace extension modules. The Gradle plugins are supported tools,
not a precedent for publishing the internal modules that they use.

## Compatibility and migration

| Previous unsupported flow | Supported replacement |
| --- | --- |
| `jbang io.btrace:btrace-client:<version> ...` | `jbang io.btrace:btrace:<version> ...` or `jbang btrace ...` |
| Maven fat-agent/plugin documentation | Deleted; use the documented Gradle fat-agent `file(...)` or `project(...)` workflow |
| External Gradle plugin resolves `btrace-core` processor | It resolves `io.btrace:btrace:<version>`; source extensions use the documented supported dependency route |
| `maven('io.btrace:btrace-metrics:...')` | `file('<path-to-btrace-metrics-extension-package>')`, or a local project |
| Maven `<extension>io.btrace:btrace-metrics:...</extension>` | Withdrawn; use the documented Gradle `file(...)` or `project(...)` embedding workflow |
| In-repo `includeBuild` makes Gradle plugins available | Release users apply `io.btrace.extension` or `io.btrace.fat-agent` by version through the Gradle Plugin Portal |
| Maven fat-agent plugin exists in a source checkout | Deleted; no module, build inclusion, demo, or release surface remains |

This is a correction before/for 3.0.  No compatibility promise is made for the withdrawn GAVs because
they were never released.  The documentation must make the reason visible enough that a user does not
mistake an unavailable Central lookup for a temporary repository outage.

## Implementation gates

1. **Inventory/deletion gate:** record all BTrace-owned GAV occurrences with `rg`; categorize each as
   source module notation (allowed), historic/architecture explanation (allowed only when explicitly
   marked non-published), or an external-consumer resolution/example (must change). Confirm that
   `btrace-maven-plugin/` is absent in full: its `build.gradle`, all `src/main`, `src/test`, and
   resources, generated plugin descriptor, and every build artifact/configuration are gone. Confirm
   `docs/tutorials/demo/fat-agent-pom.xml` is deleted, `settings.gradle` has no inclusion/reference,
   and no release workflow task can select it. Historical documentation may mention the deleted plugin
   only when explicitly labelled historical and non-installable.
2. **Gradle fallback gate:** change the external annotation processor fallback and test it without a
   sibling `:btrace-core` project; retain a regression test for in-tree behavior.
3. **Documentation gate:** migrate every external resolution/example and update `docs/README.md` only
   if a guide is added or renamed (none is expected).
4. **Maven deletion gate:** remove Maven fat-agent examples and Maven plugin installation/application
   guidance. Verify `rg` finds no user-facing `btrace-maven-plugin`, `FatAgentMojo`, or BTrace-owned
   Maven extension GAV as a supported flow; any historic mention must meet the inventory gate's
   explicit non-installable wording.
5. **Pinned-resolution gate:** add TestKit coverage for the external extension plugin and fat-agent
   source-probe lookup.  It must use a deliberately different extension project version and a pinned
   BTrace version, observe only `io.btrace:btrace:<btraceVersion>`, and reject missing/invalid version
   settings.  No dynamic `+` or BTrace client/core module lookup may remain.
6. **Plugin Portal configuration gate:** apply `com.gradle.plugin-publish`, configure both public IDs
   and their Portal metadata, and run
   `./gradlew -p btrace-gradle-plugin -PbtraceVersion=<release-version> publishPlugins --validate-only`
   with Portal credentials. The validation must pass before upload.
7. **Plugin signing gate:** run the Gradle Plugin Portal validate-only task with the configured
   signing integration and fail the release candidate early if the required Central or Portal
   credentials are absent.
8. **Included-build version gate:** invoke the Gradle plugin's independent publish task with
   `-PbtraceVersion=<release-version>`, inspect the implementation POM and both marker POMs in the
   local/staging repository, and assert each `<version>` is exactly `<release-version>`.  Repeat with
   the property missing and with a mismatch to prove a release publication fails safely.
9. **Release-workflow gate:** exercise the release publishing commands from a release-version checkout
   (or a non-destructive dry-run/test repository equivalent) and verify that the distribution, Maven
   Central engine artifact and Plugin Portal Gradle-plugin artifacts are all selected with the exact
   release property. Review the Central and Portal job summaries.
10. **Hermetic external-consumer gate:** publish a release-version `btrace-dist` artifact and the
    Gradle plugin implementation plus both marker artifacts to an isolated local/staging Maven
    repository.  Run a standalone Gradle project with no sibling BTrace projects and an isolated
    Gradle cache.  It must use `pluginManagement.repositories { maven { ... } }` and a normal
    `plugins { id 'io.btrace.extension' version '<release-version>' }` (and, separately, the
    `io.btrace.fat-agent` ID) to resolve the published marker and implementation artifacts.  It must
    not use TestKit plugin classpath injection or `includeBuild`.  Its normal dependency repository
    must resolve/compile only the exact `io.btrace:btrace:<release-version>` GAV.  This is the
    pre-release proof; it must not depend on a previously released Central artifact.
11. **Post-release smoke gate:** after the maintainer releases the Central staging repository and the
    Plugin Portal accepts/publishes both IDs, run the standalone consumer against Maven Central and
    resolve/apply the public Gradle plugins through `gradlePluginPortal()`. Record the artifact
    versions, Portal URLs, and check output in the release evidence.
12. **Pre-release end-to-end gate:** locally publish the exact-release masked engine and Gradle plugin
   implementation plus both marker artifacts to the isolated test repository. A standalone build must
   apply the plugins through normal `plugins {}` marker resolution from that repository (never
   `includeBuild` or TestKit classpath injection), build a fat agent, and launch a real target JVM
   using that generated agent. Invoke the real BTrace client from the locally published masked
   `io.btrace:btrace:<release-version>` artifact to attach to the target and send a probe. Assert the
   probe's observable result, thereby exercising the client-to-agent attach/protocol path rather than
   merely proving that an agent starts. Add or extend this as an `integration-tests` scenario. Gradle
   Plugin Portal resolution is a post-release smoke only; unit and TestKit coverage do not replace
   this client, agent, target-JVM, and protocol path.

## Acceptance criteria

- No supported/public consumer path resolves `io.btrace:btrace-agent`, `btrace-boot`, `btrace-core`,
  `btrace-maven-plugin`, or any other withdrawn BTrace module from Maven Central.
- `btrace-maven-plugin` is absent: no module directory, build script, main/test/resource source,
  plugin descriptor, demo POM, settings inclusion, release task, or user-facing installation guide
  remains. Historical documentation, if retained, explicitly calls it deleted and non-installable.
- An external Gradle extension project records/resolves `io.btrace:btrace:<version>` as its annotation
  processor fallback from a validated BTrace/plugin version, never from the extension project's
  version; the monorepo path still uses `project(':btrace-core')`.
- Gradle fat-agent source-probe compilation resolves only the version-pinned
  `io.btrace:btrace:<btraceVersion>` engine artifact and has a regression test; no dynamic
  `btrace-client:+` lookup remains.
- Direct JBang examples use `io.btrace:btrace:<version>`.
- BTrace documentation gives no standalone Maven GAV for bundled extensions or other unpublished
  modules.  It gives an actionable Gradle local-package/project alternative and makes no equivalent
  unsupported Maven claim.
- `rg` finds no unqualified BTrace-owned unpublished coordinate in external-consumer code or docs.
  Intentional module paths and clearly historical architecture text may remain only with wording that
  they are internal/not Maven publications.
- The Gradle extension and fat-agent plugin IDs resolve through the Gradle Plugin Portal, including
  their marker artifacts; the Maven Central artifacts and Portal publications have their configured
  signing verification.
- The Gradle plugin implementation POM and both plugin-marker POMs carry exactly the release version
  supplied as `-PbtraceVersion`; the included build cannot publish without that verified version.
- The release workflow publishes/checks only `io.btrace:btrace` in Central and the two Gradle plugins
  in the Gradle Plugin Portal; it cannot publish or list `btrace-maven-plugin` as a release artifact.
- `btrace-dist` remains the only engine/library Maven publication; no individual internal module or
  BTrace extension is newly published as a side effect.

## Verification

Run from the repository root with the workspace-local Gradle cache, capturing output before reading it:

```sh
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -p btrace-gradle-plugin test > /tmp/issue-884-gradle-plugin.log 2>&1
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build > /tmp/issue-884-dist.log 2>&1
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:test > /tmp/issue-884-integration.log 2>&1
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew spotlessCheck > /tmp/issue-884-spotless.log 2>&1
```

Filter/read each log for `BUILD SUCCESSFUL`, `BUILD FAILED`, failures, and test summaries.  If the
restricted environment has address-selection failures, add the repository-prescribed
`JAVA_TOOL_OPTIONS` IPv4 options.

After the distribution build, locally publish the exact-release `btrace` artifact plus the Gradle
plugin implementation and both marker artifacts to the isolated test repository used by the
hermetic/end-to-end consumer gate. Inspect `btrace.jar` with `jar tf`/`unzip -p` and verify the
manifest entry points and `META-INF/btrace/` masked sections. Run the standalone build with a
repository limited to those test publications; it must resolve Gradle plugins through locally
published marker POMs using a normal `plugins {}` flow, never TestKit classpath injection or
`includeBuild`, then build the fat agent and exercise it against the real target JVM/protocol fixture.
The test must invoke the real client from the locally published masked artifact to attach/send a probe
to that target and assert the probe's observable result; agent startup alone is insufficient.
Inspect the Gradle implementation POM plus both marker POMs and verify their exact
`-PbtraceVersion` release value. Finally run the focused `rg` deletion inventory described above. The final
PR description must state the release version/property and repository used for the hermetic standalone
consumer check, the post-release Central and Gradle Plugin Portal smoke results, the exact signed
publication commands (including the included-build `publishPlugins` command), and the artifact
availability checks. A real release additionally requires authenticated Central/Portal verification
using the maintainer-held secrets; local verification cannot substitute for that external state.
