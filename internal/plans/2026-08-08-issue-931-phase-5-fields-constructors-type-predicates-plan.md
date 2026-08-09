# Issue #931 — Phase 5 implementation plan: fields, constructors, and type predicates

**Design input:** `internal/specs/2026-08-08-issue-931-phase-5-fields-constructors-type-predicates.md`
**Required design state:** CLEAN before implementation begins
**Baseline:** `develop` after Phase 4 PR #944
**Worktree:** `/private/tmp/btrace-issue-931-phase5`

## Scope and invariants

Implement exactly five source-only `ExternalType` operations: instance/static field getters,
instance/static field setters, constructors, `isInstance`, and `cast`. A contract remains a
compile-time declaration of exact erased target signatures; generated public APIs keep ordinary
Java types and opaque `Object` boundaries. This phase neither adds a reflection language nor
changes extension staging, protocol, loader policy, package access, or masked-JAR layout unless
the artifact proof demonstrates a narrowly missing published class.

Preserve every Phase 1--4 boundary:

- virtual operations select the receiver/value defining loader; static fields and constructors
  retain legacy TCCL-with-null-system-fallback and explicit non-null loader forms;
- owners and marked target types load with `Class.forName(name, false, loader)`; resolution does
  not initialize targets, has no alternate-loader/name/signature fallback, and publishes only
  successful results;
- public lookup and exported-access requirements remain mandatory; do not use reflection,
  `setAccessible`, `privateLookupIn`, `Lookup.in`, TCCL mutation, classpath/module rewriting, or
  privileged access;
- Phase 3 `@Type` remains a direct `Object` return/parameter marker only where explicitly
  permitted; Phase 4 overload grouping remains `METHOD`-only;
- all invalid contracts receive source-positioned processor diagnostics and emit no partial
  adapter.

## Preconditions and stop conditions

1. Before editing, read `AGENTS.md`, this clean design, the Phase 4 plan, `ExternalType`,
   `ExternalTypeResolutionException`, `ExternalTypeProcessor`, `MethodSpec`, `AdapterSpec`,
   `AdapterEmitter`, `CompileTestHarness`, the existing processor/annotation tests, staged
   `ExternalData` fixtures, the external processor smoke resources, and both canonical
   extension-documentation pages. Read `docs/architecture/MaskedJarArchitecture.md` before any
   distribution assertion or packaging change.

2. Keep the existing untracked design spec untouched. Preserve any unrelated tracked/untracked
   worktree state; inspect `git status --short` before and after each implementation gate.

3. Stop for design review rather than improvise if a requirement needs generic/array target
   metadata, reflection/bulk field access, constructor selectors, one-member aliases, overload
   candidate search/coercion, private/JPMS bypass, loader fallback, cache-lifecycle redesign, or
   a protocol/staging change.

4. Stop at the owning gate on a failed diagnostic assertion, missing generated class/marker,
   failed artifact inspection, nonzero verification command, `BUILD FAILED`, failing test, or
   unexpected formatter rewrite. Never commit or push from this plan without explicit user
   authorization and all required gates passing.

## Implementation plan

### 1. Publish the five narrow source markers

Update `btrace-core/src/main/java/io/btrace/core/extensions/ExternalType.java`.

- Add public nested `@Getter(String value)`, `@Setter(String value)`,
  `@Constructor`, `@InstanceOf`, and `@Cast` annotations. Each has
  `RetentionPolicy.SOURCE` and `ElementType.METHOD`; getter/setter have exactly their
  `String value()` member and the remaining three have no elements.
- Keep `Static` runtime retention, `Type` source retention, and `Overload` source retention
  unchanged. The markers are processor input, but their nested `.class` files must still be in
  the published masked JAR so external users can compile a contract.
- Extend `btrace-core/src/test/java/io/btrace/core/extensions/ExternalTypeAnnotationTest.java` to
  lock source retention, method target, exact members, and lack of accidental runtime elements for
  all five annotations.
- Add concise JavaDoc that states exact legal signatures, `Static` scope, `Type` scope, and that
  markers never drive runtime annotation inspection.

**Gate:** no new runtime helper/public reflection API or annotation descriptor grammar is added.

### 2. Make operation kind explicit in the processor model

Update `btrace-core/src/main/java/io/btrace/extension/processor/MethodSpec.java` (and
`AdapterSpec` only if it needs a model-level helper).

- Introduce an explicit `OperationKind` with `METHOD`, `GETTER`, `SETTER`,
  `CONSTRUCTOR`, `INSTANCE_OF`, and `CAST`; retain independent `adapterName`,
  `targetName`, erased return type, marked return FQN, erased parameter types, marked parameter
  FQNs, and static state.
- Add operation metadata only as needed: field name for fields, and the defined diagnostic member
  text (`field`, `<init>`, `isInstance`, or `cast`). Do not overload a method-selector field
  to describe fields/predicates.
- Treat selector/field text as raw JVM member text in the model. Rendering is a separate concern:
  generated Java source must quote it through one shared Java-string-literal escaper, never by
  restricting or normalizing the stored member text.
- Keep target identity and generated Java descriptor calculations separate. Exact target identity
  includes operation kind, static/virtual state, target name/field name, return lookup type, and
  lookup parameter types. Generated-descriptor collision detection remains cross-kind because two
  generated Java methods cannot share a descriptor.

**Gate:** `METHOD` remains the sole participant in Phase 4 local-name and target-name group
validation. A field, constructor, predicate, or cast must never accidentally form an overload
group merely because its text matches a method name.

### 3. Validate complete contracts before rendering

Refactor `btrace-core/src/main/java/io/btrace/extension/processor/ExternalTypeProcessor.java` to
collect all adapted abstract interface methods, consume all operation annotations through
`AnnotationMirror`/`AnnotationValue` where values are needed, validate the complete contract, and
only then build/emit an `AdapterSpec`.

- Add all five annotation names to `@SupportedAnnotationTypes`, track their annotated elements,
  and report each unconsumed marker exactly once at its source position. Default and real Java
  static interface methods are skipped adapters; operation marker use there is an error.
- Reject more than one Phase 5 operation marker on a method. `Static` is valid for ordinary
  `METHOD`, `GETTER`, and `SETTER` only; reject it for constructor/predicate/cast. Reject
  `Overload` on every non-`METHOD` operation. Continue the Phase 3 direct-position-only and
  `Object`-erasure validation for `Type`, then additionally reject it in forbidden positions.
- Preserve frozen JVM field-name validation: nonblank (without trimming a valid name), unqualified
  and containing none of `.`, `;`, `[`, `/`, `<init>`, or `<clinit>`; do not use host-JDK
  keyword APIs. Quote, backslash, and control characters are valid frozen JVM name text and must
  not be rejected merely because generated Java needs escaping. Fields outside adapted methods or
  on default/static interface methods remain errors.
- Validate operation signatures exactly:
  - getter: non-`void`, zero declared target arguments; return lookup type may carry a direct
    `@Type` marker;
  - setter: `void`, exactly one declared target argument; that parameter may carry direct
    `@Type`;
  - constructor: direct `Object` return, no return `Type`, any ordinary/marked exact parameters,
    no `Static`/`Overload`;
  - `InstanceOf`: `boolean` return and exactly one direct unmarked `Object` parameter;
  - `Cast`: direct unmarked `Object` return and exactly one direct unmarked `Object` parameter.
- For fields, group by field name and static state: permit at most one getter and one setter; a
  pair must have identical exact lookup type (target FQN rather than emitted `Object` when
  marked). Reject duplicate getter/setter aliases and static/instance reuse of the same field.
- Preserve Phase 4 `METHOD` rules unchanged: duplicate local Java names need selectors on every
  group member, target-name groups have the existing all-selected rule, and a one-member selector
  remains rejected. Do not apply those maps to special operations.
- Calculate exact target-operation keys and reject duplicate keys. Separately calculate every
  generated form: virtual `(Object self, emitted args...)`, static legacy `(emitted args...)`,
  static/constructor explicit `(ClassLoader, emitted args...)`. Reject collisions with the
  established generated-descriptor diagnostic, including `create()` against
  `create(ClassLoader)` and a collision across operation kinds. Anchor each diagnostic to the
  declaration that caused it and retain no-partial-adapter behavior.

**Gate:** diagnostics explain the offending declaration/group and the corrective rule (signature,
marker, field pairing, or local rename). Do not weaken existing Phase 1--4 failure text merely to
accommodate Phase 5.

### 4. Render independent, exact public operations

Extend `btrace-core/src/main/java/io/btrace/extension/processor/AdapterEmitter.java` using the
operation kind rather than branching on method name.

- Add one shared Java string-literal escaper and use it for every generated target member literal:
  ordinary/selected method names, getter/setter field names, and resolution-exception member text.
  It must preserve the raw model text used for identity/lookup while escaping `"`, `\\`, control
  characters, and other source-sensitive code points into Java-8-valid source. Do not concatenate
  raw annotation text into generated source or alter the lookup name to its escaped spelling.
- Keep one independent successful-resolution cache per field operation/constructor. Reuse the
  existing `ResolvedCall`, virtual `ClassValue`, and static weak-identity loader index,
  per-operation monitor, bootstrap/system sentinels, expunging, and owner-keyed `ClassValue`.
  Legacy and explicit static/constructor calls share their successful entry. Failures must not
  create cache/index entries.
- Render public lookup calls exactly:
  `findGetter`, `findSetter`, `findStaticGetter`, `findStaticSetter`, and
  `findConstructor` with `MethodType.methodType(void.class, exactParameterTypes)`. Resolve
  marked field/constructor types with the successfully resolved owner's defining loader before
  lookup. Never pass the adapter control `ClassLoader` into a constructor `MethodType` or
  invocation list.
- Preserve virtual field null-receiver behavior and static explicit-loader null rejection before
  cache access. Use the Phase 2 legacy/static policy unchanged for static fields and constructors.
  Keep ordinary `METHOD` and selected-method emitter output behavior intact.
- Render predicate/cast operations with an independent `ClassValue<Class<?>>` keyed by
  `value.getClass()`. On non-null compute, load `OWNER` with that defining loader and verify
  public/exported access via `publicLookup().findVirtual(owner, "getClass",
  MethodType.methodType(Class.class))`; discard the probe handle. Then use `owner.isInstance` or
  `owner.cast`, not a member lookup/handle. Bypass the cache entirely for null: return `false` or
  `null`. Let a wrong non-null cast throw ordinary `ClassCastException`.
- Translate only resolution-phase `ClassNotFoundException`, `NoSuchFieldException`,
  `NoSuchMethodException`, and `IllegalAccessException` into
  `ExternalTypeResolutionException`, with owner plus the defined member text. Keep target
  initializer/constructor errors, linkage/security errors, wrong invocation arguments,
  `ClassCastException`, and target-thrown resolution exceptions transparent.

Update `btrace-core/src/main/java/io/btrace/core/extensions/ExternalTypeResolutionException.java`
JavaDoc to include field lookup and predicate/cast public-access probe failures while preserving
the cause contract.

**Gate:** resolution class loads use `initialize=false`; a successful lookup/probe cannot trigger
target initialization. Static field access and constructor invocation may initialize normally.

### 5. Build exhaustive focused processor and runtime tests

Extend `btrace-core/src/test/java/io/btrace/extension/processor/ExternalTypeProcessorTest.java`
and, only if necessary, `CompileTestHarness.java` with source-generation, diagnostics, isolated
loader, mutable-loader/retry, and cache fixtures. Keep existing Phase 1--4 tests untouched except
for strictly necessary shared test helpers.

- Positive generation/runtime coverage: primitive and JDK instance fields; marked `Object` field
  return/parameter; static field legacy TCCL and explicit loader forms; get/set pair sharing the
  same exact field type; public construction with regular and marked arguments; coexistence with
  an ordinary static method; predicate/cast behavior before another adapter call.
- Isolated hostile loaders with same-FQN owners prove virtual field and predicate/cast selection
  follows receiver/value definition, cache entries stay loader-isolated, wrong-loader marked
  values fail without fallback, and user `equals`/`hashCode` are never called. Cover null
  receiver, null predicate/cast semantics, null explicit loader before cache mutation, retry after
  an owner/marked type/member appears, per-operation attempts, and no negative caching.
- Failure/transparency coverage: missing/inaccessible/final field write, missing/inaccessible
  constructor, inaccessible predicate owner access probe, missing marked type, source exception
  cause/member text, target initializer/constructor failure, wrong handle argument, cast
  `ClassCastException`, security/linkage transparency, and target-thrown resolution exception.
  Each failure must assert the intended cause/message, not just any failure.
- Initialization fixture: establish that a lookup-only successful path leaves a target uninitialized
  and that invoking a static getter/setter or constructor initializes only at normal JVM execution.
- Validation fixtures: malformed field names; operation markers outside an adapted contract or on
  default/static methods; multiple operation markers; illegal `Static`, `Overload`, and `Type`;
  malformed getter/setter/constructor/predicate/cast signatures; duplicate predicates/casts;
  duplicate getter/setter aliases; incompatible read/write pairs; static/instance same-field reuse;
  exact-key duplicates; constructor generated-form collision; cross-kind descriptor collision;
  and no generated source after a contract error. Assert Phase 4 overload grouping accepts ordinary
  methods only and fields/constructors/predicates cannot enter it.
- Retain Java-8 source compatibility tests (including a field named `_` if useful) with
  `CompileTestHarness.compile(sources, 8)`, proving no host-JDK identifier/keyword validation
  leaks into the frozen field-name rule.
- Add a compile-only Java-8 generated-source fixture using `@Getter("a\"b")`. Assert the
  generated `findGetter` Java literal is correctly escaped while preserving that raw lookup text,
  then compile the generated adapter with `--release 8`; do not attempt a target invocation because
  Java source cannot declare the quote-containing field. This must prove the common escaper is
  used by a real generated adapter rather than merely unit-testing an emitter helper.

**Gate:** exact marked target FQNs, never contract `.class` literals, feed field/constructor
MethodTypes; emitted source remains Java 8 and exposes only allowed erased/Object signatures.

### 6. Prove the published masked JAR from separated Java 8 sources

Update only the smoke fixtures below
`btrace-dist/src/test/resources/external-type-processor-smoke/`:

- `target-src/example/target/ExternalParent.java` and `ExternalChild.java`: expose public
  instance/static fields, a constructor, and discriminating child/type markers without any BTrace
  dependency;
- `extension-src/example/ParentApi.java` and `ChildApi.java`: declare getters/setters,
  constructor, predicate/cast, and marked `Object` field/constructor positions without importing
  target classes;
- `extension-src/example/ExternalTypeSmoke.java`: invoke each required operation, predicate/cast
  then a second adapter call, and print stable markers for field, constructor, and type paths.

After clean `btrace.jar` creation, inspect it for all five nested annotation classes,
`ExternalType`, `ExternalTypeResolutionException`, the processor and needed support classes, plus
`META-INF/services/javax.annotation.processing.Processor`. Compile target sources first with
`javac --release 8`; compile contracts/driver separately with the masked JAR as the sole
classpath and processorpath. Assert generated adapters/classes exist, generated source uses
`Object` at every marked boundary, contains the exact field/constructor lookup forms and predicate
behavior, and contains no contract or target `.class` literal/leak. Run solely with extension
classes, target classes, and the masked JAR.

**Stop:** do not change `btrace-dist` packaging or add target output/sources to contract
compilation. A missing annotation/service/processor entry is evidence for a narrow packaging
review, not permission for broad jar restructuring.

### 7. Add real staged client-agent-target integration coverage

Modify the existing external-type scenario only:

- `integration-tests/src/test/java/resources/ExternalData.java` (and an application-only child
  fixture if required) exposes target-owned public state, a public constructor/factory scenario,
  and distinct marker methods;
- `btrace-extensions/btrace-ext-test/src/main/java/io/btrace/test/ext/ExternalDataType.java` and
  a child contract declare the Phase 5 operations with `Object`/`Type` boundaries;
- `ExternalTypeTestService.java` and `ExternalTypeTestServiceImpl.java` create/read/write then
  predicate/cast a target value before a second generated adapter call, without target imports;
- `integration-tests/src/test/btrace/ExternalTypeAdapterTest.java` emits discriminating
  target-owned field/constructor/predicate markers;
- `integration-tests/src/test/java/tests/ExternalTypeAdapterIntegrationTest.java` waits for and
  asserts each marker.

Keep `ExternalTypeAdapterExplicitTest` and its deterministic decoy-TCCL scenario dedicated to
Phase 2. The test extension must still be staged through the existing extension path, excluded
from release extension output, and compiled without the target application library. The proof must
exercise the real client, agent, instrumentation, target JVM, protocol, and staged extension.

**Gate:** a component/direct-adapter test cannot replace this end-to-end test. The observed marker
must depend on actual field use, construction, and type narrowing in the target process.

### 8. Document the exact contract and manual boundary

Update `docs/BTraceExtensionDevelopmentGuide.md` as the normative guide and
`docs/architecture/provided-style-extensions.md` as the concise manual-path pointer.

- Show a complete contract for getter, setter, static field, constructor, `isInstance`, and
  `cast`, including exact signatures, `@Type(ChildApi.class) Object`, receiver/loader forms, and
  the two constructor/static-field forms.
- Explain that fields and constructors use public exact lookup types; predicates/casts use the
  non-null value's defining loader, follow `Class` null/cast semantics, and do not initialize on
  resolution. Document field pair consistency, retryable resolution errors, target failure
  transparency, and public/exported access restrictions.
- Make unsupported scope explicit: generic/array target types, bulk/reflection-style operations,
  fluent setters, constructor selectors, runtime overload choice/coercion, non-public/JPMS
  bypass, and later security/access work retain the manual path. Avoid duplicating full guide
  tables in the architecture pointer.

**Gate:** documentation never suggests a target implements the contract, target classes are on the
extension compile classpath, a `ClassLoader` control argument becomes a target constructor argument,
or Phase 5 provides private/module bypass.

## Verification and release-surface checklist

Run from `/private/tmp/btrace-issue-931-phase5`. Redirect every Gradle command, inspect its
filtered log, build the distribution before the integration test, and rerun only an affected
command with the documented IPv4 options if the known address-selection failure occurs.

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-core:test > /tmp/btrace-issue-931-phase5-core-test.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ExternalType" /tmp/btrace-issue-931-phase5-core-test.log

GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-core:spotlessCheck > /tmp/btrace-issue-931-phase5-core-spotless.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase5-core-spotless.log

GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew clean :btrace-dist:btraceJar > /tmp/btrace-issue-931-phase5-masked-jar.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|btraceJar" /tmp/btrace-issue-931-phase5-masked-jar.log

rg --files btrace-dist/build/resources/main | rg '^btrace-dist/build/resources/main/v[^/]+/libs/btrace\.jar$' > /tmp/btrace-issue-931-phase5-jar-path.log
test "$(rg -c '^' /tmp/btrace-issue-931-phase5-jar-path.log)" = 1
BTRACE_931_PHASE5_JAR="$(rg -x 'btrace-dist/build/resources/main/v[^/]+/libs/btrace\.jar' /tmp/btrace-issue-931-phase5-jar-path.log)"
jar tf "$BTRACE_931_PHASE5_JAR" > /tmp/btrace-issue-931-phase5-jar-entries.log
for BTRACE_931_PHASE5_ENTRY in \
  'io/btrace/core/extensions/ExternalType.class' \
  'io/btrace/core/extensions/ExternalType$Getter.class' \
  'io/btrace/core/extensions/ExternalType$Setter.class' \
  'io/btrace/core/extensions/ExternalType$Constructor.class' \
  'io/btrace/core/extensions/ExternalType$InstanceOf.class' \
  'io/btrace/core/extensions/ExternalType$Cast.class' \
  'io/btrace/core/extensions/ExternalType$Type.class' \
  'io/btrace/core/extensions/ExternalType$Overload.class' \
  'io/btrace/core/extensions/ExternalTypeResolutionException.class' \
  'io/btrace/extension/processor/ExternalTypeProcessor.class' \
  'META-INF/services/javax.annotation.processing.Processor'; do
  rg -Fx "$BTRACE_931_PHASE5_ENTRY" /tmp/btrace-issue-931-phase5-jar-entries.log
done
unzip -p "$BTRACE_931_PHASE5_JAR" META-INF/services/javax.annotation.processing.Processor > /tmp/btrace-issue-931-phase5-processor-service.log
rg -n '^io\.btrace\.extension\.processor\.ExternalTypeProcessor$' /tmp/btrace-issue-931-phase5-processor-service.log

BTRACE_931_PHASE5_SMOKE=$(mktemp -d /tmp/btrace-issue-931-phase5-smoke.XXXXXX)
javac --release 8 -d "$BTRACE_931_PHASE5_SMOKE/target-classes" btrace-dist/src/test/resources/external-type-processor-smoke/target-src/example/target/*.java > /tmp/btrace-issue-931-phase5-target-javac.log 2>&1
rg -n 'error:|warning:' /tmp/btrace-issue-931-phase5-target-javac.log || true
javac --release 8 -cp "$BTRACE_931_PHASE5_JAR" -processorpath "$BTRACE_931_PHASE5_JAR" -d "$BTRACE_931_PHASE5_SMOKE/extension-classes" -s "$BTRACE_931_PHASE5_SMOKE/generated" btrace-dist/src/test/resources/external-type-processor-smoke/extension-src/example/*.java > /tmp/btrace-issue-931-phase5-extension-javac.log 2>&1
rg -n 'error:|warning:|ExternalType|ParentApi|ChildApi' /tmp/btrace-issue-931-phase5-extension-javac.log || true
test -f "$BTRACE_931_PHASE5_SMOKE/generated/example/ParentApi\$Ext.java"
test -f "$BTRACE_931_PHASE5_SMOKE/generated/example/ChildApi\$Ext.java"
test -f "$BTRACE_931_PHASE5_SMOKE/extension-classes/example/ParentApi\$Ext.class"
test -f "$BTRACE_931_PHASE5_SMOKE/extension-classes/example/ChildApi\$Ext.class"
rg -n 'find(Getter|Setter|StaticGetter|StaticSetter|Constructor)|isInstance|cast|Object' "$BTRACE_931_PHASE5_SMOKE/generated"/example/*.java
! rg -n 'ChildApi\.class|ParentApi\.class|ExternalChild\.class|ExternalParent\.class' "$BTRACE_931_PHASE5_SMOKE/generated"/example/*.java
java -cp "$BTRACE_931_PHASE5_SMOKE/extension-classes:$BTRACE_931_PHASE5_SMOKE/target-classes:$BTRACE_931_PHASE5_JAR" example.ExternalTypeSmoke > /tmp/btrace-issue-931-phase5-external-run.log 2>&1
rg -n '^external-type-field-ok$' /tmp/btrace-issue-931-phase5-external-run.log
rg -n '^external-type-constructor-ok$' /tmp/btrace-issue-931-phase5-external-run.log
rg -n '^external-type-predicate-ok$' /tmp/btrace-issue-931-phase5-external-run.log

GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build > /tmp/btrace-issue-931-phase5-dist-build.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Test" /tmp/btrace-issue-931-phase5-dist-build.log

GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-extensions:btrace-ext-test:spotlessCheck > /tmp/btrace-issue-931-phase5-ext-spotless.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase5-ext-spotless.log

GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:spotlessCheck > /tmp/btrace-issue-931-phase5-integration-spotless.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase5-integration-spotless.log

GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:test -Pintegration --tests tests.ExternalTypeAdapterIntegrationTest > /tmp/btrace-issue-931-phase5-integration.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|ExternalTypeAdapterIntegrationTest|timed out" /tmp/btrace-issue-931-phase5-integration.log
```

Before every user-authorized push, unconditionally run root formatting in this dedicated worktree,
inspect its exact diff, and repeat affected checks if it changes source. Do this even when the
working tree appears formatted; a push is blocked until this gate succeeds:

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew spotlessApply > /tmp/btrace-issue-931-phase5-spotless-apply.log 2>&1
rg -n "BUILD SUCCESSFUL|BUILD FAILED|FAILED|ERROR|Spotless" /tmp/btrace-issue-931-phase5-spotless-apply.log
git diff --check
git status --short
```

## Completion criteria

Phase 5 is complete only when all five operations meet the exact declaration, loader,
success-only-cache, public-access, failure, initialization, and target-type rules; focused tests
cover every positive and rejection boundary; the external Java 8 masked-JAR proof is genuinely
separated; the staged integration test proves field, construction, and type narrowing over the
real client-agent-target protocol; docs explain use and limits; root formatting and every required
check pass; and the final diff contains no unrelated change. This plan does not authorize Phase 6.
