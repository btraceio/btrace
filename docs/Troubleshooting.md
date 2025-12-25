# BTrace Troubleshooting Guide

This guide helps you diagnose and resolve common BTrace issues.

## Table of Contents
1. [JVM Attachment Issues](#jvm-attachment-issues)
2. [Script Compilation Errors](#script-compilation-errors)
3. [Script Verification Errors](#script-verification-errors)
4. [No Output from Scripts](#no-output-from-scripts)
5. [Class/Method Not Found](#classmethod-not-found)
6. [Performance Issues](#performance-issues)
7. [Agent Loading Problems](#agent-loading-problems)
8. [Compatibility Issues](#compatibility-issues)
9. [Debugging BTrace Scripts](#debugging-btrace-scripts)
10. [Known Limitations](#known-limitations)

## JVM Attachment Issues

### Unable to attach to target VM

**Error message:**
```
com.sun.tools.attach.AttachNotSupportedException: Unable to attach to target VM
```

**Causes and solutions:**

1. **Permission mismatch**
   - **Cause**: BTrace and target JVM run as different users
   - **Solution**: Run BTrace as the same user as the target JVM
   ```bash
   # Check process owner
   ps aux | grep <PID>

   # Run as correct user
   sudo -u <username> btrace <PID> script.java
   ```

2. **Attach mechanism disabled or restricted**

   **For JDK 8-20:**
   - **Cause**: Target JVM started with `-XX:+DisableAttachMechanism`
   - **Solution**: Remove this flag and restart the application

   **For JDK 21+ (JEP 451):**
   - **Cause**: Dynamic agent loading triggers warnings (still works but shows warning)
   - **Warning message**: `WARNING: A Java agent has been loaded dynamically`
   - **Solution**: Add `-XX:+EnableDynamicAgentLoading` to target JVM startup to suppress warnings:
     ```bash
     java -XX:+EnableDynamicAgentLoading -jar your-application.jar
     ```
   - **Note**: In a future JDK release, dynamic agent loading will be **disabled by default**. The `-XX:+EnableDynamicAgentLoading` flag will be required to use BTrace's attach mode.
   - **Alternative**: Use BTrace in agent mode (attach at startup) instead of dynamic attach:
     ```bash
     java -javaagent:/path/to/btrace-agent.jar=script=YourScript.class -jar your-application.jar
     ```

3. **JRE instead of JDK**
   - **Cause**: JRE doesn't include attach API tools
   - **Solution**: Install JDK and ensure `JAVA_HOME` points to it
   ```bash
   # Verify JDK installation
   which javac
   echo $JAVA_HOME
   ```

4. **Process in container/namespace**
   - **Cause**: Target process in Docker/different namespace
   - **Solution**: Run BTrace in same container/namespace
   ```bash
   # For Docker
   docker exec -it <container> btrace <PID> script.java
   ```

5. **SELinux/AppArmor restrictions**
   - **Cause**: Security policies prevent ptrace
   - **Solution**: Adjust security policies or disable temporarily
   ```bash
   # Check SELinux
   getenforce

   # Temporarily disable (not recommended for production)
   sudo setenforce 0
   ```

### Connection refused

**Error message:**
```
java.net.ConnectException: Connection refused
```

**Solutions:**
- Check if BTrace client port (default 2020) is available
- Specify different port: `btrace -p 3030 <PID> script.java`
- Check firewall rules

## Script Compilation Errors

### Compilation failed

**Error message:**
```
error: cannot find symbol
```

**Common causes:**

1. **Missing imports**
   ```java
   // Wrong
   @BTrace
   public class MyTrace {
       @OnMethod(...)
       public static void handler() {
           println("test");  // ERROR: cannot find symbol
       }
   }

   // Correct
   import org.openjdk.btrace.core.annotations.*;
   import static org.openjdk.btrace.core.BTraceUtils.*;

   @BTrace
   public class MyTrace {
       @OnMethod(...)
       public static void handler() {
           println("test");  // OK
       }
   }
   ```

2. **Wrong package for annotations**
   ```java
   // Old (pre-2.0)
   import com.sun.btrace.annotations.*;

   // New (2.0+)
   import org.openjdk.btrace.core.annotations.*;
   ```

3. **Classpath issues**
   - **Solution**: Add required JARs to classpath
   ```bash
   btrace -cp /path/to/lib.jar <PID> script.java
   ```

## Script Verification Errors

### BTrace script verification failed

**Error message:**
```
BTrace : verification failed
```

BTrace enforces safety restrictions. Common violations:

### 1. Creating new threads

**Wrong:**
```java
@OnMethod(...)
public static void handler() {
    new Thread(() -> {}).start();  // ERROR: Cannot create threads
}
```

**Right:**
```java
@OnMethod(...)
public static void handler() {
    // Use @OnTimer instead
}

@OnTimer(1000)
public static void periodic() {
    // Executes every second
}
```

### 2. Using synchronization

**Wrong:**
```java
private static Object lock = new Object();

@OnMethod(...)
public static void handler() {
    synchronized (lock) {  // ERROR: Cannot use synchronization
        // ...
    }
}
```

**Right:**
```java
import static org.openjdk.btrace.core.BTraceUtils.Atomic.*;

private static AtomicInteger counter = newAtomicInteger(0);

@OnMethod(...)
public static void handler() {
    incrementAndGet(counter);  // Thread-safe without synchronization
}
```

### 3. Loops

**Wrong:**
```java
@OnMethod(...)
public static void handler() {
    // ERROR: All loops forbidden in safe mode
    while (condition) { }
    for (int i = 0; i < 100; i++) { }
    do { } while (condition);
    for (String s : collection) { }
}
```

**Right:**
```java
@OnMethod(...)
public static void handler() {
    // Use BTraceUtils methods instead
    // For iteration, use unsafe mode (-u flag) if absolutely necessary
}
```

**Note:** ALL loop constructs (`for`, `while`, `do-while`, enhanced `for`) are forbidden in safe mode. Use `-u` (unsafe mode) only in controlled environments if loops are required.

### 4. File I/O operations

**Wrong:**
```java
@OnMethod(...)
public static void handler() {
    FileWriter fw = new FileWriter("out.txt");  // ERROR: No file I/O
}
```

**Right:**
```java
@OnMethod(...)
public static void handler() {
    println("Output");  // Use BTraceUtils output functions
}

// Or run in unsafe mode
// btrace -u <PID> script.java
```

### 5. Calling external methods

**Wrong:**
```java
@OnMethod(...)
public static void handler() {
    MyOtherClass.someMethod();  // ERROR: Cannot call external class methods
    System.out.println("test");  // ERROR: Cannot call System methods
}
```

**Right:**
```java
@BTrace
public class MyTrace {
    @OnMethod(...)
    public static void handler() {
        // Call helper methods within same BTrace class - OK
        myCustomMethod();

        // Use BTraceUtils methods
        println(str(timeMillis()));
    }

    private static void myCustomMethod() {
        // Helper methods in same class are allowed
        println("Helper method called");
    }
}
```

**Note:** You CAN call private static methods within the same BTrace class. You CANNOT call methods from external classes (except BTraceUtils).

### 6. Catching exceptions

**Wrong:**
```java
@OnMethod(...)
public static void handler() {
    try {
        // ...
    } catch (Exception e) {  // ERROR: Cannot catch exceptions
        // ...
    }
}
```

**Right:**
```java
// BTrace handles exceptions automatically
@OnMethod(...)
public static void handler() {
    // Any exception here is caught and logged by BTrace
}

// Or use @OnError to track exceptions
@OnMethod(..., location = @Location(Kind.ERROR))
public static void onError(Throwable t) {
    println("Exception: " + str(t));
}
```

## No Output from Scripts

### Script attaches but produces no output

**Diagnostic checklist:**

1. **Verify method is being called**
   ```java
   // Add entry point logging
   @OnMethod(clazz = "com.example.MyClass", method = "<init>")
   public static void onInit() {
       println("Constructor called - script is working!");
   }
   ```

2. **Check class and method names**
   ```bash
   # Class names are case-sensitive and must be fully qualified

   # Wrong
   @OnMethod(clazz = "MyClass", method = "process")

   # Right
   @OnMethod(clazz = "com.example.pkg.MyClass", method = "process")
   ```

3. **Test with wildcard patterns**
   ```java
   // Too specific - might not match
   @OnMethod(clazz = "com.example.MyClass", method = "processData")

   // Broader - helps identify the issue
   @OnMethod(clazz = "/com\\.example\\..*/", method = "/.*/")
   public static void catchAll() {
       println("Matched something!");
   }
   ```

4. **Verify regex escaping**
   ```java
   // Wrong - dots match any character
   @OnMethod(clazz = "/com.example..*/")

   // Right - dots are escaped
   @OnMethod(clazz = "/com\\.example\\..*/")
   ```

5. **Check for inner classes**
   ```java
   // Inner classes use $ separator
   @OnMethod(clazz = "com.example.Outer$Inner", method = "method")
   ```

6. **Enable verbose output**
   ```bash
   btrace -v <PID> script.java
   ```

7. **Verify instrumention occurred**
   ```bash
   # Dump instrumented classes
   btrace -d /tmp/btrace-dump <PID> script.java

   # Check if classes were modified
   ls -la /tmp/btrace-dump/
   ```

## Class/Method Not Found

### No methods matched

**Error message:**
```
No methods matched for probe: ...
```

**Solutions:**

1. **Use `jcmd` to find exact class names**
   ```bash
   # List loaded classes
   jcmd <PID> VM.classes | grep -i myclass

   # Find specific methods
   jcmd <PID> VM.class_hierarchy | grep -A 5 MyClass
   ```

2. **Verify BTrace is retransforming loaded classes**
   - BTrace scans ALL already-loaded classes when attaching
   - It retransforms matching classes via `Instrumentation.retransformClasses()`
   - It also listens for newly loaded classes
   - If classes aren't being instrumented, check if they are modifiable:
   ```bash
   # Some classes cannot be retransformed (e.g., native methods, JVM internals)
   # Use -v flag to see which classes are being instrumented
   btrace -v <PID> script.java
   ```

3. **Check class loader hierarchy**
   ```java
   // Match classes loaded by any classloader
   @OnMethod(clazz = "+com.example.MyClass", method = "method")
   //         ^ plus sign matches subclasses and all classloaders
   ```

4. **Verify method signature for overloaded methods**
   ```java
   // Multiple methods with same name - need signature
   @OnMethod(clazz = "com.example.MyClass",
            method = "process",
            type = "void (java.lang.String, int)")
   ```

## Performance Issues

### BTrace causes significant slowdown

**Symptoms:**
- Application becomes unresponsive
- High CPU usage
- Increased latency

**Solutions:**

1. **Use sampling**
   ```java
   @Sampled(kind = Sampled.Sampler.Adaptive)
   @OnMethod(...)
   public static void handler() {
       // Only executes on sampled invocations
   }
   ```

2. **Add level filtering**
   ```java
   @OnMethod(clazz = "...", enableAt = @Level(">=2"))
   public static void heavyHandler() {
       // Only active when level >= 2
   }

   // Control at runtime:
   // Press Ctrl-C in btrace console, type: level 0
   ```

3. **Avoid tracing high-frequency methods**
   ```java
   // BAD - called millions of times per second
   @OnMethod(clazz = "java.lang.String", method = "charAt")

   // GOOD - application-specific methods
   @OnMethod(clazz = "com.example.Service", method = "handleRequest")
   ```

4. **Minimize instrumentation scope**
   ```java
   // Too broad - matches thousands of methods
   @OnMethod(clazz = "/java\\..*/", method = "/.*/")

   // Focused - matches specific target
   @OnMethod(clazz = "com.example.Service", method = "slowMethod")
   ```

5. **Reduce output volume**
   ```java
   // Bad - prints every invocation
   @OnMethod(...)
   public static void handler() {
       println("Called");
   }

   // Better - periodic summary
   private static AtomicInteger count = newAtomicInteger(0);

   @OnMethod(...)
   public static void handler() {
       incrementAndGet(count);
   }

   @OnTimer(5000)
   public static void report() {
       println("Calls in last 5s: " + str(get(count)));
       set(count, 0);
   }
   ```

6. **Use aggregations instead of individual logs**
   ```java
   private static Aggregation duration =
       Aggregations.newAggregation(AggregationFunction.QUANTIZE);

   @OnMethod(..., location = @Location(Kind.RETURN))
   public static void onReturn(@Duration long d) {
       Aggregations.addToAggregation(duration, d / 1000000);
   }

   @OnTimer(10000)
   public static void printStats() {
       Aggregations.printAggregation("Durations", duration);
       Aggregations.clearAggregation(duration);
   }
   ```

## Agent Loading Problems

### Agent JAR not found

**Error message:**
```
Error opening zip file or JAR manifest missing : /path/to/btrace-agent.jar
```

**Solutions:**
```bash
# Verify JAR exists
ls -la /path/to/btrace-agent.jar

# Use absolute path
java -javaagent:/absolute/path/to/btrace-agent.jar=script=Script.class MyApp

# Or set BTRACE_HOME
export BTRACE_HOME=/path/to/btrace
java -javaagent:$BTRACE_HOME/libs/btrace-agent.jar=script=Script.class MyApp
```

### Script class not found in agent mode

**Error message:**
```
ClassNotFoundException: MyTrace
```

**Solutions:**
```bash
# Specify absolute path to script
java -javaagent:btrace-agent.jar=script=/absolute/path/MyTrace.class MyApp

# Or add to classpath
java -javaagent:btrace-agent.jar=script=MyTrace.class \
     -cp /path/to/scripts:app.jar MyApp
```

## Compatibility Issues

### Java Version Mismatch

**Error message:**
```
UnsupportedClassVersionError
```

**Solutions:**
- Ensure BTrace supports your Java version (BTrace supports Java 8+)
- Compile scripts with target version matching JVM
- Check `JAVA_HOME` points to correct version

### Module System Issues (Java 9+)

**Error message:**
```
IllegalAccessError: class X cannot access class Y
```

**Solutions:**
```bash
# Add required --add-opens flags
btrace --add-opens java.base/jdk.internal.misc=ALL-UNNAMED <PID> script.java

# Or add to target JVM startup
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
     MyApp
```

### Old Script Syntax

**Error:**
Scripts written for BTrace 1.x fail with 2.x

**Solution:**
Update imports:
```java
// Old (1.x)
import com.sun.btrace.annotations.*;
import static com.sun.btrace.BTraceUtils.*;

// New (2.x)
import org.openjdk.btrace.core.annotations.*;
import static org.openjdk.btrace.core.BTraceUtils.*;
```

## Debugging BTrace Scripts

### Enable verbose logging

```bash
btrace -v <PID> script.java
```

### Dump instrumented classes

```bash
btrace -d /tmp/btrace-classes <PID> script.java
```

Then examine with:
```bash
javap -c /tmp/btrace-classes/com/example/MyClass.class
```

### Print diagnostic info

```java
@OnMethod(clazz = "com.example.MyClass", method = "myMethod")
public static void handler(@ProbeClassName String clazz,
                          @ProbeMethodName String method) {
    println("Class: " + clazz);
    println("Method: " + method);
    println("Thread: " + threadName());
    println("Stack:");
    jstack(5);  // Print 5 stack frames
}
```

### Test incrementally

Start simple and add complexity:

```java
// Step 1: Verify attachment
@OnTimer(1000)
public static void heartbeat() {
    println("BTrace alive");
}

// Step 2: Verify class matching
@OnMethod(clazz = "com.example.MyClass", method = "/.*/")
public static void anyMethod() {
    println("Matched");
}

// Step 3: Add specific logic
@OnMethod(clazz = "com.example.MyClass", method = "specificMethod")
public static void specific(String arg) {
    println("Arg: " + arg);
}
```

## Kubernetes and Cloud Deployments

### Finding Process ID in Pods

**Problem:** Need to find Java process ID inside a pod

**Solutions:**
```bash
# List Java processes in pod
kubectl exec <pod-name> -- jps

# Or use ps
kubectl exec <pod-name> -- ps aux | grep java

# For multi-container pods, specify container
kubectl exec <pod-name> -c <container-name> -- jps
```

### Multi-Container Pod Considerations

**Problem:** Cannot attach to process in different container

**Solution:**
Kubernetes pods with multiple containers have separate process namespaces by default.

```yaml
# Enable process namespace sharing
apiVersion: v1
kind: Pod
metadata:
  name: myapp
spec:
  shareProcessNamespace: true  # Required for cross-container attachment
  containers:
  - name: app
    image: myapp:latest
  - name: btrace-sidecar
    image: btrace:latest
```

**Note:** With `shareProcessNamespace: true`, all containers can see each other's processes.

### Port Conflicts in Pods

**Problem:** BTrace fails with port already in use

**Error:**
```
java.net.BindException: Address already in use
```

**Cause:** BTrace uses port 2020 by default; multiple BTrace instances or port conflicts

**Solutions:**
```bash
# Use different port
kubectl exec <pod> -- btrace -p 3030 <PID> script.java

# Check port usage
kubectl exec <pod> -- netstat -an | grep 2020
```

### Security Policies and RBAC

**Problem:** Pod Security Policy blocks BTrace attachment

**Error:**
```
Operation not permitted
```

**Common causes:**

1. **Pod Security Standards** (PSP replacement in K8s 1.25+):
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: myapp
spec:
  securityContext:
    # May need to adjust based on policy
    runAsNonRoot: true
    capabilities:
      add:
      - SYS_PTRACE  # Required for process attachment
```

2. **SELinux/AppArmor**:
```bash
# Check if SELinux is blocking
kubectl exec <pod> -- getenforce

# Check AppArmor profile
kubectl exec <pod> -- cat /proc/self/attr/current
```

**Solutions:**
- Add `SYS_PTRACE` capability if allowed by security policy
- Run as same user as target JVM
- Adjust security context constraints (OpenShift)

### JDK Not Available in Container

**Problem:** BTrace requires JDK but only JRE in container

**Error:**
```
Error: tools.jar not found
```

**Solutions:**

**Option 1: Use JDK base image**
```dockerfile
# Instead of JRE
FROM openjdk:11-jre
# Use JDK
FROM openjdk:11-jdk
```

**Option 2: Install JDK in running pod** (temporary)
```bash
kubectl exec <pod> -- apt-get update && apt-get install -y openjdk-11-jdk
```

**Option 3: Sidecar with JDK**
```yaml
spec:
  shareProcessNamespace: true
  containers:
  - name: app
    image: myapp-jre:latest  # Can use JRE
  - name: btrace
    image: openjdk:11-jdk     # Sidecar has JDK
```

### Service Mesh Compatibility

**Istio/Linkerd:**

BTrace works with service meshes but some considerations apply:

**Traffic Interception:**
- Service mesh sidecars don't interfere with BTrace (different layer)
- BTrace communication port (2020) not intercepted by mesh

**mTLS:**
- BTrace uses local socket communication (not HTTP)
- mTLS between services doesn't affect BTrace

**Resource Limits:**
```yaml
# BTrace overhead may trigger limits
resources:
  requests:
    cpu: "100m"
    memory: "128Mi"
  limits:
    cpu: "500m"      # Increase if BTrace causes throttling
    memory: "512Mi"   # Increase if needed
```

### ConfigMap for BTrace Scripts

**Pattern:** Store scripts in ConfigMap for easy distribution

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: btrace-scripts
data:
  TraceMethod.java: |
    import org.openjdk.btrace.core.annotations.*;
    import static org.openjdk.btrace.core.BTraceUtils.*;

    @BTrace
    // Classname 'TraceMethod' must correspond to the file name 'TraceMethod.java'
    public class TraceMethod {
        @OnMethod(clazz = "com.example.Service", method = "process")
        public static void onProcess() {
            println("Method called");
        }
    }
---
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: app
    image: myapp:latest
    volumeMounts:
    - name: scripts
      mountPath: /btrace-scripts
  volumes:
  - name: scripts
    configMap:
      name: btrace-scripts
```

Usage:
```bash
kubectl exec <pod> -- btrace <PID> /btrace-scripts/TraceMethod.java
```

### Batch Tracing Across Pods

**Pattern:** Trace all pods in a deployment

```bash
#!/bin/bash
# trace-deployment.sh

DEPLOYMENT=$1
SCRIPT=$2

# Get all pod names for deployment
PODS=$(kubectl get pods -l app=$DEPLOYMENT -o jsonpath='{.items[*].metadata.name}')

for POD in $PODS; do
  echo "Attaching to $POD..."
  kubectl exec $POD -- btrace 1 $SCRIPT &
done

wait
echo "All traces complete"
```

Usage:
```bash
./trace-deployment.sh myapp /tmp/trace.java
```

### Persistent Tracing with StatefulSets

**Issue:** BTrace doesn't persist across pod restarts

**Solutions:**

1. **Store output in persistent volume:**
```bash
kubectl exec <pod> -- btrace -o /data/trace.log <PID> script.java
```

2. **Stream to external system:**
```java
// Use StatSD integration
@OnMethod(...)
public static void handler() {
    Statsd.send("metric.name", value);
}
```

3. **Agent mode in pod spec:**
```yaml
spec:
  containers:
  - name: app
    image: myapp:latest
    env:
    - name: JAVA_TOOL_OPTIONS
      value: "-javaagent:/opt/btrace/lib/btrace-agent.jar=script=/scripts/trace.class"
```

### Cloud-Specific Issues

**AWS EKS:**
- No specific issues; standard K8s patterns apply
- Use same region for kubectl access

**Google GKE:**
- GKE Autopilot: May restrict `SYS_PTRACE` capability
- Use standard GKE for full BTrace support

**Azure AKS:**
- No specific issues; standard K8s patterns apply
- Ensure node has sufficient resources

**OpenShift:**
- Security Context Constraints (SCC) may be stricter
- May need to add SCC for SYS_PTRACE:
```bash
oc adm policy add-scc-to-user anyuid -z default
```

For more K8s deployment patterns, see [Getting Started: K8s](GettingStarted.md#btrace-in-containers-and-kubernetes) and [FAQ: Microservices](FAQ.md#can-i-use-btrace-with-microservices).

## Known Limitations

### Cannot instrument

**Native methods:**
- Methods implemented in native code cannot be instrumented (JVM limitation)

**Sensitive classes (to avoid infinite recursion):**
- Core classes BTrace depends on: `java.lang.Object`, `String`, `ThreadLocal`, `Integer`, `Number`
- Package prefixes: `java.lang.instrument.*`, `java.lang.invoke.*`, `java.lang.ref.*`
- Lock classes: `java.util.concurrent.locks.LockSupport`, `AbstractQueuedSynchronizer`, etc.
- JDK internals: `jdk.internal.*`, `sun.invoke.*`
- BTrace itself: `org.openjdk.btrace.*`

**Classes annotated with @BTrace** (BTrace scripts themselves)

**Note:** Classes already transformed by other agents CAN be retransformed. Boot classpath and JDK classes CAN be instrumented (except the sensitive classes listed above).

### Cannot trace
- Very early VM initialization (use agent mode instead of attach mode)
- Class loading before agent initialization
- JVM shutdown sequence (partially)

### Platform-specific issues

**Windows:**
- Attach API may require admin privileges
- Path separators: use `\\` or `/`

**macOS:**
- SIP (System Integrity Protection) may block attachment
- Disable for debugging: `csrutil disable` (not recommended for production)

**Linux:**
- Some distros have ptrace restrictions in `/proc/sys/kernel/yama/ptrace_scope`
- Set to 0 to allow: `echo 0 | sudo tee /proc/sys/kernel/yama/ptrace_scope`

## Getting Help

If issues persist:

1. **Check BTrace version**
   ```bash
   btrace --version
   ```

2. **Search existing issues**
   - [GitHub Issues](https://github.com/btraceio/btrace/issues)

3. **Provide details when asking for help:**
   - BTrace version
   - Java version (`java -version`)
   - OS and version
   - Complete error message
   - Minimal reproducing script
   - Target application details

4. **Community channels:**
   - Slack: [btrace.slack.com](http://btrace.slack.com/)
   - Gitter: [gitter.im/btraceio/btrace](https://gitter.im/btraceio/btrace)
   - GitHub Issues: [github.com/btraceio/btrace/issues](https://github.com/btraceio/btrace/issues)

## See Also

- **[Documentation Hub](README.md)** - Complete documentation map and learning paths
- **[Getting Started Guide](GettingStarted.md)** - Installation, first script, and quick start
- **[Quick Reference](QuickReference.md)** - Annotation and API cheat sheet
- **[BTrace Tutorial](BTraceTutorial.md)** - Progressive lessons covering all features
- **[FAQ](FAQ.md)** - Common questions and best practices
