# Next Session Plan - Complete MethodHandle-Level Guard Implementation

## Current State (Session 3 end)
- Identified root cause: `$btrace$$level` field never initialized, so bytecode level checks always fail
- Designed superior solution: Move level checking to MethodHandle linking layer
- Implemented framework in `HandlerRepositoryImpl`:
  - `applyLevelGuard()` method exists and is called
  - `createLevelCheckMH()` stub (currently returns constant true)
  - `createNoopMH()` fully implemented
  - Uses `MethodHandles.guardWithTest()` composition
- Commits done:
  - f3780e60: refactor(instr): move level checking to MethodHandle linking layer
  - c5729200: docs: architectural refinement - level checking at MethodHandle layer

## What Needs To Be Done

### 1. DISABLE BYTECODE-LEVEL LEVEL CHECKS (File: MethodTrackingContext.java)
The bytecode currently generates level checks like:
```
getstatic $btrace$$level
ldc 100
if_icmplt skip_handler
invokedynamic
skip_handler:
```

This prevents INVOKEDYNAMIC from executing when level=0.

**Location**: `/Users/jbachorik/src/btrace/btrace-instr/src/main/java/org/openjdk/btrace/instr/MethodTrackingContext.java`
- Find method `addLevelChecks(Supplier<Label> action)` around line 245-290
- Replace the entire level checking logic with simple passthrough:
  ```java
  private Label addLevelChecks(Supplier<Label> action) {
    OnMethod om = invocation.getOnMethod();
    // Level checks are now in MethodHandle layer (HandlerRepositoryImpl.applyLevelGuard)
    return action.get();
  }
  ```
- This disables bytecode-level checks; MethodHandle layer takes over

### 2. IMPLEMENT REAL LEVEL CHECK IN createLevelCheckMH() (File: HandlerRepositoryImpl.java)
**Location**: Line ~90 in HandlerRepositoryImpl.java

Current stub returns constant true. Needs to:
1. Create a MethodHandle that queries BTraceRuntime for current instrumentation level
2. Compare against the Level requirement from the OnMethod annotation
3. Return boolean result matching the handler's parameter types

**Implementation approach**:
```java
private static MethodHandle createLevelCheckMH(Level level, MethodType testType) throws Throwable {
  // Get the Interval from the level (e.g., ">=100")
  Interval interval = level.getValue();
  
  // Create MH that queries current instrumentation level from BTraceRuntime
  // This needs to be done via reflection since we're in the agent module
  // and BTraceRuntime lives in the runtime module
  MethodHandle getLevelMH = ... // get current level from runtime
  
  // Create comparison logic based on interval (>=, <=, ==, etc.)
  MethodHandle compareLevel = ... // compare current level against interval
  
  // Drop arguments to match testType (same params, return boolean)
  return MethodHandles.dropArguments(compareLevel, 0, testType.parameterArray());
}
```

**Key challenge**: Need to query current instrumentation level. Options:
- Call static method on BTraceRuntime to get current level
- Store level in a ThreadLocal accessible from handler resolution
- Pass level through probe metadata

### 3. REMOVE $btrace$$level FIELD FROM PROBE CLASS
Once bytecode-level checks are disabled, the `$btrace$$level` field is no longer needed.

**Location**: `/Users/jbachorik/src/btrace/btrace-instr/src/main/java/org/openjdk/btrace/instr/Preprocessor.java`
- Find method `addLevelField(ClassNode cn)` around line 521
- Either comment it out or make it conditional on a feature flag

This simplifies the probe class bytecode significantly.

### 4. TEST THE COMPLETE SOLUTION
```bash
# Build
./gradlew build -x test

# Run failing test
source ~/.sdkman/bin/sdkman-init.sh
JAVA_TEST_HOME=$(sdk home java 11.0.23-tem) ./gradlew :integration-tests:test \
    --tests 'tests.BTraceFunctionalTests.testOnMethodLevel' -Pintegration
```

**Expected result**: Test passes with output containing:
- "[this, noargs]"
- "[this, args]"
- "{xxx}"

## Architecture Summary (What We're Building)

**Old approach (BROKEN)**:
```
Instrumented bytecode checks: if (probeClass.$btrace$$level >= 100) { invokedynamic }
└─ Problem: Field never initialized, always 0, condition always false
```

**New approach (BEING IMPLEMENTED)**:
```
1. Instrumented bytecode: invokedynamic (no level check)
   └─ Calls IndyDispatcher.bootstrap()

2. IndyDispatcher.bootstrap() 
   └─ Calls HandlerRepositoryImpl.resolveHandler()

3. HandlerRepositoryImpl.resolveHandler()
   └─ Resolves handler MethodHandle
   └─ Calls applyLevelGuard() to wrap with level check
   └─ Returns composed MH: guardWithTest(levelCheck, realHandler, noop)

4. guardWithTest composition
   └─ At runtime: levelTest() returns boolean
   └─ If true: invoke realHandler
   └─ If false: invoke noop (return default value)
```

**Benefits**:
- No runtime field initialization needed
- JIT can optimize knowing level at compile time
- No deopt avalanche when level changes (MutableCallSite relinks)
- Clean separation: linking-time policy, not bytecode policy

## Files Modified So Far
- HandlerRepositoryImpl.java: Added applyLevelGuard(), createLevelCheckMH(), createNoopMH()
- RESUME.md: Updated with architectural refinement

## Files To Modify Next Session
1. MethodTrackingContext.java: Disable bytecode-level checks
2. HandlerRepositoryImpl.java: Implement createLevelCheckMH() with real logic
3. Preprocessor.java: Remove/disable addLevelField()
4. Test and verify

## Command to Pick Up
```bash
cd /Users/jbachorik/src/btrace
git log --oneline -5  # Should show the recent commits
git status  # Should be clean
```

## Critical Insight (From User)
The user identified that level checks should happen at MethodHandle linking time, not in bytecode. This is architecturally superior because:
- Avoids initializing runtime fields in probe class
- Prevents deopt avalanche
- Allows JIT to optimize based on known levels
- Cleaner separation of concerns

This insight completely changed the approach from "fix the initialization" to "eliminate the field and check at linking time".
