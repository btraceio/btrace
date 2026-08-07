# Issue #931: `@ExternalType` dependable baseline and phased roadmap

Date: 2026-08-06
Status: design contract for [#931](https://github.com/btraceio/btrace/issues/931)

## Decision

Deliver this issue as small, independently releasable phases. Phase 1 hardens and documents the
existing adapter model; it does **not** expand that model. It is the first implementation because
it gives extension authors a dependable supported boundary now, while preserving a deliberate
manual escape hatch for the APIs the processor cannot model.

The existing implementation is already sound for public methods whose erased signatures are
expressible by the extension: it produces lazy `MethodHandle` dispatch and isolates virtual
handles by resolved target class. Its unsupported surface and runtime failure semantics, however,
are ambiguous in user documentation, and successful class resolution is repeated on every call.
Phase 1 closes those reliability/documentation gaps before adding new annotation syntax or target
type modelling.

## Phase 1: dependable v1 adapter contract

### Scope

Keep the current `@ExternalType` source shape and generate adapters only for an annotated
interface's non-default, non-static interface methods:

- A virtual adapter accepts `Object self` followed by the declared parameters. It resolves the
  target class using `self`'s defining class loader.
- A method annotated `@ExternalType.Static` has no receiver and resolves the target class through
  the current thread context class loader (TCCL), falling back to the system loader only when the
  TCCL is `null`.
- Lookup uses `MethodHandles.publicLookup()` and therefore supports only public members of a type
  accessible to that lookup. The declared erased parameter and return types must exactly match the
  target member's erased JVM signature. In particular, use `Object` at the adapter boundary for a
  target-only type; `Object` does not make a differently typed target method match.
- Class/handle resolution is cached only after both the target class and requested member resolve
  successfully. It must not perform `Class.forName` on every successful call. Failed class or
  member resolution is never cached, so a later call can succeed when an application class becomes
  available or a deployment changes.
- Resolution failures use the public
  `io.btrace.core.extensions.ExternalTypeResolutionException`, a final
  `IllegalStateException` whose message is exactly
  `Unable to resolve @ExternalType <owner>#<member>` and whose cause is the original
  `ClassNotFoundException`, `NoSuchMethodException`, or `IllegalAccessException`. A missing target
  class must no longer escape as an undeclared checked `ClassNotFoundException`. Invocation
  failures from a target method retain the current transparent propagation behavior.

The implementation may use generated self-contained code or a minimal shared runtime helper, but
must retain generated-adapter binary/source compatibility for the existing annotation and method
forms. If a helper is introduced, it is part of the supported `io.btrace:btrace` masked-JAR
surface required by externally built extensions, with only the narrow root-visible exposure needed
at runtime and no dependency on an unmasked internal module.

### Cache ownership and failure invariants

Generated code owns the cache; `MethodHandleCache` is not a substitute for its lifecycle rules.
The generated adapter must follow these invariants:

- **Virtual dispatch:** use a `ClassValue<ResolvedCall>` anchored to `self.getClass()`, where a
  `ResolvedCall` contains the resolved owner and its method handle. Its computation loads the
  configured owner with that receiver class's defining loader and resolves the exact member. A
  `ClassValue` computation that fails installs no value. Anchoring the value to the receiver class
  gives independent entries for isolated application classes and lets the VM discard the entry
  when that class unloads. Phase 1 guarantees reuse for sequential calls after successful
  resolution; it does not promise virtual-dispatch single-flight behavior for concurrent first use.
- **Static dispatch:** use a per-adapter/per-member weak-*identity* loader index whose values are
  weak references to the resolved target `Class<?>`. A key compares class loaders only with `==`
  and hashes with `System.identityHashCode`; it must never use a loader's `equals` or `hashCode`.
  Fixed bootstrap and system-loader sentinel keys cover those non-weak identity cases. Expunge
  stale weak keys and values before lookup or insertion. The member's
  `ClassValue<ResolvedCall>` is keyed by that target class and is the sole strong owner of the
  resolved owner/handle pair; the loader index must not contain a `ResolvedCall`. On an index miss,
  load the target class, obtain its `ClassValue` entry, and add the weak loader-to-class index only
  after that lookup resolves successfully. This topology keeps a live loader's call cached through
  its live class without allowing the index to keep the loader alive.
- **Static-cache concurrency:** serialize expunge, index lookup, class resolution,
  `ClassValue.get`, and index insertion on a per-member monitor. This defines single-flight
  resolution for that member (including one loader at a time); a failed resolution leaves no index
  entry and releases the monitor before it is reported. Do not use an unsynchronized check-then-act
  sequence that can publish a partial or failed entry.
- **Failure boundary:** catch only the three resolution exception types above while loading the
  owner or finding the member and wrap them in `ExternalTypeResolutionException`. Do not surround
  `MethodHandle.invoke` with that catch; a target-thrown `Throwable`, including a target-thrown
  `ExternalTypeResolutionException`, passes through exactly as it does today.

`MethodHandleCache` currently uses a `ConcurrentHashMap` keyed by a strong `Class<?>` and stores a
strong `MethodHandle`; a long-lived extension instance can therefore retain an application loader.
Phase 1 does not redesign that pre-existing manual utility. Its loader-safe redesign, if wanted,
needs its own compatibility/lifecycle review. Phase 1 instead documents this limitation and adds
focused unit coverage that successful manual lookups are cached while failed manual lookups remain
uncached.

### Documentation contract

Make `docs/BTraceExtensionDevelopmentGuide.md` the normative `@ExternalType` reference. Make
`docs/architecture/provided-style-extensions.md` the complementary concrete manual-linking guide
and point its adapter-scope discussion back to that normative table. Make the tutorial and
getting-started copies link to the normative guide rather than independently promise features. The
reference must state, without calling deferred items "planned":

| Capability | Phase 1 support | Author action |
| --- | --- | --- |
| Public, uniquely named static or virtual methods with exact erased signatures | Supported | Use `@ExternalType`; use `Object` only where the target member itself uses `Object`. |
| A target method with target-library parameter or return types | Unsupported | Use `ClassLoadingUtil` and `MethodHandleCache` directly. |
| Overloads | Unsupported; processor error | Use the manual helper with exact return and parameter `Class` values; it constructs the exact `MethodType` internally. |
| Fields, constructors, `instanceof`, and casts | Unsupported | Use `ClassLoadingUtil` plus the appropriate manual `MethodHandles`/`Class` operation. `MethodHandleCache` currently caches virtual and static method lookup only. |
| Non-public or non-exported named-module members | Unsupported | Do not add module-opening flags implicitly; use a public supported API or explicitly configure the target JVM outside BTrace. |

The guide must document the virtual-defining-loader/static-TCCL distinction and show
`ClassLoadingUtil.withTCCL(...)` for an author-controlled static-call context. It must also show
the manual helper pattern as the sanctioned normal path for version-variant APIs, including that
`MethodHandleCache` caches only successful lookups so a caught lookup failure remains a retryable
capability check, and disclose its current strong-loader retention. Remove the implication that
generated calls need no error boundary: callers must choose an appropriate extension-level
logging/degradation policy around target interaction. Correct the existing nonexistent
`MethodHandleCache.findGetter`, `findSetter`, and `findConstructor` examples: use direct
`MethodHandles.publicLookup()` examples (or state clearly that no cache helper exists) instead of
documenting APIs that BTrace does not provide.

### Affected components

- `btrace-core`: `ExternalType`, new public `ExternalTypeResolutionException`,
  `ExternalTypeProcessor`, generated adapter emission, `MethodHandleCache` documentation-level
  contract tests, and focused processor/runtime tests. Keep Java 8 source compatibility.
- `btrace-dist` and masked-JAR packaging only if Phase 1 introduces a runtime helper. Validate the
  published `btrace.jar`, not a sibling `btrace-core` classpath, as the external build/runtime
  contract.
- `btrace-gradle-plugin`: only if its processor/runtime artifact wiring needs adjustment; no DSL or
  plugin-ID change is intended.
- `btrace-extensions:btrace-ext-test` and `integration-tests`: extend the real extension/client /
  agent/target path to exercise regenerated static and virtual adapters.
- `docs/BTraceExtensionDevelopmentGuide.md`, `docs/architecture/provided-style-extensions.md`,
  `docs/BTraceTutorial.md`, and `docs/GettingStarted.md`: one canonical reference and concise
  pointers. No new user-documentation hierarchy is needed.

### Phase 1 acceptance criteria

1. Existing interfaces using distinct method names, primitive/JDK/value signatures, virtual
   dispatch, and `@ExternalType.Static` compile and run without source changes.
2. Repeated **sequential** virtual calls resolve a same-named target class independently for each
   of two isolated application loaders; neither loader's target handle is used for the other, and
   each loader's successful `Class.forName`/member resolution occurs once rather than once per
   call. Concurrent first use is intentionally outside this criterion.
3. Repeated static calls do the same through each of two isolated TCCLs. Static dispatch retains
   the documented `null`-TCCL system-loader fallback and does not silently switch to a
   receiver/defining-loader policy.
4. A mutable test loader proves missing-class retry separately for virtual and static paths: an
   initial lookup fails, the same loader exposes the class, and the next invocation succeeds. The
   failed class lookup creates no index or `ClassValue` entry.
5. A missing-member test invokes the adapter twice against loader A and uses a package-private
   resolution test seam to prove two member-lookup attempts (no negative cache). An isolated
   loader B defining the same target name with the member then succeeds, proving A's failure does
   not poison B.
6. Missing-class, missing-member, and inaccessible-public-lookup failures are contextual,
   cause-preserving `ExternalTypeResolutionException`s with the specified message, while a
   target-thrown exception remains transparent.
7. A focused `MethodHandleCache` unit test proves repeated successful static/virtual lookup returns
   the cached handle and repeated failed lookup remains retryable; the test and docs make no
   loader-safety claim for that utility.
8. The end-to-end integration test passes through the staged test extension and the real client,
   agent, and target JVM for one static and one virtual call. If failure behavior is observable on
   this path, add a controlled regression that proves it is contained at the extension boundary;
   detailed lookup classification may remain a `btrace-core` runtime test.
9. Documentation accurately describes the supported boundary, the static-loader caveat, modules,
   retry semantics, and the manual path. It does not claim that unsupported features are imminent.

### Verification

Run from the repository root with `GRADLE_USER_HOME=$(pwd)/.gradle-user`. Redirect each Gradle
invocation to `/tmp/btrace-issue-931-*.log`; use `rg` to extract task/test failures and `BUILD
SUCCESSFUL` before reading the filtered output.

```text
:btrace-core:test
:btrace-core:spotlessCheck
:btrace-dist:build
:btrace-dist:test
:integration-tests:test -Pintegration --tests tests.ExternalTypeAdapterIntegrationTest
```

Build the distribution from clean state with `:btrace-dist:btraceJar`, inspect the resulting masked
JAR/service metadata for `ExternalTypeResolutionException` (and any helper), and run an external
extension compilation proof against the staged/published-style `btrace.jar`. If the restricted
environment shows the known address-selection failure, rerun the same affected command with the
repository's documented IPv4 `JAVA_TOOL_OPTIONS` setting.

## Later, independently gated phases

Later phases are candidates ordered by dependency and risk. Completion of Phase 1 does not commit
their API shape, release, or date.

### Phase 2: explicit static-loader policy

Static dispatch has no receiver from which to derive an application loader. Evaluate a small,
explicit opt-in that lets an author supply a context/loader, while preserving `@ExternalType.Static`
and its TCCL behavior unchanged for source and binary compatibility. This phase must define
class-loader lifecycle/caching, TCCL restoration, and application-server/OSGi coverage before an
API is added. It must not guess a loader, mutate a thread's TCCL globally, or change existing
static adapters' loader policy.

### Phase 3: target-type signatures and chained adapters

Design an explicit representation for a target-library type in parameters and returns, including
how its target `Class` is resolved in the same application loader and substituted into the exact
`MethodType`. Cover nested/chained calls, nulls, same-name types from isolated loaders, generics
erasure, and generated public API leakage. This is deliberately separate from Phase 1 because
using another annotated Java interface as a type has runtime/linkage and compatibility consequences
that cannot be inferred from the current `Object` boundary.

### Phase 4: overload disambiguation

Add an opt-in source-level selector for a target member name/signature, retaining the existing
compile-time error for ambiguous legacy declarations. The selector must map to an exact erased
`MethodType`, have diagnostics for duplicate/invalid selectors, and coexist with Phase 3 target
type modelling. It must never select an overload by runtime argument coercion.

### Phase 5: fields, constructors, and type predicates

Introduce narrowly scoped adapter operations for public getter/setter, constructor, and
`isInstance`/cast behavior only after their generated API, null/error behavior, and access rules
are reviewed. This phase may extend the manual `MethodHandleCache` API where needed, but must not
turn annotations into a general reflection language or silently initialize target classes.

### Phase 6: non-public members and JPMS access (only with a security design)

No phase may use `privateLookupIn`, `--add-opens`, `--add-exports`, instrumentation-based module
rewrites, or privileged lookup merely to make an adapter work. If a real supported integration
requires it, create a separate security/release design covering explicit operator authority,
minimum module/package scope, JDK-version behavior, revocation, diagnostics, and integration
coverage. Until then, public exported target APIs are the boundary.

## Non-goals

- Do not change extension loading, classpath/bootclasspath injection, service discovery,
  permissions, attach/protocol behavior, or the extension Gradle DSL in Phase 1.
- Do not make target-library classes compile-time dependencies of ordinary extensions.
- Do not automatically open/read named modules, access private members, or broaden BTrace's
  privileges.
- Do not cache failed lookup results, since lazy application loading and capability probing depend
  on retry.
- Do not bundle test-only extensions into the release distribution; retain the isolated staging
  path used by `ExternalTypeAdapterIntegrationTest`.

## Compatibility, migration, release, and security boundaries

Phase 1 preserves the annotation, generated class naming, method shape, virtual defining-loader
behavior, and static TCCL behavior. Existing already-built extensions retain their generated
implementation until rebuilt; rebuilding against Phase 1 gains the cache/failure hardening without
an author migration. The only intentional observable failure change is that adapter resolution
does not leak an undeclared checked `ClassNotFoundException`; extension authors who deliberately
inspect that implementation detail must instead handle the documented unchecked resolution
`ExternalTypeResolutionException`/cause. Target-method exceptions retain their current propagation.

The external consumer contract is the published masked `io.btrace:btrace` artifact and its narrow
annotation-processor/runtime surface. `ExternalTypeResolutionException` is intentionally the only
new public adapter runtime type; any helper introduced by this work must be present there without
accidentally exposing unrelated core/client classes. No new permissions, network surface, target
JVM flags, authentication/trust decisions, module bypasses, or classpath injection are authorized
by this issue. The `publicLookup()` restriction is a security boundary, not a defect to be bypassed
silently.
