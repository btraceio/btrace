# ClassFile API Remaining Feature Parity Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development for implementation. Each task should be implemented and reviewed independently because the remaining locations touch different bytecode shapes and verifier risks.

**Goal:** Extend the Java ClassFile API backend so Java 26+ class files are handled with feature parity close to the ASM backend for all remaining `@OnMethod` location kinds that are currently skipped.

**Current State:** The ClassFile API backend supports `Kind.ENTRY`, `Kind.RETURN`, and `Kind.CALL`, including `@Self`, `@Return`, `@Duration`, `@TargetInstance`, `@TargetMethodOrField`, and call arguments where applicable. Remaining work covers type-constrained method matching, `AnyType[]` argument-array parity where missing, line probes, field probes, array probes, type-test/cast probes, allocation probes, exception probes, synchronization probes, and sampled/level guards.

**Architecture:** Keep `ClassFileApiBackend` as the Java-26+ fallback backend and add one location family at a time. For each family, map the corresponding `java.lang.classfile.instruction.*` element to a small context object that can pre-validate handler parameters, preserve the operand stack, load special parameters, and emit the existing `IndyDispatcher` invokedynamic call. Prefer shared validation and argument-loading helpers, but avoid a broad rewrite until at least two families prove the abstraction is needed.

**Tech Stack:** Java 8 source style, JDK 26 test runtime, JDK ClassFile API source set under `src/main/java24`, JUnit Jupiter, ASM only for bytecode fixture generation and inspection in tests.

---

## Scope And Constraints

- Target implementation file: `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`.
- Target tests: `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`.
- Keep existing `ENTRY`, `RETURN`, and `CALL` behavior unchanged except for shared helper extraction that is covered by regression tests.
- Preserve the invariant that every handler is validated before the backend emits any partial probe-call stack.
- Preserve target bytecode semantics and stack shape around every transformed instruction.
- Use `JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem` for ClassFile API tests.
- Redirect Gradle output to `/tmp/...` and summarize it with `rg`; do not stream full Gradle logs.

## Out Of Scope

- Compiler/verifier changes outside the backend unless a parity gap is impossible to close locally.
- Runtime `IndyDispatcher` changes unless an existing bootstrap contract cannot represent a remaining location.
- Full integration-test expansion for every feature in the first pass; unit bytecode tests are required for each task, with integration coverage added for high-risk families.

---

## Task 1: Baseline Parity Matrix And Test Harness

- [ ] Build a parity matrix from the ASM backend and `Kind` annotation docs for every remaining location kind.
- [ ] Document supported parameters per kind: ordinary args, `AnyType[]` aggregate args, `@Self`, `@Return`, `@Duration`, `@TargetInstance`, `@TargetMethodOrField`, `@ProbeClassName`, and `@ProbeMethodName`.
- [ ] Add reusable test helpers for constructing classfile-version-70 fixtures with fields, arrays, casts, allocation, exceptions, line numbers, and monitor instructions.
- [ ] Add bytecode inspection helpers that assert invokedynamic placement relative to the target instruction and verify local-slot backup/restore behavior.

**Acceptance:** The plan has an executable checklist of remaining kinds, and the test fixture helpers can create minimal Java 26 class files for every task below.

## Task 2: Type-Constrained Outer Method Matching

- [ ] Compare ClassFile API method descriptors against `OnMethod` type constraints using the same assignability rules as the ASM path.
- [ ] Support exact descriptors, regex method names, and class-name matching already handled by probe applicability.
- [ ] Add tests for matching, non-matching, primitive descriptors, arrays, and reference assignability.
- [ ] Add regression tests for `AnyType[]` aggregate argument passing on `ENTRY`, `RETURN`, and `CALL` if the matrix shows any ClassFile API gap.

**Acceptance:** Handlers with `type="..."` are no longer skipped when the current method descriptor can be evaluated safely.

## Task 3: `Kind.LINE`

- [ ] Instrument `LineNumber` metadata from the ClassFile API code model.
- [ ] Support exact line matching and existing method/class filters.
- [ ] Ensure probes are emitted at the first executable element associated with the line, not into pseudo-instruction-only regions.
- [ ] Add tests for exact line, non-matching line, repeated line entries, and line probes coexisting with entry/return/call probes.

**Acceptance:** Java 26+ classes support line probes with stable placement and no verifier regressions.

## Task 4: `Kind.FIELD_GET` And `Kind.FIELD_SET`

- [ ] Match field access instructions against `Location.clazz()` and `Location.field()`.
- [ ] Support `Where.BEFORE` and `Where.AFTER` where ASM semantics allow them.
- [ ] Support `@TargetInstance` as field owner instance or `null` for static fields.
- [ ] Support `@TargetMethodOrField`, including FQN formatting compatible with ASM behavior.
- [ ] Support field value passing for get/set handlers, including primitive boxing to `Object` or `AnyType`.
- [ ] Preserve stack shape for static and instance fields, category-1 and category-2 values, and `putfield` value/owner ordering.
- [ ] Add tests for `getfield`, `getstatic`, `putfield`, `putstatic`, primitive fields, reference fields, FQN target names, and no-match filters.

**Acceptance:** Field probes fire around Java 26+ field accesses with correct owner/name/value parameters and unchanged target semantics.

## Task 5: `Kind.ARRAY_GET` And `Kind.ARRAY_SET`

- [ ] Instrument array load and store opcodes for primitive and reference arrays.
- [ ] Support array instance, index, and value parameters according to ASM backend behavior.
- [ ] Preserve stack shape for category-2 array values.
- [ ] Add tests for object arrays, primitive arrays, long/double arrays, before/after placement, and no-match behavior.

**Acceptance:** Array probes work for all JVM array load/store instruction families.

## Task 6: `Kind.CHECKCAST` And `Kind.INSTANCEOF`

- [ ] Match type instructions against `Location.clazz()`.
- [ ] Support `@TargetInstance` for the checked/casted object.
- [ ] Support target type name passing where ASM behavior exposes it.
- [ ] Preserve null and failure semantics: probes must not turn a failing cast into a passing one or change `instanceof` results.
- [ ] Add tests for matching type, non-matching type, null operands, failing casts, and reference assignability validation.

**Acceptance:** Type-test and cast probes preserve JVM behavior and expose the same context as ASM.

## Task 7: `Kind.NEW` And `Kind.NEWARRAY`

- [ ] Instrument object allocation and primitive/reference array allocation instructions.
- [ ] Support `Where.BEFORE` and `Where.AFTER` according to ASM semantics.
- [ ] Support `@Return` for allocated object/array after allocation where available.
- [ ] Handle constructor sequencing carefully: do not expose uninitialized objects to probes in verifier-unsafe positions.
- [ ] Add tests for object allocation, primitive arrays, object arrays, multidimensional arrays, return capture, and constructor safety.

**Acceptance:** Allocation probes are verifier-safe and expose allocated values only after they are initialized or otherwise safe under ASM-compatible semantics.

## Task 8: `Kind.THROW`, `Kind.CATCH`, And `Kind.ERROR`

- [ ] Instrument explicit `athrow` instructions for `Kind.THROW`.
- [ ] Instrument exception handler entries for `Kind.CATCH`.
- [ ] Confirm existing method-duration exceptional path satisfies `Kind.ERROR`; if not, add dedicated error handlers.
- [ ] Support throwable parameters and `@Duration` where ASM semantics allow them.
- [ ] Preserve original thrown exception identity and control flow.
- [ ] Add tests for explicit throw, rethrow, catch handler entry, uncaught method exit, caught exceptions, and duration-on-error.

**Acceptance:** Exception-related probes work without changing thrown object identity or exception table behavior.

## Task 9: `Kind.SYNC_ENTRY` And `Kind.SYNC_EXIT`

- [ ] Instrument `monitorenter` and `monitorexit` instructions.
- [ ] Support monitor object parameters where ASM exposes them.
- [ ] Treat synchronized methods separately if ASM emits method-level monitor probes.
- [ ] Add tests for explicit synchronized blocks, exceptional monitor exit paths, and synchronized methods if applicable.

**Acceptance:** Monitor probes preserve locking semantics and do not disturb structured or exceptional unlock paths.

## Task 10: Sampled And Level Guards

- [ ] Compare ASM backend handling for sampled probes and level filters.
- [ ] Add equivalent guard emission before invokedynamic dispatch for ClassFile API probes.
- [ ] Ensure guards short-circuit without evaluating expensive or stack-mutating parameter loads.
- [ ] Add tests proving disabled guards leave target stack and locals untouched.

**Acceptance:** ClassFile API probes honor sampling and level semantics consistently with ASM.

## Task 11: Consolidation And Regression Suite

- [ ] Extract shared helpers only after the remaining families reveal stable duplication.
- [ ] Add regression tests combining multiple probe families in one method.
- [ ] Run targeted ClassFile API tests:

```sh
JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test --tests io.btrace.instr.ClassFileApiBackendTest > /tmp/classfileapi-remaining-test.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ClassFileApiBackendTest" /tmp/classfileapi-remaining-test.log
```

- [ ] Run formatting:

```sh
JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:spotlessCheck > /tmp/classfileapi-remaining-spotless.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|spotless" /tmp/classfileapi-remaining-spotless.log
```

- [ ] Run full agent tests:

```sh
JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test > /tmp/btrace-agent-classfileapi-remaining-test.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|tests|ClassFileApiBackendTest" /tmp/btrace-agent-classfileapi-remaining-test.log
```

**Acceptance:** All ClassFile API backend features are covered by focused unit tests, shared helper extraction is regression-tested, and the agent test suite passes on JDK 26.

---

## Suggested Implementation Order

1. Baseline matrix and fixture helpers.
2. Type-constrained method matching.
3. Line probes.
4. Field probes.
5. Array probes.
6. Cast and instanceof probes.
7. Exception probes.
8. Allocation probes.
9. Monitor probes.
10. Sampled and level guards.
11. Consolidation and full regression pass.

Field and allocation probes are expected to be the highest verifier-risk tasks. They should get independent review before downstream work builds on them.
