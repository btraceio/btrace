---
spec_id: REQ-btraceio-btrace-802
source: github
source_ref: "btraceio/btrace#802"
title: "Phase 3: INVOKEDYNAMIC handler isolation"
status: implementing
clarity_score: 82
created: 2026-04-11
implementing_session: impl-1775895298
implemented_pr: null
---

# Phase 3: INVOKEDYNAMIC handler isolation

Replace INVOKESTATIC handler copying with INVOKEDYNAMIC dispatch. Probe handler methods now stay in the probe class (bootstrap CL) and are called via ConstantCallSite, eliminating bytecode copying into target classes. IndyDispatcher must work from Java 8+.

New files: IndyDispatcher, HandlerRepository (interface), HandlerRepositoryImpl, DispatchBenchmark, DispatchTarget, Workload, DispatchScript
Deleted: CopyingVisitor, Indy, ~400 redundant golden files (replaced with unified golden files covering all combinations)
Refactored: Instrumentor, Assembler, BTraceProbeNode, BTraceProbePersisted, BTraceRuntimeImpl_9/_11

Dispatch chain: InstrumentedMethod → INVOKEDYNAMIC → IndyDispatcher.bootstrap() → HandlerRepositoryImpl.resolveHandler() → MethodHandles.publicLookup().findStatic() → ConstantCallSite

Integration fixes:
1. AnyType descriptor transformation in BTraceProbeNode.getBytecode() and BTraceProbePersisted.register() — Lorg/openjdk/btrace/core/types/AnyType; → Ljava/lang/Object;
2. StackWalker auxiliary frame skipping in BTraceRuntimeImpl_9/_11 for getCallerClassLoader() and getCallerClass()
3. HandlerRepositoryImpl cleanup: use ConcurrentHashMap with sentinel value for failed lookups instead of null; clean findStatic lookup with warn-on-failure

Review fixes:
- Symmetric probe lifecycle: unregisterProbe() in both BTraceProbeNode.unregister() and BTraceProbePersisted.unregister(); removed premature registerProbe from BTraceProbeFactory.createProbe()
- COMPUTE_FRAMES=0 in transformAnyTypeDescriptors() (only descriptor changes, no control flow)
- Remove redundant unregisterProbe from Client.onExit()

Test plan:
- gradlew :btrace-instr:test — all instrumentor tests pass
- gradlew :integration-tests:test -Pintegration — all 22 integration tests pass (including Docker)
- gradlew :benchmarks:runtime-benchmarks:jmh -PjmhInclude=DispatchBenchmark — benchmarks stable
- Automated test: verify attach/detach cycle cleans up all handler cache entries in HandlerRepositoryImpl

## Acceptance Criteria

- [ ] IndyDispatcher works from Java 8+
- [ ] All ~400 redundant golden files are replaced with unified golden files that cover all probe type combinations
- [ ] Automated test verifies attach/detach cycle cleans up all handler cache entries in HandlerRepositoryImpl
- [ ] HandlerRepositoryImpl uses ConcurrentHashMap with sentinel value for failed lookups instead of null
- [ ] All 22 integration tests pass
- [ ] All instrumentor tests pass
- [ ] DispatchBenchmark benchmarks remain stable
