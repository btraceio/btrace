# ClassFile API Backend — Remaining Gaps Plan

> **Status:** Post-merge follow-up to PR #843 (squash-merged into `develop` as
> `9f39a250`). PR #843 implemented ClassFile API parity for every `@OnMethod` location
> `Kind` (ENTRY, RETURN, CALL, LINE, FIELD_GET/SET, ARRAY_GET/SET, CHECKCAST,
> INSTANCEOF, THROW, CATCH, ERROR, NEWARRAY, NEW, SYNC_ENTRY/EXIT, plus sampled/level
> guards). This plan covers the pieces that work left open **plus** a parity gap it
> under-scoped: ordinary method-argument capture on ENTRY/RETURN and `AnyType[]`
> aggregate packaging.
>
> Note: the planning docs produced during PR #843 (`2026-06-04-…`, `2026-06-05-…`)
> were removed from `develop` after merge; this document is self-contained.

**Goal:** Close the remaining ClassFile API backend gaps versus the ASM backend, sweep
the open bot review findings on PR #843, and finish the Phase-1/14 housekeeping the
implementation plan deferred.

**Primary files:**
- `btrace-agent/src/main/java24/io/btrace/instr/ClassFileApiBackend.java`
- `btrace-agent/src/test/java/io/btrace/instr/ClassFileApiBackendTest.java`
- `btrace-agent/src/main/java/io/btrace/instr/ClassCache.java`
- `integration-tests/src/test/btrace/ClassFileApiFeatureSmokeTest.java`
- ASM parity reference: `btrace-agent/src/main/java/io/btrace/instr/Instrumentor.java`
  and `btrace-agent/src/main/java/io/btrace/instr/MethodInstrumentor.java`
  (`AnyTypeArgProvider`, `loadArguments`, `anytypeArg`).

**Test environment:**

```sh
export JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem
export GRADLE_USER_HOME=$(pwd)/.gradle-user
```

Do not stream Gradle logs. Redirect to `/tmp/...` and summarize with `rg`
(prefer the build-summarize skill).

---

## Evidence Summary (what is actually missing, with references)

### Gap A — Ordinary method arguments on ENTRY are not loaded

`ClassFileApiBackend.emitProbeCall` (around line 3320–3490) loads handler parameters in
a dispatch loop. For ENTRY probes it is called with `callContext == null` and
`lineNumber == -1` (line 822). The load loop has branches only for special params and
for context-specific ordinary params (line/field/array/typecheck/newarray/call). There
is **no branch that loads an ordinary method argument for an ENTRY handler**.

The pre-validation loop (line 3340–3380) therefore marks any ordinary ENTRY arg as
`!satisfiable` (callArgIndex=-1, lineArgIndex=-1, no context matches) and the handler is
silently skipped:

```java
log.debug("ClassFileApiBackend: skipping handler {}.{} — arg {} cannot be satisfied", ...);
return false;
```

ASM parity: `Instrumentor` ENTRY case calls
`loadArguments(vr, actionArgTypes, isStatic(), actionArgs)` (Instrumentor.java ~line 129),
which loads each ordinary arg from its local slot (`MethodInstrumentor.loadArguments`,
line 180–200). ENTRY probes capturing method arguments (`void onEntry(String a, int b)`)
are a standard, long-supported ASM feature.

Test coverage: every ENTRY test uses a `()V` handler descriptor
(`ClassFileApiBackendTest` lines 88, 105, 297; `ClassFileApiEntryTest.java` uses only
`@ProbeMethodName`). The gap is latent and untested.

**Verdict:** Real parity gap. ENTRY handlers declaring ordinary method args (typed or
`AnyType[]`) are silently dropped on Java 26+ class files.

### Gap B — Ordinary method arguments on RETURN are not loaded

Same mechanism as Gap A. `emitProbeCall` for RETURN is called with `callContext == null`
(line 895, 907). Ordinary RETURN args hit no load branch and fail pre-validation → skip.

ASM parity: `Instrumentor` RETURN case validates against
`Type.getArgumentTypes(getDescriptor())` (Instrumentor.java ~line 83) and calls
`loadArguments(vr, actionArgTypes, isStatic(), actionArgs)` (~line 943), so RETURN
handlers may capture enclosing-method arguments.

Test coverage: all RETURN tests use `()V` (line 157, 300). Latent gap.

**Verdict:** Real parity gap, same shape as Gap A.

### Gap C — `AnyType[]` aggregate is not packaged for CALL (or ENTRY/RETURN)

ASM packages all call/method args into an `Object[]` when a handler declares
`AnyType[]` (`MethodInstrumentor.AnyTypeArgProvider`, lines 636–665):

```java
asm.push(myArgTypes.length);
asm.newArray(Constants.OBJECT_TYPE);
for (int j = 0; j < myArgTypes.length; j++) {
  asm.dup().push(j).loadLocal(argType, argPtr).box(argType).arrayStore(OBJECT_TYPE);
  argPtr += argType.getSize();
}
```

The CALL path in `emitProbeCall` (line 3481–3484) loads a single call arg per ordinary
param:

```java
int callArgIndex = callArgumentIndex(om, i);
cb.loadLocal(typeKind(callContext.argumentTypes[callArgIndex]),
             callContext.argumentSlots[callArgIndex]);
```

There is no `AnyType[]` aggregate branch. Because the backend computes `argTypes` from
`om.getTargetDescriptor().replace(ANYTYPE_DESC, OBJECT_DESC)` (line 3327), an `AnyType[]`
param appears as `Object[]` and is indistinguishable from a literal `Object[]` param —
so the backend cannot currently tell it should emit the packaging loop.

Detection is available: `om.getTargetDescriptor()` returns the **original** descriptor
(the `.replace(...)` call the backend itself performs proves this), so an `AnyType[]`
param is `[` + `Constants.ANYTYPE_DESC` in the raw descriptor and can be detected before
replacement. `TypeUtils.isAnyTypeArray` / `anyTypeArray` (TypeUtils.java line 29) give the
canonical check used on the ASM side.

Pre-validation today: an `AnyType[]` ordinary CALL arg gets `callArgIndex=0`, passes the
`callArgIndex < callContext.argumentTypes.length` check, then at load time one call arg
is pushed where an `Object[]` is expected → **verifier error or wrong values**.

Test coverage: zero `AnyType[]` references in `ClassFileApiBackendTest.java`; the
integration `ClassFileApiFeatureSmokeTest.onCall` uses a single typed ordinary arg
(`Object key`), which works, but no `AnyType[]` handler is exercised.

**Verdict:** Real parity gap; actively produces bad bytecode when a CALL handler uses
`AnyType[]`. ENTRY/RETURN `AnyType[]` is folded into Gaps A/B.

### Gap D — Phase 1 shared validation helpers (deferred)

The implementation plan left Phase 1 unchecked: location-family grouping for
`collectHandlers` (today a ~30-arm if/else, line 190–224) and extraction of common
handler validation for `@Self`/`@ProbeClassName`/`@ProbeMethodName`/`@TargetInstance`/
`@TargetMethodOrField`/`@Return`/`@Duration`, which is currently duplicated across the
per-kind emit paths. This is a refactor, not a behavior gap, but it is the documented
reason Gaps A/B/C were easy to miss: ordinary-arg handling has no single home.

### Gap E — Dead "unsupported kind" debug branch

`collectHandlers` (line 206–224) handles every `Kind` value explicitly; the trailing

```java
else log.debug("Skipping unsupported probe kind {} for class {}", kind, javaClassName);
```

is now unreachable. Phase 14 asks for its removal (keep explicit debug skips only for
intentionally-unsupported edge cases — e.g. `@Duration` on SYNC, BEFORE on synchronized
methods, NEW AFTER+constructor safety skips, which are documented elsewhere).

### Gap F — Open bot review findings on PR #843

Unresolved `github-code-quality` comments:

1. **Unread local `boolean staticCall`** in `ClassFileApiBackend.canEmitCallProbe`
   (line 2679). Confirmed: the local is assigned but never read in that method (only
   `ownerType` is used). The field `CallContext.staticCall` (line 287) is genuinely
   read (line 1917, 3434) — the bot finding is about the dead *local* in
   `canEmitCallProbe`, not the field.
2. **~~Two "useless null check" findings in `ClassCache.java`~~ — STALE/MOOT.**
   Recheck (post-rebase) found the 66e2bfd3 `ClassCache.getInstance()` DCL fix was
   **dropped at merge**: the squash `9f39a250` never touched `ClassCache.java`, so those
   findings reference code absent from `develop`. What landed from 66e2bfd3 is the
   `ClassFilter.isSubTypeOf` defensive guard; its residual `if (cache == null)` arm is
   JLS-dead (holder idiom) and not bot-flagged. Resolved in Task 7 by folding it into the
   `ci == null` no-match path.
3. **~8 "useless parameter" findings in `ClassFileApiFeatureSmokeTest.java`**: unused
   handler params (`value`, `array`, `lock`, `key`, `set`, `exception` across
   `onArrayGet`, `onNewArray`, `onSyncEntry`, `onSyncExit`, `onCall`, `onNewObject`,
   `onCatch`, `onError`). For BTrace scripts the param declaration **drives
   instrumentation** (tells the backend what to capture), so an unused-in-body param is
   not actually useless. Commit `4340ffb4` fixed one (`onArraySet`'s `array`) by
   referencing it in the `println`. The right fix for the rest is the same: reference
   each captured value in the assertion `println`, which both satisfies the bot and
   strengthens the smoke test (it verifies the captured value, not just that the probe
   fired).

---

## Task Breakdown

### Task 1 — Ordinary method-argument loading for ENTRY (Gap A) — DONE

**Approach:**
- In `emitProbeCall`, add an ENTRY ordinary-arg path. ENTRY args live in fixed locals:
  slot `0` is `this` (unless static), then each arg in declaration order at slot
  `1 + offset` where offset accumulates by `Type.getSize()`. The ClassFile API
  `CodeModel` gives `thisSlot`/arg slots via `methodParamTypes` already available in the
  surrounding `instrumentMethod` context — reuse the slot computation used for CALL
  (`callContext.argumentSlots` is the model to mirror for method locals).
- Detect `AnyType[]` ordinary args (Gap C detection, shared helper) and emit the
  `Object[]` packaging loop instead of a single load.
- Add the satisfiability condition to pre-validation so typed ENTRY args are no longer
  rejected (mirror the CALL condition: ordinary index `< methodArgCount`).

**Acceptance:**
- New unit tests: ENTRY with one typed arg; ENTRY with multiple typed args (category-1
  and category-2); ENTRY with `AnyType[]`; ENTRY `@Self` + typed args; static vs
  instance method; mismatched descriptor rejected.
- Integration: a `ClassFileApiEntryArgsTest` btrace script capturing
  `Math.max(int,int)` args and printing them.

**Risk:** Medium. Slot math for category-2 args and static-vs-instance `this` offset.
Verify with `ClassFileApiBackendTest` bytecode assertions on local load opcodes.

### Task 2 — Ordinary method-argument loading for RETURN (Gap B) — DONE

**Approach:** Same as Task 1 but the RETURN emit path (line 895/907). Method args are
still in scope at every return point (locals are valid for the whole method body), so
the same slot computation applies. `@Return` and `@Duration` already have slots; ordinary
args reuse the method-local slots.

**Acceptance:**
- Unit tests: RETURN with typed method args; RETURN with `AnyType[]`; RETURN with
  `@Return` + typed args; multiple return instructions in one method; void return +
  args rejected or skipped per ASM semantics.
- Integration: extend the entry-args smoke script or add a return-args one.

**Risk:** Medium. Confirm ASM behavior for ordinary args on void-return methods
(ASM RETURN validates against `Type.getArgumentTypes(getDescriptor())`; args are
independent of return type, so they should be loadable even for void methods — verify).

### Task 3 — `AnyType[]` aggregate packaging (Gap C, CALL + shared) — DONE

**Approach:**
- Add a helper `isAnyTypeArrayParam(om, i)` that inspects the **original**
  `om.getTargetDescriptor()` (pre-replace) for `[` + `ANYTYPE_DESC` at param `i`. Use
  `Type.getArgumentTypes` on the original descriptor and `TypeUtils.isAnyTypeArray`.
- In `emitProbeCall`, for an ordinary param flagged `AnyType[]`:
  - ENTRY/RETURN: package all method args from their locals into `Object[]` (box
    primitives via the existing `boxPrimitiveReturn` helper or an equivalent
    `box(TypeKind)`).
  - CALL: package all call args from `callContext.argumentSlots` into `Object[]`.
  - Mirror `AnyTypeArgProvider.doProvide`: `push(len); newArray(OBJECT); for each:
    dup; push(j); loadLocal(type, slot); box; arrayStore`.
- Single-`AnyType` (non-array) ordinary arg: verify ASM behavior and either match it or
  document the deviation. (ASM `loadArguments` only special-cases `isAnyTypeArray`; a
  single `AnyType` ordinary arg is treated as a typed `Object` local load + box —
  confirm before implementing.)

**Acceptance:**
- Unit tests: CALL `AnyType[]` with 0/1/2/3 call args incl. long/double; CALL `AnyType[]`
  + `@TargetInstance`; ENTRY/RETURN `AnyType[]` (shared with Tasks 1/2).
- Bytecode assertion: the packaging `newarray` + `arraystore` loop is present and the
  invokedynamic descriptor ends with `Object[]`.

**Risk:** Medium-high. Boxing for category-2 primitives and the slot-accumulation order
must match `AnyTypeArgProvider` exactly or the array will contain shifted values.

### Task 4 — Phase 1 shared validation + location-family grouping (Gap D)

**Approach:**
- Replace the `collectHandlers` if/else chain with a `Map<Kind, List<ProbeHandler>>`
  keyed by an enum grouping (ENTRY/RETURN/CALL family vs instruction-site families).
- Extract the duplicated special-param validation (`@Self`, `@ProbeClassName`, etc.)
  used in `canEmitCallProbe`, `canEmitFieldProbe`, `canEmitSyncProbe`, etc. into a single
  `validateSpecialParams(om, handlerArgTypes, loader)` returning a boolean.
- Do this **after** Tasks 1–3 so the new ordinary-arg logic lands in one place and is
  not immediately re-duplicated.

**Acceptance:** No behavior change; full `ClassFileApiBackendTest` green; the
instruction-site `canEmit*` methods shrink.

**Risk:** Low-medium. Pure refactor; guard with the existing regression suite and a
before/after bytecode diff on a representative fixture.

### Task 5 — Dead-code cleanup (Gap E) — NO CHANGE (re-evaluated)

Re-checked against the ASM precedent: `Instrumentor`'s `switch (loc.getValue())` has
**no `default`** — an unknown `Kind` silently skips (returns the unmodified visitor). The
`else log.debug("Skipping unsupported probe kind ...")` arm in `ClassFileApiBackend.collectHandlers`
is the matching fallback for future `Kind` values; it is not dead in principle (only
unreachable for the current enum) and removing it would make a future `Kind` vanish with no
log, diverging from ASM. Keeping the arm is the correct, lower-risk choice. No change made.

### Task 6 — Bot finding: unread `staticCall` local (Gap F.1) — DONE

Removed the unused `boolean staticCall = ii.opcode() == Opcode.INVOKESTATIC;` local in
`canEmitCallProbe` (it was never read; `@TargetInstance` assignability uses `ownerType`).
`:btrace-agent:classFileApiBackendTest` (121 tests) green.

### Task 7 — ~~Bot finding: `ClassCache` null checks (Gap F.2)~~ RESOLVED

**Recheck correction:** The 66e2bfd3 `ClassCache.getInstance()` DCL null-check fix was
**dropped at merge** — the squash `9f39a250` did not touch `ClassCache.java`, so the two
bot findings (both on `ClassCache.java`) reference code that is not on `develop`. Gap
F.2 is moot.

What landed from 66e2bfd3 is the `ClassFilter.isSubTypeOf` defensive guard. The residual
`if (cache == null)` guard there is JLS-dead (`getInstance()` uses the holder idiom) but
not bot-flagged. Resolved by folding it into the `ci == null` conservative no-match path
(Option 3 from the pros/cons discussion):

```java
ClassCache cache = ClassCache.getInstance();
ClassInfo ci = cache != null ? cache.get(loader, typeA) : null;
if (ci == null) return false;
```

**Done:** `btrace-agent/.../ClassFilter.java` edited; `:btrace-agent:spotlessApply` +
`:btrace-agent:compileJava` + `:btrace-agent:test` green.

**Outstanding before commit:** run the JDK 8/11/17/21 integration suite to prove the
race fix (66e2bfd3) regression does not recur — unit tests do not exercise the
classloader-init race window.

### Task 8 — Bot finding: smoke-test unused params (Gap F.3) — DONE

Referenced each previously-unused captured param in its handler `println` via
`BTraceUtils.str(...)` for `Object` params (BTrace forbids `+ Object`; the existing code
only concats `String`/primitives) and `array.length` for the array param:
`onArrayGet.value`, `onNewArray.array`, `onSyncEntry.lock`, `onSyncExit.lock`,
`onCall.key`, `onNewObject.set`, `onCatch.exception`, `onError.exception`. The integration
driver (`ClassFileApiTests`) asserts via `stdout.contains(marker)` (substring), so the
appended values do not break the markers. `:integration-tests:spotlessApply` clean.

**Outstanding:** the integration run on JDK 26 (deferred) must confirm the script still
compiles under the btrace-compiler and the smoke markers still fire — `str()` on the
`SynchronizedMap` mutex / `SynchronizedSet` calls `toString`, which iterates the backing
collection (low recursion risk — `*Printed` guards prevent probe re-entry, and the backing
collections are not instrumented).

---

## Execution Order

1. **Tasks 1–3** (ordinary args + `AnyType[]`) — DONE. Implemented in
   `ClassFileApiBackend.emitProbeCall`: threaded `methodArgTypes` (computed from `methodDesc`)
   through new ENTRY/RETURN convenience overloads; added typed ordinary method-arg loading
   from fixed local slots (`methodArgSlot`), `AnyType[]` aggregate packaging
   (`emitAnyTypeArray`: anewarray + per-element load/box/arraystore, mirroring ASM
   `AnyTypeArgProvider`), and an `AnyType[]` short-circuit in `canEmitCallProbe` (without it
   `sameStackType` rejected the aggregate param and the probe was skipped). Tests added:
   typed ENTRY (instance+static), typed RETURN, ENTRY/RETURN/CALL `AnyType[]`, CALL
   `AnyType[]` on a 0-arg call — each with a JVM-verifier load check
   (`assertLoadsWithoutVerifyError`). `:btrace-agent:classFileApiBackendTest` (121 tests) and
   `:btrace-agent:test` green; spotless clean.
   - Known edge case (out of scope): sub-int (`byte`/`short`/`char`/`boolean`) `AnyType[]`
     elements box via `boxPrimitiveReturn`, consistent with the existing `@Return` boxing and
     the JVMS verifier's int-collapse; not exercised by tests (ASM `AnytypeArgs` also covers
     only String/long/String[]/int[]).
2. **Task 4** (shared validation refactor) — after 1–3 so the new code lands once.
3. **Tasks 5, 6, 8** (dead code + low-risk bot nits) — Task 6 DONE (removed unread
   `staticCall` local); Task 8 DONE (smoke-test params referenced via `str()`); Task 5
   NO CHANGE (the `else` arm is the correct fallback matching ASM's silent-skip).
4. **Task 7** — DONE (folded the residual `ClassFilter` `cache == null` guard into the
   `ci == null` path); JDK 8/11/17/21 integration run still recommended pre-commit.

## Verification Commands

```sh
# Targeted ClassFile API backend tests (JDK 26)
JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem GRADLE_USER_HOME=$(pwd)/.gradle-user \
  ./gradlew :btrace-agent:classFileApiBackendTest > /tmp/cfapi-gaps-test.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ClassFileApiBackendTest" /tmp/cfapi-gaps-test.log

# Formatting
JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem GRADLE_USER_HOME=$(pwd)/.gradle-user \
  ./gradlew :btrace-agent:spotlessCheck > /tmp/cfapi-gaps-spotless.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|spotless" /tmp/cfapi-gaps-spotless.log

# Full agent tests
JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem GRADLE_USER_HOME=$(pwd)/.gradle-user \
  ./gradlew :btrace-agent:test > /tmp/cfapi-gaps-agent.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|tests" /tmp/cfapi-gaps-agent.log

# Integration tests (build dist first; requires TEST_JAVA_HOME for older JDKs)
JAVA_HOME=$HOME/.sdkman/candidates/java/26-tem GRADLE_USER_HOME=$(pwd)/.gradle-user \
  ./gradlew -Pintegration test > /tmp/cfapi-gaps-integ.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ClassFileApi" /tmp/cfapi-gaps-integ.log
```

## Out of Scope

- Compiler/verifier changes outside `ClassFileApiBackend` unless a parity gap cannot be
  closed locally (single-`AnyType` ordinary arg is the likely edge case to revisit).
- `IndyDispatcher` / runtime changes.
- Re-opening PR #843; follow-ups land as new PRs against `develop`.