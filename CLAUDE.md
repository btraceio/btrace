# Claude Code Guide

Follow the repository-wide rules in [AGENTS.md](AGENTS.md). This file intentionally contains only Claude-specific orientation; `AGENTS.md` is the canonical operational contract.

## Common commands

Use a workspace-local Gradle cache when appropriate, and redirect Gradle output to a log before filtering and reading it (see [AGENTS.md](AGENTS.md#build-and-verification)).

```bash
# Distribution and all unit tests
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build

# Module, test class, or formatting check
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-instr:test
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-instr:test --tests '*CompilerTest'
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew spotlessCheck

# Integration tests: build the distribution first
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration :integration-tests:test

# Intentional instrumentation-bytecode changes only
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew test -PupdateTestData
```

## Where to look

- Script compiler and verifier: `btrace-compiler`
- Agent lifecycle: `btrace-agent`; bytecode weaving: `btrace-instr`
- Script API, runtime, and protocol: `btrace-core`, `btrace-runtime`
- CLI: `btrace-client`; packaging: `btrace-dist`
- Golden instrumentation data: `btrace-instr/src/test/resources/instrumentorTestData/`

## Detailed references

- [Documentation index](docs/README.md)
- [Masked JAR architecture](docs/architecture/MaskedJarArchitecture.md) — required reading for distribution/class-loading changes
- [Instrumentation architecture](docs/architecture/BTraceInstrAnalysis.md) and [backends](docs/architecture/InstrumentationBackends.md)
- [Protocol architecture](docs/architecture/Version2ProtocolArchitecture.md)
- [Extension development](docs/BTraceExtensionDevelopmentGuide.md)
- [Troubleshooting](docs/Troubleshooting.md)
