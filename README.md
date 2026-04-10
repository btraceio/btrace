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
        println(strcat("checkout: ", str(ns/1_000_000) + "ms"));
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

**Get help:** [Slack](http://btrace.slack.com/) · [Gitter](https://gitter.im/btraceio/btrace) · [GitHub Issues](https://github.com/btraceio/btrace/issues)

**Contribute:** Pull requests require signing the [Oracle Contributor Agreement](https://oca.opensource.oracle.com/).

---

## License

GPLv2 with Classpath Exception. See [LICENSE](LICENSE).

---

**Credits:** Built with [ASM](http://asm.ow2.org/), [JCTools](https://github.com/JCTools/JCTools), [hppcrt](https://github.com/vsonnier/hppcrt). Optimized with [JProfiler](http://www.ej-technologies.com/products/jprofiler/overview.html).
