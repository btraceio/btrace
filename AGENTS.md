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

## Distribution Architecture: Masked JAR

BTrace uses a **single masked JAR** (`btrace.jar`) as its distribution artifact. This JAR contains:

### Structure
```
btrace.jar
├── META-INF/
│   ├── MANIFEST.MF (Main-Class, Premain-Class, Agent-Class → org.openjdk.btrace.boot.Loader)
│   ├── btrace/
│   │   ├── agent/*.classdata    (agent classes - loaded in agent mode)
│   │   ├── client/*.classdata   (client classes - loaded in client mode)
│   │   └── shared/*.classdata   (shared classes - loaded in both modes)
├── org/openjdk/btrace/boot/     (bootstrap classes - visible to JVM)
└── org/openjdk/btrace/core/     (core/runtime classes from bootstrap module)
```

### Class Loading Strategy

1. **Bootstrap Classes** (`.class` files in root):
   - Loaded by bootstrap classloader
   - Visible to JVM and all code
   - Includes: Loader, MaskedClassLoader, MaskedJarUtils
   - These classes initialize the masked jar system

2. **Agent Classes** (`.classdata` in `META-INF/btrace/agent/`):
   - Loaded via MaskedClassLoader in agent mode
   - Isolated from application classes
   - Includes: btrace-agent, btrace-instr, btrace-runtime, relocated jctools

3. **Client Classes** (`.classdata` in `META-INF/btrace/client/`):
   - Loaded via MaskedClassLoader in client mode
   - Includes: btrace-client, btrace-compiler, lanterna UI

4. **Shared Classes** (`.classdata` in `META-INF/btrace/shared/`):
   - Loaded in both agent and client modes
   - Includes: communication protocol, annotations, ASM core
   - Critical for agent-client communication

### Why Masked JAR?

- **Single Source of Truth**: One JAR for all use cases (agent, client, standalone)
- **Bootstrap Isolation**: Agent/client classes hidden from JVM, preventing conflicts
- **No Embedded JARs**: Eliminates nested JAR extraction overhead
- **Simplified Build**: Removed redundant uber jar - masked jar handles everything

### Build Process

The masked JAR is built in `btrace-dist/build.gradle`:
1. `allClassesShadow` - Creates intermediate shadow jar with all dependencies and relocations
2. `prepareAgentClassdata` - Extracts agent classes, renames `.class` → `.classdata`
3. `prepareClientClassdata` - Extracts client classes, renames `.class` → `.classdata`
4. `prepareSharedClassdata` - Extracts shared classes, renames `.class` → `.classdata`
5. `btraceJar` - Combines bootstrap classes (as `.class`) + masked classes (as `.classdata`)

### Debugging Tips

- **ClassNotFoundException in agent mode**: Check if class is in `META-INF/btrace/agent/` (or shared if needed)
- **ClassNotFoundException in client mode**: Check if class is in `META-INF/btrace/client/` (or shared if needed)
- **NoClassDefFoundError between modes**: Class may need to be in shared section
- **Inspect masked JAR**: `unzip -l btrace.jar | grep -E "(\.class|\.classdata)"`
- **Check manifest**: `unzip -p btrace.jar META-INF/MANIFEST.MF`

## Launch Modes
```
Launch-time (agent mode):
  java -javaagent:$BTRACE_HOME/libs/btrace.jar=script=MyTrace.java -jar app.jar
           |-> Loader.premain() -> loads agent classes from .classdata -> installs transformer

Attach-time (client mode):
  btrace <PID> MyTrace.java
           |-> Loader as Main-Class -> loads client classes from .classdata -> attaches to target JVM
           |-> Target JVM: Loader.agentmain() -> loads agent classes from .classdata -> instruments

Standalone (client mode):
  java -jar btrace.jar <args>
           |-> Same as attach-time, Loader delegates to client
```

## Troubleshooting
- Attach disabled: if JVM was started with `-XX:+DisableAttachMechanism`, remove it or relaunch without it.
- Permission errors: attach requires same OS user as target JVM; on Linux/macOS avoid sudo mixing; check container/JDK permissions.
- Toolchains: ensure `JAVA_HOME` and optional `TEST_JAVA_HOME` point to valid JDKs; for integration tests, build `btrace-dist` first so client/libs exist.

### Masked JAR Troubleshooting
- **ClassNotFoundException with .classdata**: MaskedClassLoader can't find class in masked sections. Check:
  1. Is the class in the correct section? (agent/client/shared)
  2. Was the class relocated? Check package name matches relocated path
  3. Did the build complete successfully? Rebuild with `./gradlew clean :btrace-dist:btraceJar`
- **Shared classes**: If a class is used by BOTH agent and client (e.g., comm protocol, annotations), it MUST be in the shared section
- **Bootstrap vs Masked**: Bootstrap classes (.class) are visible everywhere; masked classes (.classdata) are isolated per-mode
- **Build order matters**: `allClassesShadow` must complete before prepare*Classdata tasks run

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
- For integration runs, ensure `btrace-dist/build/resources/main/v<version>/libs/btrace.jar` exists (created by the dist build).
- The masked JAR is used for all integration tests - both agent and client modes use the same artifact.

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

## Common Patterns & Lessons Learned

### Adding New Classes
1. **Determine the section**: Is the class used by agent, client, or both?
   - Agent only → prepareAgentClassdata include pattern
   - Client only → prepareClientClassdata include pattern
   - Both → prepareSharedClassdata include pattern
2. **Update build.gradle**: Add include pattern in the appropriate task
3. **Rebuild and test**: `./gradlew clean :btrace-dist:btraceJar && ./gradlew -Pintegration test`

### Dependency Relocation
- All third-party dependencies are relocated to `org.openjdk.btrace.libs.*`
- Relocations happen in `allClassesShadow` task using Shadow plugin
- Common relocations: ASM, SLF4J, JCTools
- After relocation, classes are extracted and masked in prepare*Classdata tasks

### Build Simplification Wins
- **Before**: Separate agent.jar, client.jar, boot.jar, uber.jar (4 artifacts)
- **After**: Single btrace.jar with masked sections (1 artifact)
- **Result**: Simpler build, smaller distribution, easier maintenance

### ClassLoader Isolation
- Bootstrap classes can see everything (including masked sections via MaskedClassLoader)
- Application classes cannot see masked sections (isolation prevents conflicts)
- Masked classes in agent mode cannot see masked classes in client mode (intentional isolation)
- Shared section solves cross-mode visibility when needed (e.g., command serialization)

## Hard rules
- Never commit changes unless they are fully tested or you are explicitly asked to commit
- Do not use FQNs directly! Always import types and use simple type names in the code!
- When adding classes to masked jar, always consider: agent-only, client-only, or shared?
- Rebuild the distribution after any changes to masked jar structure: `./gradlew clean :btrace-dist:btraceJar`
