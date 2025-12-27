# BTrace Documentation

Welcome to the BTrace documentation! BTrace is a safe, dynamic tracing tool for the Java platform that allows you to instrument running applications without stopping them.

## Quick Start

**New to BTrace?** Start here → [Getting Started Guide](GettingStarted.md)

Get up and running in 5 minutes with installation, your first script, and common usage patterns.

## Documentation Map

| Document | Description | Target Audience |
|----------|-------------|-----------------|
| **[Getting Started](GettingStarted.md)** | Installation, first script, deployment modes, common pitfalls | New users, quick start |
| **[Quick Reference](QuickReference.md)** | Annotations, patterns, CLI commands, built-in functions | Experienced users, quick lookup |
| **[BTrace Tutorial](BTraceTutorial.md)** | Comprehensive lessons covering all features | All users, in-depth learning |
| **[Troubleshooting Guide](Troubleshooting.md)** | Common errors, debugging, performance, compatibility | Problem-solving, debugging |
| **[FAQ](FAQ.md)** | Common questions, best practices, comparisons | All users, decision-making |

## Learning Paths

### I'm New to BTrace
1. Read [Getting Started Guide](GettingStarted.md) (10 minutes)
2. Try the 5-minute quick start example
3. Explore [BTrace Tutorial](BTraceTutorial.md) lessons 1-3
4. Keep [Quick Reference](QuickReference.md) handy

### I Need to Solve a Problem
1. Check [Troubleshooting Guide](Troubleshooting.md) for your error
2. Search [FAQ](FAQ.md) for similar issues
3. Review [Getting Started](GettingStarted.md) common pitfalls
4. Ask on [Slack](http://btrace.slack.com/) or [Gitter](https://gitter.im/btraceio/btrace)

### I Need a Quick Lookup
- **Annotations?** → [Quick Reference: Core Annotations](QuickReference.md#core-annotations)
- **CLI Commands?** → [Quick Reference: CLI Commands](QuickReference.md#cli-commands)
- **Common Patterns?** → [Quick Reference: Common Patterns](QuickReference.md#common-patterns)
- **Built-in Functions?** → [Quick Reference: Built-in Functions](QuickReference.md#built-in-functions)

### I Want Advanced Features
1. **JFR Integration** → [Getting Started: JFR Integration](GettingStarted.md#advanced-jfr-integration), [Tutorial Lesson 5](BTraceTutorial.md)
2. **Sampling** → [Quick Reference: @Sampled](QuickReference.md#sampled), [FAQ: Performance](FAQ.md#performance-issues)
3. **Aggregations** → [Quick Reference: Aggregation Functions](QuickReference.md#aggregation-functions)
4. **Cloud Deployments** → [Getting Started: K8s](GettingStarted.md#btrace-in-containers-and-kubernetes), [FAQ: K8s](FAQ.md#can-i-use-btrace-with-microservices)
5. **Level Filtering** → [Quick Reference: @Level](QuickReference.md#level)

## Documentation by Topic

### Core Features
- **Method Tracing** → [Tutorial Lesson 1](BTraceTutorial.md), [Quick Reference: @OnMethod](QuickReference.md#onmethod)
- **Timing & Duration** → [Quick Reference: @Duration](QuickReference.md#parameter-annotations), [Pattern: Method Timing](QuickReference.md#pattern-1-method-entrye xit-timing)
- **Exception Tracking** → [Quick Reference: Kind.ERROR](QuickReference.md#location-kinds), [Pattern: Exception Tracking](QuickReference.md#pattern-3-exception-tracking)
- **Field Access** → [Quick Reference: Kind.FIELD_GET/SET](QuickReference.md#location-kinds)

### Advanced Features
- **JFR Integration** → [Getting Started: JFR](GettingStarted.md#advanced-jfr-integration), [Quick Reference: @Event](QuickReference.md#event), [FAQ: JFR](FAQ.md#jfr-integration)
- **Sampling** → [Quick Reference: @Sampled](QuickReference.md#sampled), [FAQ: Performance](FAQ.md#btrace-causes-significant-slowdown)
- **Level Control** → [Quick Reference: @Level](QuickReference.md#level)
- **Aggregations** → [Quick Reference: Aggregation Functions](QuickReference.md#aggregation-functions)
- **Periodic Events** → [Quick Reference: @OnTimer](QuickReference.md#ontimer), [@PeriodicEvent](QuickReference.md#periodicevent)

### Deployment & Operations
- **Installation** → [Getting Started: Installation](GettingStarted.md#installation)
- **Three Deployment Modes** → [Getting Started: Running BTrace](GettingStarted.md#three-ways-to-run-btrace)
- **Docker & Containers** → [Getting Started: Containers](GettingStarted.md#btrace-in-containers-and-kubernetes)
- **Kubernetes** → [Getting Started: K8s](GettingStarted.md#btrace-in-containers-and-kubernetes), [FAQ: Microservices](FAQ.md#can-i-use-btrace-with-microservices), [Troubleshooting: K8s](Troubleshooting.md#kubernetes-and-cloud-deployments)
- **Performance Tuning** → [FAQ: Performance Impact](FAQ.md#whats-the-performance-impact-of-btrace), [Troubleshooting: Performance](Troubleshooting.md#performance-issues)

### Problem Solving
- **No Output** → [Troubleshooting: No Output](Troubleshooting.md#no-output-from-scripts)
- **Attachment Fails** → [Troubleshooting: JVM Attachment](Troubleshooting.md#jvm-attachment-issues)
- **Verification Errors** → [Troubleshooting: Verification](Troubleshooting.md#script-verification-errors)
- **Performance Issues** → [Troubleshooting: Performance](Troubleshooting.md#performance-issues)
- **Compatibility** → [Troubleshooting: Compatibility](Troubleshooting.md#compatibility-issues)

### Integration
- **Spring Boot** → [FAQ: Spring Boot](FAQ.md#can-i-use-btrace-with-spring-boot-applications)
- **Third-Party Libraries** → [FAQ: Third-Party](FAQ.md#how-do-i-trace-methods-from-third-party-libraries)
- **JMX Export** → [Quick Reference: @Export](QuickReference.md#export), [FAQ: Monitoring Integration](FAQ.md#can-i-integrate-btrace-with-monitoring-systems)
- **Service Mesh** → [FAQ: Service Mesh](FAQ.md#does-btrace-work-with-service-meshes-istiolinkerd)

## Sample Scripts

BTrace includes 50+ sample scripts demonstrating real-world use cases:
- Location: `btrace-dist/src/main/resources/samples/`
- Examples: File I/O tracking, JDBC monitoring, HTTP requests, memory allocation, thread analysis
- Browse: [BTrace Samples Directory](../btrace-dist/src/main/resources/samples/)

## External Resources

### Official Resources
- **GitHub Repository**: [github.com/btraceio/btrace](https://github.com/btraceio/btrace)
- **Wiki (External)**: [github.com/btraceio/btrace/wiki](https://github.com/btraceio/btrace/wiki/Home)
- **Maven Plugin**: [github.com/btraceio/btrace-maven](https://github.com/btraceio/btrace-maven)
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
2. Read the [Contributing Guidelines](../README.md#contributing---important)
3. Fork the repository and create a pull request
4. See [Build Instructions](../README.md#building-btrace) for development setup

## Version Information

- **Current Version**: Check [GitHub Releases](https://github.com/btraceio/btrace/releases/latest)
- **Java Compatibility**: Java 8-20
- **License**: GPLv2 with Classpath Exception

## Documentation Feedback

Found an issue with the documentation? Please:
- Report it on [GitHub Issues](https://github.com/btraceio/btrace/issues)
- Tag it with `documentation` label
- Or submit a pull request with improvements

---

**Ready to get started?** → [Getting Started Guide](GettingStarted.md)
