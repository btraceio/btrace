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
- Description: Intended to choose between on-demand loading (`true`) and loading at agent startup (`false`). In 3.0 the value is parsed but not consulted: every enabled extension is loaded when the agent discovers it, whatever this is set to.

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

Extension-specific settings are parsed and available inside the agent through `ExtensionConfig.getExtensionProperties(extensionId)` (prefix stripped). In 3.0 nothing hands them to the extension: `ExtensionContext` has no configuration accessor, so an extension implementation cannot read them yet. Use agent arguments (`ExtensionContext.getArgs()`) or system properties for extension settings.

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
# Parsed, but not delivered to the extension in 3.0 (see above)
btrace-metrics.histogram.default-precision=3
btrace-metrics.histogram.max-value=3600000000
```

### Example 4: Load All Extensions at Startup

```properties
# No effect in 3.0: enabled extensions are always loaded at discovery
extensions.autoload=false
```

## API Integration

### ExtensionConfig Class

`io.btrace.extension.impl.ExtensionConfig` manages extension configuration:

```java
public final class ExtensionConfig {
  public boolean isEnabled(String extensionId);
  public boolean isAutoLoad();
  public Properties getExtensionProperties(String extensionId);
}
```

### ExtensionLoader Integration

ExtensionLoader:
1. Loads configuration on initialization
2. Filters discovered extensions based on enabled/disabled lists

Not implemented in 3.0: applying the autoload setting (lazy vs eager loading) and passing
extension-specific properties when loading extensions.

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

## Backward Compatibility

- If no configuration file exists, all extensions are enabled with autoload
- Existing deployments continue to work without any configuration
- Configuration is purely additive (opt-in customization)

## Future Enhancements

- Hot reload: Detect configuration file changes and reload extensions
- JMX integration: Expose extension configuration via JMX for runtime modification
- Validation: Warn about unknown extension IDs or malformed settings
