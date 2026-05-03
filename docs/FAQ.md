# BTrace Frequently Asked Questions (FAQ)

## General Questions

### What is BTrace?
BTrace is a safe, dynamic tracing framework for the Java platform. It allows you to dynamically instrument running Java applications to inject tracing code without stopping, recompiling, or modifying the application.

### Is BTrace safe to use in production?
Yes, with proper precautions:
- **Safe mode** (default): BTrace enforces strict safety checks (no threads, no I/O, no loops)
- **Sampling**: Use `@Sampled` to reduce overhead
- **Level control**: Use `@Level` to enable/disable instrumentation dynamically
- **Test first**: Always test scripts in non-production environments first
- **Monitor overhead**: Watch for performance impact

**Not recommended in production:**
- Unsafe mode (`-u` flag)
- Tracing very high-frequency methods
- Scripts without sampling on hot paths

### How does BTrace differ from traditional logging?
| Aspect | BTrace | Traditional Logging |
|--------|--------|-------------------|
| Code changes | None required | Requires code modification |
| Deployment | Dynamic attachment | Requires redeployment |
| Overhead when disabled | Zero | Some (log statements still evaluated) |
| Production use | Can add/remove anytime | Fixed at compile time |
| Flexibility | Change what to log without restart | Fixed logging points |
| Learning curve | Steeper | Simpler |

### Does BTrace work with all Java applications?
BTrace works with:
- Java 8 through Java 20
- Standard JVMs (HotSpot, OpenJDK)
- Most application frameworks (Spring, Java EE, etc.)
- Containerized applications (Docker, Kubernetes)

Limitations:
- Requires JDK (not JRE) for attach mode
- Cannot instrument native methods
- Some restrictions on boot classpath classes

### What's the performance impact of BTrace?
Impact depends on:
- **Number of instrumented methods**: More = higher overhead
- **Method frequency**: Hot paths = significant impact
- **Handler complexity**: Simple handlers = lower overhead
- **Sampling rate**: Lower rate = lower overhead

Typical overhead:
- Well-designed scripts: <5% in production
- Aggressive instrumentation: 10-50%
- Poor practices (tracing String.charAt): Can bring system to halt

**Best practices to minimize overhead:**
```java
// Use sampling
@Sampled(kind = Sampled.Sampler.Adaptive)

// Use level control
@OnMethod(enableAt = @Level(">=1"))

// Aggregate instead of printing each event
@OnTimer(5000)
public static void printSummary() { }
```

## Usage Questions

### Can I use BTrace with Spring Boot applications?
Yes. BTrace works well with Spring Boot:

```java
// Trace Spring controller methods
@OnMethod(clazz = "@org.springframework.web.bind.annotation.RestController",
         method = "@org.springframework.web.bind.annotation.GetMapping")

// Trace Spring services
@OnMethod(clazz = "@org.springframework.stereotype.Service", method = "/.*/")

// Trace specific beans
@OnMethod(clazz = "com.example.MyService", method = "process")
```

For containerized Spring Boot:
```bash
docker exec -it <container> btrace <PID> script.java
```

### How do I trace methods from third-party libraries?
Specify the fully qualified class name:

```java
// Trace JDBC
@OnMethod(clazz = "java.sql.Statement", method = "execute.*")

// Trace HTTP clients
@OnMethod(clazz = "org.apache.http.impl.client.CloseableHttpClient",
         method = "execute")

// Trace logging frameworks
@OnMethod(clazz = "org.slf4j.impl.Log4jLoggerAdapter", method = "error")
```

### Can I modify method behavior with BTrace?
No, BTrace is **read-only**. You can:
- Observe method calls
- Read arguments and return values
- Track timing and frequency
- Collect statistics

You cannot:
- Modify arguments
- Change return values
- Skip method execution
- Modify application state

For runtime behavior modification, consider:
- Byteman
- AspectJ with load-time weaving
- Java agents with ASM/Byte Buddy

### How do I pass arguments to BTrace scripts?
```java
// Script with property
import io.btrace.core.annotations.*;

@BTrace
public class MyTrace {
    @Property
    public static String filterValue = "default";

    @OnMethod(...)
    public static void handler(String arg) {
        if (arg.equals(filterValue)) {
            println("Matched: " + arg);
        }
    }
}
```

Pass via command line:
```bash
btrace <PID> MyTrace.java filterValue=custom
```

Or via agent:
```bash
java -javaagent:btrace.jar=script=MyTrace.class,filterValue=custom MyApp
```

### Can I use BTrace with microservices?
Yes, BTrace works well in microservice architectures:

**Per-service tracing:**
```bash
# Trace specific service
kubectl exec -it pod-name -- btrace <PID> script.java
```

**Common use cases:**
- Trace REST API endpoints
- Monitor service-to-service calls
- Track latency distributions
- Debug intermittent failures

**Considerations:**
- Each JVM needs separate BTrace attachment
- Use service mesh for distributed tracing (Jaeger, Zipkin)
- BTrace is best for deep debugging, not observability

### How do I trace constructors?
Use `<init>` for instance constructors, `<clinit>` for static initializers:

```java
// Instance constructor
@OnMethod(clazz = "com.example.MyClass", method = "<init>")
public static void onNew() {
    println("Object created");
}

// Static initializer
@OnMethod(clazz = "com.example.MyClass", method = "<clinit>")
public static void onStaticInit() {
    println("Class initialized");
}
```

### Can I save BTrace output to a file?
Yes, redirect output:

```bash
# Redirect to file
btrace <PID> script.java > output.txt

# Or use -o flag
btrace -o output.txt <PID> script.java
```

For programmatic file writing (requires unsafe mode):
```bash
btrace -u <PID> script.java
```

```java
import java.io.*;

@BTrace(unsafe = true)
public class UnsafeFileWriter {
    private static FileWriter fw;

    @OnMethod(...)
    public static void handler() throws Exception {
        if (fw == null) {
            fw = new FileWriter("/tmp/trace.log");
        }
        fw.write("Event\n");
        fw.flush();
    }
}
```

## Best Practices

### When should I use BTrace?

**Ideal use cases:**
- Production debugging (intermittent issues)
- Performance analysis (method timing, hotspots)
- Understanding third-party library behavior
- Troubleshooting without source code access
- Auditing method calls
- Learning how frameworks work

**Not ideal for:**
- Permanent monitoring (use APM tools: New Relic, DataDog)
- Distributed tracing (use Zipkin, Jaeger)
- Complex business logic (refactor application)
- Modifying application behavior (use AOP)
- Long-term data collection (use metrics libraries)

## Extensions and Injection

### How do I inject extension services?
Use `@Injected` without parameters for all services/extensions. The invokedynamic injector
detects how to construct injected services and extensions. If an extension requires runtime
context, it is initialized via `Extension.initialize(ExtensionContext)`.

See also: Architecture → `architecture/ExtensionInvokeDynamicBridge.md` and
Extension Development Guide → `btrace-extension-development-guide.md`.

### What are BTrace anti-patterns?

**1. Tracing everything**
```java
// BAD
@OnMethod(clazz = "/.*/", method = "/.*/")
public static void traceAll() { }
```

**2. Expensive operations in handlers**
```java
// BAD
@OnMethod(...)
public static void handler() {
    for (int i = 0; i < 1000000; i++) {  // Expensive loop
        // ...
    }
}
```

**3. No sampling on hot paths**
```java
// BAD - called millions of times
@OnMethod(clazz = "com.example.HotClass", method = "hotMethod")
public static void handler() { }

// GOOD
@Sampled(kind = Sampled.Sampler.Adaptive)
@OnMethod(clazz = "com.example.HotClass", method = "hotMethod")
public static void handler() { }
```

**4. Printing large objects**
```java
// BAD - object might be huge
@OnMethod(...)
public static void handler(Object obj) {
    println(str(obj));  // Could be megabytes
}

// GOOD
@OnMethod(...)
public static void handler(Object obj) {
    println(name(classOf(obj)) + "@" + str(identityHashCode(obj)));
}
```

**5. Forgetting to detach**
- BTrace keeps instrumentation active even after client disconnects
- Always properly exit: Ctrl+C → type `exit`

### How do I organize BTrace scripts?

**Directory structure:**
```
btrace-scripts/
├── common/
│   ├── MethodTiming.java
│   └── ExceptionTracker.java
├── database/
│   ├── SQLLogger.java
│   └── ConnectionPoolMonitor.java
├── http/
│   ├── RequestLogger.java
│   └── ResponseTimeTracker.java
└── memory/
    └── AllocationTracker.java
```

**Naming convention:**
- Descriptive names: `HttpRequestTracker` not `Script1`
- Purpose-based: `SlowQueryDetector` not `DatabaseTrace`
- Include target: `SpringControllerTimer`

**Script template:**
```java
/*
 * Purpose: [What this script does]
 * Target: [Which application/class]
 * Usage: btrace <PID> ScriptName.java
 * Author: [Your name]
 * Date: [Creation date]
 */

import io.btrace.core.annotations.*;
import static io.btrace.core.BTraceUtils.*;

@BTrace
public class ScriptName {
    // Script implementation
}
```

## Comparison with Other Tools

### BTrace vs. Java Flight Recorder (JFR)

| Feature | BTrace | JFR |
|---------|--------|-----|
| Custom instrumentation | Yes | Limited (custom events) |
| Production overhead | Variable (1-50%) | Very low (<1%) |
| Learning curve | Moderate | Low |
| Built into JDK | No | Yes (Java 11+) |
| Dynamic attachment | Yes | Yes |
| Historical data | No | Yes (continuous recording) |
| GUI tools | Limited | Mission Control |

**When to use BTrace over JFR:**
- Need custom instrumentation points
- Want to trace specific business logic
- Need to instrument third-party libraries
- Want scripting flexibility

**When to use JFR over BTrace:**
- Need low-overhead continuous monitoring
- Want comprehensive JVM metrics
- Prefer GUI-based analysis
- Need thread dumps and allocation profiling

### BTrace vs. AspectJ

| Feature | BTrace | AspectJ |
|---------|--------|---------|
| Runtime attachment | Yes | No (requires weaving) |
| Code modification | No | Yes |
| Deployment | Dynamic | Compile-time or load-time |
| Safety checks | Yes (enforced) | No |
| Complexity | Low-Medium | High |
| Use in production | Yes (safe mode) | Yes |

**Use BTrace when:**
- Need dynamic attachment to running JVM
- Want to avoid modifying application
- Need temporary instrumentation

**Use AspectJ when:**
- Want to modify behavior (not just observe)
- Need permanent cross-cutting concerns
- Acceptable to weave at compile/load time

### BTrace vs. Byteman

| Feature | BTrace | Byteman |
|---------|--------|---------|
| Primary purpose | Tracing/monitoring | Testing/fault injection |
| Safety | Enforced (safe mode) | Optional |
| Script language | Java | Rule-based DSL |
| Behavior modification | No | Yes |
| Learning curve | Medium | Medium-High |

**Use BTrace when:**
- Production monitoring and debugging
- Want Java-based scripts
- Need enforced safety

**Use Byteman when:**
- Testing (fault injection)
- Simulating failures
- Need to modify behavior

## Troubleshooting

### Why isn't my script producing output?
See [Troubleshooting Guide](Troubleshooting.md#no-output-from-scripts) for detailed solutions.

Quick checklist:
1. Class/method names correct and fully qualified?
2. Method actually being called?
3. Imports included?
4. Using regex correctly (escaped dots)?
5. Tried verbose mode: `btrace -v`?

### How do I debug a BTrace script?
```java
// Add debug output
@OnMethod(...)
public static void handler() {
    println("Handler called!");
    println("Thread: " + threadName());
    println("Stack:");
    jstack(3);
}

// Start simple, add complexity
@OnTimer(1000)
public static void heartbeat() {
    println("Script alive at " + timestamp());
}
```

### Can I use BTrace with Java 17+?
Yes, BTrace supports Java 8-20. For Java 9+ you may need to add module opens:

```bash
btrace --add-opens java.base/java.lang=ALL-UNNAMED \
       --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
       <PID> script.java
```

Or add to target JVM startup options.

## Advanced Topics

### Can I integrate BTrace with monitoring systems?
Yes, use `@Export` to expose metrics via JMX:

```java
@BTrace
public class MetricsExporter {
    @Export
    private static long requestCount;

    @Export
    private static long errorCount;

    @OnMethod(clazz = "com.example.Service", method = "handleRequest")
    public static void onRequest() {
        requestCount++;
    }

    @OnMethod(clazz = "com.example.Service", method = "handleRequest",
             location = @Location(Kind.ERROR))
    public static void onError() {
        errorCount++;
    }
}
```

Then access via JMX or integrate with monitoring tools.

### How does BTrace integrate with JFR?

BTrace has first-class support for Java Flight Recorder (JFR), allowing you to create custom JFR events with <1% overhead.

**Example:**
```java
import io.btrace.core.jfr.JfrEvent;

@BTrace
public class JfrIntegration {
    @Event(
        name = "MethodExecution",
        label = "Method Execution Event",
        category = {"myapp", "performance"},
        fields = {
            @Event.Field(type = Event.FieldType.STRING, name = "method"),
            @Event.Field(type = Event.FieldType.LONG, name = "duration")
        }
    )
    private static JfrEvent.Factory execEvent;

    @TLS private static long startTime;

    @OnMethod(clazz = "com.example.Service", method = "process")
    public static void onEntry() {
        startTime = timeNanos();
    }

    @OnMethod(clazz = "com.example.Service", method = "process",
             location = @Location(Kind.RETURN))
    public static void onReturn(@ProbeMethodName String method) {
        JfrEvent event = Jfr.prepareEvent(execEvent);
        if (Jfr.shouldCommit(event)) {
            Jfr.setEventField(event, "method", method);
            Jfr.setEventField(event, "duration", timeNanos() - startTime);
            Jfr.commit(event);
        }
    }
}
```

See [Getting Started: JFR Integration](GettingStarted.md#advanced-jfr-integration) for complete guide.

### When should I use JFR events vs. println output?

**Use JFR events when:**
- Performance is critical (<1% overhead vs. 1-50% for println)
- You need timeline visualization in JDK Mission Control
- You want offline analysis of captured events
- You're collecting high-frequency data
- You need correlation with other JVM events

**Use println when:**
- Quick debugging with immediate console output
- Low-frequency events
- Simple ad-hoc tracing
- No need for structured data

**Performance comparison:**
```
JFR events:      <1% overhead
println:         1-50% overhead (depends on frequency)
Aggregations:    ~1-5% overhead
```

### What's the performance difference between JFR and regular BTrace output?

JFR events are significantly faster because:
1. **Native recording**: No string formatting or I/O operations
2. **Binary format**: Events stored in compact binary format
3. **Async writing**: Events written asynchronously by JVM
4. **Filtering**: `shouldCommit()` allows JVM-level filtering

**Benchmark results** (1M events):
- JFR events: ~50ms overhead
- println to console: ~5000ms overhead
- println to file: ~500ms overhead

### Can I view BTrace JFR events in JDK Mission Control?

Yes! BTrace JFR events appear alongside standard JVM events in Mission Control:

**Steps:**
1. Run BTrace script with JFR events
2. Start or dump JFR recording:
   ```bash
   jcmd <PID> JFR.start name=my-recording
   jcmd <PID> JFR.dump name=my-recording filename=recording.jfr
   ```
3. Open `recording.jfr` in JDK Mission Control
4. Navigate to Event Browser → find your custom events

Your BTrace events will have the category you specified (e.g., "myapp") and all defined fields.

### What Java versions support BTrace JFR integration?

**Supported:**
- OpenJDK 8 with backported JFR ✅
- Java 11+ ✅

**Not supported:**
- Java 9-10 ❌ (JFR introduced in Java 11, backported to OpenJDK 8)

BTrace gracefully degrades on Java 9-10: JFR event methods return empty events that are silently ignored.

**Version detection:**
```java
// BTrace automatically handles version differences
// No code changes needed for different Java versions
```

### Does BTrace work with JDK 21 and newer versions?

**Yes**, but with important considerations due to **JEP 451** (introduced in JDK 21):

**Current behavior (JDK 21+):**
- Dynamic agent loading (BTrace attach mode) still **works** but triggers warnings:
  ```
  WARNING: A Java agent has been loaded dynamically
  ```
- The warning advises using `-XX:+EnableDynamicAgentLoading` flag

**Solution for JDK 21+:**
```bash
# Start your application with this flag to suppress warnings
java -XX:+EnableDynamicAgentLoading -jar your-application.jar
```

**Future behavior:**
In a future JDK release, dynamic agent loading will be **disabled by default**. The `-XX:+EnableDynamicAgentLoading` flag will be **required** to use BTrace's attach mode.

**Alternatives:**
1. **Use agent mode** (no attach warnings):
   ```bash
   java -javaagent:/path/to/btrace.jar=script=YourScript.class -jar app.jar
   ```
2. **Prepare now**: Add `-XX:+EnableDynamicAgentLoading` to your JVM startup scripts for future compatibility

For details, see [JEP 451: Prepare to Disallow the Dynamic Loading of Agents](https://openjdk.org/jeps/451) and [Troubleshooting: JVM Attachment Issues](Troubleshooting.md#jvm-attachment-issues).

### How do I use BTrace in Kubernetes?

**Basic pattern:**
```bash
# Find pod and process
kubectl get pods
kubectl exec <pod-name> -- jps

# Run BTrace
kubectl exec -it <pod-name> -- btrace <PID> script.java
```

**Copy script to pod:**
```bash
kubectl cp MyTrace.java <pod-name>:/tmp/
kubectl exec -it <pod-name> -- btrace <PID> /tmp/MyTrace.java
```

**Trace multiple pods:**
```bash
for POD in $(kubectl get pods -l app=myapp -o name | cut -d/ -f2); do
  kubectl exec $POD -- btrace 1 script.java &
done
```

**Prerequisites:**
- JDK (not JRE) must be in container
- BTrace must be available in container image
- Same user permissions as target JVM
- `shareProcessNamespace: true` for sidecar pattern

See [Getting Started: Kubernetes](GettingStarted.md#btrace-in-containers-and-kubernetes) and [Troubleshooting: Kubernetes](Troubleshooting.md#kubernetes-and-cloud-deployments) for comprehensive guides.

### Can I trace multiple pods simultaneously?

Yes, using batch scripts or parallel execution:

**Shell script approach:**
```bash
#!/bin/bash
DEPLOYMENT=$1
SCRIPT=$2

PODS=$(kubectl get pods -l app=$DEPLOYMENT -o jsonpath='{.items[*].metadata.name}')

for POD in $PODS; do
  echo "Tracing $POD..."
  kubectl exec $POD -- btrace 1 $SCRIPT > $POD.log 2>&1 &
done

wait
echo "All traces complete"
```

**ConfigMap pattern:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: btrace-scripts
data:
  trace.java: |
    @BTrace
    public class Trace {
        // script content
    }
```

Then mount and use across all pods. See [Troubleshooting: Batch Tracing](Troubleshooting.md#batch-tracing-across-pods).

### What about sidecar vs. init container patterns for BTrace?

**Sidecar Container** (Recommended for persistent tracing):
```yaml
spec:
  shareProcessNamespace: true
  containers:
  - name: app
    image: myapp:latest
  - name: btrace
    image: bellsoft/liberica-openjdk-debian:11-cds
    command: ["/bin/sh", "-c", "sleep infinity"]
```

**Pros:** Always available, can attach/detach anytime
**Cons:** Extra resource usage
**Use when:** Need on-demand tracing capability

**Init Container** (For startup tracing):
```yaml
spec:
  initContainers:
  - name: setup-btrace
    image: btrace:latest
    command: ["cp", "-r", "/opt/btrace", "/shared"]
    volumeMounts:
    - name: shared
      mountPath: /shared
```

**Pros:** No runtime overhead
**Cons:** Only for startup instrumentation
**Use when:** Debugging initialization issues

**Agent Mode** (For permanent instrumentation):
```yaml
env:
- name: JAVA_TOOL_OPTIONS
  value: "-javaagent:/opt/btrace.jar=script=/scripts/trace.class"
```

**Pros:** Active from process start
**Cons:** Requires pod restart to change
**Use when:** Need continuous tracing

### How do I integrate BTrace with Prometheus/Grafana?

BTrace doesn't have direct Prometheus integration, but you can use **StatSD** as a bridge:

**Option 1: StatSD → Prometheus**
```java
@BTrace
public class MetricsExporter {
    @OnMethod(clazz = "com.example.Service", method = "handleRequest")
    public static void onRequest() {
        // Send to StatSD (configure with -statsd flag)
        Statsd.increment("requests.total");
    }

    @OnMethod(clazz = "com.example.Service", method = "handleRequest",
             location = @Location(Kind.ERROR))
    public static void onError() {
        Statsd.increment("requests.errors");
    }
}
```

Run with:
```bash
btrace -statsd statsd-exporter:8125 <PID> MetricsExporter.java
```

Then use [statsd_exporter](https://github.com/prometheus/statsd_exporter) to export to Prometheus.

**Option 2: JMX → Prometheus**
```java
@BTrace
public class JmxExporter {
    @Export
    private static long requestCount;

    @OnMethod(...)
    public static void handler() {
        requestCount++;
    }
}
```

Use [jmx_exporter](https://github.com/prometheus/jmx_exporter) to scrape JMX metrics.

**Recommended:** For permanent monitoring, use APM tools (New Relic, DataDog) instead. BTrace is best for ad-hoc debugging.

### Does BTrace work with service meshes (Istio/Linkerd)?

Yes, BTrace works with service meshes without interference:

**Why it works:**
- Service mesh operates at network layer (L7)
- BTrace operates at JVM bytecode level
- BTrace uses local sockets (not HTTP)
- No interaction between mesh sidecars and BTrace

**Considerations:**
1. **Resource limits**: BTrace overhead may trigger CPU/memory limits
   ```yaml
   resources:
     limits:
       cpu: "500m"      # Increase if needed
       memory: "512Mi"
   ```

2. **mTLS**: Doesn't affect BTrace (uses local communication)

3. **Port conflicts**: BTrace port 2020 not intercepted by mesh

4. **Multi-container pods**: Use `shareProcessNamespace: true` for sidecar pattern

Service mesh telemetry and BTrace serve different purposes:
- **Service mesh**: Service-to-service observability, distributed tracing
- **BTrace**: Deep JVM-level debugging, method-level insights

Use both together for comprehensive observability.

### How do I contribute to BTrace?
1. Sign the [Oracle Contributor Agreement](https://oca.opensource.oracle.com/)
2. Fork the [repository](https://github.com/btraceio/btrace)
3. Create a feature branch
4. Submit a pull request

See [Contributing Guide](../README.md#contributing) for details.

### Where can I find more examples?
- **Sample scripts**: `btrace-dist/src/main/resources/samples/` (50+ examples)
- **Tutorial**: [BTrace Tutorial](BTraceTutorial.md)
- **Wiki**: [BTrace Wiki](https://github.com/btraceio/btrace/wiki)
- **GitHub**: [BTrace Issues](https://github.com/btraceio/btrace/issues) (search for examples)

## Resources

### Documentation
- **[Documentation Hub](README.md)** - Complete documentation map and learning paths
- [Getting Started Guide](GettingStarted.md)
- [Quick Reference](QuickReference.md)
- [Troubleshooting Guide](Troubleshooting.md)
- [BTrace Tutorial](BTraceTutorial.md)
- [BTrace Wiki](https://github.com/btraceio/btrace/wiki/Home)

### Community
- **Slack**: [btrace.slack.com](http://btrace.slack.com/)
- **Gitter**: [gitter.im/btraceio/btrace](https://gitter.im/btraceio/btrace)
- **GitHub**: [github.com/btraceio/btrace](https://github.com/btraceio/btrace)

### Related Projects
- **BTrace Maven Plugin**: [github.com/btraceio/btrace-maven](https://github.com/btraceio/btrace-maven)
- **VisualVM BTrace Plugin**: [visualvm.github.io](https://visualvm.github.io)

## License
BTrace is licensed under GPLv2 with the Classpath Exception. See [LICENSE](../LICENSE) for details.
