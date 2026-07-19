# Issue #891: Integration coverage for protocol negotiation and `@OnProbe`

Date: 2026-07-18  
Status: approved implementation specification

## Scope

Close the two release-critical coverage gaps identified in #891:

1. Exercise the shipped V2, auto-negotiating client configuration and a V1 client against the normal V2-capable dynamically attached agent on every `:integration-tests:test` run.
2. Stop reporting `@OnProbe` as a passing test on runtimes where its JAXB-backed XML descriptor support is unavailable.

The existing `continuous.yml` integration-test matrix already runs for pushes and pull requests to `develop`; the new cases belong in that suite rather than in the label-gated `v2-protocol-tests.yml` workflow.

## Protocol coverage design

Add two dynamic-attach variants to the functional integration suite.  Each must start the usual
`resources.Main` target, submit a real compiled probe through the external BTrace client process,
wait for observable probe output, and fail on client error or missing output.  The target keeps its
normal agent configuration (V2-capable with negotiation); do not introduce a special test-only
agent implementation.

| Case | External client properties | Required result |
| --- | --- | --- |
| Shipped default | `btrace.comm.protocol=2`, `btrace.comm.autoNegotiate=true`, `btrace.comm.forceVersion=false` | The V2 client-agent negotiation completes and the probe produces its expected output. |
| V1 compatibility | `btrace.comm.protocol=1`, `btrace.comm.autoNegotiate=false`, `btrace.comm.forceVersion=true` | The V1 client communicates successfully with that same V2-capable agent and the probe produces its expected output. |

`RuntimeTest.classSetup()` may retain its deterministic suite defaults, but the dynamic-attach
launcher must accept per-invocation protocol options and pass those exact `-D` values to the
spawned client JVM.  Changing system properties only in the JUnit JVM is insufficient because the
client is an external process.  The existing hard-coded V2 client flags in `attach()` must therefore
be replaced or overridden only for these explicitly selected variants; existing tests retain their
current forced-V2 behavior.

The test assertions must prove the end-to-end path, not merely process startup or a unit-level
`ProtocolNegotiator` result: wait for the probe's known marker(s), assert the client did not report
`FAILED`, and assert that stderr is empty apart from the harness's existing filtered JVM warnings.

## `@OnProbe` behavior by runtime capability

`@OnProbe` maps annotations through an XML descriptor and `BTraceProbeNode` enables that mapping
only when `javax.xml.bind.JAXBException` is loadable.  The present test instead uses the legacy
`<java-home>/jre` layout as a proxy and otherwise prints a message then returns successfully.

For this issue, retain the working Java-8 functional path and replace the silent modern-JDK return
with a JUnit assumption that is visible as an aborted/skipped test in reports.  The assumption must
test the actual JAXB capability used by the feature, not the directory layout.  Its reason must say
that `@OnProbe` needs JAXB/XML probe-descriptor support and identify the unavailable capability.

When the capability is present, `testOnProbe` must still dynamically attach
`btrace/OnProbeTest.java`, wait for both `[this, noargs]` and `[this, args]`, reject `FAILED` output
and unexpected stderr, and assert both markers.  When it is absent (including the current modern
JDK matrix), execution must be reported as an explicit JUnit assumption rather than as a successful
test.  No `System.err.println(...); return;` substitute is acceptable.

Bundling or otherwise restoring JAXB support on modern JDKs is deliberately not part of this issue:
it changes the masked distribution/runtime dependency contract and needs separate artifact-level
design and validation.  Once that work exists, this assumption should naturally become satisfied
and the unchanged functional assertions will exercise the modern path.

## Acceptance and verification

- A normal `-Pintegration :integration-tests:test` run includes both protocol cases; no PR label,
  weekly workflow, or manual dispatch is required.
- The default case uses exactly `V2 + autoNegotiate=true + forceVersion=false` in the external
  client process, and the V1 case uses exactly the V1 forced configuration listed above.
- Each protocol case proves a completed dynamic attach and observed probe output against a real
  V2-capable agent; a connection-only assertion is insufficient.
- `@OnProbe` executes and asserts both XML mappings whenever JAXB is available.  Where it is not,
  the JUnit report records an assumption/abort with the stated JAXB reason, never a green no-op.
- Run the focused integration test class with the integration profile, then the relevant normal
  integration test command used by CI.  Use the repository build prerequisites (`:btrace-dist:build`
  before integration testing) and `spotlessCheck`; inspect redirected Gradle logs for the result.

## Non-goals

- Do not change the default protocol configuration or the proven client/agent negotiation wiring.
- Do not move the comprehensive protocol workflow onto every PR, duplicate its unit coverage, or
  add a full protocol-version cross-product.
- Do not add JAXB dependencies, change masked-JAR contents, or claim modern-JDK `@OnProbe` support.
- Do not add coverage for `@OnEvent`, `@OnLowMemory`, `@OnError`, or unrelated verifier gaps.
