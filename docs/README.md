# BTrace Documentation

Welcome to the BTrace documentation! BTrace is a safe, dynamic tracing tool for the Java platform that allows you to instrument running applications without stopping them.

## Quick Start

**New to BTrace?** Start here → [Getting Started Guide](GettingStarted.md)

Get up and running in 5 minutes with installation, your first script, and common usage patterns.

## Documentation Map

| Document | Description | Target Audience |
|----------|-------------|-----------------|
| **[Getting Started](GettingStarted.md)** | Installation, first script, deployment modes, common pitfalls | New users, quick start |
| **[Oneliner Guide](OnelinerGuide.md)** | DTrace-style oneliners for quick debugging without scripts | Quick debugging, ops/SRE |
| **[Quick Reference](QuickReference.md)** | Annotations, patterns, CLI commands, built-in functions | Experienced users, quick lookup |
| **[BTrace Tutorial](BTraceTutorial.md)** | Comprehensive lessons covering all features | All users, in-depth learning |
| **[Troubleshooting Guide](Troubleshooting.md)** | Common errors, debugging, performance, compatibility | Problem-solving, debugging |
| **[FAQ](FAQ.md)** | Common questions, best practices, comparisons | All users, decision-making |
| **[MCP Server](MCPServer.md)** | Using BTrace from AI clients via the Model Context Protocol | AI-assisted debugging, LLM integrations |
| **[Migration Guide 2.x → 3.0](Migration-2.x-to-3.0.md)** | Upgrading from BTrace 2.x: package rename, Java version policy, breaking changes | Existing 2.x users |
| **[Extension Development Guide](BTraceExtensionDevelopmentGuide.md)** | Writing, packaging, and publishing BTrace extensions | Extension authors, platform maintainers |

## Learning Paths

### I'm New to BTrace
1. Read [Getting Started Guide](GettingStarted.md) (10 minutes)
2. Try the 5-minute quick start example
3. Learn [Oneliner syntax](OnelinerGuide.md) for quick debugging (5 minutes)
4. Explore [BTrace Tutorial](BTraceTutorial.md) lessons 1-3
5. Keep [Quick Reference](QuickReference.md) handy

Tip: Want latency histograms fast? See [Quick Start: Histogram Metrics Extension](GettingStarted.md#quick-start-histogram-metrics-extension) and the tutorial section [Using the Histogram Metrics Extension](BTraceTutorial.md#using-the-histogram-metrics-extension-btrace-metrics).

### I Need to Solve a Problem
1. Check [Troubleshooting Guide](Troubleshooting.md) for your error
2. Search [FAQ](FAQ.md) for similar issues
3. Review [Getting Started](GettingStarted.md) common pitfalls
4. Ask on [Slack](http://btrace.slack.com/) or [Gitter](https://gitter.im/btraceio/btrace)

### I Need a Quick Lookup
- **Quick Debug?** → [Oneliner Guide](OnelinerGuide.md) for DTrace-style one-line commands
- **Annotations?** → [Quick Reference: Core Annotations](QuickReference.md#core-annotations)
- **CLI Commands?** → [Quick Reference: CLI Commands](QuickReference.md#cli-commands)
- **Common Patterns?** → [Quick Reference: Common Patterns](QuickReference.md#common-patterns)
- **Built-in Functions?** → [Quick Reference: Built-in Functions](QuickReference.md#built-in-functions)

### I Want Advanced Features
1. **JFR Integration** → [Getting Started: JFR Integration](GettingStarted.md#advanced-jfr-integration), [Tutorial Lesson 5](BTraceTutorial.md)
2. **Sampling** → [Quick Reference: @Sampled](QuickReference.md#sampled), [FAQ: Performance](FAQ.md#performance-issues)
3. **Aggregations** → [Quick Reference: Aggregation Functions](QuickReference.md#aggregation-functions)
4. **Cloud Deployments** → [Getting Started: K8s](GettingStarted.md#btrace-in-containers-and-kubernetes), [FAQ: K8s](FAQ.md#can-i-use-btrace-with-microservices)
5. **Fat Agent JAR** → [Getting Started: Fat Agent](GettingStarted.md#fat-agent-jar-single-jar-deployment) for single-JAR deployment
6. **Level Filtering** → [Quick Reference: @Level](QuickReference.md#level)
7. **Extensions Architecture** → [Extension invokedynamic Bridge](architecture/ExtensionInvokeDynamicBridge.md)

### I'm Working on AI/LLM Applications
1. **Observe LLM API calls** → [Tutorial Lesson 12.1](BTraceTutorial.md#121-llm-inference-tracing-btrace-llm-trace) — token counts, latency, cost tracking
2. **Observe RAG pipelines** → [Tutorial Lesson 12.2](BTraceTutorial.md#122-rag-pipeline-observability-btrace-rag-quality) — vector DB queries, similarity scores
3. **Observe on-device inference** → [Tutorial Lesson 12.3](BTraceTutorial.md#123-gpu-and-inference-observability-btrace-gpu-bridge) — ONNX, DJL, TensorFlow
4. **Use BTrace from an AI agent** → [MCP Server](MCPServer.md) for Claude Desktop, Claude Code, Cursor

## Documentation by Topic

### Core Features
- **Method Tracing** → [Tutorial Lesson 1](BTraceTutorial.md), [Quick Reference: @OnMethod](QuickReference.md#onmethod)
- **Timing & Duration** → [Quick Reference: @Duration](QuickReference.md#parameter-annotations), [Pattern: Method Timing](QuickReference.md#pattern-1-method-entryexit-timing)
- **Exception Tracking** → [Quick Reference: Kind.ERROR](QuickReference.md#location-kinds), [Pattern: Exception Tracking](QuickReference.md#pattern-3-exception-tracking)
- **Field Access** → [Quick Reference: Kind.FIELD_GET/SET](QuickReference.md#location-kinds)

### Advanced Features
- **JFR Integration** → [Getting Started: JFR](GettingStarted.md#advanced-jfr-integration), [Quick Reference: @Event](QuickReference.md#event), [FAQ: JFR](FAQ.md#jfr-integration)
- **Sampling** → [Quick Reference: @Sampled](QuickReference.md#sampled), [FAQ: Performance](FAQ.md#btrace-causes-significant-slowdown)
- **Level Control** → [Quick Reference: @Level](QuickReference.md#level)
- **Aggregations** → [Quick Reference: Aggregation Functions](QuickReference.md#aggregation-functions)
- **Periodic Events** → [Quick Reference: @OnTimer](QuickReference.md#ontimer), [@PeriodicEvent](QuickReference.md#periodicevent)
- **Runtime Contracts** → [Tutorial Lesson 11](BTraceTutorial.md#lesson-11--runtime-contracts-btrace-contracts) — latency budgets, call-rate limits, assertions, tagged path profiling

### AI/LLM Observability
- **LLM Inference Tracing** → [Tutorial Lesson 12.1](BTraceTutorial.md#121-llm-inference-tracing-btrace-llm-trace) — token counts, latency, cost, streaming TTFT
- **RAG Pipeline Observability** → [Tutorial Lesson 12.2](BTraceTutorial.md#122-rag-pipeline-observability-btrace-rag-quality) — vector DB queries, similarity scores, empty retrievals
- **GPU / Inference Observability** → [Tutorial Lesson 12.3](BTraceTutorial.md#123-gpu-and-inference-observability-btrace-gpu-bridge) — ONNX Runtime, DJL, TensorFlow, Panama FFM
- **AI Agents (MCP)** → [MCP Server](MCPServer.md) — connect Claude Desktop, Claude Code, or Cursor to live JVMs

### Deployment & Operations
- **Installation** → [Getting Started: Installation](GettingStarted.md#installation)
- **Deployment Modes** → [Getting Started: Running BTrace](GettingStarted.md#four-ways-to-run-btrace)
- **Fat Agent (Single-JAR)** → [Getting Started: Fat Agent](GettingStarted.md#fat-agent-jar-single-jar-deployment), [Architecture](architecture/fat-agent-plugin.md), [Gradle Plugin](../btrace-gradle-plugin/README.md)
- **Docker & Containers** → [Getting Started: Containers](GettingStarted.md#btrace-in-containers-and-kubernetes)
- **Kubernetes** → [Getting Started: K8s](GettingStarted.md#btrace-in-containers-and-kubernetes), [FAQ: Microservices](FAQ.md#can-i-use-btrace-with-microservices), [Troubleshooting: K8s](Troubleshooting.md#kubernetes-and-cloud-deployments)
- **Performance Tuning** → [FAQ: Performance Impact](FAQ.md#whats-the-performance-impact-of-btrace), [Troubleshooting: Performance](Troubleshooting.md#performance-issues)
- **Extensions CLI (btracex)** → [Permission Policy](PermissionPolicy.md) for allow/deny and quick inspection

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
- **AI / LLM Clients (MCP)** → [MCP Server](MCPServer.md) for Claude Desktop, Claude Code, Cursor
- **Published Extensions** → [Extension Development Guide](BTraceExtensionDevelopmentGuide.md) for writing and publishing extensions

### Architecture
- **Masked JAR** → [Masked JAR Architecture](architecture/MaskedJarArchitecture.md) — single-JAR distribution with classdata masking
- **v2 Binary Protocol** → [Version 2 Protocol Architecture](architecture/Version2ProtocolArchitecture.md) — custom binary serialization
- **Extension Framework** → [Extension invokedynamic Bridge](architecture/ExtensionInvokeDynamicBridge.md), [Extension Configuration](architecture/ExtensionConfiguration.md), [Extension Manifest](architecture/ExtensionManifestFormat.md), [Extension Storage](architecture/ExtensionStorageDesign.md)
- **Extension Catalog** → [Extension Development Guide](BTraceExtensionDevelopmentGuide.md) for packaging and distribution
- **Instrumentation** → [BTrace Instrumentation Analysis](architecture/BTraceInstrAnalysis.md)
- **Instrumentation Backends (ASM / ClassFile API)** → [Instrumentation Backends](architecture/InstrumentationBackends.md) — automatic backend selection for future JDK class files

### Hands-On Tutorials

- **[Hands-on tutorial series](tutorials/README.md)** — scenario-driven labs with a runnable demo app; start with [Your First Trace in 2 Minutes](tutorials/01-first-trace-in-2-minutes.md)

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
- **Maven fat-agent status**: withdrawn for 3.0.0; see [Getting Started](GettingStarted.md#maven-builds)
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
- **Java Compatibility**: BTrace 3.0 runs on Java 8–25+. Running BTrace against a JVM older than Java 17 is deprecated: it continues to work throughout 3.x but emits a deprecation warning. Support for Java < 17 will be removed in the next major release (4.0). See the [migration guide](Migration-2.x-to-3.0.md).
- **License**: Apache License 2.0

## Documentation Feedback

Found an issue with the documentation? Please:
- Report it on [GitHub Issues](https://github.com/btraceio/btrace/issues)
- Tag it with `documentation` label
- Or submit a pull request with improvements

---

**Ready to get started?** → [Getting Started Guide](GettingStarted.md)
