# Issue #931 — Phase 3 implementation plan: target-type signatures and chains

**Design input:** `internal/specs/2026-08-07-issue-931-phase-3-target-type-signatures.md` (CLEAN)
**Baseline:** `a6a7ed57` on `issue-931-target-type-signatures`
**Scope:** Phase 3 only. Add direct target-type lookup metadata and opaque chained calls without changing Phase 1/2 loader policies or beginning overload, field, constructor, cast, predicate, private-access, or JPMS work.

## Fixed implementation decisions

- Add source-retained `@ExternalType.Type(OtherContract.class)` with `{METHOD, PARAMETER}` targets. It is valid only as a direct `Object` method-return or formal-parameter declaration annotation of an adapted interface method. The Java source form remains `@Type(Other.class) Object child()` / `void set(@Type(Other.class) Object child)`; the referenced contract must itself be a non-empty `@ExternalType` interface.
- Keep generated public Java boundaries and JVM descriptors `Object`. The referenced contract is processor-only metadata: generated adapters must not use its `.class`, name it in a public signature/field, or cast target values to it.
- Resolve marked target classes only by `Class.forName(fqn, false, owner.getClassLoader())`, after resolving the owner and before the exact `MethodType`/public lookup. Do not use TCCL, a Phase 2 explicit loader, an extension loader, or fallback search for a marked type.
- Preserve Phase 2 static forms and shared cache exactly. A legacy or explicit static invocation first resolves its owner with its existing policy; marked types then resolve through that owner’s defining loader. No second type cache, FQN-only map, strong loader key, or Type runtime helper is permitted.
- Claim `ExternalType.Type` in addition to outer `ExternalType` and inspect every `METHOD`/`PARAMETER` declaration marker returned by `RoundEnvironment`. Every invalid/unconsumed declaration marker produces exactly one source-positioned diagnostic; a valid direct marker produces no duplicate diagnostic. Generic/array-component/local/cast/new type-use misuse is a javac applicability error, and duplicate annotations are javac's native non-repeatable error.

## Ordered, gated work

- [ ] **1. Establish the bounded baseline.**

  Preserve the untracked design specification and unrelated files in `/private/tmp/btrace-issue-931-phase3`. Read the clean design, `AGENTS.md`, `docs/architecture/MaskedJarArchitecture.md`, Phase 2 `AdapterEmitter`, `ExternalTypeProcessor`, `MethodSpec`, `CompileTestHarness`, staged integration fixtures, and existing external processor smoke resources before edits.

  Explicitly exclude array/generic-element markers, target types in fields/constructors/casts/predicates, overload selectors, wrappers/proxies, `MethodHandleCache` changes, target classpath dependencies, TCCL mutation/fallback, private lookup, module alteration, and Gradle DSL work.

  **Stop:** If an implementation would expose a target-library or contract-interface class in a generated public descriptor, needs a wrapper, or cannot preserve every Phase 2 static method descriptor, return to design review.

- [ ] **2. Add the Java-8 source annotation and processor support.**

  Update `btrace-core/src/main/java/io/btrace/core/extensions/ExternalType.java`:

  - add public nested `ExternalType.Type` with `@Retention(RetentionPolicy.SOURCE)`, `@Target({ElementType.METHOD, ElementType.PARAMETER})`, and `Class<?> value()`;
  - document that it marks a direct `Object` signature position with another `@ExternalType` contract, does not make the runtime value implement that interface, and is consumed only by the processor;
  - retain outer `ExternalType` and `ExternalType.Static` behaviour/retention unchanged.

  Update `btrace-core/src/main/java/io/btrace/extension/processor/ExternalTypeProcessor.java` to support both canonical annotation names through `@SupportedAnnotationTypes`. Do not alter Gradle processor registration. Use `AnnotationMirror`/`AnnotationValue` plus `TypeMirror`, not reflective access to `Class<?>` annotation values.

  **Gate:** A source using only an invalid `@ExternalType.Type` causes this processor to run and reports its own diagnostic; an external source can compile a valid marker once the published masked JAR is rebuilt.

- [ ] **3. Implement complete marker discovery and validation before adapter emission.**

  In `ExternalTypeProcessor.java`, introduce focused private declaration-marker helpers rather than widening the public API. The process flow must:

  1. Discover candidates from `roundEnv.getElementsAnnotatedWith(ExternalType.Type.class)`. This is exhaustive under the chosen `METHOD`/`PARAMETER` targets; body-local/cast/new type-use attempts are rejected by javac before processor handling.
  2. While building a valid outer-`ExternalType` method spec, consume a marker only when its annotated `ExecutableElement` return or `VariableElement` formal parameter has direct erased `java.lang.Object` type. Decode the class-valued marker element to a `TypeMirror`; verify interface kind and non-empty outer `ExternalType.value`; retain the referenced target FQN and source position.
  3. After all adapted methods are processed, emit one clear, source-positioned error for each marker not consumed: outside an adapted interface, on a skipped default/static method, or on a non-`Object` return/parameter declaration. Avoid a second catch-all error for a marker already consumed or directly diagnosed.
  4. Let javac diagnose repeated `Type` annotations as non-repeatable. Let javac reject generic-argument, array-component, local, cast, and `new` type-use placements because `Type` is not applicable there. Retain existing duplicate-method-name overload diagnostics and ordinary generic-erasure behaviour.
  5. If any processor-owned marker error makes an interface invalid, do not emit that interface’s adapter.

  The diagnostics must tell an author the permitted form, `@ExternalType.Type(OtherContract.class) Object`, and identify why the marker is invalid. Do not silently ignore unsupported placements.

  **Gate:** Valid direct markers are consumed exactly once and stay diagnostic-free; malformed declarations have one processor diagnostic/no partial adapter; repeated/type-use misuse fixtures assert javac's native diagnostics rather than a processor duplicate diagnostic.

- [ ] **4. Carry lookup metadata through emission without changing public adapter types.**

  Refactor `btrace-core/src/main/java/io/btrace/extension/processor/MethodSpec.java` to model each return/parameter position as an emitted Java erased type plus lookup metadata: either the existing literal-ready erased type or a target FQN sourced from `ExternalType.Type`. Update `AdapterSpec.java` only if a small immutable type-position model needs placement there; preserve its adapter name/package behaviour.

  Update `AdapterEmitter.java` so `methodTypeLiteral` becomes owner-aware generated construction for marked positions. In each existing `ClassValue<ResolvedCall>.computeValue`:

  - retain the current owner resolution policy, then load each marked target FQN with `Class.forName(fqn, false, owner.getClassLoader())`;
  - build the exact `MethodType` using that resolved class for marked return/parameter positions and existing `.class` literals for all other positions;
  - preserve `findVirtual`/`findStatic`, `publicLookup`, resolution-attempt counter, exception wrapping, `sneak`, and target invocation behaviour;
  - emit `Object` for every marked dispatcher return/argument and invoke the handle with those opaque values. It must not emit `OtherContract.class`, `target.package.Type.class`, a contract cast, or a wrapper.

  For static members, leave `$resolveStatic(ClassLoader)`, `$legacyStaticLoader()`, null checking, monitor, weak loader index, and successful-only insertion intact. The shared resolver obtains the owner first; its owner-keyed `ClassValue` computation resolves target signature types through `owner.getClassLoader()`. For virtual members, preserve the existing receiver-keyed `ClassValue` and resolve types from the resolved owner rather than independently from the receiver/TCCL.

  **Gate:** Existing unmarked generated source remains semantically identical; a marked generated source visibly declares `Object` boundary types but contains owner-loader `Class.forName` lookup for the target FQN. The only new published source API is the nested annotation.

- [ ] **5. Add focused processor, runtime, failure, and isolation coverage.**

  Extend `btrace-core/src/test/java/io/btrace/core/extensions/ExternalTypeAnnotationTest.java` for `Type` retention/target/value, and extend `btrace-core/src/test/java/io/btrace/extension/processor/ExternalTypeProcessorTest.java`. Extend `CompileTestHarness.java` only when an in-memory class-byte/loader fixture cannot express a case. Keep current Phase 1/2 tests intact.

  Add fixtures/tests for:

  1. **Source and API shape:** parent/child contracts with marked direct return and parameter positions compile. Generated declarations/reflection are `Object`-based; generated source contains no `ChildApi.class`/`ChildApi` signature and no target `Child.class` literal, while its generated `MethodType` resolves the target FQN. The normal `Object` contract remains an exact `Object` lookup.
  2. **Validation scan:** invalid non-contract reference, empty target contract, marker outside an adapted contract, marker on a default/static interface method, and non-`Object` declaration each fail with one useful processor diagnostic and no generated adapter. A valid direct marker must compile without a duplicate processor diagnostic. Separately assert javac's native non-repeatable diagnostic for duplicate markers and native annotation-target applicability diagnostics for generic-argument, array-component, local, cast, and `new` type-use misuse. Retain the current duplicate-method-name error.
  3. **Virtual chain:** an isolated parent returns a child and accepts a child; `ParentApi$Ext.child(parent)` feeds `ChildApi$Ext.label(child)` and `ParentApi$Ext.replaceChild(parent, child)`. The target classes exist only in the in-memory application loader, not as extension compile-time types.
  4. **Same-name loader identity:** two hostile isolated loaders define identical parent/child FQNs but return loader-specific markers. Each chain succeeds only within its own loader; passing loader-B child to loader-A parent fails transparently. Hostile `equals`/`hashCode` must never be called.
  5. **Owner-defining-loader type selection:** a selected static child loader delegates parent owner resolution to a parent loader while exposing a conflicting same-FQN child itself. Both legacy-TCCL and explicit-loader forms must resolve the signature child through the owner parent loader. Assert the target member succeeds only with that identity and the explicit control loader is absent from generated `MethodType`/invoke arguments.
  6. **Null and retry:** a marked return null propagates; a marked null parameter reaches the target after signature resolution; using null as a virtual chained receiver preserves the current NPE. A mutable loader initially hides the marked child class, producing `ExternalTypeResolutionException` with `ClassNotFoundException`, then exposes it and succeeds on the same loader with no negative cache. Preserve missing-member/access and target-thrown-exception tests.
  7. **Regression/negative evidence:** malformed marker fixtures must be run as assertions, not merely compiled as incidental sources. Record that the new direct-marker runtime test cannot pass against the pre-Phase-3 processor/emitter, and ensure every new positive case has a rejected input, wrong-loader, missing-type, or cross-loader counterexample that proves the check can fail.

  **Stop:** Do not relax a failing test by accepting `ChildApi` in an adapter signature, looking up a target type through a different loader, caching a failed type resolution, or wrapping a target object.

- [ ] **6. Add the real staged extension/client/agent/target chain.**

  Preserve `integration-tests/build.gradle` staging and its release-isolation assertion. Modify only the relevant fixtures:

  - add `integration-tests/src/test/java/resources/ExternalChild.java` as the application-only child target;
  - extend `integration-tests/src/test/java/resources/ExternalData.java` with a public method returning that child and, if needed, a public method accepting it to exercise an annotated parameter;
  - extend `btrace-extensions/btrace-ext-test/src/main/java/io/btrace/test/ext/ExternalDataType.java` with `@ExternalType.Type(ExternalChildType.class) Object child()` (and marked direct parameter only if used);
  - add `ExternalChildType.java` under the same extension source directory, annotated for the child target and exposing its public virtual marker method;
  - extend `ExternalTypeTestService.java` and `ExternalTypeTestServiceImpl.java` with a method that chains the two generated adapters while accepting/returning only `Object` at the service boundary;
  - extend `integration-tests/src/test/btrace/ExternalTypeAdapterTest.java` and `ExternalTypeAdapterIntegrationTest.java` to emit, wait for, and assert the target-owned chained marker.

  Keep the current `ExternalTypeAdapterExplicitTest.java` and explicit-loader integration test independent: its deterministic decoy TCCL continues to test Phase 2 only. Keep the legacy static/virtual scenario’s current `tag` and `value` markers while adding the chain marker to that scenario, rather than mixing it with the explicit TCCL assumptions.

  **Gate:** The external child is absent from the extension compile classpath; the real staged extension, client, agent, target JVM, instrumentation, and protocol produce the nested marker; `btrace-ext-test` remains absent from release extensions.

- [ ] **7. Make the masked-JAR proof genuinely two-stage.**

  Replace the flat inputs beneath `btrace-dist/src/test/resources/external-type-processor-smoke/` with deliberately separated resource trees, for example:

  - `target-src/example/target/ExternalParent.java` and `ExternalChild.java`, compiled into runtime-only classes without BTrace;
  - `extension-src/example/ParentApi.java`, `ChildApi.java`, and `ExternalTypeSmoke.java`, compiled in a separate invocation with no target sources/classes on its classpath.

  The driver must call a generated static or virtual parent adapter and then a child adapter, printing a stable chain marker. The extension source declares only `Object` at marked positions and references only the contract interface in `@ExternalType.Type`; it must never import a target source type.

  After clean `:btrace-dist:btraceJar`, inspect the one versioned `btrace.jar` for root-visible `ExternalType.class`, `ExternalType$Type.class`, `ExternalTypeResolutionException.class`, `ExternalTypeProcessor.class`, processor support, and the processor service entry. Then:

  1. compile only `target-src` with `javac --release 8` into `$SMOKE/target-classes`, with no BTrace dependency;
  2. compile only `extension-src` with `javac --release 8` into `$SMOKE/extension-classes` and generated source using the masked JAR as both `-cp` and `-processorpath`; target source/class paths must be absent;
  3. assert generated `ParentApi$Ext.java`/`ChildApi$Ext.java` and classes exist, their public declarations use `Object`, and generated source does not contain `ChildApi.class`, `ParentApi.class`, `example.target.ExternalChild.class`, or `example.target.ExternalParent.class`;
  4. run with exactly extension classes, target classes, and masked JAR, then assert the stable marker.

  Do not alter `btrace-dist/build.gradle` or package a test extension unless inspection proves a particular annotation/service/processor class is missing. Rebuild clean and repeat every inspection/compile/run step after any narrow packaging correction.

  **Stop:** If target sources/classes leak into the contracts compilation classpath, if the proof uses `btrace-core` output/extension implementation JAR, or if it needs broad masked-JAR exposure, stop for distribution compatibility review.

- [ ] **8. Consolidate the user documentation.**

  Update `docs/BTraceExtensionDevelopmentGuide.md` as the normative contract. Add the exact `@ExternalType.Type(ChildApi.class) Object` example, explain direct-position-only validation, opaque `Object` public boundary, owner-defining-loader resolution, nesting/chaining, null behaviour, same-name loader identity, retry/failure contract, and the continuing Phase 2 legacy/explicit static distinction. State that generic elements/arrays and later operation families remain unsupported; preserve public-lookup/module rules.

  Update `docs/architecture/provided-style-extensions.md` to point to the canonical explanation and retain manual linking for unsupported generic/array, overload, fields/constructors, casts/predicates, and access needs. Do not duplicate the complete support table. Touch `BTraceTutorial.md` or `GettingStarted.md` only if a targeted search finds a contradictory target-type claim.

  **Gate:** Documentation never says that a target object implements its contract interface, never promises generic-element/array support, and contains no generated-adapter workaround that changes TCCL or guesses a loader.

- [ ] **9. Run verification in dependency order and review the release surface.**

  All Gradle commands run from `/private/tmp/btrace-issue-931-phase3` with a worktree-local cache, redirect before reading, and stop on nonzero exit, `BUILD FAILED`, a test/format failure, missing artifact entry, compiler diagnostic mismatch, or wrong integration marker. Build distribution before integration. On the documented address-selection failure only, rerun the affected command with `JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"`.

  ```bash
  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-core:test > /tmp/btrace-issue-931-phase3-core-test.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ExternalType" /tmp/btrace-issue-931-phase3-core-test.log

  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-core:spotlessCheck > /tmp/btrace-issue-931-phase3-core-spotless.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase3-core-spotless.log

  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew clean :btrace-dist:btraceJar > /tmp/btrace-issue-931-phase3-masked-jar.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|btraceJar" /tmp/btrace-issue-931-phase3-masked-jar.log

  rg --files btrace-dist/build/resources/main | rg '^btrace-dist/build/resources/main/v[^/]+/libs/btrace\.jar$' > /tmp/btrace-issue-931-phase3-jar-path.log
  test "$(rg -c '^' /tmp/btrace-issue-931-phase3-jar-path.log)" = 1
  BTRACE_931_PHASE3_JAR="$(rg -x 'btrace-dist/build/resources/main/v[^/]+/libs/btrace\.jar' /tmp/btrace-issue-931-phase3-jar-path.log)"
  jar tf "$BTRACE_931_PHASE3_JAR" > /tmp/btrace-issue-931-phase3-jar-entries.log
  rg -n 'io/btrace/core/extensions/ExternalType(\$Type|ResolutionException)?\.class|io/btrace/extension/processor/ExternalTypeProcessor\.class|io/btrace/extension/processor/|META-INF/services/javax.annotation.processing.Processor' /tmp/btrace-issue-931-phase3-jar-entries.log
  unzip -p "$BTRACE_931_PHASE3_JAR" META-INF/services/javax.annotation.processing.Processor > /tmp/btrace-issue-931-phase3-processor-service.log
  rg -n '^io\.btrace\.extension\.processor\.ExternalTypeProcessor$' /tmp/btrace-issue-931-phase3-processor-service.log

  BTRACE_931_PHASE3_SMOKE=$(mktemp -d /tmp/btrace-issue-931-phase3-smoke.XXXXXX)
  mkdir -p "$BTRACE_931_PHASE3_SMOKE/target-classes" "$BTRACE_931_PHASE3_SMOKE/extension-classes" "$BTRACE_931_PHASE3_SMOKE/generated"
  javac --release 8 -d "$BTRACE_931_PHASE3_SMOKE/target-classes" btrace-dist/src/test/resources/external-type-processor-smoke/target-src/example/target/*.java > /tmp/btrace-issue-931-phase3-target-javac.log 2>&1
  rg -n "error:|warning:|External" /tmp/btrace-issue-931-phase3-target-javac.log || true
  javac --release 8 -cp "$BTRACE_931_PHASE3_JAR" -processorpath "$BTRACE_931_PHASE3_JAR" -d "$BTRACE_931_PHASE3_SMOKE/extension-classes" -s "$BTRACE_931_PHASE3_SMOKE/generated" btrace-dist/src/test/resources/external-type-processor-smoke/extension-src/example/*.java > /tmp/btrace-issue-931-phase3-extension-javac.log 2>&1
  rg -n "error:|warning:|ExternalType|ParentApi|ChildApi" /tmp/btrace-issue-931-phase3-extension-javac.log || true
  test -f "$BTRACE_931_PHASE3_SMOKE/generated/example/ParentApi\$Ext.java"
  test -f "$BTRACE_931_PHASE3_SMOKE/generated/example/ChildApi\$Ext.java"
  test -f "$BTRACE_931_PHASE3_SMOKE/extension-classes/example/ParentApi\$Ext.class"
  test -f "$BTRACE_931_PHASE3_SMOKE/extension-classes/example/ChildApi\$Ext.class"
  rg -n "public static java\.lang\.Object child\(java\.lang\.Object self\)|public static java\.lang\.String label\(java\.lang\.Object self\)" "$BTRACE_931_PHASE3_SMOKE/generated/example/ParentApi\$Ext.java" "$BTRACE_931_PHASE3_SMOKE/generated/example/ChildApi\$Ext.java"
  ! rg -n "ChildApi\.class|ParentApi\.class|example\.target\.ExternalChild\.class|example\.target\.ExternalParent\.class" "$BTRACE_931_PHASE3_SMOKE/generated"
  java -cp "$BTRACE_931_PHASE3_SMOKE/extension-classes:$BTRACE_931_PHASE3_SMOKE/target-classes:$BTRACE_931_PHASE3_JAR" example.ExternalTypeSmoke > /tmp/btrace-issue-931-phase3-external-run.log 2>&1
  rg -n '^external-type-chain-ok$' /tmp/btrace-issue-931-phase3-external-run.log

  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build > /tmp/btrace-issue-931-phase3-dist-build.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Test" /tmp/btrace-issue-931-phase3-dist-build.log

  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-extensions:btrace-ext-test:spotlessCheck > /tmp/btrace-issue-931-phase3-ext-spotless.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase3-ext-spotless.log

  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:spotlessCheck > /tmp/btrace-issue-931-phase3-integration-spotless.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase3-integration-spotless.log

  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:test -Pintegration --tests tests.ExternalTypeAdapterIntegrationTest > /tmp/btrace-issue-931-phase3-integration.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ExternalTypeAdapterIntegrationTest|timed out" /tmp/btrace-issue-931-phase3-integration.log

  git diff --check
  git status --short
  ```

  Confirm the intended negative evidence before claiming completion: malformed marker test inputs fail with their exact diagnostics, mismatched loader child inputs fail, hidden marked types retry only after availability, and the two-stage smoke fails if target classes are added neither to run classpath nor to the extension compiler path. Do not commit until every required gate passes and the user authorizes it.

## Completion criteria

Phase 3 is complete only when `ExternalType.Type` provides the direct-`Object` declaration contract; every supported declaration marker is consumed once or diagnosed once, while javac rejects inapplicable type-use/repeated forms; exact type classes resolve from the already-resolved owner’s defining loader; generated public APIs remain opaque and binary-compatible; chains, same-name isolated loaders, nulls, retry/failure semantics, and Phase 2 static forms pass; the staged real client-agent-target flow proves a nested target-only chain; and a genuinely separated masked-JAR smoke compiles contracts without target sources/classes using Java 8, then runs them only with target runtime classes. No Phase 4+ capability is implied.
