# Repository Guidelines

## Project Structure & Modules
- Root uses Gradle with multiple modules named `btrace-*`.
- Core code lives in module directories (for example, `btrace-core`, `btrace-agent`, `btrace-runtime`, `btrace-client`, `btrace-instr`).
- Distributions are built from `btrace-dist`.
- Integration tests live in `integration-tests`; benchmarks in `benchmarks/*`; docs in `docs/`.

## Architecture Overview
- btrace-agent: Attachable Java agent that installs a class transformer and manages script lifecycle (load/unload), output routing, and optional JFR hooks.
- btrace-compiler: Verifies and compiles BTrace scripts to bytecode.
- btrace-instr: ASM-based instrumentation and weaving utilities used by the agent/compiler.
- btrace-runtime: APIs exposed to scripts; provides safe helpers for printing, timers, and data collection.
- btrace-client: CLI/attach tooling that sends compiled scripts to the target JVM and streams results.
- extensions: API + implementations packaged as BTrace extensions (for example, statsd and metrics under `btrace-extensions/*`).
- Flow: client attaches → compiles/sends script → agent loads and instruments target classes → runtime emits events → client displays/exports.

### High-Level Flow
```
 +--------------+     attach/send     +-------------+     transform     +------------------+
 | btrace-client|  -----------------> | btrace-agent|  --------------> | instrumented JVM |
 +--------------+                      +-------------+                   +------------------+
        ^                                       |  ^                               |
        |     events/logs/stdout                |  | load/unload scripts            |
        |  <------------------------------------+  +-------------------------------+
        |                                                                           
        +--------- optional exporters via services (eg. statsd) -------------------->
```

### Modules (at a glance)
```
 btrace-client  ->  btrace-agent  ->  btrace-instr
                         |                 |
                         v                 v
                   btrace-runtime     extensions (e.g., statsd, utils, metrics)
                                 
 btrace-compiler  (validates/compiles scripts)
 btrace-dist      (packages binaries)
```

## Launch Modes
```
Launch-time:
  java -javaagent:$BTRACE_HOME/lib/btrace-agent.jar=script=MyTrace.java -jar app.jar
           |-> premain() installs transformer before classes load

Attach-time:
  btrace <PID> MyTrace.java
           |-> client attaches -> agentmain() -> instrument running JVM
```

## Troubleshooting
- Attach disabled: if JVM was started with `-XX:+DisableAttachMechanism`, remove it or relaunch without it.
- Permission errors: attach requires same OS user as target JVM; on Linux/macOS avoid sudo mixing; check container/JDK permissions.
- Toolchains: ensure `JAVA_HOME` and optional `TEST_JAVA_HOME` point to valid JDKs; for integration tests, build `btrace-dist` first so client/libs exist.

## Example Script
```java
package helloworld;
import static org.openjdk.btrace.core.BTraceUtils.*;
import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.types.AnyType;

@BTrace
public class MyTrace {
  @OnMethod(clazz="extra.HelloWorld", method="/.*/")
  public static void onAny(@ProbeMethodName String pmn) {
    println("entered: " + pmn);
  }
}
```
Run with: `btrace <PID> MyTrace.java` (see docs/BTraceTutorial.md for steps).

```java
// Args capture
package helloworld;
import static org.openjdk.btrace.core.BTraceUtils.*;
import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.types.AnyType;

@BTrace
public class ArgsTrace {
  @OnMethod(clazz="extra.HelloWorld", method="/call.*/")
  public static void onCall(@ProbeMethodName String pmn, AnyType[] args) {
    println("args for " + pmn);
    printArray(args);
  }
}
```

```java
// Return value and duration
package helloworld;
import static org.openjdk.btrace.core.BTraceUtils.*;
import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.types.AnyType;

@BTrace
public class ReturnTrace {
  @OnMethod(clazz="extra.HelloWorld", method="callC", location=@Location(Kind.RETURN))
  public static void onReturn(@Duration long dur, @Return AnyType ret) {
    println("callC ret=" + str(ret) + ", dur(ns)=" + dur);
  }
}
```

## Build, Test, and Development
! Do not consume the gradle task logs directly. !
! Write the output to a file, running through grep to include only relevant information and then read the log file. !

- Full build: `./gradlew build` — compiles all modules and runs unit tests.
- Distribution: `./gradlew :btrace-dist:build` — creates ZIP/TGZ/RPM/DEB and an exploded layout under `btrace-dist/build/resources/main`.
- Unit tests: `./gradlew test` — JUnit 5, runs per-module tests.
- Integration tests: first build dist, then `./gradlew -Pintegration test`.
  - Requires `JAVA_HOME` and typically `TEST_JAVA_HOME` (e.g., JDK 11). Example: `TEST_JAVA_HOME=$JAVA_11_HOME ./gradlew -Pintegration test`.
- Formatting: `./gradlew spotlessApply` (check with `spotlessCheck`).
- Coverage: `./gradlew jacocoTestReport` (CI publishes to Codecov).

## Coding Style & Naming
- Language: Java. Source/target set to 8; toolchains compile with JDK 11.
- Format: Google Java Format via Spotless. Import order enforced; unused imports removed.
- Packages under `org.openjdk.btrace.*`.
- Module names follow `btrace-<component>` (e.g., `btrace-extensions:btrace-utils`).

## Testing Guidelines
- Framework: JUnit Jupiter (JUnit 5).
- Unit tests reside under `src/test/java`; name classes with `*Test`.
- Integration tests in `integration-tests/src/test/java`; BTrace scripts under `integration-tests/src/test/btrace`.
- For integration runs, ensure `btrace-dist/build/resources/main/v<version>/libs/btrace-client.jar` exists (created by the dist build).

## Commit & Pull Request Guidelines
- Commit style: Conventional Commits (e.g., `feat(core): add probe`, `fix(instr): handle null arg`).
- PRs must be from signers of the Oracle Contributor Agreement (OCA) — see README.
- PR checklist:
  - Clear description and rationale; link related issues.
  - Tests updated/added; CI green across unit and integration suites.
  - Formatting passes (`spotlessCheck`); no unrelated changes.
  - For behavior changes, include before/after notes or relevant logs.

## Tips & Environment
- Useful env vars: `JAVA_HOME`, `TEST_JAVA_HOME`, `BTRACE_TEST_DEBUG=true` (verbose integration tests), optional `BTRACE_HOME` when using the exploded dist.
- Example exploded dist path: `btrace-dist/build/resources/main/v2.2.6/`.

### Restricted/CI Environments
- Prefer a workspace-local Gradle cache to avoid permission issues: set `GRADLE_USER_HOME=$(pwd)/.gradle-user`.
- If network interfaces are restricted, force IPv4 to avoid wildcard IP detection errors: set `JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"`.
- Example: `GRADLE_USER_HOME=$(pwd)/.gradle-user JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false" ./gradlew :btrace-dist:buildZip -x test`

## Hard rules
- Never commit changes unless they are fully tested or you are explicitly asked to commit
- Do not use FQNs directly! Always import types and use simple type names in the code!
