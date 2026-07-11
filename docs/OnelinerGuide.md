# BTrace Oneliner Guide

DTrace-style oneliners for quick Java debugging without writing full scripts.

## Overview

BTrace oneliners provide a fast, concise way to debug running Java applications without creating separate script files. Inspired by DTrace oneliners, they compile to standard BTrace Java code internally, ensuring zero performance overhead.

**When to use oneliners:**
- Quick debugging in production
- Ad-hoc performance investigation
- Learning BTrace basics
- Prototyping before writing full scripts

**When to use full BTrace scripts:**
- Complex logic with multiple probe points
- State management across probes
- Custom data structures
- Reusable instrumentation

## Quick Start

```bash
# Basic syntax
btrace -n 'class::method @location { action }' <PID>

# Real examples
btrace -n 'javax.swing.*::setText @entry { print method, args }' 1234
btrace -n 'java.sql.Statement::execute* @return if duration>100ms { print method, duration }' 1234
btrace -n 'java.util.HashMap::get @entry { count }' 1234
```

## Syntax Reference

### Basic Structure

```
class-pattern::method-pattern @location [filter] { action [, action]* }
```

**Components:**
- `class-pattern` - Class name with wildcards or regex
- `method-pattern` - Method name with wildcards or regex
- `@location` - Where to probe: `@entry`, `@return`, or `@error`
- `filter` (optional) - Conditional filter
- `action` - What to do: `print`, `count`, `time`, or `stack`

### Class and Method Patterns

**Wildcards** (simple pattern matching):
```bash
# Match all classes in package
'javax.swing.*::*'

# Match all subpackages
'javax.swing.**::*'

# Match method names
'MyClass::get*'
'MyClass::*Service'
```

**Regex** (advanced pattern matching):
```bash
# Regex in class name
'/com\.myapp\..*/::execute'

# Regex in method name
'java.sql.Statement::/execute.*/'

# Both
'/com\.myapp\..*/::/handle.*/
```

**Special method names:**
- `<init>` - Constructors
- `<clinit>` - Static initializers
- `*` - All methods

### Locations

| Location | Description | Available Identifiers |
|----------|-------------|----------------------|
| `@entry` | Method entry | method, args, self, class |
| `@return` | Method return | method, args, duration, return, self, class |
| `@error` | Exception thrown | method, args, duration, self, class |

**Examples:**
```bash
# Trace method entry
btrace -n 'MyClass::myMethod @entry { print method }' <PID>

# Trace method return
btrace -n 'MyClass::myMethod @return { print method, return }' <PID>

# Trace exceptions
btrace -n 'MyClass::myMethod @error { print method, stack }' <PID>
```

### Filters

**Duration filter** (only for `@return` and `@error`):
```bash
# Slow methods > 100ms
'MyClass::* @return if duration>100ms { print method, duration }'

# Very slow methods >= 500ms
'MyClass::* @return if duration>=500ms { print method }'

# Fast methods < 10ms
'MyClass::* @return if duration<10ms { print method }'
```

**Argument filter** (all locations):
```bash
# String argument equals
'MyClass::process @entry if args[0]=="CREATE" { print }'

# Numeric argument comparison
'MyClass::setValue @entry if args[0]>100 { print args }'

# Check null
'MyClass::process @entry if args[1]==null { print method }'
```

**Supported comparators:**
- `>` - Greater than
- `<` - Less than
- `==` - Equals
- `>=` - Greater than or equal
- `<=` - Less than or equal
- `!=` - Not equals

### Actions

#### print - Display Values

```bash
# Print bare message
{ print }

# Print specific identifiers
{ print method }
{ print method, args }
{ print method, duration, return }

# Available identifiers:
#   method   - Method name
#   args     - Method arguments
#   duration - Execution time (nanoseconds, @return/@error only)
#   return   - Return value (@return only)
#   self     - This instance
#   class    - Class name
```

**Examples:**
```bash
# Method with arguments
btrace -n 'MyClass::process @entry { print method, args }' <PID>

# Slow method with timing
btrace -n 'MyClass::query @return if duration>100ms { print method, duration }' <PID>

# Return value
btrace -n 'MyClass::calculate @return { print method, return }' <PID>
```

#### count - Count Invocations

```bash
# Simple count
{ count }
```

Prints total count when BTrace exits (Ctrl+C).

**Examples:**
```bash
# Count HashMap.get calls
btrace -n 'java.util.HashMap::get @entry { count }' <PID>

# Count exceptions
btrace -n 'java.lang.Exception::<init> @entry { count }' <PID>
```

#### time - Show Execution Time

```bash
# Display execution time in milliseconds
{ time }
```

Only valid for `@return` and `@error` locations.

**Examples:**
```bash
# Time database queries
btrace -n 'java.sql.Statement::execute* @return { time }' <PID>

# Time method execution
btrace -n 'MyClass::expensiveOperation @return { time }' <PID>
```

#### stack - Print Stack Trace

```bash
# Full stack trace
{ stack }

# Limited depth
{ stack(10) }
```

**Examples:**
```bash
# Stack on OutOfMemoryError
btrace -n 'java.lang.OutOfMemoryError::<init> @return { stack(10) }' <PID>

# Call path to method
btrace -n 'MyClass::suspiciousMethod @entry { stack(5) }' <PID>
```

#### Multiple Actions

Separate actions with commas:

```bash
# Print and count
{ print method, count }

# Print, count, and stack
{ print method, count, stack(3) }
```

## Common Use Cases

### Performance Debugging

**Find slow database queries:**
```bash
btrace -n 'java.sql.Statement::execute* @return if duration>100ms { print method, duration }' <PID>
```

**Find slow HTTP requests:**
```bash
btrace -n 'javax.servlet.http.HttpServlet::service @return if duration>500ms { print method, duration }' <PID>
```

**Time all methods in a class:**
```bash
btrace -n 'com.myapp.MyService::* @return { time }' <PID>
```

### Monitoring Method Calls

**Trace all Swing UI updates:**
```bash
btrace -n 'javax.swing.*::* @entry { print method, args }' <PID>
```

**Monitor file operations:**
```bash
btrace -n 'java.io.FileInputStream::<init> @entry { print args }' <PID>
```

**Count cache hits:**
```bash
btrace -n 'com.myapp.Cache::get @entry { count }' <PID>
```

### Exception Tracking

**Track all exceptions:**
```bash
btrace -n 'java.lang.Exception::<init> @entry { print self, stack(5) }' <PID>
```

**Track specific exception:**
```bash
btrace -n 'java.sql.SQLException::<init> @entry { print self, stack(10) }' <PID>
```

**Monitor OutOfMemoryError:**
```bash
btrace -n 'java.lang.OutOfMemoryError::<init> @return { stack(15) }' <PID>
```

### Data Flow Tracking

**Track user objects:**
```bash
btrace -n 'com.myapp.User::<init> @entry { print args, count }' <PID>
```

**Monitor configuration changes:**
```bash
btrace -n 'com.myapp.Config::set* @entry { print method, args }' <PID>
```

**Track specific argument values:**
```bash
btrace -n 'com.myapp.OrderService::process @entry if args[0]=="PRIORITY" { print method, args, stack }' <PID>
```

### Production Debugging

**Find who's calling deprecated methods:**
```bash
btrace -n 'com.myapp.LegacyService::oldMethod @entry { print stack(3) }' <PID>
```

**Identify slow REST endpoints:**
```bash
btrace -n 'org.springframework.web.bind.annotation.RequestMapping::* @return if duration>1000ms { print method, duration }' <PID>
```

**Monitor database connection usage:**
```bash
btrace -n 'javax.sql.DataSource::getConnection @entry { count }' <PID>
```

## Advanced Examples

### Complex Patterns

**Match multiple packages:**
```bash
btrace -n '/com\.myapp\.(service|controller)\..*/::* @entry { print method }' <PID>
```

**Match getter/setter methods:**
```bash
btrace -n 'com.myapp.User::/[gs]et.*/ @entry { print method, args }' <PID>
```

### Combining Features

**Slow methods with stack traces:**
```bash
btrace -n 'com.myapp.*::* @return if duration>200ms { print method, duration, stack(5) }' <PID>
```

**Count specific argument values:**
```bash
btrace -n 'com.myapp.OrderService::processOrder @entry if args[0]=="EXPRESS" { count }' <PID>
```

**Filter by return value (manual workaround - not directly supported yet):**
```bash
# Use duration filter as proxy for successful operations
btrace -n 'com.myapp.Service::operation @return if duration>0ms { print return }' <PID>
```

## Limitations

Current limitations of Alternative 1 (Minimal) oneliners:

1. **Single probe point** - Cannot have multiple probes in one oneliner
2. **No aggregations** - Cannot use histograms, averages, etc.
3. **No CALL location** - Cannot intercept method calls within methods
4. **No field access** - Cannot track field reads/writes
5. **Simple filters only** - No AND/OR logic in filters
6. **No state** - Cannot maintain variables across invocations

**Future enhancements (Alternative 2):**
- Multi-probe support: `probe1 | probe2 | probe3`
- Aggregations: `@hist=histogram; ... { @hist << duration by method }`
- CALL location: `@call:TargetClass::targetMethod`
- Grouping: `by method, class`

## Tips and Best Practices

### Performance

1. **Use specific patterns** instead of wildcards when possible:
   ```bash
   # Good - specific
   'com.myapp.UserService::getUserById'

   # Less optimal - very broad
   '**::*'
   ```

2. **Add filters** to reduce overhead:
   ```bash
   # Only trace slow calls
   @return if duration>100ms
   ```

3. **Limit stack depth**:
   ```bash
   # Good
   { stack(5) }

   # Expensive
   { stack }  # full stack
   ```

### Debugging Techniques

1. **Start broad, then narrow:**
   ```bash
   # Step 1: Find which class
   btrace -n 'com.myapp.*::* @entry { print class, method }' <PID>

   # Step 2: Focus on specific class
   btrace -n 'com.myapp.UserService::* @entry { print method, args }' <PID>

   # Step 3: Drill into specific method
   btrace -n 'com.myapp.UserService::getUserById @entry { print args, stack }' <PID>
   ```

2. **Use count to quantify issues:**
   ```bash
   # How often is this called?
   btrace -n 'com.myapp.Database::query @entry { count }' <PID>
   ```

3. **Combine with external tools:**
   ```bash
   # Save output to file
   btrace -n 'com.myapp.*::* @entry { print method }' <PID> > trace.log

   # Pipe to grep
   btrace -n 'com.myapp.*::* @entry { print method }' <PID> | grep "User"
   ```

### Security

1. **Oneliners run in untrusted mode by default** - Use `-u` flag for trusted operations

2. **Be careful with production** - Test oneliners in staging first

3. **Use read-only actions** - `print`, `count`, `stack` are safe; avoid modifying state

## Troubleshooting

### No Output

**Problem:** Oneliner runs but produces no output

**Solutions:**
1. Verify the method is being called:
   ```bash
   # Add entry point
   btrace -n 'MyClass::* @entry { print method }' <PID>
   ```

2. Check pattern matching:
   ```bash
   # Try exact match first
   btrace -n 'com.example.MyClass::myMethod @entry { print }' <PID>
   ```

3. Verify location:
   ```bash
   # Try all locations
   btrace -n 'MyClass::myMethod @entry { print }' <PID>
   btrace -n 'MyClass::myMethod @return { print }' <PID>
   ```

### Syntax Errors

**Problem:** "Oneliner syntax error at position X"

**Solutions:**
1. Check quotes - use single quotes for shell:
   ```bash
   # Correct
   btrace -n 'MyClass::method @entry { print }' <PID>

   # Wrong - shell interprets $
   btrace -n "MyClass::method @entry { print }" <PID>
   ```

2. Check spacing around symbols:
   ```bash
   # Correct
   if duration>100ms

   # Wrong
   ifduration>100ms
   ```

3. Verify filter location:
   ```bash
   # Error - duration only for @return/@error
   '@entry if duration>100ms'

   # Correct
   '@return if duration>100ms'
   ```

### Compilation Errors

**Problem:** "Oneliner compilation failed"

**Solutions:**
1. Enable debug mode to see generated code:
   ```bash
   btrace -v -n 'MyClass::method @entry { print }' <PID>
   ```

2. Check for unsupported features:
   - Duration filter requires `@return` or `@error`
   - Return identifier requires `@return`

3. Try equivalent full BTrace script to isolate issue

## Converting Oneliners to Full Scripts

When you outgrow oneliners, convert them to full BTrace scripts:

**Oneliner:**
```bash
btrace -n 'MyClass::process @return if duration>100ms { print method, duration }' <PID>
```

**Equivalent BTrace script (MyTrace.java):**
```java
import io.btrace.core.annotations.*;
import static io.btrace.core.BTraceUtils.*;

@BTrace
public class MyTrace {
    @OnMethod(clazz="MyClass", method="process", location=@Location(Kind.RETURN))
    public static void onProcess(@ProbeMethodName String method, @Duration long duration) {
        if (duration > 100_000_000L) {  // 100ms in nanoseconds
            println(method + " " + duration);
        }
    }
}
```

## See Also

- [Getting Started Guide](GettingStarted.md) - Installation and basics
- [BTrace Tutorial](BTraceTutorial.md) - Comprehensive BTrace features
- [Quick Reference](QuickReference.md) - Full annotation reference
- [FAQ](FAQ.md) - Common questions
- [Troubleshooting Guide](Troubleshooting.md) - Problem solving

## Feedback

Found an issue or have a suggestion for oneliners? Please:
- Report on [GitHub Issues](https://github.com/btraceio/btrace/issues)
- Tag with `oneliner` label
- Include example oneliner and expected behavior
