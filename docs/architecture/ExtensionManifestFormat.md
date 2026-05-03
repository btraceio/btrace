# BTrace Extension MANIFEST.MF Format

## Overview

This document defines the MANIFEST.MF attribute format for BTrace extensions, replacing the previous `btrace-extension.properties` file.

## Rationale

Using MANIFEST.MF for extension metadata provides:
- **Standard Java mechanism** - Already parsed by JVM and tools
- **OSGi precedent** - Follows established patterns from OSGi bundles
- **Single source of truth** - Build tools already generate MANIFEST.MF
- **Less maintenance** - No separate properties file to manage

## Attribute Mapping

### From Properties Format

```properties
extension.id=btrace-metrics
extension.version=2.3.0
extension.name=BTrace Metrics
extension.description=High-performance metrics...
btrace.api.version=2.3+
java.version=8+
services=io.btrace.metrics.MetricsService
requires.extensions=btrace-core
shaded.packages=org.HdrHistogram->io.btrace.metrics.shaded.hdrhistogram
```

### To MANIFEST.MF Format

```
BTrace-Extension-Id: btrace-metrics
BTrace-Extension-Version: 2.3.0
BTrace-Extension-Name: BTrace Metrics
BTrace-Extension-Description: High-performance metrics with HdrHistogram
 for percentiles and lock-free statistics
BTrace-API-Version: 2.3+
BTrace-Java-Version: 8+
BTrace-Extension-Services: io.btrace.metrics.MetricsService
BTrace-Extension-Requires: btrace-core
BTrace-Shaded-Packages: org.HdrHistogram->io.btrace.metrics.sh
 aded.hdrhistogram,com.clearspring.analytics->io.btrace.metrics
 .shaded.clearspring
BTrace-Extension-Permissions: NETWORK,THREADS
```

## Attribute Definitions

### Required Attributes

**BTrace-Extension-Id**
- Format: lowercase-with-hyphens
- Example: `btrace-metrics`
- Description: Unique identifier for the extension

**BTrace-Extension-Version**
- Format: semantic version (major.minor.patch[-qualifier])
- Example: `2.3.0`, `2.3.0-SNAPSHOT`
- Description: Extension version for conflict resolution

### Optional Attributes

**BTrace-Extension-Name**
- Format: human-readable string
- Example: `BTrace Metrics`
- Description: Display name for the extension

**BTrace-Extension-Description**
- Format: multi-line text (continuation with leading space)
- Example: `High-performance metrics with HdrHistogram`
- Description: Detailed description of extension functionality

**BTrace-API-Version**
- Format: version range (major.minor+)
- Example: `2.3+` (requires BTrace API 2.3 or higher)
- Description: Required BTrace API version

**BTrace-Java-Version**
- Format: version number (8+, 11+, etc.)
- Example: `8+`
- Description: Minimum Java version required

**BTrace-Extension-Services**
- Format: comma-separated fully qualified class names
- Example: `io.btrace.metrics.MetricsService,io.btrace.metrics.StatsService`
- Description: Service classes provided by this extension

**BTrace-Extension-Requires**
- Format: comma-separated extension IDs
- Example: `btrace-core,btrace-util`
- Description: Other extensions required by this extension

**BTrace-Shaded-Packages**
- Format: comma-separated package mappings (original->shaded)
- Example: `org.HdrHistogram->io.btrace.metrics.shaded.hdrhistogram`
- Description: Package relocation mappings for shaded dependencies

## MANIFEST.MF Line Continuation

Per JAR specification, manifest attributes longer than 72 bytes must be continued on the next line with a leading space:

```
BTrace-Extension-Description: This is a very long description that excee
 ds the 72-byte limit and must be continued on the next line with a lead
 ing space character.
```

## Embedded Extension Properties (`extension.properties`)

Embedded extensions (shipped inside a fat agent JAR) are described by a
`META-INF/btrace-extensions/{id}/extension.properties` file rather than a
MANIFEST.MF. The agent reads the following keys from that file:

| Key | Required | Example | Description |
|-----|----------|---------|-------------|
| `id` | no (defaults to directory name) | `btrace-spark` | Extension identifier |
| `version` | no (defaults to `0.0.0`) | `1.2.0` | Semantic version |
| `name` | no | `BTrace Spark` | Human-readable name |
| `description` | no | `Spark job tracing` | Short description |
| `btrace.api.version` | no (defaults to `3.0+`) | `3.0.0` | Minimum BTrace API version |
| `java.version` | no (defaults to `8+`) | `11+` | Minimum Java version |
| `services` | no | `org.example.SparkService` | Comma-separated service class names |
| `probes` | no | `SparkJobTracer,SparkStageTracer` | Comma-separated bundled probe class names |
| `configurator` | no | `org.example.SparkConfigurator` | Fully qualified `ExtensionConfigurator` class for zero-config probe auto-selection (see below) |

### `configurator` — Zero-Config Probe Auto-Selection

When a `configurator` class is declared, the agent calls it during startup to
decide which bundled probes to activate based on the running JVM's environment.
This allows the extension to enable the right probes automatically (e.g. Spark
driver probes vs. executor probes) without the operator having to pass a
`probes=` agent argument.

The class must implement `io.btrace.core.extensions.ExtensionConfigurator`
and have a public no-arg constructor. It is loaded via the extension's own
classloader. See [BTraceExtensionDevelopmentGuide.md](../BTraceExtensionDevelopmentGuide.md)
for a full example.

## Backward Compatibility

The extension loader supports both formats:

1. Check for MANIFEST.MF attributes first
2. Fall back to `btrace-extension.properties` if MANIFEST attributes not found
3. Log deprecation warning if using properties file

## Gradle Configuration

Extensions should configure MANIFEST.MF generation in build.gradle:

```gradle
jar {
  manifest {
    attributes(
      'BTrace-Extension-Id': 'btrace-metrics',
      'BTrace-Extension-Version': project.version,
      'BTrace-Extension-Name': 'BTrace Metrics',
      'BTrace-Extension-Description': 'High-performance metrics...',
      'BTrace-API-Version': '2.3+',
      'BTrace-Java-Version': '8+',
      'BTrace-Extension-Services': 'io.btrace.metrics.MetricsService',
      'BTrace-Shaded-Packages': 'org.HdrHistogram->io.btrace.metrics.shaded.hdrhistogram'
    )
  }
}
```

## Migration Path

1. ✅ Design MANIFEST.MF format (this document)
2. Update ExtensionMetadata parser to read MANIFEST.MF
3. Update btrace-metrics build.gradle to generate MANIFEST attributes
4. Test with both formats for backward compatibility
5. Update documentation
6. Deprecate btrace-extension.properties (remove in future release)
**BTrace-Extension-Permissions**
- Format: comma-separated permission names
- Example: `NETWORK,THREADS,FILE_WRITE`
- Description: Permissions required by the extension implementation. Automatically inferred by the build plugin via code scanning, with optional overrides in Gradle.
