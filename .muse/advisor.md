---
name: btrace-advisor
role: repo_advisor
scope: [btraceio/btrace]
confidence: high
concertato_only: true
watched_paths:
  - AGENTS.md
  - CONTRIBUTING.md
  - README.md
  - build.gradle
  - settings.gradle
  - .github/workflows/continuous.yml
  - .github/workflows/v2-protocol-tests.yml
  - .github/workflows/release.yml
  - .github/workflows/codeql-analysis.yml
---

# BTrace Repo Advisor

Operational facts for `btraceio/btrace`. No reviewer opinions — only repo-specific truths for build, test, CI, and layout.

## Branch Model

- `develop` is the sole integration branch — ALL pull requests target `develop`.
- `master` does not exist on the remote.
- Release tags are cut from `develop` after a stabilization window.
- Branch from `develop` for features/fixes (see `CONTRIBUTING.md`).

## Build System

- Build tool: Gradle with the bundled wrapper (`./gradlew`). Pinned Gradle version downloaded on first run.
- Root toolchain: JDK 11 compiles the build; `btrace-core` and `btrace-runtime` sources target Java 8.
- Included build: `btrace-gradle-plugin` is consumed via `pluginManagement { includeBuild(...) }`.
- Subprojects: auto-discovered by root `build.gradle`/`settings.gradle` matching `btrace-*` directories that contain `build.gradle`. Skipped: `btrace-gradle-plugin` (handled above), and legacy `btrace-services`, `btrace-services-api`, `btrace-statsd`.
- Dependency versions live in `settings.gradle` `dependencyResolutionManagement.versionCatalogs.libs`: ASM 9.9.1, JUnit 5.11.4, SLF4J 1.7.36, JCTools 4.0.6, JMH 1.37, testcontainers 2.0.4.
- Do **not** consume Gradle task output directly — write logs to a file, filter with `grep`, then read the filtered file (AGENTS.md directive).

### Commands

```bash
./gradlew build                              # compile + unit tests for all modules
./gradlew :btrace-dist:build                 # build distribution (ZIP/TGZ/RPM/DEB + exploded layout)
./gradlew :btrace-dist:btraceJar             # just rebuild the masked jar
./gradlew :<module>:build -x test            # faster module build
./gradlew :btrace-instr:test                 # instrumentation-only tests
./gradlew :btrace-instr:test -PupdateTestData   # regenerate instrumentor goldens
./gradlew :integration-tests:test -Pintegration # requires built dist first
./gradlew spotlessCheck | spotlessApply      # Google Java Format (Spotless)
./gradlew jacocoTestReport                   # coverage (CI uploads to Codecov)
```

### Environment variables

- `JAVA_HOME` — build JDK (typically 21 locally; CI build row is 11).
- `TEST_JAVA_HOME` — JDK used to **run** tests; CI exports this per matrix row. For integration tests you usually want `TEST_JAVA_HOME=$JAVA_11_HOME`. Locally, setting it is the way to exercise Java 8 / 17 / 21 / 25 / 26-ea runtime paths.
- `BTRACE_TEST_DEBUG=true` — verbose integration-test output.
- `BTRACE_HOME` — optional; used when running an exploded dist (e.g. `btrace-dist/build/resources/main/v<version>/`).
- `BTRACE_PERMS` / `-Dbtrace.permissions=<path>` — privileged-extensions policy for integration tests (see `CONTRIBUTING.md`).
- `GRADLE_USER_HOME=$(pwd)/.gradle-user` — recommended in restricted/CI environments to avoid permission issues with the shared cache.
- `JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"` — force IPv4 when wildcard-IP detection fails in Gradle.
- Sonatype publishing: `SONATYPE_USER` / `SONATYPE_USERNAME`, `SONATYPE_PASSWORD`, `GPG_SIGNING_KEY`, `GPG_SIGNING_PWD` (release workflow only).

## Test Infrastructure

- Framework: **JUnit 5 (Jupiter)** for both unit and integration tests.
- Unit tests: under each module's `src/test/java`, naming convention `*Test`.
- Integration tests: `integration-tests/src/test/java` (scripts under `integration-tests/src/test/btrace`) — spawn real JVMs, exercise agent + extensions, require Docker (`DOCKER_HOST=unix:///var/run/docker.sock`) and `btrace-dist/build/resources/main/v<version>/libs/btrace.jar` on disk.
- Golden instrumentor fixtures: `btrace-instr/src/test/resources/instrumentorTestData/dynamic/` — regenerate with `-PupdateTestData` after any intentional bytecode change; commit the updated goldens.
- V2 protocol tests: `btrace-core` test class globs `org.openjdk.btrace.core.comm.v2.*`, `Protocol*`, `WireProtocol*`. Dedicated workflow runs on label `test:v2-protocol`, weekly schedule, or manual dispatch.
- Running a single test: `./gradlew :btrace-instr:test --tests "org.openjdk.btrace.runtime.ExtensionIndyShimIndexTest.resolvesNoopShimFromIndex"`.
- Per-module aliases from `CONTRIBUTING.md`: `:btrace-runtime:test`, `:btrace-extension:test`, `:btrace-compiler:test`, `:btrace-instr:test`.
- CI JDK matrix (build: JDK 11): tests run on `8.0.482-tem, 11.0.30-tem, 17.0.18-tem, 21.0.10-tem, 25.0.2-tem, 26.ea.35-open` — changes must compile and pass across all of these.

## Distribution — Masked JAR

- Single artifact: `btrace.jar` with bootstrap classes as `.class` and mode-scoped classes as `.classdata` under `META-INF/btrace/{agent,client,shared}/`.
- Loader: `org.openjdk.btrace.boot.Loader` is `Main-Class`, `Premain-Class`, and `Agent-Class`. `MaskedClassLoader` resolves `.classdata`.
- Every new class **must** be categorized: agent-only, client-only, or shared. Categorization is wired into `btrace-dist/build.gradle`'s `prepareAgentClassdata` / `prepareClientClassdata` / `prepareSharedClassdata` include patterns.
- Rebuild masked jar after any restructuring: `./gradlew clean :btrace-dist:btraceJar`.
- Inspect: `unzip -l btrace.jar | grep -E '(\.class|\.classdata)'`; `unzip -p btrace.jar META-INF/MANIFEST.MF`.
- Build-order dependency: `allClassesShadow` must complete before `prepare*Classdata` tasks run; those must complete before `btraceJar` assembles.

## Known Sharp Edges

- **Javadoc API version trap**: `btrace-runtime/src/main/java/org/openjdk/btrace/runtime/auxiliary/` is compiled at Java 8 source/target. Javadoc linking to JDK 15+ API (e.g. hidden-class lookups) breaks `:btrace-runtime:javadoc` with `reference not found`. Keep javadoc references on pre-9 API or use prose.
- **Bootstrap classloader constraints**: `btrace-boot.jar` lives on the bootstrap classpath. Any class referenced from an `INVOKEDYNAMIC` bootstrap method (e.g. `IndyDispatcher`) must be bootstrap-loadable. Pulling in application-classloader-only types breaks probe dispatch.
- **Hidden-class vs `defineClass` paths**: probe script classes are defined in the bootstrap CL via `Unsafe.defineClass` with `mustBeBootstrap=true` when `isTransforming()`. JDK 15+ has a separate hidden-class codepath — multi-version runtime (`src/main/java9/`, `src/main/java11/`, `src/main/java15/`) keeps these isolated; compile each at its release level.
- **Verifier contract**: probe handler methods are always `public static void`. `MethodHandles.publicLookup().findStatic(...)` is sufficient — do not switch to `privateLookupIn` "just to be safe," it breaks bootstrap loader expectations.
- **FQN rule**: never inline fully qualified names in source. Always import and use simple type names (AGENTS.md hard rule).
- **Masked-JAR class invisibility**: `ClassNotFoundException` on `.classdata` means the class is either missing from the correct mode section or was relocated but not registered. `shared/` is required whenever agent ↔ client serialize the same type (comm protocol, annotations, ASM core).
- **`BTraceRuntimeImpl_8` diverges** from 9/11 impls on the `defineClass` path — Java 8 CI row is not redundant; always verify against it via `TEST_JAVA_HOME=$JAVA_8_HOME` locally before merging runtime changes.
- **Spotless import order**: `java`, `javax`, `org.openjdk.btrace`, everything else, static `org.openjdk.btrace`, remaining static. Reformat with `./gradlew spotlessApply` if `spotlessCheck` fails.
- **License header**: Spotless injects `/* (C) $YEAR */` on every Java file; do not hand-write headers.

## Known Recurring CI Failure Modes

- **`:initializeSonatypeStagingRepository FAILED — Failed to find staging profile for package group`** on the `publish` job of BTrace CI/CD when `develop` merges. Tied to the OSSRH → Central Portal migration (`nexusPublishing` in root `build.gradle` uses `https://ossrh-staging-api.central.sonatype.com/`). Publish is guarded by `hasSonatypeCredentials` + non-snapshot version; failures typically reflect missing/rotated Central Portal user-token credentials or an unregistered `io.github.*` package group on the new portal.
- **`btrace-runtime:javadoc FAILED — reference not found`** — see Javadoc API version trap above. Caught by the build job (not the test matrix).
- **CodeQL `configuration error`** — CodeQL workflow trips on feature branches with unusual Java toolchain setups; typically a Java setup/classpath mismatch in `actions/codeql-action@v*`, not a real finding.
- **Integration test branch-scoped failures** (e.g. `ManifestLibsTests > Dynamic attach with manifest-libs enabled`) — matrix-wide failure usually means the feature branch changed something in extension-loading or masked-jar layout that works for default attach but not dynamic attach. Not a flaky test — debug the change, don't retry.

## PR and CI Rules

- **Commit style**: Conventional Commits (`feat(core):`, `fix(instr):`, `refactor(runtime):`, `test:`, `chore:`). Scope matches module short name.
- **OCA required**: PRs are only accepted from signers of the Oracle Contributor Agreement.
- **PR checklist** (from AGENTS.md + CONTRIBUTING.md):
  1. Target branch is `develop` (never `master`).
  2. `./gradlew spotlessCheck` clean.
  3. Unit tests updated/added.
  4. `-PupdateTestData` goldens regenerated **and committed** if instrumentation changed.
  5. Integration tests pass locally if agent/dist behavior changed.
  6. For behavior changes, include before/after notes or logs.
  7. No unrelated changes in the diff.
- **CI workflows**:
  - `BTrace CI/CD` (`continuous.yml`) — build + matrix test + (on `develop`) publish + cleanup. Triggered on push/PR against `develop` and `workflow_dispatch`.
  - `V2 Protocol Tests` (`v2-protocol-tests.yml`) — opt-in via PR label `test:v2-protocol`, weekly on Sundays 02:00 UTC, or manual. Includes unit, negotiation, edge-case, JMH (label `[benchmark]` or manual), and V1↔V2 compatibility matrix.
  - `CodeQL` (`codeql-analysis.yml`) — static analysis on push/PR.
  - `release.yml` — full release pipeline (manual dispatch).
  - `update-jdk-versions.yml` — periodic JDK matrix refresh.
  - `stale.yml` — issue/PR staleness sweeper.
- **Optional labels** with CI meaning: `test:v2-protocol` (run the V2 suite), `[benchmark]` in commit message (run JMH quick benchmarks).

## File Layout

| Path | Purpose |
|---|---|
| `btrace-core` | Annotations, wire protocol (v1 + v2), shared types — Java 8 compatible. |
| `btrace-compiler` | Script compilation + safety verification (`Verifier`). |
| `btrace-instr` | ASM-based bytecode instrumentation; probe factory; `HandlerRepositoryImpl`; golden-file tests. |
| `btrace-runtime` | Multi-version runtime impls (base, `java9/`, `java11/`, `java15/`); `IndyDispatcher`; `BTraceRuntimeImpl_*`. Must stay loadable from the bootstrap classloader. |
| `btrace-agent` | Java agent entry point; `RemoteClient`, `FileClient`; JFR hooks. |
| `btrace-boot` | Bootstrap classes (visible to JVM), including `Loader`, `MaskedClassLoader`, `MaskedJarUtils`. |
| `btrace-client` | CLI client — attach, send script, stream output. |
| `btrace-compiler` | Scripts → bytecode. |
| `btrace-dist` | Distribution packaging (masked jar, RPM/DEB/ZIP/TGZ). Hosts `prepareAgentClassdata` / `prepareClientClassdata` / `prepareSharedClassdata` / `btraceJar`. |
| `btrace-extension` / `btrace-extensions/*` | Extension API + bundled implementations (`btrace-metrics`, `btrace-statsd`, `btrace-utils`) and examples. Explicit includes in `settings.gradle`. |
| `btrace-gradle-plugin` | In-repo Gradle plugin for extension authorship — consumed via `includeBuild` in `pluginManagement`, not as a subproject. |
| `integration-tests` | Docker-based end-to-end tests. |
| `benchmarks/agent-benchmark`, `benchmarks/runtime-benchmarks` | JMH benchmarks. |
| `docs/` | User docs (tutorials, oneliner guide, extension dev guide). |
| `btrace-instr/src/test/resources/instrumentorTestData/dynamic/` | Instrumentor golden files — regenerate via `-PupdateTestData`. |
| `.github/workflows/` | CI config; `README.md` in the same directory documents the workflows. |
| `.muse/advisor.md` | This file. |
