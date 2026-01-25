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
services=org.openjdk.btrace.metrics.MetricsService
requires.extensions=btrace-core
shaded.packages=org.HdrHistogram->org.openjdk.btrace.metrics.shaded.hdrhistogram
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
BTrace-Extension-Services: org.openjdk.btrace.metrics.MetricsService
BTrace-Extension-Requires: btrace-core
BTrace-Shaded-Packages: org.HdrHistogram->org.openjdk.btrace.metrics.sh
 aded.hdrhistogram,com.clearspring.analytics->org.openjdk.btrace.metrics
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
- Example: `org.openjdk.btrace.metrics.MetricsService,org.openjdk.btrace.metrics.StatsService`
- Description: Service classes provided by this extension

**BTrace-Extension-Requires**
- Format: comma-separated extension IDs
- Example: `btrace-core,btrace-util`
- Description: Other extensions required by this extension

**BTrace-Shaded-Packages**
- Format: comma-separated package mappings (original->shaded)
- Example: `org.HdrHistogram->org.openjdk.btrace.metrics.shaded.hdrhistogram`
- Description: Package relocation mappings for shaded dependencies

## MANIFEST.MF Line Continuation

Per JAR specification, manifest attributes longer than 72 bytes must be continued on the next line with a leading space:

```
BTrace-Extension-Description: This is a very long description that excee
 ds the 72-byte limit and must be continued on the next line with a lead
 ing space character.
```

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
      'BTrace-Extension-Services': 'org.openjdk.btrace.metrics.MetricsService',
      'BTrace-Shaded-Packages': 'org.HdrHistogram->org.openjdk.btrace.metrics.shaded.hdrhistogram'
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
