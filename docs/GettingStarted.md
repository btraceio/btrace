# BTrace Getting Started Guide

## What is BTrace?

BTrace is a safe, dynamic tracing tool for the Java platform. It allows you to dynamically instrument running Java applications without stopping them, recompiling code, or adding logging statements. BTrace works by injecting tracing code into the bytecode of target applications at runtime.

**Use BTrace when you need to:**
- Debug production issues without redeploying
- Profile application performance in real-time
- Track method calls, arguments, and return values
- Monitor memory allocations and object creation
- Investigate thread behavior and synchronization
- Capture stack traces at specific points

## Prerequisites

- Java 8 or higher (BTrace supports Java 8-20)
- Basic knowledge of Java programming
- Target Java application running with appropriate permissions

## Installation

### Download and Install

1. **Download** the latest release from [GitHub releases](https://github.com/btraceio/btrace/releases/latest)

2. **Extract** the distribution:
   ```bash
   # For .tar.gz
   tar -xzf btrace-<version>.tar.gz

   # For .zip
   unzip btrace-<version>.zip
   ```

3. **Set environment variables** (optional but recommended):
   ```bash
   export BTRACE_HOME=/path/to/btrace
   export PATH=$BTRACE_HOME/bin:$PATH
   ```

### Package Manager Installation

**RPM-based systems (RedHat, CentOS, Fedora):**
```bash
sudo rpm -i btrace-<version>.rpm
```

**Debian-based systems (Ubuntu, Debian):**
```bash
sudo dpkg -i btrace-<version>.deb
```

### Verify Installation

```bash
btrace -h
```

You should see the BTrace help message with available options.

## 5-Minute Quick Start

Let's trace a simple Java application to see BTrace in action.

### Step 1: Prepare a Test Application

Create a simple Java program (`TestApp.java`):

```java
public class TestApp {
    public static void main(String[] args) throws Exception {
        System.out.println("TestApp started. Press Enter to begin...");
        System.in.read();

        while (true) {
            doWork();
            Thread.sleep(1000);
        }
    }

    private static void doWork() {
        String result = processData("example", 42);
        System.out.println("Processed: " + result);
    }

    private static String processData(String name, int value) {
        return name + "-" + value;
    }
}
```

Compile and run it:
```bash
javac TestApp.java
java TestApp
```

### Step 2: Create Your First BTrace Script

Create a BTrace script (`TraceMethods.java`) to trace method calls:

```java
import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.OnMethod;
import static org.openjdk.btrace.core.BTraceUtils.println;
import static org.openjdk.btrace.core.BTraceUtils.str;

@BTrace
public class TraceMethods {
    @OnMethod(clazz = "TestApp", method = "processData")
    public static void onProcessData(String name, int value) {
        println("Called processData: name=" + name + ", value=" + str(value));
    }
}
```

### Step 3: Attach BTrace to the Running Application

1. **Find the process ID** of your TestApp:
   ```bash
   jps
   ```
   Output will show something like:
   ```
   12345 TestApp
   12346 Jps
   ```

2. **Attach BTrace**:
   ```bash
   btrace 12345 TraceMethods.java
   ```

3. **Press Enter** in the TestApp window to start processing

4. **Observe the output** in the BTrace terminal:
   ```
   Called processData: name=example, value=42
   Called processData: name=example, value=42
   ...
   ```

5. **Detach BTrace**: Press `Ctrl+C` in the BTrace terminal and type `exit`

Congratulations! You've successfully traced your first Java application with BTrace.

## Three Ways to Run BTrace

BTrace offers three deployment modes to suit different use cases:

### 1. Attach Mode (Most Common)

Attach to an already running Java process:

```bash
btrace [options] <PID> <script.java> [script-args]
```

**When to use:**
- Debugging production issues
- Ad-hoc performance analysis
- You don't want to restart the application

**Example:**
```bash
btrace -v 12345 MyTrace.java arg1 arg2
```

**Common options:**
- `-v` - Verbose output
- `-p <port>` - Specify port for communication
- `-o <file>` - Redirect output to file

### 2. Java Agent Mode (Pre-compiled Scripts)

Start a Java application with BTrace agent and a pre-compiled script:

```bash
java -javaagent:btrace-agent.jar=script=<script.class>[,arg1=value1]... YourApp
```

**When to use:**
- Tracing from application startup
- Capturing initialization issues
- Controlled environments

**Example:**
```bash
# First compile the script
btracec MyTrace.java

# Then run with agent
java -javaagent:/path/to/btrace-agent.jar=script=MyTrace.class MyApp
```

### 3. Launcher Mode (btracer)

Use the `btracer` wrapper to compile and attach in one step:

```bash
btracer <script.class> <java-app-with-args>
```

**When to use:**
- Local development and testing
- Quick experiments
- You have control over application launch

**Example:**
```bash
# First compile the script
btracec MyTrace.java

# Launch app with trace
btracer MyTrace.class java -cp myapp.jar com.example.Main
```

## Your First BTrace Scripts

### Example 1: Trace Method Entry

```java
import org.openjdk.btrace.core.annotations.*;
import static org.openjdk.btrace.core.BTraceUtils.*;

@BTrace
public class MethodEntry {
    @OnMethod(clazz = "com.example.MyClass", method = "myMethod")
    public static void onEntry() {
        println("Method called!");
    }
}
```

### Example 2: Trace Method Arguments

```java
import org.openjdk.btrace.core.annotations.*;
import static org.openjdk.btrace.core.BTraceUtils.*;

@BTrace
public class MethodArgs {
    @OnMethod(clazz = "com.example.MyClass", method = "calculate")
    public static void onCalculate(int x, int y) {
        println("calculate called with: x=" + str(x) + ", y=" + str(y));
    }
}
```

### Example 3: Trace Method Return Value

```java
import org.openjdk.btrace.core.annotations.*;
import static org.openjdk.btrace.core.BTraceUtils.*;

@BTrace
public class MethodReturn {
    @OnMethod(clazz = "com.example.MyClass", method = "calculate", location = @Location(Kind.RETURN))
    public static void onReturn(@Return int result) {
        println("calculate returned: " + str(result));
    }
}
```

### Example 4: Measure Method Duration

```java
import org.openjdk.btrace.core.annotations.*;
import static org.openjdk.btrace.core.BTraceUtils.*;

@BTrace
public class MethodDuration {
    @OnMethod(clazz = "com.example.MyClass", method = "slowMethod")
    public static void onEntry(@Duration long durationNanos) {
        if (durationNanos > 0) {
            println("slowMethod took: " + str(durationNanos / 1000000) + " ms");
        }
    }
}
```

## Advanced: JFR Integration

BTrace integrates with Java Flight Recorder (JFR) to create high-performance events with <1% overhead. JFR events are recorded natively by the JVM and can be analyzed with JDK Mission Control.

**Requirements:** OpenJDK 8 (with backported JFR) or Java 11+ (not available in Java 9-10)

### Example: Create JFR Event

```java
import org.openjdk.btrace.core.annotations.*;
import org.openjdk.btrace.core.jfr.JfrEvent;
import static org.openjdk.btrace.core.BTraceUtils.*;

@BTrace
public class MyJfrTrace {
    // Define JFR event factory
    @Event(
        name = "MethodCall",
        label = "Method Call Event",
        description = "Tracks method calls with duration",
        category = {"myapp", "performance"},
        fields = {
            @Event.Field(type = Event.FieldType.STRING, name = "method"),
            @Event.Field(type = Event.FieldType.LONG, name = "duration")
        }
    )
    private static JfrEvent.Factory callEventFactory;

    @TLS private static long startTime;

    @OnMethod(clazz = "com.example.MyClass", method = "process")
    public static void onEntry() {
        startTime = timeNanos();
    }

    @OnMethod(clazz = "com.example.MyClass", method = "process",
             location = @Location(Kind.RETURN))
    public static void onReturn(@ProbeMethodName String method) {
        // Create and commit JFR event
        JfrEvent event = Jfr.prepareEvent(callEventFactory);
        if (Jfr.shouldCommit(event)) {
            Jfr.setEventField(event, "method", method);
            Jfr.setEventField(event, "duration", timeNanos() - startTime);
            Jfr.commit(event);
        }
    }
}
```

### Viewing JFR Events

After running the script, JFR events are recorded in the flight recorder:

```bash
# Run BTrace script
btrace <PID> MyJfrTrace.java

# Start flight recording (if not already running)
jcmd <PID> JFR.start name=my-recording

# Dump recording to file
jcmd <PID> JFR.dump name=my-recording filename=recording.jfr

# Analyze with Mission Control
jmc recording.jfr
```

**Benefits over println:**
- <1% overhead vs. 1-50% for println
- Native JVM recording (no string formatting)
- Can be analyzed offline
- Timeline visualization in Mission Control

For complete JFR documentation, see [BTrace Tutorial Lesson 5](BTraceTutorial.md) and [FAQ: JFR Integration](FAQ.md#jfr-integration).

## BTrace in Containers and Kubernetes

BTrace works in containerized environments with some considerations.

### Docker Containers

**Attach to running container:**
```bash
# Find container ID/name
docker ps

# Execute BTrace in container
docker exec -it <container-id> btrace <PID> script.java
```

**Prerequisites:**
- JDK (not JRE) must be installed in container
- BTrace must be available in container or mounted
- Same user permissions as target JVM

**Example Dockerfile with official BTrace images:**
```dockerfile
# Option 1: Copy BTrace into your application image (recommended)
FROM btrace/btrace:latest AS btrace
FROM bellsoft/liberica-openjdk-debian:11-cds

COPY --from=btrace /opt/btrace /opt/btrace
ENV BTRACE_HOME=/opt/btrace
ENV PATH=$PATH:$BTRACE_HOME/bin

# Your application...
COPY target/myapp.jar /app/
ENTRYPOINT ["java", "-jar", "/app/myapp.jar"]
```

**Alternative: Manual installation (if not using official images):**
```dockerfile
FROM bellsoft/liberica-openjdk-debian:11-cds
RUN curl -L https://github.com/btraceio/btrace/releases/download/v2.2.2/btrace-2.2.2.tar.gz \
    | tar -xz -C /opt/
ENV BTRACE_HOME=/opt/btrace-2.2.2
ENV PATH=$PATH:$BTRACE_HOME/bin
```

See [docker/README.md](../docker/README.md) for more Docker usage patterns.

### Kubernetes Pods

**Attach to pod:**
```bash
# Find pod and process ID
kubectl get pods
kubectl exec <pod-name> -- jps

# Run BTrace
kubectl exec -it <pod-name> -- btrace <PID> script.java
```

**Copy script to pod first (if needed):**
```bash
kubectl cp MyTrace.java <pod-name>:/tmp/
kubectl exec -it <pod-name> -- btrace <PID> /tmp/MyTrace.java
```

**Trace multiple pods:**
```bash
# Get all pods for a deployment
PODS=$(kubectl get pods -l app=myapp -o jsonpath='{.items[*].metadata.name}')

# Attach to each pod
for POD in $PODS; do
  echo "Tracing $POD..."
  kubectl exec $POD -- btrace 1 script.java &
done
```

### Sidecar Pattern

Add BTrace as a sidecar container for persistent availability:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
spec:
  template:
    spec:
      shareProcessNamespace: true  # Important: enables cross-container process visibility
      containers:
      - name: app
        image: myapp:latest

      - name: btrace
        image: btrace/btrace:latest-alpine  # Official BTrace Alpine image
        command: ["/bin/sh", "-c", "while true; do sleep 30; done"]
        volumeMounts:
        - name: btrace-scripts
          mountPath: /scripts
      volumes:
      - name: btrace-scripts
        configMap:
          name: btrace-scripts
```

**Note:** Requires `shareProcessNamespace: true` to allow sidecar to see app container processes.

**Using the sidecar:**
```bash
# Execute BTrace from sidecar
kubectl exec <pod-name> -c btrace -- btrace $(pgrep -f myapp) /scripts/trace.btrace

# View output
kubectl logs <pod-name> -c btrace
```

### Common Issues in K8s

1. **PID Discovery**: Use `jps` or `ps aux` to find Java process ID
2. **Port Conflicts**: BTrace uses port 2020 by default; use `-p` flag if needed
3. **Security Policies**: Pod Security Policies may block ptrace; adjust as needed
4. **Resource Limits**: BTrace overhead may trigger CPU/memory limits

For comprehensive troubleshooting, see [Troubleshooting: Kubernetes](Troubleshooting.md#kubernetes-and-cloud-deployments).

## Common Pitfalls and Solutions

### 1. Permission Denied / Attachment Fails

**Problem:** `Unable to attach to target VM`

**Solutions:**
- Ensure BTrace and target app run as the same user
- **JDK 8-20**: Check if target JVM has `-XX:+DisableAttachMechanism` (remove it)
- **JDK 21+**: Add `-XX:+EnableDynamicAgentLoading` to target JVM to suppress warnings and ensure compatibility
- Verify JDK (not JRE) is installed

**Note**: Starting with JDK 21, dynamic agent loading triggers warnings. In a future JDK release, it will be disabled by default, requiring `-XX:+EnableDynamicAgentLoading` to use BTrace's attach mode. See [Troubleshooting: JVM Attachment Issues](Troubleshooting.md#jvm-attachment-issues) for details.

### 2. Script Verification Errors

**Problem:** `BTrace script verification failed`

**Common causes:**
- Using forbidden operations (creating new threads, I/O operations)
- Calling non-BTrace methods
- Using synchronization primitives

**Solution:** Use only BTrace-safe operations from `BTraceUtils` class.

### 3. No Output from Script

**Problem:** Script attaches but produces no output

**Checklist:**
- Verify class and method names are correct (case-sensitive)
- Check if the method is actually being called in the target app
- Use regular expressions carefully: `/com\\.example\\..*/` not `/com.example.*/`
- Ensure `println()` is imported from `BTraceUtils`

### 4. Script Not Finding Classes

**Problem:** Script doesn't match any classes

**Solutions:**
- Use fully qualified class names: `"com.example.MyClass"` not `"MyClass"`
- For inner classes use `$`: `"com.example.Outer$Inner"`
- Test with wildcards: `"/com\\.example\\..*/"`

### 5. Performance Impact

**Problem:** BTrace slows down the application

**Solutions:**
- Use sampling: `@Sampled` annotation
- Add level filtering: `@Level` annotation
- Limit scope: trace specific methods, not all methods
- Avoid tracing high-frequency methods

### 6. Unicode or Special Characters in Output

**Problem:** Garbled output with special characters

**Solution:** Set encoding:
```bash
btrace -Dfile.encoding=UTF-8 <PID> script.java
```

## Next Steps

Now that you have BTrace running, explore these resources:

1. **[BTrace Tutorial](BTraceTutorial.md)** - Progressive lessons covering all features
2. **[Quick Reference](QuickReference.md)** - Annotation and API cheat sheet
3. **[Sample Scripts](../btrace-dist/src/main/resources/samples/)** - 50+ real-world examples
4. **[Troubleshooting Guide](Troubleshooting.md)** - Solutions to common problems
5. **[BTrace Wiki](https://github.com/btraceio/btrace/wiki/Home)** - Comprehensive user guide

## Tips for Success

- **Start simple**: Begin with basic method tracing before complex instrumentation
- **Test locally**: Verify scripts on test applications before production use
- **Use samples**: Browse the 50+ sample scripts for patterns
- **Monitor overhead**: Always measure BTrace's performance impact
- **Keep scripts focused**: One script per specific issue
- **Version control**: Save useful scripts for reuse

## See Also

- **[Documentation Hub](README.md)** - Complete documentation map and learning paths
- **[Quick Reference](QuickReference.md)** - Annotation and API cheat sheet
- **[BTrace Tutorial](BTraceTutorial.md)** - Progressive lessons covering all features
- **[Troubleshooting Guide](Troubleshooting.md)** - Solutions to common problems
- **[FAQ](FAQ.md)** - Common questions and best practices

## Getting Help

- **Slack**: [btrace.slack.com](http://btrace.slack.com/)
- **Gitter**: [gitter.im/btraceio/btrace](https://gitter.im/btraceio/btrace)
- **GitHub Issues**: [github.com/btraceio/btrace/issues](https://github.com/btraceio/btrace/issues)
- **Wiki**: [github.com/btraceio/btrace/wiki](https://github.com/btraceio/btrace/wiki/Home)
