# Issue #931, Phase 2: explicit static ExternalType loader selection

Date: 2026-08-07
Status: design proposal; implementation must not start until design review is clean
Prerequisite: Phase 1 of #931 merged as #941

## Problem and goal

Phase 1 deliberately retained the historical static-adapter policy: a generated dispatcher for an
ExternalType.Static method loads its target through the calling thread context class loader (TCCL),
falling back to the system loader only when TCCL is null. This is source and binary compatible, but
TCCL is ambient state and is frequently wrong in application servers, OSGi, and plugin containers.
A static call has no receiver from which to derive an application defining loader.

This phase adds an explicit, per-invocation application-loader selection for generated static
adapters. It does not mutate ambient thread state, and it retains the old generated dispatcher
unchanged for existing callers.

## Decision: a generated leading-ClassLoader overload

For each non-overloaded ExternalType.Static interface method, the processor generates:

- the existing static dispatcher, with only the annotated target method parameters; and
- an additional public static dispatcher with a leading ClassLoader applicationLoader parameter,
  followed by those same target parameters.

For example, an annotated static method current(int format) generates current(int format), which
keeps the legacy TCCL policy, and current(ClassLoader applicationLoader, int format), which uses the
supplied loader exactly. The leading loader is adapter control data: it is absent from MethodType
and is not sent to MethodHandle.invoke.

The interface deliberately remains a faithful target-member signature. A target method that itself
takes ClassLoader remains unambiguous: install(ClassLoader targetArgument) has the legacy generated
form install(ClassLoader targetArgument), and the explicit form
install(ClassLoader applicationLoader, ClassLoader targetArgument).

### Options assessed

| Option | Decision | Evidence |
| --- | --- | --- |
| Generated leading-ClassLoader overload | Chosen | The generated class is already the static dispatch boundary. This adds no annotation syntax, helper object, or runtime public type; it is explicit at each call site and shares Phase 1's loader-keyed cache. |
| Parameter annotation such as ExternalType.Loader | Rejected | It inserts adapter control data into an interface intended to mirror the exact target signature, needs validation/diagnostics, and obscures a real target ClassLoader parameter. |
| Explicit context object or builder | Rejected | There is only one required value. A context type adds release surface, allocation, and loader-lifetime questions without a needed policy dimension. |
| Change Static to a defining-loader policy | Rejected | A static call has no defining object. Changing the established TCCL behavior would break generated-extension compatibility. |
| Annotation enum for TCCL/system/bootstrap | Rejected | It cannot select a container-specific application loader and makes a runtime choice a fixed declaration. |

Author guidance: derive the explicit argument from a known application object with
ClassLoadingUtil.definingLoader(applicationObject). That helper returns null for a bootstrap-owned
object, while the explicit overload deliberately rejects null. In that case normalize the selected
loader to ClassLoader.getSystemClassLoader(), whose normal delegation reaches bootstrap, before
calling the adapter. Use the legacy method only when TCCL is intentionally the policy. Do not
substitute the extension implementation's own loader.

## Exact behavior

### Existing no-loader static dispatcher

The existing generated method obtains Thread.currentThread().getContextClassLoader(). If it is
null, it substitutes ClassLoader.getSystemClassLoader(). It then loads the owner, performs the
existing publicLookup().findStatic exact-erased-signature lookup, and invokes it. This remains the
behavior of both newly regenerated and already-built adapters. There is no bootstrap reinterpretation,
alternate fallback search, or change to its descriptor.

### New explicit-loader static dispatcher

The new overload must:

1. Reject a null applicationLoader with NullPointerException before any cache mutation, class load,
   or member lookup. The generated check uses the stable parameter name applicationLoader.
2. Resolve the owner only with Class.forName(OWNER, false, applicationLoader).
3. Use the same MethodHandles.publicLookup().findStatic exact MethodType as Phase 1.
4. Invoke the handle using only the original annotated parameters.

Null deliberately means no policy; it does not select bootstrap, system, or TCCL. It is too easy to
pass accidentally and each fallback has a different observable result. Bootstrap members remain
reachable through a non-null system loader, whose normal delegation reaches bootstrap. Legacy
null-TCCL behavior still falls back to system.

Neither overload calls Thread.setContextClassLoader at any point. Target code must therefore
observe the caller's unchanged TCCL, there is no restoration responsibility, and shared executor
threads cannot leak context across invocations.

### Failure, security, and module boundaries

Phase 1 remains the complete lookup failure contract. Class-not-found, no-such-method, and
IllegalAccessException while resolving the owner/member become ExternalTypeResolutionException,
with the established message and original cause. Failures are not cached. Target-thrown exceptions,
including target-thrown ExternalTypeResolutionException, propagate unchanged.

Supplying a loader selects only the owner-resolution namespace. Lookup stays publicLookup: it does
not add module read/export edges, use privateLookupIn, access non-public members, change classpaths,
or bypass a SecurityManager. Existing JVM permission checks remain observable and must not be
caught/wrapped as resolution failures.

## Cache, lifecycle, and concurrency

The Phase 1 static cache is retained per generated adapter member:

- Its weak-identity loader-to-owner-class index has weak values and bootstrap/system sentinel keys.
- The owner ClassValue holding ResolvedCall is the sole strong owner of the resolved owner/handle
  pair. The index must not retain ResolvedCall.
- Expunge, lookup, Class.forName, ClassValue.get, and insertion remain serialized on the existing
  per-member monitor. Insert only after class and member resolution both succeed.
- The explicit overload passes its supplied loader to this same resolver. It must not create a
  second cache, a thread-local cache, or a strong ClassLoader map.

Thus an explicit and legacy invocation that select the same loader share a cache entry; isolated
loaders defining the same owner FQN use different handles. Concurrent first calls through either
overload are single-flight per member. Failed explicit resolution creates no index/ClassValue entry
and retries on a later invocation.

## Compatibility and release surface

- Source: existing annotations, direct qualified calls to generated no-loader methods, virtual
  dispatch, target matching, and the extension Gradle DSL remain unchanged. Adding a same-name
  overload can make a separately compiled consumer that uses wildcard static imports ambiguous if
  another wildcard-imported type already contributes the new-arity signature. This is a Java source
  compatibility caveat for regeneration, not a behavior change in a direct legacy call. Document
  it and advise qualified generated-adapter calls (or an explicit single-member static import).
- Binary: old generated adapters continue unchanged. New generated adapters preserve every old
  descriptor and add only a leading-ClassLoader overload. Rebuilding an extension is required only
  to call the new overload.
- Processor/runtime: this is a processor-output change only. Do not add an annotation, helper,
  service descriptor, bootstrap-visible class, or Java-11 API. Generated source stays Java 8.
- Published artifact: validate against the masked io.btrace:btrace JAR, which already carries the
  processor service from Phase 1, not directly against btrace-core. No distribution or plugin
  wiring change is expected unless this validation proves one is necessary.
- Documentation: the canonical ExternalType section in docs/BTraceExtensionDevelopmentGuide.md
  documents both overloads, the null contract, and loader selection. Replace its
  ClassLoadingUtil.withTCCL workaround for generated static calls with the new overload, while
  retaining withTCCL for manual APIs. Update docs/architecture/provided-style-extensions.md to
  point to that canonical explanation. Change tutorial/getting-started only if they independently
  state the old workaround.

## Scope

In scope:

- Generate the loader-selecting overload for each generated static dispatcher.
- Preserve the exact Phase 1 legacy dispatch path and route both overloads through the existing
  loader-safe cache.
- Test null/failure/cache/concurrency semantics, external artifact packaging, documentation, and
  the full extension-client-agent-target integration path.

Out of scope:

- New interface annotations, context objects, processor options, Gradle DSL/configuration,
  fallback search, automatic loader selection, or target-loader guessing.
- Virtual dispatch changes; target-library signature types/chaining; overload selectors; fields,
  constructors, casts, and predicates.
- Non-public/JPMS access; module or classpath mutation; security bypass; extension/agent loading
  changes; redesigning MethodHandleCache.

## Affected components

- btrace-core: AdapterEmitter and focused ExternalTypeProcessor tests. ExternalType,
  ExternalTypeResolutionException, and ClassLoadingUtil need no API change.
- docs/BTraceExtensionDevelopmentGuide.md and
  docs/architecture/provided-style-extensions.md.
- btrace-dist, btrace-gradle-plugin, and integration-tests for validation/staging only unless a
  real artifact-level wiring defect is demonstrated.

## Acceptance criteria

### Processor and runtime tests

1. Generated-source assertions prove both descriptors: legacy target-arguments-only and explicit
   leading-ClassLoader. They prove the control argument is not present in MethodType or invoke.
2. A hostile isolated loader defines the target where a deliberately wrong TCCL cannot find it.
   The explicit call succeeds with a loader-specific result; the legacy call retains TCCL behavior
   and fails with the established resolution exception.
3. Two isolated loaders defining the same FQN return their own values via the explicit overload.
   Repeated calls resolve once per loader; an explicit and legacy call selecting the same loader
   share one entry. Fixtures whose equals/hashCode throw preserve the identity-cache guarantee.
4. Concurrent first use through legacy and explicit forms with one loader resolves once under the
   member monitor. Repeat across distinct loaders to prove no handle crosses loader boundaries.
5. Explicit null throws NullPointerException before fixture class-load/member-lookup counters move.
   Separately retain Phase 1's legacy null-TCCL system-loader fallback test.
6. A bootstrap-owned target is called through the explicit overload with
   ClassLoader.getSystemClassLoader(), including the documented normalization of a null result from
   ClassLoadingUtil.definingLoader; it must resolve successfully without treating null as a loader
   selector.
7. A target signature with a real ClassLoader parameter compiles and invokes correctly through
   both generated forms, proving that the leading control argument is distinct and target arguments
   are forwarded.
8. Missing class/member/access, retry-after-class-appears, and target-thrown failures remain valid
   for the explicit form as appropriate. Failed explicit lookup must never be negatively cached.

### Integration and release tests

9. Split ExternalTypeAdapterIntegrationTest into two independently triggered target/probe
   scenarios. The existing resources.Main.probeExternal scenario keeps its normal application TCCL
   and proves legacy static TCCL dispatch plus virtual dispatch both succeed. A distinct target
   entry point (for example, probeExternalExplicit) sets a deterministic non-target TCCL and drives
   only the explicit-loader static adapter; it proves the explicit call emits the target-owned
   marker and that target code observes its TCCL unchanged. Do not combine the incompatible TCCL
   assumptions in one probe invocation.
10. From clean state build btrace-dist:btraceJar, inspect the masked JAR for the processor service
   and processor implementation, then compile and run a minimal external extension against that
   JAR that calls the new overload.
11. Run btrace-core:test, btrace-core:spotlessCheck, clean btrace-dist:btraceJar,
    btrace-dist:build, and the focused integration test with -Pintegration. Use a workspace-local
    GRADLE_USER_HOME, redirect each Gradle run to /tmp, and filter relevant output before reading.

## Completion boundary

Phase 2 is complete only when the generated leading-ClassLoader overload, unchanged legacy
overload, weak-identity cache behavior, null/error semantics, documentation, masked-artifact proof,
and end-to-end integration path satisfy these criteria. It does not authorize later #931 phases.
