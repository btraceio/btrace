# BTrace Extension Storage Design

## Overview

This document describes the design for BTrace extension storage and loading mechanism. The goal is to move extensions from the boot JAR to a dedicated extension system with built-in and user-configurable locations.

## Current State

Currently, extensions (like btrace-metrics) are packaged into the boot JAR (`btrace-boot.jar`) along with core BTrace runtime classes. This approach has several limitations:

1. **No Extension Isolation**: Extensions are mixed with core runtime code
2. **No Version Management**: Can't have multiple versions of an extension
3. **No Dynamic Loading**: All extensions loaded at startup, even if unused
4. **Deployment Friction**: Adding new extensions requires rebuilding the distribution
5. **User Extensions**: No mechanism for users to add custom extensions

## Design Goals

1. **Separation**: Extensions stored separately from core runtime
2. **Discoverability**: Automatic discovery of built-in and user extensions
3. **Configuration**: User-configurable extension locations
4. **Lazy Loading**: Load extensions only when referenced in scripts
5. **Isolation**: Extension classloading isolation to avoid conflicts
6. **Metadata**: Extension metadata for versioning and dependencies

## Directory Structure

### Distribution Layout

```
BTRACE_HOME/
├── bin/
│   ├── btrace
│   └── btracec
├── libs/
│   ├── btrace-agent.jar
│   ├── btrace-boot.jar       # Core runtime only, no extensions
│   ├── btrace-client.jar
│   └── extensions/            # Built-in extensions directory
│       ├── btrace-metrics-2.3.0.jar
│       ├── btrace-statsd-2.3.0.jar
│       └── Readme.md
└── docs/
    └── ...
```

### User Extension Locations

Extensions are discovered in the following order (later locations override earlier):

1. **Built-in**: `BTRACE_HOME/extensions/`
2. **System**: `/etc/btrace/extensions/` (Unix) or `%PROGRAMDATA%\btrace\extensions\` (Windows)
3. **User**: `~/.btrace/extensions/`
4. **Environment**: `$BTRACE_EXT_PATH` (colon-separated paths)
5. **Command-line**: `--ext-path <path>` (btrace command)
6. **Script-local**: `./.btrace/extensions/` (relative to script location)

## Extension Structure

### Extension JAR Layout

```
btrace-metrics-2.3.0.jar
├── META-INF/
│   ├── MANIFEST.MF
│   ├── btrace-extension.properties      # Extension metadata
│   └── services/
│       └── org.openjdk.btrace.core.extensions.Extension
├── org/openjdk/btrace/metrics/
│   ├── MetricsService.class
│   ├── histogram/
│   └── stats/
└── ... (shaded dependencies)
```

### Extension Metadata (`btrace-extension.properties`)

```properties
# Extension identity
extension.id=btrace-metrics
extension.version=2.3.0
extension.name=BTrace Metrics
extension.description=High-performance metrics with HdrHistogram

# API compatibility
btrace.api.version=2.3+
java.version=8+

# Service providers (optional, can also use META-INF/services)
services=org.openjdk.btrace.metrics.MetricsService

# Dependencies on other extensions (optional)
requires.extensions=

# Shadowed packages (for conflict detection)
shaded.packages=org.HdrHistogram->org.openjdk.btrace.metrics.shaded.hdrhistogram,\
                com.clearspring.analytics->org.openjdk.btrace.metrics.shaded.clearspring
```

## Extension Loading Mechanism

### 1. Discovery Phase (on Agent Startup)

```java
public class ExtensionLoader {
    private final List<ExtensionRepository> repositories;

    // Scan all configured locations
    public List<ExtensionDescriptor> discoverExtensions() {
        List<ExtensionDescriptor> extensions = new ArrayList<>();

        for (ExtensionRepository repo : repositories) {
            extensions.addAll(repo.scan());
        }

        // Resolve conflicts (latest version wins)
        return resolveExtensions(extensions);
    }
}
```

### 2. Lazy Loading (on Script Compilation)

When the compiler encounters an `@Injected` service:

```java
// In Compiler.java
private void loadRequiredExtensions(List<String> serviceTypes) {
    for (String serviceType : serviceTypes) {
        ExtensionDescriptor ext = extensionLoader.findExtensionForService(serviceType);
        if (ext != null && !ext.isLoaded()) {
            extensionLoader.load(ext);
        }
    }
}
```

### 3. ClassLoader Hierarchy

```
Bootstrap ClassLoader
    |
System ClassLoader
    |
BTrace Boot ClassLoader (btrace-boot.jar)
    |
    +-- Extension ClassLoader 1 (btrace-metrics)
    |
    +-- Extension ClassLoader 2 (btrace-statsd)
    |
    +-- ...
    |
BTrace Script ClassLoader (compiled script)
```

Each extension gets its own classloader for isolation, but they can see:
- Bootstrap classes
- BTrace core API classes
- Their own classes and shaded dependencies

## Configuration

### Agent Configuration (`btrace.conf`)

```properties
# Extension directories (colon-separated on Unix, semicolon on Windows)
extension.path=${BTRACE_HOME}/extensions:${HOME}/.btrace/extensions

# Extension loading behavior
extension.lazy-load=true
extension.fail-on-missing=false
extension.conflict-resolution=latest-version

# Extension-specific settings
extension.btrace-metrics.enabled=true
extension.btrace-statsd.enabled=true
```

### Environment Variables

```bash
# Override extension path
export BTRACE_EXT_PATH="/opt/btrace-extensions:/usr/local/btrace/ext"

# Disable lazy loading (load all extensions at startup)
export BTRACE_EXT_LAZY_LOAD=false
```

### Command-line Options

```bash
# Specify additional extension directories
btrace --ext-path /custom/extensions PID script.java

# Disable built-in extensions
btrace --no-builtin-ext PID script.java

# Enable specific extensions only
btrace --ext btrace-metrics,btrace-statsd PID script.java
```

## Extension API

### Creating an Extension

1. **Implement Service Interface**:
```java
package com.example.myext;

import org.openjdk.btrace.core.extensions.Extension;

public class MyExtensionService extends Extension {
    @Override
    public void onStart(BTraceRuntime runtime) {
        // Initialize extension
    }
}
```

2. **Register Service**:
No SPI file required. The Gradle plugin writes manifest attributes (BTrace-Extension-Services).
```
com.example.myext.MyExtensionService
```

3. **Add Metadata**:
Create `META-INF/btrace-extension.properties`:
```properties
extension.id=my-extension
extension.version=1.0.0
btrace.api.version=2.3+
```

4. **Build JAR**:
```gradle
shadowJar {
    // Shade dependencies to avoid conflicts
    relocate 'com.external.lib', 'com.example.myext.shaded.lib'
}
```

5. **Deploy**:
```bash
cp my-extension-1.0.0.jar $BTRACE_HOME/extensions/
# or
cp my-extension-1.0.0.jar ~/.btrace/ext/
```

## Extension Repositories

### Local File System Repository

```java
public class FileSystemExtensionRepository implements ExtensionRepository {
    private final Path extensionDir;

    @Override
    public List<ExtensionDescriptor> scan() {
        List<ExtensionDescriptor> extensions = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(extensionDir, "*.jar")) {
            for (Path jar : stream) {
                ExtensionDescriptor desc = parseExtension(jar);
                if (desc != null) {
                    extensions.add(desc);
                }
            }
        }

        return extensions;
    }
}
```

### Future: Remote Repository (Maven-like)

```properties
# In btrace.conf
extension.repository.url=https://extensions.btrace.io/repository
extension.repository.cache=${HOME}/.btrace/cache
```

## Migration Path

### Phase 1: Dual Mode (Current + New)
- Keep extensions in boot JAR for backward compatibility
- Add new extension loading mechanism
- Extensions can be discovered from both locations

### Phase 2: Deprecation
- Move built-in extensions to `extensions/` in distribution
- Boot JAR checks if extension exists in extensions/ before loading from itself
- Log deprecation warnings

### Phase 3: Removal
- Remove extensions from boot JAR
- Only load from extension directories

## Implementation Plan

### Stage 1: Extension Discovery (1-2 days)
- [ ] Create `ExtensionDescriptor` class
- [ ] Create `ExtensionRepository` interface and FileSystemRepository
- [ ] Implement extension scanning and metadata parsing
- [ ] Add extension.properties parsing

### Stage 2: Extension Loading (2-3 days)
- [ ] Create `ExtensionClassLoader` with proper parent delegation
- [ ] Implement lazy loading mechanism
- [ ] Add extension lifecycle management (load/unload)
- [ ] Handle ServiceLoader integration with extensions

### Stage 3: Configuration (1-2 days)
- [ ] Add configuration file support
- [ ] Environment variable handling
- [ ] Command-line argument parsing
- [ ] Extension path resolution

### Stage 4: Integration (2-3 days)
- [ ] Integrate with compiler (detect required services)
- [ ] Integrate with agent (load extensions)
- [ ] Update verifier to work with extension classloaders
- [ ] Update build to create ext/ directory structure

### Stage 5: Migration (1-2 days)
- [ ] Move btrace-metrics to ext/ directory
- [ ] Move btrace-statsd to ext/ directory
- [ ] Update documentation
- [ ] Update integration tests

### Stage 6: Testing & Documentation (2-3 days)
- [ ] Unit tests for extension loading
- [ ] Integration tests with multiple extensions
- [ ] Test extension conflicts and resolution
- [ ] Developer guide for creating extensions
- [ ] User guide for installing extensions

**Total Estimated Effort**: 9-15 days

## Security Considerations

1. **Extension Verification**:
   - Check JAR signatures
   - Validate extension metadata
   - Sandbox extension code (SecurityManager)

2. **Trusted Locations**:
   - Built-in extensions (BTRACE_HOME) are trusted
   - User extensions require explicit trust or signature

3. **Classloading Isolation**:
   - Extensions cannot access each other's classes
   - Extensions cannot override core BTrace classes

4. **Resource Limits**:
   - Limit memory usage per extension
   - Timeout for extension initialization

## Performance Considerations

1. **Lazy Loading**: Load extensions only when needed by scripts
2. **Caching**: Cache extension metadata to avoid repeated JAR scanning
3. **Parallel Loading**: Load independent extensions in parallel
4. **Minimal Overhead**: Discovery should add <100ms to agent startup

## Backward Compatibility

1. **Boot JAR Fallback**: If extension not found in ext/, check boot JAR
2. **Service Discovery**: Support both old and new service loading
3. **Configuration**: Old configurations continue to work
4. **Scripts**: Existing scripts work without modification

## Open Questions

1. **Extension Dependencies**: How do extensions depend on each other?
2. **Version Conflicts**: What happens when script needs different versions?
3. **Extension Updates**: Hot-reload extensions without restarting agent?
4. **Distribution**: Package extensions separately or all-in-one?
5. **Remote Loading**: Allow downloading extensions from remote repositories?

## References

- [Java ServiceLoader Documentation](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html)
- [OSGi Bundle Format](https://docs.osgi.org/specification/osgi.core/7.0.0/framework.module.html)
- [Maven Extension Mechanism](https://maven.apache.org/guides/mini/guide-using-extensions.html)
