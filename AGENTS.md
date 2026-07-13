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
- Main code targets Java 8 and uses the Java 11 toolchain. Follow Spotless/Google Java Format.
- Unit tests live in `src/test/java` and use `*Test`; integration tests live in `integration-tests/src/test/java`.

## Build and verification

Run Gradle with a workspace-local cache in restricted environments:

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :module:test
```

Do not consume Gradle output directly. Redirect it to a file, filter it to relevant lines, then read that file. Use `spotlessCheck` for validation and `spotlessApply` only when formatting changes are intended. Build `:btrace-dist:build` before integration tests.

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

## Reference map

- [Contribution workflow](CONTRIBUTING.md)
- [Instrumentation backend selection](docs/architecture/InstrumentationBackends.md)
- [v2 wire protocol](docs/architecture/Version2ProtocolArchitecture.md)
- [Extension development](docs/BTraceExtensionDevelopmentGuide.md) and [interface rules](docs/ExtensionInterfaceRules.md)
- [Troubleshooting](docs/Troubleshooting.md)
