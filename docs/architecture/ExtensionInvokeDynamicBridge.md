# Extension invokedynamic Bridge Architecture

## Overview

The BTrace extension system uses invokedynamic to bridge between script classloaders and extension classloaders, avoiding bootstrap classloader pollution while maintaining complete extension isolation.

**Key Benefits:**
- **Clean bootstrap namespace** - Only BTrace core classes in bootstrap, no extension JARs
- **Extension isolation** - Each extension in its own classloader with shaded dependencies
- **Security** - Extensions don't have bootstrap-level privileges
- **Java 8+ compatible** - Works on all supported Java versions (8+)

## Architecture Diagram

```
┌──────────────────────────────────────┐
│ BTrace Script                        │
│ ┌──────────────────────────────────┐ │
│ │ @Injected MetricsService metrics │ │
│ │                                  │ │
│ │ void onMethod() {                │ │
│ │   metrics.histogram("foo")  ←────┼─┼── INVOKEDYNAMIC call
│ │ }                                │ │
│ └──────────────────────────────────┘ │
│                                      │
│ Classloader: Anonymous (parent=null)│
└────────────┬─────────────────────────┘
             │ delegates to bootstrap
             ↓
┌──────────────────────────────────────┐         ┌────────────────────────────┐
│ Bootstrap ClassLoader                │         │ Extension ClassLoaders     │
│                                      │         │                            │
│ ┌─────────────────────────────────┐  │         │ ┌────────────────────────┐ │
│ │ ExtensionIndy.java              │  │◄────────┼─┤ btrace-metrics         │ │
│ │                                 │  │ bridge  │ │ - MetricsService.class │ │
│ │ public static CallSite          │  │         │ │ - HdrHistogram (shaded)│ │
│ │   bootstrapFieldGet(...) {      │  │         │ └────────────────────────┘ │
│ │   // Get extension class        │  │         │                            │
│ │   Class<?> clz =                │  │         │ ┌────────────────────────┐ │
│ │     bridge.getExtensionClass(); │  │         │ │ btrace-statsd          │ │
│ │   // Create MethodHandle        │  │         │ │ - (isolated)           │ │
│ │   return new ConstantCallSite(mh)│ │         │ └────────────────────────┘ │
│ │ }                               │  │         │                            │
│ └─────────────────────────────────┘  │         │ ┌────────────────────────┐ │
│                                      │         │ │ custom-extension       │ │
│ ┌─────────────────────────────────┐  │         │ │ - (isolated)           │ │
│ │ ExtensionBridge (interface)     │  │         │ └────────────────────────┘ │
│ │ - getExtensionClass()           │  │         │                            │
│ └─────────────────────────────────┘  │         └────────────────────────────┘
│                                      │                      ▲
│ ┌─────────────────────────────────┐  │                      │
│ │ btrace-boot.jar                 │  │                      │
│ │ - BTraceRuntime                 │  │                      │
│ │ - BTraceUtils                   │  │                      │
│ │ - Core annotations              │  │                      │
│ └─────────────────────────────────┘  │                      │
└──────────────────────────────────────┘                      │
             ▲                                                │
             │ implements                                     │
             │                                                │
┌────────────┴─────────────────────┐                          │
│ btrace-agent                     │                          │
│                                  │                          │
│ ┌──────────────────────────────┐ │                          │
│ │ ExtensionBridgeImpl          │ │                          │
│ │                              │ │                          │
│ │ static {                     │ │                          │
│ │   // Set ExtensionIndy.bridge│ │                          │
│ │   ExtensionIndy.bridge =     │ │                          │
│ │     new ExtensionBridgeImpl()│ │                          │
│ │ }                            │ │                          │
│ │                              │ │                          │
│ │ Class<?> getExtensionClass() │ │                          │
│ │   // Find extension          │ │                          │
│ │   // Load extension          │─┼──────────────────────────┘
│ │   // Return class from       │ │
│ │   // extension CL            │ │
│ └──────────────────────────────┘ │
│                                  │
│ ┌──────────────────────────────┐ │
│ │ ExtensionLoader              │ │
│ │ - discoverExtensions()       │ │
│ │ - load(ExtensionDescriptor)  │ │
│ │ - findExtensionForService()  │ │
│ └──────────────────────────────┘ │
└──────────────────────────────────┘
```

## Component Details

### 1. ExtensionIndy (btrace-runtime)

**Location:** `btrace-runtime/src/main/java/io/btrace/runtime/ExtensionIndy.java`

**Purpose:** Bootstrap methods for invokedynamic extension access

**Key Methods:**
```java
public static CallSite bootstrapFieldGet(
    MethodHandles.Lookup caller,
    String fieldName,
    MethodType type,
    String serviceClassName,
    String serviceType,
    String factoryMethod) throws Exception
```

**Responsibilities:**
- Receives invokedynamic bootstrap calls from script bytecode
- Uses ExtensionBridge to load extension service class
- Creates MethodHandle for service instantiation
- Handles both SIMPLE (no-arg) and RUNTIME (BTraceRuntime.Impl param) services
- Supports both constructors and factory methods
- Returns ConstantCallSite for zero-overhead subsequent calls

**Error Handling:**
- Gracefully degrades to null on failure
- Logs errors but doesn't throw exceptions
- Matches behavior of failed service instantiation

### 2. ExtensionBridge (btrace-core)

**Location:** `btrace-core/src/main/java/io/btrace/extension/ExtensionBridge.java`

**Purpose:** Interface for agent-to-runtime classloader bridging

**API:**
```java
public interface ExtensionBridge {
  Class<?> getExtensionClass(String serviceClassName) throws Exception;
}
```

**Why an Interface:**
- Allows btrace-runtime (bootstrap) to call btrace-agent code
- Breaks circular dependency (runtime can't depend on agent)
- Clean separation of concerns

### 3. ExtensionBridgeImpl (btrace-core)

**Location:** `btrace-core/src/main/java/io/btrace/extension/impl/ExtensionBridgeImpl.java`

**Purpose:** Agent-side implementation of ExtensionBridge

**Initialization:**
```java
static {
  try {
    Class<?> indyClz = Class.forName("io.btrace.runtime.ExtensionIndy");
    ExtensionBridge bridge = new ExtensionBridgeImpl(Main.getExtensionLoader());
    indyClz.getField("bridge").set(null, bridge);
  } catch (ClassNotFoundException e) {
    // Expected for pre-Java 8 or if ExtensionIndy unavailable
  }
}
```

**Responsibilities:**
- Sets `ExtensionIndy.bridge` field during static initialization
- Uses ExtensionLoader to find extensions providing requested services
- Loads extension classes from extension classloaders
- Ensures extensions are loaded before returning class

### 4. Preprocessor (btrace-agent)

**Location:** `btrace-agent/src/main/java/io/btrace/instr/Preprocessor.java`

**Purpose:** Transforms @Injected field references to invokedynamic

**Transformation:**
```java
// Before:
GETSTATIC ScriptClass.metrics : MetricsService

// After:
INVOKEDYNAMIC bootstrapFieldGet(
  "metrics",                           // field name (debug)
  "()Lio/btrace/metrics/MetricsService;",  // method descriptor
  ExtensionIndy.bootstrapFieldGet,     // bootstrap method
  "io.btrace.metrics.MetricsService",  // service class name
  "RUNTIME",                           // optional type hint (deprecated)
  ""                                   // factory method (empty = constructor)
) : MetricsService
```

**Modified Method:**
```java
private AbstractInsnNode updateInjectedUsage(
    ClassNode cn, FieldInsnNode fin, InsnList l, LocalVarGenerator lvg)
```

**Changes:**
- Removed local variable caching
- Removed service instantiation bytecode
- Emits InvokeDynamicInsnNode instead
- Passes service metadata as bootstrap arguments

### 5. ExtensionLoader (btrace-core)

**Location:** `btrace-core/src/main/java/io/btrace/extension/ExtensionLoader.java`

**Key Changes:**
- Avoids `instrumentation.appendToBootstrapClassLoaderSearch(jarFile)`
- Avoids an eager extension loading loop
- **Behavior:** Extensions loaded on-demand via invokedynamic

**Impact:**
- Bootstrap classloader remains clean
- Extensions only loaded when scripts use them
- Better resource utilization

## Execution Flow

### Initialization (Agent Startup)

```
1. Main.agentmain/premain()
   ↓
2. ExtensionLoader.discoverExtensions()
   - Scans extensions/ directory
   - Creates ExtensionDescriptor for each extension
   - Stores in availableExtensions map
   - Does NOT load extensions yet
   ↓
3. ExtensionBridgeImpl.<clinit>() (static initializer)
   - Creates new ExtensionBridgeImpl instance
   - Sets ExtensionIndy.bridge = instance
   - Bridge now ready for invokedynamic bootstrap calls
   ↓
4. Scripts can now be loaded and compiled
```

### Script Loading (First Access)

```
1. Script bytecode contains:
   INVOKEDYNAMIC bootstrapFieldGet(...) : MetricsService
   ↓
2. JVM calls ExtensionIndy.bootstrapFieldGet()
   (first time for this callsite)
   ↓
3. ExtensionIndy.bootstrapFieldGet():
   a. Call bridge.getExtensionClass("io.btrace.metrics.MetricsService")
   ↓
4. ExtensionBridgeImpl.getExtensionClass():
   a. Call loader.findExtensionForService(serviceClassName)
   b. If ext.isLoaded() == false:
      - loader.load(ext)  // Loads extension JAR into ExtensionClassLoader
   c. extClassLoader.loadClass(serviceClassName)
   d. Return Class<?> to bootstrap method
   ↓
5. ExtensionIndy.bootstrapFieldGet() (continued):
   a. Create MethodHandle for service instantiation:
      - Auto-detect constructor or factory
      - Initialize extensions via Extension.initialize(ExtensionContext) when first accessed
   b. Return new ConstantCallSite(methodHandle)
   ↓
6. JVM caches ConstantCallSite
   ↓
7. Subsequent calls to same INVOKEDYNAMIC:
   - Use cached CallSite (zero overhead)
   - MethodHandle directly instantiates service
   - No bootstrap method re-execution
```

### Subsequent Field Access (Same Script)

```
1. Script executes: metrics.histogram("foo")
   ↓
2. INVOKEDYNAMIC instruction
   ↓
3. JVM uses cached CallSite (no bootstrap)
   ↓
4. MethodHandle executes:
   - Instantiates MetricsService
   - Returns service instance
   ↓
5. INVOKEVIRTUAL MetricsService.histogram(String)
   - Normal virtual dispatch
   - JVM resolves from actual object class
   - Extension method executes
```

## Performance Characteristics

### First Access

**Overhead:**
- Bootstrap method execution: ~1-5ms
  - Extension class loading (if not already loaded)
  - MethodHandle creation
  - CallSite creation

**One-time cost per @Injected field per script**

### Subsequent Access

**Overhead:** Zero (compared to direct method call)
- JVM optimizes ConstantCallSite to direct call
- Identical performance to pre-invokedynamic approach
- JIT compiler inlines if possible

### Memory

**Before (Bootstrap Classpath):**
- All extension JARs in bootstrap: ~5-10MB per extension
- Permanent heap allocation
- Visible to all scripts and JVM internals

**After (invokedynamic Bridge):**
- Only loaded extensions in memory
- Extension classloaders eligible for GC when scripts unload
- Clean namespace separation

## Extension Types and Instantiation

### Extension Instances (No-arg)

Preferred pattern: extensions expose a public no-arg constructor and receive
their runtime context via `Extension.initialize(ExtensionContext)`.

**Constructor Example:**
```java
public class MyExtension extends Extension {
  public MyExtension() {
    // No-arg constructor
  }
}
```

**MethodHandle Creation:**
```java
MethodHandle constructor =
  MethodHandles.publicLookup().findConstructor(
    serviceClass,
    MethodType.methodType(void.class));
```

Factory methods are also supported but less common in the new model.

**MethodHandle Creation:**
```java
MethodHandle factory =
  MethodHandles.publicLookup().findStatic(
    serviceClass,
    "getInstance",
    MethodType.methodType(serviceClass));
```

### Runtime-Aware Initialization

If an extension needs access to runtime context, override `initialize(ExtensionContext)`
in your subclass of `Extension`. The bridge calls it after construction.

**Initialization Example:**
```java
public class MyExtension extends Extension {
  @Override
  public void initialize(ExtensionContext ctx) {
    super.initialize(ctx);
    // Use ctx as needed
  }
}
```

**MethodHandle Creation:**
```java
Class<?> runtimeImplClass =
  Class.forName("io.btrace.runtime.BTraceRuntime$Impl");

MethodHandle constructor =
  MethodHandles.publicLookup().findConstructor(
    serviceClass,
    MethodType.methodType(void.class, runtimeImplClass));

// Bind BTraceRuntime.enter() as first argument
MethodHandle getRuntimeImpl =
  MethodHandles.publicLookup().findStatic(
    BTraceRuntime.class,
    "enter",
    MethodType.methodType(runtimeImplClass));

mh = MethodHandles.filterReturnValue(getRuntimeImpl, constructor);
```

## Classloader Hierarchy

### Script Classloader

```java
// In BTraceRuntimeImpl_8.defineClass()
ClassLoader loader = new ClassLoader(null) {};  // parent = null
Class<?> cl = unsafe.defineClass(name, code, 0, code.length, loader, null);
```

**Parent:** null (delegates directly to bootstrap)
**Visibility:** Only bootstrap classes
**Impact:** Scripts can only see classes in bootstrap classpath

### Extension Classloader

```java
// In ExtensionLoader.load()
ExtensionClassLoader classLoader = new ExtensionClassLoader(
  extensionId,
  extensionVersion,
  new URL[] {jarUrl},
  parentClassLoader  // BTrace boot classloader
);
```

**Parent:** BTrace boot classloader (btrace-boot.jar)
**Visibility:** Extension JAR + BTrace core APIs
**Isolation:** Each extension in separate classloader

**Shaded Dependencies:**
- Extensions use shadow plugin to shade dependencies
- Prevents version conflicts between extensions
- Example: btrace-metrics shades HdrHistogram

### Classloader Delegation

```
Script Class
├── parent = null → Bootstrap ClassLoader
│   ├── JRE system classes
│   ├── btrace-boot.jar (BTraceRuntime, annotations)
│   └── ExtensionIndy.class
│
ExtensionClassLoader (per extension)
├── parent = BTrace Boot ClassLoader
│   └── btrace-boot.jar classes
├── Extension JAR classes
└── Shaded dependencies
```

**invokedynamic bridges the gap:**
- Script sees only bootstrap
- Bootstrap method accesses extension via bridge
- MethodHandle allows cross-classloader calls

## Java Version Compatibility

### Java 8+

**invokedynamic features used:**
- `INVOKEDYNAMIC` instruction (Java 7+)
- `MethodHandles` (Java 7+)
- `CallSite` / `ConstantCallSite` (Java 7+)
- `Handle` (Java 7+)

**NOT used:**
- Hidden classes (`Lookup.defineHiddenClass`) - Java 15+ only
- VarHandles - Java 9+ only

**Result:** Works on all BTrace-supported Java versions (8+)

### Comparison with Indy.java

**Indy.java (probe handlers):**
- Uses hidden classes (Java 15+ only)
- Creates dynamic hidden classes at link time
- Complex nested class generation

**ExtensionIndy.java (extensions):**
- Uses standard classloading (Java 8+)
- Loads classes from extension classloaders
- Simple MethodHandle creation

## Error Handling and Debugging

### Bootstrap Method Failures

**Causes:**
- Extension not found
- Extension class loading failure
- Constructor/factory method not found
- Runtime instantiation error

**Behavior:**
```java
try {
  // Load class and create MethodHandle
} catch (Throwable t) {
  // Graceful degradation: return null
  mh = MethodHandles.constant(type.returnType(), null);
}
return new ConstantCallSite(mh);
```

**Impact:**
- Script continues execution
- Service field is null
- NPE on first usage (matches current behavior)

### Debugging

**Enable logging:**
```
-Dorg.slf4j.simpleLogger.log.io.btrace.agent.extension=DEBUG
-Dorg.slf4j.simpleLogger.log.io.btrace.runtime.ExtensionIndy=DEBUG
```

**Logs show:**
- Extension discovery
- Extension loading
- Service class resolution
- MethodHandle creation

**Bytecode inspection:**
```bash
javap -v ScriptClass.class | grep -A 5 INVOKEDYNAMIC
```

**Shows:**
- Bootstrap method reference
- Bootstrap arguments
- Method descriptor

## Migration from Bootstrap Classpath Approach

### Before

**ExtensionLoader.load():**
```java
if (instrumentation != null) {
  JarFile jarFile = new JarFile(descriptor.getJarPath().toFile());
  instrumentation.appendToBootstrapClassLoaderSearch(jarFile);
}
```

**Preprocessor.updateInjectedUsage():**
```java
// Create local variable
int varIdx = lvg.newVar(implType);
// Emit instantiation bytecode
toInsert.add(new TypeInsnNode(Opcodes.NEW, implType.getInternalName()));
toInsert.add(new InsnNode(Opcodes.DUP));
toInsert.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, ...));
// Store in local variable
toInsert.add(new VarInsnNode(Opcodes.ASTORE, varIdx));
```

### After

**ExtensionLoader.load():**
```java
// Extension classes accessed via invokedynamic bridge
// (no bootstrap classpath pollution)
```

**Preprocessor.updateInjectedUsage():**
```java
// Emit invokedynamic instruction
InvokeDynamicInsnNode indyInsn = new InvokeDynamicInsnNode(
  fieldName,
  methodDescriptor,
  bootstrapHandle,
  serviceClassName,
  serviceType,
  factoryMethod);
```

### Compatibility

**Existing scripts:** No changes required
- Same @Injected annotation
- Same field types
- Same usage patterns

**Existing extensions:** No changes required
- Same service classes
- Same constructors/factories
- Same MANIFEST.MF metadata

**Behavioral changes:** None
- Extension loading now on-demand
- Performance characteristics unchanged
- Error handling identical

## Security Implications

### Bootstrap Classloader Access

**Before:**
- Extensions had bootstrap-level privileges
- Could access JVM internals
- Could interfere with system classes

**After:**
- Extensions in isolated classloaders
- Limited to parent visibility (BTrace boot)
- Cannot access bootstrap internals

### MethodHandle Security

**MethodHandles.publicLookup():**
- Only creates handles for public members
- Respects Java access control
- Cannot access private/protected methods

**Lookup Context:**
- Bootstrap method has caller's lookup
- Could create privileged handles
- Current implementation uses publicLookup() for safety

## Testing

### Unit Tests

**ExtensionIndy:**
- Test bootstrap method with mock bridge
- Test SIMPLE vs RUNTIME services
- Test constructor vs factory instantiation
- Test error handling

**ExtensionBridgeImpl:**
- Test extension finding
- Test class loading
- Test extension loading on demand

### Integration Tests

**MetricsTest:**
- Uses @Injected MetricsService
- Verifies invokedynamic bootstrap execution
- Verifies service instantiation
- Verifies method calls work

**Verification:**
```bash
# Check bytecode contains INVOKEDYNAMIC
javap -v integration-tests/build/classes/java/test/MetricsTest.class | grep INVOKEDYNAMIC

# Check extension NOT in bootstrap
jcmd <pid> VM.class_hierarchy | grep -v btrace-metrics
```

## Future Enhancements

### 1. Extension API/Impl Split

**Concept:**
- `btrace-metrics-api.jar` → bootstrap (interfaces only)
- `btrace-metrics-impl.jar` → extension CL (implementations)

**Benefits:**
- Cleaner verification
- Smaller bootstrap JAR
- Better security boundaries

### 2. Service Caching

**Current:** Each invokedynamic creates new instance
**Enhancement:** Cache service instances per script

**Implementation:**
```java
// In ExtensionIndy
private static final Map<String, Object> instanceCache = new ConcurrentHashMap<>();

// In bootstrapFieldGet()
String cacheKey = caller.lookupClass().getName() + ":" + serviceClassName;
Object cached = instanceCache.get(cacheKey);
if (cached != null) {
  return new ConstantCallSite(MethodHandles.constant(type.returnType(), cached));
}
```

### 3. Hot Reload Support

**Challenge:** Extension updates require agent restart
**Enhancement:** Use MutableCallSite instead of ConstantCallSite

**Implementation:**
```java
public static CallSite bootstrapFieldGet(...) {
  MethodHandle mh = createServiceHandle(...);
  return new MutableCallSite(mh);  // Allows invalidation
}

// On extension reload:
public static void invalidateExtension(String extensionId) {
  // Find all MutableCallSites for this extension
  // Call MutableCallSite.setTarget() with new MethodHandle
}
```

## Summary

The invokedynamic extension bridge provides:

✅ **Clean bootstrap namespace** - Only BTrace core in bootstrap
✅ **Extension isolation** - Each extension in own classloader
✅ **Java 8+ compatible** - No Java 15+ features required
✅ **Zero performance overhead** - After first call
✅ **Backward compatible** - No script changes required
✅ **Better security** - Extensions lack bootstrap privileges
✅ **On-demand loading** - Extensions loaded when needed

This architecture enables scalable, secure, and performant extension loading while maintaining full compatibility with existing BTrace scripts and extensions.
