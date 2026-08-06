# Issue #931 — Phase 1 implementation plan: dependable `@ExternalType` baseline

**Design input:** `internal/specs/2026-08-06-issue-931-external-type-roadmap.md`
**Scope:** Phase 1 only. Preserve the annotation source shape and generated adapter method shapes. Do not start target-type signatures, overload selection, fields, constructors, casts, or JPMS/private access.

## Fixed implementation decisions

- Keep resolution and cache code self-contained in generated adapters; do not add a shared adapter runtime helper. The only new public runtime API is `io.btrace.core.extensions.ExternalTypeResolutionException`.
- Keep `MethodHandleCache` structurally unchanged. Its existing strong `Class<?>`/handle cache is a manual-utility lifecycle limitation, not a generated-adapter cache.
- Preserve virtual defining-loader lookup, static TCCL lookup, and null-TCCL system-loader fallback. Preserve target-thrown failures. Translate only class/member resolution failures.
- Do not change extension Gradle DSL/plugin IDs. Touch `btrace-gradle-plugin` only if the external artifact proof shows its existing `io.btrace:btrace` processor coordinate is insufficient.

## Ordered, gated work

- [ ] **1. Establish the baseline and keep Phase 1 bounded.**

  Preserve the untracked roadmap and unrelated changes. Read the roadmap, `docs/architecture/MaskedJarArchitecture.md`, the current processor/emitter, staging, and distribution rules before edits. Explicitly exclude target-type annotations, overload selectors, field/constructor/cast operations, `privateLookupIn`, module flags, extension loading/classpath injection, and Gradle DSL work.

  **Stop:** If source compatibility of the current annotated interface or generated public static method shape cannot be retained, return to design review.

- [ ] **2. Add the exact public resolution-failure contract in `btrace-core`.**

  Add `btrace-core/src/main/java/io/btrace/core/extensions/ExternalTypeResolutionException.java` as a final public `IllegalStateException`. Its narrow constructor accepts owner FQN, member, and original cause; its message is exactly `Unable to resolve @ExternalType <owner>#<member>`, retaining the original `ClassNotFoundException`, `NoSuchMethodException`, or `IllegalAccessException`.

  Update `btrace-core/src/main/java/io/btrace/core/extensions/ExternalType.java` Javadoc for the exact-erased/public-lookup boundary and link this exception. Do not expose a generic reflection API or a checked adapter signature.

  **Gate:** Java-8 source compatibility; the exception is the only new public adapter runtime type.

- [ ] **3. Replace generated resolution/caching in `btrace-core/src/main/java/io/btrace/extension/processor/AdapterEmitter.java`.**

  Keep `ExternalTypeProcessor.java`, `AdapterSpec.java`, and `MethodSpec.java` validation, generated class names, overloaded-method rejection, and default/static-interface-method skipping. Change only emission/imports and small emitter-only support. For each adapted member, emit:

  - **Virtual:** a `ClassValue<ResolvedCall>` keyed by `self.getClass()`, whose computation loads the configured owner through that receiver class’s defining loader, resolves the exact erased `MethodType` through `MethodHandles.publicLookup().findVirtual`, and stores the resolved owner/handle pair. Sequential calls for one receiver class reuse the call; isolated receiver classes have independent values; the VM can release values with the key class. Do not promise virtual single-flight.
  - **Static:** a per-member monitor, `ReferenceQueue<ClassLoader>`, a `Map<LoaderKey, WeakReference<Class<?>>>`, fixed bootstrap/system sentinel keys, and a `ClassValue<ResolvedCall>` keyed by the resolved target class. A normal `LoaderKey` holds a weak loader reference, compares only `==`, and hashes with `System.identityHashCode`; it must never invoke a loader’s `equals` or `hashCode`. Under the monitor, expunge stale weak keys/values; select TCCL or system loader if null; check the weak loader index; on miss load the owner, get the `ClassValue` call, then—and only then—insert the weak loader-to-class index. The index never holds `ResolvedCall` or a strong loader. This serializes expunge, lookup, resolution, `ClassValue.get`, and insertion for static first use.
  - **Failure/invocation boundary:** only computations loading the class/finding the handle catch `ClassNotFoundException`, `NoSuchMethodException`, and `IllegalAccessException` and wrap the Step-2 exception. Keep `MethodHandle.invoke` outside that catch and retain the existing sneaky-throw bridge only for transparent invocation propagation. A target-thrown `ExternalTypeResolutionException` must pass through unchanged.
  - **Test seam:** emit a package-private static integer field per member, incremented immediately before its member lookup, solely to prove missing-member retry. Give it a deterministic reserved prefix plus the emitted member ordinal (for example, `__btraceExternalTypeResolutionAttempts$0`), so fields are unique even when an interface method name contains the prefix; fields and generated dispatch methods may share no conflicting declaration namespace. The processor test reads that known field with `getDeclaredField(...)`/`setAccessible(true)`, never as public adapter API. It is neither a cache mechanism nor part of the supported binary surface.

  **Gate:** successful resolution, not a failed lookup, is the only publication point. `Object` remains an exact signature type, not a coercion escape hatch.

- [ ] **4. Add focused `btrace-core` tests.**

  Extend `btrace-core/src/test/java/io/btrace/extension/processor/ExternalTypeProcessorTest.java`; extend `CompileTestHarness.java` only if needed for mutable/counting child loaders. Retain current compile-shape tests and add runnable fixtures for:

  1. Sequential virtual calls through two loaders defining the same owner FQN: distinct values and exactly one successful class/member resolution per loader.
  2. Sequential static calls through two TCCLs defining the same owner FQN: distinct values/one success each plus retained null-TCCL system fallback. Make both loaders hostile—`equals`/`hashCode` must throw or record and fail the test if invoked—so the test proves the static index uses loader identity only.
  3. Mutable virtual and static/TCCL loaders: initial missing class throws the new exception; making bytes visible through the same loader makes the next call succeed, proving no failed class/index/`ClassValue` cache.
  4. Missing member called twice on loader A: exact exception/message/cause and two attempts via the collision-safe package-private counter field, read reflectively; loader B defining the same owner with the member succeeds.
  5. A public-lookup-inaccessible fixture, exact-signature mismatch, and a target deliberately throwing the new exception: respectively `IllegalAccessException`, `NoSuchMethodException`, and transparent target propagation.
  6. Concurrent static first use with a blocking/counting hostile loader has exactly one class-load and one member-lookup counter increment, proving the monitor covers resolution through index insertion. Do not add a virtual concurrency promise.

  Add `btrace-core/src/test/java/io/btrace/extension/util/MethodHandleCacheTest.java`. Prove repeated public static/virtual lookups return the cached handle and repeated missing lookups retry; name/comment the test so it makes no loader-safety claim.

  **Stop:** A test requiring a hidden lookup, a different target signature behind `Object`, or negative caching is Phase 3/6 work, not a Phase-1 workaround.

- [ ] **5. Gate the external masked-JAR processor/runtime contract.**

  First build `:btrace-dist:btraceJar` clean and inspect the one validated `btrace-dist/build/resources/main/v<version>/libs/btrace.jar`, never a sibling `btrace-core` output. Record its `jar tf` entries and extract the actual `META-INF/services/javax.annotation.processing.Processor` content to a log. Verify root-visible `ExternalType.class` and `ExternalTypeResolutionException.class`, the service’s declared processor class, and every `io/btrace/extension/processor/` class that processor requires at javac time.

  Add the one-interface input at `btrace-dist/src/test/resources/external-type-processor-smoke/src/example/ExternalApi.java`, so the proof does not depend on hand-created source. Invoke `javac -cp` and `-processorpath` pointing *only* at this `btrace.jar`, with `-d <temporary-smoke>/classes` and `-s <temporary-smoke>/generated`. Redirect both javac stdout/stderr to `/tmp/btrace-issue-931-external-javac.log`; then assert the expected generated `example/ExternalApi$Ext.java` below `generated/` and `example/ExternalApi$Ext.class` below `classes/`. Do not put a sibling `btrace-core` artifact on either path or stage this smoke extension in release extensions.

  If inspection shows the service entry is absent, update only `btrace-dist/build.gradle` to package that exact `META-INF/services/javax.annotation.processing.Processor` entry explicitly. If inspection separately shows that the declared processor/support classes are absent, expose only the required root `io/btrace/extension/processor/**/*.class`; do not add it merely because the service is missing. Rebuild clean and repeat the inspection and javac proof. Do not expose unrelated core/client/agent packages or change the Gradle plugin unless its own external wiring demonstrably fails.

  **Gate:** External compilation succeeds with the published-style masked JAR alone. If that needs an unmasked internal module or broad exposure, stop for compatibility review.

- [ ] **6. Maintain the real client/agent/target integration path.**

  Retain `integration-tests/build.gradle`’s `stageExternalTypeClientHome` isolated staging path; do not add the test extension to `btrace-dist` release assembly. Update only as necessary:

  - `btrace-extensions/btrace-ext-test/src/main/java/io/btrace/test/ext/ExternalDataType.java`
  - `btrace-extensions/btrace-ext-test/src/main/java/io/btrace/test/ext/ExternalTypeTestService.java`
  - `btrace-extensions/btrace-ext-test/src/main/java/io/btrace/test/ext/ExternalTypeTestServiceImpl.java`
  - `integration-tests/src/test/btrace/ExternalTypeAdapterTest.java` (only if a new marker is needed)
  - `integration-tests/src/test/java/tests/ExternalTypeAdapterIntegrationTest.java`

  Keep one static and one virtual call through the staged test extension, real client, agent, target JVM, and protocol. Keep the assertion that `btrace-ext-test` appears only below the integration build home and not the release distribution. Add a controlled failure case here only if it can show an extension catches/contains a resolution failure at its boundary; detailed classification stays unit-level.

  **Gate:** Both real-path markers pass; release extensions do not contain `btrace-ext-test`.

- [ ] **7. Consolidate documentation, with the extension guide authoritative.**

  Make `docs/BTraceExtensionDevelopmentGuide.md` normative. Replace its “planned” table and “no try/catch” implication with the design’s exact support table: public uniquely named methods with exact erased signatures; manual `ClassLoadingUtil`/`MethodHandleCache` for target-only types, overloads, fields, constructors, casts/predicates; and public/exported-only module access. Document `Object`’s non-coercion rule, virtual defining loader/static TCCL distinction, null-TCCL fallback, successful-only retry semantics, the new exception, transparent target errors, and extension-level logging/degradation.

  Include a `ClassLoadingUtil.withTCCL(...)` static-call example and a version-variant manual method example using exact `Class` values/`MethodHandleCache`. State that the utility caches only successful static/virtual method lookup and can retain application loaders strongly. Replace nonexistent `MethodHandleCache.findGetter`, `findSetter`, and `findConstructor` examples with direct `MethodHandles.publicLookup()` examples or an explicit “no helper exists.”

  Reduce `docs/architecture/provided-style-extensions.md` to concrete manual linking and point adapter scope at the normative table. Make `docs/BTraceTutorial.md` and `docs/GettingStarted.md` short accurate introductions/links only; remove duplicate scope tables, future promises, and assertions that generated interaction needs no error policy.

  **Gate:** Repository search finds no nonexistent cache helper claim, no user-doc “planned” statement for unsupported operations, and no promise that generated calls need no error boundary.

- [ ] **8. Execute progressive verification and final review.**

  From the repository root, run each Gradle call with a workspace-local cache, redirect before reading, then filter the log. A nonzero exit, `BUILD FAILED`, test failure, or format failure stops at its owning step. Build distribution before integration. When any Step-6 extension fixture is touched, run both its extension-module and integration-module Spotless gates below. For an address-selection failure, rerun the same command with `JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"`.

      GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-core:test > /tmp/btrace-issue-931-core-test.log 2>&1
      rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ExternalType|MethodHandleCache" /tmp/btrace-issue-931-core-test.log

      GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-core:spotlessCheck > /tmp/btrace-issue-931-core-spotless.log 2>&1
      rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-core-spotless.log

      GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew clean :btrace-dist:btraceJar > /tmp/btrace-issue-931-masked-jar.log 2>&1
      rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|btraceJar" /tmp/btrace-issue-931-masked-jar.log

      rg --files btrace-dist/build/resources/main | rg '^btrace-dist/build/resources/main/v[^/]+/libs/btrace\.jar$' > /tmp/btrace-issue-931-masked-jar-path.log
      test "$(rg -c '^' /tmp/btrace-issue-931-masked-jar-path.log)" = 1
      BTRACE_931_JAR="$(rg -x 'btrace-dist/build/resources/main/v[^/]+/libs/btrace\.jar' /tmp/btrace-issue-931-masked-jar-path.log)"
      jar tf "$BTRACE_931_JAR" > /tmp/btrace-issue-931-masked-jar-entries.log
      rg -n "io/btrace/core/extensions/ExternalType(ResolutionException)?\.class|io/btrace/extension/processor/|META-INF/services/javax.annotation.processing.Processor" /tmp/btrace-issue-931-masked-jar-entries.log
      unzip -p "$BTRACE_931_JAR" META-INF/services/javax.annotation.processing.Processor > /tmp/btrace-issue-931-processor-service.log
      rg -n '^io\.btrace\.extension\.processor\.ExternalTypeProcessor$' /tmp/btrace-issue-931-processor-service.log

      BTRACE_931_SMOKE=$(mktemp -d /tmp/btrace-issue-931-external-type-smoke.XXXXXX)
      mkdir -p "$BTRACE_931_SMOKE/classes" "$BTRACE_931_SMOKE/generated"
      javac -cp "$BTRACE_931_JAR" -processorpath "$BTRACE_931_JAR" -d "$BTRACE_931_SMOKE/classes" -s "$BTRACE_931_SMOKE/generated" btrace-dist/src/test/resources/external-type-processor-smoke/src/example/ExternalApi.java > /tmp/btrace-issue-931-external-javac.log 2>&1
      rg -n "error:|warning:|ExternalType|ExternalApi" /tmp/btrace-issue-931-external-javac.log || true
      test -f "$BTRACE_931_SMOKE/generated/example/ExternalApi\$Ext.java"
      test -f "$BTRACE_931_SMOKE/classes/example/ExternalApi\$Ext.class"

      GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build > /tmp/btrace-issue-931-dist-build.log 2>&1
      rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Test" /tmp/btrace-issue-931-dist-build.log

      GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:test > /tmp/btrace-issue-931-dist-test.log 2>&1
      rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Test" /tmp/btrace-issue-931-dist-test.log

      GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-extensions:btrace-ext-test:spotlessCheck > /tmp/btrace-issue-931-ext-test-spotless.log 2>&1
      rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-ext-test-spotless.log

      GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:spotlessCheck > /tmp/btrace-issue-931-integration-spotless.log 2>&1
      rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-integration-spotless.log

      GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:test -Pintegration --tests tests.ExternalTypeAdapterIntegrationTest > /tmp/btrace-issue-931-external-type-it.log 2>&1
      rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ExternalTypeAdapterIntegrationTest|timed out" /tmp/btrace-issue-931-external-type-it.log

  Preserve/filter the Step-5 external-javac result in `/tmp/btrace-issue-931-external-javac.log`. Finish with `git diff --check`, `git status --short`, and a release-surface review.

## Completion criteria

Phase 1 is complete only when existing interfaces stay source-compatible; virtual/static sequential calls cache successfully and remain loader-isolated; failed class/member resolution retries; static first use is serialized without a loader leak; exact contextual/cause-preserving resolution errors replace checked class-load leakage; target failures remain transparent; manual-cache semantics/limitation are covered; staged real client-agent-target integration passes; the external masked-JAR compilation proof passes; and the four user docs carry one accurate canonical contract. No later-phase API, module bypass, target-library dependency, release-extension staging, or plugin DSL change is part of completion.
