# BTrace

**Safe, dynamic tracing for Java applications**

[![CI](https://github.com/btraceio/btrace/workflows/BTrace%20CI%2FCD/badge.svg?branch=develop)](https://github.com/btraceio/btrace/actions)
[![Release](https://img.shields.io/github/v/release/btraceio/btrace?sort=semver)](https://github.com/btraceio/btrace/releases/latest)
[![codecov](https://codecov.io/github/btraceio/btrace/coverage.svg?branch=develop)](https://codecov.io/github/btraceio/btrace?branch=develop)

BTrace dynamically instruments running Java applications to inject tracing code at runtime. No restarts. No recompilation. Built for use on production JVMs.

> **Quick links:** [Quick Reference](docs/QuickReference.md) · [Step-by-Step Tutorial](docs/GettingStarted.md)

---

## Why BTrace?

- **Zero downtime** - Attach to running JVMs without restart
- **Safe by design** - Scripts are verified against a [restricted subset](docs/QuickReference.md#restrictions) (no loops, no new threads, no external calls) before they are loaded; the subset is designed to avoid side effects on the traced application. Verification does not bound overhead: see [production precautions](docs/FAQ.md#is-btrace-safe-to-use-in-production) and the [misuse-resilience QE](docs/QEAgent.md)
- **Flexible probes** - Method entry/exit, timings, field access, allocations
- **Low overhead** - Bytecode is injected only at the probe points you select; cost scales with how hot they are

### Supported Java Versions

BTrace 3.0 runs on Java 8–27+. Running BTrace against a JVM older than Java 17 is deprecated: it continues to work throughout 3.x but emits a deprecation warning. Support for Java < 17 will be removed in the next major release (4.0). See the [migration guide](docs/Migration-2.x-to-3.0.md) for details on upgrading from BTrace 2.x and the [3.0.0 release notes](.github/release-notes/v3.0.0.md) for everything that changed.

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

The 30-second path above needs nothing else. Alternatives:

```sh
# JBang by Maven coordinate (no catalog needed, version pinned)
jbang io.btrace:btrace:3.0.0 <PID> script.java

# SDKMan
sdk install btrace

# Manual download (assets are versioned: btrace-v<version>-bin.tar.gz)
BTRACE_VERSION=3.0.0
curl -LO https://github.com/btraceio/btrace/releases/download/v${BTRACE_VERSION}/btrace-v${BTRACE_VERSION}-bin.tar.gz

# Docker: copy /opt/btrace from a version-pinned image into your own
#   FROM ghcr.io/btraceio/btrace:3.0.0 AS btrace
```

With an installed distribution the commands are `btrace <PID> <script.java>` (attach), `btracec <script.java>` (compile) and `btracer <script.class> <java-application-and-args>` (launch with the agent).

Details for every option live in one place each:
- [Installation](docs/GettingStarted.md#installation): archives, RPM/DEB packages, JBang, using the agent JAR with `-javaagent`
- [Four ways to run BTrace](docs/GettingStarted.md#four-ways-to-run-btrace) and the [startup-mode security boundary](docs/GettingStarted.md#startup-modes-and-the-30-security-boundary)
- [Docker images](docker/README.md): the Debian, Alpine and distroless variants, tags and Kubernetes patterns

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
| [Wiki](https://github.com/btraceio/btrace/wiki/Home) | External wiki with additional resources |

---

## Using BTrace

### Extensions and Removed libs/profiles

Extensions add functionality via a stable API on bootstrap and an isolated implementation. See the extension development guide and examples.

Note: The legacy `libs`/profiles mechanism has been removed. Passing `libs=<profile>` logs an error and loads nothing, so custom classes that used to arrive that way will no longer resolve. Package integrations as extensions and use provided-style class loading patterns (object hand-off + TCCL). For migration guidance and examples, see:
- `docs/architecture/migrating-from-libs-profiles.md`
- `docs/architecture/provided-style-extensions.md`
- `docs/examples/README.md`

As a last resort (discouraged), you may append a single jar to the system classpath: set `-Dbtrace.system.appendJar=/abs/path/lib.jar` on the target JVM together with the `trusted=true` **agent argument** (`-javaagent:btrace.jar=trusted=true,...`); the `-Dbtrace.trusted` system property has no effect and the agent ignores the jar without the agent argument.

### Fat Agent JAR (Single-JAR Deployment)

For environments where managing multiple JARs is impractical (Spark, Hadoop, Kubernetes), BTrace provides a fat agent JAR with embedded extensions:

```sh
# Build fat agent with the default extensions
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
        // BTrace bundles extension packages in its distribution's extensions/ directory.
        file('/path/to/btrace-metrics-3.0.0-extension.zip')
        project(':my-custom-extension')
    }
}
```

See [Fat Agent Plugin Architecture](docs/architecture/fat-agent-plugin.md) and [Gradle Plugin README](btrace-gradle-plugin/README.md) for details.

### Extensions and Permissions
BTrace supports extensions (like the bundled StatsD extension) that provide additional functionality. Extensions require explicit permissions for security:

* **Default permissions** (always granted): MESSAGING, AGGREGATION, JFR_EVENTS, PROFILING
* **Standard permissions** (granted unless denied): FILE_READ, SYSTEM_PROPS, THREAD_INFO, MEMORY_INFO
* **Privileged permissions** (require explicit grant): FILE_WRITE, NETWORK, THREADS, NATIVE, EXEC, REFLECTION, CLASSLOADER, UNLIMITED_MEMORY

Permissions are enforced based on extension/service descriptors and agent grants specified at attach-time.

An extension that declares a privileged permission links only when the operator allows it via agent options (there is no client-side flag), e.g. when starting the target with the agent:
```sh
java -javaagent:btrace.jar=allowExtensions=btrace-metrics ... MainClass
```
or persistently via a policy file (see [PermissionPolicy](docs/PermissionPolicy.md)). The older `grant=`/`deny=`/`grantAll=` agent arguments are accepted but have no effect.

If extensions fail to load, use `-le` to troubleshoot:
```sh
btrace -le <PID>
```

See the [Tutorial](docs/BTraceTutorial.md) for detailed documentation.

Extensions CLI: use `btracex` to inspect and manage extensions and the simplified permission policy:
- `btracex inspect <zip|dir>` prints extension id, version, services, and whether it’s privileged.
- `btracex policy print|set [--policy-file <path>|--home|--classpath <outDir>]` edits `allowExtensions`, `denyExtensions`, `allowPrivileged`.
- `btracex list` shows installed extensions; `btracex install` accepts a local extension package or
  separately published third-party Maven coordinates. BTrace-built packages are supplied in the
  distribution's `extensions/` directory, not as individual Maven artifacts.

Note: Extension “required permissions” are informational and help operators assess risk. Implementation linking is controlled by per‑extension allow/deny lists and the `allowPrivileged` flag. When an extension is blocked, a plain `@Injected` field fails the probe deployment fast; only `@Injected(optional = true)` falls back, and the fallback is a throwing stub by default (`mode = SHIM` or `-Dbtrace.extension.shimMode=shim` selects a no-op shim).

#### Agent Policy and Allow/Deny Lists
- Launch-time policy can be set via agent args (operator-controlled):
  - `-javaagent:btrace.jar=...,allowExtensions=btrace-statsd,my-metrics,denyExtensions=legacy-foo`
  - `-javaagent:btrace.jar=...,allowPrivileged=true` (every privileged extension; use with caution)
- Optional policy file (process-local): `-Dbtrace.permissions=/path/to/permissions.properties` or `~/.btrace/permissions.properties`.
- When an extension impl is blocked, the API remains on bootstrap so `@Injected(optional = true)` fields can be bound to a shim or throwing stub; non-optional fields fail the probe fast.

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

## Telemetry and privacy

BTrace sends no telemetry unless you opt in. The default agent start performs no network operation
and creates no telemetry thread; only the explicit `telemetry=true` agent argument enables a single
`agent_start` event. See [Getting Started: Optional agent telemetry](docs/GettingStarted.md#optional-agent-telemetry)
for what is sent and how to disable it again.

## Building from Source

```sh
git clone https://github.com/btraceio/btrace.git
cd btrace
./gradlew :btrace-dist:build
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup (required JDK toolchains, running tests) and [docs/README.md](docs/README.md) for architecture.

Tips:
- Prefer IPv4 if your environment has odd local IPs: set `GRADLE_OPTS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"`.
- Run specific modules:
  - Runtime: `./gradlew :btrace-runtime:test`
  - Core (incl. extension SPI): `./gradlew :btrace-core:test`
  - Compiler: `./gradlew :btrace-compiler:test`
  - Agent (incl. instrumentation): `./gradlew :btrace-agent:test`
- Update instrumentor golden files when bytecode output changes: `./gradlew test -PupdateTestData`.

Integration tests (optional; without `-Pintegration` the task is skipped and runs nothing):
```sh
./gradlew --no-daemon -Pintegration :integration-tests:test
```

Misuse-resilience QE (optional):
```sh
./gradlew :btrace-qe:qe
```
Runs the abuse corpus in `btrace-qe/cases/` against real JVMs and writes a findings report to `btrace-qe/build/qe-report/`. See the [QE Agent guide](docs/QEAgent.md). Every abuse idea explored so far (kept, rejected, blocked, open leads) is logged in [btrace-qe/EXPLORATION.md](btrace-qe/EXPLORATION.md); check it before proposing new cases.

## Community & Contributing

- **Slack:** [btrace.slack.com](http://btrace.slack.com/)
- **Gitter:** [gitter.im/btraceio/btrace](https://gitter.im/btraceio/btrace)
- **Issues:** [GitHub Issues](https://github.com/btraceio/btrace/issues)

Contributions are accepted under the [Apache License 2.0](LICENSE). See [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow and expectations, and [AGENTS.md](AGENTS.md) for the rules coding agents follow.

## License

Apache License 2.0. See [LICENSE](LICENSE).

---

**Credits:** Built with [ASM](http://asm.ow2.org/), [JCTools](https://github.com/JCTools/JCTools), [hppcrt](https://github.com/vsonnier/hppcrt). Optimized with [JProfiler](http://www.ej-technologies.com/products/jprofiler/overview.html).
