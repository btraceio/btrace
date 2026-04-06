# Implementation Plan: Convert probe to INVOKEDYNAMIC dispatch

## Key insight
Probe class lives on bootstrap classloader → cannot reference masked `.classdata` classes.
Fix: route all BTraceRuntimeAccess/BTraceRuntimeBridge calls through INVOKEDYNAMIC.

## Files to change

### 1. Preprocessor.java (btrace-instr)
- `addRuntimeNode()`: field desc → `Ljava/lang/Object;` (not BTraceRuntimeBridge)
- `initRuntime()`: replace handler array construction + `INVOKESTATIC forClass` with:
  ```
  LDC class
  INVOKEDYNAMIC "initRuntime" (Ljava/lang/Class;)Ljava/lang/Object; [runtimeBootstrap, "org/openjdk/btrace/runtime/BTraceRuntimeAccess"]
  PUTSTATIC runtime : Object
  ```
- `addRuntimeCheck()`: replace `INVOKESTATIC BTraceRuntimeAccess.enter(BTraceRuntimeBridge)Z` with:
  ```
  GETSTATIC runtime : Object
  INVOKEDYNAMIC "enter" (Ljava/lang/Object;)Z [runtimeBootstrap, "org/openjdk/btrace/runtime/BTraceRuntimeAccess"]
  ```
- `tlsInitSequence()`: replace `INVOKESTATIC BTraceRuntimeAccess.newThreadLocal` with:
  ```
  INVOKEDYNAMIC "newThreadLocal" (Ljava/lang/Object;)Ljava/lang/ThreadLocal; [runtimeBootstrap, "org/openjdk/btrace/runtime/BTraceRuntimeAccess"]
  ```
- ALL `INVOKEINTERFACE BTraceRuntimeBridge.xxx` → INVOKEDYNAMIC with Object receiver prepended:
  - `start()V` → `(Ljava/lang/Object;)V`
  - `leave()V` → `(Ljava/lang/Object;)V`
  - `handleException(Throwable)V` → `(Ljava/lang/Object;Ljava/lang/Throwable;)V`
  - `newPerfCounter(Object,String,String)V` → `(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V`
  - `getPerf*/putPerf*` → Object receiver prepended
  - owner arg = `"org/openjdk/btrace/core/BTraceRuntimeBridge"`
- `findBTraceRuntimeStart()`: look for INVOKEDYNAMIC "start" instead of INVOKEINTERFACE

Add shared Handle constant for runtimeBootstrap (mirror what Assembler.java has).

### 2. HandlerRepository.java (btrace-core)
Add method:
```java
Object initProbeRuntime(Class<?> probeClass);
```

### 3. HandlerRepositoryImpl.java (btrace-instr)
- Add inner class `HandlerMetadata` with arrays: TimerHandler[], EventHandler[], ErrorHandler[], ExitHandler[], LowMemoryHandler[]
- Add `Map<String, HandlerMetadata> handlerMetadata`
- In `registerProbe()`: extract handler metadata from probe's class node annotations, store in map
- Add `initProbeRuntime(Class<?> probeClass)`: retrieve metadata, call BTraceRuntimeAccess.forClass(), return Object
- In `resolveRuntime()`:
  - owner = `BTraceRuntimeAccess`, name = `"initRuntime"` → return MH to `this::initProbeRuntime` adapted to `(Class)Object`
  - owner = `BTraceRuntimeAccess`, name = `"enter"` → findStatic on BTraceRuntimeAccess.enter(BTraceRuntimeBridge), adapt Object→BTraceRuntimeBridge via asType
  - owner = `BTraceRuntimeAccess`, others → existing findStatic logic
  - owner = `BTraceRuntimeBridge` → findVirtual on BTraceRuntimeBridge interface, adapt first arg Object→BTraceRuntimeBridge

### 4. IndyDispatcher.java (btrace-core)
Add `initBootstrap` method (alternative approach - use existing `runtimeBootstrap` + special-casing in resolveRuntime).

Actually NO new bootstrap method needed: `runtimeBootstrap("initRuntime", owner=BTraceRuntimeAccess)` → delegates to `resolveRuntime` which returns initProbeRuntime handle.

## Handler metadata extraction in registerProbe
The BTraceProbe's class bytes contain annotations. We need to extract:
- `@OnTimer`: method name, period (long), from (String)
- `@OnEvent`: method name, event name (String)
- `@OnError`: method name
- `@OnExit`: method name
- `@OnLowMemory`: method name, pool (String), threshold (long), thresholdFrom (String)

Use ASM ClassReader on `probe.getFullBytecode()` with a custom visitor.

## Handler metadata for BTraceProbePersisted
Same approach: scan bytes with ClassReader.

## Descriptor constants needed (in Preprocessor)
```java
// For invokedynamic enter call:
"(Ljava/lang/Object;)Z"
// For initRuntime:  
"(Ljava/lang/Class;)Ljava/lang/Object;"
// For start/leave:
"(Ljava/lang/Object;)V"
// For handleException:
"(Ljava/lang/Object;Ljava/lang/Throwable;)V"
// For newPerfCounter:
"(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V"
// For newThreadLocal:
"(Ljava/lang/Object;)Ljava/lang/ThreadLocal;"
```
