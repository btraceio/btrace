# Issue #890: recursion verification implementation plan

## Prerequisites and boundaries

- Implement only the contract in
  `internal/specs/2026-07-18-issue-890-recursion-verification.md`; preserve unrelated changes.
- Enforce handler-reachable, same-script static direct/mutual cycles for non-trusted probes. Keep
  acyclic helpers and current trusted/unsafe bypass behavior compatible.
- Reuse the established `execution.loop.danger` diagnostic and do not expand the work into
  inter-class dispatch, reflection, invokedynamic, runtime guards, protocol, or documentation.

## Ordered implementation and gates

1. Add source-level graph collection and rejection.

   - Update `btrace-compiler/src/main/java/io/btrace/compiler/VerifierVisitor.java` to collect a
     per-class graph while visiting attributed method invocations. Use resolved
     `ExecutableElement` identity for caller/callee (including overload descriptors), record only
     calls whose callee is declared by the active BTrace class, and mark BTrace handler methods as
     roots.
   - After all members of that BTrace class have been scanned, detect direct self-recursion and
     handler-reachable mutual cycles. Report through the existing source error path with
     `execution.loop.danger`; preserve existing same-class acyclic-call allowance.
   - Extend `btrace-compiler/src/test/java/io/btrace/compiler/BTraceDslVerifierTest.java` with
     direct recursion and two-method mutual-recursion source strings that fail, plus acyclic and
     overload-distinct controls that compile successfully.

   **Gate:** compilation rejects both recursive forms with the established message before any
   bytecode is submitted, without rejecting valid helpers or external/BTrace utility calls.

2. Correct the agent graph used by node-format bytecode verification.

   - In `btrace-agent/src/main/java/io/btrace/instr/BTraceMethodNode.java`, add graph edges only
     for same-probe-class `INVOKESTATIC` instructions after owner translation; retain method name
     plus descriptor IDs and existing handler-start roots.
   - In `btrace-agent/src/main/java/io/btrace/instr/CallGraph.java`, recognize a one-node self-edge
     as a cycle rather than returning false solely because the graph has fewer than two nodes.
     Preserve the existing handler-reachability logic for mutual cycles and non-reachable helper
     graphs.
   - Keep the class-level decision in `btrace-agent/src/main/java/io/btrace/instr/Verifier.java`
     after all methods have been visited; do not attempt immediate per-instruction rejection.
   - Extend `btrace-agent/src/test/java/io/btrace/instr/CallGraphTest.java` with a direct self-loop
     regression and `btrace-agent/src/test/java/io/btrace/instr/MethodVerifierDslTest.java` (or a
     focused sibling) with handcrafted non-trusted direct/mutual recursive probe bytecode,
     same-named external-call false-positive coverage, and an acyclic helper control.

   **Gate:** `BTraceProbeFactory.createProbe(...).checkVerified()` throws `VerifierException`
   containing `execution.loop.danger` for direct and mutual crafted bytecode, but passes for
   acyclic local helpers and same-named external calls.

3. Close the persisted-probe bypass.

   - Update `btrace-agent/src/main/java/io/btrace/instr/BTraceProbePersisted.java` so persisted
     bytecode receives an equivalent same-class, handler-reachable cycle check.
   - Preserve `isVerified()`'s boolean query role if required by callers, but retain/cache the
     original `VerifierException` it catches (or deterministically rerun verification) so
     `checkVerified()` can throw that failure when verification returns false. It must not discard
     a false result or replace the established recursion diagnostic with an unrelated generic
     failure.
   - Add a focused persisted-format regression test, preferably
     `btrace-agent/src/test/java/io/btrace/instr/BTraceProbePersistedTest.java`, that feeds an
     invalid recursive persisted probe (and a general failed-verification case where practical),
     asserts `isVerified()` is false, and asserts `checkVerified()` throws `VerifierException`
     containing `execution.loop.danger`. Include a valid persisted control if the fixture format
     supports one.

   **Gate:** legacy serialized input cannot enter the accepted agent path after a recursion or any
   bytecode-verification failure merely because the boolean result was ignored; recursive input
   retains the `execution.loop.danger` diagnostic at `checkVerified()`.

4. Inspect compatibility and final behavior.

   - Confirm non-trusted source and bytecode paths use the same `execution.loop.danger` message;
     retain trusted/unsafe bypass and no-loop checks unrelated to call graphs.
   - Confirm no new dependencies, public APIs, messages, packaging, or integration-test attach
     scenario were introduced. This is a compiler/agent verification-boundary change; the focused
     source and raw-bytecode tests prove both independently.

   **Gate:** `git diff --check` is clean and every design acceptance condition is represented by a
   source or bytecode regression test.

## Verification sequence

Run from the repository root with a workspace-local Gradle cache. Redirect every Gradle invocation
to a file, filter it with `rg`, then read the filtered output. If a run hits the documented
address-selection issue, retry that exact command with
`JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"`.

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-compiler:test > /tmp/btrace-issue-890-compiler.log 2>&1
rg -n "BUILD (SUCCESSFUL|FAILED)|FAILED|ERROR|BTraceDslVerifierTest|tests" /tmp/btrace-issue-890-compiler.log
```

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-agent:test > /tmp/btrace-issue-890-agent.log 2>&1
rg -n "BUILD (SUCCESSFUL|FAILED)|FAILED|ERROR|CallGraphTest|MethodVerifierDslTest|BTraceProbePersistedTest|tests" /tmp/btrace-issue-890-agent.log
```

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-compiler:spotlessCheck :btrace-agent:spotlessCheck > /tmp/btrace-issue-890-spotless.log 2>&1
rg -n "BUILD (SUCCESSFUL|FAILED)|FAILED|ERROR|spotless" /tmp/btrace-issue-890-spotless.log
```

Finish with `git diff --check` and inspect the diff for unintended verifier-policy or descriptor
changes.

## Stop conditions

- Stop and return to design review if correct source attribution cannot distinguish same-class
  overloads, if a proposed graph would reject external/static utility calls, or if handler
  reachability cannot be maintained.
- Stop if persisted-format rejection requires changing trusted/unsafe policy, a wire-format
  migration, or a public BTrace API; determine a compatible rejection point first.
- Stop if complete protection requires inter-class, virtual, reflection, callback, or
  invokedynamic analysis; those are explicitly outside #890.
- Do not commit until all applicable gates and verification commands pass, unless the user
  explicitly directs otherwise.

## Completion criteria

- Source verification rejects handler-reachable direct and mutual same-script recursion with
  `execution.loop.danger`, while acyclic helpers and overload controls pass.
- Node and persisted agent bytecode paths reject the same recursive input; a false persisted
  `isVerified()` result always makes `checkVerified()` reject with the original or deterministically
  reproduced `execution.loop.danger` failure rather than silently succeeding.
- Agent graph edges are owner-filtered and direct self-loops are detected without false positives.
- Compiler tests, agent tests, scoped Spotless, and final diff inspection are clean; no trusted
  policy, descriptor, packaging, runtime, or protocol scope changes are present.
