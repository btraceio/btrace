# BTrace Oneliner Language - Feasibility Study

## Executive Summary

Creating a DTrace-style oneliner language for BTrace is **highly feasible** and would leverage existing infrastructure. All three alternatives compile to standard BTrace Java internally, reusing the existing compiler and instrumentation engine.

**Recommendation**: Start with **Alternative 2 (Moderate)** - it provides the best balance of simplicity and capability for production debugging while remaining approachable for learning.

---

## Implementation Status

### ✅ Phase 1: Alternative 1 (Minimal) - COMPLETED

**Completed**: January 2026

**Implementation Summary:**
- **Core Components** (~1200 LoC):
  - `OnelinerLexer.java` (150 LoC) - Tokenizer with regex and string support
  - `OnelinerParser.java` (300 LoC) - Recursive descent parser
  - `OnelinerAST.java` (200 LoC) - AST node definitions
  - `OnelinerValidator.java` (150 LoC) - Semantic validation
  - `OnelinerCodeGenerator.java` (350 LoC) - Java source generation
  - `OnelinerException.java` (50 LoC) - Error handling with position tracking

- **Test Suite** (58 tests):
  - 26 parser unit tests
  - 22 code generator unit tests
  - 10 integration tests (end-to-end compilation)

- **CLI Integration**:
  - Added `-n`/`--oneliner` flag to Main.java:256
  - Oneliner compilation flow in Main.java:331
  - Updated messages.properties with usage documentation

- **Documentation** (~1200 lines):
  - `docs/OnelinerGuide.md` (600+ lines) - Comprehensive guide
  - Updated `Readme.md` with oneliner examples
  - Updated `docs/Readme.md` documentation hub
  - Updated `docs/GettingStarted.md` with 2-minute quick start

**Capabilities Delivered:**
- ✅ Probe points: `@entry`, `@return`, `@error`
- ✅ Actions: `print`, `count`, `time`, `stack`
- ✅ Filters: `if duration>NUMBERms`, `if args[N]==VALUE`
- ✅ Patterns: Wildcards (`*`, `?`) and regex (`/pattern/`)
- ✅ Class patterns with package wildcards (`javax.swing.*`, `javax.swing.**`)
- ✅ Method patterns with wildcards and regex
- ✅ Special method names: `<init>`, `<clinit>`
- ✅ Multiple actions per probe
- ✅ Stack traces with configurable depth

**Examples Working:**
```bash
# Trace Swing UI updates
btrace -n 'javax.swing.*::setText @entry { print method, args }' 1234

# Count HashMap operations
btrace -n 'java.util.HashMap::get @entry { count }' 1234

# Find slow database queries
btrace -n 'java.sql.Statement::execute* @return if duration>100ms { print method, duration }' 1234

# Track OutOfMemoryError with stack
btrace -n 'java.lang.OutOfMemoryError::<init> @return { stack(10) }' 1234
```

**Pull Request**: #788 (Draft, labeled with AI)

**Branch**: `btrace-feature-oneliner`

**Code Quality:**
- ✅ Google Java Format applied (spotlessApply)
- ✅ No compiler warnings
- ✅ SLF4J logging for debug mode
- ✅ Clear error messages with position tracking
- ✅ Comprehensive test coverage

### 🔜 Phase 2: Alternative 2 (Moderate) - PLANNED

**Timeline**: 4-6 weeks after Phase 1 merge

**Planned Features:**
- Aggregations: `@hist=histogram`, `@avg=avg`, `@min=min`, `@max=max`, `@sum=sum`
- Multi-probe support: `probe1 | probe2 | probe3`
- CALL location: `@call:Target::method`
- Enhanced filters with AND/OR logic
- Grouping with multiple keys: `by method, class`
- Aggregation actions: `@hist << duration by method`

**Estimated Scope:**
- Parser enhancements: ~300 LoC
- Code generator updates: ~200 LoC
- Additional tests: ~30 tests
- Documentation updates: ~200 lines

### 📋 Phase 3: Alternative 3 (Comprehensive) - DEFERRED

Future consideration based on user feedback and demand for advanced features.

---

## Key Requirements

- **Execution**: Both CLI (`btrace -n "oneliner"`) and file-based with simplified syntax
- **Implementation**: Compile to Java internally (leverage existing compiler/instrumentation)
- **Priority use cases**: (1) ops/SRE quick debugging, (2) educational, (3) rapid prototyping
- **Philosophy**: Keep it minimal - focus on 80% use cases

---

## Alternative 1: MINIMAL - "Quick Debug Mode"

### Philosophy
Absolute simplest syntax for common tracing. Single probe point, single action. DTrace-inspired but more readable.

### Syntax
```
class-pattern::method-pattern @location [filter] { action }
```

### Capabilities
- **Probe points**: `entry`, `return`, `error`
- **Actions**: `print`, `count`, `time`, `stack`
- **Filters**: `if duration>NUMBERms`, `if args[N]==VALUE`

### Examples
```bash
# Trace all Swing method entries
btrace -n 'javax.swing.*::* @entry { print }'

# Count HashMap.get calls
btrace -n 'java.util.HashMap::get @entry { count }'

# Time slow JDBC queries (>100ms)
btrace -n 'java.sql.Statement::execute* @return if duration>100ms { print method,duration }'

# Print stack on OutOfMemoryError
btrace -n 'java.lang.OutOfMemoryError::<init> @return { print stack }'

# Trace file opens with arguments
btrace -n 'java.io.FileInputStream::<init> @entry { print args }'
```

### Code Generation Example
Input:
```
javax.swing.JButton::setText @entry { print method,args }
```

Generated Java:
```java
@BTrace
class BTraceOneliner {
    @OnMethod(clazz="javax.swing.JButton", method="setText")
    static void probe(@ProbeMethodName String method, AnyType[] args) {
        println(method + " " + str(args));
    }
}
```

### Complexity
- **Parser**: ~200 LoC (simple regex-based)
- **Learning curve**: 5 minutes
- **Coverage**: 60% of use cases

### Limitations
- Single probe point per invocation
- No state/aggregations
- No method call interception (CALL)
- No field access tracking

---

## Alternative 2: MODERATE - "Production Debug Mode" ⭐ RECOMMENDED

### Philosophy
Balance between simplicity and power. Supports aggregations, multiple probes, basic predicates. Ideal for production debugging and learning.

### Syntax
```
[@counter-defs;]* probe-spec [filter] { action-list } [| probe-spec {...}]*
```

### Capabilities
- **Probe points**: `entry`, `return`, `error`, `call:Target::method`
- **Actions**: `print`, `count`, aggregations (`histogram`, `avg`, `min`, `max`), `time`, `stack`
- **Filters**: Duration, arguments, return values with comparison operators
- **State**: Named counters and aggregations
- **Multi-probe**: Via `|` separator

### Examples
```bash
# Count method calls aggregated by method name
btrace -n 'javax.swing.*::* @entry { count @calls by method }'

# Histogram of JDBC query durations
btrace -n '@hist=histogram; java.sql.Statement::execute* @return { @hist << duration by method }'

# Track slow operations with details
btrace -n 'com.myapp.*::* @return if duration>50ms { print method,duration,args; stack }'

# Multiple probes: track object creation and method calls
btrace -n 'com.myapp.User::<init> @entry { count @created } |
           com.myapp.User::* @entry { count @calls }'

# Intercept method calls within a method
btrace -n 'com.myapp.Controller::handleRequest @call:java.sql.Connection::prepareStatement { print args }'

# Average response time by endpoint
btrace -n '@avg=avg; com.myapp.Controller::handle* @return { @avg << duration by method }'

# Count file operations by filename
btrace -n 'java.io.FileInputStream::<init> @entry { count @files by arg[0] }'
```

### Code Generation Example
Input:
```
@hist=histogram; java.sql.Statement::execute* @return { @hist << duration by method }
```

Generated Java:
```java
@BTrace
class BTraceOneliner {
    private static Aggregation hist =
        Aggregations.newAggregation(AggregationFunction.QUANTIZE);

    @OnMethod(clazz="java.sql.Statement", method="/execute.*/",
              location=@Location(Kind.RETURN))
    static void probe(@ProbeMethodName String method, @Duration long duration) {
        AggregationKey key = Aggregations.newAggregationKey(method);
        Aggregations.addToAggregation(hist, key, (int)(duration/1000));
    }

    @OnEvent
    static void onEvent() {
        Aggregations.printAggregation("hist", hist);
    }
}
```

### Complexity
- **Parser**: ~500 LoC (recursive descent)
- **Learning curve**: 30 minutes
- **Coverage**: 85% of use cases

### Why Recommended
- Covers production debugging needs (aggregations are critical)
- Still simple enough for quick learning
- Supports multiple probe points for complex scenarios
- Moderate implementation cost (~4-6 weeks after Alternative 1)

---

## Alternative 3: COMPREHENSIVE - "Script Mode"

### Philosophy
Full expressiveness while maintaining oneliner compactness. Supports all probe points, stateful tracking, custom variables. Closer to full BTrace.

### Capabilities
- **Probe points**: All 11 BTrace kinds (entry, return, error, call, new, throw, catch, get, set, line, sync_entry, sync_exit)
- **Actions**: Full BTraceUtils suite
- **State**: Maps, counters, profilers, custom variables
- **Predicates**: Complex expressions with logical operators
- **Event handlers**: `onevent` for reporting

### Examples
```bash
# Full profiler with percentiles
btrace -n 'var prof=profiler;
javax.swing.*::* @entry { prof.enter(method) } |
javax.swing.*::* @return { prof.exit(method, duration) };
onevent { print prof }'

# Track JDBC queries with context
btrace -n 'var queries=map[String,Number]; var count=counter;
+java.sql.Statement::execute* @entry { queries[arg[0]]=timestamp } |
+java.sql.Statement::execute* @return if duration>100ms {
    print "Slow query:", arg[0], "duration:", duration;
    stack(5); count++
};
onevent { print "Total slow:", count; print queries }'

# Memory leak detector
btrace -n 'var created=counter; var finalized=counter;
java.util.HashMap::<init> @entry { created++ } |
java.util.HashMap::finalize @entry { finalized++ };
onevent { print "Created:", created, "Finalized:", finalized }'

# Field modification tracking
btrace -n 'com.myapp.User::name @set { print "Field set to:", arg[0], "by:", method }'

# Synchronization contention tracking
btrace -n 'var locks=histogram; * ::* @sync_entry { locks << 1 by class }'
```

### Complexity
- **Parser**: ~1500 LoC (full recursive descent with type checking)
- **Learning curve**: 2-3 hours
- **Coverage**: 98% of use cases

### Use Cases
- Complex profiling scenarios
- Memory leak investigation
- Advanced performance analysis
- Power users who need full BTrace capabilities

---

## Comparative Analysis

| Feature | Alternative 1 | Alternative 2 ⭐ | Alternative 3 |
|---------|---------------|---------------|---------------|
| **Probe Points** | 3 | 4 | 11 |
| **Actions** | 5 basic | 8 + aggregations | 15+ |
| **State** | None | Counters only | Maps, profilers |
| **Predicates** | Basic filters | Duration + args | Full expressions |
| **Multi-probe** | No | Yes | Yes |
| **Aggregations** | No | 4 types | All types |
| **Parser LoC** | ~200 | ~500 | ~1500 |
| **Learn time** | 5 min | 30 min | 2-3 hours |
| **Coverage** | 60% | 85% | 98% |
| **Implementation** | 2-3 weeks | +4-6 weeks | +8-12 weeks |

### Same Task Comparison
Task: "Track JDBC query durations over 100ms"

**Alternative 1:**
```
java.sql.Statement::execute* @return if duration>100ms { print method,duration }
```

**Alternative 2:**
```
@slow=histogram; java.sql.Statement::execute* @return if duration>100ms {
    @slow << duration by method
}
```

**Alternative 3:**
```
var queries=histogram; var stacks=map[String,String];
java.sql.Statement::execute* @return if duration>100ms {
    queries << duration by method;
    stacks[method]=jstackStr(5)
};
onevent { print queries; print stacks }
```

---

## Implementation Strategy

### Phased Approach (Recommended)

**Phase 1: Alternative 1** (2-3 weeks)
- Validate concept with users
- Establish CLI pattern (`btrace -n "oneliner"`)
- Simple parser and code generator
- Get feedback on syntax choices

**Phase 2: Alternative 2** (4-6 weeks)
- Add aggregations (critical for production use)
- Add multi-probe support via `|`
- Add CALL location for method interception
- Enhanced predicates

**Phase 3: Alternative 3** (Optional, 8-12 weeks)
- Add all remaining probe points
- Add stateful variables (maps, profilers)
- Add event handlers
- Complex predicate expressions

### Technical Approach

1. **CLI Integration** - Add `-n` flag to `btrace-client/src/main/java/org/openjdk/btrace/client/Main.java`
2. **Parser** - New `btrace-compiler/src/main/java/org/openjdk/btrace/compiler/oneliner/OnelinerParser.java`
3. **Code Generator** - Generate standard BTrace Java source
4. **Delegation** - Use existing `Compiler` class for compilation
5. **Runtime** - Zero changes needed, use existing `BTraceUtils`

### File Structure
```
btrace-compiler/src/main/java/org/openjdk/btrace/compiler/oneliner/
├── OnelinerParser.java         # Parser (complexity varies by alternative)
├── OnelinerAST.java            # AST nodes
├── OnelinerCodeGen.java        # Java code generator
├── OnelinerValidator.java      # Semantic validation
└── OnelinerException.java      # Error handling
```

### Critical Files
- `btrace-client/src/main/java/org/openjdk/btrace/client/Main.java` - CLI flag parsing
- `btrace-compiler/src/main/java/org/openjdk/btrace/compiler/Compiler.java` - Compilation hook
- `btrace-core/src/main/java/org/openjdk/btrace/core/BTraceUtils.java` - Action reference
- `btrace-core/src/main/java/org/openjdk/btrace/core/annotations/Kind.java` - Probe point mapping

---

## References

### DTrace Oneliner Resources
- [Brendan Gregg's DTrace Oneliners](https://www.brendangregg.com/DTrace/dtrace_oneliners.txt) - Classic reference
- [Oracle Linux DTrace Guide](https://docs.oracle.com/cd/F61410_01/dtrace-guide/OL-DTRACE-GUIDE.pdf) - Updated January 2025
- [DTrace Scripts GitHub](https://github.com/cadets/dtrace-scripts/blob/master/one_liners.md) - Community examples

### DTrace Syntax Patterns
- Probe specification: `provider:module:function:name`
- Actions: `{ trace(), printf(), count(), sum() }`
- Aggregations: `@name[key] = function()`
- Predicates: `/condition/`

---

## Feasibility Assessment

### Advantages
✅ Reuses existing BTrace infrastructure (compiler, runtime, instrumentation)
✅ No performance overhead vs. hand-written scripts
✅ Incremental implementation path
✅ Addresses real pain point (writing full Java classes is heavy)
✅ Educational value (learn BTrace concepts via simple syntax)

### Challenges
⚠️ Syntax design requires careful user testing
⚠️ Error messages must be clear (syntax errors in oneliners can be cryptic)
⚠️ Documentation and examples critical for adoption
⚠️ Need to handle edge cases gracefully

### Risks (Low)
- Parser maintenance overhead (mitigated by keeping grammar simple)
- User confusion about limitations vs. full BTrace (mitigated by clear docs)
- Code generation bugs (mitigated by comprehensive tests)

---

## Conclusion

**All three alternatives are feasible.** Alternative 2 provides the optimal balance for production use while remaining accessible to newcomers. A phased implementation starting with Alternative 1 allows validation of the approach before investing in more complex features.

**Next Steps:**
1. User feedback on syntax preferences (DTrace-like vs. more verbose?)
2. Prototype Alternative 1 parser (~200 LoC)
3. Generate 5-10 test cases and validate generated code
4. Iterate on syntax based on readability
5. Implement Alternative 2 features incrementally
