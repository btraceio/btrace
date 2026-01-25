# BTrace Extension Configuration

## Overview

Extension configuration allows users to customize extension loading behavior, enable/disable specific extensions, and pass extension-specific settings.

## Configuration File Locations

Configuration files are searched in priority order (highest to lowest):

1. **Command-line override**: `-Dbtrace.extensions.config=/path/to/config`
2. **User config**: `~/.btrace/extensions.conf`
3. **System config**: `$BTRACE_HOME/conf/extensions.conf`

The first file found is used. Settings from multiple files are NOT merged.

## Configuration Format

Uses standard Java properties format:

```properties
# Extension Control
# Comma-separated list of extension IDs to enable (empty = all enabled)
extensions.enabled=

# Comma-separated list of extension IDs to disable
extensions.disabled=

# Auto-load extensions on demand (default: true)
extensions.autoload=true

# Extension-specific settings (passed to extension on load)
# Format: <extension-id>.<setting-name>=<value>
btrace-metrics.histogram.default-precision=3
btrace-metrics.stats.window-size=1000
```

## Configuration Properties

### Global Settings

**extensions.enabled**
- Format: comma-separated extension IDs
- Default: (empty - all enabled)
- Example: `extensions.enabled=btrace-metrics,btrace-custom`
- Description: Whitelist of extensions to load. If set, only listed extensions are loaded. If empty or not set, all discovered extensions are enabled (unless explicitly disabled).

**extensions.disabled**
- Format: comma-separated extension IDs
- Default: (empty - none disabled)
- Example: `extensions.disabled=btrace-old-metrics`
- Description: Blacklist of extensions to prevent from loading. Takes precedence over `extensions.enabled`.

**extensions.autoload**
- Format: boolean (true/false)
- Default: `true`
- Description: If `true`, extensions are loaded on demand when scripts use their services. If `false`, all enabled extensions are loaded at agent startup.

### Extension-Specific Settings

Extensions can define custom configuration properties using the pattern:
```
<extension-id>.<setting-name>=<value>
```

Examples:
```properties
# BTrace Metrics settings
btrace-metrics.histogram.default-precision=3
btrace-metrics.histogram.max-value=3600000000
btrace-metrics.stats.window-size=1000

# Custom extension settings
my-extension.feature.enabled=true
my-extension.endpoint=http://localhost:8080
```

Extension-specific settings are passed to the extension when loaded. Extensions can access these via their configuration API.

## Usage Examples

### Example 1: Disable Specific Extension

```properties
# Disable legacy metrics extension
extensions.disabled=btrace-old-metrics
```

### Example 2: Whitelist Extensions

```properties
# Only load metrics and custom extensions
extensions.enabled=btrace-metrics,my-custom-extension
```

### Example 3: Configure Extension Settings

```properties
# Configure metrics extension
btrace-metrics.histogram.default-precision=3
btrace-metrics.histogram.max-value=3600000000
```

### Example 4: Load All Extensions at Startup

```properties
# Disable lazy loading, load all extensions immediately
extensions.autoload=false
```

## API Integration

### ExtensionConfig Class

New class to manage extension configuration:

```java
public class ExtensionConfig {
  public boolean isEnabled(String extensionId);
  public boolean isAutoLoad();
  public Properties getExtensionProperties(String extensionId);
}
```

### ExtensionLoader Integration

ExtensionLoader will:
1. Load configuration on initialization
2. Filter discovered extensions based on enabled/disabled lists
3. Apply autoload setting (lazy vs eager loading)
4. Pass extension-specific properties when loading extensions

### Configuration Loading Priority

```
Command-line (-Dbtrace.extensions.config)
    ↓
User config (~/.btrace/extensions.conf)
    ↓
System config ($BTRACE_HOME/conf/extensions.conf)
    ↓
Built-in defaults (all enabled, autoload=true)
```

## Implementation Plan

1. Create ExtensionConfig class to parse and store configuration
2. Update ExtensionLoader to accept and use ExtensionConfig
3. Update Main.java to load configuration before initializing extensions
4. Add configuration file to distribution at conf/extensions.conf (empty/commented)
5. Document configuration in user guide

## Backward Compatibility

- If no configuration file exists, all extensions are enabled with autoload
- Existing deployments continue to work without any configuration
- Configuration is purely additive (opt-in customization)

## Future Enhancements

- Hot reload: Detect configuration file changes and reload extensions
- JMX integration: Expose extension configuration via JMX for runtime modification
- Validation: Warn about unknown extension IDs or malformed settings
