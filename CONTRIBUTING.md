# Contributing to BTrace

Thanks for your interest in contributing! This guide covers local development, running tests, Gradle tips, and common troubleshooting.

Note: Pull requests can only be accepted from signers of the Oracle Contributor Agreement (OCA). See the project README for details.

## Local Development

- JDK: Use a reasonably recent JDK (11+ recommended). The project targets a broad range but tests run comfortably on 11/17.
- Wrapper: Use the bundled `./gradlew` wrapper. It will download the pinned Gradle version if needed.
- Local Gradle cache (optional but recommended):
  - macOS/Linux: `export GRADLE_USER_HOME="$PWD/.gradle-home"`
  - Windows (PowerShell): `$env:GRADLE_USER_HOME = "$PWD/.gradle-home"`

## Running Tests

- All unit tests (skip integration tests):
  ```sh
  ./gradlew --no-daemon test -x integration-tests:test
  ```

- Per-module tests:
  - Runtime: `./gradlew :btrace-runtime:test`
  - Extension: `./gradlew :btrace-extension:test`
  - Compiler: `./gradlew :btrace-compiler:test`
  - Instr: `./gradlew :btrace-instr:test`

- Update instrumentor goldens when bytecode output changes:
  ```sh
  ./gradlew test -PupdateTestData
  ```

- Integration tests (spawn JVMs, exercise agent and extensions):
  ```sh
  ./gradlew --no-daemon integration-tests:test
  ```
  - If tests fail due to denied privileged extensions, pass a policy file to the tested JVMs:
    - Create `permissions.properties`:
      ```properties
      allowPrivileged=true
      allowExtensions=btrace-metrics,btrace-utils
      ```
    - Export path: `export BTRACE_PERMS=$PWD/permissions.properties`
    - Run Gradle with: `-Dbtrace.permissions=$BTRACE_PERMS`

## Gradle Tips

- Prefer IPv4 if your environment has unusual local IP settings (helps Gradle select a wildcard address):
  ```sh
  export GRADLE_OPTS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"
  ```
- Enable Gradle debug output for flakiness: add `--info` or `--debug`.
- Run a single test class/method:
  ```sh
  ./gradlew :btrace-extension:test --tests org.openjdk.btrace.extension.ExtensionBridgeImplPolicyTest
  ./gradlew :btrace-runtime:test --tests "*ExtensionIndyShimIndexTest.resolvesNoopShimFromIndex"
  ```

## Troubleshooting

- Gradle wrapper needs to download Gradle: ensure network is allowed once; subsequent runs use the local cache under `.gradle-home`.
- Error: `Could not determine a usable wildcard IP for this machine`:
  - Set the IPv4 flags shown above or ensure local networking is available.
- Permission errors when Gradle writes outside the workspace:
  - Use a local Gradle cache via `GRADLE_USER_HOME` as shown above.
- Integration tests failing with permissions denied:
  - Provide a policy file and pass it via `-Dbtrace.permissions=/path/to/permissions.properties`.

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

