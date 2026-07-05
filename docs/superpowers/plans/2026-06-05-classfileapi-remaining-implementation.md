# ClassFile API Remaining Features Step-by-Step Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Each phase below should be implemented as a small commit with at least one independent review before moving to the next verifier-sensitive phase.

**Goal:** Implement the remaining Java 26+ `ClassFileApiBackend` feature parity work in ordered, testable slices.

**Reference Plan:** `docs/superpowers/plans/2026-06-05-classfileapi-remaining-features.md`

**Primary Files:**
- `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`
- `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`
- ASM parity references under `btrace-agent/src/main/java/io/btrace/instr/Instrumentor.java`

**Test Environment:**

```sh
export JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem
export GRADLE_USER_HOME=$(pwd)/.gradle-user
```

Do not stream Gradle logs. Always redirect to `/tmp/...` and summarize with `rg`.

---

## Phase 0: Baseline Inventory And Harness

**Purpose:** Lock down the remaining work before changing backend behavior.

### Parity Table

| Kind | ASM reference | Where | Ordinary parameters | Special parameters | Primary risk |
| --- | --- | --- | --- | --- | --- |
| `LINE` | `LineNumberInstrumentor` | `BEFORE`, `AFTER` | line `int` | `@Self`, `@ProbeClassName`, `@ProbeMethodName` | emitting into pseudo-instruction regions and constructor prologue placement |
| `FIELD_GET` | `FieldAccessInstrumentor` get hooks | `BEFORE`, `AFTER` | none | `@Self`, `@TargetInstance`, `@TargetMethodOrField`, `@Return` after get | preserving `getfield` owner and category-2 field values |
| `FIELD_SET` | `FieldAccessInstrumentor` put hooks | `BEFORE`, `AFTER` | new field value | `@Self`, `@TargetInstance`, `@TargetMethodOrField` | restoring `putfield` owner/value stack order |
| `ARRAY_GET` | `ArrayAccessInstrumentor` load hooks | `BEFORE`, `AFTER` | index `int` | `@Self`, `@TargetInstance`, `@Return` after load | preserving arrayref/index and category-2 element values |
| `ARRAY_SET` | `ArrayAccessInstrumentor` store hooks | `BEFORE`, `AFTER` | index `int`, new element value | `@Self`, `@TargetInstance` | restoring arrayref/index/value stack order |
| `CHECKCAST` | `TypeCheckInstrumentor` checkcast hooks | `BEFORE`, `AFTER` | target type `String` | `@Self`, `@TargetInstance` | preserving failing-cast semantics |
| `INSTANCEOF` | `TypeCheckInstrumentor` instanceof hooks | `BEFORE`, `AFTER` | target type `String` | `@Self`, `@TargetInstance` | preserving boolean result and null behavior |
| `THROW` | `ThrowInstrumentor` | at `athrow` | enclosing method args | `@Self`, `@TargetInstance` thrown throwable | preserving thrown object identity |
| `CATCH` | `CatchInstrumentor` | handler entry | enclosing method args | `@Self`, `@TargetInstance` caught throwable | typed catch-handler filtering and frame compatibility |
| `ERROR` | `ErrorReturnInstrumentor` | uncaught method exit | thrown `Throwable` | `@Self`, `@TargetInstance`, `@Duration` | synthetic handler range and duration state |
| `NEWARRAY` | `ArrayAllocInstrumentor` | `BEFORE`, `AFTER` | array type `String`, dimensions `int` | `@Self`, `@Return` after allocation | multidimensional descriptors and return capture |
| `NEW` | object allocation hooks in ASM path | `BEFORE`, `AFTER` | object type `String` | `@Self`, `@Return` after allocation | uninitialized object verifier rules |
| `SYNC_ENTRY` | `SynchronizedInstrumentor` entry hooks | `BEFORE`, `AFTER` | lock object | `@Self`, `@TargetInstance` | lock object preservation and synchronized methods |
| `SYNC_EXIT` | `SynchronizedInstrumentor` exit hooks | `BEFORE`, `AFTER` | lock object | `@Self`, `@TargetInstance`, `@Duration` | unlock semantics on normal and exceptional paths |
| sampled/level guards | `MethodTrackingContext` and level checks | guard-dependent | n/a | n/a | guard placement before stack-mutating argument capture |

- [x] Create a parity table in this plan or a companion doc listing each remaining `Kind`, supported `Where` values, ordinary parameters, special parameters, and ASM instrumentor section.
- [x] Add test fixture helpers for Java 26 class-file generation:
  - [x] line number fixtures
  - [x] field get/set fixtures
  - [x] array get/set fixtures
  - [x] `checkcast` and `instanceof` fixtures
  - [x] `new`, `newarray`, `anewarray`, and `multianewarray` fixtures
  - [x] `athrow`, catch table, and uncaught error fixtures
  - [x] `monitorenter` and `monitorexit` fixtures
- [x] Add bytecode assertion helpers:
  - [x] find invokedynamic actions by action method name
  - [x] assert invokedynamic before or after a specific opcode
  - [x] assert generated local stores/loads preserve target instruction operands
  - [x] assert `System.nanoTime` placement for duration-sensitive probes
- [x] Add one negative test proving a still-unsupported kind returns `null` until its phase is implemented.

**Verification:**

```sh
JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test --tests io.btrace.instr.ClassFileApiBackendTest > /tmp/classfileapi-phase0.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ClassFileApiBackendTest" /tmp/classfileapi-phase0.log
```

**Commit:** `test(agent): add ClassFile API parity fixtures`

---

## Phase 1: Shared Validation And Context Plumbing

**Purpose:** Prepare reusable machinery without changing unsupported kinds yet.

- [ ] Introduce a small location-family grouping so `collectHandlers` can classify all `Kind` values without broad switch duplication.
- [ ] Extract common handler validation for:
  - [ ] enclosing `@Self`
  - [ ] `@ProbeClassName`
  - [ ] `@ProbeMethodName`
  - [ ] `@TargetInstance`
  - [ ] `@TargetMethodOrField`
  - [ ] `@Return`
  - [ ] `@Duration`
- [ ] Add `AnyType[]` aggregate argument support where missing for existing `ENTRY`, `RETURN`, and `CALL`.
- [x] Add type-constrained outer method matching from method descriptor data.
- [ ] Keep existing `ENTRY`, `RETURN`, and `CALL` tests green after each helper extraction.

**Review Focus:**
- No behavior regression for already-supported probes.
- No new stack mutations before validation succeeds.

**Commit:** `feat(agent): add ClassFile API probe validation helpers`

---

## Phase 2: `Kind.LINE`

**Purpose:** Implement the lowest stack-risk remaining location.

- [x] Collect line handlers separately from entry/return/call handlers.
- [x] Track the current line from ClassFile API line number elements.
- [x] Emit `Where.BEFORE` line probes at the next real executable element after a matching line marker.
- [x] Emit `Where.AFTER` line probes at the next line boundary and method end.
- [x] Load ordinary line number argument as `int`.
- [x] Load `@Self`, `@ProbeClassName`, and `@ProbeMethodName`.
- [x] Add tests:
  - [x] exact line match
  - [x] non-matching line
  - [x] repeated line entry only emits at intended executable point
  - [x] line probe combined with entry, return, and call probes
  - [x] after-line emission
  - [x] core special parameters

**Commit:** `feat(agent): support ClassFile API line probes`

---

## Phase 3: `Kind.FIELD_GET`

**Purpose:** Implement read access before field writes, because stack preservation is simpler.

- [x] Match `getfield` and `getstatic` owner/name against `Location.clazz()` and `Location.field()`.
- [x] For `Where.BEFORE`:
  - [x] preserve `getfield` owner when `@TargetInstance` is requested
  - [x] load `null` target instance for `getstatic`
  - [x] load target field name and FQN form compatible with ASM
- [x] For `Where.AFTER`:
  - [x] duplicate and store the field value when `@Return` is requested
  - [x] box primitive field values for `Object` and `AnyType`
  - [x] preserve category-2 `long` and `double` field values
- [x] Add tests:
  - [x] `getfield` before/after
  - [x] `getstatic` before/after
  - [x] primitive return boxing
  - [x] reference return
  - [x] FQN `@TargetMethodOrField`
  - [x] no-match owner/name filters

**Commit:** `feat(agent): support ClassFile API field get probes`

---

## Phase 4: `Kind.FIELD_SET`

**Purpose:** Implement write access after the field-get stack patterns are proven.

- [x] Match `putfield` and `putstatic` owner/name against location filters.
- [x] Backup field value before the put instruction for ordinary handler arguments.
- [x] Backup `putfield` owner when `@TargetInstance` is requested.
- [x] Restore operands in the exact JVM order before emitting the original instruction.
- [x] Support `Where.BEFORE` and `Where.AFTER`.
- [x] Support primitive value boxing for `Object` and `AnyType`.
- [x] Add tests:
  - [x] `putfield` before/after
  - [x] `putstatic` before/after
  - [x] primitive values
  - [x] category-2 values
  - [x] reference values
  - [x] FQN target field
  - [x] no-match owner/name filters

**Review Focus:** Verify `putfield` value/owner order and category-2 local allocation.

**Commit:** `feat(agent): support ClassFile API field set probes`

---

## Phase 5: `Kind.ARRAY_GET`

**Purpose:** Implement array reads before array writes.

- [x] Match all JVM array load opcodes.
- [x] Backup array reference and index before the array load when needed.
- [x] Load ordinary handler arguments: array index.
- [x] Load `@TargetInstance` as the array reference.
- [x] For `Where.AFTER`, duplicate and store the loaded element when `@Return` is requested.
- [x] Box primitive element returns for `Object` and `AnyType`.
- [x] Add tests:
  - [x] object array load
  - [x] primitive int array load
  - [x] `long` and `double` array load
  - [x] before/after placement
  - [x] return capture
  - [x] no-match type filters

**Commit:** `feat(agent): support ClassFile API array get probes`

---

## Phase 6: `Kind.ARRAY_SET`

**Purpose:** Implement array writes using the proven field-set backup pattern.

- [x] Match all JVM array store opcodes.
- [x] Backup value, index, and array reference in stack order.
- [x] Restore operands before the original array store.
- [x] Load ordinary handler arguments: index and new value, with array reference via
      `@TargetInstance`, matching ASM order.
- [x] Support primitive value boxing for `Object` and `AnyType`.
- [x] Add tests:
  - [x] object array store
  - [x] primitive int array store
  - [x] `long` and `double` array store
  - [x] before/after placement
  - [x] value capture
  - [x] no-match type filters

**Review Focus:** Confirm category-2 values do not corrupt arrayref/index restore order.

**Commit:** `feat(agent): support ClassFile API array set probes`

---

## Phase 7: `Kind.CHECKCAST` And `Kind.INSTANCEOF`

**Purpose:** Implement type-check probes with minimal operand-stack disturbance.

- [x] Match ClassFile API type-check instructions for `checkcast` and `instanceof`.
- [x] Match target type against `Location.clazz()`.
- [x] Backup checked object when `@TargetInstance` is requested.
- [x] Load ordinary handler argument as Java type name string.
- [x] Support `Where.BEFORE` and `Where.AFTER`.
- [x] Preserve behavior for null operands, failing casts, and boolean `instanceof` result.
- [x] Add tests:
  - [x] matching `checkcast`
  - [x] failing `checkcast` still throws
  - [x] matching `instanceof`
  - [x] non-matching target type
  - [x] null operand
  - [x] target instance capture

**Commit:** `feat(agent): support ClassFile API type check probes`

---

## Phase 8: `Kind.THROW`

**Purpose:** Implement explicit throw probes before synthetic error/catch handling.

- [x] Match `athrow`.
- [x] Backup throwable when `@TargetInstance` is requested.
- [x] Load `@Self`, `@ProbeClassName`, and `@ProbeMethodName`.
- [x] Preserve thrown object identity.
- [x] Add tests:
  - [x] explicit throw probe
  - [x] rethrow probe
  - [x] target throwable capture
  - [x] thrown identity preserved
  - [x] no target stack change when handler does not validate

**Commit:** `feat(agent): support ClassFile API throw probes`

---

## Phase 9: `Kind.CATCH` And `Kind.ERROR`

**Purpose:** Add exception table entry probes and uncaught-exit probes.

- [x] For `CATCH`, identify exception handler entry labels from ClassFile API exception table metadata.
- [x] Emit catch probe after the handler starts, with the caught throwable still available.
- [x] For `ERROR`, add a dedicated uncaught-exit handler if existing duration exceptional handling does not cover `Kind.ERROR` handlers.
- [x] Support throwable argument and `@TargetInstance` as ASM does.
- [x] Support `@Duration` for `ERROR`.
- [x] Add tests:
  - [x] caught exception handler entry
  - [x] caught throwable capture
  - [x] uncaught method error
  - [x] `@Duration` on error
  - [x] caught exception does not trigger error probe
  - [x] exception table ranges remain valid

**Review Focus:** Exception table transformations and stack map frame compatibility.

**Commit:** `feat(agent): support ClassFile API exception probes`

---

## Phase 10: `Kind.NEWARRAY`

**Purpose:** Implement array allocation before object allocation because arrays produce initialized references immediately.

- [x] Match `newarray`, `anewarray`, and `multianewarray`.
- [x] Load ordinary arguments: Java array type name and dimensions.
- [x] Match `Location.clazz()` against ASM-compatible Java type names.
- [x] For `Where.AFTER`, duplicate allocated array and pass `@Return`.
- [x] Support primitive and reference arrays.
- [x] Add tests:
  - [x] primitive `newarray`
  - [x] reference `anewarray`
  - [x] multidimensional array
  - [x] before/after placement
  - [x] return capture
  - [x] no-match type filter

**Commit:** `feat(agent): support ClassFile API array allocation probes`

---

## Phase 11: `Kind.NEW`

**Purpose:** Implement object allocation carefully around uninitialized JVM values.

- [x] Match `new` instructions against `Location.clazz()`.
- [x] For `Where.BEFORE`, emit before allocation with the target type name only.
- [x] For `Where.AFTER`, determine ASM-compatible safe point for `@Return`; avoid exposing uninitialized objects before constructor completion.
- [x] Skip or defer constructor edge cases that cannot be made verifier-safe.
- [x] Add tests:
  - [x] before object allocation
  - [x] after object allocation without return
  - [x] after object allocation with initialized return where verifier-safe
  - [x] constructor safety skip
  - [x] no-match type filter

**Review Focus:** Uninitialized object handling is high-risk; review bytecode with verifier expectations, not only instruction order.

**Commit:** `feat(agent): support ClassFile API object allocation probes`

---

## Phase 12: `Kind.SYNC_ENTRY` And `Kind.SYNC_EXIT`

**Purpose:** Implement synchronization probes after stack backup patterns are mature.

- [x] Match `monitorenter` and `monitorexit`.
- [x] Backup lock object for ordinary argument and `@TargetInstance`.
- [x] Support `Where.BEFORE` and `Where.AFTER`.
- [x] Document synchronized-method behavior: ClassFile API backend instruments explicit monitor bytecodes and does not synthesize separate method-flag probes.
- [x] Document and reject `@Duration` for synchronization probes; it is intentionally unsupported.
- [x] Add tests:
  - [x] synchronized block entry before/after
  - [x] synchronized block exit before/after
  - [x] exceptional monitor exit path
  - [x] unsupported sync duration parameters

**Review Focus:** Lock/unlock semantics must not be changed, especially on exceptional paths.

**Commit:** `feat(agent): support ClassFile API sync probes`

---

## Phase 13: Sampled And Level Guards

**Purpose:** Bring guard semantics in line with ASM after all probe families can emit.

- [x] Port level guard behavior for non-entry probes.
- [x] Port sampled probe behavior for entry/return/error where ASM uses `MethodTrackingContext`.
- [x] Ensure guards run before expensive parameter capture where possible.
- [x] Ensure guards do not skip required operand restoration.
- [x] Add tests:
  - [x] level-guarded probe still emits `invokedynamic`; the MethodHandle layer enforces the guard.
  - [x] sampled entry/return pair
  - [x] unsampled probe avoids `MethodTracker.hit`
  - [x] guarded field/array/call probe preserves stack through focused placement tests

**Commit:** `feat(agent): honor ClassFile API probe guards`

---

## Phase 14: Consolidation, Full Regression, And PR Update

- [ ] Remove obsolete "unsupported" debug messages for newly supported kinds.
- [ ] Keep explicit debug skips for intentionally unsupported or verifier-unsafe edge cases.
- [x] Add focused integration smoke coverage for ClassFile API field, array, allocation, sync, call, catch, and error probe families.
- [ ] Run `git diff --check`.
- [ ] Run targeted ClassFile API tests:

```sh
JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:classFileApiBackendTest > /tmp/classfileapi-final.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ClassFileApiBackendTest" /tmp/classfileapi-final.log
```

- [ ] Run formatting:

```sh
JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:spotlessCheck > /tmp/classfileapi-final-spotless.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|spotless" /tmp/classfileapi-final-spotless.log
```

- [ ] Run full agent tests:

```sh
JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test :btrace-agent:classFileApiBackendTest > /tmp/btrace-agent-classfileapi-final.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|tests|ClassFileApiBackendTest" /tmp/btrace-agent-classfileapi-final.log
```

- [ ] Update PR title and body to reflect completed scope and remaining intentionally skipped edge cases.

**Commit:** `docs(agent): update ClassFile API parity status`

---

## Implementation Order Summary

1. Baseline fixtures.
2. Shared validation helpers and type matching.
3. Line probes.
4. Field get.
5. Field set.
6. Array get.
7. Array set.
8. Checkcast and instanceof.
9. Throw.
10. Catch and error.
11. New array.
12. New object.
13. Sync entry and sync exit.
14. Guards, consolidation, tests, and PR update.
