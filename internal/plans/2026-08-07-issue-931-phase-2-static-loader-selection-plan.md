# Issue #931 — Phase 2 implementation plan: explicit static `@ExternalType` loader selection

**Design input:** `internal/specs/2026-08-07-issue-931-phase-2-static-loader-selection.md` (CLEAN)
**Prerequisite:** #941 / Phase 1 is merged on `develop`.
**Scope:** Phase 2 only. Add an explicit leading-`ClassLoader` overload to generated dispatchers for `@ExternalType.Static` methods without changing the legacy dispatcher’s descriptor or behaviour.

## Fixed decisions and boundaries

- The generated class, not the annotated interface, is the control boundary. Each non-overloaded static adapted member has both `name(targetParameters...)` and `name(ClassLoader applicationLoader, targetParameters...)`. The leading value selects only the class-loading namespace; it is never included in `MethodType` or passed to `MethodHandle.invoke`.
- `null` is rejected immediately with `NullPointerException("applicationLoader")`. It does not mean bootstrap, system, or TCCL, and it must not reach the static cache or `Class.forName`.
- The old no-loader dispatcher still reads TCCL and substitutes `ClassLoader.getSystemClassLoader()` only for null TCCL. Neither form calls `Thread.setContextClassLoader`.
- The existing Phase 1 per-member weak-identity loader index, `ClassValue<ResolvedCall>`, monitor, queue, sentinels, successful-only insertion, and resolution exception remain the sole static-resolution machinery. Do not introduce another cache, helper runtime type, annotation, processor option, Gradle DSL, or bootstrap-visible API.
- Keep `ExternalTypeProcessor`’s method-name overload rejection unchanged. It prevents collisions such as an interface declaring both `call()` and `call(ClassLoader)`, which would otherwise collide with the generated explicit overload. Do not use this phase to add overload selection; document the generated-overload wildcard-static-import caveat instead.
- Preserve public-lookup-only access, exact erased signatures, error wrapping, retry behaviour, virtual dispatch, and target-thrown exception transparency. No JPMS/private-access work, type chaining, fields, constructors, casts, predicates, fallback searching, or loader guessing belongs here.

## Ordered, gated work

- [ ] **1. Establish the Phase 2 baseline.**

  In `/private/tmp/btrace-issue-931-phase2`, preserve the untracked clean specification and all unrelated state. Read the spec, `docs/architecture/MaskedJarArchitecture.md`, the Phase 1 emitter (`btrace-core/src/main/java/io/btrace/extension/processor/AdapterEmitter.java`), `ExternalTypeProcessorTest`, the `btrace-ext-test` fixture, and the isolated `stageExternalTypeClientHome` path before editing.

  **Stop:** If #941 is not actually the `develop` baseline in this worktree, or preserving the old generated no-loader method descriptor proves impossible, stop and return to design review rather than adapting Phase 2 to a different base.

- [ ] **2. Refactor the generated static resolver once, while retaining the legacy entry point.**

  Change only `btrace-core/src/main/java/io/btrace/extension/processor/AdapterEmitter.java` for generated runtime behaviour. Keep the generated imports Java-8 compatible.

  For each static `MethodSpec`, retain the existing per-member cache declarations and make the following emitter-only refactor:

  1. Emit one shared resolver taking a non-null `ClassLoader`, conceptually `$<ordinal>$resolveStatic(ClassLoader loader)`. Move the existing synchronized expunge, weak-identity lookup-key creation, loader-index lookup, `Class.forName(OWNER, false, loader)`, `ClassValue.get(owner)`, and post-success weak-index insertion into it unchanged in order. It catches only `ClassNotFoundException` from owner resolution and wraps it in `ExternalTypeResolutionException`; `ClassValue` continues to wrap lookup `NoSuchMethodException`/`IllegalAccessException`.
  2. Emit a small legacy-loader selector, conceptually `$<ordinal>$legacyStaticLoader()`, which reads TCCL and substitutes the system loader only when TCCL is null. The old public dispatcher calls the shared resolver with that selected loader. Its generated method signature and target `MethodType`/invoke argument list stay byte-for-byte equivalent in meaning to Phase 1.
  3. Emit a second public static method of the same name with `ClassLoader applicationLoader` prepended to the original target parameter list. Its first statement is `if (applicationLoader == null) throw new NullPointerException("applicationLoader");`; place this before the `try`, resolver invocation, and any generated cache access. It calls the shared resolver with exactly that supplied loader, then invokes the resolved handle with only the original parameter variables.
  4. Extend the emitter’s parameter/argument-list generation (or add a dedicated static-list helper) so the control parameter appears only in the explicit Java declaration. It must never enter `methodTypeLiteral(m)` or the handle invocation argument list. Preserve virtual method emission exactly.
  5. Keep each static member’s monitor as the single serialization boundary. Both generated overloads must share it and the same `loaderIndex`/`ClassValue`; a legacy call and explicit call choosing the same loader must retrieve the same resolved handle. Insert into the index only after both class and member resolution succeed. Do not make a null key reachable through the explicit form; existing sentinel support remains valid for generated machinery but legacy normalizes null TCCL to system.

  Keep `btrace-core/src/main/java/io/btrace/extension/processor/ExternalTypeProcessor.java`’s existing name-only duplicate detection. Add no special case for a real target `ClassLoader` argument: an interface declaration `install(ClassLoader targetArgument)` generates legacy `install(ClassLoader)` and explicit `install(ClassLoader applicationLoader, ClassLoader targetArgument)`.

  **Gate:** The generated static adapter has exactly two public dispatch descriptors per supported static source method, the legacy descriptor remains present, and the only new generated public surface is the leading-loader overload. A source declaration whose name/arity would conflict stays a processor error under the established overloaded-method rule.

- [ ] **3. Add compile-shape and unit-level runtime coverage in `btrace-core`.**

  Extend `btrace-core/src/test/java/io/btrace/extension/processor/ExternalTypeProcessorTest.java`; extend `CompileTestHarness.java` only if its in-memory class-byte support cannot express the fixture. Keep the Phase 1 counter private and read it reflectively as the current tests do. Reuse/extend the hostile `MutableTargetLoader` so `equals` and `hashCode` throw, and add explicit invocation helpers rather than weakening the old TCCL helpers.

  Add focused tests for all of the following:

  1. **Generated source shape and collision controls.** For a static method with ordinary parameters, assert both public static declarations exist; assert `MethodType.methodType` contains only the target return/parameter types and `call.handle.invoke(...)` contains only target arguments. Retain static methods’ no-`self` assertion. Compile the existing duplicate-name/overload fixture (including a `ClassLoader`-arity variant) and assert the existing processor diagnostic still rejects it, proving no generated descriptor collision is silently accepted.
  2. **Wrong ambient loader versus explicit selected loader.** Define the target only in a hostile isolated loader and set TCCL to a different loader that cannot load it. The legacy dispatcher must throw the established `ExternalTypeResolutionException` with `ClassNotFoundException`; the explicit dispatcher called with the target loader must return that loader’s marker. Assert the caller’s TCCL remains the wrong loader after the explicit call.
  3. **Identity, isolation, and sharing.** Two hostile isolated loaders defining the same owner FQN return different loader-specific results through explicit calls. Repeated explicit calls resolve once per loader. Then make a legacy call with TCCL equal to one of those loaders and prove it reuses that member’s existing successful resolution (no additional target load/member-resolution count). This both proves `==` cache keys and a shared cache, rather than two overload-specific caches.
  4. **Mixed-form concurrency.** Start concurrent first calls for one static member—one uses the legacy form under a selected TCCL and one uses the explicit form with that exact loader—behind a latch. Assert one target class load and one successful member-resolution counter increment. Repeat with distinct isolated loaders and assert independent values/handles, never a cross-loader result.
  5. **Null and bootstrap cases.** Invoke the explicit overload with null and assert a `NullPointerException` with `applicationLoader` before the mutable loader’s class-load count or the private resolution-attempt count changes. Preserve the Phase 1 legacy null-TCCL/system-loader fallback test. Add an explicit `java.lang.System.currentTimeMillis` call using `ClassLoader.getSystemClassLoader()` and assert success; its test/documentation companion must show authors normalize a null `ClassLoadingUtil.definingLoader(bootstrapObject)` result to that system loader rather than pass null.
  6. **A real target `ClassLoader` parameter.** Compile and run an adapted static target such as `install(ClassLoader targetArgument)`. Reflectively find both generated forms, call the one-argument legacy form and two-argument explicit form, and assert the target receives the target argument—not the loader-selection argument. This is the positive descriptor/forwarding proof.
  7. **Failure and retry preservation for the explicit form.** A mutable selected loader initially missing the owner must yield the established resolution exception without an index/counter publication; after it exposes the bytes, the same explicit call succeeds and caches. Assert missing-member and inaccessible-public-lookup failures retain their original causes, and an exception thrown by the target (including `ExternalTypeResolutionException`) propagates unchanged. Do not add negative caching.

  Do not modify `MethodHandleCache` or use it as an oracle; it is outside this generated-adapter phase.

  **Stop:** If any test can pass only by changing TCCL, using an extension implementation loader, adding a fallback search, accepting null, or exposing a target-only type in a generated signature, stop and return to the design boundary.

- [ ] **4. Split the end-to-end test into separate legacy and explicit scenarios.**

  Keep `integration-tests/build.gradle`’s `stageExternalTypeClientHome` isolation and the check that `btrace-ext-test` is not a release extension. Update only the Phase 2 fixture surface:

  - `integration-tests/src/test/java/resources/Main.java`
  - `integration-tests/src/test/java/resources/ExternalData.java`
  - `btrace-extensions/btrace-ext-test/src/main/java/io/btrace/test/ext/ExternalDataType.java`
  - `btrace-extensions/btrace-ext-test/src/main/java/io/btrace/test/ext/ExternalTypeTestService.java`
  - `btrace-extensions/btrace-ext-test/src/main/java/io/btrace/test/ext/ExternalTypeTestServiceImpl.java`
  - `integration-tests/src/test/btrace/ExternalTypeAdapterTest.java`
  - `integration-tests/src/test/java/tests/ExternalTypeAdapterIntegrationTest.java`

  Preserve `resources.Main.probeExternal(ExternalData)` as the legacy scenario. Its normal application TCCL calls only the legacy static adapter and the existing virtual adapter, emitting the current `tag=ext-data-ok` and `value=42` markers.

  Add a separate `Main.probeExternalExplicit(ExternalData)` invocation path. Before calling this entry point, the target work loop selects a deterministic decoy, non-target TCCL and restores the old context afterward; that makes any accidental legacy fallback fail. Its separate BTrace handler must invoke only an explicit-loader service method. In the service implementation, derive the loader from the supplied `ExternalData` object with `ClassLoadingUtil.definingLoader(data)`, normalize a bootstrap-null result to `ClassLoader.getSystemClassLoader()`, then call `ExternalDataType$Ext.tag(applicationLoader)`. Do not use `ClassLoadingUtil.withTCCL`.

  Make `ExternalData.tag()` (or a dedicated explicit marker method with the matching `@ExternalType.Static` declaration) return a deterministic marker only when it observes the decoy TCCL still installed. The explicit test waits for and asserts that marker. This proves all four real pieces: the static owner was selected from the target object’s defining loader, the target method ran, the generated adapter did not mutate TCCL, and the real client-agent-target protocol path carried the result. It must not combine the legacy/virtual and explicit/decoy assumptions in one probe invocation or one completion condition.

  **Gate:** Two independently runnable `ExternalTypeAdapterIntegrationTest` test methods/scenarios each stage the test extension below the integration build home, prove their own markers, and leave release extensions untouched.

- [ ] **5. Extend the masked-artifact external compilation and run proof.**

  Use the published masked JAR only: `btrace-dist/build/resources/main/v<version>/libs/btrace.jar`, never a `btrace-core` class directory or sibling JAR. Update/add inputs beneath `btrace-dist/src/test/resources/external-type-processor-smoke/src/example/` so the external API has a static `@ExternalType` method and a small `main` driver that calls the generated explicit overload with its own non-null class loader. Include a minimal target class in the smoke sources which returns a stable marker.

  From a fresh temporary output directory, compile all those external sources using both `-cp` and `-processorpath` set only to the built `btrace.jar`; then run the driver with classpath `<smoke-classes>:<btrace.jar>` and assert the marker. Inspect the JAR first for root-visible `ExternalType.class`, `ExternalTypeResolutionException.class`, the exact `io/btrace/extension/processor/ExternalTypeProcessor.class` entry and its support package, and `META-INF/services/javax.annotation.processing.Processor`, including service content naming `ExternalTypeProcessor`.

  Do not alter `btrace-dist/build.gradle`, the plugin, or masked-JAR section rules unless this proof demonstrates a concrete package/service omission. If it does, make the minimal packaging change, rebuild clean, and repeat the whole inspection/compile/run proof. Do not add the smoke extension to release extensions.

  **Stop:** If external compilation or running needs an unmasked `btrace-core` artifact, an extension implementation JAR, or broad new masked-JAR exposure, stop for distribution compatibility review.

- [ ] **6. Update the canonical documentation without expanding later phases.**

  Update `docs/BTraceExtensionDevelopmentGuide.md` as the authoritative contract:

  - explain both generated static forms, the unchanged no-loader TCCL/system-fallback policy, and the explicit exact-loader policy;
  - document immediate null rejection, unchanged target TCCL, public-lookup/module limitations, successful-only retry, and shared weak-identity cache behaviour;
  - replace the `ClassLoadingUtil.withTCCL(... VersionApi$Ext.version())` workaround for generated static calls with a leading-loader example, including `ClassLoadingUtil.definingLoader(context)` and the bootstrap-null-to-system normalization;
  - retain `withTCCL` only for manual APIs;
  - state the regeneration/source compatibility caveat: newly generated overloads can make a wildcard static import ambiguous if another wildcard import contributes the same new arity; use a qualified adapter call or explicit single-member import.

  Update `docs/architecture/provided-style-extensions.md` to point to this canonical explanation and replace its generated-adapter TCCL-scoping workaround with the explicit-overload form. Do not duplicate a second support table. Leave `docs/BTraceTutorial.md` and `docs/GettingStarted.md` untouched unless a targeted search proves either independently tells users to use the old generated-adapter `withTCCL` workaround.

  **Gate:** A repository search finds no advice to scope TCCL merely to call a generated static adapter, while manual API examples still legitimately use `withTCCL`.

- [ ] **7. Run progressive verification, inspect release shape, and conclude Phase 2.**

  From `/private/tmp/btrace-issue-931-phase2`, redirect every Gradle invocation to a log before reading it and use the worktree-local cache. A nonzero exit, `BUILD FAILED`, test failure, format failure, unexpected masked-JAR entry, or wrong integration marker stops at the owning step. Build `btrace-dist` before integration. If a restricted environment hits address-selection failures, repeat the affected Gradle command with `JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"`.

  ```bash
  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-core:test > /tmp/btrace-issue-931-phase2-core-test.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ExternalType" /tmp/btrace-issue-931-phase2-core-test.log

  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-core:spotlessCheck > /tmp/btrace-issue-931-phase2-core-spotless.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase2-core-spotless.log

  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew clean :btrace-dist:btraceJar > /tmp/btrace-issue-931-phase2-masked-jar.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|btraceJar" /tmp/btrace-issue-931-phase2-masked-jar.log

  rg --files btrace-dist/build/resources/main | rg '^btrace-dist/build/resources/main/v[^/]+/libs/btrace\.jar$' > /tmp/btrace-issue-931-phase2-jar-path.log
  test "$(rg -c '^' /tmp/btrace-issue-931-phase2-jar-path.log)" = 1
  BTRACE_931_PHASE2_JAR="$(rg -x 'btrace-dist/build/resources/main/v[^/]+/libs/btrace\.jar' /tmp/btrace-issue-931-phase2-jar-path.log)"
  jar tf "$BTRACE_931_PHASE2_JAR" > /tmp/btrace-issue-931-phase2-jar-entries.log
  rg -n "io/btrace/core/extensions/ExternalType(ResolutionException)?\\.class|io/btrace/extension/processor/ExternalTypeProcessor\\.class|io/btrace/extension/processor/|META-INF/services/javax.annotation.processing.Processor" /tmp/btrace-issue-931-phase2-jar-entries.log
  unzip -p "$BTRACE_931_PHASE2_JAR" META-INF/services/javax.annotation.processing.Processor > /tmp/btrace-issue-931-phase2-processor-service.log
  rg -n '^io\.btrace\.extension\.processor\.ExternalTypeProcessor$' /tmp/btrace-issue-931-phase2-processor-service.log

  BTRACE_931_PHASE2_SMOKE=$(mktemp -d /tmp/btrace-issue-931-phase2-smoke.XXXXXX)
  mkdir -p "$BTRACE_931_PHASE2_SMOKE/classes" "$BTRACE_931_PHASE2_SMOKE/generated"
  javac -cp "$BTRACE_931_PHASE2_JAR" -processorpath "$BTRACE_931_PHASE2_JAR" -d "$BTRACE_931_PHASE2_SMOKE/classes" -s "$BTRACE_931_PHASE2_SMOKE/generated" btrace-dist/src/test/resources/external-type-processor-smoke/src/example/*.java > /tmp/btrace-issue-931-phase2-external-javac.log 2>&1
  rg -n "error:|warning:|ExternalType|ExternalApi" /tmp/btrace-issue-931-phase2-external-javac.log || true
  test -f "$BTRACE_931_PHASE2_SMOKE/generated/example/ExternalApi\$Ext.java"
  test -f "$BTRACE_931_PHASE2_SMOKE/classes/example/ExternalApi\$Ext.class"
  java -cp "$BTRACE_931_PHASE2_SMOKE/classes:$BTRACE_931_PHASE2_JAR" example.ExternalTypeSmoke > /tmp/btrace-issue-931-phase2-external-run.log 2>&1
  rg -n '^external-type-explicit-ok$' /tmp/btrace-issue-931-phase2-external-run.log

  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build > /tmp/btrace-issue-931-phase2-dist-build.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Test" /tmp/btrace-issue-931-phase2-dist-build.log

  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-extensions:btrace-ext-test:spotlessCheck > /tmp/btrace-issue-931-phase2-ext-spotless.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase2-ext-spotless.log

  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:spotlessCheck > /tmp/btrace-issue-931-phase2-integration-spotless.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase2-integration-spotless.log

  GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:test -Pintegration --tests tests.ExternalTypeAdapterIntegrationTest > /tmp/btrace-issue-931-phase2-integration.log 2>&1
  rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ExternalTypeAdapterIntegrationTest|timed out" /tmp/btrace-issue-931-phase2-integration.log

  git diff --check
  git status --short
  ```

  Review the final diff against the clean specification: it must contain only the generated leading-loader static form, shared resolver/cache refactor, focused tests, external artifact proof resources, the split real integration scenarios, and canonical documentation. Do not commit until all gates pass and the user authorizes it.

## Completion criteria

Phase 2 is complete only when every newly generated static adapter has the explicit leading-`ClassLoader` dispatcher while every legacy dispatcher keeps its exact TCCL/system-fallback behaviour and descriptor; null is rejected before cache mutation; explicit and legacy forms share one weak-identity cache; loader isolation, retry, error causes, target-error transparency, real `ClassLoader` target arguments, bootstrap/system access, and mixed concurrency all pass focused tests; the masked published JAR alone compiles and runs an external explicit-overload extension; two distinct client-agent-target integration scenarios prove legacy and explicit behaviour without TCCL mutation; and the canonical docs replace the generated-adapter TCCL workaround. This phase does not authorize Phase 3+ capabilities.
