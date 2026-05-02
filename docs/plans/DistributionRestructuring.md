# Plan: InvokeDynamic Isolation (Distribution Phase 3)

**Status:** Not Started
**Target:** v3.x (long-term)
**Prerequisites:** Masked JAR architecture (completed, see [MaskedJarArchitecture.md](../architecture/MaskedJarArchitecture.md))

## Background

The current masked JAR architecture reduces bootstrap classloader pollution to ~112 classes. This plan proposes further reducing the bootstrap footprint to ~50 KB by replacing direct method calls in instrumented bytecode with `invokedynamic` instructions, following the approach pioneered by Elastic APM.

## Goal

Reduce the bootstrap classloader footprint from ~112 classes (~200 KB) to a single dispatcher class (~50 KB) by routing all instrumented bytecode through `invokedynamic`.

## Architecture

Based on Elastic APM's approach: instrumented bytecode uses `INVOKEDYNAMIC` instead of direct calls to BTrace runtime API. A tiny `IndyDispatcher` in the bootstrap classloader resolves these calls at first invocation.

```
btrace-bootstrap-minimal.jar (~50 KB)
└── org/openjdk/btrace/indy/
    └── IndyDispatcher.java         # Only class in bootstrap CL
```

**Current:** Instrumented bytecode directly calls BTrace runtime API (requires API in bootstrap CL).
**Proposed:** Instrumented bytecode uses `INVOKEDYNAMIC`, resolved by `IndyDispatcher`. The actual runtime API lives in an isolated classloader, not bootstrap.

### Benefits

- Minimal bootstrap footprint (~50 KB vs ~200 KB)
- Better classloader isolation (no BTrace types leaked to application)
- No dependency shading needed (runtime in isolated CL)
- JIT can inline through `invokedynamic` (no performance loss after warmup)
- Cleaner architecture aligned with industry best practices

### Risks

- Requires rewriting all instrumentation code generation
- Higher implementation complexity
- Requires Java 7+ (already satisfied: BTrace requires Java 8+)
- Debugging instrumented code is harder (indirect calls)
- First-call overhead from `invokedynamic` resolution (mitigated by JIT)

## Implementation Outline

1. Create `IndyDispatcher` bootstrap class with `CallSite` factory methods
2. Modify `Instrumentor` to emit `INVOKEDYNAMIC` instead of `INVOKESTATIC` for BTrace runtime calls
3. Move runtime API out of bootstrap into agent-isolated classloader
4. Update `MaskedClassLoader` to serve as the isolated runtime CL
5. Remove bootstrap classes except `IndyDispatcher`
6. Comprehensive testing: all instrumentation tests must pass with new dispatch

## References

- [Elastic APM invokedynamic Blog](https://www.elastic.co/blog/embracing-invokedynamic-to-tame-class-loaders-in-java-agents)
- [OpenTelemetry Java Agent Structure](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/javaagent-structure.md)
- [Masked JAR Architecture](../architecture/MaskedJarArchitecture.md) (current approach)
