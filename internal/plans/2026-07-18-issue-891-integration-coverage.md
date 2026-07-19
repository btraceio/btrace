# Issue #891 implementation plan: integration coverage

Date: 2026-07-18  
Specification: `internal/specs/2026-07-18-issue-891-integration-coverage.md`

## Outcome

Every normal integration-test run proves both the shipped V2 auto-negotiating client path and a
forced-V1 client path against the dynamic V2-capable agent.  `@OnProbe` either executes its two
descriptor mappings or is reported by JUnit as an explicit JAXB-capability assumption; it cannot
silently pass without running.

## Implementation steps

1. Add explicit dynamic-attach transport settings to `RuntimeTest`.

   - Introduce a small internal value object (or equally contained representation) for the three
     protocol JVM properties and its ordered `-D` arguments.
   - Keep the current forced-V2 settings as the default for both roles, and reset those fields in
     `reset()` so all pre-existing dynamic tests retain their current behavior.
   - Add a protected test hook that selects client settings separately from target-agent settings.
     Do not rely on mutating the test JVM's system properties: the client is launched by `attach()`
     as a separate process, while the target JVM is constructed in `testDynamic()`.
   - In `testDynamic()`, append the selected agent arguments after `extraJvmArgs` when constructing
     the target command.  `classSetup()` currently snapshots `btrace.*` properties into
     `extraJvmArgs`; placing the per-invocation values later deliberately overrides that inherited
     forced-V2 baseline without changing unrelated test propagation.
   - In `attach()`, replace the three unconditional V2/`false`/`true` arguments with the selected
     client arguments.  Leave `attachOneliner()`, startup-agent launch behavior, port handling, and
     non-protocol client flags unchanged.

2. Add the two end-to-end protocol cases in `BTraceFunctionalTests`.

   - Add a small shared dynamic-test helper or two narrowly named test methods that configure the
     hook from step 1, submit the existing timer probe to `resources.Main`, and use its established
     `vm version`, `vm starttime`, and `timer` markers as completion/validation evidence.
   - Case A sets both external client and target agent to the shipped settings:
     `protocol=2`, `autoNegotiate=true`, `forceVersion=false`.
   - Case B sets the external client to forced V1:
     `protocol=1`, `autoNegotiate=false`, `forceVersion=true`; retain the target agent at V2 with
     `autoNegotiate=true`, `forceVersion=false`.  This is the required V1-over-V2-agent path, not a
     V1-only server test.
   - For both cases, wait for all expected probe markers; assert an exit code of zero, no `FAILED`
     client output, no unexpected stderr, and each marker in stdout.  Do not treat a successful
     attach banner, socket connection, or `ProtocolNegotiator` unit result as success.
   - Keep the cases in `BTraceFunctionalTests`, which already runs under the ordinary
     `continuous.yml` `-Pintegration -PCI :integration-tests:test` matrix.  Do not alter the
     label-gated `v2-protocol-tests.yml` workflow.

3. Replace `testOnProbe`'s layout-derived green skip with a real, report-visible capability gate.

   - Add a minimal Java-8-compatible test helper class under `integration-tests/src/test/java`
     with a terminating `main` that attempts to load `javax.xml.bind.JAXBException` and emits a
     stable success marker only on success.
   - Add a `RuntimeTest` helper that invokes the selected `javaHome/bin/java` with the same BTrace
     client/test classpath (`cp`), runs that helper, bounds the process wait, and returns true only
     for its zero exit code plus the success marker.  This checks the Java runtime and classpath
     used by the external BTrace process, rather than using the host JUnit JVM or the obsolete
     `<java-home>/jre` directory as a proxy.
   - In `BTraceFunctionalTests.testOnProbe`, call JUnit `assumeTrue` on that helper before dynamic
     attach.  The assumption message must explicitly state that `@OnProbe` XML probe descriptors
     require `javax.xml.bind.JAXBException`/JAXB support and that it is unavailable.  Remove the
     `System.err.println(...); return;` path.
   - Preserve the existing functional path verbatim after the gate: attach
     `btrace/OnProbeTest.java`, wait for `[this, noargs]` and `[this, args]`, assert zero exit,
     reject `FAILED` and unexpected stderr, and assert both markers.  The Java-8 run remains a
     functional test; current modern runs become JUnit-aborted with the reason in the XML/HTML
     report, not successful no-ops.

4. Keep the change scoped and format it.

   - Do not modify `ProtocolConfig`, `Client`, `RemoteClient`, masked-JAR assembly, or default
     production settings.  The work tests existing bidirectional negotiation; it does not change
     its contract.
   - Do not add JAXB dependencies or claim that modern JDKs now support `@OnProbe`.  A future
     distribution change that provides JAXB will satisfy the capability probe and automatically
     exercise the retained assertions.
   - Apply the repository formatter only to the intended Java files if needed; do not format
     unrelated working-tree changes.

## Verification gates

Run Gradle with a workspace-local cache and redirect output before inspection.

1. `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:spotlessCheck > /tmp/issue-891-spotless.log 2>&1`
2. `rg -n "BUILD (SUCCESSFUL|FAILED)|FAILURE|ERROR" /tmp/issue-891-spotless.log`
3. `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build > /tmp/issue-891-dist.log 2>&1`
4. `rg -n "BUILD (SUCCESSFUL|FAILED)|FAILURE|ERROR" /tmp/issue-891-dist.log`
5. `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration :integration-tests:test --tests tests.BTraceFunctionalTests > /tmp/issue-891-functional.log 2>&1`
6. Inspect the focused report/log for both protocol test names and the `@OnProbe` result: passed
   with both markers when JAXB is available, otherwise explicitly skipped/aborted with the JAXB
   reason.
7. `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -Pintegration -PCI :integration-tests:test > /tmp/issue-891-integration.log 2>&1`
8. `rg -n "BUILD (SUCCESSFUL|FAILED)|FAILURE|ERROR|tests completed" /tmp/issue-891-integration.log`

If restricted-network socket selection fails, retry the affected Gradle command with
`JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"`.

## Stop conditions

- Stop and investigate before widening scope if the V1 client cannot complete against a V2
  auto-negotiating agent; capture the client/agent logs and determine whether the test setup or
  existing negotiation contract is at fault.
- Stop rather than weakening assertions if a case only reaches an attach/status banner without
  probe output, or if the `@OnProbe` gate still yields a successful no-op.
- Stop and create a separate masked-distribution design if making the modern `@OnProbe` test pass
  requires adding JAXB classes or resources to the shipped artifact.
- Do not commit until the focused test, required build prerequisite, full integration run, and
  formatting gate have passed (unless the user explicitly authorizes a known failing commit).
