# Issue #889: extensions pre-release fixes

Date: 2026-07-18  
Status: implementation contract for [#889](https://github.com/btraceio/btrace/issues/889)

## Scope

Fix four bounded 3.0 pre-release defects:

1. Ship only maintained extensions in the distribution.
2. Remove DNS lookup and UDP-socket allocation from the StatsD probe path.
3. Prevent `StatsMetricImpl` standard-deviation corruption from `long` square overflow.
4. Update generated extension API-JAR metadata from `BTrace-API-Version: 2.3+` to `3.0+`.

## Distribution contract

In `btrace-dist/build.gradle`, replace dynamic packaging of every project that applies
`io.btrace.extension` with this explicit, ordered project-path allowlist:

- `:btrace-extensions:btrace-contracts`
- `:btrace-extensions:btrace-gpu-bridge`
- `:btrace-extensions:btrace-llm-trace`
- `:btrace-extensions:btrace-metrics`
- `:btrace-extensions:btrace-rag-quality`
- `:btrace-extensions:btrace-statsd`
- `:btrace-extensions:btrace-utils`

`copyExtensions` must depend on and copy packages only for those projects. The built distribution's
`extensions/` directory must exclude `btrace-ext-test`, `btrace-hadoop-example`, and
`btrace-spark-example`. A new extension is not release-shipped until it is intentionally added to
this list.

### ExternalType test-only staging

`ExternalTypeAdapterIntegrationTest` dynamically starts a target JVM and a separate client JVM.
The agent initializes extension discovery inside the target, while client compilation scans only
the sibling `extensions/` directory of its `-Dbtrace.libs` directory. Consequently, the test
extension needs two isolated, process-specific exposures; `BTRACE_EXT_PATH` alone is insufficient
for client compilation.

Add an `integration-tests/build.gradle` staging task that depends on
`:btrace-extensions:btrace-ext-test:packageExtension` and builds the temporary client home
`integration-tests/build/external-type-client-home/`. It must stage a `libs/` directory containing
the built client artifact needed for the client invocation and expand the test extension ZIP under
`extensions/btrace-ext-test/`, preserving the API/implementation JAR layout. Expose both the
client-home `libs/` path and the staged extension root to the test JVM through dedicated test
system properties. Do not copy, link, or expand the test extension into `btrace-dist/build/**`.

Add a narrowly scoped `RuntimeTest` child-process environment hook, enabled only by
`ExternalTypeAdapterIntegrationTest`. The test resolves both staging properties and asks the
harness to put `BTRACE_EXT_PATH=<temporary client home>/extensions` (the parent of
`btrace-ext-test`) only on the dynamic target JVM's `ProcessBuilder`, so the agent discovers the
test extension through its environment repository.
For the separate client `attach` process, the harness must instead replace its existing
`-Dbtrace.libs=<release libs>` argument with
`-Dbtrace.libs=<temporary client home>/libs`; that makes `Client.getExtensionApiClasspath()` scan
the staged sibling `extensions/` directory when compiling the probe. Do not set either override
globally for every integration test, mutate the parent environment, or give the target JVM the
client-only `btrace.libs` override. Clear both per-test hooks in `RuntimeTest.reset()` so later
tests cannot inherit them.

## StatsD contract

Update `btrace-extensions/btrace-statsd/src/main/java/io/btrace/statsd/StatsdImpl.java` to use the
extension lifecycle. During `initialize`, capture the configured host/port, resolve the host once,
and create/cache one `DatagramSocket` and destination address. `increment` may build and send its
local UDP packet, but must not resolve DNS, allocate a socket, or perform endpoint setup.

If initialization cannot configure an endpoint, emission is disabled for that extension instance:
probe calls remain no-op, best-effort, and non-throwing without repeated DNS retries. `close()`
must release the cached socket, and send/close must be coordinated for concurrent probe calls.
The public API, `NETWORK` permission, counter/tags wire format, default port, and synchronous
best-effort delivery remain unchanged. Changing StatsD settings after initialization is not live
reconfiguration; detach and reattach to use another endpoint.

## Metrics contract

In `btrace-extensions/btrace-metrics/src/main/java/io/btrace/metrics/stats/StatsMetricImpl.java`,
replace the `LongAdder` sum-of-squares accumulator with `DoubleAdder` and widen before
multiplication (`(double) value * value`). Retain count, sum, min/max, reset, snapshot API, and
the current population-variance calculation. This narrowly fixes square overflow; it does not
redesign the algorithm or change the separate integral-sum overflow behavior.

## Manifest contract

In `btrace-gradle-plugin/src/main/groovy/io/btrace/gradle/BTraceExtensionPlugin.groovy`, generate
`BTrace-API-Version: 3.0+` for API JAR manifests. Extension artifact/version naming remains driven
by existing Gradle project and extension metadata; no loader range-parsing change is required.

## Tests and acceptance

- Add distribution-artifact coverage proving all seven allowlisted extensions are present and the
  test/example IDs are absent from packaged and exploded output.
- Update `ExternalTypeAdapterIntegrationTest` and `RuntimeTest` coverage to prove the target gets
  the staged `BTRACE_EXT_PATH`, the client gets the isolated `btrace.libs` home, and the adapter
  still dispatches its static and virtual calls. Assert the temporary home is beneath
  `integration-tests/build` and that neither staging nor the test creates `btrace-ext-test` below
  the built release distribution.
- Add StatsD unit coverage with a local UDP receiver: repeated increments deliver the expected
  payload through initialized cached resources, setup failure remains non-fatal, and close releases
  the socket.
- Extend `btrace-extensions/btrace-metrics/src/test/java/io/btrace/metrics/StatsMetricTest.java`
  with overflow-inducing samples such as `0` and `4_000_000_000L`; assert a finite, correct,
  non-zero population standard deviation.
- Extend Gradle-plugin manifest coverage to inspect a generated API JAR and assert `3.0+`.
- `:btrace-dist:build` is the release acceptance artifact: inspect `extensions/` after that build,
  then run only `tests.ExternalTypeAdapterIntegrationTest` to prove the test-only staging path.

## Verification boundary

Run from the repository root with `GRADLE_USER_HOME=$(pwd)/.gradle-user`; redirect each Gradle run
to `/tmp/btrace-issue-889-*.log`, filter it with `rg` for task/test failures and `BUILD SUCCESSFUL`,
then inspect the filtered output. Required checks are:

```text
:btrace-extensions:btrace-metrics:test
:btrace-extensions:btrace-statsd:test
:btrace-dist:test
:btrace-dist:build
:integration-tests:test -Pintegration --tests tests.ExternalTypeAdapterIntegrationTest
:btrace-extensions:btrace-metrics:spotlessCheck
:btrace-extensions:btrace-statsd:spotlessCheck
:btrace-dist:spotlessCheck
```

Run the Gradle-plugin test task from its included-build directory and inspect its generated API-JAR
manifest test result. If a documented address-selection failure occurs, repeat the same command
with the repository's IPv4 `JAVA_TOOL_OPTIONS` setting. Do not broaden this issue to an attach,
protocol, or general extension end-to-end suite beyond the one test-only-staging regression.

## Deferred and non-goals

Do not change user-installed extension discovery, `extensions.conf` allow/deny policy, launchers,
StatsD batching/retries/asynchronous delivery, or public metric APIs. Descriptor convergence is
explicitly deferred: DSL-only, DSL plus `@ServiceDescriptor`, and legacy package-level
`@ExtensionDescriptor` forms (including legacy `version = "1.0"`) need a separate migration and
compatibility review. Runtime manifest metadata remains authoritative.

## Compatibility and security boundary

Release contents intentionally lose only test/example extensions; maintained extension IDs and
APIs remain stable. StatsD continues to require `NETWORK` and remains best-effort UDP, but avoids
unbounded DNS/socket work on instrumented application threads. Metrics change only previously
incorrect overflow cases. No authentication, trust-policy, attach, protocol, or inbound network
surface changes.
