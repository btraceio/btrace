# BTrace and GraalVM Native Image

## Overview

BTrace relies on dynamic bytecode manipulation at runtime using the JVM's
`java.lang.instrument` API and the ASM bytecode library.  GraalVM Native Image
performs ahead-of-time (AOT) compilation and does **not** support runtime class
redefinition or the Instrumentation API.  This page documents the current
limitations and available workarounds.

## What Works

| Feature | HotSpot JVM | GraalVM JIT (non-native) | GraalVM Native Image |
|---------|:-----------:|:------------------------:|:-------------------:|
| Dynamic attach (`btrace <pid>`) | Yes | Yes | No |
| Agent mode (`-javaagent`) | Yes | Yes | No |
| BTrace script compilation | Yes | Yes | Yes (*) |
| Extension loading | Yes | Yes | No |

(*) The BTrace compiler itself can run on any JVM including GraalVM JIT mode.

## Why Native Image Is Incompatible

1. **No `java.lang.instrument`** — Native Image does not include the
   Instrumentation API, so the BTrace agent cannot attach to a running process
   or be loaded as a `-javaagent`.

2. **No runtime bytecode generation** — ASM-based bytecode weaving happens at
   class-load time.  Native Image compiles all reachable code ahead of time and
   does not support defining new classes at runtime.

3. **Closed-world assumption** — Native Image requires all classes to be known
   at build time.  BTrace scripts and the classes they instrument are discovered
   dynamically.

## Workarounds

### 1. Use GraalVM in JIT Mode

GraalVM ships a standard HotSpot-based JVM alongside the native-image tool.
Running your application with `java` (not as a native image) gives you full
BTrace support, including the GraalVM JIT compiler's performance benefits:

```bash
# GraalVM JIT mode — full BTrace support
$GRAALVM_HOME/bin/java -jar myapp.jar &
btrace <pid> MyScript.bt
```

### 2. Build-Time Instrumentation (Experimental)

For scenarios where you **must** use native images, consider pre-instrumenting
your application at build time using the BTrace compiler and Gradle plugin:

```groovy
// build.gradle
plugins {
    id 'org.openjdk.btrace.gradle' version '3.0.0'
}

btrace {
    scripts = ['src/btrace/MyScript.java']
}
```

This compiles and verifies BTrace scripts at build time.  However, the actual
bytecode weaving still requires the Instrumentation API, so this approach only
validates scripts — it does not inject probes into native images.

### 3. Use OpenTelemetry for Native Image Observability

If you need observability in native images, consider using the BTrace
OpenTelemetry extension (`btrace-otel`) during development on HotSpot to
define your instrumentation points, then switch to native OpenTelemetry
instrumentation (e.g. the OpenTelemetry Java agent or manual SDK) for the
native image build.

## Future Directions

Full native-image support would require a compile-time weaving mode that:

1. Processes BTrace scripts at build time
2. Weaves instrumentation directly into application class files before AOT
   compilation
3. Bundles the BTrace runtime support classes into the native image

This is tracked as a potential future enhancement.  Contributions are welcome.

## Recommendations

- **Development/testing**: Use BTrace freely on HotSpot or GraalVM JIT mode
- **Staging**: Use BTrace with `-javaagent` for pre-production profiling
- **Production native images**: Use standard observability tools
  (OpenTelemetry, Micrometer) that support native images natively
- **Debugging native images**: Use GraalVM's built-in diagnostic tools
  (`--diagnostics-mode`, JFR support in newer GraalVM versions)
