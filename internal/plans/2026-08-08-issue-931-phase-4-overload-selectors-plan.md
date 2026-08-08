# Issue #931 — Phase 4 implementation plan: opt-in overload selectors

**Design input:** `internal/specs/2026-08-08-issue-931-phase-4-overload-selectors.md` (CLEAN)

**Baseline:** `develop` at `96c7e4b4` (#941, #942, and #943 merged)

**Scope:** Phase 4 only. Add an opt-in source annotation that selects a target overload name while
the Phase 3 lookup return/parameter types continue to form an exact `MethodType`. Preserve all
Phase 1-3 loader, cache, error, and public-lookup boundaries. Do not implement general aliases,
coercion, candidate search, fields, constructors, private access, or later phases.

## Preconditions and hard gates

1. Work only in `/private/tmp/btrace-issue-931-phase4`. Preserve the untracked clean design and
   unrelated working-tree state. Before edits, read `AGENTS.md`, the clean design,
   `docs/architecture/MaskedJarArchitecture.md`, the Phase 3 plan, `ExternalType`,
   `ExternalTypeProcessor`, `MethodSpec`, `AdapterEmitter`, `CompileTestHarness`, the staged
   extension/integration fixtures, and the external processor smoke resources.

   **Stop:** A requirement that changes Phase 1-3 loader/cache/security policy, requires target
   method enumeration, or asks for target-type descriptors in public generated methods is outside
   this phase; return to design rather than adding a workaround.

2. Establish the current test contracts before changing diagnostics. The authoritative existing
   duplicate Java-name diagnostic is `@ExternalType does not support overloaded methods`; retain
   it whenever any member of that Java-name duplicate group has no selector. Capture existing
   processor/generator test shapes so compatibility assertions compare generated source and
   failure text deliberately rather than incidentally.

   **Stop:** Do not silently update a legacy test to accept an opt-in selector. A formerly invalid
   duplicate source must remain invalid unless every member in the duplicate group opts in.

## Implementation steps

3. Add the narrow public source API in
   `btrace-core/src/main/java/io/btrace/core/extensions/ExternalType.java`:

   - Add nested public `@ExternalType.Overload`, `@Retention(SOURCE)`, `@Target(METHOD)`, with
     only `String value()`.
   - Keep `ExternalType.Type`, `ExternalType.Static`, existing retention/targets, and all binary
     surfaces unchanged. Do not add class-array, descriptor, return-type, runtime helper, or
     reflection-based selector attributes.
   - Extend `ExternalTypeAnnotationTest` to pin method-only source retention and the absence of
     unintended public annotation members.

   **Gate:** The annotation is compile-time input only; later artifact inspection must nevertheless
   find `ExternalType$Overload.class` in the published masked JAR.

4. Refactor the model boundary in
   `btrace-core/src/main/java/io/btrace/extension/processor/MethodSpec.java` and its users.

   - Store `adapterName` and `targetName` independently; initialize both to the local Java name
     for no-selector members. Retain `returnType`, `returnTargetFqn`, `paramTypes`,
     `paramTargetFqns`, and static metadata exactly as Phase 3 represents them.
   - Update `AdapterEmitter` so Java declarations and dispatcher calls use `adapterName`, while
     `findVirtual`/`findStatic` and `ExternalTypeResolutionException` member text use
     `targetName`.
   - Preserve exact Phase 3 `MethodType` construction: ordinary erased declarations yield class
     literals; each marked position loads its recorded target FQN with
     `Class.forName(fqn, false, owner.getClassLoader())`. Return type remains part of the exact
     lookup type even if the emitted signature is `Object`.

   **Stop:** No emitted `ChildApi.class`, target-library `.class`, candidate enumeration,
   `isAssignableFrom`, varargs packing, boxing/coercion, fallback target name, or retry using a
   second signature is permitted.

5. Rework `btrace-core/src/main/java/io/btrace/extension/processor/ExternalTypeProcessor.java`
   so it validates a complete outer-interface model before emitting any adapter.

   - Add `io.btrace.core.extensions.ExternalType.Overload` to supported annotation types and
     read selector values with `AnnotationMirror`/`AnnotationValue`, never reflective annotation
     access. Collect all adapted methods (non-default and non-Java-static) first.
   - Consume/validate a selector only on an abstract adapted method of an `@ExternalType`
     interface. A selector elsewhere, including a skipped default or actual Java-static method,
     receives one source-positioned unconsumed-annotation diagnostic. Let javac retain native
     applicability/non-repeatable errors for parameter/type placement and repeats.
   - Implement frozen JVM member-name validation: value is nonblank; contains none of `.`, `;`,
     `[`, or `/`; is neither `<init>` nor `<clinit>`; and is preserved byte-for-byte (do not trim a
     nonblank name). Do not call host `SourceVersion.isIdentifier` or `isKeyword`: `_` must remain
     valid under a newer host JDK when the extension is compiled as Java 8. Local adapter names
     remain normal javac-validated Java declarations.
   - First group by local Java method name. If a duplicate has any unselected member, issue the
     established legacy duplicate diagnostic. Only an all-selected duplicate group continues.
     Then group by target name (selector value, otherwise legacy local name): target groups larger
     than one require selectors on every member, while a one-member selector group is rejected as
     an unsupported general alias.
   - Compute exact target keys as `(targetName, static/virtual, lookup return, lookup params)`.
     The lookup positions use Phase 3 target FQNs rather than emitted `Object` types. Reject
     duplicate exact keys. Separately compute generated descriptors: virtual
     `(Object self, emitted args...)`, legacy static `(emitted args...)`, and explicit static
     `(ClassLoader, emitted args...)`; reject any same-local-name collision, including selected
     `read()` and `read(ClassLoader)` where Phase 2 forms overlap.
   - Retain all Phase 3 `Type` marker validation, consumption, target-FQN metadata, and error
     aggregation. Any invalid method/group prevents adapter emission for its outer interface.
     Diagnostics name the member/group/collision and prescribe selecting every group member,
     using distinct exact types, or renaming the local method.

   **Gate:** Keep legacy unique no-selector source and generated lookup byte-for-byte equivalent
   where practical. Do not add selector-name global maps, negative caches, or strong loader maps.

6. Preserve emitter runtime semantics while adding selector coverage in
   `AdapterEmitter` and focused `ExternalTypeProcessorTest` runtime fixtures.

   - Each selected member retains its own existing `ClassValue<ResolvedCall>`, static monitor,
     weak identity loader index, success-only publication, and resolution counter. Exact selected
     keys mean independent handles; selector name is not a shared cache key.
   - Virtual calls resolve owner from the receiver defining loader. Legacy static calls keep TCCL
     with null-to-system fallback; explicit static calls require a non-null leading loader before
     lookup/cache mutation. After either owner resolves, marked types load only from the owner
     defining loader.
   - Missing selected member, inaccessible selected member, owner failure, and missing marked type
     remain cause-preserving, retryable `ExternalTypeResolutionException`s naming the selected
     target name. Target exceptions, wrong-argument invocation errors, linkage/security failures,
     and target-thrown resolution errors stay transparent. Keep `publicLookup()` and existing
     module/access policy unchanged.

   **Stop:** A wrong-loader child or incompatible argument must fail; it must never choose another
   overload. Do not initialize target classes, mutate TCCL/classpaths, bypass module exports, or
   use private lookup.

## Focused test gates

7. Extend `ExternalTypeProcessorTest` and any local compile harness needed for these source,
   generated-source, and diagnostic cases. Keep a positive and an intended-failure assertion for
   each new boundary.

   - A valid same-local-name overload group and distinct local aliases both select target
     `describe(String)` and `describe(vendor.Child)`; aliases use a Phase 3
     `@ExternalType.Type(ChildApi.class) Object` declaration. Assert generated local descriptors
     remain `Object`, lookup strings use `describe`, and the marked FQN—not `ChildApi`—enters the
     `MethodType`. Include differing return types to prove return type participates in exact-key
     validation.
   - Preserve a unique no-selector generated source fixture. Assert duplicate Java names with a
     legacy member retain the exact old duplicate diagnostic; assert all-selected duplicates no
     longer receive that diagnostic.
   - Assert malformed blank, `<init>`, `<clinit>`, and slash-containing selector values; a selector
     outside an adapted interface; selector on default/actual Java-static skipped methods; a
     one-member selector group; a target group mixed with legacy members; duplicate exact target
     keys; invalid/repeated Phase 3 markers; and static legacy/explicit generated-descriptor
     collision. Retain javac-native diagnostics for inapplicable/repeated annotation syntax.
   - Add a Java-8 compilation fixture (or optional `CompileTestHarness` release flag) that uses
     selector value `_` and a target member named `_`; compile the target with `javac --release 8`.
     This proves host JDK keywords are not used for selector validation. The same fixture family
     rejects `<init>`, `<clinit>`, blank, and `x/y`.
   - Runtime tests use two hostile isolated loaders with same-FQN overloaded owner/child types;
     prove loader-specific text and child paths select independent exact handles, a loader-A child
     cannot call loader-B’s selected child overload, and hostile `equals`/`hashCode` are untouched.
     Cover missing selected overload, missing marked type, inaccessible selection, retry after
     availability, target-error transparency, wrong arguments/no fallback, and per-member cache
     counters. Cover legacy-TCCL and explicit-loader static selected overloads, explicit null
     before cache mutation, and null-TCCL system fallback.

   **Gate:** Every new failure checks the intended diagnostic/cause rather than merely any failed
   compilation or invocation.

8. Add staged, real cross-process proof. Update
   `integration-tests/src/test/java/resources/ExternalData.java`,
   `btrace-extensions/btrace-ext-test/src/main/java/io/btrace/test/ext/ExternalDataType.java`,
   `btrace-extensions/btrace-ext-test/src/main/java/io/btrace/test/ext/ExternalTypeTestService.java`,
   `btrace-extensions/btrace-ext-test/src/main/java/io/btrace/test/ext/ExternalTypeTestServiceImpl.java`,
   `integration-tests/src/test/btrace/ExternalTypeAdapterTest.java`, and
   `integration-tests/src/test/java/tests/ExternalTypeAdapterIntegrationTest.java`.

   - Give `ExternalData` target-owned `describe(String)` and `describe(ExternalChild)` overloads
     with unambiguous text and child markers. The extension contract exposes local aliases such as
     `describeText` and `describeChild`, both annotated `@ExternalType.Overload("describe")`; the
     child alias keeps its Phase 3 `@ExternalType.Type(ExternalChildType.class) Object` boundary.
   - Stage a regenerated extension that calls both aliases through the normal client-agent-target
     protocol and emits two distinct target-owned markers. The integration assertion waits for
     both markers and keeps the Phase 2 explicit-loader/release-extension-isolation scenario
     separate and passing.

   **Stop:** Do not substitute direct unit invocation for this gate. The proof must exercise the
   real client, agent, target JVM, protocol, staged extension, and application overloads.

9. Extend the packaged external smoke resources under
   `btrace-dist/src/test/resources/external-type-processor-smoke/` into a deliberately two-stage
   Java-8 test.

   - In `target-src`, provide `ExternalParent.describe(String)`,
     `ExternalParent.describe(ExternalChild)`, and a legal Java-8 target member named `_`.
   - In `extension-src`, add contract aliases with `Overload("describe")`, use Phase 3 marked
     child type metadata, and a selector `Overload("_")`; keep contract/driver sources independent
     of target imports/classes. Have the driver invoke text, child, and underscore paths and print
     precise success markers.
   - After a clean masked-JAR build, inspect the one jar for
     `ExternalType$Overload.class`, `ExternalType$Type.class`, processor implementation/support,
     and `META-INF/services/javax.annotation.processing.Processor`. Compile target sources first
     with `javac --release 8`; compile contracts and driver with `javac --release 8`, with only
     that masked JAR on `-cp` and `-processorpath`, never target output/sources. Inspect generated
     source for local `Object` signatures, selected `describe`/`_` lookup names, and no target or
     contract `.class` leakage. Require generated `ParentApi$Ext.java` and `ChildApi$Ext.java`,
     plus both compiled adapter classes, and inspect both sources for their applicable `Object`
     boundaries. Negatively scan all generated sources for `ChildApi.class`, `ParentApi.class`,
     `ExternalChild.class`, and `ExternalParent.class`; target source availability cannot mask a
     leak because the contract compilation classpath/processorpath contains only the masked JAR,
     not target source or output. Run the driver only with extension output, target output, and the
     masked JAR.

   **Gate:** A missing annotation class, processor service, processor implementation, accidental
   compile-path target dependency, failure to compile `_` under release 8, or missing marker stops
   release validation. Make no unrelated Gradle packaging change unless inspection proves it is
   needed.

10. Update the canonical extension documentation in
    `docs/BTraceExtensionDevelopmentGuide.md` and the manual-path pointer in
    `docs/architecture/provided-style-extensions.md`.

    - Explain that `Overload("targetName")` is source-only, only applies to complete selected
      groups, maps a local method name to a target name, and combines with declared erased types
      plus Phase 3 type markers for exact lookup.
    - Document aliases for opaque `Object` target types and the retained static loader forms.
      State explicitly that selectors do not offer single-method aliases, runtime overload choice,
      coercion/boxing/varargs adaptation, generic/array target type support, fields/constructors,
      private lookup, or JPMS bypass. Keep existing access/security warnings accurate.

   **Gate:** Do not add Gradle plugin DSL, generated-adapter TCCL workarounds, or promises beyond
   the verified public behavior.

## Verification and publication readiness

11. From `/private/tmp/btrace-issue-931-phase4`, redirect every Gradle command to `/tmp` and
    inspect only filtered logs. A nonzero status, `BUILD FAILED`, test/format failure, artifact
    mismatch, compiler diagnostic mismatch, or missing integration marker stops at the owning
    step. Build the distribution before integration. Only if the documented restricted-environment
    address-selection failure occurs, rerun the affected command with
    `JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"`.

    ```text
    GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-core:test > /tmp/btrace-issue-931-phase4-core-test.log 2>&1
    rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ExternalType" /tmp/btrace-issue-931-phase4-core-test.log

    GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-core:spotlessCheck > /tmp/btrace-issue-931-phase4-core-spotless.log 2>&1
    rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase4-core-spotless.log

    GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew clean :btrace-dist:btraceJar > /tmp/btrace-issue-931-phase4-masked-jar.log 2>&1
    rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|btraceJar" /tmp/btrace-issue-931-phase4-masked-jar.log

    rg --files btrace-dist/build/resources/main | rg '^btrace-dist/build/resources/main/v[^/]+/libs/btrace\.jar$' > /tmp/btrace-issue-931-phase4-jar-path.log
    test "$(rg -c '^' /tmp/btrace-issue-931-phase4-jar-path.log)" = 1
    BTRACE_931_PHASE4_JAR="$(rg -x 'btrace-dist/build/resources/main/v[^/]+/libs/btrace\.jar' /tmp/btrace-issue-931-phase4-jar-path.log)"
    jar tf "$BTRACE_931_PHASE4_JAR" > /tmp/btrace-issue-931-phase4-jar-entries.log
    rg -n 'io/btrace/core/extensions/ExternalType(\$Overload|\$Type|ResolutionException)?\.class|io/btrace/extension/processor/ExternalTypeProcessor\.class|META-INF/services/javax.annotation.processing.Processor' /tmp/btrace-issue-931-phase4-jar-entries.log
    unzip -p "$BTRACE_931_PHASE4_JAR" META-INF/services/javax.annotation.processing.Processor > /tmp/btrace-issue-931-phase4-processor-service.log
    rg -n '^io\.btrace\.extension\.processor\.ExternalTypeProcessor$' /tmp/btrace-issue-931-phase4-processor-service.log

    BTRACE_931_PHASE4_SMOKE=$(mktemp -d /tmp/btrace-issue-931-phase4-smoke.XXXXXX)
    javac --release 8 -d "$BTRACE_931_PHASE4_SMOKE/target-classes" btrace-dist/src/test/resources/external-type-processor-smoke/target-src/example/target/*.java > /tmp/btrace-issue-931-phase4-target-javac.log 2>&1
    rg -n "error:|warning:|External" /tmp/btrace-issue-931-phase4-target-javac.log || true
    javac --release 8 -cp "$BTRACE_931_PHASE4_JAR" -processorpath "$BTRACE_931_PHASE4_JAR" -d "$BTRACE_931_PHASE4_SMOKE/extension-classes" -s "$BTRACE_931_PHASE4_SMOKE/generated" btrace-dist/src/test/resources/external-type-processor-smoke/extension-src/example/*.java > /tmp/btrace-issue-931-phase4-extension-javac.log 2>&1
    rg -n "error:|warning:|ExternalType|ParentApi|ChildApi" /tmp/btrace-issue-931-phase4-extension-javac.log || true
    test -f "$BTRACE_931_PHASE4_SMOKE/generated/example/ParentApi\$Ext.java"
    test -f "$BTRACE_931_PHASE4_SMOKE/generated/example/ChildApi\$Ext.java"
    test -f "$BTRACE_931_PHASE4_SMOKE/extension-classes/example/ParentApi\$Ext.class"
    test -f "$BTRACE_931_PHASE4_SMOKE/extension-classes/example/ChildApi\$Ext.class"
    rg -n 'Object|findVirtual|"describe"|"_"' "$BTRACE_931_PHASE4_SMOKE/generated/example/ParentApi\$Ext.java"
    rg -n 'Object|findVirtual' "$BTRACE_931_PHASE4_SMOKE/generated/example/ChildApi\$Ext.java"
    ! rg -n 'ChildApi\.class|ParentApi\.class|ExternalChild\.class|ExternalParent\.class' "$BTRACE_931_PHASE4_SMOKE/generated"/example/*.java
    java -cp "$BTRACE_931_PHASE4_SMOKE/extension-classes:$BTRACE_931_PHASE4_SMOKE/target-classes:$BTRACE_931_PHASE4_JAR" example.ExternalTypeSmoke > /tmp/btrace-issue-931-phase4-external-run.log 2>&1
    rg -n '^external-type-overload-text-ok$' /tmp/btrace-issue-931-phase4-external-run.log
    rg -n '^external-type-overload-child-ok$' /tmp/btrace-issue-931-phase4-external-run.log
    rg -n '^external-type-overload-underscore-ok$' /tmp/btrace-issue-931-phase4-external-run.log

    GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build > /tmp/btrace-issue-931-phase4-dist-build.log 2>&1
    rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Test" /tmp/btrace-issue-931-phase4-dist-build.log

    GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-extensions:btrace-ext-test:spotlessCheck > /tmp/btrace-issue-931-phase4-ext-spotless.log 2>&1
    rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase4-ext-spotless.log

    GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:spotlessCheck > /tmp/btrace-issue-931-phase4-integration-spotless.log 2>&1
    rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase4-integration-spotless.log

    GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:test -Pintegration --tests tests.ExternalTypeAdapterIntegrationTest > /tmp/btrace-issue-931-phase4-integration.log 2>&1
    rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ExternalTypeAdapterIntegrationTest|timed out" /tmp/btrace-issue-931-phase4-integration.log
    ```

12. Before any user-authorized commit/push, run root formatting in the dedicated worktree, inspect
    its intentional adjustments, then repeat its check and the affected tests if it changes source.
    Finish with whitespace/status/release-surface review. This plan does not authorize committing
    or pushing.

    ```text
    GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew spotlessApply > /tmp/btrace-issue-931-phase4-root-spotless-apply.log 2>&1
    rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase4-root-spotless-apply.log
    git diff --check
    git status --short
    GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew spotlessCheck > /tmp/btrace-issue-931-phase4-root-spotless-check.log 2>&1
    rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase4-root-spotless-check.log
    ```

## Completion criteria

Phase 4 is complete only when all-selected groups map local aliases and Phase 3 FQN metadata to
exact public target handles; legacy duplicate protection is unchanged; frozen JVM-name validation
accepts `_` under `javac --release 8`; selector diagnostics are source-positioned and specific;
cache, loader, retry, error, access, and invocation boundaries hold; both application overloads
work across the real client-agent-target path; the masked jar alone supports separated Java-8
external compilation and execution; documentation is scope-accurate; all gates above pass; and
the final diff is clean. This does not authorize Phase 5 or later work.
