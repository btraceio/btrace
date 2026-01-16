# BTrace Documentation

Welcome to the BTrace documentation! BTrace is a safe, dynamic tracing tool for the Java platform that allows you to instrument running applications without stopping them.

## Quick Start

**New to BTrace?** Start here → [Getting Started Guide](gettingStarted.md)

Get up and running in 5 minutes with installation, your first script, and common usage patterns.

## Documentation Map

| Document | Description | Target Audience |
|----------|-------------|-----------------|
| **[Getting Started](gettingStarted.md)** | Installation, first script, deployment modes, common pitfalls | New users, quick start |
| **[Oneliner Guide](onelinerGuide.md)** | DTrace-style oneliners for quick debugging without scripts | Quick debugging, ops/SRE |
| **[Quick Reference](quickReference.md)** | Annotations, patterns, CLI commands, built-in functions | Experienced users, quick lookup |
| **[BTrace Tutorial](btraceTutorial.md)** | Comprehensive lessons covering all features | All users, in-depth learning |
| **[Troubleshooting Guide](troubleshooting.md)** | Common errors, debugging, performance, compatibility | Problem-solving, debugging |
| **[FAQ](faq.md)** | Common questions, best practices, comparisons | All users, decision-making |

## Learning Paths

### I'm New to BTrace
1. Read [Getting Started Guide](gettingStarted.md) (10 minutes)
2. Try the 5-minute quick start example
3. Learn [Oneliner syntax](onelinerGuide.md) for quick debugging (5 minutes)
4. Explore [BTrace Tutorial](btraceTutorial.md) lessons 1-3
5. Keep [Quick Reference](quickReference.md) handy

Tip: Want latency histograms fast? See [Quick Start: Histogram Metrics Extension](gettingStarted.md#quick-start-histogram-metrics-extension) and the tutorial section [Using the Histogram Metrics Extension](btraceTutorial.md#using-the-histogram-metrics-extension-btrace-metrics).

### I Need to Solve a Problem
1. Check [Troubleshooting Guide](troubleshooting.md) for your error
2. Search [FAQ](faq.md) for similar issues
3. Review [Getting Started](gettingStarted.md) common pitfalls
4. Ask on [Slack](http://btrace.slack.com/) or [Gitter](https://gitter.im/btraceio/btrace)

### I Need a Quick Lookup
- **Quick Debug?** → [Oneliner Guide](onelinerGuide.md) for DTrace-style one-line commands
- **Annotations?** → [Quick Reference: Core Annotations](quickReference.md#core-annotations)
- **CLI Commands?** → [Quick Reference: CLI Commands](quickReference.md#cli-commands)
- **Common Patterns?** → [Quick Reference: Common Patterns](quickReference.md#common-patterns)
- **Built-in Functions?** → [Quick Reference: Built-in Functions](quickReference.md#built-in-functions)

### I Want Advanced Features
1. **JFR Integration** → [Getting Started: JFR Integration](GettingStarted.md#advanced-jfr-integration), [Tutorial Lesson 5](BTraceTutorial.md)
2. **Sampling** → [Quick Reference: @Sampled](QuickReference.md#sampled), [FAQ: Performance](FAQ.md#performance-issues)
3. **Aggregations** → [Quick Reference: Aggregation Functions](QuickReference.md#aggregation-functions)
4. **Cloud Deployments** → [Getting Started: K8s](GettingStarted.md#btrace-in-containers-and-kubernetes), [FAQ: K8s](FAQ.md#can-i-use-btrace-with-microservices)
5. **Fat Agent JAR** → [Getting Started: Fat Agent](GettingStarted.md#fat-agent-jar-single-jar-deployment) for single-JAR deployment
6. **Level Filtering** → [Quick Reference: @Level](QuickReference.md#level)
7. **Extensions Architecture** → [Extension invokedynamic Bridge](architecture/extension-invokedynamic-bridge.md)

## Documentation by Topic

### Core Features
- **Method Tracing** → [Tutorial Lesson 1](btraceTutorial.md), [Quick Reference: @OnMethod](quickReference.md#onmethod)
- **Timing & Duration** → [Quick Reference: @Duration](quickReference.md#parameter-annotations), [Pattern: Method Timing](quickReference.md#pattern-1-method-entrye xit-timing)
- **Exception Tracking** → [Quick Reference: Kind.ERROR](quickReference.md#location-kinds), [Pattern: Exception Tracking](quickReference.md#pattern-3-exception-tracking)
- **Field Access** → [Quick Reference: Kind.FIELD_GET/SET](quickReference.md#location-kinds)

### Advanced Features
- **JFR Integration** → [Getting Started: JFR](gettingStarted.md#advanced-jfr-integration), [Quick Reference: @Event](quickReference.md#event), [FAQ: JFR](faq.md#jfr-integration)
- **Sampling** → [Quick Reference: @Sampled](quickReference.md#sampled), [FAQ: Performance](faq.md#btrace-causes-significant-slowdown)
- **Level Control** → [Quick Reference: @Level](quickReference.md#level)
- **Aggregations** → [Quick Reference: Aggregation Functions](quickReference.md#aggregation-functions)
- **Periodic Events** → [Quick Reference: @OnTimer](quickReference.md#ontimer), [@PeriodicEvent](quickReference.md#periodicevent)

### Deployment & Operations
- **Installation** → [Getting Started: Installation](GettingStarted.md#installation)
- **Deployment Modes** → [Getting Started: Running BTrace](GettingStarted.md#four-ways-to-run-btrace)
- **Fat Agent (Single-JAR)** → [Getting Started: Fat Agent](GettingStarted.md#fat-agent-jar-single-jar-deployment), [Architecture](architecture/fat-agent-plugin.md), [Gradle Plugin](../btrace-gradle-plugin/README.md), [Maven Plugin](GettingStarted.md#maven-plugin)
- **Docker & Containers** → [Getting Started: Containers](GettingStarted.md#btrace-in-containers-and-kubernetes)
- **Kubernetes** → [Getting Started: K8s](GettingStarted.md#btrace-in-containers-and-kubernetes), [FAQ: Microservices](FAQ.md#can-i-use-btrace-with-microservices), [Troubleshooting: K8s](Troubleshooting.md#kubernetes-and-cloud-deployments)
- **Performance Tuning** → [FAQ: Performance Impact](FAQ.md#whats-the-performance-impact-of-btrace), [Troubleshooting: Performance](Troubleshooting.md#performance-issues)
- **Extensions CLI (btracex)** → [Permission Policy](PermissionPolicy.md) for allow/deny and quick inspection

### Problem Solving
- **No Output** → [Troubleshooting: No Output](troubleshooting.md#no-output-from-scripts)
- **Attachment Fails** → [Troubleshooting: JVM Attachment](troubleshooting.md#jvm-attachment-issues)
- **Verification Errors** → [Troubleshooting: Verification](troubleshooting.md#script-verification-errors)
- **Performance Issues** → [Troubleshooting: Performance](troubleshooting.md#performance-issues)
- **Compatibility** → [Troubleshooting: Compatibility](troubleshooting.md#compatibility-issues)

### Integration
- **Spring Boot** → [FAQ: Spring Boot](faq.md#can-i-use-btrace-with-spring-boot-applications)
- **Third-Party Libraries** → [FAQ: Third-Party](faq.md#how-do-i-trace-methods-from-third-party-libraries)
- **JMX Export** → [Quick Reference: @Export](quickReference.md#export), [FAQ: Monitoring Integration](faq.md#can-i-integrate-btrace-with-monitoring-systems)
- **Service Mesh** → [FAQ: Service Mesh](faq.md#does-btrace-work-with-service-meshes-istiolinkerd)

### Architecture
- **Masked JAR** → [Masked JAR Architecture](architecture/MaskedJarArchitecture.md) — single-JAR distribution with classdata masking
- **v2 Binary Protocol** → [Version 2 Protocol Architecture](architecture/Version2ProtocolArchitecture.md) — custom binary serialization
- **Extension Framework** → [Extension invokedynamic Bridge](architecture/ExtensionInvokeDynamicBridge.md), [Extension Configuration](architecture/ExtensionConfiguration.md), [Extension Manifest](architecture/ExtensionManifestFormat.md), [Extension Storage](architecture/ExtensionStorageDesign.md)
- **Instrumentation** → [BTrace Instrumentation Analysis](architecture/BTraceInstrAnalysis.md)

## Sample Scripts

BTrace includes 50+ sample scripts demonstrating real-world use cases:
- Location: `btrace-dist/src/main/resources/samples/`
- Examples: File I/O tracking, JDBC monitoring, HTTP requests, memory allocation, thread analysis
- Browse: [BTrace Samples Directory](../btrace-dist/src/main/resources/samples/)

## External Resources

### Official Resources
- **GitHub Repository**: [github.com/btraceio/btrace](https://github.com/btraceio/btrace)
- **Wiki (External)**: [github.com/btraceio/btrace/wiki](https://github.com/btraceio/btrace/wiki/Home)
- **Maven Plugin (Script Compilation)**: [github.com/btraceio/btrace-maven](https://github.com/btraceio/btrace-maven)
- **Maven Plugin (Fat Agent)**: [Gradle Plugin README](../btrace-gradle-plugin/README.md#btrace-maven-plugin)
- **Releases**: [GitHub Releases](https://github.com/btraceio/btrace/releases/latest)

### Community
- **Slack**: [btrace.slack.com](http://btrace.slack.com/)
- **Gitter Chat**: [gitter.im/btraceio/btrace](https://gitter.im/btraceio/btrace)
- **Issues**: [GitHub Issues](https://github.com/btraceio/btrace/issues)
- **Discussions**: [GitHub Discussions](https://github.com/btraceio/btrace/discussions)

### Tools & Integrations
- **VisualVM Plugin**: [visualvm.github.io](https://visualvm.github.io)
- **JDK Mission Control**: For viewing JFR events created by BTrace

## Contributing

BTrace is an open-source project welcoming contributions. To contribute:

1. Sign the [Oracle Contributor Agreement](https://oca.opensource.oracle.com/)
2. Read the [Contributing Guidelines](../Readme.md#contributing---important)
3. Fork the repository and create a pull request
4. See [Build Instructions](../Readme.md#building-btrace) for development setup

## Version Information

- **Current Version**: Check [GitHub Releases](https://github.com/btraceio/btrace/releases/latest)
- **Java Compatibility**: Java 8-25
- **License**: GPLv2 with Classpath Exception

## Documentation Feedback

Found an issue with the documentation? Please:
- Report it on [GitHub Issues](https://github.com/btraceio/btrace/issues)
- Tag it with `documentation` label
- Or submit a pull request with improvements

---

**Ready to get started?** → [Getting Started Guide](gettingStarted.md)
