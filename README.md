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


## Using BTrace

### Installation

**Download:** Get the latest release from the [release page](https://github.com/btraceio/btrace/releases/latest)

**Binary distribution:**
```sh
# Extract the archive
tar -xzf btrace-*.tar.gz
# or
unzip btrace-*.zip

# Set environment variables (optional but recommended)
export BTRACE_HOME=/path/to/btrace
export PATH=$BTRACE_HOME/bin:$PATH
```

**Package installation:**
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
FROM openjdk:11-jdk

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

```sh
# Attach to running application
btrace <PID> <trace_script.java>

# Compile BTrace script
btracec <trace_script.java>

# Launch application with BTrace agent
btracer <compiled_script.class> <java-application-and-args>
```

### Documentation
For comprehensive documentation, tutorials, and guides:
* **[BTrace Documentation Hub](docs/README.md)** - Complete documentation index with learning paths, quick reference, troubleshooting, and more
* **[Getting Started Guide](docs/GettingStarted.md)** - Get up and running in 5 minutes
* **[BTrace Wiki](https://github.com/btraceio/btrace/wiki/Home)** - External wiki with additional resources

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
