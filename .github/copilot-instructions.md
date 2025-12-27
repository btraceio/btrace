# GitHub Copilot Instructions for BTrace

## About BTrace
BTrace is a safe, dynamic tracing tool for the Java platform. It dynamically instruments running Java applications to inject tracing code at runtime using bytecode instrumentation.

## Project Structure
- **Gradle multi-module project** with modules named `btrace-*`
- **Core modules**: `btrace-core`, `btrace-agent`, `btrace-runtime`, `btrace-client`, `btrace-instr`
- **Build artifacts**: `btrace-dist` for distributions
- **Tests**: `integration-tests/` for integration tests, `src/test/java` in modules for unit tests
- **Documentation**: `docs/` directory

## Architecture Overview
- **btrace-agent**: Attachable Java agent with class transformer, manages script lifecycle
- **btrace-compiler**: Verifies and compiles BTrace scripts to bytecode
- **btrace-instr**: ASM-based instrumentation and weaving utilities
- **btrace-runtime**: APIs for scripts (printing, timers, data collection)
- **btrace-client**: CLI/attach tooling for sending scripts to target JVM
- **services**: SPI for pluggable exporters (e.g., statsd)

## Development Guidelines

### Language & Versions
- **Language**: Java
- **Source/Target**: Java 8
- **Build toolchain**: JDK 11
- **Test framework**: JUnit Jupiter (JUnit 5)

### Code Style
- **Format**: Google Java Format enforced via Spotless
- **Packages**: All under `org.openjdk.btrace.*`
- **Naming**: Module names follow `btrace-<component>` pattern
- **Imports**: Order enforced; remove unused imports
- **Comments**: Only add if they match existing style or explain complex logic

### Building & Testing
```bash
# Full build with unit tests
./gradlew build

# Build distribution only
./gradlew :btrace-dist:build

# Run unit tests
./gradlew test

# Run integration tests (requires dist build first)
./gradlew -Pintegration test

# Format code
./gradlew spotlessApply

# Check formatting
./gradlew spotlessCheck
```

### Important Environment Variables
- `JAVA_HOME`: Required for builds
- `TEST_JAVA_HOME`: Required for integration tests (typically JDK 11)
- `BTRACE_TEST_DEBUG=true`: Enable verbose integration test output
- `BTRACE_HOME`: Optional, points to exploded dist

### Testing Best Practices
- Unit tests: `src/test/java` with `*Test` suffix
- Integration tests: `integration-tests/src/test/java`
- BTrace scripts: `integration-tests/src/test/btrace`
- Always run relevant tests after making changes
- Update golden files when changing instrumentor: `./gradlew test -PupdateTestData`

### Commit & PR Guidelines
- **Commit style**: Conventional Commits (e.g., `feat(core): add probe`, `fix(instr): handle null arg`)
- **Clear descriptions**: Link related issues
- **Tests required**: Update/add tests; ensure CI passes
- **Formatting**: Must pass `spotlessCheck`
- **No unrelated changes**: Keep changes focused and minimal

## Troubleshooting

### Build Issues
- **Attach disabled**: Remove `-XX:+DisableAttachMechanism` from target JVM
- **Permission errors**: Attach requires same OS user as target JVM
- **Toolchain issues**: Verify `JAVA_HOME` and `TEST_JAVA_HOME` point to valid JDKs

### Restricted Environments
```bash
# Use workspace-local Gradle cache
GRADLE_USER_HOME=$(pwd)/.gradle-user

# Force IPv4 to avoid network interface issues
JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"
```

## Code Generation Tips
- **Prefer simplicity**: Simple, performant solutions over complex designs
- **Use existing patterns**: Follow patterns from similar code in the repository
- **Minimal changes**: Make the smallest possible changes to achieve the goal
- **Reuse libraries**: Use ASM for bytecode, JCTools for concurrency, existing BTrace APIs
- **No temporary files in repo**: Use `/tmp` for scratch work
- **Security**: Never commit secrets; avoid introducing vulnerabilities

## Example BTrace Script Pattern
```java
package example;
import static org.openjdk.btrace.core.BTraceUtils.*;
import org.openjdk.btrace.core.annotations.*;

@BTrace
public class ExampleTrace {
  @OnMethod(clazz="com.example.Target", method="methodName")
  public static void onMethod(@ProbeMethodName String method) {
    println("Called: " + method);
  }
}
```

## Key Dependencies
- **ASM**: Bytecode manipulation
- **JCTools**: High-performance concurrent data structures
- **hppcrt**: Optimized collections
- **JUnit Jupiter**: Testing framework

## Additional Resources
- Full guidelines: See `AGENTS.md` in repository root
- Tutorial: `docs/BTraceTutorial.md`
- Binary releases: https://github.com/btraceio/btrace/releases
