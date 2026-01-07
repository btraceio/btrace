[![Dev build](https://github.com/btraceio/btrace/workflows/BTrace%20CI%2FCD/badge.svg?branch=develop)](https://github.com/btraceio/btrace/actions?query=workflow%3A%22BTrace+CI%2FCD%22+branch%3Adevelop) [![Download](https://img.shields.io/github/v/release/btraceio/btrace?sort=semver)](https://github.com/btraceio/btrace/releases/latest) [![codecov.io](https://codecov.io/github/btraceio/btrace/coverage.svg?branch=develop)](https://codecov.io/github/btraceio/btrace?branch=develop) [![huhu](https://img.shields.io/badge/Slack-join%20chat-brightgreen")](http://btrace.slack.com/) [![Join the chat at https://gitter.im/jbachorik/btrace](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/btraceio/btrace?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![Project Stats](https://www.openhub.net/p/btrace/widgets/project_thin_badge.gif)](https://www.openhub.net/p/btrace)

# BTrace

A safe, dynamic tracing tool for the Java platform

## Overview

BTrace dynamically instruments running Java applications to inject tracing code at runtime, without stopping the application or recompiling code. Similar to DTrace for OpenSolaris, BTrace uses bytecode instrumentation to trace methods, monitor performance, and diagnose issues in production environments.

## Credits
* Based on [ASM](http://asm.ow2.org/)
* Powered by [JCTools](https://github.com/JCTools/JCTools)
* Powered by [hppcrt](https://github.com/vsonnier/hppcrt)
* Optimized with [JProfiler Java Profiler](http://www.ej-technologies.com/products/jprofiler/overview.html)
* Build env helper using [SDKMAN!](https://sdkman.io/)

## Building BTrace

### Setup
You will need the following applications installed

* [Git](http://git-scm.com/downloads)
* (optionally, the default launcher is the bundled `gradlew` wrapper) [Gradle](http://gradle.org)


### Build

```sh
cd <btrace>
./gradlew :btrace-dist:build
```

**Output locations:**
- Binary distributions: `btrace-dist/build/distributions/` (*.tar.gz, *.zip, *.rpm, *.deb)
- Exploded binary (BTRACE_HOME): `btrace-dist/build/resources/main/`

**Updating golden files:**
When instrumentor code changes, update test golden files with:
```sh
./gradlew test -PupdateTestData
```
Commit the regenerated golden files to Git.

### How To Run Tests

For a fast local cycle, run unit tests and skip integration tests (which start forked JVMs):

```sh
export GRADLE_USER_HOME="$PWD/.gradle-home"   # keep caches inside the repo (optional)
./gradlew --no-daemon test -x integration-tests:test
```

Tips:
- Prefer IPv4 if your environment has odd local IPs: set `GRADLE_OPTS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"`.
- Run specific modules:
  - Runtime: `./gradlew :btrace-runtime:test`
  - Extension: `./gradlew :btrace-extension:test`
  - Compiler: `./gradlew :btrace-compiler:test`
  - Instr: `./gradlew :btrace-instr:test`
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

# Use BTrace immediately (replace <version> with desired version, e.g., 2.3.0)
jbang org.openjdk.btrace:btrace-client:<version> <PID> <script.java>

# After first run, use shorter alias
jbang btrace <PID> <script.java>
```

**Note:** Replace `<version>` with the desired BTrace version (e.g., `2.3.0`). See [releases](https://github.com/btraceio/btrace/releases) for available versions.

**Benefits:** Zero installation, automatic version management, works everywhere (Windows/macOS/Linux/containers), perfect for CI/CD.

**Extract agent JARs** (if needed for `--agent-jar`/`--boot-jar` flags):
```sh
# Extract embedded JARs
jbang btrace --extract-agent ~/.btrace

# This creates ~/.btrace/btrace-agent.jar and ~/.btrace/btrace-boot.jar
# Then use them:
jbang btrace --agent-jar ~/.btrace/btrace-agent.jar --boot-jar ~/.btrace/btrace-boot.jar <PID> <script.java>

# Or find them in Maven local repository (after first jbang run):
# ~/.m2/repository/org/openjdk/btrace/btrace-agent/<version>/btrace-agent-<version>.jar
# ~/.m2/repository/org/openjdk/btrace/btrace-boot/<version>/btrace-boot-<version>.jar
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

# Extract agent JARs
jbang btrace --extract-agent ~/.btrace
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

Grant permissions at runtime:
```sh
btrace --grant=NETWORK,THREADS <PID> MyProbe.class
```

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
  - `-javaagent:btrace-agent.jar=...,grant=NETWORK,THREADS,grantAll=false`
  - `-javaagent:btrace-agent.jar=...,allowExtensions=btrace-statsd,my-metrics,denyExtensions=legacy-foo`
- Optional policy file (process-local): `-Dbtrace.permissions=/path/to/permissions.properties` or `~/.btrace/permissions.properties`.
- When an extension impl is blocked, the API remains on bootstrap so SHIMs can be generated.

See docs/PermissionPolicy.md for details and examples.

#### btracex TUI (interactive)
- Launch: `btracex inspect` (with no args) opens an interactive view of installed extensions.
- Header: shows current policy file path and the list of scanned repositories.
- Table: columns State, Id, Version. State uses compact symbols: `?` (default), `+` (allowed), `-` (denied).
- Details: selection updates automatically; shows the full-word state: `default` / `allowed` / `denied` and the full path.
- Legend: a short legend under the table maps the state symbols.

Screenshot / demo (optional):

![btracex TUI](docs/images/btracex-tui.png)

![btracex TUI demo](docs/images/btracex-tui.gif)

See also: docs/TUI.md for recording tips and an ASCII preview.

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

The [BTrace Maven Plugin](https://github.com/btraceio/btrace-maven) enables:
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

BTrace is licensed under GPLv2 with the Classpath Exception. See [LICENSE](LICENSE) for details.
