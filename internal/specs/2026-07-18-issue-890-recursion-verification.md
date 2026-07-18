# Issue #890: recursion verification

Date: 2026-07-18  
Status: implementation contract for [#890](https://github.com/btraceio/btrace/issues/890)

## Scope

Reject handler-reachable direct and mutual same-script recursion at both verification boundaries.
This restores the bounded/no-loops guarantee before a hot probe can consume application-thread
stack and CPU.

## Two-layer design

### Source verifier

Extend `btrace-compiler/src/main/java/io/btrace/compiler/VerifierVisitor.java` with a per-BTrace
class graph collected from attributed `MethodInvocationTree` nodes. Use resolved
`ExecutableElement` caller/callee identity, including descriptor/signature identity for overloads,
and add an edge only when the callee is declared by the same BTrace class. Mark BTrace-annotated
handler methods as roots. After scanning the class, reject a direct self-edge or any cycle
reachable from a handler root.

This keeps valid acyclic helpers legal and excludes BTrace utilities, services, JDK calls, and
other-class calls from script graph edges. Trusted/unsafe scripts retain their current verifier
bypass; #890 does not change that policy.

### Agent bytecode verifier

Keep the agent as the authoritative defense for bytecode that bypasses source compilation. In
`btrace-agent/src/main/java/io/btrace/instr/BTraceMethodNode.java`, record graph edges only for
`INVOKESTATIC` calls whose translated owner is the probe's own class, retaining method-name plus
descriptor IDs and existing handler roots. In
`btrace-agent/src/main/java/io/btrace/instr/CallGraph.java`, make a singleton self-edge a cycle
instead of discarding every graph smaller than two nodes. The class-level `Verifier.visitEnd()`
continues its whole-class, reachability-aware decision after all methods have been read.

Apply the equivalent whole-class check to an accepted persisted-probe verifier path in
`BTraceProbePersisted`, so serialized legacy input cannot bypass the agent safety boundary. Do not
turn a same-named external static call into a local edge; owner filtering prevents false positives.
`BTraceProbePersisted.isVerified()` currently converts `VerifierException` into `false`; correct
the accepted `checkVerified()` path so a false bytecode-verification result is rejected by throwing
the verification failure rather than being silently discarded. A detected persisted recursive cycle
must therefore fail the same agent acceptance boundary as a node-format probe.

## Error behavior and compatibility

Both layers fail with the existing `execution.loop.danger` message and normal mechanisms
(compiler diagnostic/failed compilation; agent `VerifierException`). Diagnostics may add method or
cycle context. No runtime `StackOverflowError` handling, target behavior, options, or protocol
change is needed.

## Affected components and tests

- Extend `btrace-compiler/src/test/java/io/btrace/compiler/BTraceDslVerifierTest.java`: direct
  recursion and two-method mutual recursion fail with `execution.loop.danger`; acyclic helpers and
  overload-distinct non-cycles pass.
- Update `CallGraph`, `BTraceMethodNode`, class-level agent `Verifier`, and the persisted verifier
  path. Extend `btrace-agent/src/test/java/io/btrace/instr/CallGraphTest.java` for a singleton
  self-loop.
- Extend `btrace-agent/src/test/java/io/btrace/instr/MethodVerifierDslTest.java` (or a focused
  sibling) with handcrafted non-trusted direct and mutual recursive probe bytes. Factory creation
  plus `checkVerified()` must throw `VerifierException` containing `execution.loop.danger`; an
  acyclic same-class helper must pass. Add a persisted-format regression that proves a failed
  `isVerified()` result, including a recursive cycle, makes `checkVerified()` throw/reject rather
  than return successfully.

These two verifier-layer tests are the acceptance boundary: source tests prove normal compilation
fails before submission, while bytecode tests prove the agent rejects crafted/precompiled input.

## Non-goals

- Do not ban all same-class helpers, trusted-script recursion, or legal acyclic calls.
- Do not analyze inter-class, virtual/interface, reflection, invokedynamic, callback, or
  extension-service graphs.
- Do not alter tutorial wording already corrected, allocation/autoboxing policy, runtime guards,
  instrumentation, attach behavior, or release packaging.

## Acceptance, verification, and boundaries

- Both layers reject non-trusted direct and mutual handler-reachable recursion; acyclic helpers
  continue to compile and verify.
- The agent rejects raw/precompiled recursive bytecode even if source verification is bypassed.
- Persisted probes whose verification fails are rejected by `checkVerified()`; they cannot pass
  merely because `isVerified()` returned `false` after catching `VerifierException`.
- Run `:btrace-compiler:test` and `:btrace-agent:test`, plus scoped Spotless checks, with
  `GRADLE_USER_HOME=$(pwd)/.gradle-user`. Redirect each Gradle command to a log, filter with `rg`
  for selected tests, failures, and `BUILD SUCCESSFUL`, and inspect the filtered output. Retry the
  documented address-selection failure with the repository IPv4 `JAVA_TOOL_OPTIONS`.

This is a backward-compatible safety tightening for non-trusted scripts. It changes no credentials,
permissions, authentication, networking, target authorization, protocol, or release boundary. It
prevents user-provided script bytecode from creating unbounded stack/CPU pressure on traced threads.
