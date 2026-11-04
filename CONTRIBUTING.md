# Contributing to BTrace

Thanks for your interest in contributing! This guide covers local development, running tests, Gradle tips, and common troubleshooting.

## Local Development

- JDKs: the build uses Gradle toolchains and does not download them, so install these locally (Gradle finds SDKMAN and standard install locations, or list them in `org.gradle.java.installations.paths`):
  - JDK 8: compiles `btrace-agent` and provides `tools.jar` and `javadoc` (`common.gradle`).
  - JDK 11: the default compiler for the other modules.
  - JDK 24: compiles the ClassFile API backend (`btrace-agent` `java24` source set).
  - JDK 28: runs `:btrace-agent:classFileApiBackendTest`, which `check` depends on. Without it the task is skipped locally (and fails under `CI`), so those tests did not run.
- Wrapper: Use the bundled `./gradlew` wrapper. It will download the pinned Gradle version if needed.
- Local Gradle cache (optional but recommended; `.gradle-user` is gitignored):
  - macOS/Linux: `export GRADLE_USER_HOME="$PWD/.gradle-user"`
  - Windows (PowerShell): `$env:GRADLE_USER_HOME = "$PWD/.gradle-user"`

## Running Tests

- All unit tests (integration tests run only with `-Pintegration`, see below):
  ```sh
  ./gradlew --no-daemon test
  ```

- Per-module tests:
  - Runtime: `./gradlew :btrace-runtime:test`
  - Core (incl. extension framework): `./gradlew :btrace-core:test`
  - Extensions: `./gradlew :btrace-extensions:btrace-metrics:test` (similarly for the other `:btrace-extensions:*` modules)
  - Compiler: `./gradlew :btrace-compiler:test`
  - Instrumentation (lives in the agent module): `./gradlew :btrace-agent:test`

- Update instrumentor goldens when bytecode output changes:
  ```sh
  ./gradlew test -PupdateTestData
  ```

- Integration tests (spawn JVMs, exercise agent and extensions). They need `-Pintegration`: without it `:integration-tests:test` is skipped and the build still succeeds, having run nothing. The task builds the distribution it needs and writes the extension permission policy for the target JVMs itself.
  ```sh
  ./gradlew --no-daemon -Pintegration :integration-tests:test
  ```
  Check the test count in `integration-tests/build/test-results/test/` rather than the exit status.

## Gradle Tips

- Prefer IPv4 if your environment has unusual local IP settings (helps Gradle select a wildcard address):
  ```sh
  export GRADLE_OPTS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"
  ```
- Enable Gradle debug output for flakiness: add `--info` or `--debug`.
- Run a single test class/method:
  ```sh
  ./gradlew :btrace-core:test --tests io.btrace.extension.ExtensionBridgeImplPolicyTest
  ./gradlew :btrace-runtime:test --tests "*ExtensionIndyShimIndexTest.resolvesNoopShimFromIndex"
  ```

## Troubleshooting

- Gradle wrapper needs to download Gradle: ensure network is allowed once; subsequent runs use the local cache under `.gradle-user`.
- `Cannot find a Java installation ... matching: {languageVersion=N}`: install JDK N (see Local Development).
- Error: `Could not determine a usable wildcard IP for this machine`:
  - Set the IPv4 flags shown above or ensure local networking is available.
- Permission errors when Gradle writes outside the workspace:
  - Use a local Gradle cache via `GRADLE_USER_HOME` as shown above.

## Code Style & Scope

- Keep changes focused and minimal; align with existing code style.
- Update docs when changing user-visible behavior.
- Prefer clear separation of concerns and small helpers over inlined, complex logic.
- Avoid introducing new dependencies without discussion.

## Submitting a PR

1. Fork the repo and branch from `develop` (unless otherwise agreed).
2. Make your changes and run tests locally.
3. If instrumentor behavior changed, update goldens (`-PupdateTestData`) and include them in your commit.
4. Submit a PR with a concise description of the change, rationale, and any follow-ups.

Happy tracing!

