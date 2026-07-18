# Issue #889: extensions pre-release fixes implementation plan

## Prerequisites and boundaries

- Implement only the four fixes in
  `internal/specs/2026-07-18-issue-889-extensions-pre-release-fixes.md`; preserve unrelated
  working-tree changes.
- Keep `btrace-ext-test` buildable for the ExternalType regression but outside every release
  distribution. Do not change descriptor forms or package-level descriptor versions.
- Keep the target-agent and client-compiler paths separate: the target discovers the staged test
  extension through `BTRACE_EXT_PATH`; the client discovers it through an isolated per-test
  `-Dbtrace.libs` home.

## Ordered implementation and gates

1. Restrict distribution packaging in `btrace-dist/build.gradle`.

   - Replace the current all-extension-project discovery for `copyExtensions` with the explicit
     maintained project-path allowlist from the design:
     `btrace-contracts`, `btrace-gpu-bridge`, `btrace-llm-trace`, `btrace-metrics`,
     `btrace-rag-quality`, `btrace-statsd`, and `btrace-utils`.
   - Continue to depend on each allowed project's `packageExtension` and copy its
     `*-extension.zip`; do not package `btrace-ext-test` or either example project.
   - Extend `btrace-dist/src/test/java/io/btrace/dist/BTraceJarPackagingTest.java` (or a focused
     sibling distribution-artifact test) to assert the built `extensions/` ZIP/exploded contents
     contain exactly the maintained IDs and exclude `btrace-ext-test`, `btrace-hadoop-example`,
     and `btrace-spark-example`.

   **Gate:** `:btrace-dist:test` proves the release artifact contains no test/example extension;
   adding future plugin-bearing projects cannot silently make them shippable.

2. Preserve the ExternalType regression with isolated staging.

   - In `integration-tests/build.gradle`, add a staging task dependent on both
     `:btrace-extensions:btrace-ext-test:packageExtension` and `:btrace-dist:btraceJar`. The
     staging task must copy the freshly built `btrace.jar` into
     `integration-tests/build/external-type-client-home/libs/` only after `btraceJar` completes,
     then expand the test extension into `extensions/btrace-ext-test/` with its API and
     implementation JAR layout intact. Publish the staged `libs/` and `extensions/` roots as
     dedicated test system properties; never write, link, or expand the test extension under
     `btrace-dist/build/**`.
   - Wire `:integration-tests:test` to depend on this staging task (in addition to its existing
     distribution dependencies), so every selected `ExternalTypeAdapterIntegrationTest` run sees
     a fresh isolated client home rather than a stale or missing staged artifact.
   - Extend `integration-tests/src/test/java/tests/RuntimeTest.java` with resettable, opt-in
     per-test hooks: one applies `BTRACE_EXT_PATH=<staged home>/extensions` only to target JVM
     `ProcessBuilder`s; the other replaces the existing `-Dbtrace.libs` argument only for the
     attach client `ProcessBuilder` with `<staged home>/libs`.
   - Update `integration-tests/src/test/java/tests/ExternalTypeAdapterIntegrationTest.java` to
     configure both hooks from the staging properties, assert the isolated paths are beneath the
     integration-test build directory, and retain its static/virtual adapter-output assertions.
     Reset hooks after each test so no later integration test inherits the client-home or target
     extension path.

   **Gate:** task ordering proves `btraceJar` and the test-extension package exist before the
   staging copy, and `tests.ExternalTypeAdapterIntegrationTest` passes with the target agent
   loading via `BTRACE_EXT_PATH` and client compilation scanning the staged sibling `extensions/`;
   the built release distribution remains free of `btrace-ext-test` before and after the test.

3. Cache StatsD resources by extension lifecycle.

   - Update `btrace-extensions/btrace-statsd/src/main/java/io/btrace/statsd/StatsdImpl.java` to
     resolve configured host/port and create/cache one `InetAddress` plus `DatagramSocket` during
     `initialize`; `increment` must only create/send its local packet through those resources.
   - Make setup failures disable emission without retries or exception escape on a probe thread.
     Coordinate concurrent sends with `close()`, which releases the cached socket. Preserve the
     public API, UDP counter/tags format, `NETWORK` permission, and best-effort behavior.
   - Add the test configuration required by this previously untested module in
     `btrace-extensions/btrace-statsd/build.gradle`, and add focused UDP/lifecycle coverage under
     `btrace-extensions/btrace-statsd/src/test/java/io/btrace/statsd/`.

   **Gate:** repeated increments reach a local UDP receiver without per-call endpoint setup;
   failed setup and close races remain non-throwing and cleanup closes the socket.

4. Correct metric square accumulation.

   - Update `btrace-extensions/btrace-metrics/src/main/java/io/btrace/metrics/stats/StatsMetricImpl.java`
     to use `DoubleAdder` for sum-of-squares and widen before multiplying.
   - Extend `btrace-extensions/btrace-metrics/src/test/java/io/btrace/metrics/StatsMetricTest.java`
     with samples such as `0` and `4_000_000_000L`, whose square exceeds `Long.MAX_VALUE`, and
     assert a finite, correct, non-zero population standard deviation within floating-point
     tolerance. Retain ordinary snapshot assertions.

   **Gate:** the regression fails under integral square overflow and passes with the double
   accumulator, without changing public metric APIs or unrelated sum behavior.

5. Correct generated manifest metadata.

   - Change only the generated `BTrace-API-Version` value in
     `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceExtensionPlugin.groovy` from
     `2.3+` to `3.0+`.
   - Extend `btrace-gradle-plugin/src/test/java/io/btrace/gradle/BTraceExtensionPluginTest.java`
     to open the generated API JAR and assert the manifest attribute is exactly `3.0+`.

   **Gate:** the test validates the artifact manifest rather than the Gradle configuration alone;
   extension artifact/version naming is unchanged.

6. Inspect the final diff and release artifact.

   - Confirm no descriptor, loader-policy, launcher, attach/protocol, StatsD batching/retry, or
     public API changes were introduced.
   - Run `git diff --check`, inspect the built distribution's `extensions/` contents, and confirm
     the ExternalType staging tree remains only under `integration-tests/build/`.

   **Gate:** the final diff matches every design boundary and no test-only archive is present in
   `btrace-dist/build`.

## Verification sequence

Run from the repository root with a workspace-local cache. Redirect every Gradle run to the named
log, use `rg` to filter it, then read the filtered result; do not consume raw Gradle output. If a
run encounters the documented address-selection failure, rerun that same command with
`JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false"`.

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-extensions:btrace-metrics:test > /tmp/btrace-issue-889-metrics.log 2>&1
rg -n "BUILD (SUCCESSFUL|FAILED)|FAILED|ERROR|StatsMetricTest|tests" /tmp/btrace-issue-889-metrics.log
```

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-extensions:btrace-statsd:test > /tmp/btrace-issue-889-statsd.log 2>&1
rg -n "BUILD (SUCCESSFUL|FAILED)|FAILED|ERROR|Statsd.*Test|tests" /tmp/btrace-issue-889-statsd.log
```

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew -p btrace-gradle-plugin test > /tmp/btrace-issue-889-plugin.log 2>&1
rg -n "BUILD (SUCCESSFUL|FAILED)|FAILED|ERROR|BTraceExtensionPluginTest|tests" /tmp/btrace-issue-889-plugin.log
```

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:test > /tmp/btrace-issue-889-dist-test.log 2>&1
rg -n "BUILD (SUCCESSFUL|FAILED)|FAILED|ERROR|BTraceJarPackagingTest|tests" /tmp/btrace-issue-889-dist-test.log
```

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-dist:build > /tmp/btrace-issue-889-dist-build.log 2>&1
rg -n "BUILD (SUCCESSFUL|FAILED)|FAILED|ERROR|copyExtensions|explodeExtensions" /tmp/btrace-issue-889-dist-build.log
```

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :integration-tests:test -Pintegration --tests tests.ExternalTypeAdapterIntegrationTest > /tmp/btrace-issue-889-external-type.log 2>&1
rg -n "BUILD (SUCCESSFUL|FAILED)|FAILED|ERROR|ExternalTypeAdapterIntegrationTest|tests" /tmp/btrace-issue-889-external-type.log
```

```bash
GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew :btrace-extensions:btrace-metrics:spotlessCheck :btrace-extensions:btrace-statsd:spotlessCheck :btrace-dist:spotlessCheck > /tmp/btrace-issue-889-spotless.log 2>&1
rg -n "BUILD (SUCCESSFUL|FAILED)|FAILED|ERROR|spotless" /tmp/btrace-issue-889-spotless.log
```

## Stop conditions

- Stop and return to design review if test-only staging requires copying into the release output,
  if client compilation cannot be isolated through the temporary `btrace.libs` home, or if target
  extension discovery needs an unapproved loader-policy change.
- Stop if StatsD caching requires asynchronous transport, retries, a public API change, or a
  semantic change to its best-effort failure behavior.
- Stop if the double accumulator cannot meet the stated metric regression without changing the
  documented snapshot contract; reconsider the numerical design before widening scope.
- Stop if a descriptor-format cleanup, legacy annotation version change, or unrelated extension
  becomes necessary; it is explicitly deferred from #889.
- Do not commit until all applicable gates and verification steps pass, unless the user explicitly
  directs otherwise.

## Completion criteria

- The release distribution ships exactly the seven allowlisted extensions and no test/examples.
- The ExternalType test stages `btrace-ext-test` only below `integration-tests/build`, exposes it
  to the target with `BTRACE_EXT_PATH`, exposes it to the client through isolated `btrace.libs`,
  and leaves `btrace-dist/build` untouched.
- StatsD uses initialized cached endpoint/socket resources and closes them safely; metrics report
  correct finite stddev for overflow-inducing inputs.
- Generated API JAR manifests contain `BTrace-API-Version: 3.0+`.
- All scoped unit, plugin, distribution, focused integration, Spotless, and final diff checks are
  clean, with no descriptor changes.
