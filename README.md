# BTrace

**Safe, dynamic tracing for Java applications**

[![CI](https://github.com/btraceio/btrace/workflows/BTrace%20CI%2FCD/badge.svg?branch=develop)](https://github.com/btraceio/btrace/actions)
[![Release](https://img.shields.io/github/v/release/btraceio/btrace?sort=semver)](https://github.com/btraceio/btrace/releases/latest)
[![codecov](https://codecov.io/github/btraceio/btrace/coverage.svg?branch=develop)](https://codecov.io/github/btraceio/btrace?branch=develop)

BTrace dynamically instruments running Java applications to inject tracing code at runtime. No restarts. No recompilation. Production-safe.

> **Quick links:** [Quick Reference](docs/QuickReference.md) · [Step-by-Step Tutorial](docs/GettingStarted.md)

---

## Why BTrace?

- **Zero downtime** - Attach to running JVMs without restart
- **Production safe** - Verified scripts can't crash your application
- **Flexible probes** - Method entry/exit, timings, field access, allocations
- **Low overhead** - Bytecode injection with minimal performance impact

### Supported Java Versions

BTrace 3.0 runs on Java 8–25+. Running BTrace against a JVM older than Java 17 is deprecated: it continues to work throughout 3.x but emits a deprecation warning. Support for Java < 17 will be removed in the next major release (4.0). See the [migration guide](docs/Migration-2.x-to-3.0.md) for details on upgrading from BTrace 2.x.

---

## Get Started in 30 Seconds

```sh
# Install via JBang (easiest)
curl -Ls https://sh.jbang.dev | bash -s - app setup

# Add the BTrace JBang catalog (one time)
jbang catalog add --name btraceio https://raw.githubusercontent.com/btraceio/jbang-catalog/main/jbang-catalog.json

# Trace slow methods in your running app
jbang btrace@btraceio -n 'com.myapp.*::* @return if duration>100ms { print method, duration }' $(pgrep -f myapp)
```

---

## Trace Anything

**Method timing:**
```sh
btrace -n 'java.sql.Statement::execute* @return { print method, duration }' <PID>
```

**Exception tracking:**
```sh
btrace -n 'java.lang.Exception::<init> @return { print self, stack(5) }' <PID>
```

**Custom probes:**
```java
@BTrace public class Trace {
    @OnMethod(clazz = "com.example.OrderService", method = "checkout")
    public static void onCheckout(@Self Object self, @Duration long ns) {
        println("checkout: " + str(ns / 1_000_000) + "ms");
    }
}
```

See the [Oneliner Guide](docs/OnelinerGuide.md) for complete syntax.

---

## Install

```sh
# JBang (recommended - zero installation)
jbang catalog add --name btraceio https://raw.githubusercontent.com/btraceio/jbang-catalog/main/jbang-catalog.json
jbang btrace@btraceio <PID> script.java

# SDKMan
sdk install btrace

# Manual download
curl -LO https://github.com/btraceio/btrace/releases/latest/download/btrace-bin.tar.gz
```

See [Installation Guide](docs/GettingStarted.md#installation) for Docker, package managers, and more options.

---

## Documentation

| Resource | Description |
|----------|-------------|
| [Quick Reference](docs/QuickReference.md) | Cheat sheet for experienced users |
| [Getting Started](docs/GettingStarted.md) | Step-by-step first trace tutorial |
| [Full Tutorial](docs/BTraceTutorial.md) | Complete walkthrough of all features |
| [Oneliners](docs/OnelinerGuide.md) | DTrace-style quick probes |
| [Extensions](docs/BTraceExtensionDevelopmentGuide.md) | StatsD, custom integrations |
| [Documentation Hub](docs/README.md) | All docs and guides |

---

## Building from Source

```sh
git clone https://github.com/btraceio/btrace.git
cd btrace
./gradlew :btrace-dist:build
```

See [CLAUDE.md](CLAUDE.md) for development setup and architecture.

---

## Community & Contributing

**Get help:** [Slack](http://btrace.slack.com/) · [GitHub Issues](https://github.com/btraceio/btrace/issues)

Tips:
- Prefer IPv4 if your environment has odd local IPs: set `GRADLE_OPTS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"`.
- Run specific modules:
  - Runtime: `./gradlew :btrace-runtime:test`
  - Core (incl. extension SPI): `./gradlew :btrace-core:test`
  - Compiler: `./gradlew :btrace-compiler:test`
  - Agent (incl. instrumentation): `./gradlew :btrace-agent:test`
- Update instrumentor golden files when bytecode output changes: `./gradlew test -PupdateTestData`.

Integration tests (optional):
```sh
./gradlew --no-daemon integration-tests:test
```
These may exercise privileged extensions. If you run into permission denials, provide a policy file and pass it to the test JVMs via `-Dbtrace.permissions=/path/to/permissions.properties`.


## Using BTrace

### Installation

#### JBang (Easiest - Recommended)

Use [JBang](https://www.jbang.dev/) to run BTrace without manual installation:

```sh
# Install JBang (one time)
curl -Ls https://sh.jbang.dev | bash -s - app setup

# Use BTrace immediately (replace <version> with desired version, e.g., 3.0.0)
jbang io.btrace:btrace-client:<version> <PID> <script.java>

# After first run, use shorter alias
jbang btrace <PID> <script.java>
```

**Note:** Replace `<version>` with the desired BTrace version (e.g., `3.0.0`). See [releases](https://github.com/btraceio/btrace/releases) for available versions.

**Benefits:** Zero installation, automatic version management, works everywhere (Windows/macOS/Linux/containers), perfect for CI/CD.

**Agent JAR:** The client automatically discovers the masked agent JAR (`btrace.jar`) on its classpath — no extraction step is needed. If you want to use the agent JAR directly (e.g., with `-javaagent`), find it in the Maven local repository after the first jbang run:
```sh
# ~/.m2/repository/io/btrace/btrace/<version>/btrace-<version>.jar
```

See [Getting Started Guide](docs/GettingStarted.md#jbang-installation-recommended-for-quick-start) for complete JBang documentation and examples.

#### Binary Distribution

**Download:** Get the latest release from the [release page](https://github.com/btraceio/btrace/releases/latest)

```sh
# Extract the archive
tar -xzf btrace-*.tar.gz
# or
unzip btrace-*.zip

# Set environment variables (optional but recommended)
export BTRACE_HOME=/path/to/btrace
export PATH=$BTRACE_HOME/bin:$PATH
```

#### Package Installation

```sh
# RPM-based systems
sudo rpm -i btrace-*.rpm

# Debian-based systems
sudo dpkg -i btrace-*.deb
```

**Docker images:**
```dockerfile
# Copy BTrace into your application image
FROM btrace/btrace:latest AS btrace
FROM bellsoft/liberica-openjdk-debian:11-cds

COPY --from=btrace /opt/btrace /opt/btrace
ENV BTRACE_HOME=/opt/btrace PATH="${PATH}:${BTRACE_HOME}/bin"

# Your application...
```

Available variants:
- `btrace/btrace:latest` - Debian-based (~25MB)
- `btrace/btrace:latest-alpine` - Alpine-based (~15MB)
- `btrace/btrace:latest-distroless` - Distroless (~10MB)

See [docker/README.md](docker/README.md) for complete Docker documentation.

### Quick Start

**With JBang (no installation required):**
```sh
# Attach to running application
jbang btrace <PID> <trace_script.java>
```

**With installed BTrace:**
```sh
# Attach to running application
btrace <PID> <trace_script.java>

# Compile BTrace script
btracec <trace_script.java>

# Launch application with BTrace agent
btracer <compiled_script.class> <java-application-and-args>
```

### Extensions and Deprecated libs/profiles

Extensions add functionality via a stable API on bootstrap and an isolated implementation. See the extension development guide and examples.

Note: The legacy `libs`/profiles mechanism is deprecated and planned for removal after N+2 minor releases. Prefer packaging integrations as extensions and using provided-style class loading patterns (object hand-off + TCCL). For migration guidance and examples, see:
- `docs/architecture/migrating-from-libs-profiles.md`
- `docs/architecture/provided-style-extensions.md`
- `docs/examples/README.md`

As a last resort (discouraged), you may append a single jar to the system classpath: `-Dbtrace.system.appendJar=/abs/path/lib.jar -Dbtrace.trusted=true`.

### Fat Agent JAR (Single-JAR Deployment)

For environments where managing multiple JARs is impractical (Spark, Hadoop, Kubernetes), BTrace provides a fat agent JAR with embedded extensions:

```sh
# Build fat agent with all extensions
./gradlew :btrace-dist:fatAgentJar

# Build with specific extensions only
./gradlew :btrace-dist:fatAgentJar -PembedExtensions=btrace-metrics,btrace-statsd

# Use the fat agent
java -javaagent:btrace-agent-fat.jar <your-app>
```

The fat agent JAR includes:
- All agent and boot classes
- Embedded extension API classes (bootstrap)
- Embedded extension impl classes (runtime-loaded)
- Extension metadata for auto-discovery

For custom fat agent builds, use the Gradle plugin:
```groovy
plugins {
    id 'io.btrace.fat-agent'
}

btraceFatAgent {
    embedExtensions {
        maven('io.btrace:btrace-metrics:3.0.0')
        project(':my-custom-extension')
    }
}
```

See [Fat Agent Plugin Architecture](docs/architecture/fat-agent-plugin.md) and [Gradle Plugin README](btrace-gradle-plugin/README.md) for details.

### Oneliner Quick Examples

BTrace now supports DTrace-style oneliners for quick debugging without writing full Java scripts:

```sh
# Trace method entry with arguments
btrace -n 'javax.swing.*::setText @entry { print method, args }' <PID>

# Find slow database queries (>100ms)
btrace -n 'java.sql.Statement::execute* @return if duration>100ms { print method, duration }' <PID>

# Count method invocations
btrace -n 'java.util.HashMap::get @entry { count }' <PID>

# Print stack trace on OutOfMemoryError
btrace -n 'java.lang.OutOfMemoryError::<init> @return { stack(10) }' <PID>
```

**Supported features:**
- **Locations**: `@entry`, `@return`, `@error`
- **Actions**: `print`, `count`, `time`, `stack`
- **Filters**: `if duration>NUMBERms`, `if args[N]==VALUE`
- **Patterns**: Wildcards (`*`, `?`) and regex (`/pattern/`)

See [Oneliner Guide](docs/OnelinerGuide.md) for complete syntax and examples.

### Documentation
For comprehensive documentation, tutorials, and guides:
* **[BTrace Documentation Hub](docs/README.md)** - Complete documentation index with learning paths, quick reference, troubleshooting, and more
* **[Getting Started Guide](docs/GettingStarted.md)** - Get up and running in 5 minutes
* **[BTrace Wiki](https://github.com/btraceio/btrace/wiki/Home)** - External wiki with additional resources

### Extensions and Permissions
BTrace supports extensions (like StatsdExtension) that provide additional functionality. Extensions require explicit permissions for security:

* **Default permissions** (always granted): MESSAGING, AGGREGATION, JFR_EVENTS, PROFILING
* **Standard permissions** (granted unless denied): FILE_READ, SYSTEM_PROPS, THREAD_INFO, MEMORY_INFO
* **Privileged permissions** (require explicit grant): FILE_WRITE, NETWORK, THREADS, NATIVE, EXEC, REFLECTION, CLASSLOADER, UNLIMITED_MEMORY

Permissions are enforced based on extension/service descriptors and agent grants specified at attach-time.

Privileged permissions are granted via agent options (there is no client-side flag), e.g. when starting the target with the agent:
```sh
java -javaagent:btrace.jar=grant=NETWORK,THREADS ... MainClass
```
or persistently via a policy file (see [PermissionPolicy](docs/PermissionPolicy.md)).

If extensions fail to load, use `-le` to troubleshoot:
```sh
btrace -le <PID>
```

See the [Tutorial](docs/BTraceTutorial.md) for detailed documentation.

Extensions CLI: use `btracex` to inspect and manage extensions and the simplified permission policy:
- `btracex inspect <zip|dir>` prints extension id, version, services, and whether it’s privileged.
- `btracex policy print|set [--policy-file <path>|--home|--classpath <outDir>]` edits `allowExtensions`, `denyExtensions`, `allowPrivileged`.
- `btracex list` shows installed extensions; `btracex install` installs from Maven coordinates.

Note: Extension “required permissions” are informational and help operators assess risk. Implementation linking is controlled by per‑extension allow/deny lists and the `allowPrivileged` flag; when blocked, APIs remain available and SHIMs are used so probes continue safely.

#### Agent Policy and Allow/Deny Lists
- Launch-time policy can be set via agent args (operator-controlled):
  - `-javaagent:btrace.jar=...,grant=NETWORK,THREADS,grantAll=false`
  - `-javaagent:btrace.jar=...,allowExtensions=btrace-statsd,my-metrics,denyExtensions=legacy-foo`
- Optional policy file (process-local): `-Dbtrace.permissions=/path/to/permissions.properties` or `~/.btrace/permissions.properties`.
- When an extension impl is blocked, the API remains on bootstrap so SHIMs can be generated.

See docs/PermissionPolicy.md for details and examples.

#### btracex TUI (interactive)
- Launch: `btracex inspect` (with no args) opens an interactive view of installed extensions.
- Header: shows current policy file path and the list of scanned repositories.
- Table: columns State, Id, Version. State uses compact symbols: `?` (default), `+` (allowed), `-` (denied).
- Details: selection updates automatically; shows the full-word state: `default` / `allowed` / `denied` and the full path.
- Legend: a short legend under the table maps the state symbols.

The TUI provides a keyboard-driven, terminal-based view for browsing installed extensions and toggling their allow/deny state without editing the policy file by hand.

Keys
- Navigate: Arrow keys, PageUp/PageDown, Home/End
- Toggle state: space (flows `? → + (confirm) → - → +`; only `c` clears to default)
- Clear: `c` (removes extension id from both allow and deny lists)
- Explain privileges: `e` (opens a dialog with required permissions and risk descriptions)
- Filter: `/` (filter by id or path)
- Sort: `s` (choose column; repeat to toggle asc/desc)
- Adjust split: `m` (enter mode), then Up/Down to resize; press `Esc` or `m` again to exit
- Help / Quit: `?` / `q`

### Maven Integration

**Fat Agent Plugin:** The unpublished in-repository Maven fat-agent module was removed for 3.0.0
because it cannot consume the published 3.0 extension layout safely. Use the
`io.btrace.fat-agent` Gradle plugin documented above.

**Script Compilation Plugin** ([external repo](https://github.com/btraceio/btrace-maven)):
- Compilation of BTrace scripts during the build process
- BTrace Project Archetype for quick project setup

## Contributing

**Important:** Pull requests can only be accepted from signers of the [Oracle Contributor Agreement](https://oca.opensource.oracle.com/).

### Development

See [CLAUDE.md](CLAUDE.md) for detailed development guidelines and project architecture.

## Community

- **Slack:** [btrace.slack.com](http://btrace.slack.com/)
- **Gitter:** [gitter.im/btraceio/btrace](https://gitter.im/btraceio/btrace)
- **Issues:** [GitHub Issues](https://github.com/btraceio/btrace/issues)

## License

Apache License 2.0. See [LICENSE](LICENSE).

---

**Credits:** Built with [ASM](http://asm.ow2.org/), [JCTools](https://github.com/JCTools/JCTools), [hppcrt](https://github.com/vsonnier/hppcrt). Optimized with [JProfiler](http://www.ej-technologies.com/products/jprofiler/overview.html).
