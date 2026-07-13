# BTrace Quick Reference

A cheat sheet for experienced users. For step-by-step instructions, see [Getting Started](GettingStarted.md). Back to [README](../README.md).

---

## Quick Install

| Method | Command |
|--------|---------|
| JBang | `jbang catalog add --name btraceio https://raw.githubusercontent.com/btraceio/jbang-catalog/main/jbang-catalog.json && jbang btrace@btraceio <PID> script.java` |
| SDKMan | `sdk install btrace` |
| Docker | `docker pull btrace/btrace` |
| Manual | [Download latest release](https://github.com/btraceio/btrace/releases/latest) |

**Java support:** BTrace 3.0 runs on Java 8–25+. Running BTrace against a JVM older than Java 17 is deprecated: it continues to work throughout 3.x but emits a deprecation warning. Support for Java < 17 will be removed in the next major release (4.0).

---

## Table of Contents
1. [Core Annotations](#core-annotations)
2. [Location Kinds](#location-kinds)
3. [Parameter Annotations](#parameter-annotations)
4. [Other Annotations](#other-annotations)
5. [Common Patterns](#common-patterns)
6. [CLI Commands](#cli-commands)
7. [Built-in Functions](#built-in-functions)

## Core Annotations

### @BTrace
Marks a class as a BTrace script.
```java
@BTrace
public class MyTrace { }
```

### @OnMethod
Primary annotation for method instrumentation.

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `clazz` | String | Target class(es) | `"com.example.MyClass"` |
| `method` | String | Target method(s) | `"processData"` |
| `type` | String | Method signature filter | `"void (java.lang.String)"` |
| `location` | Location | Where to inject code | `@Location(Kind.RETURN)` |
| `enableAt` | Level | Instrumentation level control | `@Level(">=1")` |
| `exactTypeMatch` | boolean | Exact type matching | `true` |

**Examples:**
```java
// Match specific class and method
@OnMethod(clazz = "com.example.MyClass", method = "myMethod")

// Match with regex
@OnMethod(clazz = "/com\\.example\\..*/", method = "/get.*/")

// Match by annotation
@OnMethod(clazz = "@javax.ws.rs.Path", method = "@javax.ws.rs.GET")

// Match by signature
@OnMethod(clazz = "com.example.MyClass", method = "calculate", type = "int (int, int)")
```

### @OnTimer
Execute periodically.
```java
@OnTimer(5000)  // Every 5 seconds
public static void periodic() { }
```

### @OnEvent
Handle custom events sent from BTrace console.
```java
@OnEvent("myevent")
public static void onMyEvent() { }
```

### @OnExit
Execute when traced process exits.
```java
@OnExit
public static void onExit(int exitCode) { }
```

### @OnLowMemory
Execute when memory is low.
```java
@OnLowMemory(pool = "Tenured Gen")
public static void onLowMemory() { }
```

### @Event
Define a JFR (Java Flight Recorder) event factory for high-performance event recording.

**Requirements:** OpenJDK 8 (with backported JFR) or Java 11+

```java
@Event(
    name = "MyEvent",
    label = "My Custom Event",
    description = "Description of the event",
    category = {"myapp", "performance"},
    stacktrace = true,
    fields = {
        @Event.Field(type = Event.FieldType.STRING, name = "message"),
        @Event.Field(type = Event.FieldType.LONG, name = "duration",
                    kind = @Event.Field.Kind(name = Event.FieldKind.TIMESPAN))
    }
)
private static JfrEvent.Factory myEventFactory;
```

**Field Types:** BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE, BOOLEAN, STRING, CLASS, THREAD

**Field Kinds:** TIMESTAMP, TIMESPAN, DATAAMOUNT, FREQUENCY, MEMORYADDRESS, PERCENTAGE, BOOLEANFLAG, UNSIGNED

See [Getting Started: JFR Integration](GettingStarted.md#advanced-jfr-integration) and Pattern #9 below.

### @PeriodicEvent
Define a handler for periodic JFR events (OpenJDK 8 or Java 11+).

```java
@PeriodicEvent(
    name = "PeriodicStats",
    label = "Periodic Statistics",
    period = "10 s",  // or "eachChunk", "beginChunk", "endChunk"
    fields = @Event.Field(type = Event.FieldType.LONG, name = "count")
)
public static void emitStats(JfrEvent event) {
    if (Jfr.shouldCommit(event)) {
        Jfr.setEventField(event, "count", getCount());
        Jfr.commit(event);
    }
}
```

## Location Kinds

Specify where to inject code within a method using `@Location(Kind.XXX)`.

| Kind | Description | When | Common Parameters |
|------|-------------|------|-------------------|
| `ENTRY` | Method entry | Default | Method args |
| `RETURN` | Method return | Normal exit | `@Return`, `@Duration` |
| `ERROR` | Exception thrown | Uncaught exception | `Throwable`, `@Duration` |
| `CALL` | Method call | Before/after call | `@TargetInstance`, `@TargetMethodOrField` |
| `LINE` | Source line | Specific line | Line number |
| `FIELD_GET` | Field read | Getting field value | `@TargetInstance`, `@TargetMethodOrField` |
| `FIELD_SET` | Field write | Setting field value | New value, `@TargetInstance` |
| `NEW` | Object creation | After `new` | Object type name |
| `NEWARRAY` | Array creation | After `new[]` | Array type, dimensions |
| `CATCH` | Exception catch | Entering catch block | `Throwable` |
| `THROW` | Throwing exception | Before throw | `Throwable` |
| `ARRAY_GET` | Array read | Getting array element | Array, index |
| `ARRAY_SET` | Array write | Setting array element | Array, index, value |
| `SYNC_ENTRY` | Enter synchronized | Acquiring lock | Lock object |
| `SYNC_EXIT` | Exit synchronized | Releasing lock | Lock object |
| `INSTANCEOF` | instanceof check | Type check | Type name, `@TargetInstance` |
| `CHECKCAST` | Type cast | Casting | Type name, `@TargetInstance` |

**Location Modifiers:**

```java
@Location(value = Kind.CALL, clazz = "/.*/", method = "/.*/")  // Any call
@Location(value = Kind.CALL, clazz = "java.io.File", method = "delete")  // Specific call
@Location(value = Kind.LINE, line = 42)  // Specific line number
```

## Parameter Annotations

Inject context information into probe handler parameters.

| Annotation | Type | Description | Available In |
|------------|------|-------------|--------------|
| `@Self` | Object | Current instance (`this`) | All |
| `@Return` | Method return type | Return value | `RETURN`, `CALL` (after) |
| `@Duration` | long | Duration in nanoseconds | `RETURN`, `ERROR`, `CALL` (after) |
| `@ProbeClassName` | String | Enclosing class name | All |
| `@ProbeMethodName` | String | Enclosing method name | All |
| `@TargetInstance` | Object | Target object | `CALL`, `FIELD_GET/SET` |
| `@TargetMethodOrField` | String | Target name | `CALL`, `FIELD_GET/SET` |

**Examples:**
```java
// Method entry with arguments and context
@OnMethod(clazz = "MyClass", method = "process")
public static void onEntry(@Self Object self,
                          @ProbeClassName String clazz,
                          @ProbeMethodName String method,
                          String arg1, int arg2) { }

// Method return with value and duration
@OnMethod(clazz = "MyClass", method = "calculate", location = @Location(Kind.RETURN))
public static void onReturn(@Return int result, @Duration long duration) { }

// Method call tracking
@OnMethod(clazz = "MyClass", method = "/.*/", location = @Location(Kind.CALL, clazz = "/.*/", method = "/.*/"))
public static void onCall(@TargetInstance Object target, @TargetMethodOrField String method) { }
```

## Other Annotations

### @Injected
Injects an extension/service instance into your script.

- Use plain `@Injected` (no parameters). The invokedynamic injector auto-detects
  whether the service needs a runtime context or a no-arg construction and wires it
  accordingly.
- Annotation type: `io.btrace.core.annotations.Injected`
- Example:

```java
import io.btrace.core.annotations.*;
import io.btrace.metrics.MetricsService;

@BTrace
public class LatencyProbe {
  @Injected
  private static MetricsService metrics;
}
```

See also: Architecture → `architecture/ExtensionInvokeDynamicBridge.md`.

### @Sampled
Control sampling rate.
```java
@Sampled(kind = Sampled.Sampler.Const)  // Constant sampling
@Sampled(kind = Sampled.Sampler.Adaptive)  // Adaptive sampling
@OnMethod(...)
public static void sampledHandler() { }
```

### @Level
Control handler activation by instrumentation level.
```java
@OnMethod(clazz = "...", enableAt = @Level(">=1"))  // Level 1 or higher
@OnMethod(clazz = "...", enableAt = @Level("2..5"))  // Level 2-5 range
@OnMethod(clazz = "...", enableAt = @Level("3"))    // Exactly level 3
```

### @Export
Export data via JMX.
```java
@Export
private static long counter;
```

### @Property
Declare script properties.
```java
@Property
public static String configValue = "default";
```

### @TLS (Thread Local Storage)
Per-thread state.
```java
@TLS
private static long threadStartTime;
```

## Common Patterns

### Pattern 1: Method Entry/Exit Timing
```java
@TLS private static long startTime;

@OnMethod(clazz = "com.example.Service", method = "process")
public static void onEntry() {
    startTime = timeNanos();
}

@OnMethod(clazz = "com.example.Service", method = "process", location = @Location(Kind.RETURN))
public static void onReturn() {
    long duration = timeNanos() - startTime;
    println("Duration: " + str(duration / 1000000) + " ms");
}
```

### Pattern 2: Method Call Counting
```java
private static Map<String, AtomicInteger> counts = Collections.newHashMap();

@OnMethod(clazz = "com.example.Service", method = "/.*/")
public static void onMethod(@ProbeMethodName String method) {
    AtomicInteger counter = Collections.get(counts, method);
    if (counter == null) {
        counter = Atomic.newAtomicInteger(0);
        Collections.put(counts, method, counter);
    }
    Atomic.incrementAndGet(counter);
}

@OnTimer(5000)
public static void printStats() {
    printMap(counts);
}
```

### Pattern 3: Exception Tracking
```java
@OnMethod(clazz = "com.example.Service", method = "/.*/", location = @Location(Kind.ERROR))
public static void onError(@ProbeClassName String clazz,
                          @ProbeMethodName String method,
                          Throwable t) {
    println("Exception in " + clazz + "." + method + ": " + str(t));
    jstack();
}
```

### Pattern 4: Field Access Monitoring
```java
@OnMethod(clazz = "com.example.State", method = "/.*/", location = @Location(Kind.FIELD_SET, clazz = "/.*/", field = "status"))
public static void onFieldSet(@TargetInstance Object target,
                             @TargetMethodOrField String field,
                             Object newValue) {
    println("Field " + field + " set to: " + str(newValue));
}
```

### Pattern 5: SQL Query Logging
```java
@OnMethod(clazz = "java.sql.Statement", method = "execute.*")
public static void onSqlExecute(@Self Object stmt, String sql) {
    println("SQL: " + sql);
}
```

### Pattern 6: HTTP Request Tracking
```java
@OnMethod(clazz = "+javax.servlet.http.HttpServlet", method = "service")
public static void onRequest(@Self Object servlet,
                            javax.servlet.http.HttpServletRequest req) {
    println(str(req.getMethod()) + " " + str(req.getRequestURI()));
}
```

### Pattern 7: Object Allocation Tracking
```java
private static long objectCount;

@OnMethod(clazz = "com.example.HeavyObject", method = "<init>")
public static void onNew() {
    objectCount++;
}

@OnTimer(5000)
public static void printCount() {
    println("Objects created: " + str(objectCount));
}
```

### Pattern 8: Aggregations
```java
private static Aggregation distrib = Aggregations.newAggregation(AggregationFunction.QUANTIZE);

@OnMethod(clazz = "com.example.Service", method = "process", location = @Location(Kind.RETURN))
public static void onReturn(@Duration long duration) {
    Aggregations.addToAggregation(distrib, duration / 1000000);  // Convert to ms
}

@OnTimer(10000)
public static void printDistribution() {
    Aggregations.printAggregation("Duration distribution (ms)", distrib);
}
```

### Pattern 9: JFR Event Recording
```java
import io.btrace.core.jfr.JfrEvent;

@BTrace
public class JfrMethodTrace {
    @Event(
        name = "MethodExecution",
        label = "Method Execution Event",
        category = {"myapp"},
        fields = {
            @Event.Field(type = Event.FieldType.STRING, name = "className"),
            @Event.Field(type = Event.FieldType.STRING, name = "methodName"),
            @Event.Field(type = Event.FieldType.LONG, name = "duration",
                        kind = @Event.Field.Kind(name = Event.FieldKind.TIMESPAN))
        }
    )
    private static JfrEvent.Factory execEventFactory;

    @TLS private static long startTime;

    @OnMethod(clazz = "com.example.Service", method = "/.*/")
    public static void onEntry() {
        startTime = timeNanos();
    }

    @OnMethod(clazz = "com.example.Service", method = "/.*/",
             location = @Location(Kind.RETURN))
    public static void onReturn(@ProbeClassName String clazz,
                               @ProbeMethodName String method) {
        JfrEvent event = Jfr.prepareEvent(execEventFactory);
        if (Jfr.shouldCommit(event)) {
            Jfr.setEventField(event, "className", clazz);
            Jfr.setEventField(event, "methodName", method);
            Jfr.setEventField(event, "duration", timeNanos() - startTime);
            Jfr.commit(event);
        }
    }
}
```

**Benefits:** <1% overhead, native JVM recording, offline analysis with Mission Control.

## CLI Commands

### btrace
Attach to running JVM and trace.
```bash
btrace [options] <PID> <script.java> [script-args]
```

Read-only readiness diagnostics:

```bash
btrace doctor <pid> [--json]
```

Doctor attaches only to read target system properties, then detaches; it never loads an agent or
opens the BTrace command connection. It distinguishes Attach API availability from dynamic-agent
loading permission, which is reported as `not_tested`.

| Exit | Status | Meaning |
| ---: | --- | --- |
| 0 | ready | A usable BTrace endpoint is published. |
| 2 | preparation required | The target is readable but has no usable endpoint. |
| 3 | inaccessible | Attach is unavailable or denied. |
| 4 | unexpected failure | Invocation or provider failure prevented diagnosis. |

**Common Options:**
- `--version` - Show the version
- `-v` - Run in verbose mode
- `-l` - List all locally attachable JVMs
- `-lp` - List active probes in the given JVM (expects PID or app name)
- `-le` - List failed extensions in the given JVM (expects PID or app name)
- `-r <probe-id>` - Reconnect to an active disconnected probe
- `-r help` - Show help on remote commands
- `-o <file>` - Output to file (disables console output)
- `-u` - Run in trusted/unsafe mode
- `-d <path>` - Dump instrumented classes to specified path
- `-pd <path>` - Search path for probe XML descriptors
- `-cp <path>` / `-classpath <path>` - User class files and annotation processors path
- `-p <port>` - Port for btrace agent listener (default: 2020)
- `-host <host>` - Remote host (default: localhost)
- `-statsd <host:port>` - StatSD server configuration
- `-x` - Unattended mode (non-interactive)

**Examples:**
```bash
# List all Java processes
btrace -l

# Basic attach
btrace 12345 MyTrace.java

# Verbose with output file
btrace -v -o trace.log 12345 MyTrace.java

# List active probes in a JVM
btrace -lp 12345

# Reconnect to an active probe
btrace -r myprobe-id 12345

# With script arguments
btrace 12345 MyTrace.java arg1 arg2

# Dump instrumented classes for debugging
btrace -d /tmp/instrumented 12345 MyTrace.java

# With StatSD integration
btrace -statsd localhost:8125 12345 MyTrace.java
```

### btracec
Compile BTrace script.
```bash
btracec <script.java>
```

**Examples:**
```bash
# Compile script
btracec MyTrace.java

# Results in MyTrace.class
```

### btracer
Launch Java app with BTrace agent.
```bash
btracer <script.class> <java-app-and-args>
```

**Examples:**
```bash
# First compile
btracec MyTrace.java

# Then launch
btracer MyTrace.class java -jar myapp.jar

# With JVM options
btracer MyTrace.class java -Xmx2g -jar myapp.jar
```

### btracex
Extension management CLI: inspect, list, and install extensions and edit the permission policy.
```bash
btracex inspect [<path|dir|zip|id>] [--json]   # no args opens the interactive repo browser (TUI)
btracex list [--json]
btracex policy print [--policy-file <path>|--home|--classpath <outDir>] [--json]
btracex policy set [--allowExtensions <ids>] [--denyExtensions <ids>] [--allowPrivileged <bool>] [--policy-file <path>|--home|--classpath <outDir>]
btracex install <groupId:artifactId:version> [--repo <url> ...] [--id <extId>] [--dry-run]
```

**Examples:**
```bash
# Inspect an extension archive (id, version, services, privileged status)
btracex inspect my-extension.zip

# List installed extensions
btracex list

# Install an extension from Maven coordinates
btracex install io.btrace:btrace-statsd:3.0.0
```

### btracep
Probe inspector: prints details of a compiled BTrace probe (handlers, required permissions, bytecode).
```bash
btracep <probe_file>
```

**Example:**
```bash
btracec MyTrace.java
btracep MyTrace.class
```

### Java Agent Mode
Start app with BTrace agent directly.
```bash
java -javaagent:/path/to/btrace.jar=script=<script.class>,noServer=true[,arg=value]... YourApp
```

**Agent Parameters:**
- `script=<path>` - BTrace script class file
- `scriptdir=<dir>` - Directory to load scripts from
- `port=<port>` - Communication port; prepared mode accepts `0` and publishes the selected port
- `bindAddress=<address>` - Loopback bind address (`127.0.0.1` or `::1`); non-loopback and wildcard
  addresses are rejected in 3.0.0
- `authTokenFile=<path>` - Existing owner-protected prepared-mode token file, or a path where the
  agent creates one; the token value is never passed as an argument
- `noServer=true` - Don't start the command server; use for startup scripts that need no later
  client connection
- `telemetry=true|false` - Explicitly enable or disable the default-off `agent_start` telemetry
  event
- `bootClassPath=<path>` - Additional boot classpath

**Examples:**
```bash
# Startup script with no command endpoint
java -javaagent:btrace.jar=script=MyTrace.class,noServer=true MyApp

# With debug logging and no command endpoint
java -javaagent:btrace.jar=script=MyTrace.class,noServer=true,debug=true MyApp

# Authenticated prepared endpoint on an automatically selected loopback port
java -javaagent:btrace.jar=port=0 MyApp

# Run a startup probe and retain the authenticated endpoint for later PID-based connections
java -javaagent:btrace.jar=script=MyTrace.class,noServer=false,port=0 MyApp
```

A 3.0 client obtains the prepared endpoint and token-file path through the target JVM's Attach API.
Authentication precedes V1/V2 negotiation; older clients and missing or incorrect credentials fail
closed. Generated credentials are removed at shutdown. Remote prepared mode is unsupported in
3.0.0.

### Wire Protocol
Client and agent communicate over a pluggable wire protocol: V1 (Java serialization, backward compatible) and V2 (binary, faster). The version is auto-negotiated by default. System properties:

| Property | Default | Description |
|----------|---------|-------------|
| `btrace.comm.protocol` | `v2` | Protocol version (`1`/`v1` or `2`/`v2`) |
| `btrace.comm.autoNegotiate` | `true` | Enable automatic protocol negotiation |
| `btrace.comm.forceVersion` | `false` | Force the configured version without negotiation (mutually exclusive with auto-negotiation) |
| `btrace.protocol.negotiation.timeout` | `5000` | Negotiation timeout in milliseconds |

## Built-in Functions

The BTrace compiler auto-imports `import static io.btrace.BTrace.*` into every script.
That auto-import covers the core helper categories: output (`print`, `println`, `printf`),
string manipulation, basic math, thread info, and basic stack printing.

Many functions in the sections below are **not** in `io.btrace.BTrace` and require
`import static io.btrace.core.BTraceUtils.*;` — for example `printArray`, `classOf`,
aggregation helpers, `timeNanos`, `jstack`, collection utilities, and `Jfr.*`.
Adding that import suppresses the `io.btrace.BTrace.*` auto-import, so you must then
import both explicitly.

### Output Functions
```java
println(String)           // Print line
print(String)            // Print without newline
printArray(Object[])     // Print array
printMap(Map)            // Print map
printf(String, ...)      // Formatted print
```

### Reflection Functions
```java
str(Object)              // Object to string
name(Class)              // Class name
probeClass()             // Get current probe class
classOf(Object)          // Get object's class
field(Class, String)     // Get field value
```

### Aggregation Functions
```java
newAggregation(AggregationFunction)    // Create aggregation
addToAggregation(Aggregation, long)     // Add value
printAggregation(String, Aggregation)   // Print distribution
```

### Time Functions
```java
timeMillis()             // Current time in milliseconds
timeNanos()              // Current time in nanoseconds
timestamp()              // Formatted timestamp
```

### Thread Functions
```java
jstack()                 // Print stack trace
jstack(int)              // Print N frames
jstacks()                // Print all thread stacks
threadId()               // Current thread ID
threadName()             // Current thread name
```

### Collection Functions
```java
Collections.newHashMap()      // Create HashMap
Collections.newArrayList()    // Create ArrayList
Collections.put(Map, K, V)    // Put in map
Collections.get(Map, K)       // Get from map
Collections.size(Collection)  // Get size
```

### Atomic Functions
```java
Atomic.newAtomicInteger(int)        // Create AtomicInteger
Atomic.incrementAndGet(AtomicInteger)  // Increment and get
Atomic.get(AtomicInteger)            // Get value
```

### System Functions
```java
exit(int)                // Exit BTrace
sys(String)              // System property
getenv(String)           // Environment variable
getInstrumentationLevel()  // Current level
```

### Memory Functions
```java
sizeof(Object)           // Object size
heapUsage()              // Heap memory usage
nonHeapUsage()           // Non-heap memory usage
```

### JFR Event Operations
```java
// Create event from factory
Jfr.prepareEvent(JfrEvent.Factory)    // Create new JFR event instance

// Set event field values (overloaded for all types)
Jfr.setEventField(JfrEvent, String, byte|boolean|char|short|int|float|long|double|String)

// Event lifecycle
Jfr.shouldCommit(JfrEvent)             // Check if event should be recorded
Jfr.commit(JfrEvent)                   // Commit event to JFR
Jfr.begin(JfrEvent)                    // Start timing for timespan events
Jfr.end(JfrEvent)                      // End timing for timespan events
```

**Note:** JFR functions require OpenJDK 8 (with backported JFR) or Java 11+. Not available in Java 9-10 (graceful degradation).

## Restrictions

BTrace scripts have safety restrictions:
- No new threads
- No new classes
- No synchronization
- No loops (for, while, do-while, enhanced for)
- No external method calls (methods within same BTrace class are allowed)
- No file I/O (except via BTraceUtils)
- No System.exit

Use `-u` (unsafe mode) to bypass restrictions, but only in controlled environments.

---

## See Also

- **[Documentation Hub](README.md)** - Complete documentation map and learning paths
- **[Getting Started Guide](GettingStarted.md)** - Installation, first script, and quick start
- **[BTrace Tutorial](BTraceTutorial.md)** - Progressive lessons covering all features
- **[Troubleshooting Guide](Troubleshooting.md)** - Solutions to common problems
- **[FAQ](FAQ.md)** - Common questions and best practices
