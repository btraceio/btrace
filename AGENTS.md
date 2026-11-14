# Repository Guide for Coding Agents

## Start here

BTrace is a Java tracing tool: the client compiles and sends a script, the agent instruments the target JVM, and the runtime emits results. The root project is a multi-module Gradle build.

- `btrace-agent` — attachable agent, script lifecycle, and bytecode instrumentation/weaving
- `btrace-compiler` — script verification and compilation
- `btrace-runtime` / `btrace-core` — script APIs, runtime support, and protocol
- `btrace-client` — CLI and attachment client
- `btrace-dist` — distribution assembly; `integration-tests` — end-to-end tests
- `btrace-extensions/*` — extension API and implementations

For the developer command reference and code-navigation pointers, see [CLAUDE.md](CLAUDE.md). For user and contributor documentation, start at [docs/README.md](docs/README.md).

## Non-negotiable rules

- Do not commit unless the changes are fully tested or the user explicitly requests a commit.
- Preserve unrelated working-tree changes.
- In Java code, import types and use simple names; do not introduce fully qualified type names in source.
- Main code targets Java 8. The build needs JDK 8, 11, 24 and 28 toolchains installed locally (see [CONTRIBUTING.md](CONTRIBUTING.md#local-development)); without JDK 28, `classFileApiBackendTest` is skipped and has not verified anything. Follow Spotless/Google Java Format.
- Unit tests live in `src/test/java` and use `*Test`; integration tests live in `integration-tests/src/test/java`.
- Changes to user-visible behavior that crosses modules or process boundaries must include end-to-end functional coverage in `integration-tests`; unit and component tests are required where useful but are not a substitute for exercising the real client, agent, target JVM, and protocol interaction.
- Confirm that a new test or build gate **fails when it should**, not only that it passes. Run it against the unfixed code, or against input it must reject, and check the failure is the expected one. A check that cannot fail reports success regardless of what the code does, and reads as coverage while providing none. Where a revert is used to produce the failure, revert only the code under test: reverting too much fails for an unrelated reason and proves nothing about the behavior being asserted.

## Build and verification

Run Gradle with a workspace-local cache in restricted environments:

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :<module>:test
```

Do not consume Gradle output directly. Redirect it to a file, filter it to relevant lines, then read that file. Use `spotlessCheck` for validation and `spotlessApply` only when formatting changes are intended. Integration tests run only with `-Pintegration` (`./gradlew -Pintegration :integration-tests:test`); without it the task is skipped and the build still succeeds. Read the test counts in the results, not the exit status.

If a restricted network environment causes address-selection failures, add:

```bash
JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"
```

## Distribution changes

`btrace.jar` is a masked single-JAR distribution. Classes must be assigned to bootstrap, agent, client, or shared sections deliberately. Any masked-JAR structure change requires:

```bash
./gradlew clean :btrace-dist:btraceJar
```

Read [Masked JAR Architecture](docs/architecture/MaskedJarArchitecture.md) before modifying its class layout or loader behavior.

## Documentation placement

- User-facing and contributor documentation belongs in `docs/`; keep [docs/README.md](docs/README.md) current when adding a guide.
- Plans and session notes belong in `internal/plans/` (or `internal/superpowers/plans/`).
- Design/requirement specs belong in `internal/specs/` (or `internal/superpowers/specs/`); libretto/muse files belong in `internal/libretti/`.
- Never create or write to a singular `doc/` directory, or add plans, agent notes, or internal material below `docs/`.

## Skills

Task procedures live as skills in `.agents/skills/<name>/SKILL.md` (read by Codex, Pi and other Agent Skills harnesses). Each one is linked into `.claude/skills/<name>` as a relative symlink for Claude Code; never add a skill only under `.claude/skills/`.

## Reference map

- [Contribution workflow](CONTRIBUTING.md)
- [Instrumentation backend selection](docs/architecture/InstrumentationBackends.md)
- [v2 wire protocol](docs/architecture/Version2ProtocolArchitecture.md)
- [Extension development](docs/BTraceExtensionDevelopmentGuide.md) and [interface rules](docs/ExtensionInterfaceRules.md)
- [Troubleshooting](docs/Troubleshooting.md)
