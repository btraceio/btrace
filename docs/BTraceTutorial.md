# BTrace Tutorial (BTrace 3.0)

> **Note:** Examples use `btrace.jar` -- the single masked JAR (BTrace 2.2+). If using a legacy multi-JAR distribution, replace `btrace.jar` with `btrace-agent.jar` (and add `-Xbootclasspath/a:btrace-boot.jar` where needed).

> **Package namespace:** BTrace 3.0+ uses the `io.btrace` package (previously `org.openjdk.btrace`). The compiler auto-injects `import static io.btrace.BTrace.*` into every script, so common helpers like `println`, `str`, `monotonic`, etc. are available without any import. Legacy probes compiled against `org.openjdk.btrace` are transparently migrated at load time. See the [Migration Guide 2.x → 3.0](Migration-2.x-to-3.0.md) for full upgrade details.

**Related documentation**

| Document | What's in it |
|----------|-------------|
| [Getting Started](GettingStarted.md) | Installation, first run |
| [Migration Guide 2.x → 3.0](Migration-2.x-to-3.0.md) | Upgrading from BTrace 2.x |
| [Quick Reference](QuickReference.md) | All annotations, parameters, built-in functions |
| [Oneliner Guide](OnelinerGuide.md) | DTrace-style one-liners (`btrace -n '...'`) |
| [Extension Development Guide](BTraceExtensionDevelopmentGuide.md) | Writing and shipping extensions |
| [Extension Interface Rules](ExtensionInterfaceRules.md) | API design contract that the build enforces |
| [FAQ](FAQ.md) | Common questions and answers |
| [Troubleshooting](Troubleshooting.md) | Diagnosing agent and probe problems |

## 1. Hello World

Accustoms the learner to 'btrace' command and the way it is used.
Demonstrates the BTrace ability to instrument a class.

### Setup

#### HelloWorld Class

```java
package extra;

public abstract class HelloWorld extends HelloWorldBase {
    protected int field = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("ready when you are ...");
        System.in.read();

        callA();
    }

    private static void callA() {
        HelloWorld instance = new HelloWorldExt();
        long x = System.nanoTime();
        instance.callA("hello", 13);
        System.out.println("dur = " + (System.nanoTime() - x));
    }

    private void callA(String a, int b) {
        field++;
        callB(callC(a, b));
        field--;
    }

    private void callB(String s) {
        field++;
        System.out.println("You want " + s);
        field--;
    }

    protected abstract String callC(String a, int b);
}

final class HelloWorldExt extends HelloWorld {
    @Override
    protected String callC(String a, int b) {
        try {
            field++;
            String s = a + "-" + b;
            for (int i = 0; i < 100; i++) {
                s = callD(s);
            }
            return s;
        } finally {
            field--;
        }
    }
}

abstract class HelloWorldBase {
    protected final String callD(String s) {
        return "# " + s;
    }
}
```

### Steps

1. Run the HelloWorld application
2. Get the HelloWorld application PID via `jps` command
3. Run `btrace <PID> <HelloWorldTrace.java>`
4. Proceed with the HelloWorld application
5. Watch messages being printed

You will repeat these steps while gradually enhancing the used BTrace script

### Lessons

## Lesson 1 - Launching BTrace

##### Using `btrace` client to attach to a running JVM

`btrace [opts] <pid> <btrace-script> [<args>]`

* **opts** BTrace specific options; use `btrace -h` to obtain the list of all supported options
* **pid** process id of the traced Java program
* **btrace-script** trace program. If it is a ".java", then it is compiled before submission. Or else, it is assumed to be pre-compiled [i.e., it has to be a .class] and submitted.
* **args** trace specific arguments

Once you are attached to the target JVM you can press Ctrl-C in the terminal to show the BTrace console. From there you can either detach and exit or send an event (handled by the __@OnEvent__ annotated methods in the trace program).

##### Starting a Java application with BTrace agent

###### Directly

`java -javaagent:btrace.jar=[<agent-arg>[,<agent-arg>]*]? <launch-args>`

The agent takes a list of comma separated arguments.

* **noServer** - don't start the socket server
* **bootClassPath** - boot classpath to be used
* **systemClassPath** - system classpath to be used
* **debug** - turns on verbose debug messages (true/false)
* **trusted** - do not check for btrace restrictions violations (true/false)
* **dumpClasses** - dump the transformed bytecode to files (true/false)
* **dumpDir** - specifies the folder where the transformed classes will be dumped to
* **stdout** - redirect the btrace output to stdout instead of writing it to an arbitrary file (true/false)
* **probeDescPath** - the path to search for probe descriptor XMLs
* **startupRetransform** - enable retransform of all the loaded classes at attach (true/false)
* **scriptdir** - the path to a directory containing scripts to be run at the agent startup
* **scriptOutputFile** - the path to a file the btrace agent will store its output
* **grant** - comma-separated list of permissions to grant (e.g. `grant=NETWORK,THREADS`)
* **deny** - comma-separated list of permissions to deny (e.g. `deny=EXEC,NATIVE`)
* **grantAll** - grant all permissions (true/false) - use with caution
* **script** - colon separated list of tracing scripts to be run at the agent startup

The scripts to be run must have already been compiled to bytecode (a *.class* file) by __btracec__.

For a launch-time script that needs no later client connection, set `noServer=true`. BTrace 3.0
does not treat an unauthenticated startup command server as prepared mode; prepared mode requires
authentication and loopback-only binding. Until that path is available in a tested release, use
startup-script mode with `noServer=true` or dynamic attach where JVM policy permits it.

###### Using `btracer'

`btracer [opts] <pre-compiled-btrace.class> <vm-arg> <application-args>`

* **opts** BTrace specific options; use `btracer -h` to obtain the list of all supported options
* **pre-compiled-btrace.class** the trace script compiled to bytecode via __btracec__
* **vm-args** the VM arguments; eg. `-cp app.jar Main.class` or `-jar app.jar`
* **application-args** the application specific arguments

You can use __btracer__ to launch java application from jar (`btracer ... -jar app.jar <application args>`) or a main class (`btracer ... -cp <class_path> <main class> <application args>`)

##### Compiling trace scripts

This needs to be done in order to launch the Java application with BTrace agent.

`btracec [-cp <classpath>] [-d <directory>] <one-or-more-BTrace-.java-files>`

* **classpath** is the classpath used for compiling BTrace program(s). Default is "."
* **directory** is the output directory where compiled .class files are stored. Default is ".".

Rather than regular *javac* the BTrace compiler is used - causing the script to be validated at compile time and prevent reporting verify errors at runtime.

##### Inspecting the compiled trace scripts

BTrace compiler will, by default, create a binary trace representation which packages the trace class file together with some metadata designed to make the
trace loading and application faster. These packages are not directly readable by tools like `javap` and one must use `btracep` instead.
The syntax is straightforward - `./btracep <binary trace file>`. The tool will print out
* trace name
* verification status (trusted or not)
* transformation status (will cause class retransformation or not)
* all probe handlers (all `@OnProbe`, `@OnTimer` etc. definitions)
* ASM-ified version of the associated "data holder" class - the class contains the information that needs to be globally accessible from instrumented code

## Lesson 2 - Tracing methods

This is the main purpose of BTrace - inject a custom code to custom locations to give the insights about the internal state and dynamics of the application.

> **Import convention:** Code examples in this lesson use `import ...;` as a shorthand. In practice you need **no explicit imports at all**: the compiler auto-injects both `import static io.btrace.BTrace.*;` (utility methods like `println`, `str`, `monotonic`) and `import io.btrace.core.annotations.*;` (all BTrace annotations). The `import ...;` placeholder is kept in these examples only to signal which packages are in use. See [Lesson 7](#lesson-7---flat-dsl) for details.

1. Getting just the information that any method is being executed
```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="/.*/")
    public static void onMethod() {
        println("Hello from method");
    }
}
```

2. Get the method names
```java
package helloworld;
import ...;

@BTrace 
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld")
    public static void onMethod(@ProbeMethodName String pmn) {
        println("Hello from method " + pmn);
    }
}
```

3. Intercept only a particular method
```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="callA")
    public static void onMethod(@ProbeMethodName String pmn) {
        println("Hello from method " + pmn);
    }
}
```

4. Intercept only a particular method with name matching the handler name
```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="#")
    public static void callA(@ProbeMethodName String pmn) {
        println("Hello from method " + pmn);
    }
}
```

5. Intercept methods with names matching certain patterns
__Note:__ you can use pattern matching for the class names, too

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="/call.*/")
    public static void onMethod(@ProbeMethodName String pmn) {
        println("Hello from method " + pmn);
    }
}
```

6. Intercept methods with names matching certain patterns and inspect their parameters

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="/call.*/")
    public static void onMethod(@ProbeMethodName String pmn, AnyType[] args) {
        println("Hello from method " + pmn);
        println("Received the following parameters:");
        printArray(args);
    }
}
```

7. Intercept method with names matching certain patterns and discover their signatures
```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="/call.*/")
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn) {
        println("Hello from method " + pmn);
    }
}
```

8. Intercept methods for all subclasses and implementations of a certain class/interface

__Note:__ 'extra.HelloWorldBase.callD()' doesn't show up - it is defined in the superclass of 'extra.HelloWorld' and therefore not intercepted.

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="+extra.HelloWorld", method="/call.*/")
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn) {
        println("Hello from method " + pmn);
    }
}
```

9. Intercept method with a particular name and signature + capture the method arguments (you need to use the information learned in the previous step)

__Note:__ The order of the un-annotated parameters must correspond to the order of the traced method parameters. Annotated parameters may be placed anywhere.

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorldExt", method="callC")
    public static void onMethod(@ProbeMethodName String pmn, String param1, int param2) {
        println("Hello from method " + pmn);
        println("Arguments: param1 = " + str(param1) + ", param2 = " + str(param2));
    }
}
```

10. Intercept method with a particular name and signature but don't capture the method arguments. Here you will need to decifer the VM method signature to get java like method signature. See the @OnMethod.type() javadoc for the java like signature format.

Eg. having the VM method signature in form of (Ljava/lang/String;I)V will translate to "void (java.lang.String, int)"

__Note:__ We are using overloaded method here and specifying the signature helps BTrace determine which method should be instrumented

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="callA", type="void (java.lang.String, int)")
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn) {
        println("Hello from method " + pmn);
    }
}
```

11. Intercept method with a particular name and capture its return value
`location=@Location(Kind.RETURN)` sets up the instrumentation to be inserted just before the method exits

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="callC", location=@Location(Kind.RETURN))
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn, @Return String ret) {
        println("Hello from method " + pmn + "; returning " + ret);
    }
}
```

12. Inspect the content of an instance variable in the method declaring class

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="/call.*/", location=@Location(Kind.RETURN))
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn, @Self Object thiz) {
        println("Hello from method " + pmn);
        println("field = " + str(getInt("field", thiz)));
    }
}
```

Or retrieve the `java.lang.Field` instance first and perform a check before trying to retrieve the field value.

```java
package helloworld;
import java.lang.Class;
import java.lang.reflect.Field;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="/call.*/", location=@Location(Kind.RETURN))
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn, @Self Object thiz) {
        Class myClz = classOf(thiz);
        Field fld = field(myClz, "field", false);
        println("Hello from method " + pmn);
        if (fld != null) {
           println("field = " + str(getInt(fld, thiz)));
        }
    }
}
```

13. Get the method execution duration

__Note:__ Need to use @Location(Kind.RETURN) to be able to capture the execution duration

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="/call.*/", location=@Location(Kind.RETURN))
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn, @Duration long dur) {
        println("Hello from method " + pmn);
        println("It took " + str(dur) + "ns to execute this method");
    }
}
```

14. Tracing methods invoked from inside a particular method

__Note:__ 'class', 'method' etc. directly in the @OnMethod annotation will determine where we should look for the invocation of the methods defined by 'class', 'method' etc. parameters in the @Location annotation.

__Note:__ @ProbeMethodName and @ProbeClassName refer to the context method and class; @TargetMethodOrField refers to the traced method invocation

__Note:__ You can use the 'type' annotation parameter in @OnMethod annotation to restrict the context methods and in @Location to restrict the traced method invocations

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="callA",
              location=@Location(
                    value = Kind.CALL,
                    clazz = "extra.HelloWorld",
                    method = "/call.*/",
                    where = Where.BEFORE)
    )
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn, @TargetMethodOrField(fqn = true) String tpmn) {
        println("Hello from method " + pmn);
        println("Going to invoke method " + tpmn);
    }
}
```

15. Measuring the duration of methods invoked from inside a particular method

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="callA",
              location=@Location(
                    value = Kind.CALL,
                    clazz = "extra.HelloWorld",
                    method = "/call.*/",
                    where = Where.AFTER)
    )
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn, @TargetMethodOrField(fqn = true) String tpmn, @Duration long dur) {
        println("Hello from method " + pmn);
        println("Executing " + tpmn + " took " + dur + "ns");
    }
}
```

16. Tracing methods invoked from inside a particular method and capturing their parameters

__Note:__ The captured parameters pertain to the invoked method rather than the context method

__Note:__ The @Self annotated parameter captures the context instance and @TargetInstance annotated parameter captures the instance the method is invoked on

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="callA",
              location=@Location(
                    value = Kind.CALL,
                    clazz = "extra.HelloWorld",
                    method = "/call.*/",
                    where = Where.BEFORE)
    )
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn, @TargetMethodOrField(fqn = true) String tpmn,
                                @Self Object thiz, @TargetInstance Object tgt, String a, int b) {
        println("Hello from method " + pmn);
        println("Going to invoke method " + tpmn);
        println("context = " + str(classOf(thiz)) + ", target = " + str(classOf(tgt)));
        println("a = " + a + ", b = " + str(b));
    }
}
```

## Lesson 3 - Global callbacks

Global callbacks are not directly related to the tracing code injection but they allow us to observe the global state and act correspondingly.

> **Import convention:** Same as Lesson 2 — both annotation and utility imports are auto-injected; no explicit imports are required.

#### __@OnExit__

Called when the traced application is about to exit. Allows to capture the exit code.

__Note:__ The signature of the handler method __MUST__ be 'void (int)'

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnExit
    public static void onexit(int code) {
        println("Application exitting with " + code);
    }
}
```

#### __@OnError__

Called whenever an exception is thrown from anywhere in the BTrace handlers.

__Note:__ The signature of the handler method __MUST__ be 'void (java.lang.Throwable)'

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnError
    public static void onerror(Throwable t) {
        println("Encountered internal error " + str(t));
    }
}
```

#### __@OnTimer__

Allows to register a handler to be invoked periodically at defined intervals.

__Note:__ The annotation parameter takes the interval value in milliseconds

__Note:__ The signature of the handler method __MUST__ be 'void ()'

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnTimer(1000)
    public static void ontimer() {
        println("tick ...");
    }
}
```

#### __@OnEvent__

Used to raise events from external clients (eg. the command line client). The annotation takes a String parameter which is the event name. When not provided the event is considered to be 'unnamed'.

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnEvent
    public static void unnamed() {
        println("Received unnamed event");
    }
}
```
or

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    @OnEvent("myEvent")
    public static void myevent() {
        println("Received my event");
    }
}
```

## Lesson 4 - Sampling

Tracing many methods being executed frequently can bring a significant overhead to the traced application. And often we are not really interested in the high detail data - an aggregated view would do just fine.

Therefore it is possible to employ statistical sampling to reduce the amount of collected data and related overhead while still providing relevant information about the application behaviour.

The sampling implementation in BTrace guarantees that at least one invocation of a traced method will be recorded, no matter what the sampling settings are.

1. Intercept only each 10th invocation on average

__Note:__ Even though the 'callD' method is executed 100 times we will get only ~10 hits - as dictated by the 'mean' parameter.

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    private static int cntr = 1;
    @Sampled(kind = Sampled.Sampler.Const, mean = 10)
    @OnMethod(clazz="/extra\\.HelloWorld.*/", method="callD")
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn) {
        println("Hello from method " + pmn + " : " + (cntr++));
    }
}
```

2. Let the sampler mean parameter be adjusted dynamically by keeping to the overhead target

__Note:__ In this case the 'mean' parameter actually specifies the lowest number of nanoseconds the average interval between interceptions should be

__Note:__ Because the adaptive sampling needs to collect timestamps in order to maintain the overhead target the lowest value for the 'mean' parameter is 180 (cca. 60ns for getting start/stop timestamp pair multiplied by the safety margin of 3) 

__Note:__ The 'callD' method is very short and the number of iteration is rather limited - we will most probably get only one hit here

```java
package helloworld;
import ...;

@BTrace
public class HelloWorldTrace {
    private static int cntr = 1;
    @Sampled(kind = Sampled.Sampler.Adaptive, mean = 50)
    @OnMethod(clazz="/extra\\.HelloWorld.*/", method="callD")
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn) {
        println("Hello from method " + pmn + " : " + (cntr++));
    }
}
```

## Lesson 5 - JFR events

Since BTrace 2.1.0 it is possible to define and use JFR dynamic events directly from the BTrace scripts. This gives immediate access to the high-performance
event recording engine built directly in JVM. Being able to observe the script defined events in the bigger context of full application/JVM is an additional
benefit when comparing to the 'standard' BTrace way of writing the information to stdout or a dedicated text file.

##### Define and use a regular JFR event

A new dynamic event type in BTrace is defined via a `JfrEvent.Factory` instance configured by `@Event` aannotation.
The annotation defines the event metadata and event fields.

###### Event Metadata

* **name** - global event name
* **label** - a pretty printed event name
* **description** - long description
* **category** - arbitrary category in 'directory' format (eg. 'Application/SQL/Updates')
* **stacktrace** - whether the event should collect stacktrace or not

###### Field definition

The `fields` argument of the `@Event` annotation defines the array of event fields where each field is defined with the help
of `@Event.Field` annotation.
The fields can only be of a supported type (Java primitives + String, Class and Thread) and don't support arrays/lists. 

###### Using the event

`BTraceUtils` has been enhanced with the following methods to support working with JFR events:
* **prepareEvent(eventFactory)** - prepares a new event using the given factory
* **begin(event)** - calls `begin()` event method to start measuring the event time span
* **end(event)** - calls `end()` event method to stop measuring the event time span
* **setEventField(event, field, value)** - sets a field value
* **commit(event)** - tries and commits the event

###### Example
```java
@BTrace public class JfrEventsProbe {
    @Event(
        name = "CustomEvent",
        label = "Custom Event",
        fields = {
            @Event.Field(type = Event.FieldType.INT, name = "a"),
            @Event.Field(type = Event.FieldType.STRING, name = "b")
        }
    )
    private static JfrEvent.Factory customEventFactory;

    @OnMethod(clazz = "/.*/", method = "/.*/")
    public static void onMethod() {
        JfrEvent event = prepareEvent(customEventFactory);
        setEventField(event, "a", 10);
        setEventField(event, "b", "hello");
        commit(event);
    }
}
```

##### Define and use a periodic JFR event

A periodic JFR event is automatically generated by JFR at a given time interval or at beginning/end of a JFR chunk.

###### Periodic event handler

A periodic event is defined by an event handler - it is a method by `@PeriodEvent` annotation, pretty much like `@OnMethod` or `@OnTimer` handlers.
Similarly to the regular events the annotation defines the event metadata and the event fields.
In addition to that the annotation defines the period which can be a time unit like `10 s` or `100 ms` or it can be one of
`everyChunk`, `beginChunk` or `endChunk`.

The handler method takes one parameter of `JfrEvent` type. The value of this parameter can be used in the `BTraceUtils` JFR
specific methods.


###### Example
```java
@BTrace public class JfrEventsProbe {
    @PeriodicEvent(name = "PeriodicEvent", fields = @Event.Field(type = Event.FieldType.INT, name = "ts", kind = @Event.Field.Kind(name = Event.FieldKind.TIMESTAMP)), period = "1 s")
    public static void onPeriod(JfrEvent event) {
        if (shouldCommit(event)) {
            setEventField(event, "ts", 1);
            commit(event);
        }
    }
}
```

## Lesson 6 - Extensions and Permissions

**Hands-on lab →** [Extensions and Permissions Without Tears](tutorials/04-extensions-and-permissions.md) walks through granting, denying, and inspecting extension permissions against a live JVM, with every error message and CLI output verified against source.

BTrace supports extensions that provide additional functionality beyond the core tracing capabilities. Extensions can send metrics to external systems, integrate with DTrace, and more. To ensure safety, extensions require explicit permissions.

> See the [BTrace Extension Development Guide](BTraceExtensionDevelopmentGuide.md) for the full guide on building and deploying extensions, and the [Extension Interface Rules](ExtensionInterfaceRules.md) for the API design contract.

##### Permission System

BTrace uses a permission-based security model organized into three tiers:

###### Default Permissions (always granted)
These permissions are safe and always available:
* **MESSAGING** - Send messages to BTrace client
* **AGGREGATION** - Use aggregation functions
* **JFR_EVENTS** - Create and use JFR events
* **PROFILING** - Use profiling functions

###### Standard Permissions (granted unless explicitly denied)
These permissions allow read-only access to system information:
* **FILE_READ** - Read files from disk (limited paths)
* **SYSTEM_PROPS** - Read system properties
* **THREAD_INFO** - Read thread information
* **MEMORY_INFO** - Read memory and GC information

###### Privileged Permissions (require explicit grant)
These permissions have security implications and must be explicitly granted:
* **FILE_WRITE** - Write files to disk
* **NETWORK** - Network I/O (sockets, HTTP)
* **THREADS** - Create and manage threads
* **NATIVE** - Call native code (JNI, Unsafe)
* **EXEC** - Execute external processes
* **REFLECTION** - Use reflection
* **CLASSLOADER** - Access classloaders
* **UNLIMITED_MEMORY** - Unlimited buffer allocation

##### Permission Model

Permissions are defined by extension/service descriptors and enforced via agent grants. The Gradle plugin writes the effective permission set into the extension’s manifest.

##### Do Not Instantiate Types in Probes

- Probes do not construct arbitrary objects (`new` is not allowed). Instead, obtain builders or factories from injected services and pass built configuration handles back to the service.
- This keeps hot paths allocation-free and within verifier constraints (only BTraceUtils, injected services, or objects returned from services can be used).

Example:
```
@BTrace
class Example {
  @Injected static MetricsService metrics;
  static final HistogramConfig cfg;
  static {
    cfg = metrics.newHistogramConfig().unit("ns").build();
  }
  @OnMethod(clazz="com.example.Foo", method="bar", location=@Location(Kind.RETURN))
  static void onReturn(@Duration long d) {
    metrics.histogram("foo.bar.latency", cfg).record(d);
  }
}
```

##### Granting Permissions at Runtime

When running a probe that requires privileged permissions, you must explicitly grant them:

###### Using the btrace client
```bash
btrace --grant=NETWORK,THREADS <pid> MetricsProbe.class
```

###### Using the Java agent
```bash
java -javaagent:btrace.jar=script=MetricsProbe.class,grant=NETWORK,THREADS ...
```

###### Grant all permissions (use with caution)
```bash
btrace --grantAll=true <pid> MetricsProbe.class
```
or
```bash
java -javaagent:btrace.jar=script=MetricsProbe.class,grantAll=true ...
```

##### Per-Extension Allow/Deny (Simplified Policy)
- Allow specific extensions to link implementations via agent args:
  - `-javaagent:btrace.jar=...,allowExtensions=btrace-statsd,my-metrics`
- Deny specific extensions (implementation blocked; SHIM fallback):
  - `-javaagent:btrace.jar=...,denyExtensions=legacy-foo`
- Allow all privileged extensions:
  - `-javaagent:btrace.jar=...,allowPrivileged=true`
- Optional process-local policy file:
  - `-Dbtrace.permissions=/path/to/permissions.properties` or `~/.btrace/permissions.properties`
  - Example content:
    - `allowExtensions=btrace-statsd`
    - `denyExtensions=legacy-foo`
    - `allowPrivileged=false`
- Extensions continue to expose required permissions in logs to help operators decide whether to allow them.

##### Permission Error Messages

If a probe requires permissions that are not granted, BTrace will display a descriptive error message:

```
Probe requires permissions that are not granted:

  - NETWORK
    Network I/O (sockets, HTTP). Risk: Data exfiltration, remote connections.
  - THREADS
    Create and manage threads. Risk: Resource exhaustion, concurrent operations.

To allow these permissions, use:
  --grant=NETWORK,THREADS

Or use --grantAll=true to allow all permissions (not recommended).
```

##### Inspecting Probe Permissions

Use the `btracep` tool to inspect what permissions a compiled probe requires:

```bash
btracep MetricsProbe.class
```

The output will include a "Required permissions" line listing all permissions the probe needs.

##### Using the StatsdExtension

The StatsdExtension allows sending metrics to a StatsD server:

```java
@BTrace
public class StatsdExample {
    @Injected
    private static StatsdExtension statsd;

    @OnMethod(clazz = "com.example.API", method = "handleRequest",
              location = @Location(Kind.RETURN))
    public static void onRequest(@Duration long duration) {
        statsd.increment("api.requests");
        statsd.timing("api.latency", duration / 1_000_000);
    }
}
```

Run with:
```bash
btrace --grant=NETWORK,THREADS -statsd localhost:8125 <pid> StatsdExample.class
```

##### Using the Histogram Metrics Extension (btrace-metrics)

The histogram metrics extension provides high-performance in-process metrics using HdrHistogram. It does not require network permissions and runs entirely inside the target JVM.

Requirements:
- Build the distribution so extensions are exploded under `BTRACE_HOME/extensions/`.
- The agent discovers built-in extensions automatically; no additional flags are needed.

Example:

```java
package myprobes;

import io.btrace.core.annotations.*;
import io.btrace.metrics.MetricsService;
import io.btrace.metrics.histogram.HistogramConfig;
import io.btrace.metrics.histogram.HistogramMetric;
import io.btrace.metrics.histogram.HistogramSnapshot;
import io.btrace.metrics.stats.StatsMetric;
import io.btrace.metrics.stats.StatsSnapshot;

@BTrace
public class HistogramExample {
    // ServiceType hint is optional; omit for defaults
    @Injected
    private static MetricsService metrics;

    private static HistogramMetric histogram;
    private static StatsMetric stats;

    @OnMethod(clazz = "com.example.Service", method = "doWork")
    public static void onEntry() {
        if (histogram == null) {
            histogram = metrics.histogramMicros("service.doWork");
            stats = metrics.stats("service.doWork.stats");
        }
    }

    @OnMethod(clazz = "com.example.Service", method = "doWork", location = @Location(Kind.RETURN))
    public static void onReturn(@Duration long durationNanos) {
        long durationMicros = durationNanos / 1000;
        histogram.record(durationMicros);
        stats.record(durationMicros);
    }

    @OnTimer(1000)
    public static void onTimer() {
        HistogramSnapshot h = histogram.snapshot();
        StatsSnapshot s = stats.snapshot();

        println("=== Metrics Report ===");
        println("Count: " + s.count());
        println("Mean: " + s.mean() + " μs");
        println("Min: " + s.min() + " μs");
        println("Max: " + s.max() + " μs");
        println("P50: " + h.p50() + " μs");
        println("P95: " + h.p95() + " μs");
        println("P99: " + h.p99() + " μs");
        println("======================");
    }
}
```

Run with:
```bash
btrace <pid> HistogramExample.java
```

You should see a periodic metrics report similar to:
```
=== Metrics Report ===
Count: 4
Mean: 4178.5 μs
Min: 118 μs
Max: 16341 μs
P50: 120 μs
P95: 16343 μs
P99: 16343 μs
======================
```

Configuration (optional):
- You can tune defaults in `btrace.conf` (see Architecture: Extension Configuration):
  - `btrace-metrics.histogram.default-precision=3`
  - `btrace-metrics.histogram.max-value=3600000000`

##### Building App Integration Extensions with @ExternalType

When an extension needs to interact with application-specific types (Spark events, Hadoop objects, custom framework classes), use the `@ExternalType` annotation to eliminate manual reflective boilerplate. The BTrace extension plugin auto-registers the annotation processor.

Declare an interface in `src/main/java` annotated with `@ExternalType("fully.qualified.AppType")`:

```java
// src/main/java/org/example/spark/api/JobStartEvent.java
@ExternalType("org.apache.spark.scheduler.SparkListenerJobStart")
public interface JobStartEvent {
    int jobId();
    long time();
}
```

The processor generates `JobStartEvent$Ext` with lazy, cached `MethodHandle` dispatchers. Use them directly in the impl — no `try/catch` or cache setup required:

```java
// src/main/java/org/example/spark/impl/SparkApiImpl.java
public final class SparkApiImpl extends Extension implements SparkApi {
    @Override
    public void onJobStart(Object event) {
        int  id = JobStartEvent$Ext.jobId(event);
        long ts = JobStartEvent$Ext.time(event);
        // emit metrics / logs...
    }
}
```

Resolution happens lazily on the first call via the event object's own class loader; the handle is cached for subsequent calls. If the external class isn't loaded yet, the resolver retries next call — no `ExceptionInInitializerError` at extension startup.

Field access, constructors, non-public methods, and chained external types are **not yet handled** by the processor — they are planned for a future `@ExternalType` version. Use `ClassLoadingUtil` / `MethodHandleCache` directly in the meantime (see [Provided-Style Extensions](architecture/provided-style-extensions.md) for the full scope-limits table and workarounds).

For the full `@ExternalType` reference, see [BTrace Extension Development Guide](BTraceExtensionDevelopmentGuide.md).

##### Zero-Config Probe Auto-Selection (ExtensionConfigurator)

For fat agent deployments (Spark executors, Hadoop nodes, Kubernetes pods), extensions can automatically select the right bundled probes without operator input.

**1. List probe names and declare the configurator in `extension.properties`:**

```properties
probes=DriverTracer,ExecutorTracer
configurator=org.example.spark.SparkConfigurator
```

**2. Stage the compiled probe `.class` files in the fat agent plugin:**

```groovy
btraceFatAgent {
    embedExtensions { project(':my-spark-extension') }
    bundledProbes { from 'src/probes/compiled' }
}
```

**3. Implement `ExtensionConfigurator`:**

```java
public final class SparkConfigurator implements ExtensionConfigurator {
    @Override
    public ProbeConfiguration configure(RuntimeEnvironment env, Map<String, String> args) {
        ProbeConfiguration config = new ProbeConfiguration();
        if (env.hasClass("org.apache.spark.SparkContext")) {
            config.enable("DriverTracer");
        } else if (env.hasClass("org.apache.spark.executor.Executor")) {
            config.enable("ExecutorTracer");
        }
        return config;
    }
}
```

`RuntimeEnvironment` provides `hasClass(String)`, `getSystemProperty(String)`, `getEnv(String)`, and `getMainClassName()` for environment detection.

**4. Operator attaches — no `probes=` argument needed:**

```bash
java -javaagent:my-btrace-agent-fat.jar spark-submit ...
```

If the operator supplies `probes=`, it takes priority and the configurator is skipped. See [Extension Development Guide — Bundled Probes](BTraceExtensionDevelopmentGuide.md#bundled-probes-and-zero-config-auto-selection) for the full API.

##### Troubleshooting Failed Extensions

If extensions fail to load during agent initialization (for example, due to missing dependencies or configuration issues), BTrace will display a warning when you submit a probe:

```
[BTRACE WARN] 1 extension(s) failed to load:
  - StatsdExtension: Missing manifest metadata (ensure Gradle plugin is applied and configured)
Use 'btrace -le <PID>' for details.
```

###### Listing Failed Extensions

Use the `-le` option to see detailed information about failed extensions:

```bash
btrace -le <pid>
```

This will display all extensions that failed to load and the reasons for their failures:

```
Failed Extensions:
  1. io.btrace.statsd.StatsdExtension: Connection refused to localhost:8125
  2. io.btrace.dtrace.DTraceExtension: DTrace not available on this platform
```

###### Interactive Menu

When attached to a JVM in interactive mode (press Ctrl-C), you can also select option **7** to list failed extensions:

```
Please enter your option:
        1. exit
        2. send an event
        3. send a named event
        4. flush console output
        5. list probes
        6. detach client
        7. list failed extensions
```

This is useful for diagnosing issues when probes that rely on specific extensions are not working as expected.

##### Writing Your Own Extension (Quick Start)

**Hands-on lab →** [Write Your Own Extension in 30 Minutes](tutorials/06-write-your-own-extension.md) builds, installs, permission-grants, and injects a real extension end to end against a live JVM — including a couple of source-verified gotchas in the plugin worth knowing before you rely on it.

Extensions are standalone Gradle projects that expose a typed service interface to BTrace scripts. Unlike some plugin ecosystems, the API and implementation classes live in the **same package** — the bundled `btrace-metrics` extension (`btrace-extensions/btrace-metrics/src/main/java/io/btrace/metrics/`) is the canonical example: `MetricsService` (the interface) and `MetricsServiceImpl` (the implementation) sit side by side, and the Gradle plugin partitions API from implementation itself when it builds the API/impl jars. The full workflow is covered in the [BTrace Extension Development Guide](BTraceExtensionDevelopmentGuide.md); the steps below are the minimum to get started.

**1. Apply the BTrace extension Gradle plugin**

```gradle
plugins {
    id("io.btrace.extension") version "<btraceVersion>"
}

btraceExtension {
    id = "com.example.myext"
    services = ["com.example.myext.MyService"]
}
```

**2. Define the service API (what scripts see)**

```java
// src/main/java/com/example/myext/MyService.java
package com.example.myext;

public interface MyService {
    void record(String key, long value);
}
```

**3. Implement the service (stays isolated from scripts)**

```java
// src/main/java/com/example/myext/MyServiceImpl.java
package com.example.myext;

import io.btrace.core.extensions.Extension;

public class MyServiceImpl extends Extension implements MyService {
    @Override
    public void record(String key, long value) {
        // emit metric, write to log, etc.
    }
}
```

**4. Inject and use the service in a script**

```java
import io.btrace.core.annotations.*;
import com.example.myext.MyService;

@BTrace
public class MyProbe {
    @Injected
    private static MyService myService;

    @OnMethod(clazz="com.example.App", method="processRequest",
              location=@Location(Kind.RETURN))
    public static void onReturn(@Duration long d) {
        myService.record("app.processRequest", d);
    }
}
```

The plugin generates the service descriptor manifest, computes the required permission set, and produces a distributable ZIP that installs under `$BTRACE_HOME/extensions/`. Always list your service interface(s) explicitly in `services = [...]` as shown above — the plugin's own auto-detection fallback (for projects that omit `services`) still scans for the pre-3.0 `org.openjdk.btrace.core.extensions.ServiceDescriptor` annotation, not the current `io.btrace.core.extensions` package, so it never fires against a 3.0-style extension in this checkout. See the [hands-on lab](tutorials/06-write-your-own-extension.md) for the full, verified story on that and other plugin gotchas, and the [BTrace Extension Development Guide](BTraceExtensionDevelopmentGuide.md) for detailed coverage of: classloader isolation, permission declarations, `@ExternalType` adapters, fat-agent embedding, and the full API design rules documented in [ExtensionInterfaceRules.md](ExtensionInterfaceRules.md).

---

## Lesson 7 - Flat DSL

**Hands-on lab →** [From Oneliner to Script: The Flat DSL](tutorials/02-oneliner-to-script.md) builds this up step by step against a live JVM, from watching a oneliner's generated source to a full `@TLS`-based script.

BTrace 3.0+ ships a flat DSL class `io.btrace.BTrace` that exposes the most common helper operations as plain static methods. The compiler **automatically injects two imports** into every script:

* `import static io.btrace.BTrace.*;` — all flat DSL helpers (`println`, `str`, `monotonic`, …)
* `import io.btrace.core.annotations.*;` — all BTrace annotations (`@BTrace`, `@OnMethod`, `@TLS`, `@Level`, …)

This means a minimal BTrace script requires **zero import statements** — just annotate your class and write the probe logic.

##### What the flat DSL provides

| Category | Methods |
|----------|---------|
| Output | `print(s)`, `println(s)`, `println()`, `printf(fmt, args...)` |
| Strings | `str(o)`, `concat(a,b)`, `substr(s,start,end)`, `matches(s,regex)`, `startsWith(s,prefix)`, `endsWith(s,suffix)`, `length(s)` |
| Numbers | `abs(l/d)`, `min(a,b)`, `max(a,b)` |
| Time | `timestamp()` (wall clock ms), `monotonic()` (nanos) |
| Thread | `currentThread()`, `threadName(t)`, `threadId(t)` |
| Stack | `stackTrace()`, `printStack()`, `stackDepth()` |
| Object | `className(o)`, `identity(o)`, `size(o)` |
| Control | `exit(code)` |

For everything else (aggregations, reflection, profiling, JFR, data structures, thread operations) use `BTraceUtils` or an injected extension service.

##### Writing scripts without explicit imports

Before (classic style):

```java
import io.btrace.core.BTraceUtils;
import io.btrace.core.annotations.*;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="/.*/")
    public static void onMethod(@ProbeMethodName String pmn) {
        BTraceUtils.println("Hello from method " + pmn);
    }
}
```

After (modern zero-import style — both imports auto-injected by the compiler):

```java
@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="/.*/")
    public static void onMethod(@ProbeMethodName String pmn) {
        println("Hello from method " + pmn);
    }
}
```

You can still write the imports explicitly if you prefer (for IDE auto-complete, for example) — the compiler skips injection when they are already present:

```java
import static io.btrace.BTrace.*;
import io.btrace.core.annotations.*;

@BTrace
public class HelloWorldTrace { ... }
```

##### Mixing flat DSL with BTraceUtils

The flat DSL covers the most frequent operations. When you need something it does not offer, import `BTraceUtils` directly alongside it:

```java
import io.btrace.core.BTraceUtils;
import io.btrace.core.annotations.*;

@BTrace
public class HelloWorldTrace {
    @OnMethod(clazz="extra.HelloWorld", method="/call.*/", location=@Location(Kind.RETURN))
    public static void onMethod(@ProbeMethodName(fqn = true) String pmn,
                                @Self Object thiz) {
        // println/str come from the flat DSL (auto-injected)
        println("Hello from method " + pmn);
        // getInt/classOf come from BTraceUtils (explicit import)
        println("field = " + str(BTraceUtils.getInt("field", thiz)));
    }
}
```

##### How it works under the hood

At compile time, every `INVOKESTATIC` targeting `io.btrace.BTrace.*` is rewritten by the post-processor to an `INVOKEDYNAMIC` instruction that routes the call through `BTraceBootstrap`. This gives probe code stronger isolation from the target application's classloader hierarchy without any visible change in the script source.

---

## Lesson 8 - Thread-Local Storage

When a probe spans two separate handler methods — for example, recording a start time at method entry and computing duration at method return — you need per-thread state. BTrace provides the `@TLS` annotation for exactly this purpose: a field marked `@TLS` behaves like a `ThreadLocal`, with each thread seeing its own independent copy.

__Note:__ Fields annotated with `@TLS` must be `static`. Primitive types and `String` are the most reliable choices; object references work too but require care because the field is null-initialized per thread on first access.

##### Measuring per-thread duration

```java
import io.btrace.core.annotations.*;

@BTrace
public class HelloWorldTrace {
    @TLS
    private static long startTime;   // one copy per thread

    @OnMethod(clazz="extra.HelloWorld", method="/call.*/")
    public static void onEntry(@ProbeMethodName(fqn = true) String pmn) {
        startTime = monotonic();     // record entry time for this thread
        println("Entering " + pmn);
    }

    @OnMethod(clazz="extra.HelloWorld", method="/call.*/",
              location=@Location(Kind.RETURN))
    public static void onReturn(@ProbeMethodName(fqn = true) String pmn) {
        long dur = monotonic() - startTime;
        println(pmn + " took " + str(dur) + " ns");
    }
}
```

__Note:__ `@TLS` is reset to its zero/null value when the thread exits, so it is safe under thread pools where threads are reused across requests.

---

## Lesson 9 - Instrumentation Levels

Tracing everything at once can be overwhelming and expensive. The `@Level` annotation lets you define multiple probe handlers that are enabled at different _instrumentation levels_. At runtime you switch the active level through `@OnEvent` handlers, dialing the detail up or down without stopping and restarting the probe.

##### Defining level-aware handlers

```java
import io.btrace.core.BTraceUtils;
import io.btrace.core.annotations.*;

@BTrace
public class HelloWorldTrace {
    /**
     * Level 0 (default): only trace entry into HelloWorld's own methods.
     * Enabled when the instrumentation level equals exactly 0.
     */
    @OnMethod(clazz="extra.HelloWorld", method="/.*/",
              enableAt=@Level("=0"))
    public static void onMethodL0(@ProbeMethodName(fqn = true) String pmn) {
        println("[L0] " + pmn);
    }

    /**
     * Level 1+: trace the whole class hierarchy including subclasses.
     * Enabled when the instrumentation level is 1 or higher.
     */
    @OnMethod(clazz="+extra.HelloWorld", method="/.*/",
              enableAt=@Level(">=1"))
    public static void onMethodL1(@ProbeMethodName(fqn = true) String pmn) {
        println("[L1] " + pmn);
    }

    /** Raise to level 1 by sending a named event. */
    @OnEvent("verbose")
    public static void setVerbose() {
        BTraceUtils.setInstrumentationLevel(1);
        println("Switched to verbose tracing (level 1)");
    }

    /** Drop back to level 0. */
    @OnEvent("quiet")
    public static void setQuiet() {
        BTraceUtils.setInstrumentationLevel(0);
        println("Switched back to quiet tracing (level 0)");
    }
}
```

##### Level expression syntax

| Expression | Meaning |
|------------|---------|
| `@Level` (no value) | Always enabled (default) |
| `@Level("N")` | Level >= N (shorthand for `>=N`) |
| `@Level("=N")` | Exactly level N |
| `@Level(">N")` | Strictly greater than N |
| `@Level(">=N")` | Greater than or equal to N |
| `@Level("<N")` | Strictly less than N |
| `@Level("<=N")` | Less than or equal to N |

Where N is a non-negative integer.

##### Switching levels from the command line

While attached interactively (press Ctrl-C), choose option **2** or **3** to send an event. For the example above:

```
Please enter your option: 3
Please enter event name: verbose
```

The `setVerbose()` handler fires immediately and BTrace retransforms the target classes to activate the higher-detail probes.

---

## Lesson 10 - Oneliners

**Hands-on lab →** [Your First Trace in 2 Minutes](tutorials/01-first-trace-in-2-minutes.md) finds a real latency bug and a swallowed exception in a live JVM using nothing but oneliners.

For quick, ad-hoc investigations you do not need a `.java` file at all. BTrace supports **oneliners** via the `-n` flag: a compact, DTrace-inspired syntax that compiles to a full BTrace script internally.

```bash
btrace -n 'CLASS::METHOD @LOCATION [filter] { ACTION }' <PID>
```

##### Components

| Part | Description |
|------|-------------|
| `CLASS` | Class name, wildcard (`*`, `**`), or `/regex/` |
| `METHOD` | Method name, wildcards, or `/regex/`; `<init>` for constructors |
| `@LOCATION` | `@entry`, `@return`, or `@error` |
| `filter` | Optional: `if duration>100ms` or `if args[0]=="VALUE"` |
| `ACTION` | One or more of `print`, `count`, `time`, `stack(N)`, separated by commas |

##### Available identifiers in actions

| Identifier | Available at | Description |
|------------|-------------|-------------|
| `method` | all | Method name |
| `class` | all | Class name |
| `args` | all | Method arguments array |
| `self` | all | Receiver (`this`) |
| `duration` | `@return`, `@error` | Execution time in nanoseconds |
| `return` | `@return` | Return value |

##### Examples

**Print every call into `callA`:**
```bash
btrace -n 'extra.HelloWorld::callA @entry { print method, args }' <PID>
```

**Find calls slower than 5 ms:**
```bash
btrace -n 'extra.HelloWorld::* @return if duration>5ms { print method, duration }' <PID>
```

**Count all file opens:**
```bash
btrace -n 'java.io.FileInputStream::<init> @entry { count }' <PID>
```

**Print the stack when an `IOException` is constructed:**
```bash
btrace -n 'java.io.IOException::<init> @entry { print args, stack(8) }' <PID>
```

**Match methods by regex and time them:**
```bash
btrace -n '/com\.example\..*/::/handle.*/ @return { time }' <PID>
```

##### Limitations

- Single probe point per oneliner (no `|`-separated multi-probe)
- No `@call` location, no field access probes
- No aggregations (histograms, averages) — use a full script for those
- Filters support simple comparisons only; no `&&`/`||`

For complex scenarios — multiple probe points, state across probes, aggregations — convert to a full BTrace script. See the [Oneliner Guide](OnelinerGuide.md) for the complete syntax reference and more examples.

---

## Lesson 11 — Runtime Contracts (`btrace-contracts`)

**Hands-on lab →** [Watch Your LLM App Think](tutorials/07-llm-observability.md) puts a
`checkLatency` budget on a live call and reads it off a combined dashboard alongside
`btrace-llm-trace`'s token/cost accounting.

**Extension ID:** `btrace-contracts`  
**Service class:** `io.btrace.contracts.ContractService`

`ContractService` enforces behavioral invariants at runtime — latency budgets, call-rate limits, null checks, value-range assertions — without modifying the target code. All checks are non-throwing: a violation is counted internally and the target application keeps running. It also tracks call count and average latency per user-supplied tag, so you can compare any two code paths side by side (e.g. `"cached"` vs `"direct"`, `"v1"` vs `"v2"`).

#### API

```java
void checkLatency(String contract, long durationNanos, long budgetNanos)
void checkCallRate(String contract, int maxPerSecond)
void assertCondition(String contract, boolean condition, String message)
void checkRange(String contract, long value, long min, long max)
void checkNotNull(String contract, Object value)
void trackCodePath(String contract, long durationNanos, String tag)
String getSummary()
boolean hasViolations()
long getTotalViolations()
```

#### Example — enforcing contracts on a service endpoint

```java
import io.btrace.contracts.ContractService;
import io.btrace.core.annotations.*;
import static io.btrace.BTrace.*;

@BTrace
public class ContractCheck {

    @Injected private static ContractService contracts;

    @OnMethod(
        clazz  = "com.example.RecommendationService",
        method = "recommend",
        location = @Location(Kind.ENTRY)
    )
    public static void onEntry() {
        contracts.checkCallRate("recommend/rate", 10);
    }

    @OnMethod(
        clazz  = "com.example.RecommendationService",
        method = "recommend",
        location = @Location(Kind.RETURN)
    )
    public static void onReturn(@Return Object result, @Duration long dur) {
        contracts.checkLatency("recommend/latency", dur, 500_000_000L);
        contracts.checkNotNull("recommend/non-null-result", result);
        contracts.trackCodePath("recommend", dur, "impl-a");
    }

    @OnEvent
    public static void report() {
        println(contracts.getSummary());
        if (contracts.hasViolations()) {
            println(strcat("Total violations: ", str(contracts.getTotalViolations())));
        }
    }

    @OnTimer(15000)
    public static void periodicReport() { report(); }
}
```

Copy `btrace-contracts.jar` to `$BTRACE_HOME/extensions/`, then run:

```bash
btrace <PID> ContractCheck.java
```

---

## Lesson 12 — AI/LLM Application Observability

**Hands-on lab →** [Watch Your LLM App Think](tutorials/07-llm-observability.md) wires
`btrace-llm-trace` and `btrace-contracts` into a mocked OpenAI-shaped call and reads
token/latency/cost and budget violations off a live JVM, including a build-time detail worth
knowing before you grant either extension a permission.

Modern Java applications increasingly embed LLM inference, RAG pipelines, and on-device model execution. BTrace ships three optional extension JARs that add purpose-built services for observing these workloads without modifying application code.

All three extensions use the standard `@Injected` mechanism: declare a field in your BTrace script, annotate it, and BTrace wires up the implementation at deploy time. No reflection, no extra threads, no allocation on the hot path.

#### 12.1 LLM Inference Tracing (`btrace-llm-trace`)

**Extension ID:** `btrace-llm-trace`  
**Service class:** `io.btrace.llm.LlmTraceService`  
**Builder class:** `io.btrace.llm.CallRecord`

`LlmTraceService` records LLM API calls: token counts (input, output, cache-read, cache-creation), latency, streaming time-to-first-token, errors, tool calls, and embeddings. It maintains per-model statistics — call count, total tokens, and latency min/mean/max — and estimates cost using a built-in pricing table covering Claude, GPT-4o, Gemini, and other common models. The implementation is thread-safe and allocation-free on the hot path via a `ThreadLocal`-pooled builder.

##### Simple API

```java
void recordCall(String model, long durationNanos)
void recordCall(String model, int inputTokens, int outputTokens, long durationNanos)
CallRecord call(String model)            // returns a fluent builder
void recordEmbedding(String model, int tokenCount, long durationNanos)
void recordToolUse(String model, String toolName)
void recordError(String model, String errorType, long durationNanos)
String getSummary()
double getEstimatedCostUsd()
```

The fluent builder lets you attach every detail of a call in one expression:

```java
llm.call("claude-sonnet-4-20250514")
    .provider("anthropic")
    .inputTokens(1500)
    .outputTokens(300)
    .cacheReadTokens(800)
    .streaming()
    .timeToFirstToken(200_000_000L)
    .duration(durationNanos)
    .record();
```

##### Full example — instrumenting LangChain4j

The following script instruments the `ChatLanguageModel.generate()` method from the LangChain4j library and extracts token usage from the `AiMessage` response via `@Return`:

```java
import io.btrace.llm.LlmTraceService;
import io.btrace.llm.CallRecord;
import io.btrace.core.annotations.*;
import static io.btrace.BTrace.*;

@BTrace
public class LlmTrace {

    @Injected
    private static LlmTraceService llm;

    // @Duration is available at Kind.RETURN and Kind.ERROR — no manual timestamps needed.
    // @ProbeClassName identifies the concrete implementation class at the probe site.

    @OnMethod(
        clazz  = "+dev.langchain4j.model.chat.ChatLanguageModel",
        method = "generate",
        location = @Location(Kind.RETURN)
    )
    public static void onChatReturn(@ProbeClassName String cls,
                                    @Return Object response,
                                    @Duration long durationNanos) {
        // Extract token counts via BTrace field-access helpers.
        // TokenUsage is nested inside Response<AiMessage>.
        Object tokenUsage = get(
            field("dev.langchain4j.model.output.Response", "tokenUsage"),
            response);
        int inputTokens  = 0;
        int outputTokens = 0;
        if (tokenUsage != null) {
            inputTokens  = (Integer) get(
                field("dev.langchain4j.model.output.TokenUsage", "inputTokenCount"),
                tokenUsage);
            outputTokens = (Integer) get(
                field("dev.langchain4j.model.output.TokenUsage", "outputTokenCount"),
                tokenUsage);
        }

        llm.call(cls)
            .inputTokens(inputTokens)
            .outputTokens(outputTokens)
            .duration(durationNanos)
            .record();
    }

    @OnMethod(
        clazz  = "+dev.langchain4j.model.chat.ChatLanguageModel",
        method = "generate",
        location = @Location(Kind.ERROR)
    )
    public static void onChatError(@ProbeClassName String cls,
                                   Throwable t,
                                   @Duration long durationNanos) {
        llm.recordError(cls, str(classOf(t)), durationNanos);
    }

    // Print a summary on demand (btrace send event) or every 60 seconds.
    @OnEvent
    public static void printSummary() {
        println(llm.getSummary());
        println(strcat("Estimated cost: $",
                       str(llm.getEstimatedCostUsd())));
    }

    @OnTimer(60000)
    public static void periodicSummary() {
        printSummary();
    }
}
```

Copy `btrace-llm-trace.jar` to `$BTRACE_HOME/extensions/` — the agent discovers and loads it automatically. Then run as normal:

```bash
btrace <PID> LlmTrace.java
```

---

#### 12.2 RAG Pipeline Observability (`btrace-rag-quality`)

**Extension ID:** `btrace-rag-quality`  
**Service class:** `io.btrace.rag.RagQualityService`  
**Builder class:** `io.btrace.rag.QueryRecord`

`RagQualityService` tracks vector database query performance, result counts, similarity scores, and end-to-end pipeline latency broken down into retrieval and generation phases. It counts empty retrievals and chunk token sizes and works with any vector store: Pinecone, Milvus, Weaviate, Chroma, pgvector, Qdrant, and others.

##### Simple API

```java
void recordQuery(String source, long durationNanos)
void recordQuery(String source, int resultCount, long durationNanos)
QueryRecord query(String source)         // returns a fluent builder
void recordPipeline(String pipelineName, long retrievalNanos, long generationNanos)
void recordChunk(String source, int chunkTokens)
void recordEmptyRetrieval(String source)
String getSummary()
float getAverageTopScore()
```

Fluent builder:

```java
rag.query("pinecone")
    .resultCount(5)
    .topScore(0.92f)
    .lowScore(0.71f)
    .embeddingDimension(1536)
    .duration(durationNanos)
    .record();
```

##### Full example — RAG pipeline with Pinecone and LangChain4j

This script instruments the retrieval call to a Pinecone-backed `EmbeddingStore` and the downstream LLM call together, giving a unified view of the pipeline:

```java
import io.btrace.rag.RagQualityService;
import io.btrace.rag.QueryRecord;
import io.btrace.llm.LlmTraceService;
import io.btrace.core.annotations.*;
import static io.btrace.BTrace.*;

@BTrace
public class RagPipeline {

    @Injected private static RagQualityService rag;
    @Injected private static LlmTraceService   llm;

    // Carries the retrieval duration from the retrieval handler into the pipeline
    // handler, which runs after retrieval completes on the same thread.
    @TLS private static long lastRetrievalDur;

    // --- Retrieval phase ---

    @OnMethod(
        clazz  = "+dev.langchain4j.store.embedding.EmbeddingStore",
        method = "findRelevant",
        location = @Location(Kind.RETURN)
    )
    public static void onRetrievalReturn(@Return Object results,
                                         @ProbeClassName String store,
                                         @Duration long dur) {
        int count = (results != null) ? (Integer) call(
            method("java.util.List", "size"), results) : 0;

        if (count == 0) {
            rag.recordEmptyRetrieval(store);
        } else {
            rag.query(store)
               .resultCount(count)
               .duration(dur)
               .record();
        }
        lastRetrievalDur = dur;
    }

    // --- Generation phase ---

    @OnMethod(
        clazz  = "+dev.langchain4j.chain.ConversationalRetrievalChain",
        method = "execute",
        location = @Location(Kind.ENTRY)
    )
    public static void onPipelineEntry() {
        lastRetrievalDur = 0;
    }

    @OnMethod(
        clazz  = "+dev.langchain4j.chain.ConversationalRetrievalChain",
        method = "execute",
        location = @Location(Kind.RETURN)
    )
    public static void onPipelineReturn(@ProbeClassName String cls,
                                        @Duration long totalNanos) {
        long generationNanos = totalNanos - lastRetrievalDur;
        rag.recordPipeline(cls, lastRetrievalDur, generationNanos);
        llm.recordCall(cls, generationNanos);
    }

    @OnEvent
    public static void printSummary() {
        println("=== RAG Summary ===");
        println(rag.getSummary());
        println(strcat("Average top similarity score: ",
                       str(rag.getAverageTopScore())));
        println("=== LLM Summary ===");
        println(llm.getSummary());
    }

    @OnTimer(30000)
    public static void periodicSummary() {
        printSummary();
    }
}
```

Copy both extension JARs to `$BTRACE_HOME/extensions/`, then run:

```bash
btrace <PID> RagPipeline.java
```

---

#### 12.3 GPU and Inference Observability (`btrace-gpu-bridge`)

**Extension ID:** `btrace-gpu-bridge`  
**Service class:** `io.btrace.gpu.GpuBridgeService`  
**Builder class:** `io.btrace.gpu.InferenceRecord`

`GpuBridgeService` traces the boundary between JVM code and GPU-accelerated inference runtimes: ONNX Runtime, Deep Java Library (DJL), TensorFlow Java, and Panama FFM calls into CUDA/ROCm native libraries. It tracks batch sizes, tensor dimensions, device type and ID, GPU memory allocation and deallocation, and model load times.

##### Simple API

```java
void recordInference(String runtime, String modelName, long durationNanos)
void recordInference(String runtime, String modelName, int batchSize, long durationNanos)
InferenceRecord inference(String runtime, String modelName)   // fluent builder
void recordMemoryAlloc(String deviceType, int deviceId, long bytes)
void recordMemoryFree(String deviceType, int deviceId, long bytes)
void recordNativeCall(String library, String function, long durationNanos)
void recordModelLoad(String runtime, String modelName, long durationNanos)
String getSummary()
long getCurrentGpuMemoryBytes()
long getPeakGpuMemoryBytes()
```

##### Example — instrumenting ONNX Runtime

```java
import io.btrace.gpu.GpuBridgeService;
import io.btrace.gpu.InferenceRecord;
import io.btrace.core.annotations.*;
import static io.btrace.BTrace.*;

@BTrace
public class OnnxTrace {

    @Injected private static GpuBridgeService gpu;

    // @Self is available at Kind.RETURN, so no TLS is needed to carry the session reference.
    // @Duration provides the call duration without manual timestamp diffing.

    @OnMethod(
        clazz  = "ai.onnxruntime.OrtSession",
        method = "run",
        location = @Location(Kind.RETURN)
    )
    public static void onRunReturn(@Self Object session, @Duration long dur) {
        String modelPath = str(get(
            field("ai.onnxruntime.OrtSession", "modelPath"), session));
        gpu.inference("onnxruntime", modelPath)
           .duration(dur)
           .record();
    }

    @OnMethod(
        clazz  = "ai.onnxruntime.OrtSession",
        method = "run",
        location = @Location(Kind.ERROR)
    )
    public static void onRunError(Throwable t, @Duration long dur) {
        gpu.recordNativeCall("onnxruntime", "run/error", dur);
    }

    // @Duration at Kind.RETURN gives the load duration directly; no ENTRY handler needed.
    @OnMethod(
        clazz  = "ai.onnxruntime.OrtEnvironment",
        method = "createSession",
        location = @Location(Kind.RETURN)
    )
    public static void onLoadReturn(@Return Object sess, @Duration long dur) {
        String mp = str(get(
            field("ai.onnxruntime.OrtSession", "modelPath"), sess));
        gpu.recordModelLoad("onnxruntime", mp, dur);
    }

    @OnEvent
    public static void printSummary() {
        println(gpu.getSummary());
        println(strcat("Current GPU memory: ",
                       str(gpu.getCurrentGpuMemoryBytes() / (1024 * 1024)) + " MB"));
        println(strcat("Peak GPU memory:    ",
                       str(gpu.getPeakGpuMemoryBytes()    / (1024 * 1024)) + " MB"));
    }

    @OnTimer(60000)
    public static void periodicSummary() {
        printSummary();
    }
}
```

Copy `btrace-gpu-bridge.jar` to `$BTRACE_HOME/extensions/`, then run:

```bash
btrace <PID> OnnxTrace.java
```

---

#### Using multiple AI extensions together

All four extensions can be combined in a single script. Copy all the extension JARs you need to `$BTRACE_HOME/extensions/` — the agent discovers and loads them automatically:

```bash
cp btrace-llm-trace.jar btrace-rag-quality.jar \
   btrace-gpu-bridge.jar \
   $BTRACE_HOME/extensions/
btrace <PID> MyAIObservability.java
```

When using the fat agent at JVM startup, the same rule applies — extensions under `$BTRACE_HOME/extensions/` are loaded automatically:

```bash
java -javaagent:btrace.jar=script=MyAIObservability.java -jar myapp.jar
```

Use `@OnEvent` for on-demand reporting triggered by `btrace send event <PID>` and `@OnTimer` for periodic snapshots. Both can coexist in the same script.

---

## Lesson 13 — BTrace MCP Server: AI Agents as Diagnosticians

**Hands-on lab →** [Let an AI Assistant Debug Your JVM in 10 Minutes](tutorials/05-mcp-server.md) wires the server into Claude Code and walks a real diagnostic conversation end to end, including the gaps in the current tool set worth knowing about before you rely on it.

#### What is MCP?

MCP (Model Context Protocol) is a protocol that lets AI assistants call external tools during a conversation. Instead of the AI only producing text, it can invoke structured operations — search, read a file, query a database — and incorporate the results into its response. The BTrace MCP server exposes BTrace operations as MCP tools, so an LLM client such as Claude Desktop or Claude Code can attach to running JVMs, deploy probes, read output, and clean up — all through natural language conversation.

#### How the BTrace MCP server works

The BTrace MCP server runs as a local subprocess on the same machine as the target JVM. The AI client starts and manages the server process; you do not need to keep a terminal open for it. When the AI calls a BTrace tool, the server forwards the request to the BTrace agent (or attaches one if none is present) and returns the result. Because the server only connects to local JVMs, and because BTrace's safety model still applies to every probe — no loops, no field writes, no thread creation, and only whitelisted method calls — the AI can observe the target application in detail but cannot alter its behavior. These restrictions bound resource use rather than eliminate it: implicit allocation (autoboxing, string concatenation) is permitted, and a recursive helper is limited only by the thread stack, so probes are constrained but not guaranteed allocation-free.

#### Starting the server manually

Install the `btrace-observability` plugin from the BTrace Agent Plugins marketplace. It launches
the bundled MCP server with JBang and the single masked `io.btrace:btrace` artifact; no manual
classpath assembly is required.

The server listens on `stdio` (the MCP transport used by most clients). There is no global port
override at server startup: `port` is a per-tool-call argument on each deploy/interact tool
(`deploy_oneliner`, `deploy_script`, `list_probes`, `send_event`, `detach_probe`, `exit_probe`),
defaulting to `2020` if omitted — pass a different value on the call itself if the target agent
listens elsewhere.

If no BTrace agent is attached to the target JVM, the `deploy_oneliner` and `deploy_script` tools auto-attach one using the JVM's attach API — no extra step required.

#### Client setup

See [MCPServer.md](MCPServer.md) for installation and runtime requirements.

#### Available tools

| Tool | Description |
|---|---|
| `list_jvms` | List all local JVMs with their PIDs, main class, and BTrace attachment status |
| `deploy_oneliner` | Deploy a single-expression probe using oneliner syntax (auto-attaches agent) |
| `deploy_script` | Deploy a full BTrace script file to a target JVM |
| `list_probes` | List probes that have been detached from and are available to reconnect to (via the plain CLI's `-r` flag) |
| `send_event` | Send a named event to trigger `@OnEvent` handlers |
| `detach_probe` | Disconnect the client from a probe; the probe keeps running |
| `exit_probe` | Stop and remove the probe this client session is tracking |

#### Available prompts

The server ships three pre-built diagnostic workflows that the AI can invoke as structured prompts:

| Prompt | What it does |
|---|---|
| `diagnose_slow_endpoint` | Attaches timing probes to HTTP handler methods and reports latency distribution |
| `find_exception_source` | Traces exception construction and propagation across the call stack |
| `profile_method` | Measures throughput and latency of a named method over a configurable window |

#### Example conversation

The following exchange shows how an AI assistant uses BTrace MCP tools to diagnose a slow HTTP endpoint. Lines marked `[tool]` represent MCP tool calls made by the AI during the conversation.

---

**User:** My `/recommendations` endpoint is taking over two seconds. Can you help me find out why?

**AI:** I will start by finding which JVMs are running on this machine.

`[tool: list_jvms]`

```
Discovered Java VMs:

  PID: 12345  |  Main Class: com.example.app.ApplicationMain  |  BTrace: not attached
```

I found your application at PID 12345. Let me deploy a timing probe to the HTTP handler layer.

`[tool: deploy_oneliner]`
```
pid: "12345"
oneliner: /com\.example\..*/::/.*recommendations.*/i @return { print method, time }
```

```
Probe deployed successfully to PID 12345.
Oneliner: /com\.example\..*/::/.*recommendations.*/i @return { print method, time }
Port: 2020

Initial output:
RecommendationController.handle
execution time: 2341 ms
RecommendationController.handle
execution time: 2289 ms

Use send_event, detach_probe, or exit_probe to interact with the running probe.
```

The controller itself is taking over two seconds. Let me look inside it — I will check whether the delay is in the database call or the LLM call. Since this server tracks one active session per PID, deploying the next probe to the same PID replaces my handle to this one, so I don't need to detach first.

`[tool: deploy_oneliner]`
```
pid: "12345"
oneliner: /com\.example\.recommendation\..*/::/.+/ @return { print method, time }
```

```
Probe deployed successfully to PID 12345.
Oneliner: /com\.example\.recommendation\..*/::/.+/ @return { print method, time }
Port: 2020

Initial output:
RecommendationRepository.findCandidates
execution time: 1987 ms
LlmRankingService.rank
execution time: 312 ms

Use send_event, detach_probe, or exit_probe to interact with the running probe.
```

The database retrieval accounts for most of the latency. The LLM ranking step is fast. Let me clean up the probe.

`[tool: exit_probe]`
```
pid: "12345"
```

```
Probe stopped and removed from PID 12345.
```

**AI:** The bottleneck is `RecommendationRepository.findCandidates`, which takes roughly 2 seconds out of the total 2.3 seconds. The LLM ranking step adds only about 300 ms. I recommend investigating the query or index used by `findCandidates` — adding an index on the candidate-selection columns would likely resolve most of the latency.

---

#### Security note

The BTrace MCP server only attaches to JVMs on the local machine; it cannot connect to remote processes. Every probe the AI deploys goes through BTrace's standard verifier, which enforces the same restrictions as any other BTrace script: no loops, no field writes, no thread creation, and only whitelisted method calls. The AI can observe your application in detail but cannot alter its behavior. These restrictions bound resource use rather than eliminate it — implicit allocation (autoboxing, string concatenation) is permitted and a recursive helper is limited only by the thread stack — so probes cannot corrupt application state but are not guaranteed to be allocation-free.

## Lesson 14 - Packaging: Fat Agents and Containers

**Hands-on labs →** [One JAR to Rule Them All](tutorials/08-fat-agent.md) (fat agents) and [BTrace in Kubernetes, the Sidecar Way](tutorials/09-kubernetes-sidecar.md) (containers) walk both of these end to end against a live JVM, including a few real gaps between what's documented and what the current build actually does.

Two packaging paths exist beyond a plain `$BTRACE_HOME` install, for when you need to ship BTrace as part of someone else's deployment rather than run it interactively yourself.

**Fat agents** bundle BTrace plus one or more extensions into a single `-javaagent` JAR, with no separate extensions directory to copy alongside it. Both the `io.btrace.fat-agent` Gradle plugin and the Maven `fat-agent` goal produce this JAR by embedding an extension's API classes as plain `.class` files and its implementation as `.classdata`, matching the same masked-classdata scheme the main distribution uses (see [Masked JAR Architecture](architecture/MaskedJarArchitecture.md)). Worth knowing before you rely on this: extensions embedded this way are parsed with an empty permission set, so the privileged-permission gate that governs filesystem-installed extensions (Lesson 6) never applies to anything you bundle into a fat agent — embedding a privileged extension *is* the grant, with no separate opt-in.

**Containers** ship as three official image variants — a full toolchain image, a smaller Alpine-based image for sidecars, and a distroless image with just the runtime JARs (no shell, so no interactive attach — the probe has to be baked in via `-javaagent` at build time). The common patterns are copying the distribution out of the official image with a multi-stage `COPY --from` build, or running BTrace as a separate sidecar container sharing the target pod's process namespace (`shareProcessNamespace: true` plus the `SYS_PTRACE` capability). See [docker/README.md](../docker/README.md) for the full pattern catalog and [GettingStarted.md](GettingStarted.md#btrace-in-containers-and-kubernetes) for the conceptual overview.

## Lesson 15 - Java Versions and Instrumentation Backends

BTrace 3.0 fully supports Java 8 through the latest release, but running it against a JVM **older than Java 17 is now deprecated**. Nothing breaks: the agent emits a one-time warning to the target JVM's stderr, and the client prints a matching notice when it attaches. The exact text, produced by `io.btrace.core.JavaVersionCheck`:

```
[BTrace] WARNING: This JVM is Java <N>. Running BTrace on Java versions older than 17 is deprecated and support will be removed in the next major release. Please upgrade to Java 17 or newer. Suppress this warning with -Dbtrace.suppressJavaDeprecationWarning=true.
```

The warning fires at most once per JVM and never throws, so it cannot interfere with agent or client startup. If you're deliberately running a fleet on older JDKs during a gradual migration, set `-Dbtrace.suppressJavaDeprecationWarning=true` on the target JVM to silence it. Java 8–16 support itself is not going away in 3.0 — only in BTrace's *next* major release. See [Migrating from 2.x to 3.0](Migration-2.x-to-3.0.md) for the full support policy and timeline.

Separately, and unrelated to the deprecation floor, BTrace 3.0 also gained a second instrumentation backend to handle newer bytecode. BTrace's primary pipeline is built on ASM, which can only parse class files up to major version 69 (Java 25) — an application compiled for Java 26+ would be unparseable by ASM alone. When the agent itself runs on JDK 24 or newer, a second backend built on the JDK's own ClassFile API (`java.lang.classfile.*`, standardized in JDK 24) steps in for any class file version ASM can't handle; on older agent JDKs, such classes are simply skipped rather than crashing the target. The two backends are not equivalent in capability — the ClassFile API backend currently only supports `Kind.ENTRY`/`Kind.RETURN` probes and a narrower set of handler parameters — so this is a forward-compatibility safety net, not a full replacement. See [Instrumentation Backends](architecture/InstrumentationBackends.md) for the complete picture, including the backend-selection logic and current limitations.
